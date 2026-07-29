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

import org.niis.xroad.catalog.lister.v2.controller.MultipleVersionsException;
import org.niis.xroad.catalog.lister.v2.dto.DescriptorPayload;
import org.niis.xroad.catalog.lister.v2.dto.ServiceDto;
import org.niis.xroad.catalog.lister.v2.dto.ServiceVersionDto;
import org.niis.xroad.catalog.lister.v2.util.ServiceVersionUtil;
import org.niis.xroad.catalog.persistence.v2.repository.DescriptorRepository;
import org.niis.xroad.catalog.persistence.v2.repository.ServiceRepository;
import org.niis.xroad.catalog.persistence.v2.repository.SubsystemRepository;
import org.niis.xroad.catalog.persistence.v2.repository.projection.ServiceAggregateRow;
import org.niis.xroad.catalog.persistence.v2.repository.projection.ServiceVersionRow;
import org.niis.xroad.catalog.persistence.v2.entity.Service;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * V2 service queries backed by the {@link ServiceRepository} read model. Descriptor blobs are
 * fetched separately so list/lookup queries never carry WSDL/OpenAPI {@code data}.
 */
@org.springframework.stereotype.Service
public class ServiceServiceV2 {

    // UNKNOWN is a transient not-yet-classified state, filterable so operators can find such services.
    private static final Set<String> ALLOWED_SERVICE_TYPES = Set.of("SOAP", "REST", "OPENAPI", "UNKNOWN");

    private final ServiceRepository serviceRepository;
    private final SubsystemRepository subsystemRepository;
    private final DescriptorRepository descriptorRepository;
    private final SharedParamsCache sharedParamsCache;

    public ServiceServiceV2(ServiceRepository serviceRepository, SubsystemRepository subsystemRepository,
            DescriptorRepository descriptorRepository, SharedParamsCache sharedParamsCache) {
        this.serviceRepository = serviceRepository;
        this.subsystemRepository = subsystemRepository;
        this.descriptorRepository = descriptorRepository;
        this.sharedParamsCache = sharedParamsCache;
    }

