/**
 * The MIT License
 *
 * Copyright (c) 2023- Nordic Institute for Interoperability Solutions (NIIS)
 * Copyright (c) 2016-2023 Finnish Digital Agency
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.niis.xroad.catalog.lister.v2.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.niis.xroad.catalog.lister.v2.controller.MultipleVersionsException;
import org.niis.xroad.catalog.lister.v2.converter.ServiceAggregator;
import org.niis.xroad.catalog.lister.v2.converter.ServiceVersionConverter;
import org.niis.xroad.catalog.lister.v2.dto.DescriptorPayload;
import org.niis.xroad.catalog.lister.v2.dto.ServiceDto;
import org.niis.xroad.catalog.lister.v2.dto.ServiceVersionDto;
import org.niis.xroad.catalog.lister.v2.util.DateTimeUtil;
import org.niis.xroad.catalog.persistence.entity.OpenApi;
import org.niis.xroad.catalog.persistence.entity.Service;
import org.niis.xroad.catalog.persistence.entity.Subsystem;
import org.niis.xroad.catalog.persistence.entity.Wsdl;
import org.niis.xroad.catalog.persistence.repository.ServiceRepositoryV2;
import org.niis.xroad.catalog.persistence.repository.SubsystemRepositoryV2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class ServiceServiceV2 {

    // aggregateRows column indices from ServiceRepositoryV2.findAggregatesForList
    private static final int COL_MEMBER_CLASS = 0;
    private static final int COL_MEMBER_CODE = 1;
    private static final int COL_SUBSYSTEM_CODE = 2;
    private static final int COL_SERVICE_CODE = 3;
    // findAggregatesForList rows are length 4 (the natural-key tuple). Version counts and created/changed
    // timestamps are recomputed by the aggregator from the full version list loaded below.

    @Autowired
    private ServiceRepositoryV2 serviceRepository;

    @Autowired
    private SubsystemRepositoryV2 subsystemRepository;

    @Autowired
    private ServiceAggregator aggregator;

    @Autowired
    private ServiceVersionConverter versionConverter;

    @Autowired
    private InstanceContext instanceContext;

    @Autowired
    private ObjectMapper objectMapper;

    public ServiceDto getByNaturalKey(String memberClass, String memberCode, String subsystemCode,
                                      String serviceCode, boolean includeRemoved) {
        String instance = instanceContext.getCurrentInstance();
        List<Service> versions = loadVersions(instance, memberClass, memberCode, subsystemCode,
                serviceCode, includeRemoved);
        if (versions.isEmpty()) {
            return null;
        }
        return aggregator.aggregate(versions, includeRemoved);
    }

    public Page<ServiceDto> getForList(String memberClass, String serviceType, boolean includeRemoved,
                                       Pageable pageable) {
        boolean activeOnly = !includeRemoved;
        List<Object[]> aggregateRows = serviceRepository.findAggregatesForList(
                memberClass, serviceType, activeOnly, pageable);
        long totalCount = serviceRepository.countAggregatesForList(memberClass, serviceType, activeOnly);

        if (aggregateRows.isEmpty()) {
            return new PageImpl<>(List.of(), pageable, totalCount);
        }

        // Batch-fetch versions for all aggregates on this page. The aggregate query already filtered
        // by activeOnly and cascaded parent removal — loading versions with the same active/any
        // semantics keeps lookup and list views consistent.
        String instance = instanceContext.getCurrentInstance();
        Map<String, List<Service>> byKey = new HashMap<>();
        for (Object[] row : aggregateRows) {
            String mc = (String) row[COL_MEMBER_CLASS];
            String mcode = (String) row[COL_MEMBER_CODE];
            String ss = (String) row[COL_SUBSYSTEM_CODE];
            String code = (String) row[COL_SERVICE_CODE];
            List<Service> versions = loadVersions(instance, mc, mcode, ss, code, includeRemoved);
            byKey.put(keyOf(mc, mcode, ss, code), versions);
        }

        List<ServiceDto> dtos = new ArrayList<>();
        for (Object[] row : aggregateRows) {
            String mc = (String) row[COL_MEMBER_CLASS];
            String mcode = (String) row[COL_MEMBER_CODE];
            String ss = (String) row[COL_SUBSYSTEM_CODE];
            String code = (String) row[COL_SERVICE_CODE];
            List<Service> versions = byKey.get(keyOf(mc, mcode, ss, code));
            ServiceDto agg = aggregator.aggregate(versions, includeRemoved);
            if (agg != null) {
                dtos.add(agg);
            }
        }
        return new PageImpl<>(dtos, pageable, totalCount);
    }

    public ServiceVersionDto getVersion(String memberClass, String memberCode, String subsystemCode,
                                        String serviceCode, String serviceVersion,
                                        boolean includeRemoved) {
        // Contract: serviceVersion is the raw URL segment. Resolve the "null" sentinel to Java null
        // (same convention as ErrorLogServiceV2). A null result means the caller asked for the
        // null-version entity; a non-null result is an explicit version.
        String instance = instanceContext.getCurrentInstance();
        String resolvedVersion = DateTimeUtil.resolveVersionSentinel(serviceVersion);
        Service svc;
        if (resolvedVersion == null) {
            svc = includeRemoved
                    ? serviceRepository.findAnyNullVersionByNaturalKey(
                            instance, memberClass, memberCode, serviceCode, subsystemCode)
                    : serviceRepository.findActiveNullVersionByNaturalKey(
                            instance, memberClass, memberCode, serviceCode, subsystemCode);
        } else {
            svc = includeRemoved
                    ? serviceRepository.findAnyByMemberServiceAndSubsystemAndVersion(
                            instance, memberClass, memberCode, serviceCode, subsystemCode, resolvedVersion)
                    : serviceRepository.findActiveByMemberServiceAndSubsystemAndVersion(
                            instance, memberClass, memberCode, serviceCode, subsystemCode, resolvedVersion);
        }
        if (svc == null) {
            return null;
        }
        return versionConverter.toDto(svc, includeRemoved);
    }

    public List<ServiceVersionDto> getVersions(String memberClass, String memberCode, String subsystemCode,
                                               String serviceCode, boolean includeRemoved) {
        String instance = instanceContext.getCurrentInstance();
        List<Service> versions = loadVersions(instance, memberClass, memberCode, subsystemCode,
                serviceCode, includeRemoved);
        List<ServiceVersionDto> result = new ArrayList<>();
        for (Service s : versions) {
            result.add(versionConverter.toDto(s, includeRemoved));
        }
        // The underlying load returns versions whose order is influenced by Set iteration; sort
        // explicitly so /versions responses are stable across JVM restarts and result-set hashing.
        // Null versions sort last to match the aggregator's nullsLast convention.
        result.sort(Comparator.comparing(ServiceVersionDto::getServiceVersion,
                Comparator.nullsLast(Comparator.naturalOrder())));
        return result;
    }

    /**
     * Returns the service aggregates under a single subsystem, sorted by {@code serviceCode} ascending.
     * Subsystem is looked up via {@link SubsystemRepositoryV2} (entity-graph annotated to pre-fetch
     * services and their wsdls/openApis/rests collections) so iteration touches no extra queries.
     * Returns an empty list when the subsystem is absent — the controller is responsible for the
     * parent 404; this method stays defensive.
     */
    public List<ServiceDto> getForSubsystem(String memberClass, String memberCode, String subsystemCode,
                                            boolean includeRemoved) {
        String instance = instanceContext.getCurrentInstance();
        Subsystem subsystem = includeRemoved
                ? subsystemRepository.findAnyByNaturalKeyWithServices(instance, memberClass, memberCode, subsystemCode)
                : subsystemRepository.findActiveByNaturalKeyWithServices(instance, memberClass, memberCode, subsystemCode);
        if (subsystem == null) {
            return List.of();
        }
        Set<Service> source = includeRemoved ? subsystem.getAllServices() : subsystem.getActiveServices();
        Map<String, List<Service>> grouped = new LinkedHashMap<>();
        for (Service s : source) {
            grouped.computeIfAbsent(s.getServiceCode(), k -> new ArrayList<>()).add(s);
        }
        List<ServiceDto> result = new ArrayList<>();
        for (List<Service> group : grouped.values()) {
            ServiceDto dto = aggregator.aggregate(group, includeRemoved);
            if (dto != null) {
                result.add(dto);
            }
        }
        result.sort(Comparator.comparing(ServiceDto::getServiceCode));
        return result;
    }

    /**
     * Returns the active descriptor bytes for a single service version, or {@code null} if no
     * such version exists or the version exists but has no active WSDL or OpenAPI row. Note: the
     * returned bytes always come from a non-removed descriptor row regardless of {@code includeRemoved}
     * (which only affects whether the parent service version is "visible"). See spec §1.2 — the
     * descriptor endpoint never serves removed bytes.
     */
    public DescriptorPayload getVersionDescriptor(String memberClass, String memberCode, String subsystemCode,
                                                  String serviceCode, String serviceVersion,
                                                  boolean includeRemoved) {
        Service svc = findVersionEntity(memberClass, memberCode, subsystemCode, serviceCode,
                serviceVersion, includeRemoved);
        return svc == null ? null : payloadFor(svc);
    }

    /**
     * Returns the active descriptor bytes for a service that has exactly one visible version. Throws
     * {@link MultipleVersionsException} when 2+ visible versions exist (caller must pick a specific
     * version path). Returns {@code null} when 0 visible versions exist or when the only visible
     * version has no active descriptor. Visibility is governed by {@code includeRemoved}.
     */
    public DescriptorPayload getServiceLevelDescriptor(String memberClass, String memberCode, String subsystemCode,
                                                       String serviceCode, boolean includeRemoved) {
        String instance = instanceContext.getCurrentInstance();
        List<Service> versions = loadVersions(instance, memberClass, memberCode, subsystemCode,
                serviceCode, includeRemoved);
        if (versions.isEmpty()) {
            return null;
        }
        if (versions.size() == 1) {
            return payloadFor(versions.get(0));
        }
        List<String> versionLabels = new ArrayList<>();
        for (Service s : versions) {
            versionLabels.add(s.getServiceVersion());
        }
        throw new MultipleVersionsException(
                "Service has multiple versions; pick a specific version via /versions/{serviceVersion}/descriptor",
                versionLabels);
    }

    private Service findVersionEntity(String memberClass, String memberCode, String subsystemCode,
                                      String serviceCode, String serviceVersion, boolean includeRemoved) {
        String instance = instanceContext.getCurrentInstance();
        String resolvedVersion = DateTimeUtil.resolveVersionSentinel(serviceVersion);
        if (resolvedVersion == null) {
            return includeRemoved
                    ? serviceRepository.findAnyNullVersionByNaturalKey(
                            instance, memberClass, memberCode, serviceCode, subsystemCode)
                    : serviceRepository.findActiveNullVersionByNaturalKey(
                            instance, memberClass, memberCode, serviceCode, subsystemCode);
        }
        return includeRemoved
                ? serviceRepository.findAnyByMemberServiceAndSubsystemAndVersion(
                        instance, memberClass, memberCode, serviceCode, subsystemCode, resolvedVersion)
                : serviceRepository.findActiveByMemberServiceAndSubsystemAndVersion(
                        instance, memberClass, memberCode, serviceCode, subsystemCode, resolvedVersion);
    }

    private DescriptorPayload payloadFor(Service svc) {
        if (svc.hasActiveWsdl()) {
            Wsdl wsdl = svc.getActiveWsdl();
            return new DescriptorPayload(
                    wsdl.getData().getBytes(StandardCharsets.UTF_8),
                    MediaType.APPLICATION_XML);
        }
        if (svc.hasActiveOpenApi()) {
            OpenApi openApi = svc.getActiveOpenApi();
            byte[] body = openApi.getData().getBytes(StandardCharsets.UTF_8);
            try {
                objectMapper.readTree(body);
                return new DescriptorPayload(body, MediaType.APPLICATION_JSON);
            } catch (IOException e) {
                // Not valid JSON -> treat as YAML. Don't depend on a YAML parser being on classpath:
                // the collector validated the content at write time, so we trust the raw bytes here.
                return new DescriptorPayload(body, MediaType.parseMediaType("application/yaml"));
            }
        }
        return null;
    }

    private List<Service> loadVersions(String instance, String memberClass, String memberCode,
                                       String subsystemCode, String serviceCode, boolean includeRemoved) {
        // includeRemoved=false → parent cascade + service-row active; includeRemoved=true → all rows.
        return includeRemoved
                ? serviceRepository.findAnyByMemberServiceAndSubsystem(
                        instance, memberClass, memberCode, serviceCode, subsystemCode)
                : serviceRepository.findActiveByMemberServiceAndSubsystem(
                        instance, memberClass, memberCode, serviceCode, subsystemCode);
    }

    private String keyOf(String memberClass, String memberCode, String subsystemCode, String serviceCode) {
        return memberClass + "|" + memberCode + "|" + subsystemCode + "|" + serviceCode;
    }
}
