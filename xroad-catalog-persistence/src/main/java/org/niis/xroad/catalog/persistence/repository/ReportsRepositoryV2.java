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

import org.niis.xroad.catalog.persistence.repository.projection.MemberChangeRow;
import org.niis.xroad.catalog.persistence.repository.projection.ServiceChangeRow;
import org.niis.xroad.catalog.persistence.repository.projection.SubsystemChangeRow;
import org.niis.xroad.catalog.persistence.v2entity.MemberV2;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * V2 repository for statistics and change-log reporting. The windowed diff queries below are, by
 * design, **the one place removed data stays visible in V2** — {@code includeRemoved} was dropped
 * everywhere else when the V2 API moved to projection-based reads, but a member/subsystem/service
 * that was removed within the requested window must still be reported here so external systems can
 * sync via the changes endpoint.
 */
public interface ReportsRepositoryV2 extends Repository<MemberV2, Long>, V2ReadModelRepository {

    // Delta-based day-end snapshot per stored service_type: a baseline of services alive at the
    // window start, +1 on each created::date and -1 on each removed::date inside the window,
    // accumulated per type with a window function. O(services) instead of O(days x services);
    // served by idx_service_created / idx_service_removed. A service removed on day D does not
    // count on day D (matches the old half-open snapshot predicate). Timestamps compare in the
    // session timezone — the deployment invariant (collector/lister/Postgres share one zone)
    // applies. SUM over bigint yields numeric, hence the CAST back to bigint.
    String STATISTICS_SQL = "WITH days AS ("
            + " SELECT CAST(generate_series(CAST(:since AS date), CAST(:until AS date) - 1,"
            + "   INTERVAL '1 day') AS date) AS day"
            + "), types AS ("
            + " SELECT DISTINCT s.service_type FROM service s"
            + "), baseline AS ("
            + " SELECT s.service_type, COUNT(*) AS cnt FROM service s"
            + " WHERE s.created < CAST(:since AS date)"
            + "   AND (s.removed IS NULL OR s.removed >= CAST(:since AS date))"
            + " GROUP BY s.service_type"
            + "), deltas AS ("
            + " SELECT day, service_type, SUM(delta) AS delta FROM ("
            + "   SELECT CAST(s.created AS date) AS day, s.service_type, 1 AS delta FROM service s"
            + "   WHERE s.created >= CAST(:since AS date) AND s.created < CAST(:until AS date)"
            + "   UNION ALL"
            + "   SELECT CAST(s.removed AS date), s.service_type, -1 FROM service s"
            + "   WHERE s.removed >= CAST(:since AS date) AND s.removed < CAST(:until AS date)"
            + " ) d GROUP BY day, service_type"
            + ")"
            + " SELECT d.day, t.service_type,"
            + " CAST(COALESCE(b.cnt, 0)"
            + "   + COALESCE(SUM(dl.delta) OVER (PARTITION BY t.service_type ORDER BY d.day), 0) AS bigint)"
            + " FROM days d"
            + " CROSS JOIN types t"
            + " LEFT JOIN baseline b ON b.service_type = t.service_type"
            + " LEFT JOIN deltas dl ON dl.day = d.day AND dl.service_type = t.service_type"
            + " ORDER BY d.day, t.service_type";

    /**
     * Row shape (column order: day, service_type, count), as observed under Hibernate 6.6 /
     * Spring Boot 3.5.6 / PostgreSQL driver 42.7.10: {@code row[0]} is a {@link java.sql.Date},
     * {@code row[1]} is a {@link String} that is never null, and {@code row[2]} is a {@link Long}.
     * A day with zero services of some type still yields a {@code (day, type, 0)} row for every
     * type present in the table; an empty service table yields no rows at all (the service layer
     * pre-fills days).
     */
    @Query(value = STATISTICS_SQL, nativeQuery = true)
    List<Object[]> countServicesPerDay(@Param("since") LocalDate since, @Param("until") LocalDate until);

