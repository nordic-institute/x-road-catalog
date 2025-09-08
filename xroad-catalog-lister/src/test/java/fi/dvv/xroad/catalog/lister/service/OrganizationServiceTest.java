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
package fi.dvv.xroad.catalog.lister.service;

import com.google.common.collect.Iterables;
import fi.dvv.xroad.catalog.lister.dto.LastOrganizationCollectionData;
import fi.dvv.xroad.catalog.persistence.entity.Organization;
import org.junit.jupiter.api.Test;
import org.niis.xroad.catalog.lister.ListerApplication;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(classes = ListerApplication.class)
@ActiveProfiles({"test", "fi"})
@Transactional
public class OrganizationServiceTest {

    @Autowired
    OrganizationService organizationService;

    @Test
    public void testGetLastCollectionData() {
        LastOrganizationCollectionData lastCollectionData = organizationService
                .getLastOrganizationCollectionData();
        assertEquals(2016, lastCollectionData.getOrganizationsLastFetched().getYear());
        assertEquals(2020, lastCollectionData.getCompaniesLastFetched().getYear());
    }

    @Test
    public void testGetOrganizations() {
        Iterable<Organization> organizations = organizationService.getOrganizations("0123456-9");
        assertEquals(1, Iterables.size(organizations));
        assertEquals(1, organizations.iterator().next().getAllOrganizationNames().size());
        assertEquals(1, organizations.iterator().next().getAllOrganizationDescriptions().size());
        assertEquals(1, organizations.iterator().next().getAllEmails().size());
        assertEquals(1, organizations.iterator().next().getAllPhoneNumbers().size());
        assertEquals(1, organizations.iterator().next().getAllWebPages().size());
        assertEquals(1, organizations.iterator().next().getAllAddresses().size());
        assertEquals("0123456-9", organizations.iterator().next().getBusinessCode());
        assertEquals("abcdef123456", organizations.iterator().next().getGuid());
        assertEquals("Published", organizations.iterator().next().getPublishingStatus());
        assertEquals("Municipality", organizations.iterator().next().getOrganizationType());
        assertEquals("Vaasan kaupunki",
                organizations.iterator().next().getAllOrganizationNames().iterator().next().getValue());
        assertEquals("Vaasa on yli 67 000 asukkaan voimakkaasti kasvava kaupunki",
                organizations.iterator().next().getAllOrganizationDescriptions().iterator().next()
                        .getValue());
        assertEquals("vaasa@vaasa.fi",
                organizations.iterator().next().getAllEmails().iterator().next().getValue());
        assertEquals("62249111",
                organizations.iterator().next().getAllPhoneNumbers().iterator().next().getNumber());
        assertEquals("https://www.vaasa.fi/",
                organizations.iterator().next().getAllWebPages().iterator().next().getUrl());
        assertEquals("Street",
                organizations.iterator().next().getAllAddresses().iterator().next().getSubType());
        assertEquals("64200", organizations.iterator().next().getAllAddresses().iterator().next()
                .getAllStreetAddresses().iterator().next().getPostalCode());
        assertEquals("Motellikuja", organizations.iterator().next().getAllAddresses().iterator().next()
                .getAllStreetAddresses().iterator().next().getAllStreets().iterator().next()
                .getValue());
        assertEquals("64200", organizations.iterator().next().getAllAddresses().iterator().next()
                .getAllPostOfficeBoxAddresses().iterator().next().getPostalCode());
        assertEquals("NIVALA", organizations.iterator().next().getAllAddresses().iterator().next()
                .getAllPostOfficeBoxAddresses().iterator().next().getAllPostOffices().iterator().next()
                .getValue());
        assertEquals("NIVALA", organizations.iterator().next().getAllAddresses().iterator().next()
                .getAllPostOfficeBoxAddresses().iterator().next().getAllPostOfficeBoxes().iterator()
                .next().getValue());
        assertEquals("Kaupungintalo/kaupunginjohtaja",
                organizations.iterator().next().getAllAddresses().iterator().next()
                        .getAllPostOfficeBoxAddresses().iterator().next()
                        .getAllAdditionalInformation().iterator().next().getValue());
        assertEquals("545", organizations.iterator().next().getAllAddresses().iterator().next()
                .getAllPostOfficeBoxAddresses().iterator().next().getAllMunicipalities().iterator()
                .next().getCode());
        assertEquals("Nivala", organizations.iterator().next().getAllAddresses().iterator().next()
                .getAllPostOfficeBoxAddresses().iterator().next().getAllMunicipalities().iterator()
                .next()
                .getAllMunicipalityNames().iterator().next().getValue());
    }

