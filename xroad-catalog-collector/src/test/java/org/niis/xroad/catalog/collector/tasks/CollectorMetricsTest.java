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
package org.niis.xroad.catalog.collector.tasks;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.catalog.persistence.entity.CollectionRun;
import org.niis.xroad.catalog.persistence.repository.CollectionRunRepository;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CollectorMetricsTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2025-06-01T12:30:00Z"), ZoneOffset.UTC);

    @Mock
    private CollectionRunRepository collectionRunRepository;

    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

    @Test
    void recordsCycleDurationWithSuccessTag() {
        when(collectionRunRepository.findFirstBySuccessTrueOrderByFinishedDesc()).thenReturn(Optional.empty());
        CollectorMetrics metrics = new CollectorMetrics(meterRegistry, collectionRunRepository, FIXED_CLOCK);

        metrics.recordCycleDuration(Duration.ofSeconds(5), true);

        double totalSeconds = meterRegistry.get("xroad.catalog.collection.cycle.duration")
                .tag("success", "true")
                .timer()
                .totalTime(TimeUnit.SECONDS);
        assertEquals(5.0, totalSeconds, 0.001);
    }

    @Test
    void recordsCycleDurationWithFailureTag() {
        when(collectionRunRepository.findFirstBySuccessTrueOrderByFinishedDesc()).thenReturn(Optional.empty());
        CollectorMetrics metrics = new CollectorMetrics(meterRegistry, collectionRunRepository, FIXED_CLOCK);

        metrics.recordCycleDuration(Duration.ofSeconds(3), false);

        long count = meterRegistry.get("xroad.catalog.collection.cycle.duration")
                .tag("success", "false")
                .timer()
                .count();
        assertEquals(1, count);
    }

    @Test
    void lastSuccessGaugeIsSeededToZeroWhenNoSuccessfulRunExists() {
        when(collectionRunRepository.findFirstBySuccessTrueOrderByFinishedDesc()).thenReturn(Optional.empty());

        new CollectorMetrics(meterRegistry, collectionRunRepository, FIXED_CLOCK);

        double value = meterRegistry.get("xroad.catalog.collection.last.success.timestamp").gauge().value();
        assertEquals(0.0, value, 0.001);
    }

    @Test
    void lastSuccessGaugeIsSeededFromRepositoryAtStartup() {
        CollectionRun lastSuccessfulRun = new CollectionRun();
        lastSuccessfulRun.setFinished(LocalDateTime.of(2025, 6, 1, 10, 0));
        when(collectionRunRepository.findFirstBySuccessTrueOrderByFinishedDesc()).thenReturn(Optional.of(lastSuccessfulRun));

        new CollectorMetrics(meterRegistry, collectionRunRepository, FIXED_CLOCK);

        double value = meterRegistry.get("xroad.catalog.collection.last.success.timestamp").gauge().value();
        long expectedEpochSeconds = LocalDateTime.of(2025, 6, 1, 10, 0).atZone(ZoneOffset.UTC).toEpochSecond();
        assertEquals(expectedEpochSeconds, value, 0.001);
    }

    @Test
    void lastSuccessGaugeUpdatesOnlyOnSuccess() {
        when(collectionRunRepository.findFirstBySuccessTrueOrderByFinishedDesc()).thenReturn(Optional.empty());
        CollectorMetrics metrics = new CollectorMetrics(meterRegistry, collectionRunRepository, FIXED_CLOCK);

        metrics.recordCycleDuration(Duration.ofSeconds(1), false);
        double afterFailure = meterRegistry.get("xroad.catalog.collection.last.success.timestamp").gauge().value();
        assertEquals(0.0, afterFailure, 0.001);

        metrics.recordSuccess(LocalDateTime.of(2025, 6, 1, 11, 0));
        double afterSuccess = meterRegistry.get("xroad.catalog.collection.last.success.timestamp").gauge().value();
        long expectedEpochSeconds = LocalDateTime.of(2025, 6, 1, 11, 0).atZone(ZoneOffset.UTC).toEpochSecond();
        assertEquals(expectedEpochSeconds, afterSuccess, 0.001);
    }

    @Test
    void startupSeedingReadsTheRepositoryOnce() {
        when(collectionRunRepository.findFirstBySuccessTrueOrderByFinishedDesc()).thenReturn(Optional.empty());

        new CollectorMetrics(meterRegistry, collectionRunRepository, FIXED_CLOCK);

        verify(collectionRunRepository).findFirstBySuccessTrueOrderByFinishedDesc();
    }
}
