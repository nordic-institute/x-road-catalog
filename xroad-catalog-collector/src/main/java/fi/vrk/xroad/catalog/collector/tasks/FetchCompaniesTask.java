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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Semaphore;

import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.context.ApplicationContext;

import fi.vrk.xroad.catalog.collector.configuration.TaskPoolConfiguration;
import fi.vrk.xroad.catalog.collector.util.OrganizationUtil;
import fi.vrk.xroad.catalog.collector.wsimport.ClientType;
import fi.vrk.xroad.catalog.persistence.CatalogService;
import fi.vrk.xroad.catalog.persistence.CompanyService;
import fi.vrk.xroad.catalog.persistence.entity.BusinessAddress;
import fi.vrk.xroad.catalog.persistence.entity.BusinessAuxiliaryName;
import fi.vrk.xroad.catalog.persistence.entity.BusinessIdChange;
import fi.vrk.xroad.catalog.persistence.entity.BusinessLine;
import fi.vrk.xroad.catalog.persistence.entity.BusinessName;
import fi.vrk.xroad.catalog.persistence.entity.Company;
import fi.vrk.xroad.catalog.persistence.entity.CompanyForm;
import fi.vrk.xroad.catalog.persistence.entity.ContactDetail;
import fi.vrk.xroad.catalog.persistence.entity.Language;
import fi.vrk.xroad.catalog.persistence.entity.Liquidation;
import fi.vrk.xroad.catalog.persistence.entity.RegisteredEntry;
import fi.vrk.xroad.catalog.persistence.entity.RegisteredOffice;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class FetchCompaniesTask {

    private final String fetchCompaniesUrl;

    private final Integer fetchCompaniesLimit;

    private final CatalogService catalogService;

    private final CompanyService companyService;

    private final BlockingQueue<ClientType> fetchCompaniesQueue;

    private final TaskPoolConfiguration taskPoolConfiguration;

    private final Semaphore semaphore;

    public FetchCompaniesTask(final ApplicationContext applicationContext,
            final BlockingQueue<ClientType> fetchCompaniesQueue) {
        this.catalogService = applicationContext.getBean(CatalogService.class);
        this.companyService = applicationContext.getBean(CompanyService.class);

        this.fetchCompaniesQueue = fetchCompaniesQueue;

        this.taskPoolConfiguration = applicationContext.getBean(TaskPoolConfiguration.class);
        this.fetchCompaniesUrl = taskPoolConfiguration.getFetchCompaniesUrl();
        this.fetchCompaniesLimit = taskPoolConfiguration.getFetchCompaniesLimit();

        this.semaphore = new Semaphore(taskPoolConfiguration.getFetchCompaniesPoolSize());

    }

    public void run() {
        log.info("Starting {} with pool size {}", getClass().getSimpleName(), semaphore.availablePermits());
        try {
            while (true) {
                log.debug("Waiting for data ... ");

                // take() blocks until an element becomes available or it gets interrupted
                ClientType client = fetchCompaniesQueue.take();
                semaphore.acquire();
                Thread.ofVirtual().start(() -> fetchCompaniesForCLient(client));
            }
        } catch (InterruptedException e) {
            log.warn("Interrupted while waiting for data, stopping {}", getClass().getSimpleName(), e);
            Thread.currentThread().interrupt();
        }
    }

    protected void fetchCompaniesForCLient(final ClientType client) {
        try {
            JSONObject companiesJson = OrganizationUtil.getCompanies(client, fetchCompaniesLimit, fetchCompaniesUrl,
                    catalogService);
            JSONArray companiesArray = companiesJson.optJSONArray("results");
            int numberOfCompanies = companiesArray.length();
            log.info("Fetching data for {} companies", numberOfCompanies);
            companiesArray.forEach(item -> {
                JSONObject company = (JSONObject) item;
                String businessCode = company.optString("businessId");
                JSONObject companyJson = OrganizationUtil.getCompany(client, fetchCompaniesUrl, businessCode,
                        catalogService);
                saveData(companyJson.optJSONArray("results"));
            });
            log.info("Successfully saved data for {} companies", numberOfCompanies);
        } catch (Exception e) {
            log.error("Error while fetching companies for client {}", client, e);
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
