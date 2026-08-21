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

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.niis.xroad.catalog.collector.tasks.CollectionCycleRunner;
import org.niis.xroad.catalog.management.collector.ManagementAccessLogFilter;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins that every request to the collector's management port leaves exactly one access log line.
 *
 * <p>There is no main-port counterpart to assert here: the collector runs with {@code server.port=-1},
 * so no main connector ever binds. The main-port negative case is covered on the lister side.
 */
@SpringBootTest(classes = CollectorApplication.class, webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT,
        properties = {"server.port=-1", "management.server.port=0", "management.endpoint.health.show-components=always"})
@AutoConfigureObservability
@ActiveProfiles({"test", "general-testdata"})
class CollectorManagementAccessLogIntegrationTest {

    @MockitoBean
    private CollectionCycleRunner collectionCycleRunner;

    @LocalManagementPort
    private int managementPort;

    private final RestTemplate restTemplate = new RestTemplate();

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
        ResponseEntity<String> response = restTemplate.getForEntity(managementUrl("/actuator/health"), String.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());

        List<ILoggingEvent> events = List.copyOf(appender.list);
        assertEquals(1, events.size(), "exactly one access log line per management request, got: " + events);

        ILoggingEvent event = events.get(0);
        assertEquals(Level.INFO, event.getLevel());
        String message = event.getFormattedMessage();
        assertTrue(message.startsWith("Management request "), message);
        assertTrue(message.contains(" GET /actuator/health "), "the request target must be logged, got: " + message);
        assertTrue(message.contains("-> 200 in "), "the response status must be logged, got: " + message);
        assertTrue(message.endsWith(" ms"), message);
    }

    private static Logger filterLogger() {
        return (Logger) LoggerFactory.getLogger(ManagementAccessLogFilter.class);
    }

    private String managementUrl(String path) {
        return "http://localhost:" + managementPort + path;
    }

}
