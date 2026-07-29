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
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.niis.xroad.catalog.lister.v2.configuration.JacksonV2Configuration;

import java.time.LocalDateTime;

/**
 * V2 heartbeat response. {@code lastRunErrors} is the count of errors since the earliest
 * successful collection run.
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

    @Schema(description = "Number of active services that currently carry more than one active descriptor "
            + "(WSDL/OpenAPI) — a data-integrity anomaly. When greater than 0, inspect the collector logs: "
            + "the collector's recompute task logs a warning identifying each affected service.")
    private long descriptorAnomalies;

    @Schema(description = "True when the downloaded X-Road global configuration has passed its expiration date. "
            + "Data derived from shared-params.xml (subsystem names, member classes, security servers) may be "
            + "stale but is still served. When true, check the configuration client logs and verify the Central "
            + "Server is reachable. False also when expiry cannot be determined (see globalConfExpiresAt).")
    private boolean globalConfExpired;

    @Schema(description = "Expiration timestamp of the downloaded X-Road global configuration, read from the "
            + "configuration client's metadata sidecar file. Null when expiry is unknown — the metadata file is "
            + "missing or unreadable, e.g. before the first configuration download — in which case "
            + "globalConfExpired is false.")
    @JsonSerialize(using = JacksonV2Configuration.OffsetLocalDateTimeSerializer.class)
    private LocalDateTime globalConfExpiresAt;

    private CurrentRunDto currentRun;
}
