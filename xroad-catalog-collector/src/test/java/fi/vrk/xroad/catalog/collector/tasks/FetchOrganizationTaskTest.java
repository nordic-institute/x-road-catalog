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
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;

import org.awaitility.Awaitility;
import org.json.JSONArray;
import org.json.JSONException;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.ApplicationContext;
import org.springframework.core.io.Resource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.util.ReflectionTestUtils;

import fi.vrk.xroad.catalog.collector.configuration.TaskPoolConfiguration;
import fi.vrk.xroad.catalog.collector.util.OrganizationUtil;
import fi.vrk.xroad.catalog.collector.wsimport.ClientType;
import fi.vrk.xroad.catalog.collector.wsimport.XRoadClientIdentifierType;
import fi.vrk.xroad.catalog.collector.wsimport.XRoadObjectType;
import fi.vrk.xroad.catalog.persistence.CatalogService;
import fi.vrk.xroad.catalog.persistence.OrganizationService;

@SpringBootTest(classes = TaskPoolConfiguration.class)
@TestPropertySource(properties = {
        "xroad-catalog.fetch-organizations-limit=3",
        "xroad-catalog.max-organizations-per-request=3"
})
public class FetchOrganizationTaskTest {

    @MockBean
    CatalogService catalogService;

    @MockBean
    OrganizationService organizationService;

    @Autowired
    private ApplicationContext applicationContext;

    @Value("classpath:mock/organizations/organizationsById.json")
    private Resource organizationsByIdJSON;

    @Test
    public void testBasicNoDeadlock() throws InterruptedException {
        /**
         * Note that this test will log an error that the operation did not succeed.
         * That is ok, because all we want to check here is that the task does not
         * deadlock and takes the data from our queue. We interrupt at the end to make
         * sure that the task can also be stopped when the program exits. The actual
         * fetch logic is mocked and tested below.
         */
        BlockingQueue<ClientType> queue = new LinkedBlockingQueue<>();
        FetchOrganizationsTask fetchOrganizationsTask = new FetchOrganizationsTask(applicationContext, queue);
        Semaphore semaphore = new Semaphore(1);
        ReflectionTestUtils.setField(fetchOrganizationsTask, "semaphore", semaphore);
        ClientType clientType = new ClientType();
        Thread fetchOrganizationsRunner = Thread.ofVirtual().start(fetchOrganizationsTask::run);
        queue.add(clientType);
        Awaitility.await().atMost(Duration.ofSeconds(2)).until(() -> queue.isEmpty());
        semaphore.acquire();
        fetchOrganizationsRunner.interrupt();
        assertTrue(queue.isEmpty());
    }

    @Test
    public void testFetchOrganizationsForClient() throws JSONException, IOException {
        try (MockedStatic<OrganizationUtil> mock = Mockito.mockStatic(OrganizationUtil.class)) {
            FetchOrganizationsTask fetchOrganizationsTask = new FetchOrganizationsTask(applicationContext,
                    null);

            mock.when(() -> OrganizationUtil.getOrganizationIdsList(any(), any(), any(), any()))
                    .thenReturn(List.of(
                            "112ea34r3-1k23-412r-9142-1442asd13131",
                            "112ea34r3-1k23-412r-9142-1442asd13132",
                            "112ea34r3-1k23-412r-9142-1442asd13133"));

            JSONArray organizationsByIdResponse = new JSONArray(
                    organizationsByIdJSON.getContentAsString(StandardCharsets.UTF_8));
            mock.when(() -> OrganizationUtil.getDataByIds(any(), any(), any(), any()))
                    .thenReturn(organizationsByIdResponse);

            ClientType clientType = new ClientType();
            XRoadClientIdentifierType value = new XRoadClientIdentifierType();
            value.setXRoadInstance("INSTANCE");
            value.setMemberClass("CLASS");
            value.setMemberCode("CODE");
            value.setSubsystemCode("SUBSYSTEM");
            value.setServiceCode("aService");
            value.setServiceVersion("v1");
            value.setObjectType(XRoadObjectType.SERVICE);
            clientType.setId(value);

            fetchOrganizationsTask.fetchOrganizationsForClient(clientType);

            mock.verify(() -> OrganizationUtil.getOrganizationIdsList(any(), any(), any(), any()),
                    times(1));
            mock.verify(() -> OrganizationUtil.getDataByIds(any(), any(), any(), any()), times(1));
            verify(organizationService, times(3)).saveOrganization(any());
        }
    }
}
