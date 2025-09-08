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
import org.junit.jupiter.api.Test;
import org.niis.xrd4j.client.SOAPClient;
import org.niis.xrd4j.common.exception.XRd4JException;
import org.niis.xrd4j.common.member.ConsumerMember;
import org.niis.xrd4j.common.member.ObjectType;
import org.niis.xrd4j.common.member.ProducerMember;
import org.niis.xrd4j.common.message.ServiceRequest;
import org.niis.xrd4j.common.message.ServiceResponse;
import org.niis.xroad.catalog.collector.CollectorApplication;
import org.niis.xroad.catalog.collector.configuration.TestingConfiguration;
import org.niis.xroad.catalog.collector.service.CatalogService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = {TestingConfiguration.class, CollectorApplication.class},
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles({"test", "general-testdata"})
public class XRoadClientTest {

    @MockitoBean
    CatalogService catalogService;

    @Value("${xroad-catalog.urls.security-server-host}")
    private String xroadSecurityServerHost;

    @Value("${xroad-catalog.target.xroad-instance}")
    private String xroadInstance;

    @Value("${xroad-catalog.target.member-code}")
    private String memberCode;

    @Value("${xroad-catalog.target.member-class}")
    private String memberClass;

    @Value("${xroad-catalog.target.subsystem-code}")
    private String subsystemCode;

    private static final String REQUEST_URL = "http://requestUrl";

    private ConsumerMember getDefaultClient() throws XRd4JException {
        ConsumerMember client = new ConsumerMember(xroadInstance, memberClass, memberCode);
        client.setObjectType(ObjectType.MEMBER);
        return client;
    }

    private XRoadIdentifier getDefaultProducer() {
        return XRoadIdentifier.builder()
                .xRoadInstance(xroadInstance)
                .memberClass(memberClass)
                .memberCode(memberCode)
                .subsystemCode(subsystemCode)
                .objectType(ObjectType.SUBSYSTEM)
                .build();
    }

    @Test
    public void testCallListMethods() throws SOAPException, XRd4JException {
        ConsumerMember client = getDefaultClient();
        SOAPClient soapClient = mock(SOAPClient.class);
        ServiceResponse<String, List<ProducerMember>> response = new ServiceResponse<>();
        response.setResponseData(Arrays.asList(
                XRoadIdentifier.builder()
                        .xRoadInstance("DEV").memberClass("ORG").memberCode("m1")
                        .subsystemCode("s1").serviceCode("sc1").serviceVersion("v1")
                        .build().toProducerMember(),
                XRoadIdentifier.builder()
                        .xRoadInstance("DEV").memberClass("ORG").memberCode("m1")
                        .subsystemCode("s1").serviceCode("sc2").serviceVersion("v1")
                        .build().toProducerMember(),
                XRoadIdentifier.builder()
                        .xRoadInstance("DEV").memberClass("ORG").memberCode("m1")
                        .subsystemCode("s1").serviceCode("sc3").serviceVersion("v1")
                        .build().toProducerMember()
        ));
        when(soapClient.listMethods(any(ServiceRequest.class), eq(REQUEST_URL)))
                .thenReturn(response);
        XRoadClient xRoadClient = new XRoadClient(soapClient, client, REQUEST_URL);
        assertNotNull(xRoadClient);
        XRoadIdentifier client2 = getDefaultProducer();
        List<ProducerMember> openApiResponse = xRoadClient.getMethods(client2, catalogService);
        verify(soapClient, times(1)).listMethods(any(ServiceRequest.class), eq(REQUEST_URL));
        assertEquals(3, openApiResponse.size());
    }

    @Test
    public void testCallGetWsdl() throws Exception {
        ConsumerMember client = getDefaultClient();
        final String wsdlContent = "wsdlData";
        SOAPClient soapClient = mock(SOAPClient.class);
        ServiceResponse<GetWsdlRequest, String> response = new ServiceResponse<>();
        response.setResponseData(wsdlContent);
        when(soapClient.send(any(ServiceRequest.class), eq(REQUEST_URL),
                any(GetWsdlRequestSerializer.class), any(GetWsdlResponseDeserializer.class)))
                .thenReturn(response);
        XRoadClient xRoadClient = new XRoadClient(soapClient, client, REQUEST_URL);
        assertNotNull(xRoadClient);
        ProducerMember service = new ProducerMember(xroadInstance, memberClass, memberCode, subsystemCode, "getWsdl", "null");
        service.setObjectType(ObjectType.SERVICE);
        String wsdl = xRoadClient.getWsdl(service, catalogService);
        assertEquals(wsdlContent, wsdl);
    }

}
