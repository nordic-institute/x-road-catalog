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
import org.niis.xroad.catalog.collector.CollectorApplication;
import org.niis.xroad.catalog.collector.configuration.TaskPoolConfiguration;
import org.niis.xroad.catalog.collector.configuration.TestingConfiguration;
import org.niis.xroad.catalog.collector.service.CatalogService;
import org.niis.xroad.catalog.collector.util.XRoadRestServiceIdentifierType;
import org.niis.xroad.catalog.collector.wsimport.ClientType;
import org.niis.xroad.catalog.collector.wsimport.XRoadClientIdentifierType;
import org.niis.xroad.catalog.collector.wsimport.XRoadObjectType;
import org.niis.xroad.catalog.collector.wsimport.XRoadServiceIdentifierType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.URISyntaxException;
import java.time.Duration;
import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@SpringBootTest(classes = { TestingConfiguration.class, CollectorApplication.class,
        TaskPoolConfiguration.class }, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public class ListMethodsTaskTest {

    @MockitoBean
    CatalogService catalogService;

    @Autowired
    private TaskPoolConfiguration taskPoolConfiguration;

    @LocalServerPort
    private int port;

    @Test
    public void testListMethodsTaskSavesServicesAndGetsDescriptors()
            throws URISyntaxException, InterruptedException {
        ReflectionTestUtils.setField(taskPoolConfiguration, "securityServerHost", "http://localhost:" + port);
        ReflectionTestUtils.setField(taskPoolConfiguration, "webservicesEndpoint",
                "http://localhost:" + port + "/metaservices");
        BlockingQueue<ClientType> listedClients = new LinkedBlockingQueue<>();
        Queue<XRoadServiceIdentifierType> wsdlServices = new LinkedBlockingQueue<>();
        Queue<XRoadRestServiceIdentifierType> restServices = new LinkedBlockingQueue<>();
        Queue<XRoadRestServiceIdentifierType> openApiServices = new LinkedBlockingQueue<>();
        ListMethodsTask listMethodsTask = new ListMethodsTask(catalogService, listedClients, wsdlServices,
                restServices, openApiServices, taskPoolConfiguration);
        Semaphore semaphore = new Semaphore(1);
        ReflectionTestUtils.setField(listMethodsTask, "semaphore", semaphore);
        Thread listMethodsRunner = Thread.ofVirtual().start(listMethodsTask::run);
        ClientType clientType = new ClientType();
        XRoadClientIdentifierType value = new XRoadClientIdentifierType();
        value.setXRoadInstance("INSTANCE");
        value.setMemberClass("CLASS");
        value.setMemberCode("CODE");
        value.setSubsystemCode("SUBSYSTEM");
        value.setServiceCode("aService");
        value.setServiceVersion("v1");
        value.setObjectType(XRoadObjectType.SUBSYSTEM);
        clientType.setId(value);
        listedClients.add(clientType);

        Awaitility.await().atMost(Duration.ofSeconds(2)).until(() -> listedClients.isEmpty());

        semaphore.acquire();
        listMethodsRunner.interrupt();

        verify(catalogService, times(1)).saveServices(any(), any());

        assertEquals(3, wsdlServices.size());

        assertEquals(0, restServices.size());

        assertEquals(0, openApiServices.size());
    }

}
