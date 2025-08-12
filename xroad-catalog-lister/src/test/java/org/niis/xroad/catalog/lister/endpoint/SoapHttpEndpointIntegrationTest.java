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

import fi.dvv.xroad.catalog.lister.service.JaxbCompanyService;
import fi.dvv.xroad.catalog.lister.service.JaxbOrganizationService;
import org.junit.jupiter.api.Test;
import org.niis.xroad.catalog.lister.ListerApplication;
import org.niis.xroad.catalog.lister.generated.ErrorLog;
import org.niis.xroad.catalog.lister.generated.Member;
import org.niis.xroad.catalog.lister.service.CatalogService;
import org.niis.xroad.catalog.lister.service.JaxbCatalogService;
import org.niis.xroad.catalog.persistence.entity.OpenApi;
import org.niis.xroad.catalog.persistence.entity.Service;
import org.niis.xroad.catalog.persistence.entity.Subsystem;
import org.niis.xroad.catalog.persistence.entity.Wsdl;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.util.StreamUtils;
import org.xmlunit.builder.DiffBuilder;
import org.xmlunit.builder.Input;
import org.xmlunit.diff.Diff;
import org.xmlunit.diff.DifferenceEvaluators;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

/**
 * HTTP-level integration tests for SOAP endpoints.
 * These tests verify the exact SOAP message structure at the HTTP transport level.
 */
