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
import org.niis.xroad.catalog.persistence.entity.CollectionRun;
import org.niis.xroad.catalog.persistence.repository.CollectionRunRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * One collection cycle: list clients, block until the pending-work counter tracked by
 * {@link FetchWorkTracker} reaches zero (every fetch item registered during the cycle has
 * completed), recompute the denormalized columns, then finalize the {@link CollectionRun} row with
 * the per-type MAX(fetched) snapshot the heartbeat serves.
 *
 * <p>The wait is unbounded by design: all collector I/O is given explicit client timeouts, so every
 * fetch worker provably terminates and the counter is guaranteed to reach zero. The tick passed to
 * {@link FetchWorkTracker#awaitAllDone(long)} is a reporting interval, not a correctness poll — on
 * each tick the current pending count is written to the run row for heartbeat visibility, not
 * checked against a deadline.
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
    private final long tickMillis;

    @Autowired
    public CollectionCycleRunner(ListClientsTask listClientsTask, RecomputeDenormalizedColumnsTask recomputeTask,
            CollectionRunRepository collectionRunRepository, FetchWorkTracker fetchWorkTracker) {
        this(listClientsTask, recomputeTask, collectionRunRepository, fetchWorkTracker, TICK_MILLIS);
    }

    CollectionCycleRunner(ListClientsTask listClientsTask, RecomputeDenormalizedColumnsTask recomputeTask,
            CollectionRunRepository collectionRunRepository, FetchWorkTracker fetchWorkTracker, long tickMillis) {
        this.listClientsTask = listClientsTask;
        this.recomputeTask = recomputeTask;
        this.collectionRunRepository = collectionRunRepository;
        this.fetchWorkTracker = fetchWorkTracker;
        this.tickMillis = tickMillis;
    }

    public void run() {
        CollectionRun run = startRun();
        if (fetchWorkTracker.pending() > 0) {
            log.warn("Collection cycle starting with {} items already pending from a previous cycle", fetchWorkTracker.pending());
        }
        boolean listClientsOk = false;
        boolean allWorkDone = false;
        try {
            listClientsTask.run();
            listClientsOk = true;
            writeProgress(run);
            while (!fetchWorkTracker.awaitAllDone(tickMillis)) {
                log.info("Still collecting, {} items pending", fetchWorkTracker.pending());
                writeProgress(run);
            }
            allWorkDone = true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted while waiting for fetch tasks to finish", e);
        } catch (Exception e) {
            log.error("Collection cycle failed", e);
        }
        recomputeTask.run();
        finalizeRun(run, listClientsOk && allWorkDone);
    }

    private CollectionRun startRun() {
        CollectionRun run = new CollectionRun();
        run.setStarted(LocalDateTime.now());
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
            run.setProgressUpdated(LocalDateTime.now());
            collectionRunRepository.save(run);
        } catch (Exception e) {
            log.error("Failed to record collection run progress", e);
        }
    }

    private void finalizeRun(CollectionRun run, boolean success) {
        try {
            run.setFinished(LocalDateTime.now());
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
    }
}
