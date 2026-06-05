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

import lombok.RequiredArgsConstructor;
import org.niis.xroad.catalog.lister.v2.dto.ServiceDto;
import org.niis.xroad.catalog.lister.v2.dto.ServiceVersionSummaryDto;
import org.niis.xroad.catalog.persistence.entity.Service;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class ServiceAggregator {

    private final ServiceVersionConverter versionConverter;

    public ServiceDto aggregate(Collection<Service> serviceRows, boolean includeRemoved) {
        List<Service> filtered = serviceRows.stream()
                .filter(s -> includeRemoved || !s.getStatusInfo().isRemoved())
                .sorted(versionComparator())
                .toList();
        if (filtered.isEmpty()) {
            return null;
        }
        Service first = filtered.get(0);
        List<ServiceVersionSummaryDto> versions = new ArrayList<>();
        Set<String> types = new LinkedHashSet<>();
        for (Service s : filtered) {
            ServiceVersionSummaryDto v = versionConverter.toSummary(s);
            versions.add(v);
            types.add(v.getServiceType());
        }
        return ServiceDto.builder()
                .memberClass(first.getSubsystem().getMember().getMemberClass())
                .memberCode(first.getSubsystem().getMember().getMemberCode())
                .memberName(first.getSubsystem().getMember().getName())
                .subsystemCode(first.getSubsystem().getSubsystemCode())
                .serviceCode(first.getServiceCode())
                .versionCount(versions.size())
                .versions(versions)
                .serviceTypes(new ArrayList<>(types))
                .build();
    }

    private Comparator<Service> versionComparator() {
        return Comparator.comparing(Service::getServiceVersion,
                Comparator.nullsLast(Comparator.naturalOrder()));
    }
}
