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
package org.niis.xroad.catalog.lister;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the shipped default: the health endpoint must expose the aggregate status only. No
 * {@code show-components}/{@code show-details} override is applied here on purpose, so this test fails
 * if the production {@code application.yaml} ever starts leaking the per-component breakdown to
 * unauthenticated probe traffic again.
 */
@SpringBootTest(classes = ListerApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "management.server.port=0")
@ActiveProfiles({"test", "general-testdata"})
class ListerHealthDetailsHiddenIntegrationTest {

    @LocalManagementPort
    private int managementPort;

    private final RestTemplate restTemplate = new RestTemplate();

    @Test
    void healthEndpointExposesStatusWithoutComponentsByDefault() throws Exception {
        ResponseEntity<String> response =
                restTemplate.getForEntity("http://localhost:" + managementPort + "/actuator/health", String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());

        JsonNode body = new ObjectMapper().readTree(response.getBody());
        assertTrue(body.has("status"));
        assertFalse(body.has("components"), "health response must not expose components by default: " + response.getBody());
        assertFalse(body.has("details"), "health response must not expose details by default: " + response.getBody());
    }

}
