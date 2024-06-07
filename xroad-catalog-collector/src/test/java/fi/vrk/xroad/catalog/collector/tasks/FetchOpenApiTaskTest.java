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

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.ApplicationContext;
import org.springframework.core.io.Resource;
import org.springframework.test.util.ReflectionTestUtils;

import fi.vrk.xroad.catalog.collector.configuration.TaskPoolConfiguration;
import fi.vrk.xroad.catalog.collector.util.Endpoint;
import fi.vrk.xroad.catalog.collector.util.MethodListUtil;
import fi.vrk.xroad.catalog.collector.util.XRoadRestServiceIdentifierType;
import fi.vrk.xroad.catalog.collector.wsimport.XRoadObjectType;
import fi.vrk.xroad.catalog.persistence.CatalogService;

@SpringBootTest(classes = TaskPoolConfiguration.class)
public class FetchOpenApiTaskTest {

    @MockBean
    CatalogService catalogService;

    @Autowired
    private ApplicationContext applicationContext;

    @Value("classpath:mock/xroad/openapi/openapi.json")
    private Resource openApiFile;

    @Test
    public void testBasicNoDeadlock() throws InterruptedException, MalformedURLException, URISyntaxException {
        /**
         * Note that this test will log an error that the operation did not succeed.
         * That is ok, because all we want to check here is that the task does not
         * deadlock and takes the data from our queue. We interrupt at the end to make
         * sure that the task can also be stopped when the program exits. The actual
         * fetch logic is mocked and tested below.
         */
        BlockingQueue<XRoadRestServiceIdentifierType> queue = new LinkedBlockingQueue<>();
        FetchOpenApiTask fetchOpenApiTask = new FetchOpenApiTask(applicationContext, queue);
        Semaphore semaphore = new Semaphore(1);
        ReflectionTestUtils.setField(fetchOpenApiTask, "semaphore", semaphore);
        XRoadRestServiceIdentifierType restService = new XRoadRestServiceIdentifierType();
        Thread fetchOpenApiRunner = Thread.ofVirtual().start(fetchOpenApiTask::run);
        queue.add(restService);

        Awaitility.await().atMost(Duration.ofSeconds(2)).until(() -> queue.isEmpty());

        semaphore.acquire();
        fetchOpenApiRunner.interrupt();
        assertTrue(queue.isEmpty());
    }

    @Test
    public void testFetch() throws URISyntaxException, InterruptedException, IOException {
        try (MockedStatic<MethodListUtil> mock = Mockito.mockStatic(MethodListUtil.class)) {
            final String openApiResponse = openApiFile.getContentAsString(StandardCharsets.UTF_8);
            mock.when(() -> MethodListUtil.openApiFromResponse(any(), any(), any(), any(), any(), any(), any()))
                    .thenReturn(openApiResponse);
            mock.when(() -> MethodListUtil.getEndpointList(any())).thenCallRealMethod();

            FetchOpenApiTask fetchOpenApiTask = new FetchOpenApiTask(applicationContext, new LinkedBlockingQueue<>());

            XRoadRestServiceIdentifierType service = new XRoadRestServiceIdentifierType();
            service.setObjectType(XRoadObjectType.SERVICE);
            service.setXRoadInstance("INSTANCE");
            service.setMemberClass("CLASS");
            service.setMemberCode("CODE");
            service.setSubsystemCode("SUBSYSTEM");
            service.setServiceCode("aService");
            service.setServiceVersion("v1");
            service.setServiceType("OPENAPI");
            List<Endpoint> endpointList = new ArrayList<>();
            Endpoint endpoint = new Endpoint();
            endpoint.setMethod("GET");
            endpoint.setPath("/getServices");
            endpointList.add(endpoint);
            service.setEndpoints(endpointList);

            fetchOpenApiTask.fetch(service);

            mock.verify(() -> MethodListUtil.openApiFromResponse(any(), any(), any(), any(), any(), any(), any()),
                    times(1));
            verify(catalogService, times(0)).saveErrorLog(any());
            verify(catalogService, times(1)).saveOpenApi(any(), any(), any());
            verify(catalogService, times(1)).saveEndpoint(any(), any(), any(), any());
        }
    }

}
