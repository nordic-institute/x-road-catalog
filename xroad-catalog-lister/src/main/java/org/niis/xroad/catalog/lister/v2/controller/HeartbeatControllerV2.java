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
import org.niis.xroad.catalog.lister.v2.dto.HeartbeatDto;
import org.niis.xroad.catalog.lister.v2.service.HeartbeatService;
import org.springframework.context.annotation.PropertySource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * V2 heartbeat endpoint. The HTTP status reflects health: a fully healthy instance
 * ({@code appWorking && dbWorking}) responds 200, anything else 503 with the same body shape so
 * clients can inspect {@code appWorking}/{@code dbWorking} to identify the failing dependency
 * (V1 always returns 200). {@code @PropertySource} loads {@code version.properties} so
 * {@link HeartbeatService}'s {@code @Value}-injected app-name/app-version resolve without
 * depending on V1's controller.
 */
@RestController
@RequestMapping("/api/v2")
@PropertySource("classpath:version.properties")
public class HeartbeatControllerV2 {

    private final HeartbeatService heartbeatService;

    public HeartbeatControllerV2(HeartbeatService heartbeatService) {
        this.heartbeatService = heartbeatService;
    }

    @GetMapping(path = "/heartbeat", produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponse(responseCode = "200", description = "App and database both healthy.")
    @ApiResponse(responseCode = "503",
            description = "App or database unhealthy. Body shape is identical to the 200 response; "
                    + "inspect appWorking and dbWorking to identify the failing dependency.")
    public ResponseEntity<HeartbeatDto> heartbeat() {
        HeartbeatDto body = heartbeatService.heartbeat();
        HttpStatus status = isHealthy(body) ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE;
        return ResponseEntity.status(status).body(body);
    }

    private static boolean isHealthy(HeartbeatDto body) {
        return Boolean.TRUE.equals(body.getAppWorking()) && Boolean.TRUE.equals(body.getDbWorking());
    }
}
