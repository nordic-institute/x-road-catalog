/**
 * The MIT License
 *
 * Copyright (c) 2023- Nordic Institute for Interoperability Solutions (NIIS)
 * Copyright (c) 2016-2023 Finnish Digital Agency
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package fi.vrk.xroad.catalog.collector.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;

import fi.vrk.xroad.catalog.collector.configuration.DevelopmentConfiguration;
import fi.vrk.xroad.catalog.collector.mock.MockMetaServicesImpl;
import fi.vrk.xroad.catalog.collector.wsimport.XRoadClientIdentifierType;
import fi.vrk.xroad.catalog.collector.wsimport.XRoadObjectType;
import fi.vrk.xroad.catalog.collector.wsimport.XRoadServiceIdentifierType;
import fi.vrk.xroad.catalog.persistence.CatalogService;

@SpringBootTest(classes = DevelopmentConfiguration.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "xroad-catalog.webservices-endpoint=http://localhost:${local.server.port}/metaservices"
})
public class XRoadClientTest {

    @MockBean
    CatalogService catalogService;

    @Value("${xroad-catalog.security-server-host}")
    private String xroadSecurityServerHost;

    @Value("${xroad-catalog.xroad-instance}")
    private String xroadInstance;

    @Value("${xroad-catalog.member-code}")
    private String memberCode;

    @Value("${xroad-catalog.member-class}")
    private String memberClass;

    @Value("${xroad-catalog.subsystem-code}")
    private String subsystemCode;

    @Value("${xroad-catalog.webservices-endpoint}")
    private String webservicesEndpoint;

    private XRoadClientIdentifierType getDefaultClient() {
        XRoadClientIdentifierType client = new XRoadClientIdentifierType();
        client.setXRoadInstance(xroadInstance);
        client.setMemberClass(memberClass);
        client.setMemberCode(memberCode);
        client.setObjectType(XRoadObjectType.MEMBER);
        return client;
    }

    private XRoadServiceIdentifierType getDefaultService() {
        XRoadServiceIdentifierType service = new XRoadServiceIdentifierType();
        service.setXRoadInstance(xroadInstance);
        service.setMemberClass(memberClass);
        service.setMemberCode(memberCode);
        service.setSubsystemCode(subsystemCode);
        service.setServiceCode("code123");
        service.setServiceVersion("v3");
        service.setObjectType(XRoadObjectType.SERVICE);
        return service;
    }

    @Test
    public void testCallListMethods() throws URISyntaxException {
        XRoadClientIdentifierType client = getDefaultClient();
        XRoadClient xRoadClient = new XRoadClient(client, new URI(webservicesEndpoint));
        assertFalse(xRoadClient == null);
        XRoadClientIdentifierType client2 = getDefaultClient();
        client2.setSubsystemCode(subsystemCode);
        List<XRoadServiceIdentifierType> openApiResponse = xRoadClient.getMethods(client2, catalogService);
        assertEquals(3, openApiResponse.size());
    }

    @Test
    public void testCallGetWsdl() throws Exception {
        XRoadClientIdentifierType client = getDefaultClient();
        XRoadClient xRoadClient = new XRoadClient(client, new URI(webservicesEndpoint));
        assertFalse(xRoadClient == null);
        XRoadServiceIdentifierType service = getDefaultService();
        String wsdl = xRoadClient.getWsdl(service, catalogService);
        assertEquals(MockMetaServicesImpl.getWSDLForService(service.getServiceCode(), service.getServiceVersion()),
                wsdl);
    }

}
