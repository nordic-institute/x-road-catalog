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

import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.catalog.lister.service.CatalogService;
import org.niis.xroad.catalog.lister.v2.dto.HeartbeatV2Dto;
import org.niis.xroad.catalog.lister.v2.dto.LastCollectionDataV2Dto;
import org.niis.xroad.catalog.persistence.repository.ErrorLogRepositoryV2;
import org.niis.xroad.catalog.persistence.repository.MemberRepository;
import org.niis.xroad.catalog.persistence.repository.OpenApiRepository;
import org.niis.xroad.catalog.persistence.repository.RestRepository;
import org.niis.xroad.catalog.persistence.repository.ServiceRepository;
import org.niis.xroad.catalog.persistence.repository.SubsystemRepository;
import org.niis.xroad.catalog.persistence.repository.WsdlRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.function.Supplier;

/**
 * V2 heartbeat service. Produces the JSON response returned by {@code GET /api/v2/heartbeat}.
 *
 * <p>V2 heartbeat is a superset of V1: it adds {@code restsLastFetched} to the collection
 * timestamps and an aggregate {@code lastRunErrors} counter. The {@code lastRunErrors}
 * counter is the number of error-log rows recorded since the earliest of the per-type
 * last-fetched timestamps (i.e. since the start of the most recently completed collection
 * run). If any entity type has never been fetched the counter is defined as 0 — the system
 * has not yet completed a full run, so there is no meaningful "since" anchor.
 *
 * <p>{@code findLatestFetched} queries are served from the V1 repositories because those
 * are pure {@code MAX(fetched)} aggregates with no soft-delete semantics, so there is no
 * risk of V1 changes silently altering V2 output. Error-log queries go through
 * {@link ErrorLogRepositoryV2} so half-open range semantics match the rest of the V2 API.
 */
@Slf4j
@Component
public class HeartbeatServiceV2 {

    @Value("${xroad-catalog.app-name}")
    private String appName;

    @Value("${xroad-catalog.app-version}")
    private String appVersion;

    @Autowired
    private CatalogService catalogService;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private SubsystemRepository subsystemRepository;

    @Autowired
    private ServiceRepository serviceRepository;

    @Autowired
    private WsdlRepository wsdlRepository;

    @Autowired
    private OpenApiRepository openApiRepository;

    @Autowired
    private RestRepository restRepository;

    @Autowired
    private ErrorLogRepositoryV2 errorLogRepository;

    public HeartbeatV2Dto heartbeat() {
        LocalDateTime membersLastFetched = tryFetch(memberRepository::findLatestFetched);
        LocalDateTime subsystemsLastFetched = tryFetch(subsystemRepository::findLatestFetched);
        LocalDateTime servicesLastFetched = tryFetch(serviceRepository::findLatestFetched);
        LocalDateTime wsdlsLastFetched = tryFetch(wsdlRepository::findLatestFetched);
        LocalDateTime openapisLastFetched = tryFetch(openApiRepository::findLatestFetched);
        LocalDateTime restsLastFetched = tryFetch(restRepository::findLatestFetched);

        long lastRunErrors = computeLastRunErrors(membersLastFetched, subsystemsLastFetched,
                servicesLastFetched, wsdlsLastFetched, openapisLastFetched, restsLastFetched);

        return HeartbeatV2Dto.builder()
                .appWorking(Boolean.TRUE)
                .dbWorking(tryCheckDatabase())
                .appName(appName)
                .appVersion(appVersion)
                .systemTime(LocalDateTime.now())
                .lastCollectionData(LastCollectionDataV2Dto.builder()
                        .membersLastFetched(membersLastFetched)
                        .subsystemsLastFetched(subsystemsLastFetched)
                        .servicesLastFetched(servicesLastFetched)
                        .wsdlsLastFetched(wsdlsLastFetched)
                        .openapisLastFetched(openapisLastFetched)
                        .restsLastFetched(restsLastFetched)
                        .build())
                .lastRunErrors(lastRunErrors)
                .build();
    }

    private LocalDateTime tryFetch(Supplier<LocalDateTime> supplier) {
        try {
            return supplier.get();
        } catch (Exception e) {
            log.warn("findLatestFetched failed; reporting null", e);
            return null;
        }
    }

    private long computeLastRunErrors(LocalDateTime... lastFetchedValues) {
        if (hasNull(lastFetchedValues)) {
            return 0;
        }
        LocalDateTime earliest = Arrays.stream(lastFetchedValues)
                .min(LocalDateTime::compareTo)
                .orElseThrow();
        // ErrorLogRepositoryV2 exposes a paged query. A 1-row page is enough — we only care
        // about totalElements (the overall count, not the returned content).
        try {
            return errorLogRepository.findAnyInRange(earliest, LocalDateTime.now(),
                    Pageable.ofSize(1)).getTotalElements();
        } catch (Exception e) {
            log.warn("Failed to count errors since last collection run", e);
            return 0L;
        }
    }

    private boolean hasNull(LocalDateTime... values) {
        for (LocalDateTime v : values) {
            if (v == null) {
                return true;
            }
        }
        return false;
    }

    private Boolean tryCheckDatabase() {
        try {
            return catalogService.checkDatabaseConnection();
        } catch (Exception e) {
            log.warn("Database health check failed", e);
            return Boolean.FALSE;
        }
    }
}
