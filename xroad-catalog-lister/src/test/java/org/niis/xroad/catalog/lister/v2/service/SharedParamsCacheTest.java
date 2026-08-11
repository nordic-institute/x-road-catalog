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
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.catalog.lister.v2.dto.MemberClassInfo;
import org.niis.xroad.catalog.lister.v2.parser.SharedParamsParserV2;
import org.niis.xroad.catalog.lister.v2.parser.SharedParamsParserV2.ParsedSharedParams;
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
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

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
    private static final String METADATA_FILE = "shared-params.xml.metadata";
    private static final String MOCKED_PARSER_CONTENT = "irrelevant, parser is mocked";
    private static final String INSTANCE = "DEV";
    private static final String MEMBER_CLASS_CODE = "PUB";
    private static final String MEMBER_CLASS_DESCRIPTION = "Public";
    private static final ParsedSharedParams EMPTY_PARSE =
            new ParsedSharedParams(List.of(), List.of(), List.of());
    private static final long TIMEOUT_SECONDS = 5;
    private static final long JOIN_MILLIS = 5_000;

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
        stubEmptyParse();

        cache.securityServers();
        cache.securityServers();

        verify(parser, times(1)).parse(anyString());
    }

    @Test
    void oneRefreshParsesTheDocumentOnceForEveryDerivedResultSet() throws Exception {
        stubEmptyParse();

        cache.subsystemNames();
        cache.memberClasses();
        cache.securityServers();

        verify(parser, times(1)).parse(anyString());
    }

    @Test
    void refreshesAfterTtlExpires() throws Exception {
        stubEmptyParse();

        cache.securityServers();
        clock.advance(Duration.ofSeconds(61));
        cache.securityServers();

        verify(parser, times(2)).parse(anyString());
    }

    @Test
    void staysCachedJustUnderTtl() throws Exception {
        stubEmptyParse();

        cache.securityServers();
        clock.advance(Duration.ofSeconds(59));
        cache.securityServers();

        verify(parser, times(1)).parse(anyString());
    }

    @Test
    void memberClassesReadsDescriptionsAndCodesFromASingleParse() throws Exception {
        when(parser.parse(anyString())).thenReturn(parseWithMemberClass());

        SharedParamsCache.MemberClasses memberClasses = cache.memberClasses();

        assertEquals(MEMBER_CLASS_DESCRIPTION, memberClasses.descriptions().get(MEMBER_CLASS_CODE));
        assertTrue(memberClasses.codes().contains(MEMBER_CLASS_CODE));
        verify(parser, times(1)).parse(anyString());
    }

    /**
     * Guards the take-one-snapshot-reference rule in memberClasses(): were it refactored into two
     * independent cache reads (descriptions, then codes), a TTL expiry between them would mix data
     * from two different parses. The first parse advances the clock past the TTL, so any second
     * cache read would refresh and return the second parse's member class instead.
     */
    @Test
    void memberClassesNeverMixesDescriptionsAndCodesAcrossARefreshBoundary() throws Exception {
        when(parser.parse(anyString()))
                .thenAnswer(invocation -> {
                    clock.advance(Duration.ofSeconds(61));
                    return parseWithMemberClass();
                })
                .thenReturn(new ParsedSharedParams(
                        List.of(MemberClassInfo.builder().code("OTHER").description("Other").build()),
                        List.of(), List.of()));

        SharedParamsCache.MemberClasses memberClasses = cache.memberClasses();

        assertEquals(Set.of(MEMBER_CLASS_CODE), memberClasses.codes());
        assertEquals(MEMBER_CLASS_DESCRIPTION, memberClasses.descriptions().get(MEMBER_CLASS_CODE));
        assertFalse(memberClasses.descriptions().containsKey("OTHER"),
                "descriptions and codes must come from the same snapshot, never from two parses");
    }

    @Test
    void parseFailureYieldsEmptyResultsWithoutThrowingAndIsCachedForTheTtl() throws Exception {
        when(parser.parse(anyString())).thenThrow(new IOException(
                "did not validate against any supported shared-parameters schema version (V2-V5)"));

        assertTrue(cache.securityServers().isEmpty());
        assertTrue(cache.memberClassDescriptions().isEmpty());
        assertTrue(cache.memberClassCodes().isEmpty());
        assertNull(cache.subsystemNames().resolve(MEMBER_CLASS_CODE, "1234", "sub"));

        // still within the TTL window: the failed parse must not be retried on every call
        cache.securityServers();
        verify(parser, times(1)).parse(anyString());
    }

    @Test
    void getCurrentInstanceThrows503WhileFileMissing() {
        assertEquals(503, assertThrows(ResponseStatusException.class, cache::getCurrentInstance)
                .getStatusCode().value());
        verifyNoInteractions(parser);
    }

    @Test
    void getCurrentInstanceCachesValueForeverAfterFirstSuccess(@TempDir Path tmp) throws Exception {
        Path file = tmp.resolve(SHARED_PARAMS_FILE);
        Files.writeString(file, MOCKED_PARSER_CONTENT);
        SharedParamsCache fileBacked = new SharedParamsCache(parser, clock, file.toString());
        when(parser.parseInstanceIdentifier(file.toString())).thenReturn(INSTANCE);

        assertEquals(INSTANCE, fileBacked.getCurrentInstance());

        // Delete the file — a fresh load would now fail. The cached call must succeed.
        Files.delete(file);
        assertEquals(INSTANCE, fileBacked.getCurrentInstance());
        verify(parser, times(1)).parseInstanceIdentifier(file.toString());
    }

    /**
     * Guards the retry-on-failure contract: the 503 raised while shared-params.xml has not been
     * synced yet must NOT be cached — the next call must load and succeed once the file appears.
     * Protects against refactoring getCurrentInstance() into a failure-caching memoizer.
     */
    @Test
    void getCurrentInstanceDoesNotCacheTheMissingFileFailureAndRecoversAfterFileAppears(@TempDir Path tmp) throws Exception {
        Path file = tmp.resolve(SHARED_PARAMS_FILE);
        SharedParamsCache fileBacked = new SharedParamsCache(parser, clock, file.toString());

        assertThrows(ResponseStatusException.class, fileBacked::getCurrentInstance);

        Files.writeString(file, MOCKED_PARSER_CONTENT);
        when(parser.parseInstanceIdentifier(file.toString())).thenReturn(INSTANCE);
        assertEquals(INSTANCE, fileBacked.getCurrentInstance());
    }

    /**
     * Same retry-on-failure contract for the other failure mode: a load that reaches the parser and
     * fails (file present but not yet valid) must not be cached either — the next call retries.
     */
    @Test
    void getCurrentInstanceDoesNotCacheAFailedParseAndRetriesOnTheNextCall(@TempDir Path tmp) throws Exception {
        Path file = tmp.resolve(SHARED_PARAMS_FILE);
        Files.writeString(file, MOCKED_PARSER_CONTENT);
        SharedParamsCache fileBacked = new SharedParamsCache(parser, clock, file.toString());
        when(parser.parseInstanceIdentifier(file.toString()))
                .thenThrow(new IOException("does not validate yet"))
                .thenReturn(INSTANCE);

        assertThrows(IllegalStateException.class, fileBacked::getCurrentInstance);

        assertEquals(INSTANCE, fileBacked.getCurrentInstance());
        verify(parser, times(2)).parseInstanceIdentifier(file.toString());
    }

    @Test
    void getCurrentInstanceWrapsParseFailureAsIllegalState(@TempDir Path tmp) throws Exception {
        Path file = tmp.resolve(SHARED_PARAMS_FILE);
        Files.writeString(file, "not valid shared-params");
        SharedParamsCache fileBacked = new SharedParamsCache(parser, clock, file.toString());
        when(parser.parseInstanceIdentifier(file.toString())).thenThrow(new IOException("does not validate"));

        assertThrows(IllegalStateException.class, fileBacked::getCurrentInstance);
    }

    /**
     * The instance identifier never changes once known, so resolving it must not queue behind the
     * TTL re-parse that nearly every V2 request would otherwise wait for.
     */
    @Test
    void getCurrentInstanceDoesNotBlockWhileARefreshIsInProgress(@TempDir Path tmp) throws Exception {
        Path file = tmp.resolve(SHARED_PARAMS_FILE);
        Files.writeString(file, MOCKED_PARSER_CONTENT);
        SharedParamsCache fileBacked = new SharedParamsCache(parser, clock, file.toString());
        CountDownLatch parseEntered = new CountDownLatch(1);
        CountDownLatch releaseParse = new CountDownLatch(1);
        when(parser.parse(file.toString())).thenAnswer(invocation -> {
            parseEntered.countDown();
            awaitLatch(releaseParse);
            return EMPTY_PARSE;
        });
        when(parser.parseInstanceIdentifier(file.toString())).thenReturn(INSTANCE);
        AtomicReference<String> resolvedInstance = new AtomicReference<>();
        Thread refresher = new Thread(fileBacked::securityServers);
        Thread reader = new Thread(() -> resolvedInstance.set(fileBacked.getCurrentInstance()));

        try {
            refresher.start();
            awaitLatch(parseEntered);
            reader.start();

            Awaitility.await().atMost(Duration.ofSeconds(TIMEOUT_SECONDS))
                    .until(() -> INSTANCE.equals(resolvedInstance.get()));
        } finally {
            releaseParse.countDown();
            refresher.join(JOIN_MILLIS);
            reader.join(JOIN_MILLIS);
        }
    }

    @Test
    void readersAreServedTheExpiredSnapshotWhileAnotherThreadRefreshes() throws Exception {
        CountDownLatch parseEntered = new CountDownLatch(1);
        CountDownLatch releaseParse = new CountDownLatch(1);
        AtomicInteger parses = new AtomicInteger();
        when(parser.parse(anyString())).thenAnswer(invocation -> {
            if (parses.incrementAndGet() == 1) {
                return parseWithMemberClass();
            }
            parseEntered.countDown();
            awaitLatch(releaseParse);
            return EMPTY_PARSE;
        });
        assertTrue(cache.memberClassCodes().contains(MEMBER_CLASS_CODE));
        clock.advance(Duration.ofSeconds(61));
        AtomicReference<Set<String>> readerCodes = new AtomicReference<>();
        Thread refresher = new Thread(cache::memberClassCodes);
        Thread reader = new Thread(() -> readerCodes.set(cache.memberClassCodes()));

        try {
            refresher.start();
            awaitLatch(parseEntered);
            reader.start();

            Awaitility.await().atMost(Duration.ofSeconds(TIMEOUT_SECONDS)).until(() -> readerCodes.get() != null);
            assertTrue(readerCodes.get().contains(MEMBER_CLASS_CODE),
                    "an expired snapshot is served while one thread re-parses, instead of blocking the reader");
        } finally {
            releaseParse.countDown();
            refresher.join(JOIN_MILLIS);
            reader.join(JOIN_MILLIS);
        }
        assertTrue(cache.memberClassCodes().isEmpty(), "the re-parse result replaces the expired snapshot");
        assertEquals(2, parses.get(), "an expired TTL must trigger exactly one re-parse");
    }

    @Test
    void theFirstLoadWaitsInsteadOfServingAnUnparsedSnapshot() throws Exception {
        CountDownLatch parseEntered = new CountDownLatch(1);
        CountDownLatch releaseParse = new CountDownLatch(1);
        when(parser.parse(anyString())).thenAnswer(invocation -> {
            parseEntered.countDown();
            awaitLatch(releaseParse);
            return parseWithMemberClass();
        });
        AtomicReference<Set<String>> readerCodes = new AtomicReference<>();
        Thread loader = new Thread(cache::memberClassCodes);
        Thread reader = new Thread(() -> readerCodes.set(cache.memberClassCodes()));

        loader.start();
        awaitLatch(parseEntered);
        reader.start();
        Awaitility.await().atMost(Duration.ofSeconds(TIMEOUT_SECONDS))
                .until(() -> reader.getState() == Thread.State.WAITING);
        assertNull(readerCodes.get(), "an empty snapshot must not be served before the first parse completes");

        releaseParse.countDown();
        loader.join(JOIN_MILLIS);
        reader.join(JOIN_MILLIS);

        assertTrue(readerCodes.get().contains(MEMBER_CLASS_CODE));
        verify(parser, times(1)).parse(anyString());
    }

    @Test
    void globalConfExpiryReadsExpirationFromMetadataSidecar(@TempDir Path tmp) throws Exception {
        stubEmptyParse();
        SharedParamsCache fileBacked = cacheWithMetadata(tmp, "2024-01-02T00:00:00Z");

        SharedParamsCache.GlobalConfExpiry expiry = fileBacked.globalConfExpiry();

        assertFalse(expiry.expired());
        assertEquals(Instant.parse("2024-01-02T00:00:00Z"), expiry.expiresAt());
    }

    @Test
    void expiredGlobalConfIsFlaggedAndWarnedButDataIsStillServed(@TempDir Path tmp) throws Exception {
        when(parser.parse(anyString())).thenReturn(parseWithMemberClass());
        SharedParamsCache fileBacked = cacheWithMetadata(tmp, "2023-12-31T12:00:00Z");
        ListAppender<ILoggingEvent> appender = attachAppender();
        try {
            SharedParamsCache.GlobalConfExpiry expiry = fileBacked.globalConfExpiry();

            assertTrue(expiry.expired());
            assertEquals(Instant.parse("2023-12-31T12:00:00Z"), expiry.expiresAt());
            assertTrue(fileBacked.memberClassCodes().contains(MEMBER_CLASS_CODE),
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
        stubEmptyParse();
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
        when(parser.parse(anyString())).thenReturn(parseWithMemberClass());
        Path file = tmp.resolve(SHARED_PARAMS_FILE);
        Files.writeString(file, MOCKED_PARSER_CONTENT);
        Files.writeString(tmp.resolve(METADATA_FILE), "not json at all");
        SharedParamsCache fileBacked = new SharedParamsCache(parser, clock, file.toString());

        SharedParamsCache.GlobalConfExpiry expiry = fileBacked.globalConfExpiry();

        assertFalse(expiry.expired());
        assertNull(expiry.expiresAt());
        assertTrue(fileBacked.memberClassCodes().contains(MEMBER_CLASS_CODE));
    }

    @Test
    void confLapsingMidTtlWindowIsReportedExpiredWithoutARefresh(@TempDir Path tmp) throws Exception {
        stubEmptyParse();
        SharedParamsCache fileBacked = cacheWithMetadata(tmp, "2024-01-01T00:00:30Z");

        assertFalse(fileBacked.globalConfExpiry().expired());
        clock.advance(Duration.ofSeconds(40));
        assertTrue(fileBacked.globalConfExpiry().expired(), "expired is evaluated against the clock at call time");
        verify(parser, times(1)).parse(anyString());
    }

    private SharedParamsCache cacheWithMetadata(Path tmp, String expirationDate) throws IOException {
        Path file = tmp.resolve(SHARED_PARAMS_FILE);
        Files.writeString(file, MOCKED_PARSER_CONTENT);
        Files.writeString(tmp.resolve(METADATA_FILE),
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

    private void stubEmptyParse() throws Exception {
        when(parser.parse(anyString())).thenReturn(EMPTY_PARSE);
    }

    private static ParsedSharedParams parseWithMemberClass() {
        return new ParsedSharedParams(
                List.of(MemberClassInfo.builder().code(MEMBER_CLASS_CODE).description(MEMBER_CLASS_DESCRIPTION).build()),
                List.of(), List.of());
    }

    private static void awaitLatch(CountDownLatch latch) throws InterruptedException {
        assertTrue(latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS), "timed out waiting for the other thread");
    }

    private static final class MutableClock extends Clock {

        @SuppressWarnings("PMD.AvoidUsingVolatile")
        private volatile Instant now;

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
