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

import org.junit.jupiter.api.Test;
import org.niis.xroad.catalog.persistence.entity.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles({"test", "general-testdata"})
@SuppressWarnings("PMD.AvoidDuplicateLiterals")
public class ServiceRepositoryV2Test {

    @Autowired
    ServiceRepositoryV2 serviceRepository;

    @Test
    public void testSearchByTextActiveOnly() {
        Page<Service> active = serviceRepository.searchByText("service", true, PageRequest.of(0, 50));
        Page<Service> all = serviceRepository.searchByText("service", false, PageRequest.of(0, 50));

        assertFalse(active.isEmpty());
        assertTrue(active.getTotalElements() < all.getTotalElements());
        active.getContent().forEach(s -> {
            assertFalse(s.getStatusInfo().isRemoved());
            assertFalse(s.getSubsystem().getStatusInfo().isRemoved(),
                    "Parent subsystem must not be removed in activeOnly results");
            assertFalse(s.getSubsystem().getMember().getStatusInfo().isRemoved(),
                    "Parent member must not be removed in activeOnly results");
        });
    }

    @Test
    public void testSearchByTextExactMatch() {
        Page<Service> hits = serviceRepository.searchByText("getRandom", true, PageRequest.of(0, 10));
        assertFalse(hits.getContent().isEmpty());
        assertTrue(hits.getContent().stream().anyMatch(s -> "getRandom".equals(s.getServiceCode())));
    }

    @Test
    public void testSearchByTextNoMatches() {
        Page<Service> none = serviceRepository.searchByText("zzzzzzzzzz", true, PageRequest.of(0, 10));
        assertTrue(none.isEmpty());
    }

    @Test
    public void testFindAggregatesForList() {
        List<Object[]> all = serviceRepository.findAggregatesForList(null, null, false, PageRequest.of(0, 50));
        assertFalse(all.isEmpty());
        all.forEach(row -> {
            assertEquals(4, row.length);
            assertNotNull(row[0]);
            assertNotNull(row[3]);
        });
    }

    @Test
    public void testCountAggregatesForList() {
        long total = serviceRepository.countAggregatesForList(null, null, false);
        long pageSize = serviceRepository.findAggregatesForList(null, null, false, PageRequest.of(0, 10000)).size();
        assertEquals(total, pageSize);
        assertTrue(total > 0);
    }

    @Test
    public void testFindAggregatesForListFilteredByMemberClass() {
        List<Object[]> pubOnly = serviceRepository.findAggregatesForList("PUB", null, false, PageRequest.of(0, 50));
        assertFalse(pubOnly.isEmpty());
        pubOnly.forEach(row -> assertEquals("PUB", row[0]));

        List<Object[]> govOnly = serviceRepository.findAggregatesForList("GOV", null, false, PageRequest.of(0, 50));
        assertTrue(govOnly.isEmpty());
    }

    @Test
    public void testFindAggregatesForListFilteredBySoapTypeKeepsAllVersions() {
        List<Object[]> soapOnly = serviceRepository.findAggregatesForList(
                null, "SOAP", false, PageRequest.of(0, 50));

        boolean mixedPresent = soapOnly.stream()
                .anyMatch(row -> "14151328".equals(row[1]) && "mixedSvc".equals(row[3]));

        assertTrue(mixedPresent,
                "Expected the mixed-type service aggregate to appear when filtering by SOAP");
    }

    /**
     * Task 1.5 invariant: a service whose only WSDL row is removed must classify as REST in the
     * aggregate HAVING predicate — the predicate evaluates against active-descriptor rows only.
     * Fixture service id 33 'svc_removed_wsdl_only' under member 14151328 / subsystem_a1 has
     * a removed WSDL and no OpenAPI, so it must appear under REST and NOT under SOAP.
     */
    @Test
    public void testFindAggregatesForListRemovedOnlyWsdlClassifiesAsRest() {
        List<Object[]> soapOnly = serviceRepository.findAggregatesForList(
                null, "SOAP", false, PageRequest.of(0, 100));
        assertFalse(soapOnly.stream()
                        .anyMatch(row -> "svc_removed_wsdl_only".equals(row[3])),
                "service with only a removed WSDL must NOT classify as SOAP");

        List<Object[]> restOnly = serviceRepository.findAggregatesForList(
                null, "REST", false, PageRequest.of(0, 100));
        assertTrue(restOnly.stream()
                        .anyMatch(row -> "svc_removed_wsdl_only".equals(row[3])),
                "service with only a removed WSDL must classify as REST (default)");

        long soapCount = serviceRepository.countAggregatesForList(null, "SOAP", false);
        long restCount = serviceRepository.countAggregatesForList(null, "REST", false);
        assertTrue(restCount > 0);
        assertTrue(soapCount > 0);
        // The same aggregate cannot be both SOAP and REST: count via explicit filter must exclude it.
        long soapRowsWithThisCode = soapOnly.stream()
                .filter(row -> "svc_removed_wsdl_only".equals(row[3]))
                .count();
        assertEquals(0L, soapRowsWithThisCode);
    }

