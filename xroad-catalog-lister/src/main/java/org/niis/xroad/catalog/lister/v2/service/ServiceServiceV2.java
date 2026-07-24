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
import org.niis.xroad.catalog.lister.v2.dto.DescriptorPayload;
import org.niis.xroad.catalog.lister.v2.dto.ServiceDto;
import org.niis.xroad.catalog.lister.v2.dto.ServiceVersionDto;
import org.niis.xroad.catalog.lister.v2.util.ServiceVersionUtil;
import org.niis.xroad.catalog.persistence.repository.DescriptorRepositoryV2;
import org.niis.xroad.catalog.persistence.repository.ServiceRepositoryV2;
import org.niis.xroad.catalog.persistence.repository.SubsystemRepositoryV2;
import org.niis.xroad.catalog.persistence.repository.projection.ServiceAggregateRow;
import org.niis.xroad.catalog.persistence.repository.projection.ServiceVersionRow;
import org.niis.xroad.catalog.persistence.v2entity.ServiceV2;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * V2 service service, backed by the {@link ServiceRepositoryV2} read model. Descriptor blobs are
 * fetched separately through {@link DescriptorRepositoryV2}; the list/lookup queries never carry
 * WSDL/OpenAPI {@code data} along.
 */
@Service
public class ServiceServiceV2 {

    private final ServiceRepositoryV2 serviceRepository;
    private final SubsystemRepositoryV2 subsystemRepository;
    private final DescriptorRepositoryV2 descriptorRepository;
    private final InstanceContext instanceContext;
    private final ObjectMapper objectMapper;

    public ServiceServiceV2(ServiceRepositoryV2 serviceRepository, SubsystemRepositoryV2 subsystemRepository,
            DescriptorRepositoryV2 descriptorRepository, InstanceContext instanceContext, ObjectMapper objectMapper) {
        this.serviceRepository = serviceRepository;
        this.subsystemRepository = subsystemRepository;
        this.descriptorRepository = descriptorRepository;
        this.instanceContext = instanceContext;
        this.objectMapper = objectMapper;
    }

