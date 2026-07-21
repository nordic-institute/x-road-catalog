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
package org.niis.xroad.catalog.lister.endpoint.services.isprovider;

import jakarta.xml.soap.SOAPException;
import org.niis.xrd4j.common.exception.XRd4JException;
import org.niis.xrd4j.common.message.ErrorMessage;
import org.niis.xrd4j.common.message.ServiceRequest;
import org.niis.xrd4j.common.message.ServiceResponse;
import org.niis.xroad.catalog.lister.endpoint.ListerService;
import org.niis.xroad.catalog.lister.service.CatalogService;
import org.niis.xroad.catalog.persistence.entity.Member;

/**
 * @deprecated Superseded by the V2 REST API ({@code org.niis.xroad.catalog.lister.v2});
 *             V1 is kept for compatibility and is scheduled for removal.
 */
@Deprecated
public class IsProviderService implements ListerService<IsProviderRequest, Boolean> {
    private static final IsProviderRequestDeserializer REQUEST_DESERIALIZER = new IsProviderRequestDeserializer();
    private static final IsProviderResponseSerializer RESPONSE_SERIALIZER = new IsProviderResponseSerializer();
    private final CatalogService catalogService;

    public IsProviderService(final CatalogService catalogService) {
        this.catalogService = catalogService;
    }

    public ServiceResponse<IsProviderRequest, Boolean> execute(ServiceRequest<IsProviderRequest> request)
            throws XRd4JException, SOAPException {
        REQUEST_DESERIALIZER.deserialize(request);
        
        Member member = catalogService.getMember(
                request.getRequestData().getXRoadInstance(),
                request.getRequestData().getMemberClass(),
                request.getRequestData().getMemberCode()
        );

        if (member == null) {
            request.setErrorMessage(new ErrorMessage(FAULT_CODE_SERVER, "Member with xRoadInstance \""
                    + request.getRequestData().getXRoadInstance()
                    + "\", memberClass \"" + request.getRequestData().getMemberClass()
                    + "\" and memberCode \"" + request.getRequestData().getMemberCode() + "\" not found", null, null));
            throw new XRd4JException("Member not found");
        }

        boolean isProvider = member.getAllSubsystems().stream().anyMatch(
                subsystem -> subsystem.getAllServices().stream().anyMatch(
                        service -> service.hasWsdl() || service.hasOpenApi() || service.hasRest()));
        
        ServiceResponse<IsProviderRequest, Boolean> response = new ServiceResponse<>(request.getConsumer(),
                request.getProducer(), request.getId());
        response.getProducer().setNamespaceUrl(NAMESPACE_URL);
        response.getProducer().setNamespacePrefix(NAMESPACE_PREFIX);
        response.setResponseData(isProvider);
        RESPONSE_SERIALIZER.serialize(response, request);
        return response;
    }
}
