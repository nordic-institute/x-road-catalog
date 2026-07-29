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
import org.niis.xroad.catalog.persistence.v2.repository.projection.SearchHitRow;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * V2 unified search over members, subsystems, and service aggregates, read from the
 * {@code active_search_index} view. The backing UNION executes at the database level so pagination
 * stays correct and totalCount exact. Extends {@code Repository<Member, Long>} only so Spring Data
 * registers it; queries are native and not tied to the Member entity.
 */
public interface SearchRepository extends Repository<Member, Long> {

    String SEARCH_MATCH = "(u.sort_key LIKE LOWER(:qLike) ESCAPE '\\' "
            + "OR (u.entity_type = 'member' AND LOWER(u.member_code) LIKE LOWER(:qLike) ESCAPE '\\'))";

    String SEARCH_UNION_SQL = "SELECT u.entity_type AS entityType, u.entity_id AS entityId, "
            + "u.sort_key AS sortKey, u.member_class AS memberClass, u.member_code AS memberCode, "
            + "u.member_name AS memberName, u.subsystem_code AS subsystemCode, u.service_code AS serviceCode, "
            + "u.is_provider AS isProvider, u.service_types AS serviceTypes, "
            + "COUNT(*) OVER () AS totalCount "
            + "FROM active_search_index u WHERE " + SEARCH_MATCH
            + " ORDER BY u.sort_key, u.entity_type, u.entity_id LIMIT :pageSize OFFSET :offset";

    String SEARCH_COUNT_SQL = "SELECT COUNT(*) FROM active_search_index u WHERE " + SEARCH_MATCH;

    @Query(value = SEARCH_UNION_SQL, nativeQuery = true)
    List<SearchHitRow> searchUnion(@Param("qLike") String qLike,
                                   @Param("pageSize") int pageSize,
                                   @Param("offset") long offset);

    /**
     * Fallback for pages requested past the end of the result set, where {@link #searchUnion}
     * returns no rows and cannot carry {@code totalCount}.
     */
    @Query(value = SEARCH_COUNT_SQL, nativeQuery = true)
    long countSearchUnion(@Param("qLike") String qLike);
}
