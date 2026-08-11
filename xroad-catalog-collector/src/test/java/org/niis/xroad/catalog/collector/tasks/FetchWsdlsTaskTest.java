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
import org.niis.xroad.catalog.collector.util.GetWsdlRequest;
import org.niis.xroad.catalog.collector.util.GetWsdlRequestSerializer;
import org.niis.xroad.catalog.collector.util.GetWsdlResponseDeserializer;
import org.niis.xroad.catalog.collector.util.XRoadClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = {TestingConfiguration.class, CollectorApplication.class},
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles({"test", "general-testdata"})
public class FetchWsdlsTaskTest {

    @MockitoBean
    CatalogService catalogService;

    @Autowired
    private TaskPoolConfiguration taskPoolConfiguration;

    @Test
    public void testFetchWsdl() throws InterruptedException, XRd4JException, SOAPException {
        final String wsdlContent = "wsdlData";
        SOAPClient soapClient = mock(SOAPClient.class);
        ServiceResponse<GetWsdlRequest, String> response = new ServiceResponse<>();
        response.setResponseData(wsdlContent);
        when(soapClient.send(any(ServiceRequest.class), eq(taskPoolConfiguration.getSecurityServerHost()),
                any(GetWsdlRequestSerializer.class), any(GetWsdlResponseDeserializer.class)))
                .thenReturn(response);
        XRoadClient xRoadClient = new XRoadClient(soapClient,
                new ConsumerMember(taskPoolConfiguration.getXroadInstance(), taskPoolConfiguration.getMemberClass(),
                        taskPoolConfiguration.getMemberCode(), taskPoolConfiguration.getSubsystemCode()),
                taskPoolConfiguration.getSecurityServerHost(), new RestTemplate(), Clock.systemDefaultZone());
        BlockingQueue<ProducerMember> wsdlServices = new LinkedBlockingQueue<>();
        FetchWorkTracker fetchWorkTracker = new FetchWorkTracker();
        fetchWorkTracker.register(1);
        FetchWsdlsTask fetchWsdlsTask = new FetchWsdlsTask(catalogService, taskPoolConfiguration, wsdlServices, fetchWorkTracker,
                new RestTemplate(), Clock.systemDefaultZone());
        ReflectionTestUtils.setField(fetchWsdlsTask, "xroadClient", xRoadClient);
        Semaphore semaphore = new Semaphore(1);
        ReflectionTestUtils.setField(fetchWsdlsTask, "semaphore", semaphore);
        Thread fetchWsdlsRunner = Thread.ofVirtual().start(fetchWsdlsTask::run);
        ProducerMember service = new ProducerMember(
                "INSTANCE", "CLASS",
                "CODE", "SUBSYSTEM",
                "aService", "v1");
        service.setObjectType(ObjectType.SERVICE);
        wsdlServices.add(service);

        Awaitility.await().atMost(Duration.ofSeconds(2)).until(wsdlServices::isEmpty);

        semaphore.acquire();
        fetchWsdlsRunner.interrupt();

        verify(catalogService, times(1)).saveWsdl(any(), any(), any());

        Awaitility.await().atMost(Duration.ofSeconds(2)).until(() -> fetchWorkTracker.pending() == 0);
    }

    @Test
    public void testFetchWsdlPendingReturnsToZeroWhenFetchThrows() throws InterruptedException, XRd4JException, SOAPException {
        SOAPClient soapClient = mock(SOAPClient.class);
        when(soapClient.send(any(ServiceRequest.class), eq(taskPoolConfiguration.getSecurityServerHost()),
                any(GetWsdlRequestSerializer.class), any(GetWsdlResponseDeserializer.class)))
                .thenThrow(new RuntimeException("boom"));
        XRoadClient xRoadClient = new XRoadClient(soapClient,
                new ConsumerMember(taskPoolConfiguration.getXroadInstance(), taskPoolConfiguration.getMemberClass(),
                        taskPoolConfiguration.getMemberCode(), taskPoolConfiguration.getSubsystemCode()),
                taskPoolConfiguration.getSecurityServerHost(), new RestTemplate(), Clock.systemDefaultZone());
        BlockingQueue<ProducerMember> wsdlServices = new LinkedBlockingQueue<>();
        FetchWorkTracker fetchWorkTracker = new FetchWorkTracker();
        fetchWorkTracker.register(1);
        FetchWsdlsTask fetchWsdlsTask = new FetchWsdlsTask(catalogService, taskPoolConfiguration, wsdlServices, fetchWorkTracker,
                new RestTemplate(), Clock.systemDefaultZone());
        ReflectionTestUtils.setField(fetchWsdlsTask, "xroadClient", xRoadClient);
        Semaphore semaphore = new Semaphore(1);
        ReflectionTestUtils.setField(fetchWsdlsTask, "semaphore", semaphore);
        Thread fetchWsdlsRunner = Thread.ofVirtual().start(fetchWsdlsTask::run);
        ProducerMember service = new ProducerMember(
                "INSTANCE", "CLASS",
                "CODE", "SUBSYSTEM",
                "aService", "v1");
        service.setObjectType(ObjectType.SERVICE);
        wsdlServices.add(service);

        Awaitility.await().atMost(Duration.ofSeconds(2)).until(wsdlServices::isEmpty);

        semaphore.acquire();
        fetchWsdlsRunner.interrupt();

        verify(catalogService, times(0)).saveWsdl(any(), any(), any());

        Awaitility.await().atMost(Duration.ofSeconds(2)).until(() -> fetchWorkTracker.pending() == 0);
    }
}
