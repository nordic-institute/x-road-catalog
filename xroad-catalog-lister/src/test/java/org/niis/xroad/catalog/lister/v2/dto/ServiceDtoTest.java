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
import org.niis.xroad.catalog.persistence.entity.StatusInfo;
import org.niis.xroad.catalog.persistence.repository.projection.ServiceVersionRow;
import org.niis.xroad.catalog.persistence.v2entity.ServiceV2;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServiceDtoTest {

    @Test
    void testFromSortsVersionsWithNullLast() {
        LocalDateTime now = LocalDateTime.now();
        ServiceVersionRow v1 = row("mixedSvc", "v1", "SOAP", now);
        ServiceVersionRow vNull = row("mixedSvc", null, "REST", now);
        List<ServiceVersionRow> rows = new ArrayList<>(List.of(vNull, v1));

        ServiceDto dto = ServiceDto.from(rows);

        assertEquals("mixedSvc", dto.getServiceCode());
        assertEquals(2, dto.getVersionCount());
        assertEquals(Arrays.asList("v1", null),
                dto.getVersions().stream().map(v -> v.getServiceVersion()).toList());
    }

    @Test
    void testFromKeepsDistinctServiceTypesInVersionOrderWithoutCollapsing() {
        LocalDateTime now = LocalDateTime.now();
        ServiceVersionRow v1 = row("mixedSvc", "v1", "REST", now);
        ServiceVersionRow v2 = row("mixedSvc", "v2", "SOAP", now);
        List<ServiceVersionRow> rows = new ArrayList<>(List.of(v1, v2));

        ServiceDto dto = ServiceDto.from(rows);

        assertEquals(List.of("REST", "SOAP"), dto.getServiceTypes(),
                "a bare service code can legitimately be multi-typed; must not collapse to a scalar");
    }

    @Test
    void testFromThrowsForEmptyList() {
        assertThrows(IllegalArgumentException.class, () -> ServiceDto.from(List.of()));
    }

    @Test
    void testFromPopulatesContextFromFirstSortedRow() {
        LocalDateTime now = LocalDateTime.now();
        ServiceVersionRow v1 = row("svc", "v1", "REST", now);
        List<ServiceVersionRow> rows = new ArrayList<>(List.of(v1));

        ServiceDto dto = ServiceDto.from(rows);

        assertEquals("PUB", dto.getMemberClass());
        assertEquals("14151328", dto.getMemberCode());
        assertEquals("Nahka-Albert", dto.getMemberName());
        assertEquals("sub1", dto.getSubsystemCode());
    }

    @Test
    void testFromEntitiesSortsAndAggregatesVersions() {
        ServiceV2 v1 = service("mixedSvc", "v1", "SOAP");
        ServiceV2 vNull = service("mixedSvc", null, "REST");

        ServiceDto dto = ServiceDto.fromEntities("PUB", "14151328", "Nahka-Albert", "sub1",
                List.of(vNull, v1));

        assertEquals("mixedSvc", dto.getServiceCode());
        assertEquals(2, dto.getVersionCount());
        assertTrue(dto.getServiceTypes().containsAll(List.of("SOAP", "REST")));
        assertEquals(Arrays.asList("v1", null),
                dto.getVersions().stream().map(v -> v.getServiceVersion()).toList());
    }

    @Test
    void testFromEntitiesThrowsForEmptyCollection() {
        assertThrows(IllegalArgumentException.class,
                () -> ServiceDto.fromEntities("PUB", "14151328", "Nahka-Albert", "sub1", List.of()));
    }

    private ServiceVersionRow row(String serviceCode, String version, String serviceType, LocalDateTime now) {
        return new FakeServiceVersionRow("PUB", "14151328", "Nahka-Albert", "sub1", 1L,
                serviceCode, version, serviceType, now, now, now, null);
    }

    private ServiceV2 service(String code, String version, String serviceType) {
        ServiceV2 s = new ServiceV2();
        ReflectionTestUtils.setField(s, "serviceCode", code);
        ReflectionTestUtils.setField(s, "serviceVersion", version);
        ReflectionTestUtils.setField(s, "serviceType", serviceType);
        LocalDateTime now = LocalDateTime.now();
        ReflectionTestUtils.setField(s, "statusInfo", new StatusInfo(now, now, now, null));
        return s;
    }

    @SuppressWarnings("PMD.DataClass")
    private record FakeServiceVersionRow(String memberClass, String memberCode, String memberName,
                                  String subsystemCode, long subsystemId, String serviceCode, String serviceVersion,
                                  String serviceType, LocalDateTime created, LocalDateTime changed,
                                  LocalDateTime fetched, LocalDateTime removed) implements ServiceVersionRow {

        @Override
        public String getMemberClass() {
            return memberClass;
        }

        @Override
        public String getMemberCode() {
            return memberCode;
        }

        @Override
        public String getMemberName() {
            return memberName;
        }

        @Override
        public String getSubsystemCode() {
            return subsystemCode;
        }

        @Override
        public long getSubsystemId() {
            return subsystemId;
        }

        @Override
        public String getServiceCode() {
            return serviceCode;
        }

        @Override
        public String getServiceVersion() {
            return serviceVersion;
        }

        @Override
        public String getServiceType() {
            return serviceType;
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

        @Override
        public LocalDateTime getRemoved() {
            return removed;
        }
    }
}
