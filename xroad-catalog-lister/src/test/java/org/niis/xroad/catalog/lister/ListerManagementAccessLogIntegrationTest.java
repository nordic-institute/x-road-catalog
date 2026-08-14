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

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.niis.xroad.catalog.management.lister.ManagementAccessLogFilter;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins both directions of the management access log: requests to the management port are logged, and
 * requests to the lister's public API on the main port are not.
 *
 * <p>The negative direction is the load-bearing one. {@code @ManagementContextConfiguration} is
 * meta-annotated with {@code @Configuration}, hence with {@code @Component}, so moving
 * {@code ManagementAccessLogConfiguration} into one of {@code ListerDefaultConfiguration}'s component-scan
 * roots would make the main context register the filter as well and start logging every public REST and
 * SOAP call. This test fails if that ever happens.
 *
 * <p>{@code management.server.port} must be overridden to {@code 0} here: {@code application-test.yaml}
 * sets it to {@code -1}, which would leave no management context — and therefore no filter — at all.
 */
@SpringBootTest(classes = ListerApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"management.server.port=0", "management.endpoint.health.show-components=always"})
@AutoConfigureObservability
@ActiveProfiles({"test", "general-testdata"})
class ListerManagementAccessLogIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @LocalManagementPort
    private int managementPort;

    private final RestTemplate managementRestTemplate = new RestTemplate();

    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void attachAppender() {
        appender = new ListAppender<>();
        appender.start();
        filterLogger().addAppender(appender);
    }

    @AfterEach
    void detachAppender() {
        filterLogger().detachAppender(appender);
        appender.stop();
    }

    @Test
    void managementPortRequestIsLogged() {
        ResponseEntity<String> response = managementRestTemplate.getForEntity(managementUrl("/actuator/health"), String.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());

        List<ILoggingEvent> events = capturedEvents();
        assertEquals(1, events.size(), "exactly one access log line per management request, got: " + events);

        ILoggingEvent event = events.get(0);
        assertEquals(Level.INFO, event.getLevel());
        String message = event.getFormattedMessage();
        assertTrue(message.startsWith("Management request "), message);
        assertTrue(message.contains(" GET /actuator/health "), "the request target must be logged, got: " + message);
        assertTrue(message.contains("-> 200 in "), "the response status must be logged, got: " + message);
        assertTrue(message.endsWith(" ms"), message);
    }

    /**
     * Hits the management port first, on purpose: proving capture works in this very context means the
     * empty result of the second phase cannot be a false negative caused by a mis-wired appender.
     */
    @Test
    void mainPortRequestIsNotLogged() {
        ResponseEntity<String> managementResponse =
                managementRestTemplate.getForEntity(managementUrl("/actuator/health"), String.class);
        assertEquals(HttpStatus.OK, managementResponse.getStatusCode());
        assertEquals(1, capturedEvents().size(), "sanity check: the management port must be logged");

        appender.list.clear();

        ResponseEntity<String> mainResponse = restTemplate.getForEntity("/api/v2/heartbeat", String.class);
        assertEquals(HttpStatus.OK, mainResponse.getStatusCode(), "the main port request must actually have been served");
        assertEquals(List.of(), capturedEvents(),
                "the main port must never be access logged — has ManagementAccessLogConfiguration been moved "
                        + "into a component-scanned package?");
    }

    private List<ILoggingEvent> capturedEvents() {
        return List.copyOf(appender.list);
    }

    private static Logger filterLogger() {
        return (Logger) LoggerFactory.getLogger(ManagementAccessLogFilter.class);
    }

    private String managementUrl(String path) {
        return "http://localhost:" + managementPort + path;
    }

}
