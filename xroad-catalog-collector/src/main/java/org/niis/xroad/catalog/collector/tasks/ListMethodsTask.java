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

import jakarta.xml.soap.SOAPException;
import lombok.extern.slf4j.Slf4j;
import org.niis.xrd4j.common.exception.XRd4JException;
import org.niis.xrd4j.common.member.ConsumerMember;
import org.niis.xrd4j.common.member.ProducerMember;
import org.niis.xroad.catalog.collector.configuration.TaskPoolConfiguration;
import org.niis.xroad.catalog.collector.service.CatalogService;
import org.niis.xroad.catalog.collector.util.IdentifierUtil;
import org.niis.xroad.catalog.collector.util.MemberWithName;
import org.niis.xroad.catalog.collector.util.MethodListUtil;
import org.niis.xroad.catalog.collector.util.XRoadClient;
import org.niis.xroad.catalog.collector.util.XRoadIdentifier;
import org.niis.xroad.catalog.persistence.entity.Member;
import org.niis.xroad.catalog.persistence.entity.Service;
import org.niis.xroad.catalog.persistence.entity.Subsystem;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Semaphore;

@Slf4j
@Component
public class ListMethodsTask implements Runnable {

    private static final String SERVICE_TYPE_REST = "REST";

    private final String xroadSecurityServerHost;

    private final ConsumerMember consumerMember;

    private final CatalogService catalogService;

    private final TaskPoolConfiguration taskPoolConfiguration;

    private final XRoadClient xroadClient;

    private final Semaphore semaphore;

    private final BlockingQueue<MemberWithName> clientsQueue;

    private final Queue<ProducerMember> wsdlQueue;

    private final Queue<XRoadIdentifier> openApiQueue;

    private final Queue<XRoadIdentifier> restQueue;

    public ListMethodsTask(final CatalogService  catalogService, final BlockingQueue<MemberWithName> listMethodsQueue,
                           final Queue<ProducerMember> wsdlServicesQueue, final Queue<XRoadIdentifier> restServicesQueue,
                           final Queue<XRoadIdentifier> openApiServicesQueue, final TaskPoolConfiguration taskPoolConfiguration)
            throws XRd4JException, SOAPException {
        this.catalogService = catalogService;

        this.clientsQueue = listMethodsQueue;
        this.wsdlQueue = wsdlServicesQueue;
        this.openApiQueue = openApiServicesQueue;
        this.restQueue = restServicesQueue;

        this.taskPoolConfiguration = taskPoolConfiguration;
        this.xroadSecurityServerHost = taskPoolConfiguration.getSecurityServerHost();
        this.consumerMember = new ConsumerMember(taskPoolConfiguration.getXroadInstance(),
                taskPoolConfiguration.getMemberClass(), taskPoolConfiguration.getMemberCode(),
                taskPoolConfiguration.getSubsystemCode());

        String webservicesEndpoint = taskPoolConfiguration.getWebservicesEndpoint();

        this.semaphore = new Semaphore(taskPoolConfiguration.getListMethodsPoolSize());

        this.xroadClient = new XRoadClient(consumerMember, webservicesEndpoint);
    }

    public void run() {
        log.info("Starting ListMethodsTask with pool size {}", taskPoolConfiguration.getListMethodsPoolSize());
        try {
            while (true) {
                log.debug("Polling for clients ... ");

                // take() blocks until an element becomes available or it gets interrupted
                MemberWithName client = clientsQueue.take();
                semaphore.acquire();
                Thread.ofVirtual().start(() -> saveSubsystemsAndServices(client));
            }
        } catch (InterruptedException e) {
            log.warn("Interrupted while waiting for clients, stopping ListMethodsTask", e);
            Thread.currentThread().interrupt();
        }
    }

    private void saveSubsystemsAndServices(final MemberWithName client) {
        try {
            if (shouldBeIgnored(client)) {
                log.info("Subsystem {} marked as ignored in configuration, skipping services", IdentifierUtil.toString(client));
                return;
            }

            Subsystem subsystem = new Subsystem(
                    new Member(client.getId().getXRoadInstance(), client.getId().getMemberClass(),
                            client.getId().getMemberCode(), client.getName()),
                    client.getId().getSubsystemCode());

            log.debug("Handling subsystem {} ", subsystem);

            List<XRoadIdentifier> restServices = MethodListUtil.methodListFromResponse(client.getId(),
                    xroadSecurityServerHost, consumerMember, catalogService);
            log.info("Received {} REST methods for client {} ", restServices.size(),
                    IdentifierUtil.toString(client));

            List<ProducerMember> soapServices = xroadClient.getMethods(client.getId(), catalogService);
            log.info("Received {} SOAP methods for client {} ", soapServices.size(),
                    IdentifierUtil.toString(client));

            List<Service> services = new ArrayList<>();
            for (XRoadIdentifier service : restServices) {
                services.add(new Service(subsystem, service.getServiceCode(), service.getServiceVersion()));
            }
            for (ProducerMember service : soapServices) {
                services.add(new Service(subsystem, service.getServiceCode(), service.getServiceVersion()));
            }

            catalogService.saveServices(subsystem.createKey(), services);

            this.wsdlQueue.addAll(soapServices);

            for (XRoadIdentifier service : restServices) {
                if (service.getServiceType().equalsIgnoreCase(SERVICE_TYPE_REST)) {
                    this.restQueue.add(service);
                } else {
                    this.openApiQueue.add(service);
                }
            }

            log.debug("Subsystem {} handled", subsystem);
        } catch (Exception e) {
            log.error("Error while handling client {}", IdentifierUtil.toString(client), e);
        } finally {
            semaphore.release();
        }
    }

    private boolean shouldBeIgnored(final MemberWithName subsystem) {
        String identifier = String.format("%s:%s:%s:%s",
                subsystem.getId().getXRoadInstance(),
                subsystem.getId().getMemberClass(),
                subsystem.getId().getMemberCode(),
                subsystem.getId().getSubsystemCode());
        return taskPoolConfiguration.getIgnoredSubsystemIds().contains(identifier);
    }
}
