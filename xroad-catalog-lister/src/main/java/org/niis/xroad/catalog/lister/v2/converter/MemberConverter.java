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

import org.niis.xroad.catalog.lister.v2.dto.FullMemberDto;
import org.niis.xroad.catalog.lister.v2.dto.FullSubsystemDto;
import org.niis.xroad.catalog.lister.v2.dto.MemberDto;
import org.niis.xroad.catalog.persistence.entity.Member;
import org.niis.xroad.catalog.persistence.entity.Service;
import org.niis.xroad.catalog.persistence.entity.StatusInfo;
import org.niis.xroad.catalog.persistence.entity.Subsystem;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

@Component
public class MemberConverter {

    public MemberDto toDto(Member member) {
        return toDto(member, false);
    }

    public MemberDto toDtoIncludingRemoved(Member member) {
        return toDto(member, true);
    }

    private MemberDto toDto(Member member, boolean includeRemoved) {
        Set<Subsystem> subsystems = includeRemoved ? member.getAllSubsystems() : member.getActiveSubsystems();
        int subsystemCount = subsystems.size();
        int serviceCount = 0;
        for (Subsystem sub : subsystems) {
            Set<Service> services = includeRemoved ? sub.getAllServices() : sub.getActiveServices();
            serviceCount += services.size();
        }
        StatusInfo info = member.getStatusInfo();
        return MemberDto.builder()
                .memberClass(member.getMemberClass())
                .memberCode(member.getMemberCode())
                .name(member.getName())
                .isProvider(computeIsProvider(member))
                .subsystemCount(subsystemCount)
                .serviceCount(serviceCount)
                .created(info.getCreated())
                .changed(info.getChanged())
                .fetched(info.getFetched())
                .removed(info.getRemoved())
                .build();
    }

    /**
     * Builds the {@link FullMemberDto} for the {@code ?full=true} browse endpoint. The flat
     * member-level fields (including {@code subsystemCount} and {@code serviceCount}) are computed
     * identically to {@link #toDto(Member, boolean)}; the caller supplies the already-assembled
     * {@code subsystems} list so this converter does not depend on the subsystem converter or the
     * service aggregator.
     */
    public FullMemberDto toFullDto(Member member, List<FullSubsystemDto> subsystems, boolean includeRemoved) {
        Set<Subsystem> entitySubsystems = includeRemoved ? member.getAllSubsystems() : member.getActiveSubsystems();
        int subsystemCount = entitySubsystems.size();
        int serviceCount = 0;
        for (Subsystem sub : entitySubsystems) {
            Set<Service> services = includeRemoved ? sub.getAllServices() : sub.getActiveServices();
            serviceCount += services.size();
        }
        StatusInfo info = member.getStatusInfo();
        return FullMemberDto.builder()
                .memberClass(member.getMemberClass())
                .memberCode(member.getMemberCode())
                .name(member.getName())
                .isProvider(computeIsProvider(member))
                .subsystemCount(subsystemCount)
                .serviceCount(serviceCount)
                .created(info.getCreated())
                .changed(info.getChanged())
                .fetched(info.getFetched())
                .removed(info.getRemoved())
                .subsystems(subsystems)
                .build();
    }

    /**
     * Spec §6.1 / Task 1.5: a member is a provider iff it is not removed and has at least one
     * active service under an active subsystem. No descriptor check — a descriptor-less active
     * service still classifies as REST under {@code ServiceClassifier}, so it counts. This method
     * is pinned to the active view independent of {@code includeRemoved}: a removed member is
     * never currently a provider, even with stale active children surfaced via includeRemoved.
     */
    private boolean computeIsProvider(Member member) {
        if (member.getStatusInfo().isRemoved()) {
            return false;
        }
        for (Subsystem sub : member.getActiveSubsystems()) {
            if (!sub.getActiveServices().isEmpty()) {
                return true;
            }
        }
        return false;
    }
}
