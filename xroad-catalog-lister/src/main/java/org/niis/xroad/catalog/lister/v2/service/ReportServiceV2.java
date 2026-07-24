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

import org.niis.xroad.catalog.lister.v2.dto.ChangeLogBucketDto;
import org.niis.xroad.catalog.lister.v2.dto.ChangeLogBucketsDto;
import org.niis.xroad.catalog.lister.v2.dto.ChangeLogDayDto;
import org.niis.xroad.catalog.lister.v2.dto.ChangeLogMemberItemDto;
import org.niis.xroad.catalog.lister.v2.dto.ChangeLogServiceItemDto;
import org.niis.xroad.catalog.lister.v2.dto.ChangeLogSubsystemItemDto;
import org.niis.xroad.catalog.lister.v2.dto.ServiceStatisticsRowDto;
import org.niis.xroad.catalog.lister.v2.util.DateTimeUtil;
import org.niis.xroad.catalog.persistence.repository.ReportsRepositoryV2;
import org.niis.xroad.catalog.persistence.repository.projection.MemberChangeRow;
import org.niis.xroad.catalog.persistence.repository.projection.ServiceChangeRow;
import org.niis.xroad.catalog.persistence.repository.projection.SubsystemChangeRow;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.sql.Date;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * V2 report service. Produces reports derived from the collected catalog, computed entirely via
 * SQL aggregates on {@link ReportsRepositoryV2} — no entity hydration, no per-day query loops.
 *
 * <p>{@link #serviceStatistics(LocalDate, LocalDate)} pre-fills every day in the requested window
 * with zero counts, then folds the single {@code countServicesPerDay} result set (one row per
 * {@code (day, service_type)} pair, computed by a delta-based window-function query rather than a
 * per-day join) on top of that scaffold, so the wire format has one row per day regardless of
 * whether the query returns any rows for a given day.
 *
 * <p>{@link #changeLog(LocalDate, LocalDate, Pageable)} first fetches the page's event days from
 * {@code findChangeLogDayPage} — a single DB-paginated query over the whole window — then narrows
 * the nine change-log item queries to that page's day span, so the cost of a page is proportional
 * to the page rather than to the whole requested window.
 */
@Service
public class ReportServiceV2 {

    /** Hard cap on the range passed to any report method (spec §4). */
    private static final long MAX_REPORT_DAYS = 90;

    private static final int SOAP_INDEX = 0;
    private static final int OPENAPI_INDEX = 1;
    private static final int REST_INDEX = 2;
    private static final int SERVICE_TYPE_COLUMN_COUNT = 3;
    // UNKNOWN (not yet classified by the collector recompute) belongs to no descriptor bucket and
    // is left out rather than inflating the REST count with a guess.
    private static final int UNCLASSIFIED_INDEX = -1;

    private final ReportsRepositoryV2 reportsRepository;

    public ReportServiceV2(ReportsRepositoryV2 reportsRepository) {
        this.reportsRepository = reportsRepository;
    }

    /**
     * Per-day snapshot of service counts by descriptor type over {@code [since, until)}.
     * The result is not paginated (one row per day; at most {@value #MAX_REPORT_DAYS} rows).
     *
     * @param since inclusive start date
     * @param until exclusive end date
     * @return one row per day in the range, in chronological order
     * @throws IllegalArgumentException if {@code since} is after {@code until} or the range exceeds
     *         {@value #MAX_REPORT_DAYS} days; see {@link DateTimeUtil#validateDateRange}
     */
    public List<ServiceStatisticsRowDto> serviceStatistics(LocalDate since, LocalDate until) {
        DateTimeUtil.validateDateRange(since.atStartOfDay(), until.atStartOfDay(), MAX_REPORT_DAYS);
        List<Object[]> rows = reportsRepository.countServicesPerDay(since, until);
        Map<LocalDate, long[]> byDay = new TreeMap<>();
        for (LocalDate day = since; day.isBefore(until); day = day.plusDays(1)) {
            byDay.put(day, new long[SERVICE_TYPE_COLUMN_COUNT]);
        }
        for (Object[] row : rows) {
            LocalDate day = ((Date) row[0]).toLocalDate();
            long[] counts = byDay.get(day);
            if (counts == null || row[1] == null) {
                continue;
            }
            long count = ((Number) row[2]).longValue();
            int index = switch ((String) row[1]) {
                case "SOAP" -> SOAP_INDEX;
                case "OPENAPI" -> OPENAPI_INDEX;
                case "REST" -> REST_INDEX;
                default -> UNCLASSIFIED_INDEX;
            };
            if (index != UNCLASSIFIED_INDEX) {
                counts[index] += count;
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
     * Paginated per-day change log over {@code [since, until)}. Each returned day contains
     * three buckets (created / modified / removed) with members, subsystems and services.
     *
     * <p>Days with no changes are omitted so that {@code totalCount} reflects only the days
     * that actually had at least one event. Days are ordered chronologically, oldest first.
     *
     * @param since inclusive start date
     * @param until exclusive end date
     * @param pageable Spring page request (offset + size; sort is ignored — always chronological)
     * @return page of {@link ChangeLogDayDto}; {@code totalElements} is the number of
     *         days with at least one change, not the number of calendar days in the range
     * @throws IllegalArgumentException if {@code since} is after {@code until} or the range exceeds
     *         {@value #MAX_REPORT_DAYS} days; see {@link DateTimeUtil#validateDateRange}
     */
    public Page<ChangeLogDayDto> changeLog(LocalDate since, LocalDate until, Pageable pageable) {
        LocalDateTime start = since.atStartOfDay();
        LocalDateTime end = until.atStartOfDay();
        DateTimeUtil.validateDateRange(start, end, MAX_REPORT_DAYS);

        List<Object[]> dayPage = reportsRepository.findChangeLogDayPage(
                start, end, pageable.getPageSize(), pageable.getOffset());
        if (dayPage.isEmpty()) {
            long total = pageable.getOffset() == 0 ? 0 : reportsRepository.countChangeLogDays(start, end);
            return new PageImpl<>(List.of(), pageable, total);
        }
        long totalDays = ((Number) dayPage.get(0)[1]).longValue();
        LocalDate firstDay = ((Date) dayPage.get(0)[0]).toLocalDate();
        LocalDate lastDay = ((Date) dayPage.get(dayPage.size() - 1)[0]).toLocalDate();

        // The page's days are consecutive members of the sorted event-day list, so narrowing the
        // nine item queries to [firstDay, lastDay+1) yields exactly the items of this page: any
        // event day inside that span would itself have been on the page.
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
