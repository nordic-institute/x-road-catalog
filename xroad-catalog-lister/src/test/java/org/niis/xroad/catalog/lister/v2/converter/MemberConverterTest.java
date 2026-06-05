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
import org.niis.xroad.catalog.lister.v2.dto.MemberDto;
import org.niis.xroad.catalog.persistence.entity.Member;
import org.niis.xroad.catalog.persistence.entity.Service;
import org.niis.xroad.catalog.persistence.entity.StatusInfo;
import org.niis.xroad.catalog.persistence.entity.Subsystem;
import org.niis.xroad.catalog.persistence.entity.Wsdl;

import java.time.LocalDateTime;
import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MemberConverterTest {

    private final MemberConverter converter = new MemberConverter();

    @Test
    void testConvertActiveMemberWithProvider() {
        Member member = buildMember("PUB", "14151328", "Nahka-Albert", false);
        Subsystem sub = buildSubsystem(member, "sub1", false);
        Service svc = buildService(sub, "svcA", "v1", false);
        Wsdl wsdl = new Wsdl();
        wsdl.setStatusInfo(new StatusInfo(LocalDateTime.now(), LocalDateTime.now(), LocalDateTime.now(), null));
        svc.setWsdl(wsdl);
        sub.getAllServices().add(svc);
        member.getAllSubsystems().add(sub);

        MemberDto dto = converter.toDto(member);
        assertEquals("PUB", dto.getMemberClass());
        assertEquals("14151328", dto.getMemberCode());
        assertTrue(dto.isProvider(), "member with an active service under an active subsystem must be provider");
        assertEquals(1, dto.getSubsystemCount());
        assertEquals(1, dto.getServiceCount());
    }

    @Test
    void testConvertNonProviderNoServices() {
        Member member = buildMember("PUB", "14151329", "Plain member", false);
        Subsystem sub = buildSubsystem(member, "sub1", false);
        member.getAllSubsystems().add(sub);
        MemberDto dto = converter.toDto(member);
        assertFalse(dto.isProvider(), "member with no services is not a provider");
        assertEquals(1, dto.getSubsystemCount());
        assertEquals(0, dto.getServiceCount());
    }

    /**
     * Task 1.5 invariant 1: the new isProvider predicate is service-existence based (not
     * descriptor-existence based). A descriptor-less active service still counts.
     */
    @Test
    void testDescriptorLessActiveServiceMakesMemberProvider() {
        Member member = buildMember("PUB", "descriptor-less", "no descriptors", false);
        Subsystem sub = buildSubsystem(member, "sub1", false);
        Service svc = buildService(sub, "svcA", "v1", false);
        sub.getAllServices().add(svc);
        member.getAllSubsystems().add(sub);

        MemberDto dto = converter.toDto(member);
        assertTrue(dto.isProvider(),
                "member with an active service but no descriptor rows must still be a provider");
        assertEquals(1, dto.getServiceCount());
    }

    /**
     * Task 1.5 invariant 2: an active subsystem whose only service is removed does not make the
     * member a provider.
     */
    @Test
    void testActiveSubsystemWithOnlyRemovedServiceIsNotProvider() {
        Member member = buildMember("PUB", "only-removed-service", "only removed", false);
        Subsystem sub = buildSubsystem(member, "sub1", false);
        Service removedSvc = buildService(sub, "removedSvc", "v1", true);
        sub.getAllServices().add(removedSvc);
        member.getAllSubsystems().add(sub);

        MemberDto dto = converter.toDto(member);
        assertFalse(dto.isProvider(),
                "active subsystem with only removed services must not make member a provider");
    }

    /**
     * Task 1.5 invariant 3: an active service under a removed subsystem does not make the member a
     * provider - the subsystem-level filter excludes the orphaned child.
     */
    @Test
    void testActiveServiceUnderRemovedSubsystemIsNotProvider() {
        Member member = buildMember("PUB", "svc-under-removed-sub", "svc under removed sub", false);
        Subsystem removedSub = buildSubsystem(member, "sub1", true);
        Service activeSvc = buildService(removedSub, "svcA", "v1", false);
        removedSub.getAllServices().add(activeSvc);
        member.getAllSubsystems().add(removedSub);

        MemberDto dto = converter.toDto(member);
        assertFalse(dto.isProvider(),
                "active service under a removed subsystem must NOT make member a provider");
    }

    /**
     * Task 1.5 invariant 4: a removed member with stale active children is never a provider, in
     * both {@code toDto} and {@code toDtoIncludingRemoved}. The state indicator is pinned to the
     * active view independent of the {@code includeRemoved} flag.
     */
    @Test
    void testRemovedMemberWithStaleActiveChildrenIsNotProvider() {
        Member member = buildMember("PUB", "removed-with-stale", "removed with stale", true);
        Subsystem activeSub = buildSubsystem(member, "staleSub", false);
        Service activeSvc = buildService(activeSub, "staleSvc", "v1", false);
        activeSub.getAllServices().add(activeSvc);
        member.getAllSubsystems().add(activeSub);

        MemberDto dtoActive = converter.toDto(member);
        assertFalse(dtoActive.isProvider(),
                "removed member must never be a provider, even with stale active children (toDto)");

        MemberDto dtoIncludingRemoved = converter.toDtoIncludingRemoved(member);
        assertFalse(dtoIncludingRemoved.isProvider(),
                "removed member must never be a provider (toDtoIncludingRemoved short-circuit)");
    }

    private Member buildMember(String mc, String code, String name, boolean removed) {
        Member m = new Member();
        m.setXRoadInstance("dev-cs");
        m.setMemberClass(mc);
        m.setMemberCode(code);
        m.setName(name);
        LocalDateTime now = LocalDateTime.now();
        m.setStatusInfo(new StatusInfo(now, now, now, removed ? now : null));
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
