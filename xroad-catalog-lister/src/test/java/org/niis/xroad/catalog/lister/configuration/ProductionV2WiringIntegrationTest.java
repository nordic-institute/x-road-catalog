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
import org.niis.xroad.catalog.persistence.entity.Member;
import org.niis.xroad.catalog.persistence.repository.MemberRepositoryV2;
import org.niis.xroad.catalog.persistence.repository.projection.MemberListRow;
import org.niis.xroad.catalog.persistence.testsupport.PostgresTestBase;
import org.niis.xroad.catalog.persistence.v2entity.MemberV2;
import org.niis.xroad.catalog.persistence.v2entity.ServiceV2;
import org.niis.xroad.catalog.persistence.v2entity.SubsystemV2;

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
 * Boots the real production wiring (no {@code test} profile active, so {@link
 * V2ProductionConfiguration} fires exactly as it would in a real deployment) against a genuine
 * Liquibase-managed Postgres schema, and proves the
 * V2 read-model repositories actually resolve and execute a query against it.
 *
 * <p>Every other lister test activates the {@code test} profile (H2, {@code create-drop}), under
 * which {@code v2entity} is deliberately never scanned -- so until this test existed, no test
 * anywhere booted a context with {@code v2entity} scanned alongside the V1 {@code entity} package
 * with the V2 repositories resolving against it. A wrong scan package, or a mapping conflict that
 * only appears once both entity packages coexist in one persistence unit, would previously have
 * surfaced for the first time in a real deployment.
 *
 * <p>{@code xroad-catalog.configuration-client.enabled} is off: that flag gates {@link
 * ConfigClientInitializer}, unrelated to what this test checks, which would otherwise bind a real
 * Jetty admin port. {@code webEnvironment = NONE} suffices since this test only needs the
 * {@code ApplicationContext} and its repositories, not an HTTP listener.
 *
 * <p>{@code spring.main.allow-bean-definition-overriding} is on for a reason unrelated to the
 * V2/repository wiring under test: {@code ListerDefaultConfiguration}'s component scan sees nested
 * {@code @TestConfiguration} classes belonging to unrelated {@code @WebMvcTest}s that declare a
 * clashing {@code clock()} bean -- something that never happens in an actual deployed jar. Every
 * other full-context lister test tolerates this the same way via {@code application-test.yaml},
 * which this test deliberately does not activate.
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
    private MemberRepositoryV2 memberRepositoryV2;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    void v2EntitiesAreManagedTypesAndV2RepositoryResolvesAgainstProductionSchema() {
        Set<Class<?>> managedTypes = entityManager.getMetamodel().getManagedTypes().stream()
                .map(ManagedType::getJavaType)
                .collect(Collectors.toSet());

        // The V2 read model is actually scanned in this context...
        assertTrue(managedTypes.contains(MemberV2.class), "MemberV2 must be a managed JPA type in production wiring");
        assertTrue(managedTypes.contains(SubsystemV2.class), "SubsystemV2 must be a managed JPA type in production wiring");
        assertTrue(managedTypes.contains(ServiceV2.class), "ServiceV2 must be a managed JPA type in production wiring");
        // ...alongside the V1 entity model, without the dual-mapping collision the v2entity
        // package split exists to avoid.
        assertTrue(managedTypes.contains(Member.class), "V1 Member must remain a managed JPA type alongside v2entity");

        Page<MemberListRow> page = memberRepositoryV2.findActiveForList(
                "TEST-WIRING", null, null, PageRequest.of(0, 20));

        assertEquals(1, page.getTotalElements());
        assertEquals("Wiring Test Member", page.getContent().get(0).getName());
    }
}
