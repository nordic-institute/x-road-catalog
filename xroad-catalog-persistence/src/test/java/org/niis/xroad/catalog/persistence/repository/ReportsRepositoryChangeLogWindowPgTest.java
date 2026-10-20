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

import org.niis.xroad.catalog.persistence.v2.repository.ReportsRepository;
import org.niis.xroad.catalog.persistence.v2.repository.projection.ChangeLogDayRow;
import org.niis.xroad.catalog.persistence.v2.repository.projection.MemberChangeRow;
import org.niis.xroad.catalog.persistence.v2.repository.projection.ServiceChangeRow;
import org.niis.xroad.catalog.persistence.v2.repository.projection.SubsystemChangeRow;
import org.niis.xroad.catalog.persistence.testsupport.PostgresTestBase;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.jdbc.Sql;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the change-log "modified between" queries over a multi-day window — the shape the report
 * service issues (one query per report range, bucketed by day in Java).
 * {@code pg/changelog-window-padding.sql} supplies rows where {@code changed} differs from both
 * {@code created} and {@code removed}, all in May 2025 so other tests' expected counts stay
 * untouched. Window used throughout: {@code [2025-05-01T00:00, 2025-05-10T00:00)}.
 */
@SpringBootTest
@EntityScan(basePackages = {
        "org.niis.xroad.catalog.persistence.entity",
        "org.niis.xroad.catalog.persistence.v2.entity"
})
@Sql(scripts = {"classpath:pg/v2-fixture.sql", "classpath:pg/changelog-window-padding.sql"},
        executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class ReportsRepositoryChangeLogWindowPgTest extends PostgresTestBase {

    private static final LocalDateTime WINDOW_START = LocalDateTime.of(2025, 5, 1, 0, 0);
    private static final LocalDateTime WINDOW_END = LocalDateTime.of(2025, 5, 10, 0, 0);

    @Autowired
    private ReportsRepository reportsRepository;

    @Autowired
    private DenormalizationRepository denormalizationRepository;

    @BeforeEach
    void recomputeDenormalizedColumns() {
        denormalizationRepository.recomputeMemberIsProvider();
        denormalizationRepository.recomputeServiceType();
    }

    @Test
    void createdThenModifiedInWindowAppearsInBothCreatedAndModifiedBucketsForMember() {
        List<MemberChangeRow> created = reportsRepository.findMembersCreatedBetween(WINDOW_START, WINDOW_END);
        List<MemberChangeRow> modified = reportsRepository.findMembersModifiedBetween(WINDOW_START, WINDOW_END);

        assertTrue(created.stream().anyMatch(r -> "M10".equals(r.getMemberCode())),
                "member created inside the window must be reported as created");
        assertTrue(modified.stream().anyMatch(r -> "M10".equals(r.getMemberCode())),
                "member created AND genuinely modified inside the window must also be reported as modified");
    }

    @Test
    void createdThenModifiedInWindowAppearsInBothBucketsForSubsystem() {
        List<SubsystemChangeRow> created = reportsRepository.findSubsystemsCreatedBetween(WINDOW_START, WINDOW_END);
        List<SubsystemChangeRow> modified = reportsRepository.findSubsystemsModifiedBetween(WINDOW_START, WINDOW_END);

        assertTrue(created.stream().anyMatch(r -> "SS10".equals(r.getSubsystemCode())),
                "subsystem created inside the window must be reported as created");
        assertTrue(modified.stream().anyMatch(r -> "SS10".equals(r.getSubsystemCode())),
                "subsystem created AND genuinely modified inside the window must also be reported as modified");
    }

    @Test
    void createdThenModifiedInWindowAppearsInBothBucketsForService() {
        List<ServiceChangeRow> created = reportsRepository.findServicesCreatedBetween(WINDOW_START, WINDOW_END);
        List<ServiceChangeRow> modified = reportsRepository.findServicesModifiedBetween(WINDOW_START, WINDOW_END);

        assertTrue(created.stream().anyMatch(r -> "svcH".equals(r.getServiceCode())),
                "service created inside the window must be reported as created");
        assertTrue(modified.stream().anyMatch(r -> "svcH".equals(r.getServiceCode())),
                "service created AND genuinely modified inside the window must also be reported as modified");
    }

    @Test
    void createdOnlyInWindowIsNotReportedAsModified() {
        List<MemberChangeRow> created = reportsRepository.findMembersCreatedBetween(WINDOW_START, WINDOW_END);
        List<MemberChangeRow> modified = reportsRepository.findMembersModifiedBetween(WINDOW_START, WINDOW_END);

        assertTrue(created.stream().anyMatch(r -> "M11".equals(r.getMemberCode())),
                "member created (and never since modified) inside the window must be reported as created");
        assertTrue(modified.stream().noneMatch(r -> "M11".equals(r.getMemberCode())),
                "a create-only row (changed == created) must never be reported as modified");
    }

    @Test
    void removedInWindowIsReportedAsRemovedOnlyNotCreatedOrModified() {
        List<MemberChangeRow> created = reportsRepository.findMembersCreatedBetween(WINDOW_START, WINDOW_END);
        List<MemberChangeRow> modified = reportsRepository.findMembersModifiedBetween(WINDOW_START, WINDOW_END);
        List<MemberChangeRow> removed = reportsRepository.findMembersRemovedBetween(WINDOW_START, WINDOW_END);

        assertTrue(removed.stream().anyMatch(r -> "M12".equals(r.getMemberCode())),
                "member removed inside the window must be reported as removed");
        assertTrue(modified.stream().noneMatch(r -> "M12".equals(r.getMemberCode())),
                "a remove row (changed == removed) must never be reported as modified");
        assertTrue(created.stream().noneMatch(r -> "M12".equals(r.getMemberCode())),
                "member created well before the window must not be reported as created");
    }

    @Test
    void modifiedOnlyWhenCreateWasBeforeWindowIsReportedAsModifiedOnly() {
        List<MemberChangeRow> created = reportsRepository.findMembersCreatedBetween(WINDOW_START, WINDOW_END);
        List<MemberChangeRow> modified = reportsRepository.findMembersModifiedBetween(WINDOW_START, WINDOW_END);
        List<MemberChangeRow> removed = reportsRepository.findMembersRemovedBetween(WINDOW_START, WINDOW_END);

        assertTrue(modified.stream().anyMatch(r -> "M13".equals(r.getMemberCode())),
                "member modified inside the window, created well before it, must be reported as modified");
        assertTrue(created.stream().noneMatch(r -> "M13".equals(r.getMemberCode())),
                "member created before the window must not be reported as created");
        assertTrue(removed.stream().noneMatch(r -> "M13".equals(r.getMemberCode())),
                "member never removed must not be reported as removed");
    }

    @Test
    void memberModifiedBucketContainsExactlyTheGenuinelyModifiedMembers() {
        List<MemberChangeRow> modified = reportsRepository.findMembersModifiedBetween(WINDOW_START, WINDOW_END);

        assertEquals(2, modified.size(), "M10 (created+modified) and M13 (modified only) qualify; "
                + "M11 (create-only) and M12 (remove-only) must be excluded");
    }

    @Test
    void dayPageReturnsOnlyDaysWithEventsWithTotalAndOrdering() {
        List<ChangeLogDayRow> page = reportsRepository.findChangeLogDayPage(WINDOW_START, WINDOW_END, 100, 0);

        assertFalse(page.isEmpty());
        long totalDays = page.get(0).getTotalDays();
        assertEquals(page.size(), totalDays,
                "page size 100 covers the whole window, so the page must hold every event day");
        List<LocalDate> days = page.stream().map(ChangeLogDayRow::getDay).toList();
        assertEquals(days.stream().sorted().toList(), days, "days must be ascending");
        assertEquals(totalDays, reportsRepository.countChangeLogDays(WINDOW_START, WINDOW_END));

        List<ChangeLogDayRow> firstDayOnly = reportsRepository.findChangeLogDayPage(WINDOW_START, WINDOW_END, 1, 0);
        assertEquals(1, firstDayOnly.size());
        assertEquals(days.get(0), firstDayOnly.get(0).getDay());
        assertEquals(totalDays, firstDayOnly.get(0).getTotalDays(),
                "total_days must be the whole-window day count even on a 1-day page");
    }
}
