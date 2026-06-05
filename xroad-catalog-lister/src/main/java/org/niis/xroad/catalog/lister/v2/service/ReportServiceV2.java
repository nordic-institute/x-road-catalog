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

import org.niis.xroad.catalog.lister.v2.converter.ServiceClassifier;
import org.niis.xroad.catalog.lister.v2.dto.ChangeLogBucketDto;
import org.niis.xroad.catalog.lister.v2.dto.ChangeLogBucketsDto;
import org.niis.xroad.catalog.lister.v2.dto.ChangeLogDayDto;
import org.niis.xroad.catalog.lister.v2.dto.ChangeLogMemberItemDto;
import org.niis.xroad.catalog.lister.v2.dto.ChangeLogServiceItemDto;
import org.niis.xroad.catalog.lister.v2.dto.ChangeLogSubsystemItemDto;
import org.niis.xroad.catalog.lister.v2.dto.ServiceStatisticsRowDto;
import org.niis.xroad.catalog.persistence.entity.Member;
import org.niis.xroad.catalog.persistence.entity.Service;
import org.niis.xroad.catalog.persistence.entity.Subsystem;
import org.niis.xroad.catalog.persistence.repository.MemberRepositoryV2;
import org.niis.xroad.catalog.persistence.repository.ServiceRepositoryV2;
import org.niis.xroad.catalog.persistence.repository.SubsystemRepositoryV2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * V2 report service. Produces reports derived from the collected catalog.
 *
 * <p>The per-day {@link #serviceStatistics(LocalDate, LocalDate)} report corrects a
 * V1 over-count bug: V1 used {@code !created.isAfter(dayEnd)} and so would include a service
 * created at {@code day+1 00:00:00} in the previous day's count. V2 uses
 * {@code created < nextDayStart} (half-open), aligning "end of day" with the natural
 * {@code [dayStart, nextDayStart)} window.
 *
 * <p>The {@link #changeLog(LocalDate, LocalDate, Pageable)} report iterates the range
 * day by day and emits only days on which at least one member, subsystem or service was
 * created, modified or removed.
 */
@Component
public class ReportServiceV2 {

    /** Hard cap on the range passed to any report method (spec §4). */
    private static final long MAX_REPORT_DAYS = 90;

    @Autowired
    private MemberRepositoryV2 memberRepository;

    @Autowired
    private SubsystemRepositoryV2 subsystemRepository;

    @Autowired
    private ServiceRepositoryV2 serviceRepository;

    @Autowired
    private ServiceClassifier classifier;

    /**
     * Per-day snapshot of service counts by descriptor type over {@code [since, until)}.
     * The result is not paginated (one row per day; at most {@value #MAX_REPORT_DAYS} rows).
     *
     * @param since inclusive start date
     * @param until exclusive end date
     * @return one row per day in the range, in chronological order
     * @throws IllegalArgumentException if inputs fail {@link #validateReportRange}
     */
    public List<ServiceStatisticsRowDto> serviceStatistics(LocalDate since, LocalDate until) {
        validateReportRange(since, until);

        // Load all service rows once (active + removed). Counts are computed in memory per day
        // because every day in the range needs to inspect the same full set.
        List<Service> allServices = new ArrayList<>();
        serviceRepository.findAll().forEach(allServices::add);

        List<ServiceStatisticsRowDto> out = new ArrayList<>();
        LocalDate day = since;
        while (day.isBefore(until)) {
            LocalDateTime nextDayStart = day.plusDays(1).atStartOfDay();
            out.add(countServicesAtEndOfDay(day, nextDayStart, allServices));
            day = day.plusDays(1);
        }
        return out;
    }

    /**
     * Snapshot semantics: a service counts toward a given day when it existed at the
     * end of that day — equivalently, when
     * {@code created < nextDayStart && (removed == null || removed >= nextDayStart)}.
     */
    private ServiceStatisticsRowDto countServicesAtEndOfDay(LocalDate day,
                                                            LocalDateTime nextDayStart,
                                                            List<Service> allServices) {
        long soap = 0;
        long rest = 0;
        long openapi = 0;
        for (Service svc : allServices) {
            LocalDateTime created = svc.getStatusInfo().getCreated();
            LocalDateTime removed = svc.getStatusInfo().getRemoved();
            boolean existedAtDayEnd = created.isBefore(nextDayStart)
                    && (removed == null || !removed.isBefore(nextDayStart));
            if (!existedAtDayEnd) {
                continue;
            }
            String type = classifier.resolveType(svc);
            if ("SOAP".equals(type)) {
                soap++;
            } else if ("OPENAPI".equals(type)) {
                openapi++;
            } else {
                rest++;
            }
        }
        return ServiceStatisticsRowDto.builder()
                .date(day)
                .soapServices(soap)
                .restServices(rest)
                .openApiServices(openapi)
                .build();
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
     * @throws IllegalArgumentException if inputs fail {@link #validateReportRange}
     */
    public Page<ChangeLogDayDto> changeLog(LocalDate since, LocalDate until, Pageable pageable) {
        validateReportRange(since, until);

        List<ChangeLogDayDto> allDays = new ArrayList<>();
        LocalDate day = since;
        while (day.isBefore(until)) {
            LocalDateTime dayStart = day.atStartOfDay();
            LocalDateTime dayEnd = day.plusDays(1).atStartOfDay();
            ChangeLogDayDto dayDto = buildDayDto(day, dayStart, dayEnd);
            if (dayDto != null) {
                allDays.add(dayDto);
            }
            day = day.plusDays(1);
        }
        int from = (int) Math.min(pageable.getOffset(), allDays.size());
        int to = Math.min(from + pageable.getPageSize(), allDays.size());
        return new PageImpl<>(allDays.subList(from, to), pageable, allDays.size());
    }

    /**
     * Assembles a {@link ChangeLogDayDto} for a single {@code [dayStart, dayEnd)} slice.
     * Returns {@code null} if nothing was created, modified or removed during that day.
     */
    private ChangeLogDayDto buildDayDto(LocalDate day, LocalDateTime dayStart, LocalDateTime dayEnd) {
        List<Member> createdMembers = memberRepository.findCreatedBetween(dayStart, dayEnd);
        List<Member> modifiedMembers = memberRepository.findModifiedBetween(dayStart, dayEnd);
        List<Member> removedMembers = memberRepository.findRemovedBetween(dayStart, dayEnd);
        List<Subsystem> createdSubs = subsystemRepository.findCreatedBetween(dayStart, dayEnd);
        List<Subsystem> modifiedSubs = subsystemRepository.findChangedBetween(dayStart, dayEnd);
        List<Subsystem> removedSubs = subsystemRepository.findRemovedBetween(dayStart, dayEnd);
        List<Service> createdSvcs = serviceRepository.findCreatedBetween(dayStart, dayEnd);
        List<Service> modifiedSvcs = serviceRepository.findChangedBetween(dayStart, dayEnd);
        List<Service> removedSvcs = serviceRepository.findRemovedBetween(dayStart, dayEnd);

        int total = createdMembers.size() + modifiedMembers.size() + removedMembers.size()
                + createdSubs.size() + modifiedSubs.size() + removedSubs.size()
                + createdSvcs.size() + modifiedSvcs.size() + removedSvcs.size();
        if (total == 0) {
            return null;
        }
        return ChangeLogDayDto.builder()
                .date(day)
                .created(bucketsFor(createdMembers, createdSubs, createdSvcs))
                .modified(bucketsFor(modifiedMembers, modifiedSubs, modifiedSvcs))
                .removed(bucketsFor(removedMembers, removedSubs, removedSvcs))
                .build();
    }

    /**
     * Wraps the three entity-type lists into a {@link ChangeLogBucketsDto} with per-type
     * counts and item DTOs.
     */
    private ChangeLogBucketsDto bucketsFor(List<Member> members, List<Subsystem> subsystems, List<Service> services) {
        return ChangeLogBucketsDto.builder()
                .members(toMemberBucket(members))
                .subsystems(toSubsystemBucket(subsystems))
                .services(toServiceBucket(services))
                .build();
    }

    private ChangeLogBucketDto<ChangeLogMemberItemDto> toMemberBucket(List<Member> members) {
        List<ChangeLogMemberItemDto> items = new ArrayList<>(members.size());
        for (Member m : members) {
            items.add(ChangeLogMemberItemDto.builder()
                    .memberClass(m.getMemberClass())
                    .memberCode(m.getMemberCode())
                    .name(m.getName())
                    .build());
        }
        return ChangeLogBucketDto.<ChangeLogMemberItemDto>builder()
                .count(items.size())
                .items(items)
                .build();
    }

    private ChangeLogBucketDto<ChangeLogSubsystemItemDto> toSubsystemBucket(List<Subsystem> subsystems) {
        List<ChangeLogSubsystemItemDto> items = new ArrayList<>(subsystems.size());
        for (Subsystem s : subsystems) {
            Member owner = s.getMember();
            items.add(ChangeLogSubsystemItemDto.builder()
                    .memberClass(owner.getMemberClass())
                    .memberCode(owner.getMemberCode())
                    .memberName(owner.getName())
                    .subsystemCode(s.getSubsystemCode())
                    .build());
        }
        return ChangeLogBucketDto.<ChangeLogSubsystemItemDto>builder()
                .count(items.size())
                .items(items)
                .build();
    }

    private ChangeLogBucketDto<ChangeLogServiceItemDto> toServiceBucket(List<Service> services) {
        List<ChangeLogServiceItemDto> items = new ArrayList<>(services.size());
        for (Service svc : services) {
            Subsystem sub = svc.getSubsystem();
            Member owner = sub.getMember();
            items.add(ChangeLogServiceItemDto.builder()
                    .memberClass(owner.getMemberClass())
                    .memberCode(owner.getMemberCode())
                    .memberName(owner.getName())
                    .subsystemCode(sub.getSubsystemCode())
                    .serviceCode(svc.getServiceCode())
                    .serviceVersion(svc.getServiceVersion())
                    .serviceType(classifier.resolveType(svc))
                    .build());
        }
        return ChangeLogBucketDto.<ChangeLogServiceItemDto>builder()
                .count(items.size())
                .items(items)
                .build();
    }

    /**
     * Validates a report-range request. With the date-only contract, the midnight invariant is
     * carried by the {@link LocalDate} type — no runtime midnight check is needed.
     *
     * @param since inclusive start date
     * @param until exclusive end date
     * @throws IllegalArgumentException if {@code since >= until} or {@code until - since > MAX_REPORT_DAYS}
     */
    void validateReportRange(LocalDate since, LocalDate until) {
        if (!since.isBefore(until)) {
            throw new IllegalArgumentException("'since' must be strictly before 'until'");
        }
        long days = ChronoUnit.DAYS.between(since, until);
        if (days > MAX_REPORT_DAYS) {
            throw new IllegalArgumentException(
                    "Date range exceeds maximum of " + MAX_REPORT_DAYS + " days (requested: " + days + ")");
        }
    }
}
