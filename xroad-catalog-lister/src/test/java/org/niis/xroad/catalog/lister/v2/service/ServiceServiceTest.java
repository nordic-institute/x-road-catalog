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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.catalog.lister.v2.controller.MultipleVersionsException;
import org.niis.xroad.catalog.lister.v2.controller.BadRequestException;
import org.niis.xroad.catalog.lister.v2.dto.DescriptorPayload;
import org.niis.xroad.catalog.lister.v2.dto.ServiceDto;
import org.niis.xroad.catalog.lister.v2.dto.ServiceVersionDto;
import org.niis.xroad.catalog.persistence.v2.repository.DescriptorRepository;
import org.niis.xroad.catalog.persistence.v2.repository.ServiceRepository;
import org.niis.xroad.catalog.persistence.v2.repository.SubsystemRepository;
import org.niis.xroad.catalog.persistence.v2.repository.projection.ServiceAggregateRow;
import org.niis.xroad.catalog.persistence.v2.repository.projection.ServiceVersionRow;
import org.niis.xroad.catalog.persistence.v2.entity.Service;
import org.niis.xroad.catalog.persistence.v2.entity.StatusInfo;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ServiceServiceTest {

    private static final String INSTANCE = "TEST-INSTANCE";
    private static final String PUB = "PUB";
    private static final String CODE_14151328 = "14151328";
    private static final String SUBSYSTEM_A1 = "subsystem_a1";
    private static final String MIXED_SVC = "mixedSvc";
    private static final String REST = "REST";
    private static final String BLANK_DATA = "   ";
    private static final String OPENAPI_JSON = "{\"openapi\":\"3.0.0\"}";

    @Mock
    private ServiceRepository serviceRepository;

    @Mock
    private SubsystemRepository subsystemRepository;

    @Mock
    private DescriptorRepository descriptorRepository;

    @Mock
    private SharedParamsCache sharedParamsCache;

    private ServiceService service;

    @BeforeEach
    void setUp() {
        service = new ServiceService(serviceRepository, subsystemRepository, descriptorRepository,
                sharedParamsCache);
        org.mockito.Mockito.lenient().when(sharedParamsCache.getCurrentInstance()).thenReturn(INSTANCE);
    }

    @Test
    void testGetByNaturalKeyAggregatesMixedTypeVersions() {
        when(serviceRepository.findActiveVersionRowsForService(INSTANCE, PUB, CODE_14151328, SUBSYSTEM_A1, MIXED_SVC))
                .thenReturn(List.of(
                        versionRow(1L, MIXED_SVC, "v1", "SOAP"),
                        versionRow(1L, MIXED_SVC, "v2", REST)));

        Optional<ServiceDto> result = service.getByNaturalKey(PUB, CODE_14151328, SUBSYSTEM_A1, MIXED_SVC);

        assertTrue(result.isPresent());
        ServiceDto dto = result.get();
        assertEquals(2, dto.getVersionCount());
        assertTrue(dto.getServiceTypes().containsAll(List.of("SOAP", REST)));
    }

    @Test
    void testGetByNaturalKeyReturnsEmptyOptionalForMissingService() {
        when(serviceRepository.findActiveVersionRowsForService(INSTANCE, PUB, CODE_14151328, SUBSYSTEM_A1, "doesNotExist"))
                .thenReturn(List.of());

        assertTrue(service.getByNaturalKey(PUB, CODE_14151328, SUBSYSTEM_A1, "doesNotExist").isEmpty());
    }

    @Test
    void testGetForListShortCircuitsOnZeroCount() {
        when(serviceRepository.countActiveAggregatesForList(INSTANCE, PUB, null)).thenReturn(0L);

        Page<ServiceDto> page = service.getForList(PUB, null, PageRequest.of(0, 50));

        assertTrue(page.isEmpty());
        verify(serviceRepository, never()).findActiveAggregatesForList(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void testGetForListRejectsUnknownServiceType() {
        assertThrows(BadRequestException.class,
                () -> service.getForList(PUB, "GRAPHQL", PageRequest.of(0, 50)));
    }

    @Test
    void testGetForListNormalizesBlankServiceTypeToNoFilter() {
        when(serviceRepository.countActiveAggregatesForList(INSTANCE, PUB, null)).thenReturn(0L);

        Page<ServiceDto> page = service.getForList(PUB, "  ", PageRequest.of(0, 50));

        assertTrue(page.isEmpty());
        verify(serviceRepository, times(1)).countActiveAggregatesForList(INSTANCE, PUB, null);
    }

    @Test
    void testGetForListUses3QueriesAndFiltersExactPairs() {
        // Two subsystems (10, 20) both have a serviceCode "svc" aggregate; the over-selected version
        // batch returns rows for both cross-product pairs, and byKey must discard the (10, "other")
        // ghost pair while keeping each aggregate's (subsystemId, serviceCode) versions separate.
        when(serviceRepository.countActiveAggregatesForList(INSTANCE, PUB, null)).thenReturn(2L);
        ServiceAggregateRow agg1 = aggregateRow(10L, "svc");
        ServiceAggregateRow agg2 = aggregateRow(20L, "svc");
        when(serviceRepository.findActiveAggregatesForList(INSTANCE, PUB, null, PageRequest.of(0, 50)))
                .thenReturn(List.of(agg1, agg2));
        when(serviceRepository.findActiveVersionRowsForKeys(anySet(), anySet())).thenReturn(List.of(
                versionRowForSubsystem(10L, "svc", "v1", "SOAP"),
                versionRowForSubsystem(20L, "svc", "v1", REST),
                versionRowForSubsystem(20L, "svc", "v2", REST)));

        Page<ServiceDto> page = service.getForList(PUB, null, PageRequest.of(0, 50));

        assertEquals(2, page.getContent().size());
        ServiceDto dto1 = page.getContent().get(0);
        ServiceDto dto2 = page.getContent().get(1);
        assertEquals(1, dto1.getVersionCount(), "subsystem 10's aggregate must only see its own version");
        assertEquals(2, dto2.getVersionCount(), "subsystem 20's aggregate must only see its own two versions");
        verify(serviceRepository, times(1)).countActiveAggregatesForList(INSTANCE, PUB, null);
        verify(serviceRepository, times(1)).findActiveAggregatesForList(INSTANCE, PUB, null, PageRequest.of(0, 50));
        verify(serviceRepository, times(1)).findActiveVersionRowsForKeys(anySet(), anySet());
    }

    @Test
    void testGetForListOmitsAggregateWhoseVersionRowsVanished() {
        // The collector can soft-delete a service between the aggregate and the version query; the
        // aggregate is then gone, so it is omitted rather than failing the whole page.
        when(serviceRepository.countActiveAggregatesForList(INSTANCE, PUB, null)).thenReturn(2L);
        when(serviceRepository.findActiveAggregatesForList(INSTANCE, PUB, null, PageRequest.of(0, 50)))
                .thenReturn(List.of(aggregateRow(10L, "svc"), aggregateRow(20L, "vanishedSvc")));
        when(serviceRepository.findActiveVersionRowsForKeys(anySet(), anySet())).thenReturn(List.of(
                versionRowForSubsystem(10L, "svc", "v1", "SOAP")));

        Page<ServiceDto> page = service.getForList(PUB, null, PageRequest.of(0, 50));

        assertEquals(1, page.getContent().size());
        assertEquals("svc", page.getContent().get(0).getServiceCode());
        assertEquals(1, page.getContent().get(0).getVersionCount());
    }

    @Test
    void testGetForSubsystemReturnsEmptyOptionalWhenSubsystemAbsent() {
        when(subsystemRepository.existsActiveByNaturalKey(INSTANCE, PUB, CODE_14151328, "does-not-exist"))
                .thenReturn(false);

        assertTrue(service.getForSubsystem(PUB, CODE_14151328, "does-not-exist").isEmpty());
    }

    @Test
    void testGetForSubsystemReturnsAggregatesInQueryOrder() {
        when(subsystemRepository.existsActiveByNaturalKey(INSTANCE, PUB, CODE_14151328, SUBSYSTEM_A1))
                .thenReturn(true);
        when(serviceRepository.findActiveVersionRowsForSubsystem(INSTANCE, PUB, CODE_14151328, SUBSYSTEM_A1))
                .thenReturn(List.of(
                        versionRow(1L, "getRandom", null, REST),
                        versionRow(2L, MIXED_SVC, "v1", "SOAP"),
                        versionRow(2L, MIXED_SVC, "v2", REST)));

        Optional<List<ServiceDto>> result = service.getForSubsystem(PUB, CODE_14151328, SUBSYSTEM_A1);

        assertTrue(result.isPresent());
        List<ServiceDto> services = result.get();
        assertEquals(List.of("getRandom", MIXED_SVC), services.stream().map(ServiceDto::getServiceCode).toList());
        ServiceDto mixed = services.get(1);
        assertEquals(2, mixed.getVersionCount());
    }

    @Test
    void testGetVersionsReturnsRepositoryOrderOrEmptyWhenAbsent() {
        Service v1 = serviceEntity(MIXED_SVC, "v2", REST);
        Service v2 = serviceEntity(MIXED_SVC, "v1", "SOAP");
        when(serviceRepository.findActiveVersionsByNaturalKey(INSTANCE, PUB, CODE_14151328, SUBSYSTEM_A1, MIXED_SVC))
                .thenReturn(List.of(v1, v2));

        Optional<List<ServiceVersionDto>> versions = service.getVersions(PUB, CODE_14151328, SUBSYSTEM_A1, MIXED_SVC);

        assertTrue(versions.isPresent());
        assertEquals(2, versions.get().size());
        assertEquals("v2", versions.get().get(0).getServiceVersion());
        assertEquals("v1", versions.get().get(1).getServiceVersion());
    }

    @Test
    void testGetVersionsReturnsEmptyOptionalWhenServiceAbsent() {
        when(serviceRepository.findActiveVersionsByNaturalKey(INSTANCE, PUB, CODE_14151328, SUBSYSTEM_A1, "doesNotExist"))
                .thenReturn(List.of());

        assertTrue(service.getVersions(PUB, CODE_14151328, SUBSYSTEM_A1, "doesNotExist").isEmpty());
    }

    @Test
    void testGetVersionResolvesNullSentinel() {
        Service svc = serviceEntity("service-with-null-version", null, REST);
        when(serviceRepository.findActiveNullVersionByNaturalKey(INSTANCE, PUB, "15", "subsystem_7-1",
                "service-with-null-version")).thenReturn(Optional.of(svc));

        Optional<ServiceVersionDto> result =
                service.getVersion(PUB, "15", "subsystem_7-1", "service-with-null-version", "null");

        assertTrue(result.isPresent());
        assertNull(result.get().getServiceVersion());
    }

    @Test
    void testGetVersionReturnsEmptyOptionalWhenAbsent() {
        when(serviceRepository.findActiveVersionByNaturalKey(INSTANCE, PUB, CODE_14151328, SUBSYSTEM_A1, MIXED_SVC, "v99"))
                .thenReturn(Optional.empty());

        assertTrue(service.getVersion(PUB, CODE_14151328, SUBSYSTEM_A1, MIXED_SVC, "v99").isEmpty());
    }

    @Test
    void testGetVersionDescriptorReturnsWsdlAsXml() {
        Service svc = serviceEntityWithId(101L, MIXED_SVC, "v1", "SOAP");
        when(serviceRepository.findActiveVersionByNaturalKey(INSTANCE, PUB, CODE_14151328, SUBSYSTEM_A1, MIXED_SVC, "v1"))
                .thenReturn(Optional.of(svc));
        when(descriptorRepository.findActiveWsdlData(101L)).thenReturn(List.of("<wsdl>wsdl-mixedSvc-v1</wsdl>"));

        Optional<DescriptorPayload> result = service.getVersionDescriptor(PUB, CODE_14151328, SUBSYSTEM_A1, MIXED_SVC, "v1");

        assertTrue(result.isPresent());
        DescriptorPayload payload = result.get();
        assertEquals(MediaType.APPLICATION_XML, payload.contentType());
        assertTrue(new String(payload.content(), StandardCharsets.UTF_8).contains("wsdl-mixedSvc-v1"));
    }

    @Test
    void testGetVersionDescriptorReturnsOpenApiJson() {
        Service svc = serviceEntityWithId(102L, "descJsonSvc", "v1", "OPENAPI");
        when(serviceRepository.findActiveVersionByNaturalKey(INSTANCE, PUB, CODE_14151328, SUBSYSTEM_A1, "descJsonSvc", "v1"))
                .thenReturn(Optional.of(svc));
        when(descriptorRepository.findActiveWsdlData(102L)).thenReturn(List.of());
        when(descriptorRepository.findActiveOpenApiData(102L)).thenReturn(List.of(OPENAPI_JSON));

        Optional<DescriptorPayload> result =
                service.getVersionDescriptor(PUB, CODE_14151328, SUBSYSTEM_A1, "descJsonSvc", "v1");

        assertTrue(result.isPresent());
        assertEquals(MediaType.APPLICATION_JSON, result.get().contentType());
    }

    @Test
    void testGetVersionDescriptorReturnsOpenApiYaml() {
        Service svc = serviceEntityWithId(103L, "descYamlSvc", "v1", "OPENAPI");
        when(serviceRepository.findActiveVersionByNaturalKey(INSTANCE, PUB, CODE_14151328, SUBSYSTEM_A1, "descYamlSvc", "v1"))
                .thenReturn(Optional.of(svc));
        when(descriptorRepository.findActiveWsdlData(103L)).thenReturn(List.of());
        when(descriptorRepository.findActiveOpenApiData(103L)).thenReturn(List.of("openapi: 3.0.0\ninfo:\n  title: x"));

        Optional<DescriptorPayload> result =
                service.getVersionDescriptor(PUB, CODE_14151328, SUBSYSTEM_A1, "descYamlSvc", "v1");

        assertTrue(result.isPresent());
        DescriptorPayload payload = result.get();
        assertEquals(MediaType.parseMediaType("application/yaml"), payload.contentType());
        assertTrue(new String(payload.content(), StandardCharsets.UTF_8).startsWith("openapi:"));
    }

    @Test
    void testGetVersionDescriptorReturnsOpenApiJsonForArrayContent() {
        Service svc = serviceEntityWithId(106L, "descJsonArraySvc", "v1", "OPENAPI");
        when(serviceRepository.findActiveVersionByNaturalKey(INSTANCE, PUB, CODE_14151328, SUBSYSTEM_A1,
                "descJsonArraySvc", "v1")).thenReturn(Optional.of(svc));
        when(descriptorRepository.findActiveWsdlData(106L)).thenReturn(List.of());
        when(descriptorRepository.findActiveOpenApiData(106L)).thenReturn(List.of("[{\"openapi\":\"3.0.0\"}]"));

        Optional<DescriptorPayload> result =
                service.getVersionDescriptor(PUB, CODE_14151328, SUBSYSTEM_A1, "descJsonArraySvc", "v1");

        assertTrue(result.isPresent());
        assertEquals(MediaType.APPLICATION_JSON, result.get().contentType());
    }

    @Test
    void testGetVersionDescriptorReturnsOpenApiJsonForLeadingWhitespaceContent() {
        Service svc = serviceEntityWithId(107L, "descPaddedJsonSvc", "v1", "OPENAPI");
        when(serviceRepository.findActiveVersionByNaturalKey(INSTANCE, PUB, CODE_14151328, SUBSYSTEM_A1,
                "descPaddedJsonSvc", "v1")).thenReturn(Optional.of(svc));
        when(descriptorRepository.findActiveWsdlData(107L)).thenReturn(List.of());
        when(descriptorRepository.findActiveOpenApiData(107L)).thenReturn(List.of("\n  \t{\"openapi\":\"3.0.0\"}"));

        Optional<DescriptorPayload> result =
                service.getVersionDescriptor(PUB, CODE_14151328, SUBSYSTEM_A1, "descPaddedJsonSvc", "v1");

        assertTrue(result.isPresent());
        DescriptorPayload payload = result.get();
        assertEquals(MediaType.APPLICATION_JSON, payload.contentType());
        assertEquals("\n  \t{\"openapi\":\"3.0.0\"}", new String(payload.content(), StandardCharsets.UTF_8));
    }

    @Test
    void testGetVersionDescriptorTreatsBlankOpenApiAsNoDescriptor() {
        // A blank row carries no descriptor (e.g. written by an older failed fetch); serving it would
        // be an empty 200 typed application/yaml instead of an honest 404.
        Service svc = serviceEntityWithId(108L, "descEmptySvc", "v1", "OPENAPI");
        when(serviceRepository.findActiveVersionByNaturalKey(INSTANCE, PUB, CODE_14151328, SUBSYSTEM_A1,
                "descEmptySvc", "v1")).thenReturn(Optional.of(svc));
        when(descriptorRepository.findActiveWsdlData(108L)).thenReturn(List.of());
        when(descriptorRepository.findActiveOpenApiData(108L)).thenReturn(List.of(BLANK_DATA));

        assertTrue(service.getVersionDescriptor(PUB, CODE_14151328, SUBSYSTEM_A1, "descEmptySvc", "v1").isEmpty());
    }

    @Test
    void testGetVersionDescriptorFallsBackToOpenApiWhenWsdlIsBlank() {
        Service svc = serviceEntityWithId(109L, "descBlankWsdlSvc", "v1", "OPENAPI");
        when(serviceRepository.findActiveVersionByNaturalKey(INSTANCE, PUB, CODE_14151328, SUBSYSTEM_A1,
                "descBlankWsdlSvc", "v1")).thenReturn(Optional.of(svc));
        when(descriptorRepository.findActiveWsdlData(109L)).thenReturn(List.of(BLANK_DATA));
        when(descriptorRepository.findActiveOpenApiData(109L)).thenReturn(List.of(OPENAPI_JSON));

        Optional<DescriptorPayload> result =
                service.getVersionDescriptor(PUB, CODE_14151328, SUBSYSTEM_A1, "descBlankWsdlSvc", "v1");

        assertTrue(result.isPresent());
        assertEquals(MediaType.APPLICATION_JSON, result.get().contentType());
    }

    @Test
    void testGetServiceLevelDescriptorTreatsBlankOpenApiAsNoDescriptor() {
        Service svc = serviceEntityWithId(110L, "descEmptySvc", "v1", "OPENAPI");
        when(serviceRepository.findActiveVersionsByNaturalKey(INSTANCE, PUB, CODE_14151328, SUBSYSTEM_A1,
                "descEmptySvc")).thenReturn(List.of(svc));
        when(descriptorRepository.findActiveWsdlData(110L)).thenReturn(List.of());
        when(descriptorRepository.findActiveOpenApiData(110L)).thenReturn(List.of(""));

        assertTrue(service.getServiceLevelDescriptor(PUB, CODE_14151328, SUBSYSTEM_A1, "descEmptySvc").isEmpty());
    }

    @Test
    void testGetVersionDescriptorReturnsEmptyOptionalForNoDescriptor() {
        Service svc = serviceEntityWithId(104L, "descRestOnlySvc", "v1", REST);
        when(serviceRepository.findActiveVersionByNaturalKey(INSTANCE, PUB, CODE_14151328, SUBSYSTEM_A1, "descRestOnlySvc", "v1"))
                .thenReturn(Optional.of(svc));
        when(descriptorRepository.findActiveWsdlData(104L)).thenReturn(List.of());
        when(descriptorRepository.findActiveOpenApiData(104L)).thenReturn(List.of());

        assertTrue(service.getVersionDescriptor(PUB, CODE_14151328, SUBSYSTEM_A1, "descRestOnlySvc", "v1").isEmpty());
    }

    @Test
    void testGetVersionDescriptorReturnsEmptyOptionalForMissingVersion() {
        when(serviceRepository.findActiveVersionByNaturalKey(INSTANCE, PUB, CODE_14151328, SUBSYSTEM_A1, MIXED_SVC, "v99"))
                .thenReturn(Optional.empty());

        assertTrue(service.getVersionDescriptor(PUB, CODE_14151328, SUBSYSTEM_A1, MIXED_SVC, "v99").isEmpty());
    }

    @Test
    void testGetServiceLevelDescriptorReturnsForSingleVersion() {
        Service svc = serviceEntityWithId(105L, "descJsonSvc", "v1", "OPENAPI");
        when(serviceRepository.findActiveVersionsByNaturalKey(INSTANCE, PUB, CODE_14151328, SUBSYSTEM_A1, "descJsonSvc"))
                .thenReturn(List.of(svc));
        when(descriptorRepository.findActiveWsdlData(105L)).thenReturn(List.of());
        when(descriptorRepository.findActiveOpenApiData(105L)).thenReturn(List.of(OPENAPI_JSON));

        Optional<DescriptorPayload> result =
                service.getServiceLevelDescriptor(PUB, CODE_14151328, SUBSYSTEM_A1, "descJsonSvc");

        assertTrue(result.isPresent());
        assertEquals(MediaType.APPLICATION_JSON, result.get().contentType());
    }

    @Test
    void testGetServiceLevelDescriptorReturnsEmptyOptionalForNoVersions() {
        when(serviceRepository.findActiveVersionsByNaturalKey(INSTANCE, PUB, CODE_14151328, SUBSYSTEM_A1, "doesNotExist"))
                .thenReturn(List.of());

        assertTrue(service.getServiceLevelDescriptor(PUB, CODE_14151328, SUBSYSTEM_A1, "doesNotExist").isEmpty());
    }

    @Test
    void testGetServiceLevelDescriptorThrowsOnMultipleVersions() {
        Service v1 = serviceEntity(MIXED_SVC, "v1", "SOAP");
        Service v2 = serviceEntity(MIXED_SVC, "v2", REST);
        when(serviceRepository.findActiveVersionsByNaturalKey(INSTANCE, PUB, CODE_14151328, SUBSYSTEM_A1, MIXED_SVC))
                .thenReturn(List.of(v1, v2));

        MultipleVersionsException ex = assertThrows(MultipleVersionsException.class,
                () -> service.getServiceLevelDescriptor(PUB, CODE_14151328, SUBSYSTEM_A1, MIXED_SVC));

        assertEquals(List.of("v1", "v2"), ex.getVersions());
    }

    private static Service serviceEntity(String code, String version, String type) {
        return serviceEntityWithId(1L, code, version, type);
    }

    private static Service serviceEntityWithId(long id, String code, String version, String type) {
        Service s = new Service();
        ReflectionTestUtils.setField(s, "id", id);
        ReflectionTestUtils.setField(s, "serviceCode", code);
        ReflectionTestUtils.setField(s, "serviceVersion", version);
        ReflectionTestUtils.setField(s, "serviceType", type);
        LocalDateTime now = LocalDateTime.now();
        ReflectionTestUtils.setField(s, "statusInfo", new StatusInfo(now, now, now));
        ReflectionTestUtils.setField(s, "endpoints", Set.of());
        return s;
    }

    private static ServiceVersionRow versionRow(long subsystemId, String serviceCode, String version, String type) {
        return versionRowForSubsystem(subsystemId, serviceCode, version, type);
    }

    private static ServiceVersionRow versionRowForSubsystem(long subsystemId, String serviceCode, String version,
                                                            String type) {
        LocalDateTime now = LocalDateTime.now();
        return new FakeServiceVersionRow(PUB, CODE_14151328, "Nahka-Albert", SUBSYSTEM_A1, subsystemId,
                serviceCode, version, type, now, now, now);
    }

    private static ServiceAggregateRow aggregateRow(long subsystemId, String serviceCode) {
        return new ServiceAggregateRow() {
            @Override
            public String getMemberClass() {
                return PUB;
            }

            @Override
            public String getMemberCode() {
                return CODE_14151328;
            }

            @Override
            public String getSubsystemCode() {
                return SUBSYSTEM_A1;
            }

            @Override
            public String getServiceCode() {
                return serviceCode;
            }

            @Override
            public long getSubsystemId() {
                return subsystemId;
            }
        };
    }

    @SuppressWarnings("PMD.DataClass")
    private record FakeServiceVersionRow(String memberClass, String memberCode, String memberName,
                                  String subsystemCode, long subsystemId, String serviceCode, String serviceVersion,
                                  String serviceType, LocalDateTime created, LocalDateTime changed,
                                  LocalDateTime fetched) implements ServiceVersionRow {

        @Override
        public String getMemberClass() {
            return memberClass;
        }

        @Override
        public String getMemberCode() {
            return memberCode;
        }

        @Override
        public String getMemberName() {
            return memberName;
        }

        @Override
        public String getSubsystemCode() {
            return subsystemCode;
        }

        @Override
        public long getSubsystemId() {
            return subsystemId;
        }

        @Override
        public String getServiceCode() {
            return serviceCode;
        }

        @Override
        public String getServiceVersion() {
            return serviceVersion;
        }

        @Override
        public String getServiceType() {
            return serviceType;
        }

        @Override
        public LocalDateTime getCreated() {
            return created;
        }

        @Override
        public LocalDateTime getChanged() {
            return changed;
        }

        @Override
        public LocalDateTime getFetched() {
            return fetched;
        }
    }
}
