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
import org.niis.xroad.catalog.persistence.v2.repository.MemberRepository;
import org.niis.xroad.catalog.persistence.v2.repository.projection.MemberClassCountRow;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * V2 member-class service. Member counts come from the database; descriptions from the
 * shared-params XML global settings.
 */
@Service
public class MemberClassService {

    private final SharedParamsCache sharedParamsCache;
    private final MemberRepository memberRepository;

    public MemberClassService(SharedParamsCache sharedParamsCache, MemberRepository memberRepository) {
        this.sharedParamsCache = sharedParamsCache;
        this.memberRepository = memberRepository;
    }

    /**
     * A code exists when it has either a shared-params description or at least one active member;
     * anything else returns an empty {@link Optional}.
     */
    public Optional<MemberClassDto> getByCode(String code) {
        if (code == null) {
            return Optional.empty();
        }
        String description = sharedParamsCache.memberClassDescriptions().get(code);
        long count = memberRepository.countActiveByMemberClass(sharedParamsCache.getCurrentInstance(), code);
        if (description == null && count == 0) {
            return Optional.empty();
        }
        return Optional.of(MemberClassDto.builder()
                .code(code)
                .description(description)
                .memberCount(Math.toIntExact(count))
                .build());
    }

    /**
     * Lists every known member class: the union of codes declared in the shared-params global
     * settings and codes with at least one active member, counted with a single grouped query.
     */
    public List<MemberClassDto> list() {
        SharedParamsCache.MemberClasses memberClasses = sharedParamsCache.memberClasses();
        Map<String, String> descriptions = memberClasses.descriptions();

        Map<String, Long> counts = new HashMap<>();
        for (MemberClassCountRow row : memberRepository.countActiveGroupedByMemberClass(sharedParamsCache.getCurrentInstance())) {
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
