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

import org.niis.xroad.catalog.lister.v2.dto.MemberSearchHit;
import org.niis.xroad.catalog.lister.v2.dto.SearchHit;
import org.niis.xroad.catalog.lister.v2.dto.ServiceSearchHit;
import org.niis.xroad.catalog.lister.v2.dto.SubsystemSearchHit;
import org.niis.xroad.catalog.persistence.repository.SearchRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Cross-type search over members, subsystems and service aggregates. Uses the native UNION query in
 * {@link SearchRepository} for DB-level pagination; the exact total across every page rides along on
 * every returned row (column 10, via {@code COUNT(*) OVER ()}), so a matching page is served by a
 * single statement. {@code is_provider} and {@code service_types} ride along the same way (columns 8
 * and 9), so this is pure row mapping — no per-row repository calls, no N+1. {@link
 * SearchRepository#countSearchUnion} is only consulted when a page is empty at a non-zero offset,
 * since {@code searchUnion} then returns no rows to carry a total.
 */
@Service
public class SearchServiceV2 {

    private static final int MIN_QUERY_LENGTH = 3;
    private static final int COL_TYPE = 0;
    private static final int COL_MEMBER_CLASS = 3;
    private static final int COL_MEMBER_CODE = 4;
    private static final int COL_MEMBER_NAME = 5;
    private static final int COL_SUBSYSTEM_CODE = 6;
    private static final int COL_SERVICE_CODE = 7;
    private static final int COL_IS_PROVIDER = 8;
    private static final int COL_SERVICE_TYPES = 9;
    private static final int COL_TOTAL_COUNT = 10;

    private final SearchRepository searchRepository;

    public SearchServiceV2(SearchRepository searchRepository) {
        this.searchRepository = searchRepository;
    }

    public Page<SearchHit> search(String query, Pageable pageable) {
        if (query == null || query.length() < MIN_QUERY_LENGTH) {
            throw new IllegalArgumentException(
                    "Query parameter 'q' must be at least " + MIN_QUERY_LENGTH + " characters");
        }

        String like = "%" + escapeLike(query) + "%";
        List<Object[]> rows = searchRepository.searchUnion(like, pageable.getPageSize(), pageable.getOffset());
        long totalCount;
        if (rows.isEmpty()) {
            totalCount = pageable.getOffset() == 0 ? 0 : searchRepository.countSearchUnion(like);
            return new PageImpl<>(List.of(), pageable, totalCount);
        }
        totalCount = ((Number) rows.get(0)[COL_TOTAL_COUNT]).longValue();
        return new PageImpl<>(rows.stream().map(this::toResult).toList(), pageable, totalCount);
    }

    private SearchHit toResult(Object[] row) {
        String type = (String) row[COL_TYPE];
        String memberClass = (String) row[COL_MEMBER_CLASS];
        String memberCode = (String) row[COL_MEMBER_CODE];
        String memberName = (String) row[COL_MEMBER_NAME];
        String subsystemCode = (String) row[COL_SUBSYSTEM_CODE];
        String serviceCode = (String) row[COL_SERVICE_CODE];
        return switch (type) {
            case "member" -> new MemberSearchHit(
                    memberClass,
                    memberCode,
                    memberName,
                    Boolean.TRUE.equals(row[COL_IS_PROVIDER]));
            case "subsystem" -> new SubsystemSearchHit(
                    memberClass,
                    memberCode,
                    memberName,
                    subsystemCode);
            case "service" -> new ServiceSearchHit(
                    memberClass,
                    memberCode,
                    memberName,
                    subsystemCode,
                    serviceCode,
                    List.of(((String) row[COL_SERVICE_TYPES]).split(",")));
            default -> throw new IllegalStateException("Unknown entity_type: " + type);
        };
    }

    /**
     * Escapes the SQL LIKE metacharacters {@code \}, {@code %} and {@code _} in {@code input} so
     * that user-typed text in the {@code q} parameter is matched as a literal substring rather than
     * a pattern. The escape character is {@code \}, declared via {@code ESCAPE '\\'} on every
     * {@code LIKE} clause in {@link SearchRepository}. Order matters: {@code \} is escaped first;
     * the second and third replacements then prepend {@code \} to {@code %} and {@code _} without
     * being double-escaped.
     */
    private static String escapeLike(String input) {
        return input
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }
}
