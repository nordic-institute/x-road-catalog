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
import org.niis.xroad.catalog.lister.v2.dto.FullMemberDto;
import org.niis.xroad.catalog.lister.v2.dto.FullSubsystemDto;
import org.niis.xroad.catalog.lister.v2.dto.MemberDto;
import org.niis.xroad.catalog.lister.v2.dto.ServiceDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@TestPropertySource(properties = {"xroad-catalog.shared-params-file=src/test/resources/shared-params-dev-cs.xml"})
@ActiveProfiles({"test", "general-testdata"})
public class MemberServiceV2Test {

    private static final String PUB = "PUB";

    @Autowired
    private MemberServiceV2 memberService;

    @Test
    public void testGetByNaturalKeyActive() {
        MemberDto dto = memberService.getByNaturalKey(PUB, "14151328", false);
        assertNotNull(dto);
        assertEquals("Nahka-Albert", dto.getName());
    }

    @Test
    public void testGetByNaturalKeyRemovedReturns404Default() {
        MemberDto dto = memberService.getByNaturalKey(PUB, "14151329", false);
        assertNull(dto, "removed member must return null by default (controllers map to 404)");
    }

    @Test
    public void testGetByNaturalKeyRemovedWithIncludeRemoved() {
        MemberDto dto = memberService.getByNaturalKey(PUB, "14151329", true);
        assertNotNull(dto);
        assertNotNull(dto.getRemoved(), "removed timestamp should be set");
    }

    @Test
    public void testGetForList() {
        Page<MemberDto> page = memberService.getForList(PUB, null, false, PageRequest.of(0, 20));
        assertTrue(page.getTotalElements() > 0);
        page.getContent().forEach(dto -> assertEquals(PUB, dto.getMemberClass()));
    }

    @Test
    public void testGetFullTreeHappyPath() {
        FullMemberDto dto = memberService.getFullTree(PUB, "14151328", false);
        assertNotNull(dto);
        assertEquals(PUB, dto.getMemberClass());
        assertEquals("14151328", dto.getMemberCode());
        assertEquals("Nahka-Albert", dto.getName());
        assertNotNull(dto.getSubsystems());
        assertFalse(dto.getSubsystems().isEmpty());
        dto.getSubsystems().forEach(sub -> {
            assertNotNull(sub.getServices());
            assertNull(sub.getRemoved(), "active-only view must exclude removed subsystems");
        });
    }

    @Test
    public void testGetFullTreeReturnsNullForMissing() {
        FullMemberDto dto = memberService.getFullTree(PUB, "does-not-exist", false);
        assertNull(dto);
        FullMemberDto dtoIncludeRemoved = memberService.getFullTree(PUB, "does-not-exist", true);
        assertNull(dtoIncludeRemoved);
    }

    @Test
    public void testGetFullTreeReturnsNullForRemovedWhenActiveOnly() {
        FullMemberDto dto = memberService.getFullTree(PUB, "14151329", false);
        assertNull(dto, "removed member must return null in active-only view");
    }

    @Test
    public void testGetFullTreeSurfacesRemovedWhenIncludeRemoved() {
        FullMemberDto dto = memberService.getFullTree(PUB, "14151329", true);
        assertNotNull(dto);
        assertNotNull(dto.getRemoved(), "removed timestamp must be exposed");
    }

    @Test
    public void testGetFullTreeSubsystemsAndServicesDeterministicOrdering() {
        // includeRemoved=true pulls both active subsystems (a1, a2) and the removed one (a3_removed)
        // under PUB/14151328. Subsystems must be sorted asc by subsystemCode; services within each
        // subsystem must be sorted asc by serviceCode.
        FullMemberDto dto = memberService.getFullTree(PUB, "14151328", true);
        assertNotNull(dto);
        List<FullSubsystemDto> subs = dto.getSubsystems();
        assertTrue(subs.size() >= 2, "expected at least 2 subsystems in full tree");
        List<String> subsystemCodes = subs.stream().map(FullSubsystemDto::getSubsystemCode).toList();
        List<String> expectedSorted = subsystemCodes.stream().sorted(Comparator.naturalOrder()).toList();
        assertEquals(expectedSorted, subsystemCodes, "subsystems must be sorted by subsystemCode asc");
        for (FullSubsystemDto sub : subs) {
            List<ServiceDto> services = sub.getServices();
            assertNotNull(services);
            List<String> serviceCodes = services.stream().map(ServiceDto::getServiceCode).toList();
            List<String> expectedServiceCodes = serviceCodes.stream().sorted(Comparator.naturalOrder()).toList();
            assertEquals(expectedServiceCodes, serviceCodes,
                    "services within subsystem " + sub.getSubsystemCode() + " must be sorted by serviceCode asc");
        }
    }
}
