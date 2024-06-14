/**
 *
 *  The MIT License
 *
 *  Copyright (c) 2023- Nordic Institute for Interoperability Solutions (NIIS)
 *  Copyright (c) 2016-2023 Finnish Digital Agency
 *
 *  Permission is hereby granted, free of charge, to any person obtaining a copy
 *  of this software and associated documentation files (the "Software"), to deal
 *  in the Software without restriction, including without limitation the rights
 *  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  copies of the Software, and to permit persons to whom the Software is
 *  furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in
 *  all copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 *  THE SOFTWARE.
 *
 */
package fi.vrk.xroad.catalog.collector.tasks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.ApplicationContext;
import org.springframework.test.util.ReflectionTestUtils;

import fi.vrk.xroad.catalog.collector.configuration.TaskPoolConfiguration;
import fi.vrk.xroad.catalog.collector.util.ClientListUtil;
import fi.vrk.xroad.catalog.collector.wsimport.ClientList;
import fi.vrk.xroad.catalog.collector.wsimport.ClientType;
import fi.vrk.xroad.catalog.collector.wsimport.XRoadClientIdentifierType;
import fi.vrk.xroad.catalog.collector.wsimport.XRoadObjectType;
import fi.vrk.xroad.catalog.persistence.CatalogService;
import fi.vrk.xroad.catalog.persistence.entity.Member;

@SpringBootTest(classes = TaskPoolConfiguration.class)
public class ListClientsTaskTest {

    @Autowired
    private ApplicationContext applicationContext;

    @MockBean
    CatalogService catalogService;

    @Test
    public void testOnReceiveWhenFetchUnlimited() throws Exception {

        try (MockedStatic<ClientListUtil> mocked = Mockito.mockStatic(ClientListUtil.class)) {
            TaskPoolConfiguration conf = applicationContext.getBean(TaskPoolConfiguration.class);

            ReflectionTestUtils.setField(conf, "fetchRunUnlimited", true);

            ClientList clientList = new ClientList();
            clientList.getMember().add(createClientType(XRoadObjectType.MEMBER, "member1", null));
            clientList.getMember().add(createClientType(XRoadObjectType.SUBSYSTEM, "member1", "sub1"));
            clientList.getMember().add(createClientType(XRoadObjectType.SUBSYSTEM, "member1", "sub2"));
            clientList.getMember().add(createClientType(XRoadObjectType.SUBSYSTEM, "member1", "sub3"));
            clientList.getMember().add(createClientType(XRoadObjectType.MEMBER, "member2", null));
            clientList.getMember().add(createClientType(XRoadObjectType.SUBSYSTEM, "member2", "sssub1"));
            clientList.getMember().add(createClientType(XRoadObjectType.SUBSYSTEM, "member2", "sssub2"));

            mocked.when(() -> ClientListUtil.clientListFromResponse(any(String.class)))
                    .thenReturn(clientList);

            final Queue<ClientType> listMethodsQueue = new ConcurrentLinkedQueue<>();
            final Queue<String> fetchCompaniesQueue = new ConcurrentLinkedQueue<>();
            final Queue<String> fetchOrganisationsQueue = new ConcurrentLinkedQueue<>();

            final Member member1 = new Member();
            member1.setMemberCode("member1");
            final Member member2 = new Member();
            member2.setMemberCode("member2");
            Mockito.when(catalogService.saveAllMembersAndSubsystems(any())).thenReturn(Set.of(member1, member2));

            ListClientsTask listClientsTask = new ListClientsTask(applicationContext, listMethodsQueue,
                    fetchCompaniesQueue, fetchOrganisationsQueue);
            listClientsTask.run();

            verify(catalogService, times(1)).saveAllMembersAndSubsystems(any());

            assertEquals(5, listMethodsQueue.size());
            assertEquals(2, fetchCompaniesQueue.size());
            assertEquals(2, fetchOrganisationsQueue.size());
        }
    }

