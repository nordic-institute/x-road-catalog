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
package org.niis.xroad.catalog.lister.configuration;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.niis.xroad.catalog.lister.ListerApplication;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the opt-in behaviour of {@code xroad-catalog.legacy-api.enabled}: with the property set to
 * {@code true}, the whole V1 REST and SOAP surface must be reachable again, together with its
 * springdoc group.
 */
@SpringBootTest(classes = ListerApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "xroad-catalog.legacy-api.enabled=true",
        "springdoc.api-docs.enabled=true",
        "springdoc.swagger-ui.enabled=true"
})
@ActiveProfiles({"test", "general-testdata"})
class LegacyApiToggleEnabledTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void v1HeartbeatIsReachable() {
        assertEquals(200, restTemplate.getForEntity("/api/heartbeat", String.class).getStatusCode().value());
    }

    @Test
    void swaggerListsBothGroupsWithV2Primary() throws JSONException {
        ResponseEntity<String> swaggerConfig = restTemplate.getForEntity("/v3/api-docs/swagger-config", String.class);
        assertEquals(200, swaggerConfig.getStatusCode().value());
        JSONObject json = new JSONObject(swaggerConfig.getBody());
        List<String> groupNames = new ArrayList<>();
        for (int i = 0; i < json.getJSONArray("urls").length(); i++) {
            groupNames.add(json.getJSONArray("urls").getJSONObject(i).getString("name"));
        }
        assertThat(groupNames, hasItem("v1"));
        assertThat(groupNames, hasItem("v2"));
        assertEquals("v2", json.getString("urls.primaryName"));
    }

    @Test
    void wsdlIsServedViaQueryParameter() {
        ResponseEntity<String> response = restTemplate.getForEntity("/ws?wsdl", String.class);
        assertEquals(200, response.getStatusCode().value());
        String body = response.getBody();
        assertNotNull(body);
        assertTrue(body.contains("wsdl:definitions"));
    }

    @Test
    void wsdlIsServedViaServicesWsdlPath() {
        ResponseEntity<String> response = restTemplate.getForEntity("/ws/services.wsdl", String.class);
        assertEquals(200, response.getStatusCode().value());
        String body = response.getBody();
        assertNotNull(body);
        assertTrue(body.contains("wsdl:definitions"));
    }
}
