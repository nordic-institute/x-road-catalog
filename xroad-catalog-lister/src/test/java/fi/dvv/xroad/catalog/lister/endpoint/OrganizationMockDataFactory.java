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
package fi.dvv.xroad.catalog.lister.endpoint;

import fi.dvv.xroad.catalog.persistence.entity.Company;
import fi.dvv.xroad.catalog.persistence.entity.Organization;
import org.niis.xroad.catalog.persistence.entity.StatusInfo;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public final class OrganizationMockDataFactory {

    // Consistent test constants
    public static final LocalDateTime FIXED_TEST_TIME = LocalDateTime.of(2025, 1, 1, 12, 0, 0);

    // Standard test business codes and GUIDs
    public static final String DEFAULT_BUSINESS_CODE = "1234567-8";
    public static final String DEFAULT_ORGANIZATION_GUID = "12345678-1234-1234-1234-123456789012";
    public static final String DEFAULT_ORGANIZATION_TYPE = "Municipality";
    public static final String DEFAULT_PUBLISHING_STATUS = "Published";
    
    public static final String GOVT_ORG_BUSINESS_CODE = "0123456-7";
    public static final String GOVT_ORG_GUID = "11111111-1111-1111-1111-111111111111";
    
    public static final String PUBLIC_SERVICE_BUSINESS_CODE = "9876543-2";
    public static final String PUBLIC_SERVICE_GUID = "22222222-2222-2222-2222-222222222222";

    public static final String NON_EXISTENT_BUSINESS_CODE = "9999999-9";
    public static final String NON_EXISTENT_GUID = "99999999-9999-9999-9999-999999999999";

    // Company test constants
    public static final String DEFAULT_BUSINESS_ID = "1234567-8";
    public static final String DEFAULT_COMPANY_FORM = "LLC";
    public static final String DEFAULT_COMPANY_NAME = "Test Company Ltd";
    public static final String DEFAULT_DETAILS_URI = "http://example.com/company/details";

    public static final String TEST_COMPANY_BUSINESS_ID = "2345678-9";
    public static final String WEATHER_SERVICES_BUSINESS_ID = "3456789-0";
    public static final String NON_EXISTENT_BUSINESS_ID = "9999999-9";

    private OrganizationMockDataFactory() {
        // Utility class - prevent instantiation
    }

    // Factory methods for consistent object creation

    public static StatusInfo createStandardStatusInfo() {
        return new StatusInfo(FIXED_TEST_TIME, FIXED_TEST_TIME, FIXED_TEST_TIME, null);
    }

    public static Organization createStandardOrganization(String businessCode, String guid,
                                                          String organizationType, String publishingStatus) {
        Organization organization = new Organization();
        organization.setBusinessCode(businessCode);
        organization.setGuid(guid);
        organization.setOrganizationType(organizationType);
        organization.setPublishingStatus(publishingStatus);
        organization.setStatusInfo(createStandardStatusInfo());
        return organization;
    }

    public static Company createStandardCompany(String businessId, String name, String companyForm) {
        Company company = new Company();
        company.setBusinessId(businessId);
        company.setName(name);
        company.setCompanyForm(companyForm);
        company.setDetailsUri(DEFAULT_DETAILS_URI);
        company.setRegistrationDate(FIXED_TEST_TIME);
        company.setStatusInfo(createStandardStatusInfo());
        return company;
    }

    // Pre-defined standard test entities

    public static final Organization GOVERNMENT_ORGANIZATION = createStandardOrganization(
            GOVT_ORG_BUSINESS_CODE, GOVT_ORG_GUID, DEFAULT_ORGANIZATION_TYPE, DEFAULT_PUBLISHING_STATUS);

    public static final Organization PUBLIC_SERVICE_ORGANIZATION = createStandardOrganization(
            PUBLIC_SERVICE_BUSINESS_CODE, PUBLIC_SERVICE_GUID, DEFAULT_ORGANIZATION_TYPE, DEFAULT_PUBLISHING_STATUS);

    public static final Company TEST_COMPANY = createStandardCompany(
            TEST_COMPANY_BUSINESS_ID, "Test Company Ltd", DEFAULT_COMPANY_FORM);

    public static final Company WEATHER_SERVICES_COMPANY = createStandardCompany(
            WEATHER_SERVICES_BUSINESS_ID, "Weather Services Inc", DEFAULT_COMPANY_FORM);

    // Pre-defined collections for common test scenarios

    public static List<Organization> getStandardOrganizationList() {
        return Arrays.asList(GOVERNMENT_ORGANIZATION, PUBLIC_SERVICE_ORGANIZATION);
    }

    public static List<Company> getStandardCompanyList() {
        return Arrays.asList(TEST_COMPANY, WEATHER_SERVICES_COMPANY);
    }

    // Helper methods for creating organizations/companies with specific identifiers

    public static Organization createOrganizationWithBusinessCode(String businessCode) {
        return createStandardOrganization(businessCode, DEFAULT_ORGANIZATION_GUID,
                DEFAULT_ORGANIZATION_TYPE, DEFAULT_PUBLISHING_STATUS);
    }

    public static Optional<Organization> createOrganizationOptionalWithGuid(String guid) {
        Organization organization = createStandardOrganization(DEFAULT_BUSINESS_CODE, guid,
                DEFAULT_ORGANIZATION_TYPE, DEFAULT_PUBLISHING_STATUS);
        return Optional.of(organization);
    }

    public static Company createCompanyWithBusinessId(String businessId) {
        return createStandardCompany(businessId, DEFAULT_COMPANY_NAME, DEFAULT_COMPANY_FORM);
    }

    // Helper methods for creating empty results (for not found scenarios)

    public static List<Organization> getEmptyOrganizationList() {
        return Arrays.asList();
    }

    public static List<Company> getEmptyCompanyList() {
        return Arrays.asList();
    }

    public static Optional<Organization> getEmptyOrganizationOptional() {
        return Optional.empty();
    }

    // Helper method to create an organization with changed status info for testing hasOrganizationChanged
    public static Organization createOrganizationWithChangedTime(String businessCode, String guid, LocalDateTime changedTime) {
        Organization organization = createStandardOrganization(businessCode, guid, DEFAULT_ORGANIZATION_TYPE, DEFAULT_PUBLISHING_STATUS);
        StatusInfo statusInfo = new StatusInfo(FIXED_TEST_TIME, changedTime, FIXED_TEST_TIME, null);
        organization.setStatusInfo(statusInfo);
        return organization;
    }

    // Helper method to create a company with changed status info for testing hasCompanyChanged
    public static Company createCompanyWithChangedTime(String businessId, String name, LocalDateTime changedTime) {
        Company company = createStandardCompany(businessId, name, DEFAULT_COMPANY_FORM);
        StatusInfo statusInfo = new StatusInfo(FIXED_TEST_TIME, changedTime, FIXED_TEST_TIME, null);
        company.setStatusInfo(statusInfo);
        return company;
    }
}
