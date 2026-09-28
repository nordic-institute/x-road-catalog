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
package org.niis.xroad.catalog.collector.configuration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit test (no Spring context) for the client I/O timeout wiring in {@link TaskPoolConfiguration}.
 */
class TaskPoolConfigurationTest {

    private static final String SAAJ_CONNECT_TIMEOUT_PROPERTY = "saaj.connect.timeout";
    private static final String SAAJ_READ_TIMEOUT_PROPERTY = "saaj.read.timeout";

    private static final long CONNECT_TIMEOUT_SECONDS = 7;
    private static final long READ_TIMEOUT_SECONDS = 42;

    private final TaskPoolConfiguration taskPoolConfiguration = new TaskPoolConfiguration();

    private String savedConnectTimeoutProperty;
    private String savedReadTimeoutProperty;

    @BeforeEach
    void setUp() {
        savedConnectTimeoutProperty = System.getProperty(SAAJ_CONNECT_TIMEOUT_PROPERTY);
        savedReadTimeoutProperty = System.getProperty(SAAJ_READ_TIMEOUT_PROPERTY);
        ReflectionTestUtils.setField(taskPoolConfiguration, "clientConnectTimeoutSeconds", CONNECT_TIMEOUT_SECONDS);
        ReflectionTestUtils.setField(taskPoolConfiguration, "clientReadTimeoutSeconds", READ_TIMEOUT_SECONDS);
    }

    @AfterEach
    void tearDown() {
        restoreProperty(SAAJ_CONNECT_TIMEOUT_PROPERTY, savedConnectTimeoutProperty);
        restoreProperty(SAAJ_READ_TIMEOUT_PROPERTY, savedReadTimeoutProperty);
    }

    private void restoreProperty(String key, String savedValue) {
        if (savedValue == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, savedValue);
        }
    }

    @Test
    void restTemplateBeanCarriesConfiguredTimeouts() {
        RestTemplate restTemplate = taskPoolConfiguration.restTemplate();

        assertInstanceOf(SimpleClientHttpRequestFactory.class, restTemplate.getRequestFactory());
        SimpleClientHttpRequestFactory requestFactory = (SimpleClientHttpRequestFactory) restTemplate.getRequestFactory();

        // SimpleClientHttpRequestFactory exposes no timeout getters; read the private fields via reflection.
        assertEquals((int) Duration.ofSeconds(CONNECT_TIMEOUT_SECONDS).toMillis(),
                ReflectionTestUtils.getField(requestFactory, "connectTimeout"));
        assertEquals((int) Duration.ofSeconds(READ_TIMEOUT_SECONDS).toMillis(),
                ReflectionTestUtils.getField(requestFactory, "readTimeout"));
    }

    @Test
    void configureSaajTimeoutsSetsSystemPropertiesInMillis() {
        taskPoolConfiguration.configureSaajTimeouts();

        assertEquals(String.valueOf(Duration.ofSeconds(CONNECT_TIMEOUT_SECONDS).toMillis()),
                System.getProperty(SAAJ_CONNECT_TIMEOUT_PROPERTY));
        assertEquals(String.valueOf(Duration.ofSeconds(READ_TIMEOUT_SECONDS).toMillis()),
                System.getProperty(SAAJ_READ_TIMEOUT_PROPERTY));
    }

    @Test
    void normalizeUrlsStripsSingleTrailingSlash() {
        ReflectionTestUtils.setField(taskPoolConfiguration, "securityServerHost", "https://ss.example.org:8443/");

        taskPoolConfiguration.normalizeUrls();

        assertEquals("https://ss.example.org:8443", taskPoolConfiguration.getSecurityServerHost());
    }

    @Test
    void normalizeUrlsStripsMultipleTrailingSlashes() {
        ReflectionTestUtils.setField(taskPoolConfiguration, "listClientsHost", "https://ss.example.org:8443///");

        taskPoolConfiguration.normalizeUrls();

        assertEquals("https://ss.example.org:8443", taskPoolConfiguration.getListClientsHost());
    }

    @Test
    void normalizeUrlsLeavesValueWithoutTrailingSlashUnchanged() {
        ReflectionTestUtils.setField(taskPoolConfiguration, "webservicesEndpoint", "https://ss.example.org:8443");

        taskPoolConfiguration.normalizeUrls();

        assertEquals("https://ss.example.org:8443", taskPoolConfiguration.getWebservicesEndpoint());
    }

    @Test
    void normalizeUrlsLeavesNullValueUnchanged() {
        ReflectionTestUtils.setField(taskPoolConfiguration, "securityServerHost", null);

        taskPoolConfiguration.normalizeUrls();

        assertNull(taskPoolConfiguration.getSecurityServerHost());
    }

    @Test
    void validateHourWindowsAcceptsTheDefaultWindows() {
        setHourWindows(3, 4, 3, 4);

        assertDoesNotThrow(taskPoolConfiguration::validateHourWindows);
    }

    @Test
    void validateHourWindowsAcceptsTheFullDay() {
        setHourWindows(0, 23, 0, 23);

        assertDoesNotThrow(taskPoolConfiguration::validateHourWindows);
    }

    @Test
    void validateHourWindowsRejectsFetchHourAbove23() {
        setHourWindows(3, 24, 3, 4);

        IllegalStateException e = assertThrows(IllegalStateException.class, taskPoolConfiguration::validateHourWindows);
        assertTrue(e.getMessage().contains("xroad-catalog.tasks.fetch-time-before-hour"), e.getMessage());
        assertTrue(e.getMessage().contains("24"), e.getMessage());
    }

    @Test
    void validateHourWindowsRejectsNegativeFlushHour() {
        setHourWindows(3, 4, -1, 4);

        IllegalStateException e = assertThrows(IllegalStateException.class, taskPoolConfiguration::validateHourWindows);
        assertTrue(e.getMessage().contains("xroad-catalog.log-storage.flush-log-time-after-hour"), e.getMessage());
    }

    @Test
    void validateHourWindowsRejectsFetchWindowThatDoesNotStartBeforeItEnds() {
        setHourWindows(4, 3, 3, 4);

        IllegalStateException e = assertThrows(IllegalStateException.class, taskPoolConfiguration::validateHourWindows);
        assertTrue(e.getMessage().contains("xroad-catalog.tasks.fetch-time-after-hour"), e.getMessage());
        assertTrue(e.getMessage().contains("xroad-catalog.tasks.fetch-time-before-hour"), e.getMessage());
    }

    @Test
    void validateHourWindowsRejectsEmptyFlushWindow() {
        setHourWindows(3, 4, 3, 3);

        IllegalStateException e = assertThrows(IllegalStateException.class, taskPoolConfiguration::validateHourWindows);
        assertTrue(e.getMessage().contains("xroad-catalog.log-storage.flush-log-time-after-hour"), e.getMessage());
    }

    private void setHourWindows(int fetchAfter, int fetchBefore, int flushAfter, int flushBefore) {
        ReflectionTestUtils.setField(taskPoolConfiguration, "fetchTimeAfterHour", fetchAfter);
        ReflectionTestUtils.setField(taskPoolConfiguration, "fetchTimeBeforeHour", fetchBefore);
        ReflectionTestUtils.setField(taskPoolConfiguration, "flushLogTimeAfterHour", flushAfter);
        ReflectionTestUtils.setField(taskPoolConfiguration, "flushLogTimeBeforeHour", flushBefore);
    }

}
