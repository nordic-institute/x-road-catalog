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
import org.niis.xroad.catalog.persistence.v2.repository.projection.ServiceVersionRow;
import org.niis.xroad.catalog.persistence.v2.entity.Endpoint;
import org.niis.xroad.catalog.persistence.v2.entity.Service;
import org.niis.xroad.catalog.persistence.v2.entity.StatusInfo;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ServiceVersionSummaryDtoTest {

    private static final String SERVICE_CODE = "getTaxInfo";

    @Test
    void testFromEntityReadsServiceTypeColumn() {
        Service svc = buildService(SERVICE_CODE, "v1", "OPENAPI");
        ServiceVersionSummaryDto summary = ServiceVersionSummaryDto.from(svc);
        assertEquals("v1", summary.getServiceVersion());
        assertEquals("OPENAPI", summary.getServiceType());
    }

    @Test
    void testFromRowMapsAllFields() {
        LocalDateTime now = LocalDateTime.now();
        ServiceVersionRow row = new FakeServiceVersionRow("PUB", "14151328", "Nahka-Albert", "sub1", 1L,
                SERVICE_CODE, "v2", "SOAP", now, now, now);
        ServiceVersionSummaryDto summary = ServiceVersionSummaryDto.from(row);
        assertEquals("v2", summary.getServiceVersion());
        assertEquals("SOAP", summary.getServiceType());
        assertEquals(now, summary.getCreated());
    }

    private Service buildService(String code, String version, String serviceType) {
        Service s = new Service();
        ReflectionTestUtils.setField(s, "serviceCode", code);
        ReflectionTestUtils.setField(s, "serviceVersion", version);
        ReflectionTestUtils.setField(s, "serviceType", serviceType);
        LocalDateTime now = LocalDateTime.now();
        ReflectionTestUtils.setField(s, "statusInfo", new StatusInfo(now, now, now));
        ReflectionTestUtils.setField(s, "endpoints", new HashSet<Endpoint>());
        return s;
    }

    @SuppressWarnings("PMD.DataClass")
    private record FakeServiceVersionRow(String memberClass, String memberCode, String memberName,
                                  String subsystemCode, long subsystemId, String serviceCode, String serviceVersion,
                                  String serviceType, LocalDateTime created, LocalDateTime changed,
                                  LocalDateTime fetched) implements ServiceVersionRow {

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
    }
}
