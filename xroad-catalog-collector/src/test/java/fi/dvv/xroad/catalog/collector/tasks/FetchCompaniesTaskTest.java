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
package fi.dvv.xroad.catalog.collector.tasks;

import fi.dvv.xroad.catalog.collector.configuration.FinlandTaskPoolConfiguration;
import fi.dvv.xroad.catalog.collector.service.CompanyService;
import fi.dvv.xroad.catalog.collector.util.OrganizationUtil;
import org.awaitility.Awaitility;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.niis.xroad.catalog.collector.CollectorApplication;
import org.niis.xroad.catalog.collector.service.CatalogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.Resource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@SpringBootTest(classes = CollectorApplication.class)
@ActiveProfiles({"test", "fi-testdata"})
@TestPropertySource(properties = { "xroad-catalog.fetch-companies-url=" })
public class FetchCompaniesTaskTest {

    @MockitoBean
    CatalogService catalogService;

    @MockitoBean
    CompanyService companyService;

    @Autowired
    private FinlandTaskPoolConfiguration finlandTaskPoolConfiguration;

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
        BlockingQueue<String> queue = new LinkedBlockingQueue<>();
        FetchCompaniesTask fetchCompaniesTask = new FetchCompaniesTask(catalogService, companyService, finlandTaskPoolConfiguration, queue);
        Semaphore semaphore = new Semaphore(1);
        ReflectionTestUtils.setField(fetchCompaniesTask, "semaphore", semaphore);
        Thread fetchCompaniesRunner = Thread.ofVirtual().start(fetchCompaniesTask::run);
        queue.add("");

        Awaitility.await().atMost(Duration.ofSeconds(2)).until(() -> queue.isEmpty());

        semaphore.acquire();
        fetchCompaniesRunner.interrupt();

        assertTrue(queue.isEmpty());
    }

    @Test
    public void testFetchCompanyForClient() throws JSONException, IOException {
        try (MockedStatic<OrganizationUtil> mock = Mockito.mockStatic(OrganizationUtil.class)) {
            FetchCompaniesTask fetchCompaniesTask = new FetchCompaniesTask(catalogService, companyService, finlandTaskPoolConfiguration,
                    null);

            final Optional<JSONObject> getCompanyResponse = Optional.ofNullable(new JSONObject(
                    companyJSON.getContentAsString(StandardCharsets.UTF_8)));
            mock.when(() -> OrganizationUtil.getCompany(any(), any(), any())).thenReturn(getCompanyResponse);

            fetchCompaniesTask.fetchCompanyData("1234567-9");

            mock.verify(() -> OrganizationUtil.getCompany(any(), any(), any()), times(1));
            verify(companyService, times(1)).saveCompany(any());
        }
    }

    @Test
    public void testFetchCompanyForClientNotFound() throws JSONException, IOException {
        try (MockedStatic<OrganizationUtil> mock = Mockito.mockStatic(OrganizationUtil.class)) {
            FetchCompaniesTask fetchCompaniesTask = new FetchCompaniesTask(catalogService, companyService, finlandTaskPoolConfiguration,
                    null);

            mock.when(() -> OrganizationUtil.getCompany(any(), any(), any())).thenReturn(Optional.empty());

            fetchCompaniesTask.fetchCompanyData("1234567-9");

            mock.verify(() -> OrganizationUtil.getCompany(any(), any(), any()), times(1));
            verifyNoInteractions(companyService);
        }
    }
}
