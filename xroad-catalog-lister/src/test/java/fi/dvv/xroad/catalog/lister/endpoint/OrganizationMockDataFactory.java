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

import fi.dvv.xroad.catalog.persistence.entity.Address;
import fi.dvv.xroad.catalog.persistence.entity.BusinessAddress;
import fi.dvv.xroad.catalog.persistence.entity.BusinessAuxiliaryName;
import fi.dvv.xroad.catalog.persistence.entity.BusinessIdChange;
import fi.dvv.xroad.catalog.persistence.entity.BusinessLine;
import fi.dvv.xroad.catalog.persistence.entity.BusinessName;
import fi.dvv.xroad.catalog.persistence.entity.Company;
import fi.dvv.xroad.catalog.persistence.entity.CompanyForm;
import fi.dvv.xroad.catalog.persistence.entity.ContactDetail;
import fi.dvv.xroad.catalog.persistence.entity.Email;
import fi.dvv.xroad.catalog.persistence.entity.Language;
import fi.dvv.xroad.catalog.persistence.entity.Liquidation;
import fi.dvv.xroad.catalog.persistence.entity.Organization;
import fi.dvv.xroad.catalog.persistence.entity.OrganizationDescription;
import fi.dvv.xroad.catalog.persistence.entity.OrganizationName;
import fi.dvv.xroad.catalog.persistence.entity.PhoneNumber;
import fi.dvv.xroad.catalog.persistence.entity.PostOffice;
import fi.dvv.xroad.catalog.persistence.entity.PostOfficeBox;
import fi.dvv.xroad.catalog.persistence.entity.PostOfficeBoxAddress;
import fi.dvv.xroad.catalog.persistence.entity.PostOfficeBoxAddressAdditionalInformation;
import fi.dvv.xroad.catalog.persistence.entity.PostOfficeBoxAddressMunicipality;
import fi.dvv.xroad.catalog.persistence.entity.PostOfficeBoxAddressMunicipalityName;
import fi.dvv.xroad.catalog.persistence.entity.RegisteredEntry;
import fi.dvv.xroad.catalog.persistence.entity.RegisteredOffice;
import fi.dvv.xroad.catalog.persistence.entity.Street;
import fi.dvv.xroad.catalog.persistence.entity.StreetAddress;
import fi.dvv.xroad.catalog.persistence.entity.StreetAddressAdditionalInformation;
import fi.dvv.xroad.catalog.persistence.entity.StreetAddressMunicipality;
import fi.dvv.xroad.catalog.persistence.entity.StreetAddressMunicipalityName;
import fi.dvv.xroad.catalog.persistence.entity.StreetAddressPostOffice;
import fi.dvv.xroad.catalog.persistence.entity.WebPage;
import org.niis.xroad.catalog.persistence.entity.StatusInfo;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

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

    // Factory method to create organization with all related entities populated
    public static Organization createFullOrganizationWithAllDetails(String businessCode, String guid) {
        Organization organization = createStandardOrganization(businessCode, guid, DEFAULT_ORGANIZATION_TYPE, DEFAULT_PUBLISHING_STATUS);
        
        Set<OrganizationName> names = new HashSet<>();
        OrganizationName primaryName = new OrganizationName();
        primaryName.setLanguage("EN");
        primaryName.setType("Name");
        primaryName.setValue("Test Organization Ltd");
        primaryName.setOrganization(organization);
        primaryName.setStatusInfo(createStandardStatusInfo());
        names.add(primaryName);

        OrganizationName alternateName = new OrganizationName();
        alternateName.setLanguage("EN");
        alternateName.setType("AlternateName");
        alternateName.setValue("Test Org");
        alternateName.setOrganization(organization);
        alternateName.setStatusInfo(createStandardStatusInfo());
        names.add(alternateName);

        Set<OrganizationDescription> descriptions = new HashSet<>();
        OrganizationDescription description = new OrganizationDescription();
        description.setLanguage("EN");
        description.setType("Description");
        description.setValue("Test organization description");
        description.setOrganization(organization);
        description.setStatusInfo(createStandardStatusInfo());
        descriptions.add(description);

        Set<Email> emails = new HashSet<>();
        Email email = new Email();
        email.setValue("contact@example.com");
        email.setDescription("Main contact email");
        email.setOrganization(organization);
        email.setStatusInfo(createStandardStatusInfo());
        emails.add(email);

        Set<PhoneNumber> phoneNumbers = new HashSet<>();
        PhoneNumber phoneNumber = new PhoneNumber();
        phoneNumber.setNumber("+1234567890");
        phoneNumber.setLanguage("EN");
        phoneNumber.setPrefixNumber("+1");
        phoneNumber.setIsFinnishServiceNumber(false);
        phoneNumber.setOrganization(organization);
        phoneNumber.setStatusInfo(createStandardStatusInfo());
        phoneNumbers.add(phoneNumber);

        Set<WebPage> webPages = new HashSet<>();
        WebPage webPage = new WebPage();
        webPage.setUrl("https://www.example.com");
        webPage.setLanguage("EN");
        webPage.setValue("Official website");
        webPage.setOrganization(organization);
        webPage.setStatusInfo(createStandardStatusInfo());
        webPages.add(webPage);

        Set<Address> addresses = new HashSet<>();
        Address address = new Address();
        address.setCountry("Test Country");
        address.setType("Visiting address");
        address.setOrganization(organization);
        address.setStatusInfo(createStandardStatusInfo());
        addresses.add(address);

        organization.getAllOrganizationNames().addAll(names);
        organization.getAllOrganizationDescriptions().addAll(descriptions);
        organization.getAllEmails().addAll(emails);
        organization.getAllPhoneNumbers().addAll(phoneNumbers);
        organization.getAllWebPages().addAll(webPages);
        organization.getAllAddresses().addAll(addresses);

        return organization;
    }

    // Factory method to create company with all details populated
    public static Company createFullCompanyWithAllDetails(String businessId, String name) {
        Company company = createStandardCompany(businessId, name, DEFAULT_COMPANY_FORM);
        
        company.setName(name + " (Full Details)");
        company.setDetailsUri("https://www.example.com/company/" + businessId);
        
        return company;
    }

    // Helper method to create StatusInfo with a specific changed time for testing change detection
    public static StatusInfo createStatusInfoWithChangedTime(LocalDateTime changedTime) {
        return new StatusInfo(FIXED_TEST_TIME, changedTime, FIXED_TEST_TIME, null);
    }

    // Helper method to create StreetAddress with all nested entities having changed times
    public static StreetAddress createStreetAddressWithAllChangedEntities(LocalDateTime changedTime) {
        StreetAddress streetAddress = new StreetAddress();
        streetAddress.setStreetNumber("123");
        streetAddress.setPostalCode("00100");
        streetAddress.setLatitude("60.1699");
        streetAddress.setLongitude("24.9384");
        streetAddress.setCoordinateState("Ok");
        streetAddress.setStatusInfo(createStatusInfoWithChangedTime(changedTime));

        // Create Street
        Street street = new Street();
        street.setLanguage("EN");
        street.setValue("Main Street 123");
        street.setStreetAddress(streetAddress);
        street.setStatusInfo(createStatusInfoWithChangedTime(changedTime));
        Set<Street> streets = new HashSet<>();
        streets.add(street);
        streetAddress.setStreets(streets);

        // Create StreetAddressPostOffice
        StreetAddressPostOffice postOffice = new StreetAddressPostOffice();
        postOffice.setLanguage("EN");
        postOffice.setValue("Helsinki");
        postOffice.setStreetAddress(streetAddress);
        postOffice.setStatusInfo(createStatusInfoWithChangedTime(changedTime));
        Set<StreetAddressPostOffice> postOffices = new HashSet<>();
        postOffices.add(postOffice);
        streetAddress.setPostOffices(postOffices);

        // Create StreetAddressMunicipality and StreetAddressMunicipalityName
        StreetAddressMunicipality municipality = new StreetAddressMunicipality();
        municipality.setCode("091");
        municipality.setStreetAddress(streetAddress);
        municipality.setStatusInfo(createStatusInfoWithChangedTime(changedTime));

        StreetAddressMunicipalityName municipalityName = new StreetAddressMunicipalityName();
        municipalityName.setLanguage("EN");
        municipalityName.setValue("Helsinki");
        municipalityName.setStreetAddressMunicipality(municipality);
        municipalityName.setStatusInfo(createStatusInfoWithChangedTime(changedTime));
        Set<StreetAddressMunicipalityName> municipalityNames = new HashSet<>();
        municipalityNames.add(municipalityName);
        municipality.setStreetAddressMunicipalityNames(municipalityNames);

        Set<StreetAddressMunicipality> municipalities = new HashSet<>();
        municipalities.add(municipality);
        streetAddress.setMunicipalities(municipalities);

        // Create StreetAddressAdditionalInformation
        StreetAddressAdditionalInformation additionalInfo = new StreetAddressAdditionalInformation();
        additionalInfo.setLanguage("EN");
        additionalInfo.setValue("Building A, Floor 2");
        additionalInfo.setStreetAddress(streetAddress);
        additionalInfo.setStatusInfo(createStatusInfoWithChangedTime(changedTime));
        Set<StreetAddressAdditionalInformation> additionalInfoSet = new HashSet<>();
        additionalInfoSet.add(additionalInfo);
        streetAddress.setAdditionalInformation(additionalInfoSet);

        return streetAddress;
    }

    // Helper method to create PostOfficeBoxAddress with all nested entities having changed times
    public static PostOfficeBoxAddress createPostOfficeBoxAddressWithAllChangedEntities(LocalDateTime changedTime) {
        PostOfficeBoxAddress postOfficeBoxAddress = new PostOfficeBoxAddress();
        postOfficeBoxAddress.setPostalCode("00200");
        postOfficeBoxAddress.setStatusInfo(createStatusInfoWithChangedTime(changedTime));

        // Create PostOffice
        PostOffice postOffice = new PostOffice();
        postOffice.setLanguage("EN");
        postOffice.setValue("Helsinki Post Office");
        postOffice.setPostOfficeBoxAddress(postOfficeBoxAddress);
        postOffice.setStatusInfo(createStatusInfoWithChangedTime(changedTime));
        Set<PostOffice> postOffices = new HashSet<>();
        postOffices.add(postOffice);
        postOfficeBoxAddress.setPostOffices(postOffices);

        // Create PostOfficeBox
        PostOfficeBox postOfficeBox = new PostOfficeBox();
        postOfficeBox.setLanguage("EN");
        postOfficeBox.setValue("PO Box 123");
        postOfficeBox.setPostOfficeBoxAddress(postOfficeBoxAddress);
        postOfficeBox.setStatusInfo(createStatusInfoWithChangedTime(changedTime));
        Set<PostOfficeBox> postOfficeBoxes = new HashSet<>();
        postOfficeBoxes.add(postOfficeBox);
        postOfficeBoxAddress.setPostOfficesBoxes(postOfficeBoxes);

        // Create PostOfficeBoxAddressMunicipality and PostOfficeBoxAddressMunicipalityName
        PostOfficeBoxAddressMunicipality municipality = new PostOfficeBoxAddressMunicipality();
        municipality.setCode("091");
        municipality.setPostOfficeBoxAddress(postOfficeBoxAddress);
        municipality.setStatusInfo(createStatusInfoWithChangedTime(changedTime));

        PostOfficeBoxAddressMunicipalityName municipalityName = new PostOfficeBoxAddressMunicipalityName();
        municipalityName.setLanguage("EN");
        municipalityName.setValue("Helsinki");
        municipalityName.setPostOfficeBoxAddressMunicipality(municipality);
        municipalityName.setStatusInfo(createStatusInfoWithChangedTime(changedTime));
        Set<PostOfficeBoxAddressMunicipalityName> municipalityNames = new HashSet<>();
        municipalityNames.add(municipalityName);
        municipality.setPostOfficeBoxAddressMunicipalityNames(municipalityNames);

        Set<PostOfficeBoxAddressMunicipality> municipalities = new HashSet<>();
        municipalities.add(municipality);
        postOfficeBoxAddress.setPostOfficeBoxAddressMunicipalities(municipalities);

        // Create PostOfficeBoxAddressAdditionalInformation
        PostOfficeBoxAddressAdditionalInformation additionalInfo = new PostOfficeBoxAddressAdditionalInformation();
        additionalInfo.setLanguage("EN");
        additionalInfo.setValue("Department XYZ");
        additionalInfo.setPostOfficeBoxAddress(postOfficeBoxAddress);
        additionalInfo.setStatusInfo(createStatusInfoWithChangedTime(changedTime));
        Set<PostOfficeBoxAddressAdditionalInformation> additionalInfoSet = new HashSet<>();
        additionalInfoSet.add(additionalInfo);
        postOfficeBoxAddress.setAdditionalInformation(additionalInfoSet);

        return postOfficeBoxAddress;
    }

    // Simplified method to test just one StreetAddress entity
    public static Organization createOrganizationWithOneStreetAddress(String businessCode, String guid, LocalDateTime changedTime) {
        // Create base organization with basic entities (working ones)
        Organization organization = createStandardOrganization(businessCode, guid, DEFAULT_ORGANIZATION_TYPE, DEFAULT_PUBLISHING_STATUS);
        organization.setStatusInfo(createStatusInfoWithChangedTime(changedTime));
        
        // Add the basic working entities
        Set<OrganizationName> names = new HashSet<>();
        OrganizationName primaryName = new OrganizationName();
        primaryName.setLanguage("EN");
        primaryName.setType("Name");
        primaryName.setValue("Test Organization");
        primaryName.setOrganization(organization);
        primaryName.setStatusInfo(createStatusInfoWithChangedTime(changedTime));
        names.add(primaryName);
        organization.getAllOrganizationNames().addAll(names);
        
        // Add one Address with one StreetAddress
        Set<Address> addresses = new HashSet<>();
        Address address = new Address();
        address.setCountry("Finland");
        address.setType("Postal");
        address.setSubType("Primary");
        address.setOrganization(organization);
        address.setStatusInfo(createStatusInfoWithChangedTime(changedTime));
        
        // Create ONE StreetAddress with changed time
        StreetAddress streetAddress = new StreetAddress();
        streetAddress.setStreetNumber("123");
        streetAddress.setPostalCode("00100");
        streetAddress.setLatitude("60.1699");
        streetAddress.setLongitude("24.9384");
        streetAddress.setCoordinateState("Ok");
        streetAddress.setStatusInfo(createStatusInfoWithChangedTime(changedTime));
        
        // Set the bidirectional relationship using the working pattern
        streetAddress.setAddress(address);
        
        // Create collection and ASSIGN it to the Address (following working JaxbOrganizationServiceTest pattern)
        Set<StreetAddress> streetAddresses = new HashSet<>();
        streetAddresses.add(streetAddress);
        address.setStreetAddresses(streetAddresses);  // ASSIGN collection instead of manual sync
        
        addresses.add(address);
        organization.setAddresses(addresses);  // ASSIGN collection instead of manual sync

        return organization;
    }

    // Comprehensive method to create organization with ALL possible changed entities for complete test coverage
    public static Organization createOrganizationWithAllChangedEntities(String businessCode, String guid, LocalDateTime changedTime) {
        // Create base organization with changed time
        Organization organization = createStandardOrganization(businessCode, guid, DEFAULT_ORGANIZATION_TYPE, DEFAULT_PUBLISHING_STATUS);
        organization.setStatusInfo(createStatusInfoWithChangedTime(changedTime));
        
        // Create OrganizationNames with changed times
        Set<OrganizationName> names = new HashSet<>();
        OrganizationName primaryName = new OrganizationName();
        primaryName.setLanguage("EN");
        primaryName.setType("Name");
        primaryName.setValue("Changed Organization Ltd");
        primaryName.setOrganization(organization);
        primaryName.setStatusInfo(createStatusInfoWithChangedTime(changedTime));
        names.add(primaryName);

        // Create OrganizationDescriptions with changed times
        Set<OrganizationDescription> descriptions = new HashSet<>();
        OrganizationDescription description = new OrganizationDescription();
        description.setLanguage("EN");
        description.setType("Description");
        description.setValue("Changed organization description");
        description.setOrganization(organization);
        description.setStatusInfo(createStatusInfoWithChangedTime(changedTime));
        descriptions.add(description);

        // Create Emails with changed times
        Set<Email> emails = new HashSet<>();
        Email email = new Email();
        email.setValue("changed@example.com");
        email.setDescription("Changed contact email");
        email.setOrganization(organization);
        email.setStatusInfo(createStatusInfoWithChangedTime(changedTime));
        emails.add(email);

        // Create PhoneNumbers with changed times
        Set<PhoneNumber> phoneNumbers = new HashSet<>();
        PhoneNumber phoneNumber = new PhoneNumber();
        phoneNumber.setNumber("+987654321");
        phoneNumber.setLanguage("EN");
        phoneNumber.setPrefixNumber("+1");
        phoneNumber.setIsFinnishServiceNumber(false);
        phoneNumber.setOrganization(organization);
        phoneNumber.setStatusInfo(createStatusInfoWithChangedTime(changedTime));
        phoneNumbers.add(phoneNumber);

        // Create WebPages with changed times
        Set<WebPage> webPages = new HashSet<>();
        WebPage webPage = new WebPage();
        webPage.setUrl("https://www.changed-example.com");
        webPage.setLanguage("EN");
        webPage.setValue("Changed website");
        webPage.setOrganization(organization);
        webPage.setStatusInfo(createStatusInfoWithChangedTime(changedTime));
        webPages.add(webPage);

        // Create Address with changed time and nested street/post office box addresses
        Set<Address> addresses = new HashSet<>();
        Address address = new Address();
        address.setCountry("Changed Country");
        address.setType("Changed address type");
        address.setSubType("Changed address subtype");
        address.setOrganization(organization);
        address.setStatusInfo(createStatusInfoWithChangedTime(changedTime));
        
        // Add StreetAddress with all nested entities using working pattern
        StreetAddress streetAddress = createStreetAddressWithAllChangedEntities(changedTime);
        streetAddress.setAddress(address);
        Set<StreetAddress> streetAddresses = new HashSet<>();
        streetAddresses.add(streetAddress);
        address.setStreetAddresses(streetAddresses);  // ASSIGN collection instead of manual sync

        // Add PostOfficeBoxAddress with all nested entities using working pattern
        PostOfficeBoxAddress postOfficeBoxAddress = createPostOfficeBoxAddressWithAllChangedEntities(changedTime);
        postOfficeBoxAddress.setAddress(address);
        Set<PostOfficeBoxAddress> postOfficeBoxAddresses = new HashSet<>();
        postOfficeBoxAddresses.add(postOfficeBoxAddress);
        address.setPostOfficeBoxAddresses(postOfficeBoxAddresses);  // ASSIGN collection instead of manual sync

        addresses.add(address);

        // Assign all entity collections to organization using working pattern
        organization.setOrganizationNames(names);
        organization.setOrganizationDescriptions(descriptions);
        organization.setEmails(emails);
        organization.setPhoneNumbers(phoneNumbers);
        organization.setWebPages(webPages);
        organization.setAddresses(addresses);

        return organization;
    }

    // Comprehensive method to create company with ALL possible changed entities for complete test coverage
    public static Company createCompanyWithAllChangedEntities(String businessId, String name, LocalDateTime changedTime) {
        Company company = createStandardCompany(businessId, name, DEFAULT_COMPANY_FORM);
        company.setStatusInfo(createStatusInfoWithChangedTime(changedTime));
        
        // Create BusinessAddress entities with changed times
        Set<BusinessAddress> businessAddresses = new HashSet<>();
        BusinessAddress businessAddress = createBusinessAddress(changedTime, company);
        businessAddresses.add(businessAddress);
        
        // Create BusinessAuxiliaryName entities with changed times
        Set<BusinessAuxiliaryName> businessAuxiliaryNames = new HashSet<>();
        BusinessAuxiliaryName businessAuxiliaryName = new BusinessAuxiliaryName();
        businessAuxiliaryName.setSource(1L);
        businessAuxiliaryName.setOrdering(1L);
        businessAuxiliaryName.setVersion(1L);
        businessAuxiliaryName.setName("Test Auxiliary Name");
        businessAuxiliaryName.setLanguage("EN");
        businessAuxiliaryName.setRegistrationDate(FIXED_TEST_TIME);
        businessAuxiliaryName.setCompany(company);
        businessAuxiliaryName.setStatusInfo(createStatusInfoWithChangedTime(changedTime));
        businessAuxiliaryNames.add(businessAuxiliaryName);
        
        // Create BusinessIdChange entities with changed times
        Set<BusinessIdChange> businessIdChanges = new HashSet<>();
        BusinessIdChange businessIdChange = createBusinessIdChange(businessId, changedTime, company);
        businessIdChanges.add(businessIdChange);
        
        // Create BusinessLine entities with changed times
        Set<BusinessLine> businessLines = new HashSet<>();
        BusinessLine businessLine = new BusinessLine();
        businessLine.setSource(1L);
        businessLine.setOrdering(1L);
        businessLine.setVersion(1L);
        businessLine.setName("Test Business Line");
        businessLine.setLanguage("EN");
        businessLine.setRegistrationDate(FIXED_TEST_TIME);
        businessLine.setCompany(company);
        businessLine.setStatusInfo(createStatusInfoWithChangedTime(changedTime));
        businessLines.add(businessLine);
        
        // Create BusinessName entities with changed times
        Set<BusinessName> businessNames = new HashSet<>();
        BusinessName businessName = new BusinessName();
        businessName.setSource(1L);
        businessName.setOrdering(1L);
        businessName.setVersion(1L);
        businessName.setName("Changed Business Name");
        businessName.setLanguage("EN");
        businessName.setRegistrationDate(FIXED_TEST_TIME);
        businessName.setCompany(company);
        businessName.setStatusInfo(createStatusInfoWithChangedTime(changedTime));
        businessNames.add(businessName);
        
        // Create CompanyForm entities with changed times
        Set<CompanyForm> companyForms = new HashSet<>();
        CompanyForm companyForm = new CompanyForm();
        companyForm.setSource(1L);
        companyForm.setVersion(1L);
        companyForm.setName("Test Company Form");
        companyForm.setLanguage("EN");
        companyForm.setType(1L);
        companyForm.setRegistrationDate(FIXED_TEST_TIME);
        companyForm.setCompany(company);
        companyForm.setStatusInfo(createStatusInfoWithChangedTime(changedTime));
        companyForms.add(companyForm);
        
        // Create ContactDetail entities with changed times
        Set<ContactDetail> contactDetails = new HashSet<>();
        ContactDetail contactDetail = new ContactDetail();
        contactDetail.setSource(1L);
        contactDetail.setVersion(1L);
        contactDetail.setLanguage("EN");
        contactDetail.setValue("Test Contact Detail");
        contactDetail.setType("Email");
        contactDetail.setRegistrationDate(FIXED_TEST_TIME);
        contactDetail.setCompany(company);
        contactDetail.setStatusInfo(createStatusInfoWithChangedTime(changedTime));
        contactDetails.add(contactDetail);
        
        // Create Language entities with changed times
        Set<Language> languages = new HashSet<>();
        Language language = new Language();
        language.setSource(1L);
        language.setVersion(1L);
        language.setLanguage("EN");
        language.setName("English");
        language.setRegistrationDate(FIXED_TEST_TIME);
        language.setCompany(company);
        language.setStatusInfo(createStatusInfoWithChangedTime(changedTime));
        languages.add(language);
        
        // Create Liquidation entities with changed times
        Set<Liquidation> liquidations = new HashSet<>();
        Liquidation liquidation = new Liquidation();
        liquidation.setSource(1L);
        liquidation.setVersion(1L);
        liquidation.setName("Test Liquidation");
        liquidation.setLanguage("EN");
        liquidation.setType(1L);
        liquidation.setRegistrationDate(FIXED_TEST_TIME);
        liquidation.setCompany(company);
        liquidation.setStatusInfo(createStatusInfoWithChangedTime(changedTime));
        liquidations.add(liquidation);
        
        // Create RegisteredEntry entities with changed times
        Set<RegisteredEntry> registeredEntries = new HashSet<>();
        RegisteredEntry registeredEntry = new RegisteredEntry();
        registeredEntry.setDescription("Test Registered Entry");
        registeredEntry.setStatus(1L);
        registeredEntry.setRegister(1L);
        registeredEntry.setLanguage("EN");
        registeredEntry.setAuthority(1L);
        registeredEntry.setRegistrationDate(FIXED_TEST_TIME);
        registeredEntry.setCompany(company);
        registeredEntry.setStatusInfo(createStatusInfoWithChangedTime(changedTime));
        registeredEntries.add(registeredEntry);
        
        // Create RegisteredOffice entities with changed times
        Set<RegisteredOffice> registeredOffices = new HashSet<>();
        RegisteredOffice registeredOffice = new RegisteredOffice();
        registeredOffice.setSource(1L);
        registeredOffice.setOrdering(1L);
        registeredOffice.setVersion(1L);
        registeredOffice.setName("Test Registered Office");
        registeredOffice.setLanguage("EN");
        registeredOffice.setRegistrationDate(FIXED_TEST_TIME);
        registeredOffice.setCompany(company);
        registeredOffice.setStatusInfo(createStatusInfoWithChangedTime(changedTime));
        registeredOffices.add(registeredOffice);
        
        // Set all entity collections to company using direct setters
        company.setBusinessAddresses(businessAddresses);
        company.setBusinessAuxiliaryNames(businessAuxiliaryNames);
        company.setBusinessIdChanges(businessIdChanges);
        company.setBusinessLines(businessLines);
        company.setBusinessNames(businessNames);
        company.setCompanyForms(companyForms);
        company.setContactDetails(contactDetails);
        company.setLanguages(languages);
        company.setLiquidations(liquidations);
        company.setRegisteredEntries(registeredEntries);
        company.setRegisteredOffices(registeredOffices);
        
        return company;
    }

    private static BusinessIdChange createBusinessIdChange(String businessId, LocalDateTime changedTime, Company company) {
        BusinessIdChange businessIdChange = new BusinessIdChange();
        businessIdChange.setSource(1L);
        businessIdChange.setDescription("Business ID changed");
        businessIdChange.setReason("Test reason");
        businessIdChange.setChangeDate("2025-01-01");
        businessIdChange.setChange("1");
        businessIdChange.setOldBusinessId("1111111-1");
        businessIdChange.setNewBusinessId(businessId);
        businessIdChange.setLanguage("EN");
        businessIdChange.setCompany(company);
        businessIdChange.setStatusInfo(createStatusInfoWithChangedTime(changedTime));
        return businessIdChange;
    }

    private static BusinessAddress createBusinessAddress(LocalDateTime changedTime, Company company) {
        BusinessAddress businessAddress = new BusinessAddress();
        businessAddress.setSource(1L);
        businessAddress.setVersion(1L);
        businessAddress.setCareOf("Test Care Of");
        businessAddress.setStreet("Business Street 123");
        businessAddress.setPostCode("00100");
        businessAddress.setCity("Business City");
        businessAddress.setLanguage("EN");
        businessAddress.setType(1L);
        businessAddress.setCountry("Finland");
        businessAddress.setRegistrationDate(FIXED_TEST_TIME);
        businessAddress.setCompany(company);
        businessAddress.setStatusInfo(createStatusInfoWithChangedTime(changedTime));
        return businessAddress;
    }
}
