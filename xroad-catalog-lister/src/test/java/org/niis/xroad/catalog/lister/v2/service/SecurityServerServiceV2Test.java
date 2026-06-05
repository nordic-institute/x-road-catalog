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
package org.niis.xroad.catalog.lister.v2.service;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.niis.xroad.catalog.lister.dto.MemberInfo;
import org.niis.xroad.catalog.lister.dto.SecurityServerData;
import org.niis.xroad.catalog.lister.dto.SecurityServerDataList;
import org.niis.xroad.catalog.lister.parser.SharedParamsParser;
import org.niis.xroad.catalog.lister.v2.dto.SecurityServerBrowseItemDto;
import org.niis.xroad.catalog.lister.v2.dto.SecurityServerListItemDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@TestPropertySource(properties = {"xroad-catalog.shared-params-file=src/test/resources/shared-params-2.xml"})
@ActiveProfiles({"test", "general-testdata"})
public class SecurityServerServiceV2Test {

    private static final String ORG = "ORG";
    private static final String NIIS_CODE = "2908758-4";
    private static final String NIISSS01 = "niisss01";
    private static final String NIISSS02 = "niisss02";

    @Autowired
    private SecurityServerServiceV2 service;

    @Test
    public void testListReturnsServersWithClientCount() {
        Page<SecurityServerListItemDto> page = service.list(PageRequest.of(0, 20));
        assertFalse(page.isEmpty());
        // shared-params-2.xml has 4 security servers: niisss01, testcomss01, testagess01, niisss02
        assertTrue(page.getTotalElements() >= 4);
        SecurityServerListItemDto niisss01 = page.getContent().stream()
                .filter(s -> NIISSS01.equals(s.getServerCode()))
                .findFirst()
                .orElseThrow();
        assertEquals("10.0.0.1", niisss01.getAddress());
        assertEquals(2, niisss01.getClientCount(), "niisss01 has 2 clients (Management, MonitoringClient)");
    }

    @Test
    public void testGetForMemberReturnsBrowseShape() {
        // Member id0 (ORG/2908758-4 NIIS) owns niisss01 and niisss02
        List<SecurityServerBrowseItemDto> servers = service.getForMember(ORG, NIIS_CODE);
        assertEquals(2, servers.size());
        servers.forEach(s -> {
            assertEquals(ORG, s.getOwner().getMemberClass());
            assertEquals(NIIS_CODE, s.getOwner().getMemberCode());
        });
        SecurityServerBrowseItemDto niisss01 = servers.stream()
                .filter(s -> NIISSS01.equals(s.getServerCode()))
                .findFirst()
                .orElseThrow();
        assertFalse(niisss01.getClients().isEmpty(), "browse shape must include full clients list");
    }

    @Test
    public void testGetForMemberSortsByServerCodeAscending() throws Exception {
        // Build a parser stub returning servers in non-alphabetical order under a single owner.
        SecurityServerServiceV2 isolated = new SecurityServerServiceV2();
        SharedParamsParser stubParser = Mockito.mock(SharedParamsParser.class);
        MemberInfo owner = MemberInfo.builder().memberClass(ORG).memberCode(NIIS_CODE).name("NIIS").build();
        SecurityServerData zSrv = SecurityServerData.builder()
                .owner(owner).serverCode("zzz-server").address("10.0.0.9").clients(List.of()).build();
        SecurityServerData mSrv = SecurityServerData.builder()
                .owner(owner).serverCode("mmm-server").address("10.0.0.5").clients(List.of()).build();
        SecurityServerData aSrv = SecurityServerData.builder()
                .owner(owner).serverCode("aaa-server").address("10.0.0.1").clients(List.of()).build();
        SecurityServerDataList parsed = new SecurityServerDataList();
        parsed.setSecurityServerDataList(List.of(zSrv, mSrv, aSrv));
        Mockito.when(stubParser.parseDetails(Mockito.anyString())).thenReturn(parsed);
        ReflectionTestUtils.setField(isolated, "parser", stubParser);
        ReflectionTestUtils.setField(isolated, "sharedParamsFile", "stub.xml");

        List<SecurityServerBrowseItemDto> servers = isolated.getForMember(ORG, NIIS_CODE);
        assertEquals(3, servers.size());
        assertEquals("aaa-server", servers.get(0).getServerCode());
        assertEquals("mmm-server", servers.get(1).getServerCode());
        assertEquals("zzz-server", servers.get(2).getServerCode());
    }

