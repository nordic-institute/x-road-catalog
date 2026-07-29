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
package org.niis.xroad.catalog.persistence.v2.repository;

import org.niis.xroad.catalog.persistence.entity.Member;
import org.niis.xroad.catalog.persistence.v2.repository.projection.ChangeLogDayRow;
import org.niis.xroad.catalog.persistence.v2.repository.projection.MemberChangeRow;
import org.niis.xroad.catalog.persistence.v2.repository.projection.ServiceChangeRow;
import org.niis.xroad.catalog.persistence.v2.repository.projection.ServiceCountRow;
import org.niis.xroad.catalog.persistence.v2.repository.projection.SubsystemChangeRow;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * V2 repository for statistics and change-log reporting — the one V2 read path where removed rows
 * stay visible, since entities removed within the requested window must still be reported for
 * change-based sync. Every query is native SQL against the base tables, bypassing the active-rows
 * views. Anchors on the V1 {@code Member} entity purely so Spring Data registers it; it stays
 * eager under the {@code test} profile and must not extend the read-model marker.
 */
public interface ReportsRepository extends Repository<Member, Long> {

    // Delta-based day-end snapshot per stored service_type: baseline of services alive at window
    // start, +1 per in-window created::date and -1 per removed::date, accumulated per type with a
    // window function — O(services), served by idx_service_created / idx_service_removed. A
    // service removed on day D does not count on day D. Timestamps compare in the session
    // timezone (collector/lister/Postgres share one zone). SUM over bigint yields numeric, hence
    // the CAST back to bigint.
    String STATISTICS_SQL = "WITH days AS ("
            + " SELECT CAST(generate_series(CAST(:since AS date), CAST(:until AS date) - 1,"
            + "   INTERVAL '1 day') AS date) AS day"
            + "), types AS ("
            + " SELECT t.service_type FROM (VALUES ('SOAP'), ('OPENAPI'), ('REST'), ('UNKNOWN')) AS t(service_type)"
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
            + " SELECT d.day AS day, t.service_type AS serviceType,"
            + " CAST(COALESCE(b.cnt, 0)"
            + "   + COALESCE(SUM(dl.delta) OVER (PARTITION BY t.service_type ORDER BY d.day), 0) AS bigint) AS count"
            + " FROM days d"
            + " CROSS JOIN types t"
            + " LEFT JOIN baseline b ON b.service_type = t.service_type"
            + " LEFT JOIN deltas dl ON dl.day = d.day AND dl.service_type = t.service_type"
            + " ORDER BY d.day, t.service_type";

    /**
     * One {@code (day, serviceType, count)} row per day and per type present in the table; an
     * empty service table yields no rows at all (the service layer pre-fills days).
     */
    @Query(value = STATISTICS_SQL, nativeQuery = true)
    List<ServiceCountRow> countServicesPerDay(@Param("since") LocalDate since, @Param("until") LocalDate until);

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

    @Query(value = "SELECT day AS day, COUNT(*) OVER () AS totalDays FROM ("
            + " SELECT DISTINCT CAST(ev AS date) AS day FROM (" + CHANGE_EVENTS_SQL + ") events"
            + ") days ORDER BY day LIMIT :pageSize OFFSET :offset", nativeQuery = true)
    List<ChangeLogDayRow> findChangeLogDayPage(@Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            @Param("pageSize") int pageSize, @Param("offset") long offset);

