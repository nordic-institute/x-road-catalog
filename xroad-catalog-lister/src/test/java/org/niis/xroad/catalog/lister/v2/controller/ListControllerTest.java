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
import org.niis.xroad.catalog.lister.v2.dto.MemberDto;
import org.niis.xroad.catalog.lister.v2.dto.SecurityServerListItemDto;
import org.niis.xroad.catalog.lister.v2.dto.SecurityServerOwnerDto;
import org.niis.xroad.catalog.lister.v2.dto.ServiceDto;
import org.niis.xroad.catalog.lister.v2.dto.SubsystemDto;
import org.niis.xroad.catalog.lister.v2.service.MemberServiceV2;
import org.niis.xroad.catalog.lister.v2.service.SecurityServerServiceV2;
import org.niis.xroad.catalog.lister.v2.service.ServiceServiceV2;
import org.niis.xroad.catalog.lister.v2.service.SubsystemServiceV2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ListController.class)
@Import({V2ExceptionHandler.class, V2DispatchExceptionHandler.class})
class ListControllerTest {

    private static final String LIST_SECURITY_SERVERS_PATH = "/api/v2/list/security-servers";
    private static final String LIST_MEMBERS_PATH = "/api/v2/list/members";
    private static final String LIST_SUBSYSTEMS_PATH = "/api/v2/list/subsystems";
    private static final String LIST_SERVICES_PATH = "/api/v2/list/services";
    private static final String SERVICE_TYPE = "serviceType";
    private static final String MEMBER_CLASS_PARAM = "memberClass";
    private static final String PUB = "PUB";
    private static final String SORT_BY = "sortBy";
    private static final String SERVICE_COUNT = "serviceCount";
    private static final String JSON_ERROR = "$.error";
    private static final String JSON_MESSAGE = "$.message";
    private static final String JSON_STATUS = "$.status";
    private static final String BAD_REQUEST_ERROR = "BadRequest";
    private static final String METHOD_NOT_ALLOWED_ERROR = "MethodNotAllowed";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private MemberServiceV2 memberService;

    @MockBean
    private SubsystemServiceV2 subsystemService;

    @MockBean
    private ServiceServiceV2 serviceService;

    @MockBean
    private SecurityServerServiceV2 securityServerService;

