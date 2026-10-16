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

import org.niis.xroad.catalog.persistence.v2.repository.projection.MemberClassCountRow;
import org.niis.xroad.catalog.persistence.v2.repository.projection.MemberListRow;
import org.niis.xroad.catalog.persistence.v2.entity.Member;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * V2 read-model repository for {@link Member}. Every query is instance-scoped.
 *
 * <p>Named {@code memberRepositoryV2} because the default bean name would collide with the V1
 * {@code MemberRepository} when both base packages are scanned, silently dropping one bean.
 */
@Repository("memberRepositoryV2")
public interface MemberRepository extends org.springframework.data.repository.Repository<Member, Long>, ReadModelRepository {

    String LIST_SELECT = "SELECT m.memberClass AS memberClass, m.memberCode AS memberCode, m.name AS name, "
            + "m.isProvider AS provider, "
            + "(SELECT COUNT(ss) FROM SubsystemV2 ss WHERE ss.member = m) AS subsystemCount, "
            + "(SELECT COUNT(s) FROM ServiceV2 s WHERE s.subsystem.member = m) AS serviceCount, "
            + "m.statusInfo.created AS created, m.statusInfo.changed AS changed, "
            + "m.statusInfo.fetched AS fetched "
            + "FROM MemberV2 m ";

    String LIST_WHERE = "WHERE m.xRoadInstance = :xRoadInstance "
            + "AND (:memberClass IS NULL OR m.memberClass = :memberClass) "
            + "AND (:isProvider IS NULL OR m.isProvider = :isProvider)";

    @Query(value = LIST_SELECT + LIST_WHERE,
            countQuery = "SELECT COUNT(m) FROM MemberV2 m " + LIST_WHERE)
    Page<MemberListRow> findActiveForList(@Param("xRoadInstance") String xRoadInstance,
                                          @Param("memberClass") String memberClass,
                                          @Param("isProvider") Boolean isProvider,
                                          Pageable pageable);

    @Query(LIST_SELECT + "WHERE m.xRoadInstance = :xRoadInstance AND m.memberClass = :memberClass "
            + "AND m.memberCode = :memberCode")
    Optional<MemberListRow> findActiveSummaryByNaturalKey(@Param("xRoadInstance") String xRoadInstance,
                                                          @Param("memberClass") String memberClass,
                                                          @Param("memberCode") String memberCode);

    @Query("SELECT COUNT(m) > 0 FROM MemberV2 m WHERE m.xRoadInstance = :xRoadInstance "
            + "AND m.memberClass = :memberClass AND m.memberCode = :memberCode")
    boolean existsActiveByNaturalKey(@Param("xRoadInstance") String xRoadInstance,
                                     @Param("memberClass") String memberClass,
                                     @Param("memberCode") String memberCode);

    /**
     * Active natural-key lookup pre-fetching the member's subsystem/service tree. The fetched
     * associations are view-backed, so the subtree contains only active rows.
     */
    @EntityGraph(attributePaths = {"subsystems", "subsystems.services"})
    @Query("SELECT m FROM MemberV2 m WHERE m.xRoadInstance = :xRoadInstance "
            + "AND m.memberClass = :memberClass AND m.memberCode = :memberCode")
    Optional<Member> findActiveWithTreeByNaturalKey(@Param("xRoadInstance") String xRoadInstance,
                                                      @Param("memberClass") String memberClass,
                                                      @Param("memberCode") String memberCode);

    @Query("SELECT m.memberClass AS code, COUNT(m) AS memberCount FROM MemberV2 m "
            + "WHERE m.xRoadInstance = :xRoadInstance GROUP BY m.memberClass")
    List<MemberClassCountRow> countActiveGroupedByMemberClass(@Param("xRoadInstance") String xRoadInstance);

    @Query("SELECT COUNT(m) FROM MemberV2 m WHERE m.xRoadInstance = :xRoadInstance "
            + "AND m.memberClass = :memberClass")
    long countActiveByMemberClass(@Param("xRoadInstance") String xRoadInstance,
                                  @Param("memberClass") String memberClass);
}
