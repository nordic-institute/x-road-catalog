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
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.niis.xroad.catalog.lister.v2.dto.FullMemberDto;
import org.niis.xroad.catalog.lister.v2.dto.MemberClassDto;
import org.niis.xroad.catalog.lister.v2.dto.MemberDto;
import org.niis.xroad.catalog.lister.v2.dto.PagedCollectionResponse;
import org.niis.xroad.catalog.lister.v2.dto.SecurityServerBrowseItemDto;
import org.niis.xroad.catalog.lister.v2.dto.ServiceDto;
import org.niis.xroad.catalog.lister.v2.dto.ServiceVersionDto;
import org.niis.xroad.catalog.lister.v2.dto.SubsystemDto;
import org.niis.xroad.catalog.lister.v2.exception.ResourceNotFoundException;
import org.niis.xroad.catalog.lister.v2.service.MemberClassService;
import org.niis.xroad.catalog.lister.v2.service.MemberService;
import org.niis.xroad.catalog.lister.v2.service.SecurityServerService;
import org.niis.xroad.catalog.lister.v2.service.ServiceService;
import org.niis.xroad.catalog.lister.v2.service.SubsystemService;
import org.niis.xroad.catalog.lister.v2.util.PaginationUtil;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/v2/browse")
public class BrowseController {

    private static final Set<String> MEMBER_SORT_FIELDS = Set.of("name", "memberCode", "created", "changed");
    private static final Map<String, String> MEMBER_SORT_ALIASES = Map.of(
            "created", "statusInfo.created",
            "changed", "statusInfo.changed");

    private final MemberClassService memberClassService;
    private final MemberService memberService;
    private final SubsystemService subsystemService;
    private final ServiceService serviceService;
    private final SecurityServerService securityServerService;

    public BrowseController(MemberClassService memberClassService, MemberService memberService,
            SubsystemService subsystemService, ServiceService serviceService,
            SecurityServerService securityServerService) {
        this.memberClassService = memberClassService;
        this.memberService = memberService;
        this.subsystemService = subsystemService;
        this.serviceService = serviceService;
        this.securityServerService = securityServerService;
    }

    @GetMapping("/member-classes")
    public PagedCollectionResponse<MemberClassDto> listMemberClasses() {
        return PagedCollectionResponse.fromList(memberClassService.list());
    }

    @GetMapping("/member-classes/{memberClass}")
    public MemberClassDto getMemberClass(@PathVariable("memberClass") String memberClass) {
        return memberClassService.getByCode(memberClass)
                .orElseThrow(() -> ResourceNotFoundException.of("Member class", memberClass));
    }

    @GetMapping("/member-classes/{memberClass}/members")
    public PagedCollectionResponse<MemberDto> listMembers(
            @PathVariable("memberClass") String memberClass,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size,
            @RequestParam(value = "sortBy", required = false) String sortBy,
            @RequestParam(value = "sortOrder", required = false) String sortOrder) {
        memberClassService.getByCode(memberClass)
                .orElseThrow(() -> ResourceNotFoundException.of("Member class", memberClass));
        Pageable pageable = PaginationUtil.toPageable(page, size, sortBy, sortOrder, "name",
                MEMBER_SORT_FIELDS, MEMBER_SORT_ALIASES);
        Page<MemberDto> result = memberService.getForList(memberClass, null, pageable);
        return PagedCollectionResponse.fromPage(result);
    }

