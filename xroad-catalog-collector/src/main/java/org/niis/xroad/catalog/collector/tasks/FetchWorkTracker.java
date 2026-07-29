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
 * Counts fetch-work items that are enqueued but not yet completed; the collection cycle is done when
 * the count reaches zero.
 *
 * <p>{@link #register(int)} MUST be called before the items are enqueued: otherwise a consumer could
 * complete an item before it is registered, letting the count read zero while work still exists.
 */
@Slf4j
@Component
public class FetchWorkTracker {

    private final ReentrantLock lock = new ReentrantLock();

    private final Condition allDone = lock.newCondition();

    private long pendingCount;

    /**
     * Adds {@code n} to the pending count; must be called before the covered items are enqueued.
     *
     * @param n number of work items; zero or negative values are a no-op
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
     * Decrements the pending count, signalling waiters at zero. A call with no pending work logs an
     * error and clamps to zero, so a bookkeeping bug does not kill a worker mid-{@code finally}.
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
     * Waits for the pending count to reach zero; a completion that drives it to zero wakes the wait immediately.
     *
     * @param waitMillis maximum time to wait, in milliseconds
     * @return true if the pending count is (or became) zero, false if {@code waitMillis} elapsed first
     * @throws InterruptedException if interrupted while waiting
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
     * Returns the current pending work count.
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
