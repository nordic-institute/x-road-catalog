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

import org.niis.xroad.catalog.persistence.entity.Member;
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
 * V2 repository for Member. Separate from V1 MemberRepository so V2 query changes
 * cannot accidentally alter V1 behavior.
 */
public interface MemberRepositoryV2 extends CrudRepository<Member, Long>, PagingAndSortingRepository<Member, Long> {

    /**
     * Natural-key lookup regardless of removed status.
     */
    @EntityGraph(attributePaths = {"subsystems", "subsystems.services"}, type = EntityGraph.EntityGraphType.LOAD)
    @Query("SELECT m FROM Member m WHERE m.xRoadInstance = :xRoadInstance "
            + "AND m.memberClass = :memberClass "
            + "AND m.memberCode = :memberCode")
    Member findAnyByNaturalKey(@Param("xRoadInstance") String xRoadInstance,
            @Param("memberClass") String memberClass,
            @Param("memberCode") String memberCode);

    @Query("SELECT m FROM Member m WHERE m.statusInfo.removed IS NULL")
    List<Member> findAllActive();

    /**
     * Active natural-key lookup. Member has no parent, so no cascade is applicable.
     */
    @EntityGraph(attributePaths = {"subsystems", "subsystems.services"}, type = EntityGraph.EntityGraphType.LOAD)
    @Query("SELECT m FROM Member m WHERE m.xRoadInstance = :xRoadInstance "
            + "AND m.memberClass = :memberClass "
            + "AND m.memberCode = :memberCode "
            + "AND m.statusInfo.removed IS NULL")
    Member findActiveByNaturalKey(@Param("xRoadInstance") String xRoadInstance,
                                  @Param("memberClass") String memberClass,
                                  @Param("memberCode") String memberCode);

    /**
     * Active natural-key lookup pre-fetching the full subtree used by the V2 {@code ?full=true}
     * browse endpoint. The entity graph covers subsystems, services, and all four child collections
     * (wsdls, openApis, rests, endpoints). The aggregate full-tree shape exposes only
     * {@link org.niis.xroad.catalog.lister.v2.dto.ServiceVersionSummaryDto} per version (no
     * endpoints); however, {@code Service.endpoints} is {@code FetchType.EAGER}, so Hibernate would
     * still fire a per-service select for endpoints at hydration time. Including it in the graph
     * collapses those fetches into the single join pass -- matching the {@code ServiceRepositoryV2}
     * entity graph conventions and keeping query count invariant to subtree size.
     */
    @EntityGraph(attributePaths = {
            "subsystems",
            "subsystems.services",
            "subsystems.services.wsdls",
            "subsystems.services.openApis",
            "subsystems.services.rests",
            "subsystems.services.endpoints"
    }, type = EntityGraph.EntityGraphType.LOAD)
    @Query("SELECT m FROM Member m WHERE m.xRoadInstance = :xRoadInstance "
            + "AND m.memberClass = :memberClass "
            + "AND m.memberCode = :memberCode "
            + "AND m.statusInfo.removed IS NULL")
    Member findActiveByNaturalKeyWithFullTree(@Param("xRoadInstance") String xRoadInstance,
                                              @Param("memberClass") String memberClass,
                                              @Param("memberCode") String memberCode);

    /**
     * Natural-key lookup regardless of removed status, pre-fetching the full subtree. Same entity
     * graph as {@link #findActiveByNaturalKeyWithFullTree} without the removed filter so the
     * {@code ?includeRemoved=true} case resolves both active and removed members.
     */
    @EntityGraph(attributePaths = {
            "subsystems",
            "subsystems.services",
            "subsystems.services.wsdls",
            "subsystems.services.openApis",
            "subsystems.services.rests",
            "subsystems.services.endpoints"
    }, type = EntityGraph.EntityGraphType.LOAD)
    @Query("SELECT m FROM Member m WHERE m.xRoadInstance = :xRoadInstance "
            + "AND m.memberClass = :memberClass "
            + "AND m.memberCode = :memberCode")
    Member findAnyByNaturalKeyWithFullTree(@Param("xRoadInstance") String xRoadInstance,
                                           @Param("memberClass") String memberClass,
                                           @Param("memberCode") String memberCode);

    @Query("SELECT m FROM Member m WHERE "
            + "(LOWER(m.name) LIKE LOWER(CONCAT('%', :q, '%')) "
            + "OR LOWER(m.memberCode) LIKE LOWER(CONCAT('%', :q, '%'))) "
            + "AND (:activeOnly = false OR m.statusInfo.removed IS NULL)")
    Page<Member> searchByText(@Param("q") String query,
                              @Param("activeOnly") boolean activeOnly,
                              Pageable pageable);

    /**
     * Paged member list filter. The {@code :isProvider} predicate matches
     * {@code MemberConverter.computeIsProvider} exactly: a member is a provider iff it is not
     * removed and has at least one active service under an active subsystem. No descriptor check.
     * See spec §6.1 and the V2 {@code serviceType} REST default.
     */
    @EntityGraph(attributePaths = {"subsystems", "subsystems.services"}, type = EntityGraph.EntityGraphType.LOAD)
    @Query("SELECT m FROM Member m WHERE "
            + "(:memberClass IS NULL OR m.memberClass = :memberClass) "
            + "AND (:activeOnly = false OR m.statusInfo.removed IS NULL) "
            + "AND (:isProvider IS NULL "
            + "     OR (:isProvider = true "
            + "         AND m.statusInfo.removed IS NULL "
            + "         AND EXISTS (SELECT s FROM Service s "
            + "                     WHERE s.subsystem.member = m "
            + "                     AND s.statusInfo.removed IS NULL "
            + "                     AND s.subsystem.statusInfo.removed IS NULL)) "
            + "     OR (:isProvider = false "
            + "         AND (m.statusInfo.removed IS NOT NULL "
            + "              OR NOT EXISTS (SELECT s FROM Service s "
            + "                             WHERE s.subsystem.member = m "
            + "                             AND s.statusInfo.removed IS NULL "
            + "                             AND s.subsystem.statusInfo.removed IS NULL))))")
    Page<Member> findForList(@Param("memberClass") String memberClass,
                             @Param("isProvider") Boolean isProvider,
                             @Param("activeOnly") boolean activeOnly,
                             Pageable pageable);

    @Query("SELECT m FROM Member m WHERE "
            + "m.statusInfo.created >= :startDate AND m.statusInfo.created < :endDate")
    List<Member> findCreatedBetween(@Param("startDate") LocalDateTime startDate,
                                    @Param("endDate") LocalDateTime endDate);

    @Query("SELECT m FROM Member m WHERE "
            + "m.statusInfo.changed >= :startDate AND m.statusInfo.changed < :endDate "
            + "AND (m.statusInfo.created < :startDate OR m.statusInfo.created >= :endDate) "
            + "AND (m.statusInfo.removed IS NULL "
            + "     OR m.statusInfo.removed < :startDate "
            + "     OR m.statusInfo.removed >= :endDate)")
    List<Member> findModifiedBetween(@Param("startDate") LocalDateTime startDate,
                                     @Param("endDate") LocalDateTime endDate);

    @Query("SELECT m FROM Member m WHERE "
            + "m.statusInfo.removed >= :startDate AND m.statusInfo.removed < :endDate")
    List<Member> findRemovedBetween(@Param("startDate") LocalDateTime startDate,
                                    @Param("endDate") LocalDateTime endDate);
}
