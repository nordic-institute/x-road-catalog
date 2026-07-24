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
import org.niis.xroad.catalog.persistence.entity.StatusInfo;
import org.niis.xroad.catalog.persistence.repository.projection.ServiceVersionRow;
import org.niis.xroad.catalog.persistence.v2entity.ServiceV2;

import java.time.LocalDateTime;

/**
 * Descriptor-free summary of one service version. {@code serviceType} is {@code SOAP},
 * {@code OPENAPI}, {@code REST} or {@code UNKNOWN} (collected but not yet classified).
 */
@Getter
@Builder
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.ALWAYS)
public class ServiceVersionSummaryDto {
    private final String serviceVersion;
    private final String serviceType;
    @JsonSerialize(using = JacksonV2Configuration.OffsetLocalDateTimeSerializer.class)
    private final LocalDateTime created;
    @JsonSerialize(using = JacksonV2Configuration.OffsetLocalDateTimeSerializer.class)
    private final LocalDateTime changed;
    @JsonSerialize(using = JacksonV2Configuration.OffsetLocalDateTimeSerializer.class)
    private final LocalDateTime fetched;
    @JsonSerialize(using = JacksonV2Configuration.OffsetLocalDateTimeSerializer.class)
    private final LocalDateTime removed;

    public static ServiceVersionSummaryDto from(ServiceV2 service) {
        StatusInfo info = service.getStatusInfo();
        return ServiceVersionSummaryDto.builder()
                .serviceVersion(service.getServiceVersion())
                .serviceType(service.getServiceType())
                .created(info.getCreated())
                .changed(info.getChanged())
                .fetched(info.getFetched())
                .removed(info.getRemoved())
                .build();
    }

    public static ServiceVersionSummaryDto from(ServiceVersionRow row) {
        return ServiceVersionSummaryDto.builder()
                .serviceVersion(row.getServiceVersion())
                .serviceType(row.getServiceType())
                .created(row.getCreated())
                .changed(row.getChanged())
                .fetched(row.getFetched())
                .removed(row.getRemoved())
                .build();
    }
}
