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
package org.niis.xroad.catalog.collector.tasks;

import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.catalog.persistence.repository.DenormalizationRepository;
import org.springframework.stereotype.Component;

/**
 * Maintains the V2 denormalized columns ({@code member.is_provider}, {@code service.service_type})
 * after each collection cycle.
 *
 * <p>{@code is_provider} is {@code true} when the member is not removed AND has at least one active
 * service under an active subsystem.
 *
 * <p>{@code service_type} is derived from the service's active descriptors, with priority
 * SOAP &gt; OPENAPI &gt; REST: a service with an active WSDL is SOAP, otherwise a service with an
 * active OpenAPI descriptor is OPENAPI, otherwise a service with an active rest row is REST,
 * otherwise (no active descriptor or rest row) it is UNKNOWN. A service having
 * both an active WSDL and an active OpenAPI descriptor at once is a data-integrity anomaly (a
 * service version should have at most one active descriptor) — it is logged at WARN and resolved
 * by the same SOAP &gt; OPENAPI &gt; REST priority order.
 *
 * <p>{@link #run()} never throws: it is invoked from a fixed-delay scheduled task, and an
 * uncaught exception there would permanently kill the schedule.
 */
@Slf4j
@Component
public class RecomputeDenormalizedColumnsTask {

    private final DenormalizationRepository denormalizationRepository;

    public RecomputeDenormalizedColumnsTask(DenormalizationRepository denormalizationRepository) {
        this.denormalizationRepository = denormalizationRepository;
    }

    @SuppressWarnings("checkstyle:MagicNumber")
    public void run() {
        try {
            int members = denormalizationRepository.recomputeMemberIsProvider();
            int services = denormalizationRepository.recomputeServiceType();
            log.info("Recomputed denormalized columns: {} member rows, {} service rows updated", members, services);
            for (Object[] row : denormalizationRepository.findServicesWithMultipleActiveDescriptors()) {
                log.warn("Data-integrity anomaly: service id={} ({}:{}:{}:{} version {}) has multiple active descriptors"
                                + " (wsdl={}, openApi={})",
                        row[0], row[1], row[2], row[3], row[4], row[5], row[6], row[7]);
            }
        } catch (Exception e) {
            log.error("Failed to recompute denormalized columns", e);
        }
    }
}
