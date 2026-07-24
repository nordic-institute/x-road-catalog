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
package org.niis.xroad.catalog.lister.v2.dto;

import org.junit.jupiter.api.Test;
import org.niis.xroad.catalog.persistence.entity.StatusInfo;
import org.niis.xroad.catalog.persistence.v2entity.EndpointV2;
import org.niis.xroad.catalog.persistence.v2entity.ServiceV2;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServiceVersionDtoTest {

    private static final String SERVICE_CODE = "getTaxInfo";

    @Test
    void testSoapHasDescriptorTrue() {
        ServiceV2 svc = buildService(SERVICE_CODE, "v1", "SOAP");
        ServiceVersionDto dto = ServiceVersionDto.from(svc);
        assertEquals("SOAP", dto.getServiceType());
        assertTrue(dto.isHasDescriptor());
    }

    @Test
    void testOpenApiHasDescriptorTrue() {
        ServiceV2 svc = buildService(SERVICE_CODE, "v1", "OPENAPI");
        ServiceVersionDto dto = ServiceVersionDto.from(svc);
        assertEquals("OPENAPI", dto.getServiceType());
        assertTrue(dto.isHasDescriptor());
    }

    @Test
    void testRestHasDescriptorFalse() {
        ServiceV2 svc = buildService(SERVICE_CODE, null, "REST");
        ServiceVersionDto dto = ServiceVersionDto.from(svc);
        assertEquals("REST", dto.getServiceType());
        assertFalse(dto.isHasDescriptor(), "REST is the descriptor-less service type");
        assertNull(dto.getServiceVersion(), "null version passes through");
    }

    @Test
    void testUnknownHasDescriptorFalse() {
        ServiceV2 svc = buildService(SERVICE_CODE, null, "UNKNOWN");
        ServiceVersionDto dto = ServiceVersionDto.from(svc);
        assertEquals("UNKNOWN", dto.getServiceType(), "the unclassified state surfaces as-is, not as a REST guess");
        assertFalse(dto.isHasDescriptor(), "a not-yet-classified service must not claim a descriptor");
    }

    @Test
    void testEndpointsComeFromActiveEndpointsHelper() {
        ServiceV2 svc = buildService(SERVICE_CODE, "v1", "REST");
        addEndpoint(svc, "GET", "/active", false);
        addEndpoint(svc, "GET", "/removed", true);
        ServiceVersionDto dto = ServiceVersionDto.from(svc);
        assertEquals(1, dto.getEndpoints().size(), "getActiveEndpoints() already excludes removed rows");
        assertEquals("/active", dto.getEndpoints().get(0).getPath());
    }

    private ServiceV2 buildService(String code, String version, String serviceType) {
        ServiceV2 s = new ServiceV2();
        ReflectionTestUtils.setField(s, "serviceCode", code);
        ReflectionTestUtils.setField(s, "serviceVersion", version);
        ReflectionTestUtils.setField(s, "serviceType", serviceType);
        LocalDateTime now = LocalDateTime.now();
        ReflectionTestUtils.setField(s, "statusInfo", new StatusInfo(now, now, now, null));
        ReflectionTestUtils.setField(s, "endpoints", new HashSet<EndpointV2>());
        return s;
    }

    private void addEndpoint(ServiceV2 service, String method, String path, boolean removed) {
        EndpointV2 e = new EndpointV2();
        ReflectionTestUtils.setField(e, "service", service);
        ReflectionTestUtils.setField(e, "method", method);
        ReflectionTestUtils.setField(e, "path", path);
        LocalDateTime now = LocalDateTime.now();
        ReflectionTestUtils.setField(e, "statusInfo", new StatusInfo(now, now, now, removed ? now : null));
        Set<EndpointV2> endpoints = service.getEndpoints();
        endpoints.add(e);
    }
}
