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
package org.niis.xroad.catalog.collector.util;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the boundary semantics of the hour windows: open at both ends, so {@code after:00:00} and
 * {@code before:00:00} themselves are outside, and an hour outside 0-23 is rejected by {@code LocalDate#atTime}.
 */
class CollectorUtilsTest {

    private static final int AFTER_HOUR = 3;
    private static final int BEFORE_HOUR = 4;

    @Test
    void oneSecondBeforeTheWindowStartsIsOutside() {
        assertFalse(CollectorUtils.isTimeBetweenHours(fixedClockAt(2, 59, 59), AFTER_HOUR, BEFORE_HOUR));
    }

    @Test
    void exactlyAtTheWindowStartIsOutside() {
        assertFalse(CollectorUtils.isTimeBetweenHours(fixedClockAt(3, 0, 0), AFTER_HOUR, BEFORE_HOUR));
    }

    @Test
    void insideTheWindowIsInside() {
        assertTrue(CollectorUtils.isTimeBetweenHours(fixedClockAt(3, 30, 0), AFTER_HOUR, BEFORE_HOUR));
    }

    @Test
    void exactlyAtTheWindowEndIsOutside() {
        assertFalse(CollectorUtils.isTimeBetweenHours(fixedClockAt(4, 0, 0), AFTER_HOUR, BEFORE_HOUR));
    }

    @Test
    void equalStartAndEndHourIsAlwaysOutside() {
        assertFalse(CollectorUtils.isTimeBetweenHours(fixedClockAt(3, 30, 0), AFTER_HOUR, AFTER_HOUR));
    }

    @Test
    void hourOutsideTheDayThrows() {
        assertThrows(DateTimeException.class, () -> CollectorUtils.isTimeBetweenHours(fixedClockAt(3, 30, 0), AFTER_HOUR, 24));
    }

    private static Clock fixedClockAt(int hour, int minute, int second) {
        return Clock.fixed(LocalDate.of(2025, 6, 1).atTime(hour, minute, second).toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
    }
}
