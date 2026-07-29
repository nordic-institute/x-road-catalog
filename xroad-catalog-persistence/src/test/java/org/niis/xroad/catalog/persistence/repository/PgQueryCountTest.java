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

import org.niis.xroad.catalog.persistence.v2.repository.ErrorLogRepository;
import org.niis.xroad.catalog.persistence.v2.repository.MemberRepository;
import org.niis.xroad.catalog.persistence.v2.repository.ReportsRepository;
import org.niis.xroad.catalog.persistence.v2.repository.SearchRepository;
import org.niis.xroad.catalog.persistence.v2.repository.ServiceRepository;
import org.niis.xroad.catalog.persistence.v2.repository.SubsystemRepository;
import org.niis.xroad.catalog.persistence.v2.repository.projection.SearchHitRow;
import org.niis.xroad.catalog.persistence.v2.repository.projection.ServiceAggregateRow;
import org.niis.xroad.catalog.persistence.v2.repository.projection.ServiceCountRow;
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
 * Pins the exact Hibernate prepared-statement count of each V2 list/search entry point and proves
 * it is invariant to page/result-set size, so a reintroduced N+1 or a lazily walked association
 * fails fast. Each measured block clears the persistence context and the {@link Statistics}
 * snapshot first, so fixture loading and the denormalization recompute cannot leak into a delta.
 */
@SpringBootTest
@EntityScan(basePackages = {
        "org.niis.xroad.catalog.persistence.entity",
        "org.niis.xroad.catalog.persistence.v2.entity"
})
@Sql(scripts = {"classpath:pg/v2-fixture.sql", "classpath:pg/query-count-padding.sql", "classpath:pg/error-log-padding.sql"},
        executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class PgQueryCountTest extends PostgresTestBase {

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private SubsystemRepository subsystemRepository;

    @Autowired
    private ServiceRepository serviceRepository;

    @Autowired
    private SearchRepository searchRepository;

    @Autowired
    private ReportsRepository reportsRepository;

    @Autowired
    private ErrorLogRepository errorLogRepository;

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
     * Rows + count = 2 statements at every page size; the correlated select-list subqueries compile
     * into the single row statement. {@code pg/query-count-padding.sql} keeps the result set (24)
     * larger than page size 20 so {@code PageableExecutionUtils} cannot short-circuit the count.
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
                "MemberRepository.findActiveForList query count must not grow with page size");
    }

    /**
     * Rows + count = 2 statements at every page size; padding keeps the result set (22) larger
     * than page size 20 so the count query cannot be short-circuited.
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
                "SubsystemRepository.findActiveForList query count must not grow with page size");
    }

    /**
     * The service list sequence is count + aggregate page + one batch version fetch for the page's
     * keys — 3 statements at any page size, never one version query per aggregate row.
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
     * {@code searchUnion} carries its total via {@code COUNT(*) OVER ()}, so one native statement
     * serves both page and total regardless of result-set size.
     */
    @Test
    void searchQueryCountIsExactlyOneAndInvariantWithResultSetSize() {
        entityManager.clear();
        stats.clear();
        List<SearchHitRow> smallPage = searchRepository.searchUnion("%svc%", 1, 0);
        long queriesSmallPage = stats.getPrepareStatementCount();

        entityManager.clear();
        stats.clear();
        List<SearchHitRow> largePage = searchRepository.searchUnion("%svc%", 10, 0);
        long queriesLargePage = stats.getPrepareStatementCount();

        assertEquals(1, smallPage.size());
        assertEquals(3, largePage.size(), "svcA, svcB, svcF all match %svc%");
        assertEquals(3L, smallPage.get(0).getTotalCount(),
                "total_count must be the full match count even on a 1-row page");
        assertEquals(1, queriesSmallPage, "searchUnion must be a single statement carrying its own total");
        assertEquals(queriesSmallPage, queriesLargePage,
                "search query count must not grow with result-set size");
    }

    @Test
    void statisticsQueryIsExactlyOneStatement() {
        entityManager.clear();
        stats.clear();
        List<ServiceCountRow> rows = reportsRepository.countServicesPerDay(
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

    /**
     * Rows + count = 2 statements at every page size; {@code pg/error-log-padding.sql} (25 rows)
     * keeps the result set larger than page size 20 so the count cannot be short-circuited.
     */
    @Test
    void errorsRangeQueryCountIsExactlyTwoAtBothPageSizes() {
        java.time.LocalDateTime since = java.time.LocalDateTime.of(2025, 5, 1, 0, 0);
        java.time.LocalDateTime until = java.time.LocalDateTime.of(2025, 5, 10, 0, 0);

        entityManager.clear();
        stats.clear();
        Page<?> page1 = errorLogRepository.findAnyInRange(since, until, PageRequest.of(0, 1));
        long queriesAtPageSize1 = stats.getPrepareStatementCount();

        entityManager.clear();
        stats.clear();
        Page<?> page20 = errorLogRepository.findAnyInRange(since, until, PageRequest.of(0, 20));
        long queriesAtPageSize20 = stats.getPrepareStatementCount();

        assertEquals(25, page1.getTotalElements());
        assertEquals(20, page20.getContent().size());
        assertEquals(2, queriesAtPageSize1, "errors range query must be exactly rows + count");
        assertEquals(queriesAtPageSize1, queriesAtPageSize20,
                "errors query count must not grow with page size");
    }
}
