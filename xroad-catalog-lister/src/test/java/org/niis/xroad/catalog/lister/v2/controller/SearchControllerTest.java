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
package org.niis.xroad.catalog.lister.v2.controller;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.niis.xroad.catalog.lister.v2.dto.MemberSearchHit;
import org.niis.xroad.catalog.lister.v2.dto.SearchHit;
import org.niis.xroad.catalog.lister.v2.dto.ServiceSearchHit;
import org.niis.xroad.catalog.lister.v2.dto.SubsystemSearchHit;
import org.niis.xroad.catalog.lister.v2.service.SearchService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SearchController.class)
@Import({ApiExceptionHandler.class, DispatchExceptionHandler.class})
class SearchControllerTest {

    private static final String SEARCH_PATH = "/api/v2/search";
    private static final String Q = "q";
    private static final String QUERY_TEST = "test";
    private static final String JSON_ERROR = "$.error";
    private static final String JSON_MESSAGE = "$.message";
    private static final String JSON_STATUS = "$.status";
    private static final String BAD_REQUEST_ERROR = "BadRequest";
    private static final String METHOD_NOT_ALLOWED_ERROR = "MethodNotAllowed";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SearchService searchService;

    @Test
    void searchReturnsMixedTypePagedShape() throws Exception {
        SearchHit memberHit = new MemberSearchHit("GOV", "1234567-8", "Tax Authority", true);
        SearchHit subsystemHit = new SubsystemSearchHit("GOV", "1234567-8", "Tax Authority", "TaxServices");
        SearchHit serviceHit = new ServiceSearchHit("COM", "9876543-2", "Accounting Corp",
                "Finance", "getTaxReport", List.of("REST"));
        when(searchService.search(eq("tax"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(memberHit, subsystemHit, serviceHit),
                        PageRequest.of(0, 20), 3));

        mockMvc.perform(get(SEARCH_PATH).param(Q, "tax"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(3))
                .andExpect(jsonPath("$.items[0].type").value("member"))
                .andExpect(jsonPath("$.items[0].memberClass").value("GOV"))
                .andExpect(jsonPath("$.items[0].memberCode").value("1234567-8"))
                .andExpect(jsonPath("$.items[0].name").value("Tax Authority"))
                .andExpect(jsonPath("$.items[0].provider").value(true))
                .andExpect(jsonPath("$.items[1].type").value("subsystem"))
                .andExpect(jsonPath("$.items[1].memberName").value("Tax Authority"))
                .andExpect(jsonPath("$.items[1].subsystemCode").value("TaxServices"))
                .andExpect(jsonPath("$.items[2].type").value("service"))
                .andExpect(jsonPath("$.items[2].subsystemCode").value("Finance"))
                .andExpect(jsonPath("$.items[2].serviceCode").value("getTaxReport"))
                .andExpect(jsonPath("$.items[2].serviceTypes[0]").value("REST"))
                .andExpect(jsonPath("$.totalCount").value(3))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalPages").value(1));
    }

    @Test
    void searchSubsystemAndServiceRowsOmitIsProvider() throws Exception {
        SearchHit subsystemHit = new SubsystemSearchHit("GOV", "1234567-8", "Tax Authority", "TaxServices");
        SearchHit serviceHit = new ServiceSearchHit("COM", "9876543-2", "Accounting Corp",
                "Finance", "getTaxReport", List.of("REST"));
        when(searchService.search(eq("tax"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(subsystemHit, serviceHit), PageRequest.of(0, 20), 2));

        mockMvc.perform(get(SEARCH_PATH).param(Q, "tax"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].provider").doesNotExist())
                .andExpect(jsonPath("$.items[1].provider").doesNotExist());
    }

    @Test
    void searchMissingQueryParamReturns400() throws Exception {
        mockMvc.perform(get(SEARCH_PATH))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath(JSON_ERROR).value(BAD_REQUEST_ERROR))
                .andExpect(jsonPath(JSON_STATUS).value(400))
                .andExpect(jsonPath(JSON_MESSAGE).value("Query parameter 'q' is required"));
    }

    @Test
    void searchBlankQueryParamReturns400() throws Exception {
        when(searchService.search(eq("   "), any(Pageable.class)))
                .thenThrow(new BadRequestException(
                        "Query parameter 'q' must be at least 3 characters"));

        mockMvc.perform(get(SEARCH_PATH).param(Q, "   "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath(JSON_MESSAGE).value("Query parameter 'q' must be at least 3 characters"));
    }

    @Test
    void searchEmptyQueryParamReturns400() throws Exception {
        when(searchService.search(eq(""), any(Pageable.class)))
                .thenThrow(new BadRequestException(
                        "Query parameter 'q' must be at least 3 characters"));

        mockMvc.perform(get(SEARCH_PATH).param(Q, ""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath(JSON_MESSAGE).value("Query parameter 'q' must be at least 3 characters"));
    }

    @Test
    void searchTooShortQueryReturns400FromService() throws Exception {
        when(searchService.search(eq("ab"), any(Pageable.class)))
                .thenThrow(new BadRequestException(
                        "Query parameter 'q' must be at least 3 characters"));

        mockMvc.perform(get(SEARCH_PATH).param(Q, "ab"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath(JSON_ERROR).value(BAD_REQUEST_ERROR))
                .andExpect(jsonPath(JSON_MESSAGE).value(
                        Matchers.containsString("at least 3 characters")));
    }

    @Test
    void searchAppliesDefaultPageAndSize() throws Exception {
        when(searchService.search(eq(QUERY_TEST), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        mockMvc.perform(get(SEARCH_PATH).param(Q, QUERY_TEST))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(20));

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(searchService).search(eq(QUERY_TEST), captor.capture());
        assertThat(captor.getValue().getPageNumber()).isZero();
        assertThat(captor.getValue().getPageSize()).isEqualTo(20);
        assertThat(captor.getValue().getSort().isUnsorted()).isTrue();
    }

    @Test
    void searchPropagatesPaginationParams() throws Exception {
        when(searchService.search(eq(QUERY_TEST), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(2, 5), 30));

        mockMvc.perform(get(SEARCH_PATH).param(Q, QUERY_TEST).param("page", "3").param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(3))
                .andExpect(jsonPath("$.size").value(5))
                .andExpect(jsonPath("$.totalCount").value(30))
                .andExpect(jsonPath("$.totalPages").value(6));

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(searchService).search(eq(QUERY_TEST), captor.capture());
        assertThat(captor.getValue().getPageNumber()).isEqualTo(2);
        assertThat(captor.getValue().getPageSize()).isEqualTo(5);
    }

    @Test
    void searchRejectsNonIntegerPage() throws Exception {
        mockMvc.perform(get(SEARCH_PATH).param(Q, QUERY_TEST).param("page", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath(JSON_ERROR).value(BAD_REQUEST_ERROR))
                .andExpect(jsonPath(JSON_MESSAGE).value(Matchers.containsString("page")));
    }

    @Test
    void searchRejectsNonIntegerSize() throws Exception {
        mockMvc.perform(get(SEARCH_PATH).param(Q, QUERY_TEST).param("size", "twenty"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath(JSON_MESSAGE).value(Matchers.containsString("size")));
    }

    @Test
    void searchRejectsZeroPage() throws Exception {
        // PaginationUtil rejects a page below 1 → ApiExceptionHandler maps to 400.
        mockMvc.perform(get(SEARCH_PATH).param(Q, QUERY_TEST).param("page", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath(JSON_ERROR).value(BAD_REQUEST_ERROR));
    }

    @Test
    void searchRejectsNegativeSize() throws Exception {
        mockMvc.perform(get(SEARCH_PATH).param(Q, QUERY_TEST).param("size", "-5"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath(JSON_ERROR).value(BAD_REQUEST_ERROR));
    }

    @Test
    void searchRejectsOversizedPageSize() throws Exception {
        mockMvc.perform(get(SEARCH_PATH).param(Q, QUERY_TEST).param("size", "201"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath(JSON_ERROR).value(BAD_REQUEST_ERROR))
                .andExpect(jsonPath(JSON_MESSAGE).value(
                        Matchers.containsString("must not exceed 200")));
    }

    @Test
    void searchAcceptsMaxPageSizeBoundary() throws Exception {
        when(searchService.search(eq(QUERY_TEST), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 200), 0));

        mockMvc.perform(get(SEARCH_PATH).param(Q, QUERY_TEST).param("size", "200"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(200));
    }

    @Test
    void searchIgnoresUnsupportedSortByParam() throws Exception {
        // The search endpoint does not support sortBy/sortOrder. Sending them must not change the result;
        // the controller does not declare them, so Spring silently ignores them.
        when(searchService.search(eq(QUERY_TEST), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        mockMvc.perform(get(SEARCH_PATH).param(Q, QUERY_TEST)
                        .param("sortBy", "name").param("sortOrder", "desc"))
                .andExpect(status().isOk());

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(searchService).search(eq(QUERY_TEST), captor.capture());
        assertThat(captor.getValue().getSort().isUnsorted()).isTrue();
    }

    @Test
    void searchIgnoresUnsupportedIncludeRemovedParam() throws Exception {
        // Search always returns active entities only and does not support includeRemoved.
        when(searchService.search(eq(QUERY_TEST), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        mockMvc.perform(get(SEARCH_PATH).param(Q, QUERY_TEST).param("includeRemoved", "true"))
                .andExpect(status().isOk());

        // Service is invoked with q only; the controller never reads includeRemoved.
        verify(searchService).search(eq(QUERY_TEST), any(Pageable.class));
    }

    @Test
    void searchReturnsEmptyEnvelopeWhenNoMatches() throws Exception {
        when(searchService.search(eq("zzz"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        mockMvc.perform(get(SEARCH_PATH).param(Q, "zzz"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0))
                .andExpect(jsonPath("$.totalCount").value(0))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalPages").value(0));
    }

    @Test
    void postToSearchReturns405() throws Exception {
        mockMvc.perform(post(SEARCH_PATH))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath(JSON_STATUS).value(405))
                .andExpect(jsonPath(JSON_ERROR).value(METHOD_NOT_ALLOWED_ERROR))
                .andExpect(header().string("Allow", "GET"));
    }

    @Test
    void postToSearchUnderContextPathReturns405() throws Exception {
        mockMvc.perform(post("/catalog" + SEARCH_PATH).contextPath("/catalog"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath(JSON_STATUS).value(405))
                .andExpect(jsonPath(JSON_ERROR).value(METHOD_NOT_ALLOWED_ERROR))
                .andExpect(header().string("Allow", "GET"));
    }
}
