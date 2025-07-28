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

import org.niis.xroad.catalog.lister.dto.DistinctServiceStatistics;
import org.niis.xroad.catalog.lister.dto.LastCollectionData;
import org.niis.xroad.catalog.lister.dto.MemberData;
import org.niis.xroad.catalog.lister.dto.MemberDataList;
import org.niis.xroad.catalog.lister.dto.ServiceData;
import org.niis.xroad.catalog.lister.dto.ServiceStatistics;
import org.niis.xroad.catalog.lister.dto.SubsystemData;
import org.niis.xroad.catalog.lister.dto.XRoadData;
import org.niis.xroad.catalog.persistence.entity.ErrorLog;
import org.niis.xroad.catalog.persistence.entity.Member;
import org.niis.xroad.catalog.persistence.entity.OpenApi;
import org.niis.xroad.catalog.persistence.entity.Rest;
import org.niis.xroad.catalog.persistence.entity.Service;
import org.niis.xroad.catalog.persistence.entity.Subsystem;
import org.niis.xroad.catalog.persistence.entity.Wsdl;
import org.niis.xroad.catalog.persistence.repository.ErrorLogRepository;
import org.niis.xroad.catalog.persistence.repository.MemberRepository;
import org.niis.xroad.catalog.persistence.repository.OpenApiRepository;
import org.niis.xroad.catalog.persistence.repository.RestRepository;
import org.niis.xroad.catalog.persistence.repository.ServiceRepository;
import org.niis.xroad.catalog.persistence.repository.SubsystemRepository;
import org.niis.xroad.catalog.persistence.repository.WsdlRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Implementation for catalogservice CRUD
 */
@Component
@Transactional
@SuppressWarnings("PMD.AvoidFieldNameMatchingTypeName")
public class CatalogServiceImpl implements CatalogService {

    private static final String MULTIPLE_MATCHES_FOUND_TO = "multiple matches found to ";

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
    WsdlRepository wsdlRepository;

    @Autowired
    ErrorLogRepository errorLogRepository;

    @Override
    public Iterable<Member> getAllMembers() {
        return memberRepository.findAll();
    }

    @Override
    public Iterable<Member> getAllMembers(LocalDateTime startDateTime, LocalDateTime endDateTime) {
        return memberRepository.findAllChangedBetween(startDateTime, endDateTime);
    }

    @Override
    public Member getMember(String xRoadInstance, String memberClass, String memberCode) {
        return memberRepository.findActiveByNaturalKey(xRoadInstance, memberClass, memberCode);
    }

    @Override
    public Wsdl getWsdl(String externalId) {
        List<Wsdl> matches = wsdlRepository.findAnyByExternalId(externalId);
        if (matches.size() > 1) {
            throw new IllegalStateException(MULTIPLE_MATCHES_FOUND_TO + externalId + ": " + matches);
        } else if (matches.size() == 1) {
            return matches.iterator().next();
        } else {
            return null;
        }
    }

    @Override
    public OpenApi getOpenApi(String externalId) {
        List<OpenApi> matches = openApiRepository.findAnyByExternalId(externalId);
        if (matches.size() > 1) {
            throw new IllegalStateException(MULTIPLE_MATCHES_FOUND_TO + externalId + ": " + matches);
        } else if (matches.size() == 1) {
            return matches.iterator().next();
        } else {
            return null;
        }
    }

    @Override
    public Rest getRest(Service service) {
        List<Rest> matches = restRepository.findAnyByService(service);
        if (matches.size() > 1) {
            throw new IllegalStateException(
                    MULTIPLE_MATCHES_FOUND_TO + service.getServiceCode() + " serviceCode: " + matches);
        } else if (matches.size() == 1) {
            return matches.iterator().next();
        } else {
            return null;
        }
    }

    @Override
    public Service getService(String xRoadInstance,
            String memberClass,
            String memberCode,
            String serviceCode,
            String subsystemCode,
            String serviceVersion) {
        if (serviceVersion == null) {
            return serviceRepository.findAllByMemberServiceAndSubsystemVersionNull(xRoadInstance,
                    memberClass, memberCode, serviceCode, subsystemCode);
        }
        return serviceRepository.findAllByMemberServiceAndSubsystemAndVersion(xRoadInstance,
                memberClass, memberCode, serviceCode, subsystemCode, serviceVersion);
    }

    @Override
    public List<Service> getServices(String xRoadInstance,
            String memberClass,
            String memberCode,
            String subsystemCode,
            String serviceCode) {
        return serviceRepository.findServicesByMemberServiceAndSubsystem(xRoadInstance,
                memberClass,
                memberCode,
                serviceCode,
                subsystemCode);
    }

