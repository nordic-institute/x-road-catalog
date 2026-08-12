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

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.catalog.persistence.entity.CollectionRun;
import org.niis.xroad.catalog.persistence.repository.CollectionRunRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Business metrics for the collector's collection cycle, exported via the Prometheus registry.
 */
@Slf4j
@Component
public class CollectorMetrics {

    static final String CYCLE_DURATION_METRIC = "xroad.catalog.collection.cycle.duration";
    static final String LAST_SUCCESS_METRIC = "xroad.catalog.collection.last.success";
    static final String SUCCESS_TAG = "success";

    private final MeterRegistry meterRegistry;
    private final Clock clock;
    private final AtomicLong lastSuccessEpochSeconds = new AtomicLong();

    @Autowired
    public CollectorMetrics(MeterRegistry meterRegistry, CollectionRunRepository collectionRunRepository, Clock clock) {
        this.meterRegistry = meterRegistry;
        this.clock = clock;
        Gauge.builder(LAST_SUCCESS_METRIC, lastSuccessEpochSeconds, AtomicLong::get)
                .description("Epoch seconds of the last successful collection cycle, for alerting on staleness")
                .baseUnit("seconds")
                .register(meterRegistry);
        seedLastSuccess(collectionRunRepository);
    }

    public void recordCycleDuration(Duration duration, boolean success) {
        Timer.builder(CYCLE_DURATION_METRIC)
                .description("Duration of a full collector collection cycle")
                .tag(SUCCESS_TAG, String.valueOf(success))
                .register(meterRegistry)
                .record(duration);
    }

    public void recordSuccess(LocalDateTime finishedAt) {
        lastSuccessEpochSeconds.set(finishedAt.atZone(clock.getZone()).toEpochSecond());
    }

    private void seedLastSuccess(CollectionRunRepository collectionRunRepository) {
        try {
            collectionRunRepository.findFirstBySuccessTrueOrderByFinishedDesc()
                    .map(CollectionRun::getFinished)
                    .ifPresent(this::recordSuccess);
        } catch (Exception e) {
            log.error("Failed to seed the last successful collection cycle metric", e);
        }
    }
}
