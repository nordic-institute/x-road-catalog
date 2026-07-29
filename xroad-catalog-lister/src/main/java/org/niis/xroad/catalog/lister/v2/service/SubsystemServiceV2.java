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
import org.niis.xroad.catalog.persistence.v2.repository.MemberRepository;
import org.niis.xroad.catalog.persistence.v2.repository.SubsystemRepository;
import org.niis.xroad.catalog.persistence.v2.repository.projection.SubsystemListRow;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * V2 subsystem service, backed by the {@link SubsystemRepository} read model.
 */
@Service
public class SubsystemServiceV2 {

    private final SubsystemRepository subsystemRepository;
    private final MemberRepository memberRepository;
    private final SharedParamsCache sharedParamsCache;

    public SubsystemServiceV2(SubsystemRepository subsystemRepository, MemberRepository memberRepository,
            SharedParamsCache sharedParamsCache) {
        this.subsystemRepository = subsystemRepository;
        this.memberRepository = memberRepository;
        this.sharedParamsCache = sharedParamsCache;
    }

    public Optional<SubsystemDto> getByNaturalKey(String memberClass, String memberCode, String subsystemCode) {
        SubsystemNameLookup lookup = sharedParamsCache.subsystemNames();
        return subsystemRepository.findActiveSummaryByNaturalKey(
                        sharedParamsCache.getCurrentInstance(), memberClass, memberCode, subsystemCode)
                .map(row -> SubsystemDto.from(row, lookup));
    }

    public Page<SubsystemDto> getForList(String memberClass, Pageable pageable) {
        Page<SubsystemListRow> rows = subsystemRepository.findActiveForList(
                sharedParamsCache.getCurrentInstance(), memberClass, pageable);
        SubsystemNameLookup lookup = sharedParamsCache.subsystemNames();
        return rows.map(row -> SubsystemDto.from(row, lookup));
    }

    /**
     * Subsystems under one member, in repository {@code subsystemCode} order. Empty
     * {@link Optional} means the member is absent; a present-but-empty list means it has no active
     * subsystems.
     */
    public Optional<List<SubsystemDto>> getForMember(String memberClass, String memberCode) {
        String instance = sharedParamsCache.getCurrentInstance();
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