    @Test
    void listSecurityServersReturnsPagedShape() throws Exception {
        SecurityServerListItemDto dto = SecurityServerListItemDto.builder()
                .serverCode("SS1").address("10.0.0.1")
                .owner(SecurityServerOwnerDto.builder()
                        .memberClass("ORG").memberCode("2908758-4").name("NIIS").build())
                .clientCount(2).build();
        when(securityServerService.list(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(dto), PageRequest.of(0, 20), 1));

        mockMvc.perform(get(LIST_SECURITY_SERVERS_PATH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].serverCode").value("SS1"))
                .andExpect(jsonPath("$.items[0].address").value("10.0.0.1"))
                .andExpect(jsonPath("$.items[0].owner.memberClass").value("ORG"))
                .andExpect(jsonPath("$.items[0].owner.memberCode").value("2908758-4"))
                .andExpect(jsonPath("$.items[0].owner.name").value("NIIS"))
                .andExpect(jsonPath("$.items[0].clientCount").value(2))
                .andExpect(jsonPath("$.totalCount").value(1))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalPages").value(1));
    }

    @Test
    void listSecurityServersRejectsInvalidSortField() throws Exception {
        mockMvc.perform(get(LIST_SECURITY_SERVERS_PATH).param("sortBy", "owner"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath(JSON_ERROR).value(BAD_REQUEST_ERROR))
                .andExpect(jsonPath(JSON_MESSAGE).value(
                        Matchers.containsString("Invalid sort field")));
    }

    @Test
    void listSecurityServersRejectsInvalidSortOrder() throws Exception {
        mockMvc.perform(get(LIST_SECURITY_SERVERS_PATH).param("sortOrder", "weird"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath(JSON_MESSAGE).value(
                        Matchers.containsString("Invalid sort order")));
    }

    @Test
    void listSecurityServersAcceptsAddressSort() throws Exception {
        when(securityServerService.list(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        mockMvc.perform(get(LIST_SECURITY_SERVERS_PATH)
                        .param("sortBy", "address").param("sortOrder", "desc"))
                .andExpect(status().isOk());

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(securityServerService).list(captor.capture());
        Sort.Order primary = captor.getValue().getSort().stream().findFirst().orElseThrow();
        assertThat(primary.getProperty()).isEqualTo("address");
        assertThat(primary.getDirection()).isEqualTo(Sort.Direction.DESC);
    }

    @Test
    void invalidIntegerPageReturns400() throws Exception {
        mockMvc.perform(get(LIST_SECURITY_SERVERS_PATH).param("page", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath(JSON_ERROR).value(BAD_REQUEST_ERROR))
                .andExpect(jsonPath(JSON_MESSAGE).value(
                        Matchers.containsString("page")));
    }

    @Test
    void invalidIntegerSizeReturns400() throws Exception {
        mockMvc.perform(get(LIST_SECURITY_SERVERS_PATH).param("size", "twenty"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath(JSON_MESSAGE).value(
                        Matchers.containsString("size")));
    }

    @Test
    void postToListSecurityServersReturns405() throws Exception {
        mockMvc.perform(post(LIST_SECURITY_SERVERS_PATH))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath(JSON_STATUS).value(405))
                .andExpect(jsonPath(JSON_ERROR).value(METHOD_NOT_ALLOWED_ERROR))
                .andExpect(header().string("Allow", "GET"));
    }

    @Test
    void postToListSecurityServersUnderContextPathReturns405() throws Exception {
        mockMvc.perform(post("/catalog" + LIST_SECURITY_SERVERS_PATH).contextPath("/catalog"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath(JSON_STATUS).value(405))
                .andExpect(jsonPath(JSON_ERROR).value(METHOD_NOT_ALLOWED_ERROR))
                .andExpect(header().string("Allow", "GET"));
    }

    @Test
    void listMembersHonorsFilters() throws Exception {
        MemberDto m = MemberDto.builder().memberClass(PUB).memberCode("123").name("Alice")
                .isProvider(true).subsystemCount(2).serviceCount(7).build();
        when(memberService.getForList(eq(PUB), eq(Boolean.TRUE), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(m), PageRequest.of(0, 20), 1));

        mockMvc.perform(get(LIST_MEMBERS_PATH)
                        .param(MEMBER_CLASS_PARAM, PUB).param("provider", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].memberClass").value(PUB))
                .andExpect(jsonPath("$.items[0].memberCode").value("123"))
                .andExpect(jsonPath("$.items[0].name").value("Alice"))
                .andExpect(jsonPath("$.items[0].provider").value(true))
                .andExpect(jsonPath("$.items[0].subsystemCount").value(2))
                .andExpect(jsonPath("$.items[0].serviceCount").value(7))
                .andExpect(jsonPath("$.totalCount").value(1));
    }

    @Test
    void listMembersAppliesDefaultSort() throws Exception {
        when(memberService.getForList(eq(null), eq(null), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        mockMvc.perform(get(LIST_MEMBERS_PATH)).andExpect(status().isOk());

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(memberService).getForList(eq(null), eq(null), captor.capture());
        Sort.Order primary = captor.getValue().getSort().stream().findFirst().orElseThrow();
        assertThat(primary.getProperty()).isEqualTo("name");
        assertThat(primary.getDirection()).isEqualTo(Sort.Direction.ASC);
    }

    @Test
    void listMembersTranslatesCreatedSortToEmbeddedPath() throws Exception {
        when(memberService.getForList(eq(null), eq(null), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        mockMvc.perform(get(LIST_MEMBERS_PATH).param("sortBy", "created"))
                .andExpect(status().isOk());

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(memberService).getForList(eq(null), eq(null), captor.capture());
        Sort.Order primary = captor.getValue().getSort().stream().findFirst().orElseThrow();
        assertThat(primary.getProperty()).isEqualTo("statusInfo.created");
    }

    @Test
    void listMembersRejectsInvalidProvider() throws Exception {
        mockMvc.perform(get(LIST_MEMBERS_PATH).param("provider", "yes"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath(JSON_MESSAGE).value(
                        Matchers.containsString("provider")));
    }

    @Test
    void listMembersRejectsInvalidSortField() throws Exception {
        mockMvc.perform(get(LIST_MEMBERS_PATH).param("sortBy", "memberClass"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath(JSON_MESSAGE).value(
                        Matchers.containsString("Invalid sort field")));
    }

    @Test
    void listSubsystemsReturnsParentContextFields() throws Exception {
        SubsystemDto dto = SubsystemDto.builder()
                .memberClass(PUB).memberCode("123").memberName("Alice")
                .subsystemCode("ss1").subsystemName("First").serviceCount(3).build();
        when(subsystemService.getForList(eq(PUB), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(dto), PageRequest.of(0, 20), 1));

        mockMvc.perform(get(LIST_SUBSYSTEMS_PATH).param(MEMBER_CLASS_PARAM, PUB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].memberClass").value(PUB))
                .andExpect(jsonPath("$.items[0].memberCode").value("123"))
                .andExpect(jsonPath("$.items[0].memberName").value("Alice"))
                .andExpect(jsonPath("$.items[0].subsystemCode").value("ss1"))
                .andExpect(jsonPath("$.items[0].subsystemName").value("First"))
                .andExpect(jsonPath("$.items[0].serviceCount").value(3));
    }

    @Test
    void listSubsystemsRejectsServiceCountSort() throws Exception {
        mockMvc.perform(get(LIST_SUBSYSTEMS_PATH).param(SORT_BY, SERVICE_COUNT))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath(JSON_MESSAGE).value(
                        Matchers.containsString("Invalid sort field")));
    }

    @Test
    void listSubsystemsTranslatesCreatedSort() throws Exception {
        when(subsystemService.getForList(eq(null), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        mockMvc.perform(get(LIST_SUBSYSTEMS_PATH).param(SORT_BY, "changed").param("sortOrder", "desc"))
                .andExpect(status().isOk());

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(subsystemService).getForList(eq(null), captor.capture());
        Sort.Order primary = captor.getValue().getSort().stream().findFirst().orElseThrow();
        assertThat(primary.getProperty()).isEqualTo("statusInfo.changed");
        assertThat(primary.getDirection()).isEqualTo(Sort.Direction.DESC);
    }

    @Test
    void listSubsystemsRejectsInvalidSortField() throws Exception {
        mockMvc.perform(get(LIST_SUBSYSTEMS_PATH).param(SORT_BY, "memberCode"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listServicesReturnsAggregateShape() throws Exception {
        ServiceDto dto = ServiceDto.builder()
                .memberClass(PUB).memberCode("123").memberName("Alice").subsystemCode("ss1")
                .serviceCode("getThing").serviceTypes(List.of("SOAP", "REST")).versionCount(2)
                .versions(List.of()).build();
        when(serviceService.getForList(eq(PUB), eq("SOAP"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(dto), PageRequest.of(0, 20), 1));

        mockMvc.perform(get(LIST_SERVICES_PATH)
                        .param(MEMBER_CLASS_PARAM, PUB).param(SERVICE_TYPE, "SOAP"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].memberClass").value(PUB))
                .andExpect(jsonPath("$.items[0].memberCode").value("123"))
                .andExpect(jsonPath("$.items[0].memberName").value("Alice"))
                .andExpect(jsonPath("$.items[0].subsystemCode").value("ss1"))
                .andExpect(jsonPath("$.items[0].serviceCode").value("getThing"))
                .andExpect(jsonPath("$.items[0].serviceTypes[0]").value("SOAP"))
                .andExpect(jsonPath("$.items[0].versionCount").value(2));
    }

    @Test
    void listServicesRejectsInvalidServiceType() throws Exception {
        mockMvc.perform(get(LIST_SERVICES_PATH).param(SERVICE_TYPE, "GRAPHQL"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath(JSON_MESSAGE).value(
                        Matchers.containsString(SERVICE_TYPE)));
    }

    @Test
    void listServicesAcceptsAllowedServiceTypes() throws Exception {
        when(serviceService.getForList(eq(null), anyString(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));
        for (String t : new String[] {"SOAP", "REST", "OPENAPI"}) {
            mockMvc.perform(get(LIST_SERVICES_PATH).param(SERVICE_TYPE, t))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void listServicesUsesUnsortedPageable() throws Exception {
        when(serviceService.getForList(eq(null), eq(null), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        mockMvc.perform(get(LIST_SERVICES_PATH).param(SORT_BY, "versionCount"))
                .andExpect(status().isOk());

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(serviceService).getForList(eq(null), eq(null), captor.capture());
        assertThat(captor.getValue().getSort().isUnsorted()).isTrue();
    }
}
