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
import org.niis.xroad.catalog.lister.v2.dto.MemberClassDto;
import org.niis.xroad.catalog.persistence.repository.MemberRepositoryV2;
import org.niis.xroad.catalog.persistence.repository.projection.MemberClassCountRow;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MemberClassServiceV2Test {

    private static final String INSTANCE = "TEST-INSTANCE";
    private static final String PUB = "PUB";

    @Mock
    private SharedParamsCache sharedParamsCache;

    @Mock
    private MemberRepositoryV2 memberRepository;

    @Mock
    private InstanceContext instanceContext;

    private MemberClassServiceV2 service;

    @BeforeEach
    void setUp() {
        service = new MemberClassServiceV2(sharedParamsCache, memberRepository, instanceContext);
    }

    @Test
    void testListIncludesPubMemberCount() {
        when(instanceContext.getCurrentInstance()).thenReturn(INSTANCE);
        when(sharedParamsCache.memberClasses())
                .thenReturn(new SharedParamsCache.MemberClasses(Map.of(PUB, "Public"), Set.of(PUB)));
        when(memberRepository.countActiveGroupedByMemberClass(INSTANCE)).thenReturn(List.of(
                countRow(PUB, 5L)));

        List<MemberClassDto> classes = service.list();

        assertFalse(classes.isEmpty());
        boolean pubPresent = classes.stream().anyMatch(c -> PUB.equals(c.getCode()) && c.getMemberCount() > 0);
        assertTrue(pubPresent, "PUB class present in DB must appear in listing with positive memberCount");
    }

    @Test
    void testListSortsByCode() {
        when(instanceContext.getCurrentInstance()).thenReturn(INSTANCE);
        when(sharedParamsCache.memberClasses())
                .thenReturn(new SharedParamsCache.MemberClasses(Map.of(), Set.of()));
        when(memberRepository.countActiveGroupedByMemberClass(INSTANCE)).thenReturn(List.of(
                countRow(PUB, 2L), countRow("COM", 1L), countRow("GOV", 3L)));

        List<MemberClassDto> classes = service.list();

        assertEquals(List.of("COM", "GOV", PUB), classes.stream().map(MemberClassDto::getCode).toList());
    }

    @Test
    void testGetByCodeReturnsMatchingClass() {
        when(instanceContext.getCurrentInstance()).thenReturn(INSTANCE);
        when(memberRepository.countActiveByMemberClass(INSTANCE, PUB)).thenReturn(4L);

        MemberClassDto pub = service.getByCode(PUB);

        assertNotNull(pub, "PUB class should be returned by getByCode");
        assertEquals(PUB, pub.getCode());
        assertTrue(pub.getMemberCount() > 0, "PUB must have a positive member count");
    }

    @Test
    void testGetByCodeReturnsNullForUnknownCode() {
        when(instanceContext.getCurrentInstance()).thenReturn(INSTANCE);
        when(memberRepository.countActiveByMemberClass(INSTANCE, "DOES-NOT-EXIST")).thenReturn(0L);

        assertNull(service.getByCode("DOES-NOT-EXIST"));
    }

    @Test
    void testGetByCodeReturnsNullForNullInput() {
        assertNull(service.getByCode(null));
    }

    @Test
    void testGetByCodeToleratesParserFailure() {
        // The cache absorbs parser failures internally and serves an empty map for the TTL window;
        // the service must still surface a class backed by real members.
        when(instanceContext.getCurrentInstance()).thenReturn(INSTANCE);
        when(sharedParamsCache.memberClassDescriptions()).thenReturn(Map.of());
        when(memberRepository.countActiveByMemberClass(INSTANCE, PUB)).thenReturn(1L);

        MemberClassDto pub = service.getByCode(PUB);

        assertNotNull(pub, "a parser failure must not prevent returning a class backed by real members");
    }

    private static MemberClassCountRow countRow(String code, long count) {
        return new MemberClassCountRow() {
            @Override
            public String getCode() {
                return code;
            }

            @Override
            public long getMemberCount() {
                return count;
            }
        };
    }
}
