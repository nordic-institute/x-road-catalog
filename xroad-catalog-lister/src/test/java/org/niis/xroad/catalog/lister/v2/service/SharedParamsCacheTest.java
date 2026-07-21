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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.catalog.lister.v2.dto.MemberClassInfo;
import org.niis.xroad.catalog.lister.v2.dto.SecurityServerInfoV2;
import org.niis.xroad.catalog.lister.v2.dto.SubsystemNameInfo;
import org.niis.xroad.catalog.lister.v2.parser.SharedParamsParserV2;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
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
