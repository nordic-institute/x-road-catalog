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

package fi.dvv.xroad.catalog.lister.endpoint;

import org.junit.jupiter.api.Test;
import org.niis.xroad.catalog.lister.ListerApplication;
import fi.dvv.xroad.catalog.lister.service.CompanyService;
import fi.dvv.xroad.catalog.lister.service.OrganizationService;

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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.BDDMockito.given;

/**
 * HTTP-level integration tests for Organization SOAP endpoints.
 * These tests verify the exact SOAP message structure at the HTTP transport level.
 *
 * Test data is created using {@link OrganizationMockDataFactory} to ensure consistency across all tests.
 * All mock objects use standardized patterns for business codes, GUIDs, and timestamps.
 */
@SpringBootTest(classes = ListerApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@SuppressWarnings("PMD.AvoidDuplicateLiterals")
public class OrganizationEndpointIntegrationTest {

    private final TestRestTemplate restTemplate = new TestRestTemplate();

    @LocalServerPort
    private int port;

    @MockitoBean
    CompanyService companyService;

    @MockitoBean
    OrganizationService organizationService;

    private String getEndpointUrl() {
        return "http://localhost:" + port + "/ws";
    }

    @Test
    public void testGetOrganizationsHttpSoap() throws Exception {
        mockOrganizationsForBusinessCode(OrganizationMockDataFactory.GOVT_ORG_BUSINESS_CODE);

        String soapRequest = loadXmlFromClasspath("organization-soap-requests/GetOrganizationsRequest.xml");
        ResponseEntity<String> response = sendSoapRequest(soapRequest);
        
        assertEquals(HttpStatus.OK, response.getStatusCode());
        
        String expectedResponse = loadXmlFromClasspath("organization-soap-responses/GetOrganizationsResponse.xml");
        assertXmlEquals(expectedResponse, response.getBody(), "GetOrganizations response should match expected XML");
    }

    @Test
    public void testGetOrganizationsNotFoundHttpSoap() throws Exception {
        given(organizationService.getOrganizations(OrganizationMockDataFactory.NON_EXISTENT_BUSINESS_CODE))
                .willReturn(OrganizationMockDataFactory.getEmptyOrganizationList());

        String soapRequest = loadXmlFromClasspath("organization-soap-requests/GetOrganizationsNotFoundRequest.xml");
        ResponseEntity<String> response = sendSoapRequest(soapRequest);
        
        String expectedResponse = loadXmlFromClasspath("organization-soap-responses/GetOrganizationsNotFoundResponse.xml");
        assertXmlEquals(expectedResponse, response.getBody(), "GetOrganizations not found response should match expected SOAP fault");
    }

    @Test
    public void testHasOrganizationChangedHttpSoap() throws Exception {
        mockOrganizationChangedValues(OrganizationMockDataFactory.GOVT_ORG_GUID);

        String soapRequest = loadXmlFromClasspath("organization-soap-requests/HasOrganizationChangedRequest.xml");
        ResponseEntity<String> response = sendSoapRequest(soapRequest);
        
        assertEquals(HttpStatus.OK, response.getStatusCode());
        
        String expectedResponse = loadXmlFromClasspath("organization-soap-responses/HasOrganizationChangedResponse.xml");
        assertXmlEquals(expectedResponse, response.getBody(), "HasOrganizationChanged response should match expected XML");
    }

    @Test
    public void testHasOrganizationChangedNotFoundHttpSoap() throws Exception {
        given(organizationService.getOrganization(OrganizationMockDataFactory.NON_EXISTENT_GUID))
                .willReturn(OrganizationMockDataFactory.getEmptyOrganizationOptional());

        String soapRequest = loadXmlFromClasspath("organization-soap-requests/HasOrganizationChangedNotFoundRequest.xml");
        ResponseEntity<String> response = sendSoapRequest(soapRequest);
        
        String expectedResponse = loadXmlFromClasspath("organization-soap-responses/HasOrganizationChangedNotFoundResponse.xml");
        assertXmlEquals(expectedResponse, response.getBody(), "HasOrganizationChanged not found response should match expected SOAP fault");
    }

    @Test
    public void testHasOrganizationChangedNullGuidHttpSoap() throws Exception {
        String soapRequest = loadXmlFromClasspath("organization-soap-requests/HasOrganizationChangedNullGuidRequest.xml");
        ResponseEntity<String> response = sendSoapRequest(soapRequest);
        
        String expectedResponse = loadXmlFromClasspath("organization-soap-responses/HasOrganizationChangedNullGuidResponse.xml");
        assertXmlEquals(expectedResponse, response.getBody(), "HasOrganizationChanged null GUID response should match expected SOAP fault");
    }

    @Test
    public void testGetOrganizationsWithFullDataHttpSoap() throws Exception {
        given(organizationService.getOrganizations(OrganizationMockDataFactory.GOVT_ORG_BUSINESS_CODE))
                .willReturn(Arrays.asList(OrganizationMockDataFactory.createFullOrganizationWithAllDetails(
                        OrganizationMockDataFactory.GOVT_ORG_BUSINESS_CODE, OrganizationMockDataFactory.GOVT_ORG_GUID)));

        String soapRequest = loadXmlFromClasspath("organization-soap-requests/GetOrganizationsWithFullDataRequest.xml");
        ResponseEntity<String> response = sendSoapRequest(soapRequest);
        
        assertEquals(HttpStatus.OK, response.getStatusCode());
        
        String expectedResponse = loadXmlFromClasspath("organization-soap-responses/GetOrganizationsWithFullDataResponse.xml");
        assertXmlEquals(expectedResponse, response.getBody(), "GetOrganizations with full data response should match expected XML");
    }

    @Test
    public void testGetOrganizationsEmptyBusinessCodeHttpSoap() throws Exception {
        String soapRequest = loadXmlFromClasspath("organization-soap-requests/GetOrganizationsEmptyBusinessCodeRequest.xml");
        ResponseEntity<String> response = sendSoapRequest(soapRequest);
        
        String expectedResponse = loadXmlFromClasspath("organization-soap-responses/GetOrganizationsEmptyBusinessCodeResponse.xml");
        assertXmlEquals(expectedResponse, response.getBody(),
                "GetOrganizations empty business code response should match expected SOAP fault");
    }

    @Test
    public void testHasOrganizationChangedMissingStartDateTimeHttpSoap() throws Exception {
        mockOrganizationChangedValues(OrganizationMockDataFactory.GOVT_ORG_GUID);

        String soapRequest = loadXmlFromClasspath("organization-soap-requests/HasOrganizationChangedMissingStartDateTimeRequest.xml");
        ResponseEntity<String> response = sendSoapRequest(soapRequest);
        
        String expectedResponse = loadXmlFromClasspath(
                "organization-soap-responses/HasOrganizationChangedMissingStartDateTimeResponse.xml");
        assertXmlEquals(expectedResponse, response.getBody(),
                "HasOrganizationChanged missing startDateTime response should match expected SOAP fault");
    }

    @Test
    public void testHasOrganizationChangedMissingEndDateTimeHttpSoap() throws Exception {
        mockOrganizationChangedValues(OrganizationMockDataFactory.GOVT_ORG_GUID);

        String soapRequest = loadXmlFromClasspath("organization-soap-requests/HasOrganizationChangedMissingEndDateTimeRequest.xml");
        ResponseEntity<String> response = sendSoapRequest(soapRequest);
        
        String expectedResponse = loadXmlFromClasspath(
                "organization-soap-responses/HasOrganizationChangedMissingEndDateTimeResponse.xml");
        assertXmlEquals(expectedResponse, response.getBody(),
                "HasOrganizationChanged missing endDateTime response should match expected SOAP fault");
    }

    @Test
    public void testHasOrganizationChangedEmptyGuidHttpSoap() throws Exception {
        String soapRequest = loadXmlFromClasspath("organization-soap-requests/HasOrganizationChangedEmptyGuidRequest.xml");
        ResponseEntity<String> response = sendSoapRequest(soapRequest);
        
        String expectedResponse = loadXmlFromClasspath("organization-soap-responses/HasOrganizationChangedEmptyGuidResponse.xml");
        assertXmlEquals(expectedResponse, response.getBody(),
                "HasOrganizationChanged empty GUID response should match expected SOAP fault");
    }

    @Test
    public void testHasOrganizationChangedEndBeforeStartHttpSoap() throws Exception {
        mockOrganizationChangedValues(OrganizationMockDataFactory.GOVT_ORG_GUID);

        String soapRequest = loadXmlFromClasspath("organization-soap-requests/HasOrganizationChangedEndBeforeStartRequest.xml");
        ResponseEntity<String> response = sendSoapRequest(soapRequest);
        
        String expectedResponse = loadXmlFromClasspath("organization-soap-responses/HasOrganizationChangedEndBeforeStartResponse.xml");
        assertXmlEquals(expectedResponse, response.getBody(),
                "HasOrganizationChanged end before start response should match expected SOAP fault");
    }

    @Test
    public void testGetCompaniesHttpSoap() throws Exception {
        mockCompaniesForBusinessId(OrganizationMockDataFactory.TEST_COMPANY_BUSINESS_ID);

        String soapRequest = loadXmlFromClasspath("organization-soap-requests/GetCompaniesRequest.xml");
        ResponseEntity<String> response = sendSoapRequest(soapRequest);
        
        assertEquals(HttpStatus.OK, response.getStatusCode());
        
        String expectedResponse = loadXmlFromClasspath("organization-soap-responses/GetCompaniesResponse.xml");
        assertXmlEquals(expectedResponse, response.getBody(), "GetCompanies response should match expected XML");
    }

    @Test
    public void testGetCompaniesNotFoundHttpSoap() throws Exception {
        given(companyService.getCompanies(OrganizationMockDataFactory.NON_EXISTENT_BUSINESS_ID))
                .willReturn(OrganizationMockDataFactory.getEmptyCompanyList());

        String soapRequest = loadXmlFromClasspath("organization-soap-requests/GetCompaniesNotFoundRequest.xml");
        ResponseEntity<String> response = sendSoapRequest(soapRequest);
        
        String expectedResponse = loadXmlFromClasspath("organization-soap-responses/GetCompaniesNotFoundResponse.xml");
        assertXmlEquals(expectedResponse, response.getBody(), "GetCompanies not found response should match expected SOAP fault");
    }

    @Test
    public void testHasCompanyChangedHttpSoap() throws Exception {
        mockCompanyChangedValues(OrganizationMockDataFactory.TEST_COMPANY_BUSINESS_ID);

        String soapRequest = loadXmlFromClasspath("organization-soap-requests/HasCompanyChangedRequest.xml");
        ResponseEntity<String> response = sendSoapRequest(soapRequest);
        
        assertEquals(HttpStatus.OK, response.getStatusCode());
        
        String expectedResponse = loadXmlFromClasspath("organization-soap-responses/HasCompanyChangedResponse.xml");
        assertXmlEquals(expectedResponse, response.getBody(), "HasCompanyChanged response should match expected XML");
    }

    @Test
    public void testHasCompanyChangedNotFoundHttpSoap() throws Exception {
        given(companyService.getCompanies(OrganizationMockDataFactory.NON_EXISTENT_BUSINESS_ID))
                .willReturn(OrganizationMockDataFactory.getEmptyCompanyList());

        String soapRequest = loadXmlFromClasspath("organization-soap-requests/HasCompanyChangedNotFoundRequest.xml");
        ResponseEntity<String> response = sendSoapRequest(soapRequest);
        
        String expectedResponse = loadXmlFromClasspath("organization-soap-responses/HasCompanyChangedNotFoundResponse.xml");
        assertXmlEquals(expectedResponse, response.getBody(), "HasCompanyChanged not found response should match expected SOAP fault");
    }

    @Test
    public void testHasCompanyChangedNullBusinessIdHttpSoap() throws Exception {
        String soapRequest = loadXmlFromClasspath("organization-soap-requests/HasCompanyChangedNullBusinessIdRequest.xml");
        ResponseEntity<String> response = sendSoapRequest(soapRequest);
        
        String expectedResponse = loadXmlFromClasspath("organization-soap-responses/HasCompanyChangedNullBusinessIdResponse.xml");
        assertXmlEquals(expectedResponse, response.getBody(),
                "HasCompanyChanged null business ID response should match expected SOAP fault");
    }

    @Test
    public void testGetCompaniesWithFullDataHttpSoap() throws Exception {
        given(companyService.getCompanies(OrganizationMockDataFactory.TEST_COMPANY_BUSINESS_ID))
                .willReturn(Arrays.asList(OrganizationMockDataFactory.createFullCompanyWithAllDetails(
                        OrganizationMockDataFactory.TEST_COMPANY_BUSINESS_ID, "Test Company Ltd")));

        String soapRequest = loadXmlFromClasspath("organization-soap-requests/GetCompaniesWithFullDataRequest.xml");
        ResponseEntity<String> response = sendSoapRequest(soapRequest);
        
        assertEquals(HttpStatus.OK, response.getStatusCode());
        
        String expectedResponse = loadXmlFromClasspath("organization-soap-responses/GetCompaniesWithFullDataResponse.xml");
        assertXmlEquals(expectedResponse, response.getBody(), "GetCompanies with full data response should match expected XML");
    }

    @Test
    public void testGetCompaniesEmptyBusinessIdHttpSoap() throws Exception {
        String soapRequest = loadXmlFromClasspath("organization-soap-requests/GetCompaniesEmptyBusinessIdRequest.xml");
        ResponseEntity<String> response = sendSoapRequest(soapRequest);
        
        String expectedResponse = loadXmlFromClasspath("organization-soap-responses/GetCompaniesEmptyBusinessIdResponse.xml");
        assertXmlEquals(expectedResponse, response.getBody(), "GetCompanies empty business ID response should match expected SOAP fault");
    }

    @Test
    public void testHasCompanyChangedMissingStartDateTimeHttpSoap() throws Exception {
        mockCompanyChangedValues(OrganizationMockDataFactory.TEST_COMPANY_BUSINESS_ID);

        String soapRequest = loadXmlFromClasspath("organization-soap-requests/HasCompanyChangedMissingStartDateTimeRequest.xml");
        ResponseEntity<String> response = sendSoapRequest(soapRequest);
        
        String expectedResponse = loadXmlFromClasspath("organization-soap-responses/HasCompanyChangedMissingStartDateTimeResponse.xml");
        assertXmlEquals(expectedResponse, response.getBody(),
                "HasCompanyChanged missing startDateTime response should match expected SOAP fault");
    }

    @Test
    public void testHasCompanyChangedMissingEndDateTimeHttpSoap() throws Exception {
        mockCompanyChangedValues(OrganizationMockDataFactory.TEST_COMPANY_BUSINESS_ID);

        String soapRequest = loadXmlFromClasspath("organization-soap-requests/HasCompanyChangedMissingEndDateTimeRequest.xml");
        ResponseEntity<String> response = sendSoapRequest(soapRequest);
        
        String expectedResponse = loadXmlFromClasspath("organization-soap-responses/HasCompanyChangedMissingEndDateTimeResponse.xml");
        assertXmlEquals(expectedResponse, response.getBody(),
                "HasCompanyChanged missing endDateTime response should match expected SOAP fault");
    }

    @Test
    public void testHasCompanyChangedEmptyBusinessIdHttpSoap() throws Exception {
        String soapRequest = loadXmlFromClasspath("organization-soap-requests/HasCompanyChangedEmptyBusinessIdRequest.xml");
        ResponseEntity<String> response = sendSoapRequest(soapRequest);
        
        String expectedResponse = loadXmlFromClasspath("organization-soap-responses/HasCompanyChangedEmptyBusinessIdResponse.xml");
        assertXmlEquals(expectedResponse, response.getBody(),
                "HasCompanyChanged empty business ID response should match expected SOAP fault");
    }

    @Test
    public void testHasCompanyChangedEndBeforeStartHttpSoap() throws Exception {
        mockCompanyChangedValues(OrganizationMockDataFactory.TEST_COMPANY_BUSINESS_ID);

        String soapRequest = loadXmlFromClasspath("organization-soap-requests/HasCompanyChangedEndBeforeStartRequest.xml");
        ResponseEntity<String> response = sendSoapRequest(soapRequest);
        
        String expectedResponse = loadXmlFromClasspath("organization-soap-responses/HasCompanyChangedEndBeforeStartResponse.xml");
        assertXmlEquals(expectedResponse, response.getBody(),
                "HasCompanyChanged end before start response should match expected SOAP fault");
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
                        // Ignore namespace prefix differences
                        (comparison, outcome) -> {
                            if (comparison.getType() == org.xmlunit.diff.ComparisonType.NAMESPACE_PREFIX) {
                                return org.xmlunit.diff.ComparisonResult.EQUAL;
                            }
                            return outcome;
                        }
                ))
                // Remove this once we have completely switched to xrd4j
                // Ignore xml:lang attribute differences - XRD4J doesn't automatically add xml:lang="en" to faultstring elements
                .withAttributeFilter(attr -> !"xml:lang".equals(attr.getName()))
                .ignoreWhitespace()
                .ignoreComments()
                .build();

        if (diff.hasDifferences()) {
            fail(message + "\n" + diff.toString() + "\n\nExpected:\n" + expectedXml + "\n\nActual:\n" + actualXml);
        }
    }

    // Mock helper methods

    private void mockOrganizationsForBusinessCode(String businessCode) {
        given(organizationService.getOrganizations(businessCode))
                .willReturn(Arrays.asList(OrganizationMockDataFactory.createOrganizationWithBusinessCode(businessCode)));
    }

    private void mockOrganizationChangedValues(String guid) {
        // Create an organization with a changed time within the test window
        LocalDateTime changedTime = OrganizationMockDataFactory.FIXED_TEST_TIME.plusHours(1);
        fi.dvv.xroad.catalog.persistence.entity.Organization organization =
                OrganizationMockDataFactory.createOrganizationWithChangedTime(
                        OrganizationMockDataFactory.GOVT_ORG_BUSINESS_CODE, guid, changedTime);
        
        given(organizationService.getOrganization(guid))
                .willReturn(java.util.Optional.of(organization));
    }

    private void mockCompaniesForBusinessId(String businessId) {
        given(companyService.getCompanies(businessId))
                .willReturn(Arrays.asList(OrganizationMockDataFactory.createCompanyWithBusinessId(businessId)));
    }

    private void mockCompanyChangedValues(String businessId) {
        // Create a company with a changed time within the test window
        LocalDateTime changedTime = OrganizationMockDataFactory.FIXED_TEST_TIME.plusHours(1);
        fi.dvv.xroad.catalog.persistence.entity.Company company =
                OrganizationMockDataFactory.createCompanyWithChangedTime(
                        businessId, "Test Company Ltd", changedTime);

        given(companyService.getCompanies(businessId))
                .willReturn(Arrays.asList(company));
    }
}
