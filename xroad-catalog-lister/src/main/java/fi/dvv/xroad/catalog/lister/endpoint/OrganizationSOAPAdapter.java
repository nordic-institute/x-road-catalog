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

import fi.dvv.xroad.catalog.lister.endpoint.services.getcompanies.GetCompaniesRequest;
import fi.dvv.xroad.catalog.lister.endpoint.services.getcompanies.GetCompaniesService;
import fi.dvv.xroad.catalog.lister.endpoint.services.getorganizations.GetOrganizationsRequest;
import fi.dvv.xroad.catalog.lister.endpoint.services.getorganizations.GetOrganizationsService;
import fi.dvv.xroad.catalog.lister.endpoint.services.hascompanychanged.HasCompanyChangedRequest;
import fi.dvv.xroad.catalog.lister.endpoint.services.hascompanychanged.HasCompanyChangedService;
import fi.dvv.xroad.catalog.lister.endpoint.services.hasorganizationchanged.HasOrganizationChangedRequest;
import fi.dvv.xroad.catalog.lister.endpoint.services.hasorganizationchanged.HasOrganizationChangedService;
import fi.dvv.xroad.catalog.lister.service.CompanyService;
import fi.dvv.xroad.catalog.lister.service.OrganizationService;
import jakarta.xml.soap.SOAPException;
import org.niis.xrd4j.common.exception.XRd4JException;
import org.niis.xrd4j.common.message.ServiceRequest;
import org.niis.xrd4j.common.message.ServiceResponse;

public class OrganizationSOAPAdapter {

    private final GetOrganizationsService getOrganizationsService;
    private final HasOrganizationChangedService hasOrganizationChangedService;
    private final GetCompaniesService getCompaniesService;
    private final HasCompanyChangedService hasCompanyChangedService;

    public OrganizationSOAPAdapter(OrganizationService organizationService, CompanyService companyService) {
        this.getOrganizationsService = new GetOrganizationsService(organizationService);
        this.hasOrganizationChangedService = new HasOrganizationChangedService(organizationService);
        this.getCompaniesService = new GetCompaniesService(companyService);
        this.hasCompanyChangedService = new HasCompanyChangedService(companyService);
    }

    public ServiceResponse handleRequest(ServiceRequest request) throws SOAPException, XRd4JException {
        switch (request.getProducer().getServiceCode()) {
            case "GetOrganizations":
                @SuppressWarnings("unchecked")
                ServiceRequest<GetOrganizationsRequest> getOrganizationsRequest = (ServiceRequest<GetOrganizationsRequest>) request;
                return getOrganizationsService.execute(getOrganizationsRequest);
            case "HasOrganizationChanged":
                @SuppressWarnings("unchecked")
                ServiceRequest<HasOrganizationChangedRequest> hasOrganizationChangedRequest =
                        (ServiceRequest<HasOrganizationChangedRequest>) request;
                return hasOrganizationChangedService.execute(hasOrganizationChangedRequest);
            case "GetCompanies":
                @SuppressWarnings("unchecked")
                ServiceRequest<GetCompaniesRequest> getCompaniesRequest = (ServiceRequest<GetCompaniesRequest>) request;
                return getCompaniesService.execute(getCompaniesRequest);
            case "HasCompanyChanged":
                @SuppressWarnings("unchecked")
                ServiceRequest<HasCompanyChangedRequest> hasCompanyChangedRequest = (ServiceRequest<HasCompanyChangedRequest>) request;
                return hasCompanyChangedService.execute(hasCompanyChangedRequest);
            default:
                throw new XRd4JException("Unknown service: " + request.getProducer().getServiceCode());
        }
    }
}
