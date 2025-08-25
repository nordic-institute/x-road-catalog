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

import fi.dvv.xroad.catalog.lister.endpoint.OrganizationSOAPAdapter;
import jakarta.xml.soap.SOAPException;
import lombok.extern.slf4j.Slf4j;
import org.niis.xrd4j.common.exception.XRd4JException;
import org.niis.xrd4j.common.message.ErrorMessage;
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
import org.niis.xroad.catalog.lister.service.CatalogService;
import fi.dvv.xroad.catalog.lister.service.OrganizationService;
import fi.dvv.xroad.catalog.lister.service.CompanyService;

import java.util.Optional;

@Slf4j
public class SOAPAdapter extends AbstractAdapterServlet {
    
    private final transient ListMembersService listMembersService;
    private final transient GetErrorsService getErrorsService;
    private final transient GetOpenAPIService getOpenAPIService;
    private final transient GetServiceTypeService getServiceTypeService;
    private final transient GetWsdlService getWsdlService;
    private final transient IsProviderService isProviderService;
    private final transient Optional<OrganizationSOAPAdapter> organizationSOAPAdapter;

    
    public SOAPAdapter(CatalogService catalogService, OrganizationService organizationService, CompanyService companyService) {
        super();
        this.listMembersService = new ListMembersService(catalogService);
        this.getErrorsService = new GetErrorsService(catalogService);
        this.getOpenAPIService = new GetOpenAPIService(catalogService);
        this.getServiceTypeService = new GetServiceTypeService(catalogService);
        this.getWsdlService = new GetWsdlService(catalogService);
        this.isProviderService = new IsProviderService(catalogService);
        this.organizationSOAPAdapter =
                organizationService != null && companyService != null
                        ? Optional.of(new OrganizationSOAPAdapter(organizationService, companyService))
                        : Optional.empty();
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
            case "HasOrganizationChanged":
            case "GetCompanies":
            case "HasCompanyChanged":
                // Organization and Company services are currently only available with the FI profile
                if (organizationSOAPAdapter.isEmpty()) {
                    log.warn("Organization and Company components not initialised, request {} unavailable",
                            request.getProducer().getServiceCode());
                    request.setErrorMessage(new ErrorMessage("SOAP-ENV:Server",
                            "Unknown service: " + request.getProducer().getServiceCode(), null, null));
                    throw new XRd4JException("Unknown service: " + request.getProducer().getServiceCode());
                }
                return organizationSOAPAdapter.get().handleRequest(request);
            default:
                request.setErrorMessage(new ErrorMessage("SOAP-ENV:Server",
                        "Unknown service: " + request.getProducer().getServiceCode(), null, null));
                throw new XRd4JException("Unknown service: " + request.getProducer().getServiceCode());
        }
    }

    @Override
    protected String getWSDLPath() {
        return "services.wsdl";
    }
}
