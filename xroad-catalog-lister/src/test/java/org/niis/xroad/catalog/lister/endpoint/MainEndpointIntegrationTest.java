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

import org.junit.jupiter.api.Test;
import org.niis.xroad.catalog.lister.ListerApplication;
import org.niis.xroad.catalog.lister.service.CatalogService;
import org.niis.xroad.catalog.persistence.entity.Member;
import org.niis.xroad.catalog.persistence.entity.OpenApi;
import org.niis.xroad.catalog.persistence.entity.Service;
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
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.util.StreamUtils;
import org.xmlunit.builder.DiffBuilder;
import org.xmlunit.builder.Input;
import org.xmlunit.diff.Diff;
import org.xmlunit.diff.DifferenceEvaluators;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

/**
 * HTTP-level integration tests for SOAP endpoints.
 * These tests verify the exact SOAP message structure at the HTTP transport level.
 *
 * Test data is created using {@link MainMockDataFactory} to ensure consistency across all tests.
 * All mock objects use standardized patterns for member codes, external IDs, and timestamps.
 */
@SpringBootTest(
        classes = ListerApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"xroad-catalog.country.fi.enabled=false", "spring.sql.init.mode=never"})
@ActiveProfiles("test")
@DirtiesContext
@SuppressWarnings("PMD.AvoidDuplicateLiterals")
public class MainEndpointIntegrationTest {

    private final TestRestTemplate restTemplate = new TestRestTemplate();

    @LocalServerPort
    private int port;

    @MockitoBean
    CatalogService catalogService;



    private String getEndpointUrl() {
        return "http://localhost:" + port + "/ws";
    }

    @Test
    public void testListMembersHttpSoap() throws Exception {
        mockMembersForListServices();

        String soapRequest = loadXmlFromClasspath("main-soap-requests/ListMembersRequest.xml");
        ResponseEntity<String> response = sendSoapRequest(soapRequest);
        
        assertEquals(HttpStatus.OK, response.getStatusCode());
        
        String expectedResponse = loadXmlFromClasspath("main-soap-responses/ListMembersResponse.xml");
        assertXmlEquals(expectedResponse, response.getBody(), "ListMembers response should match expected XML");
    }

    @Test
    public void testGetServiceTypeSoapHttpSoap() throws Exception {
        mockServicesForGetServiceType("testService", "SOAP");

        String soapRequest = loadXmlFromClasspath("main-soap-requests/GetServiceTypeSoapRequest.xml");
        ResponseEntity<String> response = sendSoapRequest(soapRequest);
        
        assertEquals(HttpStatus.OK, response.getStatusCode());
        
        String expectedResponse = loadXmlFromClasspath("main-soap-responses/GetServiceTypeSoapResponse.xml");
        assertXmlEquals(expectedResponse, response.getBody(), "GetServiceType SOAP response should match expected XML");
    }

    @Test
    public void testIsProviderTrueHttpSoap() throws Exception {
        mockProvider();

        String soapRequest = loadXmlFromClasspath("main-soap-requests/IsProviderTrueRequest.xml");
        ResponseEntity<String> response = sendSoapRequest(soapRequest);
        
        assertEquals(HttpStatus.OK, response.getStatusCode());
        
        String expectedResponse = loadXmlFromClasspath("main-soap-responses/IsProviderTrueResponse.xml");
        assertXmlEquals(expectedResponse, response.getBody(), "IsProvider true response should match expected XML");
    }

    @Test
    public void testIsProviderFalseHttpSoap() throws Exception {
        mockNoProvider();

        String soapRequest = loadXmlFromClasspath("main-soap-requests/IsProviderFalseRequest.xml");
        ResponseEntity<String> response = sendSoapRequest(soapRequest);
        
        assertEquals(HttpStatus.OK, response.getStatusCode());
        
        String expectedResponse = loadXmlFromClasspath("main-soap-responses/IsProviderFalseResponse.xml");
        assertXmlEquals(expectedResponse, response.getBody(), "IsProvider false response should match expected XML");
    }

