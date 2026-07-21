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
import org.niis.xroad.catalog.lister.v2.dto.FullMemberDto;
import org.niis.xroad.catalog.lister.v2.dto.MemberClassDto;
import org.niis.xroad.catalog.lister.v2.dto.MemberDto;
import org.niis.xroad.catalog.lister.v2.dto.PagedCollectionResponse;
import org.niis.xroad.catalog.lister.v2.dto.SecurityServerBrowseItemDto;
import org.niis.xroad.catalog.lister.v2.dto.ServiceDto;
import org.niis.xroad.catalog.lister.v2.dto.ServiceVersionDto;
import org.niis.xroad.catalog.lister.v2.dto.SubsystemDto;
import org.niis.xroad.catalog.lister.v2.service.MemberClassServiceV2;
import org.niis.xroad.catalog.lister.v2.service.MemberServiceV2;
import org.niis.xroad.catalog.lister.v2.service.SecurityServerServiceV2;
import org.niis.xroad.catalog.lister.v2.service.ServiceServiceV2;
import org.niis.xroad.catalog.lister.v2.service.SubsystemServiceV2;
import org.niis.xroad.catalog.lister.v2.util.PaginationUtil;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    private final MemberClassServiceV2 memberClassService;
    private final MemberServiceV2 memberService;
    private final SubsystemServiceV2 subsystemService;
    private final ServiceServiceV2 serviceService;
    private final SecurityServerServiceV2 securityServerService;

    public BrowseController(MemberClassServiceV2 memberClassService, MemberServiceV2 memberService,
            SubsystemServiceV2 subsystemService, ServiceServiceV2 serviceService,
            SecurityServerServiceV2 securityServerService) {
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
        MemberClassDto dto = memberClassService.getByCode(memberClass);
        if (dto == null) {
            throw new V2ResourceNotFoundException("Member class '" + memberClass + "' not found");
        }
        return dto;
    }

    @GetMapping("/member-classes/{memberClass}/members")
    public PagedCollectionResponse<MemberDto> listMembers(
            @PathVariable("memberClass") String memberClass,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size,
            @RequestParam(value = "sortBy", required = false) String sortBy,
            @RequestParam(value = "sortOrder", required = false) String sortOrder) {
        if (memberClassService.getByCode(memberClass) == null) {
            throw new V2ResourceNotFoundException("Member class '" + memberClass + "' not found");
        }
        Pageable pageable = PaginationUtil.toPageable(page, size, sortBy, sortOrder, "name",
                MEMBER_SORT_FIELDS, MEMBER_SORT_ALIASES);
        Page<MemberDto> result = memberService.getForList(memberClass, null, pageable);
        return PagedCollectionResponse.fromPage(result);
    }

    @GetMapping("/member-classes/{memberClass}/members/{memberCode}")
    public Object getMember(
            @PathVariable("memberClass") String memberClass,
            @PathVariable("memberCode") String memberCode,
            @RequestParam(value = "full", defaultValue = "false") boolean full) {
        if (full) {
            FullMemberDto dto = memberService.getFullTree(memberClass, memberCode);
            if (dto == null) {
                throw memberNotFound(memberClass, memberCode);
            }
            return dto;
        }
        MemberDto dto = memberService.getByNaturalKey(memberClass, memberCode);
        if (dto == null) {
            throw memberNotFound(memberClass, memberCode);
        }
        return dto;
    }

    @GetMapping("/member-classes/{memberClass}/members/{memberCode}/subsystems")
    public PagedCollectionResponse<SubsystemDto> listSubsystems(
            @PathVariable("memberClass") String memberClass,
            @PathVariable("memberCode") String memberCode) {
        List<SubsystemDto> items = subsystemService.getForMember(memberClass, memberCode)
                .orElseThrow(() -> memberNotFound(memberClass, memberCode));
        return PagedCollectionResponse.fromList(items);
    }

    @GetMapping("/member-classes/{memberClass}/members/{memberCode}/security-servers")
    public PagedCollectionResponse<SecurityServerBrowseItemDto> listSecurityServersForMember(
            @PathVariable("memberClass") String memberClass,
            @PathVariable("memberCode") String memberCode) {
        if (!memberService.existsActive(memberClass, memberCode)) {
            throw memberNotFound(memberClass, memberCode);
        }
        List<SecurityServerBrowseItemDto> items = securityServerService.getForMember(memberClass, memberCode);
        return PagedCollectionResponse.fromList(items);
    }

    @GetMapping("/member-classes/{memberClass}/members/{memberCode}/subsystems/{subsystemCode}")
    public SubsystemDto getSubsystem(
            @PathVariable("memberClass") String memberClass,
            @PathVariable("memberCode") String memberCode,
            @PathVariable("subsystemCode") String subsystemCode) {
        SubsystemDto dto = subsystemService.getByNaturalKey(memberClass, memberCode, subsystemCode);
        if (dto == null) {
            throw subsystemNotFound(memberClass, memberCode, subsystemCode);
        }
        return dto;
    }

    @GetMapping("/member-classes/{memberClass}/members/{memberCode}/subsystems/{subsystemCode}/services")
    public PagedCollectionResponse<ServiceDto> listServices(
            @PathVariable("memberClass") String memberClass,
            @PathVariable("memberCode") String memberCode,
            @PathVariable("subsystemCode") String subsystemCode) {
        List<ServiceDto> items = serviceService.getForSubsystem(memberClass, memberCode, subsystemCode)
                .orElseThrow(() -> subsystemNotFound(memberClass, memberCode, subsystemCode));
        return PagedCollectionResponse.fromList(items);
    }

    @GetMapping("/member-classes/{memberClass}/members/{memberCode}/subsystems/{subsystemCode}/services/{serviceCode}")
    public ServiceDto getService(
            @PathVariable("memberClass") String memberClass,
            @PathVariable("memberCode") String memberCode,
            @PathVariable("subsystemCode") String subsystemCode,
            @PathVariable("serviceCode") String serviceCode) {
        ServiceDto dto = serviceService.getByNaturalKey(memberClass, memberCode, subsystemCode, serviceCode);
        if (dto == null) {
            throw serviceNotFound(memberClass, memberCode, subsystemCode, serviceCode);
        }
        return dto;
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
                .orElseThrow(() -> serviceNotFound(memberClass, memberCode, subsystemCode, serviceCode));
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
        ServiceVersionDto dto = serviceService.getVersion(
                memberClass, memberCode, subsystemCode, serviceCode, serviceVersion);
        if (dto == null) {
            throw new V2ResourceNotFoundException(
                    "Service version '" + memberClass + "/" + memberCode + "/" + subsystemCode + "/"
                            + serviceCode + "/" + serviceVersion + "' not found");
        }
        return dto;
    }

    private V2ResourceNotFoundException memberNotFound(String memberClass, String memberCode) {
        return new V2ResourceNotFoundException(
                "Member '" + memberClass + "/" + memberCode + "' not found");
    }

    private V2ResourceNotFoundException subsystemNotFound(String memberClass, String memberCode, String subsystemCode) {
        return new V2ResourceNotFoundException(
                "Subsystem '" + memberClass + "/" + memberCode + "/" + subsystemCode + "' not found");
    }

    private V2ResourceNotFoundException serviceNotFound(String memberClass, String memberCode,
                                                        String subsystemCode, String serviceCode) {
        return new V2ResourceNotFoundException(
                "Service '" + memberClass + "/" + memberCode + "/" + subsystemCode + "/" + serviceCode + "' not found");
    }
}
