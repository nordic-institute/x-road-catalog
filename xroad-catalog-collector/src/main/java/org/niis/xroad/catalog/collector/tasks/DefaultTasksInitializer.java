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

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.catalog.collector.configuration.TaskPoolConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Starts the collector's scheduled cycle and its virtual fetch workers on {@link ApplicationStartedEvent},
 * and tears both down within a bounded grace period on context close.
 *
 * <p>{@link #shutdown()} has to run before the {@code DataSource} closes, so the interrupted cycle can
 * still finalize its run row. It does, because this bean transitively depends on the repositories via
 * {@link CollectionCycleRunner} — that dependency is load-bearing.
 *
 * <p>Accepted limitation: virtual fetch workers already handed off by {@link FetchHandOff} are untracked;
 * shutdown does not wait for them beyond the grace period.
 */
@Slf4j
@Component
public class DefaultTasksInitializer implements ApplicationListener<ApplicationStartedEvent> {

    private static final long SHUTDOWN_GRACE_SECONDS = 25;

    private final ScheduledExecutorService scheduler;
    private final FetchWsdlsTask fetchWsdlsTask;
    private final FetchRestTask fetchRestTask;
    private final FetchOpenApiTask fetchOpenApiTask;
    private final ListMethodsTask listMethodsTask;
    private final CollectionCycleRunner collectionCycleRunner;
    private final TaskPoolConfiguration taskPoolConfiguration;

    private final List<Thread> workers = new CopyOnWriteArrayList<>();

    @Autowired
    public DefaultTasksInitializer(ScheduledExecutorService collectorScheduler, FetchWsdlsTask fetchWsdlsTask,
            FetchRestTask fetchRestTask, FetchOpenApiTask fetchOpenApiTask, ListMethodsTask listMethodsTask,
            CollectionCycleRunner collectionCycleRunner, TaskPoolConfiguration taskPoolConfiguration) {
        this.scheduler = collectorScheduler;
        this.fetchWsdlsTask = fetchWsdlsTask;
        this.fetchRestTask = fetchRestTask;
        this.fetchOpenApiTask = fetchOpenApiTask;
        this.listMethodsTask = listMethodsTask;
        this.collectionCycleRunner = collectionCycleRunner;
        this.taskPoolConfiguration = taskPoolConfiguration;
    }

    @Override
    public void onApplicationEvent(ApplicationStartedEvent ase) {
        workers.add(Thread.ofVirtual().name("fetch-wsdls-worker").start(fetchWsdlsTask));
        workers.add(Thread.ofVirtual().name("fetch-rest-worker").start(fetchRestTask));
        workers.add(Thread.ofVirtual().name("fetch-openapi-worker").start(fetchOpenApiTask));
        workers.add(Thread.ofVirtual().name("list-methods-worker").start(listMethodsTask));

        // The ListClientsTask is the main task that starts the whole process and
        // gathers information that the other tasks will react on to do work
        long collectorInterval = taskPoolConfiguration.getCollectorInterval();
        log.info("Starting up catalog collector with collector interval of {} minutes", collectorInterval);

        scheduler.scheduleWithFixedDelay(collectionCycleRunner::run, 0, collectorInterval, TimeUnit.MINUTES);
    }

    @PreDestroy
    void shutdown() {
        scheduler.shutdownNow();
        workers.forEach(Thread::interrupt);

        Instant deadline = Instant.now().plusSeconds(SHUTDOWN_GRACE_SECONDS);
        try {
            List<String> stillRunning = new ArrayList<>();
            if (!scheduler.awaitTermination(remaining(deadline).toNanos(), TimeUnit.NANOSECONDS)) {
                stillRunning.add("collector-scheduler");
            }
            for (Thread worker : workers) {
                worker.join(remaining(deadline));
                if (worker.isAlive()) {
                    stillRunning.add(worker.getName());
                }
            }
            if (!stillRunning.isEmpty()) {
                log.warn("Collector shutdown grace period of {}s elapsed with threads still running: {}",
                        SHUTDOWN_GRACE_SECONDS, stillRunning);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private Duration remaining(Instant deadline) {
        Duration left = Duration.between(Instant.now(), deadline);
        return left.isNegative() ? Duration.ZERO : left;
    }
}
