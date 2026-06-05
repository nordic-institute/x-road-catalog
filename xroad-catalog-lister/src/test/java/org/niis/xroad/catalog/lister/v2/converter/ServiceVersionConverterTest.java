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
package org.niis.xroad.catalog.lister.v2.converter;

import org.junit.jupiter.api.Test;
import org.niis.xroad.catalog.lister.v2.dto.ServiceVersionDto;
import org.niis.xroad.catalog.lister.v2.dto.ServiceVersionSummaryDto;
import org.niis.xroad.catalog.persistence.entity.Endpoint;
import org.niis.xroad.catalog.persistence.entity.OpenApi;
import org.niis.xroad.catalog.persistence.entity.Rest;
import org.niis.xroad.catalog.persistence.entity.Service;
import org.niis.xroad.catalog.persistence.entity.StatusInfo;
import org.niis.xroad.catalog.persistence.entity.Wsdl;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ServiceVersionConverterTest {

    private static final String SERVICE_CODE = "getTaxInfo";

    private final ServiceVersionConverter converter = buildConverter();

    private static ServiceVersionConverter buildConverter() {
        ServiceVersionConverter c = new ServiceVersionConverter();
        // Inject the classifier directly; @Autowired field is resolved by Spring in production but
        // tests wire it explicitly via reflection to keep the converter pure-unit-test friendly.
        ReflectionTestUtils.setField(c, "classifier", new ServiceClassifier());
        return c;
    }

    @Test
    void testSoapServiceType() {
        Service svc = buildService(SERVICE_CODE, "v1");
        svc.setWsdl(buildWsdl());
        ServiceVersionDto dto = converter.toDto(svc, false);
        assertEquals("SOAP", dto.getServiceType());
        assertTrue(dto.isHasDescriptor());
    }

    @Test
    void testOpenApiServiceType() {
        Service svc = buildService(SERVICE_CODE, "v1");
        svc.setOpenApi(buildOpenApi());
        ServiceVersionDto dto = converter.toDto(svc, false);
        assertEquals("OPENAPI", dto.getServiceType());
    }

    @Test
    void testRestServiceTypeAndHasDescriptorFalseForRestEntity() {
        // A service with only a Rest entity is classified REST — but hasDescriptor must be false
        // because V2's /descriptor endpoint only serves WSDL/OpenAPI raw documents.
        Service svc = buildService(SERVICE_CODE, null);
        svc.setRest(buildRest());
        ServiceVersionDto dto = converter.toDto(svc, false);
        assertEquals("REST", dto.getServiceType());
        assertFalse(dto.isHasDescriptor(),
                "Rest entity does not count as a descriptor for V2 /descriptor endpoint");
        assertNull(dto.getServiceVersion(), "null version passes through");
    }

    @Test
    void testNoDescriptor() {
        Service svc = buildService(SERVICE_CODE, "v1");
        ServiceVersionDto dto = converter.toDto(svc, false);
        assertFalse(dto.isHasDescriptor());
        assertEquals("REST", dto.getServiceType(), "services with no descriptor default to REST per V1 semantics");
    }

    @Test
    void testHasDescriptorTrueForSoap() {
        Service svc = buildService(SERVICE_CODE, "v1");
        svc.setWsdl(buildWsdl());
        ServiceVersionDto dto = converter.toDto(svc, false);
        assertTrue(dto.isHasDescriptor());
    }

    @Test
    void testHasDescriptorTrueForOpenApi() {
        Service svc = buildService(SERVICE_CODE, "v1");
        svc.setOpenApi(buildOpenApi());
        ServiceVersionDto dto = converter.toDto(svc, false);
        assertTrue(dto.isHasDescriptor());
    }

    @Test
    void testEndpointsActiveOnlyByDefault() {
        Service svc = buildService(SERVICE_CODE, "v1");
        svc.getAllEndpoints().add(buildEndpoint("GET", "/active", false));
        svc.getAllEndpoints().add(buildEndpoint("GET", "/removed", true));
        ServiceVersionDto dto = converter.toDto(svc, false);
        assertEquals(1, dto.getEndpoints().size());
        assertEquals("/active", dto.getEndpoints().get(0).getPath());
    }

    @Test
    void testEndpointsIncludeRemoved() {
        Service svc = buildService(SERVICE_CODE, "v1");
        svc.getAllEndpoints().add(buildEndpoint("GET", "/active", false));
        svc.getAllEndpoints().add(buildEndpoint("GET", "/removed", true));
        ServiceVersionDto dto = converter.toDto(svc, true);
        assertEquals(2, dto.getEndpoints().size());
    }

    @Test
    void testToSummarySoapServiceType() {
        Service svc = buildService(SERVICE_CODE, "v1");
        svc.setWsdl(buildWsdl());
        ServiceVersionSummaryDto summary = converter.toSummary(svc);
        assertEquals("v1", summary.getServiceVersion());
        assertEquals("SOAP", summary.getServiceType());
    }

    @Test
    void testToSummaryOpenApiServiceType() {
        Service svc = buildService(SERVICE_CODE, "v1");
        svc.setOpenApi(buildOpenApi());
        ServiceVersionSummaryDto summary = converter.toSummary(svc);
        assertEquals("OPENAPI", summary.getServiceType());
    }

    @Test
    void testToSummaryRestServiceType() {
        Service svc = buildService(SERVICE_CODE, null);
        svc.setRest(buildRest());
        ServiceVersionSummaryDto summary = converter.toSummary(svc);
        assertEquals("REST", summary.getServiceType());
        assertNull(summary.getServiceVersion(), "null version passes through");
    }

    @Test
    void testToSummaryDefaultsToRestWithoutDescriptor() {
        Service svc = buildService(SERVICE_CODE, "v1");
        ServiceVersionSummaryDto summary = converter.toSummary(svc);
        assertEquals("REST", summary.getServiceType(), "services with no descriptor default to REST per V1 semantics");
    }

    @Test
    void testToSummaryPopulatesTimestamps() {
        Service svc = buildService(SERVICE_CODE, "v1");
        LocalDateTime removedAt = LocalDateTime.now();
        svc.getStatusInfo().setRemoved(removedAt);
        ServiceVersionSummaryDto summary = converter.toSummary(svc);
        assertNotNull(summary.getCreated());
        assertNotNull(summary.getChanged());
        assertNotNull(summary.getFetched());
        assertEquals(removedAt, summary.getRemoved());
    }

    @Test
    void testToSummaryRemovedNullWhenActive() {
        Service svc = buildService(SERVICE_CODE, "v1");
        ServiceVersionSummaryDto summary = converter.toSummary(svc);
        assertNull(summary.getRemoved());
    }

    private Service buildService(String code, String version) {
        Service s = new Service();
        s.setServiceCode(code);
        s.setServiceVersion(version);
        LocalDateTime now = LocalDateTime.now();
        s.setStatusInfo(new StatusInfo(now, now, now, null));
        s.setEndpoints(new HashSet<>());
        s.setWsdls(new HashSet<>());
        s.setOpenApis(new HashSet<>());
        s.setRests(new HashSet<>());
        return s;
    }

    private Wsdl buildWsdl() {
        Wsdl w = new Wsdl();
        LocalDateTime now = LocalDateTime.now();
        w.setStatusInfo(new StatusInfo(now, now, now, null));
        return w;
    }

    private OpenApi buildOpenApi() {
        OpenApi o = new OpenApi();
        LocalDateTime now = LocalDateTime.now();
        o.setStatusInfo(new StatusInfo(now, now, now, null));
        return o;
    }

    private Rest buildRest() {
        Rest r = new Rest();
        LocalDateTime now = LocalDateTime.now();
        r.setStatusInfo(new StatusInfo(now, now, now, null));
        return r;
    }

    private Endpoint buildEndpoint(String method, String path, boolean removed) {
        Endpoint e = new Endpoint();
        e.setMethod(method);
        e.setPath(path);
        LocalDateTime now = LocalDateTime.now();
        e.setStatusInfo(new StatusInfo(now, now, now, removed ? now : null));
        return e;
    }
}
