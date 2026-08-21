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
import org.niis.xroad.catalog.persistence.repository.projection.DescriptorAnomalyRow;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Set-based maintenance of the V2 denormalized columns. Runs after each collection cycle;
 * idempotent, so the flags can never be staler than one collector interval. Extends
 * {@code Repository<Member, Long>} only so Spring Data registers it; all queries are native.
 *
 * <p>{@code service_type} classification priority is SOAP &gt; OPENAPI &gt; REST, each requiring
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

    // Shared by DESCRIPTOR_ANOMALIES_SQL and COUNT_DESCRIPTOR_ANOMALIES_SQL so the anomaly definition
    // (which services count as having multiple active descriptors) cannot drift between the two queries.
    // Deliberately excludes "FROM service s": the row query interposes the subsystem/member joins between
    // that and this fragment, while the count query appends this fragment directly after its own FROM.
    String DESCRIPTOR_ANOMALY_FROM_WHERE = " LEFT JOIN (SELECT service_id, COUNT(*) AS cnt FROM wsdl"
            + "   WHERE removed IS NULL GROUP BY service_id) w ON w.service_id = s.id"
            + " LEFT JOIN (SELECT service_id, COUNT(*) AS cnt FROM open_api"
            + "   WHERE removed IS NULL GROUP BY service_id) o ON o.service_id = s.id"
            + " WHERE s.removed IS NULL AND COALESCE(w.cnt, 0) + COALESCE(o.cnt, 0) > 1";

    String DESCRIPTOR_ANOMALIES_SQL = "SELECT s.id AS serviceId, m.member_class AS memberClass,"
            + " m.member_code AS memberCode, ss.subsystem_code AS subsystemCode,"
            + " s.service_code AS serviceCode, s.service_version AS serviceVersion,"
            + " COALESCE(w.cnt, 0) AS wsdlCount, COALESCE(o.cnt, 0) AS openapiCount"
            + " FROM service s"
            + " JOIN subsystem ss ON s.subsystem_id = ss.id"
            + " JOIN member m ON ss.member_id = m.id"
            + DESCRIPTOR_ANOMALY_FROM_WHERE
            + " ORDER BY s.id";

    String COUNT_DESCRIPTOR_ANOMALIES_SQL = "SELECT COUNT(*) FROM service s" + DESCRIPTOR_ANOMALY_FROM_WHERE;

    @Modifying
    @Transactional
    @Query(value = RECOMPUTE_IS_PROVIDER_SQL, nativeQuery = true)
    int recomputeMemberIsProvider();

    @Modifying
    @Transactional
    @Query(value = RECOMPUTE_SERVICE_TYPE_SQL, nativeQuery = true)
    int recomputeServiceType();

    @Query(value = DESCRIPTOR_ANOMALIES_SQL, nativeQuery = true)
    List<DescriptorAnomalyRow> findServicesWithMultipleActiveDescriptors();

    @Query(value = COUNT_DESCRIPTOR_ANOMALIES_SQL, nativeQuery = true)
    long countServicesWithMultipleActiveDescriptors();
}
