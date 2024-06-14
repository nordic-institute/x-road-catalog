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

import java.util.Queue;
import java.util.Set;

import org.springframework.context.ApplicationContext;

import fi.vrk.xroad.catalog.collector.configuration.TaskPoolConfiguration;
import fi.vrk.xroad.catalog.collector.util.CollectorUtils;
import fi.vrk.xroad.catalog.persistence.CatalogService;
import fi.vrk.xroad.catalog.persistence.entity.ErrorLog;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class UpdateExternalsTask implements Runnable {

    private final TaskPoolConfiguration taskPoolConfiguration;
    private final CatalogService catalogService;
    private final Queue<String> fetchCompaniesQueue;
    private final Queue<String> fetchOrganizationsQueue;

    public UpdateExternalsTask(ApplicationContext applicationContext, Queue<String> fetchCompaniesQueue,
            Queue<String> fetchOrganizationsQueue) {
        this.taskPoolConfiguration = applicationContext.getBean(TaskPoolConfiguration.class);
        this.catalogService = applicationContext.getBean(CatalogService.class);
        this.fetchCompaniesQueue = fetchCompaniesQueue;
        this.fetchOrganizationsQueue = fetchOrganizationsQueue;
    }

    public void run() {
        if (taskPoolConfiguration.isFetchExternalRunUnlimited()
                || CollectorUtils.isTimeBetweenHours(taskPoolConfiguration.getFetchExternalTimeAfterHour(),
                        taskPoolConfiguration.getFetchExternalTimeBeforeHour())) {
            updateMemberCompanyAndOrganizations();
        }
    }

    private void updateMemberCompanyAndOrganizations() {
        try {
            Set<String> members = catalogService.getMembersRequiringExternalUpdate(
                    taskPoolConfiguration.getFetchExternalUpdateAfterDays(),
                    taskPoolConfiguration.getFetchExternalLimit());

            log.info("Sending {} members requiring external update to workers, batch limit {}", members.size(),
                    taskPoolConfiguration.getFetchExternalLimit());

            fetchCompaniesQueue.addAll(members);
            fetchOrganizationsQueue.addAll(members);

        } catch (Exception e) {
            ErrorLog errorLog = CollectorUtils.createErrorLog(null,
                    "Error when updating member companies and organizations: " + e.getMessage(), "500");
            catalogService.saveErrorLog(errorLog);
            log.error("Error when updating member companies and organizations", e);
        }

    }

}
