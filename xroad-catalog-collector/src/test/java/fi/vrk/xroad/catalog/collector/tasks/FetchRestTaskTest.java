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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.ApplicationContext;
import org.springframework.test.util.ReflectionTestUtils;

import fi.vrk.xroad.catalog.collector.configuration.TaskPoolConfiguration;
import fi.vrk.xroad.catalog.collector.util.Endpoint;
import fi.vrk.xroad.catalog.collector.util.XRoadRestServiceIdentifierType;
import fi.vrk.xroad.catalog.collector.wsimport.XRoadObjectType;
import fi.vrk.xroad.catalog.persistence.CatalogService;

@SpringBootTest(classes = TaskPoolConfiguration.class)
public class FetchRestTaskTest {

    @MockBean
    CatalogService catalogService;

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    public void testFetchRestTask() throws MalformedURLException, URISyntaxException, InterruptedException {
        BlockingQueue<XRoadRestServiceIdentifierType> restServices = new LinkedBlockingQueue<>();
        FetchRestTask fetchRestTask = new FetchRestTask(applicationContext, restServices);
        Semaphore semaphore = new Semaphore(1);
        ReflectionTestUtils.setField(fetchRestTask, "semaphore", semaphore);
        Thread fetchRestRunner = Thread.ofVirtual().start(fetchRestTask::run);

        XRoadRestServiceIdentifierType service = new XRoadRestServiceIdentifierType();
        service.setObjectType(XRoadObjectType.SERVICE);
        service.setXRoadInstance("INSTANCE");
        service.setMemberClass("CLASS");
        service.setMemberCode("CODE");
        service.setSubsystemCode("SUBSYSTEM");
        service.setServiceCode("aService");
        service.setServiceVersion("v1");
        service.setServiceType("REST");
        List<Endpoint> endpointList = new ArrayList<>();
        endpointList.add(Endpoint.builder().method("GET").path("/getServices").build());
        service.setEndpoints(endpointList);

        restServices.add(service);

        Awaitility.await().atMost(Duration.ofSeconds(2)).until(() -> restServices.isEmpty());

        semaphore.acquire();
        fetchRestRunner.interrupt();

        verify(catalogService, times(0)).saveErrorLog(any());
        verify(catalogService, times(1)).saveRest(any(), any(), any());
        verify(catalogService, times(1)).saveEndpoint(any(), any(), any(), any());
    }
}
