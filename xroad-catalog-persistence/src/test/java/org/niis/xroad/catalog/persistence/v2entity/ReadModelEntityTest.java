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
package org.niis.xroad.catalog.persistence.v2entity;

import org.niis.xroad.catalog.persistence.repository.DenormalizationRepository;
import org.niis.xroad.catalog.persistence.testsupport.PostgresTestBase;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * The V2 read-model entities live in a package sibling to {@code entity} (not nested under it) so
 * that {@code PersistenceDefaultConfiguration}'s production {@code @EntityScan("...entity")}
 * excludes them everywhere else; Hibernate's schema drop otherwise rejects the V1/V2 dual mapping
 * of the same physical tables with {@code SchemaManagementException: SQL strings added more than
 * once}. This test-only {@code @EntityScan} adds the V2 package back in, scoped to this context.
 */
@SpringBootTest
@EntityScan(basePackages = {
        "org.niis.xroad.catalog.persistence.entity",
        "org.niis.xroad.catalog.persistence.v2entity"
})
@Sql(scripts = "classpath:pg/v2-fixture.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class ReadModelEntityTest extends PostgresTestBase {

    @PersistenceContext
    private EntityManager entityManager;

    @Autowired
    private DenormalizationRepository denormalizationRepository;

    @BeforeEach
    void recomputeDenormalizedColumns() {
        denormalizationRepository.recomputeMemberIsProvider();
        denormalizationRepository.recomputeServiceType();
    }

    @Test
    @Transactional
    void memberTreeIsLazyAndActiveHelpersFilter() {
        MemberV2 m1 = entityManager.createQuery(
                "SELECT m FROM MemberV2 m WHERE m.memberCode = 'M1'", MemberV2.class).getSingleResult();
        assertFalse(Hibernate.isInitialized(m1.getSubsystems()), "subsystems must be LAZY");
        assertEquals(1, m1.getActiveSubsystems().size());
        SubsystemV2 ss1 = m1.getActiveSubsystems().iterator().next();
        assertEquals("SS1", ss1.getSubsystemCode());
        assertEquals(4, ss1.getActiveServices().size());
    }

    @Test
    @Transactional
    void serviceMapsDenormalizedTypeColumn() {
        ServiceV2 svc = entityManager.find(ServiceV2.class, 21L);
        assertEquals("SOAP", svc.getServiceType());
        assertFalse(Hibernate.isInitialized(svc.getSubsystem()), "parent must be LAZY");
        assertEquals(1, entityManager.find(ServiceV2.class, 23L).getActiveEndpoints().size());
    }
}
