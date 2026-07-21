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

import org.niis.xroad.catalog.lister.v2.dto.SecurityServerBrowseItemDto;
import org.niis.xroad.catalog.lister.v2.dto.SecurityServerClientDto;
import org.niis.xroad.catalog.lister.v2.dto.SecurityServerInfoV2;
import org.niis.xroad.catalog.lister.v2.dto.SecurityServerListItemDto;
import org.niis.xroad.catalog.lister.v2.dto.SecurityServerOwnerDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class SecurityServerServiceV2 {

    private final SharedParamsCache sharedParamsCache;
    private final InstanceContext instanceContext;

    public SecurityServerServiceV2(SharedParamsCache sharedParamsCache, InstanceContext instanceContext) {
        this.sharedParamsCache = sharedParamsCache;
        this.instanceContext = instanceContext;
    }

    public Page<SecurityServerListItemDto> list(Pageable pageable) {
        assertReady();
        List<SecurityServerInfoV2> all = sharedParamsCache.securityServers();
        List<SecurityServerListItemDto> items = new ArrayList<>(all.stream().map(this::toListItem).toList());
        items.sort(buildComparator(pageable));
        int from = (int) Math.min(pageable.getOffset(), items.size());
        int to = Math.min(from + pageable.getPageSize(), items.size());
        return new PageImpl<>(items.subList(from, to), pageable, items.size());
    }

    public List<SecurityServerBrowseItemDto> getForMember(String memberClass, String memberCode) {
        assertReady();
        List<SecurityServerInfoV2> all = sharedParamsCache.securityServers();
        List<SecurityServerBrowseItemDto> result = new ArrayList<>();
        for (SecurityServerInfoV2 s : all) {
            SecurityServerInfoV2.MemberRef owner = s.owner();
            if (owner != null
                    && memberClass.equals(owner.memberClass())
                    && memberCode.equals(owner.memberCode())) {
                result.add(toBrowseItem(s));
            }
        }
        result.sort(Comparator.comparing(SecurityServerBrowseItemDto::getServerCode,
                Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)));
        return result;
    }

    // SharedParamsCache caches a parse failure (missing/unreadable shared-params.xml) as an empty
    // snapshot for the full TTL, so an empty securityServers() list is ambiguous between "not ready
    // yet" and "genuinely zero security servers". InstanceContext already distinguishes those two
    // cases for every other V2 endpoint (it raises 503 until shared-params.xml first becomes
    // readable, then caches success for the process lifetime); routing through it here keeps this
    // endpoint's not-ready signalling identical to its siblings without adding another failure-mode
    // cache to SharedParamsCache itself. The returned instance id is intentionally unused -- only
    // the readiness check matters here.
    private void assertReady() {
        instanceContext.getCurrentInstance();
    }

    private Comparator<SecurityServerListItemDto> buildComparator(Pageable pageable) {
        Sort.Order order = pageable.getSort().stream().findFirst()
                .orElse(new Sort.Order(Sort.Direction.ASC, "serverCode"));
        Comparator<SecurityServerListItemDto> primary;
        if ("address".equals(order.getProperty())) {
            primary = Comparator.comparing(SecurityServerListItemDto::getAddress,
                    Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
        } else {
            primary = Comparator.comparing(SecurityServerListItemDto::getServerCode,
                    Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
        }
        if (order.isDescending()) {
            primary = primary.reversed();
        }
        Comparator<SecurityServerListItemDto> tieBreak = Comparator.comparing(
                SecurityServerListItemDto::getServerCode,
                Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
        return primary.thenComparing(tieBreak);
    }

    private SecurityServerListItemDto toListItem(SecurityServerInfoV2 data) {
        return SecurityServerListItemDto.builder()
                .serverCode(data.serverCode())
                .address(data.address())
                .owner(toOwner(data.owner()))
                .clientCount(data.clients() != null ? data.clients().size() : 0)
                .build();
    }

    private SecurityServerBrowseItemDto toBrowseItem(SecurityServerInfoV2 data) {
        List<SecurityServerClientDto> clients = new ArrayList<>();
        if (data.clients() != null) {
            for (SecurityServerInfoV2.ClientRef c : data.clients()) {
                clients.add(SecurityServerClientDto.builder()
                        .memberClass(c.memberClass())
                        .memberCode(c.memberCode())
                        .subsystemCode(c.subsystemCode())
                        .build());
            }
        }
        return SecurityServerBrowseItemDto.builder()
                .serverCode(data.serverCode())
                .address(data.address())
                .owner(toOwner(data.owner()))
                .clients(clients)
                .build();
    }

    private SecurityServerOwnerDto toOwner(SecurityServerInfoV2.MemberRef ref) {
        if (ref == null) {
            return null;
        }
        return SecurityServerOwnerDto.builder()
                .memberClass(ref.memberClass())
                .memberCode(ref.memberCode())
                .name(ref.name())
                .build();
    }
}