    @Test
    public void testGetWsdlHttpSoap() throws Exception {
        String externalId = "1000";
        Wsdl testWsdl = new Wsdl(new Service(), "<wsdl>This is WSDL content</wsdl>", externalId);
        testWsdl.setStatusInfo(MainMockDataFactory.createStandardStatusInfo());
        given(catalogService.getWsdl(externalId))
                .willReturn(testWsdl);

        String soapRequest = loadXmlFromClasspath("main-soap-requests/GetWsdlRequest.xml");
        ResponseEntity<String> response = sendSoapRequest(soapRequest);
        
        assertEquals(HttpStatus.OK, response.getStatusCode());
        
        String expectedResponse = loadXmlFromClasspath("main-soap-responses/GetWsdlResponse.xml");
        assertXmlEquals(expectedResponse, response.getBody(), "GetWsdl response should match expected XML");
    }

    @Test
    public void testGetOpenApiHttpSoap() throws Exception {
        String externalId = "3003";
        OpenApi testOpenApi = new OpenApi(new Service(), "This is OpenAPI content", externalId);
        testOpenApi.setStatusInfo(MainMockDataFactory.createStandardStatusInfo());
        given(catalogService.getOpenApi(externalId))
                .willReturn(testOpenApi);

        String soapRequest = loadXmlFromClasspath("main-soap-requests/GetOpenApiRequest.xml");
        ResponseEntity<String> response = sendSoapRequest(soapRequest);
        
        assertEquals(HttpStatus.OK, response.getStatusCode());
        
        String expectedResponse = loadXmlFromClasspath("main-soap-responses/GetOpenApiResponse.xml");
        assertXmlEquals(expectedResponse, response.getBody(), "GetOpenApi response should match expected XML");
    }

    @Test
    public void testGetServiceTypeOpenApiHttpSoap() throws Exception {
        mockServicesForGetServiceType("getRandomOpenAPI", "OPENAPI");
        
        String soapRequest = loadXmlFromClasspath("main-soap-requests/GetServiceTypeOpenApiRequest.xml");
        ResponseEntity<String> response = sendSoapRequest(soapRequest);
        
        assertEquals(HttpStatus.OK, response.getStatusCode());
        
        String expectedResponse = loadXmlFromClasspath("main-soap-responses/GetServiceTypeOpenApiResponse.xml");
        assertXmlEquals(expectedResponse, response.getBody(), "GetServiceType OpenAPI response should match expected XML");
    }

    @Test
    public void testGetServiceTypeRestHttpSoap() throws Exception {
        mockServicesForGetServiceType("getRandomREST", "REST");
        
        String soapRequest = loadXmlFromClasspath("main-soap-requests/GetServiceTypeRestRequest.xml");
        ResponseEntity<String> response = sendSoapRequest(soapRequest);
        
        assertEquals(HttpStatus.OK, response.getStatusCode());
        
        String expectedResponse = loadXmlFromClasspath("main-soap-responses/GetServiceTypeRestResponse.xml");
        assertXmlEquals(expectedResponse, response.getBody(), "GetServiceType REST response should match expected XML");
    }

    @Test
    public void testGetErrorsHttpSoap() throws Exception {
        mockErrors();
        
        String soapRequest = loadXmlFromClasspath("main-soap-requests/GetErrorsRequest.xml");
        ResponseEntity<String> response = sendSoapRequest(soapRequest);
        
        assertEquals(HttpStatus.OK, response.getStatusCode());
        
        String expectedResponse = loadXmlFromClasspath("main-soap-responses/GetErrorsResponse.xml");
        assertXmlEquals(expectedResponse, response.getBody(), "GetErrors response should match expected XML");
    }

    // Negative test cases - document current error handling behavior
    
