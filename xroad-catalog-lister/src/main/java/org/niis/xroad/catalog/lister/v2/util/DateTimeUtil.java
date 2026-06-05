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

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;

public final class DateTimeUtil {

    private static final String VERSION_SENTINEL = "null";

    private DateTimeUtil() {
    }

    /**
     * Parses {@code value} as an ISO-8601 calendar date ({@code yyyy-MM-dd}). V2 endpoints accept
     * date-only inputs; sub-day precision and timezone offsets are rejected — V2 standardizes on
     * day-resolution range queries (spec §8) regardless of the underlying collector cadence.
     *
     * @param value ISO-8601 calendar date
     * @return parsed date
     * @throws IllegalArgumentException if {@code value} is null/blank or doesn't match {@code yyyy-MM-dd}
     */
    public static LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Date value must not be null or blank");
        }
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException(
                    "Unable to parse date value '" + value + "'. Expected format: yyyy-MM-dd",
                    ex);
        }
    }

    public static void validateDateRange(LocalDateTime since, LocalDateTime until, long maxDays) {
        if (since.isAfter(until)) {
            throw new IllegalArgumentException("'since' must not be after 'until'");
        }
        long daysBetween = ChronoUnit.DAYS.between(since, until);
        if (daysBetween > maxDays) {
            throw new IllegalArgumentException(
                    "Date range must not exceed " + maxDays + " days (was " + daysBetween + " days)");
        }
    }

    /**
     * Parse {@code value} as a {@code yyyy-MM-dd} date, or return {@code defaultIfMissing} when
     * {@code value} is null or blank. Malformed non-blank values still raise {@link IllegalArgumentException}
     * via {@link #parseDate(String)}.
     */
    public static LocalDate parseDateOrDefault(String value, LocalDate defaultIfMissing) {
        if (value == null || value.isBlank()) {
            return defaultIfMissing;
        }
        return parseDate(value);
    }

    /**
     * Today's date in UTC. The {@link Clock} is injected so tests can pin the value.
     */
    public static LocalDate today(Clock clock) {
        return LocalDate.now(clock);
    }

    /**
     * Resolves the URL-segment version sentinel. The literal {@code "null"} (case-sensitive) in a
     * service-version path segment maps to a Java {@code null}, which the persistence layer treats
     * as "service has no version label". Any other string is passed through unchanged. A real
     * version literally named {@code "null"} is therefore unaddressable; this is documented in the
     * V2 OpenAPI spec.
     */
    public static String resolveVersionSentinel(String serviceVersion) {
        if (VERSION_SENTINEL.equals(serviceVersion)) {
            return null;
        }
        return serviceVersion;
    }
}
