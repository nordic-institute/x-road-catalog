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
import org.niis.xroad.catalog.lister.v2.dto.SecurityServerInfo;
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
public class SecurityServerServiceTest {

    private static final String ORG = "ORG";
    private static final String NIIS_CODE = "2908758-4";
    private static final String NIISSS01 = "niisss01";
    private static final String NIISSS02 = "niisss02";

    @Autowired
    private SecurityServerService service;

    @Test
    public void testListReturnsServersWithClientCount() {
        Page<SecurityServerListItemDto> page = service.list(PageRequest.of(0, 20));
        assertFalse(page.isEmpty());
        // shared-params-2.xml has 4 Security Servers: niisss01, testcomss01, testagess01, niisss02
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
    public void testGetForMemberPreservesSharedParamsDocumentOrder() {
        // Build a cache stub returning servers in non-alphabetical order under a single owner.
        SharedParamsCache stubCache = Mockito.mock(SharedParamsCache.class);
        SecurityServerInfo.MemberRef owner = new SecurityServerInfo.MemberRef(ORG, NIIS_CODE, "NIIS");
        SecurityServerInfo zSrv = new SecurityServerInfo("zzz-server", "10.0.0.9", owner, List.of());
        SecurityServerInfo mSrv = new SecurityServerInfo("mmm-server", "10.0.0.5", owner, List.of());
        SecurityServerInfo aSrv = new SecurityServerInfo("aaa-server", "10.0.0.1", owner, List.of());
        Mockito.when(stubCache.securityServers()).thenReturn(List.of(zSrv, mSrv, aSrv));
        SecurityServerService isolated = new SecurityServerService(stubCache);

        List<SecurityServerBrowseItemDto> servers = isolated.getForMember(ORG, NIIS_CODE);
        assertEquals(3, servers.size());
        assertEquals("zzz-server", servers.get(0).getServerCode());
        assertEquals("mmm-server", servers.get(1).getServerCode());
        assertEquals("aaa-server", servers.get(2).getServerCode());
    }

    @Test
    public void testGetForMemberFromFixtureKeepsDocumentOrder() {
        List<SecurityServerBrowseItemDto> servers = service.getForMember(ORG, NIIS_CODE);
        assertEquals(2, servers.size());
        assertEquals(NIISSS01, servers.get(0).getServerCode());
        assertEquals(NIISSS02, servers.get(1).getServerCode());
    }

    @Test
    public void testListSortByAddressBreaksTiesByServerCodeAscending() {
        SharedParamsCache stubCache = Mockito.mock(SharedParamsCache.class);
        SecurityServerInfo.MemberRef owner = new SecurityServerInfo.MemberRef(ORG, NIIS_CODE, "NIIS");
        SecurityServerInfo ssB = new SecurityServerInfo("B-01", "10.0.0.1", owner, List.of());
        SecurityServerInfo ssA = new SecurityServerInfo("A-02", "10.0.0.1", owner, List.of());
        Mockito.when(stubCache.securityServers()).thenReturn(List.of(ssB, ssA));
        SecurityServerService isolated = new SecurityServerService(stubCache);

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
        SecurityServerInfo.MemberRef owner = new SecurityServerInfo.MemberRef(ORG, NIIS_CODE, "NIIS");
        SecurityServerInfo ssB = new SecurityServerInfo("B-01", "10.0.0.1", owner, List.of());
        SecurityServerInfo ssA = new SecurityServerInfo("A-02", "10.0.0.1", owner, List.of());
        Mockito.when(stubCache.securityServers()).thenReturn(List.of(ssB, ssA));
        SecurityServerService isolated = new SecurityServerService(stubCache);

        Page<SecurityServerListItemDto> page = isolated.list(PageRequest.of(0, 20,
                org.springframework.data.domain.Sort.by(
                        org.springframework.data.domain.Sort.Direction.DESC, "address")));

        assertEquals(2, page.getContent().size());
        assertEquals("A-02", page.getContent().get(0).getServerCode());
        assertEquals("B-01", page.getContent().get(1).getServerCode());
    }

    @Test
    public void testListPropagates503WhileInstanceNotYetReady() {
        // The TTL snapshot caches a missing/unparseable shared-params.xml as an empty result,
        // indistinguishable from genuinely zero servers; routing through getCurrentInstance()
        // surfaces this not-ready window as 503 rather than an authoritative empty page.
        SharedParamsCache stubCache = Mockito.mock(SharedParamsCache.class);
        Mockito.when(stubCache.getCurrentInstance()).thenThrow(
                new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "not ready"));
        SecurityServerService isolated = new SecurityServerService(stubCache);

        assertThrows(ResponseStatusException.class, () -> isolated.list(PageRequest.of(0, 20)));
        assertThrows(ResponseStatusException.class, () -> isolated.getForMember(ORG, NIIS_CODE));
        Mockito.verify(stubCache, Mockito.never()).securityServers();
    }
}
