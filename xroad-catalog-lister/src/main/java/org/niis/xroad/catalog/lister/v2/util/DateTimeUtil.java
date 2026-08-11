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

import org.niis.xroad.catalog.lister.v2.exception.BadRequestException;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;

public final class DateTimeUtil {

    private DateTimeUtil() {
    }

    /**
     * Parses an ISO-8601 calendar date ({@code yyyy-MM-dd}). Sub-day precision and timezone
     * offsets are rejected — V2 standardizes on day-resolution range queries.
     *
     * @throws BadRequestException if {@code value} is null/blank or not {@code yyyy-MM-dd}
     */
    public static LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) {
            throw new BadRequestException("Date value must not be null or blank");
        }
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException ex) {
            throw new BadRequestException(
                    "Unable to parse date value '" + value + "'. Expected format: yyyy-MM-dd",
                    ex);
        }
    }

    public static void validateDateRange(LocalDateTime since, LocalDateTime until, long maxDays) {
        if (since.isAfter(until)) {
            throw new BadRequestException("'since' must not be after 'until'");
        }
        long daysBetween = ChronoUnit.DAYS.between(since, until);
        if (daysBetween > maxDays) {
            throw new BadRequestException(
                    "Date range must not exceed " + maxDays + " days (was " + daysBetween + " days)");
        }
    }

    /**
     * Like {@link #parseDate(String)}, but a null/blank {@code value} yields {@code defaultIfMissing};
     * malformed non-blank values still raise {@link BadRequestException}.
     */
    public static LocalDate parseDateOrDefault(String value, LocalDate defaultIfMissing) {
        if (value == null || value.isBlank()) {
            return defaultIfMissing;
        }
        return parseDate(value);
    }

    /**
     * Today in the {@link Clock}'s zone — server-local in production, matching the local-wall-clock
     * convention stored timestamps use; the clock is injected so tests can pin the value.
     */
    public static LocalDate today(Clock clock) {
        return LocalDate.now(clock);
    }
}
