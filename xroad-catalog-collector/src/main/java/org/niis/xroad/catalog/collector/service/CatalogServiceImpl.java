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
package org.niis.xroad.catalog.collector.service;

import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.catalog.persistence.entity.Endpoint;
import org.niis.xroad.catalog.persistence.entity.ErrorLog;
import org.niis.xroad.catalog.persistence.entity.Member;
import org.niis.xroad.catalog.persistence.entity.MemberId;
import org.niis.xroad.catalog.persistence.entity.OpenApi;
import org.niis.xroad.catalog.persistence.entity.Rest;
import org.niis.xroad.catalog.persistence.entity.Service;
import org.niis.xroad.catalog.persistence.entity.ServiceId;
import org.niis.xroad.catalog.persistence.entity.StatusInfo;
import org.niis.xroad.catalog.persistence.entity.Subsystem;
import org.niis.xroad.catalog.persistence.entity.SubsystemId;
import org.niis.xroad.catalog.persistence.entity.Wsdl;
import org.niis.xroad.catalog.persistence.repository.EndpointRepository;
import org.niis.xroad.catalog.persistence.repository.ErrorLogRepository;
import org.niis.xroad.catalog.persistence.repository.MemberRepository;
import org.niis.xroad.catalog.persistence.repository.OpenApiRepository;
import org.niis.xroad.catalog.persistence.repository.RestRepository;
import org.niis.xroad.catalog.persistence.repository.ServiceRepository;
import org.niis.xroad.catalog.persistence.repository.SubsystemRepository;
import org.niis.xroad.catalog.persistence.repository.WsdlRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.StreamSupport;

/**
 * Implementation for catalogservice CRUD
 */
@org.springframework.stereotype.Service
@Transactional
@Slf4j
public class CatalogServiceImpl implements CatalogService {

    private static final String NOT_FOUND = " not found!";

    private static final String SUBSYSTEM_ID_REQUIRED = "subsystemId is required";

    private static final String SERVICE_ID_REQUIRED = "serviceId is required";

    @Autowired
    MemberRepository memberRepository;

    @Autowired
    SubsystemRepository subsystemRepository;

    @Autowired
    ServiceRepository serviceRepository;

    @Autowired
    OpenApiRepository openApiRepository;

    @Autowired
    RestRepository restRepository;

    @Autowired
    EndpointRepository endpointRepository;

    @Autowired
    WsdlRepository wsdlRepository;

    @Autowired
    ErrorLogRepository errorLogRepository;

    @Override
    public Set<Member> saveAllMembersAndSubsystems(Collection<Member> members) {
        LocalDateTime now = LocalDateTime.now();
        // process members
        Map<MemberId, Member> unprocessedOldMembers = new HashMap<>();
        StreamSupport.stream(memberRepository.findAll().spliterator(), false)
                .forEach(member -> unprocessedOldMembers.put(member.createKey(), member));
        Set<Member> newMembers = new HashSet<>();

        for (Member member : members) {
            Member oldMember = unprocessedOldMembers.get(member.createKey());
            if (oldMember == null) {
                // brand new item
                newMembers.add(member);
                member.getStatusInfo().setTimestampsForNew(now);
                for (Subsystem subsystem : member.getAllSubsystems()) {
                    subsystem.getStatusInfo().setTimestampsForNew(now);
                    subsystem.setMember(member);
                }
                member = memberRepository.save(member);
            } else {
                handleOldMember(now, member, oldMember);

                member = memberRepository.save(oldMember);
            }
            unprocessedOldMembers.remove(member.createKey());
        }
        // now unprocessedOldMembers should all be removed (either already removed, or
        // will be now)
        removeUnprocessedOldMembers(now, unprocessedOldMembers);
        return newMembers;
    }

