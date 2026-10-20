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
package org.niis.xroad.catalog.lister.v2.dto;

import org.junit.jupiter.api.Test;
import org.niis.xroad.catalog.persistence.v2.repository.projection.MemberListRow;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MemberDtoTest {

    @Test
    void testFromMapsRowFieldsDirectly() {
        LocalDateTime now = LocalDateTime.now();
        MemberListRow row = new FakeMemberListRow("PUB", "14151328", "Nahka-Albert", true, 2, 5,
                now, now, now);

        MemberDto dto = MemberDto.from(row);

        assertEquals("PUB", dto.getMemberClass());
        assertEquals("14151328", dto.getMemberCode());
        assertEquals("Nahka-Albert", dto.getName());
        assertTrue(dto.isProvider());
        assertEquals(2, dto.getSubsystemCount());
        assertEquals(5, dto.getServiceCount());
        assertEquals(now, dto.getCreated());
        assertEquals(now, dto.getChanged());
        assertEquals(now, dto.getFetched());
    }

    @Test
    void testFromNonProviderRow() {
        LocalDateTime now = LocalDateTime.now();
        MemberListRow row = new FakeMemberListRow("PUB", "14151329", "Plain member", false, 0, 0,
                now, now, now);

        MemberDto dto = MemberDto.from(row);

        assertFalse(dto.isProvider());
    }

    @SuppressWarnings("PMD.DataClass")
    private record FakeMemberListRow(String memberClass, String memberCode, String name, boolean provider,
                                      long subsystemCount, long serviceCount, LocalDateTime created,
                                      LocalDateTime changed, LocalDateTime fetched) implements MemberListRow {

        @Override
        public String getMemberClass() {
            return memberClass;
        }

        @Override
        public String getMemberCode() {
            return memberCode;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public boolean isProvider() {
            return provider;
        }

        @Override
        public long getSubsystemCount() {
            return subsystemCount;
        }

        @Override
        public long getServiceCount() {
            return serviceCount;
        }

        @Override
        public LocalDateTime getCreated() {
            return created;
        }

        @Override
        public LocalDateTime getChanged() {
            return changed;
        }

        @Override
        public LocalDateTime getFetched() {
            return fetched;
        }
    }
}