    @Query(value = "SELECT COUNT(DISTINCT CAST(ev AS date)) FROM (" + CHANGE_EVENTS_SQL + ") events",
            nativeQuery = true)
    long countChangeLogDays(@Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    String MEMBER_CHANGE_COLS =
            "SELECT m.member_class AS memberClass, m.member_code AS memberCode, m.name AS name, ";

    String SUBSYSTEM_CHANGE_COLS =
            "SELECT m.member_class AS memberClass, m.member_code AS memberCode, m.name AS memberName, "
            + "ss.subsystem_code AS subsystemCode, ";

    String SUBSYSTEM_CHANGE_FROM = "FROM subsystem ss JOIN member m ON ss.member_id = m.id ";

    String SERVICE_CHANGE_COLS =
            "SELECT m.member_class AS memberClass, m.member_code AS memberCode, m.name AS memberName, "
            + "ss.subsystem_code AS subsystemCode, s.service_code AS serviceCode, "
            + "s.service_version AS serviceVersion, s.service_type AS serviceType, ";

    String SERVICE_CHANGE_FROM =
            "FROM service s JOIN subsystem ss ON s.subsystem_id = ss.id JOIN member m ON ss.member_id = m.id ";

    @Query(value = MEMBER_CHANGE_COLS + "m.created AS eventTime FROM member m "
            + "WHERE m.created >= :startDate AND m.created < :endDate", nativeQuery = true)
    List<MemberChangeRow> findMembersCreatedBetween(@Param("startDate") LocalDateTime startDate,
                                                     @Param("endDate") LocalDateTime endDate);

    // StatusInfo sets changed == created on create and changed == removed on remove, so without
    // these guards a create/remove would double-count as a modification. The guard must be an
    // equality check against the coinciding event, not window membership: an in-window create
    // does not disqualify a later, distinct change from being reported.
    @Query(value = MEMBER_CHANGE_COLS + "m.changed AS eventTime FROM member m "
            + "WHERE m.changed >= :startDate AND m.changed < :endDate "
            + "AND m.changed <> m.created AND (m.removed IS NULL OR m.changed <> m.removed)", nativeQuery = true)
    List<MemberChangeRow> findMembersModifiedBetween(@Param("startDate") LocalDateTime startDate,
                                                      @Param("endDate") LocalDateTime endDate);

    @Query(value = MEMBER_CHANGE_COLS + "m.removed AS eventTime FROM member m "
            + "WHERE m.removed >= :startDate AND m.removed < :endDate", nativeQuery = true)
    List<MemberChangeRow> findMembersRemovedBetween(@Param("startDate") LocalDateTime startDate,
                                                     @Param("endDate") LocalDateTime endDate);

    @Query(value = SUBSYSTEM_CHANGE_COLS + "ss.created AS eventTime " + SUBSYSTEM_CHANGE_FROM
            + "WHERE ss.created >= :startDate AND ss.created < :endDate", nativeQuery = true)
    List<SubsystemChangeRow> findSubsystemsCreatedBetween(@Param("startDate") LocalDateTime startDate,
                                                           @Param("endDate") LocalDateTime endDate);

    // Same equality-guard rationale as findMembersModifiedBetween.
    @Query(value = SUBSYSTEM_CHANGE_COLS + "ss.changed AS eventTime " + SUBSYSTEM_CHANGE_FROM
            + "WHERE ss.changed >= :startDate AND ss.changed < :endDate "
            + "AND ss.changed <> ss.created AND (ss.removed IS NULL OR ss.changed <> ss.removed)", nativeQuery = true)
    List<SubsystemChangeRow> findSubsystemsModifiedBetween(@Param("startDate") LocalDateTime startDate,
                                                            @Param("endDate") LocalDateTime endDate);

    @Query(value = SUBSYSTEM_CHANGE_COLS + "ss.removed AS eventTime " + SUBSYSTEM_CHANGE_FROM
            + "WHERE ss.removed >= :startDate AND ss.removed < :endDate", nativeQuery = true)
    List<SubsystemChangeRow> findSubsystemsRemovedBetween(@Param("startDate") LocalDateTime startDate,
                                                           @Param("endDate") LocalDateTime endDate);

    @Query(value = SERVICE_CHANGE_COLS + "s.created AS eventTime " + SERVICE_CHANGE_FROM
            + "WHERE s.created >= :startDate AND s.created < :endDate", nativeQuery = true)
    List<ServiceChangeRow> findServicesCreatedBetween(@Param("startDate") LocalDateTime startDate,
                                                       @Param("endDate") LocalDateTime endDate);

    // Same equality-guard rationale as findMembersModifiedBetween.
    @Query(value = SERVICE_CHANGE_COLS + "s.changed AS eventTime " + SERVICE_CHANGE_FROM
            + "WHERE s.changed >= :startDate AND s.changed < :endDate "
            + "AND s.changed <> s.created AND (s.removed IS NULL OR s.changed <> s.removed)", nativeQuery = true)
    List<ServiceChangeRow> findServicesModifiedBetween(@Param("startDate") LocalDateTime startDate,
                                                        @Param("endDate") LocalDateTime endDate);

    @Query(value = SERVICE_CHANGE_COLS + "s.removed AS eventTime " + SERVICE_CHANGE_FROM
            + "WHERE s.removed >= :startDate AND s.removed < :endDate", nativeQuery = true)
    List<ServiceChangeRow> findServicesRemovedBetween(@Param("startDate") LocalDateTime startDate,
                                                       @Param("endDate") LocalDateTime endDate);
}
