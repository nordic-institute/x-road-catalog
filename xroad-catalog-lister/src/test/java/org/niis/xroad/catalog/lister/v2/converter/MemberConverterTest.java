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
package org.niis.xroad.catalog.lister.v2.converter;

import org.junit.jupiter.api.Test;
import org.niis.xroad.catalog.lister.v2.dto.FullMemberDto;
import org.niis.xroad.catalog.lister.v2.dto.FullSubsystemDto;
import org.niis.xroad.catalog.lister.v2.dto.MemberDto;
import org.niis.xroad.catalog.persistence.entity.StatusInfo;
import org.niis.xroad.catalog.persistence.repository.projection.MemberListRow;
import org.niis.xroad.catalog.persistence.v2entity.MemberV2;
import org.niis.xroad.catalog.persistence.v2entity.ServiceV2;
import org.niis.xroad.catalog.persistence.v2entity.SubsystemV2;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MemberConverterTest {

    private final MemberConverter converter = new MemberConverter();

    @Test
    void testToDtoMapsRowFieldsDirectly() {
        LocalDateTime now = LocalDateTime.now();
        MemberListRow row = new FakeMemberListRow("PUB", "14151328", "Nahka-Albert", true, 2, 5,
                now, now, now, null);

        MemberDto dto = converter.toDto(row);

        assertEquals("PUB", dto.getMemberClass());
        assertEquals("14151328", dto.getMemberCode());
        assertEquals("Nahka-Albert", dto.getName());
        assertTrue(dto.isProvider());
        assertEquals(2, dto.getSubsystemCount());
        assertEquals(5, dto.getServiceCount());
        assertEquals(now, dto.getCreated());
        assertEquals(now, dto.getChanged());
        assertEquals(now, dto.getFetched());
        assertNull(dto.getRemoved());
    }

    @Test
    void testToDtoNonProviderRemovedRow() {
        LocalDateTime now = LocalDateTime.now();
        MemberListRow row = new FakeMemberListRow("PUB", "14151329", "Plain member", false, 0, 0,
                now, now, now, now);

        MemberDto dto = converter.toDto(row);

        assertFalse(dto.isProvider());
        assertEquals(now, dto.getRemoved());
    }

    @Test
    void testToFullDtoComputesCountsFromActiveHelpersAndUsesEntityIsProvider() {
        MemberV2 member = buildMember(true);
        SubsystemV2 activeSub = buildSubsystem(member, "sub1", false);
        addActiveService(activeSub, "svcA");
        addActiveService(activeSub, "svcB");
        SubsystemV2 removedSub = buildSubsystem(member, "sub2", true);
        addActiveService(removedSub, "svcC");
        member.getSubsystems().add(activeSub);
        member.getSubsystems().add(removedSub);

        FullMemberDto dto = converter.toFullDto(member, List.of());

        assertEquals("PUB", dto.getMemberClass());
        assertEquals("14151328", dto.getMemberCode());
        assertTrue(dto.isProvider(), "isProvider must come from the entity column, not from walking children");
        assertEquals(1, dto.getSubsystemCount(), "removed subsystem must not be counted");
        assertEquals(2, dto.getServiceCount(), "services under the removed subsystem must not be counted");
    }

    @Test
    void testToFullDtoIsProviderFalseWhenColumnFalseDespiteActiveChildren() {
        MemberV2 member = buildMember(false);
        SubsystemV2 sub = buildSubsystem(member, "sub1", false);
        addActiveService(sub, "svcA");
        member.getSubsystems().add(sub);

        FullMemberDto dto = converter.toFullDto(member, List.of());

        assertFalse(dto.isProvider(), "isProvider is the denormalized column value, not recomputed here");
    }

    @Test
    void testToFullDtoCarriesSuppliedSubsystems() {
        MemberV2 member = buildMember(false);
        FullSubsystemDto sub = FullSubsystemDto.builder().subsystemCode("sub1").build();

        FullMemberDto dto = converter.toFullDto(member, List.of(sub));

        assertEquals(1, dto.getSubsystems().size());
        assertEquals("sub1", dto.getSubsystems().get(0).getSubsystemCode());
    }

    private MemberV2 buildMember(boolean provider) {
        MemberV2 m = new MemberV2();
        ReflectionTestUtils.setField(m, "memberClass", "PUB");
        ReflectionTestUtils.setField(m, "memberCode", "14151328");
        ReflectionTestUtils.setField(m, "name", "Nahka-Albert");
        ReflectionTestUtils.setField(m, "isProvider", provider);
        LocalDateTime now = LocalDateTime.now();
        ReflectionTestUtils.setField(m, "statusInfo", new StatusInfo(now, now, now, null));
        ReflectionTestUtils.setField(m, "subsystems", new HashSet<SubsystemV2>());
        return m;
    }

    private SubsystemV2 buildSubsystem(MemberV2 parent, String code, boolean removed) {
        SubsystemV2 s = new SubsystemV2();
        ReflectionTestUtils.setField(s, "member", parent);
        ReflectionTestUtils.setField(s, "subsystemCode", code);
        LocalDateTime now = LocalDateTime.now();
        ReflectionTestUtils.setField(s, "statusInfo", new StatusInfo(now, now, now, removed ? now : null));
        ReflectionTestUtils.setField(s, "services", new HashSet<ServiceV2>());
        return s;
    }

    private void addActiveService(SubsystemV2 subsystem, String serviceCode) {
        ServiceV2 svc = new ServiceV2();
        ReflectionTestUtils.setField(svc, "subsystem", subsystem);
        ReflectionTestUtils.setField(svc, "serviceCode", serviceCode);
        ReflectionTestUtils.setField(svc, "serviceType", "REST");
        LocalDateTime now = LocalDateTime.now();
        ReflectionTestUtils.setField(svc, "statusInfo", new StatusInfo(now, now, now, null));
        Set<ServiceV2> services = subsystem.getServices();
        services.add(svc);
    }

    @SuppressWarnings("PMD.DataClass")
    private record FakeMemberListRow(String memberClass, String memberCode, String name, boolean provider,
                                      long subsystemCount, long serviceCount, LocalDateTime created,
                                      LocalDateTime changed, LocalDateTime fetched,
                                      LocalDateTime removed) implements MemberListRow {

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

        @Override
        public LocalDateTime getRemoved() {
            return removed;
        }
    }
}
