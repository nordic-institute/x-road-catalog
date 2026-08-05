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
package org.niis.xroad.catalog.collector.util;

import jakarta.xml.soap.SOAPException;
import lombok.extern.slf4j.Slf4j;
import org.niis.xrd4j.client.SOAPClient;
import org.niis.xrd4j.client.SOAPClientImpl;
import org.niis.xrd4j.common.member.ConsumerMember;
import org.niis.xrd4j.common.member.ObjectType;
import org.niis.xrd4j.common.member.ProducerMember;
import org.niis.xrd4j.common.message.ServiceRequest;
import org.niis.xrd4j.common.message.ServiceResponse;
import org.niis.xrd4j.common.util.Constants;
import org.niis.xroad.catalog.collector.exception.XRoadClientException;
import org.niis.xroad.catalog.collector.service.CatalogService;
import org.niis.xroad.catalog.persistence.entity.ErrorLog;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
public class XRoadClient {
    static final GetWsdlRequestSerializer GET_WSDL_REQUEST_SERIALIZER = new GetWsdlRequestSerializer();
    static final GetWsdlResponseDeserializer GET_WSDL_RESPONSE_DESERIALIZER = new GetWsdlResponseDeserializer();

    final SOAPClient soapClient;
    final ConsumerMember consumerMember;
    final String securityServerURL;
    final RestTemplate restTemplate;

    public XRoadClient(final SOAPClient soapClient, final ConsumerMember consumerMember, final String securityServerURL,
                       final RestTemplate restTemplate) {
        this.soapClient = soapClient;
        this.consumerMember = consumerMember;
        this.securityServerURL = securityServerURL;
        this.restTemplate = restTemplate;
    }

    public XRoadClient(final ConsumerMember consumerMember, final String securityServerURL, final RestTemplate restTemplate)
            throws SOAPException {
        this(new SOAPClientImpl(), consumerMember, securityServerURL, restTemplate);
    }

    /**
     * Calls the service using XRD4J
     */
    public List<ProducerMember> getMethods(final XRoadIdentifier member, final CatalogService catalogService) {
        List<ProducerMember> response = null;
        try {
            ServiceRequest<String> request = new ServiceRequest<>(consumerMember, member.toProducerMember(), queryId());
            response = soapClient.listMethods(request, securityServerURL).getResponseData();
        } catch (Exception e) {
            log.error("Fetch of SOAP services failed: {}", e.getMessage());
            ErrorLog errorLog = ErrorLog.builder()
                    .created(LocalDateTime.now())
                    .message("Fetch of SOAP services failed: " + e.getMessage())
                    .code("500")
                    .xRoadInstance(member.getXRoadInstance())
                    .memberClass(member.getMemberClass())
                    .memberCode(member.getMemberCode())
                    .serviceCode(member.getServiceCode())
                    .serviceVersion(member.getServiceVersion())
                    .subsystemCode(member.getSubsystemCode())
                    .build();
            catalogService.saveErrorLog(errorLog);
        }
        return response != null ? response : new ArrayList<>();
    }

    public String getWsdl(final ProducerMember service, final CatalogService catalogService) throws XRoadClientException {
        try {
            GetWsdlRequest requestData = new GetWsdlRequest(service.getServiceCode(), service.getServiceVersion());

            ProducerMember metaserviceProducer = new ProducerMember(service.getXRoadInstance(), service.getMemberClass(),
                    service.getMemberCode(), service.getSubsystemCode(), "getWsdl");
            metaserviceProducer.setServiceVersion(null);
            metaserviceProducer.setObjectType(ObjectType.SERVICE);
            metaserviceProducer.setNamespacePrefix(Constants.NS_XRD_PREFIX);
            metaserviceProducer.setNamespaceUrl(Constants.NS_XRD_URL);
            ServiceRequest<GetWsdlRequest> request = new ServiceRequest<>(consumerMember, metaserviceProducer, queryId());
            request.setRequestData(requestData);
            ServiceResponse<GetWsdlRequest, String> response = soapClient.send(request, securityServerURL,
                    GET_WSDL_REQUEST_SERIALIZER, GET_WSDL_RESPONSE_DESERIALIZER);
            return response.getResponseData();
        } catch (Exception e) {
            log.error("Fetch of WSDL failed: {}", e.getMessage());
            ErrorLog errorLog = ErrorLog.builder()
                    .created(LocalDateTime.now())
                    .message("Fetch of WSDL failed: " + e.getMessage())
                    .code("500")
                    .xRoadInstance(service.getXRoadInstance())
                    .memberClass(service.getMemberClass())
                    .memberCode(service.getMemberCode())
                    .serviceCode(service.getServiceCode())
                    .serviceVersion(service.getServiceVersion())
                    .subsystemCode(service.getSubsystemCode())
                    .build();
            catalogService.saveErrorLog(errorLog);
            throw new XRoadClientException(e);
        }
    }

    /**
     * @return the OpenAPI descriptor, or {@code null} when the fetch failed
     */
    public String getOpenApi(XRoadIdentifier service,
                             String host,
                             ConsumerMember clientIdentifier,
                             CatalogService catalogService) {

        return MethodListUtil.openApiFromResponse(service, host, clientIdentifier, catalogService, restTemplate);
    }

    private String queryId() {
        return "xroad-catalog-collector-" + UUID.randomUUID();
    }

}
