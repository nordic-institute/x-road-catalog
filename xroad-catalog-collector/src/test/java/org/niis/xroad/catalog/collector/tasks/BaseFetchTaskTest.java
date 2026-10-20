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

import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BaseFetchTaskTest {

    private static final class RecordingFetchTask extends BaseFetchTask<String> {

        private final List<String> fetched = new CopyOnWriteArrayList<>();

        private RecordingFetchTask(final BlockingQueue<String> inputQueue, final int poolSize,
                final FetchWorkTracker fetchWorkTracker) {
            super(inputQueue, poolSize, fetchWorkTracker);
        }

        @Override
        protected void fetch(final String input) {
            fetched.add(input);
        }
    }

    @Test
    void takenItemIsCompletedWhenHandOffIsInterruptedBeforeSemaphoreIsAcquired() throws InterruptedException {
        BlockingQueue<String> inputQueue = new LinkedBlockingQueue<>();
        FetchWorkTracker fetchWorkTracker = new FetchWorkTracker();
        fetchWorkTracker.register(1);
        RecordingFetchTask task = new RecordingFetchTask(inputQueue, 1, fetchWorkTracker);
        Semaphore exhausted = new Semaphore(0);
        ReflectionTestUtils.setField(task, "semaphore", exhausted);

        Thread runner = Thread.ofVirtual().start(task::run);
        inputQueue.add("input");

        Awaitility.await().atMost(Duration.ofSeconds(5)).until(exhausted::hasQueuedThreads);

        runner.interrupt();

        Awaitility.await().atMost(Duration.ofSeconds(5)).until(() -> fetchWorkTracker.pending() == 0);
        assertTrue(inputQueue.isEmpty());
        assertEquals(List.of(), task.fetched);
    }

    @Test
    void handedOffItemIsCompletedByTheWorker() {
        BlockingQueue<String> inputQueue = new LinkedBlockingQueue<>();
        FetchWorkTracker fetchWorkTracker = new FetchWorkTracker();
        fetchWorkTracker.register(1);
        RecordingFetchTask task = new RecordingFetchTask(inputQueue, 1, fetchWorkTracker);

        Thread runner = Thread.ofVirtual().start(task::run);
        inputQueue.add("input");

        Awaitility.await().atMost(Duration.ofSeconds(5)).until(() -> fetchWorkTracker.pending() == 0);
        assertEquals(List.of("input"), task.fetched);

        runner.interrupt();
    }
}
