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
package org.niis.xroad.catalog.lister.v2.util;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class PaginationUtil {

    public static final int DEFAULT_PAGE = 1;
    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_PAGE_SIZE = 200;

    private static final String ID_TIE_BREAK_FIELD = "id";

    private PaginationUtil() {
    }

    /**
     * Builds a {@link Pageable} whose sort appends a secondary {@code id ASC} order as a deterministic tie-break
     * (spec §8). Use for entity queries whose selected row exposes an {@code id} field that JPQL/JPA can reference
     * (e.g. {@code Member}, {@code Subsystem}, {@code Service}, {@code ErrorLog}).
     */
    public static Pageable toPageable(Integer page, Integer size, String sortBy, String sortOrder,
                                      String defaultSortField, Set<String> allowedSortFields) {
        Sort primary = buildPrimarySort(sortBy, sortOrder, defaultSortField, allowedSortFields);
        Sort sort = primary.and(Sort.by(Sort.Direction.ASC, ID_TIE_BREAK_FIELD));
        return PageRequest.of(resolvePageIndex(page), resolvePageSize(size), sort);
    }

    /**
     * Builds a {@link Pageable} without appending an {@code id} tie-break. Use for grouped/projection queries whose
     * {@code SELECT} does not expose an {@code id} column (e.g. aggregate queries with {@code GROUP BY}), or for
     * callers that already provide a unique composite sort key. The caller is responsible for stable ordering.
     */
    public static Pageable toPageableNoTieBreak(Integer page, Integer size, String sortBy, String sortOrder,
                                                String defaultSortField, Set<String> allowedSortFields) {
        Sort sort = buildPrimarySort(sortBy, sortOrder, defaultSortField, allowedSortFields);
        return PageRequest.of(resolvePageIndex(page), resolvePageSize(size), sort);
    }

    /**
     * Same as {@link #toPageable(Integer, Integer, String, String, String, Set)} but supports a logical to JPQL
     * field alias map. The {@code sortBy} value is validated against {@code allowedSortFields} (logical names),
     * then translated through {@code aliases} before being passed to {@link Sort#by(Sort.Direction, String...)}.
     * Logical names absent from the map fall through unchanged.
     *
     * @throws NullPointerException if {@code aliases} is null; use {@link Map#of()} for none.
     */
    public static Pageable toPageable(Integer page, Integer size, String sortBy, String sortOrder,
                                      String defaultSortField, Set<String> allowedSortFields,
                                      Map<String, String> aliases) {
        Objects.requireNonNull(aliases, "aliases must not be null; use Map.of() for none");
        Sort primary = buildPrimarySort(sortBy, sortOrder, defaultSortField, allowedSortFields, aliases);
        Sort sort = primary.and(Sort.by(Sort.Direction.ASC, ID_TIE_BREAK_FIELD));
        return PageRequest.of(resolvePageIndex(page), resolvePageSize(size), sort);
    }

    /**
     * Builds a {@link Pageable} with no {@link Sort} attached. Use for endpoints whose ordering is
     * fixed at the repository / SQL layer and where the public API surface intentionally does not
     * expose {@code sortBy} / {@code sortOrder} (e.g., {@code GET /api/v2/search}). Page-index and
     * page-size defaults match the other overloads ({@code page=1}, {@code size=20}); the resulting
     * {@code Pageable} delegates to {@link PageRequest#of(int, int)} so an out-of-range {@code page}
     * or {@code size} surfaces as {@link IllegalArgumentException} for the controller layer to map
     * to {@code 400 BadRequest}.
     */
    public static Pageable toPageableNoSort(Integer page, Integer size) {
        return PageRequest.of(resolvePageIndex(page), resolvePageSize(size));
    }

    private static Sort buildPrimarySort(String sortBy, String sortOrder, String defaultSortField,
                                         Set<String> allowedSortFields) {
        return buildPrimarySort(sortBy, sortOrder, defaultSortField, allowedSortFields, Map.of());
    }

    private static Sort buildPrimarySort(String sortBy, String sortOrder, String defaultSortField,
                                         Set<String> allowedSortFields, Map<String, String> aliases) {
        String resolvedLogical = resolveSortField(sortBy, defaultSortField, allowedSortFields);
        String physical = aliases.getOrDefault(resolvedLogical, resolvedLogical);
        Sort.Direction direction = resolveSortDirection(sortOrder);
        return Sort.by(direction, physical);
    }

    private static int resolvePageIndex(Integer page) {
        int pageNumber = page != null ? page : DEFAULT_PAGE;
        return pageNumber - 1;
    }

    private static int resolvePageSize(Integer size) {
        int resolved = size != null ? size : DEFAULT_SIZE;
        if (resolved > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException(
                    "Query parameter 'size' must not exceed " + MAX_PAGE_SIZE);
        }
        return resolved;
    }

    private static String resolveSortField(String sortBy, String defaultSortField, Set<String> allowedSortFields) {
        if (sortBy == null || sortBy.isBlank()) {
            return defaultSortField;
        }
        if (!allowedSortFields.contains(sortBy)) {
            throw new IllegalArgumentException(
                    "Invalid sort field: '%s'. Allowed fields: %s".formatted(sortBy, allowedSortFields));
        }
        return sortBy;
    }

    private static Sort.Direction resolveSortDirection(String sortOrder) {
        if (sortOrder == null || sortOrder.isBlank()) {
            return Sort.Direction.ASC;
        }
        return switch (sortOrder.toLowerCase(Locale.ROOT)) {
            case "asc" -> Sort.Direction.ASC;
            case "desc" -> Sort.Direction.DESC;
            default -> throw new IllegalArgumentException(
                    "Invalid sort order: '%s'. Allowed values: 'asc', 'desc'".formatted(sortOrder));
        };
    }
}
