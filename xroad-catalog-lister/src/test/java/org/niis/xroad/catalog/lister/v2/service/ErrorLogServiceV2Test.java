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

import org.junit.jupiter.api.Test;
import org.niis.xroad.catalog.lister.v2.dto.ErrorLogDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@TestPropertySource(properties = {"xroad-catalog.shared-params-file=src/test/resources/shared-params-dev-cs.xml"})
@ActiveProfiles({"test", "general-testdata"})
public class ErrorLogServiceV2Test {

    @Autowired
    private ErrorLogServiceV2 errorLogService;

    // Row 7 (2022-01-01) is outside this window to prove the query is bounded;
    // the window stays under the 90-day cap.
    private final LocalDateTime start = LocalDateTime.parse("2020-04-01T00:00:00");
    private final LocalDateTime end = LocalDateTime.parse("2020-06-01T00:00:00");

    @Test
    public void testGetByServiceMatchesVersionRows() {
        Page<ErrorLogDto> page = errorLogService.get(
                "GOV", "1234", "TestSubsystem", "testService", null,
                start, end, PageRequest.of(0, 100));
        // Fixture rows 8 (v1) and 9 (v2)
        assertEquals(2, page.getTotalElements());
    }

    @Test
    public void testGetByVersion() {
        Page<ErrorLogDto> page = errorLogService.get(
                "GOV", "1234", "TestSubsystem", "testService", "v1",
                start, end, PageRequest.of(0, 100));
        assertEquals(1, page.getTotalElements());
        assertEquals("v1", page.getContent().get(0).getServiceVersion());
    }

    @Test
    public void testGetByNullVersion() {
        // Fixture row 10: restService with serviceVersion=null
        Page<ErrorLogDto> page = errorLogService.get(
                "GOV", "1234", "TestSubsystem", "restService", "null",
                start, end, PageRequest.of(0, 100));
        assertEquals(1, page.getTotalElements());
        assertNull(page.getContent().get(0).getServiceVersion());
    }

    @Test
    public void testGetAtServiceLevelReturnsAllVersionsWhenVersionParamIsJavaNull() {
        // serviceVersion = Java null (no version-level filter) => return all errors for the service
        // Fixture rows 8 (v1), 9 (v2) — both testService errors regardless of version
        Page<ErrorLogDto> page = errorLogService.get(
                "GOV", "1234", "TestSubsystem", "testService", null,
                start, end, PageRequest.of(0, 100));
        assertEquals(2, page.getTotalElements(),
                "Java null serviceVersion must dispatch to service-level query, not null-version");
    }

    @Test
    public void testGetAtSubsystemLevel() {
        // Fixture rows 2, 8, 9, 10 match DEV/GOV/1234/TestSubsystem
        Page<ErrorLogDto> page = errorLogService.get(
                "GOV", "1234", "TestSubsystem", null, null,
                start, end, PageRequest.of(0, 100));
        assertEquals(4, page.getTotalElements());
    }

    @Test
    public void testGetAtMemberLevel() {
        // Fixture rows 2, 3, 8, 9, 10 match GOV/1234
        Page<ErrorLogDto> page = errorLogService.get(
                "GOV", "1234", null, null, null,
                start, end, PageRequest.of(0, 100));
        assertEquals(5, page.getTotalElements());
    }

    @Test
    public void testGetAtMemberClassLevel() {
        // Fixture rows 2, 3, 4, 8, 9, 10 match GOV
        Page<ErrorLogDto> page = errorLogService.get(
                "GOV", null, null, null, null,
                start, end, PageRequest.of(0, 100));
        assertEquals(6, page.getTotalElements());
    }

    @Test
    public void testGetAtInstanceLevel() {
        // All rows whose created falls inside [start, end) (rows 1-6, 8-10); row 7 is 2022 and excluded
        Page<ErrorLogDto> page = errorLogService.get(
                null, null, null, null, null,
                start, end, PageRequest.of(0, 100));
        assertEquals(9, page.getTotalElements());
    }

    @Test
    public void testGetAcceptsSinceEqualUntil() {
        // Canonical DateTimeUtil semantics: since == until is an empty window, not an error.
        Page<ErrorLogDto> page = errorLogService.get(
                null, null, null, null, null,
                start, start, PageRequest.of(0, 100));
        assertTrue(page.isEmpty());
    }

    @Test
    public void testGetRejectsSinceAfterUntil() {
        assertThrows(IllegalArgumentException.class, () -> errorLogService.get(
                null, null, null, null, null,
                end, start, PageRequest.of(0, 100)));
    }

    @Test
    public void testGetRejectsRangeOver90Days() {
        LocalDateTime tooFar = start.plusDays(91);
        assertThrows(IllegalArgumentException.class, () -> errorLogService.get(
                null, null, null, null, null,
                start, tooFar, PageRequest.of(0, 100)));
    }

    @Test
    public void testGetAcceptsExactly90DaysRange() {
        // Boundary: 90 days is allowed; only > 90 is rejected (DateTimeUtil.validateDateRange).
        LocalDateTime exactlyMax = start.plusDays(90);
        Page<ErrorLogDto> page = errorLogService.get(
                null, null, null, null, null,
                start, exactlyMax, PageRequest.of(0, 100));
        assertEquals(9, page.getTotalElements());
    }
}