    @Override
    public void saveServices(SubsystemId subsystemId, Collection<Service> services) {
        if (subsystemId == null) {
            throw new IllegalStateException("subsystem " + subsystemId + NOT_FOUND);
        }
        Subsystem oldSubsystem = subsystemRepository.findActiveByNaturalKey(subsystemId.getXRoadInstance(),
                subsystemId.getMemberClass(), subsystemId.getMemberCode(),
                subsystemId.getSubsystemCode());
        if (oldSubsystem == null) {
            throw new IllegalStateException("subsystem " + subsystemId + NOT_FOUND);
        }

        LocalDateTime now = LocalDateTime.now();

        Map<ServiceId, Service> unprocessedOldServices = new HashMap<>();
        oldSubsystem.getAllServices().stream().forEach(s -> unprocessedOldServices.put(s.createKey(), s));

        for (Service service : services) {
            Service oldService = unprocessedOldServices.get(service.createKey());
            if (oldService == null) {
                // brand new item, add it
                service.getStatusInfo().setTimestampsForNew(now);
                service.setSubsystem(oldSubsystem);
                oldSubsystem.getAllServices().add(service);
            } else {
                oldService.getStatusInfo().setTimestampsForFetched(now);
            }
            unprocessedOldServices.remove(service.createKey());
        }

        // now unprocessedOldServices should all be removed (either already removed, or
        // will be now)
        for (Service oldToRemove : unprocessedOldServices.values()) {
            StatusInfo status = oldToRemove.getStatusInfo();
            if (!status.isRemoved()) {
                status.setTimestampsForRemoved(now);
            }
        }

    }

    @Override
    public void saveWsdl(SubsystemId subsystemId, ServiceId serviceId, String wsdlString) {
        Assert.notNull(subsystemId, SUBSYSTEM_ID_REQUIRED);
        Assert.notNull(serviceId, SERVICE_ID_REQUIRED);
        if (isBlank(wsdlString)) {
            log.warn("Blank WSDL for service {}, keeping the stored descriptor", serviceId);
            saveBlankDescriptorErrorLog(subsystemId, serviceId, "WSDL");
            return;
        }
        Service oldService = getExistingService(subsystemId, serviceId);
        LocalDateTime now = LocalDateTime.now();
        Wsdl wsdl = new Wsdl();
        wsdl.setData(wsdlString);
        Wsdl oldWsdl = oldService.getWsdl();
        if (oldWsdl == null) {
            wsdl.initializeExternalId();
            wsdl.getStatusInfo().setTimestampsForNew(now);
            oldService.setWsdl(wsdl);
            wsdl.setService(oldService);
            wsdlRepository.save(wsdl);
        } else {
            if (oldWsdl.getStatusInfo().isRemoved()) {
                // resurrect
                oldWsdl.setData(wsdl.getData());
                oldWsdl.getStatusInfo().setChanged(now);
                oldWsdl.getStatusInfo().setRemoved(null);
                oldWsdl.getStatusInfo().setFetched(now);
            } else {
                // update existing
                boolean wsdlChanged = !oldWsdl.getData().equals(wsdl.getData());
                if (wsdlChanged) {
                    oldWsdl.getStatusInfo().setChanged(now);
                    oldWsdl.setData(wsdl.getData());
                }
                oldWsdl.getStatusInfo().setFetched(now);
            }
        }
    }

    @Override
    public void saveOpenApi(SubsystemId subsystemId, ServiceId serviceId, String openApiString) {
        Assert.notNull(subsystemId, SUBSYSTEM_ID_REQUIRED);
        Assert.notNull(serviceId, SERVICE_ID_REQUIRED);
        if (isBlank(openApiString)) {
            log.warn("Blank OpenAPI for service {}, keeping the stored descriptor", serviceId);
            saveBlankDescriptorErrorLog(subsystemId, serviceId, "OpenAPI");
            return;
        }
        Service oldService = getExistingService(subsystemId, serviceId);
        LocalDateTime now = LocalDateTime.now();
        OpenApi openApi = new OpenApi();
        openApi.setData(openApiString);
        OpenApi oldOpenApi = oldService.getOpenApi();
        if (oldOpenApi == null) {
            openApi.initializeExternalId();
            openApi.getStatusInfo().setTimestampsForNew(now);
            oldService.setOpenApi(openApi);
            openApi.setService(oldService);
            openApiRepository.save(openApi);
        } else {
            if (oldOpenApi.getStatusInfo().isRemoved()) {
                // resurrect
                oldOpenApi.setData(openApi.getData());
                oldOpenApi.getStatusInfo().setChanged(now);
                oldOpenApi.getStatusInfo().setRemoved(null);
                oldOpenApi.getStatusInfo().setFetched(now);
            } else {
                // update existing
                boolean openApiChanged = !oldOpenApi.getData().equals(openApi.getData());
                if (openApiChanged) {
                    oldOpenApi.getStatusInfo().setChanged(now);
                    oldOpenApi.setData(openApi.getData());
                }
                oldOpenApi.getStatusInfo().setFetched(now);
            }
        }
    }

