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
package org.niis.xroad.catalog.lister.v2.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import org.niis.xroad.catalog.lister.v2.configuration.JacksonV2Configuration;
import org.niis.xroad.catalog.persistence.v2.entity.Member;
import org.niis.xroad.catalog.persistence.v2.entity.StatusInfo;
import org.niis.xroad.catalog.persistence.v2.entity.Subsystem;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Member shape used by the {@code ?full=true} member browse endpoint. Mirrors the flat fields of
 * {@link MemberDto} and adds the nested list of {@link FullSubsystemDto} under this member.
 */
@Getter
@Builder
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.ALWAYS)
public class FullMemberDto {
    private final String memberClass;
    private final String memberCode;
    private final String name;
    @JsonProperty("provider")
    private final boolean isProvider;
    private final int subsystemCount;
    private final int serviceCount;

    @JsonSerialize(using = JacksonV2Configuration.OffsetLocalDateTimeSerializer.class)
    private final LocalDateTime created;
    @JsonSerialize(using = JacksonV2Configuration.OffsetLocalDateTimeSerializer.class)
    private final LocalDateTime changed;
    @JsonSerialize(using = JacksonV2Configuration.OffsetLocalDateTimeSerializer.class)
    private final LocalDateTime fetched;

    private final List<FullSubsystemDto> subsystems;

    /**
     * Counts are computed over the loaded entity graph (view-backed, so every row is active);
     * {@code isProvider} comes from the denormalized column maintained by the collector recompute.
     */
    public static FullMemberDto from(Member member, List<FullSubsystemDto> subsystems) {
        int subsystemCount = 0;
        int serviceCount = 0;
        for (Subsystem sub : member.getSubsystems()) {
            subsystemCount++;
            serviceCount += sub.getServices().size();
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
                .subsystems(subsystems)
                .build();
    }
}
