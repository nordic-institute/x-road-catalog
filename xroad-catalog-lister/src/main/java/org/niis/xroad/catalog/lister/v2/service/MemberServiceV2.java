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
import org.niis.xroad.catalog.persistence.v2.repository.MemberRepository;
import org.niis.xroad.catalog.persistence.v2.repository.projection.MemberListRow;
import org.niis.xroad.catalog.persistence.v2.entity.Member;
import org.niis.xroad.catalog.persistence.v2.entity.Service;
import org.niis.xroad.catalog.persistence.v2.entity.Subsystem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * V2 member service, backed by the {@link MemberRepository} read model.
 */
@org.springframework.stereotype.Service
public class MemberServiceV2 {

    private final MemberRepository memberRepository;
    private final SharedParamsCache sharedParamsCache;

    public MemberServiceV2(MemberRepository memberRepository, SharedParamsCache sharedParamsCache) {
        this.memberRepository = memberRepository;
        this.sharedParamsCache = sharedParamsCache;
    }

    public Optional<MemberDto> getByNaturalKey(String memberClass, String memberCode) {
        return memberRepository.findActiveSummaryByNaturalKey(
                        sharedParamsCache.getCurrentInstance(), memberClass, memberCode)
                .map(MemberDto::from);
    }

    public boolean existsActive(String memberClass, String memberCode) {
        return memberRepository.existsActiveByNaturalKey(
                sharedParamsCache.getCurrentInstance(), memberClass, memberCode);
    }

    public Page<MemberDto> getForList(String memberClass, Boolean isProvider, Pageable pageable) {
        Page<MemberListRow> rows = memberRepository.findActiveForList(
                sharedParamsCache.getCurrentInstance(), memberClass, isProvider, pageable);
        return rows.map(MemberDto::from);
    }

    /**
     * Full nested subtree for one member ({@code ?full=true}); empty {@link Optional} when absent.
     * Subsystems and services are sorted by code for deterministic output.
     */
    public Optional<FullMemberDto> getFullTree(String memberClass, String memberCode) {
        return memberRepository.findActiveWithTreeByNaturalKey(
                        sharedParamsCache.getCurrentInstance(), memberClass, memberCode)
                .map(member -> toFullDto(member, sharedParamsCache.subsystemNames()));
    }

    private FullMemberDto toFullDto(Member member, SubsystemNameLookup nameLookup) {
        List<FullSubsystemDto> subsystemDtos = new ArrayList<>();
        List<Subsystem> sorted = member.getSubsystems().stream()
                .sorted(Comparator.comparing(Subsystem::getSubsystemCode)).toList();
        for (Subsystem sub : sorted) {
            Map<String, List<Service>> byCode = new TreeMap<>();
            for (Service svc : sub.getServices()) {
                byCode.computeIfAbsent(svc.getServiceCode(), k -> new ArrayList<>()).add(svc);
            }
            List<ServiceDto> services = new ArrayList<>();
            for (List<Service> group : byCode.values()) {
                services.add(ServiceDto.fromEntities(member.getMemberClass(), member.getMemberCode(),
                        member.getName(), sub.getSubsystemCode(), group));
            }
            subsystemDtos.add(FullSubsystemDto.from(sub, nameLookup, services));
        }
        return FullMemberDto.from(member, subsystemDtos);
    }
}