    @Override
    public List<ServiceStatistics> getServiceStatistics(LocalDateTime startDateTime, LocalDateTime endDateTime) {
        List<ServiceStatistics> serviceStatisticsList = new ArrayList<>();
        List<Service> services = serviceRepository.findAllActive();
        LocalDateTime dateInPast = startDateTime;
        while (isDateBetweenDates(dateInPast, startDateTime, endDateTime)) {
            ServiceStatistics serviceStatistics = createServiceStatistics(services, dateInPast, endDateTime);
            serviceStatisticsList.add(serviceStatistics);
            dateInPast = dateInPast.plusDays(1);
        }
        return serviceStatisticsList;
    }

    private ServiceStatistics createServiceStatistics(List<Service> services,
            LocalDateTime dateInPast,
            LocalDateTime endDateTime) {
        AtomicLong numberOfSoapServices = new AtomicLong();
        AtomicLong numberOfRestServices = new AtomicLong();
        AtomicLong numberOfOpenApiServices = new AtomicLong();

        services.forEach(service -> {
            LocalDateTime creationDate = service.getStatusInfo().getCreated();
            if (creationDate.isBefore(endDateTime)) {
                if (service.hasOpenApi()) {
                    numberOfOpenApiServices.getAndIncrement();
                } else if (service.hasWsdl()) {
                    numberOfSoapServices.getAndIncrement();
                } else {
                    numberOfRestServices.getAndIncrement();
                }
            }
        });

        return ServiceStatistics.builder()
                .created(dateInPast)
                .numberOfRestServices(numberOfRestServices.longValue())
                .numberOfSoapServices(numberOfSoapServices.longValue())
                .numberOfOpenApiServices(numberOfOpenApiServices.longValue()).build();
    }

    @Override
    public Page<ErrorLog> getErrors(XRoadData xRoadData,
            int page,
            int limit,
            LocalDateTime startDateTime,
            LocalDateTime endDateTime) {
        Page<ErrorLog> errorLogList;
        String xRoadInstance = xRoadData.getXRoadInstance();
        String memberClass = xRoadData.getMemberClass();
        String memberCode = xRoadData.getMemberCode();
        String subsystemCode = xRoadData.getSubsystemCode();

        if (xRoadInstance != null) {
            if (memberClass != null) {
                if (memberCode != null) {
                    if (subsystemCode != null) {
                        errorLogList = errorLogRepository.findAnyByAllParameters(startDateTime,
                                endDateTime,
                                xRoadInstance,
                                memberClass,
                                memberCode,
                                subsystemCode,
                                PageRequest.of(page, limit));
                    } else {
                        errorLogList = errorLogRepository.findAnyByMemberCode(startDateTime,
                                endDateTime,
                                xRoadInstance,
                                memberClass,
                                memberCode,
                                PageRequest.of(page, limit));
                    }
                } else {
                    errorLogList = errorLogRepository.findAnyByMemberClass(startDateTime,
                            endDateTime,
                            xRoadInstance,
                            memberClass,
                            PageRequest.of(page, limit));
                }
            } else {
                errorLogList = errorLogRepository.findAnyByInstance(startDateTime,
                        endDateTime,
                        xRoadInstance,
                        PageRequest.of(page, limit));
            }
        } else {
            errorLogList = errorLogRepository.findAnyByCreated(startDateTime,
                    endDateTime,
                    PageRequest.of(page, limit));
        }

        return errorLogList;
    }

    @Override
    public List<DistinctServiceStatistics> getDistinctServiceStatistics(LocalDateTime startDateTime,
            LocalDateTime endDateTime) {
        List<DistinctServiceStatistics> serviceStatisticsList = new ArrayList<>();
        List<Service> services = serviceRepository.findAllActive();
        LocalDateTime dateInPast = startDateTime;
        while (isDateBetweenDates(dateInPast, startDateTime, endDateTime)) {
            long totalDistinctServices = 0;
            List<Service> servicesBetweenDates = services.stream()
                    .filter(p -> p.getStatusInfo().getCreated().isBefore(endDateTime))
                    .toList();
            if (!servicesBetweenDates.isEmpty()) {
                totalDistinctServices = servicesBetweenDates.stream().map(Service::getServiceCode).distinct().count();

                DistinctServiceStatistics serviceStatistics = DistinctServiceStatistics.builder().created(dateInPast)
                        .numberOfDistinctServices(totalDistinctServices).build();

                serviceStatisticsList.add(serviceStatistics);
            }

            dateInPast = dateInPast.plusDays(1);
        }
        return serviceStatisticsList;
    }

