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
package org.niis.xroad.catalog.lister.v2.service;

import org.niis.xroad.catalog.lister.v2.converter.SubsystemNameLookup;
import org.niis.xroad.catalog.lister.v2.dto.SubsystemDto;
import org.niis.xroad.catalog.persistence.repository.MemberRepositoryV2;
import org.niis.xroad.catalog.persistence.repository.SubsystemRepositoryV2;
import org.niis.xroad.catalog.persistence.repository.projection.SubsystemListRow;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * V2 subsystem service, backed by the {@link SubsystemRepositoryV2} read model.
 */
@Service
public class SubsystemServiceV2 {

    private final SubsystemRepositoryV2 subsystemRepository;
    private final MemberRepositoryV2 memberRepository;
    private final SharedParamsCache sharedParamsCache;
    private final InstanceContext instanceContext;

    public SubsystemServiceV2(SubsystemRepositoryV2 subsystemRepository, MemberRepositoryV2 memberRepository,
            SharedParamsCache sharedParamsCache, InstanceContext instanceContext) {
        this.subsystemRepository = subsystemRepository;
        this.memberRepository = memberRepository;
        this.sharedParamsCache = sharedParamsCache;
        this.instanceContext = instanceContext;
    }

    /**
     * @return the subsystem's DTO, or an empty {@link Optional} if absent (controller maps to 404)
     */
    public Optional<SubsystemDto> getByNaturalKey(String memberClass, String memberCode, String subsystemCode) {
        SubsystemNameLookup lookup = sharedParamsCache.subsystemNames();
        return subsystemRepository.findActiveSummaryByNaturalKey(
                        instanceContext.getCurrentInstance(), memberClass, memberCode, subsystemCode)
                .map(row -> SubsystemDto.from(row, lookup));
    }

    public Page<SubsystemDto> getForList(String memberClass, Pageable pageable) {
        Page<SubsystemListRow> rows = subsystemRepository.findActiveForList(
                instanceContext.getCurrentInstance(), memberClass, pageable);
        SubsystemNameLookup lookup = sharedParamsCache.subsystemNames();
        return rows.map(row -> SubsystemDto.from(row, lookup));
    }

    /**
     * Returns the subsystems under a single member, sorted by {@code subsystemCode} ascending (the
     * repository query already orders the rows, so no re-sort is needed here). An empty
     * {@link Optional} means the member itself is absent (404); a present-but-empty list means the
     * member exists but has no active subsystems (200 {@code []}).
     */
    public Optional<List<SubsystemDto>> getForMember(String memberClass, String memberCode) {
        String instance = instanceContext.getCurrentInstance();
        if (!memberRepository.existsActiveByNaturalKey(instance, memberClass, memberCode)) {
            return Optional.empty();
        }
        List<SubsystemListRow> rows = subsystemRepository.findActiveForMember(instance, memberClass, memberCode);
        SubsystemNameLookup lookup = sharedParamsCache.subsystemNames();
        List<SubsystemDto> result = new ArrayList<>(rows.size());
        for (SubsystemListRow row : rows) {
            result.add(SubsystemDto.from(row, lookup));
        }
        return Optional.of(result);
    }
}
