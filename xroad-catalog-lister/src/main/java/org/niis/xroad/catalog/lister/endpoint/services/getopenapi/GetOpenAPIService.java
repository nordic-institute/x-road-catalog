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
package org.niis.xroad.catalog.lister.endpoint.services.getopenapi;

import jakarta.xml.soap.SOAPException;
import org.niis.xrd4j.common.exception.XRd4JException;
import org.niis.xrd4j.common.message.ErrorMessage;
import org.niis.xrd4j.common.message.ServiceRequest;
import org.niis.xrd4j.common.message.ServiceResponse;
import org.niis.xroad.catalog.lister.endpoint.ListerService;
import org.niis.xroad.catalog.lister.service.CatalogService;
import org.niis.xroad.catalog.persistence.entity.OpenApi;

/**
 * @deprecated Superseded by the V2 REST API ({@code org.niis.xroad.catalog.lister.v2}); scheduled for removal.
 */
@Deprecated(forRemoval = true)
public class GetOpenAPIService implements ListerService<GetOpenAPIRequest, String> {
    private static final GetOpenAPIRequestDeserializer REQUEST_DESERIALIZER = new GetOpenAPIRequestDeserializer();
    private static final GetOpenAPIResponseSerializer RESPONSE_SERIALIZER = new GetOpenAPIResponseSerializer();
    private final CatalogService catalogService;

    public GetOpenAPIService(final CatalogService catalogService) {
        this.catalogService = catalogService;
    }

    public ServiceResponse<GetOpenAPIRequest, String> execute(ServiceRequest<GetOpenAPIRequest> request)
            throws XRd4JException, SOAPException {
        REQUEST_DESERIALIZER.deserialize(request);
        
        OpenApi openApiEntity = catalogService.getOpenApi(request.getRequestData().getExternalId());
        if (openApiEntity == null) {
            request.setErrorMessage(
                    new ErrorMessage(FAULT_CODE_SERVER, "OpenApi with external id "
                            + request.getRequestData().getExternalId() + " not found", null, null));
            throw new XRd4JException("OpenApi not found");
        }
        String openApi = openApiEntity.getData();
        
        ServiceResponse<GetOpenAPIRequest, String> response = new ServiceResponse<>(request.getConsumer(),
                request.getProducer(), request.getId());
        response.getProducer().setNamespaceUrl(NAMESPACE_URL);
        response.getProducer().setNamespacePrefix(NAMESPACE_PREFIX);
        response.setResponseData(openApi);
        RESPONSE_SERIALIZER.serialize(response, request);
        return response;
    }
}