    @Test
    public void testWhenFetchNotUnlimitedAndTimeOutsideOfConfiguration() throws Exception {
        try (MockedStatic<ClientListUtil> mocked = mockStatic(ClientListUtil.class)) {
            TaskPoolConfiguration conf = applicationContext.getBean(TaskPoolConfiguration.class);

            ReflectionTestUtils.setField(conf, "fetchRunUnlimited", false);
            ReflectionTestUtils.setField(conf, "fetchTimeAfterHour", 23);
            ReflectionTestUtils.setField(conf, "fetchTimeBeforeHour", 23);

            ClientList clientList = new ClientList();
            clientList.getMember().add(createClientType(XRoadObjectType.MEMBER, "member1", null));
            clientList.getMember().add(createClientType(XRoadObjectType.SUBSYSTEM, "member1", "sub1"));
            clientList.getMember().add(createClientType(XRoadObjectType.SUBSYSTEM, "member1", "sub2"));
            clientList.getMember().add(createClientType(XRoadObjectType.SUBSYSTEM, "member1", "sub3"));
            clientList.getMember().add(createClientType(XRoadObjectType.MEMBER, "member2", null));
            clientList.getMember().add(createClientType(XRoadObjectType.SUBSYSTEM, "member2", "sssub1"));
            clientList.getMember().add(createClientType(XRoadObjectType.SUBSYSTEM, "member2", "sssub2"));

            mocked.when(() -> ClientListUtil.clientListFromResponse(any())).thenReturn(clientList);

            final Queue<ClientType> listMethodsQueue = new ConcurrentLinkedQueue<>();
            final Queue<String> fetchCompaniesQueue = new ConcurrentLinkedQueue<>();
            final Queue<String> fetchOrganisationsQueue = new ConcurrentLinkedQueue<>();

            final Member member1 = new Member();
            member1.setMemberCode("member1");
            final Member member2 = new Member();
            member2.setMemberCode("member2");
            Mockito.when(catalogService.saveAllMembersAndSubsystems(any())).thenReturn(Set.of(member1, member2));

            ListClientsTask listClientsTask = new ListClientsTask(applicationContext, listMethodsQueue,
                    fetchCompaniesQueue, fetchOrganisationsQueue);
            listClientsTask.run();

            verify(catalogService, times(0)).saveAllMembersAndSubsystems(any());

            assertEquals(0, listMethodsQueue.size());
            assertEquals(0, fetchCompaniesQueue.size());
            assertEquals(0, fetchOrganisationsQueue.size());
        }
    }

    @Test
    public void testOnReceiveWhenFetchNotUnlimitedButTimeIsInBetween() throws Exception {
        try (MockedStatic<ClientListUtil> mocked = mockStatic(ClientListUtil.class)) {
            TaskPoolConfiguration conf = applicationContext.getBean(TaskPoolConfiguration.class);

            ReflectionTestUtils.setField(conf, "fetchRunUnlimited", false);
            ReflectionTestUtils.setField(conf, "fetchTimeAfterHour", 0);
            ReflectionTestUtils.setField(conf, "fetchTimeBeforeHour", 23);

            ClientList clientList = new ClientList();
            clientList.getMember().add(createClientType(XRoadObjectType.MEMBER, "member1", null));
            clientList.getMember().add(createClientType(XRoadObjectType.SUBSYSTEM, "member1", "sub1"));
            clientList.getMember().add(createClientType(XRoadObjectType.SUBSYSTEM, "member1", "sub2"));
            clientList.getMember().add(createClientType(XRoadObjectType.SUBSYSTEM, "member1", "sub3"));
            clientList.getMember().add(createClientType(XRoadObjectType.MEMBER, "member2", null));
            clientList.getMember().add(createClientType(XRoadObjectType.SUBSYSTEM, "member2", "sssub1"));
            clientList.getMember().add(createClientType(XRoadObjectType.SUBSYSTEM, "member2", "sssub2"));

            mocked.when(() -> ClientListUtil.clientListFromResponse(any())).thenReturn(clientList);

            final Queue<ClientType> listMethodsQueue = new ConcurrentLinkedQueue<>();
            final Queue<String> fetchCompaniesQueue = new ConcurrentLinkedQueue<>();
            final Queue<String> fetchOrganisationsQueue = new ConcurrentLinkedQueue<>();

            final Member member1 = new Member();
            member1.setMemberCode("member1");
            final Member member2 = new Member();
            member2.setMemberCode("member2");
            Mockito.when(catalogService.saveAllMembersAndSubsystems(any())).thenReturn(Set.of(member1, member2));

            ListClientsTask listClientsTask = new ListClientsTask(applicationContext, listMethodsQueue,
                    fetchCompaniesQueue, fetchOrganisationsQueue);
            listClientsTask.run();

            verify(catalogService, times(1)).saveAllMembersAndSubsystems(any());

            assertEquals(5, listMethodsQueue.size());
            assertEquals(2, fetchCompaniesQueue.size());
            assertEquals(2, fetchOrganisationsQueue.size());
        }
    }

