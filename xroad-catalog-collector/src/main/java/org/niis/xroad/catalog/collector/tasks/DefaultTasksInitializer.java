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

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.catalog.collector.configuration.TaskPoolConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;


@Slf4j
@Component
public class DefaultTasksInitializer implements ApplicationListener<ApplicationStartedEvent> {
    @Autowired
    private TaskPoolConfiguration taskPoolConfiguration;
    @Autowired
    private FetchWsdlsTask fetchWsdlsTask;
    @Autowired
    private FetchRestTask fetchRestTask;
    @Autowired
    private FetchOpenApiTask fetchOpenApiTask;
    @Autowired
    private ListMethodsTask listMethodsTask;
    @Autowired private ListClientsTask listClientsTask;
    @Autowired
    private RecomputeDenormalizedColumnsTask recomputeDenormalizedColumnsTask;


    @Override
    @SneakyThrows
    public void onApplicationEvent(ApplicationStartedEvent ase) {
        final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

        Thread.ofVirtual().start(fetchWsdlsTask);
        Thread.ofVirtual().start(fetchRestTask);
        Thread.ofVirtual().start(fetchOpenApiTask);
        Thread.ofVirtual().start(listMethodsTask);

        // The ListClientsTask is the main task that starts the whole process and
        // gathers information that the other tasks will react on to do work
        long collectorInterval = taskPoolConfiguration.getCollectorInterval();
        log.info("Starting up catalog collector with collector interval of {} minutes", collectorInterval);

        scheduler.scheduleWithFixedDelay(() -> {
            listClientsTask.run();
            recomputeDenormalizedColumnsTask.run();
        }, 0, collectorInterval, TimeUnit.MINUTES);
    }
}
