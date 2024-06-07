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
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;

import org.awaitility.Awaitility;
import org.json.JSONException;
import org.json.JSONObject;
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
import fi.vrk.xroad.catalog.persistence.CompanyService;

@SpringBootTest(classes = TaskPoolConfiguration.class)
@TestPropertySource(properties = { "xroad-catalog.fetch-companies-url=" })
public class FetchCompaniesTaskTest {

    @MockBean
    CatalogService catalogService;

    @MockBean
    CompanyService companyService;

    @Autowired
    private ApplicationContext applicationContext;

    @Value("classpath:mock/companies/getCompanies.json")
    private Resource companiesJSON;

    @Value("classpath:mock/companies/company.json")
    private Resource companyJSON;

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
        FetchCompaniesTask fetchCompaniesTask = new FetchCompaniesTask(applicationContext, queue);
        Semaphore semaphore = new Semaphore(1);
        ReflectionTestUtils.setField(fetchCompaniesTask, "semaphore", semaphore);
        ClientType clientType = new ClientType();
        Thread fetchCompaniesRunner = Thread.ofVirtual().start(fetchCompaniesTask::run);
        queue.add(clientType);

        Awaitility.await().atMost(Duration.ofSeconds(2)).until(() -> queue.isEmpty());

        semaphore.acquire();
        fetchCompaniesRunner.interrupt();

        assertTrue(queue.isEmpty());
    }

    @Test
    public void testFetchCompaniesForClient() throws JSONException, IOException {
        try (MockedStatic<OrganizationUtil> mock = Mockito.mockStatic(OrganizationUtil.class)) {
            FetchCompaniesTask fetchCompaniesTask = new FetchCompaniesTask(applicationContext, null);

            final JSONObject getCompaniesResponse = new JSONObject(
                    companiesJSON.getContentAsString(StandardCharsets.UTF_8));
            mock.when(() -> OrganizationUtil.getCompanies(any(), any(), any(), any()))
                    .thenReturn(getCompaniesResponse);

            final JSONObject getCompanyResponse = new JSONObject(
                    companyJSON.getContentAsString(StandardCharsets.UTF_8));
            mock.when(() -> OrganizationUtil.getCompany(any(), any(), any(), any()))
                    .thenReturn(getCompanyResponse);
            ClientType clientType = new ClientType();
            XRoadClientIdentifierType value = new XRoadClientIdentifierType();
            value.setXRoadInstance("INSTANCE");
            value.setMemberClass("COM");
            value.setMemberCode("1234567-9");
            value.setSubsystemCode("SUBSYSTEM");
            value.setServiceCode("aService");
            value.setServiceVersion("v1");
            value.setObjectType(XRoadObjectType.SERVICE);
            clientType.setId(value);

            fetchCompaniesTask.fetchCompaniesForCLient(clientType);

            mock.verify(() -> OrganizationUtil.getCompanies(any(), any(), any(), any()), times(1));
            mock.verify(() -> OrganizationUtil.getCompany(any(), any(), any(), any()), times(4));
            verify(companyService, times(4)).saveCompany(any());
        }
    }

}
