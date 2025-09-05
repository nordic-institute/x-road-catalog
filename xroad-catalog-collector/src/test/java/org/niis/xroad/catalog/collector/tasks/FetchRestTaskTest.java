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
import org.niis.xrd4j.common.exception.XRd4JException;
import org.niis.xrd4j.common.member.ObjectType;
import org.niis.xroad.catalog.collector.configuration.IgnoredSubsystemIdsProperties;
import org.niis.xroad.catalog.collector.configuration.TaskPoolConfiguration;
import org.niis.xroad.catalog.collector.service.CatalogService;
import org.niis.xroad.catalog.collector.util.Endpoint;
import org.niis.xroad.catalog.collector.util.XRoadIdentifier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@SpringBootTest(classes = {TaskPoolConfiguration.class, IgnoredSubsystemIdsProperties.class})
@ActiveProfiles("test")
public class FetchRestTaskTest {

    @MockitoBean
    CatalogService catalogService;

    @Autowired
    private TaskPoolConfiguration taskPoolConfiguration;

    @Test
    public void testFetchRestTask() throws InterruptedException, XRd4JException {
        BlockingQueue<XRoadIdentifier> restServices = new LinkedBlockingQueue<>();
        FetchRestTask fetchRestTask = new FetchRestTask(catalogService, taskPoolConfiguration, restServices);
        Semaphore semaphore = new Semaphore(1);
        ReflectionTestUtils.setField(fetchRestTask, "semaphore", semaphore);
        Thread fetchRestRunner = Thread.ofVirtual().start(fetchRestTask::run);

        XRoadIdentifier service = XRoadIdentifier.builder()
                .xRoadInstance("INSTANCE")
                .memberClass("CLASS")
                .memberCode("CODE")
                .subsystemCode("SUBSYSTEM")
                .serviceCode("aService")
                .serviceVersion("v1")
                .objectType(ObjectType.SERVICE)
                .serviceType("REST")
                .build();
        List<Endpoint> endpointList = new ArrayList<>();
        endpointList.add(Endpoint.builder().method("GET").path("/getServices").build());
        service.setEndpoints(endpointList);

        restServices.add(service);

        Awaitility.await().atMost(Duration.ofSeconds(2)).until(restServices::isEmpty);

        semaphore.acquire();
        fetchRestRunner.interrupt();

        verify(catalogService, times(0)).saveErrorLog(any());
        verify(catalogService, times(1)).saveRest(any(), any(), any());
        verify(catalogService, times(1)).saveEndpoint(any(), any(), any(), any());
    }
}
