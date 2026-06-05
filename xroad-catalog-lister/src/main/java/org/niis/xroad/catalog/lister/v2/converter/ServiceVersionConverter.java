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

import org.niis.xroad.catalog.lister.v2.dto.EndpointDto;
import org.niis.xroad.catalog.lister.v2.dto.ServiceVersionDto;
import org.niis.xroad.catalog.lister.v2.dto.ServiceVersionSummaryDto;
import org.niis.xroad.catalog.persistence.entity.Endpoint;
import org.niis.xroad.catalog.persistence.entity.Service;
import org.niis.xroad.catalog.persistence.entity.StatusInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class ServiceVersionConverter {

    @Autowired
    private ServiceClassifier classifier;

    public ServiceVersionDto toDto(Service service, boolean includeRemoved) {
        String serviceType = classifier.resolveType(service);
        boolean hasDescriptor = classifier.hasDescriptor(service);
        List<EndpointDto> endpoints = new ArrayList<>();
        for (Endpoint e : service.getAllEndpoints()) {
            boolean removed = e.getStatusInfo().isRemoved();
            if (removed && !includeRemoved) {
                continue;
            }
            endpoints.add(EndpointDto.builder()
                    .method(e.getMethod())
                    .path(e.getPath())
                    .removed(removed ? e.getStatusInfo().getRemoved() : null)
                    .build());
        }
        StatusInfo info = service.getStatusInfo();
        return ServiceVersionDto.builder()
                .serviceVersion(service.getServiceVersion())
                .serviceType(serviceType)
                .hasDescriptor(hasDescriptor)
                .endpoints(endpoints)
                .created(info.getCreated())
                .changed(info.getChanged())
                .fetched(info.getFetched())
                .removed(info.getRemoved())
                .build();
    }

    public ServiceVersionSummaryDto toSummary(Service service) {
        StatusInfo info = service.getStatusInfo();
        return ServiceVersionSummaryDto.builder()
                .serviceVersion(service.getServiceVersion())
                .serviceType(classifier.resolveType(service))
                .created(info.getCreated())
                .changed(info.getChanged())
                .fetched(info.getFetched())
                .removed(info.getRemoved())
                .build();
    }
}
