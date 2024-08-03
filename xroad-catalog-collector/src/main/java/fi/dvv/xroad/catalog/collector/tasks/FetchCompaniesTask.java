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
import fi.dvv.xroad.catalog.collector.service.CompanyService;
import fi.dvv.xroad.catalog.collector.util.OrganizationUtil;
import fi.dvv.xroad.catalog.persistence.entity.BusinessAddress;
import fi.dvv.xroad.catalog.persistence.entity.BusinessAuxiliaryName;
import fi.dvv.xroad.catalog.persistence.entity.BusinessIdChange;
import fi.dvv.xroad.catalog.persistence.entity.BusinessLine;
import fi.dvv.xroad.catalog.persistence.entity.BusinessName;
import fi.dvv.xroad.catalog.persistence.entity.Company;
import fi.dvv.xroad.catalog.persistence.entity.CompanyForm;
import fi.dvv.xroad.catalog.persistence.entity.ContactDetail;
import fi.dvv.xroad.catalog.persistence.entity.Language;
import fi.dvv.xroad.catalog.persistence.entity.Liquidation;
import fi.dvv.xroad.catalog.persistence.entity.RegisteredEntry;
import fi.dvv.xroad.catalog.persistence.entity.RegisteredOffice;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONArray;
import org.json.JSONObject;
import org.niis.xroad.catalog.collector.service.CatalogService;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Semaphore;

@Slf4j
@Component
public class FetchCompaniesTask implements Runnable {

    private final String fetchCompaniesUrl;

    private final CatalogService catalogService;

    private final CompanyService companyService;

    private final BlockingQueue<String> fetchCompaniesQueue;

    private final Semaphore semaphore;

    public FetchCompaniesTask(final CatalogService catalogService, CompanyService companyService,
                              final FinlandTaskPoolConfiguration finlandTaskPoolConfiguration, final BlockingQueue<String> fetchCompaniesQueue) {
        this.catalogService = catalogService;
        this.companyService = companyService;

        this.fetchCompaniesQueue = fetchCompaniesQueue;

        this.fetchCompaniesUrl = finlandTaskPoolConfiguration.getFetchCompaniesUrl();

        this.semaphore = new Semaphore(finlandTaskPoolConfiguration.getFetchCompaniesPoolSize());

    }

    public void run() {
        log.info("Starting FetchCompaniesTask with pool size {}", semaphore.availablePermits());
        try {
            while (true) {
                log.debug("Waiting for data ... ");

                // take() blocks until an element becomes available or it gets interrupted
                String businessId = fetchCompaniesQueue.take();
                semaphore.acquire();
                Thread.ofVirtual().start(() -> fetchCompanyData(businessId));
            }
        } catch (InterruptedException e) {
            log.warn("Interrupted while waiting for data, stopping {}", getClass().getSimpleName(), e);
            Thread.currentThread().interrupt();
        }
    }

    protected void fetchCompanyData(final String businessId) {
        try {
            log.info("Fetching company information for member {}", businessId);

            Optional<JSONObject> company = OrganizationUtil.getCompany(fetchCompaniesUrl, businessId,
                    catalogService);
            company.ifPresent(companyJson -> saveData(companyJson.optJSONArray("results")));
        } catch (Exception e) {
            log.error("Error while fetching company information for member {}", businessId, e);
        } finally {
            semaphore.release();
        }
    }

    private void saveData(JSONArray data) {
        for (int i = 0; i < data.length(); i++) {
            Company savedCompany = companyService.saveCompany(OrganizationUtil.createCompany(data.optJSONObject(i)));
            saveBusinessAddresses(data.optJSONObject(i).optJSONArray("addresses"), savedCompany);
            saveBusinessAuxiliaryNames(data.optJSONObject(i).optJSONArray("auxiliaryNames"), savedCompany);
            saveBusinessIdChanges(data.optJSONObject(i).optJSONArray("businessIdChanges"), savedCompany);
            saveBusinessLines(data.optJSONObject(i).optJSONArray("businessLines"), savedCompany);
            saveBusinessNames(data.optJSONObject(i).optJSONArray("businessNames"), savedCompany);
            saveCompanyForms(data.optJSONObject(i).optJSONArray("companyForms"), savedCompany);
            saveContactDetails(data.optJSONObject(i).optJSONArray("contactDetails"), savedCompany);
            saveLanguages(data.optJSONObject(i).optJSONArray("languages"), savedCompany);
            saveLiquidations(data.optJSONObject(i).optJSONArray("liquidations"), savedCompany);
            saveRegisteredEntries(data.optJSONObject(i).optJSONArray("registeredEntries"), savedCompany);
            saveRegisteredOffices(data.optJSONObject(i).optJSONArray("registeredOffices"), savedCompany);
            log.info("Company information saved for member {}", savedCompany.getBusinessId());
        }
    }

