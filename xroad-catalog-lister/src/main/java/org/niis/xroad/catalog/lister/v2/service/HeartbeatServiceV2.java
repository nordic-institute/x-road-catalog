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
import org.niis.xroad.catalog.lister.v2.dto.CurrentRunDto;
import org.niis.xroad.catalog.lister.v2.dto.HeartbeatV2Dto;
import org.niis.xroad.catalog.lister.v2.dto.LastCollectionDataV2Dto;
import org.niis.xroad.catalog.persistence.entity.CollectionRun;
import org.niis.xroad.catalog.persistence.repository.CollectionRunRepository;
import org.niis.xroad.catalog.persistence.repository.DenormalizationRepository;
import org.niis.xroad.catalog.persistence.v2.repository.ErrorLogRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;

/**
 * Produces the {@code GET /api/v2/heartbeat} response.
 *
 * <p>The six {@code lastCollectionData} timestamps are the snapshot columns of the latest finished
 * {@link CollectionRun}, and {@code lastRunErrors} counts error-log rows since that run's
 * {@code started} timestamp. With no finished run, the timestamps are null and the counter 0.
 *
 * <p>{@code currentRun} is present only while a collection cycle is in progress (a
 * {@link CollectionRun} with a null {@code finished} timestamp). {@code pendingItems} not
 * decreasing while {@code progressUpdated} keeps advancing means the collector is alive but hung;
 * a stale {@code progressUpdated} on an old unfinished run means it died mid-cycle.
 *
 * <p>{@code globalConfExpired}/{@code globalConfExpiresAt} surface global-configuration staleness
 * from {@link SharedParamsCache#globalConfExpiry()}: expired conf is flagged, not refused, and an
 * unknown expiry reports {@code false}/null.
 */
@Slf4j
@Service
public class HeartbeatServiceV2 {

    private final String appName;
    private final String appVersion;
    private final CollectionRunRepository collectionRunRepository;
    private final ErrorLogRepository errorLogRepository;
    private final DenormalizationRepository denormalizationRepository;
    private final SharedParamsCache sharedParamsCache;
    private final Clock clock;

    public HeartbeatServiceV2(@Value("${xroad-catalog.app-name}") String appName,
            @Value("${xroad-catalog.app-version}") String appVersion,
            CollectionRunRepository collectionRunRepository, ErrorLogRepository errorLogRepository,
            DenormalizationRepository denormalizationRepository, SharedParamsCache sharedParamsCache, Clock clock) {
        this.appName = appName;
        this.appVersion = appVersion;
        this.collectionRunRepository = collectionRunRepository;
        this.errorLogRepository = errorLogRepository;
        this.denormalizationRepository = denormalizationRepository;
        this.sharedParamsCache = sharedParamsCache;
        this.clock = clock;
    }

    public HeartbeatV2Dto heartbeat() {
        CollectionRun lastRun = tryFetchLastFinishedRun();
        SharedParamsCache.GlobalConfExpiry confExpiry = tryGetGlobalConfExpiry();
        return HeartbeatV2Dto.builder()
                .appWorking(Boolean.TRUE)
                .dbWorking(tryCheckDatabase())
                .appName(appName)
                .appVersion(appVersion)
                .systemTime(LocalDateTime.now(clock))
                .lastCollectionData(toLastCollectionData(lastRun))
                .lastRunErrors(computeLastRunErrors(lastRun))
                .descriptorAnomalies(tryCountDescriptorAnomalies())
                .globalConfExpired(confExpiry.expired())
                .globalConfExpiresAt(toLocalDateTime(confExpiry.expiresAt()))
                .currentRun(toCurrentRun(tryFetchCurrentRun()))
                .build();
    }

    private SharedParamsCache.GlobalConfExpiry tryGetGlobalConfExpiry() {
        try {
            return sharedParamsCache.globalConfExpiry();
        } catch (Exception e) {
            log.warn("Failed to determine global configuration expiry; reporting unknown", e);
            return new SharedParamsCache.GlobalConfExpiry(false, null);
        }
    }

    private LocalDateTime toLocalDateTime(Instant instant) {
        return instant == null ? null : LocalDateTime.ofInstant(instant, clock.getZone());
    }

    private CollectionRun tryFetchLastFinishedRun() {
        try {
            return collectionRunRepository.findFirstByFinishedIsNotNullOrderByFinishedDesc().orElse(null);
        } catch (Exception e) {
            log.warn("Failed to load latest collection run; reporting empty collection data", e);
            return null;
        }
    }

    private CollectionRun tryFetchCurrentRun() {
        try {
            return collectionRunRepository.findFirstByFinishedIsNullOrderByStartedDesc().orElse(null);
        } catch (Exception e) {
            log.warn("Failed to load in-progress collection run; reporting no current run", e);
            return null;
        }
    }

    private CurrentRunDto toCurrentRun(CollectionRun currentRun) {
        if (currentRun == null) {
            return null;
        }
        return CurrentRunDto.builder()
                .started(currentRun.getStarted())
                .pendingItems(currentRun.getPendingItems())
                .progressUpdated(currentRun.getProgressUpdated())
                .build();
    }

    private LastCollectionDataV2Dto toLastCollectionData(CollectionRun lastRun) {
        if (lastRun == null) {
            return LastCollectionDataV2Dto.builder().build();
        }
        return LastCollectionDataV2Dto.builder()
                .membersLastFetched(lastRun.getMembersLastFetched())
                .subsystemsLastFetched(lastRun.getSubsystemsLastFetched())
                .servicesLastFetched(lastRun.getServicesLastFetched())
                .wsdlsLastFetched(lastRun.getWsdlsLastFetched())
                .openapisLastFetched(lastRun.getOpenapisLastFetched())
                .restsLastFetched(lastRun.getRestsLastFetched())
                .build();
    }

    private long computeLastRunErrors(CollectionRun lastRun) {
        if (lastRun == null) {
            return 0L;
        }
        try {
            return errorLogRepository.countInRange(lastRun.getStarted(), LocalDateTime.now(clock));
        } catch (Exception e) {
            log.warn("Failed to count errors since last collection run", e);
            return 0L;
        }
    }

    private long tryCountDescriptorAnomalies() {
        try {
            return denormalizationRepository.findServicesWithMultipleActiveDescriptors().size();
        } catch (Exception e) {
            log.warn("Failed to count descriptor anomalies; reporting 0", e);
            return 0L;
        }
    }

    private Boolean tryCheckDatabase() {
        try {
            return Integer.valueOf(1).equals(collectionRunRepository.checkConnection());
        } catch (Exception e) {
            log.warn("Database health check failed", e);
            return Boolean.FALSE;
        }
    }
}
