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

import org.niis.xroad.catalog.lister.v2.dto.ChangeLogDayDto;
import org.niis.xroad.catalog.lister.v2.dto.PagedCollectionResponse;
import org.niis.xroad.catalog.lister.v2.dto.ServiceStatisticsRowDto;
import org.niis.xroad.catalog.lister.v2.service.ReportServiceV2;
import org.niis.xroad.catalog.lister.v2.util.DateTimeUtil;
import org.niis.xroad.catalog.lister.v2.util.PaginationUtil;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

/**
 * V2 reports endpoints. {@code since}/{@code until} are optional {@code yyyy-MM-dd} query
 * parameters; sub-day precision and timezone offsets are rejected. {@code until} defaults to
 * {@code today + 1 day} (server-local; the exclusive cutoff of the half-open {@code [since, until)}
 * window includes all of today) and {@code since} to {@code until - 7 days} — the trailing week.
 *
 * <p>Range validation happens in {@link ReportServiceV2}: {@code since == until} is allowed;
 * {@code since} after {@code until} or a range over 90 days maps to 400. Both endpoints declare
 * {@code produces=application/json}, so an {@code Accept} header excluding JSON gets a framework
 * 406 — the V2 reports are JSON only.
 */
@RestController
@RequestMapping("/api/v2/reports")
public class ReportsController {

    private static final int DEFAULT_REPORT_WINDOW_DAYS = 7;

    private final ReportServiceV2 reportService;
    private final Clock clock;

    public ReportsController(ReportServiceV2 reportService, Clock clock) {
        this.reportService = reportService;
        this.clock = clock;
    }

    @GetMapping(path = "/service-statistics", produces = MediaType.APPLICATION_JSON_VALUE)
    public PagedCollectionResponse<ServiceStatisticsRowDto> serviceStatistics(
            @RequestParam(value = "since", required = false) String sinceStr,
            @RequestParam(value = "until", required = false) String untilStr) {
        // Defaults: until = tomorrow (exclusive cutoff includes today), since = until - 7 days.
        LocalDate defaultUntil = DateTimeUtil.today(clock).plusDays(1);
        LocalDate until = DateTimeUtil.parseDateOrDefault(untilStr, defaultUntil);
        LocalDate since = DateTimeUtil.parseDateOrDefault(sinceStr, until.minusDays(DEFAULT_REPORT_WINDOW_DAYS));
        List<ServiceStatisticsRowDto> rows = reportService.serviceStatistics(since, until);
        return PagedCollectionResponse.fromList(rows);
    }

    @GetMapping(path = "/changes", produces = MediaType.APPLICATION_JSON_VALUE)
    public PagedCollectionResponse<ChangeLogDayDto> changes(
            @RequestParam(value = "since", required = false) String sinceStr,
            @RequestParam(value = "until", required = false) String untilStr,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size) {
        LocalDate defaultUntil = DateTimeUtil.today(clock).plusDays(1);
        LocalDate until = DateTimeUtil.parseDateOrDefault(untilStr, defaultUntil);
        LocalDate since = DateTimeUtil.parseDateOrDefault(sinceStr, until.minusDays(DEFAULT_REPORT_WINDOW_DAYS));
        Pageable pageable = PaginationUtil.toPageableNoSort(page, size);
        Page<ChangeLogDayDto> result = reportService.changeLog(since, until, pageable);
        return PagedCollectionResponse.fromPage(result);
    }
}