    @Test
    public void testListMembersWithNullStartDateTime() throws Exception {
        mockMembersForListServices();

        String soapRequest = loadXmlFromClasspath("main-soap-requests/ListMembersNullStartDateRequest.xml");
        ResponseEntity<String> response = sendSoapRequest(soapRequest);
        
        String expectedResponse = loadXmlFromClasspath("main-soap-responses/ListMembersNullStartDateResponse.xml");
        assertXmlEquals(expectedResponse, response.getBody(), "ListMembers null startDateTime response should match expected SOAP fault");
    }

    @Test
    public void testListMembersWithNullEndDateTime() throws Exception {
        mockMembersForListServices();

        String soapRequest = loadXmlFromClasspath("main-soap-requests/ListMembersNullEndDateRequest.xml");
        ResponseEntity<String> response = sendSoapRequest(soapRequest);
        
        String expectedResponse = loadXmlFromClasspath("main-soap-responses/ListMembersNullEndDateResponse.xml");
        assertXmlEquals(expectedResponse, response.getBody(), "ListMembers null endDateTime response should match expected SOAP fault");
    }

    @Test
    public void testGetServiceTypeNotFound() throws Exception {
        given(catalogService.getService(
                MainMockDataFactory.DEFAULT_XROAD_INSTANCE,
                MainMockDataFactory.MEMBER_CLASS_ORG,
                "99999999",
                "NonExistentService",
                "NonExistentSubsystem",
                "v1"))
                .willReturn(null);

        String soapRequest = loadXmlFromClasspath("main-soap-requests/GetServiceTypeNotFoundRequest.xml");
        ResponseEntity<String> response = sendSoapRequest(soapRequest);
        
        String expectedResponse = loadXmlFromClasspath("main-soap-responses/GetServiceTypeNotFoundResponse.xml");
        assertXmlEquals(expectedResponse, response.getBody(), "GetServiceType not found response should match expected SOAP fault");
    }

    @Test
    public void testIsProviderMemberNotFound() throws Exception {
        given(catalogService.getMember(MainMockDataFactory.DEFAULT_XROAD_INSTANCE, MainMockDataFactory.MEMBER_CLASS_ORG, "99999999"))
                .willReturn(null);

        String soapRequest = loadXmlFromClasspath("main-soap-requests/IsProviderMemberNotFoundRequest.xml");
        ResponseEntity<String> response = sendSoapRequest(soapRequest);
        
        String expectedResponse = loadXmlFromClasspath("main-soap-responses/IsProviderMemberNotFoundResponse.xml");
        assertXmlEquals(expectedResponse, response.getBody(), "IsProvider member not found response should match expected SOAP fault");
    }

    @Test
    public void testGetWsdlNotFound() throws Exception {
        given(catalogService.getWsdl("nonexistent-id")).willReturn(null);

        String soapRequest = loadXmlFromClasspath("main-soap-requests/GetWsdlNotFoundRequest.xml");
        ResponseEntity<String> response = sendSoapRequest(soapRequest);
        
        String expectedResponse = loadXmlFromClasspath("main-soap-responses/GetWsdlNotFoundResponse.xml");
        assertXmlEquals(expectedResponse, response.getBody(), "GetWsdl not found response should match expected SOAP fault");
    }

    @Test
    public void testGetOpenApiNotFound() throws Exception {
        given(catalogService.getOpenApi("nonexistent-id")).willReturn(null);

        String soapRequest = loadXmlFromClasspath("main-soap-requests/GetOpenApiNotFoundRequest.xml");
        ResponseEntity<String> response = sendSoapRequest(soapRequest);
        
        String expectedResponse = loadXmlFromClasspath("main-soap-responses/GetOpenApiNotFoundResponse.xml");
        assertXmlEquals(expectedResponse, response.getBody(), "GetOpenApi not found response should match expected SOAP fault");
    }

    // Enhanced positive test cases with rich hierarchical data
    
