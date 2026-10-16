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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.niis.xrd4j.common.exception.XRd4JException;
import org.niis.xrd4j.common.member.ConsumerMember;
import org.niis.xrd4j.common.member.ObjectType;
import org.niis.xroad.catalog.collector.service.CatalogService;
import org.niis.xroad.catalog.persistence.entity.ErrorLog;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Clock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * The X-Road {@code getOpenAPI} metaservice returns the description exactly as the service publishes
 * it, as YAML or JSON, so the collector must keep it verbatim instead of requiring JSON.
 */
class MethodListUtilTest {

    private static final String HOST = "http://ss0:8080";
    private static final String OPENAPI_URL = HOST + "/r1/DEV/COM/1234/TestService/getOpenAPI?serviceCode=petstore";

    private RestTemplate restTemplate;
    private MockRestServiceServer server;
    private CatalogService catalogService;

    @BeforeEach
    void setUp() {
        restTemplate = new RestTemplate();
        server = MockRestServiceServer.bindTo(restTemplate).build();
        catalogService = mock(CatalogService.class);
        // the error-log dedupe remembers the last failing member across calls
        ReflectionTestUtils.setField(MethodListUtil.class, "securityServerMetadata", null);
    }

    @Test
    void openApiFromResponseKeepsYamlDescriptorVerbatim() throws XRd4JException {
        String yaml = "openapi: 3.0.3\ninfo:\n  title: Petstore\n  description: Lemmikkieläimet ä ö\n"
                + "  version: '1.0'\npaths: {}\n";
        server.expect(requestTo(OPENAPI_URL))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-Road-Client", "DEV/COM/4321/catalog"))
                .andRespond(withSuccess(yaml, MediaType.parseMediaType("application/yaml")));

        String descriptor = MethodListUtil.openApiFromResponse(service(), HOST, consumer(), catalogService,
                restTemplate, Clock.systemUTC());

        assertEquals(yaml, descriptor);
        verify(catalogService, never()).saveErrorLog(any());
        server.verify();
    }

    @Test
    void openApiFromResponseKeepsJsonDescriptorVerbatim() throws XRd4JException {
        String json = "{\n  \"openapi\": \"3.0.3\",\n"
                + "  \"info\": {\"title\": \"Petstore\", \"description\": \"Lemmikkieläimet ä ö\", \"version\": \"1.0\"},\n"
                + "  \"paths\": {}\n}\n";
        server.expect(requestTo(OPENAPI_URL))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

        String descriptor = MethodListUtil.openApiFromResponse(service(), HOST, consumer(), catalogService,
                restTemplate, Clock.systemUTC());

        assertEquals(json, descriptor);
        verify(catalogService, never()).saveErrorLog(any());
    }

    @Test
    void openApiFromResponseReadsCharsetlessBodyAsUtf8() throws XRd4JException {
        String yaml = "openapi: 3.0.3\ninfo:\n  title: Café ÕÄÖÜ\n";
        // the security server falls back to a bare text/plain when the service declares no Content-Type
        server.expect(requestTo(OPENAPI_URL))
                .andRespond(withSuccess(yaml.getBytes(StandardCharsets.UTF_8), MediaType.TEXT_PLAIN));

        String descriptor = MethodListUtil.openApiFromResponse(service(), HOST, consumer(), catalogService,
                restTemplate, Clock.systemUTC());

        assertEquals(yaml, descriptor);
    }

    @Test
    void openApiFromResponseHonoursDeclaredCharset() throws XRd4JException {
        String yaml = "openapi: 3.0.3\ninfo:\n  title: Café\n";
        server.expect(requestTo(OPENAPI_URL))
                .andRespond(withSuccess(yaml.getBytes(StandardCharsets.ISO_8859_1),
                        MediaType.parseMediaType("text/plain; charset=ISO-8859-1")));

        String descriptor = MethodListUtil.openApiFromResponse(service(), HOST, consumer(), catalogService,
                restTemplate, Clock.systemUTC());

        assertEquals(yaml, descriptor);
    }

    @Test
    void openApiFromResponseReturnsNullAndRecordsErrorWhenFetchFails() throws XRd4JException {
        server.expect(requestTo(OPENAPI_URL)).andRespond(withServerError());

        String descriptor = MethodListUtil.openApiFromResponse(service(), HOST, consumer(), catalogService,
                restTemplate, Clock.systemUTC());

        assertNull(descriptor);
        ArgumentCaptor<ErrorLog> errorLog = ArgumentCaptor.forClass(ErrorLog.class);
        verify(catalogService).saveErrorLog(errorLog.capture());
        assertTrue(errorLog.getValue().getMessage().startsWith("Fetch of OpenAPI descriptor failed(url: " + OPENAPI_URL),
                errorLog.getValue().getMessage());
        assertEquals("petstore", errorLog.getValue().getServiceCode());
    }

    private static ConsumerMember consumer() throws XRd4JException {
        return new ConsumerMember("DEV", "COM", "4321", "catalog");
    }

    private static XRoadIdentifier service() {
        return XRoadIdentifier.builder()
                .xRoadInstance("DEV")
                .memberClass("COM")
                .memberCode("1234")
                .subsystemCode("TestService")
                .serviceCode("petstore")
                .objectType(ObjectType.SERVICE)
                .serviceType("OPENAPI3")
                .build();
    }
}
