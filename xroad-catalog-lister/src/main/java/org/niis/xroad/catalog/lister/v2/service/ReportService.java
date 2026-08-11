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

import org.niis.xroad.catalog.lister.v2.exception.BadRequestException;
import org.niis.xroad.catalog.lister.v2.dto.ChangeLogBucketDto;
import org.niis.xroad.catalog.lister.v2.dto.ChangeLogBucketsDto;
import org.niis.xroad.catalog.lister.v2.dto.ChangeLogDayDto;
import org.niis.xroad.catalog.lister.v2.dto.ChangeLogMemberItemDto;
import org.niis.xroad.catalog.lister.v2.dto.ChangeLogServiceItemDto;
import org.niis.xroad.catalog.lister.v2.dto.ChangeLogSubsystemItemDto;
import org.niis.xroad.catalog.lister.v2.dto.ServiceStatisticsRowDto;
import org.niis.xroad.catalog.lister.v2.util.DateTimeUtil;
import org.niis.xroad.catalog.persistence.v2.repository.ReportsRepository;
import org.niis.xroad.catalog.persistence.v2.repository.projection.ChangeLogDayRow;
import org.niis.xroad.catalog.persistence.v2.repository.projection.MemberChangeRow;
import org.niis.xroad.catalog.persistence.v2.repository.projection.ServiceChangeRow;
import org.niis.xroad.catalog.persistence.v2.repository.projection.ServiceCountRow;
import org.niis.xroad.catalog.persistence.v2.repository.projection.SubsystemChangeRow;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * V2 reports computed entirely via SQL aggregates on {@link ReportsRepository} — no entity
 * hydration, no per-day query loops.
 *
 * <p>{@link #serviceStatistics(LocalDate, LocalDate)} pre-fills every day in the window with zero
 * counts, then folds the single {@code countServicesPerDay} result set on top, so the wire format
 * has one row per day even when the query returns none for it.
 *
 * <p>{@link #changeLog(LocalDate, LocalDate, Pageable)} fetches the page's event days with one
 * DB-paginated query, then narrows the nine item queries to that page's day span, keeping the cost
 * of a page proportional to the page rather than the whole window.
 */
@Service
public class ReportService {

    /** Hard cap on the date range accepted by any report method. */
    private static final long MAX_REPORT_DAYS = 90;

    private static final int SOAP_INDEX = 0;
    private static final int OPENAPI_INDEX = 1;
    private static final int REST_INDEX = 2;
    private static final int SERVICE_TYPE_COLUMN_COUNT = 3;
    // UNKNOWN (not yet classified) fits no descriptor bucket and is left out
    private static final int UNCLASSIFIED_INDEX = -1;

    private final ReportsRepository reportsRepository;

    public ReportService(ReportsRepository reportsRepository) {
        this.reportsRepository = reportsRepository;
    }

    /**
     * Per-day service counts by descriptor type over {@code [since, until)} — one row per day in
     * chronological order, not paginated (at most {@value #MAX_REPORT_DAYS} rows).
     *
     * @throws BadRequestException if {@code since} is after {@code until} or the range exceeds
     *         {@value #MAX_REPORT_DAYS} days
     */
    public List<ServiceStatisticsRowDto> serviceStatistics(LocalDate since, LocalDate until) {
        DateTimeUtil.validateDateRange(since.atStartOfDay(), until.atStartOfDay(), MAX_REPORT_DAYS);
        List<ServiceCountRow> rows = reportsRepository.countServicesPerDay(since, until);
        Map<LocalDate, long[]> byDay = new TreeMap<>();
        for (LocalDate day = since; day.isBefore(until); day = day.plusDays(1)) {
            byDay.put(day, new long[SERVICE_TYPE_COLUMN_COUNT]);
        }
        for (ServiceCountRow row : rows) {
            long[] counts = byDay.get(row.getDay());
            if (counts == null) {
                continue;
            }
            int index = switch (row.getServiceType()) {
                case "SOAP" -> SOAP_INDEX;
                case "OPENAPI" -> OPENAPI_INDEX;
                case "REST" -> REST_INDEX;
                default -> UNCLASSIFIED_INDEX;
            };
            if (index != UNCLASSIFIED_INDEX) {
                counts[index] += row.getCount();
            }
        }
        List<ServiceStatisticsRowDto> out = new ArrayList<>(byDay.size());
        byDay.forEach((day, counts) -> out.add(ServiceStatisticsRowDto.builder()
                .date(day)
                .soapServices(counts[SOAP_INDEX])
                .openApiServices(counts[OPENAPI_INDEX])
                .restServices(counts[REST_INDEX])
                .build()));
        return out;
    }

    /**
     * Paginated per-day change log over {@code [since, until)}; each day has created/modified/removed
     * buckets of members, subsystems and services. Days without changes are omitted, so
     * {@code totalElements} counts event days, not calendar days. Chronological, oldest first;
     * the pageable's sort is ignored.
     *
     * @throws BadRequestException if {@code since} is after {@code until} or the range exceeds
     *         {@value #MAX_REPORT_DAYS} days
     */
    public Page<ChangeLogDayDto> changeLog(LocalDate since, LocalDate until, Pageable pageable) {
        LocalDateTime start = since.atStartOfDay();
        LocalDateTime end = until.atStartOfDay();
        DateTimeUtil.validateDateRange(start, end, MAX_REPORT_DAYS);

        List<ChangeLogDayRow> dayPage = reportsRepository.findChangeLogDayPage(
                start, end, pageable.getPageSize(), pageable.getOffset());
        if (dayPage.isEmpty()) {
            long total = pageable.getOffset() == 0 ? 0 : reportsRepository.countChangeLogDays(start, end);
            return new PageImpl<>(List.of(), pageable, total);
        }
        long totalDays = dayPage.get(0).getTotalDays();
        LocalDate firstDay = dayPage.get(0).getDay();
        LocalDate lastDay = dayPage.get(dayPage.size() - 1).getDay();

        // The page's days are consecutive members of the sorted event-day list, so narrowing the
        // nine item queries to [firstDay, lastDay+1) yields exactly this page's items.
        LocalDateTime pageStart = firstDay.atStartOfDay();
        LocalDateTime pageEnd = lastDay.plusDays(1).atStartOfDay();
        Map<LocalDate, DayBuckets> days = new TreeMap<>();
        reportsRepository.findMembersCreatedBetween(pageStart, pageEnd)
                .forEach(r -> dayOf(days, r.getEventTime()).membersCreated.add(r));
        reportsRepository.findMembersModifiedBetween(pageStart, pageEnd)
                .forEach(r -> dayOf(days, r.getEventTime()).membersModified.add(r));
        reportsRepository.findMembersRemovedBetween(pageStart, pageEnd)
                .forEach(r -> dayOf(days, r.getEventTime()).membersRemoved.add(r));
        reportsRepository.findSubsystemsCreatedBetween(pageStart, pageEnd)
                .forEach(r -> dayOf(days, r.getEventTime()).subsystemsCreated.add(r));
        reportsRepository.findSubsystemsModifiedBetween(pageStart, pageEnd)
                .forEach(r -> dayOf(days, r.getEventTime()).subsystemsModified.add(r));
        reportsRepository.findSubsystemsRemovedBetween(pageStart, pageEnd)
                .forEach(r -> dayOf(days, r.getEventTime()).subsystemsRemoved.add(r));
        reportsRepository.findServicesCreatedBetween(pageStart, pageEnd)
                .forEach(r -> dayOf(days, r.getEventTime()).servicesCreated.add(r));
        reportsRepository.findServicesModifiedBetween(pageStart, pageEnd)
                .forEach(r -> dayOf(days, r.getEventTime()).servicesModified.add(r));
        reportsRepository.findServicesRemovedBetween(pageStart, pageEnd)
                .forEach(r -> dayOf(days, r.getEventTime()).servicesRemoved.add(r));

        List<ChangeLogDayDto> dayDtos = days.entrySet().stream()
                .map(e -> toDayDto(e.getKey(), e.getValue()))
                .toList();
        return new PageImpl<>(dayDtos, pageable, totalDays);
    }

    private static DayBuckets dayOf(Map<LocalDate, DayBuckets> days, LocalDateTime eventTime) {
        return days.computeIfAbsent(eventTime.toLocalDate(), d -> new DayBuckets());
    }

    private static ChangeLogDayDto toDayDto(LocalDate day, DayBuckets b) {
        return ChangeLogDayDto.builder()
                .date(day)
                .created(bucketsFor(b.membersCreated, b.subsystemsCreated, b.servicesCreated))
                .modified(bucketsFor(b.membersModified, b.subsystemsModified, b.servicesModified))
                .removed(bucketsFor(b.membersRemoved, b.subsystemsRemoved, b.servicesRemoved))
                .build();
    }

    private static ChangeLogBucketsDto bucketsFor(List<MemberChangeRow> members, List<SubsystemChangeRow> subsystems,
            List<ServiceChangeRow> services) {
        return ChangeLogBucketsDto.builder()
                .members(toMemberBucket(members))
                .subsystems(toSubsystemBucket(subsystems))
                .services(toServiceBucket(services))
                .build();
    }

    private static ChangeLogBucketDto<ChangeLogMemberItemDto> toMemberBucket(List<MemberChangeRow> rows) {
        List<ChangeLogMemberItemDto> items = new ArrayList<>(rows.size());
        for (MemberChangeRow r : rows) {
            items.add(ChangeLogMemberItemDto.builder()
                    .memberClass(r.getMemberClass())
                    .memberCode(r.getMemberCode())
                    .name(r.getName())
                    .build());
        }
        return ChangeLogBucketDto.<ChangeLogMemberItemDto>builder().count(items.size()).items(items).build();
    }

    private static ChangeLogBucketDto<ChangeLogSubsystemItemDto> toSubsystemBucket(List<SubsystemChangeRow> rows) {
        List<ChangeLogSubsystemItemDto> items = new ArrayList<>(rows.size());
        for (SubsystemChangeRow r : rows) {
            items.add(ChangeLogSubsystemItemDto.builder()
                    .memberClass(r.getMemberClass())
                    .memberCode(r.getMemberCode())
                    .memberName(r.getMemberName())
                    .subsystemCode(r.getSubsystemCode())
                    .build());
        }
        return ChangeLogBucketDto.<ChangeLogSubsystemItemDto>builder().count(items.size()).items(items).build();
    }

    private static ChangeLogBucketDto<ChangeLogServiceItemDto> toServiceBucket(List<ServiceChangeRow> rows) {
        List<ChangeLogServiceItemDto> items = new ArrayList<>(rows.size());
        for (ServiceChangeRow r : rows) {
            items.add(ChangeLogServiceItemDto.builder()
                    .memberClass(r.getMemberClass())
                    .memberCode(r.getMemberCode())
                    .memberName(r.getMemberName())
                    .subsystemCode(r.getSubsystemCode())
                    .serviceCode(r.getServiceCode())
                    .serviceVersion(r.getServiceVersion())
                    .serviceType(r.getServiceType())
                    .build());
        }
        return ChangeLogBucketDto.<ChangeLogServiceItemDto>builder().count(items.size()).items(items).build();
    }

    /** Per-day buckets accumulated while folding the nine change-log query results. */
    private static final class DayBuckets {
        private final List<MemberChangeRow> membersCreated = new ArrayList<>();
        private final List<MemberChangeRow> membersModified = new ArrayList<>();
        private final List<MemberChangeRow> membersRemoved = new ArrayList<>();
        private final List<SubsystemChangeRow> subsystemsCreated = new ArrayList<>();
        private final List<SubsystemChangeRow> subsystemsModified = new ArrayList<>();
        private final List<SubsystemChangeRow> subsystemsRemoved = new ArrayList<>();
        private final List<ServiceChangeRow> servicesCreated = new ArrayList<>();
        private final List<ServiceChangeRow> servicesModified = new ArrayList<>();
        private final List<ServiceChangeRow> servicesRemoved = new ArrayList<>();
    }
}
