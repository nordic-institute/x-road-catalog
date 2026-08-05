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

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.niis.xroad.catalog.lister.v2.dto.EndpointDto;
import org.niis.xroad.catalog.lister.v2.dto.FullMemberDto;
import org.niis.xroad.catalog.lister.v2.dto.FullSubsystemDto;
import org.niis.xroad.catalog.lister.v2.dto.MemberClassDto;
import org.niis.xroad.catalog.lister.v2.dto.MemberDto;
import org.niis.xroad.catalog.lister.v2.dto.ServiceDto;
import org.niis.xroad.catalog.lister.v2.dto.ServiceVersionDto;
import org.niis.xroad.catalog.lister.v2.dto.ServiceVersionSummaryDto;
import org.niis.xroad.catalog.lister.v2.dto.SecurityServerBrowseItemDto;
import org.niis.xroad.catalog.lister.v2.dto.SecurityServerClientDto;
import org.niis.xroad.catalog.lister.v2.dto.SecurityServerOwnerDto;
import org.niis.xroad.catalog.lister.v2.dto.SubsystemDto;
import org.niis.xroad.catalog.lister.v2.service.MemberClassService;
import org.niis.xroad.catalog.lister.v2.service.MemberService;
import org.niis.xroad.catalog.lister.v2.service.SecurityServerService;
import org.niis.xroad.catalog.lister.v2.service.ServiceService;
import org.niis.xroad.catalog.lister.v2.service.SubsystemService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(BrowseController.class)
@Import(ApiExceptionHandler.class)
class BrowseControllerTest {

    private static final String PUB = "PUB";
    private static final String ABSENT_MEMBER_CLASS = "NOPE";
    private static final String ABSENT_MEMBER_CLASS_MESSAGE = "Member class 'NOPE' not found";
    private static final String CODE_14151328 = "14151328";
    private static final String CODE_14151329 = "14151329";
    private static final String MEMBER_NAME = "Nahka-Albert";
    private static final String SUBSYSTEM_A1 = "subsystem_a1";
    private static final String SUBSYSTEM_A3_REMOVED = "subsystem_a3_removed";
    private static final String SERVICE_GET_RANDOM = "getRandom";
    private static final String SERVICE_MIXED = "mixedSvc";
    private static final String SOAP = "SOAP";
    private static final String REST = "REST";
    private static final String MISSING = "missing";
    private static final String JSON_TOTAL_COUNT = "$.totalCount";
    private static final String JSON_MESSAGE = "$.message";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private MemberClassService memberClassService;

    @MockBean
    private MemberService memberService;

    @MockBean
    private SubsystemService subsystemService;

    @MockBean
    private ServiceService serviceService;

    @MockBean
    private SecurityServerService securityServerService;

    @Test
    void listMemberClassesReturnsFromListShape() throws Exception {
        when(memberClassService.list()).thenReturn(List.of(
                MemberClassDto.builder().code("GOV").description("Government").memberCount(2).build(),
                MemberClassDto.builder().code(PUB).description("Public").memberCount(5).build()));

        mockMvc.perform(get("/api/v2/browse/member-classes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].code").value("GOV"))
                .andExpect(jsonPath("$.items[0].description").value("Government"))
                .andExpect(jsonPath("$.items[0].memberCount").value(2))
                .andExpect(jsonPath("$.items[1].code").value(PUB))
                .andExpect(jsonPath(JSON_TOTAL_COUNT).value(2))
                .andExpect(content().string(not(containsString("\"page\""))))
                .andExpect(content().string(not(containsString("\"size\""))))
                .andExpect(content().string(not(containsString("\"totalPages\""))));
    }

