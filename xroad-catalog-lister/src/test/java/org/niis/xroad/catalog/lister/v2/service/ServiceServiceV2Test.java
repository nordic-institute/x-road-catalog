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

import org.junit.jupiter.api.Test;
import org.niis.xroad.catalog.lister.v2.controller.MultipleVersionsException;
import org.niis.xroad.catalog.lister.v2.dto.DescriptorPayload;
import org.niis.xroad.catalog.lister.v2.dto.ServiceDto;
import org.niis.xroad.catalog.lister.v2.dto.ServiceVersionDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@TestPropertySource(properties = {"xroad-catalog.shared-params-file=src/test/resources/shared-params-dev-cs.xml"})
@ActiveProfiles({"test", "general-testdata"})
public class ServiceServiceV2Test {

    private static final String PUB = "PUB";
    private static final String CODE_14151328 = "14151328";
    private static final String SUBSYSTEM_A1 = "subsystem_a1";
    private static final String MIXED_SVC = "mixedSvc";

    @Autowired
    private ServiceServiceV2 serviceService;

    @Test
    public void testGetMixedTypeAggregate() {
        // Phase 2 Task 8 fixture: member 14151328 / subsystem_a1 / mixedSvc has v1 (SOAP) + v2 (REST)
        ServiceDto dto = serviceService.getByNaturalKey(PUB, CODE_14151328, SUBSYSTEM_A1, MIXED_SVC, false);
        assertNotNull(dto);
        assertEquals(2, dto.getVersionCount());
        assertTrue(dto.getServiceTypes().contains("SOAP"));
        assertTrue(dto.getServiceTypes().contains("REST"));
    }

    @Test
    public void testGetForListFiltersBySoapTypeButKeepsAllVersions() {
        Page<ServiceDto> page = serviceService.getForList(null, "SOAP", false, PageRequest.of(0, 50));
        ServiceDto mixed = page.getContent().stream()
                .filter(s -> MIXED_SVC.equals(s.getServiceCode()))
                .findFirst()
                .orElseThrow();
        assertEquals(2, mixed.getVersionCount(), "SOAP filter selects service; aggregate keeps all versions");
    }

    @Test
    public void testGetVersion() {
        ServiceVersionDto v = serviceService.getVersion(PUB, CODE_14151328, SUBSYSTEM_A1, MIXED_SVC, "v1", false);
        assertNotNull(v);
        assertEquals("v1", v.getServiceVersion());
        assertEquals("SOAP", v.getServiceType());
    }

    @Test
    public void testGetVersionsReturnsSortedNullsLast() {
        // mixedSvc has v1 + v2; assert deterministic order.
        List<ServiceVersionDto> versions = serviceService.getVersions(
                PUB, CODE_14151328, SUBSYSTEM_A1, MIXED_SVC, false);
        assertEquals(2, versions.size());
        assertEquals("v1", versions.get(0).getServiceVersion());
        assertEquals("v2", versions.get(1).getServiceVersion());
    }

    @Test
    public void testGetVersionResolvesNullVersionSentinel() {
        // Fixture: service id 10 'service-with-null-version' under subsystem 8 (member id 7, code '15').
        ServiceVersionDto v = serviceService.getVersion(
                PUB, "15", "subsystem_7-1", "service-with-null-version", "null", false);
        assertNotNull(v, "'null' sentinel must resolve to the null-version service row");
        assertNull(v.getServiceVersion());
    }

    @Test
    public void testGetByNaturalKeyReturnsNullForMissingService() {
        ServiceDto dto = serviceService.getByNaturalKey(
                PUB, CODE_14151328, SUBSYSTEM_A1, "doesNotExist", false);
        assertNull(dto);
    }

    @Test
    public void testGetForSubsystemReturnsAggregatesSortedByServiceCode() {
        // subsystem_a1 has getRandom (id 2), mixedSvc (ids 20,21), svc_removed_wsdl_only (id 33).
        List<ServiceDto> services = serviceService.getForSubsystem(PUB, CODE_14151328, SUBSYSTEM_A1, false);
        assertFalse(services.isEmpty());
        List<String> codes = services.stream().map(ServiceDto::getServiceCode).toList();
        List<String> expectedSorted = new java.util.ArrayList<>(codes);
        expectedSorted.sort(java.util.Comparator.naturalOrder());
        assertEquals(expectedSorted, codes, "service codes must be sorted ascending");
        assertTrue(codes.contains("getRandom"));
        assertTrue(codes.contains(MIXED_SVC));
    }

    @Test
    public void testGetForSubsystemMixedSvcAggregatesBothVersions() {
        List<ServiceDto> services = serviceService.getForSubsystem(PUB, CODE_14151328, SUBSYSTEM_A1, false);
        ServiceDto mixed = services.stream()
                .filter(s -> MIXED_SVC.equals(s.getServiceCode()))
                .findFirst()
                .orElseThrow();
        assertEquals(2, mixed.getVersionCount(), "mixedSvc aggregate must include both v1 and v2");
    }

    @Test
    public void testGetForSubsystemExcludesRemovedServicesByDefault() {
        // subsystem 8 (member 15, subsystem_7-1) has both active and removed services.
        List<ServiceDto> activeOnly = serviceService.getForSubsystem(PUB, "15", "subsystem_7-1", false);
        for (ServiceDto dto : activeOnly) {
            assertFalse(dto.getServiceCode().startsWith("removed-service"),
                    "active-only view must not surface removed services: " + dto.getServiceCode());
        }
    }