    @Test
    public void testListMembersWithFullHierarchy() throws Exception {
        mockMembersWithFullHierarchy();
        
        String soapRequest = loadXmlFromClasspath("main-soap-requests/ListMembersRequest.xml");
        ResponseEntity<String> response = sendSoapRequest(soapRequest);
        
        assertEquals(HttpStatus.OK, response.getStatusCode());
        
        String expectedResponse = loadXmlFromClasspath("main-soap-responses/ListMembersWithFullHierarchyResponse.xml");
        assertXmlEquals(expectedResponse, response.getBody(), "ListMembersWithFullHierarchy response should match expected XML");
    }
    
    @Test
    public void testIsProviderWithVariousServiceTypes() throws Exception {
        mockProviderWithMixedServices();
        
        String soapRequest = loadXmlFromClasspath("main-soap-requests/IsProviderTrueRequest.xml");
        ResponseEntity<String> response = sendSoapRequest(soapRequest);
        
        assertEquals(HttpStatus.OK, response.getStatusCode());
        String expectedResponse = loadXmlFromClasspath("main-soap-responses/IsProviderTrueResponse.xml");
        assertXmlEquals(expectedResponse, response.getBody(), "IsProvider with various service types should return true");
    }
    
    @Test
    public void testIsProviderWithOnlyWsdlServices() throws Exception {
        mockProviderWithOnlyWsdl();
        
        String soapRequest = loadXmlFromClasspath("main-soap-requests/IsProviderTrueRequest.xml");
        ResponseEntity<String> response = sendSoapRequest(soapRequest);
        
        assertEquals(HttpStatus.OK, response.getStatusCode());
        String expectedResponse = loadXmlFromClasspath("main-soap-responses/IsProviderTrueResponse.xml");
        assertXmlEquals(expectedResponse, response.getBody(), "IsProvider with only WSDL services should return true");
    }
    
    @Test
    public void testIsProviderWithOnlyRestServices() throws Exception {
        mockProviderWithOnlyRest();
        
        String soapRequest = loadXmlFromClasspath("main-soap-requests/IsProviderTrueRequest.xml");
        ResponseEntity<String> response = sendSoapRequest(soapRequest);
        
        assertEquals(HttpStatus.OK, response.getStatusCode());
        String expectedResponse = loadXmlFromClasspath("main-soap-responses/IsProviderTrueResponse.xml");
        assertXmlEquals(expectedResponse, response.getBody(), "IsProvider with only REST services should return true");
    }
    
    @Test
    public void testIsProviderWithOnlyOpenApiServices() throws Exception {
        mockProviderWithOnlyOpenApi();
        
        String soapRequest = loadXmlFromClasspath("main-soap-requests/IsProviderTrueRequest.xml");
        ResponseEntity<String> response = sendSoapRequest(soapRequest);
        
        assertEquals(HttpStatus.OK, response.getStatusCode());
        String expectedResponse = loadXmlFromClasspath("main-soap-responses/IsProviderTrueResponse.xml");
        assertXmlEquals(expectedResponse, response.getBody(), "IsProvider with only OpenAPI services should return true");
    }
    
    @Test
    public void testIsProviderWithMultipleSubsystems() throws Exception {
        mockProviderWithMultipleSubsystems();
        
        String soapRequest = loadXmlFromClasspath("main-soap-requests/IsProviderTrueRequest.xml");
        ResponseEntity<String> response = sendSoapRequest(soapRequest);
        
        assertEquals(HttpStatus.OK, response.getStatusCode());
        String expectedResponse = loadXmlFromClasspath("main-soap-responses/IsProviderTrueResponse.xml");
        assertXmlEquals(expectedResponse, response.getBody(), "IsProvider with multiple subsystems should return true");
    }
    
