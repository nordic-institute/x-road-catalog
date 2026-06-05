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

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.niis.xroad.catalog.persistence.entity.Member;
import org.niis.xroad.catalog.persistence.entity.Service;
import org.niis.xroad.catalog.persistence.entity.Subsystem;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Asserts that V2 repository methods issue a query count invariant to page size / subtree size.
 * Hibernate's {@code FetchType.EAGER} on Member.subsystems / Subsystem.services / Service.wsdls /
 * openApis / rests / endpoints produces an N+1 pattern without an explicit fetch plan; the
 * {@code @EntityGraph} retrofits on the V2 repository methods collapse those fetches into a bounded
 * set of joins. This test guards against regressions by exercising the same traversals the V2
 * converters perform at serve time (walks children after the repo call) and comparing the prepared-
 * statement count across two different input sizes.
 *
 * The fixture is loaded per-test via {@code @Sql(BEFORE_TEST_METHOD)} into an otherwise clean
 * (rollback-per-test) transaction so the same synthetic IDs can be re-inserted between methods.
 */
@SpringBootTest
@Transactional
@Sql(scripts = "classpath:general/query-count-fixture.sql",
        executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@ActiveProfiles({"test", "general-testdata"})
public class RepositoryQueryCountTest {

    @Autowired
    private MemberRepositoryV2 memberRepository;

    @Autowired
    private SubsystemRepositoryV2 subsystemRepository;

    @Autowired
    private ServiceRepositoryV2 serviceRepository;

    @PersistenceContext
    private EntityManager entityManager;

    private Statistics stats;

    @BeforeEach
    void setUp() {
        stats = entityManager.getEntityManagerFactory()
                .unwrap(SessionFactory.class)
                .getStatistics();
        stats.setStatisticsEnabled(true);
        stats.clear();
    }

    @Test
    void findForListMemberQueryCountInvariantWithPageSize() {
        entityManager.clear();
        stats.clear();
        Page<Member> page1 = memberRepository.findForList("PUB", null, true, PageRequest.of(0, 1));
        int touchedPage1 = touchMemberChildren(page1);
        long queriesAtPageSize1 = stats.getPrepareStatementCount();

        entityManager.clear();
        stats.clear();
        Page<Member> page20 = memberRepository.findForList("PUB", null, true, PageRequest.of(0, 20));
        int touchedPage20 = touchMemberChildren(page20);
        long queriesAtPageSize20 = stats.getPrepareStatementCount();

        assertFalse(page1.getContent().isEmpty(), "page 1 should not be empty");
        assertFalse(page20.getContent().isEmpty(), "page 20 should not be empty");
        assertTrue(touchedPage20 >= touchedPage1, "page 20 must see at least as many services as page 1");
        assertEquals(queriesAtPageSize1, queriesAtPageSize20,
                "MemberRepositoryV2.findForList query count must not grow with page size - signals N+1");
    }

    @Test
    void findForListSubsystemQueryCountInvariantWithPageSize() {
        entityManager.clear();
        stats.clear();
        Page<Subsystem> page1 = subsystemRepository.findForList(null, true, PageRequest.of(0, 1));
        int touchedPage1 = touchSubsystemChildren(page1);
        long queriesAtPageSize1 = stats.getPrepareStatementCount();

        entityManager.clear();
        stats.clear();
        Page<Subsystem> page20 = subsystemRepository.findForList(null, true, PageRequest.of(0, 20));
        int touchedPage20 = touchSubsystemChildren(page20);
        long queriesAtPageSize20 = stats.getPrepareStatementCount();

        assertFalse(page1.getContent().isEmpty(), "page 1 should not be empty");
        assertFalse(page20.getContent().isEmpty(), "page 20 should not be empty");
        assertTrue(touchedPage20 >= touchedPage1, "page 20 must see at least as many services as page 1");
        assertEquals(queriesAtPageSize1, queriesAtPageSize20,
                "SubsystemRepositoryV2.findForList query count must not grow with page size - signals N+1");
    }

    @Test
    void findActiveByNaturalKeyWithFullTreeQueryCountInvariantWithSubtreeSize() {
        // qc-0 has 1 subsystem + 1 service; qc-20 has 21 subsystems (20 empty + 1 extra via
        // scenario-B 'qc-many-sub-*' parented on member 10020). The entity graph must collapse
        // child fetches regardless of subtree size -> same query count.
        entityManager.clear();
        stats.clear();
        Member small = memberRepository.findActiveByNaturalKeyWithFullTree("dev-cs", "PUB", "qc-0");
        int touchedSmall = touchFullTree(small);
        long queriesSmall = stats.getPrepareStatementCount();

        entityManager.clear();
        stats.clear();
        Member large = memberRepository.findActiveByNaturalKeyWithFullTree("dev-cs", "PUB", "qc-20");
        int touchedLarge = touchFullTree(large);
        long queriesLarge = stats.getPrepareStatementCount();

        assertEquals(1, small.getAllSubsystems().size(), "fixture precondition: qc-0 has 1 subsystem");
        assertTrue(large.getAllSubsystems().size() > 1, "fixture precondition: qc-20 has many subsystems");
        assertTrue(touchedLarge >= touchedSmall, "large member must touch at least as many descendants");
        assertEquals(queriesSmall, queriesLarge,
                "MemberRepositoryV2.findActiveByNaturalKeyWithFullTree query count must be invariant to subtree size "
                        + "- entity graph should pre-fetch subsystems/services/wsdls/openApis/rests in one pass");
    }

    @Test
    void findActiveByNaturalKeyWithServicesQueryCountInvariantWithServiceCount() {
        // qc-many-sub-0 has 1 service (qcSingleSvc); qc-many-sub-1 has 2 services (qcMultiSvc v1+v2),
        // each with a WSDL row. The entity graph covering services + their wsdls/openApis/rests must
        // collapse child fetches regardless of how many services hang off the subsystem - same query
        // count whether we resolve the single-service or multi-service subsystem.
        entityManager.clear();
        stats.clear();
        Subsystem singleSvcSubsystem = subsystemRepository.findActiveByNaturalKeyWithServices(
                "dev-cs", "PUB", "qc-20", "qc-many-sub-0");
        int touchedSingle = touchSubsystemServices(singleSvcSubsystem);
        long queriesAtOneService = stats.getPrepareStatementCount();

        entityManager.clear();
        stats.clear();
        Subsystem multiSvcSubsystem = subsystemRepository.findActiveByNaturalKeyWithServices(
                "dev-cs", "PUB", "qc-20", "qc-many-sub-1");
        int touchedMulti = touchSubsystemServices(multiSvcSubsystem);
        long queriesAtTwoServices = stats.getPrepareStatementCount();

        assertEquals(1, singleSvcSubsystem.getAllServices().size(),
                "fixture precondition: qc-many-sub-0 has exactly one service");
        assertEquals(2, multiSvcSubsystem.getAllServices().size(),
                "fixture precondition: qc-many-sub-1 has exactly two services");
        assertTrue(touchedMulti >= touchedSingle, "multi-service subsystem must touch at least as many descriptors");
        assertEquals(queriesAtOneService, queriesAtTwoServices,
                "SubsystemRepositoryV2.findActiveByNaturalKeyWithServices query count must be invariant to service count "
                        + "- entity graph should pre-fetch services/wsdls/openApis/rests in one pass");
    }

    @Test
    void findActiveByMemberServiceAndSubsystemQueryCountInvariantWithVersionCount() {
        // qcSingleSvc (subsystem 10100) has exactly one version; qcMultiSvc (subsystem 10101)
        // has two versions. With the attributePaths graph covering wsdls/openApis/rests/endpoints,
        // the lookup should issue the same number of queries regardless of version count.
        entityManager.clear();
        stats.clear();
        List<Service> singleVersion = serviceRepository.findActiveByMemberServiceAndSubsystem(
                "dev-cs", "PUB", "qc-20", "qcSingleSvc", "qc-many-sub-0");
        int touchedSingle = touchServiceChildren(singleVersion);
        long queriesAtOneVersion = stats.getPrepareStatementCount();

        entityManager.clear();
        stats.clear();
        List<Service> twoVersions = serviceRepository.findActiveByMemberServiceAndSubsystem(
                "dev-cs", "PUB", "qc-20", "qcMultiSvc", "qc-many-sub-1");
        int touchedTwo = touchServiceChildren(twoVersions);
        long queriesAtTwoVersions = stats.getPrepareStatementCount();

        assertEquals(1, singleVersion.size(), "fixture precondition: qcSingleSvc has exactly one version");
        assertEquals(2, twoVersions.size(), "fixture precondition: qcMultiSvc has exactly two versions");
        assertTrue(touchedTwo >= touchedSingle, "multi-version service must touch at least as many children");
        assertEquals(queriesAtOneVersion, queriesAtTwoVersions,
                "ServiceRepositoryV2.findActiveByMemberServiceAndSubsystem query count must be invariant to version count "
                        + "- entity graph should pre-fetch wsdls/openApis/rests/endpoints in one pass");
    }

    /**
     * Force Member -> Subsystem -> Service traversal so the child collections Hibernate might
     * fetch lazily / per-row actually get touched in the measurement window. Returns the total
     * number of services seen so the result can be consumed by the caller (PMD UselessPureMethodCall).
     */
    private int touchMemberChildren(Iterable<Member> members) {
        int serviceCount = 0;
        for (Member m : members) {
            for (Subsystem s : m.getActiveSubsystems()) {
                serviceCount += s.getActiveServices().size();
            }
        }
        return serviceCount;
    }

    private int touchSubsystemChildren(Iterable<Subsystem> subsystems) {
        int serviceCount = 0;
        for (Subsystem s : subsystems) {
            serviceCount += s.getActiveServices().size();
        }
        return serviceCount;
    }

    /**
     * Walks the full subtree materialised by {@code findActiveByNaturalKeyWithFullTree}:
     * subsystems -> services -> wsdls/openApis/rests. Touching each child collection forces
     * Hibernate to materialise any still-proxied associations so the query count reflects what
     * serve-time traversal would observe.
     */
    private int touchFullTree(Member member) {
        int touched = 0;
        for (Subsystem sub : member.getAllSubsystems()) {
            for (Service s : sub.getAllServices()) {
                if (s.hasWsdl()) {
                    touched++;
                }
                if (s.hasOpenApi()) {
                    touched++;
                }
                if (s.hasRest()) {
                    touched++;
                }
                touched++;
            }
        }
        return touched;
    }

    /**
     * Walks subsystem -> services -> descriptors so the entity graph for
     * {@code findActiveByNaturalKeyWithServices} (services/wsdls/openApis/rests) is exercised
     * end-to-end. Endpoints are not in this graph; touching them would fault and skew counts.
     */
    private int touchSubsystemServices(Subsystem subsystem) {
        int descriptorCount = 0;
        for (Service s : subsystem.getAllServices()) {
            if (s.hasWsdl()) {
                descriptorCount++;
            }
            if (s.hasOpenApi()) {
                descriptorCount++;
            }
            if (s.hasRest()) {
                descriptorCount++;
            }
        }
        return descriptorCount;
    }

    /**
     * Walks the same four descriptor collections ServiceVersionConverter reads so the entity graph's
     * four attributePaths are exercised end-to-end. The hasX() helpers touch the underlying Set and
     * force Hibernate to materialize the association, which is what we want for the query-count
     * measurement.
     */
    private int touchServiceChildren(Iterable<Service> services) {
        int descriptorCount = 0;
        for (Service s : services) {
            if (s.hasWsdl()) {
                descriptorCount++;
            }
            if (s.hasOpenApi()) {
                descriptorCount++;
            }
            if (s.hasRest()) {
                descriptorCount++;
            }
            descriptorCount += s.getAllEndpoints().size();
        }
        return descriptorCount;
    }
}
