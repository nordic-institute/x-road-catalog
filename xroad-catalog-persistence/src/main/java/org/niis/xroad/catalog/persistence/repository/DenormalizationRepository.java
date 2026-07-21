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
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Set-based maintenance of the V2 denormalized columns. Runs after each collection cycle;
 * idempotent and self-healing — the recompute IS the reconciliation mechanism, so the flags can
 * never be staler than one collector interval. Extends Repository&lt;Member, Long&gt; only so
 * Spring Data recognises it; all queries are native.
 *
 * <p>{@code service_type} classification uses priority SOAP &gt; OPENAPI &gt; REST, each requiring
 * an active descriptor/rest row; a service with none stays {@code UNKNOWN}.
 */
public interface DenormalizationRepository extends Repository<Member, Long> {

    String RECOMPUTE_IS_PROVIDER_SQL = "UPDATE member m SET is_provider = calc.value FROM ("
            + " SELECT m2.id, (m2.removed IS NULL AND EXISTS ("
            + "   SELECT 1 FROM subsystem ss JOIN service s ON s.subsystem_id = ss.id"
            + "   WHERE ss.member_id = m2.id AND ss.removed IS NULL AND s.removed IS NULL)) AS value"
            + " FROM member m2) calc"
            + " WHERE calc.id = m.id AND m.is_provider IS DISTINCT FROM calc.value";

    String RECOMPUTE_SERVICE_TYPE_SQL = "UPDATE service s SET service_type = calc.value FROM ("
            + " SELECT s2.id, CASE"
            + "   WHEN EXISTS (SELECT 1 FROM wsdl w WHERE w.service_id = s2.id AND w.removed IS NULL) THEN 'SOAP'"
            + "   WHEN EXISTS (SELECT 1 FROM open_api o WHERE o.service_id = s2.id AND o.removed IS NULL) THEN 'OPENAPI'"
            + "   WHEN EXISTS (SELECT 1 FROM rest r WHERE r.service_id = s2.id AND r.removed IS NULL) THEN 'REST'"
            + "   ELSE 'UNKNOWN' END AS value"
            + " FROM service s2) calc"
            + " WHERE calc.id = s.id AND s.service_type IS DISTINCT FROM calc.value";

    String DESCRIPTOR_ANOMALIES_SQL = "SELECT s.id, m.member_class, m.member_code, ss.subsystem_code,"
            + " s.service_code, s.service_version,"
            + " COALESCE(w.cnt, 0) AS wsdl_count, COALESCE(o.cnt, 0) AS openapi_count"
            + " FROM service s"
            + " JOIN subsystem ss ON s.subsystem_id = ss.id"
            + " JOIN member m ON ss.member_id = m.id"
            + " LEFT JOIN (SELECT service_id, COUNT(*) AS cnt FROM wsdl"
            + "   WHERE removed IS NULL GROUP BY service_id) w ON w.service_id = s.id"
            + " LEFT JOIN (SELECT service_id, COUNT(*) AS cnt FROM open_api"
            + "   WHERE removed IS NULL GROUP BY service_id) o ON o.service_id = s.id"
            + " WHERE s.removed IS NULL AND COALESCE(w.cnt, 0) + COALESCE(o.cnt, 0) > 1"
            + " ORDER BY s.id";

    @Modifying
    @Transactional
    @Query(value = RECOMPUTE_IS_PROVIDER_SQL, nativeQuery = true)
    int recomputeMemberIsProvider();

    @Modifying
    @Transactional
    @Query(value = RECOMPUTE_SERVICE_TYPE_SQL, nativeQuery = true)
    int recomputeServiceType();

    @Query(value = DESCRIPTOR_ANOMALIES_SQL, nativeQuery = true)
    List<Object[]> findServicesWithMultipleActiveDescriptors();
}
