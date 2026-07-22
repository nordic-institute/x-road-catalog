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
package org.niis.xroad.catalog.collector.tasks;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.niis.xrd4j.common.exception.XRd4JException;
import org.niis.xrd4j.common.member.ObjectType;
import org.niis.xroad.catalog.collector.CollectorApplication;
import org.niis.xroad.catalog.collector.configuration.TaskPoolConfiguration;
import org.niis.xroad.catalog.collector.events.NewMembersEventPublisher;
import org.niis.xroad.catalog.collector.service.CatalogService;
import org.niis.xroad.catalog.collector.util.ClientListUtil;
import org.niis.xroad.catalog.collector.util.MemberWithName;
import org.niis.xroad.catalog.collector.util.XRoadIdentifier;
import org.niis.xroad.catalog.persistence.entity.Member;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@SpringBootTest(classes = CollectorApplication.class)
@ActiveProfiles({"test", "general-testdata"})
@SuppressWarnings("PMD.AvoidDuplicateLiterals")
public class ListClientsTaskTest {

    @Autowired
    private TaskPoolConfiguration conf;

    @MockitoBean
    CatalogService catalogService;

    @MockitoBean
    NewMembersEventPublisher newMembersEventPublisher;

    // DefaultTasksInitializer schedules CollectionCycleRunner::run on ApplicationStartedEvent, which
    // would otherwise call the real, Spring-managed ListClientsTask bean (sharing this test's mocked
    // catalogService) from a background thread and race with the manually constructed instances below.
    @MockitoBean
    CollectionCycleRunner collectionCycleRunner;

    @Test
    public void testOnReceiveWhenFetchUnlimited() throws XRd4JException {

        try (MockedStatic<ClientListUtil> mocked = Mockito.mockStatic(ClientListUtil.class)) {

            ReflectionTestUtils.setField(conf, "fetchRunUnlimited", true);

            List<MemberWithName> clientList = Arrays.asList(
                    createClientType(ObjectType.MEMBER, "member1", null),
                    createClientType(ObjectType.SUBSYSTEM, "member1", "sub1"),
                    createClientType(ObjectType.SUBSYSTEM, "member1", "sub2"),
                    createClientType(ObjectType.SUBSYSTEM, "member1", "sub3"),
                    createClientType(ObjectType.MEMBER, "member2", null),
                    createClientType(ObjectType.SUBSYSTEM, "member2", "sssub1"),
                    createClientType(ObjectType.SUBSYSTEM, "member2", "sssub2")
            );

            mocked.when(() -> ClientListUtil.clientListFromResponse(any(String.class), any(RestTemplate.class)))
                    .thenReturn(clientList);

            final Queue<MemberWithName> listMethodsQueue = new ConcurrentLinkedQueue<>();

            final Member member1 = new Member();
            member1.setMemberCode("member1");
            final Member member2 = new Member();
            member2.setMemberCode("member2");
            Mockito.when(catalogService.saveAllMembersAndSubsystems(any())).thenReturn(Set.of(member1, member2));

            FetchWorkTracker fetchWorkTracker = new FetchWorkTracker();
            ListClientsTask listClientsTask = new ListClientsTask(catalogService, conf, listMethodsQueue, newMembersEventPublisher,
                    fetchWorkTracker, new RestTemplate());
            listClientsTask.run();

            verify(catalogService, times(1)).saveAllMembersAndSubsystems(any());
            verify(newMembersEventPublisher, times(1)).publishNewMembersEvent(eq(Set.of("member1", "member2")));

            assertEquals(5, listMethodsQueue.size());
            assertEquals(5, fetchWorkTracker.pending());
        }
    }

    @Test
    public void testWhenFetchNotUnlimitedAndTimeOutsideOfConfiguration() throws XRd4JException {
        try (MockedStatic<ClientListUtil> mocked = mockStatic(ClientListUtil.class)) {

            ReflectionTestUtils.setField(conf, "fetchRunUnlimited", false);
            ReflectionTestUtils.setField(conf, "fetchTimeAfterHour", 23);
            ReflectionTestUtils.setField(conf, "fetchTimeBeforeHour", 23);

            List<MemberWithName> clientList = Arrays.asList(
                    createClientType(ObjectType.SUBSYSTEM, "member1", "sub1"),
                    createClientType(ObjectType.SUBSYSTEM, "member1", "sub2"),
                    createClientType(ObjectType.SUBSYSTEM, "member1", "sub3"),
                    createClientType(ObjectType.MEMBER, "member2", null),
                    createClientType(ObjectType.SUBSYSTEM, "member2", "sssub1"),
                    createClientType(ObjectType.SUBSYSTEM, "member2", "sssub2"),
                    createClientType(ObjectType.MEMBER, "member1", null)
            );

            mocked.when(() -> ClientListUtil.clientListFromResponse(any(), any(RestTemplate.class))).thenReturn(clientList);

            final Queue<MemberWithName> listMethodsQueue = new ConcurrentLinkedQueue<>();

            final Member member1 = new Member();
            member1.setMemberCode("member1");
            final Member member2 = new Member();
            member2.setMemberCode("member2");

            FetchWorkTracker fetchWorkTracker = new FetchWorkTracker();
            ListClientsTask listClientsTask = new ListClientsTask(catalogService, conf, listMethodsQueue, newMembersEventPublisher,
                    fetchWorkTracker, new RestTemplate());
            listClientsTask.run();

            verifyNoInteractions(catalogService);
            verifyNoInteractions(newMembersEventPublisher);

            assertEquals(0, listMethodsQueue.size());
            assertEquals(0, fetchWorkTracker.pending());
        }
    }

