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

import org.niis.xroad.catalog.persistence.repository.projection.MemberClassCountRow;
import org.niis.xroad.catalog.persistence.repository.projection.MemberListRow;
import org.niis.xroad.catalog.persistence.testsupport.PostgresTestBase;
import org.niis.xroad.catalog.persistence.v2entity.MemberV2;
import org.niis.xroad.catalog.persistence.v2entity.SubsystemV2;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Ground truth: {@code pg/v2-fixture.sql}. GOV members M1 (active, provider) and M2 (active,
 * empty); COM members M3 (removed) and M4 (active member, only a removed subsystem). M1's only
 * active subsystem SS1 has four active services (svcA v1/v2, svcB, svcF); svcC is removed.
 *
 * <p>Same test-only {@code @EntityScan} pattern as {@code ReadModelEntityTest}: production
 * {@code PersistenceDefaultConfiguration} scans only {@code entity}, so the V2 read-model package
 * must be added back for this context.
 */
@SpringBootTest
@EntityScan(basePackages = {
        "org.niis.xroad.catalog.persistence.entity",
        "org.niis.xroad.catalog.persistence.v2entity"
})
@Sql(scripts = "classpath:pg/v2-fixture.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class MemberRepositoryV2PgTest extends PostgresTestBase {

    private static final String INSTANCE = "TEST";

    @Autowired
    private MemberRepositoryV2 memberRepository;

    @Autowired
    private DenormalizationRepository denormalizationRepository;

    @BeforeEach
    void recomputeDenormalizedColumns() {
        denormalizationRepository.recomputeMemberIsProvider();
        denormalizationRepository.recomputeServiceType();
    }

    @Test
    void findActiveForListReturnsActiveMembersWithCounts() {
        Page<MemberListRow> page = memberRepository.findActiveForList(INSTANCE, null, null, PageRequest.of(0, 20));
        assertEquals(3, page.getTotalElements());

        MemberListRow m1 = findByCode(page.getContent(), "M1");
        assertTrue(m1.isProvider());
        assertEquals(1, m1.getSubsystemCount());
        assertEquals(4, m1.getServiceCount());

        MemberListRow m2 = findByCode(page.getContent(), "M2");
        assertFalse(m2.isProvider());
        assertEquals(0, m2.getSubsystemCount());
        assertEquals(0, m2.getServiceCount());

        MemberListRow m4 = findByCode(page.getContent(), "M4");
        assertFalse(m4.isProvider());
        assertEquals(0, m4.getSubsystemCount());
        assertEquals(0, m4.getServiceCount());
    }

    @Test
    void findActiveForListPageSizeProvesSqlLimit() {
        Page<MemberListRow> page = memberRepository.findActiveForList(INSTANCE, null, null, PageRequest.of(0, 1));
        assertEquals(1, page.getContent().size());
        assertEquals(3, page.getTotalElements());
    }

    @Test
    void findActiveForListFiltersByMemberClassAndIsProvider() {
        Page<MemberListRow> govProviders =
                memberRepository.findActiveForList(INSTANCE, "GOV", true, PageRequest.of(0, 20));
        assertEquals(1, govProviders.getTotalElements());
        assertEquals("M1", govProviders.getContent().get(0).getMemberCode());

        Page<MemberListRow> govNonProviders =
                memberRepository.findActiveForList(INSTANCE, "GOV", false, PageRequest.of(0, 20));
        assertEquals(1, govNonProviders.getTotalElements());
        assertEquals("M2", govNonProviders.getContent().get(0).getMemberCode());

        Page<MemberListRow> allNonProviders =
                memberRepository.findActiveForList(INSTANCE, null, false, PageRequest.of(0, 20));
        assertEquals(2, allNonProviders.getTotalElements());
        assertTrue(allNonProviders.getContent().stream().anyMatch(r -> "M2".equals(r.getMemberCode())));
        assertTrue(allNonProviders.getContent().stream().anyMatch(r -> "M4".equals(r.getMemberCode())));
    }

    @Test
    void findActiveSummaryByNaturalKeyExcludesRemovedMember() {
        Optional<MemberListRow> removed = memberRepository.findActiveSummaryByNaturalKey(INSTANCE, "COM", "M3");
        assertTrue(removed.isEmpty());
    }

    @Test
    void existsActiveByNaturalKeyChecksActiveStatusAndUnknownCodes() {
        assertTrue(memberRepository.existsActiveByNaturalKey(INSTANCE, "GOV", "M1"));
        assertFalse(memberRepository.existsActiveByNaturalKey(INSTANCE, "COM", "M3"));
        assertFalse(memberRepository.existsActiveByNaturalKey(INSTANCE, "GOV", "does-not-exist"));
    }

    @Test
    void countActiveGroupedByMemberClassGroupsCorrectly() {
        List<MemberClassCountRow> counts = memberRepository.countActiveGroupedByMemberClass(INSTANCE);
        assertEquals(2, findCount(counts, "GOV"));
        assertEquals(1, findCount(counts, "COM"));
    }

    @Test
    @Transactional
    void findActiveWithTreeByNaturalKeyReturnsOnlyActiveSubsystemAndServices() {
        Optional<MemberV2> found = memberRepository.findActiveWithTreeByNaturalKey(INSTANCE, "GOV", "M1");
        assertTrue(found.isPresent());

        MemberV2 m1 = found.get();
        assertEquals(1, m1.getActiveSubsystems().size());
        SubsystemV2 ss1 = m1.getActiveSubsystems().iterator().next();
        assertEquals("SS1", ss1.getSubsystemCode());
        assertEquals(4, ss1.getActiveServices().size());
    }

    @Test
    void findLatestFetchedReturnsMaxAcrossAllMembers() {
        assertEquals(LocalDateTime.of(2025, 6, 1, 10, 0), memberRepository.findLatestFetched());
    }

    @Test
    void checkConnectionReturnsOne() {
        assertEquals(1, memberRepository.checkConnection());
    }

    private static MemberListRow findByCode(List<MemberListRow> rows, String memberCode) {
        return rows.stream()
                .filter(row -> memberCode.equals(row.getMemberCode()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no row for member code " + memberCode));
    }

    private static long findCount(List<MemberClassCountRow> counts, String code) {
        return counts.stream()
                .filter(row -> code.equals(row.getCode()))
                .findFirst()
                .map(MemberClassCountRow::getMemberCount)
                .orElseThrow(() -> new AssertionError("no member-class count for " + code));
    }
}