    @Test
    public void testGetOrganization() {
        Optional<Organization> organization = organizationService.getOrganization("abcdef123456");
        assertEquals(true, organization.isPresent());
        assertEquals(1, organization.get().getAllOrganizationNames().size());
        assertEquals(1, organization.get().getAllOrganizationDescriptions().size());
        assertEquals(1, organization.get().getAllEmails().size());
        assertEquals(1, organization.get().getAllPhoneNumbers().size());
        assertEquals(1, organization.get().getAllWebPages().size());
        assertEquals(1, organization.get().getAllAddresses().size());
        assertEquals("0123456-9", organization.get().getBusinessCode());
        assertEquals("abcdef123456", organization.get().getGuid());
        assertEquals("Published", organization.get().getPublishingStatus());
        assertEquals("Municipality", organization.get().getOrganizationType());
        assertEquals("Vaasan kaupunki",
                organization.get().getAllOrganizationNames().iterator().next().getValue());
        assertEquals("Vaasa on yli 67 000 asukkaan voimakkaasti kasvava kaupunki",
                organization.get().getAllOrganizationDescriptions().iterator().next().getValue());
        assertEquals("vaasa@vaasa.fi", organization.get().getAllEmails().iterator().next().getValue());
        assertEquals("62249111", organization.get().getAllPhoneNumbers().iterator().next().getNumber());
        assertEquals("https://www.vaasa.fi/", organization.get().getAllWebPages().iterator().next().getUrl());
        assertEquals("Street", organization.get().getAllAddresses().iterator().next().getSubType());
        assertEquals("64200", organization.get().getAllAddresses().iterator().next()
                .getAllStreetAddresses().iterator().next().getPostalCode());
        assertEquals("Motellikuja", organization.get().getAllAddresses().iterator().next()
                .getAllStreetAddresses().iterator().next().getAllStreets().iterator().next()
                .getValue());
        assertEquals("64200", organization.get().getAllAddresses().iterator().next()
                .getAllPostOfficeBoxAddresses().iterator().next().getPostalCode());
        assertEquals("NIVALA", organization.get().getAllAddresses().iterator().next()
                .getAllPostOfficeBoxAddresses().iterator().next().getAllPostOffices().iterator().next()
                .getValue());
        assertEquals("NIVALA", organization.get().getAllAddresses().iterator().next()
                .getAllPostOfficeBoxAddresses().iterator().next().getAllPostOfficeBoxes().iterator()
                .next().getValue());
        assertEquals("Kaupungintalo/kaupunginjohtaja", organization.get().getAllAddresses().iterator().next()
                .getAllPostOfficeBoxAddresses().iterator().next().getAllAdditionalInformation()
                .iterator().next().getValue());
        assertEquals("545", organization.get().getAllAddresses().iterator().next()
                .getAllPostOfficeBoxAddresses().iterator().next().getAllMunicipalities().iterator()
                .next().getCode());
        assertEquals("Nivala", organization.get().getAllAddresses().iterator().next()
                .getAllPostOfficeBoxAddresses().iterator().next().getAllMunicipalities().iterator()
                .next()
                .getAllMunicipalityNames().iterator().next().getValue());
    }
}
