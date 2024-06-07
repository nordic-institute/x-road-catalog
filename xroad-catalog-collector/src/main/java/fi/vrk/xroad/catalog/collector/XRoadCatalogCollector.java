/**
 * The MIT License
 *
 * Copyright (c) 2023- Nordic Institute for Interoperability Solutions (NIIS)
 * Copyright (c) 2016-2023 Finnish Digital Agency
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package fi.vrk.xroad.catalog.collector;

import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;

import fi.vrk.xroad.catalog.collector.configuration.TaskPoolConfiguration;
import fi.vrk.xroad.catalog.collector.tasks.FetchCompaniesTask;
import fi.vrk.xroad.catalog.collector.tasks.FetchOpenApiTask;
import fi.vrk.xroad.catalog.collector.tasks.FetchOrganizationsTask;
import fi.vrk.xroad.catalog.collector.tasks.FetchRestTask;
import fi.vrk.xroad.catalog.collector.tasks.FetchWsdlsTask;
import fi.vrk.xroad.catalog.collector.tasks.ListClientsTask;
import fi.vrk.xroad.catalog.collector.tasks.ListMethodsTask;
import fi.vrk.xroad.catalog.collector.util.XRoadRestServiceIdentifierType;
import fi.vrk.xroad.catalog.collector.wsimport.ClientType;
import fi.vrk.xroad.catalog.collector.wsimport.XRoadServiceIdentifierType;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@SpringBootApplication
public class XRoadCatalogCollector {

    private static final String FI_PROFILE = "fi";

    public static void main(String[] args) throws MalformedURLException, URISyntaxException {

        ApplicationContext context = SpringApplication.run(XRoadCatalogCollector.class, args);

        final Environment env = context.getEnvironment();

        final String keystore = env.getProperty("xroad-catalog.ssl-keystore");
        final String keystorePw = env.getProperty("xroad-catalog.ssl-keystore-password");

        if (keystore != null && !keystore.isEmpty() && keystorePw != null) {
            if (!Path.of(keystore).toFile().exists()) {
                log.warn("Keystore file at {} is not accessible or does not exist, not using keystore", keystore);
            } else {
                log.info("Using keystore at {}", keystore);
                System.setProperty("javax.net.ssl.keyStore", keystore);
                System.setProperty("javax.net.ssl.keyStorePassword", keystorePw);
            }
        }

        final boolean isFIProfile = Arrays.stream(env.getActiveProfiles())
                .anyMatch(str -> str.equalsIgnoreCase(FI_PROFILE));

        final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
        final BlockingQueue<ClientType> listMethodsQueue = new LinkedBlockingQueue<>();
        final BlockingQueue<ClientType> fetchCompaniesQueue = isFIProfile ? new LinkedBlockingQueue<>() : null;
        final BlockingQueue<ClientType> fetchOrganizationsQueue = isFIProfile ? new LinkedBlockingQueue<>() : null;
        final BlockingQueue<XRoadServiceIdentifierType> fetchWsdlsQueue = new LinkedBlockingQueue<>();
        final BlockingQueue<XRoadRestServiceIdentifierType> fetchRestQueue = new LinkedBlockingQueue<>();
        final BlockingQueue<XRoadRestServiceIdentifierType> fetchOpenApiQueue = new LinkedBlockingQueue<>();

        if (isFIProfile) {
            log.info("FI profile detected, starting up organizations and companies fetchers");
            final FetchCompaniesTask fetchCompaniesTask = new FetchCompaniesTask(context, fetchCompaniesQueue);
            Thread.ofVirtual().start(fetchCompaniesTask::run);

            final FetchOrganizationsTask fetchOrganizationsTask = new FetchOrganizationsTask(context,
                    fetchOrganizationsQueue);
            Thread.ofVirtual().start(fetchOrganizationsTask::run);
        }

        final FetchWsdlsTask fetchWsdlsTask = new FetchWsdlsTask(context, fetchWsdlsQueue);
        Thread.ofVirtual().start(fetchWsdlsTask::run);

        final FetchRestTask fetchRestTask = new FetchRestTask(context, fetchRestQueue);
        Thread.ofVirtual().start(fetchRestTask::run);

        final FetchOpenApiTask fetchOpenApiTask = new FetchOpenApiTask(context, fetchOpenApiQueue);
        Thread.ofVirtual().start(fetchOpenApiTask::run);

        final ListMethodsTask listMethodsTask = new ListMethodsTask(context, listMethodsQueue, fetchWsdlsQueue,
                fetchRestQueue,
                fetchOpenApiQueue);
        Thread.ofVirtual().start(listMethodsTask::run);

        // The ListClientsTask is the main task that starts the whole process and
        // gathers information that the other tasks will react on to do work
        final ListClientsTask listClientsTask = new ListClientsTask(context, listMethodsQueue, fetchCompaniesQueue,
                fetchOrganizationsQueue);

        long collectorInterval = context.getBean(TaskPoolConfiguration.class).getCollectorInterval();
        log.info("Starting up catalog collector with collector interval of {}", collectorInterval);

        scheduler.scheduleWithFixedDelay(listClientsTask::run, 0, collectorInterval, TimeUnit.MINUTES);

    }

}
