/**
 *
 *  The MIT License
 *
 *  Copyright (c) 2023- Nordic Institute for Interoperability Solutions (NIIS)
 *  Copyright (c) 2016-2023 Finnish Digital Agency
 *
 *  Permission is hereby granted, free of charge, to any person obtaining a copy
 *  of this software and associated documentation files (the "Software"), to deal
 *  in the Software without restriction, including without limitation the rights
 *  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  copies of the Software, and to permit persons to whom the Software is
 *  furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in
 *  all copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 *  THE SOFTWARE.
 *
 */
package fi.vrk.xroad.catalog.collector.tasks;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Semaphore;

import org.springframework.context.ApplicationContext;

import fi.vrk.xroad.catalog.collector.configuration.TaskPoolConfiguration;
import fi.vrk.xroad.catalog.collector.util.ClientTypeUtil;
import fi.vrk.xroad.catalog.collector.util.MethodListUtil;
import fi.vrk.xroad.catalog.collector.util.XRoadClient;
import fi.vrk.xroad.catalog.collector.util.XRoadRestServiceIdentifierType;
import fi.vrk.xroad.catalog.collector.wsimport.ClientType;
import fi.vrk.xroad.catalog.collector.wsimport.XRoadServiceIdentifierType;
import fi.vrk.xroad.catalog.persistence.CatalogService;
import fi.vrk.xroad.catalog.persistence.entity.Member;
import fi.vrk.xroad.catalog.persistence.entity.Service;
import fi.vrk.xroad.catalog.persistence.entity.Subsystem;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ListMethodsTask implements Runnable {

    private static final String SERVICE_TYPE_REST = "REST";

    private String xroadSecurityServerHost;

    private String xroadInstance;

    private String memberCode;

    private String memberClass;

    private String subsystemCode;

    private String webservicesEndpoint;

    private final CatalogService catalogService;

    private final TaskPoolConfiguration taskPoolConfiguration;

    private final XRoadClient xroadClient;

    private final Semaphore semaphore;

    private final BlockingQueue<ClientType> clientsQueue;

    private final Queue<XRoadServiceIdentifierType> wsdlQueue;

    private final Queue<XRoadRestServiceIdentifierType> openApiQueue;

    private final Queue<XRoadRestServiceIdentifierType> restQueue;

    public ListMethodsTask(final ApplicationContext applicationContext, final BlockingQueue<ClientType> clientsQueue,
            final Queue<XRoadServiceIdentifierType> wsdlQueue, final Queue<XRoadRestServiceIdentifierType> restQueue,
            final Queue<XRoadRestServiceIdentifierType> openApiQueue) throws URISyntaxException {
        this.catalogService = applicationContext.getBean(CatalogService.class);

        this.clientsQueue = clientsQueue;
        this.wsdlQueue = wsdlQueue;
        this.openApiQueue = openApiQueue;
        this.restQueue = restQueue;

        this.taskPoolConfiguration = applicationContext.getBean(TaskPoolConfiguration.class);
        this.xroadSecurityServerHost = taskPoolConfiguration.getSecurityServerHost();
        this.xroadInstance = taskPoolConfiguration.getXroadInstance();
        this.memberCode = taskPoolConfiguration.getMemberCode();
        this.memberClass = taskPoolConfiguration.getMemberClass();
        this.subsystemCode = taskPoolConfiguration.getSubsystemCode();
        this.webservicesEndpoint = taskPoolConfiguration.getWebservicesEndpoint();

        this.semaphore = new Semaphore(taskPoolConfiguration.getListMethodsPoolSize());

        this.xroadClient = new XRoadClient(
                ClientTypeUtil.toSubsystem(xroadInstance, memberClass, memberCode, subsystemCode),
                new URI(webservicesEndpoint));
    }

    public void run() {
        log.info("Starting ListMethodsTask with pool size {}", taskPoolConfiguration.getListMethodsPoolSize());
        try {
            while (true) {
                log.debug("Polling for clients ... ");

                // take() blocks until an element becomes available or it gets interrupted
                ClientType client = clientsQueue.take();
                semaphore.acquire();
                Thread.ofVirtual().start(() -> saveSubsystemsAndServices(client));
            }
        } catch (InterruptedException e) {
            log.warn("Interrupted while waiting for clients, stopping ListMethodsTask", e);
            Thread.currentThread().interrupt();
        }
    }

    private void saveSubsystemsAndServices(final ClientType clientType) {
        try {
            Subsystem subsystem = new Subsystem(
                    new Member(clientType.getId().getXRoadInstance(), clientType.getId().getMemberClass(),
                            clientType.getId().getMemberCode(), clientType.getName()),
                    clientType.getId().getSubsystemCode());

            log.debug("Handling subsystem {} ", subsystem);

            List<XRoadRestServiceIdentifierType> restServices = MethodListUtil.methodListFromResponse(clientType,
                    xroadSecurityServerHost, xroadInstance, memberClass, memberCode, subsystemCode, catalogService);
            log.info("Received {} REST methods for client {} ", restServices.size(),
                    ClientTypeUtil.toString(clientType));

            List<XRoadServiceIdentifierType> soapServices = xroadClient.getMethods(clientType.getId(), catalogService);
            log.info("Received {} SOAP methods for client {} ", soapServices.size(),
                    ClientTypeUtil.toString(clientType));

            List<Service> services = new ArrayList<>();
            for (XRoadRestServiceIdentifierType service : restServices) {
                services.add(new Service(subsystem, service.getServiceCode(), service.getServiceVersion()));
            }
            for (XRoadServiceIdentifierType service : soapServices) {
                services.add(new Service(subsystem, service.getServiceCode(), service.getServiceVersion()));
            }

            catalogService.saveServices(subsystem.createKey(), services);

            this.wsdlQueue.addAll(soapServices);

            for (XRoadRestServiceIdentifierType service : restServices) {
                if (service.getServiceType().equalsIgnoreCase(SERVICE_TYPE_REST)) {
                    this.restQueue.add(service);
                } else {
                    this.openApiQueue.add(service);
                }
            }

            log.debug("Subsystem {} handled", subsystem);
        } catch (Exception e) {
            log.error("Error while handling client {}", ClientTypeUtil.toString(clientType), e);
        } finally {
            semaphore.release();
        }
    }
}
