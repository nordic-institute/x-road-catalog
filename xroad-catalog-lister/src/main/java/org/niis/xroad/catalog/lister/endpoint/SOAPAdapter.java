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
package org.niis.xroad.catalog.lister.endpoint;

import jakarta.xml.soap.SOAPException;
import org.niis.xrd4j.common.exception.XRd4JException;
import org.niis.xrd4j.common.message.ServiceRequest;
import org.niis.xrd4j.common.message.ServiceResponse;
import org.niis.xrd4j.server.AbstractAdapterServlet;
import org.niis.xroad.catalog.lister.endpoint.services.geterrors.GetErrorsService;
import org.niis.xroad.catalog.lister.endpoint.services.geterrors.GetErrorsRequest;
import org.niis.xroad.catalog.lister.endpoint.services.getopenapi.GetOpenAPIService;
import org.niis.xroad.catalog.lister.endpoint.services.getopenapi.GetOpenAPIRequest;
import org.niis.xroad.catalog.lister.endpoint.services.getservicetype.GetServiceTypeService;
import org.niis.xroad.catalog.lister.endpoint.services.getservicetype.GetServiceTypeRequest;
import org.niis.xroad.catalog.lister.endpoint.services.getwsdl.GetWsdlService;
import org.niis.xroad.catalog.lister.endpoint.services.getwsdl.GetWsdlRequest;
import org.niis.xroad.catalog.lister.endpoint.services.isprovider.IsProviderService;
import org.niis.xroad.catalog.lister.endpoint.services.isprovider.IsProviderRequest;
import org.niis.xroad.catalog.lister.endpoint.services.listmembers.ListMembersService;
import org.niis.xroad.catalog.lister.endpoint.services.listmembers.ListMembersRequest;
import fi.dvv.xroad.catalog.lister.endpoint.services.getorganizations.GetOrganizationsService;
import fi.dvv.xroad.catalog.lister.endpoint.services.getorganizations.GetOrganizationsRequest;
import fi.dvv.xroad.catalog.lister.endpoint.services.hasorganizationchanged.HasOrganizationChangedService;
import fi.dvv.xroad.catalog.lister.endpoint.services.hasorganizationchanged.HasOrganizationChangedRequest;
import fi.dvv.xroad.catalog.lister.endpoint.services.getcompanies.GetCompaniesService;
import fi.dvv.xroad.catalog.lister.endpoint.services.getcompanies.GetCompaniesRequest;
import fi.dvv.xroad.catalog.lister.endpoint.services.hascompanychanged.HasCompanyChangedService;
import fi.dvv.xroad.catalog.lister.endpoint.services.hascompanychanged.HasCompanyChangedRequest;
import org.niis.xroad.catalog.lister.service.CatalogService;
import fi.dvv.xroad.catalog.lister.service.OrganizationService;
import fi.dvv.xroad.catalog.lister.service.CompanyService;

public class SOAPAdapter extends AbstractAdapterServlet {
    
    private final transient ListMembersService listMembersService;
    private final transient GetErrorsService getErrorsService;
    private final transient GetOpenAPIService getOpenAPIService;
    private final transient GetServiceTypeService getServiceTypeService;
    private final transient GetWsdlService getWsdlService;
    private final transient IsProviderService isProviderService;
    private final transient GetOrganizationsService getOrganizationsService;
    private final transient HasOrganizationChangedService hasOrganizationChangedService;
    private final transient GetCompaniesService getCompaniesService;
    private final transient HasCompanyChangedService hasCompanyChangedService;
    
    public SOAPAdapter(CatalogService catalogService, OrganizationService organizationService, CompanyService companyService) {
        super();
        this.listMembersService = new ListMembersService(catalogService);
        this.getErrorsService = new GetErrorsService(catalogService);
        this.getOpenAPIService = new GetOpenAPIService(catalogService);
        this.getServiceTypeService = new GetServiceTypeService(catalogService);
        this.getWsdlService = new GetWsdlService(catalogService);
        this.isProviderService = new IsProviderService(catalogService);
        this.getOrganizationsService = new GetOrganizationsService(organizationService);
        this.hasOrganizationChangedService = new HasOrganizationChangedService(organizationService);
        this.getCompaniesService = new GetCompaniesService(companyService);
        this.hasCompanyChangedService = new HasCompanyChangedService(companyService);
    }

    @Override
    protected ServiceResponse handleRequest(ServiceRequest request) throws SOAPException, XRd4JException {
        switch (request.getProducer().getServiceCode()) {
            case "ListMembers":
                @SuppressWarnings("unchecked")
                ServiceRequest<ListMembersRequest> listMembersRequest = (ServiceRequest<ListMembersRequest>) request;
                return listMembersService.execute(listMembersRequest);
            case "GetErrors":
                @SuppressWarnings("unchecked")
                ServiceRequest<GetErrorsRequest> getErrorsRequest = (ServiceRequest<GetErrorsRequest>) request;
                return getErrorsService.execute(getErrorsRequest);
            case "GetOpenAPI":
                @SuppressWarnings("unchecked")
                ServiceRequest<GetOpenAPIRequest> getOpenAPIRequest = (ServiceRequest<GetOpenAPIRequest>) request;
                return getOpenAPIService.execute(getOpenAPIRequest);
            case "GetServiceType":
                @SuppressWarnings("unchecked")
                ServiceRequest<GetServiceTypeRequest> getServiceTypeRequest = (ServiceRequest<GetServiceTypeRequest>) request;
                return getServiceTypeService.execute(getServiceTypeRequest);
            case "GetWsdl":
                @SuppressWarnings("unchecked")
                ServiceRequest<GetWsdlRequest> getWsdlRequest = (ServiceRequest<GetWsdlRequest>) request;
                return getWsdlService.execute(getWsdlRequest);
            case "IsProvider":
                @SuppressWarnings("unchecked")
                ServiceRequest<IsProviderRequest> isProviderRequest = (ServiceRequest<IsProviderRequest>) request;
                return isProviderService.execute(isProviderRequest);
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

    @Override
    protected String getWSDLPath() {
        return "services.wsdl";
    }
}
