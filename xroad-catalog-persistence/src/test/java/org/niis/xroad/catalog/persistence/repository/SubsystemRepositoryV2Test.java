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
import org.niis.xroad.catalog.persistence.entity.Subsystem;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles({"test", "general-testdata"})
public class SubsystemRepositoryV2Test {

    private static final String INSTANCE = "dev-cs";
    private static final String PUB = "PUB";
    private static final String CODE_14151328 = "14151328";
    private static final String SUBSYSTEM_A1 = "subsystem_a1";
    private static final String SUBSYSTEM_A3_REMOVED = "subsystem_a3_removed";

    @Autowired
    SubsystemRepositoryV2 subsystemRepository;

    @Test
    public void testFindAnyByNaturalKeyIncludesRemoved() {
        Subsystem active = subsystemRepository.findAnyByNaturalKey(INSTANCE, PUB, CODE_14151328, SUBSYSTEM_A1);
        assertNotNull(active);
        assertFalse(active.getStatusInfo().isRemoved());

        Subsystem removed = subsystemRepository.findAnyByNaturalKey(INSTANCE, PUB, CODE_14151328, SUBSYSTEM_A3_REMOVED);
        assertNotNull(removed);
        assertTrue(removed.getStatusInfo().isRemoved());

        Subsystem missing = subsystemRepository.findAnyByNaturalKey(INSTANCE, PUB, CODE_14151328, "does-not-exist");
        assertNull(missing);
    }

    @Test
    public void testFindActiveByNaturalKeyWithServicesPrefetchesServices() {
        Subsystem subsystem = subsystemRepository.findActiveByNaturalKeyWithServices(
                INSTANCE, PUB, CODE_14151328, SUBSYSTEM_A1);
        assertNotNull(subsystem);
        assertFalse(subsystem.getStatusInfo().isRemoved());
        assertFalse(subsystem.getAllServices().isEmpty(), "services must be pre-fetched, not lazily loaded");
    }

    @Test
    public void testFindActiveByNaturalKeyWithServicesExcludesRemovedParents() {
        Subsystem removed = subsystemRepository.findActiveByNaturalKeyWithServices(
                INSTANCE, PUB, CODE_14151328, SUBSYSTEM_A3_REMOVED);
        assertNull(removed, "removed subsystem must not surface in active lookup");

        Subsystem underRemovedMember = subsystemRepository.findActiveByNaturalKeyWithServices(
                INSTANCE, PUB, "14151329", "removed_subsystem");
        assertNull(underRemovedMember, "subsystem under removed member must not surface in active lookup");
    }

    @Test
    public void testFindAnyByNaturalKeyWithServicesIncludesRemoved() {
        Subsystem removed = subsystemRepository.findAnyByNaturalKeyWithServices(
                INSTANCE, PUB, CODE_14151328, SUBSYSTEM_A3_REMOVED);
        assertNotNull(removed);
        assertTrue(removed.getStatusInfo().isRemoved());

        Subsystem missing = subsystemRepository.findAnyByNaturalKeyWithServices(
                INSTANCE, PUB, CODE_14151328, "does-not-exist");
        assertNull(missing);
    }

    @Test
    public void testSearchByText() {
        Page<Subsystem> activeHits = subsystemRepository.searchByText("subsystem_a", true, PageRequest.of(0, 10));
        assertEquals(2, activeHits.getTotalElements(), "activeOnly should exclude subsystem_a3_removed");
        activeHits.getContent().forEach(s -> {
            assertTrue(s.getSubsystemCode().contains("subsystem_a"));
            assertFalse(s.getStatusInfo().isRemoved());
        });

        Page<Subsystem> allHits = subsystemRepository.searchByText("subsystem_a", false, PageRequest.of(0, 10));
        assertEquals(3, allHits.getTotalElements(), "includeRemoved should return subsystem_a3_removed too");
        assertTrue(allHits.getContent().stream().anyMatch(s -> SUBSYSTEM_A3_REMOVED.equals(s.getSubsystemCode())));

        Page<Subsystem> none = subsystemRepository.searchByText("zzzzzzzzzz", true, PageRequest.of(0, 10));
        assertTrue(none.isEmpty());
    }