    @GetMapping("/member-classes/{memberClass}/members/{memberCode}")
    @ApiResponse(responseCode = "200", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
            schema = @Schema(oneOf = {MemberDto.class, FullMemberDto.class})))
    public Object getMember(
            @PathVariable("memberClass") String memberClass,
            @PathVariable("memberCode") String memberCode,
            @Parameter(description = "When true, returns the full member tree (subsystems, services and "
                    + "aggregate counts) as the second response shape instead of the plain member.")
            @RequestParam(value = "full", defaultValue = "false") boolean full) {
        if (full) {
            return memberService.getFullTree(memberClass, memberCode)
                    .orElseThrow(() -> ResourceNotFoundException.of("Member", memberClass, memberCode));
        }
        return memberService.getByNaturalKey(memberClass, memberCode)
                .orElseThrow(() -> ResourceNotFoundException.of("Member", memberClass, memberCode));
    }

    @GetMapping("/member-classes/{memberClass}/members/{memberCode}/subsystems")
    public PagedCollectionResponse<SubsystemDto> listSubsystems(
            @PathVariable("memberClass") String memberClass,
            @PathVariable("memberCode") String memberCode) {
        List<SubsystemDto> items = subsystemService.getForMember(memberClass, memberCode)
                .orElseThrow(() -> ResourceNotFoundException.of("Member", memberClass, memberCode));
        return PagedCollectionResponse.fromList(items);
    }

    @GetMapping("/member-classes/{memberClass}/members/{memberCode}/security-servers")
    public PagedCollectionResponse<SecurityServerBrowseItemDto> listSecurityServersForMember(
            @PathVariable("memberClass") String memberClass,
            @PathVariable("memberCode") String memberCode) {
        if (!memberService.existsActive(memberClass, memberCode)) {
            throw ResourceNotFoundException.of("Member", memberClass, memberCode);
        }
        List<SecurityServerBrowseItemDto> items = securityServerService.getForMember(memberClass, memberCode);
        return PagedCollectionResponse.fromList(items);
    }

    @GetMapping("/member-classes/{memberClass}/members/{memberCode}/subsystems/{subsystemCode}")
    public SubsystemDto getSubsystem(
            @PathVariable("memberClass") String memberClass,
            @PathVariable("memberCode") String memberCode,
            @PathVariable("subsystemCode") String subsystemCode) {
        return subsystemService.getByNaturalKey(memberClass, memberCode, subsystemCode)
                .orElseThrow(() -> ResourceNotFoundException.of("Subsystem", memberClass, memberCode, subsystemCode));
    }

    @GetMapping("/member-classes/{memberClass}/members/{memberCode}/subsystems/{subsystemCode}/services")
    public PagedCollectionResponse<ServiceDto> listServices(
            @PathVariable("memberClass") String memberClass,
            @PathVariable("memberCode") String memberCode,
            @PathVariable("subsystemCode") String subsystemCode) {
        List<ServiceDto> items = serviceService.getForSubsystem(memberClass, memberCode, subsystemCode)
                .orElseThrow(() -> ResourceNotFoundException.of("Subsystem", memberClass, memberCode, subsystemCode));
        return PagedCollectionResponse.fromList(items);
    }

    @GetMapping("/member-classes/{memberClass}/members/{memberCode}/subsystems/{subsystemCode}/services/{serviceCode}")
    public ServiceDto getService(
            @PathVariable("memberClass") String memberClass,
            @PathVariable("memberCode") String memberCode,
            @PathVariable("subsystemCode") String subsystemCode,
            @PathVariable("serviceCode") String serviceCode) {
        return serviceService.getByNaturalKey(memberClass, memberCode, subsystemCode, serviceCode)
                .orElseThrow(() -> ResourceNotFoundException.of(
                        "Service", memberClass, memberCode, subsystemCode, serviceCode));
    }

    @GetMapping("/member-classes/{memberClass}/members/{memberCode}/subsystems/{subsystemCode}"
            + "/services/{serviceCode}/versions")
    public PagedCollectionResponse<ServiceVersionDto> listServiceVersions(
            @PathVariable("memberClass") String memberClass,
            @PathVariable("memberCode") String memberCode,
            @PathVariable("subsystemCode") String subsystemCode,
            @PathVariable("serviceCode") String serviceCode) {
        List<ServiceVersionDto> items = serviceService.getVersions(
                        memberClass, memberCode, subsystemCode, serviceCode)
                .orElseThrow(() -> ResourceNotFoundException.of(
                        "Service", memberClass, memberCode, subsystemCode, serviceCode));
        return PagedCollectionResponse.fromList(items);
    }

    @GetMapping("/member-classes/{memberClass}/members/{memberCode}/subsystems/{subsystemCode}"
            + "/services/{serviceCode}/versions/{serviceVersion}")
    public ServiceVersionDto getServiceVersion(
            @PathVariable("memberClass") String memberClass,
            @PathVariable("memberCode") String memberCode,
            @PathVariable("subsystemCode") String subsystemCode,
            @PathVariable("serviceCode") String serviceCode,
            @Parameter(
                    description = "Service version label. Use the literal string \"null\" (case-sensitive) to "
                            + "address a service version that has no version label. A real version literally "
                            + "named \"null\" is therefore unaddressable.",
                    example = "v1")
            @PathVariable("serviceVersion") String serviceVersion) {
        // Pass the raw URL segment through; the service layer resolves the "null" sentinel to null.
        return serviceService.getVersion(memberClass, memberCode, subsystemCode, serviceCode, serviceVersion)
                .orElseThrow(() -> ResourceNotFoundException.of(
                        "Service version", memberClass, memberCode, subsystemCode, serviceCode, serviceVersion));
    }
}
