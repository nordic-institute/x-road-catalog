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
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the default-off behaviour of {@code xroad-catalog.legacy-api.enabled}: with the property
 * genuinely absent, the entire V1 REST and SOAP surface must be unreachable, as if it never existed.
 */
@SpringBootTest(classes = ListerApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "springdoc.api-docs.enabled=true",
        "springdoc.swagger-ui.enabled=true"
})
@ActiveProfiles({"test", "general-testdata"})
class LegacyApiToggleDisabledTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void v1RestEndpointsAreNotFound() {
        assertEquals(404, restTemplate.getForEntity("/api/heartbeat", String.class).getStatusCode().value());
        assertEquals(404, restTemplate.getForEntity("/api/listSecurityServers", String.class).getStatusCode().value());
        assertEquals(404,
                restTemplate.getForEntity("/api/getServiceStatistics", String.class).getStatusCode().value());
    }

    @Test
    void soapEndpointsAreNotFound() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.TEXT_XML);
        HttpEntity<String> entity = new HttpEntity<>("<soap/>", headers);

        assertEquals(404, restTemplate.postForEntity("/ws", entity, String.class).getStatusCode().value());
        assertEquals(404, restTemplate.postForEntity("/ws/ListMembers", entity, String.class).getStatusCode().value());
        assertEquals(404, restTemplate.getForEntity("/ws?wsdl", String.class).getStatusCode().value());
    }

    @Test
    void v2HeartbeatIsUnaffected() {
        assertEquals(200, restTemplate.getForEntity("/api/v2/heartbeat", String.class).getStatusCode().value());
    }

    @Test
    void swaggerOnlyListsTheV2Group() throws JSONException {
        ResponseEntity<String> swaggerConfig = restTemplate.getForEntity("/v3/api-docs/swagger-config", String.class);
        assertEquals(200, swaggerConfig.getStatusCode().value());
        JSONObject json = new JSONObject(swaggerConfig.getBody());
        assertTrue(json.getJSONArray("urls").length() > 0);
        List<String> groupNames = new ArrayList<>();
        for (int i = 0; i < json.getJSONArray("urls").length(); i++) {
            groupNames.add(json.getJSONArray("urls").getJSONObject(i).getString("name"));
        }
        assertThat(groupNames, hasItem("v2"));
        assertThat(groupNames, not(hasItem("v1")));

        assertEquals(404, restTemplate.getForEntity("/v3/api-docs/v1", String.class).getStatusCode().value());
        assertEquals(200, restTemplate.getForEntity("/v3/api-docs/v2", String.class).getStatusCode().value());
    }
}
