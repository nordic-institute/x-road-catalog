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
import org.niis.xroad.catalog.persistence.entity.StatusInfo;
import org.niis.xroad.catalog.persistence.repository.projection.MemberListRow;
import org.niis.xroad.catalog.persistence.v2entity.MemberV2;
import org.niis.xroad.catalog.persistence.v2entity.SubsystemV2;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class MemberConverter {

    public MemberDto toDto(MemberListRow row) {
        return MemberDto.builder()
                .memberClass(row.getMemberClass())
                .memberCode(row.getMemberCode())
                .name(row.getName())
                .isProvider(row.isProvider())
                .subsystemCount(Math.toIntExact(row.getSubsystemCount()))
                .serviceCount(Math.toIntExact(row.getServiceCount()))
                .created(row.getCreated())
                .changed(row.getChanged())
                .fetched(row.getFetched())
                .removed(row.getRemoved())
                .build();
    }

    /**
     * Builds the {@link FullMemberDto} for the {@code ?full=true} browse endpoint. {@code isProvider}
     * comes straight from the denormalized {@link MemberV2#isProvider()} column maintained by the
     * collector recompute; {@code subsystemCount}/{@code serviceCount} are computed from the
     * {@code getActive*} helpers over the loaded entity graph. The caller supplies the
     * already-assembled {@code subsystems} list so this converter does not depend on the subsystem
     * converter or the service aggregator.
     */
    public FullMemberDto toFullDto(MemberV2 member, List<FullSubsystemDto> subsystems) {
        int subsystemCount = 0;
        int serviceCount = 0;
        for (SubsystemV2 sub : member.getActiveSubsystems()) {
            subsystemCount++;
            serviceCount += sub.getActiveServices().size();
        }
        StatusInfo info = member.getStatusInfo();
        return FullMemberDto.builder()
                .memberClass(member.getMemberClass())
                .memberCode(member.getMemberCode())
                .name(member.getName())
                .isProvider(member.isProvider())
                .subsystemCount(subsystemCount)
                .serviceCount(serviceCount)
                .created(info.getCreated())
                .changed(info.getChanged())
                .fetched(info.getFetched())
                .removed(info.getRemoved())
                .subsystems(subsystems)
                .build();
    }
}
