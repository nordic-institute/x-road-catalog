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
 * Test data is created using {@link SoapMockDataFactory} to ensure consistency across all tests.
 * All mock objects use standardized patterns for member codes, external IDs, and timestamps.
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
        Wsdl testWsdl = new Wsdl(new Service(), "<wsdl>This is WSDL content</wsdl>", externalId);
        testWsdl.setStatusInfo(SoapMockDataFactory.createStandardStatusInfo());
        given(catalogService.getWsdl(externalId))
                .willReturn(testWsdl);

        String soapRequest = loadXmlFromClasspath("soap-requests/GetWsdlRequest.xml");
        ResponseEntity<String> response = sendSoapRequest(soapRequest);
        
        assertEquals(HttpStatus.OK, response.getStatusCode());
        
        String expectedResponse = loadXmlFromClasspath("soap-responses/GetWsdlResponse.xml");
        assertXmlEquals(expectedResponse, response.getBody(), "GetWsdl response should match expected XML");
    }

    @Test
    public void testGetOpenApiHttpSoap() throws Exception {
        String externalId = "3003";
        OpenApi testOpenApi = new OpenApi(new Service(), "This is OpenAPI content", externalId);
        testOpenApi.setStatusInfo(SoapMockDataFactory.createStandardStatusInfo());
        given(catalogService.getOpenApi(externalId))
                .willReturn(testOpenApi);

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

    // Negative test cases - document current error handling behavior
    
    @Test
    public void testListMembersWithNullStartDateTime() throws Exception {
        mockMembersForListServices();

        String soapRequest = loadXmlFromClasspath("soap-requests/ListMembersNullStartDateRequest.xml");
        ResponseEntity<String> response = sendSoapRequest(soapRequest);
        
        String expectedResponse = loadXmlFromClasspath("soap-responses/ListMembersNullStartDateResponse.xml");
        assertXmlEquals(expectedResponse, response.getBody(), "ListMembers null startDateTime response should match expected SOAP fault");
    }

    @Test
    public void testListMembersWithNullEndDateTime() throws Exception {
        mockMembersForListServices();

        String soapRequest = loadXmlFromClasspath("soap-requests/ListMembersNullEndDateRequest.xml");
        ResponseEntity<String> response = sendSoapRequest(soapRequest);
        
        String expectedResponse = loadXmlFromClasspath("soap-responses/ListMembersNullEndDateResponse.xml");
        assertXmlEquals(expectedResponse, response.getBody(), "ListMembers null endDateTime response should match expected SOAP fault");
    }

    @Test
    public void testGetServiceTypeNotFound() throws Exception {
        given(catalogService.getService(
                SoapMockDataFactory.DEFAULT_XROAD_INSTANCE,
                SoapMockDataFactory.MEMBER_CLASS_ORG,
                "99999999",
                "NonExistentService",
                "NonExistentSubsystem",
                "v1"))
                .willReturn(null);

        String soapRequest = loadXmlFromClasspath("soap-requests/GetServiceTypeNotFoundRequest.xml");
        ResponseEntity<String> response = sendSoapRequest(soapRequest);
        
        String expectedResponse = loadXmlFromClasspath("soap-responses/GetServiceTypeNotFoundResponse.xml");
        assertXmlEquals(expectedResponse, response.getBody(), "GetServiceType not found response should match expected SOAP fault");
    }

    @Test
    public void testIsProviderMemberNotFound() throws Exception {
        given(catalogService.getMember(SoapMockDataFactory.DEFAULT_XROAD_INSTANCE, SoapMockDataFactory.MEMBER_CLASS_ORG, "99999999"))
                .willReturn(null);

        String soapRequest = loadXmlFromClasspath("soap-requests/IsProviderMemberNotFoundRequest.xml");
        ResponseEntity<String> response = sendSoapRequest(soapRequest);
        
        String expectedResponse = loadXmlFromClasspath("soap-responses/IsProviderMemberNotFoundResponse.xml");
        assertXmlEquals(expectedResponse, response.getBody(), "IsProvider member not found response should match expected SOAP fault");
    }

    @Test
    public void testGetWsdlNotFound() throws Exception {
        given(catalogService.getWsdl("nonexistent-id")).willReturn(null);

        String soapRequest = loadXmlFromClasspath("soap-requests/GetWsdlNotFoundRequest.xml");
        ResponseEntity<String> response = sendSoapRequest(soapRequest);
        
        String expectedResponse = loadXmlFromClasspath("soap-responses/GetWsdlNotFoundResponse.xml");
        assertXmlEquals(expectedResponse, response.getBody(), "GetWsdl not found response should match expected SOAP fault");
    }

    @Test
    public void testGetOpenApiNotFound() throws Exception {
        given(catalogService.getOpenApi("nonexistent-id")).willReturn(null);

        String soapRequest = loadXmlFromClasspath("soap-requests/GetOpenApiNotFoundRequest.xml");
        ResponseEntity<String> response = sendSoapRequest(soapRequest);
        
        String expectedResponse = loadXmlFromClasspath("soap-responses/GetOpenApiNotFoundResponse.xml");
        assertXmlEquals(expectedResponse, response.getBody(), "GetOpenApi not found response should match expected SOAP fault");
    }

    // Enhanced positive test cases with rich hierarchical data
    
    @Test
    public void testListMembersWithFullHierarchy() throws Exception {
        mockMembersWithFullHierarchy();
        
        String soapRequest = loadXmlFromClasspath("soap-requests/ListMembersRequest.xml");
        ResponseEntity<String> response = sendSoapRequest(soapRequest);
        
        assertEquals(HttpStatus.OK, response.getStatusCode());
        
        String expectedResponse = loadXmlFromClasspath("soap-responses/ListMembersWithFullHierarchyResponse.xml");
        assertXmlEquals(expectedResponse, response.getBody(), "ListMembersWithFullHierarchy response should match expected XML");
    }
    
    @Test
    public void testIsProviderWithVariousServiceTypes() throws Exception {
        mockProviderWithMixedServices();
        
        String soapRequest = loadXmlFromClasspath("soap-requests/IsProviderTrueRequest.xml");
        ResponseEntity<String> response = sendSoapRequest(soapRequest);
        
        assertEquals(HttpStatus.OK, response.getStatusCode());
        String expectedResponse = loadXmlFromClasspath("soap-responses/IsProviderTrueResponse.xml");
        assertXmlEquals(expectedResponse, response.getBody(), "IsProvider with various service types should return true");
    }
    
    @Test
    public void testIsProviderWithOnlyWsdlServices() throws Exception {
        mockProviderWithOnlyWsdl();
        
        String soapRequest = loadXmlFromClasspath("soap-requests/IsProviderTrueRequest.xml");
        ResponseEntity<String> response = sendSoapRequest(soapRequest);
        
        assertEquals(HttpStatus.OK, response.getStatusCode());
        String expectedResponse = loadXmlFromClasspath("soap-responses/IsProviderTrueResponse.xml");
        assertXmlEquals(expectedResponse, response.getBody(), "IsProvider with only WSDL services should return true");
    }
    
    @Test
    public void testIsProviderWithOnlyRestServices() throws Exception {
        mockProviderWithOnlyRest();
        
        String soapRequest = loadXmlFromClasspath("soap-requests/IsProviderTrueRequest.xml");
        ResponseEntity<String> response = sendSoapRequest(soapRequest);
        
        assertEquals(HttpStatus.OK, response.getStatusCode());
        String expectedResponse = loadXmlFromClasspath("soap-responses/IsProviderTrueResponse.xml");
        assertXmlEquals(expectedResponse, response.getBody(), "IsProvider with only REST services should return true");
    }
    
    @Test
    public void testIsProviderWithOnlyOpenApiServices() throws Exception {
        mockProviderWithOnlyOpenApi();
        
        String soapRequest = loadXmlFromClasspath("soap-requests/IsProviderTrueRequest.xml");
        ResponseEntity<String> response = sendSoapRequest(soapRequest);
        
        assertEquals(HttpStatus.OK, response.getStatusCode());
        String expectedResponse = loadXmlFromClasspath("soap-responses/IsProviderTrueResponse.xml");
        assertXmlEquals(expectedResponse, response.getBody(), "IsProvider with only OpenAPI services should return true");
    }
    
    @Test
    public void testIsProviderWithMultipleSubsystems() throws Exception {
        mockProviderWithMultipleSubsystems();
        
        String soapRequest = loadXmlFromClasspath("soap-requests/IsProviderTrueRequest.xml");
        ResponseEntity<String> response = sendSoapRequest(soapRequest);
        
        assertEquals(HttpStatus.OK, response.getStatusCode());
        String expectedResponse = loadXmlFromClasspath("soap-responses/IsProviderTrueResponse.xml");
        assertXmlEquals(expectedResponse, response.getBody(), "IsProvider with multiple subsystems should return true");
    }
    
    @Test
    public void testGetErrorsWithEmptyResult() throws Exception {
        // Mock empty error list to test the "not found" behavior mentioned in ServiceEndpointImpl:175-178
        given(catalogService.getErrorLog(any(LocalDateTime.class), any(LocalDateTime.class)))
                .willReturn(List.of());
        
        String soapRequest = loadXmlFromClasspath("soap-requests/GetErrorsRequest.xml");
        ResponseEntity<String> response = sendSoapRequest(soapRequest);
        
        String expectedResponse = loadXmlFromClasspath("soap-responses/GetErrorsEmptyResultResponse.xml");
        assertXmlEquals(expectedResponse, response.getBody(), "GetErrors empty result response should match expected SOAP fault");
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
                        DifferenceEvaluators.downgradeDifferencesToSimilar(org.xmlunit.diff.ComparisonType.TEXT_VALUE),
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
            fail(message + "\n" + diff.toString() + "\n\nExpected:\n" + expectedXml + "\n\nActual:\n" + actualXml);
        }
    }

    // Enhanced mock helper methods with rich hierarchical data
    
    private void mockMembersWithFullHierarchy() {
        // Member A: Financial institution with multiple subsystems and service types
        Member memberA = SoapMockDataFactory.createMemberWithServices(SoapMockDataFactory.MEMBER_CLASS_ORG,
                SoapMockDataFactory.GOVT_ORG_CODE, SoapMockDataFactory.GOVT_ORG_NAME,
                SoapMockDataFactory.createSubsystemWithServices("TaxSystem",
                SoapMockDataFactory.createStandardSoapService("calculateTax", "v1", "<wsdl>Tax calculation WSDL</wsdl>"),
                SoapMockDataFactory.createStandardSoapService("validateTaxpayer", "v2", "<wsdl>Taxpayer validation WSDL</wsdl>"),
                SoapMockDataFactory.createStandardRestService("getTaxHistory", "v1")
            ),
                SoapMockDataFactory.createSubsystemWithServices("UserManagement",
                SoapMockDataFactory.createStandardOpenApiService("userAPI", "v1", "OpenAPI spec for user management"),
                SoapMockDataFactory.createStandardRestService("getUserData", "v1"),
                SoapMockDataFactory.createStandardRestService("updateUserProfile", "v2")
            )
        );
        
        // Member B: Weather service provider with only OpenAPI
        Member memberB = SoapMockDataFactory.createMemberWithServices(SoapMockDataFactory.MEMBER_CLASS_COM,
                SoapMockDataFactory.WEATHER_SERVICES_CODE, SoapMockDataFactory.WEATHER_SERVICES_NAME,
                SoapMockDataFactory.createSubsystemWithServices("WeatherAPI",
                SoapMockDataFactory.createStandardOpenApiService("weatherAPI", "v1", "Weather forecasting API"),
                SoapMockDataFactory.createStandardOpenApiService("climateAPI", "v2", "Climate data API")
            )
        );
        
        // Member C: Legacy SOAP-only provider
        Member memberC = SoapMockDataFactory.createMemberWithServices(SoapMockDataFactory.MEMBER_CLASS_ORG,
                SoapMockDataFactory.LEGACY_SYSTEMS_CODE, SoapMockDataFactory.LEGACY_SYSTEMS_NAME,
                SoapMockDataFactory.createSubsystemWithServices("LegacyServices",
                SoapMockDataFactory.createStandardSoapService("legacyOperation1", "v1", "<wsdl>Legacy WSDL 1</wsdl>"),
                SoapMockDataFactory.createStandardSoapService("legacyOperation2", "v1", "<wsdl>Legacy WSDL 2</wsdl>"),
                SoapMockDataFactory.createStandardSoapService("legacyBatchProcess", "v3", "<wsdl>Batch processing WSDL</wsdl>")
            )
        );
        
        // Member D: Service with multiple versions
        Member memberD = SoapMockDataFactory.createMemberWithServices(SoapMockDataFactory.MEMBER_CLASS_COM,
                SoapMockDataFactory.DATA_SERVICES_CODE, SoapMockDataFactory.DATA_SERVICES_NAME,
                SoapMockDataFactory.createSubsystemWithServices("VersionedServices",
                SoapMockDataFactory.createStandardSoapService("dataService", "v1", "<wsdl>Data service v1</wsdl>"),
                SoapMockDataFactory.createStandardSoapService("dataService", "v2", "<wsdl>Data service v2</wsdl>"),
                SoapMockDataFactory.createStandardOpenApiService("dataService", "v3", "Data service v3 OpenAPI")
            )
        );
        
        given(catalogService.getAllMembers(any(LocalDateTime.class), any(LocalDateTime.class)))
                .willReturn(Arrays.asList(memberA, memberB, memberC, memberD));
    }
    
    private void mockProviderWithMixedServices() {
        Member member = SoapMockDataFactory.createMemberWithServices(SoapMockDataFactory.MEMBER_CLASS_ORG,
                SoapMockDataFactory.PROVIDER_MEMBER_CODE, SoapMockDataFactory.PROVIDER_MEMBER_NAME,
                SoapMockDataFactory.createSubsystemWithServices("MixedSubsystem",
                SoapMockDataFactory.createStandardSoapService("soapOp", "v1", "<wsdl>SOAP WSDL</wsdl>"),
                SoapMockDataFactory.createStandardRestService("restOp", "v1"),
                SoapMockDataFactory.createStandardOpenApiService("apiOp", "v1", "OpenAPI spec")
            )
        );
        
        given(catalogService.getMember(SoapMockDataFactory.DEFAULT_XROAD_INSTANCE, SoapMockDataFactory.MEMBER_CLASS_ORG,
                SoapMockDataFactory.PROVIDER_MEMBER_CODE)).willReturn(member);
    }
    
    private void mockProviderWithOnlyWsdl() {
        Member member = SoapMockDataFactory.createMemberWithServices(SoapMockDataFactory.MEMBER_CLASS_ORG,
                SoapMockDataFactory.PROVIDER_MEMBER_CODE, SoapMockDataFactory.PROVIDER_MEMBER_NAME,
                SoapMockDataFactory.createSubsystemWithServices("WsdlOnlySubsystem",
                SoapMockDataFactory.createStandardSoapService("soapOp1", "v1", "<wsdl>SOAP WSDL 1</wsdl>"),
                SoapMockDataFactory.createStandardSoapService("soapOp2", "v1", "<wsdl>SOAP WSDL 2</wsdl>")
            )
        );
        
        given(catalogService.getMember(SoapMockDataFactory.DEFAULT_XROAD_INSTANCE, SoapMockDataFactory.MEMBER_CLASS_ORG,
                SoapMockDataFactory.PROVIDER_MEMBER_CODE)).willReturn(member);
    }
    
    private void mockProviderWithOnlyRest() {
        Member member = SoapMockDataFactory.createMemberWithServices(SoapMockDataFactory.MEMBER_CLASS_ORG,
                SoapMockDataFactory.PROVIDER_MEMBER_CODE, SoapMockDataFactory.PROVIDER_MEMBER_NAME,
                SoapMockDataFactory.createSubsystemWithServices("RestOnlySubsystem",
                SoapMockDataFactory.createStandardRestService("restOp1", "v1"),
                SoapMockDataFactory.createStandardRestService("restOp2", "v2")
            )
        );
        
        given(catalogService.getMember(SoapMockDataFactory.DEFAULT_XROAD_INSTANCE, SoapMockDataFactory.MEMBER_CLASS_ORG,
                SoapMockDataFactory.PROVIDER_MEMBER_CODE)).willReturn(member);
    }
    
    private void mockProviderWithOnlyOpenApi() {
        Member member = SoapMockDataFactory.createMemberWithServices(SoapMockDataFactory.MEMBER_CLASS_ORG,
                SoapMockDataFactory.PROVIDER_MEMBER_CODE, SoapMockDataFactory.PROVIDER_MEMBER_NAME,
                SoapMockDataFactory.createSubsystemWithServices("ApiOnlySubsystem",
                SoapMockDataFactory.createStandardOpenApiService("apiOp1", "v1", "API spec 1"),
                SoapMockDataFactory.createStandardOpenApiService("apiOp2", "v1", "API spec 2")
            )
        );
        
        given(catalogService.getMember(SoapMockDataFactory.DEFAULT_XROAD_INSTANCE, SoapMockDataFactory.MEMBER_CLASS_ORG,
                SoapMockDataFactory.PROVIDER_MEMBER_CODE)).willReturn(member);
    }
    
    private void mockProviderWithMultipleSubsystems() {
        Member member = SoapMockDataFactory.createMemberWithServices(SoapMockDataFactory.MEMBER_CLASS_ORG,
                SoapMockDataFactory.PROVIDER_MEMBER_CODE, SoapMockDataFactory.PROVIDER_MEMBER_NAME,
                SoapMockDataFactory.createSubsystemWithServices("Subsystem1",
                SoapMockDataFactory.createStandardSoapService("service1", "v1", "<wsdl>Service 1</wsdl>")
            ),
                SoapMockDataFactory.createSubsystemWithServices("Subsystem2",
                SoapMockDataFactory.createStandardOpenApiService("service2", "v1", "API Service 2")
            ),
                SoapMockDataFactory.createSubsystemWithServices("Subsystem3",
                SoapMockDataFactory.createStandardRestService("service3", "v1")
            )
        );
        
        given(catalogService.getMember(SoapMockDataFactory.DEFAULT_XROAD_INSTANCE, SoapMockDataFactory.MEMBER_CLASS_ORG,
                SoapMockDataFactory.PROVIDER_MEMBER_CODE)).willReturn(member);
    }

    // Mock helper methods (reused from ApplicationTests.java)

    private void mockMembersForListServices() {
        given(catalogService.getAllMembers(any(LocalDateTime.class), any(LocalDateTime.class)))
                .willReturn(SoapMockDataFactory.getStandardMemberList());
    }

    private void mockServicesForGetServiceType(String serviceCode,
                                               String serviceType) {
        Service service;
        
        if ("SOAP".equalsIgnoreCase(serviceType)) {
            service = SoapMockDataFactory.createStandardSoapService(serviceCode, "v1", "<wsdl>Test WSDL for " + serviceCode + "</wsdl>");
        } else if ("OPENAPI".equalsIgnoreCase(serviceType)) {
            service = SoapMockDataFactory.createStandardOpenApiService(serviceCode, "v1", "OpenAPI spec for " + serviceCode);
        } else {
            // REST service
            service = SoapMockDataFactory.createStandardRestService(serviceCode, "v1");
        }
        
        given(catalogService.getService(SoapMockDataFactory.DEFAULT_XROAD_INSTANCE, SoapMockDataFactory.MEMBER_CLASS_ORG,
                SoapMockDataFactory.PROVIDER_MEMBER_CODE, serviceCode, "TestSubSystem", "v1"))
                .willReturn(service);
    }

    private void mockProvider() {
        given(catalogService.getMember(SoapMockDataFactory.DEFAULT_XROAD_INSTANCE, SoapMockDataFactory.MEMBER_CLASS_ORG,
                SoapMockDataFactory.PROVIDER_MEMBER_CODE)).willReturn(SoapMockDataFactory.PROVIDER_MEMBER);
    }

    private void mockNoProvider() {
        given(catalogService.getMember(SoapMockDataFactory.DEFAULT_XROAD_INSTANCE, SoapMockDataFactory.MEMBER_CLASS_ORG,
                SoapMockDataFactory.NON_PROVIDER_MEMBER_CODE)).willReturn(SoapMockDataFactory.NON_PROVIDER_MEMBER);
    }

    private void mockErrors() {
        given(catalogService.getErrorLog(any(LocalDateTime.class), any(LocalDateTime.class)))
                .willReturn(SoapMockDataFactory.getStandardErrorList());
    }

}
