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
import org.niis.xroad.catalog.lister.v2.dto.HeartbeatV2Dto;
import org.niis.xroad.catalog.lister.v2.dto.LastCollectionDataV2Dto;
import org.niis.xroad.catalog.persistence.repository.DescriptorRepositoryV2;
import org.niis.xroad.catalog.persistence.repository.ErrorLogRepositoryV2;
import org.niis.xroad.catalog.persistence.repository.MemberRepositoryV2;
import org.niis.xroad.catalog.persistence.repository.ServiceRepositoryV2;
import org.niis.xroad.catalog.persistence.repository.SubsystemRepositoryV2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.Clock;
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
 * <p>Every dependency here is V2-only: {@code findLatestFetched} queries are pure
 * {@code MAX(fetched)} aggregates on the V2 read-model repositories, with no V1 dependency left
 * to sever. Error-log queries go through {@link ErrorLogRepositoryV2} so half-open range
 * semantics match the rest of the V2 API.
 */
@Slf4j
@Service
public class HeartbeatServiceV2 {

    private final String appName;
    private final String appVersion;
    private final MemberRepositoryV2 memberRepository;
    private final SubsystemRepositoryV2 subsystemRepository;
    private final ServiceRepositoryV2 serviceRepository;
    private final DescriptorRepositoryV2 descriptorRepository;
    private final ErrorLogRepositoryV2 errorLogRepository;
    private final Clock clock;

    public HeartbeatServiceV2(@Value("${xroad-catalog.app-name}") String appName,
            @Value("${xroad-catalog.app-version}") String appVersion,
            MemberRepositoryV2 memberRepository, SubsystemRepositoryV2 subsystemRepository,
            ServiceRepositoryV2 serviceRepository, DescriptorRepositoryV2 descriptorRepository,
            ErrorLogRepositoryV2 errorLogRepository, Clock clock) {
        this.appName = appName;
        this.appVersion = appVersion;
        this.memberRepository = memberRepository;
        this.subsystemRepository = subsystemRepository;
        this.serviceRepository = serviceRepository;
        this.descriptorRepository = descriptorRepository;
        this.errorLogRepository = errorLogRepository;
        this.clock = clock;
    }

    public HeartbeatV2Dto heartbeat() {
        LocalDateTime membersLastFetched = tryFetch(memberRepository::findLatestFetched);
        LocalDateTime subsystemsLastFetched = tryFetch(subsystemRepository::findLatestFetched);
        LocalDateTime servicesLastFetched = tryFetch(serviceRepository::findLatestFetched);
        LocalDateTime wsdlsLastFetched = tryFetch(descriptorRepository::findLatestWsdlFetched);
        LocalDateTime openapisLastFetched = tryFetch(descriptorRepository::findLatestOpenApiFetched);
        LocalDateTime restsLastFetched = tryFetch(descriptorRepository::findLatestRestFetched);

        long lastRunErrors = computeLastRunErrors(membersLastFetched, subsystemsLastFetched,
                servicesLastFetched, wsdlsLastFetched, openapisLastFetched, restsLastFetched);

        return HeartbeatV2Dto.builder()
                .appWorking(Boolean.TRUE)
                .dbWorking(tryCheckDatabase())
                .appName(appName)
                .appVersion(appVersion)
                .systemTime(LocalDateTime.now(clock))
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
            return errorLogRepository.findAnyInRange(earliest, LocalDateTime.now(clock),
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
            return Integer.valueOf(1).equals(memberRepository.checkConnection());
        } catch (Exception e) {
            log.warn("Database health check failed", e);
            return Boolean.FALSE;
        }
    }
}
