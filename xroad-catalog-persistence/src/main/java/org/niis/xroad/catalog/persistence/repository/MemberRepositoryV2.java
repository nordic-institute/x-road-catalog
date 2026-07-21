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

import org.niis.xroad.catalog.persistence.repository.projection.MemberClassCountRow;
import org.niis.xroad.catalog.persistence.repository.projection.MemberListRow;
import org.niis.xroad.catalog.persistence.v2entity.MemberV2;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * V2 read-model repository for {@link MemberV2}. Every query is instance-scoped
 * ({@code m.xRoadInstance = :xRoadInstance}), unlike the V1-era {@code findForList} it replaces.
 */
public interface MemberRepositoryV2 extends Repository<MemberV2, Long>, V2ReadModelRepository {

    String LIST_SELECT = "SELECT m.memberClass AS memberClass, m.memberCode AS memberCode, m.name AS name, "
            + "m.isProvider AS provider, "
            + "(SELECT COUNT(ss) FROM SubsystemV2 ss WHERE ss.member = m AND ss.statusInfo.removed IS NULL) AS subsystemCount, "
            + "(SELECT COUNT(s) FROM ServiceV2 s WHERE s.subsystem.member = m AND s.statusInfo.removed IS NULL "
            + "AND s.subsystem.statusInfo.removed IS NULL) AS serviceCount, "
            + "m.statusInfo.created AS created, m.statusInfo.changed AS changed, "
            + "m.statusInfo.fetched AS fetched, m.statusInfo.removed AS removed "
            + "FROM MemberV2 m ";

    String LIST_WHERE = "WHERE m.xRoadInstance = :xRoadInstance "
            + "AND (:memberClass IS NULL OR m.memberClass = :memberClass) "
            + "AND m.statusInfo.removed IS NULL "
            + "AND (:isProvider IS NULL OR m.isProvider = :isProvider)";

    @Query(value = LIST_SELECT + LIST_WHERE,
            countQuery = "SELECT COUNT(m) FROM MemberV2 m " + LIST_WHERE)
    Page<MemberListRow> findActiveForList(@Param("xRoadInstance") String xRoadInstance,
                                          @Param("memberClass") String memberClass,
                                          @Param("isProvider") Boolean isProvider,
                                          Pageable pageable);

    @Query(LIST_SELECT + "WHERE m.xRoadInstance = :xRoadInstance AND m.memberClass = :memberClass "
            + "AND m.memberCode = :memberCode AND m.statusInfo.removed IS NULL")
    Optional<MemberListRow> findActiveSummaryByNaturalKey(@Param("xRoadInstance") String xRoadInstance,
                                                          @Param("memberClass") String memberClass,
                                                          @Param("memberCode") String memberCode);

    @Query("SELECT COUNT(m) > 0 FROM MemberV2 m WHERE m.xRoadInstance = :xRoadInstance "
            + "AND m.memberClass = :memberClass AND m.memberCode = :memberCode AND m.statusInfo.removed IS NULL")
    boolean existsActiveByNaturalKey(@Param("xRoadInstance") String xRoadInstance,
                                     @Param("memberClass") String memberClass,
                                     @Param("memberCode") String memberCode);

    /**
     * Active natural-key lookup pre-fetching the member's subsystem/service tree. Only the member
     * root is active-filtered; the fetched subtree contains removed rows and callers must filter
     * via the {@code getActive*} helpers on {@link MemberV2} and {@code SubsystemV2}.
     */
    @EntityGraph(attributePaths = {"subsystems", "subsystems.services"})
    @Query("SELECT m FROM MemberV2 m WHERE m.xRoadInstance = :xRoadInstance "
            + "AND m.memberClass = :memberClass AND m.memberCode = :memberCode AND m.statusInfo.removed IS NULL")
    Optional<MemberV2> findActiveWithTreeByNaturalKey(@Param("xRoadInstance") String xRoadInstance,
                                                      @Param("memberClass") String memberClass,
                                                      @Param("memberCode") String memberCode);

    @Query("SELECT m.memberClass AS code, COUNT(m) AS memberCount FROM MemberV2 m "
            + "WHERE m.xRoadInstance = :xRoadInstance AND m.statusInfo.removed IS NULL GROUP BY m.memberClass")
    List<MemberClassCountRow> countActiveGroupedByMemberClass(@Param("xRoadInstance") String xRoadInstance);

    @Query("SELECT COUNT(m) FROM MemberV2 m WHERE m.xRoadInstance = :xRoadInstance "
            + "AND m.memberClass = :memberClass AND m.statusInfo.removed IS NULL")
    long countActiveByMemberClass(@Param("xRoadInstance") String xRoadInstance,
                                  @Param("memberClass") String memberClass);

    @Query("SELECT MAX(m.statusInfo.fetched) FROM MemberV2 m")
    LocalDateTime findLatestFetched();

    @Query(value = "SELECT 1", nativeQuery = true)
    Integer checkConnection();
}
