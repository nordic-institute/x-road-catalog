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
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Tracks the number of fetch-work items that have been enqueued but not yet completed, so that collection
 * completion can be detected via an explicit counter reaching zero instead of by polling task idleness.
 *
 * <p>Discipline: {@link #register(int)} MUST be called BEFORE the corresponding items are enqueued for
 * processing, not after. If the increment happened after enqueueing, a consumer could dequeue and complete
 * the item before the producer registers it, letting the pending count observe zero while work still exists.
 */
@Slf4j
@Component
public class FetchWorkTracker {

    private final ReentrantLock lock = new ReentrantLock();

    private final Condition allDone = lock.newCondition();

    private long pendingCount;

    /**
     * Adds {@code n} to the pending work count. Must be called before the {@code n} items it covers are
     * enqueued; see the class javadoc for the reason.
     *
     * @param n number of work items to register; values less than or equal to zero are a no-op
     */
    public void register(final int n) {
        if (n <= 0) {
            return;
        }
        lock.lock();
        try {
            pendingCount += n;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Marks one previously registered work item as complete, decrementing the pending count. Signals all
     * waiters once the count reaches zero. If the count is already zero, logs an error and clamps to zero
     * instead of going negative or throwing, so a bookkeeping bug does not kill a worker mid-{@code finally}.
     */
    public void complete() {
        lock.lock();
        try {
            if (pendingCount <= 0) {
                log.error("FetchWorkTracker.complete() called with no pending work; clamping to zero");
                pendingCount = 0;
                return;
            }
            pendingCount--;
            if (pendingCount == 0) {
                allDone.signalAll();
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Waits for the pending count to reach zero.
     *
     * @param waitMillis maximum time to wait, in milliseconds; this is a reporting tick, not a polling
     *                    interval, since a completion that drives pending to zero wakes the wait immediately
     * @return true if the pending count is (or became) zero, false if {@code waitMillis} elapsed first
     * @throws InterruptedException if the current thread is interrupted while waiting
     */
    public boolean awaitAllDone(final long waitMillis) throws InterruptedException {
        lock.lock();
        try {
            long remainingNanos = TimeUnit.MILLISECONDS.toNanos(waitMillis);
            while (pendingCount > 0) {
                if (remainingNanos <= 0) {
                    return false;
                }
                remainingNanos = allDone.awaitNanos(remainingNanos);
            }
            return true;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Returns the current pending work count, for progress reporting.
     *
     * @return the current pending count
     */
    public long pending() {
        lock.lock();
        try {
            return pendingCount;
        } finally {
            lock.unlock();
        }
    }
}