    public Optional<ServiceDto> getByNaturalKey(String memberClass, String memberCode, String subsystemCode, String serviceCode) {
        String instance = sharedParamsCache.getCurrentInstance();
        List<ServiceVersionRow> versions = serviceRepository.findActiveVersionRowsForService(
                instance, memberClass, memberCode, subsystemCode, serviceCode);
        if (versions.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(ServiceDto.from(versions));
    }

    /**
     * Three queries per page (count, aggregates, versions) regardless of page size. The version
     * fetch over-selects the cross product of the two IN-lists (JPQL has no portable row-value IN);
     * the {@code byKey} lookup discards rows whose exact {@code (subsystemId, serviceCode)} pair
     * was not requested.
     */
    public Page<ServiceDto> getForList(String memberClass, String serviceType, Pageable pageable) {
        String resolvedType = resolveServiceType(serviceType);
        String instance = sharedParamsCache.getCurrentInstance();
        long totalCount = serviceRepository.countActiveAggregatesForList(instance, memberClass, resolvedType);
        if (totalCount == 0) {
            return new PageImpl<>(List.of(), pageable, 0);
        }
        List<ServiceAggregateRow> aggregates =
                serviceRepository.findActiveAggregatesForList(instance, memberClass, resolvedType, pageable);
        if (aggregates.isEmpty()) {
            return new PageImpl<>(List.of(), pageable, totalCount);
        }
        Set<Long> subsystemIds = aggregates.stream().map(ServiceAggregateRow::getSubsystemId).collect(Collectors.toSet());
        Set<String> serviceCodes = aggregates.stream().map(ServiceAggregateRow::getServiceCode).collect(Collectors.toSet());
        List<ServiceVersionRow> versionRows = serviceRepository.findActiveVersionRowsForKeys(subsystemIds, serviceCodes);
        Map<String, List<ServiceVersionRow>> byKey = new HashMap<>();
        for (ServiceVersionRow row : versionRows) {
            byKey.computeIfAbsent(row.getSubsystemId() + "|" + row.getServiceCode(), k -> new ArrayList<>()).add(row);
        }
        List<ServiceDto> dtos = new ArrayList<>(aggregates.size());
        for (ServiceAggregateRow agg : aggregates) {
            dtos.add(ServiceDto.from(byKey.get(agg.getSubsystemId() + "|" + agg.getServiceCode())));
        }
        return new PageImpl<>(dtos, pageable, totalCount);
    }

    private static String resolveServiceType(String serviceType) {
        if (serviceType == null || serviceType.isBlank()) {
            return null;
        }
        if (!ALLOWED_SERVICE_TYPES.contains(serviceType)) {
            throw new IllegalArgumentException(
                    "Invalid value for query parameter 'serviceType': '" + serviceType
                            + "'. Allowed: " + ALLOWED_SERVICE_TYPES);
        }
        return serviceType;
    }

    /**
     * Service aggregates under one subsystem, in {@code serviceCode} order (the version-row query
     * orders by {@code serviceCode, serviceVersion}; the {@link LinkedHashMap} preserves it). Empty
     * {@link Optional} means the subsystem is absent; a present-but-empty list means it has no
     * active services.
     */
    public Optional<List<ServiceDto>> getForSubsystem(String memberClass, String memberCode, String subsystemCode) {
        String instance = sharedParamsCache.getCurrentInstance();
        if (!subsystemRepository.existsActiveByNaturalKey(instance, memberClass, memberCode, subsystemCode)) {
            return Optional.empty();
        }
        List<ServiceVersionRow> versionRows =
                serviceRepository.findActiveVersionRowsForSubsystem(instance, memberClass, memberCode, subsystemCode);
        Map<String, List<ServiceVersionRow>> byCode = new LinkedHashMap<>();
        for (ServiceVersionRow row : versionRows) {
            byCode.computeIfAbsent(row.getServiceCode(), k -> new ArrayList<>()).add(row);
        }
        List<ServiceDto> result = new ArrayList<>(byCode.size());
        for (List<ServiceVersionRow> group : byCode.values()) {
            result.add(ServiceDto.from(group));
        }
        return Optional.of(result);
    }

    /**
     * @return version DTOs, or an empty {@link Optional} when the service is absent — an active
     *         aggregate always has at least one version, so empty means "no such service"
     */
    public Optional<List<ServiceVersionDto>> getVersions(String memberClass, String memberCode,
                                                          String subsystemCode, String serviceCode) {
        String instance = sharedParamsCache.getCurrentInstance();
        List<Service> versions = serviceRepository.findActiveVersionsByNaturalKey(
                instance, memberClass, memberCode, subsystemCode, serviceCode);
        if (versions.isEmpty()) {
            return Optional.empty();
        }
        List<ServiceVersionDto> result = new ArrayList<>(versions.size());
        for (Service s : versions) {
            result.add(ServiceVersionDto.from(s));
        }
        return Optional.of(result);
    }

    public Optional<ServiceVersionDto> getVersion(String memberClass, String memberCode, String subsystemCode,
                                        String serviceCode, String serviceVersion) {
        return findVersionEntity(memberClass, memberCode, subsystemCode, serviceCode, serviceVersion)
                .map(ServiceVersionDto::from);
    }

    /**
     * Active descriptor bytes for a version; empty when the version is absent or has no active
     * WSDL/OpenAPI row.
     */
    public Optional<DescriptorPayload> getVersionDescriptor(String memberClass, String memberCode, String subsystemCode,
                                                  String serviceCode, String serviceVersion) {
        return findVersionEntity(memberClass, memberCode, subsystemCode, serviceCode, serviceVersion)
                .map(svc -> payloadFor(svc.getId()));
    }

    /**
     * Active descriptor bytes when the service has exactly one active version; empty when it has
     * none or the sole version has no active descriptor.
     *
     * @throws MultipleVersionsException when 2+ active versions exist
     */
    public Optional<DescriptorPayload> getServiceLevelDescriptor(String memberClass, String memberCode, String subsystemCode,
                                                       String serviceCode) {
        String instance = sharedParamsCache.getCurrentInstance();
        List<Service> versions = serviceRepository.findActiveVersionsByNaturalKey(
                instance, memberClass, memberCode, subsystemCode, serviceCode);
        if (versions.isEmpty()) {
            return Optional.empty();
        }
        if (versions.size() == 1) {
            return Optional.ofNullable(payloadFor(versions.get(0).getId()));
        }
        List<String> versionLabels = new ArrayList<>();
        for (Service s : versions) {
            versionLabels.add(s.getServiceVersion());
        }
        throw new MultipleVersionsException(
                "Service has multiple versions; pick a specific version via /versions/{serviceVersion}/descriptor",
                versionLabels);
    }

    private Optional<Service> findVersionEntity(String memberClass, String memberCode, String subsystemCode,
                                        String serviceCode, String serviceVersion) {
        String instance = sharedParamsCache.getCurrentInstance();
        String resolvedVersion = ServiceVersionUtil.resolveVersionSentinel(serviceVersion);
        if (resolvedVersion == null) {
            return serviceRepository.findActiveNullVersionByNaturalKey(
                    instance, memberClass, memberCode, subsystemCode, serviceCode);
        }
        return serviceRepository.findActiveVersionByNaturalKey(
                instance, memberClass, memberCode, subsystemCode, serviceCode, resolvedVersion);
    }

    // WSDL takes priority over OpenAPI; first row wins (lowest id).
    private DescriptorPayload payloadFor(long serviceId) {
        List<String> wsdl = descriptorRepository.findActiveWsdlData(serviceId);
        if (!wsdl.isEmpty()) {
            return new DescriptorPayload(wsdl.getFirst().getBytes(StandardCharsets.UTF_8), MediaType.APPLICATION_XML);
        }
        List<String> openApi = descriptorRepository.findActiveOpenApiData(serviceId);
        if (!openApi.isEmpty()) {
            return openApiPayload(openApi.getFirst());
        }
        return null;
    }

    private DescriptorPayload openApiPayload(String data) {
        byte[] body = data.getBytes(StandardCharsets.UTF_8);
        // Stored OpenAPI content is JSON re-serialized by the collector (always starts with '{'),
        // and since JSON has no comments, any other leading character means YAML.
        String trimmed = data.stripLeading();
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            return new DescriptorPayload(body, MediaType.APPLICATION_JSON);
        }
        return new DescriptorPayload(body, MediaType.parseMediaType("application/yaml"));
    }
}