@SpringBootTest(classes = ListerApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@SuppressWarnings("PMD.AvoidDuplicateLiterals")
public class SoapHttpEndpointIntegrationTest {

    private final TestRestTemplate restTemplate = new TestRestTemplate();

    @LocalServerPort
    private int port;

    @MockitoBean
    CatalogService catalogService;

    @MockitoBean
    JaxbCatalogService jaxbCatalogService;

    @MockitoBean
    JaxbCompanyService jaxbCompanyService;

    @MockitoBean
    JaxbOrganizationService jaxbOrganizationService;

    private String getEndpointUrl() {
        return "http://localhost:" + port + "/ws";
    }

    @Test
    public void testListMembersHttpSoap() throws Exception {
        mockMembersForListServices();

        String soapRequest = loadXmlFromClasspath("soap-requests/ListMembersRequest.xml");
        ResponseEntity<String> response = sendSoapRequest(soapRequest);
        
        assertEquals(HttpStatus.OK, response.getStatusCode());
        
        String expectedResponse = loadXmlFromClasspath("soap-responses/ListMembersResponse.xml");
        assertXmlEquals(expectedResponse, response.getBody(), "ListMembers response should match expected XML");
    }

    @Test
    public void testGetServiceTypeSoapHttpSoap() throws Exception {
        mockServicesForGetServiceType("testService", "SOAP");

        String soapRequest = loadXmlFromClasspath("soap-requests/GetServiceTypeSoapRequest.xml");
        ResponseEntity<String> response = sendSoapRequest(soapRequest);
        
        assertEquals(HttpStatus.OK, response.getStatusCode());
        
        String expectedResponse = loadXmlFromClasspath("soap-responses/GetServiceTypeSoapResponse.xml");
        assertXmlEquals(expectedResponse, response.getBody(), "GetServiceType SOAP response should match expected XML");
    }

    @Test
    public void testIsProviderTrueHttpSoap() throws Exception {
        mockProvider();

        String soapRequest = loadXmlFromClasspath("soap-requests/IsProviderTrueRequest.xml");
        ResponseEntity<String> response = sendSoapRequest(soapRequest);
        
        assertEquals(HttpStatus.OK, response.getStatusCode());
        
        String expectedResponse = loadXmlFromClasspath("soap-responses/IsProviderTrueResponse.xml");
        assertXmlEquals(expectedResponse, response.getBody(), "IsProvider true response should match expected XML");
    }

    @Test
    public void testIsProviderFalseHttpSoap() throws Exception {
        mockNoProvider();

        String soapRequest = loadXmlFromClasspath("soap-requests/IsProviderFalseRequest.xml");
        ResponseEntity<String> response = sendSoapRequest(soapRequest);
        
        assertEquals(HttpStatus.OK, response.getStatusCode());
        
        String expectedResponse = loadXmlFromClasspath("soap-responses/IsProviderFalseResponse.xml");
        assertXmlEquals(expectedResponse, response.getBody(), "IsProvider false response should match expected XML");
    }

    @Test
    public void testGetWsdlHttpSoap() throws Exception {
        String externalId = "1000";
        given(catalogService.getWsdl(externalId))
                .willReturn(new Wsdl(new Service(), "<wsdl>This is WSDL content</wsdl>", externalId));

        String soapRequest = loadXmlFromClasspath("soap-requests/GetWsdlRequest.xml");
        ResponseEntity<String> response = sendSoapRequest(soapRequest);
        
        assertEquals(HttpStatus.OK, response.getStatusCode());
        
        String expectedResponse = loadXmlFromClasspath("soap-responses/GetWsdlResponse.xml");
        assertXmlEquals(expectedResponse, response.getBody(), "GetWsdl response should match expected XML");
    }

    @Test
    public void testGetOpenApiHttpSoap() throws Exception {
        String externalId = "3003";
        given(catalogService.getOpenApi(externalId))
                .willReturn(new OpenApi(new Service(), "This is OpenAPI content", externalId));

        String soapRequest = loadXmlFromClasspath("soap-requests/GetOpenApiRequest.xml");
        ResponseEntity<String> response = sendSoapRequest(soapRequest);
        
        assertEquals(HttpStatus.OK, response.getStatusCode());
        
        String expectedResponse = loadXmlFromClasspath("soap-responses/GetOpenApiResponse.xml");
        assertXmlEquals(expectedResponse, response.getBody(), "GetOpenApi response should match expected XML");
    }

    @Test
    public void testGetServiceTypeOpenApiHttpSoap() throws Exception {
        mockServicesForGetServiceType("getRandomOpenAPI", "OPENAPI");
        
        String soapRequest = loadXmlFromClasspath("soap-requests/GetServiceTypeOpenApiRequest.xml");
        ResponseEntity<String> response = sendSoapRequest(soapRequest);
        
        assertEquals(HttpStatus.OK, response.getStatusCode());
        
        String expectedResponse = loadXmlFromClasspath("soap-responses/GetServiceTypeOpenApiResponse.xml");
        assertXmlEquals(expectedResponse, response.getBody(), "GetServiceType OpenAPI response should match expected XML");
    }

    @Test
    public void testGetServiceTypeRestHttpSoap() throws Exception {
        mockServicesForGetServiceType("getRandomREST", "REST");
        
        String soapRequest = loadXmlFromClasspath("soap-requests/GetServiceTypeRestRequest.xml");
        ResponseEntity<String> response = sendSoapRequest(soapRequest);
        
        assertEquals(HttpStatus.OK, response.getStatusCode());
        
        String expectedResponse = loadXmlFromClasspath("soap-responses/GetServiceTypeRestResponse.xml");
        assertXmlEquals(expectedResponse, response.getBody(), "GetServiceType REST response should match expected XML");
    }

    @Test
    public void testGetErrorsHttpSoap() throws Exception {
        mockErrors();
        
        String soapRequest = loadXmlFromClasspath("soap-requests/GetErrorsRequest.xml");
        ResponseEntity<String> response = sendSoapRequest(soapRequest);
        
        assertEquals(HttpStatus.OK, response.getStatusCode());
        
        String expectedResponse = loadXmlFromClasspath("soap-responses/GetErrorsResponse.xml");
        assertXmlEquals(expectedResponse, response.getBody(), "GetErrors response should match expected XML");
    }





    // HTTP and XML utility methods

    private ResponseEntity<String> sendSoapRequest(String soapRequest) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.TEXT_XML);
        headers.set("SOAPAction", "");
        
        HttpEntity<String> entity = new HttpEntity<>(soapRequest, headers);
        
        return restTemplate.postForEntity(getEndpointUrl(), entity, String.class);
    }

    private String loadXmlFromClasspath(String path) throws IOException {
        ClassPathResource resource = new ClassPathResource(path);
        return StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
    }

    private void assertXmlEquals(String expectedXml, String actualXml, String message) {
        Diff diff = DiffBuilder.compare(Input.fromString(expectedXml))
                .withTest(Input.fromString(actualXml))
                .withDifferenceEvaluator(DifferenceEvaluators.chain(
                        DifferenceEvaluators.Default,
                        // Ignore whitespace-only text nodes
                        DifferenceEvaluators.downgradeDifferencesToSimilar(org.xmlunit.diff.ComparisonType.TEXT_VALUE)
                ))
                .ignoreWhitespace()
                .ignoreComments()
                .build();

        if (diff.hasDifferences()) {
            fail(message + "\n" + diff.toString() + "\n\nExpected:\n" + expectedXml + "\n\nActual:\n" + actualXml);
        }
    }

    // Mock helper methods (reused from ApplicationTests.java)

    private void mockMembersForListServices() {
        Member member1 = new Member();
        member1.setXRoadInstance("DEV");
        member1.setMemberClass("ORG");
        member1.setMemberCode("1234");
        
        Member member2 = new Member();
        member2.setXRoadInstance("DEV");
        member2.setMemberClass("ORG");
        member2.setMemberCode("5678");
        
        Member member3 = new Member();
        member3.setXRoadInstance("DEV");
        member3.setMemberClass("COM");
        member3.setMemberCode("1234");
        
        given(jaxbCatalogService.getAllMembers(any(), any())).willReturn(Arrays.asList(member1, member2, member3));
    }

    private void mockServicesForGetServiceType(String serviceCode,
                                               String serviceType) {
        Service service = new Service();
        service.setServiceCode(serviceCode);
        service.setServiceVersion("v1");
        
        if ("SOAP".equalsIgnoreCase(serviceType)) {
            service.setWsdl(new Wsdl());
        } else if ("OPENAPI".equalsIgnoreCase(serviceType)) {
            service.setOpenApi(new OpenApi());
        }
        // REST service has neither WSDL nor OpenAPI
        
        given(catalogService.getService("DEV", "ORG", "14151328", serviceCode, "TestSubSystem", "v1"))
                .willReturn(service);
    }

    private void mockProvider() {
        org.niis.xroad.catalog.persistence.entity.Member member = new org.niis.xroad.catalog.persistence.entity.Member();
        member.setXRoadInstance("DEV");
        member.setMemberClass("ORG");
        member.setMemberCode("14151328");
        
        Subsystem subsystem = new Subsystem();
        subsystem.setSubsystemCode("TestSubsystem");
        
        Service service = new Service();
        service.setServiceCode("TestService");
        service.setWsdl(new Wsdl(service, "This is WSDL", "3242efdf34r"));
        
        subsystem.setServices(Set.of(service));
        member.setSubsystems(Set.of(subsystem));
        
        given(catalogService.getMember("DEV", "ORG", "14151328")).willReturn(member);
    }

    private void mockNoProvider() {
        org.niis.xroad.catalog.persistence.entity.Member member = new org.niis.xroad.catalog.persistence.entity.Member();
        member.setXRoadInstance("DEV");
        member.setMemberClass("ORG");
        member.setMemberCode("88855888");
        
        Subsystem subsystem = new Subsystem();
        subsystem.setSubsystemCode("TestSubsystem");
        
        Service service = new Service();
        service.setServiceCode("TestService");
        // No WSDL, OpenAPI, or REST - not a provider
        
        subsystem.setServices(Set.of(service));
        member.setSubsystems(Set.of(subsystem));
        
        given(catalogService.getMember("DEV", "ORG", "88855888")).willReturn(member);
    }

    private void mockErrors() {
        List<ErrorLog> errors = new ArrayList<>();
        List<String> errorMessages = Arrays.asList(
                "Service not found",
                "Error with certificate",
                "Unknown error",
                "Access restricted",
                "Connection refused",
                "Multiple values returned"
        );
        
        for (String errorMessage : errorMessages) {
            ErrorLog errorLog = new ErrorLog();
            errorLog.setMessage(errorMessage);
            errorLog.setXRoadInstance("DEV");
            errorLog.setMemberClass("ORG");
            errorLog.setMemberCode("1234");
            errorLog.setSubsystemCode("TestSubsystem");
            errorLog.setServiceCode("TestService");
            errors.add(errorLog);
        }
        
        given(jaxbCatalogService.getErrorLog(any(), any())).willReturn(errors);
    }
}
