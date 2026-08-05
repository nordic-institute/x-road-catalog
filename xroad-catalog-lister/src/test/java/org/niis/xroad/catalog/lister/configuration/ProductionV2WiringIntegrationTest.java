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
package org.niis.xroad.catalog.lister.configuration;

import org.niis.xroad.catalog.lister.ListerApplication;
import org.niis.xroad.catalog.persistence.v2.repository.MemberRepository;
import org.niis.xroad.catalog.persistence.v2.repository.projection.MemberListRow;
import org.niis.xroad.catalog.persistence.testsupport.PostgresTestBase;
import org.niis.xroad.catalog.persistence.v2.entity.Service;
import org.niis.xroad.catalog.persistence.v2.entity.Subsystem;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.metamodel.ManagedType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.jdbc.Sql;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Boots the real production wiring (no {@code test} profile, so {@link ProductionConfigurationV2}
 * fires) against a Liquibase-managed Postgres schema and proves the V2 read-model repositories
 * resolve and execute a query against it. Every other lister test runs the {@code test} profile
 * (H2, {@code create-drop}), where {@code v2.entity} is deliberately never scanned, so only this
 * test catches a wrong scan package or a mapping conflict between the coexisting V1 and V2 entity
 * packages.
 *
 * <p>{@code configuration-client.enabled} is off so {@link ConfigClientInitializer} does not bind
 * a real Jetty admin port; {@code webEnvironment = NONE} because only the context and repositories
 * are needed. {@code allow-bean-definition-overriding} is on because {@code
 * ListerDefaultConfiguration}'s component scan picks up nested {@code @TestConfiguration} classes
 * of unrelated {@code @WebMvcTest}s that declare a clashing {@code clock()} bean.
 */
@SpringBootTest(classes = ListerApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "xroad-catalog.configuration-client.enabled=false",
                "xroad-catalog.shared-params-file=src/test/resources/shared-params.xml",
                "spring.main.allow-bean-definition-overriding=true"
        })
@Sql(scripts = "classpath:pg/production-wiring-fixture.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class ProductionV2WiringIntegrationTest extends PostgresTestBase {

    @Autowired
    private MemberRepository memberRepositoryV2;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    void v2EntitiesAreManagedTypesAndV2RepositoryResolvesAgainstProductionSchema() {
        Set<Class<?>> managedTypes = entityManager.getMetamodel().getManagedTypes().stream()
                .map(ManagedType::getJavaType)
                .collect(Collectors.toSet());

        assertTrue(managedTypes.contains(org.niis.xroad.catalog.persistence.v2.entity.Member.class),
                "Member must be a managed JPA type in production wiring");
        assertTrue(managedTypes.contains(Subsystem.class), "Subsystem must be a managed JPA type in production wiring");
        assertTrue(managedTypes.contains(Service.class), "Service must be a managed JPA type in production wiring");
        assertTrue(managedTypes.contains(org.niis.xroad.catalog.persistence.entity.Member.class),
                "V1 Member must remain a managed JPA type alongside v2.entity");

        Page<MemberListRow> page = memberRepositoryV2.findActiveForList(
                "TEST-WIRING", null, null, PageRequest.of(0, 20));

        assertEquals(1, page.getTotalElements());
        assertEquals("Wiring Test Member", page.getContent().get(0).getName());
    }
}
