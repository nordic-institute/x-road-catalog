/**
 * The MIT License
 *
 * Copyright (c) 2023- Nordic Institute for Interoperability Solutions (NIIS)
 * Copyright (c) 2016-2023 Finnish Digital Agency
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package fi.vrk.xroad.catalog.persistence;

import fi.vrk.xroad.catalog.persistence.entity.Company;
import fi.vrk.xroad.catalog.persistence.repository.CompanyRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Month;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
public class CompanyRepositoryTest {

    @Autowired
    CompanyRepository companyRepository;

    @Test
    public void testFindAllByBusinessId() {
        Set<Company> company = companyRepository.findAllByBusinessId("1710128-9");
        assertEquals(true, company.iterator().hasNext());
        assertEquals(1, company.size());
        assertNotNull(company.iterator().next().getStatusInfo());
        assertEquals("1710128-9", company.iterator().next().getBusinessId());
        assertEquals("OYJ", company.iterator().next().getCompanyForm());
        assertEquals("Gofore Oyj", company.iterator().next().getName());
        assertEquals("", company.iterator().next().getDetailsUri());
        assertEquals(1, company.iterator().next().getAllBusinessAddresses().size());
        assertEquals(1, company.iterator().next().getAllBusinessAuxiliaryNames().size());
        assertEquals(1, company.iterator().next().getAllBusinessIdChanges().size());
        assertEquals(1, company.iterator().next().getAllBusinessLines().size());
        assertEquals(1, company.iterator().next().getAllBusinessNames().size());
        assertEquals(1, company.iterator().next().getAllCompanyForms().size());
        assertEquals(1, company.iterator().next().getAllContactDetails().size());
        assertEquals(1, company.iterator().next().getAllLanguages().size());
        assertEquals(1, company.iterator().next().getAllLiquidations().size());
        assertEquals(1, company.iterator().next().getAllRegisteredEntries().size());
        assertEquals(1, company.iterator().next().getAllRegisteredOffices().size());
        assertEquals(LocalDate.of(2001, 6, 11), company.iterator().next().getRegistrationDate().toLocalDate());
    }

    @Test
    public void testFindLatestFetched() {
        LocalDateTime latestFetched = companyRepository.findLatestFetched();
        assertEquals(2020, latestFetched.getYear());
        assertEquals(Month.MAY, latestFetched.getMonth());
        assertEquals(4, latestFetched.getDayOfMonth());
        assertEquals(11, latestFetched.getHour());
    }

}


