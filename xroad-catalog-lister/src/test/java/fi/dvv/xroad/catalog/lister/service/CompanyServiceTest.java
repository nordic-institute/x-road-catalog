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

import fi.dvv.xroad.catalog.persistence.entity.Company;
import org.junit.jupiter.api.Test;
import org.niis.xroad.catalog.lister.ListerApplication;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(classes = ListerApplication.class)
@ActiveProfiles("test")
@Transactional
public class CompanyServiceTest {

    @Autowired
    CompanyService companyService;

    @Test
    public void testGetCompanies() {
        Iterable<Company> companies = companyService.getCompanies("1710128-9");
        assertEquals(1, companies.iterator().next().getAllBusinessAddresses().size());
        assertEquals(1, companies.iterator().next().getAllBusinessAuxiliaryNames().size());
        assertEquals(1, companies.iterator().next().getAllBusinessIdChanges().size());
        assertEquals(1, companies.iterator().next().getAllBusinessLines().size());
        assertEquals(1, companies.iterator().next().getAllBusinessNames().size());
        assertEquals(1, companies.iterator().next().getAllCompanyForms().size());
        assertEquals(1, companies.iterator().next().getAllContactDetails().size());
        assertEquals(1, companies.iterator().next().getAllLanguages().size());
        assertEquals(1, companies.iterator().next().getAllLiquidations().size());
        assertEquals(1, companies.iterator().next().getAllRegisteredEntries().size());
        assertEquals(1, companies.iterator().next().getAllRegisteredOffices().size());
        assertEquals("1710128-9", companies.iterator().next().getBusinessId());
        assertEquals("OYJ", companies.iterator().next().getCompanyForm());
        assertEquals("Gofore Oyj", companies.iterator().next().getName());
        assertEquals("Kalevantie 2",
                companies.iterator().next().getAllBusinessAddresses().iterator().next().getStreet());
        assertEquals("Solinor",
                companies.iterator().next().getAllBusinessAuxiliaryNames().iterator().next().getName());
        assertEquals("1796717-0", companies.iterator().next().getAllBusinessIdChanges().iterator().next()
                .getOldBusinessId());
        assertEquals("Dataprogrammering",
                companies.iterator().next().getAllBusinessLines().iterator().next().getName());
        assertEquals("FI", companies.iterator().next().getAllBusinessNames().iterator().next().getLanguage());
        assertEquals("Public limited company",
                companies.iterator().next().getAllCompanyForms().iterator().next().getName());
        assertEquals("EN", companies.iterator().next().getAllContactDetails().iterator().next().getLanguage());
        assertEquals("Finska", companies.iterator().next().getAllLanguages().iterator().next().getName());
        assertEquals("FI", companies.iterator().next().getAllLiquidations().iterator().next().getLanguage());
        assertEquals("Unregistered", companies.iterator().next().getAllRegisteredEntries().iterator().next()
                .getDescription());
        assertEquals("FI",
                companies.iterator().next().getAllRegisteredOffices().iterator().next().getLanguage());
    }
}
