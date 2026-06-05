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

import org.junit.jupiter.api.Test;
import org.niis.xroad.catalog.lister.v2.dto.MemberSearchHit;
import org.niis.xroad.catalog.lister.v2.dto.SearchHit;
import org.niis.xroad.catalog.lister.v2.dto.ServiceSearchHit;
import org.niis.xroad.catalog.lister.v2.dto.SubsystemSearchHit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@TestPropertySource(properties = {"xroad-catalog.shared-params-file=src/test/resources/shared-params-dev-cs.xml"})
@ActiveProfiles({"test", "general-testdata"})
public class SearchServiceV2Test {

    @Autowired
    private SearchServiceV2 searchService;

    @Test
    public void testSearchMixedTypes() {
        Page<SearchHit> page = searchService.search("subsystem", PageRequest.of(0, 50));
        assertFalse(page.isEmpty());
        Set<String> types = page.getContent().stream().map(SearchServiceV2Test::typeOf).collect(Collectors.toSet());
        assertTrue(types.contains("subsystem"));
    }

    @Test
    public void testSearchMinQueryLengthEnforced() {
        assertThrows(IllegalArgumentException.class, () -> searchService.search("ab", PageRequest.of(0, 50)));
    }

    @Test
    public void testSearchNullQueryRejected() {
        assertThrows(IllegalArgumentException.class, () -> searchService.search(null, PageRequest.of(0, 50)));
    }

    @Test
    public void testSearchIsActiveOnly() {
        Page<SearchHit> page = searchService.search("subsystem", PageRequest.of(0, 100));
        boolean leaks = page.getContent().stream()
                .anyMatch(r -> r instanceof SubsystemSearchHit s && "subsystem_a3_removed".equals(s.subsystemCode()));
        assertFalse(leaks, "search must not return removed entities");
    }

    @Test
    public void testSearchPaginationIsStable() {
        Page<SearchHit> page1 = searchService.search("subsystem", PageRequest.of(0, 2));
        Page<SearchHit> page2 = searchService.search("subsystem", PageRequest.of(0, 2));
        assertEquals(page1.getContent().size(), page2.getContent().size());
        for (int i = 0; i < page1.getContent().size(); i++) {
            assertEquals(stableKey(page1.getContent().get(i)), stableKey(page2.getContent().get(i)));
        }
    }

    @Test
    public void testSearchAggregatesServices() {
        // Phase 2 Task 8 fixture: member 14151328 / subsystem_a1 / mixedSvc has v1 (SOAP) + v2 (REST)
        // Must appear ONCE in search results, not once per version.
        Page<SearchHit> page = searchService.search("mixedSvc", PageRequest.of(0, 50));
        long mixedSvcHits = page.getContent().stream()
                .filter(r -> r instanceof ServiceSearchHit s && "mixedSvc".equals(s.serviceCode()))
                .count();
        assertEquals(1, mixedSvcHits, "mixedSvc aggregate must appear once, not once per version");
        ServiceSearchHit mixed = page.getContent().stream()
                .filter(r -> r instanceof ServiceSearchHit s && "mixedSvc".equals(s.serviceCode()))
                .map(r -> (ServiceSearchHit) r)
                .findFirst()
                .orElseThrow();
        assertTrue(mixed.serviceTypes().contains("SOAP"));
        assertTrue(mixed.serviceTypes().contains("REST"));
    }

    /**
     * Task 1.5 regression: a member whose only active service has no descriptor rows of any kind
     * must still report {@code isProvider: true} in search results. The new predicate is
     * service-existence based (spec §6.1), matching {@code ServiceClassifier}'s REST default.
     * Fixture: member 13 ("Updated Service") with service 3 under subsystem_5-1 (no WSDL /
     * OpenAPI / Rest rows). Its name "Updated Service" matches the search query.
     */
    @Test
    public void testSearchMemberWithDescriptorLessActiveServiceIsProvider() {
        Page<SearchHit> page = searchService.search("Updated Service", PageRequest.of(0, 50));
        MemberSearchHit match = page.getContent().stream()
                .filter(r -> r instanceof MemberSearchHit m && "13".equals(m.memberCode()))
                .map(r -> (MemberSearchHit) r)
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "Member 13 ('Updated Service') must appear in search results"));
        assertTrue(match.isProvider(),
                "member with an active descriptor-less service must still be provider in search");
    }

    @Test
    public void testSearchEscapesPercentMetacharacter() {
        // Constructed to expose a regression: WITHOUT escape, the LIKE pattern "%subs%m%" matches
        // any text containing "subs" followed eventually by "m" — including every "subsystem*"
        // row in the fixture (multiple matches). WITH escape, the pattern requires the literal
        // substring "subs%m", which no fixture row contains. So an empty result here proves the
        // service is treating '%' as a literal, not as a LIKE wildcard.
        Page<SearchHit> page = searchService.search("subs%m", PageRequest.of(0, 50));
        assertTrue(page.isEmpty(),
                "literal '%' in q must be escaped, not interpreted as a LIKE wildcard");
    }

    @Test
    public void testSearchEscapesUnderscoreMetacharacter() {
        // Constructed to expose a regression: WITHOUT escape, the LIKE pattern "%s_bsystem%"
        // matches "subsystem" ('s' + 'u' bound by the '_' wildcard + "bsystem"), so every
        // "subsystem*" row in the fixture matches. WITH escape, the pattern requires the literal
        // substring "s_bsystem", which no fixture row contains. So an empty result here proves
        // the service is treating '_' as a literal, not as a single-char LIKE wildcard.
        Page<SearchHit> page = searchService.search("s_bsystem", PageRequest.of(0, 50));
        assertTrue(page.isEmpty(),
                "literal '_' in q must be escaped, not interpreted as a single-char LIKE wildcard");
    }

    @Test
    public void testSearchPagesDoNotOverlap() {
        // Spec §3.3 + §8: pagination must be deterministic and stable across pages.
        // A query that matches at least 4 rows is paginated 2 per page; the two pages must be disjoint.
        Page<SearchHit> page1 = searchService.search("subsystem", PageRequest.of(0, 2));
        Page<SearchHit> page2 = searchService.search("subsystem", PageRequest.of(1, 2));
        assertEquals(2, page1.getNumberOfElements(), "fixture must yield at least 4 'subsystem' matches");
        assertTrue(page2.getNumberOfElements() >= 1, "fixture must yield at least 4 'subsystem' matches");
        Set<String> page1Keys = page1.getContent().stream().map(SearchServiceV2Test::stableKey).collect(Collectors.toSet());
        Set<String> page2Keys = page2.getContent().stream().map(SearchServiceV2Test::stableKey).collect(Collectors.toSet());
        page1Keys.retainAll(page2Keys);
        assertTrue(page1Keys.isEmpty(),
                "page 1 and page 2 must be disjoint; overlap: " + page1Keys);
    }

    private static String typeOf(SearchHit hit) {
        return switch (hit) {
            case MemberSearchHit m -> "member";
            case SubsystemSearchHit s -> "subsystem";
            case ServiceSearchHit s -> "service";
        };
    }

    private static String stableKey(SearchHit hit) {
        return switch (hit) {
            case MemberSearchHit m -> "member:" + m.memberClass() + ":" + m.memberCode() + ":" + m.name();
            case SubsystemSearchHit s -> "subsystem:" + s.memberClass() + ":" + s.memberCode() + ":" + s.subsystemCode();
            case ServiceSearchHit s -> "service:" + s.memberClass() + ":" + s.memberCode() + ":"
                    + s.subsystemCode() + ":" + s.serviceCode();
        };
    }
}
