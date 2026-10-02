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

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.catalog.collector.configuration.TaskPoolConfiguration;
import org.niis.xroad.catalog.persistence.entity.CollectionRun;
import org.niis.xroad.catalog.persistence.repository.CollectionRunRepository;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CollectionCycleRunnerTest {

    private static final long TICK_MILLIS = 50L;

    // Starts at 12:30 so the window arithmetic below is deterministic regardless of wall clock; only the
    // bounded-window test moves it, to end the fetch window while the cycle is waiting.
    private static final Instant START = Instant.parse("2025-06-01T12:30:00Z");
    private static final Instant AFTER_WINDOW_END = Instant.parse("2025-06-01T13:30:00Z");

    private final SteppingClock clock = new SteppingClock(START);

    @Mock
    private ListClientsTask listClientsTask;
    @Mock
    private RecomputeDenormalizedColumnsTask recomputeTask;
    @Mock
    private CollectionRunRepository collectionRunRepository;
    @Mock
    private TaskPoolConfiguration taskPoolConfiguration;
    @Mock
    private CollectorMetrics collectorMetrics;

    private final FetchWorkTracker fetchWorkTracker = new FetchWorkTracker();

    private CollectionCycleRunner newRunner() {
        return new CollectionCycleRunner(listClientsTask, recomputeTask, collectionRunRepository, fetchWorkTracker,
                taskPoolConfiguration, collectorMetrics, clock, TICK_MILLIS);
    }

    private void unlimitedFetchWindow() {
        when(taskPoolConfiguration.isFetchRunUnlimited()).thenReturn(true);
    }

    private void insideFetchWindow() {
        when(taskPoolConfiguration.isFetchRunUnlimited()).thenReturn(false);
        when(taskPoolConfiguration.getFetchTimeAfterHour()).thenReturn(12);
        when(taskPoolConfiguration.getFetchTimeBeforeHour()).thenReturn(13);
    }

    private void outsideFetchWindow() {
        when(taskPoolConfiguration.isFetchRunUnlimited()).thenReturn(false);
        when(taskPoolConfiguration.getFetchTimeAfterHour()).thenReturn(13);
        when(taskPoolConfiguration.getFetchTimeBeforeHour()).thenReturn(14);
    }

    private void listClientsSucceeds() {
        when(listClientsTask.run()).thenReturn(true);
    }

    private void registerWorkWhenListingClients(int items) {
        doAnswer(invocation -> {
            fetchWorkTracker.register(items);
            return true;
        }).when(listClientsTask).run();
    }

    private void registerWorkAndEndFetchWindowWhenListingClients(int items) {
        doAnswer(invocation -> {
            fetchWorkTracker.register(items);
            clock.set(AFTER_WINDOW_END);
            return true;
        }).when(listClientsTask).run();
    }

    private List<String> runCapturingRunnerLog(CollectionCycleRunner runner) {
        Logger runnerLogger = (Logger) LoggerFactory.getLogger(CollectionCycleRunner.class);
        Level originalLevel = runnerLogger.getLevel();
        runnerLogger.setLevel(Level.DEBUG);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        runnerLogger.addAppender(appender);
        try {
            runner.run();
        } finally {
            runnerLogger.detachAppender(appender);
            runnerLogger.setLevel(originalLevel);
        }
        return appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
    }

    @Test
    void outsideTheFetchWindowTheCycleIsSkippedWithoutRunRowFetchRecomputeOrMetrics() {
        outsideFetchWindow();

        List<String> logged = runCapturingRunnerLog(newRunner());

        assertEquals(List.of("Outside the fetch window 13:00-14:00, skipping the collection cycle"), logged);
        verify(listClientsTask, never()).run();
        verifyNoInteractions(recomputeTask, collectionRunRepository, collectorMetrics);
    }

    @Test
    void oldErrorLogEntriesAreFlushedOnEveryTickAlsoOutsideTheFetchWindow() {
        outsideFetchWindow();

        newRunner().run();

        verify(listClientsTask).flushOldErrorLogEntries();
        verify(listClientsTask, never()).run();
    }

    @Test
    void fetchRunUnlimitedBypassesClosedFetchHours() {
        unlimitedFetchWindow();
        lenient().when(taskPoolConfiguration.getFetchTimeAfterHour()).thenReturn(13);
        lenient().when(taskPoolConfiguration.getFetchTimeBeforeHour()).thenReturn(14);
        when(collectionRunRepository.save(any(CollectionRun.class))).thenAnswer(inv -> inv.getArgument(0));

        newRunner().run();

        verify(listClientsTask).run();
        verify(recomputeTask).run();
    }

    /**
     * Pins the never-throws contract of {@code run()} for the checks that precede the cycle's own try block:
     * an hour outside 0-23 makes {@code CollectorUtils.isTimeBetweenHours} throw, and a fixed-delay scheduler
     * silently stops after an uncaught exception.
     */
    @Test
    void invalidFetchHourIsContainedAndSkipsTheCycle() {
        when(taskPoolConfiguration.isFetchRunUnlimited()).thenReturn(false);
        when(taskPoolConfiguration.getFetchTimeAfterHour()).thenReturn(3);
        when(taskPoolConfiguration.getFetchTimeBeforeHour()).thenReturn(24);

        CollectionCycleRunner runner = newRunner();

        assertDoesNotThrow(runner::run);
        verify(listClientsTask, never()).run();
        verifyNoInteractions(recomputeTask, collectionRunRepository, collectorMetrics);
    }

    @Test
    void errorLogFlushFailureIsContainedAndSkipsTheCycle() {
        doThrow(new IllegalStateException("db down")).when(listClientsTask).flushOldErrorLogEntries();

        CollectionCycleRunner runner = newRunner();

        assertDoesNotThrow(runner::run);
        verify(listClientsTask, never()).run();
        verifyNoInteractions(recomputeTask, collectionRunRepository, collectorMetrics);
    }

    @Test
    void insideTheFetchWindowTheCycleRunsAfterTheErrorLogFlush() {
        insideFetchWindow();
        listClientsSucceeds();
        when(collectionRunRepository.save(any(CollectionRun.class))).thenAnswer(inv -> inv.getArgument(0));

        List<String> logged = runCapturingRunnerLog(newRunner());

        assertFalse(logged.stream().anyMatch(line -> line.contains("skipping the collection cycle")), logged.toString());
        var order = inOrder(listClientsTask, recomputeTask);
        order.verify(listClientsTask).flushOldErrorLogEntries();
        order.verify(listClientsTask).run();
        order.verify(recomputeTask).run();
        ArgumentCaptor<CollectionRun> saved = ArgumentCaptor.forClass(CollectionRun.class);
        verify(collectionRunRepository, atLeastOnce()).save(saved.capture());
        assertEquals(Boolean.TRUE, saved.getValue().getSuccess());
        verify(collectorMetrics).recordCycleDuration(any(Duration.class), eq(true));
        verify(collectorMetrics).recordSuccess(saved.getValue().getFinished());
    }

    @Test
    void successfulCycleFinalizesRunWithZeroPendingItems() {
        unlimitedFetchWindow();
        listClientsSucceeds();
        when(collectionRunRepository.save(any(CollectionRun.class))).thenAnswer(inv -> inv.getArgument(0));
        when(collectionRunRepository.findLatestMemberFetched()).thenReturn(LocalDateTime.of(2025, 6, 1, 10, 0));

        CollectionCycleRunner runner = newRunner();
        runner.run();

        var order = inOrder(listClientsTask, recomputeTask);
        order.verify(listClientsTask).run();
        order.verify(recomputeTask).run();

        ArgumentCaptor<CollectionRun> saved = ArgumentCaptor.forClass(CollectionRun.class);
        verify(collectionRunRepository, atLeastOnce()).save(saved.capture());
        CollectionRun finalRow = saved.getValue();
        assertEquals(Boolean.TRUE, finalRow.getSuccess());
        assertNotNull(finalRow.getFinished());
        assertEquals(0, finalRow.getPendingItems());
        assertEquals(LocalDateTime.of(2025, 6, 1, 10, 0), finalRow.getMembersLastFetched());
    }

    /**
     * Pins that a throwing metrics collaborator cannot skip the run-row finalization write: the lister's
     * heartbeat reads that row, so leaving it unfinalized would corrupt the staleness signal. The
     * {@code run} object is mutated in place, so the save call count, not its final state, is what tells a
     * skipped save apart from a completed one.
     */
    @Test
    void metricRecordingFailureDoesNotPreventRunFinalization() {
        unlimitedFetchWindow();
        listClientsSucceeds();
        when(collectionRunRepository.save(any(CollectionRun.class))).thenAnswer(inv -> inv.getArgument(0));
        doThrow(new IllegalStateException("meter registry boom"))
                .when(collectorMetrics).recordCycleDuration(any(Duration.class), anyBoolean());

        CollectionCycleRunner runner = newRunner();
        runner.run();

        verify(recomputeTask).run();
        ArgumentCaptor<CollectionRun> saved = ArgumentCaptor.forClass(CollectionRun.class);
        verify(collectionRunRepository, atLeast(3)).save(saved.capture());
        CollectionRun finalRow = saved.getValue();
        assertNotNull(finalRow.getFinished());
        assertEquals(Boolean.TRUE, finalRow.getSuccess());
    }

    /**
     * Pins that the cycle-duration metric uses a monotonic elapsed-time source rather than the injected
     * wall clock: under this fixed clock any wall-clock difference is exactly zero, so only elapsed time
     * can produce the strictly positive duration asserted here. See {@link CollectionCycleRunner#run()}.
     */
    @Test
    void cycleDurationMetricUsesMonotonicElapsedTimeNotTheWallClock() {
        unlimitedFetchWindow();
        listClientsSucceeds();
        when(collectionRunRepository.save(any(CollectionRun.class))).thenAnswer(inv -> inv.getArgument(0));

        CollectionCycleRunner runner = newRunner();
        runner.run();

        ArgumentCaptor<Duration> recorded = ArgumentCaptor.forClass(Duration.class);
        verify(collectorMetrics).recordCycleDuration(recorded.capture(), eq(true));
        assertTrue(recorded.getValue().compareTo(Duration.ZERO) > 0,
                "expected a positive elapsed duration under a fixed wall clock, got " + recorded.getValue());
    }

    /**
     * The production failure path: {@link ListClientsTask#run()} catches the fetch failure, writes the
     * error log row and returns false. The cycle must then be finalized as unsuccessful in the run row and
     * the duration timer, and the last-success gauge must not advance.
     */
    @Test
    void listClientsFetchFailureFinalizesRunUnsuccessfulAndLeavesTheSuccessGaugeAlone() {
        unlimitedFetchWindow();
        when(listClientsTask.run()).thenReturn(false);
        when(collectionRunRepository.save(any(CollectionRun.class))).thenAnswer(inv -> inv.getArgument(0));

        CollectionCycleRunner runner = newRunner();
        runner.run();

        verify(recomputeTask).run();
        ArgumentCaptor<CollectionRun> saved = ArgumentCaptor.forClass(CollectionRun.class);
        verify(collectionRunRepository, atLeastOnce()).save(saved.capture());
        assertEquals(Boolean.FALSE, saved.getValue().getSuccess());
        assertNotNull(saved.getValue().getFinished());
        verify(collectorMetrics).recordCycleDuration(any(Duration.class), eq(false));
        verify(collectorMetrics, never()).recordSuccess(any());
    }

    /**
     * Pins the runner's own catch: {@link ListClientsTask#run()} never throws, so this only guards the
     * scheduler against a regression of that contract, with the same unsuccessful finalization.
     */
    @Test
    void exceptionEscapingListClientsIsContainedAndRunIsFinalizedUnsuccessful() {
        unlimitedFetchWindow();
        doThrow(new IllegalStateException("boom")).when(listClientsTask).run();
        when(collectionRunRepository.save(any(CollectionRun.class))).thenAnswer(inv -> inv.getArgument(0));

        CollectionCycleRunner runner = newRunner();

        assertDoesNotThrow(runner::run);
        verify(recomputeTask).run();
        ArgumentCaptor<CollectionRun> saved = ArgumentCaptor.forClass(CollectionRun.class);
        verify(collectionRunRepository, atLeastOnce()).save(saved.capture());
        assertEquals(Boolean.FALSE, saved.getValue().getSuccess());
        verify(collectorMetrics, never()).recordSuccess(any());
    }

    @Test
    void interruptDuringWaitReturnsWithoutThrowingAndMarksRunUnsuccessful() throws InterruptedException {
        unlimitedFetchWindow();
        registerWorkWhenListingClients(1);
        when(collectionRunRepository.save(any(CollectionRun.class))).thenAnswer(inv -> inv.getArgument(0));

        CollectionCycleRunner runner = newRunner();

        AtomicBoolean interruptedFlagRestored = new AtomicBoolean();
        Thread runnerThread = new Thread(() -> {
            runner.run();
            interruptedFlagRestored.set(Thread.currentThread().isInterrupted());
        });
        runnerThread.start();
        await().atMost(Duration.ofSeconds(2))
                .until(() -> runnerThread.getState() == Thread.State.TIMED_WAITING
                        || runnerThread.getState() == Thread.State.WAITING);
        runnerThread.interrupt();
        runnerThread.join(2_000);

        assertTrue(interruptedFlagRestored.get(), "interrupt flag should have been restored on the runner thread");
        verify(recomputeTask).run();
        ArgumentCaptor<CollectionRun> saved = ArgumentCaptor.forClass(CollectionRun.class);
        verify(collectionRunRepository, atLeastOnce()).save(saved.capture());
        assertEquals(Boolean.FALSE, saved.getValue().getSuccess());
    }

    /**
     * Pins the ordering the graceful-shutdown feature depends on: the finalization writes in
     * {@code run()}'s {@code finally} block run with the interrupt flag clear, and the flag is restored
     * only afterwards. Fails if the restore is ever moved back into the {@code catch} block.
     */
    @Test
    void finalizationWritesRunWithInterruptFlagClearedThenRestoredAfterReturn() throws InterruptedException {
        unlimitedFetchWindow();
        registerWorkWhenListingClients(1);
        when(collectionRunRepository.save(any(CollectionRun.class))).thenAnswer(inv -> inv.getArgument(0));

        AtomicBoolean interruptedDuringFinalization = new AtomicBoolean(true);
        doAnswer(invocation -> {
            interruptedDuringFinalization.set(Thread.currentThread().isInterrupted());
            return null;
        }).when(recomputeTask).run();

        CollectionCycleRunner runner = newRunner();

        AtomicBoolean interruptedFlagRestored = new AtomicBoolean();
        Thread runnerThread = new Thread(() -> {
            runner.run();
            interruptedFlagRestored.set(Thread.currentThread().isInterrupted());
        });
        runnerThread.start();
        await().atMost(Duration.ofSeconds(2))
                .until(() -> runnerThread.getState() == Thread.State.TIMED_WAITING
                        || runnerThread.getState() == Thread.State.WAITING);
        runnerThread.interrupt();
        runnerThread.join(2_000);

        assertFalse(interruptedDuringFinalization.get(), "finalization writes should run with the interrupt flag cleared");
        assertTrue(interruptedFlagRestored.get(), "interrupt flag should be restored once finalization completes");
    }

    @Test
    void progressIsWrittenAfterListClientsAndOnEachTickWhilePending() {
        unlimitedFetchWindow();
        registerWorkWhenListingClients(1);
        when(collectionRunRepository.save(any(CollectionRun.class))).thenAnswer(inv -> inv.getArgument(0));

        CollectionCycleRunner runner = newRunner();

        Thread runnerThread = new Thread(runner::run);
        runnerThread.start();

        // >= 3: the start-run write, the initial progress write, and at least one tick write.
        await().atMost(Duration.ofSeconds(5)).until(() -> mockingDetails(collectionRunRepository)
                .getInvocations().size() >= 3);

        ArgumentCaptor<CollectionRun> saved = ArgumentCaptor.forClass(CollectionRun.class);
        verify(collectionRunRepository, atLeastOnce()).save(saved.capture());
        assertTrue(saved.getAllValues().stream().anyMatch(r -> Integer.valueOf(1).equals(r.getPendingItems())),
                "expected at least one progress write with pendingItems == 1");
        assertTrue(saved.getAllValues().stream().anyMatch(r -> r.getProgressUpdated() != null),
                "expected progressUpdated to be set on a progress write");

        fetchWorkTracker.complete();
        await().atMost(Duration.ofSeconds(2)).until(() -> !runnerThread.isAlive());

        verify(recomputeTask).run();
    }

    @Test
    void progressWriteFailureDoesNotAbortCycle() {
        unlimitedFetchWindow();
        listClientsSucceeds();
        when(collectionRunRepository.save(any(CollectionRun.class)))
                .thenReturn(new CollectionRun())
                .thenThrow(new IllegalStateException("db down"))
                .thenAnswer(inv -> inv.getArgument(0));

        CollectionCycleRunner runner = newRunner();
        runner.run();

        verify(recomputeTask).run();
        ArgumentCaptor<CollectionRun> saved = ArgumentCaptor.forClass(CollectionRun.class);
        verify(collectionRunRepository, atLeastOnce()).save(saved.capture());
        assertNotNull(saved.getValue().getFinished());
    }

    @Test
    void leftoverPendingWorkFromPreviousCycleIsDiscardedAndCycleCompletes() {
        unlimitedFetchWindow();
        listClientsSucceeds();
        when(collectionRunRepository.save(any(CollectionRun.class))).thenAnswer(inv -> inv.getArgument(0));
        fetchWorkTracker.register(3);

        CollectionCycleRunner runner = newRunner();
        assertTimeoutPreemptively(Duration.ofSeconds(10), runner::run);

        assertEquals(0, fetchWorkTracker.pending());
        verify(listClientsTask).run();
        verify(recomputeTask).run();
        ArgumentCaptor<CollectionRun> saved = ArgumentCaptor.forClass(CollectionRun.class);
        verify(collectionRunRepository, atLeastOnce()).save(saved.capture());
        assertEquals(Boolean.TRUE, saved.getValue().getSuccess());
    }

    @Test
    void boundedFetchWindowEndsTheWaitAndFinalizesTheRunUnsuccessful() {
        insideFetchWindow();
        registerWorkAndEndFetchWindowWhenListingClients(2);
        when(collectionRunRepository.save(any(CollectionRun.class))).thenAnswer(inv -> inv.getArgument(0));

        CollectionCycleRunner runner = newRunner();
        assertTimeoutPreemptively(Duration.ofSeconds(10), runner::run);

        assertEquals(0, fetchWorkTracker.pending());
        verify(recomputeTask).run();
        ArgumentCaptor<CollectionRun> saved = ArgumentCaptor.forClass(CollectionRun.class);
        verify(collectionRunRepository, atLeastOnce()).save(saved.capture());
        assertEquals(Boolean.FALSE, saved.getValue().getSuccess());
    }

    @Test
    void unlimitedFetchWindowKeepsWaitingWithoutDeadline() {
        unlimitedFetchWindow();
        registerWorkWhenListingClients(1);
        when(collectionRunRepository.save(any(CollectionRun.class))).thenAnswer(inv -> inv.getArgument(0));

        CollectionCycleRunner runner = newRunner();
        Thread runnerThread = new Thread(runner::run);
        runnerThread.start();

        // many ticks pass without a deadline cutting the wait short
        await().atMost(Duration.ofSeconds(5)).until(() -> mockingDetails(collectionRunRepository)
                .getInvocations().size() >= 10);
        assertTrue(runnerThread.isAlive());
        assertEquals(1, fetchWorkTracker.pending());

        fetchWorkTracker.complete();
        await().atMost(Duration.ofSeconds(5)).until(() -> !runnerThread.isAlive());

        ArgumentCaptor<CollectionRun> saved = ArgumentCaptor.forClass(CollectionRun.class);
        verify(collectionRunRepository, atLeastOnce()).save(saved.capture());
        assertEquals(Boolean.TRUE, saved.getValue().getSuccess());
    }

    private static final class SteppingClock extends Clock {

        private final AtomicReference<Instant> now;

        SteppingClock(Instant start) {
            super();
            this.now = new AtomicReference<>(start);
        }

        void set(Instant instant) {
            now.set(instant);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now.get();
        }
    }
}
