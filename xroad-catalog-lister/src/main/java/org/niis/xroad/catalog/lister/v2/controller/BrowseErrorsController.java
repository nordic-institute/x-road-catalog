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

import io.swagger.v3.oas.annotations.Parameter;
import lombok.Getter;
import lombok.Setter;
import org.springdoc.core.annotations.ParameterObject;
import org.niis.xroad.catalog.lister.v2.dto.ErrorLogDto;
import org.niis.xroad.catalog.lister.v2.dto.PagedCollectionResponse;
import org.niis.xroad.catalog.lister.v2.service.ErrorLogServiceV2;
import org.niis.xroad.catalog.lister.v2.util.DateTimeUtil;
import org.niis.xroad.catalog.lister.v2.util.PaginationUtil;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;

/**
 * Browse errors at every hierarchy level. Lives apart from {@link BrowseController} because the
 * error-log dispatch doesn't share state with the structural browse routes — keeping them
 * separate avoids cross-bleed between the controller-slice tests.
 *
 * <p>Pagination/sort query parameters ({@code page}, {@code size}, {@code sortBy}, {@code sortOrder})
 * are bundled into {@link PageOpts} so handlers stay under PMD's parameter-count threshold.</p>
 *
 * <p>Both {@code since} and {@code until} are optional. When omitted, {@code until} defaults to
 * tomorrow 00:00 UTC (an exclusive cutoff so today is included) and {@code since} defaults to
 * one day before {@code until} (so the window collapses to "today's errors only" when both are
 * omitted). Because defaults always supply both bounds, missing-parameter 400s are no longer
 * possible — only malformed dates, {@code since > until}, and date ranges exceeding 90 days
 * still surface a 400.</p>
 *
 * <p>Path segments identifying a parent (member class, member, subsystem, service, version) are
 * used only as WHERE-clause values for the error-log query — an unknown parent simply yields a
 * 200 with an empty page, matching {@link BrowseController}'s list endpoints. There is no
 * existence check against the parent hierarchy.</p>
 */
@RestController
@RequestMapping("/api/v2/browse")
public class BrowseErrorsController {

    private static final Set<String> ERROR_SORT_FIELDS = Set.of("created", "code");
    private static final String DEFAULT_SORT_FIELD = "created";
    private static final String DEFAULT_SORT_ORDER = "desc";

    private final Clock clock;
    private final ErrorLogServiceV2 errorLogService;

    public BrowseErrorsController(Clock clock, ErrorLogServiceV2 errorLogService) {
        this.clock = clock;
        this.errorLogService = errorLogService;
    }

    @GetMapping("/errors")
    public PagedCollectionResponse<ErrorLogDto> catalogErrors(
            @RequestParam(value = "since", required = false) String sinceStr,
            @RequestParam(value = "until", required = false) String untilStr,
            @ParameterObject PageOpts pageOpts) {
        TimeRange range = parseRange(sinceStr, untilStr);
        return dispatch(null, null, null, null, null, range, pageOpts);
    }

    @GetMapping("/member-classes/{memberClass}/errors")
    public PagedCollectionResponse<ErrorLogDto> memberClassErrors(
            @PathVariable("memberClass") String memberClass,
            @RequestParam(value = "since", required = false) String sinceStr,
            @RequestParam(value = "until", required = false) String untilStr,
            @ParameterObject PageOpts pageOpts) {
        TimeRange range = parseRange(sinceStr, untilStr);
        return dispatch(memberClass, null, null, null, null, range, pageOpts);
    }

    @GetMapping("/member-classes/{memberClass}/members/{memberCode}/errors")
    public PagedCollectionResponse<ErrorLogDto> memberErrors(
            @PathVariable("memberClass") String memberClass,
            @PathVariable("memberCode") String memberCode,
            @RequestParam(value = "since", required = false) String sinceStr,
            @RequestParam(value = "until", required = false) String untilStr,
            @ParameterObject PageOpts pageOpts) {
        TimeRange range = parseRange(sinceStr, untilStr);
        return dispatch(memberClass, memberCode, null, null, null, range, pageOpts);
    }

    @GetMapping("/member-classes/{memberClass}/members/{memberCode}/subsystems/{subsystemCode}/errors")
    public PagedCollectionResponse<ErrorLogDto> subsystemErrors(
            @PathVariable("memberClass") String memberClass,
            @PathVariable("memberCode") String memberCode,
            @PathVariable("subsystemCode") String subsystemCode,
            @RequestParam(value = "since", required = false) String sinceStr,
            @RequestParam(value = "until", required = false) String untilStr,
            @ParameterObject PageOpts pageOpts) {
        TimeRange range = parseRange(sinceStr, untilStr);
        return dispatch(memberClass, memberCode, subsystemCode, null, null, range, pageOpts);
    }