    @Test
    public void testGetForMemberFromFixtureIsAlphabeticallyOrdered() {
        List<SecurityServerBrowseItemDto> servers = service.getForMember(ORG, NIIS_CODE);
        assertEquals(2, servers.size());
        assertEquals(NIISSS01, servers.get(0).getServerCode());
        assertEquals(NIISSS02, servers.get(1).getServerCode());
    }

    @Test
    public void testListSortByAddressBreaksTiesByServerCodeAscending() throws Exception {
        SecurityServerServiceV2 isolated = new SecurityServerServiceV2();
        SharedParamsParser stubParser = Mockito.mock(SharedParamsParser.class);
        MemberInfo owner = MemberInfo.builder().memberClass(ORG).memberCode(NIIS_CODE).name("NIIS").build();
        SecurityServerData ssB = SecurityServerData.builder()
                .owner(owner).serverCode("B-01").address("10.0.0.1").clients(List.of()).build();
        SecurityServerData ssA = SecurityServerData.builder()
                .owner(owner).serverCode("A-02").address("10.0.0.1").clients(List.of()).build();
        SecurityServerDataList parsed = new SecurityServerDataList();
        parsed.setSecurityServerDataList(List.of(ssB, ssA));
        Mockito.when(stubParser.parseDetails(Mockito.anyString())).thenReturn(parsed);
        ReflectionTestUtils.setField(isolated, "parser", stubParser);
        ReflectionTestUtils.setField(isolated, "sharedParamsFile", "stub.xml");

        Page<SecurityServerListItemDto> page = isolated.list(PageRequest.of(0, 20,
                org.springframework.data.domain.Sort.by(
                        org.springframework.data.domain.Sort.Direction.ASC, "address")));

        assertEquals(2, page.getContent().size());
        assertEquals("A-02", page.getContent().get(0).getServerCode());
        assertEquals("B-01", page.getContent().get(1).getServerCode());
    }

    @Test
    public void testListDescendingPrimaryStillBreaksTiesAscendingOnServerCode() throws Exception {
        SecurityServerServiceV2 isolated = new SecurityServerServiceV2();
        SharedParamsParser stubParser = Mockito.mock(SharedParamsParser.class);
        MemberInfo owner = MemberInfo.builder().memberClass(ORG).memberCode(NIIS_CODE).name("NIIS").build();
        SecurityServerData ssB = SecurityServerData.builder()
                .owner(owner).serverCode("B-01").address("10.0.0.1").clients(List.of()).build();
        SecurityServerData ssA = SecurityServerData.builder()
                .owner(owner).serverCode("A-02").address("10.0.0.1").clients(List.of()).build();
        SecurityServerDataList parsed = new SecurityServerDataList();
        parsed.setSecurityServerDataList(List.of(ssB, ssA));
        Mockito.when(stubParser.parseDetails(Mockito.anyString())).thenReturn(parsed);
        ReflectionTestUtils.setField(isolated, "parser", stubParser);
        ReflectionTestUtils.setField(isolated, "sharedParamsFile", "stub.xml");

        Page<SecurityServerListItemDto> page = isolated.list(PageRequest.of(0, 20,
                org.springframework.data.domain.Sort.by(
                        org.springframework.data.domain.Sort.Direction.DESC, "address")));

        assertEquals(2, page.getContent().size());
        assertEquals("A-02", page.getContent().get(0).getServerCode());
        assertEquals("B-01", page.getContent().get(1).getServerCode());
    }
}
