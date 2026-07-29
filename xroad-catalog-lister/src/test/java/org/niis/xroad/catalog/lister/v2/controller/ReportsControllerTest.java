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
import org.niis.xroad.catalog.lister.v2.dto.ChangeLogBucketDto;
import org.niis.xroad.catalog.lister.v2.dto.ChangeLogBucketsDto;
import org.niis.xroad.catalog.lister.v2.dto.ChangeLogDayDto;
import org.niis.xroad.catalog.lister.v2.dto.ChangeLogMemberItemDto;
import org.niis.xroad.catalog.lister.v2.dto.ChangeLogServiceItemDto;
import org.niis.xroad.catalog.lister.v2.dto.ChangeLogSubsystemItemDto;
import org.niis.xroad.catalog.lister.v2.dto.ServiceStatisticsRowDto;
import org.niis.xroad.catalog.lister.v2.service.ReportServiceV2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ReportsController.class)
@Import({V2ExceptionHandler.class, V2DispatchExceptionHandler.class, ReportsControllerTest.FixedClockConfig.class})
class ReportsControllerTest {

    private static final String STATS_PATH = "/api/v2/reports/service-statistics";
    private static final String CHANGES_PATH = "/api/v2/reports/changes";
    private static final String SINCE = "since";
    private static final String UNTIL = "until";
    private static final String SINCE_2026_04_01 = "2026-04-01";
    private static final String UNTIL_2026_04_03 = "2026-04-03";
    private static final LocalDate SINCE_DATE = LocalDate.of(2026, 4, 1);
    private static final LocalDate UNTIL_DATE = LocalDate.of(2026, 4, 3);
    private static final LocalDate FIXED_TODAY = LocalDate.of(2026, 5, 7);
    private static final String JSON_ERROR = "$.error";
    private static final String JSON_MESSAGE = "$.message";
    private static final String JSON_STATUS = "$.status";
    private static final String BAD_REQUEST_ERROR = "BadRequest";
    private static final String METHOD_NOT_ALLOWED_ERROR = "MethodNotAllowed";

    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        Clock clock() {
            return Clock.fixed(Instant.parse("2026-05-07T00:00:00Z"), ZoneOffset.UTC);
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ReportServiceV2 reportService;

    @Test
    void serviceStatisticsReturnsItemsWithoutPaginationMetadata() throws Exception {
        ServiceStatisticsRowDto day1 = ServiceStatisticsRowDto.builder()
                .date(LocalDate.of(2026, 4, 1))
                .soapServices(120).restServices(85).openApiServices(43).build();
        ServiceStatisticsRowDto day2 = ServiceStatisticsRowDto.builder()
                .date(LocalDate.of(2026, 4, 2))
                .soapServices(121).restServices(85).openApiServices(44).build();
        when(reportService.serviceStatistics(eq(SINCE_DATE), eq(UNTIL_DATE)))
                .thenReturn(List.of(day1, day2));

        mockMvc.perform(get(STATS_PATH).param(SINCE, SINCE_2026_04_01).param(UNTIL, UNTIL_2026_04_03))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].date").value("2026-04-01"))
                .andExpect(jsonPath("$.items[0].soapServices").value(120))
                .andExpect(jsonPath("$.items[0].restServices").value(85))
                .andExpect(jsonPath("$.items[0].openApiServices").value(43))
                .andExpect(jsonPath("$.items[1].date").value("2026-04-02"))
                .andExpect(jsonPath("$.totalCount").value(2))
                // Service-statistics responses do not include pagination metadata.
                .andExpect(content().string(not(containsString("\"page\""))))
                .andExpect(content().string(not(containsString("\"size\""))))
                .andExpect(content().string(not(containsString("\"totalPages\""))));
    }

