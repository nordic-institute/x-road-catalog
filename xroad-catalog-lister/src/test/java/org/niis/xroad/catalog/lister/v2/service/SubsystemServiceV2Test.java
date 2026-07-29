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
import org.niis.xroad.catalog.lister.v2.dto.SubsystemDto;
import org.niis.xroad.catalog.persistence.v2.repository.MemberRepository;
import org.niis.xroad.catalog.persistence.v2.repository.SubsystemRepository;
import org.niis.xroad.catalog.persistence.v2.repository.projection.SubsystemListRow;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubsystemServiceV2Test {

    private static final String INSTANCE = "TEST-INSTANCE";
    private static final String PUB = "PUB";
    private static final String CODE_14151328 = "14151328";
    private static final String SUBSYSTEM_A1 = "subsystem_a1";
    private static final String SUBSYSTEM_A2 = "subsystem_a2";

    @Mock
    private SubsystemRepository subsystemRepository;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private SharedParamsCache sharedParamsCache;


    private SubsystemServiceV2 service;

    @BeforeEach
    void setUp() {
        service = new SubsystemServiceV2(subsystemRepository, memberRepository, sharedParamsCache);
    }

    @Test
    void testGetByNaturalKeyReturnsDtoWithNameFromSharedParams() {
        when(sharedParamsCache.getCurrentInstance()).thenReturn(INSTANCE);
        when(subsystemRepository.findActiveSummaryByNaturalKey(INSTANCE, PUB, CODE_14151328, SUBSYSTEM_A1))
                .thenReturn(Optional.of(subsystemListRow(SUBSYSTEM_A1, 3)));
        when(sharedParamsCache.subsystemNames()).thenReturn((memberClass, memberCode, subsystemCode) ->
                PUB.equals(memberClass) && CODE_14151328.equals(memberCode) && SUBSYSTEM_A1.equals(subsystemCode)
                        ? "Subsystem A1" : null);

        Optional<SubsystemDto> result = service.getByNaturalKey(PUB, CODE_14151328, SUBSYSTEM_A1);

        assertTrue(result.isPresent());
        SubsystemDto dto = result.get();
        assertEquals(SUBSYSTEM_A1, dto.getSubsystemCode());
        assertEquals("Subsystem A1", dto.getSubsystemName(),
                "subsystemName must be resolved from the shared-params lookup");
        assertEquals(3, dto.getServiceCount());
    }

    @Test
    void testGetByNaturalKeyReturnsEmptyOptionalWhenAbsent() {
        when(sharedParamsCache.getCurrentInstance()).thenReturn(INSTANCE);
        when(subsystemRepository.findActiveSummaryByNaturalKey(INSTANCE, PUB, CODE_14151328, "does-not-exist"))
                .thenReturn(Optional.empty());

        assertTrue(service.getByNaturalKey(PUB, CODE_14151328, "does-not-exist").isEmpty());
    }

    @Test
    void testGetForListMapsPageOfRows() {
        when(sharedParamsCache.getCurrentInstance()).thenReturn(INSTANCE);
        when(sharedParamsCache.subsystemNames()).thenReturn((memberClass, memberCode, subsystemCode) -> null);
        Page<SubsystemListRow> page = new PageImpl<>(List.of(subsystemListRow(SUBSYSTEM_A1, 1)),
                PageRequest.of(0, 20), 1);
        when(subsystemRepository.findActiveForList(INSTANCE, PUB, PageRequest.of(0, 20))).thenReturn(page);

        Page<SubsystemDto> result = service.getForList(PUB, PageRequest.of(0, 20));

        assertEquals(1, result.getTotalElements());
        assertEquals(SUBSYSTEM_A1, result.getContent().get(0).getSubsystemCode());
    }

    @Test
    void testGetForMemberReturnsEmptyOptionalWhenMemberAbsent() {
        when(sharedParamsCache.getCurrentInstance()).thenReturn(INSTANCE);
        when(memberRepository.existsActiveByNaturalKey(INSTANCE, PUB, "no-such-member")).thenReturn(false);

        assertTrue(service.getForMember(PUB, "no-such-member").isEmpty(),
                "absent member must yield an empty Optional (404), not an empty list");
    }

    @Test
    void testGetForMemberReturnsSortedSubsystemsWhenMemberExists() {
        when(sharedParamsCache.getCurrentInstance()).thenReturn(INSTANCE);
        when(memberRepository.existsActiveByNaturalKey(INSTANCE, PUB, CODE_14151328)).thenReturn(true);
        when(sharedParamsCache.subsystemNames()).thenReturn((memberClass, memberCode, subsystemCode) -> null);
        when(subsystemRepository.findActiveForMember(INSTANCE, PUB, CODE_14151328)).thenReturn(List.of(
                subsystemListRow(SUBSYSTEM_A1, 1), subsystemListRow(SUBSYSTEM_A2, 0)));

        Optional<List<SubsystemDto>> result = service.getForMember(PUB, CODE_14151328);

        assertTrue(result.isPresent());
        assertEquals(2, result.get().size());
        assertEquals(SUBSYSTEM_A1, result.get().get(0).getSubsystemCode());
        assertEquals(SUBSYSTEM_A2, result.get().get(1).getSubsystemCode());
    }

    @Test
    void testGetForMemberReturnsPresentButEmptyListWhenMemberHasNoSubsystems() {
        when(sharedParamsCache.getCurrentInstance()).thenReturn(INSTANCE);
        when(memberRepository.existsActiveByNaturalKey(INSTANCE, PUB, CODE_14151328)).thenReturn(true);
        when(subsystemRepository.findActiveForMember(INSTANCE, PUB, CODE_14151328)).thenReturn(List.of());

        Optional<List<SubsystemDto>> result = service.getForMember(PUB, CODE_14151328);

        assertTrue(result.isPresent(), "an existing member with zero subsystems is still a present Optional");
        assertTrue(result.get().isEmpty());
    }

    private static SubsystemListRow subsystemListRow(String subsystemCode, long serviceCount) {
        LocalDateTime now = LocalDateTime.now();
        return new SubsystemListRow() {
            @Override
            public String getMemberClass() {
                return PUB;
            }

            @Override
            public String getMemberCode() {
                return CODE_14151328;
            }

            @Override
            public String getMemberName() {
                return "Nahka-Albert";
            }

            @Override
            public String getSubsystemCode() {
                return subsystemCode;
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
        };
    }
}
