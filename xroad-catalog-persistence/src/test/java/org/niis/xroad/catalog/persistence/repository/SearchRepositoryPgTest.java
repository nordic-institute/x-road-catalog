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

import org.niis.xroad.catalog.persistence.testsupport.PostgresTestBase;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.jdbc.Sql;

import java.util.List;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Ground truth: {@code pg/v2-fixture.sql}, after the {@code @BeforeEach} recompute. M1 (active,
 * name "Provider Member") is the only provider: subsystem SS1 has four active services (svcA
 * v1/v2 = ids 21/22, svcB = id 23, svcF = id 27); svcC (id 24) is removed; svcD (id 25) hangs off
 * removed member M3; svcE (id 26) hangs off removed subsystem SS4. Per
 * {@code DenormalizationRepository.RECOMPUTE_SERVICE_TYPE_SQL}, service 21 = SOAP (active wsdl),
 * 22 = OPENAPI (its wsdl is removed, only the open_api is active), 23 = REST (classified by its
 * active rest row), 27 = SOAP (the anomaly case: active wsdl AND active open_api — wsdl wins). svcA
 * aggregates ids 21+22 into one row, so its {@code service_types} is the sorted, comma-joined distinct set
 * "OPENAPI,SOAP" (O &lt; S). svcB is REST-only. svcF is SOAP-only (DISTINCT collapses the
 * anomaly to a single value).
 *
 * <p>Of the four subsystems, only SS1 survives the removed/parent-cascade filters (SS2 itself
 * removed, SS3's parent M3 removed, SS4 itself removed) — see
 * {@code SubsystemRepositoryV2PgTest} for the full derivation.
 *
 * <p>Unlike the other V2 repositories, {@link SearchRepository} extends
 * {@code Repository<Member, Long>} and returns raw {@code Object[]} rows (no v2entity read-model
 * classes), so this test does not need the v2entity {@code @EntityScan} used by
 * {@code MemberRepositoryV2PgTest} and friends.
 */
@SpringBootTest
@Sql(scripts = "classpath:pg/v2-fixture.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class SearchRepositoryPgTest extends PostgresTestBase {

    private static final int ENTITY_TYPE = 0;
    private static final int MEMBER_CODE = 4;
    private static final int SUBSYSTEM_CODE = 6;
    private static final int SERVICE_CODE = 7;
    private static final int IS_PROVIDER = 8;
    private static final int SERVICE_TYPES = 9;
    private static final int TOTAL_COUNT = 10;

    @Autowired
    private SearchRepository searchRepository;

    @Autowired
    private DenormalizationRepository denormalizationRepository;

    @BeforeEach
    void recomputeDenormalizedColumns() {
        denormalizationRepository.recomputeMemberIsProvider();
        denormalizationRepository.recomputeServiceType();
    }

    @Test
    void searchUnionAggregatesServiceTypesForServiceRows() {
        List<Object[]> rows = searchRepository.searchUnion("%svc%", 10, 0);

        assertEquals(3, rows.size(), "only svcA, svcB, svcF are active and match; svcC/D/E must be excluded");
        assertEquals("OPENAPI,SOAP", serviceTypesOf(rows, "svcA"), "svcA aggregates ids 21 (SOAP) and 22 (OPENAPI)");
        assertEquals("REST", serviceTypesOf(rows, "svcB"), "svcB (id 23) is classified REST by its rest row");
        assertEquals("SOAP", serviceTypesOf(rows, "svcF"), "svcF (id 27) is the wsdl+open_api anomaly, classified SOAP");
        assertTrue(rows.stream().allMatch(r -> r[IS_PROVIDER] == null), "is_provider is only populated on member rows");
    }

    @Test
    void searchUnionReturnsIsProviderTrueForMemberRowMatchingProvider() {
        List<Object[]> rows = searchRepository.searchUnion("%Provider%", 10, 0);

        assertEquals(1, rows.size(), "only M1's name \"Provider Member\" contains \"Provider\"");
        Object[] m1 = rows.get(0);
        assertEquals("member", m1[ENTITY_TYPE]);
        assertEquals(Boolean.TRUE, m1[IS_PROVIDER]);
        assertNull(m1[SERVICE_TYPES], "service_types is only populated on service rows");
    }

    @Test
    void searchUnionExcludesRemovedMemberEvenWhenNameMatches() {
        // All four members' names contain "Member", but M3 ("Removed Member") is removed.
        List<Object[]> rows = searchRepository.searchUnion("%Member%", 10, 0);

        assertEquals(3, rows.size(), "M3 must be excluded despite its name matching");
        assertTrue(rows.stream().noneMatch(r -> "M3".equals(r[MEMBER_CODE])), "member_code M3 must not appear");
        assertEquals(Boolean.TRUE, isProviderOf(rows, "M1"));
        assertEquals(Boolean.FALSE, isProviderOf(rows, "M2"));
        assertEquals(Boolean.FALSE, isProviderOf(rows, "M4"));
    }

    @Test
    void searchUnionReturnsOnlySs1ForSubsystemQuery() {
        // SS1-SS4 all match "%SS%"; only SS1 survives the removed/parent-cascade filters.
        List<Object[]> rows = searchRepository.searchUnion("%SS%", 10, 0);

        assertEquals(1, rows.size());
        Object[] ss1 = rows.get(0);
        assertEquals("subsystem", ss1[ENTITY_TYPE]);
        assertEquals("SS1", ss1[SUBSYSTEM_CODE]);
        assertNull(ss1[IS_PROVIDER]);
        assertNull(ss1[SERVICE_TYPES]);
    }

    @Test
    void countSearchUnionMatchesUnpagedRowCountForEveryQuery() {
        for (String qLike : List.of("%svc%", "%Provider%", "%Member%", "%SS%")) {
            long count = searchRepository.countSearchUnion(qLike);
            List<Object[]> all = searchRepository.searchUnion(qLike, 10_000, 0);
            assertEquals(count, all.size(), "countSearchUnion mismatch for " + qLike);
        }
    }

    @Test
    void searchUnionLimitOffsetPagesThroughServiceResultsDeterministically() {
        // Sort key order for "%svc%" is lowercased service_code: svca, svcb, svcf.
        List<Object[]> page0 = searchRepository.searchUnion("%svc%", 1, 0);
        List<Object[]> page1 = searchRepository.searchUnion("%svc%", 1, 1);
        List<Object[]> page2 = searchRepository.searchUnion("%svc%", 1, 2);

        assertEquals(1, page0.size());
        assertEquals(1, page1.size());
        assertEquals(1, page2.size());
        assertEquals("svcA", page0.get(0)[SERVICE_CODE]);
        assertEquals("svcB", page1.get(0)[SERVICE_CODE]);
        assertEquals("svcF", page2.get(0)[SERVICE_CODE]);
        assertEquals(3L, ((Number) page0.get(0)[TOTAL_COUNT]).longValue(),
                "total_count must reflect all matching rows, not just the 1-row page");
    }

    private static String serviceTypesOf(List<Object[]> rows, String serviceCode) {
        return rowMatching(rows, r -> serviceCode.equals(r[SERVICE_CODE]))[SERVICE_TYPES].toString();
    }

    private static Object isProviderOf(List<Object[]> rows, String memberCode) {
        return rowMatching(rows, r -> memberCode.equals(r[MEMBER_CODE]))[IS_PROVIDER];
    }

    private static Object[] rowMatching(List<Object[]> rows, Predicate<Object[]> predicate) {
        return rows.stream()
                .filter(predicate)
                .findFirst()
                .orElseThrow(() -> new AssertionError("no matching row found"));
    }
}
