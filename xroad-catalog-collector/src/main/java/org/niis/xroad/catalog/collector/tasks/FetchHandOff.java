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

import java.util.concurrent.Semaphore;

final class FetchHandOff {

    private FetchHandOff() {
    }

    /**
     * Hands a dequeued, already-registered work item to a worker started on a virtual thread. The worker
     * owns the permit and the tracked item once it is started (its own {@code finally} does the release
     * and complete); until then this method owns both, so an interrupt in {@code acquire()} or a failed
     * start cannot leak a registered item and leave the collection cycle waiting for it forever.
     */
    @SuppressWarnings("PMD.AvoidCatchingThrowable")
    static void handOff(final Semaphore semaphore, final FetchWorkTracker tracker, final Runnable worker)
            throws InterruptedException {
        try {
            semaphore.acquire();
        } catch (InterruptedException e) {
            // The item is already dequeued and registered; nobody else will complete it
            tracker.complete();
            throw e;
        }
        try {
            Thread.ofVirtual().start(worker);
        } catch (Throwable t) {
            // Deliberately catches Throwable: if the start fails with anything, including an Error,
            // the worker never started and cannot release the permit or complete the tracked item,
            // so this must clean up and rethrow or the collection cycle would wait forever
            semaphore.release();
            tracker.complete();
            throw t;
        }
    }
}
