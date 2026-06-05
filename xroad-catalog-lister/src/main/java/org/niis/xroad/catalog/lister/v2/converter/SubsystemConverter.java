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

import org.niis.xroad.catalog.lister.v2.dto.FullSubsystemDto;
import org.niis.xroad.catalog.lister.v2.dto.ServiceDto;
import org.niis.xroad.catalog.lister.v2.dto.SubsystemDto;
import org.niis.xroad.catalog.persistence.entity.StatusInfo;
import org.niis.xroad.catalog.persistence.entity.Subsystem;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class SubsystemConverter {

    public SubsystemDto toDto(Subsystem subsystem, SubsystemNameLookup nameLookup) {
        return toDto(subsystem, nameLookup, false);
    }

    public SubsystemDto toDto(Subsystem subsystem, SubsystemNameLookup nameLookup, boolean includeRemoved) {
        int serviceCount = includeRemoved
                ? subsystem.getAllServices().size()
                : subsystem.getActiveServices().size();
        String memberClass = subsystem.getMember().getMemberClass();
        String memberCode = subsystem.getMember().getMemberCode();
        String subsystemCode = subsystem.getSubsystemCode();
        StatusInfo info = subsystem.getStatusInfo();
        return SubsystemDto.builder()
                .memberClass(memberClass)
                .memberCode(memberCode)
                .memberName(subsystem.getMember().getName())
                .subsystemCode(subsystemCode)
                .subsystemName(nameLookup.resolve(memberClass, memberCode, subsystemCode))
                .serviceCount(serviceCount)
                .created(info.getCreated())
                .changed(info.getChanged())
                .fetched(info.getFetched())
                .removed(info.getRemoved())
                .build();
    }

    /**
     * Builds the {@link FullSubsystemDto} used by the {@code ?full=true} browse endpoint. Flat
     * subsystem fields mirror {@link #toDto(Subsystem, SubsystemNameLookup, boolean)}; the caller
     * provides the already-aggregated {@code services} list (produced via {@code ServiceAggregator}
     * grouped by {@code serviceCode}).
     */
    public FullSubsystemDto toFullDto(Subsystem subsystem, SubsystemNameLookup nameLookup,
                                      List<ServiceDto> services, boolean includeRemoved) {
        int serviceCount = includeRemoved
                ? subsystem.getAllServices().size()
                : subsystem.getActiveServices().size();
        String memberClass = subsystem.getMember().getMemberClass();
        String memberCode = subsystem.getMember().getMemberCode();
        String subsystemCode = subsystem.getSubsystemCode();
        StatusInfo info = subsystem.getStatusInfo();
        return FullSubsystemDto.builder()
                .memberClass(memberClass)
                .memberCode(memberCode)
                .memberName(subsystem.getMember().getName())
                .subsystemCode(subsystemCode)
                .subsystemName(nameLookup.resolve(memberClass, memberCode, subsystemCode))
                .serviceCount(serviceCount)
                .created(info.getCreated())
                .changed(info.getChanged())
                .fetched(info.getFetched())
                .removed(info.getRemoved())
                .services(services)
                .build();
    }
}
