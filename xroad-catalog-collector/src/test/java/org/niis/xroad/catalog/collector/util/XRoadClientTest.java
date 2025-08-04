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

import fi.dvv.xroad.catalog.collector.mock.MockMetaServicesImpl;
import jakarta.xml.soap.SOAPException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.niis.xrd4j.common.exception.XRd4JException;
import org.niis.xrd4j.common.member.ConsumerMember;
import org.niis.xrd4j.common.member.ObjectType;
import org.niis.xrd4j.common.member.ProducerMember;
import org.niis.xroad.catalog.collector.CollectorApplication;
import org.niis.xroad.catalog.collector.configuration.TestingConfiguration;
import org.niis.xroad.catalog.collector.service.CatalogService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest(classes = {TestingConfiguration.class, CollectorApplication.class},
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
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

    @LocalServerPort
    private int port;

    private String webservicesEndpoint;

    @BeforeEach
    public void setUp() {
        webservicesEndpoint = "http://localhost:%s/metaservices".formatted(port);
    }

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
        XRoadClient xRoadClient = new XRoadClient(client, webservicesEndpoint);
        assertNotNull(xRoadClient);
        XRoadIdentifier client2 = getDefaultProducer();
        List<ProducerMember> openApiResponse = xRoadClient.getMethods(client2, catalogService);
        assertEquals(3, openApiResponse.size());
    }

    @Test
    public void testCallGetWsdl() throws Exception {
        ConsumerMember client = getDefaultClient();
        XRoadClient xRoadClient = new XRoadClient(client, webservicesEndpoint);
        assertNotNull(xRoadClient);
        ProducerMember service = new ProducerMember(xroadInstance, memberClass, memberCode, subsystemCode, "getWsdl", "null");
        service.setObjectType(ObjectType.SERVICE);
        String wsdl = xRoadClient.getWsdl(service, catalogService);
        assertEquals(MockMetaServicesImpl.getWSDLForService(service.getServiceCode(), service.getServiceVersion()),
                wsdl);
    }

}
