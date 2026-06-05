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

import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.catalog.lister.v2.converter.MemberConverter;
import org.niis.xroad.catalog.lister.v2.converter.ServiceAggregator;
import org.niis.xroad.catalog.lister.v2.converter.SubsystemConverter;
import org.niis.xroad.catalog.lister.v2.converter.SubsystemNameLookup;
import org.niis.xroad.catalog.lister.v2.dto.FullMemberDto;
import org.niis.xroad.catalog.lister.v2.dto.FullSubsystemDto;
import org.niis.xroad.catalog.lister.v2.dto.MemberDto;
import org.niis.xroad.catalog.lister.v2.dto.ServiceDto;
import org.niis.xroad.catalog.lister.v2.dto.SubsystemNameInfo;
import org.niis.xroad.catalog.lister.v2.parser.SharedParamsParserV2;
import org.niis.xroad.catalog.persistence.entity.Member;
import org.niis.xroad.catalog.persistence.entity.Subsystem;
import org.niis.xroad.catalog.persistence.repository.MemberRepositoryV2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
public class MemberServiceV2 {

    @Autowired
    private MemberRepositoryV2 memberRepository;

    @Autowired
    private MemberConverter converter;

    @Autowired
    private SubsystemConverter subsystemConverter;

    @Autowired
    private ServiceAggregator serviceAggregator;

    @Autowired
    private SharedParamsParserV2 sharedParamsParser;

    @Autowired
    private InstanceContext instanceContext;

    @Value("${xroad-catalog.shared-params-file}")
    private String sharedParamsFile;

    public MemberDto getByNaturalKey(String memberClass, String memberCode, boolean includeRemoved) {
        String xRoadInstance = instanceContext.getCurrentInstance();
        Member member;
        if (includeRemoved) {
            member = memberRepository.findAnyByNaturalKey(xRoadInstance, memberClass, memberCode);
        } else {
            member = memberRepository.findActiveByNaturalKey(xRoadInstance, memberClass, memberCode);
        }
        if (member == null) {
            return null;
        }
        return includeRemoved ? converter.toDtoIncludingRemoved(member) : converter.toDto(member);
    }

    public Page<MemberDto> getForList(String memberClass, Boolean isProvider,
                                      boolean includeRemoved, Pageable pageable) {
        boolean activeOnly = !includeRemoved;
        Page<Member> members = memberRepository.findForList(memberClass, isProvider, activeOnly, pageable);
        return includeRemoved ? members.map(converter::toDtoIncludingRemoved) : members.map(converter::toDto);
    }

    /**
     * Builds the full nested subtree for a single member ({@code ?full=true}). Returns {@code null}
     * when the member is absent (or removed and {@code includeRemoved} is false) so the controller
     * can map to 404. Subsystems are sorted by {@code subsystemCode} ascending and services within
     * each subsystem by {@code serviceCode} ascending for deterministic output across restarts
     * (the underlying entity Sets iterate in insertion order).
     */
    public FullMemberDto getFullTree(String memberClass, String memberCode, boolean includeRemoved) {
        String xRoadInstance = instanceContext.getCurrentInstance();
        Member member = includeRemoved
                ? memberRepository.findAnyByNaturalKeyWithFullTree(xRoadInstance, memberClass, memberCode)
                : memberRepository.findActiveByNaturalKeyWithFullTree(xRoadInstance, memberClass, memberCode);
        if (member == null) {
            return null;
        }
        SubsystemNameLookup nameLookup = subsystemNameLookup();
        List<Subsystem> sortedSubsystems = sortedSubsystems(member, includeRemoved);
        List<FullSubsystemDto> subsystemDtos = new ArrayList<>();
        for (Subsystem sub : sortedSubsystems) {
            List<ServiceDto> services = aggregateServices(sub, includeRemoved);
            subsystemDtos.add(subsystemConverter.toFullDto(sub, nameLookup, services, includeRemoved));
        }
        return converter.toFullDto(member, subsystemDtos, includeRemoved);
    }

    private List<Subsystem> sortedSubsystems(Member member, boolean includeRemoved) {
        Set<Subsystem> source = includeRemoved ? member.getAllSubsystems() : member.getActiveSubsystems();
        List<Subsystem> list = new ArrayList<>(source);
        list.sort(Comparator.comparing(Subsystem::getSubsystemCode));
        return list;
    }

    private List<ServiceDto> aggregateServices(Subsystem subsystem, boolean includeRemoved) {
        Set<org.niis.xroad.catalog.persistence.entity.Service> source = includeRemoved
                ? subsystem.getAllServices() : subsystem.getActiveServices();
        Map<String, List<org.niis.xroad.catalog.persistence.entity.Service>> byCode = new LinkedHashMap<>();
        List<org.niis.xroad.catalog.persistence.entity.Service> sortedByCode = new ArrayList<>(source);
        sortedByCode.sort(Comparator.comparing(org.niis.xroad.catalog.persistence.entity.Service::getServiceCode));
        for (org.niis.xroad.catalog.persistence.entity.Service s : sortedByCode) {
            byCode.computeIfAbsent(s.getServiceCode(), k -> new ArrayList<>()).add(s);
        }
        List<ServiceDto> aggregates = new ArrayList<>();
        for (List<org.niis.xroad.catalog.persistence.entity.Service> group : byCode.values()) {
            ServiceDto dto = serviceAggregator.aggregate(group, includeRemoved);
            if (dto != null) {
                aggregates.add(dto);
            }
        }
        aggregates.sort(Comparator.comparing(ServiceDto::getServiceCode));
        return aggregates;
    }

    private SubsystemNameLookup subsystemNameLookup() {
        Map<String, String> byKey = new HashMap<>();
        try {
            List<SubsystemNameInfo> names = sharedParamsParser.parseSubsystemNames(sharedParamsFile);
            for (SubsystemNameInfo n : names) {
                byKey.put(keyOf(n.getMemberClass(), n.getMemberCode(), n.getSubsystemCode()), n.getSubsystemName());
            }
        } catch (Exception e) {
            log.warn("Failed to parse shared-params for subsystemName enrichment: {}", sharedParamsFile, e);
        }
        return (mc, mcode, sc) -> byKey.get(keyOf(mc, mcode, sc));
    }

    private String keyOf(String memberClass, String memberCode, String subsystemCode) {
        return memberClass + "|" + memberCode + "|" + subsystemCode;
    }
}