    @Test
    public void testFindForList() {
        Page<Subsystem> active = subsystemRepository.findForList(null, true, PageRequest.of(0, 50));
        assertFalse(active.isEmpty());
        active.getContent().forEach(s -> assertFalse(s.getStatusInfo().isRemoved()));

        Page<Subsystem> pubOnly = subsystemRepository.findForList(PUB, true, PageRequest.of(0, 50));
        assertTrue(pubOnly.getContent().stream().allMatch(s -> PUB.equals(s.getMember().getMemberClass())));

        Page<Subsystem> govOnly = subsystemRepository.findForList("GOV", true, PageRequest.of(0, 50));
        assertTrue(govOnly.isEmpty());
    }

    @Test
    public void testFindForListIncludesRemovedWhenNotActiveOnly() {
        Page<Subsystem> active = subsystemRepository.findForList(null, true, PageRequest.of(0, 50));
        Page<Subsystem> all = subsystemRepository.findForList(null, false, PageRequest.of(0, 50));
        assertTrue(active.getTotalElements() < all.getTotalElements());
        assertTrue(all.getContent().stream().anyMatch(s -> "removed_subsystem".equals(s.getSubsystemCode())));
        assertTrue(all.getContent().stream().anyMatch(s -> SUBSYSTEM_A3_REMOVED.equals(s.getSubsystemCode())));
    }

    @Test
    public void testActiveOnlyExcludesSubsystemsOfRemovedMembers() {
        // Fixture: member id 8 (code 14151329 "Removed item") is removed.
        // Its subsystems (if any) must not appear in activeOnly=true results.
        Page<Subsystem> active = subsystemRepository.findForList(null, true, PageRequest.of(0, 100));
        active.getContent().forEach(s -> {
            assertFalse(s.getMember().getStatusInfo().isRemoved(),
                    "activeOnly must exclude subsystems whose parent member is removed");
        });
    }

    @Test
    public void testFindCreatedBetween() {
        LocalDateTime start = LocalDateTime.of(2016, 1, 1, 0, 0);
        LocalDateTime end = LocalDateTime.of(2016, 12, 31, 0, 0);
        List<Subsystem> created = subsystemRepository.findCreatedBetween(start, end);
        assertFalse(created.isEmpty(), "Fixture contains subsystems created in 2016");
        assertTrue(created.stream().anyMatch(s -> SUBSYSTEM_A1.equals(s.getSubsystemCode())));
        assertTrue(created.stream().anyMatch(s -> "subsystem_b1".equals(s.getSubsystemCode())));
        created.forEach(s -> {
            LocalDateTime c = s.getStatusInfo().getCreated();
            assertTrue(!c.isBefore(start) && c.isBefore(end));
        });

        LocalDateTime emptyStart = LocalDateTime.of(2018, 1, 1, 0, 0);
        LocalDateTime emptyEnd = LocalDateTime.of(2018, 12, 31, 0, 0);
        List<Subsystem> nothingCreated = subsystemRepository.findCreatedBetween(emptyStart, emptyEnd);
        assertTrue(nothingCreated.isEmpty());
    }

    @Test
    public void testFindChangedBetweenExcludesCreatedAndRemoved() {
        LocalDateTime start = LocalDateTime.of(2017, 1, 1, 0, 0);
        LocalDateTime end = LocalDateTime.of(2017, 12, 31, 0, 0);
        List<Subsystem> changed = subsystemRepository.findChangedBetween(start, end);
        assertFalse(changed.isEmpty());
        assertTrue(changed.stream().anyMatch(s -> "subsystem_4-1-changed".equals(s.getSubsystemCode())));
        assertTrue(changed.stream().anyMatch(s -> "subsystem_7-2-changed".equals(s.getSubsystemCode())));
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
        LocalDateTime start = LocalDateTime.of(2017, 1, 1, 0, 0);
        LocalDateTime end = LocalDateTime.of(2017, 12, 31, 0, 0);
        List<Subsystem> removed = subsystemRepository.findRemovedBetween(start, end);
        assertFalse(removed.isEmpty());
        assertTrue(removed.stream().anyMatch(s -> "removed_subsystem".equals(s.getSubsystemCode())));
        removed.forEach(s -> {
            LocalDateTime r = s.getStatusInfo().getRemoved();
            assertNotNull(r);
            assertTrue(!r.isBefore(start) && r.isBefore(end));
        });
    }
}