    @Test
    public void testFindAggregatesForListActiveOnlyExcludesRemovedVersions() {
        List<Object[]> active = serviceRepository.findAggregatesForList(null, null, true, PageRequest.of(0, 100));
        long allRemovedCode = active.stream()
                .filter(row -> "removed-service_7-1-3".equals(row[3]))
                .count();
        assertEquals(0L, allRemovedCode);

        List<Object[]> all = serviceRepository.findAggregatesForList(null, null, false, PageRequest.of(0, 100));
        long allRemovedCodeIncluded = all.stream()
                .filter(row -> "removed-service_7-1-3".equals(row[3]))
                .count();
        assertEquals(1L, allRemovedCodeIncluded);
    }

    @Test
    public void testCountAggregatesForListActiveOnly() {
        long activeTotal = serviceRepository.countAggregatesForList(null, null, true);
        long allTotal = serviceRepository.countAggregatesForList(null, null, false);
        assertTrue(activeTotal < allTotal);
    }

    @Test
    public void testFindAggregatesForListIsOrderedByServiceCodeAscending() {
        List<Object[]> rows = serviceRepository.findAggregatesForList(null, null, false, PageRequest.of(0, 100));
        assertFalse(rows.isEmpty());
        List<String> codes = new ArrayList<>();
        rows.forEach(row -> codes.add((String) row[3]));
        List<String> sorted = new ArrayList<>(codes);
        Collections.sort(sorted);
        assertEquals(sorted, codes);
    }

    @Test
    public void testFindCreatedBetween() {
        LocalDateTime start = LocalDateTime.of(2016, 1, 1, 0, 0);
        LocalDateTime end = LocalDateTime.of(2016, 12, 31, 0, 0);
        List<Service> created = serviceRepository.findCreatedBetween(start, end);
        assertFalse(created.isEmpty());
        assertTrue(created.stream().anyMatch(s -> "getRandom".equals(s.getServiceCode())));
        assertTrue(created.stream().anyMatch(s -> "testService".equals(s.getServiceCode())));
        assertTrue(created.stream().anyMatch(s -> "svc_removed_wsdl_only".equals(s.getServiceCode())),
                "Task 1.5 fixture service 33 must be in the 2016 created range");
        created.forEach(s -> {
            LocalDateTime c = s.getStatusInfo().getCreated();
            assertTrue(!c.isBefore(start) && c.isBefore(end));
        });

        LocalDateTime emptyStart = LocalDateTime.of(2018, 1, 1, 0, 0);
        LocalDateTime emptyEnd = LocalDateTime.of(2018, 12, 31, 0, 0);
        List<Service> nothingCreated = serviceRepository.findCreatedBetween(emptyStart, emptyEnd);
        assertTrue(nothingCreated.isEmpty());
    }

    @Test
    public void testFindChangedBetweenExcludesCreatedAndRemoved() {
        LocalDateTime start = LocalDateTime.of(2017, 1, 1, 0, 0);
        LocalDateTime end = LocalDateTime.of(2017, 12, 31, 0, 0);
        List<Service> changed = serviceRepository.findChangedBetween(start, end);
        assertFalse(changed.isEmpty());
        assertTrue(changed.stream().anyMatch(s -> "dummy-service_5-1-1-changed".equals(s.getServiceCode())));
        assertTrue(changed.stream().anyMatch(s -> "dummy-service_7-1-1-changed".equals(s.getServiceCode())));
        changed.forEach(s -> {
            LocalDateTime ch = s.getStatusInfo().getChanged();
            LocalDateTime c = s.getStatusInfo().getCreated();
            LocalDateTime r = s.getStatusInfo().getRemoved();
            assertNotNull(ch);
            assertTrue(!ch.isBefore(start) && ch.isBefore(end));
            assertTrue(c.isBefore(start) || !c.isBefore(end));
            assertTrue(r == null || r.isBefore(start) || !r.isBefore(end));
        });
    }

    @Test
    public void testFindRemovedBetween() {
        LocalDateTime start = LocalDateTime.of(2016, 1, 1, 0, 0);
        LocalDateTime end = LocalDateTime.of(2016, 12, 31, 0, 0);
        List<Service> removed = serviceRepository.findRemovedBetween(start, end);
        assertFalse(removed.isEmpty());
        removed.forEach(s -> {
            LocalDateTime r = s.getStatusInfo().getRemoved();
            assertNotNull(r);
            assertTrue(!r.isBefore(start) && r.isBefore(end));
        });
    }
}
