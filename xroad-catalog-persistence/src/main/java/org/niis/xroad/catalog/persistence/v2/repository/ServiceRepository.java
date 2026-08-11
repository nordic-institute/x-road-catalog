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

import org.niis.xroad.catalog.persistence.v2.repository.projection.ServiceAggregateRow;
import org.niis.xroad.catalog.persistence.v2.repository.projection.ServiceVersionRow;
import org.niis.xroad.catalog.persistence.v2.entity.Service;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * V2 read-model repository for {@link Service}. Every query is instance-scoped; activeness comes
 * from the mapped {@code active_service} view (parent cascade included), so queries carry no
 * removed predicates. Descriptor blobs are served by {@link DescriptorRepository} and never ride
 * along these queries.
 *
 * <p>Named {@code serviceRepositoryV2} because the default bean name would collide with the V1
 * {@code ServiceRepository} when both base packages are scanned, silently dropping one bean.
 */
@Repository("serviceRepositoryV2")
public interface ServiceRepository extends org.springframework.data.repository.Repository<Service, Long>,
        ReadModelRepository {

    String NATURAL_KEY = "s.serviceCode = :serviceCode AND s.subsystem.subsystemCode = :subsystemCode "
            + "AND s.subsystem.member.memberCode = :memberCode AND s.subsystem.member.memberClass = :memberClass "
            + "AND s.subsystem.member.xRoadInstance = :xRoadInstance";

    String VERSION_ROW_SELECT = "SELECT s.subsystem.member.memberClass AS memberClass, "
            + "s.subsystem.member.memberCode AS memberCode, s.subsystem.member.name AS memberName, "
            + "s.subsystem.subsystemCode AS subsystemCode, s.subsystem.id AS subsystemId, "
            + "s.serviceCode AS serviceCode, s.serviceVersion AS serviceVersion, s.serviceType AS serviceType, "
            + "s.statusInfo.created AS created, s.statusInfo.changed AS changed, "
            + "s.statusInfo.fetched AS fetched FROM ServiceV2 s ";

    /**
     * One row per unique {@code serviceCode} within a subsystem. {@code s.subsystem.id} keys the
     * follow-up version batch fetch without widening the grouping (functionally determined by the
     * grouped natural key). {@code serviceType} filtering uses {@code SUM(CASE ...) > 0} because
     * JPQL has no portable {@code BOOL_OR}.
     */
    @Query("SELECT s.subsystem.member.memberClass AS memberClass, s.subsystem.member.memberCode AS memberCode, "
            + "s.subsystem.subsystemCode AS subsystemCode, s.serviceCode AS serviceCode, s.subsystem.id AS subsystemId "
            + "FROM ServiceV2 s WHERE s.subsystem.member.xRoadInstance = :xRoadInstance "
            + "AND (:memberClass IS NULL OR s.subsystem.member.memberClass = :memberClass) "
            + "GROUP BY s.subsystem.member.memberClass, s.subsystem.member.memberCode, "
            + "s.subsystem.subsystemCode, s.serviceCode, s.subsystem.id "
            + "HAVING (:serviceType IS NULL OR SUM(CASE WHEN s.serviceType = :serviceType THEN 1 ELSE 0 END) > 0) "
            + "ORDER BY s.serviceCode ASC, s.subsystem.member.memberClass ASC, "
            + "s.subsystem.member.memberCode ASC, s.subsystem.subsystemCode ASC")
    List<ServiceAggregateRow> findActiveAggregatesForList(@Param("xRoadInstance") String xRoadInstance,
            @Param("memberClass") String memberClass, @Param("serviceType") String serviceType, Pageable pageable);

    /**
     * Count of aggregates matching the same filters as {@link #findActiveAggregatesForList}; must
     * equal its unpaged row count. Native SQL so it is a plain COUNT over the same GROUP BY.
     */
    @Query(value = "SELECT COUNT(*) FROM ("
            + " SELECT 1 FROM active_service s"
            + " JOIN active_subsystem sub ON s.subsystem_id = sub.id"
            + " JOIN active_member m ON sub.member_id = m.id"
            + " WHERE m.x_road_instance = :xRoadInstance"
            + "   AND (:memberClass IS NULL OR m.member_class = :memberClass)"
            + " GROUP BY m.member_class, m.member_code, sub.subsystem_code, s.service_code, s.subsystem_id"
            + " HAVING (:serviceType IS NULL"
            + "   OR SUM(CASE WHEN s.service_type = :serviceType THEN 1 ELSE 0 END) > 0)"
            + ") g", nativeQuery = true)
    long countActiveAggregatesForList(@Param("xRoadInstance") String xRoadInstance,
            @Param("memberClass") String memberClass, @Param("serviceType") String serviceType);

    /**
     * Active version rows for a page of aggregates. JPQL has no portable row-value {@code IN}, so
     * the two IN-lists over-select the cross product; callers must discard rows whose
     * {@code (subsystemId, serviceCode)} pair is absent from the request.
     */
    @Query(VERSION_ROW_SELECT + "WHERE s.subsystem.id IN :subsystemIds AND s.serviceCode IN :serviceCodes "
            + "ORDER BY s.serviceCode, s.serviceVersion")
    List<ServiceVersionRow> findActiveVersionRowsForKeys(@Param("subsystemIds") Collection<Long> subsystemIds,
            @Param("serviceCodes") Collection<String> serviceCodes);

    @Query(VERSION_ROW_SELECT + "WHERE " + NATURAL_KEY)
    List<ServiceVersionRow> findActiveVersionRowsForService(@Param("xRoadInstance") String xRoadInstance,
            @Param("memberClass") String memberClass, @Param("memberCode") String memberCode,
            @Param("subsystemCode") String subsystemCode, @Param("serviceCode") String serviceCode);

    @Query(VERSION_ROW_SELECT + "WHERE s.subsystem.member.xRoadInstance = :xRoadInstance "
            + "AND s.subsystem.member.memberClass = :memberClass AND s.subsystem.member.memberCode = :memberCode "
            + "AND s.subsystem.subsystemCode = :subsystemCode "
            + "ORDER BY s.serviceCode, s.serviceVersion")
    List<ServiceVersionRow> findActiveVersionRowsForSubsystem(@Param("xRoadInstance") String xRoadInstance,
            @Param("memberClass") String memberClass, @Param("memberCode") String memberCode,
            @Param("subsystemCode") String subsystemCode);

    /**
     * All active versions for one natural key (including null version), with {@code endpoints}
     * pre-fetched to avoid N+1.
     */
    @EntityGraph(attributePaths = {"endpoints"})
    @Query("SELECT s FROM ServiceV2 s WHERE " + NATURAL_KEY)
    List<Service> findActiveVersionsByNaturalKey(@Param("xRoadInstance") String xRoadInstance,
            @Param("memberClass") String memberClass, @Param("memberCode") String memberCode,
            @Param("subsystemCode") String subsystemCode, @Param("serviceCode") String serviceCode);

    @EntityGraph(attributePaths = {"endpoints"})
    @Query("SELECT s FROM ServiceV2 s WHERE " + NATURAL_KEY + " AND s.serviceVersion = :serviceVersion")
    Optional<Service> findActiveVersionByNaturalKey(@Param("xRoadInstance") String xRoadInstance,
            @Param("memberClass") String memberClass, @Param("memberCode") String memberCode,
            @Param("subsystemCode") String subsystemCode, @Param("serviceCode") String serviceCode,
            @Param("serviceVersion") String serviceVersion);

    @EntityGraph(attributePaths = {"endpoints"})
    @Query("SELECT s FROM ServiceV2 s WHERE " + NATURAL_KEY + " AND s.serviceVersion IS NULL")
    Optional<Service> findActiveNullVersionByNaturalKey(@Param("xRoadInstance") String xRoadInstance,
            @Param("memberClass") String memberClass, @Param("memberCode") String memberCode,
            @Param("subsystemCode") String subsystemCode, @Param("serviceCode") String serviceCode);
}
