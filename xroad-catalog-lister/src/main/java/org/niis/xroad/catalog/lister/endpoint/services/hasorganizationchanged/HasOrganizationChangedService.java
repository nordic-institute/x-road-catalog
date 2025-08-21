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
package org.niis.xroad.catalog.lister.endpoint.services.hasorganizationchanged;

import jakarta.xml.soap.SOAPException;
import org.niis.xrd4j.common.exception.XRd4JException;
import org.niis.xrd4j.common.message.ErrorMessage;
import org.niis.xrd4j.common.message.ServiceRequest;
import org.niis.xrd4j.common.message.ServiceResponse;
import org.niis.xroad.catalog.lister.endpoint.ListerService;
import fi.dvv.xroad.catalog.lister.service.OrganizationService;
import fi.dvv.xroad.catalog.persistence.entity.Organization;

import java.util.Optional;

public class HasOrganizationChangedService implements ListerService<HasOrganizationChangedRequest, OrganizationChangeResult> {
    private static final HasOrganizationChangedRequestDeserializer REQUEST_DESERIALIZER = new HasOrganizationChangedRequestDeserializer();
    private static final HasOrganizationChangedResponseSerializer RESPONSE_SERIALIZER = new HasOrganizationChangedResponseSerializer();
    private final OrganizationService organizationService;

    public HasOrganizationChangedService(final OrganizationService organizationService) {
        this.organizationService = organizationService;
    }

    public ServiceResponse<HasOrganizationChangedRequest, OrganizationChangeResult> execute(
            ServiceRequest<HasOrganizationChangedRequest> request)
            throws XRd4JException, SOAPException {
        REQUEST_DESERIALIZER.deserialize(request);

        if (request.getRequestData().getGuid() == null || request.getRequestData().getGuid().isEmpty()) {
            request.setErrorMessage(
                    new ErrorMessage("SOAP-ENV:Server", "Guid is a required parameter", null, null));
            throw new XRd4JException("Guid is required");
        }
        
        Optional<Organization> organization = organizationService.getOrganization(request.getRequestData().getGuid());
        if (organization.isEmpty()) {
            request.setErrorMessage(
                    new ErrorMessage("SOAP-ENV:Server",
                            "Organization with guid " + request.getRequestData().getGuid() + " not found",
                            null, null));
            throw new XRd4JException("Organization not found");
        }

        OrganizationChangeResult result = new OrganizationChangeResult(
                organization.get(),
                request.getRequestData().getStartDateTime(),
                request.getRequestData().getEndDateTime());
        
        ServiceResponse<HasOrganizationChangedRequest, OrganizationChangeResult> response = new ServiceResponse<>(request.getConsumer(),
                request.getProducer(), request.getId());
        response.getProducer().setNamespaceUrl(NAMESPACE_URL);
        response.getProducer().setNamespacePrefix(NAMESPACE_PREFIX);
        response.setResponseData(result);
        RESPONSE_SERIALIZER.serialize(response, request);
        return response;
    }

}
