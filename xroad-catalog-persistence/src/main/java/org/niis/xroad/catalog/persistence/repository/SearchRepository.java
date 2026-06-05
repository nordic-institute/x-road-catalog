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
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * V2 unified search over Members, Subsystems, and service aggregates (grouped by serviceCode).
 * The UNION is executed at the database level to keep pagination correct and totalCount exact.
 *
 * Column layout for every row returned by searchUnion:
 *   [0] entity_type: 'member' | 'subsystem' | 'service'
 *   [1] entity_id:   long — internal id for tie-break (for service aggregates, MIN(service.id))
 *   [2] sort_key:    lowercased primary text key (name / subsystem_code / service_code)
 *   [3] member_class
 *   [4] member_code
 *   [5] member_name
 *   [6] subsystem_code (null for members)
 *   [7] service_code  (null for members and subsystems)
 *
 * Extends Repository&lt;Member, Long&gt; only so Spring Data recognises it; queries are native and
 * not tied to the Member entity.
 */
public interface SearchRepository extends Repository<Member, Long> {

    String SEARCH_UNION_SQL = "SELECT * FROM ("
            + " SELECT 'member' AS entity_type, m.id AS entity_id,"
            + "        LOWER(m.name) AS sort_key,"
            + "        m.member_class AS member_class, m.member_code AS member_code, m.name AS member_name,"
            + "        CAST(NULL AS VARCHAR) AS subsystem_code, CAST(NULL AS VARCHAR) AS service_code"
            + " FROM member m"
            + " WHERE (LOWER(m.name) LIKE LOWER(:qLike) ESCAPE '\\'"
            + "        OR LOWER(m.member_code) LIKE LOWER(:qLike) ESCAPE '\\')"
            + "   AND m.removed IS NULL"
            + " UNION ALL"
            + " SELECT 'subsystem', sub.id, LOWER(sub.subsystem_code),"
            + "        m.member_class, m.member_code, m.name,"
            + "        sub.subsystem_code, CAST(NULL AS VARCHAR)"
            + " FROM subsystem sub"
            + " JOIN member m ON sub.member_id = m.id"
            + " WHERE LOWER(sub.subsystem_code) LIKE LOWER(:qLike) ESCAPE '\\'"
            + "   AND sub.removed IS NULL AND m.removed IS NULL"
            + " UNION ALL"
            + " SELECT 'service', MIN(s.id), LOWER(s.service_code),"
            + "        m.member_class, m.member_code, m.name,"
            + "        sub.subsystem_code, s.service_code"
            + " FROM service s"
            + " JOIN subsystem sub ON s.subsystem_id = sub.id"
            + " JOIN member m ON sub.member_id = m.id"
            + " WHERE LOWER(s.service_code) LIKE LOWER(:qLike) ESCAPE '\\'"
            + "   AND s.removed IS NULL AND sub.removed IS NULL AND m.removed IS NULL"
            + " GROUP BY s.service_code, m.member_class, m.member_code, m.name, sub.subsystem_code"
            + ") u ORDER BY u.sort_key, u.entity_type, u.entity_id"
            + " LIMIT :pageSize OFFSET :offset";

    String SEARCH_COUNT_SQL = "SELECT COUNT(*) FROM ("
            + " SELECT m.id FROM member m"
            + " WHERE (LOWER(m.name) LIKE LOWER(:qLike) ESCAPE '\\'"
            + "        OR LOWER(m.member_code) LIKE LOWER(:qLike) ESCAPE '\\')"
            + "   AND m.removed IS NULL"
            + " UNION ALL"
            + " SELECT sub.id FROM subsystem sub"
            + " JOIN member m ON sub.member_id = m.id"
            + " WHERE LOWER(sub.subsystem_code) LIKE LOWER(:qLike) ESCAPE '\\'"
            + "   AND sub.removed IS NULL AND m.removed IS NULL"
            + " UNION ALL"
            + " SELECT MIN(s.id) FROM service s"
            + " JOIN subsystem sub ON s.subsystem_id = sub.id"
            + " JOIN member m ON sub.member_id = m.id"
            + " WHERE LOWER(s.service_code) LIKE LOWER(:qLike) ESCAPE '\\'"
            + "   AND s.removed IS NULL AND sub.removed IS NULL AND m.removed IS NULL"
            + " GROUP BY s.service_code, sub.member_id, sub.subsystem_code"
            + ") u";

    @Query(value = SEARCH_UNION_SQL, nativeQuery = true)
    List<Object[]> searchUnion(@Param("qLike") String qLike,
                               @Param("pageSize") int pageSize,
                               @Param("offset") long offset);

    @Query(value = SEARCH_COUNT_SQL, nativeQuery = true)
    long countSearchUnion(@Param("qLike") String qLike);
}
