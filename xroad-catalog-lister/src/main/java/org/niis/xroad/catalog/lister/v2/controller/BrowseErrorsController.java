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
import org.niis.xroad.catalog.lister.v2.service.ErrorLogService;
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
 * Browse errors at every hierarchy level, separate from {@link BrowseController} because the
 * error-log dispatch shares no state with the structural browse routes.
 *
 * <p>{@code since}/{@code until} are optional: {@code until} defaults to tomorrow 00:00
 * server-local time (exclusive cutoff so today is included) and {@code since} to one day before
 * {@code until}, so omitting both means "today's errors only". Malformed dates,
 * {@code since > until}, and ranges over 90 days surface a 400.</p>
 *
 * <p>Parent path segments are used only as WHERE-clause values — an unknown parent yields a 200
 * with an empty page; there is no existence check against the parent hierarchy.</p>
 */
@RestController
@RequestMapping("/api/v2/browse")
public class BrowseErrorsController {

    private static final Set<String> ERROR_SORT_FIELDS = Set.of("created", "code");
    private static final String DEFAULT_SORT_FIELD = "created";
    private static final String DEFAULT_SORT_ORDER = "desc";

    private static final String SINCE_DESCRIPTION =
            "Start of the date window, inclusive (yyyy-MM-dd; sub-day precision and timezone offsets are "
                    + "rejected), taken as 00:00 server-local time. Defaults to 'until' minus 1 day. Must not be "
                    + "after 'until'; equal values are accepted and select an empty window. The window must not "
                    + "exceed 90 days.";
    private static final String UNTIL_DESCRIPTION =
            "End of the date window, exclusive (yyyy-MM-dd; sub-day precision and timezone offsets are "
                    + "rejected). The cutoff is 00:00 server-local time on this day, so the named day itself is not "
                    + "included: pass until=2026-01-02 to cover everything up to and including 2026-01-01. Defaults "
                    + "to tomorrow, which includes all of today.";

    private final Clock clock;
    private final ErrorLogService errorLogService;

    public BrowseErrorsController(Clock clock, ErrorLogService errorLogService) {
        this.clock = clock;
        this.errorLogService = errorLogService;
    }

    @GetMapping("/errors")
    public PagedCollectionResponse<ErrorLogDto> catalogErrors(
            @Parameter(description = SINCE_DESCRIPTION, example = "2026-01-01")
            @RequestParam(value = "since", required = false) String sinceStr,
            @Parameter(description = UNTIL_DESCRIPTION, example = "2026-01-08")
            @RequestParam(value = "until", required = false) String untilStr,
            @ParameterObject PageOpts pageOpts) {
        return dispatch(null, null, null, null, null, sinceStr, untilStr, pageOpts);
    }

    @GetMapping("/member-classes/{memberClass}/errors")
    public PagedCollectionResponse<ErrorLogDto> memberClassErrors(
            @PathVariable("memberClass") String memberClass,
            @Parameter(description = SINCE_DESCRIPTION, example = "2026-01-01")
            @RequestParam(value = "since", required = false) String sinceStr,
            @Parameter(description = UNTIL_DESCRIPTION, example = "2026-01-08")
            @RequestParam(value = "until", required = false) String untilStr,
            @ParameterObject PageOpts pageOpts) {
        return dispatch(memberClass, null, null, null, null, sinceStr, untilStr, pageOpts);
    }

    @GetMapping("/member-classes/{memberClass}/members/{memberCode}/errors")
    public PagedCollectionResponse<ErrorLogDto> memberErrors(
            @PathVariable("memberClass") String memberClass,
            @PathVariable("memberCode") String memberCode,
            @Parameter(description = SINCE_DESCRIPTION, example = "2026-01-01")
            @RequestParam(value = "since", required = false) String sinceStr,
            @Parameter(description = UNTIL_DESCRIPTION, example = "2026-01-08")
            @RequestParam(value = "until", required = false) String untilStr,
            @ParameterObject PageOpts pageOpts) {
        return dispatch(memberClass, memberCode, null, null, null, sinceStr, untilStr, pageOpts);
    }