    @Test
    public void testGetForSubsystemIncludesRemovedWhenFlagged() {
        List<ServiceDto> all = serviceService.getForSubsystem(PUB, "15", "subsystem_7-1", true);
        boolean foundRemoved = all.stream().anyMatch(dto -> dto.getServiceCode().startsWith("removed-service"));
        assertTrue(foundRemoved, "includeRemoved=true must surface removed services");
    }

    @Test
    public void testGetForSubsystemReturnsEmptyForMissingSubsystem() {
        List<ServiceDto> result = serviceService.getForSubsystem(PUB, CODE_14151328, "does-not-exist", false);
        assertTrue(result.isEmpty());
    }

    @Test
    public void testGetVersionDescriptorReturnsWsdlAsXml() {
        // Fixture: mixedSvc/v1 has a WSDL row.
        DescriptorPayload payload = serviceService.getVersionDescriptor(
                PUB, CODE_14151328, SUBSYSTEM_A1, MIXED_SVC, "v1", false);
        assertNotNull(payload);
        assertEquals(MediaType.APPLICATION_XML, payload.contentType());
        String body = new String(payload.content(), StandardCharsets.UTF_8);
        assertTrue(body.contains("wsdl-mixedSvc-v1"), "WSDL body must come from the active wsdl row");
    }

    @Test
    public void testGetVersionDescriptorReturnsOpenApiJson() {
        // Task 6 fixture: descJsonSvc/v1 has an OpenAPI row whose data is valid JSON.
        DescriptorPayload payload = serviceService.getVersionDescriptor(
                PUB, CODE_14151328, SUBSYSTEM_A1, "descJsonSvc", "v1", false);
        assertNotNull(payload);
        assertEquals(MediaType.APPLICATION_JSON, payload.contentType());
        assertTrue(new String(payload.content(), StandardCharsets.UTF_8).contains("\"openapi\""));
    }

    @Test
    public void testGetVersionDescriptorReturnsOpenApiYaml() {
        // Task 6 fixture: descYamlSvc/v1 has an OpenAPI row whose data is YAML (not parseable as JSON).
        DescriptorPayload payload = serviceService.getVersionDescriptor(
                PUB, CODE_14151328, SUBSYSTEM_A1, "descYamlSvc", "v1", false);
        assertNotNull(payload);
        assertEquals(MediaType.parseMediaType("application/yaml"), payload.contentType());
        assertTrue(new String(payload.content(), StandardCharsets.UTF_8).startsWith("openapi:"));
    }

    @Test
    public void testGetVersionDescriptorReturnsNullForRestOnlyService() {
        // Task 6 fixture: descRestOnlySvc has no wsdl/openApi rows -> 404 territory.
        DescriptorPayload payload = serviceService.getVersionDescriptor(
                PUB, CODE_14151328, SUBSYSTEM_A1, "descRestOnlySvc", "v1", false);
        assertNull(payload);
    }

    @Test
    public void testGetVersionDescriptorReturnsNullWhenOnlyRemovedWsdlExists() {
        // Task 6 regression: Task 1.5's getActiveWsdl() must filter out removed rows.
        DescriptorPayload payload = serviceService.getVersionDescriptor(
                PUB, CODE_14151328, SUBSYSTEM_A1, "descRemovedWsdlSvc", "v1", false);
        assertNull(payload, "active-only descriptor accessor must not return a removed WSDL");
    }

    @Test
    public void testGetVersionDescriptorReturnsNullForMissingVersion() {
        DescriptorPayload payload = serviceService.getVersionDescriptor(
                PUB, CODE_14151328, SUBSYSTEM_A1, MIXED_SVC, "v99", false);
        assertNull(payload);
    }

    @Test
    public void testGetServiceLevelDescriptorReturnsForSingleVersion() {
        DescriptorPayload payload = serviceService.getServiceLevelDescriptor(
                PUB, CODE_14151328, SUBSYSTEM_A1, "descJsonSvc", false);
        assertNotNull(payload);
        assertEquals(MediaType.APPLICATION_JSON, payload.contentType());
    }

    @Test
    public void testGetServiceLevelDescriptorReturnsNullForNoVersions() {
        DescriptorPayload payload = serviceService.getServiceLevelDescriptor(
                PUB, CODE_14151328, SUBSYSTEM_A1, "doesNotExist", false);
        assertNull(payload);
    }

    @Test
    public void testGetServiceLevelDescriptorReturnsNullForSingleVersionWithoutDescriptor() {
        DescriptorPayload payload = serviceService.getServiceLevelDescriptor(
                PUB, CODE_14151328, SUBSYSTEM_A1, "descRestOnlySvc", false);
        assertNull(payload);
    }

    @Test
    public void testGetServiceLevelDescriptorThrowsOnMultipleVersions() {
        MultipleVersionsException ex = assertThrows(MultipleVersionsException.class,
                () -> serviceService.getServiceLevelDescriptor(
                        PUB, CODE_14151328, SUBSYSTEM_A1, MIXED_SVC, false));
        assertEquals(List.of("v1", "v2"), ex.getVersions(),
                "MultipleVersionsException must expose versions sorted with nullsLast");
    }
}
