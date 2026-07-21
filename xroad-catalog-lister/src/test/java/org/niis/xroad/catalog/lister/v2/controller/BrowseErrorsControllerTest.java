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
import org.niis.xroad.catalog.lister.v2.dto.ErrorLogDto;
import org.niis.xroad.catalog.lister.v2.dto.MemberClassDto;
import org.niis.xroad.catalog.lister.v2.service.ErrorLogServiceV2;
import org.niis.xroad.catalog.lister.v2.service.MemberClassServiceV2;
import org.niis.xroad.catalog.lister.v2.service.MemberServiceV2;
import org.niis.xroad.catalog.lister.v2.service.ServiceServiceV2;
import org.niis.xroad.catalog.lister.v2.service.SubsystemServiceV2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(BrowseErrorsController.class)
@Import({V2ExceptionHandler.class, BrowseErrorsControllerTest.FixedClockConfig.class})
class BrowseErrorsControllerTest {

    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        Clock clock() {
            return Clock.fixed(Instant.parse("2026-05-07T00:00:00Z"), ZoneOffset.UTC);
        }
    }

    private static final LocalDate FIXED_TODAY = LocalDate.of(2026, 5, 7);

    private static final String PUB = "PUB";
    private static final String MEMBER_CODE = "14151328";
    private static final String SUBSYSTEM_CODE = "subsystem_a1";
    private static final String SERVICE_CODE = "getRandom";
    private static final String VERSION = "v1";
    private static final String NULL_LITERAL = "null";
    private static final String MISSING = "missing";

    private static final String SINCE_VALUE = "2024-01-01";
    private static final String UNTIL_VALUE = "2024-02-01";
    private static final String SINCE_PARAM = "since";
    private static final String UNTIL_PARAM = "until";

    private static final LocalDateTime SINCE_PARSED = LocalDateTime.of(2024, 1, 1, 0, 0);
    private static final LocalDateTime UNTIL_PARSED = LocalDateTime.of(2024, 2, 1, 0, 0);

    private static final String JSON_TOTAL_COUNT = "$.totalCount";
    private static final String JSON_ERROR = "$.error";
    private static final String JSON_STATUS = "$.status";
    private static final String JSON_MESSAGE = "$.message";
    private static final String BAD_REQUEST = "BadRequest";
    private static final String NOT_FOUND_ERROR = "NotFound";
    private static final String SAMPLE_MESSAGE = "boom";
    private static final String SAMPLE_CODE = "ERR_42";

    private static final String CATALOG_ROUTE = "/api/v2/browse/errors";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ErrorLogServiceV2 errorLogService;

    @MockBean
    private MemberClassServiceV2 memberClassService;

    @MockBean
    private MemberServiceV2 memberService;

    @MockBean
    private SubsystemServiceV2 subsystemService;

    @MockBean
    private ServiceServiceV2 serviceService;

    @Test
    void catalogErrorsHappyPathReturns200WithPaginatedShape() throws Exception {
        Page<ErrorLogDto> page = pageOf(errorDto(SINCE_PARSED));
        when(errorLogService.get(eq(null), eq(null), eq(null), eq(null), eq(null),
                eq(SINCE_PARSED), eq(UNTIL_PARSED), any(Pageable.class)))
                .thenReturn(page);

        mockMvc.perform(get(CATALOG_ROUTE).param(SINCE_PARAM, SINCE_VALUE).param(UNTIL_PARAM, UNTIL_VALUE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].message").value(SAMPLE_MESSAGE))
                .andExpect(jsonPath("$.items[0].code").value(SAMPLE_CODE))
                .andExpect(jsonPath(JSON_TOTAL_COUNT).value(1))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalPages").value(1));
    }

    @Test
    void catalogErrorsAppliesCreatedDescDefaultSort() throws Exception {
        Page<ErrorLogDto> page = pageOf();
        when(errorLogService.get(eq(null), eq(null), eq(null), eq(null), eq(null),
                eq(SINCE_PARSED), eq(UNTIL_PARSED), any(Pageable.class)))
                .thenReturn(page);

        mockMvc.perform(get(CATALOG_ROUTE).param(SINCE_PARAM, SINCE_VALUE).param(UNTIL_PARAM, UNTIL_VALUE))
                .andExpect(status().isOk());

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(errorLogService).get(eq(null), eq(null), eq(null), eq(null), eq(null),
                eq(SINCE_PARSED), eq(UNTIL_PARSED), captor.capture());
        Sort.Order primary = captor.getValue().getSort().iterator().next();
        assertThat(primary.getProperty()).isEqualTo("created");
        assertThat(primary.getDirection()).isEqualTo(Sort.Direction.DESC);
    }

    @Test
    void catalogErrorsExplicitSortOrderAscOverridesDefault() throws Exception {
        Page<ErrorLogDto> page = pageOf();
        when(errorLogService.get(eq(null), eq(null), eq(null), eq(null), eq(null),
                eq(SINCE_PARSED), eq(UNTIL_PARSED), any(Pageable.class)))
                .thenReturn(page);

        mockMvc.perform(get(CATALOG_ROUTE)
                        .param(SINCE_PARAM, SINCE_VALUE).param(UNTIL_PARAM, UNTIL_VALUE)
                        .param("sortOrder", "asc"))
                .andExpect(status().isOk());

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(errorLogService).get(eq(null), eq(null), eq(null), eq(null), eq(null),
                eq(SINCE_PARSED), eq(UNTIL_PARSED), captor.capture());
        Sort.Order primary = captor.getValue().getSort().iterator().next();
        assertThat(primary.getDirection()).isEqualTo(Sort.Direction.ASC);
    }

    @Test
    void catalogErrorsAllowsCodeAsSortField() throws Exception {
        Page<ErrorLogDto> page = pageOf();
        when(errorLogService.get(eq(null), eq(null), eq(null), eq(null), eq(null),
                eq(SINCE_PARSED), eq(UNTIL_PARSED), any(Pageable.class)))
                .thenReturn(page);

        mockMvc.perform(get(CATALOG_ROUTE)
                        .param(SINCE_PARAM, SINCE_VALUE).param(UNTIL_PARAM, UNTIL_VALUE)
                        .param("sortBy", "code"))
                .andExpect(status().isOk());

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(errorLogService).get(eq(null), eq(null), eq(null), eq(null), eq(null),
                eq(SINCE_PARSED), eq(UNTIL_PARSED), captor.capture());
        Sort.Order primary = captor.getValue().getSort().iterator().next();
        assertThat(primary.getProperty()).isEqualTo("code");
    }

    @Test
    void catalogErrorsRejectsDisallowedSortByWith400() throws Exception {
        mockMvc.perform(get(CATALOG_ROUTE)
                        .param(SINCE_PARAM, SINCE_VALUE).param(UNTIL_PARAM, UNTIL_VALUE)
                        .param("sortBy", "bogus"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath(JSON_STATUS).value(400))
                .andExpect(jsonPath(JSON_ERROR).value(BAD_REQUEST));
    }

    @Test
    void catalogErrorsSinceAfterUntilReturns400() throws Exception {
        mockMvc.perform(get(CATALOG_ROUTE)
                        .param(SINCE_PARAM, "2024-03-01")
                        .param(UNTIL_PARAM, "2024-02-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath(JSON_STATUS).value(400))
                .andExpect(jsonPath(JSON_ERROR).value(BAD_REQUEST))
                .andExpect(jsonPath(JSON_MESSAGE).value("'since' must not be after 'until'"));
    }

    @Test
    void catalogErrorsRejectsOffsetInput() throws Exception {
        mockMvc.perform(get(CATALOG_ROUTE)
                        .param(SINCE_PARAM, "2024-01-01T00:00:00+03:00")
                        .param(UNTIL_PARAM, UNTIL_VALUE))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath(JSON_MESSAGE).value(Matchers.containsString("yyyy-MM-dd")));
    }

    @Test
    void catalogErrorsRejectsDateTimeInput() throws Exception {
        mockMvc.perform(get(CATALOG_ROUTE)
                        .param(SINCE_PARAM, "2024-01-01T12:00:00")
                        .param(UNTIL_PARAM, UNTIL_VALUE))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath(JSON_MESSAGE).value(Matchers.containsString("yyyy-MM-dd")));
    }

    @Test
    void catalogErrorsRejectsZuluInput() throws Exception {
        mockMvc.perform(get(CATALOG_ROUTE)
                        .param(SINCE_PARAM, "2024-01-01T00:00:00Z")
                        .param(UNTIL_PARAM, UNTIL_VALUE))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath(JSON_MESSAGE).value(Matchers.containsString("yyyy-MM-dd")));
    }

    @Test
    void catalogErrorsPassesMidnightServerLocalToService() throws Exception {
        LocalDateTime expectedSince = LocalDate.of(2024, 1, 1).atStartOfDay();
        LocalDateTime expectedUntil = LocalDate.of(2024, 2, 1).atStartOfDay();
        when(errorLogService.get(any(), any(), any(), any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get(CATALOG_ROUTE).param(SINCE_PARAM, SINCE_VALUE).param(UNTIL_PARAM, UNTIL_VALUE))
                .andExpect(status().isOk());

        ArgumentCaptor<LocalDateTime> sinceCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> untilCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(errorLogService).get(any(), any(), any(), any(), any(),
                sinceCaptor.capture(), untilCaptor.capture(), any(Pageable.class));
        assertThat(sinceCaptor.getValue()).isEqualTo(expectedSince);
        assertThat(untilCaptor.getValue()).isEqualTo(expectedUntil);
    }

    @Test
    void catalogErrorsAppliesDefaultsWhenBothMissing() throws Exception {
        when(errorLogService.get(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        mockMvc.perform(get("/api/v2/browse/errors")).andExpect(status().isOk());

        ArgumentCaptor<LocalDateTime> sinceCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> untilCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(errorLogService).get(any(), any(), any(), any(), any(),
                sinceCaptor.capture(), untilCaptor.capture(), any(Pageable.class));
        // Window = today only: half-open [today 00:00, tomorrow 00:00).
        assertThat(sinceCaptor.getValue()).isEqualTo(FIXED_TODAY.atStartOfDay());
        assertThat(untilCaptor.getValue()).isEqualTo(FIXED_TODAY.plusDays(1).atStartOfDay());
    }

    @Test
    void catalogErrorsAppliesUntilDefaultWhenOnlySincePresent() throws Exception {
        when(errorLogService.get(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        mockMvc.perform(get("/api/v2/browse/errors").param("since", "2026-05-01"))
                .andExpect(status().isOk());

        ArgumentCaptor<LocalDateTime> sinceCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> untilCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(errorLogService).get(any(), any(), any(), any(), any(),
                sinceCaptor.capture(), untilCaptor.capture(), any(Pageable.class));
        assertThat(sinceCaptor.getValue()).isEqualTo(LocalDate.of(2026, 5, 1).atStartOfDay());
        assertThat(untilCaptor.getValue()).isEqualTo(FIXED_TODAY.plusDays(1).atStartOfDay());
    }

    @Test
    void memberClassErrorsHappyPathDispatchesWithMemberClassOnly() throws Exception {
        when(memberClassService.getByCode(PUB)).thenReturn(memberClassDto());
        when(errorLogService.get(eq(PUB), eq(null), eq(null), eq(null), eq(null),
                eq(SINCE_PARSED), eq(UNTIL_PARSED), any(Pageable.class)))
                .thenReturn(pageOf(errorDto(SINCE_PARSED)));

        mockMvc.perform(get(memberClassRoute(PUB))
                        .param(SINCE_PARAM, SINCE_VALUE).param(UNTIL_PARAM, UNTIL_VALUE))
                .andExpect(status().isOk())
                .andExpect(jsonPath(JSON_TOTAL_COUNT).value(1));
    }

    @Test
    void memberClassErrorsUnknownMemberClassReturns404() throws Exception {
        when(memberClassService.getByCode(MISSING)).thenReturn(null);

        mockMvc.perform(get(memberClassRoute(MISSING))
                        .param(SINCE_PARAM, SINCE_VALUE).param(UNTIL_PARAM, UNTIL_VALUE))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath(JSON_STATUS).value(404))
                .andExpect(jsonPath(JSON_ERROR).value(NOT_FOUND_ERROR))
                .andExpect(jsonPath(JSON_MESSAGE).value("Member class 'missing' not found"));
    }

    @Test
    void memberErrorsHappyPathDispatchesWithMemberClassAndCode() throws Exception {
        when(memberClassService.getByCode(PUB)).thenReturn(memberClassDto());
        when(memberService.existsActive(PUB, MEMBER_CODE)).thenReturn(true);
        when(errorLogService.get(eq(PUB), eq(MEMBER_CODE), eq(null), eq(null), eq(null),
                eq(SINCE_PARSED), eq(UNTIL_PARSED), any(Pageable.class)))
                .thenReturn(pageOf(errorDto(SINCE_PARSED)));

        mockMvc.perform(get(memberRoute(PUB, MEMBER_CODE))
                        .param(SINCE_PARAM, SINCE_VALUE).param(UNTIL_PARAM, UNTIL_VALUE))
                .andExpect(status().isOk())
                .andExpect(jsonPath(JSON_TOTAL_COUNT).value(1));
    }

    @Test
    void memberErrorsUnknownMemberClassReturns404BeforeMemberLookup() throws Exception {
        when(memberClassService.getByCode(MISSING)).thenReturn(null);

        mockMvc.perform(get(memberRoute(MISSING, MEMBER_CODE))
                        .param(SINCE_PARAM, SINCE_VALUE).param(UNTIL_PARAM, UNTIL_VALUE))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath(JSON_MESSAGE).value("Member class 'missing' not found"));
    }

    @Test
    void memberErrorsRemovedMemberReturns404() throws Exception {
        when(memberClassService.getByCode(PUB)).thenReturn(memberClassDto());
        when(memberService.existsActive(PUB, MEMBER_CODE)).thenReturn(false);

        mockMvc.perform(get(memberRoute(PUB, MEMBER_CODE))
                        .param(SINCE_PARAM, SINCE_VALUE).param(UNTIL_PARAM, UNTIL_VALUE))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath(JSON_MESSAGE).value("Member 'PUB/14151328' not found"));
    }

    @Test
    void subsystemErrorsHappyPath() throws Exception {
        when(memberClassService.getByCode(PUB)).thenReturn(memberClassDto());
        when(memberService.existsActive(PUB, MEMBER_CODE)).thenReturn(true);
        when(subsystemService.existsActive(PUB, MEMBER_CODE, SUBSYSTEM_CODE)).thenReturn(true);
        when(errorLogService.get(eq(PUB), eq(MEMBER_CODE), eq(SUBSYSTEM_CODE), eq(null), eq(null),
                eq(SINCE_PARSED), eq(UNTIL_PARSED), any(Pageable.class)))
                .thenReturn(pageOf(errorDto(SINCE_PARSED)));

        mockMvc.perform(get(subsystemRoute(PUB, MEMBER_CODE, SUBSYSTEM_CODE))
                        .param(SINCE_PARAM, SINCE_VALUE).param(UNTIL_PARAM, UNTIL_VALUE))
                .andExpect(status().isOk())
                .andExpect(jsonPath(JSON_TOTAL_COUNT).value(1));
    }

    @Test
    void subsystemErrorsRemovedSubsystemReturns404() throws Exception {
        when(memberClassService.getByCode(PUB)).thenReturn(memberClassDto());
        when(memberService.existsActive(PUB, MEMBER_CODE)).thenReturn(true);
        when(subsystemService.existsActive(PUB, MEMBER_CODE, SUBSYSTEM_CODE)).thenReturn(false);

        mockMvc.perform(get(subsystemRoute(PUB, MEMBER_CODE, SUBSYSTEM_CODE))
                        .param(SINCE_PARAM, SINCE_VALUE).param(UNTIL_PARAM, UNTIL_VALUE))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath(JSON_MESSAGE)
                        .value("Subsystem 'PUB/14151328/subsystem_a1' not found"));
    }

    @Test
    void serviceErrorsHappyPath() throws Exception {
        when(memberClassService.getByCode(PUB)).thenReturn(memberClassDto());
        when(memberService.existsActive(PUB, MEMBER_CODE)).thenReturn(true);
        when(subsystemService.existsActive(PUB, MEMBER_CODE, SUBSYSTEM_CODE)).thenReturn(true);
        when(serviceService.existsActive(PUB, MEMBER_CODE, SUBSYSTEM_CODE, SERVICE_CODE))
                .thenReturn(true);
        when(errorLogService.get(eq(PUB), eq(MEMBER_CODE), eq(SUBSYSTEM_CODE), eq(SERVICE_CODE), eq(null),
                eq(SINCE_PARSED), eq(UNTIL_PARSED), any(Pageable.class)))
                .thenReturn(pageOf(errorDto(SINCE_PARSED)));

        mockMvc.perform(get(serviceRoute(PUB, MEMBER_CODE, SUBSYSTEM_CODE, SERVICE_CODE))
                        .param(SINCE_PARAM, SINCE_VALUE).param(UNTIL_PARAM, UNTIL_VALUE))
                .andExpect(status().isOk())
                .andExpect(jsonPath(JSON_TOTAL_COUNT).value(1));
    }

    @Test
    void serviceErrorsUnknownServiceReturns404() throws Exception {
        when(memberClassService.getByCode(PUB)).thenReturn(memberClassDto());
        when(memberService.existsActive(PUB, MEMBER_CODE)).thenReturn(true);
        when(subsystemService.existsActive(PUB, MEMBER_CODE, SUBSYSTEM_CODE)).thenReturn(true);
        when(serviceService.existsActive(PUB, MEMBER_CODE, SUBSYSTEM_CODE, MISSING)).thenReturn(false);

        mockMvc.perform(get(serviceRoute(PUB, MEMBER_CODE, SUBSYSTEM_CODE, MISSING))
                        .param(SINCE_PARAM, SINCE_VALUE).param(UNTIL_PARAM, UNTIL_VALUE))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath(JSON_MESSAGE)
                        .value("Service 'PUB/14151328/subsystem_a1/missing' not found"));
    }

    @Test
    void versionErrorsHappyPathDispatchesWithRawVersion() throws Exception {
        when(memberClassService.getByCode(PUB)).thenReturn(memberClassDto());
        when(memberService.existsActive(PUB, MEMBER_CODE)).thenReturn(true);
        when(subsystemService.existsActive(PUB, MEMBER_CODE, SUBSYSTEM_CODE)).thenReturn(true);
        when(serviceService.existsActive(PUB, MEMBER_CODE, SUBSYSTEM_CODE, SERVICE_CODE))
                .thenReturn(true);
        when(serviceService.existsActiveVersion(PUB, MEMBER_CODE, SUBSYSTEM_CODE, SERVICE_CODE, VERSION))
                .thenReturn(true);
        when(errorLogService.get(eq(PUB), eq(MEMBER_CODE), eq(SUBSYSTEM_CODE), eq(SERVICE_CODE), eq(VERSION),
                eq(SINCE_PARSED), eq(UNTIL_PARSED), any(Pageable.class)))
                .thenReturn(pageOf(errorDto(SINCE_PARSED)));

        mockMvc.perform(get(versionRoute(PUB, MEMBER_CODE, SUBSYSTEM_CODE, SERVICE_CODE, VERSION))
                        .param(SINCE_PARAM, SINCE_VALUE).param(UNTIL_PARAM, UNTIL_VALUE))
                .andExpect(status().isOk())
                .andExpect(jsonPath(JSON_TOTAL_COUNT).value(1));
    }

    @Test
    void versionErrorsNullSentinelPassesRawLiteralToErrorService() throws Exception {
        when(memberClassService.getByCode(PUB)).thenReturn(memberClassDto());
        when(memberService.existsActive(PUB, MEMBER_CODE)).thenReturn(true);
        when(subsystemService.existsActive(PUB, MEMBER_CODE, SUBSYSTEM_CODE)).thenReturn(true);
        when(serviceService.existsActive(PUB, MEMBER_CODE, SUBSYSTEM_CODE, SERVICE_CODE))
                .thenReturn(true);
        when(serviceService.existsActiveVersion(PUB, MEMBER_CODE, SUBSYSTEM_CODE, SERVICE_CODE, NULL_LITERAL))
                .thenReturn(true);
        when(errorLogService.get(eq(PUB), eq(MEMBER_CODE), eq(SUBSYSTEM_CODE), eq(SERVICE_CODE), eq(NULL_LITERAL),
                eq(SINCE_PARSED), eq(UNTIL_PARSED), any(Pageable.class)))
                .thenReturn(pageOf());

        mockMvc.perform(get(versionRoute(PUB, MEMBER_CODE, SUBSYSTEM_CODE, SERVICE_CODE, NULL_LITERAL))
                        .param(SINCE_PARAM, SINCE_VALUE).param(UNTIL_PARAM, UNTIL_VALUE))
                .andExpect(status().isOk());

        verify(errorLogService).get(eq(PUB), eq(MEMBER_CODE), eq(SUBSYSTEM_CODE), eq(SERVICE_CODE), eq(NULL_LITERAL),
                eq(SINCE_PARSED), eq(UNTIL_PARSED), any(Pageable.class));
    }

    @Test
    void versionErrorsUnknownVersionReturns404() throws Exception {
        when(memberClassService.getByCode(PUB)).thenReturn(memberClassDto());
        when(memberService.existsActive(PUB, MEMBER_CODE)).thenReturn(true);
        when(subsystemService.existsActive(PUB, MEMBER_CODE, SUBSYSTEM_CODE)).thenReturn(true);
        when(serviceService.existsActive(PUB, MEMBER_CODE, SUBSYSTEM_CODE, SERVICE_CODE))
                .thenReturn(true);
        when(serviceService.existsActiveVersion(PUB, MEMBER_CODE, SUBSYSTEM_CODE, SERVICE_CODE, "v9")).thenReturn(false);

        mockMvc.perform(get(versionRoute(PUB, MEMBER_CODE, SUBSYSTEM_CODE, SERVICE_CODE, "v9"))
                        .param(SINCE_PARAM, SINCE_VALUE).param(UNTIL_PARAM, UNTIL_VALUE))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath(JSON_MESSAGE)
                        .value("Service version 'PUB/14151328/subsystem_a1/getRandom/v9' not found"));
    }

    @Test
    void errorsRangeOver90DaysIsRejectedWith400() throws Exception {
        mockMvc.perform(get("/api/v2/browse/errors")
                        .param("since", "2025-01-01")
                        .param("until", "2025-06-01"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void errorsRangeOfExactly90DaysIsAccepted() throws Exception {
        when(errorLogService.get(any(), any(), any(), any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        mockMvc.perform(get("/api/v2/browse/errors")
                        .param("since", "2025-01-01")
                        .param("until", "2025-04-01"))
                .andExpect(status().isOk());
    }

    private static String memberClassRoute(String mc) {
        return "/api/v2/browse/member-classes/" + mc + "/errors";
    }

    private static String memberRoute(String mc, String mcode) {
        return "/api/v2/browse/member-classes/" + mc + "/members/" + mcode + "/errors";
    }

    private static String subsystemRoute(String mc, String mcode, String ss) {
        return "/api/v2/browse/member-classes/" + mc + "/members/" + mcode + "/subsystems/" + ss + "/errors";
    }

    private static String serviceRoute(String mc, String mcode, String ss, String sc) {
        return subsystemRoute(mc, mcode, ss).replace("/errors", "/services/" + sc + "/errors");
    }

    private static String versionRoute(String mc, String mcode, String ss, String sc, String v) {
        return serviceRoute(mc, mcode, ss, sc).replace("/errors", "/versions/" + v + "/errors");
    }

    private static Page<ErrorLogDto> pageOf(ErrorLogDto... rows) {
        List<ErrorLogDto> list = List.of(rows);
        return new PageImpl<>(list, PageRequest.of(0, 20), list.size());
    }

    private static ErrorLogDto errorDto(LocalDateTime created) {
        return ErrorLogDto.builder()
                .message(SAMPLE_MESSAGE)
                .code(SAMPLE_CODE)
                .memberClass(PUB)
                .memberCode(MEMBER_CODE)
                .subsystemCode(SUBSYSTEM_CODE)
                .serviceCode(SERVICE_CODE)
                .serviceVersion(VERSION)
                .created(created)
                .build();
    }

    private static MemberClassDto memberClassDto() {
        return MemberClassDto.builder().code(PUB).description("Public").memberCount(1).build();
    }
}