    // Every timestamp that the nine change queries would report, reduced to its calendar day.
    // The modified-branch guards mirror find*ModifiedBetween exactly: changed == created marks a
    // create and changed == removed marks a remove, neither of which is a modification.
    String CHANGE_EVENTS_SQL = "SELECT m.created AS ev FROM member m"
            + " WHERE m.created >= :startDate AND m.created < :endDate"
            + " UNION ALL SELECT m.changed FROM member m"
            + " WHERE m.changed >= :startDate AND m.changed < :endDate AND m.changed <> m.created"
            + "   AND (m.removed IS NULL OR m.changed <> m.removed)"
            + " UNION ALL SELECT m.removed FROM member m"
            + " WHERE m.removed >= :startDate AND m.removed < :endDate"
            + " UNION ALL SELECT ss.created FROM subsystem ss"
            + " WHERE ss.created >= :startDate AND ss.created < :endDate"
            + " UNION ALL SELECT ss.changed FROM subsystem ss"
            + " WHERE ss.changed >= :startDate AND ss.changed < :endDate AND ss.changed <> ss.created"
            + "   AND (ss.removed IS NULL OR ss.changed <> ss.removed)"
            + " UNION ALL SELECT ss.removed FROM subsystem ss"
            + " WHERE ss.removed >= :startDate AND ss.removed < :endDate"
            + " UNION ALL SELECT s.created FROM service s"
            + " WHERE s.created >= :startDate AND s.created < :endDate"
            + " UNION ALL SELECT s.changed FROM service s"
            + " WHERE s.changed >= :startDate AND s.changed < :endDate AND s.changed <> s.created"
            + "   AND (s.removed IS NULL OR s.changed <> s.removed)"
            + " UNION ALL SELECT s.removed FROM service s"
            + " WHERE s.removed >= :startDate AND s.removed < :endDate";

    @Query(value = "SELECT day, CAST(COUNT(*) OVER () AS bigint) AS total_days FROM ("
            + " SELECT DISTINCT CAST(ev AS date) AS day FROM (" + CHANGE_EVENTS_SQL + ") events"
            + ") days ORDER BY day LIMIT :pageSize OFFSET :offset", nativeQuery = true)
    List<Object[]> findChangeLogDayPage(@Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            @Param("pageSize") int pageSize, @Param("offset") long offset);

