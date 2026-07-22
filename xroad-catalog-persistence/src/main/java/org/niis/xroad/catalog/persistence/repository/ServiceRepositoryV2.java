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

import org.niis.xroad.catalog.persistence.repository.projection.ServiceAggregateRow;
import org.niis.xroad.catalog.persistence.repository.projection.ServiceVersionRow;
import org.niis.xroad.catalog.persistence.v2entity.ServiceV2;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * V2 read-model repository for {@link ServiceV2}. Every query is instance-scoped
 * ({@code s.subsystem.member.xRoadInstance = :xRoadInstance}), unlike the V1-era
 * {@code findAggregatesForList} it replaces. Active-only queries apply the parent-cascade check:
 * a service is active only when neither it, its parent subsystem, nor its parent member has been
 * soft-deleted. Descriptor blobs (WSDL/OpenAPI {@code data}) never ride along these queries; see
 * {@link DescriptorRepositoryV2}.
 */
public interface ServiceRepositoryV2 extends Repository<ServiceV2, Long>, V2ReadModelRepository {

    String ACTIVE_CASCADE = "s.statusInfo.removed IS NULL AND s.subsystem.statusInfo.removed IS NULL "
            + "AND s.subsystem.member.statusInfo.removed IS NULL";

    String NATURAL_KEY = "s.serviceCode = :serviceCode AND s.subsystem.subsystemCode = :subsystemCode "
            + "AND s.subsystem.member.memberCode = :memberCode AND s.subsystem.member.memberClass = :memberClass "
            + "AND s.subsystem.member.xRoadInstance = :xRoadInstance";

    String VERSION_ROW_SELECT = "SELECT s.subsystem.member.memberClass AS memberClass, "
            + "s.subsystem.member.memberCode AS memberCode, s.subsystem.member.name AS memberName, "
            + "s.subsystem.subsystemCode AS subsystemCode, s.subsystem.id AS subsystemId, "
            + "s.serviceCode AS serviceCode, s.serviceVersion AS serviceVersion, s.serviceType AS serviceType, "
            + "s.statusInfo.created AS created, s.statusInfo.changed AS changed, "
            + "s.statusInfo.fetched AS fetched, s.statusInfo.removed AS removed FROM ServiceV2 s ";

    /**
     * One row per unique {@code serviceCode} within a subsystem (a "service aggregate") for the V2
     * service list endpoint. {@code s.subsystem.id} is added to the {@code GROUP BY} to key the
     * subsequent version batch fetch; it does not widen the grouping because it is functionally
     * determined by the natural-key tuple already being grouped on. {@code serviceType} filtering
     * uses a per-group existence test via {@code SUM(CASE ...) > 0} because JPQL has no portable
     * {@code BOOL_OR} aggregate.
     */
    @Query("SELECT s.subsystem.member.memberClass AS memberClass, s.subsystem.member.memberCode AS memberCode, "
            + "s.subsystem.subsystemCode AS subsystemCode, s.serviceCode AS serviceCode, s.subsystem.id AS subsystemId "
            + "FROM ServiceV2 s WHERE s.subsystem.member.xRoadInstance = :xRoadInstance "
            + "AND (:memberClass IS NULL OR s.subsystem.member.memberClass = :memberClass) "
            + "AND " + ACTIVE_CASCADE + " "
            + "GROUP BY s.subsystem.member.memberClass, s.subsystem.member.memberCode, "
            + "s.subsystem.subsystemCode, s.serviceCode, s.subsystem.id "
            + "HAVING (:serviceType IS NULL OR SUM(CASE WHEN s.serviceType = :serviceType THEN 1 ELSE 0 END) > 0) "
            + "ORDER BY s.serviceCode ASC, s.subsystem.member.memberClass ASC, "
            + "s.subsystem.member.memberCode ASC, s.subsystem.subsystemCode ASC")
    List<ServiceAggregateRow> findActiveAggregatesForList(@Param("xRoadInstance") String xRoadInstance,
            @Param("memberClass") String memberClass, @Param("serviceType") String serviceType, Pageable pageable);

    /**
     * Count of aggregates matching the same filters as {@link #findActiveAggregatesForList}. Must
     * always equal the unpaged row count of that method — pinned by the parity matrix test in
     * {@code ServiceRepositoryV2PgTest}. Native SQL so the count is a plain COUNT over the same
     * GROUP BY the rows query uses, instead of hashing a CONCAT of the natural key per row.
     */
    @Query(value = "SELECT COUNT(*) FROM ("
            + " SELECT 1 FROM service s"
            + " JOIN subsystem sub ON s.subsystem_id = sub.id"
            + " JOIN member m ON sub.member_id = m.id"
            + " WHERE m.x_road_instance = :xRoadInstance"
            + "   AND (:memberClass IS NULL OR m.member_class = :memberClass)"
            + "   AND s.removed IS NULL AND sub.removed IS NULL AND m.removed IS NULL"
            + " GROUP BY m.member_class, m.member_code, sub.subsystem_code, s.service_code, s.subsystem_id"
            + " HAVING (:serviceType IS NULL"
            + "   OR SUM(CASE WHEN s.service_type = :serviceType THEN 1 ELSE 0 END) > 0)"
            + ") g", nativeQuery = true)
    long countActiveAggregatesForList(@Param("xRoadInstance") String xRoadInstance,
            @Param("memberClass") String memberClass, @Param("serviceType") String serviceType);

