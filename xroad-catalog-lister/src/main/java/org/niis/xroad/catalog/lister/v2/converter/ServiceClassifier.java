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

import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.catalog.persistence.entity.Service;
import org.springframework.stereotype.Component;

/**
 * Canonical classifier for V2 service type + descriptor presence. Every V2 site that asks
 * "what type is this service" goes through this helper so the WARN-on-invariant-violation
 * and the SOAP &gt; OPENAPI &gt; REST priority stay consistent across call sites.
 */
@Component
@Slf4j
public class ServiceClassifier {

    /**
     * Returns {@code "SOAP"} | {@code "OPENAPI"} | {@code "REST"}. REST is the default for
     * descriptor-less services (matches V1 semantics and spec §6.4). Active descriptors only
     * — services whose only descriptor rows are removed classify as REST.
     */
    public String resolveType(Service service) {
        boolean wsdl = service.hasActiveWsdl();
        boolean openapi = service.hasActiveOpenApi();
        if (wsdl && openapi) {
            // X-Road protocol invariant: at most one active descriptor per service version.
            // Seeing both is a data-integrity anomaly upstream in the collector. Log and fall through.
            log.warn("Service {}/{}/{}/{}/{} version {} has both active WSDL and active OpenAPI "
                            + "- X-Road invariant violated; classifying as SOAP (priority fallback)",
                    service.getSubsystem().getMember().getXRoadInstance(),
                    service.getSubsystem().getMember().getMemberClass(),
                    service.getSubsystem().getMember().getMemberCode(),
                    service.getSubsystem().getSubsystemCode(),
                    service.getServiceCode(),
                    service.getServiceVersion());
        }
        if (wsdl) {
            return "SOAP";
        }
        if (openapi) {
            return "OPENAPI";
        }
        return "REST";
    }

    /**
     * True iff the service has an active WSDL or active OpenAPI. Rest entities do not count —
     * V2's {@code /descriptor} endpoint serves only WSDL/OpenAPI raw documents.
     */
    public boolean hasDescriptor(Service service) {
        return service.hasActiveWsdl() || service.hasActiveOpenApi();
    }
}
