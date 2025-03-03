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

import fi.dvv.xroad.catalog.lister.dto.LastOrganizationCollectionData;
import fi.dvv.xroad.catalog.persistence.entity.Organization;
import fi.dvv.xroad.catalog.persistence.repository.CompanyRepository;
import fi.dvv.xroad.catalog.persistence.repository.OrganizationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

import static org.niis.xroad.catalog.lister.util.ServiceUtil.toLocalDateTime;

@Component
@Transactional
public class OrganizationServiceImpl implements OrganizationService {

    @Autowired
    OrganizationRepository organizationRepository;

    @Autowired
    CompanyRepository companyRepository;

    @Override
    public LastOrganizationCollectionData getLastOrganizationCollectionData() {
        return LastOrganizationCollectionData.builder()
                .organizationsLastFetched(toLocalDateTime(organizationRepository.findLatestFetched()))
                .companiesLastFetched(toLocalDateTime(companyRepository.findLatestFetched())).build();
    }

    @Override
    public Iterable<Organization> getOrganizations(String businessCode) {
        return organizationRepository.findAllByBusinessCode(businessCode);
    }

    @Override
    public Optional<Organization> getOrganization(String guid) {
        return organizationRepository.findAnyByOrganizationGuid(guid);
    }

}
