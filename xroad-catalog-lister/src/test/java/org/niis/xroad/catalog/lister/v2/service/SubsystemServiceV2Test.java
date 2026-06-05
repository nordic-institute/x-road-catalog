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
package org.niis.xroad.catalog.lister.v2.service;

import org.junit.jupiter.api.Test;
import org.niis.xroad.catalog.lister.v2.dto.SubsystemDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@TestPropertySource(properties = {"xroad-catalog.shared-params-file=src/test/resources/shared-params-dev-cs.xml"})
@ActiveProfiles({"test", "general-testdata"})
public class SubsystemServiceV2Test {

    private static final String PUB = "PUB";
    private static final String CODE_14151328 = "14151328";
    private static final String SUBSYSTEM_A1 = "subsystem_a1";
    private static final String SUBSYSTEM_A2 = "subsystem_a2";
    private static final String SUBSYSTEM_A3_REMOVED = "subsystem_a3_removed";

    @Autowired
    private SubsystemServiceV2 subsystemService;

    @Test
    public void testGetByNaturalKey() {
        // From fixture: member 14151328 / subsystem_a1 (active).
        // shared-params-dev-cs.xml carries a <subsystemName>Subsystem A1</subsystemName> for it.
        SubsystemDto dto = subsystemService.getByNaturalKey(PUB, CODE_14151328, SUBSYSTEM_A1, false);
        assertNotNull(dto);
        assertEquals(SUBSYSTEM_A1, dto.getSubsystemCode());
        assertEquals("Subsystem A1", dto.getSubsystemName(),
                "subsystemName must come from shared-params-dev-cs.xml v5 fixture");
    }

    @Test
    public void testGetByNaturalKeyRemovedReturnsNullByDefault() {
        SubsystemDto dto = subsystemService.getByNaturalKey(PUB, CODE_14151328, SUBSYSTEM_A3_REMOVED, false);
        assertNull(dto);
    }

    @Test
    public void testGetByNaturalKeyWithIncludeRemoved() {
        SubsystemDto dto = subsystemService.getByNaturalKey(PUB, CODE_14151328, SUBSYSTEM_A3_REMOVED, true);
        assertNotNull(dto);
        assertNotNull(dto.getRemoved());
    }

    @Test
    public void testGetForList() {
        Page<SubsystemDto> page = subsystemService.getForList(PUB, false, PageRequest.of(0, 20));
        assertTrue(page.getTotalElements() > 0);
    }

    @Test
    public void testGetForMemberSortedBySubsystemCode() {
        List<SubsystemDto> result = subsystemService.getForMember(PUB, CODE_14151328, false);
        assertEquals(2, result.size(), "active-only view must include subsystem_a1 and subsystem_a2");
        assertEquals(SUBSYSTEM_A1, result.get(0).getSubsystemCode());
        assertEquals(SUBSYSTEM_A2, result.get(1).getSubsystemCode());
    }

    @Test
    public void testGetForMemberExcludesRemovedByDefault() {
        List<SubsystemDto> result = subsystemService.getForMember(PUB, CODE_14151328, false);
        result.forEach(dto -> assertNull(dto.getRemoved(), "active-only view must not surface removed subsystems"));
    }

    @Test
    public void testGetForMemberIncludesRemovedWhenRequested() {
        List<SubsystemDto> result = subsystemService.getForMember(PUB, CODE_14151328, true);
        assertEquals(3, result.size(), "includeRemoved=true must surface subsystem_a3_removed too");
        assertEquals(SUBSYSTEM_A1, result.get(0).getSubsystemCode());
        assertEquals(SUBSYSTEM_A2, result.get(1).getSubsystemCode());
        assertEquals(SUBSYSTEM_A3_REMOVED, result.get(2).getSubsystemCode());
        assertNotNull(result.get(2).getRemoved());
    }

    @Test
    public void testGetForMemberNonexistentReturnsEmpty() {
        List<SubsystemDto> result = subsystemService.getForMember(PUB, "no-such-member", false);
        assertTrue(result.isEmpty());
    }
}
