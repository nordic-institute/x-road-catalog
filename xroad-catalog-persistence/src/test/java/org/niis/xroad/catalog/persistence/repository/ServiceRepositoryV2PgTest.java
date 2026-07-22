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

import org.niis.xroad.catalog.persistence.repository.projection.ServiceAggregateRow;
import org.niis.xroad.catalog.persistence.repository.projection.ServiceVersionRow;
import org.niis.xroad.catalog.persistence.testsupport.PostgresTestBase;
import org.niis.xroad.catalog.persistence.v2entity.ServiceV2;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Ground truth: {@code pg/v2-fixture.sql}. Under active GOV member M1's only active subsystem SS1
 * (id 11), four services are active: svcA (ids 21/22, versions 1.0/2.0), svcB (id 23, null
 * version), and svcF (id 27, the deliberate multi-descriptor anomaly with both an active WSDL and
 * an active OpenAPI, classified SOAP by the recompute since SOAP wins the priority order). svcC
 * (id 24) is removed. svcD (id 25, under COM member M3, which is removed) and svcE (id 26, under
 * COM member M4's removed subsystem SS4) are both excluded by the parent cascade.
 *
 * <p>Recomputed {@code service_type} per the fixture header: 21=SOAP, 22=OPENAPI (its WSDL id 32
 * is removed, leaving only the active OpenAPI id 33), 23=REST (rest row 36), 27=SOAP. svcC (id 24),
 * though removed, is also reclassified: 24=UNKNOWN (no active descriptor and no rest row).
 *
 * <p>Same test-only {@code @EntityScan} pattern as {@code MemberRepositoryV2PgTest} /
 * {@code SubsystemRepositoryV2PgTest}: production {@code PersistenceDefaultConfiguration} scans
 * only {@code entity}, so the V2 read-model package must be added back for this context.
 */
@SpringBootTest
@EntityScan(basePackages = {
        "org.niis.xroad.catalog.persistence.entity",
        "org.niis.xroad.catalog.persistence.v2entity"
})
@Sql(scripts = "classpath:pg/v2-fixture.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class ServiceRepositoryV2PgTest extends PostgresTestBase {

    private static final String INSTANCE = "TEST";
    private static final String GOV = "GOV";
    private static final String SS1 = "SS1";

    @Autowired
    private ServiceRepositoryV2 serviceRepository;

    @Autowired
    private DescriptorRepositoryV2 descriptorRepository;

    @Autowired
    private DenormalizationRepository denormalizationRepository;

    @BeforeEach
    void recomputeDenormalizedColumns() {
        denormalizationRepository.recomputeMemberIsProvider();
        denormalizationRepository.recomputeServiceType();
    }

    @Test
    void findActiveAggregatesForListReturnsThreeAggregatesOrderedByServiceCode() {
        List<ServiceAggregateRow> rows = serviceRepository.findActiveAggregatesForList(
                INSTANCE, null, null, Pageable.unpaged());

        assertEquals(3, rows.size());
        assertEquals("svcA", rows.get(0).getServiceCode());
        assertEquals("svcB", rows.get(1).getServiceCode());
        assertEquals("svcF", rows.get(2).getServiceCode());
        rows.forEach(row -> {
            assertEquals(GOV, row.getMemberClass());
            assertEquals("M1", row.getMemberCode());
            assertEquals(SS1, row.getSubsystemCode());
            assertEquals(11L, row.getSubsystemId());
        });
    }

    @Test
    void findActiveAggregatesForListFiltersByServiceType() {
        List<ServiceAggregateRow> soap = serviceRepository.findActiveAggregatesForList(
                INSTANCE, null, "SOAP", Pageable.unpaged());
        assertEquals(Set.of("svcA", "svcF"), codesOf(soap));

        List<ServiceAggregateRow> openApi = serviceRepository.findActiveAggregatesForList(
                INSTANCE, null, "OPENAPI", Pageable.unpaged());
        assertEquals(Set.of("svcA"), codesOf(openApi));

        List<ServiceAggregateRow> rest = serviceRepository.findActiveAggregatesForList(
                INSTANCE, null, "REST", Pageable.unpaged());
        assertEquals(Set.of("svcB"), codesOf(rest));
    }

    /**
     * Pins that {@link ServiceRepositoryV2#countActiveAggregatesForList} and the unpaged
     * {@link ServiceRepositoryV2#findActiveAggregatesForList} agree for every combination of
     * {@code memberClass} and {@code serviceType} against the fixture. GOV data matches the
     * unfiltered totals (only SS1/M1 qualifies); COM contributes zero rows in every case because
     * M3 is removed and M4's only subsystem SS4 is removed (both fail the parent cascade).
     */
    @Test
    void countAndUnpagedRowsAgreeAcrossFullFilterMatrix() {
        record Expectation(String memberClass, String serviceType, long expectedCount) {
        }
        List<Expectation> expectations = List.of(
                new Expectation(null, null, 3),
                new Expectation(null, "SOAP", 2),
                new Expectation(null, "OPENAPI", 1),
                new Expectation(null, "REST", 1),
                new Expectation(GOV, null, 3),
                new Expectation(GOV, "SOAP", 2),
                new Expectation(GOV, "OPENAPI", 1),
                new Expectation(GOV, "REST", 1),
                new Expectation("COM", null, 0),
                new Expectation("COM", "SOAP", 0),
                new Expectation("COM", "OPENAPI", 0),
                new Expectation("COM", "REST", 0));

        expectations.forEach(expectation -> {
            long count = serviceRepository.countActiveAggregatesForList(
                    INSTANCE, expectation.memberClass(), expectation.serviceType());
            int rowCount = serviceRepository.findActiveAggregatesForList(
                    INSTANCE, expectation.memberClass(), expectation.serviceType(), Pageable.unpaged()).size();

            assertEquals(expectation.expectedCount(), count,
                    () -> "count mismatch for memberClass=" + expectation.memberClass()
                            + " serviceType=" + expectation.serviceType());
            assertEquals(count, rowCount,
                    () -> "count/row parity broken for memberClass=" + expectation.memberClass()
                            + " serviceType=" + expectation.serviceType());
        });
    }

    @Test
    void findActiveVersionRowsForKeysReturnsBothSvcAVersions() {
        List<ServiceVersionRow> rows = serviceRepository.findActiveVersionRowsForKeys(
                List.of(11L), List.of("svcA"));

        assertEquals(2, rows.size());
        assertEquals("1.0", rows.get(0).getServiceVersion());
        assertEquals("SOAP", rows.get(0).getServiceType());
        assertEquals("2.0", rows.get(1).getServiceVersion());
        assertEquals("OPENAPI", rows.get(1).getServiceType());
        rows.forEach(row -> assertEquals("Provider Member", row.getMemberName()));
    }

    @Test
    void findActiveVersionRowsForServiceExcludesRemovedAndCascadedServices() {
        List<ServiceVersionRow> svcA = serviceRepository.findActiveVersionRowsForService(
                INSTANCE, GOV, "M1", SS1, "svcA");
        assertEquals(2, svcA.size());

        List<ServiceVersionRow> svcC = serviceRepository.findActiveVersionRowsForService(
                INSTANCE, GOV, "M1", SS1, "svcC");
        assertTrue(svcC.isEmpty(), "svcC is itself removed");

        List<ServiceVersionRow> svcD = serviceRepository.findActiveVersionRowsForService(
                INSTANCE, "COM", "M3", "SS3", "svcD");
        assertTrue(svcD.isEmpty(), "svcD's parent member M3 is removed");
    }

    @Test
    @Transactional
    void findActiveVersionsByNaturalKeyReturnsOneEntityWithOneActiveEndpoint() {
        List<ServiceV2> svcB = serviceRepository.findActiveVersionsByNaturalKey(
                INSTANCE, GOV, "M1", SS1, "svcB");

        assertEquals(1, svcB.size());
        assertEquals(1, svcB.get(0).getActiveEndpoints().size(), "POST /bar is removed, GET /foo is active");
    }

    @Test
    void findActiveVersionByNaturalKeyAndFindActiveNullVersionByNaturalKeyResolveExactVersions() {
        Optional<ServiceV2> svcA10 = serviceRepository.findActiveVersionByNaturalKey(
                INSTANCE, GOV, "M1", SS1, "svcA", "1.0");
        assertTrue(svcA10.isPresent());

        Optional<ServiceV2> svcB = serviceRepository.findActiveNullVersionByNaturalKey(
                INSTANCE, GOV, "M1", SS1, "svcB");
        assertTrue(svcB.isPresent());
    }

    @Test
    void existsActiveByNaturalKeyChecksServiceAndParentCascade() {
        assertTrue(serviceRepository.existsActiveByNaturalKey(INSTANCE, GOV, "M1", SS1, "svcB"));
        assertFalse(serviceRepository.existsActiveByNaturalKey(INSTANCE, GOV, "M1", SS1, "svcC"),
                "svcC is itself removed");
        assertFalse(serviceRepository.existsActiveByNaturalKey(INSTANCE, "COM", "M3", "SS3", "svcD"),
                "svcD's parent member M3 is removed");
    }

    @Test
    void existsActiveVersionChecksExactVersionIncludingNullSentinelWithoutHydratingEndpoints() {
        assertTrue(serviceRepository.existsActiveVersionByNaturalKey("TEST", "GOV", "M1", SS1, "svcA", "1.0"));
        assertFalse(serviceRepository.existsActiveVersionByNaturalKey("TEST", "GOV", "M1", SS1, "svcA", "9.9"));
        assertTrue(serviceRepository.existsActiveNullVersionByNaturalKey("TEST", "GOV", "M1", SS1, "svcB"));
        assertFalse(serviceRepository.existsActiveNullVersionByNaturalKey("TEST", "GOV", "M1", SS1, "svcA"));
    }

    @Test
    void findActiveWsdlDataAndOpenApiDataReflectDescriptorRemovalAndAnomaly() {
        assertEquals(List.of("<wsdl>svcA-1</wsdl>"), descriptorRepository.findActiveWsdlData(21));

        assertTrue(descriptorRepository.findActiveWsdlData(22).isEmpty(), "service 22's only WSDL is removed");
        assertEquals(List.of("{\"openapi\":\"3.0.0\"}"), descriptorRepository.findActiveOpenApiData(22));

        assertEquals(List.of("<wsdl>svcF</wsdl>"), descriptorRepository.findActiveWsdlData(27));
        assertEquals(List.of("{\"openapi\":\"3.0.0\"}"), descriptorRepository.findActiveOpenApiData(27));
    }

    private static Set<String> codesOf(List<ServiceAggregateRow> rows) {
        return rows.stream().map(ServiceAggregateRow::getServiceCode).collect(Collectors.toSet());
    }
}