    @Test
    public void testGetErrorsWithEmptyResult() throws Exception {
        // Mock empty error list to test the "not found" behavior mentioned in ServiceEndpointImpl:175-178
        given(catalogService.getErrorLog(any(LocalDateTime.class), any(LocalDateTime.class)))
                .willReturn(List.of());
        
        String soapRequest = loadXmlFromClasspath("main-soap-requests/GetErrorsRequest.xml");
        ResponseEntity<String> response = sendSoapRequest(soapRequest);
        
        String expectedResponse = loadXmlFromClasspath("main-soap-responses/GetErrorsEmptyResultResponse.xml");
        assertXmlEquals(expectedResponse, response.getBody(), "GetErrors empty result response should match expected SOAP fault");
    }

    // FI Profile disabled tests - verify error handling when FI profile services are unavailable
    
    @Test
    public void testGetOrganizationsFiProfileDisabled() throws Exception {
        String soapRequest = loadXmlFromClasspath("organization-soap-requests/GetOrganizationsRequest.xml");
        ResponseEntity<String> response = sendSoapRequest(soapRequest);
        
        assertEquals(HttpStatus.OK, response.getStatusCode());
        
        String expectedResponse = loadXmlFromClasspath("main-soap-responses/GetOrganizationsFiProfileDisabledResponse.xml");
        assertXmlEquals(expectedResponse, response.getBody(), "GetOrganizations should return error when FI profile is disabled");
    }
    
    @Test
    public void testHasOrganizationChangedFiProfileDisabled() throws Exception {
        String soapRequest = loadXmlFromClasspath("organization-soap-requests/HasOrganizationChangedRequest.xml");
        ResponseEntity<String> response = sendSoapRequest(soapRequest);
        
        assertEquals(HttpStatus.OK, response.getStatusCode());
        
        String expectedResponse = loadXmlFromClasspath("main-soap-responses/HasOrganizationChangedFiProfileDisabledResponse.xml");
        assertXmlEquals(expectedResponse, response.getBody(), "HasOrganizationChanged should return error when FI profile is disabled");
    }
    
    @Test
    public void testGetCompaniesFiProfileDisabled() throws Exception {
        String soapRequest = loadXmlFromClasspath("organization-soap-requests/GetCompaniesRequest.xml");
        ResponseEntity<String> response = sendSoapRequest(soapRequest);
        
        assertEquals(HttpStatus.OK, response.getStatusCode());
        
        String expectedResponse = loadXmlFromClasspath("main-soap-responses/GetCompaniesFiProfileDisabledResponse.xml");
        assertXmlEquals(expectedResponse, response.getBody(), "GetCompanies should return error when FI profile is disabled");
    }
    
