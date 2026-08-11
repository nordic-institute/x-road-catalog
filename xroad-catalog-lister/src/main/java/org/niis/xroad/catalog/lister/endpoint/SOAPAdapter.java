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
import lombok.extern.slf4j.Slf4j;
import org.niis.xrd4j.common.exception.XRd4JException;
import org.niis.xrd4j.common.message.ErrorMessage;
import org.niis.xrd4j.common.message.ServiceRequest;
import org.niis.xrd4j.common.message.ServiceResponse;
import org.niis.xrd4j.server.AbstractAdapterServlet;
import org.niis.xroad.catalog.lister.endpoint.services.geterrors.GetErrorsService;
import org.niis.xroad.catalog.lister.endpoint.services.getopenapi.GetOpenAPIService;
import org.niis.xroad.catalog.lister.endpoint.services.getservicetype.GetServiceTypeService;
import org.niis.xroad.catalog.lister.endpoint.services.getwsdl.GetWsdlService;
import org.niis.xroad.catalog.lister.endpoint.services.isprovider.IsProviderService;
import org.niis.xroad.catalog.lister.endpoint.services.listmembers.ListMembersService;
import org.niis.xroad.catalog.lister.service.CatalogService;


/**
 * @deprecated Superseded by the V2 REST API ({@code org.niis.xroad.catalog.lister.v2}); scheduled for removal.
 */
@Deprecated(forRemoval = true)
@Slf4j
public class SOAPAdapter extends AbstractAdapterServlet {
    
    private final transient ListMembersService listMembersService;
    private final transient GetErrorsService getErrorsService;
    private final transient GetOpenAPIService getOpenAPIService;
    private final transient GetServiceTypeService getServiceTypeService;
    private final transient GetWsdlService getWsdlService;
    private final transient IsProviderService isProviderService;

    
    public SOAPAdapter(CatalogService catalogService) {
        super();
        this.listMembersService = new ListMembersService(catalogService);
        this.getErrorsService = new GetErrorsService(catalogService);
        this.getOpenAPIService = new GetOpenAPIService(catalogService);
        this.getServiceTypeService = new GetServiceTypeService(catalogService);
        this.getWsdlService = new GetWsdlService(catalogService);
        this.isProviderService = new IsProviderService(catalogService);
    }

    @Override
    @SuppressWarnings("unchecked")
    protected ServiceResponse handleRequest(ServiceRequest request) throws SOAPException, XRd4JException {
        return switch (request.getProducer().getServiceCode()) {
            case "ListMembers" -> listMembersService.execute(request);
            case "GetErrors" -> getErrorsService.execute(request);
            case "GetOpenAPI" -> getOpenAPIService.execute(request);
            case "GetServiceType" -> getServiceTypeService.execute(request);
            case "GetWsdl" -> getWsdlService.execute(request);
            case "IsProvider" -> isProviderService.execute(request);
            default -> {
                request.setErrorMessage(new ErrorMessage("SOAP-ENV:Server",
                        "Unknown service: " + request.getProducer().getServiceCode(), null, null));
                throw new XRd4JException("Unknown service: " + request.getProducer().getServiceCode());
            }
        };
    }

    @Override
    protected String getWSDLPath() {
        return "services.wsdl";
    }
}