    @Override
    public List<MemberDataList> getMemberData(LocalDateTime startDateTime, LocalDateTime endDateTime) {
        List<MemberDataList> listOfMemberDataLists = new ArrayList<>();
        Set<Member> members = memberRepository.findAll();
        LocalDateTime dateInPast = startDateTime;
        while (isDateBetweenDates(dateInPast, startDateTime, endDateTime)) {
            List<MemberData> memberDataList = new ArrayList<>();
            members.forEach(member -> {
                LocalDateTime creationDate = member.getStatusInfo().getCreated();
                if (creationDate.isBefore(endDateTime)) {
                    AtomicReference<Boolean> isProvider = new AtomicReference<>();
                    isProvider.set(Boolean.FALSE);
                    Set<Subsystem> subsystems = member.getAllSubsystems();
                    List<SubsystemData> subsystemDataList = new ArrayList<>();
                    subsystems.forEach(subsystem -> {
                        List<ServiceData> serviceDataList = new ArrayList<>();
                        Set<Service> services = subsystem.getAllServices();
                        services.forEach(service -> {
                            ServiceData serviceData = ServiceData.builder()
                                    .created(service.getStatusInfo().getCreated())
                                    .serviceCode(service.getServiceCode())
                                    .active(!service.getStatusInfo().isRemoved())
                                    .serviceVersion(service.getServiceVersion()).build();
                            serviceDataList.add(serviceData);
                            if (service.hasWsdl() || service.hasOpenApi()) {
                                isProvider.set(Boolean.TRUE);
                            }
                        });
                        SubsystemData subsystemData = SubsystemData.builder()
                                .created(subsystem.getStatusInfo().getCreated())
                                .subsystemCode(subsystem.getSubsystemCode())
                                .active(!subsystem.getStatusInfo().isRemoved())
                                .serviceList(serviceDataList).build();
                        subsystemDataList.add(subsystemData);
                    });

                    MemberData memberData = MemberData.builder()
                            .created(creationDate)
                            .provider(isProvider.get())
                            .memberClass(member.getMemberClass())
                            .memberCode(member.getMemberCode())
                            .name(member.getName())
                            .xRoadInstance(member.getXRoadInstance())
                            .subsystemList(subsystemDataList).build();
                    memberDataList.add(memberData);
                }
            });
            listOfMemberDataLists.add(MemberDataList.builder().date(dateInPast).memberDataList(memberDataList).build());
            dateInPast = dateInPast.plusDays(1);
        }

        return listOfMemberDataLists;
    }

    @Override
    public Iterable<ErrorLog> getErrorLog(LocalDateTime startDateTime, LocalDateTime endDateTime) {
        return errorLogRepository.findAny(startDateTime, endDateTime);
    }

    @Override
    public Boolean checkDatabaseConnection() {
        return Integer.valueOf(1).equals(memberRepository.checkConnection());
    }

    @Override
    public LastCollectionData getLastCollectionData() {
        return LastCollectionData.builder()
                .membersLastFetched(memberRepository.findLatestFetched() == null ? null
                        : LocalDateTime.ofInstant(serviceRepository.findLatestFetched(), ZoneId.systemDefault()))
                .openapisLastFetched(openApiRepository.findLatestFetched() == null ? null
                        : LocalDateTime.ofInstant(openApiRepository.findLatestFetched(), ZoneId.systemDefault()))
                .servicesLastFetched(serviceRepository.findLatestFetched() == null ? null
                        : LocalDateTime.ofInstant(serviceRepository.findLatestFetched(), ZoneId.systemDefault()))
                .subsystemsLastFetched(subsystemRepository.findLatestFetched() == null ? null
                        : LocalDateTime.ofInstant(serviceRepository.findLatestFetched(), ZoneId.systemDefault()))
                .wsdlsLastFetched(wsdlRepository.findLatestFetched() == null ? null
                        : LocalDateTime.ofInstant(wsdlRepository.findLatestFetched(), ZoneId.systemDefault())).build();
    }

    private boolean isDateBetweenDates(LocalDateTime dateToBeChecked,
            LocalDateTime startDate,
            LocalDateTime endDate) {
        return (dateToBeChecked.isAfter(startDate) || dateToBeChecked.isEqual(startDate))
                && (dateToBeChecked.isBefore(endDate) || dateToBeChecked.isEqual(endDate))
                && (dateToBeChecked.isBefore(LocalDateTime.now()) || dateToBeChecked.isEqual(LocalDateTime.now()));
    }

}
