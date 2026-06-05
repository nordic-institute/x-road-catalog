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
import org.niis.xroad.catalog.lister.v2.converter.SubsystemConverter;
import org.niis.xroad.catalog.lister.v2.converter.SubsystemNameLookup;
import org.niis.xroad.catalog.lister.v2.dto.SubsystemDto;
import org.niis.xroad.catalog.lister.v2.dto.SubsystemNameInfo;
import org.niis.xroad.catalog.lister.v2.parser.SharedParamsParserV2;
import org.niis.xroad.catalog.persistence.entity.Member;
import org.niis.xroad.catalog.persistence.entity.Subsystem;
import org.niis.xroad.catalog.persistence.repository.MemberRepositoryV2;
import org.niis.xroad.catalog.persistence.repository.SubsystemRepositoryV2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
public class SubsystemServiceV2 {

    @Autowired
    private SubsystemRepositoryV2 subsystemRepository;

    @Autowired
    private MemberRepositoryV2 memberRepository;

    @Autowired
    private SubsystemConverter converter;

    @Autowired
    private SharedParamsParserV2 sharedParamsParser;

    @Autowired
    private InstanceContext instanceContext;

    @Value("${xroad-catalog.shared-params-file}")
    private String sharedParamsFile;

    public SubsystemDto getByNaturalKey(String memberClass, String memberCode, String subsystemCode,
                                        boolean includeRemoved) {
        String instance = instanceContext.getCurrentInstance();
        Subsystem subsystem = includeRemoved
                ? subsystemRepository.findAnyByNaturalKey(instance, memberClass, memberCode, subsystemCode)
                : subsystemRepository.findActiveByNaturalKey(instance, memberClass, memberCode, subsystemCode);
        if (subsystem == null) {
            return null;
        }
        return converter.toDto(subsystem, subsystemNameLookup(), includeRemoved);
    }

    public Page<SubsystemDto> getForList(String memberClass, boolean includeRemoved, Pageable pageable) {
        boolean activeOnly = !includeRemoved;
        Page<Subsystem> page = subsystemRepository.findForList(memberClass, activeOnly, pageable);
        SubsystemNameLookup lookup = subsystemNameLookup();
        return page.map(sub -> converter.toDto(sub, lookup, includeRemoved));
    }

    /**
     * Returns the subsystems under a single member, sorted by {@code subsystemCode} ascending.
     * Member is looked up via {@link MemberRepositoryV2} (entity-graph annotated in Task 0) so the
     * subsystem set is pre-fetched. Returns an empty list when the member is absent (the controller
     * is responsible for the parent 404; this method stays defensive). When {@code includeRemoved}
     * is true, removed subsystems are included; otherwise the active-only view is returned.
     */
    public List<SubsystemDto> getForMember(String memberClass, String memberCode, boolean includeRemoved) {
        String instance = instanceContext.getCurrentInstance();
        Member member = includeRemoved
                ? memberRepository.findAnyByNaturalKey(instance, memberClass, memberCode)
                : memberRepository.findActiveByNaturalKey(instance, memberClass, memberCode);
        if (member == null) {
            return Collections.emptyList();
        }
        Set<Subsystem> source = includeRemoved ? member.getAllSubsystems() : member.getActiveSubsystems();
        SubsystemNameLookup lookup = subsystemNameLookup();
        List<Subsystem> sorted = new ArrayList<>(source);
        sorted.sort(Comparator.comparing(Subsystem::getSubsystemCode));
        List<SubsystemDto> result = new ArrayList<>(sorted.size());
        for (Subsystem sub : sorted) {
            result.add(converter.toDto(sub, lookup, includeRemoved));
        }
        return result;
    }

    private SubsystemNameLookup subsystemNameLookup() {
        Map<String, String> byKey = new HashMap<>();
        try {
            List<SubsystemNameInfo> names = sharedParamsParser.parseSubsystemNames(sharedParamsFile);
            for (SubsystemNameInfo n : names) {
                byKey.put(keyOf(n.getMemberClass(), n.getMemberCode(), n.getSubsystemCode()), n.getSubsystemName());
            }
        } catch (Exception e) {
            // parse errors must not break API serving — subsystemName is best-effort
            log.warn("Failed to parse shared-params for subsystemName enrichment: {}", sharedParamsFile, e);
        }
        return (mc, mcode, sc) -> byKey.get(keyOf(mc, mcode, sc));
    }

    private String keyOf(String memberClass, String memberCode, String subsystemCode) {
        return memberClass + "|" + memberCode + "|" + subsystemCode;
    }
}
