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
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.niis.xrd4j.common.exception.XRd4JException;
import org.niis.xrd4j.common.member.ObjectType;
import org.niis.xroad.catalog.collector.CollectorApplication;
import org.niis.xroad.catalog.collector.configuration.TaskPoolConfiguration;
import org.niis.xroad.catalog.collector.events.NewMembersEventPublisher;
import org.niis.xroad.catalog.collector.exception.CatalogCollectorRuntimeException;
import org.niis.xroad.catalog.collector.service.CatalogService;
import org.niis.xroad.catalog.collector.util.ClientListUtil;
import org.niis.xroad.catalog.collector.util.MemberWithName;
import org.niis.xroad.catalog.collector.util.XRoadIdentifier;
import org.niis.xroad.catalog.persistence.entity.ErrorLog;
import org.niis.xroad.catalog.persistence.entity.Member;
import org.niis.xroad.catalog.persistence.entity.Subsystem;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

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

    // Mocked so the ApplicationStartedEvent schedule does not run the real ListClientsTask on a
    // background thread and race with the manually constructed instances below.
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
                    fetchWorkTracker, new RestTemplate(), Clock.systemDefaultZone());

            assertTrue(listClientsTask.run());

            verify(catalogService, times(1)).saveAllMembersAndSubsystems(any());
            verify(newMembersEventPublisher, times(1)).publishNewMembersEvent(eq(Set.of("member1", "member2")));

            assertEquals(5, listMethodsQueue.size());
            assertEquals(5, fetchWorkTracker.pending());
        }
    }

    @Test
    public void testFetchesEvenWhenTheFetchWindowIsClosed() throws XRd4JException {
        try (MockedStatic<ClientListUtil> mocked = mockStatic(ClientListUtil.class)) {

            // The fetch window is decided by CollectionCycleRunner; a closed window must not stop this task.
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
            Mockito.when(catalogService.saveAllMembersAndSubsystems(any())).thenReturn(Set.of(member1, member2));

            FetchWorkTracker fetchWorkTracker = new FetchWorkTracker();
            ListClientsTask listClientsTask = new ListClientsTask(catalogService, conf, listMethodsQueue, newMembersEventPublisher,
                    fetchWorkTracker, new RestTemplate(), Clock.systemDefaultZone());
            listClientsTask.run();

            verify(catalogService, times(1)).saveAllMembersAndSubsystems(any());
            verify(newMembersEventPublisher, times(1)).publishNewMembersEvent(eq(Set.of("member1", "member2")));

            assertEquals(5, listMethodsQueue.size());
            assertEquals(5, fetchWorkTracker.pending());
        }
    }

    @Test
    public void testFlushOldErrorLogEntriesInsideTheFlushWindow() {
        ReflectionTestUtils.setField(conf, "flushLogTimeAfterHour", 3);
        ReflectionTestUtils.setField(conf, "flushLogTimeBeforeHour", 4);
        ReflectionTestUtils.setField(conf, "errorLogLengthInDays", 90);

        ListClientsTask listClientsTask = new ListClientsTask(catalogService, conf, new ConcurrentLinkedQueue<>(),
                newMembersEventPublisher, new FetchWorkTracker(), new RestTemplate(), fixedClockAt(3, 30));
        listClientsTask.flushOldErrorLogEntries();

        verify(catalogService, times(1)).deleteOldErrorLogEntries(90);
        verifyNoMoreInteractions(catalogService);
    }

    @Test
    public void testFlushOldErrorLogEntriesOutsideTheFlushWindow() {
        ReflectionTestUtils.setField(conf, "flushLogTimeAfterHour", 3);
        ReflectionTestUtils.setField(conf, "flushLogTimeBeforeHour", 4);

        ListClientsTask listClientsTask = new ListClientsTask(catalogService, conf, new ConcurrentLinkedQueue<>(),
                newMembersEventPublisher, new FetchWorkTracker(), new RestTemplate(), fixedClockAt(12, 30));
        listClientsTask.flushOldErrorLogEntries();

        verifyNoInteractions(catalogService);
    }

    @Test
    public void testFlushOldErrorLogEntriesFailureIsContained() {
        ReflectionTestUtils.setField(conf, "flushLogTimeAfterHour", 3);
        ReflectionTestUtils.setField(conf, "flushLogTimeBeforeHour", 4);
        Mockito.doThrow(new IllegalStateException("db down")).when(catalogService).deleteOldErrorLogEntries(any());

        ListClientsTask listClientsTask = new ListClientsTask(catalogService, conf, new ConcurrentLinkedQueue<>(),
                newMembersEventPublisher, new FetchWorkTracker(), new RestTemplate(), fixedClockAt(3, 30));

        assertDoesNotThrow(listClientsTask::flushOldErrorLogEntries);
    }

    @Test
    public void testIgnoredSubsystemIsExcludedFromCatalogAndFromListMethods() throws XRd4JException {
        try (MockedStatic<ClientListUtil> mocked = mockStatic(ClientListUtil.class)) {

            ReflectionTestUtils.setField(conf, "fetchRunUnlimited", true);

            // DEV:COM:1234:Test is listed in xroad-catalog.instance.ignored-subsystem-ids of the test profile
            List<MemberWithName> clientList = Arrays.asList(
                    createClientType(ObjectType.MEMBER, "DEV", "COM", "1234", null),
                    createClientType(ObjectType.SUBSYSTEM, "DEV", "COM", "1234", "Test"),
                    createClientType(ObjectType.SUBSYSTEM, "DEV", "COM", "1234", "Kept")
            );
            mocked.when(() -> ClientListUtil.clientListFromResponse(any(), any(RestTemplate.class))).thenReturn(clientList);

            final Queue<MemberWithName> listMethodsQueue = new ConcurrentLinkedQueue<>();
            Mockito.when(catalogService.saveAllMembersAndSubsystems(any())).thenReturn(Set.of());

            FetchWorkTracker fetchWorkTracker = new FetchWorkTracker();
            ListClientsTask listClientsTask = new ListClientsTask(catalogService, conf, listMethodsQueue, newMembersEventPublisher,
                    fetchWorkTracker, new RestTemplate(), Clock.systemDefaultZone());
            listClientsTask.run();

            ArgumentCaptor<Collection<Member>> saved = ArgumentCaptor.captor();
            verify(catalogService, times(1)).saveAllMembersAndSubsystems(saved.capture());
            assertEquals(1, saved.getValue().size());
            Set<String> savedSubsystems = saved.getValue().iterator().next().getAllSubsystems().stream()
                    .map(Subsystem::getSubsystemCode).collect(Collectors.toSet());
            assertEquals(Set.of("Kept"), savedSubsystems);

            assertEquals(1, listMethodsQueue.size());
            assertEquals("Kept", listMethodsQueue.peek().getId().getSubsystemCode());
            assertEquals(1, fetchWorkTracker.pending());
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
                    fetchWorkTracker, new RestTemplate(), Clock.systemDefaultZone());
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
                fetchWorkTracker, new RestTemplate(), Clock.systemDefaultZone());

        assertFalse(listClientsTask.run());

        verify(catalogService, times(1)).saveErrorLog(any());
        verifyNoInteractions(newMembersEventPublisher);
        assertEquals(0, listMethodsQueue.size());
        assertEquals(0, fetchWorkTracker.pending());
    }

    /**
     * Guards the contract {@code CollectionCycleRunner} relies on: a failing listClients call is reported
     * as a failure (never thrown) and the error log row carrying the cause is still written.
     */
    @Test
    public void testFailedListClientsFetchReportsFailureAndSavesTheErrorLog() {
        try (MockedStatic<ClientListUtil> mocked = mockStatic(ClientListUtil.class)) {
            mocked.when(() -> ClientListUtil.clientListFromResponse(any(), any(RestTemplate.class)))
                    .thenThrow(new CatalogCollectorRuntimeException("listClients answered HTTP 200 OK with a body that is not JSON"));

            final Queue<MemberWithName> listMethodsQueue = new ConcurrentLinkedQueue<>();
            FetchWorkTracker fetchWorkTracker = new FetchWorkTracker();
            ListClientsTask listClientsTask = new ListClientsTask(catalogService, conf, listMethodsQueue, newMembersEventPublisher,
                    fetchWorkTracker, new RestTemplate(), Clock.systemDefaultZone());

            assertFalse(assertDoesNotThrow(listClientsTask::run));

            ArgumentCaptor<ErrorLog> errorLog = ArgumentCaptor.forClass(ErrorLog.class);
            verify(catalogService, times(1)).saveErrorLog(errorLog.capture());
            assertEquals("500", errorLog.getValue().getCode());
            assertTrue(errorLog.getValue().getMessage().endsWith(
                    "/listClients): listClients answered HTTP 200 OK with a body that is not JSON"),
                    errorLog.getValue().getMessage());
            verifyNoInteractions(newMembersEventPublisher);
            assertEquals(0, listMethodsQueue.size());
            assertEquals(0, fetchWorkTracker.pending());
        }
    }

    private MemberWithName createClientType(ObjectType objectType, String memberCode, String subsystemCode) throws XRd4JException {
        return createClientType(objectType, "FI", "GOV", memberCode, subsystemCode);
    }

    private MemberWithName createClientType(ObjectType objectType, String xroadInstance, String memberClass, String memberCode,
                                            String subsystemCode) throws XRd4JException {
        MemberWithName c = new MemberWithName();
        XRoadIdentifier xrcit = XRoadIdentifier.builder()
                .xRoadInstance(xroadInstance)
                .memberClass(memberClass)
                .memberCode(memberCode)
                .subsystemCode(subsystemCode)
                .build();

        xrcit.setObjectType(objectType);
        c.setId(xrcit);
        c.setName(memberCode);
        return c;

    }

    private static Clock fixedClockAt(int hour, int minute) {
        return Clock.fixed(LocalDate.of(2025, 6, 1).atTime(hour, minute).toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
    }
}