    @GetMapping("/member-classes/{memberClass}/members/{memberCode}/subsystems/{subsystemCode}/errors")
    public PagedCollectionResponse<ErrorLogDto> subsystemErrors(
            @PathVariable("memberClass") String memberClass,
            @PathVariable("memberCode") String memberCode,
            @PathVariable("subsystemCode") String subsystemCode,
            @Parameter(description = SINCE_DESCRIPTION, example = "2026-01-01")
            @RequestParam(value = "since", required = false) String sinceStr,
            @Parameter(description = UNTIL_DESCRIPTION, example = "2026-01-08")
            @RequestParam(value = "until", required = false) String untilStr,
            @ParameterObject PageOpts pageOpts) {
        return dispatch(memberClass, memberCode, subsystemCode, null, null, sinceStr, untilStr, pageOpts);
    }

    @GetMapping("/member-classes/{memberClass}/members/{memberCode}/subsystems/{subsystemCode}"
            + "/services/{serviceCode}/errors")
    public PagedCollectionResponse<ErrorLogDto> serviceErrors(
            @PathVariable("memberClass") String memberClass,
            @PathVariable("memberCode") String memberCode,
            @PathVariable("subsystemCode") String subsystemCode,
            @PathVariable("serviceCode") String serviceCode,
            @Parameter(description = SINCE_DESCRIPTION, example = "2026-01-01")
            @RequestParam(value = "since", required = false) String sinceStr,
            @Parameter(description = UNTIL_DESCRIPTION, example = "2026-01-08")
            @RequestParam(value = "until", required = false) String untilStr,
            @ParameterObject PageOpts pageOpts) {
        return dispatch(memberClass, memberCode, subsystemCode, serviceCode, null, sinceStr, untilStr, pageOpts);
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
            @Parameter(description = SINCE_DESCRIPTION, example = "2026-01-01")
            @RequestParam(value = "since", required = false) String sinceStr,
            @Parameter(description = UNTIL_DESCRIPTION, example = "2026-01-08")
            @RequestParam(value = "until", required = false) String untilStr,
            @ParameterObject PageOpts pageOpts) {
        // serviceVersion is the raw URL segment; ErrorLogService.get resolves the "null" sentinel internally.
        return dispatch(memberClass, memberCode, subsystemCode, serviceCode, serviceVersion, sinceStr, untilStr, pageOpts);
    }

    private PagedCollectionResponse<ErrorLogDto> dispatch(String memberClass, String memberCode, String subsystemCode,
                                                          String serviceCode, String serviceVersion,
                                                          String sinceStr, String untilStr, PageOpts pageOpts) {
        // Defaults: until = tomorrow 00:00 (exclusive so today is included), since = until - 1 day.
        // Range bounds (since <= until, <= 90 days) are enforced in ErrorLogService.get.
        LocalDate today = DateTimeUtil.today(clock);
        LocalDate untilDate = DateTimeUtil.parseDateOrDefault(untilStr, today.plusDays(1));
        LocalDate sinceDate = DateTimeUtil.parseDateOrDefault(sinceStr, untilDate.minusDays(1));
        LocalDateTime since = sinceDate.atStartOfDay();
        LocalDateTime until = untilDate.atStartOfDay();

        // Errors default to "created desc", but PaginationUtil defaults sortOrder to "asc"; apply the default here.
        String sortOrder = pageOpts.getSortOrder();
        String effectiveSortOrder = (sortOrder == null || sortOrder.isBlank()) ? DEFAULT_SORT_ORDER : sortOrder;
        Pageable pageable = PaginationUtil.toPageable(pageOpts.getPage(), pageOpts.getSize(),
                pageOpts.getSortBy(), effectiveSortOrder, DEFAULT_SORT_FIELD, ERROR_SORT_FIELDS);

        Page<ErrorLogDto> result = errorLogService.get(memberClass, memberCode, subsystemCode, serviceCode,
                serviceVersion, since, until, pageable);
        return PagedCollectionResponse.fromPage(result);
    }

    /**
     * Setter-based bean Spring binds the {@code page}/{@code size}/{@code sortBy}/{@code sortOrder}
     * request parameters into; bundling them keeps handlers under PMD's parameter-count threshold.
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
