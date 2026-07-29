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
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import org.niis.xroad.catalog.lister.v2.configuration.JacksonV2Configuration;
import org.niis.xroad.catalog.lister.v2.converter.SubsystemNameLookup;
import org.niis.xroad.catalog.persistence.v2.repository.projection.SubsystemListRow;

import java.time.LocalDateTime;

@Getter
@Builder
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.ALWAYS)
public class SubsystemDto {
    private final String memberClass;
    private final String memberCode;
    private final String memberName;
    private final String subsystemCode;
    private final String subsystemName;
    private final int serviceCount;

    @JsonSerialize(using = JacksonV2Configuration.OffsetLocalDateTimeSerializer.class)
    private final LocalDateTime created;
    @JsonSerialize(using = JacksonV2Configuration.OffsetLocalDateTimeSerializer.class)
    private final LocalDateTime changed;
    @JsonSerialize(using = JacksonV2Configuration.OffsetLocalDateTimeSerializer.class)
    private final LocalDateTime fetched;

    public static SubsystemDto from(SubsystemListRow row, SubsystemNameLookup nameLookup) {
        String memberClass = row.getMemberClass();
        String memberCode = row.getMemberCode();
        String subsystemCode = row.getSubsystemCode();
        return SubsystemDto.builder()
                .memberClass(memberClass)
                .memberCode(memberCode)
                .memberName(row.getMemberName())
                .subsystemCode(subsystemCode)
                .subsystemName(nameLookup.resolve(memberClass, memberCode, subsystemCode))
                .serviceCount(Math.toIntExact(row.getServiceCount()))
                .created(row.getCreated())
                .changed(row.getChanged())
                .fetched(row.getFetched())
                .build();
    }
}
