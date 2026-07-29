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
package org.niis.xroad.catalog.lister.v2.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.catalog.lister.v2.dto.MemberClassInfo;
import org.niis.xroad.catalog.lister.v2.dto.SecurityServerInfoV2;
import org.niis.xroad.catalog.lister.v2.dto.SubsystemNameInfo;
import org.niis.xroad.catalog.lister.v2.parser.SharedParamsParserV2;
import org.slf4j.LoggerFactory;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SharedParamsCacheTest {

    private static final String SHARED_PARAMS_FILE = "shared-params.xml";

    @Mock
    private SharedParamsParserV2 parser;

    private final MutableClock clock = new MutableClock(Instant.parse("2024-01-01T00:00:00Z"));

    private SharedParamsCache cache;

    @BeforeEach
    void setUp() {
        cache = new SharedParamsCache(parser, clock, SHARED_PARAMS_FILE);
    }

    @Test
    void twoCallsWithinTtlInvokeParserOnlyOnce() throws Exception {
        stubEmptyParseResults();

        cache.securityServers();
        cache.securityServers();

        verify(parser, times(1)).parseSecurityServers(anyString());
        verify(parser, times(1)).parseSubsystemNames(anyString());
        verify(parser, times(1)).parseMemberClasses(anyString());
    }

    @Test
    void refreshesAfterTtlExpires() throws Exception {
        stubEmptyParseResults();

        cache.securityServers();
        clock.advance(Duration.ofSeconds(61));
        cache.securityServers();

        verify(parser, times(2)).parseSecurityServers(anyString());
    }

    @Test
    void staysCachedJustUnderTtl() throws Exception {
        stubEmptyParseResults();

        cache.securityServers();
        clock.advance(Duration.ofSeconds(59));
        cache.securityServers();

        verify(parser, times(1)).parseSecurityServers(anyString());
    }

    @Test
    void memberClassesReadsDescriptionsAndCodesFromASingleParse() throws Exception {
        stubEmptyParseResults();
        when(parser.parseMemberClasses(anyString())).thenReturn(List.of(
                MemberClassInfo.builder().code("PUB").description("Public").build()));

        SharedParamsCache.MemberClasses memberClasses = cache.memberClasses();

        assertEquals("Public", memberClasses.descriptions().get("PUB"));
        assertTrue(memberClasses.codes().contains("PUB"));
        verify(parser, times(1)).parseMemberClasses(anyString());
    }

    @Test
    void parseFailureYieldsEmptyResultsWithoutThrowingAndIsCachedForTheTtl() throws Exception {
        when(parser.parseSubsystemNames(anyString())).thenThrow(new IOException("boom"));

        assertTrue(cache.securityServers().isEmpty());
        assertTrue(cache.memberClassDescriptions().isEmpty());
        assertTrue(cache.memberClassCodes().isEmpty());
        assertNull(cache.subsystemNames().resolve("PUB", "1234", "sub"));

        // still within the TTL window: the failed parse must not be retried on every call
        cache.securityServers();
        verify(parser, times(1)).parseSubsystemNames(anyString());
    }

    @Test
    void getCurrentInstanceThrows503WhileFileMissing() {
        assertEquals(503, assertThrows(ResponseStatusException.class, cache::getCurrentInstance)
                .getStatusCode().value());
        verifyNoInteractions(parser);
    }

    @Test
    void getCurrentInstanceCachesValueForeverAfterFirstSuccess(@TempDir Path tmp) throws Exception {
        Path file = tmp.resolve("shared-params.xml");
        Files.writeString(file, "irrelevant, parser is mocked");
        SharedParamsCache fileBacked = new SharedParamsCache(parser, clock, file.toString());
        when(parser.parseInstanceIdentifier(file.toString())).thenReturn("DEV");

        assertEquals("DEV", fileBacked.getCurrentInstance());

        // Delete the file — a fresh load would now fail. The cached call must succeed.
        Files.delete(file);
        assertEquals("DEV", fileBacked.getCurrentInstance());
        verify(parser, times(1)).parseInstanceIdentifier(file.toString());
    }

    @Test
    void getCurrentInstanceRecoversAfterFileAppears(@TempDir Path tmp) throws Exception {
        Path file = tmp.resolve("shared-params.xml");
        SharedParamsCache fileBacked = new SharedParamsCache(parser, clock, file.toString());

        assertThrows(ResponseStatusException.class, fileBacked::getCurrentInstance);

        Files.writeString(file, "irrelevant, parser is mocked");
        when(parser.parseInstanceIdentifier(file.toString())).thenReturn("DEV");
        assertEquals("DEV", fileBacked.getCurrentInstance());
    }

    @Test
    void getCurrentInstanceWrapsParseFailureAsIllegalState(@TempDir Path tmp) throws Exception {
        Path file = tmp.resolve("shared-params.xml");
        Files.writeString(file, "not valid shared-params");
        SharedParamsCache fileBacked = new SharedParamsCache(parser, clock, file.toString());
        when(parser.parseInstanceIdentifier(file.toString())).thenThrow(new IOException("does not validate"));

        assertThrows(IllegalStateException.class, fileBacked::getCurrentInstance);
    }

    @Test
    void globalConfExpiryReadsExpirationFromMetadataSidecar(@TempDir Path tmp) throws Exception {
        stubEmptyParseResults();
        SharedParamsCache fileBacked = cacheWithMetadata(tmp, "2024-01-02T00:00:00Z");

        SharedParamsCache.GlobalConfExpiry expiry = fileBacked.globalConfExpiry();

        assertFalse(expiry.expired());
        assertEquals(Instant.parse("2024-01-02T00:00:00Z"), expiry.expiresAt());
    }

    @Test
    void expiredGlobalConfIsFlaggedAndWarnedButDataIsStillServed(@TempDir Path tmp) throws Exception {
        stubEmptyParseResults();
        when(parser.parseMemberClasses(anyString())).thenReturn(List.of(
                MemberClassInfo.builder().code("PUB").description("Public").build()));
        SharedParamsCache fileBacked = cacheWithMetadata(tmp, "2023-12-31T12:00:00Z");
        ListAppender<ILoggingEvent> appender = attachAppender();
        try {
            SharedParamsCache.GlobalConfExpiry expiry = fileBacked.globalConfExpiry();

            assertTrue(expiry.expired());
            assertEquals(Instant.parse("2023-12-31T12:00:00Z"), expiry.expiresAt());
            assertTrue(fileBacked.memberClassCodes().contains("PUB"),
                    "expired conf must be flagged, not refused — data is still served");
            List<ILoggingEvent> warns = appender.list.stream()
                    .filter(event -> event.getLevel() == Level.WARN).toList();
            assertEquals(1, warns.size(), "exactly one warning per refresh");
            assertTrue(warns.get(0).getFormattedMessage().contains("expired since 2023-12-31T12:00:00Z"));
        } finally {
            detachAppender(appender);
        }
    }

    @Test
    void missingMetadataSidecarReportsUnknownExpiryWithoutWarning() throws Exception {
        stubEmptyParseResults();
        ListAppender<ILoggingEvent> appender = attachAppender();
        try {
            SharedParamsCache.GlobalConfExpiry expiry = cache.globalConfExpiry();

            assertFalse(expiry.expired());
            assertNull(expiry.expiresAt());
            assertTrue(appender.list.stream().noneMatch(event -> event.getLevel() == Level.WARN),
                    "an unknown expiry must not produce warnings");
        } finally {
            detachAppender(appender);
        }
    }

    @Test
    void unparsableMetadataSidecarReportsUnknownExpiryAndKeepsServingData(@TempDir Path tmp) throws Exception {
        stubEmptyParseResults();
        when(parser.parseMemberClasses(anyString())).thenReturn(List.of(
                MemberClassInfo.builder().code("PUB").description("Public").build()));
        Path file = tmp.resolve("shared-params.xml");
        Files.writeString(file, "irrelevant, parser is mocked");
        Files.writeString(tmp.resolve("shared-params.xml.metadata"), "not json at all");
        SharedParamsCache fileBacked = new SharedParamsCache(parser, clock, file.toString());

        SharedParamsCache.GlobalConfExpiry expiry = fileBacked.globalConfExpiry();

        assertFalse(expiry.expired());
        assertNull(expiry.expiresAt());
        assertTrue(fileBacked.memberClassCodes().contains("PUB"));
    }

    @Test
    void confLapsingMidTtlWindowIsReportedExpiredWithoutARefresh(@TempDir Path tmp) throws Exception {
        stubEmptyParseResults();
        SharedParamsCache fileBacked = cacheWithMetadata(tmp, "2024-01-01T00:00:30Z");

        assertFalse(fileBacked.globalConfExpiry().expired());
        clock.advance(Duration.ofSeconds(40));
        assertTrue(fileBacked.globalConfExpiry().expired(), "expired is evaluated against the clock at call time");
        verify(parser, times(1)).parseSecurityServers(anyString());
    }

    private SharedParamsCache cacheWithMetadata(Path tmp, String expirationDate) throws IOException {
        Path file = tmp.resolve("shared-params.xml");
        Files.writeString(file, "irrelevant, parser is mocked");
        Files.writeString(tmp.resolve("shared-params.xml.metadata"),
                "{\"contentIdentifier\":\"SHARED-PARAMETERS\",\"instanceIdentifier\":\"DEV\","
                        + "\"expirationDate\":\"" + expirationDate + "\",\"contentFileName\":null,"
                        + "\"contentLocation\":\"\",\"configurationVersion\":\"5\"}");
        return new SharedParamsCache(parser, clock, file.toString());
    }

    private ListAppender<ILoggingEvent> attachAppender() {
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        ((Logger) LoggerFactory.getLogger(SharedParamsCache.class)).addAppender(appender);
        return appender;
    }

    private void detachAppender(ListAppender<ILoggingEvent> appender) {
        ((Logger) LoggerFactory.getLogger(SharedParamsCache.class)).detachAppender(appender);
    }

    private void stubEmptyParseResults() throws Exception {
        when(parser.parseSubsystemNames(anyString())).thenReturn(List.<SubsystemNameInfo>of());
        when(parser.parseMemberClasses(anyString())).thenReturn(List.<MemberClassInfo>of());
        when(parser.parseSecurityServers(anyString())).thenReturn(List.<SecurityServerInfoV2>of());
    }

    private static final class MutableClock extends Clock {

        private Instant now;

        MutableClock(Instant now) {
            super();
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