    /**
     * @return the service aggregate DTO, or an empty {@link Optional} if absent (controller maps to 404)
     */
    public Optional<ServiceDto> getByNaturalKey(String memberClass, String memberCode, String subsystemCode, String serviceCode) {
        String instance = instanceContext.getCurrentInstance();
        List<ServiceVersionRow> versions = serviceRepository.findActiveVersionRowsForService(
                instance, memberClass, memberCode, subsystemCode, serviceCode);
        if (versions.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(ServiceDto.from(versions));
    }

    /**
     * Count-then-rows with a short-circuit on zero, and a batched version fetch keyed by
     * {@code (subsystemId, serviceCode)}: 3 queries per page (count, aggregates, versions),
     * regardless of page size. {@code findActiveVersionRowsForKeys} deliberately over-selects the
     * cross product of the two IN-lists (JPQL has no portable row-value IN), so {@code byKey} is
     * load-bearing here — it discards any row whose exact pair wasn't actually requested.
     */
    public Page<ServiceDto> getForList(String memberClass, String serviceType, Pageable pageable) {
        String instance = instanceContext.getCurrentInstance();
        long totalCount = serviceRepository.countActiveAggregatesForList(instance, memberClass, serviceType);
        if (totalCount == 0) {
            return new PageImpl<>(List.of(), pageable, 0);
        }
        List<ServiceAggregateRow> aggregates =
                serviceRepository.findActiveAggregatesForList(instance, memberClass, serviceType, pageable);
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

    /**
     * Returns the service aggregates under a single subsystem, sorted by {@code serviceCode}
     * ascending (the version-row query already orders by {@code serviceCode, serviceVersion}, so
     * grouping into a {@link LinkedHashMap} preserves that order without a re-sort). An empty
     * {@link Optional} means the subsystem itself is absent (404); a present-but-empty list means
     * the subsystem exists but has no active services (200 {@code []}).
     */
    public Optional<List<ServiceDto>> getForSubsystem(String memberClass, String memberCode, String subsystemCode) {
        String instance = instanceContext.getCurrentInstance();
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
     * @return sorted (nulls-last) version DTOs, or an empty {@link Optional} if the service is
     *         absent — an active aggregate always has at least one version, so an empty result set
     *         means "no such service" (controller maps to 404), not "zero versions"
     */
    public Optional<List<ServiceVersionDto>> getVersions(String memberClass, String memberCode,
                                                          String subsystemCode, String serviceCode) {
        String instance = instanceContext.getCurrentInstance();
        List<ServiceV2> versions = serviceRepository.findActiveVersionsByNaturalKey(
                instance, memberClass, memberCode, subsystemCode, serviceCode);
        if (versions.isEmpty()) {
            return Optional.empty();
        }
        List<ServiceV2> sorted = versions.stream()
                .sorted(Comparator.comparing(ServiceV2::getServiceVersion, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
        List<ServiceVersionDto> result = new ArrayList<>(sorted.size());
        for (ServiceV2 s : sorted) {
            result.add(ServiceVersionDto.from(s));
        }
        return Optional.of(result);
    }

    /**
     * @return the version DTO, or an empty {@link Optional} if no such version exists (controller maps to 404)
     */
    public Optional<ServiceVersionDto> getVersion(String memberClass, String memberCode, String subsystemCode,
                                        String serviceCode, String serviceVersion) {
        return findVersionEntity(memberClass, memberCode, subsystemCode, serviceCode, serviceVersion)
                .map(ServiceVersionDto::from);
    }

    /**
     * Returns the active descriptor bytes for a single service version, or an empty {@link Optional} if no such
     * version exists or the version exists but has no active WSDL or OpenAPI row.
     */
    public Optional<DescriptorPayload> getVersionDescriptor(String memberClass, String memberCode, String subsystemCode,
                                                  String serviceCode, String serviceVersion) {
        return findVersionEntity(memberClass, memberCode, subsystemCode, serviceCode, serviceVersion)
                .map(svc -> payloadFor(svc.getId()));
    }

    /**
     * Returns the active descriptor bytes for a service that has exactly one active version. Throws
     * {@link MultipleVersionsException} when 2+ active versions exist (caller must pick a specific
     * version path). Returns an empty {@link Optional} when 0 active versions exist or when the only
     * version has no active descriptor.
     */
    public Optional<DescriptorPayload> getServiceLevelDescriptor(String memberClass, String memberCode, String subsystemCode,
                                                       String serviceCode) {
        String instance = instanceContext.getCurrentInstance();
        List<ServiceV2> versions = serviceRepository.findActiveVersionsByNaturalKey(
                instance, memberClass, memberCode, subsystemCode, serviceCode);
        if (versions.isEmpty()) {
            return Optional.empty();
        }
        if (versions.size() == 1) {
            return Optional.ofNullable(payloadFor(versions.get(0).getId()));
        }
        List<String> versionLabels = new ArrayList<>();
        for (ServiceV2 s : versions) {
            versionLabels.add(s.getServiceVersion());
        }
        throw new MultipleVersionsException(
                "Service has multiple versions; pick a specific version via /versions/{serviceVersion}/descriptor",
                versionLabels);
    }

    private Optional<ServiceV2> findVersionEntity(String memberClass, String memberCode, String subsystemCode,
                                        String serviceCode, String serviceVersion) {
        String instance = instanceContext.getCurrentInstance();
        String resolvedVersion = ServiceVersionUtil.resolveVersionSentinel(serviceVersion);
        if (resolvedVersion == null) {
            return serviceRepository.findActiveNullVersionByNaturalKey(
                    instance, memberClass, memberCode, subsystemCode, serviceCode);
        }
        return serviceRepository.findActiveVersionByNaturalKey(
                instance, memberClass, memberCode, subsystemCode, serviceCode, resolvedVersion);
    }

    /**
     * WSDL-then-OpenAPI probing preserves the priority order established by the old
     * {@code getActiveWsdl}/{@code getActiveOpenApi} tiebreak: first row = lowest id.
     */
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
        try {
            objectMapper.readTree(body);
            return new DescriptorPayload(body, MediaType.APPLICATION_JSON);
        } catch (IOException e) {
            // Not valid JSON -> treat as YAML. Don't depend on a YAML parser being on classpath:
            // the collector validated the content at write time, so we trust the raw bytes here.
            return new DescriptorPayload(body, MediaType.parseMediaType("application/yaml"));
        }
    }
}
