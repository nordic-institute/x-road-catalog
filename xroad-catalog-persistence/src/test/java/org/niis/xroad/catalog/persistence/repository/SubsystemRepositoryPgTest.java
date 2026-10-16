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

import org.niis.xroad.catalog.persistence.v2.repository.SubsystemRepository;
import org.niis.xroad.catalog.persistence.v2.repository.projection.SubsystemListRow;
import org.niis.xroad.catalog.persistence.testsupport.PostgresTestBase;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.jdbc.Sql;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runs against {@code pg/v2-fixture.sql}. Only SS1 (under active member M1) is active: SS2 and
 * SS4 are themselves removed, SS3's parent member M3 is removed (parent-cascade). SS1's active
 * service count is 4 (service 24 is removed). Production configuration scans only {@code entity},
 * so the V2 read-model package is added back via {@code @EntityScan}.
 */
@SpringBootTest
@EntityScan(basePackages = {
        "org.niis.xroad.catalog.persistence.entity",
        "org.niis.xroad.catalog.persistence.v2.entity"
})
@Sql(scripts = "classpath:pg/v2-fixture.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class SubsystemRepositoryPgTest extends PostgresTestBase {

    private static final String INSTANCE = "TEST";

    @Autowired
    private SubsystemRepository subsystemRepository;

    @Autowired
    private DenormalizationRepository denormalizationRepository;

    @BeforeEach
    void recomputeDenormalizedColumns() {
        denormalizationRepository.recomputeMemberIsProvider();
        denormalizationRepository.recomputeServiceType();
    }

    @Test
    void findActiveForListReturnsOnlySubsystemSurvivingParentCascade() {
        Page<SubsystemListRow> page = subsystemRepository.findActiveForList(INSTANCE, null, PageRequest.of(0, 20));
        assertEquals(1, page.getTotalElements());

        SubsystemListRow ss1 = page.getContent().get(0);
        assertEquals("GOV", ss1.getMemberClass());
        assertEquals("M1", ss1.getMemberCode());
        assertEquals("Provider Member", ss1.getMemberName());
        assertEquals("SS1", ss1.getSubsystemCode());
        assertEquals(4, ss1.getServiceCount());
        assertEquals(LocalDateTime.of(2025, 1, 2, 10, 0), ss1.getCreated());
        assertEquals(LocalDateTime.of(2025, 1, 2, 10, 0), ss1.getChanged());
        assertEquals(LocalDateTime.of(2025, 6, 1, 10, 0), ss1.getFetched());
    }

    @Test
    void findActiveForListPageSizeProvesSqlLimit() {
        Page<SubsystemListRow> page = subsystemRepository.findActiveForList(INSTANCE, null, PageRequest.of(0, 1));
        assertEquals(1, page.getContent().size());
        assertEquals(1, page.getTotalElements());
    }

    @Test
    void findActiveForListFiltersByMemberClass() {
        Page<SubsystemListRow> gov = subsystemRepository.findActiveForList(INSTANCE, "GOV", PageRequest.of(0, 20));
        assertEquals(1, gov.getTotalElements());

        Page<SubsystemListRow> com = subsystemRepository.findActiveForList(INSTANCE, "COM", PageRequest.of(0, 20));
        assertEquals(0, com.getTotalElements());
    }

    @Test
    void findActiveForMemberReturnsSs1ForM1AndEmptyForM2() {
        List<SubsystemListRow> m1Subsystems = subsystemRepository.findActiveForMember(INSTANCE, "GOV", "M1");
        assertEquals(1, m1Subsystems.size());
        assertEquals("SS1", m1Subsystems.get(0).getSubsystemCode());

        List<SubsystemListRow> m2Subsystems = subsystemRepository.findActiveForMember(INSTANCE, "GOV", "M2");
        assertTrue(m2Subsystems.isEmpty());
    }

    @Test
    void existsActiveByNaturalKeyChecksSubsystemAndParentCascade() {
        assertTrue(subsystemRepository.existsActiveByNaturalKey(INSTANCE, "GOV", "M1", "SS1"));
        assertFalse(subsystemRepository.existsActiveByNaturalKey(INSTANCE, "GOV", "M1", "SS2"),
                "SS2 is itself removed");
        assertFalse(subsystemRepository.existsActiveByNaturalKey(INSTANCE, "COM", "M3", "SS3"),
                "SS3's parent member M3 is removed");
    }

    @Test
    void findActiveSummaryByNaturalKeyExcludesRemovedSubsystem() {
        Optional<SubsystemListRow> ss4 = subsystemRepository.findActiveSummaryByNaturalKey(INSTANCE, "COM", "M4", "SS4");
        assertTrue(ss4.isEmpty(), "SS4 is itself removed");

        Optional<SubsystemListRow> ss1 = subsystemRepository.findActiveSummaryByNaturalKey(INSTANCE, "GOV", "M1", "SS1");
        assertTrue(ss1.isPresent());
        assertEquals(4, ss1.get().getServiceCount());
    }

}