    @Test
    public void testOnReceiveWhenFetchNotUnlimitedButTimeIsInBetween() throws XRd4JException {
        try (MockedStatic<ClientListUtil> mocked = mockStatic(ClientListUtil.class)) {

            ReflectionTestUtils.setField(conf, "fetchRunUnlimited", false);
            ReflectionTestUtils.setField(conf, "fetchTimeAfterHour", 0);
            ReflectionTestUtils.setField(conf, "fetchTimeBeforeHour", 23);

            List<MemberWithName> clientList = Arrays.asList(
                    createClientType(ObjectType.MEMBER, "member1", null),
                    createClientType(ObjectType.SUBSYSTEM, "member1", "sub1"),
                    createClientType(ObjectType.SUBSYSTEM, "member1", "sub2"),
                    createClientType(ObjectType.SUBSYSTEM, "member1", "sub3"),
                    createClientType(ObjectType.MEMBER, "member2", null),
                    createClientType(ObjectType.SUBSYSTEM, "member2", "sssub1"),
                    createClientType(ObjectType.SUBSYSTEM, "member2", "sssub2")
            );

            mocked.when(() -> ClientListUtil.clientListFromResponse(any(), any(RestTemplate.class))).thenReturn(clientList);

            final Queue<MemberWithName> listMethodsQueue = new ConcurrentLinkedQueue<>();

            final Member member1 = new Member();
            member1.setMemberCode("member1");
            final Member member2 = new Member();
            member2.setMemberCode("member2");
            Mockito.when(catalogService.saveAllMembersAndSubsystems(any())).thenReturn(Set.of(member1, member2));

            FetchWorkTracker fetchWorkTracker = new FetchWorkTracker();
            ListClientsTask listClientsTask = new ListClientsTask(catalogService, conf, listMethodsQueue, newMembersEventPublisher,
                    fetchWorkTracker, new RestTemplate());
            listClientsTask.run();

            // Note: This line is time-sensitive and will fail if run between 23:00-00:00.
            verify(catalogService, times(1)).saveAllMembersAndSubsystems(any());
            verify(newMembersEventPublisher, times(1)).publishNewMembersEvent(eq(Set.of("member1", "member2")));
            assertEquals(5, listMethodsQueue.size());
            assertEquals(5, fetchWorkTracker.pending());
        }
    }

    @Test
    public void testOnReceiveWithEmptyMemberList() {
        try (MockedStatic<ClientListUtil> mocked = mockStatic(ClientListUtil.class)) {

            ReflectionTestUtils.setField(conf, "fetchRunUnlimited", true);

            List<MemberWithName> clientList = new ArrayList<>();
            mocked.when(() -> ClientListUtil.clientListFromResponse(any(), any(RestTemplate.class))).thenReturn(clientList);

            final Queue<MemberWithName> listMethodsQueue = new ConcurrentLinkedQueue<>();

            Mockito.when(catalogService.saveAllMembersAndSubsystems(any())).thenReturn(Set.of());

            FetchWorkTracker fetchWorkTracker = new FetchWorkTracker();
            ListClientsTask listClientsTask = new ListClientsTask(catalogService, conf, listMethodsQueue, newMembersEventPublisher,
                    fetchWorkTracker, new RestTemplate());
            listClientsTask.run();

            verify(catalogService, times(1)).saveAllMembersAndSubsystems(any());
            verify(newMembersEventPublisher, times(1)).publishNewMembersEvent(any());
            assertEquals(0, listMethodsQueue.size());
            assertEquals(0, fetchWorkTracker.pending());
        }
    }

    @Test
    public void testSaveErrorLog() {
        ReflectionTestUtils.setField(conf, "fetchRunUnlimited", true);

        final Queue<MemberWithName> listMethodsQueue = new ConcurrentLinkedQueue<>();

        FetchWorkTracker fetchWorkTracker = new FetchWorkTracker();
        ListClientsTask listClientsTask = new ListClientsTask(catalogService, conf, listMethodsQueue, newMembersEventPublisher,
                fetchWorkTracker, new RestTemplate());
        listClientsTask.run();

        verify(catalogService, times(1)).saveErrorLog(any());
        verifyNoInteractions(newMembersEventPublisher);
        assertEquals(0, listMethodsQueue.size());
        assertEquals(0, fetchWorkTracker.pending());
    }

    private MemberWithName createClientType(ObjectType objectType, String memberCode, String subsystemCode) throws XRd4JException {
        MemberWithName c = new MemberWithName();
        XRoadIdentifier xrcit = XRoadIdentifier.builder()
                .xRoadInstance("FI")
                .memberClass("GOV")
                .memberCode(memberCode)
                .subsystemCode(subsystemCode)
                .build();

        xrcit.setObjectType(objectType);
        c.setId(xrcit);
        c.setName(memberCode);
        return c;

    }
}
