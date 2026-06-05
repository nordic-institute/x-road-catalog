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
package org.niis.xroad.catalog.lister.v2.controller;

import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.niis.xroad.catalog.lister.v2.dto.HeartbeatV2Dto;
import org.niis.xroad.catalog.lister.v2.service.HeartbeatServiceV2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.PropertySource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * V2 heartbeat endpoint (spec §5). Reports app health, database reachability, last-collection
 * timestamps for each entity type (members, subsystems, services, WSDLs, OpenAPIs, RESTs), and
 * the count of error-log rows recorded since the most recent collection run. The HTTP status
 * reflects health (RFC 7231 explicitly permits non-2xx responses with full bodies): a fully
 * healthy instance ({@code appWorking == TRUE && dbWorking == TRUE}) responds {@code 200 OK};
 * any other combination — DB unreachable, app self-check failure, or both — responds
 * {@code 503 Service Unavailable} with the same body shape so clients can inspect
 * {@code appWorking} and {@code dbWorking} to identify the failing dependency. This is a
 * deliberate departure from V1, which always returned 200 and forced clients to parse the body
 * to detect outages. The {@code @PropertySource} declaration loads {@code version.properties}
 * into the V2-owned scope so {@link HeartbeatServiceV2}'s {@code @Value}-injected
 * {@code app-name}/{@code app-version} fields are resolvable without depending on V1's
 * controller.
 */
@RestController
@RequestMapping("/api/v2")
@PropertySource("classpath:version.properties")
public class HeartbeatV2Controller {

    @Autowired
    private HeartbeatServiceV2 heartbeatService;

    @GetMapping(path = "/heartbeat", produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "App and database both healthy."),
            @ApiResponse(responseCode = "503",
                    description = "App or database unhealthy. Body shape is identical to the 200 response; "
                            + "inspect appWorking and dbWorking to identify the failing dependency.")
    })
    public ResponseEntity<HeartbeatV2Dto> heartbeat() {
        HeartbeatV2Dto body = heartbeatService.heartbeat();
        HttpStatus status = isHealthy(body) ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE;
        return ResponseEntity.status(status).body(body);
    }

    private static boolean isHealthy(HeartbeatV2Dto body) {
        return Boolean.TRUE.equals(body.getAppWorking()) && Boolean.TRUE.equals(body.getDbWorking());
    }
}
