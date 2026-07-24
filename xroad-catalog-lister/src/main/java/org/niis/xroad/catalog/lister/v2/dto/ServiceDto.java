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

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import org.niis.xroad.catalog.persistence.repository.projection.ServiceVersionRow;
import org.niis.xroad.catalog.persistence.v2entity.ServiceV2;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Aggregates one bare {@code serviceCode} within one subsystem into a single {@link ServiceDto}.
 * Versions are sorted by {@code serviceVersion} with nulls last; {@code serviceTypes} is the
 * distinct set of types in that sorted version order rather than a single scalar, because a bare
 * service code can legitimately be multi-typed: the asymmetric REST-then-versioned-WSDL path lets a
 * descriptor-less first version and a WSDL-backed later version coexist under the same aggregate.
 * Collapsing that to one type would silently drop information the API contract needs.
 *
 * <p>Both entry points require a non-empty input: every real caller only ever builds an aggregate
 * from a {@code serviceCode} that is already known to have at least one active version row (the
 * aggregate query and the entity-graph subtree traversal both only produce groups that exist), so an
 * empty collection here means a caller-side bug, not a legitimate "no versions" case. Rather than
 * silently returning {@code null} into a page of DTOs, both methods fail loudly.
 */
@Getter
@Builder
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.ALWAYS)
public class ServiceDto {
    private final String memberClass;
    private final String memberCode;
    private final String memberName;
    private final String subsystemCode;
    private final String serviceCode;
    private final List<String> serviceTypes;
    private final int versionCount;
    private final List<ServiceVersionSummaryDto> versions;

    /**
     * @throws IllegalArgumentException if {@code versionRows} is null or empty
     */
    public static ServiceDto from(List<ServiceVersionRow> versionRows) {
        if (versionRows == null || versionRows.isEmpty()) {
            throw new IllegalArgumentException("versionRows must not be null or empty");
        }
        List<ServiceVersionRow> sorted = versionRows.stream()
                .sorted(Comparator.comparing(ServiceVersionRow::getServiceVersion, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
        ServiceVersionRow first = sorted.get(0);
        List<ServiceVersionSummaryDto> versions = new ArrayList<>();
        Set<String> types = new LinkedHashSet<>();
        for (ServiceVersionRow row : sorted) {
            ServiceVersionSummaryDto v = ServiceVersionSummaryDto.from(row);
            versions.add(v);
            types.add(v.getServiceType());
        }
        return ServiceDto.builder()
                .memberClass(first.getMemberClass())
                .memberCode(first.getMemberCode())
                .memberName(first.getMemberName())
                .subsystemCode(first.getSubsystemCode())
                .serviceCode(first.getServiceCode())
                .versionCount(versions.size())
                .versions(versions)
                .serviceTypes(new ArrayList<>(types))
                .build();
    }

    /**
     * @throws IllegalArgumentException if {@code versions} is null or empty
     */
    public static ServiceDto fromEntities(String memberClass, String memberCode, String memberName,
                                   String subsystemCode, Collection<ServiceV2> versions) {
        if (versions == null || versions.isEmpty()) {
            throw new IllegalArgumentException("versions must not be null or empty");
        }
        List<ServiceV2> sorted = versions.stream()
                .sorted(Comparator.comparing(ServiceV2::getServiceVersion, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
        ServiceV2 first = sorted.get(0);
        List<ServiceVersionSummaryDto> summaries = new ArrayList<>();
        Set<String> types = new LinkedHashSet<>();
        for (ServiceV2 service : sorted) {
            ServiceVersionSummaryDto v = ServiceVersionSummaryDto.from(service);
            summaries.add(v);
            types.add(v.getServiceType());
        }
        return ServiceDto.builder()
                .memberClass(memberClass)
                .memberCode(memberCode)
                .memberName(memberName)
                .subsystemCode(subsystemCode)
                .serviceCode(first.getServiceCode())
                .versionCount(summaries.size())
                .versions(summaries)
                .serviceTypes(new ArrayList<>(types))
                .build();
    }
}
