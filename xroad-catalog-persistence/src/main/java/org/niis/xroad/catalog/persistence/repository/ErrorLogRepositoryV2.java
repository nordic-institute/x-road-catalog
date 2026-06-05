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

import org.niis.xroad.catalog.persistence.entity.ErrorLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.PagingAndSortingRepository;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

/**
 * V2 repository for ErrorLog. Separate from V1 ErrorLogRepository so V2 query changes
 * cannot accidentally alter V1 behavior.
 *
 * xRoadInstance is deployment-implicit per V2 spec (one instance per catalog deployment),
 * so none of these queries filter by it. Date ranges use the half-open [since, until)
 * convention. No hardcoded ORDER BY — callers pass sort via Pageable.
 */
public interface ErrorLogRepositoryV2 extends CrudRepository<ErrorLog, Long>,
        PagingAndSortingRepository<ErrorLog, Long> {

    @Query("SELECT e FROM ErrorLog e WHERE e.created >= :startDate AND e.created < :endDate")
    Page<ErrorLog> findAnyInRange(@Param("startDate") LocalDateTime startDate,
                                  @Param("endDate") LocalDateTime endDate,
                                  Pageable pageable);

    @Query("SELECT e FROM ErrorLog e WHERE e.created >= :startDate AND e.created < :endDate "
            + "AND e.memberClass = :memberClass")
    Page<ErrorLog> findAnyByMemberClass(@Param("startDate") LocalDateTime startDate,
                                        @Param("endDate") LocalDateTime endDate,
                                        @Param("memberClass") String memberClass,
                                        Pageable pageable);

    @Query("SELECT e FROM ErrorLog e WHERE e.created >= :startDate AND e.created < :endDate "
            + "AND e.memberClass = :memberClass "
            + "AND e.memberCode = :memberCode")
    Page<ErrorLog> findAnyByMember(@Param("startDate") LocalDateTime startDate,
                                   @Param("endDate") LocalDateTime endDate,
                                   @Param("memberClass") String memberClass,
                                   @Param("memberCode") String memberCode,
                                   Pageable pageable);

    @Query("SELECT e FROM ErrorLog e WHERE e.created >= :startDate AND e.created < :endDate "
            + "AND e.memberClass = :memberClass "
            + "AND e.memberCode = :memberCode "
            + "AND e.subsystemCode = :subsystemCode")
    Page<ErrorLog> findAnyBySubsystem(@Param("startDate") LocalDateTime startDate,
                                      @Param("endDate") LocalDateTime endDate,
                                      @Param("memberClass") String memberClass,
                                      @Param("memberCode") String memberCode,
                                      @Param("subsystemCode") String subsystemCode,
                                      Pageable pageable);

    @Query("SELECT e FROM ErrorLog e WHERE e.created >= :startDate AND e.created < :endDate "
            + "AND e.memberClass = :memberClass "
            + "AND e.memberCode = :memberCode "
            + "AND e.subsystemCode = :subsystemCode "
            + "AND e.serviceCode = :serviceCode")
    Page<ErrorLog> findAnyByService(@Param("startDate") LocalDateTime startDate,
                                    @Param("endDate") LocalDateTime endDate,
                                    @Param("memberClass") String memberClass,
                                    @Param("memberCode") String memberCode,
                                    @Param("subsystemCode") String subsystemCode,
                                    @Param("serviceCode") String serviceCode,
                                    Pageable pageable);

    @Query("SELECT e FROM ErrorLog e WHERE e.created >= :startDate AND e.created < :endDate "
            + "AND e.memberClass = :memberClass "
            + "AND e.memberCode = :memberCode "
            + "AND e.subsystemCode = :subsystemCode "
            + "AND e.serviceCode = :serviceCode "
            + "AND e.serviceVersion = :serviceVersion")
    Page<ErrorLog> findAnyByVersion(@Param("startDate") LocalDateTime startDate,
                                    @Param("endDate") LocalDateTime endDate,
                                    @Param("memberClass") String memberClass,
                                    @Param("memberCode") String memberCode,
                                    @Param("subsystemCode") String subsystemCode,
                                    @Param("serviceCode") String serviceCode,
                                    @Param("serviceVersion") String serviceVersion,
                                    Pageable pageable);

    @Query("SELECT e FROM ErrorLog e WHERE e.created >= :startDate AND e.created < :endDate "
            + "AND e.memberClass = :memberClass "
            + "AND e.memberCode = :memberCode "
            + "AND e.subsystemCode = :subsystemCode "
            + "AND e.serviceCode = :serviceCode "
            + "AND e.serviceVersion IS NULL")
    Page<ErrorLog> findAnyByNullVersion(@Param("startDate") LocalDateTime startDate,
                                        @Param("endDate") LocalDateTime endDate,
                                        @Param("memberClass") String memberClass,
                                        @Param("memberCode") String memberCode,
                                        @Param("subsystemCode") String subsystemCode,
                                        @Param("serviceCode") String serviceCode,
                                        Pageable pageable);
}
