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

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaginationUtilTest {

    private static final String DEFAULT_SORT_FIELD = "name";
    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of("name", "created", "updated", "memberCode");
    private static final String FIELD_CREATED = "created";
    private static final String ALIAS_STATUS_INFO_CREATED = "statusInfo.created";
    private static final String SORT_ORDER_ASC = "asc";

    @Test
    void toPageableDefaultValuesAppendIdTieBreak() {
        Pageable pageable = PaginationUtil.toPageable(null, null, null, null,
                DEFAULT_SORT_FIELD, ALLOWED_SORT_FIELDS);

        assertThat(pageable.getPageNumber()).isZero();
        assertThat(pageable.getPageSize()).isEqualTo(PaginationUtil.DEFAULT_SIZE);
        assertThat(pageable.getSort().stream().toList()).containsExactly(
                new Sort.Order(Sort.Direction.ASC, DEFAULT_SORT_FIELD),
                new Sort.Order(Sort.Direction.ASC, "id"));
    }

    @Test
    void toPageableCustomValuesAppendIdTieBreak() {
        Pageable pageable = PaginationUtil.toPageable(2, 50, "memberCode", "desc",
                DEFAULT_SORT_FIELD, ALLOWED_SORT_FIELDS);

        assertThat(pageable.getPageNumber()).isEqualTo(1);
        assertThat(pageable.getPageSize()).isEqualTo(50);
        assertThat(pageable.getSort().stream().toList()).containsExactly(
                new Sort.Order(Sort.Direction.DESC, "memberCode"),
                new Sort.Order(Sort.Direction.ASC, "id"));
    }

    @Test
    void toPageableSortOrderIsCaseInsensitive() {
        Pageable pageable = PaginationUtil.toPageable(null, null, "name", "DESC",
                DEFAULT_SORT_FIELD, ALLOWED_SORT_FIELDS);

        assertThat(pageable.getSort().stream().toList()).containsExactly(
                new Sort.Order(Sort.Direction.DESC, "name"),
                new Sort.Order(Sort.Direction.ASC, "id"));
    }

    @Test
    void toPageableRejectsInvalidSortField() {
        assertThatThrownBy(() ->
                PaginationUtil.toPageable(null, null, "invalid", null,
                        DEFAULT_SORT_FIELD, ALLOWED_SORT_FIELDS))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid sort field")
                .hasMessageContaining("invalid");
    }

    @Test
    void toPageableRejectsInvalidSortOrder() {
        assertThatThrownBy(() ->
                PaginationUtil.toPageable(null, null, null, "sideways",
                        DEFAULT_SORT_FIELD, ALLOWED_SORT_FIELDS))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid sort order")
                .hasMessageContaining("sideways");
    }

    @Test
    void toPageableNoTieBreakDefaultValuesHaveSingleOrder() {
        Pageable pageable = PaginationUtil.toPageableNoTieBreak(null, null, null, null,
                DEFAULT_SORT_FIELD, ALLOWED_SORT_FIELDS);

        assertThat(pageable.getPageNumber()).isZero();
        assertThat(pageable.getPageSize()).isEqualTo(PaginationUtil.DEFAULT_SIZE);
        List<Sort.Order> orders = pageable.getSort().stream().toList();
        assertThat(orders).containsExactly(new Sort.Order(Sort.Direction.ASC, DEFAULT_SORT_FIELD));
    }

    @Test
    void toPageableNoTieBreakCustomValues() {
        Pageable pageable = PaginationUtil.toPageableNoTieBreak(3, 25, "memberCode", "desc",
                DEFAULT_SORT_FIELD, ALLOWED_SORT_FIELDS);

        assertThat(pageable.getPageNumber()).isEqualTo(2);
        assertThat(pageable.getPageSize()).isEqualTo(25);
        assertThat(pageable.getSort().stream().toList())
                .containsExactly(new Sort.Order(Sort.Direction.DESC, "memberCode"));
    }

    @Test
    void toPageableNoTieBreakRejectsInvalidSortField() {
        assertThatThrownBy(() ->
                PaginationUtil.toPageableNoTieBreak(null, null, "invalid", null,
                        DEFAULT_SORT_FIELD, ALLOWED_SORT_FIELDS))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid sort field")
                .hasMessageContaining("invalid");
    }

    @Test
    void toPageableNoTieBreakRejectsInvalidSortOrder() {
        assertThatThrownBy(() ->
                PaginationUtil.toPageableNoTieBreak(null, null, null, "sideways",
                        DEFAULT_SORT_FIELD, ALLOWED_SORT_FIELDS))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid sort order")
                .hasMessageContaining("sideways");
    }

    @Test
    void bothMethodsProduceIdenticalErrorMessagesForInvalidSortField() {
        String msgTieBreak = extractMessage(() -> PaginationUtil.toPageable(
                null, null, "invalid", null, DEFAULT_SORT_FIELD, ALLOWED_SORT_FIELDS));
        String msgNoTieBreak = extractMessage(() -> PaginationUtil.toPageableNoTieBreak(
                null, null, "invalid", null, DEFAULT_SORT_FIELD, ALLOWED_SORT_FIELDS));
        assertThat(msgTieBreak).isEqualTo(msgNoTieBreak);
    }

    @Test
    void bothMethodsProduceIdenticalErrorMessagesForInvalidSortOrder() {
        String msgTieBreak = extractMessage(() -> PaginationUtil.toPageable(
                null, null, null, "sideways", DEFAULT_SORT_FIELD, ALLOWED_SORT_FIELDS));
        String msgNoTieBreak = extractMessage(() -> PaginationUtil.toPageableNoTieBreak(
                null, null, null, "sideways", DEFAULT_SORT_FIELD, ALLOWED_SORT_FIELDS));
        assertThat(msgTieBreak).isEqualTo(msgNoTieBreak);
    }

    @Test
    void toPageableTranslatesAliasedSortField() {
        Pageable p = PaginationUtil.toPageable(1, 20, FIELD_CREATED, SORT_ORDER_ASC, DEFAULT_SORT_FIELD,
                Set.of(DEFAULT_SORT_FIELD, FIELD_CREATED), Map.of(FIELD_CREATED, ALIAS_STATUS_INFO_CREATED));
        Sort.Order primary = p.getSort().stream().findFirst().orElseThrow();
        assertThat(primary.getProperty()).isEqualTo(ALIAS_STATUS_INFO_CREATED);
        assertThat(primary.getDirection()).isEqualTo(Sort.Direction.ASC);
        Sort.Order tieBreak = p.getSort().getOrderFor("id");
        assertThat(tieBreak).isNotNull();
        assertThat(tieBreak.getDirection()).isEqualTo(Sort.Direction.ASC);
    }

    @Test
    void toPageablePassesNonAliasedFieldUnchanged() {
        Pageable p = PaginationUtil.toPageable(1, 20, DEFAULT_SORT_FIELD, "desc", DEFAULT_SORT_FIELD,
                Set.of(DEFAULT_SORT_FIELD), Map.of(FIELD_CREATED, ALIAS_STATUS_INFO_CREATED));
        Sort.Order primary = p.getSort().stream().findFirst().orElseThrow();
        assertThat(primary.getProperty()).isEqualTo(DEFAULT_SORT_FIELD);
        assertThat(primary.getDirection()).isEqualTo(Sort.Direction.DESC);
    }

    @Test
    void toPageableValidatesAgainstLogicalAllowList() {
        assertThatThrownBy(() -> PaginationUtil.toPageable(1, 20, ALIAS_STATUS_INFO_CREATED, SORT_ORDER_ASC,
                DEFAULT_SORT_FIELD, Set.of(DEFAULT_SORT_FIELD, FIELD_CREATED),
                Map.of(FIELD_CREATED, ALIAS_STATUS_INFO_CREATED)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid sort field");
    }

    @Test
    void toPageableRequiresNonNullAliases() {
        assertThatThrownBy(() -> PaginationUtil.toPageable(1, 20, DEFAULT_SORT_FIELD, SORT_ORDER_ASC, DEFAULT_SORT_FIELD,
                Set.of(DEFAULT_SORT_FIELD), null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("aliases");
    }

    private static String extractMessage(Runnable runnable) {
        try {
            runnable.run();
        } catch (IllegalArgumentException e) {
            return e.getMessage();
        }
        throw new AssertionError("Expected IllegalArgumentException");
    }

    @Test
    void toPageableNoSortDefaultsToPage1Size20Unsorted() {
        Pageable pageable = PaginationUtil.toPageableNoSort(null, null);
        assertThat(pageable.getPageNumber()).isZero();
        assertThat(pageable.getPageSize()).isEqualTo(20);
        assertThat(pageable.getSort().isUnsorted()).isTrue();
    }

    @Test
    void toPageableNoSortHonorsExplicitPageAndSize() {
        Pageable pageable = PaginationUtil.toPageableNoSort(3, 5);
        assertThat(pageable.getPageNumber()).isEqualTo(2);
        assertThat(pageable.getPageSize()).isEqualTo(5);
        assertThat(pageable.getSort().isUnsorted()).isTrue();
    }

    @Test
    void toPageableNoSortRejectsZeroPage() {
        assertThatThrownBy(() -> PaginationUtil.toPageableNoSort(0, 20))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void toPageableNoSortRejectsNegativeSize() {
        assertThatThrownBy(() -> PaginationUtil.toPageableNoSort(1, -1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void toPageableNoSortRejectsZeroSize() {
        assertThatThrownBy(() -> PaginationUtil.toPageableNoSort(1, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void toPageableAcceptsMaxPageSizeBoundary() {
        Pageable pageable = PaginationUtil.toPageableNoSort(1, 200);
        assertThat(pageable.getPageSize()).isEqualTo(200);
    }

    @Test
    void toPageableRejectsSizeAboveMaxOnNoSort() {
        assertThatThrownBy(() -> PaginationUtil.toPageableNoSort(1, 201))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not exceed 200");
    }

    @Test
    void toPageableRejectsSizeAboveMaxOnSortedOverload() {
        assertThatThrownBy(() -> PaginationUtil.toPageable(
                1, 201, null, null, DEFAULT_SORT_FIELD, ALLOWED_SORT_FIELDS))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not exceed 200");
    }
}