    @Test
    public void testOnReceiveWithEmptyMemberList() throws Exception {
        try (MockedStatic<ClientListUtil> mocked = mockStatic(ClientListUtil.class)) {
            TaskPoolConfiguration conf = applicationContext.getBean(TaskPoolConfiguration.class);

            ReflectionTestUtils.setField(conf, "fetchRunUnlimited", true);

            ClientList clientList = new ClientList();
            mocked.when(() -> ClientListUtil.clientListFromResponse(any())).thenReturn(clientList);

            final Queue<ClientType> listMethodsQueue = new ConcurrentLinkedQueue<>();
            final Queue<String> fetchCompaniesQueue = new ConcurrentLinkedQueue<>();
            final Queue<String> fetchOrganisationsQueue = new ConcurrentLinkedQueue<>();

            Mockito.when(catalogService.saveAllMembersAndSubsystems(any())).thenReturn(Set.of());

            ListClientsTask listClientsTask = new ListClientsTask(applicationContext, listMethodsQueue,
                    fetchCompaniesQueue, fetchOrganisationsQueue);
            listClientsTask.run();

            verify(catalogService, times(1)).saveAllMembersAndSubsystems(any());
            assertEquals(0, listMethodsQueue.size());
            assertEquals(0, fetchCompaniesQueue.size());
            assertEquals(0, fetchOrganisationsQueue.size());
        }
    }

    @Test
    public void testSaveErrorLog() {
        TaskPoolConfiguration conf = applicationContext.getBean(TaskPoolConfiguration.class);

        ReflectionTestUtils.setField(conf, "fetchRunUnlimited", true);

        final Queue<ClientType> listMethodsQueue = new ConcurrentLinkedQueue<>();
        final Queue<String> fetchCompaniesQueue = new ConcurrentLinkedQueue<>();
        final Queue<String> fetchOrganisationsQueue = new ConcurrentLinkedQueue<>();

        ListClientsTask listClientsTask = new ListClientsTask(applicationContext, listMethodsQueue,
                fetchCompaniesQueue, fetchOrganisationsQueue);
        listClientsTask.run();

        verify(catalogService, times(1)).saveErrorLog(any());
        assertEquals(0, listMethodsQueue.size());
        assertEquals(0, fetchCompaniesQueue.size());
        assertEquals(0, fetchOrganisationsQueue.size());
    }

    protected ClientType createClientType(XRoadObjectType objectType, String memberCode, String subsystemCode) {
        ClientType c = new ClientType();
        XRoadClientIdentifierType xrcit = new XRoadClientIdentifierType();

        xrcit.setXRoadInstance("FI");
        xrcit.setMemberClass("GOV");
        xrcit.setMemberCode(memberCode);
        xrcit.setSubsystemCode(subsystemCode);
        xrcit.setObjectType(objectType);
        c.setId(xrcit);
        c.setName(memberCode);
        return c;

    }
}
