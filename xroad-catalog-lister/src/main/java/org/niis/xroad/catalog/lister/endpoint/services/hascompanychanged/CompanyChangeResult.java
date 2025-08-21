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
package org.niis.xroad.catalog.lister.endpoint.services.hascompanychanged;

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

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * FIXME: Replace this in-memory change detection logic with a database query-based solution
 * for better performance and scalability. Currently, this loads full entity graphs into memory
 * to check for changes, which is inefficient for large datasets.
 */
public class CompanyChangeResult {
    private static final String COMPANY = "Company";
    private static final String BUSINESS_ADDRESS = "BusinessAddress";
    private static final String BUSINESS_AUXILIARY_NAME = "BusinessAuxiliaryName";
    private static final String BUSINESS_ID_CHANGE = "BusinessIdChange";
    private static final String BUSINESS_LINE = "BusinessLine";
    private static final String BUSINESS_NAME = "BusinessName";
    private static final String COMPANY_FORM = "CompanyForm";
    private static final String CONTACT_DETAIL = "ContactDetail";
    private static final String LANGUAGE = "Language";
    private static final String LIQUIDATION = "Liquidation";
    private static final String REGISTERED_ENTRY = "RegisteredEntry";
    private static final String REGISTERED_OFFICE = "RegisteredOffice";

    private final boolean changed;
    private final List<String> changedValueNames;

    public CompanyChangeResult(Iterable<Company> companies, LocalDateTime startDateTime, LocalDateTime endDateTime) {
        this.changedValueNames = new ArrayList<>();
        
        for (Company company : companies) {
            Set<BusinessAddress> businessAddresses = company.getAllBusinessAddresses();
            Set<BusinessAuxiliaryName> businessAuxiliaryNames = company.getAllBusinessAuxiliaryNames();
            Set<BusinessIdChange> businessIdChanges = company.getAllBusinessIdChanges();
            Set<BusinessLine> businessLines = company.getAllBusinessLines();
            Set<BusinessName> businessNames = company.getAllBusinessNames();
            Set<CompanyForm> companyForms = company.getAllCompanyForms();
            Set<ContactDetail> contactDetails = company.getAllContactDetails();
            Set<Language> languages = company.getAllLanguages();
            Set<Liquidation> liquidations = company.getAllLiquidations();
            Set<RegisteredEntry> registeredEntries = company.getAllRegisteredEntries();
            Set<RegisteredOffice> registeredOffices = company.getAllRegisteredOffices();

            if (company.getStatusInfo().getChanged().isAfter(startDateTime)
                    && company.getStatusInfo().getChanged().isBefore(endDateTime)) {
                changedValueNames.add(COMPANY);
            }

            if (businessAddresses.stream().anyMatch(obj -> obj.getStatusInfo().getChanged().isAfter(startDateTime)
                    && obj.getStatusInfo().getChanged().isBefore(endDateTime))) {
                changedValueNames.add(BUSINESS_ADDRESS);
            }

            if (businessAuxiliaryNames.stream().anyMatch(obj -> obj.getStatusInfo().getChanged().isAfter(startDateTime)
                    && obj.getStatusInfo().getChanged().isBefore(endDateTime))) {
                changedValueNames.add(BUSINESS_AUXILIARY_NAME);
            }

            if (businessIdChanges.stream().anyMatch(obj -> obj.getStatusInfo().getChanged().isAfter(startDateTime)
                    && obj.getStatusInfo().getChanged().isBefore(endDateTime))) {
                changedValueNames.add(BUSINESS_ID_CHANGE);
            }

            if (businessLines.stream().anyMatch(obj -> obj.getStatusInfo().getChanged().isAfter(startDateTime)
                    && obj.getStatusInfo().getChanged().isBefore(endDateTime))) {
                changedValueNames.add(BUSINESS_LINE);
            }

            if (businessNames.stream().anyMatch(obj -> obj.getStatusInfo().getChanged().isAfter(startDateTime)
                    && obj.getStatusInfo().getChanged().isBefore(endDateTime))) {
                changedValueNames.add(BUSINESS_NAME);
            }

            if (companyForms.stream().anyMatch(obj -> obj.getStatusInfo().getChanged().isAfter(startDateTime)
                    && obj.getStatusInfo().getChanged().isBefore(endDateTime))) {
                changedValueNames.add(COMPANY_FORM);
            }

            if (contactDetails.stream().anyMatch(obj -> obj.getStatusInfo().getChanged().isAfter(startDateTime)
                    && obj.getStatusInfo().getChanged().isBefore(endDateTime))) {
                changedValueNames.add(CONTACT_DETAIL);
            }

            if (languages.stream().anyMatch(obj -> obj.getStatusInfo().getChanged().isAfter(startDateTime)
                    && obj.getStatusInfo().getChanged().isBefore(endDateTime))) {
                changedValueNames.add(LANGUAGE);
            }

            if (liquidations.stream().anyMatch(obj -> obj.getStatusInfo().getChanged().isAfter(startDateTime)
                    && obj.getStatusInfo().getChanged().isBefore(endDateTime))) {
                changedValueNames.add(LIQUIDATION);
            }

            if (registeredEntries.stream().anyMatch(obj -> obj.getStatusInfo().getChanged().isAfter(startDateTime)
                    && obj.getStatusInfo().getChanged().isBefore(endDateTime))) {
                changedValueNames.add(REGISTERED_ENTRY);
            }

            if (registeredOffices.stream().anyMatch(obj -> obj.getStatusInfo().getChanged().isAfter(startDateTime)
                    && obj.getStatusInfo().getChanged().isBefore(endDateTime))) {
                changedValueNames.add(REGISTERED_OFFICE);
            }
        }

        this.changed = !changedValueNames.isEmpty();
    }

    public boolean isChanged() {
        return changed;
    }

    public List<String> getChangedValueNames() {
        return changedValueNames;
    }
}
