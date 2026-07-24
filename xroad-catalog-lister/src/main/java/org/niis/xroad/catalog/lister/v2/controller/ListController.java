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
import org.niis.xroad.catalog.lister.v2.dto.MemberDto;
import org.niis.xroad.catalog.lister.v2.dto.PagedCollectionResponse;
import org.niis.xroad.catalog.lister.v2.dto.SecurityServerListItemDto;
import org.niis.xroad.catalog.lister.v2.dto.ServiceDto;
import org.niis.xroad.catalog.lister.v2.dto.SubsystemDto;
import org.niis.xroad.catalog.lister.v2.service.MemberServiceV2;
import org.niis.xroad.catalog.lister.v2.service.SecurityServerServiceV2;
import org.niis.xroad.catalog.lister.v2.service.ServiceServiceV2;
import org.niis.xroad.catalog.lister.v2.service.SubsystemServiceV2;
import org.niis.xroad.catalog.lister.v2.util.PaginationUtil;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/v2/list")
public class ListController {

    private static final String STATUS_INFO_CREATED = "statusInfo.created";
    private static final String STATUS_INFO_CHANGED = "statusInfo.changed";
    private static final String CREATED = "created";
    private static final String CHANGED = "changed";

    private static final Set<String> SECURITY_SERVER_SORT_FIELDS = Set.of("serverCode", "address");
    private static final Set<String> MEMBER_SORT_FIELDS = Set.of("name", "memberCode", CREATED, CHANGED);
    private static final Map<String, String> STATUS_TIMESTAMP_ALIASES = Map.of(
            CREATED, STATUS_INFO_CREATED,
            CHANGED, STATUS_INFO_CHANGED);

    private static final Set<String> SUBSYSTEM_SORT_FIELDS = Set.of("subsystemCode", CREATED, CHANGED);

    // UNKNOWN is filterable so operators can find services collected but not yet classified by the
    // collector recompute. It is a transient state, not a descriptor kind.
    private static final Set<String> ALLOWED_SERVICE_TYPES = Set.of("SOAP", "REST", "OPENAPI", "UNKNOWN");

    private final MemberServiceV2 memberService;
    private final SubsystemServiceV2 subsystemService;
    private final ServiceServiceV2 serviceService;
    private final SecurityServerServiceV2 securityServerService;

    public ListController(MemberServiceV2 memberService, SubsystemServiceV2 subsystemService,
            ServiceServiceV2 serviceService, SecurityServerServiceV2 securityServerService) {
        this.memberService = memberService;
        this.subsystemService = subsystemService;
        this.serviceService = serviceService;
        this.securityServerService = securityServerService;
    }

    @GetMapping("/security-servers")
    public PagedCollectionResponse<SecurityServerListItemDto> listSecurityServers(
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size,
            @RequestParam(value = "sortBy", required = false) String sortBy,
            @RequestParam(value = "sortOrder", required = false) String sortOrder) {
        Pageable pageable = PaginationUtil.toPageableNoTieBreak(page, size, sortBy, sortOrder,
                "serverCode", SECURITY_SERVER_SORT_FIELDS);
        Page<SecurityServerListItemDto> result = securityServerService.list(pageable);
        return PagedCollectionResponse.fromPage(result);
    }

    @GetMapping("/members")
    public PagedCollectionResponse<MemberDto> listMembers(
            @RequestParam(value = "memberClass", required = false) String memberClass,
            @Parameter(description = "Filters by provider status. Accepts true/false/on/off/yes/no/1/0; "
                    + "omitted or null applies no filter.")
            @RequestParam(value = "provider", required = false) Boolean provider,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size,
            @RequestParam(value = "sortBy", required = false) String sortBy,
            @RequestParam(value = "sortOrder", required = false) String sortOrder) {
        Pageable pageable = PaginationUtil.toPageable(page, size, sortBy, sortOrder, "name",
                MEMBER_SORT_FIELDS, STATUS_TIMESTAMP_ALIASES);
        Page<MemberDto> result = memberService.getForList(memberClass, provider, pageable);
        return PagedCollectionResponse.fromPage(result);
    }

    @GetMapping("/subsystems")
    public PagedCollectionResponse<SubsystemDto> listSubsystems(
            @RequestParam(value = "memberClass", required = false) String memberClass,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size,
            @RequestParam(value = "sortBy", required = false) String sortBy,
            @RequestParam(value = "sortOrder", required = false) String sortOrder) {
        Pageable pageable = PaginationUtil.toPageable(page, size, sortBy, sortOrder, "subsystemCode",
                SUBSYSTEM_SORT_FIELDS, STATUS_TIMESTAMP_ALIASES);
        Page<SubsystemDto> result = subsystemService.getForList(memberClass, pageable);
        return PagedCollectionResponse.fromPage(result);
    }

    @GetMapping("/services")
    public PagedCollectionResponse<ServiceDto> listServices(
            @RequestParam(value = "memberClass", required = false) String memberClass,
            @RequestParam(value = "serviceType", required = false) String serviceType,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size) {
        if (serviceType != null && !serviceType.isBlank() && !ALLOWED_SERVICE_TYPES.contains(serviceType)) {
            throw new IllegalArgumentException(
                    "Invalid value for query parameter 'serviceType': '" + serviceType
                            + "'. Allowed: " + ALLOWED_SERVICE_TYPES);
        }
        // The service list is an aggregate view ordered by serviceCode ascending in the repository
        // (spec §8). sortBy/sortOrder are intentionally unsupported, mirroring /api/v2/search.
        Pageable pageable = PaginationUtil.toPageableNoSort(page, size);
        String resolvedType = (serviceType == null || serviceType.isBlank()) ? null : serviceType;
        Page<ServiceDto> result = serviceService.getForList(memberClass, resolvedType, pageable);
        return PagedCollectionResponse.fromPage(result);
    }
}
