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
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.niis.xrd4j.client.SOAPClient;
import org.niis.xrd4j.common.exception.XRd4JException;
import org.niis.xrd4j.common.member.ConsumerMember;
import org.niis.xrd4j.common.member.ObjectType;
import org.niis.xrd4j.common.member.ProducerMember;
import org.niis.xrd4j.common.message.ServiceRequest;
import org.niis.xrd4j.common.message.ServiceResponse;
import org.niis.xroad.catalog.collector.CollectorApplication;
import org.niis.xroad.catalog.collector.configuration.TaskPoolConfiguration;
import org.niis.xroad.catalog.collector.configuration.TestingConfiguration;
import org.niis.xroad.catalog.collector.service.CatalogService;
import org.niis.xroad.catalog.collector.util.MemberWithName;
import org.niis.xroad.catalog.collector.util.XRoadClient;
import org.niis.xroad.catalog.collector.util.XRoadIdentifier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = { TestingConfiguration.class, CollectorApplication.class,
        TaskPoolConfiguration.class }, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles({"test", "general-testdata"})
public class ListMethodsTaskTest {

    @MockitoBean
    CatalogService catalogService;

    @Autowired
    private TaskPoolConfiguration taskPoolConfiguration;

    @Test
    public void testListMethodsTaskSavesServicesAndGetsDescriptors()
            throws InterruptedException, XRd4JException, SOAPException {
        SOAPClient soapClient = mock(SOAPClient.class);
        ServiceResponse<String, java.util.List<ProducerMember>> response = new ServiceResponse<>();
        response.setResponseData(java.util.List.of(
                XRoadIdentifier.builder()
                        .xRoadInstance("INSTANCE").memberClass("CLASS").memberCode("CODE")
                        .subsystemCode("SUBSYSTEM").serviceCode("testServiceFoo").serviceVersion("v1")
                        .build().toProducerMember(),
                XRoadIdentifier.builder()
                        .xRoadInstance("INSTANCE").memberClass("CLASS").memberCode("CODE")
                        .subsystemCode("SUBSYSTEM").serviceCode("testServiceBar").serviceVersion("v1")
                        .build().toProducerMember(),
                XRoadIdentifier.builder()
                        .xRoadInstance("INSTANCE").memberClass("CLASS").memberCode("CODE")
                        .subsystemCode("SUBSYSTEM").serviceCode("testServiceBaz").serviceVersion("v1")
                        .build().toProducerMember()
        ));
        when(soapClient.listMethods(any(ServiceRequest.class), eq(taskPoolConfiguration.getSecurityServerHost())))
                .thenReturn(response);
        XRoadClient xRoadClient = new XRoadClient(soapClient,
                new ConsumerMember(taskPoolConfiguration.getXroadInstance(), taskPoolConfiguration.getMemberClass(),
                        taskPoolConfiguration.getMemberCode(), taskPoolConfiguration.getSubsystemCode()),
                taskPoolConfiguration.getSecurityServerHost(), new RestTemplate());
        BlockingQueue<MemberWithName> listedClients = new LinkedBlockingQueue<>();
        Queue<ProducerMember> wsdlServices = new LinkedBlockingQueue<>();
        Queue<XRoadIdentifier> restServices = new LinkedBlockingQueue<>();
        Queue<XRoadIdentifier> openApiServices = new LinkedBlockingQueue<>();
        FetchWorkTracker fetchWorkTracker = new FetchWorkTracker();
        fetchWorkTracker.register(1);
        ListMethodsTask listMethodsTask = new ListMethodsTask(catalogService, listedClients, wsdlServices,
                restServices, openApiServices, taskPoolConfiguration, fetchWorkTracker, new RestTemplate());
        ReflectionTestUtils.setField(listMethodsTask, "xroadClient", xRoadClient);
        Semaphore semaphore = new Semaphore(1);
        ReflectionTestUtils.setField(listMethodsTask, "semaphore", semaphore);
        Thread listMethodsRunner = Thread.ofVirtual().start(listMethodsTask::run);
        MemberWithName clientType = new MemberWithName();
        XRoadIdentifier value = XRoadIdentifier.builder()
                .xRoadInstance("INSTANCE")
                .memberClass("CLASS")
                .memberCode("CODE")
                .subsystemCode("SUBSYSTEM")
                .build();
        value.setObjectType(ObjectType.SUBSYSTEM);
        clientType.setId(value);
        listedClients.add(clientType);

        Awaitility.await().atMost(Duration.ofSeconds(2)).until(listedClients::isEmpty);

        semaphore.acquire();
        listMethodsRunner.interrupt();

        verify(catalogService, times(1)).saveServices(any(), any());
        verify(soapClient, times(1)).listMethods(any(ServiceRequest.class), eq(taskPoolConfiguration.getSecurityServerHost()));

        assertEquals(3, wsdlServices.size());

        assertEquals(0, restServices.size());

        assertEquals(0, openApiServices.size());

        // -1 for the worker's own item, +1 per enqueued SOAP service; the empty REST registration is a no-op.
        Awaitility.await().atMost(Duration.ofSeconds(2)).until(() -> fetchWorkTracker.pending() == 3);
    }

