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
package org.niis.xroad.catalog.lister.service;

import lombok.extern.slf4j.Slf4j;
import org.json.JSONException;
import org.junit.jupiter.api.Test;
import org.niis.xroad.catalog.lister.TestUtil;
import org.niis.xroad.catalog.lister.dto.DistinctServiceStatistics;
import org.niis.xroad.catalog.lister.dto.LastCollectionData;
import org.niis.xroad.catalog.lister.dto.MemberDataList;
import org.niis.xroad.catalog.lister.dto.ServiceStatistics;
import org.niis.xroad.catalog.lister.dto.XRoadData;
import org.niis.xroad.catalog.persistence.entity.ErrorLog;
import org.niis.xroad.catalog.persistence.entity.Member;
import org.niis.xroad.catalog.persistence.entity.OpenApi;
import org.niis.xroad.catalog.persistence.entity.Rest;
import org.niis.xroad.catalog.persistence.entity.Service;
import org.niis.xroad.catalog.persistence.entity.Subsystem;
import org.niis.xroad.catalog.persistence.entity.Wsdl;
import org.niis.xroad.catalog.persistence.repository.MemberRepository;
import org.niis.xroad.catalog.persistence.repository.ServiceRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.Month;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Slf4j
@SpringBootTest
@ActiveProfiles({"test", "general-testdata"})
@Transactional
public class CatalogServiceTest {
    @Autowired
    CatalogService catalogService;

    @Autowired
    MemberRepository memberRepository;

    @Autowired
    ServiceRepository serviceRepository;

    @Autowired
    TestUtil testUtil;

    @Test
    public void testGetWsdl() {
        Wsdl wsdl = catalogService.getWsdl("1000");
        assertNotNull(wsdl);
        assertEquals("<?xml version=\"1.0\" standalone=\"no\"?><wsdl-6-1-1-1-changed/>", wsdl.getData());
        assertEquals(7, wsdl.getService().getSubsystem().getId());
    }

    @Test
    public void testGetWsdlNotFound() {
        Wsdl wsdl = catalogService.getWsdl("9899");
        assertNull(wsdl);
    }

