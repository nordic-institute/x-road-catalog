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
import org.niis.xroad.catalog.lister.v2.dto.FullMemberDto;
import org.niis.xroad.catalog.lister.v2.dto.FullSubsystemDto;
import org.niis.xroad.catalog.lister.v2.dto.MemberDto;
import org.niis.xroad.catalog.lister.v2.dto.ServiceDto;
import org.niis.xroad.catalog.persistence.repository.MemberRepositoryV2;
import org.niis.xroad.catalog.persistence.repository.projection.MemberListRow;
import org.niis.xroad.catalog.persistence.v2entity.MemberV2;
import org.niis.xroad.catalog.persistence.v2entity.ServiceV2;
import org.niis.xroad.catalog.persistence.v2entity.SubsystemV2;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * V2 member service, backed by the {@link MemberRepositoryV2} read model.
 */
@Service
public class MemberServiceV2 {

    private final MemberRepositoryV2 memberRepository;
    private final SharedParamsCache sharedParamsCache;
    private final InstanceContext instanceContext;

    public MemberServiceV2(MemberRepositoryV2 memberRepository, SharedParamsCache sharedParamsCache,
            InstanceContext instanceContext) {
        this.memberRepository = memberRepository;
        this.sharedParamsCache = sharedParamsCache;
        this.instanceContext = instanceContext;
    }

    /**
     * @return the member's flat DTO, or an empty {@link Optional} if absent or removed (controller maps to 404)
     */
    public Optional<MemberDto> getByNaturalKey(String memberClass, String memberCode) {
        return memberRepository.findActiveSummaryByNaturalKey(
                        instanceContext.getCurrentInstance(), memberClass, memberCode)
                .map(MemberDto::from);
    }

    public boolean existsActive(String memberClass, String memberCode) {
        return memberRepository.existsActiveByNaturalKey(
                instanceContext.getCurrentInstance(), memberClass, memberCode);
    }

    public Page<MemberDto> getForList(String memberClass, Boolean isProvider, Pageable pageable) {
        Page<MemberListRow> rows = memberRepository.findActiveForList(
                instanceContext.getCurrentInstance(), memberClass, isProvider, pageable);
        return rows.map(MemberDto::from);
    }

    /**
     * Builds the full nested subtree for a single member ({@code ?full=true}). Returns an empty
     * {@link Optional} when the member is absent so the controller can map to 404. Subsystems are
     * sorted by {@code subsystemCode} ascending and services within each subsystem by
     * {@code serviceCode} ascending for deterministic output across restarts.
     */
    public Optional<FullMemberDto> getFullTree(String memberClass, String memberCode) {
        return memberRepository.findActiveWithTreeByNaturalKey(
                        instanceContext.getCurrentInstance(), memberClass, memberCode)
                .map(member -> toFullDto(member, sharedParamsCache.subsystemNames()));
    }

    private FullMemberDto toFullDto(MemberV2 member, SubsystemNameLookup nameLookup) {
        List<FullSubsystemDto> subsystemDtos = new ArrayList<>();
        List<SubsystemV2> sorted = member.getActiveSubsystems().stream()
                .sorted(Comparator.comparing(SubsystemV2::getSubsystemCode)).toList();
        for (SubsystemV2 sub : sorted) {
            Map<String, List<ServiceV2>> byCode = new TreeMap<>();
            for (ServiceV2 svc : sub.getActiveServices()) {
                byCode.computeIfAbsent(svc.getServiceCode(), k -> new ArrayList<>()).add(svc);
            }
            List<ServiceDto> services = new ArrayList<>();
            for (List<ServiceV2> group : byCode.values()) {
                services.add(ServiceDto.fromEntities(member.getMemberClass(), member.getMemberCode(),
                        member.getName(), sub.getSubsystemCode(), group));
            }
            subsystemDtos.add(FullSubsystemDto.from(sub, nameLookup, services));
        }
        return FullMemberDto.from(member, subsystemDtos);
    }
}
