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
package org.niis.xroad.catalog.collector;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.niis.xroad.catalog.collector.tasks.CollectionCycleRunner;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

import javax.sql.DataSource;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.willCallRealMethod;
import static org.mockito.BDDMockito.willThrow;

/**
 * Proves the actuator surface is served exclusively on the management port ({@code server.port=-1}
 * disables the main connector) and that the readiness health group reflects real database state.
 *
 * <p>{@link AutoConfigureObservability} is required: plain {@code @SpringBootTest} disables metrics
 * export by default, which would otherwise 404 {@code /actuator/prometheus} even though it works
 * outside of tests.
 */
@SpringBootTest(classes = CollectorApplication.class, webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT,
        properties = {"server.port=-1", "management.server.port=0"})
@AutoConfigureObservability
@ActiveProfiles({"test", "general-testdata"})
class CollectorManagementEndpointsIntegrationTest {

    @MockitoBean
    private CollectionCycleRunner collectionCycleRunner;

    @Autowired
    private Environment environment;

    @MockitoSpyBean
    private DataSource dataSource;

    @LocalManagementPort
    private int managementPort;

    private final RestTemplate restTemplate = new RestTemplate();

    private String managementUrl(String path) {
        return "http://localhost:" + managementPort + path;
    }

    @Test
    void healthEndpointsRespondOnManagementPort() {
        assertEquals(HttpStatus.OK, restTemplate.getForEntity(managementUrl("/actuator/health"), String.class).getStatusCode());
        assertEquals(HttpStatus.OK,
                restTemplate.getForEntity(managementUrl("/actuator/health/liveness"), String.class).getStatusCode());
        assertEquals(HttpStatus.OK,
                restTemplate.getForEntity(managementUrl("/actuator/health/readiness"), String.class).getStatusCode());
    }

    @Test
    void readinessGroupListsReadinessStateAndDbComponents() throws Exception {
        ResponseEntity<String> response = restTemplate.getForEntity(managementUrl("/actuator/health/readiness"), String.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());

        JsonNode components = new ObjectMapper().readTree(response.getBody()).get("components");
        assertTrue(components.has("readinessState"));
        assertTrue(components.has("db"));
    }

    @Test
    void prometheusEndpointReturnsPrometheusExpositionFormat() {
        ResponseEntity<String> response = restTemplate.getForEntity(managementUrl("/actuator/prometheus"), String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(MediaType.TEXT_PLAIN.isCompatibleWith(response.getHeaders().getContentType()));
        assertTrue(response.getBody() != null && !response.getBody().isBlank());
    }

    /**
     * With {@code server.port=-1} the main connector never binds; Boot still records the disabled
     * port verbatim as {@code local.server.port}, so {@code "-1"} (never a real bound port) is the
     * proof that no main HTTP listener ever came up.
     */
    @Test
    void mainConnectorNeverStarts() {
        assertEquals("-1", environment.getProperty("local.server.port"));
    }

    @Test
    void readinessReflectsDatabaseState() throws SQLException {
        assertEquals(HttpStatus.OK,
                restTemplate.getForEntity(managementUrl("/actuator/health/readiness"), String.class).getStatusCode());

        willThrow(new SQLException("Simulated database outage")).given(dataSource).getConnection();
        try {
            HttpServerErrorException exception = assertThrows(HttpServerErrorException.class,
                    () -> restTemplate.getForEntity(managementUrl("/actuator/health/readiness"), String.class));
            assertEquals(HttpStatus.SERVICE_UNAVAILABLE, exception.getStatusCode());

            assertEquals(HttpStatus.OK,
                    restTemplate.getForEntity(managementUrl("/actuator/health/liveness"), String.class).getStatusCode());
        } finally {
            willCallRealMethod().given(dataSource).getConnection();
        }

        assertEquals(HttpStatus.OK,
                restTemplate.getForEntity(managementUrl("/actuator/health/readiness"), String.class).getStatusCode());
    }

}