    @Test
    public void testGetWsdlMultipleException() {
        try {
            catalogService.getWsdl("9999");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("multiple matches found to 9999"));
        }
    }

    @Test
    public void testGetOpenApi() {
        OpenApi openApi = catalogService.getOpenApi("3003");
        assertNotNull(openApi);
        assertEquals("<openapi>", openApi.getData());
        assertEquals(8, openApi.getService().getSubsystem().getId());
    }

    @Test
    public void testGetOpenApiNotFound() {
        OpenApi openApi = catalogService.getOpenApi("9899");
        assertNull(openApi);
    }

    @Test
    public void testGetOpenApiMultipleException() {
        try {
            catalogService.getOpenApi("3004");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("multiple matches found to 3004"));
        }
    }

    @Test
    public void testGetRest() {
        Service service = serviceRepository.findById(13L).get();
        Rest rest = catalogService.getRest(service);
        assertNotNull(rest);
        assertEquals("{\"endpoint_list\": []}}", rest.getData());
        assertEquals(8, rest.getService().getSubsystem().getId());
    }

    @Test
    public void testGetRestNotFound() {
        Service service = serviceRepository.findById(12L).get();
        Rest rest = catalogService.getRest(service);
        assertNull(rest);
    }

    @Test
    public void testGetRestMultipleException() {
        try {
            Service service = serviceRepository.findById(1L).get();
            catalogService.getRest(service);
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("multiple matches found to"));
        }
    }

    @Test
    public void testGetErrorLog() {
        LocalDateTime changedAfter = LocalDateTime.of(2020, Month.JANUARY, 1, 0, 0, 0);
        LocalDateTime endDate = LocalDateTime.of(2022, Month.JANUARY, 1, 0, 0, 0);
        Iterable<ErrorLog> errorLogEntries = catalogService.getErrorLog(changedAfter, endDate);
        assertNotNull(errorLogEntries);
        assertEquals(true, errorLogEntries.iterator().hasNext());
    }

    @Test
    public void testGetErrorsForSubsystem() {
        XRoadData xRoadData = XRoadData.builder().xRoadInstance("DEV").memberClass("GOV").memberCode("1234")
                .subsystemCode("TestSubsystem").build();
        Page<ErrorLog> errorLogEntries = catalogService.getErrors(xRoadData, 0, 100,
                LocalDateTime.parse("2020-01-01T00:00:00"), LocalDateTime.now());
        assertNotNull(errorLogEntries);
        assertEquals(1, errorLogEntries.getNumberOfElements());
        assertEquals(1, errorLogEntries.getTotalPages());
    }

    @Test
    public void testGetErrorsForMemberCode() {
        XRoadData xRoadData = XRoadData.builder().xRoadInstance("DEV").memberClass("GOV").memberCode("1234")
                .subsystemCode(null).build();
        Page<ErrorLog> errorLogEntries = catalogService.getErrors(xRoadData, 0, 100,
                LocalDateTime.parse("2020-01-01T00:00:00"), LocalDateTime.now());
        assertNotNull(errorLogEntries);
        assertEquals(2, errorLogEntries.getNumberOfElements());
        assertEquals(1, errorLogEntries.getTotalPages());
    }

    @Test
    public void testGetErrorsForMemberClass() {
        XRoadData xRoadData = XRoadData.builder().xRoadInstance("DEV").memberClass("GOV").memberCode(null)
                .subsystemCode(null).build();
        Page<ErrorLog> errorLogEntries = catalogService.getErrors(xRoadData, 0, 100,
                LocalDateTime.parse("2020-01-01T00:00:00"), LocalDateTime.now());
        assertNotNull(errorLogEntries);
        assertEquals(3, errorLogEntries.getNumberOfElements());
        assertEquals(1, errorLogEntries.getTotalPages());
    }

    @Test
    public void testGetErrorsForInstance() {
        XRoadData xRoadData = XRoadData.builder().xRoadInstance("DEV").memberClass(null).memberCode(null)
                .subsystemCode(null).build();
        Page<ErrorLog> errorLogEntries = catalogService.getErrors(xRoadData, 0, 100,
                LocalDateTime.parse("2020-01-01T00:00:00"), LocalDateTime.now());
        assertNotNull(errorLogEntries);
        assertEquals(4, errorLogEntries.getNumberOfElements());
        assertEquals(1, errorLogEntries.getTotalPages());
    }

    @Test
    public void testGetErrorsAll() {
        XRoadData xRoadData = XRoadData.builder().xRoadInstance(null).memberClass(null).memberCode(null)
                .subsystemCode(null).build();
        Page<ErrorLog> errorLogEntries = catalogService.getErrors(xRoadData, 0, 100,
                LocalDateTime.parse("2020-01-01T00:00:00"), LocalDateTime.now());
        assertNotNull(errorLogEntries);
        assertEquals(7, errorLogEntries.getNumberOfElements());
        assertEquals(1, errorLogEntries.getTotalPages());
    }

    @Test
    public void testEntityTreesFetchedCorrectly() throws InterruptedException {
        assertEntityTreeFetchedCorrectly(catalogService.getAllMembers());
        LocalDateTime modifiedSince1800 = LocalDateTime.of(1800, 1, 1, 0, 0);
        LocalDateTime endDate = LocalDateTime.of(2022, Month.JANUARY, 1, 0, 0, 0);
        assertEntityTreeFetchedCorrectly(catalogService.getAllMembers(modifiedSince1800, endDate));
    }

    private void assertEntityTreeFetchedCorrectly(Iterable<Member> members) {
        log.info("members loaded, detaching");
        for (Member m : members) {
            testUtil.entityManagerDetach(m);
        }
        log.info("all members detached");
        testUtil.entityManagerClear();
        // member - subsystem - service should be fetched
        // service - wdsl should also be fetched
        // wsdl.data should not be fetched but this would require hibernate
        // bytecode enhancement, and is too much of a pain
        Member m = testUtil.getEntity(members, 1L).get();
        assertNotNull(m);
        Subsystem ss = testUtil.getEntity(m.getAllSubsystems(), 1L).get();
        assertNotNull(ss);
        Service s = testUtil.getEntity(ss.getAllServices(), 2L).get();
        assertNotNull(s);
        Wsdl wsdl = s.getWsdl();
        assertNotNull(wsdl);
        assertNotNull(wsdl.getData());
    }

    @Test
    public void testGetMember() {
        Member member = memberRepository.findById(1L).get();
        Member foundMember = catalogService.getMember(member.getXRoadInstance(),
                member.getMemberClass(), member.getMemberCode());
        assertNotNull(foundMember);
    }

    @Test
    public void testGetAllMembersSince() {
        // all members that contain parts that were modified since 1.1.2007 (3-8)
        Iterable<Member> members = catalogService.getAllMembers(
                testUtil.createDate(1, 1, 2017),
                testUtil.createDate(1, 1, 2022));
        log.info("found members: " + testUtil.getIds(members));
        assertEquals(Arrays.asList(3L, 4L, 5L, 6L, 7L, 8L),
                new ArrayList<Long>(testUtil.getIds(members)));
    }

    @Test
    public void testGetAllMembers() {
        Iterable<Member> members = catalogService.getAllMembers();
        assertEquals(Arrays.asList(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L),
                new ArrayList<Long>(testUtil.getIds(members)));
    }

    @Test
    public void testGetService() {
        Service service = serviceRepository.findById(1L).get();
        Service foundService = catalogService.getService(service.getSubsystem().getMember().getXRoadInstance(),
                service.getSubsystem().getMember().getMemberClass(),
                service.getSubsystem().getMember().getMemberCode(),
                service.getServiceCode(),
                service.getSubsystem().getSubsystemCode(),
                service.getServiceVersion());
        assertNotNull(foundService);
    }

    @Test
    public void testGetServiceNullVersion() {
        Service service = serviceRepository.findById(10L).get();
        Service foundService = catalogService.getService(service.getSubsystem().getMember().getXRoadInstance(),
                service.getSubsystem().getMember().getMemberClass(),
                service.getSubsystem().getMember().getMemberCode(),
                service.getServiceCode(),
                service.getSubsystem().getSubsystemCode(),
                service.getServiceVersion());
        assertNotNull(foundService);
    }

    @Test
    public void testGetServices() {
        Service service = serviceRepository.findById(1L).get();
        List<Service> foundServices = catalogService.getServices(service.getSubsystem().getMember().getXRoadInstance(),
                service.getSubsystem().getMember().getMemberClass(),
                service.getSubsystem().getMember().getMemberCode(),
                service.getSubsystem().getSubsystemCode(),
                service.getServiceCode());
        assertNotNull(foundServices);
        assertEquals(1, foundServices.size());
    }

    @Test
    public void testGetLastCollectionData() {
        LastCollectionData lastCollectionData = catalogService.getLastCollectionData();
        assertEquals(2017, lastCollectionData.getMembersLastFetched().getYear());
        assertEquals(2016, lastCollectionData.getOpenapisLastFetched().getYear());
        assertEquals(2017, lastCollectionData.getServicesLastFetched().getYear());
        assertEquals(2017, lastCollectionData.getSubsystemsLastFetched().getYear());
        assertEquals(2017, lastCollectionData.getWsdlsLastFetched().getYear());
    }

    @Test
    public void testGetServiceStatistics() throws JSONException {
        LocalDateTime startDateTime = LocalDateTime.of(2014, 1, 1, 0, 0);
        LocalDateTime endDateTime = LocalDateTime.of(2022, 1, 1, 0, 0);
        List<ServiceStatistics> serviceStatistics = catalogService.getServiceStatistics(startDateTime, endDateTime);
        assertEquals(2923, serviceStatistics.size());
    }

    @Test
    public void testGetDistinctServiceStatistics() throws JSONException {
        LocalDateTime startDateTime = LocalDateTime.of(2014, 1, 1, 0, 0);
        LocalDateTime endDateTime = LocalDateTime.of(2022, 1, 1, 0, 0);
        List<DistinctServiceStatistics> distinctServiceStatistics = catalogService
                .getDistinctServiceStatistics(startDateTime, endDateTime);
        assertEquals(2923, distinctServiceStatistics.size());
    }

    @Test
    public void testGetMemberData() throws JSONException {
        LocalDateTime startDateTime = LocalDateTime.of(2014, 1, 1, 0, 0);
        LocalDateTime endDateTime = LocalDateTime.of(2022, 1, 1, 0, 0);
        List<MemberDataList> members = catalogService.getMemberData(startDateTime, endDateTime);
        assertEquals(2923, members.size());
    }

    @Test
    public void testCheckDatabaseConnection() {
        assertTrue(catalogService.checkDatabaseConnection());
    }

}
