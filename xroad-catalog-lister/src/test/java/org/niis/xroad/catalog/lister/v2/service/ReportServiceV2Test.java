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
import org.niis.xroad.catalog.lister.v2.dto.ChangeLogDayDto;
import org.niis.xroad.catalog.lister.v2.dto.ChangeLogServiceItemDto;
import org.niis.xroad.catalog.lister.v2.dto.ChangeLogSubsystemItemDto;
import org.niis.xroad.catalog.lister.v2.dto.ServiceStatisticsRowDto;
import org.niis.xroad.catalog.persistence.v2.repository.ReportsRepository;
import org.niis.xroad.catalog.persistence.v2.repository.projection.ChangeLogDayRow;
import org.niis.xroad.catalog.persistence.v2.repository.projection.MemberChangeRow;
import org.niis.xroad.catalog.persistence.v2.repository.projection.ServiceChangeRow;
import org.niis.xroad.catalog.persistence.v2.repository.projection.ServiceCountRow;
import org.niis.xroad.catalog.persistence.v2.repository.projection.SubsystemChangeRow;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportServiceV2Test {

    @Mock
    private ReportsRepository reportsRepository;

    private ReportServiceV2 reportService;

    @BeforeEach
    void setUp() {
        reportService = new ReportServiceV2(reportsRepository);
    }

    @Test
    void testServiceStatisticsFoldsRowsByDayAndType() {
        // Rows deliberately out of chronological order to prove the TreeMap fold sorts by day.
        when(reportsRepository.countServicesPerDay(any(), any())).thenReturn(statsRows(
                row(LocalDate.of(2016, 1, 2), "SOAP", 4L),
                row(LocalDate.of(2016, 1, 1), "SOAP", 2L),
                row(LocalDate.of(2016, 1, 1), "OPENAPI", 1L),
                row(LocalDate.of(2016, 1, 1), "REST", 3L)));

        List<ServiceStatisticsRowDto> rows = reportService.serviceStatistics(
                LocalDate.of(2016, 1, 1), LocalDate.of(2016, 1, 3));

        assertEquals(2, rows.size());
        assertEquals(LocalDate.of(2016, 1, 1), rows.get(0).getDate());
        assertEquals(2, rows.get(0).getSoapServices());
        assertEquals(1, rows.get(0).getOpenApiServices());
        assertEquals(3, rows.get(0).getRestServices());
        assertEquals(LocalDate.of(2016, 1, 2), rows.get(1).getDate());
        assertEquals(4, rows.get(1).getSoapServices());
        assertEquals(0, rows.get(1).getRestServices());
    }

    @Test
    void testServiceStatisticsZeroDayWithEmptyResultYieldsAllZeroCounts() {
        // An empty service table yields no rows at all from the delta SQL; the pre-fill must
        // still emit the day, with every column at zero.
        when(reportsRepository.countServicesPerDay(any(), any())).thenReturn(List.of());

        List<ServiceStatisticsRowDto> rows = reportService.serviceStatistics(
                LocalDate.of(2024, 12, 1), LocalDate.of(2024, 12, 2));

        assertEquals(1, rows.size());
        ServiceStatisticsRowDto day = rows.get(0);
        assertEquals(LocalDate.of(2024, 12, 1), day.getDate());
        assertEquals(0, day.getSoapServices());
        assertEquals(0, day.getOpenApiServices());
        assertEquals(0, day.getRestServices());
    }

    @Test
    void testServiceStatisticsExcludesUnclassifiedFromEveryBucket() {
        when(reportsRepository.countServicesPerDay(any(), any())).thenReturn(statsRows(
                row(LocalDate.of(2016, 1, 1), "UNKNOWN", 5L)));

        List<ServiceStatisticsRowDto> rows = reportService.serviceStatistics(
                LocalDate.of(2016, 1, 1), LocalDate.of(2016, 1, 2));

        ServiceStatisticsRowDto day = rows.get(0);
        assertEquals(0, day.getRestServices(), "not-yet-classified services must not inflate the REST count");
        assertEquals(0, day.getSoapServices());
        assertEquals(0, day.getOpenApiServices());
    }

    @Test
    void testServiceStatisticsExcludesUnrecognisedServiceTypes() {
        when(reportsRepository.countServicesPerDay(any(), any())).thenReturn(statsRows(
                row(LocalDate.of(2016, 1, 1), "SOMETHING_ELSE", 5L)));

        List<ServiceStatisticsRowDto> rows = reportService.serviceStatistics(
                LocalDate.of(2016, 1, 1), LocalDate.of(2016, 1, 2));

        assertEquals(0, rows.get(0).getRestServices(), "an unrecognised type is not silently counted as REST");
    }

    @Test
    void testServiceStatisticsAcceptsSinceEqualUntil() {
        // Canonical DateTimeUtil semantics: since == until is an empty window, not an error.
        LocalDate day = LocalDate.of(2016, 1, 1);
        List<ServiceStatisticsRowDto> rows = reportService.serviceStatistics(day, day);
        assertTrue(rows.isEmpty());
    }

    @Test
    void testServiceStatisticsRejectsSinceAfterUntil() {
        LocalDate since = LocalDate.of(2016, 1, 5);
        LocalDate until = LocalDate.of(2016, 1, 1);
        assertThrows(IllegalArgumentException.class, () -> reportService.serviceStatistics(since, until));
    }

    @Test
    void testServiceStatisticsEnforcesMaxRange() {
        // 91 days — exceeds the 90-day cap.
        LocalDate since = LocalDate.of(2025, 1, 1);
        LocalDate until = LocalDate.of(2025, 4, 2);
        assertThrows(IllegalArgumentException.class, () -> reportService.serviceStatistics(since, until));
    }

    @Test
    void testServiceStatisticsAcceptsExactlyMaxRange() {
        // 90 days — right at the cap; DateTimeUtil.validateDateRange must not reject the boundary.
        // The pre-fill still emits one zeroed DTO per day for the mocked empty result.
        when(reportsRepository.countServicesPerDay(any(), any())).thenReturn(List.of());
        LocalDate since = LocalDate.of(2025, 1, 1);
        LocalDate until = LocalDate.of(2025, 4, 1);
        List<ServiceStatisticsRowDto> rows = reportService.serviceStatistics(since, until);
        assertEquals(90, rows.size());
        assertTrue(rows.stream().allMatch(r -> r.getSoapServices() == 0
                && r.getOpenApiServices() == 0 && r.getRestServices() == 0));
    }

    @Test
    void testChangeLogCapturesCreationWindow() {
        stubEmptyChangeWindows();
        LocalDate createdDay = LocalDate.of(2016, 1, 1);
        LocalDateTime day1EventTime = createdDay.atTime(10, 0);
        stubDayPage(dayRow(createdDay, 1));
        when(reportsRepository.findMembersCreatedBetween(any(), any())).thenReturn(List.of(
                memberRow("PUB", "1", "Member One", day1EventTime)));

        Page<ChangeLogDayDto> page = reportService.changeLog(
                LocalDate.of(2016, 1, 1), LocalDate.of(2016, 1, 8), PageRequest.of(0, 100));

        ChangeLogDayDto day1 = page.getContent().stream()
                .filter(d -> d.getDate().equals(LocalDate.of(2016, 1, 1)))
                .findFirst()
                .orElseThrow(() -> new AssertionError("2016-01-01 must be present"));
        assertTrue(day1.getCreated().getMembers().getCount() > 0, "members created on 2016-01-01");
    }

    @Test
    void testChangeLogCapturesRemovalWindow() {
        stubEmptyChangeWindows();
        LocalDate day2 = LocalDate.of(2017, 1, 2);
        LocalDateTime day2EventTime = day2.atTime(9, 30);
        stubDayPage(dayRow(day2, 1));
        when(reportsRepository.findMembersRemovedBetween(any(), any())).thenReturn(List.of(
                memberRow("COM", "8", "Removed Member", day2EventTime)));

        Page<ChangeLogDayDto> page = reportService.changeLog(
                LocalDate.of(2017, 1, 1), LocalDate.of(2017, 1, 8), PageRequest.of(0, 100));

        ChangeLogDayDto day = page.getContent().stream()
                .filter(d -> d.getDate().equals(LocalDate.of(2017, 1, 2)))
                .findFirst()
                .orElseThrow(() -> new AssertionError("2017-01-02 must be present"));
        assertTrue(day.getRemoved().getMembers().getCount() > 0, "member removed on 2017-01-02");
    }

    @Test
    void testChangeLogOmitsDaysWithZeroChanges() {
        stubDayPage();

        Page<ChangeLogDayDto> page = reportService.changeLog(
                LocalDate.of(2018, 1, 1), LocalDate.of(2018, 1, 8), PageRequest.of(0, 100));

        assertTrue(page.isEmpty(), "no-change days must be omitted");
    }

    @Test
    void testChangeLogEnforcesMaxRange() {
        LocalDate since = LocalDate.of(2025, 1, 1);
        LocalDate until = LocalDate.of(2025, 6, 1);
        assertThrows(IllegalArgumentException.class,
                () -> reportService.changeLog(since, until, PageRequest.of(0, 10)));
    }

    @Test
    void testChangeLogAcceptsSinceEqualUntil() {
        // Canonical DateTimeUtil semantics: since == until is an empty window, not an error.
        LocalDate day = LocalDate.of(2016, 1, 1);
        Page<ChangeLogDayDto> page = reportService.changeLog(day, day, PageRequest.of(0, 100));
        assertTrue(page.isEmpty());
    }

    @Test
    void testChangeLogPassesThroughServiceTypeAndMemberNameFromRow() {
        stubEmptyChangeWindows();
        LocalDate day = LocalDate.of(2016, 1, 1);
        LocalDateTime eventTime = day.atTime(12, 0);
        stubDayPage(dayRow(day, 1));
        when(reportsRepository.findServicesCreatedBetween(any(), any())).thenReturn(List.of(
                serviceRow("PUB", "14151328", "Nahka-Albert", "subsystem_a1",
                        "svc_removed_wsdl_only", null, "REST", eventTime)));

        Page<ChangeLogDayDto> page = reportService.changeLog(
                LocalDate.of(2016, 1, 1), LocalDate.of(2016, 1, 8), PageRequest.of(0, 100));

        ChangeLogServiceItemDto match = page.getContent().stream()
                .flatMap(d -> d.getCreated().getServices().getItems().stream())
                .filter(item -> "svc_removed_wsdl_only".equals(item.getServiceCode()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("service must appear in the created bucket"));
        assertEquals("REST", match.getServiceType());
        assertEquals("Nahka-Albert", match.getMemberName());
    }

    @Test
    void testChangeLogPassesThroughSubsystemFieldsFromRow() {
        stubEmptyChangeWindows();
        LocalDate day = LocalDate.of(2016, 1, 1);
        LocalDateTime eventTime = day.atTime(8, 0);
        stubDayPage(dayRow(day, 1));
        when(reportsRepository.findSubsystemsCreatedBetween(any(), any())).thenReturn(List.of(
                subsystemRow("PUB", "14151328", "Nahka-Albert", "subsystem_a1", eventTime)));

        Page<ChangeLogDayDto> page = reportService.changeLog(
                LocalDate.of(2016, 1, 1), LocalDate.of(2016, 1, 8), PageRequest.of(0, 100));

        ChangeLogSubsystemItemDto match = page.getContent().stream()
                .flatMap(d -> d.getCreated().getSubsystems().getItems().stream())
                .filter(item -> "subsystem_a1".equals(item.getSubsystemCode()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("subsystem must appear in the created bucket"));
        assertEquals("Nahka-Albert", match.getMemberName());
        assertEquals("PUB", match.getMemberClass());
    }

    @Test
    void testChangeLogContentDaysMatchDayPageAndTotalElementsComesFromTotalDays() {
        stubEmptyChangeWindows();
        LocalDate day1 = LocalDate.of(2019, 1, 1);
        LocalDate day2 = LocalDate.of(2019, 1, 2);
        // total_days (7) deliberately differs from the 2-day page content, proving totalElements is
        // read straight off the stubbed total_days column rather than derived from content size.
        stubDayPage(dayRow(day1, 7), dayRow(day2, 7));
        when(reportsRepository.findMembersCreatedBetween(any(), any())).thenReturn(List.of(
                memberRow("PUB", "m1", "Member 1", day1.atTime(1, 0)),
                memberRow("PUB", "m2", "Member 2", day2.atTime(1, 0))));

        Page<ChangeLogDayDto> page = reportService.changeLog(
                LocalDate.of(2019, 1, 1), LocalDate.of(2019, 1, 10), PageRequest.of(0, 2));

        assertEquals(List.of(day1, day2), page.getContent().stream().map(ChangeLogDayDto::getDate).toList());
        assertEquals(7, page.getTotalElements());
    }

    @Test
    void testChangeLogEmptyDayPageAtOffsetZeroYieldsEmptyPageAndSkipsItemQueries() {
        stubDayPage();

        Page<ChangeLogDayDto> page = reportService.changeLog(
                LocalDate.of(2020, 1, 1), LocalDate.of(2020, 1, 8), PageRequest.of(0, 10));

        assertTrue(page.isEmpty());
        assertEquals(0, page.getTotalElements());
        verify(reportsRepository).findChangeLogDayPage(any(), any(), anyInt(), anyLong());
        verifyNoMoreInteractions(reportsRepository);
    }

    @Test
    void testChangeLogEmptyDayPageAtNonZeroOffsetUsesCountChangeLogDaysFallback() {
        stubDayPage();
        when(reportsRepository.countChangeLogDays(any(), any())).thenReturn(7L);

        Page<ChangeLogDayDto> page = reportService.changeLog(
                LocalDate.of(2020, 1, 1), LocalDate.of(2020, 1, 8), PageRequest.of(1, 10));

        assertTrue(page.isEmpty());
        assertEquals(7, page.getTotalElements());
        verify(reportsRepository).findChangeLogDayPage(any(), any(), anyInt(), anyLong());
        verify(reportsRepository).countChangeLogDays(any(), any());
        verifyNoMoreInteractions(reportsRepository);
    }

    private void stubDayPage(ChangeLogDayRow... dayRows) {
        when(reportsRepository.findChangeLogDayPage(any(), any(), anyInt(), anyLong())).thenReturn(List.of(dayRows));
    }

    private static ChangeLogDayRow dayRow(LocalDate day, long totalDays) {
        return new ChangeLogDayRow() {
            @Override
            public LocalDate getDay() {
                return day;
            }

            @Override
            public long getTotalDays() {
                return totalDays;
            }
        };
    }

    private void stubEmptyChangeWindows() {
        when(reportsRepository.findMembersCreatedBetween(any(), any())).thenReturn(List.of());
        when(reportsRepository.findMembersModifiedBetween(any(), any())).thenReturn(List.of());
        when(reportsRepository.findMembersRemovedBetween(any(), any())).thenReturn(List.of());
        when(reportsRepository.findSubsystemsCreatedBetween(any(), any())).thenReturn(List.of());
        when(reportsRepository.findSubsystemsModifiedBetween(any(), any())).thenReturn(List.of());
        when(reportsRepository.findSubsystemsRemovedBetween(any(), any())).thenReturn(List.of());
        when(reportsRepository.findServicesCreatedBetween(any(), any())).thenReturn(List.of());
        when(reportsRepository.findServicesModifiedBetween(any(), any())).thenReturn(List.of());
        when(reportsRepository.findServicesRemovedBetween(any(), any())).thenReturn(List.of());
    }

    private static ServiceCountRow row(LocalDate day, String serviceType, long count) {
        return new ServiceCountRow() {
            @Override
            public LocalDate getDay() {
                return day;
            }

            @Override
            public String getServiceType() {
                return serviceType;
            }

            @Override
            public long getCount() {
                return count;
            }
        };
    }

    private static List<ServiceCountRow> statsRows(ServiceCountRow... rows) {
        return List.of(rows);
    }

    private static MemberChangeRow memberRow(String memberClass, String memberCode, String name,
            LocalDateTime eventTime) {
        return new MemberChangeRow() {
            @Override
            public String getMemberClass() {
                return memberClass;
            }

            @Override
            public String getMemberCode() {
                return memberCode;
            }

            @Override
            public String getName() {
                return name;
            }

            @Override
            public LocalDateTime getEventTime() {
                return eventTime;
            }
        };
    }

    private static ServiceChangeRow serviceRow(String memberClass, String memberCode, String memberName,
            String subsystemCode, String serviceCode, String serviceVersion, String serviceType,
            LocalDateTime eventTime) {
        return new ServiceChangeRow() {
            @Override
            public String getMemberClass() {
                return memberClass;
            }

            @Override
            public String getMemberCode() {
                return memberCode;
            }

            @Override
            public String getMemberName() {
                return memberName;
            }

            @Override
            public String getSubsystemCode() {
                return subsystemCode;
            }

            @Override
            public String getServiceCode() {
                return serviceCode;
            }

            @Override
            public String getServiceVersion() {
                return serviceVersion;
            }

            @Override
            public String getServiceType() {
                return serviceType;
            }

            @Override
            public LocalDateTime getEventTime() {
                return eventTime;
            }
        };
    }

    private static SubsystemChangeRow subsystemRow(String memberClass, String memberCode, String memberName,
            String subsystemCode, LocalDateTime eventTime) {
        return new SubsystemChangeRow() {
            @Override
            public String getMemberClass() {
                return memberClass;
            }

            @Override
            public String getMemberCode() {
                return memberCode;
            }

            @Override
            public String getMemberName() {
                return memberName;
            }

            @Override
            public String getSubsystemCode() {
                return subsystemCode;
            }

            @Override
            public LocalDateTime getEventTime() {
                return eventTime;
            }
        };
    }
}
