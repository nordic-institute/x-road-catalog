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
import org.niis.xroad.catalog.lister.endpoint.services.listmembers.ListMembersService;
import org.niis.xroad.catalog.lister.endpoint.services.listmembers.types.ListMembersRequest;
import org.niis.xroad.catalog.lister.service.CatalogService;

public class SOAPAdapter extends AbstractAdapterServlet {
    
    private final transient ListMembersService listMembersService;
    
    public SOAPAdapter(CatalogService catalogService) {
        super();
        this.listMembersService = new ListMembersService(catalogService);
    }

    @Override
    protected ServiceResponse<?, ?> handleRequest(ServiceRequest<?> request) throws SOAPException, XRd4JException {
        switch (request.getProducer().getServiceCode()) {
            case "ListMembers":
                @SuppressWarnings("unchecked")
                ServiceRequest<ListMembersRequest> listMembersRequest = (ServiceRequest<ListMembersRequest>) request;
                return listMembersService.execute(listMembersRequest);
            default:
                throw new XRd4JException("Unknown service: " + request.getProducer().getServiceCode());
        }
    }

    @Override
    protected String getWSDLPath() {
        return "services.wsdl";
    }
}