    private void saveBusinessAddresses(JSONArray businessAddressesJson, Company savedCompany) {
        List<BusinessAddress> businessAddresses = businessAddressesJson != null
                ? OrganizationUtil.createBusinessAddresses(businessAddressesJson)
                : new ArrayList<>();

        businessAddresses.forEach(businessAddress -> {
            businessAddress.setCompany(savedCompany);
            companyService.saveBusinessAddress(businessAddress);
        });
    }

    private void saveBusinessAuxiliaryNames(JSONArray businessAuxiliaryNamesJson, Company savedCompany) {
        List<BusinessAuxiliaryName> businessAuxiliaryNames = businessAuxiliaryNamesJson != null
                ? OrganizationUtil.createBusinessAuxiliaryNames(businessAuxiliaryNamesJson)
                : new ArrayList<>();
        businessAuxiliaryNames.forEach(businessAuxiliaryName -> {
            businessAuxiliaryName.setCompany(savedCompany);
            companyService.saveBusinessAuxiliaryName(businessAuxiliaryName);
        });
    }

    private void saveBusinessIdChanges(JSONArray businessIdChangesJson, Company savedCompany) {
        List<BusinessIdChange> businessIdChanges = businessIdChangesJson != null
                ? OrganizationUtil.createBusinessIdChanges(businessIdChangesJson)
                : new ArrayList<>();
        businessIdChanges.forEach(businessIdChange -> {
            businessIdChange.setCompany(savedCompany);
            companyService.saveBusinessIdChange(businessIdChange);
        });
    }

    private void saveBusinessLines(JSONArray businessLinesJson, Company savedCompany) {
        List<BusinessLine> businessLines = businessLinesJson != null
                ? OrganizationUtil.createBusinessLines(businessLinesJson)
                : new ArrayList<>();
        businessLines.forEach(businessLine -> {
            businessLine.setCompany(savedCompany);
            companyService.saveBusinessLine(businessLine);
        });
    }

    private void saveBusinessNames(JSONArray businessNamesJson, Company savedCompany) {
        List<BusinessName> businessNames = businessNamesJson != null
                ? OrganizationUtil.createBusinessNames(businessNamesJson)
                : new ArrayList<>();
        businessNames.forEach(businessName -> {
            businessName.setCompany(savedCompany);
            companyService.saveBusinessName(businessName);
        });
    }

    private void saveCompanyForms(JSONArray companyFormsJson, Company savedCompany) {
        List<CompanyForm> companyForms = companyFormsJson != null
                ? OrganizationUtil.createCompanyForms(companyFormsJson)
                : new ArrayList<>();
        companyForms.forEach(companyForm -> {
            companyForm.setCompany(savedCompany);
            companyService.saveCompanyForm(companyForm);
        });
    }

    private void saveContactDetails(JSONArray contactDetailsJson, Company savedCompany) {
        List<ContactDetail> contactDetails = contactDetailsJson != null
                ? OrganizationUtil.createContactDetails(contactDetailsJson)
                : new ArrayList<>();
        contactDetails.forEach(contactDetail -> {
            contactDetail.setCompany(savedCompany);
            companyService.saveContactDetail(contactDetail);
        });
    }

    private void saveLanguages(JSONArray languagesJson, Company savedCompany) {
        List<Language> languages = languagesJson != null
                ? OrganizationUtil.createLanguages(languagesJson)
                : new ArrayList<>();
        languages.forEach(language -> {
            language.setCompany(savedCompany);
            companyService.saveLanguage(language);
        });
    }

    private void saveLiquidations(JSONArray liquidationsJson, Company savedCompany) {
        List<Liquidation> liquidations = liquidationsJson != null
                ? OrganizationUtil.createLiquidations(liquidationsJson)
                : new ArrayList<>();
        liquidations.forEach(liquidation -> {
            liquidation.setCompany(savedCompany);
            companyService.saveLiquidation(liquidation);
        });
    }

    private void saveRegisteredEntries(JSONArray registeredEntriesJson, Company savedCompany) {
        List<RegisteredEntry> registeredEntries = registeredEntriesJson != null
                ? OrganizationUtil.createRegisteredEntries(registeredEntriesJson)
                : new ArrayList<>();
        registeredEntries.forEach(registeredEntry -> {
            registeredEntry.setCompany(savedCompany);
            companyService.saveRegisteredEntry(registeredEntry);
        });
    }

    private void saveRegisteredOffices(JSONArray registeredOfficesJson, Company savedCompany) {
        List<RegisteredOffice> registeredOffices = registeredOfficesJson != null
                ? OrganizationUtil.createRegisteredOffices(registeredOfficesJson)
                : new ArrayList<>();
        registeredOffices.forEach(registeredOffice -> {
            registeredOffice.setCompany(savedCompany);
            companyService.saveRegisteredOffice(registeredOffice);
        });
    }
}
