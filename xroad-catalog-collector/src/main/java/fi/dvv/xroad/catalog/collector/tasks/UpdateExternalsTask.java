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
package fi.dvv.xroad.catalog.collector.tasks;

import fi.dvv.xroad.catalog.collector.configuration.FinlandTaskPoolConfiguration;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.catalog.collector.service.CatalogService;
import org.niis.xroad.catalog.collector.util.CollectorUtils;
import org.niis.xroad.catalog.persistence.entity.ErrorLog;
import org.springframework.stereotype.Component;

import java.util.Queue;
import java.util.Set;

@Slf4j
@Component
public class UpdateExternalsTask implements Runnable {

    private final FinlandTaskPoolConfiguration finlandTaskPoolConfiguration;
    private final CatalogService catalogService;
    private final Queue<String> fetchCompaniesQueue;
    private final Queue<String> fetchOrganizationsQueue;

    public UpdateExternalsTask(FinlandTaskPoolConfiguration finlandTaskPoolConfiguration, CatalogService catalogService,
                               Queue<String> fetchCompaniesQueue, Queue<String> fetchOrganizationsQueue) {
        this.finlandTaskPoolConfiguration = finlandTaskPoolConfiguration;
        this.catalogService = catalogService;
        this.fetchCompaniesQueue = fetchCompaniesQueue;
        this.fetchOrganizationsQueue = fetchOrganizationsQueue;
    }

    public void run() {
        log.info("Starting UpdateExternalsTask");
        if (finlandTaskPoolConfiguration.isFetchExternalRunUnlimited()
                || CollectorUtils.isTimeBetweenHours(finlandTaskPoolConfiguration.getFetchExternalTimeAfterHour(),
                        finlandTaskPoolConfiguration.getFetchExternalTimeBeforeHour())) {
            updateMemberCompanyAndOrganizations();
        }
    }

    private void updateMemberCompanyAndOrganizations() {
        try {
            Set<String> members = catalogService.getMembersRequiringExternalUpdate(
                    finlandTaskPoolConfiguration.getFetchExternalUpdateAfterDays(),
                    finlandTaskPoolConfiguration.getFetchExternalLimit());

            log.info("Sending {} members requiring external update to workers, batch limit {}", members.size(),
                    finlandTaskPoolConfiguration.getFetchExternalLimit());

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
