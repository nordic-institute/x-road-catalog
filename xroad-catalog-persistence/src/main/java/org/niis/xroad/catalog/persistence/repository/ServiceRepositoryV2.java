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

import org.niis.xroad.catalog.persistence.entity.Service;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.PagingAndSortingRepository;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * V2 repository for Service. Separate from V1 ServiceRepository so V2 query changes
 * cannot accidentally alter V1 behavior.
 *
 * Active-only queries exclude services whose parent subsystem or member has been removed
 * (parent-cascade check) — spec's active-only contract applies to the whole hierarchy.
 */
public interface ServiceRepositoryV2 extends CrudRepository<Service, Long>,
        PagingAndSortingRepository<Service, Long> {

    /**
     * All versions for a (member, subsystem, serviceCode) regardless of removed state.
     * Intended for `includeRemoved=true` lookups and for {@code getVersions} when all rows are needed.
     */
    @EntityGraph(attributePaths = {"wsdls", "openApis", "rests", "endpoints"}, type = EntityGraph.EntityGraphType.LOAD)
    @Query("SELECT s FROM Service s WHERE s.serviceCode = :serviceCode "
            + "AND s.subsystem.subsystemCode = :subsystemCode "
            + "AND s.subsystem.member.memberCode = :memberCode "
            + "AND s.subsystem.member.memberClass = :memberClass "
            + "AND s.subsystem.member.xRoadInstance = :xRoadInstance")
    List<Service> findAnyByMemberServiceAndSubsystem(@Param("xRoadInstance") String xRoadInstance,
                                                    @Param("memberClass") String memberClass,
                                                    @Param("memberCode") String memberCode,
                                                    @Param("serviceCode") String serviceCode,
                                                    @Param("subsystemCode") String subsystemCode);

    /**
     * Active versions for a (member, subsystem, serviceCode) with parent cascade.
     * Returns only service rows whose own {@code removed} is null AND whose parent subsystem
     * and parent member have not been removed.
     */
    @EntityGraph(attributePaths = {"wsdls", "openApis", "rests", "endpoints"}, type = EntityGraph.EntityGraphType.LOAD)
    @Query("SELECT s FROM Service s WHERE s.serviceCode = :serviceCode "
            + "AND s.subsystem.subsystemCode = :subsystemCode "
            + "AND s.subsystem.member.memberCode = :memberCode "
            + "AND s.subsystem.member.memberClass = :memberClass "
            + "AND s.subsystem.member.xRoadInstance = :xRoadInstance "
            + "AND s.statusInfo.removed IS NULL "
            + "AND s.subsystem.statusInfo.removed IS NULL "
            + "AND s.subsystem.member.statusInfo.removed IS NULL")
    List<Service> findActiveByMemberServiceAndSubsystem(@Param("xRoadInstance") String xRoadInstance,
                                                       @Param("memberClass") String memberClass,
                                                       @Param("memberCode") String memberCode,
                                                       @Param("serviceCode") String serviceCode,
                                                       @Param("subsystemCode") String subsystemCode);

    /**
     * Exact version lookup regardless of removed state.
     */
    @EntityGraph(attributePaths = {"wsdls", "openApis", "rests", "endpoints"}, type = EntityGraph.EntityGraphType.LOAD)
    @Query("SELECT s FROM Service s WHERE s.serviceCode = :serviceCode "
            + "AND s.subsystem.subsystemCode = :subsystemCode "
            + "AND s.subsystem.member.memberCode = :memberCode "
            + "AND s.subsystem.member.memberClass = :memberClass "
            + "AND s.subsystem.member.xRoadInstance = :xRoadInstance "
            + "AND s.serviceVersion = :serviceVersion")
    Service findAnyByMemberServiceAndSubsystemAndVersion(@Param("xRoadInstance") String xRoadInstance,
                                                        @Param("memberClass") String memberClass,
                                                        @Param("memberCode") String memberCode,
                                                        @Param("serviceCode") String serviceCode,
                                                        @Param("subsystemCode") String subsystemCode,
                                                        @Param("serviceVersion") String serviceVersion);

    /**
     * Exact version lookup with parent cascade (active service + active subsystem + active member).
     */
    @EntityGraph(attributePaths = {"wsdls", "openApis", "rests", "endpoints"}, type = EntityGraph.EntityGraphType.LOAD)
    @Query("SELECT s FROM Service s WHERE s.serviceCode = :serviceCode "
            + "AND s.subsystem.subsystemCode = :subsystemCode "
            + "AND s.subsystem.member.memberCode = :memberCode "
            + "AND s.subsystem.member.memberClass = :memberClass "
            + "AND s.subsystem.member.xRoadInstance = :xRoadInstance "
            + "AND s.serviceVersion = :serviceVersion "
            + "AND s.statusInfo.removed IS NULL "
            + "AND s.subsystem.statusInfo.removed IS NULL "
            + "AND s.subsystem.member.statusInfo.removed IS NULL")
    Service findActiveByMemberServiceAndSubsystemAndVersion(@Param("xRoadInstance") String xRoadInstance,
                                                            @Param("memberClass") String memberClass,
                                                            @Param("memberCode") String memberCode,
                                                            @Param("serviceCode") String serviceCode,
                                                            @Param("subsystemCode") String subsystemCode,
                                                            @Param("serviceVersion") String serviceVersion);

    /**
     * Null-version lookup regardless of removed state.
     */
    @EntityGraph(attributePaths = {"wsdls", "openApis", "rests", "endpoints"}, type = EntityGraph.EntityGraphType.LOAD)
    @Query("SELECT s FROM Service s WHERE s.serviceCode = :serviceCode "
            + "AND s.subsystem.subsystemCode = :subsystemCode "
            + "AND s.subsystem.member.memberCode = :memberCode "
            + "AND s.subsystem.member.memberClass = :memberClass "
            + "AND s.subsystem.member.xRoadInstance = :xRoadInstance "
            + "AND s.serviceVersion IS NULL")
    Service findAnyNullVersionByNaturalKey(@Param("xRoadInstance") String xRoadInstance,
                                           @Param("memberClass") String memberClass,
                                           @Param("memberCode") String memberCode,
                                           @Param("serviceCode") String serviceCode,
                                           @Param("subsystemCode") String subsystemCode);

    /**
     * Null-version lookup with parent cascade (active service + active subsystem + active member).
     */
    @EntityGraph(attributePaths = {"wsdls", "openApis", "rests", "endpoints"}, type = EntityGraph.EntityGraphType.LOAD)
    @Query("SELECT s FROM Service s WHERE s.serviceCode = :serviceCode "
            + "AND s.subsystem.subsystemCode = :subsystemCode "
            + "AND s.subsystem.member.memberCode = :memberCode "
            + "AND s.subsystem.member.memberClass = :memberClass "
            + "AND s.subsystem.member.xRoadInstance = :xRoadInstance "
            + "AND s.serviceVersion IS NULL "
            + "AND s.statusInfo.removed IS NULL "
            + "AND s.subsystem.statusInfo.removed IS NULL "
            + "AND s.subsystem.member.statusInfo.removed IS NULL")
    Service findActiveNullVersionByNaturalKey(@Param("xRoadInstance") String xRoadInstance,
                                              @Param("memberClass") String memberClass,
                                              @Param("memberCode") String memberCode,
                                              @Param("serviceCode") String serviceCode,
                                              @Param("subsystemCode") String subsystemCode);

    @Query("SELECT s FROM Service s WHERE "
            + "LOWER(s.serviceCode) LIKE LOWER(CONCAT('%', :q, '%')) "
            + "AND (:activeOnly = false OR (s.statusInfo.removed IS NULL "
            + "                              AND s.subsystem.statusInfo.removed IS NULL "
            + "                              AND s.subsystem.member.statusInfo.removed IS NULL))")
    Page<Service> searchByText(@Param("q") String query,
                               @Param("activeOnly") boolean activeOnly,
                               Pageable pageable);

    /**
     * Returns service aggregates (one row per unique serviceCode within a subsystem) for the V2 list endpoint.
     * Each row: [memberClass, memberCode, subsystemCode, serviceCode]. serviceType filtering matches V2
     * converter classification (priority: SOAP &gt; OPENAPI &gt; REST); descriptor-less versions are classified
     * as REST. Filtering is applied in HAVING so that ALL versions of a matching aggregate are counted.
     *
     * Active-only mode filters services whose own `removed` is null AND whose parent subsystem and member are
     * not removed — the soft-delete cascade is enforced at query time.
     *
     * Ordering is fixed at serviceCode ascending with the full GROUP BY tuple appended as a deterministic
     * tie-break (spec §8). The endpoint does not expose sortBy/sortOrder; callers pass an unsorted Pageable.
     */
    @Query("SELECT s.subsystem.member.memberClass, s.subsystem.member.memberCode, "
            + "s.subsystem.subsystemCode, s.serviceCode "
            + "FROM Service s WHERE "
            + "(:memberClass IS NULL OR s.subsystem.member.memberClass = :memberClass) "
            + "AND (:activeOnly = false OR (s.statusInfo.removed IS NULL "
            + "                              AND s.subsystem.statusInfo.removed IS NULL "
            + "                              AND s.subsystem.member.statusInfo.removed IS NULL)) "
            + "GROUP BY s.subsystem.member.memberClass, s.subsystem.member.memberCode, "
            + "s.subsystem.subsystemCode, s.serviceCode "
            + "HAVING (:serviceType IS NULL "
            + "        OR (:serviceType = 'SOAP' "
            + "            AND SUM(CASE WHEN EXISTS (SELECT w FROM Wsdl w "
            + "                                       WHERE w.service = s AND w.statusInfo.removed IS NULL) "
            + "                         THEN 1 ELSE 0 END) > 0) "
            + "        OR (:serviceType = 'OPENAPI' "
            + "            AND SUM(CASE WHEN (NOT EXISTS (SELECT w FROM Wsdl w "
            + "                                            WHERE w.service = s AND w.statusInfo.removed IS NULL)) "
            + "                          AND EXISTS (SELECT o FROM OpenApi o "
            + "                                      WHERE o.service = s AND o.statusInfo.removed IS NULL) "
            + "                         THEN 1 ELSE 0 END) > 0) "
            + "        OR (:serviceType = 'REST' "
            + "            AND SUM(CASE WHEN (NOT EXISTS (SELECT w FROM Wsdl w "
            + "                                            WHERE w.service = s AND w.statusInfo.removed IS NULL)) "
            + "                          AND (NOT EXISTS (SELECT o FROM OpenApi o "
            + "                                           WHERE o.service = s AND o.statusInfo.removed IS NULL)) "
            + "                         THEN 1 ELSE 0 END) > 0)) "
            + "ORDER BY s.serviceCode ASC, s.subsystem.member.memberClass ASC, "
            + "s.subsystem.member.memberCode ASC, s.subsystem.subsystemCode ASC")
    List<Object[]> findAggregatesForList(@Param("memberClass") String memberClass,
                                         @Param("serviceType") String serviceType,
                                         @Param("activeOnly") boolean activeOnly,
                                         Pageable pageable);

    /**
     * Count of aggregates matching the same filters as findAggregatesForList. Descriptor presence
     * is evaluated against non-removed rows only, to match the converter-side {@code resolveType}.
     */
    @Query("SELECT COUNT(DISTINCT CONCAT(s.subsystem.member.memberClass, '|', "
            + "s.subsystem.member.memberCode, '|', s.subsystem.subsystemCode, '|', s.serviceCode)) "
            + "FROM Service s WHERE "
            + "(:memberClass IS NULL OR s.subsystem.member.memberClass = :memberClass) "
            + "AND (:activeOnly = false OR (s.statusInfo.removed IS NULL "
            + "                              AND s.subsystem.statusInfo.removed IS NULL "
            + "                              AND s.subsystem.member.statusInfo.removed IS NULL)) "
            + "AND (:serviceType IS NULL "
            + "     OR (:serviceType = 'SOAP' AND EXISTS ("
            + "         SELECT s2 FROM Service s2 WHERE s2.subsystem = s.subsystem "
            + "         AND s2.serviceCode = s.serviceCode "
            + "         AND (:activeOnly = false OR s2.statusInfo.removed IS NULL) "
            + "         AND EXISTS (SELECT w FROM Wsdl w "
            + "                     WHERE w.service = s2 AND w.statusInfo.removed IS NULL))) "
            + "     OR (:serviceType = 'OPENAPI' AND EXISTS ("
            + "         SELECT s2 FROM Service s2 WHERE s2.subsystem = s.subsystem "
            + "         AND s2.serviceCode = s.serviceCode "
            + "         AND (:activeOnly = false OR s2.statusInfo.removed IS NULL) "
            + "         AND (NOT EXISTS (SELECT w FROM Wsdl w "
            + "                          WHERE w.service = s2 AND w.statusInfo.removed IS NULL)) "
            + "         AND EXISTS (SELECT o FROM OpenApi o "
            + "                     WHERE o.service = s2 AND o.statusInfo.removed IS NULL))) "
            + "     OR (:serviceType = 'REST' AND EXISTS ("
            + "         SELECT s2 FROM Service s2 WHERE s2.subsystem = s.subsystem "
            + "         AND s2.serviceCode = s.serviceCode "
            + "         AND (:activeOnly = false OR s2.statusInfo.removed IS NULL) "
            + "         AND (NOT EXISTS (SELECT w FROM Wsdl w "
            + "                          WHERE w.service = s2 AND w.statusInfo.removed IS NULL)) "
            + "         AND (NOT EXISTS (SELECT o FROM OpenApi o "
            + "                          WHERE o.service = s2 AND o.statusInfo.removed IS NULL)))))")
    long countAggregatesForList(@Param("memberClass") String memberClass,
                                @Param("serviceType") String serviceType,
                                @Param("activeOnly") boolean activeOnly);

    @Query("SELECT s FROM Service s WHERE "
            + "s.statusInfo.created >= :startDate AND s.statusInfo.created < :endDate")
    List<Service> findCreatedBetween(@Param("startDate") LocalDateTime startDate,
                                     @Param("endDate") LocalDateTime endDate);

    @Query("SELECT s FROM Service s WHERE "
            + "s.statusInfo.changed >= :startDate AND s.statusInfo.changed < :endDate "
            + "AND (s.statusInfo.created < :startDate OR s.statusInfo.created >= :endDate) "
            + "AND (s.statusInfo.removed IS NULL "
            + "     OR s.statusInfo.removed < :startDate "
            + "     OR s.statusInfo.removed >= :endDate)")
    List<Service> findChangedBetween(@Param("startDate") LocalDateTime startDate,
                                     @Param("endDate") LocalDateTime endDate);

    @Query("SELECT s FROM Service s WHERE "
            + "s.statusInfo.removed >= :startDate AND s.statusInfo.removed < :endDate")
    List<Service> findRemovedBetween(@Param("startDate") LocalDateTime startDate,
                                     @Param("endDate") LocalDateTime endDate);
}
