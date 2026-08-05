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
import org.niis.xroad.catalog.lister.v2.controller.BadRequestException;
import org.niis.xroad.catalog.lister.v2.dto.MemberSearchHit;
import org.niis.xroad.catalog.lister.v2.dto.SearchHit;
import org.niis.xroad.catalog.lister.v2.dto.ServiceSearchHit;
import org.niis.xroad.catalog.lister.v2.dto.SubsystemSearchHit;
import org.niis.xroad.catalog.persistence.v2.repository.SearchRepository;
import org.niis.xroad.catalog.persistence.v2.repository.projection.SearchHitRow;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

/**
 * Cross-type search over members, subsystems and service aggregates via the native UNION query in
 * {@link SearchRepository}. The exact total rides along on every row ({@code COUNT(*) OVER ()}),
 * so a matching page is served by a single statement with pure row mapping — no N+1.
 * {@link SearchRepository#countSearchUnion} is consulted only when a page is empty at a non-zero
 * offset, since no returned rows then carry a total.
 *
 * <p>Results are scoped to the current X-Road instance, so a hit can always be resolved by the
 * browse endpoints; resolving the instance also gives search the same not-ready 503 as its siblings.
 */
@Slf4j
@Service
public class SearchService {

    private static final int MIN_QUERY_LENGTH = 3;

    private final SearchRepository searchRepository;
    private final SharedParamsCache sharedParamsCache;

    public SearchService(SearchRepository searchRepository, SharedParamsCache sharedParamsCache) {
        this.searchRepository = searchRepository;
        this.sharedParamsCache = sharedParamsCache;
    }

    public Page<SearchHit> search(String query, Pageable pageable) {
        if (query == null || query.isBlank() || query.length() < MIN_QUERY_LENGTH) {
            throw new BadRequestException(
                    "Query parameter 'q' must be at least " + MIN_QUERY_LENGTH + " characters");
        }

        String instance = sharedParamsCache.getCurrentInstance();
        String like = "%" + escapeLike(query) + "%";
        List<SearchHitRow> rows = searchRepository.searchUnion(instance, like, pageable.getPageSize(),
                pageable.getOffset());
        long totalCount;
        if (rows.isEmpty()) {
            totalCount = pageable.getOffset() == 0 ? 0 : searchRepository.countSearchUnion(instance, like);
            return new PageImpl<>(List.of(), pageable, totalCount);
        }
        totalCount = rows.get(0).getTotalCount();
        List<SearchHit> hits = rows.stream().map(this::toResult).filter(Objects::nonNull).toList();
        return new PageImpl<>(hits, pageable, totalCount);
    }

    private SearchHit toResult(SearchHitRow row) {
        return switch (row.getEntityType()) {
            case "member" -> new MemberSearchHit(
                    row.getMemberClass(), row.getMemberCode(), row.getMemberName(),
                    Boolean.TRUE.equals(row.getIsProvider()));
            case "subsystem" -> new SubsystemSearchHit(
                    row.getMemberClass(), row.getMemberCode(), row.getMemberName(), row.getSubsystemCode());
            case "service" -> new ServiceSearchHit(
                    row.getMemberClass(), row.getMemberCode(), row.getMemberName(), row.getSubsystemCode(),
                    row.getServiceCode(), List.of(row.getServiceTypes().split(",")));
            default -> {
                log.warn("Skipping search index row with unknown entity_type '{}'", row.getEntityType());
                yield null;
            }
        };
    }

    /**
     * Escapes {@code \}, {@code %} and {@code _} so user text matches as a literal substring; the
     * escape character is declared via {@code ESCAPE '\\'} on every {@code LIKE} clause. Order
     * matters: {@code \} first, so the later replacements are not double-escaped.
     */
    private static String escapeLike(String input) {
        return input
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }
}
