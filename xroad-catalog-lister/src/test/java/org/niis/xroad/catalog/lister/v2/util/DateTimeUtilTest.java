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
package org.niis.xroad.catalog.lister.v2.util;

import org.junit.jupiter.api.Test;
import org.niis.xroad.catalog.lister.v2.exception.BadRequestException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DateTimeUtilTest {

    @Test
    void parseDateAcceptsDateOnly() {
        LocalDate result = DateTimeUtil.parseDate("2026-04-10");
        assertThat(result).isEqualTo(LocalDate.of(2026, 4, 10));
    }

    @Test
    void parseDateRejectsOffsetForm() {
        assertThatThrownBy(() -> DateTimeUtil.parseDate("2026-04-10T00:00:00+03:00"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("yyyy-MM-dd");
    }

    @Test
    void parseDateRejectsZuluForm() {
        assertThatThrownBy(() -> DateTimeUtil.parseDate("2026-04-10T00:00:00Z"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("yyyy-MM-dd");
    }

    @Test
    void parseDateRejectsLocalDateTimeForm() {
        assertThatThrownBy(() -> DateTimeUtil.parseDate("2026-04-10T14:30:00"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("yyyy-MM-dd");
    }

    @Test
    void parseDateNullThrows() {
        assertThatThrownBy(() -> DateTimeUtil.parseDate(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("null or blank");
    }

    @Test
    void parseDateBlankThrows() {
        assertThatThrownBy(() -> DateTimeUtil.parseDate("   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("null or blank");
    }

    @Test
    void parseDateGarbageThrows() {
        assertThatThrownBy(() -> DateTimeUtil.parseDate("not-a-date"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("yyyy-MM-dd");
    }

    @Test
    void validateDateRangeValid() {
        LocalDateTime since = LocalDateTime.of(2026, 4, 1, 0, 0);
        LocalDateTime until = LocalDateTime.of(2026, 4, 10, 0, 0);
        DateTimeUtil.validateDateRange(since, until, 30);
    }

    @Test
    void validateDateRangeSinceAfterUntilThrows() {
        LocalDateTime since = LocalDateTime.of(2026, 4, 10, 0, 0);
        LocalDateTime until = LocalDateTime.of(2026, 4, 1, 0, 0);
        assertThatThrownBy(() -> DateTimeUtil.validateDateRange(since, until, 30))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not be after");
    }

    @Test
    void validateDateRangeExceedsMaxDaysThrows() {
        LocalDateTime since = LocalDateTime.of(2026, 1, 1, 0, 0);
        LocalDateTime until = LocalDateTime.of(2026, 4, 10, 0, 0);
        assertThatThrownBy(() -> DateTimeUtil.validateDateRange(since, until, 30))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not exceed 30 days");
    }

    @Test
    void invalidDateInputIsReportedAsClientError() {
        // Client-correctable input must stay a 400; only BadRequestException maps there.
        LocalDateTime since = LocalDateTime.of(2026, 4, 10, 0, 0);
        LocalDateTime until = LocalDateTime.of(2026, 4, 1, 0, 0);
        assertThatThrownBy(() -> DateTimeUtil.validateDateRange(since, until, 30))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> DateTimeUtil.validateDateRange(until, since, 3))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> DateTimeUtil.parseDate("not-a-date"))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> DateTimeUtil.parseDate(null))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void validateDateRangeExactlyMaxDays() {
        LocalDateTime since = LocalDateTime.of(2026, 4, 1, 0, 0);
        LocalDateTime until = LocalDateTime.of(2026, 5, 1, 0, 0);
        DateTimeUtil.validateDateRange(since, until, 30);
    }

    @Test
    void parseDateOrDefaultReturnsParsedWhenPresent() {
        LocalDate fallback = LocalDate.of(2026, 1, 1);
        assertThat(DateTimeUtil.parseDateOrDefault("2026-04-30", fallback))
                .isEqualTo(LocalDate.of(2026, 4, 30));
    }

    @Test
    void parseDateOrDefaultReturnsFallbackWhenNull() {
        LocalDate fallback = LocalDate.of(2026, 1, 1);
        assertThat(DateTimeUtil.parseDateOrDefault(null, fallback)).isEqualTo(fallback);
    }

    @Test
    void parseDateOrDefaultReturnsFallbackWhenBlank() {
        LocalDate fallback = LocalDate.of(2026, 1, 1);
        assertThat(DateTimeUtil.parseDateOrDefault("   ", fallback)).isEqualTo(fallback);
    }

    @Test
    void parseDateOrDefaultStillRejectsMalformed() {
        assertThatThrownBy(() ->
                DateTimeUtil.parseDateOrDefault("2026/04/30", LocalDate.of(2026, 1, 1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("yyyy-MM-dd");
    }

    @Test
    void todayUsesProvidedClockUtc() {
        Clock fixed = Clock.fixed(Instant.parse("2026-05-07T08:30:00Z"), ZoneOffset.UTC);
        assertThat(DateTimeUtil.today(fixed)).isEqualTo(LocalDate.of(2026, 5, 7));
    }
}
