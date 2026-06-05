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
package org.niis.xroad.catalog.lister.v2.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class PagedCollectionResponseTest {

    @Test
    void fromPageMapsContentAndPaginationMetadata() {
        var content = List.of("alpha", "beta", "gamma");
        var pageable = PageRequest.of(2, 10);
        Page<String> page = new PageImpl<>(content, pageable, 53);

        var response = PagedCollectionResponse.fromPage(page);

        assertEquals(content, response.getItems());
        assertEquals(53, response.getTotalCount());
        assertEquals(3, response.getPage());
        assertEquals(10, response.getSize());
        assertEquals(6, response.getTotalPages());
    }

    @Test
    void fromPageFirstPageIs1Indexed() {
        var content = List.of("one");
        var pageable = PageRequest.of(0, 5);
        Page<String> page = new PageImpl<>(content, pageable, 1);

        var response = PagedCollectionResponse.fromPage(page);

        assertEquals(1, response.getPage());
        assertEquals(5, response.getSize());
        assertEquals(1, response.getTotalPages());
    }

    @Test
    void fromListWrapsListWithTotalCountOnly() {
        var items = List.of("x", "y");

        var response = PagedCollectionResponse.fromList(items);

        assertEquals(items, response.getItems());
        assertEquals(2, response.getTotalCount());
        assertNull(response.getPage());
        assertNull(response.getSize());
        assertNull(response.getTotalPages());
    }

    @Test
    void fromListEmptyListReturnsTotalCountZero() {
        var response = PagedCollectionResponse.fromList(List.of());

        assertEquals(List.of(), response.getItems());
        assertEquals(0, response.getTotalCount());
        assertNull(response.getPage());
        assertNull(response.getSize());
        assertNull(response.getTotalPages());
    }

    @Test
    void fromListSerializedJsonOmitsPaginationKeys() throws Exception {
        // Spec §8: non-paginated collection responses include `items` and `totalCount` but
        // omit `page`, `size`, and `totalPages`. Inspect the raw JSON to make the contract
        // explicit (jsonPath().doesNotExist() also matches `null`, which was the buggy state).
        var response = PagedCollectionResponse.fromList(List.of("alpha", "beta"));
        String json = new ObjectMapper().writeValueAsString(response);
        assertThat(json).contains("\"items\"");
        assertThat(json).contains("\"totalCount\"");
        assertThat(json).doesNotContain("\"page\"");
        assertThat(json).doesNotContain("\"size\"");
        assertThat(json).doesNotContain("\"totalPages\"");
    }

    @Test
    void fromPageSerializedJsonIncludesAllPaginationKeys() throws Exception {
        // Sanity: the paginated factory must keep page/size/totalPages in the wire shape.
        Page<String> page = new PageImpl<>(List.of("x"), PageRequest.of(0, 10), 1);
        var response = PagedCollectionResponse.fromPage(page);
        String json = new ObjectMapper().writeValueAsString(response);
        assertThat(json).contains("\"items\"");
        assertThat(json).contains("\"totalCount\"");
        assertThat(json).contains("\"page\"");
        assertThat(json).contains("\"size\"");
        assertThat(json).contains("\"totalPages\"");
    }
}
