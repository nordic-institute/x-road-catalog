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

import org.niis.xroad.catalog.persistence.entity.Subsystem;
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
 * V2 repository for Subsystem. Separate from V1 SubsystemRepository so V2 query changes
 * cannot accidentally alter V1 behavior.
 *
 * Active-only queries must exclude subsystems whose parent member has been removed
 * (parent-cascade check) — spec's active-only contract applies to the whole hierarchy.
 */
public interface SubsystemRepositoryV2 extends CrudRepository<Subsystem, Long>,
        PagingAndSortingRepository<Subsystem, Long> {

    /**
     * Natural-key lookup regardless of removed status.
     */
    @EntityGraph(attributePaths = {"services"}, type = EntityGraph.EntityGraphType.LOAD)
    @Query("SELECT s FROM Subsystem s WHERE s.subsystemCode = :subsystemCode "
            + "AND s.member.xRoadInstance = :xRoadInstance "
            + "AND s.member.memberClass = :memberClass "
            + "AND s.member.memberCode = :memberCode")
    Subsystem findAnyByNaturalKey(@Param("xRoadInstance") String xRoadInstance,
                                  @Param("memberClass") String memberClass,
                                  @Param("memberCode") String memberCode,
                                  @Param("subsystemCode") String subsystemCode);

    /**
     * Active natural-key lookup with parent cascade: subsystem is returned only when
     * neither the subsystem itself nor its parent member has been soft-deleted.
     */
    @EntityGraph(attributePaths = {"services"}, type = EntityGraph.EntityGraphType.LOAD)
    @Query("SELECT s FROM Subsystem s WHERE s.subsystemCode = :subsystemCode "
            + "AND s.member.xRoadInstance = :xRoadInstance "
            + "AND s.member.memberClass = :memberClass "
            + "AND s.member.memberCode = :memberCode "
            + "AND s.statusInfo.removed IS NULL "
            + "AND s.member.statusInfo.removed IS NULL")
    Subsystem findActiveByNaturalKey(@Param("xRoadInstance") String xRoadInstance,
                                     @Param("memberClass") String memberClass,
                                     @Param("memberCode") String memberCode,
                                     @Param("subsystemCode") String subsystemCode);

    /**
     * Active natural-key lookup pre-fetching the service aggregate descriptor collections.
     * Used by the V2 service-list browse endpoint to avoid N+1 queries when iterating
     * {@code subsystem.getActiveServices()} and walking each service's wsdls/openApis/rests.
     * Endpoints are deliberately omitted from the graph: the aggregate response carries
     * version summaries only (no endpoints) per spec §6.3; endpoints load on the
     * {@code /versions/{v}} route via {@code ServiceRepositoryV2}.
     */
    @EntityGraph(attributePaths = {"services", "services.wsdls", "services.openApis", "services.rests"},
            type = EntityGraph.EntityGraphType.LOAD)
    @Query("SELECT s FROM Subsystem s WHERE s.subsystemCode = :subsystemCode "
            + "AND s.member.xRoadInstance = :xRoadInstance "
            + "AND s.member.memberClass = :memberClass "
            + "AND s.member.memberCode = :memberCode "
            + "AND s.statusInfo.removed IS NULL "
            + "AND s.member.statusInfo.removed IS NULL")
    Subsystem findActiveByNaturalKeyWithServices(@Param("xRoadInstance") String xRoadInstance,
                                                 @Param("memberClass") String memberClass,
                                                 @Param("memberCode") String memberCode,
                                                 @Param("subsystemCode") String subsystemCode);

    /**
     * Natural-key lookup including removed rows, pre-fetching the service aggregate descriptor
     * collections. Counterpart to {@link #findActiveByNaturalKeyWithServices} for the
     * {@code includeRemoved=true} branch of the V2 service-list browse endpoint.
     */
    @EntityGraph(attributePaths = {"services", "services.wsdls", "services.openApis", "services.rests"},
            type = EntityGraph.EntityGraphType.LOAD)
    @Query("SELECT s FROM Subsystem s WHERE s.subsystemCode = :subsystemCode "
            + "AND s.member.xRoadInstance = :xRoadInstance "
            + "AND s.member.memberClass = :memberClass "
            + "AND s.member.memberCode = :memberCode")
    Subsystem findAnyByNaturalKeyWithServices(@Param("xRoadInstance") String xRoadInstance,
                                              @Param("memberClass") String memberClass,
                                              @Param("memberCode") String memberCode,
                                              @Param("subsystemCode") String subsystemCode);

    @Query("SELECT s FROM Subsystem s WHERE "
            + "LOWER(s.subsystemCode) LIKE LOWER(CONCAT('%', :q, '%')) "
            + "AND (:activeOnly = false OR (s.statusInfo.removed IS NULL "
            + "                              AND s.member.statusInfo.removed IS NULL))")
    Page<Subsystem> searchByText(@Param("q") String query,
                                 @Param("activeOnly") boolean activeOnly,
                                 Pageable pageable);

    @EntityGraph(attributePaths = {"services"}, type = EntityGraph.EntityGraphType.LOAD)
    @Query("SELECT s FROM Subsystem s WHERE "
            + "(:memberClass IS NULL OR s.member.memberClass = :memberClass) "
            + "AND (:activeOnly = false OR (s.statusInfo.removed IS NULL "
            + "                              AND s.member.statusInfo.removed IS NULL))")
    Page<Subsystem> findForList(@Param("memberClass") String memberClass,
                                @Param("activeOnly") boolean activeOnly,
                                Pageable pageable);

    @Query("SELECT s FROM Subsystem s WHERE "
            + "s.statusInfo.created >= :startDate AND s.statusInfo.created < :endDate")
    List<Subsystem> findCreatedBetween(@Param("startDate") LocalDateTime startDate,
                                       @Param("endDate") LocalDateTime endDate);

    @Query("SELECT s FROM Subsystem s WHERE "
            + "s.statusInfo.changed >= :startDate AND s.statusInfo.changed < :endDate "
            + "AND (s.statusInfo.created < :startDate OR s.statusInfo.created >= :endDate) "
            + "AND (s.statusInfo.removed IS NULL "
            + "     OR s.statusInfo.removed < :startDate "
            + "     OR s.statusInfo.removed >= :endDate)")
    List<Subsystem> findChangedBetween(@Param("startDate") LocalDateTime startDate,
                                       @Param("endDate") LocalDateTime endDate);

    @Query("SELECT s FROM Subsystem s WHERE "
            + "s.statusInfo.removed >= :startDate AND s.statusInfo.removed < :endDate")
    List<Subsystem> findRemovedBetween(@Param("startDate") LocalDateTime startDate,
                                       @Param("endDate") LocalDateTime endDate);
}