    @Override
    public void saveRest(SubsystemId subsystemId, ServiceId serviceId, String restString) {
        Assert.notNull(subsystemId, SUBSYSTEM_ID_REQUIRED);
        Assert.notNull(serviceId, SERVICE_ID_REQUIRED);
        Service oldService = getExistingService(subsystemId, serviceId);
        LocalDateTime now = LocalDateTime.now();
        Rest rest = new Rest();
        rest.setData(restString);
        Rest oldRest = oldService.getRest();
        if (oldRest == null) {
            rest.initializeExternalId();
            rest.getStatusInfo().setTimestampsForNew(now);
            oldService.setRest(rest);
            rest.setService(oldService);
            restRepository.save(rest);
        } else {
            if (oldRest.getStatusInfo().isRemoved()) {
                // resurrect
                oldRest.setData(rest.getData());
                oldRest.getStatusInfo().setChanged(now);
                oldRest.getStatusInfo().setRemoved(null);
                oldRest.getStatusInfo().setFetched(now);
            } else {
                // update existing
                boolean restChanged = !oldRest.getData().equals(rest.getData());
                if (restChanged) {
                    oldRest.getStatusInfo().setChanged(now);
                    oldRest.setData(rest.getData());
                }
                oldRest.getStatusInfo().setFetched(now);
            }
        }
    }

    @Override
    public void saveEndpoint(SubsystemId subsystemId, ServiceId serviceId, String method, String path) {
        Assert.notNull(subsystemId, SUBSYSTEM_ID_REQUIRED);
        Assert.notNull(serviceId, SERVICE_ID_REQUIRED);
        Assert.notNull(method, "method is required");
        Assert.notNull(path, "path is required");
        Service oldService = getExistingService(subsystemId, serviceId);
        Endpoint oldEndpoint = endpointRepository.findAnyByServicePathAndMethod(oldService, method, path);
        if (oldEndpoint != null) {
            oldEndpoint.getStatusInfo().setChanged(LocalDateTime.now());
            oldEndpoint.getStatusInfo().setRemoved(null);
            oldEndpoint.getStatusInfo().setFetched(LocalDateTime.now());
        } else {
            Endpoint endpoint = new Endpoint();
            endpoint.setPath(path);
            endpoint.setMethod(method);
            endpoint.getStatusInfo().setTimestampsForNew(LocalDateTime.now());
            endpoint.getStatusInfo().setRemoved(null);
            oldService.setEndpoint(endpoint);
            endpoint.setService(oldService);
            endpointRepository.save(endpoint);
        }
    }

    @Override
    public void prepareEndpoints(SubsystemId subsystemId, ServiceId serviceId) {
        Assert.notNull(subsystemId, SUBSYSTEM_ID_REQUIRED);
        Assert.notNull(serviceId, SERVICE_ID_REQUIRED);
        Service oldService = getExistingService(subsystemId, serviceId);
        List<Endpoint> oldEndpoints = endpointRepository.findAnyByService(oldService);
        oldEndpoints.forEach(existingEndpoint -> {
            if (!existingEndpoint.getStatusInfo().isRemoved()) {
                existingEndpoint.getStatusInfo().setRemoved(LocalDateTime.now());
                existingEndpoint.getStatusInfo().setChanged(LocalDateTime.now());
                existingEndpoint.getStatusInfo().setFetched(LocalDateTime.now());
            }
        });
    }

    @Override
    public ErrorLog saveErrorLog(ErrorLog errorLog) {
        return errorLogRepository.save(errorLog);
    }

