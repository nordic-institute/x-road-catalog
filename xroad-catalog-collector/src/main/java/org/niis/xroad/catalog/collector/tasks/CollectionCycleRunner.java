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

import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.catalog.collector.configuration.TaskPoolConfiguration;
import org.niis.xroad.catalog.persistence.entity.CollectionRun;
import org.niis.xroad.catalog.persistence.repository.CollectionRunRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * One collection cycle: list clients, block until the {@link FetchWorkTracker} pending counter
 * reaches zero, recompute the denormalized columns, then finalize the {@link CollectionRun} row.
 *
 * <p>The tick passed to {@link FetchWorkTracker#awaitAllDone(long)} is a reporting interval, not a
 * deadline — each tick writes the pending count to the run row for heartbeat visibility. The wait
 * itself ends at the end of the configured fetch window, so a counter that never drains cannot block
 * the scheduler thread and stop collection altogether. A fetch window configured as unlimited has no
 * end, so in that case the wait stays unbounded.
 *
 * <p>{@link #run()} never throws: it is the fixed-delay scheduler body, and an uncaught exception
 * would permanently kill the schedule.
 */
@Slf4j
@Component
public class CollectionCycleRunner {

    private static final long TICK_MILLIS = 60_000L;

    private final ListClientsTask listClientsTask;
    private final RecomputeDenormalizedColumnsTask recomputeTask;
    private final CollectionRunRepository collectionRunRepository;
    private final FetchWorkTracker fetchWorkTracker;
    private final TaskPoolConfiguration taskPoolConfiguration;
    private final CollectorMetrics collectorMetrics;
    private final Clock clock;
    private final long tickMillis;

    @Autowired
    public CollectionCycleRunner(ListClientsTask listClientsTask, RecomputeDenormalizedColumnsTask recomputeTask,
            CollectionRunRepository collectionRunRepository, FetchWorkTracker fetchWorkTracker,
            TaskPoolConfiguration taskPoolConfiguration, CollectorMetrics collectorMetrics, Clock clock) {
        this(listClientsTask, recomputeTask, collectionRunRepository, fetchWorkTracker, taskPoolConfiguration, collectorMetrics,
                clock, TICK_MILLIS);
    }

    CollectionCycleRunner(ListClientsTask listClientsTask, RecomputeDenormalizedColumnsTask recomputeTask,
            CollectionRunRepository collectionRunRepository, FetchWorkTracker fetchWorkTracker,
            TaskPoolConfiguration taskPoolConfiguration, CollectorMetrics collectorMetrics, Clock clock, long tickMillis) {
        this.listClientsTask = listClientsTask;
        this.recomputeTask = recomputeTask;
        this.collectionRunRepository = collectionRunRepository;
        this.fetchWorkTracker = fetchWorkTracker;
        this.taskPoolConfiguration = taskPoolConfiguration;
        this.collectorMetrics = collectorMetrics;
        this.clock = clock;
        this.tickMillis = tickMillis;
    }

    public void run() {
        CollectionRun run = startRun();
        long leftover = fetchWorkTracker.reset();
        if (leftover > 0) {
            log.warn("Collection cycle discarded {} items left pending by a previous cycle", leftover);
        }
        boolean listClientsOk = false;
        boolean allWorkDone = false;
        boolean interrupted = false;
        try {
            LocalDateTime deadline = fetchWindowEnd();
            listClientsTask.run();
            listClientsOk = true;
            writeProgress(run);
            allWorkDone = awaitAllWorkDone(run, deadline);
        } catch (InterruptedException e) {
            // The interrupt flag is deliberately NOT restored here: the finally block below still has
            // to reach the database to finalize this run, and a borrowed connection's own interruptible
            // wait (e.g. Hikari's connection handoff) would otherwise immediately fail on a flag that
            // was never cleared. Restored once those writes are done, below.
            interrupted = true;
            log.warn("Interrupted while waiting for fetch tasks to finish", e);
        } catch (Exception e) {
            log.error("Collection cycle failed", e);
        } finally {
            // Runs even on failure: partial fetch results still need recomputing and the run row
            // must be closed (success=false). Both catch internally, so run() never throws.
            recomputeTask.run();
            finalizeRun(run, listClientsOk && allWorkDone);
        }
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * End of the window in which the collector is allowed to fetch, or null when fetching is unlimited.
     * Reuses the {@code fetch-time-before-hour} semantics of {@link ListClientsTask}.
     */
    private LocalDateTime fetchWindowEnd() {
        if (taskPoolConfiguration.isFetchRunUnlimited()) {
            return null;
        }
        return LocalDate.now(clock).atTime(taskPoolConfiguration.getFetchTimeBeforeHour(), 0);
    }

    private boolean awaitAllWorkDone(CollectionRun run, LocalDateTime deadline) throws InterruptedException {
        while (!fetchWorkTracker.awaitAllDone(tickMillis)) {
            if (deadline != null && LocalDateTime.now(clock).isAfter(deadline)) {
                log.error("Fetch window ended at {} with {} items still pending; abandoning this collection cycle",
                        deadline, fetchWorkTracker.pending());
                long discarded = fetchWorkTracker.reset();
                log.warn("Discarded {} pending items of the abandoned collection cycle", discarded);
                return false;
            }
            log.info("Collecting ecosystem data, {} items pending", fetchWorkTracker.pending());
            writeProgress(run);
        }
        return true;
    }

    private CollectionRun startRun() {
        CollectionRun run = new CollectionRun();
        run.setStarted(LocalDateTime.now(clock));
        try {
            return collectionRunRepository.save(run);
        } catch (Exception e) {
            log.error("Failed to record collection run start", e);
            return run;
        }
    }

    private void writeProgress(CollectionRun run) {
        try {
            run.setPendingItems((int) fetchWorkTracker.pending());
            run.setProgressUpdated(LocalDateTime.now(clock));
            collectionRunRepository.save(run);
        } catch (Exception e) {
            log.error("Failed to record collection run progress", e);
        }
    }

    private void finalizeRun(CollectionRun run, boolean success) {
        try {
            run.setFinished(LocalDateTime.now(clock));
            run.setSuccess(success);
            run.setPendingItems(0);
            run.setMembersLastFetched(collectionRunRepository.findLatestMemberFetched());
            run.setSubsystemsLastFetched(collectionRunRepository.findLatestSubsystemFetched());
            run.setServicesLastFetched(collectionRunRepository.findLatestServiceFetched());
            run.setWsdlsLastFetched(collectionRunRepository.findLatestWsdlFetched());
            run.setOpenapisLastFetched(collectionRunRepository.findLatestOpenApiFetched());
            run.setRestsLastFetched(collectionRunRepository.findLatestRestFetched());
            collectionRunRepository.save(run);
        } catch (Exception e) {
            log.error("Failed to finalize collection run", e);
        }
        // Recorded after the row is saved above, and isolated in its own catch, so a MeterRegistry
        // failure here can never skip or roll back the run-row finalization the lister's heartbeat relies
        // on.
        try {
            collectorMetrics.recordCycleDuration(Duration.between(run.getStarted(), run.getFinished()), success);
            if (success) {
                collectorMetrics.recordSuccess(run.getFinished());
            }
        } catch (Exception e) {
            log.error("Failed to record collection cycle metrics", e);
        }
    }
}
