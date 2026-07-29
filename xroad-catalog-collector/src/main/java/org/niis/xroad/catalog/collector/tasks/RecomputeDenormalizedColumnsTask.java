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
import org.niis.xroad.catalog.persistence.repository.projection.DescriptorAnomalyRow;
import org.springframework.stereotype.Component;

/**
 * Maintains the V2 denormalized columns ({@code member.is_provider}, {@code service.service_type})
 * after each collection cycle.
 *
 * <p>{@code is_provider}: the member is not removed and has at least one active service under an
 * active subsystem. {@code service_type}: derived from the service's active descriptors with
 * priority SOAP &gt; OPENAPI &gt; REST, or UNKNOWN when none exist. Multiple active descriptors on
 * one service version is a data-integrity anomaly, logged at WARN and resolved by the same
 * priority order.
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
            for (DescriptorAnomalyRow row : denormalizationRepository.findServicesWithMultipleActiveDescriptors()) {
                log.warn("Data-integrity anomaly: service id={} ({}:{}:{}:{} version {}) has multiple active descriptors"
                                + " (wsdl={}, openApi={})",
                        row.getServiceId(), row.getMemberClass(), row.getMemberCode(), row.getSubsystemCode(),
                        row.getServiceCode(), row.getServiceVersion(), row.getWsdlCount(), row.getOpenapiCount());
            }
        } catch (Exception e) {
            log.error("Failed to recompute denormalized columns", e);
        }
    }
}
