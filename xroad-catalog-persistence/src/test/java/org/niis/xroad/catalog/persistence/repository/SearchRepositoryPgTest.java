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
package org.niis.xroad.catalog.persistence.repository;

import org.niis.xroad.catalog.persistence.v2.repository.SearchRepository;
import org.niis.xroad.catalog.persistence.v2.repository.projection.SearchHitRow;
import org.niis.xroad.catalog.persistence.testsupport.PostgresTestBase;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;

import java.util.List;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runs against {@code pg/v2-fixture.sql} after the denormalization recompute. Only member M1 and
 * subsystem SS1 survive the removed/parent-cascade filters; SS1's active services are svcA (SOAP
 * id 21 + OPENAPI id 22, aggregated to "OPENAPI,SOAP"), svcB (REST) and svcF (the wsdl+open_api
 * anomaly, classified SOAP). {@link SearchRepository} returns {@link SearchHitRow} projections,
 * so no v2.entity {@code @EntityScan} is needed.
 */
@SpringBootTest
@Sql(scripts = "classpath:pg/v2-fixture.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class SearchRepositoryPgTest extends PostgresTestBase {

    private static final String INSTANCE = "TEST";
    private static final String FOREIGN_INSTANCE = "OTHER";
    private static final String MEMBER_QUERY = "%Member%";

    @Autowired
    private SearchRepository searchRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DenormalizationRepository denormalizationRepository;

    @BeforeEach
    void recomputeDenormalizedColumns() {
        denormalizationRepository.recomputeMemberIsProvider();
        denormalizationRepository.recomputeServiceType();
    }

    @Test
    void searchUnionAggregatesServiceTypesForServiceRows() {
        List<SearchHitRow> rows = searchRepository.searchUnion(INSTANCE, "%svc%", 10, 0);

        assertEquals(3, rows.size(), "only svcA, svcB, svcF are active and match; svcC/D/E must be excluded");
        assertEquals("OPENAPI,SOAP", serviceTypesOf(rows, "svcA"), "svcA aggregates ids 21 (SOAP) and 22 (OPENAPI)");
        assertEquals("REST", serviceTypesOf(rows, "svcB"), "svcB (id 23) is classified REST by its rest row");
        assertEquals("SOAP", serviceTypesOf(rows, "svcF"), "svcF (id 27) is the wsdl+open_api anomaly, classified SOAP");
        assertTrue(rows.stream().allMatch(r -> r.getIsProvider() == null), "is_provider is only populated on member rows");
    }

    @Test
    void searchUnionReturnsIsProviderTrueForMemberRowMatchingProvider() {
        List<SearchHitRow> rows = searchRepository.searchUnion(INSTANCE, "%Provider%", 10, 0);

        assertEquals(1, rows.size(), "only M1's name \"Provider Member\" contains \"Provider\"");
        SearchHitRow m1 = rows.get(0);
        assertEquals("member", m1.getEntityType());
        assertEquals(Boolean.TRUE, m1.getIsProvider());
        assertNull(m1.getServiceTypes(), "service_types is only populated on service rows");
    }

    @Test
    void searchUnionExcludesRemovedMemberEvenWhenNameMatches() {
        // All four members' names contain "Member", but M3 ("Removed Member") is removed.
        List<SearchHitRow> rows = searchRepository.searchUnion(INSTANCE, MEMBER_QUERY, 10, 0);

        assertEquals(3, rows.size(), "M3 must be excluded despite its name matching");
        assertTrue(rows.stream().noneMatch(r -> "M3".equals(r.getMemberCode())), "member_code M3 must not appear");
        assertEquals(Boolean.TRUE, isProviderOf(rows, "M1"));
        assertEquals(Boolean.FALSE, isProviderOf(rows, "M2"));
        assertEquals(Boolean.FALSE, isProviderOf(rows, "M4"));
    }

    @Test
    void searchUnionReturnsOnlySs1ForSubsystemQuery() {
        // SS1-SS4 all match "%SS%"; only SS1 survives the removed/parent-cascade filters.
        List<SearchHitRow> rows = searchRepository.searchUnion(INSTANCE, "%SS%", 10, 0);

        assertEquals(1, rows.size());
        SearchHitRow ss1 = rows.get(0);
        assertEquals("subsystem", ss1.getEntityType());
        assertEquals("SS1", ss1.getSubsystemCode());
        assertNull(ss1.getIsProvider());
        assertNull(ss1.getServiceTypes());
    }

    @Test
    void countSearchUnionMatchesUnpagedRowCountForEveryQuery() {
        for (String qLike : List.of("%svc%", "%Provider%", MEMBER_QUERY, "%SS%")) {
            long count = searchRepository.countSearchUnion(INSTANCE, qLike);
            List<SearchHitRow> all = searchRepository.searchUnion(INSTANCE, qLike, 10_000, 0);
            assertEquals(count, all.size(), "countSearchUnion mismatch for " + qLike);
        }
    }

    @Test
    void searchUnionLimitOffsetPagesThroughServiceResultsDeterministically() {
        // Sort key order for "%svc%" is lowercased service_code: svca, svcb, svcf.
        List<SearchHitRow> page0 = searchRepository.searchUnion(INSTANCE, "%svc%", 1, 0);
        List<SearchHitRow> page1 = searchRepository.searchUnion(INSTANCE, "%svc%", 1, 1);
        List<SearchHitRow> page2 = searchRepository.searchUnion(INSTANCE, "%svc%", 1, 2);

        assertEquals(1, page0.size());
        assertEquals(1, page1.size());
        assertEquals(1, page2.size());
        assertEquals("svcA", page0.get(0).getServiceCode());
        assertEquals("svcB", page1.get(0).getServiceCode());
        assertEquals("svcF", page2.get(0).getServiceCode());
        assertEquals(3L, page0.get(0).getTotalCount(),
                "total_count must reflect all matching rows, not just the 1-row page");
    }

    /**
     * Search must be instance-scoped like every other V2 read: hits from another instance would 404
     * on the follow-up browse call.
     */
    @Test
    @Sql(scripts = {"classpath:pg/v2-fixture.sql", "classpath:pg/search-foreign-instance.sql"},
            executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    void searchUnionExcludesRowsBelongingToAnotherXRoadInstance() {
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM member WHERE x_road_instance = 'OTHER'", Integer.class),
                "the foreign-instance row must be present in the base table");

        List<SearchHitRow> members = searchRepository.searchUnion(INSTANCE, MEMBER_QUERY, 10, 0);
        assertEquals(3, members.size(), "M5 belongs to instance OTHER and must not be a hit");
        assertTrue(members.stream().noneMatch(r -> "M5".equals(r.getMemberCode())));
        assertEquals(3L, searchRepository.countSearchUnion(INSTANCE, MEMBER_QUERY));

        assertTrue(searchRepository.searchUnion(INSTANCE, "%SS5%", 10, 0).isEmpty(),
                "a subsystem of a foreign-instance member must not be a hit");
        assertTrue(searchRepository.searchUnion(INSTANCE, "%svcG%", 10, 0).isEmpty(),
                "a service of a foreign-instance member must not be a hit");

        List<SearchHitRow> foreignHits = searchRepository.searchUnion(FOREIGN_INSTANCE, MEMBER_QUERY, 10, 0);
        assertEquals(1, foreignHits.size(), "the same query scoped to OTHER returns only its own member");
        assertEquals("M5", foreignHits.get(0).getMemberCode());
    }

    private static String serviceTypesOf(List<SearchHitRow> rows, String serviceCode) {
        return rowMatching(rows, r -> serviceCode.equals(r.getServiceCode())).getServiceTypes();
    }

    private static Boolean isProviderOf(List<SearchHitRow> rows, String memberCode) {
        return rowMatching(rows, r -> memberCode.equals(r.getMemberCode())).getIsProvider();
    }

    private static SearchHitRow rowMatching(List<SearchHitRow> rows, Predicate<SearchHitRow> predicate) {
        return rows.stream()
                .filter(predicate)
                .findFirst()
                .orElseThrow(() -> new AssertionError("no matching row found"));
    }
}
