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
package fi.dvv.xroad.catalog.lister.endpoint.services.hasorganizationchanged;

import fi.dvv.xroad.catalog.lister.dto.ChangeResult;
import fi.dvv.xroad.catalog.persistence.entity.Address;
import fi.dvv.xroad.catalog.persistence.entity.Email;
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
import fi.dvv.xroad.catalog.persistence.entity.Street;
import fi.dvv.xroad.catalog.persistence.entity.StreetAddress;
import fi.dvv.xroad.catalog.persistence.entity.StreetAddressAdditionalInformation;
import fi.dvv.xroad.catalog.persistence.entity.StreetAddressMunicipality;
import fi.dvv.xroad.catalog.persistence.entity.StreetAddressMunicipalityName;
import fi.dvv.xroad.catalog.persistence.entity.StreetAddressPostOffice;
import fi.dvv.xroad.catalog.persistence.entity.WebPage;

import java.time.LocalDateTime;
import java.util.Set;

/**
 * FIXME: Replace this in-memory change detection logic with a database query-based solution
 * for better performance and scalability. Currently, this loads full entity graphs into memory
 * to check for changes, which is inefficient for large datasets.
 */
public class OrganizationChangeResult extends ChangeResult {
    private static final String ORGANIZATION = "Organization";
    private static final String ORGANIZATION_NAME = "OrganizationName";
    private static final String ORGANIZATION_DESCRIPTION = "OrganizationDescription";
    private static final String EMAIL = "Email";
    private static final String PHONE_NUMBER = "PhoneNumber";
    private static final String WEB_PAGE = "WebPage";
    private static final String ADDRESS = "Address";
    private static final String STREET_ADDRESS = "StreetAddress";
    private static final String STREET = "Street";
    private static final String STREET_ADDRESS_POST_OFFICE = "StreetAddress PostOffice";
    private static final String STREET_ADDRESS_MUNICIPALITY = "StreetAddress Municipality";
    private static final String STREET_ADDRESS_MUNICIPALITY_NAME = "StreetAddress Municipality Name";
    private static final String STREET_ADDRESS_ADDITIONAL_INFORMATION = "StreetAddress AdditionalInformation";
    private static final String POST_OFFICE_BOX_ADDRESS = "PostOfficeBoxAddress";
    private static final String POST_OFFICE_BOX_ADDRESS_POST_OFFICE = "PostOfficeBoxAddress PostOffice";
    private static final String POST_OFFICE_BOX_ADDRESS_POST_OFFICE_BOX = "PostOfficeBoxAddress PostOfficeBox";
    private static final String POST_OFFICE_BOX_ADDRESS_ADDITIONAL_INFORMATION = "PostOfficeBoxAddress AdditionalInformation";
    private static final String POST_OFFICE_BOX_ADDRESS_MUNICIPALITY = "PostOfficeBoxAddress Municipality";
    private static final String POST_OFFICE_BOX_ADDRESS_MUNICIPALITY_NAME = "PostOfficeBoxAddress Municipality Name";

    public OrganizationChangeResult(Organization organization, LocalDateTime startDateTime, LocalDateTime endDateTime) {
        super();
        
        Set<OrganizationName> organizationNames = organization.getAllOrganizationNames();
        Set<OrganizationDescription> organizationDescriptions = organization.getAllOrganizationDescriptions();
        Set<Email> emails = organization.getAllEmails();
        Set<PhoneNumber> phoneNumbers = organization.getAllPhoneNumbers();
        Set<WebPage> webPages = organization.getAllWebPages();
        Set<Address> addresses = organization.getAllAddresses();

        if (organization.getStatusInfo().getChanged().isAfter(startDateTime)
                && organization.getStatusInfo().getChanged().isBefore(endDateTime)) {
            changedValueNames.add(ORGANIZATION);
        }

        if (organizationNames.stream().anyMatch(obj -> obj.getStatusInfo().getChanged().isAfter(startDateTime)
                && obj.getStatusInfo().getChanged().isBefore(endDateTime))) {
            changedValueNames.add(ORGANIZATION_NAME);
        }

        if (organizationDescriptions.stream().anyMatch(obj -> obj.getStatusInfo().getChanged().isAfter(startDateTime)
                && obj.getStatusInfo().getChanged().isBefore(endDateTime))) {
            changedValueNames.add(ORGANIZATION_DESCRIPTION);
        }

        if (emails.stream().anyMatch(obj -> obj.getStatusInfo().getChanged().isAfter(startDateTime)
                && obj.getStatusInfo().getChanged().isBefore(endDateTime))) {
            changedValueNames.add(EMAIL);
        }

        if (phoneNumbers.stream().anyMatch(obj -> obj.getStatusInfo().getChanged().isAfter(startDateTime)
                && obj.getStatusInfo().getChanged().isBefore(endDateTime))) {
            changedValueNames.add(PHONE_NUMBER);
        }

        if (webPages.stream().anyMatch(obj -> obj.getStatusInfo().getChanged().isAfter(startDateTime)
                && obj.getStatusInfo().getChanged().isBefore(endDateTime))) {
            changedValueNames.add(WEB_PAGE);
        }

        checkAddressChanges(addresses, startDateTime, endDateTime);
    }

    private void checkAddressChanges(Set<Address> addresses, LocalDateTime startDateTime, LocalDateTime endDateTime) {
        addresses.forEach(address -> {
            if (address.getStatusInfo().getChanged().isAfter(startDateTime)
                    && address.getStatusInfo().getChanged().isBefore(endDateTime)) {
                changedValueNames.add(ADDRESS);
            }

            Set<StreetAddress> streetAddresses = address.getAllStreetAddresses();
            streetAddresses.forEach(streetAddress -> checkStreetAddressChanges(streetAddress, startDateTime, endDateTime));

            Set<PostOfficeBoxAddress> postOfficeBoxAddresses = address.getAllPostOfficeBoxAddresses();
            postOfficeBoxAddresses.forEach(postOfficeBoxAddress ->
                    checkPostOfficeBoxAddressChanges(postOfficeBoxAddress, startDateTime, endDateTime));
        });
    }