    @Test
    public void testHasCompanyChangedFiProfileDisabled() throws Exception {
        String soapRequest = loadXmlFromClasspath("organization-soap-requests/HasCompanyChangedRequest.xml");
        ResponseEntity<String> response = sendSoapRequest(soapRequest);
        
        assertEquals(HttpStatus.OK, response.getStatusCode());
        
        String expectedResponse = loadXmlFromClasspath("main-soap-responses/HasCompanyChangedFiProfileDisabledResponse.xml");
        assertXmlEquals(expectedResponse, response.getBody(), "HasCompanyChanged should return error when FI profile is disabled");
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
                       // Ignore namespace prefix differences
                        (comparison, outcome) -> {
                            if (comparison.getType() == org.xmlunit.diff.ComparisonType.NAMESPACE_PREFIX) {
                                return org.xmlunit.diff.ComparisonResult.EQUAL;
                            }
                            return outcome;
                        }
                ))
                .ignoreWhitespace()
                .ignoreComments()
                .build();

        if (diff.hasDifferences()) {
            fail(message + "\n" + diff
                    + "\n\nExpected:\n" + expectedXml
                    + "\n\nActual:\n" + prettyPrintXml(actualXml));
        }
    }

    private String prettyPrintXml(String xml) {
        try {
            javax.xml.parsers.DocumentBuilderFactory factory = javax.xml.parsers.DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            javax.xml.parsers.DocumentBuilder builder = factory.newDocumentBuilder();
            org.w3c.dom.Document document = builder.parse(new java.io.ByteArrayInputStream(xml.getBytes()));

            javax.xml.transform.TransformerFactory transformerFactory = javax.xml.transform.TransformerFactory.newInstance();
            javax.xml.transform.Transformer transformer = transformerFactory.newTransformer();
            transformer.setOutputProperty(javax.xml.transform.OutputKeys.INDENT, "yes");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
            transformer.setOutputProperty(javax.xml.transform.OutputKeys.OMIT_XML_DECLARATION, "no");

            java.io.StringWriter stringWriter = new java.io.StringWriter();
            javax.xml.transform.stream.StreamResult streamResult = new javax.xml.transform.stream.StreamResult(stringWriter);
            javax.xml.transform.dom.DOMSource domSource = new javax.xml.transform.dom.DOMSource(document);
            transformer.transform(domSource, streamResult);

            return stringWriter.toString().replaceAll("\n\\s*\n", "\n");
        } catch (Exception e) {
            // If pretty printing fails, return original XML
            return xml;
        }
    }

    // Enhanced mock helper methods with rich hierarchical data
    
    private void mockMembersWithFullHierarchy() {
        // Member A: Financial institution with multiple subsystems and service types
        Member memberA = MainMockDataFactory.createMemberWithServices(MainMockDataFactory.MEMBER_CLASS_ORG,
                MainMockDataFactory.GOVT_ORG_CODE, MainMockDataFactory.GOVT_ORG_NAME,
                MainMockDataFactory.createSubsystemWithServices("TaxSystem",
                MainMockDataFactory.createStandardSoapService("calculateTax", "v1", "<wsdl>Tax calculation WSDL</wsdl>"),
                MainMockDataFactory.createStandardSoapService("validateTaxpayer", "v2", "<wsdl>Taxpayer validation WSDL</wsdl>"),
                MainMockDataFactory.createStandardRestService("getTaxHistory", "v1")
            ),
                MainMockDataFactory.createSubsystemWithServices("UserManagement",
                MainMockDataFactory.createStandardOpenApiService("userAPI", "v1", "OpenAPI spec for user management"),
                MainMockDataFactory.createStandardRestService("getUserData", "v1"),
                MainMockDataFactory.createStandardRestService("updateUserProfile", "v2")
            )
        );
        
        // Member B: Weather service provider with only OpenAPI
        Member memberB = MainMockDataFactory.createMemberWithServices(MainMockDataFactory.MEMBER_CLASS_COM,
                MainMockDataFactory.WEATHER_SERVICES_CODE, MainMockDataFactory.WEATHER_SERVICES_NAME,
                MainMockDataFactory.createSubsystemWithServices("WeatherAPI",
                MainMockDataFactory.createStandardOpenApiService("weatherAPI", "v1", "Weather forecasting API"),
                MainMockDataFactory.createStandardOpenApiService("climateAPI", "v2", "Climate data API")
            )
        );
        
        // Member C: Legacy SOAP-only provider
        Member memberC = MainMockDataFactory.createMemberWithServices(MainMockDataFactory.MEMBER_CLASS_ORG,
                MainMockDataFactory.LEGACY_SYSTEMS_CODE, MainMockDataFactory.LEGACY_SYSTEMS_NAME,
                MainMockDataFactory.createSubsystemWithServices("LegacyServices",
                MainMockDataFactory.createStandardSoapService("legacyOperation1", "v1", "<wsdl>Legacy WSDL 1</wsdl>"),
                MainMockDataFactory.createStandardSoapService("legacyOperation2", "v1", "<wsdl>Legacy WSDL 2</wsdl>"),
                MainMockDataFactory.createStandardSoapService("legacyBatchProcess", "v3", "<wsdl>Batch processing WSDL</wsdl>")
            )
        );
        
        // Member D: Service with multiple versions
        Member memberD = MainMockDataFactory.createMemberWithServices(MainMockDataFactory.MEMBER_CLASS_COM,
                MainMockDataFactory.DATA_SERVICES_CODE, MainMockDataFactory.DATA_SERVICES_NAME,
                MainMockDataFactory.createSubsystemWithServices("VersionedServices",
                MainMockDataFactory.createStandardSoapService("dataService", "v1", "<wsdl>Data service v1</wsdl>"),
                MainMockDataFactory.createStandardSoapService("dataService", "v2", "<wsdl>Data service v2</wsdl>"),
                MainMockDataFactory.createStandardOpenApiService("dataService", "v3", "Data service v3 OpenAPI")
            )
        );
        
        given(catalogService.getAllMembers(any(LocalDateTime.class), any(LocalDateTime.class)))
                .willReturn(Arrays.asList(memberA, memberB, memberC, memberD));
    }
    
    private void mockProviderWithMixedServices() {
        Member member = MainMockDataFactory.createMemberWithServices(MainMockDataFactory.MEMBER_CLASS_ORG,
                MainMockDataFactory.PROVIDER_MEMBER_CODE, MainMockDataFactory.PROVIDER_MEMBER_NAME,
                MainMockDataFactory.createSubsystemWithServices("MixedSubsystem",
                MainMockDataFactory.createStandardSoapService("soapOp", "v1", "<wsdl>SOAP WSDL</wsdl>"),
                MainMockDataFactory.createStandardRestService("restOp", "v1"),
                MainMockDataFactory.createStandardOpenApiService("apiOp", "v1", "OpenAPI spec")
            )
        );
        
        given(catalogService.getMember(MainMockDataFactory.DEFAULT_XROAD_INSTANCE, MainMockDataFactory.MEMBER_CLASS_ORG,
                MainMockDataFactory.PROVIDER_MEMBER_CODE)).willReturn(member);
    }
    
    private void mockProviderWithOnlyWsdl() {
        Member member = MainMockDataFactory.createMemberWithServices(MainMockDataFactory.MEMBER_CLASS_ORG,
                MainMockDataFactory.PROVIDER_MEMBER_CODE, MainMockDataFactory.PROVIDER_MEMBER_NAME,
                MainMockDataFactory.createSubsystemWithServices("WsdlOnlySubsystem",
                MainMockDataFactory.createStandardSoapService("soapOp1", "v1", "<wsdl>SOAP WSDL 1</wsdl>"),
                MainMockDataFactory.createStandardSoapService("soapOp2", "v1", "<wsdl>SOAP WSDL 2</wsdl>")
            )
        );
        
        given(catalogService.getMember(MainMockDataFactory.DEFAULT_XROAD_INSTANCE, MainMockDataFactory.MEMBER_CLASS_ORG,
                MainMockDataFactory.PROVIDER_MEMBER_CODE)).willReturn(member);
    }
    
    private void mockProviderWithOnlyRest() {
        Member member = MainMockDataFactory.createMemberWithServices(MainMockDataFactory.MEMBER_CLASS_ORG,
                MainMockDataFactory.PROVIDER_MEMBER_CODE, MainMockDataFactory.PROVIDER_MEMBER_NAME,
                MainMockDataFactory.createSubsystemWithServices("RestOnlySubsystem",
                MainMockDataFactory.createStandardRestService("restOp1", "v1"),
                MainMockDataFactory.createStandardRestService("restOp2", "v2")
            )
        );
        
        given(catalogService.getMember(MainMockDataFactory.DEFAULT_XROAD_INSTANCE, MainMockDataFactory.MEMBER_CLASS_ORG,
                MainMockDataFactory.PROVIDER_MEMBER_CODE)).willReturn(member);
    }
    
    private void mockProviderWithOnlyOpenApi() {
        Member member = MainMockDataFactory.createMemberWithServices(MainMockDataFactory.MEMBER_CLASS_ORG,
                MainMockDataFactory.PROVIDER_MEMBER_CODE, MainMockDataFactory.PROVIDER_MEMBER_NAME,
                MainMockDataFactory.createSubsystemWithServices("ApiOnlySubsystem",
                MainMockDataFactory.createStandardOpenApiService("apiOp1", "v1", "API spec 1"),
                MainMockDataFactory.createStandardOpenApiService("apiOp2", "v1", "API spec 2")
            )
        );
        
        given(catalogService.getMember(MainMockDataFactory.DEFAULT_XROAD_INSTANCE, MainMockDataFactory.MEMBER_CLASS_ORG,
                MainMockDataFactory.PROVIDER_MEMBER_CODE)).willReturn(member);
    }
    
    private void mockProviderWithMultipleSubsystems() {
        Member member = MainMockDataFactory.createMemberWithServices(MainMockDataFactory.MEMBER_CLASS_ORG,
                MainMockDataFactory.PROVIDER_MEMBER_CODE, MainMockDataFactory.PROVIDER_MEMBER_NAME,
                MainMockDataFactory.createSubsystemWithServices("Subsystem1",
                MainMockDataFactory.createStandardSoapService("service1", "v1", "<wsdl>Service 1</wsdl>")
            ),
                MainMockDataFactory.createSubsystemWithServices("Subsystem2",
                MainMockDataFactory.createStandardOpenApiService("service2", "v1", "API Service 2")
            ),
                MainMockDataFactory.createSubsystemWithServices("Subsystem3",
                MainMockDataFactory.createStandardRestService("service3", "v1")
            )
        );
        
        given(catalogService.getMember(MainMockDataFactory.DEFAULT_XROAD_INSTANCE, MainMockDataFactory.MEMBER_CLASS_ORG,
                MainMockDataFactory.PROVIDER_MEMBER_CODE)).willReturn(member);
    }

    // Mock helper methods (reused from ApplicationTests.java)

    private void mockMembersForListServices() {
        given(catalogService.getAllMembers(any(LocalDateTime.class), any(LocalDateTime.class)))
                .willReturn(MainMockDataFactory.getStandardMemberList());
    }

    private void mockServicesForGetServiceType(String serviceCode,
                                               String serviceType) {
        Service service;
        
        if ("SOAP".equalsIgnoreCase(serviceType)) {
            service = MainMockDataFactory.createStandardSoapService(serviceCode, "v1", "<wsdl>Test WSDL for " + serviceCode + "</wsdl>");
        } else if ("OPENAPI".equalsIgnoreCase(serviceType)) {
            service = MainMockDataFactory.createStandardOpenApiService(serviceCode, "v1", "OpenAPI spec for " + serviceCode);
        } else {
            // REST service
            service = MainMockDataFactory.createStandardRestService(serviceCode, "v1");
        }
        
        given(catalogService.getService(MainMockDataFactory.DEFAULT_XROAD_INSTANCE, MainMockDataFactory.MEMBER_CLASS_ORG,
                MainMockDataFactory.PROVIDER_MEMBER_CODE, serviceCode, "TestSubSystem", "v1"))
                .willReturn(service);
    }

    private void mockProvider() {
        given(catalogService.getMember(MainMockDataFactory.DEFAULT_XROAD_INSTANCE, MainMockDataFactory.MEMBER_CLASS_ORG,
                MainMockDataFactory.PROVIDER_MEMBER_CODE)).willReturn(MainMockDataFactory.PROVIDER_MEMBER);
    }

    private void mockNoProvider() {
        given(catalogService.getMember(MainMockDataFactory.DEFAULT_XROAD_INSTANCE, MainMockDataFactory.MEMBER_CLASS_ORG,
                MainMockDataFactory.NON_PROVIDER_MEMBER_CODE)).willReturn(MainMockDataFactory.NON_PROVIDER_MEMBER);
    }

    private void mockErrors() {
        given(catalogService.getErrorLog(any(LocalDateTime.class), any(LocalDateTime.class)))
                .willReturn(MainMockDataFactory.getStandardErrorList());
    }

}
