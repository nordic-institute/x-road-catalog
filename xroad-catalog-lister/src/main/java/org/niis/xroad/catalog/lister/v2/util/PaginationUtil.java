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

import org.niis.xroad.catalog.lister.v2.exception.BadRequestException;
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
     * Builds a {@link Pageable} appending a secondary {@code id ASC} tie-break so page contents
     * stay stable across requests. Requires the selected row to expose an {@code id} field.
     */
    public static Pageable toPageable(Integer page, Integer size, String sortBy, String sortOrder,
                                      String defaultSortField, Set<String> allowedSortFields) {
        Sort primary = buildPrimarySort(sortBy, sortOrder, defaultSortField, allowedSortFields);
        Sort sort = primary.and(Sort.by(Sort.Direction.ASC, ID_TIE_BREAK_FIELD));
        return PageRequest.of(resolvePageIndex(page), resolvePageSize(size), sort);
    }

    /**
     * Builds a {@link Pageable} without the {@code id} tie-break, for queries whose {@code SELECT}
     * exposes no {@code id} column. The caller is responsible for stable ordering.
     */
    public static Pageable toPageableNoTieBreak(Integer page, Integer size, String sortBy, String sortOrder,
                                                String defaultSortField, Set<String> allowedSortFields) {
        Sort sort = buildPrimarySort(sortBy, sortOrder, defaultSortField, allowedSortFields);
        return PageRequest.of(resolvePageIndex(page), resolvePageSize(size), sort);
    }

    /**
     * Same as {@link #toPageable(Integer, Integer, String, String, String, Set)} but translates the
     * validated logical {@code sortBy} through {@code aliases} to its JPQL field; logical names
     * absent from the map fall through unchanged.
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
     * Builds a {@link Pageable} with no {@link Sort}, for endpoints whose ordering is fixed at the
     * SQL layer and that expose no {@code sortBy}/{@code sortOrder} parameters. An out-of-range
     * {@code page} or {@code size} surfaces as {@link BadRequestException} (mapped to 400).
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

    // Validated here rather than left to PageRequest.of, whose messages describe the internal
    // zero-based index instead of the 'page' parameter the client sent.
    private static int resolvePageIndex(Integer page) {
        int pageNumber = page != null ? page : DEFAULT_PAGE;
        if (pageNumber < 1) {
            throw new BadRequestException("Query parameter 'page' must be 1 or greater");
        }
        return pageNumber - 1;
    }

    private static int resolvePageSize(Integer size) {
        int resolved = size != null ? size : DEFAULT_SIZE;
        if (resolved < 1) {
            throw new BadRequestException("Query parameter 'size' must be 1 or greater");
        }
        if (resolved > MAX_PAGE_SIZE) {
            throw new BadRequestException(
                    "Query parameter 'size' must not exceed " + MAX_PAGE_SIZE);
        }
        return resolved;
    }

    private static String resolveSortField(String sortBy, String defaultSortField, Set<String> allowedSortFields) {
        if (sortBy == null || sortBy.isBlank()) {
            return defaultSortField;
        }
        if (!allowedSortFields.contains(sortBy)) {
            throw new BadRequestException(
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
            default -> throw new BadRequestException(
                    "Invalid sort order: '%s'. Allowed values: 'asc', 'desc'".formatted(sortOrder));
        };
    }
}
