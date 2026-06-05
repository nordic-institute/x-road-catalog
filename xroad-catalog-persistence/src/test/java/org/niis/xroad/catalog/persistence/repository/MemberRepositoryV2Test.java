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
package org.niis.xroad.catalog.persistence.repository;

import org.junit.jupiter.api.Test;
import org.niis.xroad.catalog.persistence.entity.Member;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles({"test", "general-testdata"})
public class MemberRepositoryV2Test {

    @Autowired
    MemberRepositoryV2 memberRepository;

    @Test
    public void testFindAnyByNaturalKeyIncludesRemoved() {
        // From test-data-template.sql: member id 8 (dev-cs/PUB/14151329 "Removed item") has removed != null
        Member removed = memberRepository.findAnyByNaturalKey("dev-cs", "PUB", "14151329");
        assertNotNull(removed);
        assertTrue(removed.getStatusInfo().isRemoved());

        Member active = memberRepository.findAnyByNaturalKey("dev-cs", "PUB", "14151328");
        assertNotNull(active);
        assertFalse(active.getStatusInfo().isRemoved());

        Member missing = memberRepository.findAnyByNaturalKey("dev-cs", "PUB", "does-not-exist");
        assertNull(missing);
    }

    @Test
    public void testSearchByTextMatchesMemberCode() {
        Page<Member> active = memberRepository.searchByText("14151", true, PageRequest.of(0, 10));
        assertEquals(1, active.getTotalElements(), "activeOnly should exclude removed 14151329");
        assertEquals("14151328", active.getContent().get(0).getMemberCode());

        Page<Member> all = memberRepository.searchByText("14151", false, PageRequest.of(0, 10));
        assertEquals(2, all.getTotalElements(), "including removed should return both members");
    }

    @Test
    public void testSearchByTextMatchesName() {
        Page<Member> hits = memberRepository.searchByText("Albert", true, PageRequest.of(0, 10));
        assertFalse(hits.isEmpty());
        assertTrue(hits.getContent().stream().anyMatch(m -> m.getName().contains("Albert")));
    }

    @Test
    public void testSearchByTextNoMatches() {
        Page<Member> none = memberRepository.searchByText("zzzzzzzzzzzzzz", true, PageRequest.of(0, 10));
        assertTrue(none.isEmpty());
    }

    @Test
    public void testFindForList() {
        Page<Member> all = memberRepository.findForList(null, null, false, PageRequest.of(0, 50));
        assertFalse(all.isEmpty());

        Page<Member> pubOnly = memberRepository.findForList("PUB", null, false, PageRequest.of(0, 50));
        assertTrue(pubOnly.getContent().stream().allMatch(m -> "PUB".equals(m.getMemberClass())));

        Page<Member> govOnly = memberRepository.findForList("GOV", null, false, PageRequest.of(0, 50));
        assertTrue(govOnly.isEmpty());

        // Under the Task 1.5 rewrite, :isProvider=true iff member is not removed AND has at least one
        // active service under an active subsystem (no descriptor check). Member 14151328 has active
        // services (getRandom, testService, mixedSvc, svc_removed_wsdl_only) under active subsystems.
        Page<Member> providersOnly = memberRepository.findForList(null, true, false, PageRequest.of(0, 50));
        assertFalse(providersOnly.isEmpty(), "Fixture contains at least one provider member");
        assertTrue(providersOnly.getContent().stream().anyMatch(m -> "14151328".equals(m.getMemberCode())),
                "Member 14151328 should be identified as a provider");

        Page<Member> nonProviders = memberRepository.findForList(null, false, false, PageRequest.of(0, 50));
        assertFalse(nonProviders.isEmpty(),
                "Fixture contains non-provider members (e.g. member 88855888 whose subsystem has no services)");
        nonProviders.getContent().forEach(m -> assertFalse(
                hasActiveServiceUnderActiveSubsystem(m),
                "non-provider members must not have any active service under an active subsystem"));
    }

    /**
     * Task 1.5 invariant 1: a member with one active service and no descriptor rows of any kind is
     * still a provider — the new predicate checks for active service existence, not descriptor rows.
     * Member 13 ("Updated Service") has service 3 (no WSDL / OpenAPI / Rest attached) under
     * subsystem 6; this must appear in ?isProvider=true results.
     */
    @Test
    public void testFindForListIsProviderDescriptorLessService() {
        Page<Member> providersOnly = memberRepository.findForList(null, true, true, PageRequest.of(0, 50));
        assertTrue(providersOnly.getContent().stream().anyMatch(m -> "13".equals(m.getMemberCode())),
                "Member 13 has an active service with no descriptor rows - new rule must include it as provider");
    }

    /**
     * Task 1.5 invariant 2: a member whose active subsystem contains only removed services is NOT a
     * provider. Fixture member 'only-removed-service' has service 30 (removed) under active subsystem 20.
     */
    @Test
    public void testFindForListIsProviderExcludesRemovedOnlyService() {
        Page<Member> providersOnly = memberRepository.findForList(null, true, true, PageRequest.of(0, 50));
        assertFalse(providersOnly.getContent().stream()
                        .anyMatch(m -> "only-removed-service".equals(m.getMemberCode())),
                "member with only removed services must be excluded from ?isProvider=true");

        Page<Member> nonProvidersIncludingRemoved =
                memberRepository.findForList(null, false, false, PageRequest.of(0, 50));
        assertTrue(nonProvidersIncludingRemoved.getContent().stream()
                        .anyMatch(m -> "only-removed-service".equals(m.getMemberCode())),
                "member with only removed services must appear in ?isProvider=false");
    }

