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

import org.niis.xroad.catalog.persistence.repository.projection.SubsystemListRow;
import org.niis.xroad.catalog.persistence.v2entity.SubsystemV2;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * V2 read-model repository for {@link SubsystemV2}. Every query is instance-scoped
 * ({@code s.member.xRoadInstance = :xRoadInstance}), unlike the V1-era {@code findForList} it
 * replaces. Active-only queries apply the parent-cascade check: a subsystem is active only when
 * neither it nor its parent member has been soft-deleted.
 */
public interface SubsystemRepositoryV2 extends Repository<SubsystemV2, Long>, V2ReadModelRepository {

    String LIST_SELECT = "SELECT s.member.memberClass AS memberClass, s.member.memberCode AS memberCode, "
            + "s.member.name AS memberName, s.subsystemCode AS subsystemCode, "
            + "(SELECT COUNT(sv) FROM ServiceV2 sv WHERE sv.subsystem = s AND sv.statusInfo.removed IS NULL) AS serviceCount, "
            + "s.statusInfo.created AS created, s.statusInfo.changed AS changed, "
            + "s.statusInfo.fetched AS fetched, s.statusInfo.removed AS removed "
            + "FROM SubsystemV2 s ";

    String ACTIVE_CASCADE = "s.statusInfo.removed IS NULL AND s.member.statusInfo.removed IS NULL";

    String LIST_WHERE = "WHERE s.member.xRoadInstance = :xRoadInstance "
            + "AND (:memberClass IS NULL OR s.member.memberClass = :memberClass) "
            + "AND " + ACTIVE_CASCADE;

    @Query(value = LIST_SELECT + LIST_WHERE,
            countQuery = "SELECT COUNT(s) FROM SubsystemV2 s " + LIST_WHERE)
    Page<SubsystemListRow> findActiveForList(@Param("xRoadInstance") String xRoadInstance,
                                             @Param("memberClass") String memberClass,
                                             Pageable pageable);

    @Query(LIST_SELECT + "WHERE s.member.xRoadInstance = :xRoadInstance AND s.member.memberClass = :memberClass "
            + "AND s.member.memberCode = :memberCode AND s.subsystemCode = :subsystemCode AND " + ACTIVE_CASCADE)
    Optional<SubsystemListRow> findActiveSummaryByNaturalKey(@Param("xRoadInstance") String xRoadInstance,
                                                              @Param("memberClass") String memberClass,
                                                              @Param("memberCode") String memberCode,
                                                              @Param("subsystemCode") String subsystemCode);

    @Query(LIST_SELECT + "WHERE s.member.xRoadInstance = :xRoadInstance AND s.member.memberClass = :memberClass "
            + "AND s.member.memberCode = :memberCode AND " + ACTIVE_CASCADE + " ORDER BY s.subsystemCode")
    List<SubsystemListRow> findActiveForMember(@Param("xRoadInstance") String xRoadInstance,
                                               @Param("memberClass") String memberClass,
                                               @Param("memberCode") String memberCode);

    @Query("SELECT COUNT(s) > 0 FROM SubsystemV2 s WHERE s.member.xRoadInstance = :xRoadInstance "
            + "AND s.member.memberClass = :memberClass AND s.member.memberCode = :memberCode "
            + "AND s.subsystemCode = :subsystemCode AND " + ACTIVE_CASCADE)
    boolean existsActiveByNaturalKey(@Param("xRoadInstance") String xRoadInstance,
                                     @Param("memberClass") String memberClass,
                                     @Param("memberCode") String memberCode,
                                     @Param("subsystemCode") String subsystemCode);
}