    @Test
    public void testListMethodsTaskIgnoresSubsystem()
            throws InterruptedException, XRd4JException, SOAPException {
        SOAPClient soapClient = mock(SOAPClient.class);
        ServiceResponse<String, java.util.List<ProducerMember>> response = new ServiceResponse<>();
        response.setResponseData(java.util.List.of(
                XRoadIdentifier.builder()
                        .xRoadInstance("INSTANCE").memberClass("CLASS").memberCode("CODE")
                        .subsystemCode("SUBSYSTEM").serviceCode("testServiceFoo").serviceVersion("v1")
                        .build().toProducerMember(),
                XRoadIdentifier.builder()
                        .xRoadInstance("INSTANCE").memberClass("CLASS").memberCode("CODE")
                        .subsystemCode("SUBSYSTEM").serviceCode("testServiceBar").serviceVersion("v1")
                        .build().toProducerMember(),
                XRoadIdentifier.builder()
                        .xRoadInstance("INSTANCE").memberClass("CLASS").memberCode("CODE")
                        .subsystemCode("SUBSYSTEM").serviceCode("testServiceBaz").serviceVersion("v1")
                        .build().toProducerMember()
        ));
        when(soapClient.listMethods(any(ServiceRequest.class), eq(taskPoolConfiguration.getSecurityServerHost())))
                .thenReturn(response);
        XRoadClient xRoadClient = new XRoadClient(soapClient,
                new ConsumerMember(taskPoolConfiguration.getXroadInstance(), taskPoolConfiguration.getMemberClass(),
                        taskPoolConfiguration.getMemberCode(), taskPoolConfiguration.getSubsystemCode()),
                taskPoolConfiguration.getSecurityServerHost(), new RestTemplate());
        BlockingQueue<MemberWithName> listedClients = new LinkedBlockingQueue<>();
        Queue<ProducerMember> wsdlServices = new LinkedBlockingQueue<>();
        Queue<XRoadIdentifier> restServices = new LinkedBlockingQueue<>();
        Queue<XRoadIdentifier> openApiServices = new LinkedBlockingQueue<>();
        FetchWorkTracker fetchWorkTracker = new FetchWorkTracker();
        fetchWorkTracker.register(1);
        ListMethodsTask listMethodsTask = new ListMethodsTask(catalogService, listedClients, wsdlServices,
                restServices, openApiServices, taskPoolConfiguration, fetchWorkTracker, new RestTemplate());
        ReflectionTestUtils.setField(listMethodsTask, "xroadClient", xRoadClient);
        Semaphore semaphore = new Semaphore(1);
        ReflectionTestUtils.setField(listMethodsTask, "semaphore", semaphore);
        Thread listMethodsRunner = Thread.ofVirtual().start(listMethodsTask::run);
        MemberWithName clientType = new MemberWithName();
        XRoadIdentifier value = XRoadIdentifier.builder()
                .xRoadInstance("DEV")
                .memberClass("COM")
                .memberCode("1234")
                .subsystemCode("Test")
                .build();
        value.setObjectType(ObjectType.SUBSYSTEM);
        clientType.setId(value);
        listedClients.add(clientType);

        Awaitility.await().atMost(Duration.ofSeconds(2)).until(listedClients::isEmpty);

        semaphore.acquire();
        listMethodsRunner.interrupt();

        verify(catalogService, times(0)).saveServices(any(), any());
        verify(soapClient, times(0)).listMethods(any(ServiceRequest.class), eq(taskPoolConfiguration.getSecurityServerHost()));

        assertEquals(0, wsdlServices.size());

        assertEquals(0, restServices.size());

        assertEquals(0, openApiServices.size());

        // ignored subsystem returns early: the worker's own registered item is completed, nothing enqueued.
        Awaitility.await().atMost(Duration.ofSeconds(2)).until(() -> fetchWorkTracker.pending() == 0);
    }

