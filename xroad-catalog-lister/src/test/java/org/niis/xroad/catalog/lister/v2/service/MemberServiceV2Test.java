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
package org.niis.xroad.catalog.lister.v2.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.catalog.lister.v2.dto.FullMemberDto;
import org.niis.xroad.catalog.lister.v2.dto.FullSubsystemDto;
import org.niis.xroad.catalog.lister.v2.dto.MemberDto;
import org.niis.xroad.catalog.lister.v2.dto.ServiceDto;
import org.niis.xroad.catalog.persistence.entity.StatusInfo;
import org.niis.xroad.catalog.persistence.repository.MemberRepositoryV2;
import org.niis.xroad.catalog.persistence.repository.projection.MemberListRow;
import org.niis.xroad.catalog.persistence.v2entity.MemberV2;
import org.niis.xroad.catalog.persistence.v2entity.ServiceV2;
import org.niis.xroad.catalog.persistence.v2entity.SubsystemV2;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MemberServiceV2Test {

    private static final String INSTANCE = "TEST-INSTANCE";
    private static final String PUB = "PUB";
    private static final String MEMBER_CODE = "14151328";

    @Mock
    private MemberRepositoryV2 memberRepository;

    @Mock
    private SharedParamsCache sharedParamsCache;

    @Mock
    private InstanceContext instanceContext;

    private MemberServiceV2 service;

    @BeforeEach
    void setUp() {
        service = new MemberServiceV2(memberRepository, sharedParamsCache, instanceContext);
    }

    @Test
    void testGetByNaturalKeyReturnsDtoWhenPresent() {
        when(instanceContext.getCurrentInstance()).thenReturn(INSTANCE);
        when(memberRepository.findActiveSummaryByNaturalKey(INSTANCE, PUB, MEMBER_CODE))
                .thenReturn(Optional.of(memberListRow(PUB, MEMBER_CODE, "Nahka-Albert", true, 2, 3)));

        Optional<MemberDto> result = service.getByNaturalKey(PUB, MEMBER_CODE);

        assertTrue(result.isPresent());
        MemberDto dto = result.get();
        assertEquals("Nahka-Albert", dto.getName());
        assertTrue(dto.isProvider());
        assertEquals(2, dto.getSubsystemCount());
        assertEquals(3, dto.getServiceCount());
    }

    @Test
    void testGetByNaturalKeyReturnsEmptyOptionalWhenAbsent() {
        when(instanceContext.getCurrentInstance()).thenReturn(INSTANCE);
        when(memberRepository.findActiveSummaryByNaturalKey(INSTANCE, PUB, "does-not-exist"))
                .thenReturn(Optional.empty());

        assertTrue(service.getByNaturalKey(PUB, "does-not-exist").isEmpty());
    }

    @Test
    void testExistsActiveDelegatesToRepository() {
        when(instanceContext.getCurrentInstance()).thenReturn(INSTANCE);
        when(memberRepository.existsActiveByNaturalKey(INSTANCE, PUB, MEMBER_CODE)).thenReturn(true);

        assertTrue(service.existsActive(PUB, MEMBER_CODE));
    }

    @Test
    void testGetForListMapsPageOfRows() {
        when(instanceContext.getCurrentInstance()).thenReturn(INSTANCE);
        Page<MemberListRow> page = new PageImpl<>(
                List.of(memberListRow(PUB, MEMBER_CODE, "Nahka-Albert", false, 0, 0)),
                PageRequest.of(0, 20), 1);
        when(memberRepository.findActiveForList(INSTANCE, PUB, null, PageRequest.of(0, 20))).thenReturn(page);

        Page<MemberDto> result = service.getForList(PUB, null, PageRequest.of(0, 20));

        assertEquals(1, result.getTotalElements());
        assertEquals(PUB, result.getContent().get(0).getMemberClass());
    }

    @Test
    void testGetFullTreeReturnsEmptyOptionalForMissing() {
        when(instanceContext.getCurrentInstance()).thenReturn(INSTANCE);
        when(memberRepository.findActiveWithTreeByNaturalKey(INSTANCE, PUB, "does-not-exist"))
                .thenReturn(Optional.empty());

        assertTrue(service.getFullTree(PUB, "does-not-exist").isEmpty());
    }

    @Test
    void testGetFullTreeSortsSubsystemsAndServicesAndAggregates() {
        when(instanceContext.getCurrentInstance()).thenReturn(INSTANCE);
        when(sharedParamsCache.subsystemNames()).thenReturn((memberClass, memberCode, subsystemCode) ->
                PUB.equals(memberClass) && MEMBER_CODE.equals(memberCode) && "subsystem_a1".equals(subsystemCode)
                        ? "Subsystem A1" : null);

        MemberV2 member = member(PUB, MEMBER_CODE, "Nahka-Albert", true);
        SubsystemV2 subB = subsystem(member, "subsystem_b1");
        SubsystemV2 subA = subsystem(member, "subsystem_a1");
        // mixedSvc has two versions (v1 SOAP, v2 REST) that must aggregate into one ServiceDto.
        serviceEntity(subA, "mixedSvc", "v2", "REST");
        serviceEntity(subA, "mixedSvc", "v1", "SOAP");
        serviceEntity(subA, "getRandom", null, "REST");
        serviceEntity(subB, "onlyService", null, "REST");
        setSubsystems(member, subB, subA);

        when(memberRepository.findActiveWithTreeByNaturalKey(INSTANCE, PUB, MEMBER_CODE))
                .thenReturn(Optional.of(member));

        Optional<FullMemberDto> result = service.getFullTree(PUB, MEMBER_CODE);

        assertTrue(result.isPresent());
        FullMemberDto dto = result.get();
        assertEquals(PUB, dto.getMemberClass());
        assertEquals(MEMBER_CODE, dto.getMemberCode());
        assertTrue(dto.isProvider());
        assertEquals(2, dto.getSubsystemCount());
        // serviceCount is the raw active-row count (FullMemberDto#from sums getActiveServices()
        // per subsystem), not the aggregated-DTO count: subsystem_a1 has 3 rows (mixedSvc v1, v2,
        // getRandom) and subsystem_b1 has 1 (onlyService) = 4.
        assertEquals(4, dto.getServiceCount());

        List<FullSubsystemDto> subs = dto.getSubsystems();
        assertEquals(List.of("subsystem_a1", "subsystem_b1"),
                subs.stream().map(FullSubsystemDto::getSubsystemCode).toList(),
                "subsystems must be sorted by subsystemCode ascending");
        assertEquals("Subsystem A1", subs.get(0).getSubsystemName());

        List<ServiceDto> a1Services = subs.get(0).getServices();
        assertEquals(List.of("getRandom", "mixedSvc"),
                a1Services.stream().map(ServiceDto::getServiceCode).toList(),
                "services within a subsystem must be sorted by serviceCode ascending");
        ServiceDto mixed = a1Services.stream().filter(s -> "mixedSvc".equals(s.getServiceCode())).findFirst()
                .orElseThrow();
        assertEquals(2, mixed.getVersionCount(), "mixedSvc must aggregate both versions into one entry");
        assertTrue(mixed.getServiceTypes().containsAll(List.of("SOAP", "REST")));
    }

    @Test
    void testGetFullTreeExcludesRemovedSubsystemsAndServices() {
        when(instanceContext.getCurrentInstance()).thenReturn(INSTANCE);
        when(sharedParamsCache.subsystemNames()).thenReturn((memberClass, memberCode, subsystemCode) -> null);

        MemberV2 member = member(PUB, MEMBER_CODE, "Nahka-Albert", false);
        SubsystemV2 active = subsystem(member, "active-sub");
        SubsystemV2 removed = subsystem(member, "removed-sub");
        ReflectionTestUtils.setField(removed, "statusInfo", statusInfo(LocalDateTime.now()));
        serviceEntity(active, "svc", null, "REST");
        setSubsystems(member, active, removed);

        when(memberRepository.findActiveWithTreeByNaturalKey(INSTANCE, PUB, MEMBER_CODE))
                .thenReturn(Optional.of(member));

        Optional<FullMemberDto> result = service.getFullTree(PUB, MEMBER_CODE);

        assertTrue(result.isPresent());
        FullMemberDto dto = result.get();
        assertEquals(1, dto.getSubsystems().size(), "removed subsystem must not appear in the active-only tree");
        assertEquals("active-sub", dto.getSubsystems().get(0).getSubsystemCode());
        assertFalse(dto.getSubsystems().get(0).getServices().isEmpty());
    }

    private static MemberListRow memberListRow(String memberClass, String memberCode, String name,
                                               boolean provider, long subsystemCount, long serviceCount) {
        LocalDateTime now = LocalDateTime.now();
        return new MemberListRow() {
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
                return now;
            }

            @Override
            public LocalDateTime getChanged() {
                return now;
            }

            @Override
            public LocalDateTime getFetched() {
                return now;
            }

            @Override
            public LocalDateTime getRemoved() {
                return null;
            }
        };
    }

    private static MemberV2 member(String memberClass, String memberCode, String name, boolean provider) {
        MemberV2 m = new MemberV2();
        ReflectionTestUtils.setField(m, "xRoadInstance", INSTANCE);
        ReflectionTestUtils.setField(m, "memberClass", memberClass);
        ReflectionTestUtils.setField(m, "memberCode", memberCode);
        ReflectionTestUtils.setField(m, "name", name);
        ReflectionTestUtils.setField(m, "isProvider", provider);
        ReflectionTestUtils.setField(m, "statusInfo", statusInfo(null));
        return m;
    }

    private static SubsystemV2 subsystem(MemberV2 member, String subsystemCode) {
        SubsystemV2 s = new SubsystemV2();
        ReflectionTestUtils.setField(s, "member", member);
        ReflectionTestUtils.setField(s, "subsystemCode", subsystemCode);
        ReflectionTestUtils.setField(s, "statusInfo", statusInfo(null));
        return s;
    }

    private static ServiceV2 serviceEntity(SubsystemV2 subsystem, String serviceCode, String serviceVersion,
                                     String serviceType) {
        ServiceV2 svc = new ServiceV2();
        ReflectionTestUtils.setField(svc, "subsystem", subsystem);
        ReflectionTestUtils.setField(svc, "serviceCode", serviceCode);
        ReflectionTestUtils.setField(svc, "serviceVersion", serviceVersion);
        ReflectionTestUtils.setField(svc, "serviceType", serviceType);
        ReflectionTestUtils.setField(svc, "statusInfo", statusInfo(null));
        Set<ServiceV2> services = new HashSet<>(subsystemServices(subsystem));
        services.add(svc);
        ReflectionTestUtils.setField(subsystem, "services", services);
        return svc;
    }

    @SuppressWarnings("unchecked")
    private static Set<ServiceV2> subsystemServices(SubsystemV2 subsystem) {
        Object current = ReflectionTestUtils.getField(subsystem, "services");
        return current == null ? new HashSet<>() : (Set<ServiceV2>) current;
    }

    private static void setSubsystems(MemberV2 member, SubsystemV2... subsystems) {
        ReflectionTestUtils.setField(member, "subsystems", new HashSet<>(Set.of(subsystems)));
    }

    private static StatusInfo statusInfo(LocalDateTime removed) {
        LocalDateTime now = LocalDateTime.now();
        return new StatusInfo(now, now, now, removed);
    }
}
