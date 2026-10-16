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
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FullMemberDtoTest {

    @Test
    void testFromComputesCountsFromActiveHelpersAndUsesEntityIsProvider() {
        Member member = buildMember(true);
        Subsystem sub = buildSubsystem(member, "sub1");
        addActiveService(sub, "svcA");
        addActiveService(sub, "svcB");
        member.getSubsystems().add(sub);

        FullMemberDto dto = FullMemberDto.from(member, List.of());

        assertEquals("PUB", dto.getMemberClass());
        assertEquals("14151328", dto.getMemberCode());
        assertTrue(dto.isProvider(), "isProvider must come from the entity column, not from walking children");
        assertEquals(1, dto.getSubsystemCount());
        assertEquals(2, dto.getServiceCount());
    }

    @Test
    void testFromIsProviderFalseWhenColumnFalseDespiteActiveChildren() {
        Member member = buildMember(false);
        Subsystem sub = buildSubsystem(member, "sub1");
        addActiveService(sub, "svcA");
        member.getSubsystems().add(sub);

        FullMemberDto dto = FullMemberDto.from(member, List.of());

        assertFalse(dto.isProvider(), "isProvider is the denormalized column value, not recomputed here");
    }

    @Test
    void testFromCarriesSuppliedSubsystems() {
        Member member = buildMember(false);
        FullSubsystemDto sub = FullSubsystemDto.builder().subsystemCode("sub1").build();

        FullMemberDto dto = FullMemberDto.from(member, List.of(sub));

        assertEquals(1, dto.getSubsystems().size());
        assertEquals("sub1", dto.getSubsystems().get(0).getSubsystemCode());
    }

    private Member buildMember(boolean provider) {
        Member m = new Member();
        ReflectionTestUtils.setField(m, "memberClass", "PUB");
        ReflectionTestUtils.setField(m, "memberCode", "14151328");
        ReflectionTestUtils.setField(m, "name", "Nahka-Albert");
        ReflectionTestUtils.setField(m, "isProvider", provider);
        LocalDateTime now = LocalDateTime.now();
        ReflectionTestUtils.setField(m, "statusInfo", new StatusInfo(now, now, now));
        ReflectionTestUtils.setField(m, "subsystems", new HashSet<Subsystem>());
        return m;
    }

    private Subsystem buildSubsystem(Member parent, String code) {
        Subsystem s = new Subsystem();
        ReflectionTestUtils.setField(s, "member", parent);
        ReflectionTestUtils.setField(s, "subsystemCode", code);
        LocalDateTime now = LocalDateTime.now();
        ReflectionTestUtils.setField(s, "statusInfo", new StatusInfo(now, now, now));
        ReflectionTestUtils.setField(s, "services", new HashSet<Service>());
        return s;
    }

    private void addActiveService(Subsystem subsystem, String serviceCode) {
        Service svc = new Service();
        ReflectionTestUtils.setField(svc, "subsystem", subsystem);
        ReflectionTestUtils.setField(svc, "serviceCode", serviceCode);
        ReflectionTestUtils.setField(svc, "serviceType", "REST");
        LocalDateTime now = LocalDateTime.now();
        ReflectionTestUtils.setField(svc, "statusInfo", new StatusInfo(now, now, now));
        Set<Service> services = subsystem.getServices();
        services.add(svc);
    }
}
