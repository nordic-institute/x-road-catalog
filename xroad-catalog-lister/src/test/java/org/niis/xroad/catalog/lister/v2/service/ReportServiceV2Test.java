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
import org.niis.xroad.catalog.lister.v2.dto.ChangeLogDayDto;
import org.niis.xroad.catalog.lister.v2.dto.ChangeLogServiceItemDto;
import org.niis.xroad.catalog.lister.v2.dto.ServiceStatisticsRowDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@TestPropertySource(properties = {"xroad-catalog.shared-params-file=src/test/resources/shared-params-dev-cs.xml"})
@ActiveProfiles({"test", "general-testdata"})
public class ReportServiceV2Test {

    @Autowired
    private ReportServiceV2 reportService;

    @Test
    public void testServiceStatisticsDailySnapshots() {
        // All general-testdata services are created on 2016-01-01 00:00:00+02.
        // A 3-day window starting at 2016-01-01 must produce exactly 3 rows in order.
        LocalDate since = LocalDate.of(2016, 1, 1);
        LocalDate until = LocalDate.of(2016, 1, 4);
        List<ServiceStatisticsRowDto> rows = reportService.serviceStatistics(since, until);
        assertEquals(3, rows.size());
        assertEquals(LocalDate.of(2016, 1, 1), rows.get(0).getDate());
        assertEquals(LocalDate.of(2016, 1, 2), rows.get(1).getDate());
        assertEquals(LocalDate.of(2016, 1, 3), rows.get(2).getDate());

        ServiceStatisticsRowDto first = rows.get(0);
        assertFalse(first.getSoapServices() == 0 && first.getRestServices() == 0 && first.getOpenApiServices() == 0,
                "fixture has services at end of 2016-01-01");
    }

    @Test
    public void testServiceStatisticsRejectsSinceEqualUntil() {
        LocalDate day = LocalDate.of(2016, 1, 1);
        assertThrows(IllegalArgumentException.class, () -> reportService.serviceStatistics(day, day));
    }

    @Test
    public void testServiceStatisticsRejectsSinceAfterUntil() {
        LocalDate since = LocalDate.of(2016, 1, 5);
        LocalDate until = LocalDate.of(2016, 1, 1);
        assertThrows(IllegalArgumentException.class, () -> reportService.serviceStatistics(since, until));
    }

    @Test
    public void testServiceStatisticsEnforcesMaxRange() {
        // 91 days — exceeds the 90-day cap.
        LocalDate since = LocalDate.of(2025, 1, 1);
        LocalDate until = LocalDate.of(2025, 4, 2);
        assertThrows(IllegalArgumentException.class, () -> reportService.serviceStatistics(since, until));
    }

    @Test
    public void testServiceStatisticsAcceptsExactlyMaxRange() {
        // 90 days — right at the cap.
        LocalDate since = LocalDate.of(2025, 1, 1);
        LocalDate until = LocalDate.of(2025, 4, 1);
        List<ServiceStatisticsRowDto> rows = reportService.serviceStatistics(since, until);
        assertEquals(90, rows.size());
    }

    @Test
    public void testChangeLogCapturesCreationWindow() {
        // Fixture has many entities created on 2016-01-01. A 7-day window around that date
        // must surface members on the first day.
        LocalDate since = LocalDate.of(2016, 1, 1);
        LocalDate until = LocalDate.of(2016, 1, 8);
        Page<ChangeLogDayDto> page = reportService.changeLog(since, until, PageRequest.of(0, 100));
        Set<LocalDate> dates = page.getContent().stream()
                .map(ChangeLogDayDto::getDate)
                .collect(Collectors.toSet());
        assertTrue(dates.contains(LocalDate.of(2016, 1, 1)), "2016-01-01 must be present");
        ChangeLogDayDto day1 = page.getContent().stream()
                .filter(d -> d.getDate().equals(LocalDate.of(2016, 1, 1)))
                .findFirst()
                .orElseThrow();
        assertTrue(day1.getCreated().getMembers().getCount() > 0, "members created on 2016-01-01");
    }

    @Test
    public void testChangeLogCapturesModificationWindow() {
        // Fixture: subsystems id 5/9, services id 3/5, members id 7/8 have changed = 2017-01-02.
        // Member id 8 was also removed on 2017-01-02.
        LocalDate since = LocalDate.of(2017, 1, 1);
        LocalDate until = LocalDate.of(2017, 1, 8);
        Page<ChangeLogDayDto> page = reportService.changeLog(since, until, PageRequest.of(0, 100));
        Set<LocalDate> dates = page.getContent().stream()
                .map(ChangeLogDayDto::getDate)
                .collect(Collectors.toSet());
        assertTrue(dates.contains(LocalDate.of(2017, 1, 2)), "2017-01-02 must be present");
        ChangeLogDayDto day = page.getContent().stream()
                .filter(d -> d.getDate().equals(LocalDate.of(2017, 1, 2)))
                .findFirst()
                .orElseThrow();
        assertTrue(day.getRemoved().getMembers().getCount() > 0, "member removed on 2017-01-02");
    }

    @Test
    public void testChangeLogOmitsDaysWithZeroChanges() {
        // 2018 has no change events in the fixture.
        LocalDate since = LocalDate.of(2018, 1, 1);
        LocalDate until = LocalDate.of(2018, 1, 8);
        Page<ChangeLogDayDto> page = reportService.changeLog(since, until, PageRequest.of(0, 100));
        assertTrue(page.isEmpty(), "no-change days must be omitted");
    }

    @Test
    public void testChangeLogEnforcesMaxRange() {
        // > 90 days — must be rejected by validateReportRange.
        LocalDate since = LocalDate.of(2025, 1, 1);
        LocalDate until = LocalDate.of(2025, 6, 1);
        assertThrows(IllegalArgumentException.class,
                () -> reportService.changeLog(since, until, PageRequest.of(0, 10)));
    }

    /**
     * Task 1.5 regression: a service whose only WSDL row is removed must classify as REST — not
     * SOAP — in changeLog output. The Task 1.5 fixture service 33 (svc_removed_wsdl_only) is
     * created 2016-01-01 with a removed WSDL row.
     */
    @Test
    public void testChangeLogClassifiesRemovedOnlyWsdlAsRest() {
        LocalDate since = LocalDate.of(2016, 1, 1);
        LocalDate until = LocalDate.of(2016, 1, 8);
        Page<ChangeLogDayDto> page = reportService.changeLog(since, until, PageRequest.of(0, 100));

        ChangeLogServiceItemDto match = page.getContent().stream()
                .flatMap(d -> d.getCreated().getServices().getItems().stream())
                .filter(item -> "svc_removed_wsdl_only".equals(item.getServiceCode()))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "Fixture service svc_removed_wsdl_only must appear in the created bucket"));
        assertEquals("REST", match.getServiceType(),
                "service whose only WSDL is removed must classify as REST, not SOAP");
    }
}