    /**
     * Active version rows for a page of aggregates, keyed by {@code (subsystemId, serviceCode)}.
     * JPQL has no portable row-value {@code IN}, so this over-selects the cross product of the two
     * IN-lists rather than the exact pair set; the caller (service layer) must discard rows whose
     * {@code (subsystemId, serviceCode)} pair was not actually requested.
     */
    @Query(VERSION_ROW_SELECT + "WHERE s.subsystem.id IN :subsystemIds AND s.serviceCode IN :serviceCodes "
            + "AND s.statusInfo.removed IS NULL ORDER BY s.serviceCode, s.serviceVersion")
    List<ServiceVersionRow> findActiveVersionRowsForKeys(@Param("subsystemIds") Collection<Long> subsystemIds,
            @Param("serviceCodes") Collection<String> serviceCodes);

    @Query(VERSION_ROW_SELECT + "WHERE " + NATURAL_KEY + " AND " + ACTIVE_CASCADE)
    List<ServiceVersionRow> findActiveVersionRowsForService(@Param("xRoadInstance") String xRoadInstance,
            @Param("memberClass") String memberClass, @Param("memberCode") String memberCode,
            @Param("subsystemCode") String subsystemCode, @Param("serviceCode") String serviceCode);

    @Query(VERSION_ROW_SELECT + "WHERE s.subsystem.member.xRoadInstance = :xRoadInstance "
            + "AND s.subsystem.member.memberClass = :memberClass AND s.subsystem.member.memberCode = :memberCode "
            + "AND s.subsystem.subsystemCode = :subsystemCode AND " + ACTIVE_CASCADE
            + " ORDER BY s.serviceCode, s.serviceVersion")
    List<ServiceVersionRow> findActiveVersionRowsForSubsystem(@Param("xRoadInstance") String xRoadInstance,
            @Param("memberClass") String memberClass, @Param("memberCode") String memberCode,
            @Param("subsystemCode") String subsystemCode);

    /**
     * Active versions for one service natural key (all {@code serviceVersion} rows, including the
     * null-version case) with the {@code endpoints} collection pre-fetched so detail-endpoint
     * traversal does not N+1.
     */
    @EntityGraph(attributePaths = {"endpoints"})
    @Query("SELECT s FROM ServiceV2 s WHERE " + NATURAL_KEY + " AND " + ACTIVE_CASCADE)
    List<ServiceV2> findActiveVersionsByNaturalKey(@Param("xRoadInstance") String xRoadInstance,
            @Param("memberClass") String memberClass, @Param("memberCode") String memberCode,
            @Param("subsystemCode") String subsystemCode, @Param("serviceCode") String serviceCode);

    @EntityGraph(attributePaths = {"endpoints"})
    @Query("SELECT s FROM ServiceV2 s WHERE " + NATURAL_KEY + " AND s.serviceVersion = :serviceVersion AND "
            + ACTIVE_CASCADE)
    Optional<ServiceV2> findActiveVersionByNaturalKey(@Param("xRoadInstance") String xRoadInstance,
            @Param("memberClass") String memberClass, @Param("memberCode") String memberCode,
            @Param("subsystemCode") String subsystemCode, @Param("serviceCode") String serviceCode,
            @Param("serviceVersion") String serviceVersion);

    @EntityGraph(attributePaths = {"endpoints"})
    @Query("SELECT s FROM ServiceV2 s WHERE " + NATURAL_KEY + " AND s.serviceVersion IS NULL AND " + ACTIVE_CASCADE)
    Optional<ServiceV2> findActiveNullVersionByNaturalKey(@Param("xRoadInstance") String xRoadInstance,
            @Param("memberClass") String memberClass, @Param("memberCode") String memberCode,
            @Param("subsystemCode") String subsystemCode, @Param("serviceCode") String serviceCode);

    @Query("SELECT COUNT(s) > 0 FROM ServiceV2 s WHERE " + NATURAL_KEY + " AND " + ACTIVE_CASCADE)
    boolean existsActiveByNaturalKey(@Param("xRoadInstance") String xRoadInstance,
            @Param("memberClass") String memberClass, @Param("memberCode") String memberCode,
            @Param("subsystemCode") String subsystemCode, @Param("serviceCode") String serviceCode);

    @Query("SELECT COUNT(s) > 0 FROM ServiceV2 s WHERE " + NATURAL_KEY
            + " AND s.serviceVersion = :serviceVersion AND " + ACTIVE_CASCADE)
    boolean existsActiveVersionByNaturalKey(@Param("xRoadInstance") String xRoadInstance,
            @Param("memberClass") String memberClass, @Param("memberCode") String memberCode,
            @Param("subsystemCode") String subsystemCode, @Param("serviceCode") String serviceCode,
            @Param("serviceVersion") String serviceVersion);

    @Query("SELECT COUNT(s) > 0 FROM ServiceV2 s WHERE " + NATURAL_KEY
            + " AND s.serviceVersion IS NULL AND " + ACTIVE_CASCADE)
    boolean existsActiveNullVersionByNaturalKey(@Param("xRoadInstance") String xRoadInstance,
            @Param("memberClass") String memberClass, @Param("memberCode") String memberCode,
            @Param("subsystemCode") String subsystemCode, @Param("serviceCode") String serviceCode);
}
