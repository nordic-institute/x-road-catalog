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
 * V2 reports endpoints (spec §4). Both {@code since} and {@code until} are optional query
 * parameters in {@code yyyy-MM-dd} form (spec §8) — sub-day precision and timezone offsets are
 * rejected by {@link DateTimeUtil#parseDate}. When omitted, defaults are applied so callers can
 * hit the routes with no parameters and get a useful trailing-week window:
 * <ul>
 *   <li>{@code until} defaults to {@code today + 1 day} (UTC). The half-open
 *       {@code [since, until)} contract of {@link ReportServiceV2#serviceStatistics} and
 *       {@link ReportServiceV2#changeLog} means an exclusive cutoff of tomorrow includes all of
 *       today's data.</li>
 *   <li>{@code since} defaults to {@code until - 7 days}, yielding the trailing 7 calendar days
 *       including today.</li>
 * </ul>
 * Defaults are computed from an injected {@link Clock} so tests can pin "today". Resolved values
 * are passed straight to {@link ReportServiceV2#serviceStatistics} / {@link ReportServiceV2#changeLog},
 * which validate the range via {@code DateTimeUtil.validateDateRange} — {@code since == until} is
 * allowed, {@code since} after {@code until} or a range over 90 days (spec §4) raises
 * {@link IllegalArgumentException}, mapped to {@code 400 BadRequest} by {@link V2ExceptionHandler}.
 * Both endpoints declare {@code produces=application/json} so Spring rejects content negotiation for non-JSON
 * {@code Accept} headers (spec §4: "JSON only — no CSV in V2"). Clients that opt into an
 * unsupported media type get a {@code 406 Not Acceptable} from the framework, which is the
 * correct HTTP-level response.
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
        // Defaults: until = tomorrow (exclusive cutoff so today is included in the half-open
        // [since, until) range; see ReportServiceV2 contract). since = until - 7 days.
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
