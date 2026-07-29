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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.catalog.persistence.entity.CollectionRun;
import org.niis.xroad.catalog.persistence.repository.CollectionRunRepository;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CollectionCycleRunnerTest {

    private static final long TICK_MILLIS = 50L;

    @Mock
    private ListClientsTask listClientsTask;
    @Mock
    private RecomputeDenormalizedColumnsTask recomputeTask;
    @Mock
    private CollectionRunRepository collectionRunRepository;

    private final FetchWorkTracker fetchWorkTracker = new FetchWorkTracker();

    @Test
    void successfulCycleFinalizesRunWithZeroPendingItems() {
        when(collectionRunRepository.save(any(CollectionRun.class))).thenAnswer(inv -> inv.getArgument(0));
        when(collectionRunRepository.findLatestMemberFetched()).thenReturn(LocalDateTime.of(2025, 6, 1, 10, 0));

        CollectionCycleRunner runner = new CollectionCycleRunner(listClientsTask, recomputeTask,
                collectionRunRepository, fetchWorkTracker, TICK_MILLIS);
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

    @Test
    void listClientsFailureIsContainedAndRunIsFinalizedUnsuccessful() {
        doThrow(new IllegalStateException("boom")).when(listClientsTask).run();
        when(collectionRunRepository.save(any(CollectionRun.class))).thenAnswer(inv -> inv.getArgument(0));

        CollectionCycleRunner runner = new CollectionCycleRunner(listClientsTask, recomputeTask,
                collectionRunRepository, fetchWorkTracker, TICK_MILLIS);
        runner.run();

        verify(recomputeTask).run();
        ArgumentCaptor<CollectionRun> saved = ArgumentCaptor.forClass(CollectionRun.class);
        verify(collectionRunRepository, atLeastOnce()).save(saved.capture());
        assertEquals(Boolean.FALSE, saved.getValue().getSuccess());
    }

    @Test
    void interruptDuringWaitReturnsWithoutThrowingAndMarksRunUnsuccessful() throws InterruptedException {
        fetchWorkTracker.register(1);
        when(collectionRunRepository.save(any(CollectionRun.class))).thenAnswer(inv -> inv.getArgument(0));

        CollectionCycleRunner runner = new CollectionCycleRunner(listClientsTask, recomputeTask,
                collectionRunRepository, fetchWorkTracker, TICK_MILLIS);

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

    @Test
    void progressIsWrittenAfterListClientsAndOnEachTickWhilePending() {
        fetchWorkTracker.register(1);
        when(collectionRunRepository.save(any(CollectionRun.class))).thenAnswer(inv -> inv.getArgument(0));

        CollectionCycleRunner runner = new CollectionCycleRunner(listClientsTask, recomputeTask,
                collectionRunRepository, fetchWorkTracker, TICK_MILLIS);

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
        when(collectionRunRepository.save(any(CollectionRun.class)))
                .thenReturn(new CollectionRun())
                .thenThrow(new IllegalStateException("db down"))
                .thenAnswer(inv -> inv.getArgument(0));

        CollectionCycleRunner runner = new CollectionCycleRunner(listClientsTask, recomputeTask,
                collectionRunRepository, fetchWorkTracker, TICK_MILLIS);
        runner.run();

        verify(recomputeTask).run();
        ArgumentCaptor<CollectionRun> saved = ArgumentCaptor.forClass(CollectionRun.class);
        verify(collectionRunRepository, atLeastOnce()).save(saved.capture());
        assertNotNull(saved.getValue().getFinished());
    }
}