    @Test
    void listMemberClassesReturnsEmpty() throws Exception {
        when(memberClassService.list()).thenReturn(List.of());

        mockMvc.perform(get("/api/v2/browse/member-classes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0))
                .andExpect(jsonPath(JSON_TOTAL_COUNT).value(0));
    }

    @Test
    void getMemberClassReturnsDto() throws Exception {
        when(memberClassService.getByCode(PUB)).thenReturn(Optional.of(
                MemberClassDto.builder().code(PUB).description("Public").memberCount(5).build()));

        mockMvc.perform(get("/api/v2/browse/member-classes/PUB"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(PUB))
                .andExpect(jsonPath("$.description").value("Public"))
                .andExpect(jsonPath("$.memberCount").value(5))
                .andExpect(jsonPath("$.items").doesNotExist())
                .andExpect(jsonPath(JSON_TOTAL_COUNT).doesNotExist());
    }

    @Test
    void getMemberClassReturns404WhenAbsent() throws Exception {
        when(memberClassService.getByCode(ABSENT_MEMBER_CLASS)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v2/browse/member-classes/NOPE"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("NotFound"))
                .andExpect(jsonPath(JSON_MESSAGE).value(ABSENT_MEMBER_CLASS_MESSAGE));
    }

    @Test
    void listMembersReturnsPagedResponseWithMetadata() throws Exception {
        when(memberClassService.getByCode(PUB)).thenReturn(Optional.of(MemberClassDto.builder().code(PUB).build()));
        MemberDto dto = memberDto(PUB, CODE_14151328, MEMBER_NAME);
        Page<MemberDto> page = new PageImpl<>(List.of(dto), PageRequest.of(0, 20), 1);
        when(memberService.getForList(eq(PUB), eq(null), any(Pageable.class))).thenReturn(page);

        mockMvc.perform(get("/api/v2/browse/member-classes/PUB/members"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].memberClass").value(PUB))
                .andExpect(jsonPath("$.items[0].memberCode").value(CODE_14151328))
                .andExpect(jsonPath("$.items[0].name").value(MEMBER_NAME))
                .andExpect(jsonPath(JSON_TOTAL_COUNT).value(1))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalPages").value(1));
    }

    @Test
    void listMembersDisallowedSortByReturns400() throws Exception {
        when(memberClassService.getByCode(PUB)).thenReturn(Optional.of(MemberClassDto.builder().code(PUB).build()));

        mockMvc.perform(get("/api/v2/browse/member-classes/PUB/members?sortBy=bogus"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("BadRequest"));
    }

    @Test
    void listMembersUnknownMemberClassReturns404() throws Exception {
        when(memberClassService.getByCode(ABSENT_MEMBER_CLASS)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v2/browse/member-classes/NOPE/members"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("NotFound"))
                .andExpect(jsonPath(JSON_MESSAGE).value(ABSENT_MEMBER_CLASS_MESSAGE));
    }

    @Test
    void listMembersTranslatesCreatedSortToEmbeddedPath() throws Exception {
        when(memberClassService.getByCode(PUB)).thenReturn(Optional.of(MemberClassDto.builder().code(PUB).build()));
        when(memberService.getForList(eq(PUB), eq(null), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        mockMvc.perform(get("/api/v2/browse/member-classes/{mc}/members", PUB)
                        .param("sortBy", "created").param("sortOrder", "desc"))
                .andExpect(status().isOk());

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(memberService).getForList(eq(PUB), eq(null), captor.capture());
        Sort.Order primary = captor.getValue().getSort().stream().findFirst().orElseThrow();
        assertThat(primary.getProperty()).isEqualTo("statusInfo.created");
        assertThat(primary.getDirection()).isEqualTo(Sort.Direction.DESC);
    }

    @Test
    void getMemberReturnsDtoWithoutPaginationMetadata() throws Exception {
        MemberDto dto = memberDto(PUB, CODE_14151328, MEMBER_NAME);
        when(memberService.getByNaturalKey(PUB, CODE_14151328)).thenReturn(Optional.of(dto));

        mockMvc.perform(get("/api/v2/browse/member-classes/PUB/members/14151328"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.memberClass").value(PUB))
                .andExpect(jsonPath("$.memberCode").value(CODE_14151328))
                .andExpect(jsonPath("$.name").value(MEMBER_NAME))
                .andExpect(jsonPath("$.items").doesNotExist())
                .andExpect(jsonPath(JSON_TOTAL_COUNT).doesNotExist())
                .andExpect(jsonPath("$.page").doesNotExist());
    }

    @Test
    void getMemberRemovedReturns404() throws Exception {
        when(memberService.getByNaturalKey(PUB, CODE_14151329)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v2/browse/member-classes/PUB/members/" + CODE_14151329))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("NotFound"))
                .andExpect(jsonPath(JSON_MESSAGE).value("Member 'PUB/14151329' not found"));
    }

    @Test
    void getMemberNonexistentReturns404() throws Exception {
        when(memberService.getByNaturalKey(PUB, MISSING)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v2/browse/member-classes/PUB/members/missing"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getMemberFullTrueReturnsNestedStructure() throws Exception {
        FullMemberDto fullDto = fullMemberDto();
        when(memberService.getFullTree(PUB, CODE_14151328)).thenReturn(Optional.of(fullDto));

        mockMvc.perform(get("/api/v2/browse/member-classes/PUB/members/14151328?full=true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.memberClass").value(PUB))
                .andExpect(jsonPath("$.memberCode").value(CODE_14151328))
                .andExpect(jsonPath("$.provider").value(true))
                .andExpect(jsonPath("$.subsystems.length()").value(1))
                .andExpect(jsonPath("$.subsystems[0].subsystemCode").value(SUBSYSTEM_A1))
                .andExpect(jsonPath("$.subsystems[0].services.length()").value(1))
                .andExpect(jsonPath("$.subsystems[0].services[0].serviceCode").value("getRandom"))
                .andExpect(jsonPath("$.subsystems[0].services[0].versions.length()").value(1))
                .andExpect(jsonPath("$.subsystems[0].services[0].versions[0].serviceVersion").value("v1"))
                .andExpect(jsonPath("$.subsystems[0].services[0].versions[0].hasDescriptor").doesNotExist())
                .andExpect(jsonPath("$.subsystems[0].services[0].versions[0].endpoints").doesNotExist());
    }

    @Test
    void getMemberFullTrueReturns404WhenAbsent() throws Exception {
        when(memberService.getFullTree(PUB, MISSING)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v2/browse/member-classes/PUB/members/missing?full=true"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void listSubsystemsReturnsItemsWithoutPaginationMetadata() throws Exception {
        // SubsystemService#getForMember encodes parent existence via Optional, so there is no separate member guard call.
        SubsystemDto a1 = subsystemDto(SUBSYSTEM_A1);
        SubsystemDto a2 = subsystemDto("subsystem_a2");
        when(subsystemService.getForMember(PUB, CODE_14151328)).thenReturn(Optional.of(List.of(a1, a2)));

        mockMvc.perform(get("/api/v2/browse/member-classes/PUB/members/14151328/subsystems"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].subsystemCode").value(SUBSYSTEM_A1))
                .andExpect(jsonPath("$.items[1].subsystemCode").value("subsystem_a2"))
                .andExpect(jsonPath(JSON_TOTAL_COUNT).value(2))
                .andExpect(content().string(not(containsString("\"page\""))))
                .andExpect(content().string(not(containsString("\"size\""))))
                .andExpect(content().string(not(containsString("\"totalPages\""))));
    }

    @Test
    void listSubsystemsRemovedMemberReturns404() throws Exception {
        when(subsystemService.getForMember(PUB, CODE_14151329)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v2/browse/member-classes/PUB/members/" + CODE_14151329 + "/subsystems"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("NotFound"))
                .andExpect(jsonPath(JSON_MESSAGE).value("Member 'PUB/14151329' not found"));
    }

    @Test
    void listSubsystemsNonexistentMemberReturns404() throws Exception {
        when(subsystemService.getForMember(PUB, MISSING)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v2/browse/member-classes/PUB/members/missing/subsystems"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getSubsystemReturnsDto() throws Exception {
        SubsystemDto dto = subsystemDto(SUBSYSTEM_A1);
        when(subsystemService.getByNaturalKey(PUB, CODE_14151328, SUBSYSTEM_A1)).thenReturn(Optional.of(dto));

        mockMvc.perform(get("/api/v2/browse/member-classes/PUB/members/14151328/subsystems/subsystem_a1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.memberClass").value(PUB))
                .andExpect(jsonPath("$.memberCode").value(CODE_14151328))
                .andExpect(jsonPath("$.subsystemCode").value(SUBSYSTEM_A1))
                .andExpect(jsonPath("$.items").doesNotExist())
                .andExpect(jsonPath(JSON_TOTAL_COUNT).doesNotExist());
    }

    @Test
    void getSubsystemRemovedReturns404() throws Exception {
        when(subsystemService.getByNaturalKey(PUB, CODE_14151328, SUBSYSTEM_A3_REMOVED)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v2/browse/member-classes/PUB/members/14151328/subsystems/subsystem_a3_removed"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("NotFound"))
                .andExpect(jsonPath(JSON_MESSAGE).value("Subsystem 'PUB/14151328/subsystem_a3_removed' not found"));
    }

    @Test
    void getSubsystemNonexistentReturns404() throws Exception {
        when(subsystemService.getByNaturalKey(PUB, CODE_14151328, MISSING)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v2/browse/member-classes/PUB/members/14151328/subsystems/missing"))
                .andExpect(status().isNotFound());
    }

    @Test
    void listServicesReturnsAggregatesWithoutPaginationMetadata() throws Exception {
        // ServiceService#getForSubsystem encodes parent existence via Optional, so there is no separate subsystem guard call.
        ServiceDto getRandom = serviceDto(SERVICE_GET_RANDOM, List.of(versionSummary("v1", SOAP)));
        ServiceDto mixed = serviceDto(SERVICE_MIXED, List.of(
                versionSummary("v1", SOAP),
                versionSummary("v2", REST)));
        when(serviceService.getForSubsystem(PUB, CODE_14151328, SUBSYSTEM_A1))
                .thenReturn(Optional.of(List.of(getRandom, mixed)));

        mockMvc.perform(get(serviceCollectionPath(SUBSYSTEM_A1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].serviceCode").value(SERVICE_GET_RANDOM))
                .andExpect(jsonPath("$.items[1].serviceCode").value(SERVICE_MIXED))
                .andExpect(jsonPath("$.items[1].versionCount").value(2))
                .andExpect(jsonPath("$.items[1].versions[0].serviceVersion").value("v1"))
                .andExpect(jsonPath("$.items[1].versions[0].hasDescriptor").doesNotExist())
                .andExpect(jsonPath("$.items[1].versions[0].endpoints").doesNotExist())
                .andExpect(jsonPath(JSON_TOTAL_COUNT).value(2))
                .andExpect(content().string(not(containsString("\"page\""))))
                .andExpect(content().string(not(containsString("\"size\""))))
                .andExpect(content().string(not(containsString("\"totalPages\""))));
    }

    @Test
    void listServicesRemovedSubsystemReturns404() throws Exception {
        when(serviceService.getForSubsystem(PUB, CODE_14151328, SUBSYSTEM_A3_REMOVED)).thenReturn(Optional.empty());

        mockMvc.perform(get(serviceCollectionPath(SUBSYSTEM_A3_REMOVED)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath(JSON_MESSAGE).value("Subsystem 'PUB/14151328/subsystem_a3_removed' not found"));
    }

    @Test
    void listServicesNonexistentMemberReturns404() throws Exception {
        when(serviceService.getForSubsystem(PUB, MISSING, SUBSYSTEM_A1)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v2/browse/member-classes/PUB/members/missing/subsystems/" + SUBSYSTEM_A1 + "/services"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getServiceReturnsAggregateWithSummaryVersions() throws Exception {
        ServiceDto dto = serviceDto(SERVICE_MIXED, List.of(
                versionSummary("v1", SOAP),
                versionSummary("v2", REST)));
        when(serviceService.getByNaturalKey(PUB, CODE_14151328, SUBSYSTEM_A1, SERVICE_MIXED)).thenReturn(Optional.of(dto));

        mockMvc.perform(get(servicePath(SUBSYSTEM_A1, SERVICE_MIXED)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.serviceCode").value(SERVICE_MIXED))
                .andExpect(jsonPath("$.versionCount").value(2))
                .andExpect(jsonPath("$.versions[0].serviceVersion").value("v1"))
                .andExpect(jsonPath("$.versions[0].hasDescriptor").doesNotExist())
                .andExpect(jsonPath("$.versions[0].endpoints").doesNotExist())
                .andExpect(jsonPath("$.versions[1].hasDescriptor").doesNotExist());
    }

    @Test
    void getServiceAllVersionsRemovedReturns404ByDefault() throws Exception {
        when(serviceService.getByNaturalKey(PUB, CODE_14151328, SUBSYSTEM_A1, SERVICE_MIXED)).thenReturn(Optional.empty());

        mockMvc.perform(get(servicePath(SUBSYSTEM_A1, SERVICE_MIXED)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath(JSON_MESSAGE).value("Service 'PUB/14151328/subsystem_a1/mixedSvc' not found"));
    }

    @Test
    void getServiceNonexistentReturns404() throws Exception {
        when(serviceService.getByNaturalKey(PUB, CODE_14151328, SUBSYSTEM_A1, MISSING)).thenReturn(Optional.empty());

        mockMvc.perform(get(servicePath(SUBSYSTEM_A1, MISSING)))
                .andExpect(status().isNotFound());
    }

    @Test
    void listServiceVersionsReturnsFullShape() throws Exception {
        ServiceVersionDto v1 = versionFull("v1", SOAP, true, List.of(
                EndpointDto.builder().method("GET").path("/getRandom").build()));
        ServiceVersionDto v2 = versionFull("v2", REST, true, List.of());
        when(serviceService.getVersions(PUB, CODE_14151328, SUBSYSTEM_A1, SERVICE_MIXED))
                .thenReturn(Optional.of(List.of(v1, v2)));

        mockMvc.perform(get(versionsPath(SUBSYSTEM_A1, SERVICE_MIXED)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].serviceVersion").value("v1"))
                .andExpect(jsonPath("$.items[0].hasDescriptor").value(true))
                .andExpect(jsonPath("$.items[0].endpoints.length()").value(1))
                .andExpect(jsonPath("$.items[0].endpoints[0].method").value("GET"))
                .andExpect(jsonPath("$.items[0].endpoints[0].path").value("/getRandom"))
                .andExpect(jsonPath(JSON_TOTAL_COUNT).value(2))
                .andExpect(jsonPath("$.page").doesNotExist());
    }

    @Test
    void listServiceVersionsNonexistentServiceReturns404() throws Exception {
        when(serviceService.getVersions(PUB, CODE_14151328, SUBSYSTEM_A1, MISSING)).thenReturn(Optional.empty());

        mockMvc.perform(get(versionsPath(SUBSYSTEM_A1, MISSING)))
                .andExpect(status().isNotFound());
    }

    @Test
    void getServiceVersionReturnsFullShape() throws Exception {
        ServiceVersionDto v = versionFull("v1", SOAP, true, List.of());
        when(serviceService.getVersion(PUB, CODE_14151328, SUBSYSTEM_A1, SERVICE_MIXED, "v1")).thenReturn(Optional.of(v));

        mockMvc.perform(get(versionsPath(SUBSYSTEM_A1, SERVICE_MIXED) + "/v1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.serviceVersion").value("v1"))
                .andExpect(jsonPath("$.serviceType").value(SOAP))
                .andExpect(jsonPath("$.hasDescriptor").value(true))
                .andExpect(jsonPath("$.endpoints").isArray());
    }

    @Test
    void getServiceVersionNullSentinelPassesRawLiteralToService() throws Exception {
        // Literal "null" in the URL maps to a null version; the raw "null" must reach the
        // service layer, which resolves the sentinel internally.
        ServiceVersionDto nullVersion = versionFull(null, REST, false, List.of());
        when(serviceService.getVersion(PUB, CODE_14151328, SUBSYSTEM_A1, SERVICE_MIXED, "null"))
                .thenReturn(Optional.of(nullVersion));

        mockMvc.perform(get(versionsPath(SUBSYSTEM_A1, SERVICE_MIXED) + "/null"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.serviceVersion").value(nullValue()))
                .andExpect(jsonPath("$.serviceType").value(REST));
    }

    @Test
    void getServiceVersionNonexistentReturns404() throws Exception {
        when(serviceService.getVersion(PUB, CODE_14151328, SUBSYSTEM_A1, SERVICE_MIXED, "v9")).thenReturn(Optional.empty());

        mockMvc.perform(get(versionsPath(SUBSYSTEM_A1, SERVICE_MIXED) + "/v9"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath(JSON_MESSAGE).value(
                        "Service version 'PUB/14151328/subsystem_a1/mixedSvc/v9' not found"));
    }

    @Test
    void listSecurityServersForMemberReturnsItemsWithoutPaginationMetadata() throws Exception {
        when(memberService.existsActive(PUB, CODE_14151328)).thenReturn(true);
        SecurityServerBrowseItemDto srv1 = securityServerItem("ss-alpha", "10.0.0.1");
        SecurityServerBrowseItemDto srv2 = securityServerItem("ss-beta", "10.0.0.2");
        when(securityServerService.getForMember(PUB, CODE_14151328)).thenReturn(List.of(srv1, srv2));

        mockMvc.perform(get(securityServersPath(CODE_14151328)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].serverCode").value("ss-alpha"))
                .andExpect(jsonPath("$.items[0].address").value("10.0.0.1"))
                .andExpect(jsonPath("$.items[0].owner.memberClass").value(PUB))
                .andExpect(jsonPath("$.items[0].owner.memberCode").value(CODE_14151328))
                .andExpect(jsonPath("$.items[1].serverCode").value("ss-beta"))
                .andExpect(jsonPath(JSON_TOTAL_COUNT).value(2))
                .andExpect(content().string(not(containsString("\"page\""))))
                .andExpect(content().string(not(containsString("\"size\""))))
                .andExpect(content().string(not(containsString("\"totalPages\""))));
    }

    @Test
    void listSecurityServersForMemberMissingMemberReturns404() throws Exception {
        when(memberService.existsActive(PUB, MISSING)).thenReturn(false);

        mockMvc.perform(get(securityServersPath(MISSING)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("NotFound"))
                .andExpect(jsonPath(JSON_MESSAGE).value("Member 'PUB/missing' not found"));
    }

    @Test
    void listSecurityServersForMemberRemovedMemberReturns404() throws Exception {
        // Removed members are looked up active-only and yield 404.
        when(memberService.existsActive(PUB, CODE_14151329)).thenReturn(false);

        mockMvc.perform(get(securityServersPath(CODE_14151329)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath(JSON_MESSAGE).value("Member 'PUB/14151329' not found"));
    }

    @Test
    void listSecurityServersForMemberEmptyListReturns200WithZeroTotal() throws Exception {
        when(memberService.existsActive(PUB, CODE_14151328)).thenReturn(true);
        when(securityServerService.getForMember(PUB, CODE_14151328)).thenReturn(List.of());

        mockMvc.perform(get(securityServersPath(CODE_14151328)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0))
                .andExpect(jsonPath(JSON_TOTAL_COUNT).value(0))
                .andExpect(content().string(not(containsString("\"page\""))))
                .andExpect(content().string(not(containsString("\"size\""))))
                .andExpect(content().string(not(containsString("\"totalPages\""))));
    }

    private String securityServersPath(String memberCode) {
        return "/api/v2/browse/member-classes/PUB/members/" + memberCode + "/security-servers";
    }

    private SecurityServerBrowseItemDto securityServerItem(String serverCode, String address) {
        return SecurityServerBrowseItemDto.builder()
                .serverCode(serverCode)
                .address(address)
                .owner(SecurityServerOwnerDto.builder()
                        .memberClass(PUB).memberCode(CODE_14151328).name(MEMBER_NAME).build())
                .clients(List.<SecurityServerClientDto>of())
                .build();
    }

    private String serviceCollectionPath(String subsystemCode) {
        return "/api/v2/browse/member-classes/PUB/members/14151328/subsystems/" + subsystemCode + "/services";
    }

    private String servicePath(String subsystemCode, String serviceCode) {
        return serviceCollectionPath(subsystemCode) + "/" + serviceCode;
    }

    private String versionsPath(String subsystemCode, String serviceCode) {
        return servicePath(subsystemCode, serviceCode) + "/versions";
    }

    private ServiceDto serviceDto(String serviceCode, List<ServiceVersionSummaryDto> versions) {
        return ServiceDto.builder()
                .memberClass(PUB)
                .memberCode(CODE_14151328)
                .memberName(MEMBER_NAME)
                .subsystemCode(SUBSYSTEM_A1)
                .serviceCode(serviceCode)
                .versionCount(versions.size())
                .versions(versions)
                .serviceTypes(versions.stream().map(ServiceVersionSummaryDto::getServiceType).distinct().toList())
                .build();
    }

    private ServiceVersionSummaryDto versionSummary(String serviceVersion, String serviceType) {
        LocalDateTime now = LocalDateTime.of(2016, 1, 1, 0, 0);
        return ServiceVersionSummaryDto.builder()
                .serviceVersion(serviceVersion)
                .serviceType(serviceType)
                .created(now)
                .changed(now)
                .fetched(now)
                .build();
    }

    private ServiceVersionDto versionFull(String serviceVersion, String serviceType, boolean hasDescriptor,
                                          List<EndpointDto> endpoints) {
        LocalDateTime now = LocalDateTime.of(2016, 1, 1, 0, 0);
        return ServiceVersionDto.builder()
                .serviceVersion(serviceVersion)
                .serviceType(serviceType)
                .hasDescriptor(hasDescriptor)
                .endpoints(endpoints)
                .created(now)
                .changed(now)
                .fetched(now)
                .build();
    }

    private SubsystemDto subsystemDto(String subsystemCode) {
        LocalDateTime now = LocalDateTime.of(2016, 1, 1, 0, 0);
        return SubsystemDto.builder()
                .memberClass(PUB)
                .memberCode(CODE_14151328)
                .memberName(MEMBER_NAME)
                .subsystemCode(subsystemCode)
                .subsystemName(null)
                .serviceCount(0)
                .created(now)
                .changed(now)
                .fetched(now)
                .build();
    }

    private MemberDto memberDto(String memberClass, String memberCode, String name) {
        LocalDateTime now = LocalDateTime.of(2016, 1, 1, 0, 0);
        return MemberDto.builder()
                .memberClass(memberClass)
                .memberCode(memberCode)
                .name(name)
                .isProvider(true)
                .subsystemCount(2)
                .serviceCount(4)
                .created(now)
                .changed(now)
                .fetched(now)
                .build();
    }

    private FullMemberDto fullMemberDto() {
        LocalDateTime now = LocalDateTime.of(2016, 1, 1, 0, 0);
        ServiceVersionSummaryDto version = ServiceVersionSummaryDto.builder()
                .serviceVersion("v1")
                .serviceType("SOAP")
                .created(now)
                .changed(now)
                .fetched(now)
                .build();
        ServiceDto service = ServiceDto.builder()
                .memberClass(PUB)
                .memberCode("14151328")
                .memberName("Nahka-Albert")
                .subsystemCode(SUBSYSTEM_A1)
                .serviceCode("getRandom")
                .versionCount(1)
                .versions(List.of(version))
                .serviceTypes(List.of("SOAP"))
                .build();
        FullSubsystemDto sub = FullSubsystemDto.builder()
                .memberClass(PUB)
                .memberCode("14151328")
                .memberName("Nahka-Albert")
                .subsystemCode(SUBSYSTEM_A1)
                .subsystemName(null)
                .serviceCount(1)
                .created(now)
                .changed(now)
                .fetched(now)
                .services(List.of(service))
                .build();
        return FullMemberDto.builder()
                .memberClass(PUB)
                .memberCode("14151328")
                .name("Nahka-Albert")
                .isProvider(true)
                .subsystemCount(1)
                .serviceCount(1)
                .created(now)
                .changed(now)
                .fetched(now)
                .subsystems(List.of(sub))
                .build();
    }

}