    @Test
    public void testListMethodsTaskPendingReturnsToZeroWhenSaveServicesThrows()
            throws InterruptedException, XRd4JException, SOAPException {
        SOAPClient soapClient = mock(SOAPClient.class);
        ServiceResponse<String, java.util.List<ProducerMember>> response = new ServiceResponse<>();
        response.setResponseData(java.util.List.of(
                XRoadIdentifier.builder()
                        .xRoadInstance("INSTANCE").memberClass("CLASS").memberCode("CODE")
                        .subsystemCode("SUBSYSTEM").serviceCode("testServiceFoo").serviceVersion("v1")
                        .build().toProducerMember()
        ));
        when(soapClient.listMethods(any(ServiceRequest.class), eq(taskPoolConfiguration.getSecurityServerHost())))
                .thenReturn(response);
        XRoadClient xRoadClient = new XRoadClient(soapClient,
                new ConsumerMember(taskPoolConfiguration.getXroadInstance(), taskPoolConfiguration.getMemberClass(),
                        taskPoolConfiguration.getMemberCode(), taskPoolConfiguration.getSubsystemCode()),
                taskPoolConfiguration.getSecurityServerHost(), new RestTemplate());
        doThrow(new RuntimeException("boom")).when(catalogService).saveServices(any(), any());
        BlockingQueue<MemberWithName> listedClients = new LinkedBlockingQueue<>();
        Queue<ProducerMember> wsdlServices = new LinkedBlockingQueue<>();
        Queue<XRoadIdentifier> restServices = new LinkedBlockingQueue<>();
        Queue<XRoadIdentifier> openApiServices = new LinkedBlockingQueue<>();
        FetchWorkTracker fetchWorkTracker = new FetchWorkTracker();
        fetchWorkTracker.register(1);
        ListMethodsTask listMethodsTask = new ListMethodsTask(catalogService, listedClients, wsdlServices,
                restServices, openApiServices, taskPoolConfiguration, fetchWorkTracker, new RestTemplate());
        ReflectionTestUtils.setField(listMethodsTask, "xroadClient", xRoadClient);
        Semaphore semaphore = new Semaphore(1);
        ReflectionTestUtils.setField(listMethodsTask, "semaphore", semaphore);
        Thread listMethodsRunner = Thread.ofVirtual().start(listMethodsTask::run);
        MemberWithName clientType = new MemberWithName();
        XRoadIdentifier value = XRoadIdentifier.builder()
                .xRoadInstance("INSTANCE")
                .memberClass("CLASS")
                .memberCode("CODE")
                .subsystemCode("SUBSYSTEM")
                .build();
        value.setObjectType(ObjectType.SUBSYSTEM);
        clientType.setId(value);
        listedClients.add(clientType);

        Awaitility.await().atMost(Duration.ofSeconds(2)).until(listedClients::isEmpty);

        semaphore.acquire();
        listMethodsRunner.interrupt();

        assertEquals(0, wsdlServices.size());
        assertEquals(0, restServices.size());
        assertEquals(0, openApiServices.size());

        // saveServices throws before either queue registration runs; only the worker's own item completes.
        Awaitility.await().atMost(Duration.ofSeconds(2)).until(() -> fetchWorkTracker.pending() == 0);
    }

}
