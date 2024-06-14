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

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;

import org.springframework.context.ApplicationContext;

import fi.vrk.xroad.catalog.collector.configuration.TaskPoolConfiguration;
import fi.vrk.xroad.catalog.collector.util.ClientListUtil;
import fi.vrk.xroad.catalog.collector.util.ClientTypeUtil;
import fi.vrk.xroad.catalog.collector.util.CollectorUtils;
import fi.vrk.xroad.catalog.collector.wsimport.ClientList;
import fi.vrk.xroad.catalog.collector.wsimport.ClientType;
import fi.vrk.xroad.catalog.collector.wsimport.XRoadObjectType;
import fi.vrk.xroad.catalog.persistence.CatalogService;
import fi.vrk.xroad.catalog.persistence.entity.ErrorLog;
import fi.vrk.xroad.catalog.persistence.entity.Member;
import fi.vrk.xroad.catalog.persistence.entity.MemberId;
import fi.vrk.xroad.catalog.persistence.entity.Subsystem;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ListClientsTask implements Runnable {

    private final TaskPoolConfiguration taskPoolConfiguration;
    private final CatalogService catalogService;
    private final Queue<ClientType> listMethodsQueue;
    private final Queue<String> fetchCompaniesQueue;
    private final Queue<String> fetchOrganizationsQueue;

    public ListClientsTask(ApplicationContext applicationContext, Queue<ClientType> listMethodsQueue,
            Queue<String> fetchCompaniesQueue, Queue<String> fetchOrganizationsQueue) {
        this.taskPoolConfiguration = applicationContext.getBean(TaskPoolConfiguration.class);
        this.catalogService = applicationContext.getBean(CatalogService.class);
        this.listMethodsQueue = listMethodsQueue;
        this.fetchCompaniesQueue = fetchCompaniesQueue;
        this.fetchOrganizationsQueue = fetchOrganizationsQueue;
    }

    public void run() {
        if (CollectorUtils.isTimeBetweenHours(taskPoolConfiguration.getFlushLogTimeAfterHour(),
                taskPoolConfiguration.getFlushLogTimeBeforeHour())) {
            catalogService.deleteOldErrorLogEntries(taskPoolConfiguration.getErrorLogLengthInDays());
        }

        if (taskPoolConfiguration.isFetchRunUnlimited()
                || CollectorUtils.isTimeBetweenHours(taskPoolConfiguration.getFetchTimeAfterHour(),
                        taskPoolConfiguration.getFetchTimeBeforeHour())) {
            fetchClients();
        }
    }

    private void fetchClients() {
        String listClientsUrl = taskPoolConfiguration.getListClientsHost() + "/listClients";
        try {
            log.info("Getting client list from {}", listClientsUrl);
            ClientList clientList = ClientListUtil.clientListFromResponse(listClientsUrl);
            HashMap<MemberId, Member> m = populateMapWithMembers(clientList);
            Set<Member> newMembers = catalogService.saveAllMembersAndSubsystems(m.values());

            // We only fetch WSDL-s and REST services from subsystems
            List<ClientType> subsystems = clientList.getMember().stream()
                    .filter(client -> XRoadObjectType.SUBSYSTEM.equals(client.getId().getObjectType()))
                    .toList();
            listMethodsQueue.addAll(subsystems);

            log.info("All subsystems ({}) sent to ListMethodsTask", subsystems.size());

            // The fetchCompaniesQueue and fetchOrganizationsQueue should only be
            // initialized if the FI profile is active.
            if (fetchCompaniesQueue != null) {
                fetchCompaniesQueue.addAll(newMembers.stream().map(Member::getMemberCode).toList());
                log.info("{} new members sent to the FetchCompaniesTask", newMembers.size());
            }
            if (fetchOrganizationsQueue != null) {
                fetchOrganizationsQueue.addAll(newMembers.stream().map(Member::getMemberCode).toList());
                log.info("{} new members sent to the FetchOrganizationsTask", newMembers.size());
            }
        } catch (Exception e) {
            ErrorLog errorLog = CollectorUtils.createErrorLog(null,
                    "Error when fetching listClients(url: " + listClientsUrl + "): " + e.getMessage(), "500");
            catalogService.saveErrorLog(errorLog);
            log.error("Error when fetching listClients(url: {})", listClientsUrl, e);
        }

    }

    private HashMap<MemberId, Member> populateMapWithMembers(ClientList clientList) {
        HashMap<MemberId, Member> m = new HashMap<>();
        int clientCounter = 0;
        for (ClientType clientType : clientList.getMember()) {
            clientCounter++;
            log.debug("{} - {}", clientCounter, ClientTypeUtil.toString(clientType));
            Member newMember = new Member(clientType.getId().getXRoadInstance(), clientType.getId()
                    .getMemberClass(),
                    clientType.getId().getMemberCode(), clientType.getName());
            newMember.setSubsystems(new HashSet<>());
            m.putIfAbsent(newMember.createKey(), newMember);

            if (XRoadObjectType.SUBSYSTEM.equals(clientType.getId().getObjectType())) {
                Subsystem newSubsystem = new Subsystem(newMember, clientType.getId().getSubsystemCode());
                m.get(newMember.createKey()).getAllSubsystems().add(newSubsystem);
            }
        }

        return m;
    }
}
