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
import org.niis.xroad.catalog.persistence.v2.entity.Member;
import org.niis.xroad.catalog.persistence.v2.entity.Service;
import org.niis.xroad.catalog.persistence.v2.entity.StatusInfo;
import org.niis.xroad.catalog.persistence.v2.entity.Subsystem;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FullSubsystemDtoTest {

    @Test
    void testFromUsesActiveServiceHelperAndCarriesSuppliedServices() {
        Subsystem subsystem = buildSubsystem();
        addService(subsystem);
        addService(subsystem);
        ServiceDto service = ServiceDto.builder().serviceCode("svcA").build();

        FullSubsystemDto dto = FullSubsystemDto.from(subsystem, (mc, mcode, sc) -> "Tax Services", List.of(service));

        assertEquals("PUB", dto.getMemberClass());
        assertEquals("14151328", dto.getMemberCode());
        assertEquals("Nahka-Albert", dto.getMemberName());
        assertEquals("TaxServices", dto.getSubsystemCode());
        assertEquals("Tax Services", dto.getSubsystemName());
        assertEquals(2, dto.getServiceCount());
        assertEquals(1, dto.getServices().size());
        assertEquals("svcA", dto.getServices().get(0).getServiceCode());
    }

    private Subsystem buildSubsystem() {
        Member member = new Member();
        ReflectionTestUtils.setField(member, "memberClass", "PUB");
        ReflectionTestUtils.setField(member, "memberCode", "14151328");
        ReflectionTestUtils.setField(member, "name", "Nahka-Albert");

        Subsystem s = new Subsystem();
        ReflectionTestUtils.setField(s, "member", member);
        ReflectionTestUtils.setField(s, "subsystemCode", "TaxServices");
        LocalDateTime now = LocalDateTime.now();
        ReflectionTestUtils.setField(s, "statusInfo", new StatusInfo(now, now, now));
        ReflectionTestUtils.setField(s, "services", new HashSet<Service>());
        return s;
    }

    private void addService(Subsystem subsystem) {
        Service svc = new Service();
        ReflectionTestUtils.setField(svc, "subsystem", subsystem);
        ReflectionTestUtils.setField(svc, "serviceCode", "svc");
        ReflectionTestUtils.setField(svc, "serviceType", "REST");
        LocalDateTime now = LocalDateTime.now();
        ReflectionTestUtils.setField(svc, "statusInfo", new StatusInfo(now, now, now));
        subsystem.getServices().add(svc);
    }
}
