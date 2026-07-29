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
import org.niis.xroad.catalog.persistence.v2.repository.projection.MemberChangeRow;
import org.niis.xroad.catalog.persistence.v2.repository.projection.ServiceChangeRow;
import org.niis.xroad.catalog.persistence.v2.repository.projection.ServiceCountRow;
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
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Ground truth: {@code pg/v2-fixture.sql}. Service creation dates: 21=01-03, 22=01-04, 23=01-05,
 * 24=01-06 (removed 04-01), 25=01-07, 26=01-08, 27=01-09; recomputed service_type: 21=SOAP,
 * 22=OPENAPI, 23=REST, 24/25/26=UNKNOWN, 27=SOAP (wsdl+open_api anomaly). Subsystem SS2 (id 12) and
 * service 24 (svcC) share the create/modify/remove guard fixtures used by the mutual-exclusivity
 * assertions below.
 */
@SpringBootTest
@EntityScan(basePackages = {
        "org.niis.xroad.catalog.persistence.entity",
        "org.niis.xroad.catalog.persistence.v2.entity"
})
@Sql(scripts = "classpath:pg/v2-fixture.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class ReportsRepositoryPgTest extends PostgresTestBase {

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
    void countServicesPerDaySingleDaySeesAllActiveAndNotYetRemovedServices() {
        Map<String, Long> counts = countsForSingleDay(
                reportsRepository.countServicesPerDay(LocalDate.of(2025, 1, 9), LocalDate.of(2025, 1, 10)),
                LocalDate.of(2025, 1, 9));

        assertEquals(2L, counts.get("SOAP"));
        assertEquals(1L, counts.get("OPENAPI"));
        assertEquals(1L, counts.get("REST"));
        assertEquals(3L, counts.get("UNKNOWN"));
    }

    @Test
    void countServicesPerDayExcludesServiceRemovedBeforeTheDay() {
        Map<String, Long> counts = countsForSingleDay(
                reportsRepository.countServicesPerDay(LocalDate.of(2025, 4, 2), LocalDate.of(2025, 4, 3)),
                LocalDate.of(2025, 4, 2));

        assertEquals(2L, counts.get("SOAP"));
        assertEquals(1L, counts.get("OPENAPI"));
        assertEquals(1L, counts.get("REST"));
        assertEquals(2L, counts.get("UNKNOWN"));
    }

    @Test
    void countServicesPerDayReturnsZeroCountsForDaysBeforeAnyService() {
        List<ServiceCountRow> rows = reportsRepository.countServicesPerDay(
                LocalDate.of(2024, 12, 1), LocalDate.of(2024, 12, 2));

        assertFalse(rows.isEmpty(), "each service_type present in the table yields a zero row");
        for (ServiceCountRow row : rows) {
            assertEquals(LocalDate.of(2024, 12, 1), row.getDay());
            assertEquals(0L, row.getCount());
        }
    }

    @Test
    void countServicesPerDayAppliesRemovalOnTheRemovalDayAcrossAMultiDayWindow() {
        List<ServiceCountRow> rows = reportsRepository.countServicesPerDay(
                LocalDate.of(2025, 3, 31), LocalDate.of(2025, 4, 3));

        assertEquals(3L, countFor(rows, LocalDate.of(2025, 3, 31), "UNKNOWN"));
        assertEquals(2L, countFor(rows, LocalDate.of(2025, 4, 1), "UNKNOWN"),
                "a service removed on day D no longer counts on day D");
        assertEquals(2L, countFor(rows, LocalDate.of(2025, 4, 2), "UNKNOWN"));
        assertEquals(2L, countFor(rows, LocalDate.of(2025, 4, 2), "SOAP"));
        assertEquals(1L, countFor(rows, LocalDate.of(2025, 4, 2), "REST"));
    }

    private static long countFor(List<ServiceCountRow> rows, LocalDate day, String type) {
        return rows.stream()
                .filter(r -> day.equals(r.getDay()) && type.equals(r.getServiceType()))
                .mapToLong(ServiceCountRow::getCount)
                .sum();
    }

    @Test
    void countServicesPerDayHalfOpenBoundaryIncludesSameDayCreation() {
        Map<String, Long> counts = countsForSingleDay(
                reportsRepository.countServicesPerDay(LocalDate.of(2025, 1, 3), LocalDate.of(2025, 1, 4)),
                LocalDate.of(2025, 1, 3));

        assertEquals(1L, counts.get("SOAP"));
    }

    @Test
    void findMembersRemovedBetweenReturnsM3WithRemovedEventTime() {
        List<MemberChangeRow> rows = reportsRepository.findMembersRemovedBetween(
                LocalDateTime.of(2025, 3, 1, 0, 0), LocalDateTime.of(2025, 3, 2, 0, 0));

        assertEquals(1, rows.size());
        assertEquals("M3", rows.get(0).getMemberCode());
        assertEquals(LocalDateTime.of(2025, 3, 1, 10, 0), rows.get(0).getEventTime());
    }

    @Test
    void findSubsystemsModifiedAndRemovedAreMutuallyExclusiveForSs2() {
        List<SubsystemChangeRow> modified = reportsRepository.findSubsystemsModifiedBetween(
                LocalDateTime.of(2025, 2, 15, 0, 0), LocalDateTime.of(2025, 2, 16, 0, 0));
        assertTrue(modified.isEmpty());

        List<SubsystemChangeRow> removed = reportsRepository.findSubsystemsRemovedBetween(
                LocalDateTime.of(2025, 2, 15, 0, 0), LocalDateTime.of(2025, 2, 16, 0, 0));
        assertEquals(1, removed.size());
        assertEquals("SS2", removed.get(0).getSubsystemCode());
    }

    @Test
    void findServicesCreatedBetweenReturnsSevenRowsWithMemberNameAndServiceType() {
        List<ServiceChangeRow> rows = reportsRepository.findServicesCreatedBetween(
                LocalDateTime.of(2025, 1, 3, 0, 0), LocalDateTime.of(2025, 1, 10, 0, 0));

        assertEquals(7, rows.size());
        assertTrue(rows.stream().allMatch(r -> r.getMemberName() != null && !r.getMemberName().isBlank()));
        assertTrue(rows.stream().allMatch(r -> r.getServiceType() != null && !r.getServiceType().isBlank()));
    }

    private static Map<String, Long> countsForSingleDay(List<ServiceCountRow> rows, LocalDate expectedDay) {
        return rows.stream()
                .peek(row -> assertEquals(expectedDay, row.getDay()))
                .collect(Collectors.toMap(ServiceCountRow::getServiceType, ServiceCountRow::getCount));
    }
}
