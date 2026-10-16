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
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.time.Clock;
import java.time.Duration;
import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
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

    private static final String XROAD_INSTANCE = "INSTANCE";

    private static final String MEMBER_CLASS = "CLASS";

    private static final String MEMBER_CODE = "CODE";

    private static final String SUBSYSTEM_CODE = "SUBSYSTEM";

    private static final String SERVICE_VERSION = "v1";

    private static final String SERVICE_IDENTIFIERS = "\"xroad_instance\":\"INSTANCE\",\"member_class\":\"CLASS\","
            + "\"member_code\":\"CODE\",\"subsystem_code\":\"SUBSYSTEM\",\"object_type\":\"SERVICE\",\"endpoint_list\":[]";

    private static final String SERVICE_WITH_REST_TYPE = "{" + SERVICE_IDENTIFIERS
            + ",\"service_code\":\"restService\",\"service_type\":\"REST\"}";

    private static final String SECOND_SERVICE_WITH_REST_TYPE = "{" + SERVICE_IDENTIFIERS
            + ",\"service_code\":\"anotherRestService\",\"service_type\":\"REST\"}";

    private static final String SERVICE_WITHOUT_SERVICE_TYPE = "{" + SERVICE_IDENTIFIERS
            + ",\"service_code\":\"serviceWithoutType\"}";

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
                        .xRoadInstance(XROAD_INSTANCE).memberClass(MEMBER_CLASS).memberCode(MEMBER_CODE)
                        .subsystemCode(SUBSYSTEM_CODE).serviceCode("testServiceFoo").serviceVersion(SERVICE_VERSION)
                        .build().toProducerMember(),
                XRoadIdentifier.builder()
                        .xRoadInstance(XROAD_INSTANCE).memberClass(MEMBER_CLASS).memberCode(MEMBER_CODE)
                        .subsystemCode(SUBSYSTEM_CODE).serviceCode("testServiceBar").serviceVersion(SERVICE_VERSION)
                        .build().toProducerMember(),
                XRoadIdentifier.builder()
                        .xRoadInstance(XROAD_INSTANCE).memberClass(MEMBER_CLASS).memberCode(MEMBER_CODE)
                        .subsystemCode(SUBSYSTEM_CODE).serviceCode("testServiceBaz").serviceVersion(SERVICE_VERSION)
                        .build().toProducerMember()
        ));
        when(soapClient.listMethods(any(ServiceRequest.class), eq(taskPoolConfiguration.getSecurityServerHost())))
                .thenReturn(response);
        XRoadClient xRoadClient = new XRoadClient(soapClient,
                new ConsumerMember(taskPoolConfiguration.getXroadInstance(), taskPoolConfiguration.getMemberClass(),
                        taskPoolConfiguration.getMemberCode(), taskPoolConfiguration.getSubsystemCode()),
                taskPoolConfiguration.getSecurityServerHost(), new RestTemplate(), Clock.systemDefaultZone());
        BlockingQueue<MemberWithName> listedClients = new LinkedBlockingQueue<>();
        Queue<ProducerMember> wsdlServices = new LinkedBlockingQueue<>();
        Queue<XRoadIdentifier> restServices = new LinkedBlockingQueue<>();
        Queue<XRoadIdentifier> openApiServices = new LinkedBlockingQueue<>();
        FetchWorkTracker fetchWorkTracker = new FetchWorkTracker();
        fetchWorkTracker.register(1);
        ListMethodsTask listMethodsTask = new ListMethodsTask(catalogService, listedClients, wsdlServices,
                restServices, openApiServices, taskPoolConfiguration, fetchWorkTracker, new RestTemplate(),
                Clock.systemDefaultZone());
        ReflectionTestUtils.setField(listMethodsTask, "xroadClient", xRoadClient);
        Semaphore semaphore = new Semaphore(1);
        ReflectionTestUtils.setField(listMethodsTask, "semaphore", semaphore);
        Thread listMethodsRunner = Thread.ofVirtual().start(listMethodsTask::run);
        MemberWithName clientType = new MemberWithName();
        XRoadIdentifier value = XRoadIdentifier.builder()
                .xRoadInstance(XROAD_INSTANCE)
                .memberClass(MEMBER_CLASS)
                .memberCode(MEMBER_CODE)
                .subsystemCode(SUBSYSTEM_CODE)
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
                        .xRoadInstance(XROAD_INSTANCE).memberClass(MEMBER_CLASS).memberCode(MEMBER_CODE)
                        .subsystemCode(SUBSYSTEM_CODE).serviceCode("testServiceFoo").serviceVersion(SERVICE_VERSION)
                        .build().toProducerMember(),
                XRoadIdentifier.builder()
                        .xRoadInstance(XROAD_INSTANCE).memberClass(MEMBER_CLASS).memberCode(MEMBER_CODE)
                        .subsystemCode(SUBSYSTEM_CODE).serviceCode("testServiceBar").serviceVersion(SERVICE_VERSION)
                        .build().toProducerMember(),
                XRoadIdentifier.builder()
                        .xRoadInstance(XROAD_INSTANCE).memberClass(MEMBER_CLASS).memberCode(MEMBER_CODE)
                        .subsystemCode(SUBSYSTEM_CODE).serviceCode("testServiceBaz").serviceVersion(SERVICE_VERSION)
                        .build().toProducerMember()
        ));
        when(soapClient.listMethods(any(ServiceRequest.class), eq(taskPoolConfiguration.getSecurityServerHost())))
                .thenReturn(response);
        XRoadClient xRoadClient = new XRoadClient(soapClient,
                new ConsumerMember(taskPoolConfiguration.getXroadInstance(), taskPoolConfiguration.getMemberClass(),
                        taskPoolConfiguration.getMemberCode(), taskPoolConfiguration.getSubsystemCode()),
                taskPoolConfiguration.getSecurityServerHost(), new RestTemplate(), Clock.systemDefaultZone());
        BlockingQueue<MemberWithName> listedClients = new LinkedBlockingQueue<>();
        Queue<ProducerMember> wsdlServices = new LinkedBlockingQueue<>();
        Queue<XRoadIdentifier> restServices = new LinkedBlockingQueue<>();
        Queue<XRoadIdentifier> openApiServices = new LinkedBlockingQueue<>();
        FetchWorkTracker fetchWorkTracker = new FetchWorkTracker();
        fetchWorkTracker.register(1);
        ListMethodsTask listMethodsTask = new ListMethodsTask(catalogService, listedClients, wsdlServices,
                restServices, openApiServices, taskPoolConfiguration, fetchWorkTracker, new RestTemplate(),
                Clock.systemDefaultZone());
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
                        .xRoadInstance(XROAD_INSTANCE).memberClass(MEMBER_CLASS).memberCode(MEMBER_CODE)
                        .subsystemCode(SUBSYSTEM_CODE).serviceCode("testServiceFoo").serviceVersion(SERVICE_VERSION)
                        .build().toProducerMember()
        ));
        when(soapClient.listMethods(any(ServiceRequest.class), eq(taskPoolConfiguration.getSecurityServerHost())))
                .thenReturn(response);
        XRoadClient xRoadClient = new XRoadClient(soapClient,
                new ConsumerMember(taskPoolConfiguration.getXroadInstance(), taskPoolConfiguration.getMemberClass(),
                        taskPoolConfiguration.getMemberCode(), taskPoolConfiguration.getSubsystemCode()),
                taskPoolConfiguration.getSecurityServerHost(), new RestTemplate(), Clock.systemDefaultZone());
        doThrow(new RuntimeException("boom")).when(catalogService).saveServices(any(), any());
        BlockingQueue<MemberWithName> listedClients = new LinkedBlockingQueue<>();
        Queue<ProducerMember> wsdlServices = new LinkedBlockingQueue<>();
        Queue<XRoadIdentifier> restServices = new LinkedBlockingQueue<>();
        Queue<XRoadIdentifier> openApiServices = new LinkedBlockingQueue<>();
        FetchWorkTracker fetchWorkTracker = new FetchWorkTracker();
        fetchWorkTracker.register(1);
        ListMethodsTask listMethodsTask = new ListMethodsTask(catalogService, listedClients, wsdlServices,
                restServices, openApiServices, taskPoolConfiguration, fetchWorkTracker, new RestTemplate(),
                Clock.systemDefaultZone());
        ReflectionTestUtils.setField(listMethodsTask, "xroadClient", xRoadClient);
        Semaphore semaphore = new Semaphore(1);
        ReflectionTestUtils.setField(listMethodsTask, "semaphore", semaphore);
        Thread listMethodsRunner = Thread.ofVirtual().start(listMethodsTask::run);
        MemberWithName clientType = new MemberWithName();
        XRoadIdentifier value = XRoadIdentifier.builder()
                .xRoadInstance(XROAD_INSTANCE)
                .memberClass(MEMBER_CLASS)
                .memberCode(MEMBER_CODE)
                .subsystemCode(SUBSYSTEM_CODE)
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

    @Test
    public void testListMethodsTaskHandlesRestServiceWithoutServiceType()
            throws InterruptedException, XRd4JException, SOAPException {
        Queue<ProducerMember> wsdlServices = new LinkedBlockingQueue<>();
        Queue<XRoadIdentifier> restServices = new LinkedBlockingQueue<>();
        Queue<XRoadIdentifier> openApiServices = new LinkedBlockingQueue<>();
        FetchWorkTracker fetchWorkTracker = new FetchWorkTracker();

        runListMethodsForSubsystem(listMethodsResponse(SERVICE_WITH_REST_TYPE, SERVICE_WITHOUT_SERVICE_TYPE),
                wsdlServices, restServices, openApiServices, fetchWorkTracker);

        assertEquals(0, wsdlServices.size());
        assertEquals(1, restServices.size());
        assertEquals(1, openApiServices.size());

        // a missing service_type must not throw: the service is treated as a non-REST one and both items stay pending
        Awaitility.await().atMost(Duration.ofSeconds(2)).until(() -> fetchWorkTracker.pending() == 2);
    }

    @Test
    public void testListMethodsTaskReconcilesRegisteredItemsThatCannotBeEnqueued()
            throws InterruptedException, XRd4JException, SOAPException {
        Queue<ProducerMember> wsdlServices = new LinkedBlockingQueue<>();
        // capacity 1: the second add() throws, leaving the rest of the registered batch un-enqueued
        Queue<XRoadIdentifier> restServices = new LinkedBlockingQueue<>(1);
        Queue<XRoadIdentifier> openApiServices = new LinkedBlockingQueue<>();
        FetchWorkTracker fetchWorkTracker = new FetchWorkTracker();

        runListMethodsForSubsystem(listMethodsResponse(SERVICE_WITH_REST_TYPE, SECOND_SERVICE_WITH_REST_TYPE),
                wsdlServices, restServices, openApiServices, fetchWorkTracker);

        assertEquals(1, restServices.size());
        assertEquals(0, openApiServices.size());

        // only the enqueued item may stay pending; the un-enqueued remainder must be reconciled
        Awaitility.await().atMost(Duration.ofSeconds(2)).until(() -> fetchWorkTracker.pending() == 1);
    }

    private void runListMethodsForSubsystem(String listMethodsResponseBody, Queue<ProducerMember> wsdlServices,
            Queue<XRoadIdentifier> restServices, Queue<XRoadIdentifier> openApiServices,
            FetchWorkTracker fetchWorkTracker) throws InterruptedException, XRd4JException, SOAPException {
        RestTemplate restTemplate = mock(RestTemplate.class);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok(listMethodsResponseBody));

        SOAPClient soapClient = mock(SOAPClient.class);
        ServiceResponse<String, java.util.List<ProducerMember>> response = new ServiceResponse<>();
        response.setResponseData(java.util.List.of());
        when(soapClient.listMethods(any(ServiceRequest.class), eq(taskPoolConfiguration.getSecurityServerHost())))
                .thenReturn(response);
        XRoadClient xRoadClient = new XRoadClient(soapClient,
                new ConsumerMember(taskPoolConfiguration.getXroadInstance(), taskPoolConfiguration.getMemberClass(),
                        taskPoolConfiguration.getMemberCode(), taskPoolConfiguration.getSubsystemCode()),
                taskPoolConfiguration.getSecurityServerHost(), restTemplate, Clock.systemDefaultZone());

        BlockingQueue<MemberWithName> listedClients = new LinkedBlockingQueue<>();
        fetchWorkTracker.register(1);
        ListMethodsTask listMethodsTask = new ListMethodsTask(catalogService, listedClients, wsdlServices,
                restServices, openApiServices, taskPoolConfiguration, fetchWorkTracker, restTemplate,
                Clock.systemDefaultZone());
        ReflectionTestUtils.setField(listMethodsTask, "xroadClient", xRoadClient);
        Semaphore semaphore = new Semaphore(1);
        ReflectionTestUtils.setField(listMethodsTask, "semaphore", semaphore);
        Thread listMethodsRunner = Thread.ofVirtual().start(listMethodsTask::run);

        MemberWithName clientType = new MemberWithName();
        XRoadIdentifier value = XRoadIdentifier.builder()
                .xRoadInstance(XROAD_INSTANCE)
                .memberClass(MEMBER_CLASS)
                .memberCode(MEMBER_CODE)
                .subsystemCode(SUBSYSTEM_CODE)
                .build();
        value.setObjectType(ObjectType.SUBSYSTEM);
        clientType.setId(value);
        listedClients.add(clientType);

        Awaitility.await().atMost(Duration.ofSeconds(2)).until(listedClients::isEmpty);

        semaphore.acquire();
        listMethodsRunner.interrupt();
    }

    private static String listMethodsResponse(String... services) {
        return "{\"service\":[" + String.join(",", services) + "]}";
    }

}
