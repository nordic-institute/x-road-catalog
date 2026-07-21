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

import org.niis.xroad.catalog.lister.v2.dto.MemberClassDto;
import org.niis.xroad.catalog.persistence.repository.MemberRepositoryV2;
import org.niis.xroad.catalog.persistence.repository.projection.MemberClassCountRow;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * V2 member-class service. Member counts come straight from the database (denormalized nowhere);
 * descriptions come from the shared-params XML global settings.
 */
@Service
public class MemberClassServiceV2 {

    private final SharedParamsCache sharedParamsCache;
    private final MemberRepositoryV2 memberRepository;
    private final InstanceContext instanceContext;

    public MemberClassServiceV2(SharedParamsCache sharedParamsCache, MemberRepositoryV2 memberRepository,
            InstanceContext instanceContext) {
        this.sharedParamsCache = sharedParamsCache;
        this.memberRepository = memberRepository;
        this.instanceContext = instanceContext;
    }

    /**
     * Looks up a single member class by code. {@code memberCount} is resolved via
     * {@link MemberRepositoryV2#countActiveByMemberClass} rather than {@link #list()} so a lookup
     * for one code costs one grouped count query, not a full description parse plus a full grouped
     * count query. A code is considered to exist when it has either a shared-params description or
     * at least one active member; an unknown code (neither) returns {@code null} (controller maps to
     * 404).
     */
    public MemberClassDto getByCode(String code) {
        if (code == null) {
            return null;
        }
        String description = sharedParamsCache.memberClassDescriptions().get(code);
        long count = memberRepository.countActiveByMemberClass(instanceContext.getCurrentInstance(), code);
        if (description == null && count == 0) {
            return null;
        }
        return MemberClassDto.builder()
                .code(code)
                .description(description)
                .memberCount(Math.toIntExact(count))
                .build();
    }

    /**
     * Lists every known member class: the union of codes declared in the shared-params global
     * settings and codes with at least one active member. Member counts come from
     * {@link MemberRepositoryV2#countActiveGroupedByMemberClass} (one grouped query) instead of the
     * old per-member counting loop.
     */
    public List<MemberClassDto> list() {
        SharedParamsCache.MemberClasses memberClasses = sharedParamsCache.memberClasses();
        Map<String, String> descriptions = memberClasses.descriptions();

        Map<String, Long> counts = new HashMap<>();
        for (MemberClassCountRow row : memberRepository.countActiveGroupedByMemberClass(instanceContext.getCurrentInstance())) {
            counts.put(row.getCode(), row.getMemberCount());
        }

        Set<String> allCodes = new HashSet<>(memberClasses.codes());
        allCodes.addAll(counts.keySet());

        List<MemberClassDto> out = new ArrayList<>();
        for (String code : allCodes) {
            out.add(MemberClassDto.builder()
                    .code(code)
                    .description(descriptions.get(code))
                    .memberCount(Math.toIntExact(counts.getOrDefault(code, 0L)))
                    .build());
        }
        out.sort(Comparator.comparing(MemberClassDto::getCode));
        return out;
    }
}