    @GetMapping("/member-classes/{memberClass}/members/{memberCode}/subsystems/{subsystemCode}"
            + "/services/{serviceCode}/errors")
    public PagedCollectionResponse<ErrorLogDto> serviceErrors(
            @PathVariable("memberClass") String memberClass,
            @PathVariable("memberCode") String memberCode,
            @PathVariable("subsystemCode") String subsystemCode,
            @PathVariable("serviceCode") String serviceCode,
            @RequestParam(value = "since", required = false) String sinceStr,
            @RequestParam(value = "until", required = false) String untilStr,
            @ParameterObject PageOpts pageOpts) {
        TimeRange range = parseRange(sinceStr, untilStr);
        return dispatch(memberClass, memberCode, subsystemCode, serviceCode, null, range, pageOpts);
    }

    @GetMapping("/member-classes/{memberClass}/members/{memberCode}/subsystems/{subsystemCode}"
            + "/services/{serviceCode}/versions/{serviceVersion}/errors")
    public PagedCollectionResponse<ErrorLogDto> versionErrors(
            @PathVariable("memberClass") String memberClass,
            @PathVariable("memberCode") String memberCode,
            @PathVariable("subsystemCode") String subsystemCode,
            @PathVariable("serviceCode") String serviceCode,
            @Parameter(
                    description = "Service version label. Use the literal string \"null\" (case-sensitive) to "
                            + "address a service version that has no version label. A real version literally "
                            + "named \"null\" is therefore unaddressable.",
                    example = "v1")
            @PathVariable("serviceVersion") String serviceVersion,
            @RequestParam(value = "since", required = false) String sinceStr,
            @RequestParam(value = "until", required = false) String untilStr,
            @ParameterObject PageOpts pageOpts) {
        TimeRange range = parseRange(sinceStr, untilStr);
        // serviceVersion is the raw URL segment; ErrorLogServiceV2.get resolves the "null" sentinel internally.
        return dispatch(memberClass, memberCode, subsystemCode, serviceCode, serviceVersion, range, pageOpts);
    }

    private TimeRange parseRange(String sinceStr, String untilStr) {
        // Defaults: until = tomorrow 00:00 (exclusive cutoff so today is included), since = today 00:00.
        // Window collapses to "today's errors only" when both are omitted.
        // Range bounds (since <= until, <= 90 days) are enforced by ErrorLogServiceV2.get, not here —
        // it owns the bounds contract regardless of caller.
        LocalDate today = DateTimeUtil.today(clock);
        LocalDate untilDate = DateTimeUtil.parseDateOrDefault(untilStr, today.plusDays(1));
        LocalDate sinceDate = DateTimeUtil.parseDateOrDefault(sinceStr, untilDate.minusDays(1));
        LocalDateTime since = sinceDate.atStartOfDay();
        LocalDateTime until = untilDate.atStartOfDay();
        return new TimeRange(since, until);
    }

    private PagedCollectionResponse<ErrorLogDto> dispatch(String memberClass, String memberCode, String subsystemCode,
                                                          String serviceCode, String serviceVersion,
                                                          TimeRange range, PageOpts pageOpts) {
        // PaginationUtil.toPageable defaults sortOrder to "asc"; spec §8 requires the errors default to
        // "created desc". Inject the default locally so we don't perturb the shared utility.
        String sortOrder = pageOpts.getSortOrder();
        String effectiveSortOrder = (sortOrder == null || sortOrder.isBlank()) ? DEFAULT_SORT_ORDER : sortOrder;
        Pageable pageable = PaginationUtil.toPageable(pageOpts.getPage(), pageOpts.getSize(),
                pageOpts.getSortBy(), effectiveSortOrder, DEFAULT_SORT_FIELD, ERROR_SORT_FIELDS);

        Page<ErrorLogDto> result = errorLogService.get(memberClass, memberCode, subsystemCode, serviceCode,
                serviceVersion, range.since(), range.until(), pageable);
        return PagedCollectionResponse.fromPage(result);
    }

    private record TimeRange(LocalDateTime since, LocalDateTime until) { }

    /**
     * Spring binds {@code page}, {@code size}, {@code sortBy}, {@code sortOrder} request parameters into this
     * setter-based bean (default {@code @ModelAttribute} for non-simple types). Bundling the four pagination knobs
     * keeps each handler under PMD's parameter-count threshold without sacrificing explicit {@code @PathVariable}
     * and {@code @RequestParam} bindings on the still-individual fields.
     */
    @Getter
    @Setter
    public static class PageOpts {
        private Integer page;
        private Integer size;
        private String sortBy;
        private String sortOrder;
    }
}
