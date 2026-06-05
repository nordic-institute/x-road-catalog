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
package org.niis.xroad.catalog.lister.v2.converter;

import org.junit.jupiter.api.Test;
import org.niis.xroad.catalog.lister.v2.dto.SubsystemDto;
import org.niis.xroad.catalog.persistence.entity.Member;
import org.niis.xroad.catalog.persistence.entity.Service;
import org.niis.xroad.catalog.persistence.entity.StatusInfo;
import org.niis.xroad.catalog.persistence.entity.Subsystem;

import java.time.LocalDateTime;
import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class SubsystemConverterTest {

    private final SubsystemConverter converter = new SubsystemConverter();

    @Test
    void testConvertWithSubsystemNameLookup() {
        Member m = buildMember();
        Subsystem sub = buildSubsystem(m, "TaxServices", false);
        Service svc = buildService(sub, "getTaxInfo", "v1", false);
        sub.getAllServices().add(svc);
        m.getAllSubsystems().add(sub);

        SubsystemDto dto = converter.toDto(sub, (mc, mcode, sc) -> "Tax Services");
        assertEquals("PUB", dto.getMemberClass());
        assertEquals("14151328", dto.getMemberCode());
        assertEquals("Nahka-Albert", dto.getMemberName());
        assertEquals("TaxServices", dto.getSubsystemCode());
        assertEquals("Tax Services", dto.getSubsystemName());
        assertEquals(1, dto.getServiceCount());
    }

    @Test
    void testConvertWithoutSubsystemName() {
        Member m = buildMember();
        Subsystem sub = buildSubsystem(m, "PlainSub", false);
        m.getAllSubsystems().add(sub);

        SubsystemDto dto = converter.toDto(sub, (mc, mcode, sc) -> null);
        assertNull(dto.getSubsystemName());
    }

    private Member buildMember() {
        Member m = new Member();
        m.setXRoadInstance("dev-cs");
        m.setMemberClass("PUB");
        m.setMemberCode("14151328");
        m.setName("Nahka-Albert");
        LocalDateTime now = LocalDateTime.now();
        m.setStatusInfo(new StatusInfo(now, now, now, null));
        m.setSubsystems(new HashSet<>());
        return m;
    }

    private Subsystem buildSubsystem(Member parent, String code, boolean removed) {
        Subsystem s = new Subsystem();
        s.setSubsystemCode(code);
        s.setMember(parent);
        LocalDateTime now = LocalDateTime.now();
        s.setStatusInfo(new StatusInfo(now, now, now, removed ? now : null));
        s.setServices(new HashSet<>());
        return s;
    }

    private Service buildService(Subsystem parent, String code, String version, boolean removed) {
        Service s = new Service();
        s.setSubsystem(parent);
        s.setServiceCode(code);
        s.setServiceVersion(version);
        LocalDateTime now = LocalDateTime.now();
        s.setStatusInfo(new StatusInfo(now, now, now, removed ? now : null));
        return s;
    }
}
