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
package fi.dvv.xroad.catalog.lister.endpoint.services.hascompanychanged;

import jakarta.xml.soap.SOAPException;
import org.niis.xrd4j.common.exception.XRd4JException;
import org.niis.xrd4j.common.message.ErrorMessage;
import org.niis.xrd4j.common.message.ServiceRequest;
import org.niis.xrd4j.common.message.ServiceResponse;
import org.niis.xroad.catalog.lister.endpoint.ListerService;
import fi.dvv.xroad.catalog.lister.service.CompanyService;
import fi.dvv.xroad.catalog.persistence.entity.Company;
import org.springframework.util.StringUtils;

@SuppressWarnings("java:S3776")
public class HasCompanyChangedService implements ListerService<HasCompanyChangedRequest, CompanyChangeResult> {
    private static final HasCompanyChangedRequestDeserializer REQUEST_DESERIALIZER = new HasCompanyChangedRequestDeserializer();
    private static final HasCompanyChangedResponseSerializer RESPONSE_SERIALIZER = new HasCompanyChangedResponseSerializer();
    private final CompanyService companyService;

    public HasCompanyChangedService(final CompanyService companyService) {
        this.companyService = companyService;
    }

    public ServiceResponse<HasCompanyChangedRequest, CompanyChangeResult> execute(ServiceRequest<HasCompanyChangedRequest> request)
            throws XRd4JException, SOAPException {
        REQUEST_DESERIALIZER.deserialize(request);

        if (!StringUtils.hasText(request.getRequestData().getBusinessId())) {
            request.setErrorMessage(
                    new ErrorMessage(FAULT_CODE_SERVER, "BusinessId is a required parameter", null, null));
            throw new XRd4JException("BusinessId is required");
        }

        if (request.getRequestData().getStartDateTime() == null || request.getRequestData().getEndDateTime() == null) {
            request.setErrorMessage(
                    new ErrorMessage(FAULT_CODE_SERVER, "startDateTime and endDateTime parameters are required", null, null));
            throw new XRd4JException("Missing required parameters");
        }
        
        Iterable<Company> companies = companyService.getCompanies(request.getRequestData().getBusinessId());
        if (!companies.iterator().hasNext()) {
            request.setErrorMessage(
                    new ErrorMessage(FAULT_CODE_SERVER,
                            "company with businessId " + request.getRequestData().getBusinessId() + " not found",
                            null, null));
            throw new XRd4JException("Companies not found");
        }

        CompanyChangeResult result = new CompanyChangeResult(
                companies.iterator().next(),
                request.getRequestData().getStartDateTime(),
                request.getRequestData().getEndDateTime());
        
        ServiceResponse<HasCompanyChangedRequest, CompanyChangeResult> response = new ServiceResponse<>(request.getConsumer(),
                request.getProducer(), request.getId());
        response.getProducer().setNamespaceUrl(NAMESPACE_URL);
        response.getProducer().setNamespacePrefix(NAMESPACE_PREFIX);
        response.setResponseData(result);
        RESPONSE_SERIALIZER.serialize(response, request);
        return response;
    }


}
