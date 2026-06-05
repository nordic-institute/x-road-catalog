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

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.niis.xroad.catalog.lister.v2.configuration.JacksonV2Configuration;

import java.time.LocalDateTime;

/**
 * V2 heartbeat response. Adds {@code lastRunErrors} (count of errors since the earliest
 * successful collection run) on top of the V1 shape, and carries a V2-specific
 * {@link LastCollectionDataV2Dto} which includes {@code restsLastFetched}.
 */
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class HeartbeatV2Dto {

    private Boolean appWorking;

    private Boolean dbWorking;

    private String appName;

    private String appVersion;

    @JsonSerialize(using = JacksonV2Configuration.OffsetLocalDateTimeSerializer.class)
    private LocalDateTime systemTime;

    private LastCollectionDataV2Dto lastCollectionData;

    private long lastRunErrors;
}
