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

        if (addresses.stream().anyMatch(obj -> obj.getStatusInfo().getChanged().isAfter(startDateTime)
                && obj.getStatusInfo().getChanged().isBefore(endDateTime))) {
            changedValueNames.add(ADDRESS);
        }
    }
}
