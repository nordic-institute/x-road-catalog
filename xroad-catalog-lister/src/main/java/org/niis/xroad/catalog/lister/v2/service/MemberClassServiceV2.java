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
import org.niis.xroad.catalog.lister.v2.dto.MemberClassDto;
import org.niis.xroad.catalog.lister.v2.dto.MemberClassInfo;
import org.niis.xroad.catalog.lister.v2.parser.SharedParamsParserV2;
import org.niis.xroad.catalog.persistence.entity.Member;
import org.niis.xroad.catalog.persistence.repository.MemberRepositoryV2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Component
public class MemberClassServiceV2 {

    @Autowired
    private SharedParamsParserV2 parser;

    @Autowired
    private MemberRepositoryV2 memberRepository;

    @Value("${xroad-catalog.shared-params-file}")
    private String sharedParamsFile;

    public MemberClassDto getByCode(String code) {
        if (code == null) {
            return null;
        }
        for (MemberClassDto dto : list()) {
            if (code.equals(dto.getCode())) {
                return dto;
            }
        }
        return null;
    }

    public List<MemberClassDto> list() {
        Map<String, String> descriptions = new HashMap<>();
        try {
            for (MemberClassInfo info : parser.parseMemberClasses(sharedParamsFile)) {
                descriptions.put(info.getCode(), info.getDescription());
            }
        } catch (Exception e) {
            log.warn("Failed to parse member classes from shared-params file {}", sharedParamsFile, e);
        }

        Map<String, Integer> counts = new HashMap<>();
        for (Member member : memberRepository.findAllActive()) {
            counts.merge(member.getMemberClass(), 1, Integer::sum);
        }

        Set<String> allCodes = new HashSet<>();
        allCodes.addAll(descriptions.keySet());
        allCodes.addAll(counts.keySet());

        List<MemberClassDto> out = new ArrayList<>();
        for (String code : allCodes) {
            out.add(MemberClassDto.builder()
                    .code(code)
                    .description(descriptions.get(code))
                    .memberCount(counts.getOrDefault(code, 0))
                    .build());
        }
        out.sort(Comparator.comparing(MemberClassDto::getCode));
        return out;
    }
}
