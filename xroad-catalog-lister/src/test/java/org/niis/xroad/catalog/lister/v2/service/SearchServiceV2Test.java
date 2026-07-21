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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.catalog.lister.v2.dto.MemberSearchHit;
import org.niis.xroad.catalog.lister.v2.dto.SearchHit;
import org.niis.xroad.catalog.lister.v2.dto.ServiceSearchHit;
import org.niis.xroad.catalog.lister.v2.dto.SubsystemSearchHit;
import org.niis.xroad.catalog.persistence.repository.SearchRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SearchServiceV2Test {

    @Mock
    private SearchRepository searchRepository;

    private SearchServiceV2 service;

    @BeforeEach
    void setUp() {
        service = new SearchServiceV2(searchRepository);
    }

    @Test
    void testSearchMinQueryLengthEnforced() {
        assertThrows(IllegalArgumentException.class, () -> service.search("ab", PageRequest.of(0, 50)));
    }

    @Test
    void testSearchNullQueryRejected() {
        assertThrows(IllegalArgumentException.class, () -> service.search(null, PageRequest.of(0, 50)));
    }

    @Test
    void testSearchReturnsEmptyPageWithZeroTotalWhenNoRowsMatchAtOffsetZero() {
        when(searchRepository.searchUnion(anyString(), anyInt(), anyLong())).thenReturn(List.of());

        Page<SearchHit> page = service.search("nomatch", PageRequest.of(0, 50));

        assertTrue(page.isEmpty());
        assertEquals(0, page.getTotalElements());
        verify(searchRepository, never()).countSearchUnion(anyString());
    }

    @Test
    void emptyPageBeyondEndFallsBackToCountQuery() {
        when(searchRepository.searchUnion("%abc%", 10, 20L)).thenReturn(List.of());
        when(searchRepository.countSearchUnion("%abc%")).thenReturn(12L);

        Page<SearchHit> page = service.search("abc", PageRequest.of(2, 10));

        assertEquals(0, page.getContent().size());
        assertEquals(12, page.getTotalElements());
    }

    @Test
    void testSearchMapsMemberRowWithIsProviderFromColumn8() {
        Object[] row = {"member", 1L, "nahka", "PUB", "14151328", "Nahka-Albert", null, null, Boolean.TRUE, null, 1L};
        when(searchRepository.searchUnion(anyString(), anyInt(), anyLong())).thenReturn(List.<Object[]>of(row));

        Page<SearchHit> page = service.search("nahka", PageRequest.of(0, 50));

        MemberSearchHit hit = (MemberSearchHit) page.getContent().get(0);
        assertEquals("PUB", hit.memberClass());
        assertEquals("14151328", hit.memberCode());
        assertEquals("Nahka-Albert", hit.name());
        assertTrue(hit.isProvider());
        assertEquals(1, page.getTotalElements());
    }

    @Test
    void testSearchMapsMemberRowIsProviderFalseWhenColumnNull() {
        Object[] row = {"member", 1L, "nahka", "PUB", "14151328", "Nahka-Albert", null, null, null, null, 1L};
        when(searchRepository.searchUnion(anyString(), anyInt(), anyLong())).thenReturn(List.<Object[]>of(row));

        Page<SearchHit> page = service.search("nahka", PageRequest.of(0, 50));

        MemberSearchHit hit = (MemberSearchHit) page.getContent().get(0);
        assertFalse(hit.isProvider());
    }

    @Test
    void testSearchMapsSubsystemRow() {
        Object[] row = {"subsystem", 2L, "subsystem_a1", "PUB", "14151328", "Nahka-Albert", "subsystem_a1", null,
                null, null, 1L};
        when(searchRepository.searchUnion(anyString(), anyInt(), anyLong())).thenReturn(List.<Object[]>of(row));

        Page<SearchHit> page = service.search("subsystem", PageRequest.of(0, 50));

        SubsystemSearchHit hit = (SubsystemSearchHit) page.getContent().get(0);
        assertEquals("subsystem_a1", hit.subsystemCode());
        assertEquals("PUB", hit.memberClass());
    }

    @Test
    void testSearchMapsServiceRowSplittingServiceTypesColumn() {
        Object[] row = {"service", 3L, "mixedsvc", "PUB", "14151328", "Nahka-Albert", "subsystem_a1", "mixedSvc",
                null, "REST,SOAP", 1L};
        when(searchRepository.searchUnion(anyString(), anyInt(), anyLong())).thenReturn(List.<Object[]>of(row));

        Page<SearchHit> page = service.search("mixedSvc", PageRequest.of(0, 50));

        ServiceSearchHit hit = (ServiceSearchHit) page.getContent().get(0);
        assertEquals("mixedSvc", hit.serviceCode());
        assertEquals(List.of("REST", "SOAP"), hit.serviceTypes());
    }

    @Test
    void testSearchThrowsForUnknownEntityType() {
        Object[] row = {"bogus", 1L, "xyz", "PUB", "14151328", "Nahka-Albert", null, null, null, null, 1L};
        when(searchRepository.searchUnion(anyString(), anyInt(), anyLong())).thenReturn(List.<Object[]>of(row));

        assertThrows(IllegalStateException.class, () -> service.search("xyz", PageRequest.of(0, 50)));
    }

    @Test
    void testSearchEscapesLikeMetacharactersBeforePassingToRepository() {
        when(searchRepository.searchUnion(anyString(), anyInt(), anyLong())).thenReturn(List.of());
        ArgumentCaptor<String> likeCaptor = ArgumentCaptor.forClass(String.class);

        service.search("subs%m_x\\y", PageRequest.of(0, 50));

        verify(searchRepository).searchUnion(likeCaptor.capture(), anyInt(), anyLong());
        assertEquals("%subs\\%m\\_x\\\\y%", likeCaptor.getValue(),
                "'\\' must be escaped first, then '%' and '_', so they are matched literally");
    }

    @Test
    void testSearchPassesThroughPageSizeAndOffset() {
        when(searchRepository.searchUnion(anyString(), anyInt(), anyLong())).thenReturn(List.of());
        when(searchRepository.countSearchUnion(anyString())).thenReturn(5L);

        service.search("subsystem", PageRequest.of(1, 2));

        verify(searchRepository).searchUnion(anyString(), org.mockito.ArgumentMatchers.eq(2),
                org.mockito.ArgumentMatchers.eq(2L));
    }
}
