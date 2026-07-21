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

import org.niis.xroad.catalog.persistence.repository.projection.ServiceAggregateRow;
import org.niis.xroad.catalog.persistence.testsupport.PostgresTestBase;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.jdbc.Sql;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Pins the exact Hibernate prepared-statement count of the V2 read-model's list/search entry
 * points against {@code pg/v2-fixture.sql}, and proves each count is invariant to page size /
 * result-set size. This is the regression gate for the projection rewrite: the whole point of
 * replacing entity-graph traversals with flat projection queries was to collapse each list request
 * into a small, fixed number of statements (rows + count, never one query per returned row). A
 * pinned count that silently grows signals a reintroduced N+1 or a lazy-loaded association being
 * walked outside the query itself.
 *
 * <p>Ground truth: under active GOV member M1's only active subsystem SS1 (id 11), four services
 * are active (svcA ids 21/22, svcB id 23, svcF id 27); svcC (id 24) is removed. svcD/svcE hang off
 * a removed member / removed subsystem respectively and are excluded by the parent cascade — see
 * {@code MemberRepositoryV2PgTest} / {@code SubsystemRepositoryV2PgTest} / {@code
 * ServiceRepositoryV2PgTest} for the full per-repository derivations this test reuses.
 *
 * <p>Statement counts are isolated per measured block by clearing the persistence context
 * ({@code entityManager.clear()}) and the Hibernate {@link Statistics} snapshot ({@code
 * stats.clear()}) immediately before the call under test; fixture loading (the {@code @Sql}
 * script) and the {@code @BeforeEach} denormalization recompute both run before that reset, so
 * neither can leak into a measured delta.
 */
