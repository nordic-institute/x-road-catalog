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

import io.swagger.v3.oas.annotations.Parameter;
import org.niis.xroad.catalog.lister.v2.dto.DescriptorPayload;
import org.niis.xroad.catalog.lister.v2.service.ServiceServiceV2;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Serves raw descriptor bytes (WSDL XML or OpenAPI JSON/YAML) for X-Road services. Lives apart
 * from {@link BrowseController} because the response body is a non-JSON byte stream — wiring this
 * route into the JSON controller would force every JSON endpoint to opt out of the default content
 * negotiation. The 409-on-multi-version shortcut at the service-level path returns a JSON
 * {@code ErrorResponse} via {@link V2ExceptionHandler}; that's the only JSON body emitted here.
 */
@RestController
@RequestMapping("/api/v2/browse")
public class BrowseDescriptorController {

    private final ServiceServiceV2 serviceService;

    public BrowseDescriptorController(ServiceServiceV2 serviceService) {
        this.serviceService = serviceService;
    }

    @GetMapping("/member-classes/{memberClass}/members/{memberCode}"
            + "/subsystems/{subsystemCode}/services/{serviceCode}/versions/{serviceVersion}/descriptor")
    public ResponseEntity<byte[]> getVersionDescriptor(
            @PathVariable("memberClass") String memberClass,
            @PathVariable("memberCode") String memberCode,
            @PathVariable("subsystemCode") String subsystemCode,
            @PathVariable("serviceCode") String serviceCode,
            @Parameter(
                    description = "Service version label. Use the literal string \"null\" (case-sensitive) to "
                            + "address a service version that has no version label. A real version literally "
                            + "named \"null\" is therefore unaddressable.",
                    example = "v1")
            @PathVariable("serviceVersion") String serviceVersion) {
        // serviceVersion is the raw URL segment ("null" sentinel resolved inside the service layer).
        DescriptorPayload payload = serviceService.getVersionDescriptor(
                        memberClass, memberCode, subsystemCode, serviceCode, serviceVersion)
                .orElseThrow(() -> V2ResourceNotFoundException.of(
                        "Descriptor for service version", memberClass, memberCode, subsystemCode, serviceCode, serviceVersion));
        return ResponseEntity.ok()
                .contentType(payload.contentType())
                .body(payload.content());
    }

    @GetMapping("/member-classes/{memberClass}/members/{memberCode}"
            + "/subsystems/{subsystemCode}/services/{serviceCode}/descriptor")
    public ResponseEntity<byte[]> getServiceDescriptor(
            @PathVariable("memberClass") String memberClass,
            @PathVariable("memberCode") String memberCode,
            @PathVariable("subsystemCode") String subsystemCode,
            @PathVariable("serviceCode") String serviceCode) {
        // The service layer throws MultipleVersionsException for 2+ visible versions; that maps to
        // 409 in V2ExceptionHandler. An empty Optional means "0 versions" or "1 version, no descriptor"
        // — both surface as 404 here without distinguishing them (spec §1.2 doesn't require it).
        DescriptorPayload payload = serviceService.getServiceLevelDescriptor(
                        memberClass, memberCode, subsystemCode, serviceCode)
                .orElseThrow(() -> V2ResourceNotFoundException.of(
                        "Descriptor for service", memberClass, memberCode, subsystemCode, serviceCode));
        return ResponseEntity.ok()
                .contentType(payload.contentType())
                .body(payload.content());
    }
}