    private void checkStreetAddressChanges(StreetAddress streetAddress, LocalDateTime startDateTime, LocalDateTime endDateTime) {
        if (streetAddress.getStatusInfo().getChanged().isAfter(startDateTime)
                && streetAddress.getStatusInfo().getChanged().isBefore(endDateTime)) {
            changedValueNames.add(STREET_ADDRESS);
        }

        Set<Street> streets = streetAddress.getAllStreets();
        streets.forEach(street -> {
            if (street.getStatusInfo().getChanged().isAfter(startDateTime)
                    && street.getStatusInfo().getChanged().isBefore(endDateTime)) {
                changedValueNames.add(STREET);
            }
        });

        Set<StreetAddressPostOffice> postOffices = streetAddress.getAllPostOffices();
        postOffices.forEach(postOffice -> {
            if (postOffice.getStatusInfo().getChanged().isAfter(startDateTime)
                    && postOffice.getStatusInfo().getChanged().isBefore(endDateTime)) {
                changedValueNames.add(STREET_ADDRESS_POST_OFFICE);
            }
        });

        Set<StreetAddressMunicipality> municipalities = streetAddress.getAllMunicipalities();
        municipalities.forEach(municipality -> {
            if (municipality.getStatusInfo().getChanged().isAfter(startDateTime)
                    && municipality.getStatusInfo().getChanged().isBefore(endDateTime)) {
                changedValueNames.add(STREET_ADDRESS_MUNICIPALITY);
            }

            Set<StreetAddressMunicipalityName> municipalityNames = municipality.getAllMunicipalityNames();
            municipalityNames.forEach(municipalityName -> {
                if (municipalityName.getStatusInfo().getChanged().isAfter(startDateTime)
                        && municipalityName.getStatusInfo().getChanged().isBefore(endDateTime)) {
                    changedValueNames.add(STREET_ADDRESS_MUNICIPALITY_NAME);
                }
            });
        });

        Set<StreetAddressAdditionalInformation> additionalInfoList = streetAddress.getAllAdditionalInformation();
        additionalInfoList.forEach(additionalInfo -> {
            if (additionalInfo.getStatusInfo().getChanged().isAfter(startDateTime)
                    && additionalInfo.getStatusInfo().getChanged().isBefore(endDateTime)) {
                changedValueNames.add(STREET_ADDRESS_ADDITIONAL_INFORMATION);
            }
        });
    }

    private void checkPostOfficeBoxAddressChanges(PostOfficeBoxAddress postOfficeBoxAddress,
            LocalDateTime startDateTime, LocalDateTime endDateTime) {
        if (postOfficeBoxAddress.getStatusInfo().getChanged().isAfter(startDateTime)
                && postOfficeBoxAddress.getStatusInfo().getChanged().isBefore(endDateTime)) {
            changedValueNames.add(POST_OFFICE_BOX_ADDRESS);
        }

        Set<PostOffice> postOffices = postOfficeBoxAddress.getAllPostOffices();
        postOffices.forEach(postOffice -> {
            if (postOffice.getStatusInfo().getChanged().isAfter(startDateTime)
                    && postOffice.getStatusInfo().getChanged().isBefore(endDateTime)) {
                changedValueNames.add(POST_OFFICE_BOX_ADDRESS_POST_OFFICE);
            }
        });

        Set<PostOfficeBox> postOfficeBoxes = postOfficeBoxAddress.getAllPostOfficeBoxes();
        postOfficeBoxes.forEach(postOfficeBox -> {
            if (postOfficeBox.getStatusInfo().getChanged().isAfter(startDateTime)
                    && postOfficeBox.getStatusInfo().getChanged().isBefore(endDateTime)) {
                changedValueNames.add(POST_OFFICE_BOX_ADDRESS_POST_OFFICE_BOX);
            }
        });

        Set<PostOfficeBoxAddressAdditionalInformation> additionalInfoList =
                postOfficeBoxAddress.getAllAdditionalInformation();
        additionalInfoList.forEach(additionalInfo -> {
            if (additionalInfo.getStatusInfo().getChanged().isAfter(startDateTime)
                    && additionalInfo.getStatusInfo().getChanged().isBefore(endDateTime)) {
                changedValueNames.add(POST_OFFICE_BOX_ADDRESS_ADDITIONAL_INFORMATION);
            }
        });

        Set<PostOfficeBoxAddressMunicipality> municipalities = postOfficeBoxAddress.getAllMunicipalities();
        municipalities.forEach(municipality -> {
            if (municipality.getStatusInfo().getChanged().isAfter(startDateTime)
                    && municipality.getStatusInfo().getChanged().isBefore(endDateTime)) {
                changedValueNames.add(POST_OFFICE_BOX_ADDRESS_MUNICIPALITY);
            }

            Set<PostOfficeBoxAddressMunicipalityName> municipalityNames = municipality.getAllMunicipalityNames();
            municipalityNames.forEach(municipalityName -> {
                if (municipalityName.getStatusInfo().getChanged().isAfter(startDateTime)
                        && municipalityName.getStatusInfo().getChanged().isBefore(endDateTime)) {
                    changedValueNames.add(POST_OFFICE_BOX_ADDRESS_MUNICIPALITY_NAME);
                }
            });
        });
    }
}
