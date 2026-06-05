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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles({"test", "general-testdata"})
public class SearchRepositoryTest {

    @Autowired
    SearchRepository searchRepository;

    @Test
    public void testSearchUnionFindsMembersSubsystemsAndServices() {
        List<Object[]> rows = searchRepository.searchUnion("%subsystem%", 100, 0);
        assertFalse(rows.isEmpty());
        Set<String> types = rows.stream().map(r -> (String) r[0]).collect(Collectors.toSet());
        assertTrue(types.contains("subsystem"), "subsystem_* codes should match");
    }

    @Test
    public void testSearchUnionAggregatesServicesByServiceCode() {
        // Fixture: 'mixedSvc' under subsystem_a1 has 2 versions — must appear as ONE row.
        List<Object[]> rows = searchRepository.searchUnion("%mixedSvc%", 100, 0);
        long mixedSvcRows = rows.stream()
                .filter(r -> "service".equals(r[0]) && "mixedSvc".equals(r[7]))
                .count();
        assertEquals(1, mixedSvcRows, "service aggregate must appear once, not once per version");
    }

    @Test
    public void testSearchUnionExcludesRemoved() {
        // Fixture has 'subsystem_a3_removed' with removed != null; must NOT appear.
        List<Object[]> rows = searchRepository.searchUnion("%subsystem_a3_removed%", 100, 0);
        assertEquals(0, rows.size(), "removed entities must be filtered out");
    }

    @Test
    public void testSearchUnionIsCaseInsensitive() {
        List<Object[]> lower = searchRepository.searchUnion("%nahka%", 100, 0);
        List<Object[]> upper = searchRepository.searchUnion("%NAHKA%", 100, 0);
        assertEquals(lower.size(), upper.size());
        assertFalse(lower.isEmpty());
    }

    @Test
    public void testSearchUnionPaginationIsStable() {
        List<Object[]> page1 = searchRepository.searchUnion("%subsystem%", 2, 0);
        List<Object[]> page1Again = searchRepository.searchUnion("%subsystem%", 2, 0);
        assertEquals(page1.size(), page1Again.size());
        for (int i = 0; i < page1.size(); i++) {
            assertEquals(page1.get(i)[0], page1Again.get(i)[0]);
            assertEquals(page1.get(i)[2], page1Again.get(i)[2]);
        }
    }

    @Test
    public void testCountSearchUnionMatchesPageSum() {
        long total = searchRepository.countSearchUnion("%subsystem%");
        List<Object[]> all = searchRepository.searchUnion("%subsystem%", 10_000, 0);
        assertEquals(total, all.size());
    }
}