    @Test
    void serviceStatisticsAppliesDefaultsWhenSinceMissing() throws Exception {
        when(reportService.serviceStatistics(any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(List.of());

        mockMvc.perform(get(STATS_PATH).param(UNTIL, "2026-05-07"))
                .andExpect(status().isOk());

        ArgumentCaptor<LocalDate> sinceCaptor = ArgumentCaptor.forClass(LocalDate.class);
        ArgumentCaptor<LocalDate> untilCaptor = ArgumentCaptor.forClass(LocalDate.class);
        verify(reportService).serviceStatistics(sinceCaptor.capture(), untilCaptor.capture());
        assertThat(untilCaptor.getValue()).isEqualTo(LocalDate.of(2026, 5, 7));
        assertThat(sinceCaptor.getValue()).isEqualTo(LocalDate.of(2026, 4, 30));
    }

    @Test
    void serviceStatisticsAppliesDefaultsWhenUntilMissing() throws Exception {
        when(reportService.serviceStatistics(any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(List.of());

        mockMvc.perform(get(STATS_PATH).param(SINCE, "2026-04-01"))
                .andExpect(status().isOk());

        ArgumentCaptor<LocalDate> untilCaptor = ArgumentCaptor.forClass(LocalDate.class);
        verify(reportService).serviceStatistics(any(LocalDate.class), untilCaptor.capture());
        assertThat(untilCaptor.getValue()).isEqualTo(FIXED_TODAY.plusDays(1));
    }

    @Test
    void serviceStatisticsAppliesDefaultsWhenBothMissing() throws Exception {
        when(reportService.serviceStatistics(any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(List.of());

        mockMvc.perform(get(STATS_PATH))
                .andExpect(status().isOk());

        ArgumentCaptor<LocalDate> sinceCaptor = ArgumentCaptor.forClass(LocalDate.class);
        ArgumentCaptor<LocalDate> untilCaptor = ArgumentCaptor.forClass(LocalDate.class);
        verify(reportService).serviceStatistics(sinceCaptor.capture(), untilCaptor.capture());
        assertThat(untilCaptor.getValue()).isEqualTo(FIXED_TODAY.plusDays(1));
        assertThat(sinceCaptor.getValue()).isEqualTo(FIXED_TODAY.minusDays(6));
    }

    @Test
    void serviceStatisticsRejectsMalformedSince() throws Exception {
        mockMvc.perform(get(STATS_PATH)
                        .param(SINCE, "2026/04/30").param(UNTIL, "2026-05-07"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath(JSON_MESSAGE).value(Matchers.containsString("yyyy-MM-dd")));
    }

    @Test
    void serviceStatisticsRejectsMalformedDate() throws Exception {
        mockMvc.perform(get(STATS_PATH).param(SINCE, "not-a-date").param(UNTIL, UNTIL_2026_04_03))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath(JSON_ERROR).value(BAD_REQUEST_ERROR))
                .andExpect(jsonPath(JSON_MESSAGE).value(Matchers.containsString("yyyy-MM-dd")));
    }

    @Test
    void serviceStatisticsRejectsOffsetInput() throws Exception {
        mockMvc.perform(get(STATS_PATH)
                        .param(SINCE, "2026-04-10T00:00:00+03:00")
                        .param(UNTIL, UNTIL_2026_04_03))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath(JSON_ERROR).value(BAD_REQUEST_ERROR))
                .andExpect(jsonPath(JSON_MESSAGE).value(Matchers.containsString("yyyy-MM-dd")));
    }

    @Test
    void serviceStatisticsRejectsDateTimeInput() throws Exception {
        mockMvc.perform(get(STATS_PATH)
                        .param(SINCE, "2026-04-01T12:00:00")
                        .param(UNTIL, UNTIL_2026_04_03))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath(JSON_ERROR).value(BAD_REQUEST_ERROR))
                .andExpect(jsonPath(JSON_MESSAGE).value(Matchers.containsString("yyyy-MM-dd")));
    }

    @Test
    void serviceStatisticsRejectsZuluInput() throws Exception {
        mockMvc.perform(get(STATS_PATH)
                        .param(SINCE, "2026-04-10T00:00:00Z")
                        .param(UNTIL, UNTIL_2026_04_03))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath(JSON_ERROR).value(BAD_REQUEST_ERROR))
                .andExpect(jsonPath(JSON_MESSAGE).value(Matchers.containsString("yyyy-MM-dd")));
    }

    @Test
    void serviceStatisticsPassesParsedDateToService() throws Exception {
        LocalDate expectedSince = LocalDate.of(2026, 4, 1);
        LocalDate expectedUntil = LocalDate.of(2026, 4, 3);
        when(reportService.serviceStatistics(any(), any())).thenReturn(List.of());

        mockMvc.perform(get(STATS_PATH).param(SINCE, "2026-04-01").param(UNTIL, "2026-04-03"))
                .andExpect(status().isOk());

        ArgumentCaptor<LocalDate> sinceCaptor = ArgumentCaptor.forClass(LocalDate.class);
        ArgumentCaptor<LocalDate> untilCaptor = ArgumentCaptor.forClass(LocalDate.class);
        verify(reportService).serviceStatistics(sinceCaptor.capture(), untilCaptor.capture());
        assertThat(sinceCaptor.getValue()).isEqualTo(expectedSince);
        assertThat(untilCaptor.getValue()).isEqualTo(expectedUntil);
    }

    @Test
    void serviceStatisticsRejectsSinceAfterUntil() throws Exception {
        when(reportService.serviceStatistics(any(LocalDate.class), any(LocalDate.class)))
                .thenThrow(new IllegalArgumentException("'since' must not be after 'until'"));

        mockMvc.perform(get(STATS_PATH).param(SINCE, "2026-04-05").param(UNTIL, SINCE_2026_04_01))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath(JSON_ERROR).value(BAD_REQUEST_ERROR))
                .andExpect(jsonPath(JSON_MESSAGE).value(Matchers.containsString("must not be after")));
    }

    @Test
    void serviceStatisticsAcceptsSinceEqualsUntil() throws Exception {
        // since == until is an accepted empty window.
        when(reportService.serviceStatistics(eq(SINCE_DATE), eq(SINCE_DATE)))
                .thenReturn(List.of());

        mockMvc.perform(get(STATS_PATH).param(SINCE, SINCE_2026_04_01).param(UNTIL, SINCE_2026_04_01))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test
    void serviceStatisticsRejectsRangeAbove90Days() throws Exception {
        when(reportService.serviceStatistics(any(LocalDate.class), any(LocalDate.class)))
                .thenThrow(new IllegalArgumentException(
                        "Date range must not exceed 90 days (was 91 days)"));

        mockMvc.perform(get(STATS_PATH).param(SINCE, "2026-01-01").param(UNTIL, "2026-04-02"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath(JSON_ERROR).value(BAD_REQUEST_ERROR))
                .andExpect(jsonPath(JSON_MESSAGE).value(Matchers.containsString("90 days")));
    }

    @Test
    void serviceStatisticsAcceptsExactly90DayRange() throws Exception {
        // Exactly 90 days is allowed; only > 90 is rejected.
        LocalDate since = LocalDate.of(2026, 1, 1);
        LocalDate until = LocalDate.of(2026, 4, 1);
        when(reportService.serviceStatistics(eq(since), eq(until)))
                .thenReturn(List.of());

        mockMvc.perform(get(STATS_PATH).param(SINCE, "2026-01-01").param(UNTIL, "2026-04-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0))
                .andExpect(jsonPath("$.totalCount").value(0));

        verify(reportService).serviceStatistics(eq(since), eq(until));
    }

    @Test
    void serviceStatisticsIgnoresUnsupportedParams() throws Exception {
        // page/size/sortBy/sortOrder/includeRemoved have no defined effect
        // on /service-statistics; the controller does not declare them and Spring ignores them.
        when(reportService.serviceStatistics(eq(SINCE_DATE), eq(UNTIL_DATE)))
                .thenReturn(List.of());

        mockMvc.perform(get(STATS_PATH)
                        .param(SINCE, SINCE_2026_04_01).param(UNTIL, UNTIL_2026_04_03)
                        .param("page", "5").param("size", "100")
                        .param("sortBy", "date").param("sortOrder", "desc")
                        .param("includeRemoved", "true"))
                .andExpect(status().isOk());

        verify(reportService).serviceStatistics(eq(SINCE_DATE), eq(UNTIL_DATE));
    }

    @Test
    void serviceStatisticsReturnsEmptyEnvelopeWhenNoData() throws Exception {
        when(reportService.serviceStatistics(eq(SINCE_DATE), eq(UNTIL_DATE)))
                .thenReturn(List.of());

        mockMvc.perform(get(STATS_PATH).param(SINCE, SINCE_2026_04_01).param(UNTIL, UNTIL_2026_04_03))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0))
                .andExpect(jsonPath("$.totalCount").value(0))
                .andExpect(content().string(not(containsString("\"page\""))))
                .andExpect(content().string(not(containsString("\"size\""))))
                .andExpect(content().string(not(containsString("\"totalPages\""))));
    }

    @Test
    void postToServiceStatisticsReturns405() throws Exception {
        mockMvc.perform(post(STATS_PATH))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath(JSON_STATUS).value(405))
                .andExpect(jsonPath(JSON_ERROR).value(METHOD_NOT_ALLOWED_ERROR))
                .andExpect(header().string("Allow", "GET"));
    }

    @Test
    void changesReturnsPaginatedDayBuckets() throws Exception {
        ChangeLogDayDto day1 = ChangeLogDayDto.builder()
                .date(LocalDate.of(2026, 4, 1))
                .created(buckets(
                        List.of(memberItem("GOV", "1234567-8", "Tax Authority")),
                        List.of(),
                        List.of()))
                .modified(buckets(List.of(), List.of(), List.of()))
                .removed(buckets(List.of(), List.of(), List.of()))
                .build();
        ChangeLogDayDto day2 = ChangeLogDayDto.builder()
                .date(LocalDate.of(2026, 4, 3))
                .created(buckets(List.of(), List.of(), List.of()))
                .modified(buckets(List.of(), List.of(), List.of()))
                .removed(buckets(List.of(), List.of(),
                        List.of(serviceItem("GOV", "1234567-8", "Tax Authority", "OldSystem",
                                "legacyLookup", null, "SOAP"))))
                .build();
        // totalCount = 2 (number of days WITH changes); calendar range covers 2026-04-01..04-04.
        when(reportService.changeLog(eq(SINCE_DATE), eq(LocalDate.of(2026, 4, 5)),
                any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(day1, day2), PageRequest.of(0, 20), 2));

        mockMvc.perform(get(CHANGES_PATH)
                        .param(SINCE, SINCE_2026_04_01).param(UNTIL, "2026-04-05"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].date").value("2026-04-01"))
                .andExpect(jsonPath("$.items[0].created.members.count").value(1))
                .andExpect(jsonPath("$.items[0].created.members.items[0].memberClass").value("GOV"))
                .andExpect(jsonPath("$.items[0].created.members.items[0].memberCode").value("1234567-8"))
                .andExpect(jsonPath("$.items[0].created.members.items[0].name").value("Tax Authority"))
                .andExpect(jsonPath("$.items[0].created.subsystems.count").value(0))
                .andExpect(jsonPath("$.items[0].created.subsystems.items.length()").value(0))
                .andExpect(jsonPath("$.items[0].created.services.count").value(0))
                .andExpect(jsonPath("$.items[0].modified.members.count").value(0))
                .andExpect(jsonPath("$.items[0].removed.members.count").value(0))
                .andExpect(jsonPath("$.items[1].date").value("2026-04-03"))
                .andExpect(jsonPath("$.items[1].removed.services.count").value(1))
                .andExpect(jsonPath("$.items[1].removed.services.items[0].serviceCode").value("legacyLookup"))
                .andExpect(jsonPath("$.items[1].removed.services.items[0].serviceVersion").value(
                        Matchers.nullValue()))
                .andExpect(jsonPath("$.items[1].removed.services.items[0].serviceType").value("SOAP"))
                .andExpect(jsonPath("$.totalCount").value(2))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalPages").value(1));
    }

    private static ChangeLogBucketsDto buckets(List<ChangeLogMemberItemDto> members,
                                               List<ChangeLogSubsystemItemDto> subsystems,
                                               List<ChangeLogServiceItemDto> services) {
        return ChangeLogBucketsDto.builder()
                .members(ChangeLogBucketDto.<ChangeLogMemberItemDto>builder()
                        .count(members.size()).items(members).build())
                .subsystems(ChangeLogBucketDto.<ChangeLogSubsystemItemDto>builder()
                        .count(subsystems.size()).items(subsystems).build())
                .services(ChangeLogBucketDto.<ChangeLogServiceItemDto>builder()
                        .count(services.size()).items(services).build())
                .build();
    }

    private static ChangeLogMemberItemDto memberItem(String memberClass, String memberCode, String name) {
        return ChangeLogMemberItemDto.builder()
                .memberClass(memberClass).memberCode(memberCode).name(name).build();
    }

    private static ChangeLogServiceItemDto serviceItem(String memberClass, String memberCode,
                                                       String memberName, String subsystemCode,
                                                       String serviceCode, String serviceVersion,
                                                       String serviceType) {
        return ChangeLogServiceItemDto.builder()
                .memberClass(memberClass).memberCode(memberCode).memberName(memberName)
                .subsystemCode(subsystemCode).serviceCode(serviceCode)
                .serviceVersion(serviceVersion).serviceType(serviceType).build();
    }

    @Test
    void changesAppliesDefaultsWhenSinceMissing() throws Exception {
        when(reportService.changeLog(any(LocalDate.class), any(LocalDate.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        mockMvc.perform(get(CHANGES_PATH).param(UNTIL, "2026-05-07"))
                .andExpect(status().isOk());

        ArgumentCaptor<LocalDate> sinceCaptor = ArgumentCaptor.forClass(LocalDate.class);
        ArgumentCaptor<LocalDate> untilCaptor = ArgumentCaptor.forClass(LocalDate.class);
        verify(reportService).changeLog(sinceCaptor.capture(), untilCaptor.capture(), any(Pageable.class));
        assertThat(untilCaptor.getValue()).isEqualTo(LocalDate.of(2026, 5, 7));
        assertThat(sinceCaptor.getValue()).isEqualTo(LocalDate.of(2026, 4, 30));
    }

    @Test
    void changesAppliesDefaultsWhenUntilMissing() throws Exception {
        when(reportService.changeLog(any(LocalDate.class), any(LocalDate.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        mockMvc.perform(get(CHANGES_PATH).param(SINCE, "2026-04-01"))
                .andExpect(status().isOk());

        ArgumentCaptor<LocalDate> untilCaptor = ArgumentCaptor.forClass(LocalDate.class);
        verify(reportService).changeLog(any(LocalDate.class), untilCaptor.capture(), any(Pageable.class));
        assertThat(untilCaptor.getValue()).isEqualTo(FIXED_TODAY.plusDays(1));
    }

    @Test
    void changesAppliesDefaultsWhenBothMissing() throws Exception {
        when(reportService.changeLog(any(LocalDate.class), any(LocalDate.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        mockMvc.perform(get(CHANGES_PATH))
                .andExpect(status().isOk());

        ArgumentCaptor<LocalDate> sinceCaptor = ArgumentCaptor.forClass(LocalDate.class);
        ArgumentCaptor<LocalDate> untilCaptor = ArgumentCaptor.forClass(LocalDate.class);
        verify(reportService).changeLog(sinceCaptor.capture(), untilCaptor.capture(), any(Pageable.class));
        assertThat(untilCaptor.getValue()).isEqualTo(FIXED_TODAY.plusDays(1));
        assertThat(sinceCaptor.getValue()).isEqualTo(FIXED_TODAY.minusDays(6));
    }

    @Test
    void changesRejectsMalformedSince() throws Exception {
        mockMvc.perform(get(CHANGES_PATH).param(SINCE, "tomorrow").param(UNTIL, UNTIL_2026_04_03))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath(JSON_MESSAGE).value(Matchers.containsString("yyyy-MM-dd")));
    }

    @Test
    void changesRejectsOffsetInput() throws Exception {
        mockMvc.perform(get(CHANGES_PATH)
                        .param(SINCE, "2026-04-10T00:00:00+03:00")
                        .param(UNTIL, UNTIL_2026_04_03))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath(JSON_ERROR).value(BAD_REQUEST_ERROR))
                .andExpect(jsonPath(JSON_MESSAGE).value(Matchers.containsString("yyyy-MM-dd")));
    }

    @Test
    void changesRejectsDateTimeInput() throws Exception {
        mockMvc.perform(get(CHANGES_PATH)
                        .param(SINCE, "2026-04-01T12:00:00")
                        .param(UNTIL, UNTIL_2026_04_03))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath(JSON_ERROR).value(BAD_REQUEST_ERROR))
                .andExpect(jsonPath(JSON_MESSAGE).value(Matchers.containsString("yyyy-MM-dd")));
    }

    @Test
    void changesRejectsZuluInput() throws Exception {
        mockMvc.perform(get(CHANGES_PATH)
                        .param(SINCE, "2026-04-10T00:00:00Z")
                        .param(UNTIL, UNTIL_2026_04_03))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath(JSON_ERROR).value(BAD_REQUEST_ERROR))
                .andExpect(jsonPath(JSON_MESSAGE).value(Matchers.containsString("yyyy-MM-dd")));
    }

    @Test
    void changesPassesParsedDateToService() throws Exception {
        LocalDate expectedSince = LocalDate.of(2026, 4, 1);
        LocalDate expectedUntil = LocalDate.of(2026, 4, 3);
        when(reportService.changeLog(any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        mockMvc.perform(get(CHANGES_PATH).param(SINCE, "2026-04-01").param(UNTIL, "2026-04-03"))
                .andExpect(status().isOk());

        ArgumentCaptor<LocalDate> sinceCaptor = ArgumentCaptor.forClass(LocalDate.class);
        ArgumentCaptor<LocalDate> untilCaptor = ArgumentCaptor.forClass(LocalDate.class);
        verify(reportService).changeLog(sinceCaptor.capture(), untilCaptor.capture(), any(Pageable.class));
        assertThat(sinceCaptor.getValue()).isEqualTo(expectedSince);
        assertThat(untilCaptor.getValue()).isEqualTo(expectedUntil);
    }

    @Test
    void changesRejectsRangeAbove90Days() throws Exception {
        when(reportService.changeLog(any(LocalDate.class), any(LocalDate.class), any(Pageable.class)))
                .thenThrow(new IllegalArgumentException(
                        "Date range must not exceed 90 days (was 91 days)"));

        mockMvc.perform(get(CHANGES_PATH).param(SINCE, "2026-01-01").param(UNTIL, "2026-04-02"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath(JSON_MESSAGE).value(Matchers.containsString("90 days")));
    }

    @Test
    void changesAppliesDefaultPageAndSize() throws Exception {
        when(reportService.changeLog(eq(SINCE_DATE), eq(UNTIL_DATE), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        mockMvc.perform(get(CHANGES_PATH).param(SINCE, SINCE_2026_04_01).param(UNTIL, UNTIL_2026_04_03))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalCount").value(0))
                .andExpect(jsonPath("$.totalPages").value(0));

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(reportService).changeLog(eq(SINCE_DATE), eq(UNTIL_DATE), captor.capture());
        assertThat(captor.getValue().getPageNumber()).isZero();
        assertThat(captor.getValue().getPageSize()).isEqualTo(20);
        assertThat(captor.getValue().getSort().isUnsorted()).isTrue();
    }

    @Test
    void changesPropagatesPaginationParams() throws Exception {
        when(reportService.changeLog(eq(SINCE_DATE), eq(UNTIL_DATE), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(2, 5), 12));

        mockMvc.perform(get(CHANGES_PATH).param(SINCE, SINCE_2026_04_01).param(UNTIL, UNTIL_2026_04_03)
                        .param("page", "3").param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(3))
                .andExpect(jsonPath("$.size").value(5))
                .andExpect(jsonPath("$.totalCount").value(12))
                .andExpect(jsonPath("$.totalPages").value(3));

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(reportService).changeLog(eq(SINCE_DATE), eq(UNTIL_DATE), captor.capture());
        assertThat(captor.getValue().getPageNumber()).isEqualTo(2);
        assertThat(captor.getValue().getPageSize()).isEqualTo(5);
    }

    @Test
    void changesRejectsNonIntegerPage() throws Exception {
        mockMvc.perform(get(CHANGES_PATH).param(SINCE, SINCE_2026_04_01).param(UNTIL, UNTIL_2026_04_03)
                        .param("page", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath(JSON_ERROR).value(BAD_REQUEST_ERROR))
                .andExpect(jsonPath(JSON_MESSAGE).value(Matchers.containsString("page")));
    }

    @Test
    void changesRejectsNonIntegerSize() throws Exception {
        mockMvc.perform(get(CHANGES_PATH).param(SINCE, SINCE_2026_04_01).param(UNTIL, UNTIL_2026_04_03)
                        .param("size", "twenty"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath(JSON_MESSAGE).value(Matchers.containsString("size")));
    }

    @Test
    void changesRejectsZeroPage() throws Exception {
        // PageRequest.of(-1, 20) throws IllegalArgumentException via PaginationUtil → 400.
        mockMvc.perform(get(CHANGES_PATH).param(SINCE, SINCE_2026_04_01).param(UNTIL, UNTIL_2026_04_03)
                        .param("page", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath(JSON_ERROR).value(BAD_REQUEST_ERROR));
    }

    @Test
    void changesRejectsNegativeSize() throws Exception {
        mockMvc.perform(get(CHANGES_PATH).param(SINCE, SINCE_2026_04_01).param(UNTIL, UNTIL_2026_04_03)
                        .param("size", "-5"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath(JSON_ERROR).value(BAD_REQUEST_ERROR));
    }

    @Test
    void changesRejectsOversizedPageSize() throws Exception {
        mockMvc.perform(get(CHANGES_PATH).param(SINCE, SINCE_2026_04_01).param(UNTIL, UNTIL_2026_04_03)
                        .param("size", "201"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath(JSON_ERROR).value(BAD_REQUEST_ERROR))
                .andExpect(jsonPath(JSON_MESSAGE).value(Matchers.containsString("must not exceed 200")));
    }

    @Test
    void changesAcceptsMaxPageSizeBoundary() throws Exception {
        when(reportService.changeLog(eq(SINCE_DATE), eq(UNTIL_DATE), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 200), 0));

        mockMvc.perform(get(CHANGES_PATH).param(SINCE, SINCE_2026_04_01).param(UNTIL, UNTIL_2026_04_03)
                        .param("size", "200"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(200));
    }

    @Test
    void changesPageBeyondLastReturnsEmptyItemsAndAccurateMetadata() throws Exception {
        // Requesting a page past the last is not an error — it returns an empty `items` array
        // with the requested page number and the actual totalCount/totalPages.
        when(reportService.changeLog(eq(SINCE_DATE), eq(UNTIL_DATE), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(9, 5), 5));

        mockMvc.perform(get(CHANGES_PATH).param(SINCE, SINCE_2026_04_01).param(UNTIL, UNTIL_2026_04_03)
                        .param("page", "10").param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0))
                .andExpect(jsonPath("$.totalCount").value(5))
                .andExpect(jsonPath("$.page").value(10))
                .andExpect(jsonPath("$.size").value(5))
                .andExpect(jsonPath("$.totalPages").value(1));
    }

    @Test
    void changesIgnoresUnsupportedSortAndIncludeRemovedParams() throws Exception {
        // Chronological order is fixed; includeRemoved is implicit on /changes
        // (the "removed" bucket always exists). Extra params silently ignored.
        when(reportService.changeLog(eq(SINCE_DATE), eq(UNTIL_DATE), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        mockMvc.perform(get(CHANGES_PATH).param(SINCE, SINCE_2026_04_01).param(UNTIL, UNTIL_2026_04_03)
                        .param("sortBy", "date").param("sortOrder", "desc")
                        .param("includeRemoved", "true"))
                .andExpect(status().isOk());

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(reportService).changeLog(eq(SINCE_DATE), eq(UNTIL_DATE), captor.capture());
        assertThat(captor.getValue().getSort().isUnsorted()).isTrue();
    }

    @Test
    void changesReturnsEmptyEnvelopeWhenNoChangesInRange() throws Exception {
        // Days with zero changes are omitted from items; totalCount reflects days
        // that had at least one change. An empty range therefore produces totalCount=0.
        when(reportService.changeLog(eq(SINCE_DATE), eq(UNTIL_DATE), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        mockMvc.perform(get(CHANGES_PATH).param(SINCE, SINCE_2026_04_01).param(UNTIL, UNTIL_2026_04_03))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0))
                .andExpect(jsonPath("$.totalCount").value(0))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalPages").value(0));
    }

    @Test
    void postToChangesReturns405() throws Exception {
        mockMvc.perform(post(CHANGES_PATH))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath(JSON_STATUS).value(405))
                .andExpect(jsonPath(JSON_ERROR).value(METHOD_NOT_ALLOWED_ERROR))
                .andExpect(header().string("Allow", "GET"));
    }

    @Test
    void postToChangesUnderContextPathReturns405() throws Exception {
        mockMvc.perform(post("/catalog" + CHANGES_PATH).contextPath("/catalog"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath(JSON_STATUS).value(405))
                .andExpect(jsonPath(JSON_ERROR).value(METHOD_NOT_ALLOWED_ERROR))
                .andExpect(header().string("Allow", "GET"));
    }
}
