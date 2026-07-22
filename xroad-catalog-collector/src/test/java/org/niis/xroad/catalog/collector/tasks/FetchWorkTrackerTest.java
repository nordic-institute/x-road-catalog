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

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FetchWorkTrackerTest {

    @Test
    void awaitAllDoneReturnsTrueImmediatelyWhenNothingRegistered() throws InterruptedException {
        FetchWorkTracker tracker = new FetchWorkTracker();

        assertTrue(tracker.awaitAllDone(1000));
    }

    @Test
    void awaitAllDoneReturnsTrueWhenRegisteredCountIsFullyCompleted() throws InterruptedException {
        FetchWorkTracker tracker = new FetchWorkTracker();

        tracker.register(3);
        tracker.complete();
        tracker.complete();
        tracker.complete();

        assertTrue(tracker.awaitAllDone(1000));
    }

    @Test
    void completeSignalsWaitingThreadPromptly() throws Exception {
        FetchWorkTracker tracker = new FetchWorkTracker();
        tracker.register(1);

        AtomicBoolean waiterResult = new AtomicBoolean(false);
        AtomicReference<Throwable> waiterFailure = new AtomicReference<>();
        Thread waiter = new Thread(() -> {
            try {
                waiterResult.set(tracker.awaitAllDone(60_000));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                waiterFailure.set(e);
            }
        });
        waiter.start();

        Awaitility.await().atMost(Duration.ofSeconds(5))
                .until(() -> waiter.getState() == Thread.State.TIMED_WAITING);

        tracker.complete();

        waiter.join(5_000);
        assertFalse(waiter.isAlive());
        assertNull(waiterFailure.get());
        assertTrue(waiterResult.get());
    }

    @Test
    void awaitAllDoneReturnsFalseWhenTimeoutElapsesWithPendingWork() throws InterruptedException {
        FetchWorkTracker tracker = new FetchWorkTracker();
        tracker.register(1);

        assertFalse(tracker.awaitAllDone(100));
    }

    @Test
    void completeBelowZeroClampsToZeroAndSubsequentCycleStillWorks() throws InterruptedException {
        FetchWorkTracker tracker = new FetchWorkTracker();

        tracker.complete();

        assertEquals(0, tracker.pending());

        tracker.register(1);
        tracker.complete();

        assertTrue(tracker.awaitAllDone(1000));
        assertEquals(0, tracker.pending());
    }

    @Test
    void interruptWhileWaitingThrowsInterruptedException() throws Exception {
        FetchWorkTracker tracker = new FetchWorkTracker();
        tracker.register(1);

        AtomicBoolean interruptedExceptionThrown = new AtomicBoolean(false);
        AtomicReference<Throwable> waiterFailure = new AtomicReference<>();
        Thread waiter = new Thread(() -> {
            try {
                tracker.awaitAllDone(60_000);
                waiterFailure.set(new AssertionError("expected InterruptedException was not thrown"));
            } catch (InterruptedException expected) {
                interruptedExceptionThrown.set(true);
            }
        });
        waiter.start();

        Awaitility.await().atMost(Duration.ofSeconds(5))
                .until(() -> waiter.getState() == Thread.State.TIMED_WAITING);

        waiter.interrupt();

        waiter.join(5_000);
        assertFalse(waiter.isAlive());
        assertNull(waiterFailure.get());
        assertTrue(interruptedExceptionThrown.get());
    }

    @Test
    void pendingReflectsRegisterAndCompleteArithmetic() {
        FetchWorkTracker tracker = new FetchWorkTracker();

        assertEquals(0, tracker.pending());

        tracker.register(5);
        assertEquals(5, tracker.pending());

        tracker.register(0);
        tracker.register(-3);
        assertEquals(5, tracker.pending());

        tracker.complete();
        tracker.complete();
        assertEquals(3, tracker.pending());
    }

    @Test
    void registerWithNonPositiveCountIsNoOp() {
        FetchWorkTracker tracker = new FetchWorkTracker();

        tracker.register(0);
        tracker.register(-1);

        assertEquals(0, tracker.pending());
    }
}