    @Override
    public void deleteOldErrorLogEntries(Integer daysBefore) {
        LocalDateTime oldDate = LocalDateTime.now().minusDays(daysBefore);
        errorLogRepository.deleteEntriesOlderThan(oldDate);
    }

    @Override
    public Set<String> getMembersRequiringExternalUpdate(int daysSinceLastUpdate, int batchSize) {
        return memberRepository.findMembersRequiringExternalUpdate(daysSinceLastUpdate, batchSize);
    }

    private void handleOldMember(LocalDateTime now, Member member, Member oldMember) {
        oldMember.updateWithDataFrom(member, now);
        // process subsystems for the old member
        Map<SubsystemId, Subsystem> unprocessedOldSubsystems = new HashMap<>();
        for (Subsystem subsystem : oldMember.getAllSubsystems()) {
            unprocessedOldSubsystems.put(subsystem.createKey(), subsystem);
        }
        for (Subsystem subsystem : member.getAllSubsystems()) {
            Subsystem oldSubsystem = unprocessedOldSubsystems.get(subsystem.createKey());
            if (oldSubsystem == null) {
                // brand new item, add it
                subsystem.getStatusInfo().setTimestampsForNew(now);
                subsystem.setMember(oldMember);
                oldMember.getAllSubsystems().add(subsystem);
            } else {
                oldSubsystem.getStatusInfo().setTimestampsForFetched(now);
            }
            unprocessedOldSubsystems.remove(subsystem.createKey());
        }
        // remaining old subsystems - that were not included in member.subsystems -
        // are removed (if not already)
        for (Subsystem oldToRemove : unprocessedOldSubsystems.values()) {
            StatusInfo status = oldToRemove.getStatusInfo();
            if (!status.isRemoved()) {
                status.setTimestampsForRemoved(now);
            }
        }
    }

    private void removeUnprocessedOldMembers(LocalDateTime now, Map<MemberId, Member> unprocessedOldMembers) {
        for (Member oldToRemove : unprocessedOldMembers.values()) {
            StatusInfo status = oldToRemove.getStatusInfo();
            if (!status.isRemoved()) {
                status.setTimestampsForRemoved(now);
            }
            for (Subsystem subsystem : oldToRemove.getAllSubsystems()) {
                if (!subsystem.getStatusInfo().isRemoved()) {
                    subsystem.getStatusInfo().setTimestampsForRemoved(now);
                }
            }
        }
    }

    private Service getExistingService(SubsystemId subsystemId, ServiceId serviceId) {
        Service oldService;
        if (serviceId.getServiceVersion() == null) {
            oldService = serviceRepository.findActiveNullVersionByNaturalKey(
                    subsystemId.getXRoadInstance(),
                    subsystemId.getMemberClass(), subsystemId.getMemberCode(),
                    subsystemId.getSubsystemCode(), serviceId.getServiceCode());
        } else {
            oldService = serviceRepository.findActiveByNaturalKey(subsystemId.getXRoadInstance(),
                    subsystemId.getMemberClass(), subsystemId.getMemberCode(),
                    subsystemId.getSubsystemCode(), serviceId.getServiceCode(),
                    serviceId.getServiceVersion());
        }
        if (oldService == null) {
            throw new IllegalStateException("service " + serviceId + NOT_FOUND);
        }
        return oldService;
    }

    private void saveBlankDescriptorErrorLog(SubsystemId subsystemId, ServiceId serviceId, String descriptorType) {
        ErrorLog errorLog = ErrorLog.builder()
                .created(LocalDateTime.now())
                .message("Blank " + descriptorType + " descriptor fetched for service " + serviceId
                        + ", keeping the stored descriptor")
                .code("500")
                .xRoadInstance(subsystemId.getXRoadInstance())
                .memberClass(subsystemId.getMemberClass())
                .memberCode(subsystemId.getMemberCode())
                .subsystemCode(subsystemId.getSubsystemCode())
                .serviceCode(serviceId.getServiceCode())
                .serviceVersion(serviceId.getServiceVersion())
                .build();
        errorLogRepository.save(errorLog);
    }

    // A failed WSDL or OpenAPI fetch produces no descriptor content; storing it would destroy the collected one.
    private static boolean isBlank(String data) {
        return data == null || data.isBlank();
    }
}