    /**
     * Task 1.5 invariant 3: a member whose only active service lives under a removed subsystem is
     * NOT a provider. Fixture member 'svc-under-removed-sub' has service 31 (active) under subsystem 21
     * (removed).
     */
    @Test
    public void testFindForListIsProviderRespectsRemovedSubsystemCascade() {
        Page<Member> providersOnly = memberRepository.findForList(null, true, true, PageRequest.of(0, 50));
        assertFalse(providersOnly.getContent().stream()
                        .anyMatch(m -> "svc-under-removed-sub".equals(m.getMemberCode())),
                "active service under removed subsystem must NOT be counted as provider");
    }

    /**
     * Task 1.5 invariant 4: a removed member with stale active subsystems/services is NOT a provider
     * even with activeOnly=false. `isProvider` is pinned to the active view.
     */
    @Test
    public void testFindForListIsProviderExcludesRemovedMember() {
        Page<Member> providersIncludingRemoved =
                memberRepository.findForList(null, true, false, PageRequest.of(0, 50));
        assertFalse(providersIncludingRemoved.getContent().stream()
                        .anyMatch(m -> "removed-with-stale".equals(m.getMemberCode())),
                "removed member with stale active children must NOT be a provider");

        Page<Member> nonProviders = memberRepository.findForList(null, false, false, PageRequest.of(0, 50));
        assertTrue(nonProviders.getContent().stream()
                        .anyMatch(m -> "removed-with-stale".equals(m.getMemberCode())),
                "removed member with stale active children must appear in ?isProvider=false when activeOnly=false");
    }

    private static boolean hasActiveServiceUnderActiveSubsystem(Member member) {
        return !member.getStatusInfo().isRemoved()
                && member.getActiveSubsystems().stream()
                        .flatMap(sub -> sub.getActiveServices().stream())
                        .findAny()
                        .isPresent();
    }

    @Test
    public void testFindCreatedBetween() {
        LocalDateTime start = LocalDateTime.of(2016, 1, 1, 0, 0);
        LocalDateTime end = LocalDateTime.of(2016, 12, 31, 0, 0);
        List<Member> created = memberRepository.findCreatedBetween(start, end);
        assertFalse(created.isEmpty(), "Fixture contains members created in 2016");
        assertTrue(created.stream().anyMatch(m -> "14151328".equals(m.getMemberCode())));
        assertTrue(created.stream().anyMatch(m -> "15".equals(m.getMemberCode())));
        created.forEach(m -> {
            LocalDateTime c = m.getStatusInfo().getCreated();
            assertTrue(!c.isBefore(start) && c.isBefore(end));
        });

        LocalDateTime emptyStart = LocalDateTime.of(2018, 1, 1, 0, 0);
        LocalDateTime emptyEnd = LocalDateTime.of(2018, 12, 31, 0, 0);
        List<Member> nothingCreated = memberRepository.findCreatedBetween(emptyStart, emptyEnd);
        assertTrue(nothingCreated.isEmpty(), "No members were created in 2018");
    }

    @Test
    public void testFindModifiedBetweenExcludesCreatedAndRemoved() {
        LocalDateTime start = LocalDateTime.of(2017, 1, 1, 0, 0);
        LocalDateTime end = LocalDateTime.of(2017, 12, 31, 0, 0);
        List<Member> modified = memberRepository.findModifiedBetween(start, end);
        assertFalse(modified.isEmpty(), "Fixture contains members changed in 2017");
        assertTrue(modified.stream().anyMatch(m -> "11".equals(m.getMemberCode())));
        assertTrue(modified.stream().anyMatch(m -> "15".equals(m.getMemberCode())));
        modified.forEach(m -> {
            LocalDateTime ch = m.getStatusInfo().getChanged();
            LocalDateTime c = m.getStatusInfo().getCreated();
            LocalDateTime r = m.getStatusInfo().getRemoved();
            assertNotNull(ch);
            assertTrue(!ch.isBefore(start) && ch.isBefore(end),
                    "Modified row's changed timestamp must be inside [start, end)");
            assertTrue(c.isBefore(start) || !c.isBefore(end),
                    "Modified row should not have created timestamp in range");
            assertTrue(r == null || r.isBefore(start) || !r.isBefore(end),
                    "Modified row should not have removed timestamp in range");
        });
    }

    @Test
    public void testFindRemovedBetween() {
        LocalDateTime start = LocalDateTime.of(2017, 1, 1, 0, 0);
        LocalDateTime end = LocalDateTime.of(2017, 12, 31, 0, 0);
        List<Member> removed = memberRepository.findRemovedBetween(start, end);
        assertFalse(removed.isEmpty(), "Fixture contains member removed in 2017");
        assertTrue(removed.stream().anyMatch(m -> "14151329".equals(m.getMemberCode())));
        removed.forEach(m -> {
            LocalDateTime r = m.getStatusInfo().getRemoved();
            assertNotNull(r);
            assertTrue(!r.isBefore(start) && r.isBefore(end));
        });
    }
}