    @Query(value = "SELECT COUNT(DISTINCT CAST(ev AS date)) FROM (" + CHANGE_EVENTS_SQL + ") events",
            nativeQuery = true)
    long countChangeLogDays(@Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    @Query("SELECT m.memberClass AS memberClass, m.memberCode AS memberCode, m.name AS name, "
            + "m.statusInfo.created AS eventTime FROM MemberV2 m "
            + "WHERE m.statusInfo.created >= :startDate AND m.statusInfo.created < :endDate")
    List<MemberChangeRow> findMembersCreatedBetween(@Param("startDate") LocalDateTime startDate,
                                                     @Param("endDate") LocalDateTime endDate);

    // The guard excludes rows whose changed timestamp coincides with a create or a remove:
    // StatusInfo sets changed == created on create and changed == removed on remove, so without
    // these guards a create/remove would double-count as a modification. This must be an equality
    // check against the coinciding event, not window membership -- a create that falls inside
    // [startDate, endDate) does not itself disqualify a later, genuinely distinct change that also
    // falls in the window from being reported as a modification.
    @Query("SELECT m.memberClass AS memberClass, m.memberCode AS memberCode, m.name AS name, "
            + "m.statusInfo.changed AS eventTime FROM MemberV2 m "
            + "WHERE m.statusInfo.changed >= :startDate AND m.statusInfo.changed < :endDate "
            + "AND m.statusInfo.changed <> m.statusInfo.created "
            + "AND (m.statusInfo.removed IS NULL OR m.statusInfo.changed <> m.statusInfo.removed)")
    List<MemberChangeRow> findMembersModifiedBetween(@Param("startDate") LocalDateTime startDate,
                                                      @Param("endDate") LocalDateTime endDate);

    @Query("SELECT m.memberClass AS memberClass, m.memberCode AS memberCode, m.name AS name, "
            + "m.statusInfo.removed AS eventTime FROM MemberV2 m "
            + "WHERE m.statusInfo.removed >= :startDate AND m.statusInfo.removed < :endDate")
    List<MemberChangeRow> findMembersRemovedBetween(@Param("startDate") LocalDateTime startDate,
                                                     @Param("endDate") LocalDateTime endDate);

    @Query("SELECT ss.member.memberClass AS memberClass, ss.member.memberCode AS memberCode, "
            + "ss.member.name AS memberName, ss.subsystemCode AS subsystemCode, "
            + "ss.statusInfo.created AS eventTime FROM SubsystemV2 ss "
            + "WHERE ss.statusInfo.created >= :startDate AND ss.statusInfo.created < :endDate")
    List<SubsystemChangeRow> findSubsystemsCreatedBetween(@Param("startDate") LocalDateTime startDate,
                                                           @Param("endDate") LocalDateTime endDate);

    // See findMembersModifiedBetween for why the created/removed guards must be equality checks,
    // not window-membership checks.
    @Query("SELECT ss.member.memberClass AS memberClass, ss.member.memberCode AS memberCode, "
            + "ss.member.name AS memberName, ss.subsystemCode AS subsystemCode, "
            + "ss.statusInfo.changed AS eventTime FROM SubsystemV2 ss "
            + "WHERE ss.statusInfo.changed >= :startDate AND ss.statusInfo.changed < :endDate "
            + "AND ss.statusInfo.changed <> ss.statusInfo.created "
            + "AND (ss.statusInfo.removed IS NULL OR ss.statusInfo.changed <> ss.statusInfo.removed)")
    List<SubsystemChangeRow> findSubsystemsModifiedBetween(@Param("startDate") LocalDateTime startDate,
                                                            @Param("endDate") LocalDateTime endDate);

    @Query("SELECT ss.member.memberClass AS memberClass, ss.member.memberCode AS memberCode, "
            + "ss.member.name AS memberName, ss.subsystemCode AS subsystemCode, "
            + "ss.statusInfo.removed AS eventTime FROM SubsystemV2 ss "
            + "WHERE ss.statusInfo.removed >= :startDate AND ss.statusInfo.removed < :endDate")
    List<SubsystemChangeRow> findSubsystemsRemovedBetween(@Param("startDate") LocalDateTime startDate,
                                                           @Param("endDate") LocalDateTime endDate);

    @Query("SELECT s.subsystem.member.memberClass AS memberClass, s.subsystem.member.memberCode AS memberCode, "
            + "s.subsystem.member.name AS memberName, s.subsystem.subsystemCode AS subsystemCode, "
            + "s.serviceCode AS serviceCode, s.serviceVersion AS serviceVersion, s.serviceType AS serviceType, "
            + "s.statusInfo.created AS eventTime FROM ServiceV2 s "
            + "WHERE s.statusInfo.created >= :startDate AND s.statusInfo.created < :endDate")
    List<ServiceChangeRow> findServicesCreatedBetween(@Param("startDate") LocalDateTime startDate,
                                                       @Param("endDate") LocalDateTime endDate);

    // See findMembersModifiedBetween for why the created/removed guards must be equality checks,
    // not window-membership checks.
    @Query("SELECT s.subsystem.member.memberClass AS memberClass, s.subsystem.member.memberCode AS memberCode, "
            + "s.subsystem.member.name AS memberName, s.subsystem.subsystemCode AS subsystemCode, "
            + "s.serviceCode AS serviceCode, s.serviceVersion AS serviceVersion, s.serviceType AS serviceType, "
            + "s.statusInfo.changed AS eventTime FROM ServiceV2 s "
            + "WHERE s.statusInfo.changed >= :startDate AND s.statusInfo.changed < :endDate "
            + "AND s.statusInfo.changed <> s.statusInfo.created "
            + "AND (s.statusInfo.removed IS NULL OR s.statusInfo.changed <> s.statusInfo.removed)")
    List<ServiceChangeRow> findServicesModifiedBetween(@Param("startDate") LocalDateTime startDate,
                                                        @Param("endDate") LocalDateTime endDate);

    @Query("SELECT s.subsystem.member.memberClass AS memberClass, s.subsystem.member.memberCode AS memberCode, "
            + "s.subsystem.member.name AS memberName, s.subsystem.subsystemCode AS subsystemCode, "
            + "s.serviceCode AS serviceCode, s.serviceVersion AS serviceVersion, s.serviceType AS serviceType, "
            + "s.statusInfo.removed AS eventTime FROM ServiceV2 s "
            + "WHERE s.statusInfo.removed >= :startDate AND s.statusInfo.removed < :endDate")
    List<ServiceChangeRow> findServicesRemovedBetween(@Param("startDate") LocalDateTime startDate,
                                                       @Param("endDate") LocalDateTime endDate);
}
