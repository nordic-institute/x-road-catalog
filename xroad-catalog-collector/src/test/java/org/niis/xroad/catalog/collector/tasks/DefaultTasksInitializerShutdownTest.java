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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.catalog.collector.configuration.TaskPoolConfiguration;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

/**
 * No-Spring guard for {@link DefaultTasksInitializer#shutdown()}: pins that shutdown interrupts the
 * scheduler and every worker, bounds its own wait, and lets an in-flight {@link CollectionCycleRunner}
 * cycle observe the interrupt (the steps {@code DefaultTasksInitializer} owns once
 * {@code CollectionCycleRunner}'s own {@code finally} block finalizes the run row).
 */
@ExtendWith(MockitoExtension.class)
class DefaultTasksInitializerShutdownTest {

    @Mock
    private FetchWsdlsTask fetchWsdlsTask;
    @Mock
    private FetchRestTask fetchRestTask;
    @Mock
    private FetchOpenApiTask fetchOpenApiTask;
    @Mock
    private ListMethodsTask listMethodsTask;
    @Mock
    private CollectionCycleRunner collectionCycleRunner;
    @Mock
    private TaskPoolConfiguration taskPoolConfiguration;

    @Test
    void shutdownInterruptsSchedulerAndWorkersWithinBoundAndObservesInFlightCycleInterrupt() throws InterruptedException {
        CountDownLatch cycleStarted = new CountDownLatch(1);
        CountDownLatch cycleInterrupted = new CountDownLatch(1);

        blockUntilInterrupted(fetchWsdlsTask);
        blockUntilInterrupted(fetchRestTask);
        blockUntilInterrupted(fetchOpenApiTask);
        blockUntilInterrupted(listMethodsTask);
        doAnswer(invocation -> {
            cycleStarted.countDown();
            try {
                new CountDownLatch(1).await();
            } catch (InterruptedException e) {
                cycleInterrupted.countDown();
                Thread.currentThread().interrupt();
            }
            return null;
        }).when(collectionCycleRunner).run();
        when(taskPoolConfiguration.getCollectorInterval()).thenReturn(20L);

        ScheduledExecutorService scheduler =
                Executors.newSingleThreadScheduledExecutor(Thread.ofPlatform().name("test-collector-scheduler").factory());
        DefaultTasksInitializer initializer = new DefaultTasksInitializer(scheduler, fetchWsdlsTask, fetchRestTask,
                fetchOpenApiTask, listMethodsTask, collectionCycleRunner, taskPoolConfiguration);

        initializer.onApplicationEvent(null);
        assertTrue(cycleStarted.await(5, TimeUnit.SECONDS), "collection cycle should have started");

        @SuppressWarnings("unchecked")
        List<Thread> workers = (List<Thread>) ReflectionTestUtils.getField(initializer, "workers");
        assertEquals(4, workers.size());

        long startNanos = System.nanoTime();
        initializer.shutdown();
        Duration elapsed = Duration.ofNanos(System.nanoTime() - startNanos);

        assertTrue(elapsed.toSeconds() < 10, "shutdown() should return well within the grace bound, took " + elapsed);
        assertTrue(scheduler.isTerminated(), "scheduler should be terminated");
        workers.forEach(worker -> assertFalse(worker.isAlive(), worker.getName() + " should be dead"));
        assertTrue(cycleInterrupted.await(5, TimeUnit.SECONDS), "in-flight cycle should have observed the interrupt");
    }

    @Test
    void shutdownWithoutPriorEventIsANoOp() {
        ScheduledExecutorService scheduler =
                Executors.newSingleThreadScheduledExecutor(Thread.ofPlatform().name("test-collector-scheduler-noop").factory());
        DefaultTasksInitializer initializer = new DefaultTasksInitializer(scheduler, fetchWsdlsTask, fetchRestTask,
                fetchOpenApiTask, listMethodsTask, collectionCycleRunner, taskPoolConfiguration);

        assertDoesNotThrow(initializer::shutdown);
        assertTrue(scheduler.isTerminated());
    }

    private void blockUntilInterrupted(Runnable task) {
        doAnswer(invocation -> {
            try {
                new CountDownLatch(1).await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return null;
        }).when(task).run();
    }
}
