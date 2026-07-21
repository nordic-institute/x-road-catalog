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
import org.niis.xroad.catalog.lister.v2.dto.SecurityServerBrowseItemDto;
import org.niis.xroad.catalog.lister.v2.dto.SecurityServerInfoV2;
import org.niis.xroad.catalog.lister.v2.dto.SecurityServerListItemDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
    public void testGetForMemberSortsByServerCodeAscending() {
        // Build a cache stub returning servers in non-alphabetical order under a single owner.
        SharedParamsCache stubCache = Mockito.mock(SharedParamsCache.class);
        SecurityServerInfoV2.MemberRef owner = new SecurityServerInfoV2.MemberRef(ORG, NIIS_CODE, "NIIS");
        SecurityServerInfoV2 zSrv = new SecurityServerInfoV2("zzz-server", "10.0.0.9", owner, List.of());
        SecurityServerInfoV2 mSrv = new SecurityServerInfoV2("mmm-server", "10.0.0.5", owner, List.of());
        SecurityServerInfoV2 aSrv = new SecurityServerInfoV2("aaa-server", "10.0.0.1", owner, List.of());
        Mockito.when(stubCache.securityServers()).thenReturn(List.of(zSrv, mSrv, aSrv));
        SecurityServerServiceV2 isolated = new SecurityServerServiceV2(stubCache, Mockito.mock(InstanceContext.class));

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
    public void testListSortByAddressBreaksTiesByServerCodeAscending() {
        SharedParamsCache stubCache = Mockito.mock(SharedParamsCache.class);
        SecurityServerInfoV2.MemberRef owner = new SecurityServerInfoV2.MemberRef(ORG, NIIS_CODE, "NIIS");
        SecurityServerInfoV2 ssB = new SecurityServerInfoV2("B-01", "10.0.0.1", owner, List.of());
        SecurityServerInfoV2 ssA = new SecurityServerInfoV2("A-02", "10.0.0.1", owner, List.of());
        Mockito.when(stubCache.securityServers()).thenReturn(List.of(ssB, ssA));
        SecurityServerServiceV2 isolated = new SecurityServerServiceV2(stubCache, Mockito.mock(InstanceContext.class));

        Page<SecurityServerListItemDto> page = isolated.list(PageRequest.of(0, 20,
                org.springframework.data.domain.Sort.by(
                        org.springframework.data.domain.Sort.Direction.ASC, "address")));

        assertEquals(2, page.getContent().size());
        assertEquals("A-02", page.getContent().get(0).getServerCode());
        assertEquals("B-01", page.getContent().get(1).getServerCode());
    }

    @Test
    public void testListDescendingPrimaryStillBreaksTiesAscendingOnServerCode() {
        SharedParamsCache stubCache = Mockito.mock(SharedParamsCache.class);
        SecurityServerInfoV2.MemberRef owner = new SecurityServerInfoV2.MemberRef(ORG, NIIS_CODE, "NIIS");
        SecurityServerInfoV2 ssB = new SecurityServerInfoV2("B-01", "10.0.0.1", owner, List.of());
        SecurityServerInfoV2 ssA = new SecurityServerInfoV2("A-02", "10.0.0.1", owner, List.of());
        Mockito.when(stubCache.securityServers()).thenReturn(List.of(ssB, ssA));
        SecurityServerServiceV2 isolated = new SecurityServerServiceV2(stubCache, Mockito.mock(InstanceContext.class));

        Page<SecurityServerListItemDto> page = isolated.list(PageRequest.of(0, 20,
                org.springframework.data.domain.Sort.by(
                        org.springframework.data.domain.Sort.Direction.DESC, "address")));

        assertEquals(2, page.getContent().size());
        assertEquals("A-02", page.getContent().get(0).getServerCode());
        assertEquals("B-01", page.getContent().get(1).getServerCode());
    }

    @Test
    public void testListPropagates503WhileInstanceNotYetReady() {
        // SharedParamsCache caches a missing/unparseable shared-params.xml as an empty snapshot for
        // its whole TTL, which is indistinguishable from "genuinely zero security servers". Routing
        // through InstanceContext -- the same readiness gate every other V2 endpoint uses -- means
        // this not-ready window surfaces as 503, not as an authoritative empty page.
        SharedParamsCache stubCache = Mockito.mock(SharedParamsCache.class);
        InstanceContext notReady = Mockito.mock(InstanceContext.class);
        Mockito.when(notReady.getCurrentInstance()).thenThrow(
                new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "not ready"));
        SecurityServerServiceV2 isolated = new SecurityServerServiceV2(stubCache, notReady);

        assertThrows(ResponseStatusException.class, () -> isolated.list(PageRequest.of(0, 20)));
        assertThrows(ResponseStatusException.class, () -> isolated.getForMember(ORG, NIIS_CODE));
        Mockito.verifyNoInteractions(stubCache);
    }
}