@SpringBootTest
@EntityScan(basePackages = {
        "org.niis.xroad.catalog.persistence.entity",
        "org.niis.xroad.catalog.persistence.v2entity"
})
@Sql(scripts = {"classpath:pg/v2-fixture.sql", "classpath:pg/query-count-padding.sql"},
        executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class PgQueryCountTest extends PostgresTestBase {

    @Autowired
    private MemberRepositoryV2 memberRepository;

    @Autowired
    private SubsystemRepositoryV2 subsystemRepository;

    @Autowired
    private ServiceRepositoryV2 serviceRepository;

    @Autowired
    private SearchRepository searchRepository;

    @Autowired
    private ReportsRepositoryV2 reportsRepository;

    @Autowired
    private DenormalizationRepository denormalizationRepository;

    @PersistenceContext
    private EntityManager entityManager;

    private Statistics stats;

    @BeforeEach
    void recomputeDenormalizedColumns() {
        denormalizationRepository.recomputeMemberIsProvider();
        denormalizationRepository.recomputeServiceType();
        stats = entityManager.getEntityManagerFactory()
                .unwrap(SessionFactory.class)
                .getStatistics();
        stats.setStatisticsEnabled(true);
    }

    /**
     * {@code findActiveForList} issues exactly one row query (the two per-member subquery
     * expressions in the select list are correlated subqueries compiled into that single SQL
     * statement, not separate round-trips) plus one {@code COUNT} query — 2 statements — at every
     * page size. Proven at page sizes 1 and 20: {@code pg/query-count-padding.sql} adds 21 extra
     * active members on top of the canonical fixture's 3 (24 total), so a page size of 20 can
     * never hold the full result set and {@link org.springframework.data.support.PageableExecutionUtils}
     * can never short-circuit the count by deriving it from {@code content.size()}. Without that
     * padding, page size 20 would fall into that skip-count branch and this invariant could not be
     * asserted at that page size.
     */
    @Test
    void findActiveForListMemberQueryCountIsExactlyTwoAtBothPageSizes() {
        entityManager.clear();
        stats.clear();
        Page<?> page1 = memberRepository.findActiveForList("TEST", null, null, PageRequest.of(0, 1));
        long queriesAtPageSize1 = stats.getPrepareStatementCount();

        entityManager.clear();
        stats.clear();
        Page<?> page20 = memberRepository.findActiveForList("TEST", null, null, PageRequest.of(0, 20));
        long queriesAtPageSize20 = stats.getPrepareStatementCount();

        assertEquals(1, page1.getContent().size());
        assertEquals(20, page20.getContent().size());
        assertEquals(24, page1.getTotalElements());
        assertEquals(24, page20.getTotalElements());
        assertEquals(2, queriesAtPageSize1, "findActiveForList must be exactly rows + count");
        assertEquals(queriesAtPageSize1, queriesAtPageSize20,
                "MemberRepositoryV2.findActiveForList query count must not grow with page size");
    }

    /**
     * Same shape as the member list: one row query (with a correlated {@code serviceCount}
     * subquery) plus one {@code COUNT} query, 2 statements total, at every page size. Proven at
     * page sizes 1 and 20: {@code pg/query-count-padding.sql} adds 21 extra active subsystems on
     * top of the canonical fixture's 1 active subsystem (SS1), 22 total, so a page size of 20 can
     * never hold the full result set and {@link org.springframework.data.support.PageableExecutionUtils}
     * can never short-circuit the count. Without that padding, page size 20 would fall into that
     * skip-count branch and this invariant could not be asserted at that page size.
     */
    @Test
    void findActiveForListSubsystemQueryCountIsExactlyTwoAtBothPageSizes() {
        entityManager.clear();
        stats.clear();
        Page<?> page1 = subsystemRepository.findActiveForList("TEST", null, PageRequest.of(0, 1));
        long queriesAtPageSize1 = stats.getPrepareStatementCount();

        entityManager.clear();
        stats.clear();
        Page<?> page20 = subsystemRepository.findActiveForList("TEST", null, PageRequest.of(0, 20));
        long queriesAtPageSize20 = stats.getPrepareStatementCount();

        assertEquals(1, page1.getContent().size());
        assertEquals(20, page20.getContent().size());
        assertEquals(22, page1.getTotalElements());
        assertEquals(22, page20.getTotalElements());
        assertEquals(2, queriesAtPageSize1, "findActiveForList must be exactly rows + count");
        assertEquals(queriesAtPageSize1, queriesAtPageSize20,
                "SubsystemRepositoryV2.findActiveForList query count must not grow with page size");
    }

    /**
     * The V2 service list is served by three calls: a count, a page of service-code aggregates,
     * and a single batch fetch of every active version row keyed by {@code (subsystemId,
     * serviceCode)} for that page. All three are single SQL statements regardless of how many
     * aggregate rows the page holds, so the total is exactly 3 at any page size — the design's
     * replacement for what would otherwise be one version query per aggregate row (N+1).
     */
    @Test
    void servicesListSequenceQueryCountIsExactlyThreeAndInvariantWithPageSize() {
        entityManager.clear();
        stats.clear();
        long queriesAtPageSize1 = runServicesListSequence(PageRequest.of(0, 1));

        entityManager.clear();
        stats.clear();
        long queriesAtPageSize20 = runServicesListSequence(PageRequest.of(0, 20));

        assertEquals(3, queriesAtPageSize1,
                "services list sequence must be exactly count + aggregates + version-rows-for-keys");
        assertEquals(queriesAtPageSize1, queriesAtPageSize20,
                "services list sequence query count must not grow with page size");
    }

    private long runServicesListSequence(PageRequest pageRequest) {
        long count = serviceRepository.countActiveAggregatesForList("TEST", null, null);
        assertEquals(3, count, "SS1 has 3 distinct active service codes: svcA, svcB, svcF");

        List<ServiceAggregateRow> aggregates =
                serviceRepository.findActiveAggregatesForList("TEST", null, null, pageRequest);
        assertFalse(aggregates.isEmpty());

        Set<Long> subsystemIds = aggregates.stream()
                .map(ServiceAggregateRow::getSubsystemId)
                .collect(Collectors.toSet());
        Set<String> serviceCodes = aggregates.stream()
                .map(ServiceAggregateRow::getServiceCode)
                .collect(Collectors.toSet());
        serviceRepository.findActiveVersionRowsForKeys(subsystemIds, serviceCodes);

        return stats.getPrepareStatementCount();
    }

    /**
     * {@code searchUnion} carries its exact total via {@code COUNT(*) OVER ()} on every row, so a
     * single native statement serves both the page and the total — 1 statement, regardless of
     * result-set size (proved here by pinning the same count at a 1-row and a 10-row page over a
     * query matching all three active service aggregates).
     */
    @Test
    void searchQueryCountIsExactlyOneAndInvariantWithResultSetSize() {
        entityManager.clear();
        stats.clear();
        List<Object[]> smallPage = searchRepository.searchUnion("%svc%", 1, 0);
        long queriesSmallPage = stats.getPrepareStatementCount();

        entityManager.clear();
        stats.clear();
        List<Object[]> largePage = searchRepository.searchUnion("%svc%", 10, 0);
        long queriesLargePage = stats.getPrepareStatementCount();

        assertEquals(1, smallPage.size());
        assertEquals(3, largePage.size(), "svcA, svcB, svcF all match %svc%");
        assertEquals(3L, ((Number) smallPage.get(0)[10]).longValue(),
                "total_count must be the full match count even on a 1-row page");
        assertEquals(1, queriesSmallPage, "searchUnion must be a single statement carrying its own total");
        assertEquals(queriesSmallPage, queriesLargePage,
                "search query count must not grow with result-set size");
    }

    @Test
    void statisticsQueryIsExactlyOneStatement() {
        entityManager.clear();
        stats.clear();
        List<Object[]> rows = reportsRepository.countServicesPerDay(
                java.time.LocalDate.of(2025, 1, 1), java.time.LocalDate.of(2025, 3, 31));
        assertEquals(1, stats.getPrepareStatementCount(), "statistics must be a single SQL statement");
        assertFalse(rows.isEmpty());
    }

    @Test
    void changeLogDayPageIsExactlyOneStatement() {
        entityManager.clear();
        stats.clear();
        reportsRepository.findChangeLogDayPage(
                java.time.LocalDateTime.of(2025, 1, 1, 0, 0),
                java.time.LocalDateTime.of(2025, 3, 31, 0, 0), 10, 0);
        assertEquals(1, stats.getPrepareStatementCount(), "day-page query must be a single statement");
    }
}
