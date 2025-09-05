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

import lombok.extern.slf4j.Slf4j;
import org.niis.xrd4j.common.member.ObjectType;
import org.niis.xroad.catalog.collector.configuration.TaskPoolConfiguration;
import org.niis.xroad.catalog.collector.events.NewMembersEventPublisher;
import org.niis.xroad.catalog.collector.service.CatalogService;
import org.niis.xroad.catalog.collector.util.ClientListUtil;
import org.niis.xroad.catalog.collector.util.IdentifierUtil;
import org.niis.xroad.catalog.collector.util.CollectorUtils;
import org.niis.xroad.catalog.collector.util.MemberWithName;
import org.niis.xroad.catalog.persistence.entity.ErrorLog;
import org.niis.xroad.catalog.persistence.entity.Member;
import org.niis.xroad.catalog.persistence.entity.MemberId;
import org.niis.xroad.catalog.persistence.entity.Subsystem;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Component
public class ListClientsTask implements Runnable {

    private final TaskPoolConfiguration taskPoolConfiguration;
    private final CatalogService catalogService;
    private final Queue<MemberWithName> listMethodsQueue;
    private final NewMembersEventPublisher newMembersEventPublisher;

    public ListClientsTask(CatalogService catalogService, TaskPoolConfiguration taskPoolConfiguration,
                           Queue<MemberWithName> listMethodsQueue, NewMembersEventPublisher newMembersEventPublisher) {
        this.taskPoolConfiguration = taskPoolConfiguration;
        this.catalogService = catalogService;
        this.listMethodsQueue = listMethodsQueue;
        this.newMembersEventPublisher = newMembersEventPublisher;
    }

    public void run() {
        log.info("Starting ListClientsTask");
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
            List<MemberWithName> clientList = ClientListUtil.clientListFromResponse(listClientsUrl);
            HashMap<MemberId, Member> m = populateMapWithMembers(clientList);
            Set<Member> newMembers = catalogService.saveAllMembersAndSubsystems(m.values());

            // We only fetch WSDL-s and REST services from subsystems
            List<MemberWithName> subsystems = clientList.stream()
                    .filter(client -> ObjectType.SUBSYSTEM.equals(client.getId().getObjectType()))
                    .toList();
            listMethodsQueue.addAll(subsystems);

            log.info("All subsystems ({}) sent to ListMethodsTask", subsystems.size());

            newMembersEventPublisher.publishNewMembersEvent(newMembers.stream().map(Member::getMemberCode).collect(Collectors.toSet()));
            log.info("{} new members were published as event", newMembers.size());
        } catch (Exception e) {
            ErrorLog errorLog = CollectorUtils.createErrorLog(null,
                    "Error when fetching listClients(url: " + listClientsUrl + "): " + e.getMessage(), "500");
            catalogService.saveErrorLog(errorLog);
            log.error("Error when fetching listClients(url: {})", listClientsUrl, e);
        }

    }

    private HashMap<MemberId, Member> populateMapWithMembers(List<MemberWithName> clientList) {
        HashMap<MemberId, Member> m = new HashMap<>();
        int clientCounter = 0;
        for (MemberWithName client : clientList) {
            clientCounter++;
            log.debug("{} - {}", clientCounter, IdentifierUtil.toString(client));
            Member newMember = new Member(client.getId().getXRoadInstance(),
                    client.getId().getMemberClass(),
                    client.getId().getMemberCode(),
                    client.getName());
            newMember.setSubsystems(new HashSet<>());
            m.putIfAbsent(newMember.createKey(), newMember);

            if (ObjectType.SUBSYSTEM.equals(client.getId().getObjectType())) {
                Subsystem newSubsystem = new Subsystem(newMember, client.getId().getSubsystemCode());
                m.get(newMember.createKey()).getAllSubsystems().add(newSubsystem);
            }
        }

        return m;
    }
}
