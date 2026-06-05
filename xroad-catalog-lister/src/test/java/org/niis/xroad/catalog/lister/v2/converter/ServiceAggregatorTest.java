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
import org.niis.xroad.catalog.lister.v2.dto.ServiceDto;
import org.niis.xroad.catalog.persistence.entity.Member;
import org.niis.xroad.catalog.persistence.entity.Service;
import org.niis.xroad.catalog.persistence.entity.StatusInfo;
import org.niis.xroad.catalog.persistence.entity.Subsystem;
import org.niis.xroad.catalog.persistence.entity.Wsdl;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ServiceAggregatorTest {

    private final ServiceVersionConverter versionConverter = buildConverter();
    private final ServiceAggregator aggregator = new ServiceAggregator(versionConverter);

    private static ServiceVersionConverter buildConverter() {
        ServiceVersionConverter c = new ServiceVersionConverter();
        ReflectionTestUtils.setField(c, "classifier", new ServiceClassifier());
        return c;
    }

    @Test
    void testAggregateMultipleVersionsWithDifferentTypes() {
        Member m = member("PUB", "14151328", "Nahka-Albert");
        Subsystem s = subsystem(m, "sub1");
        Service v1 = service(s, "mixedSvc", "v1");
        v1.setWsdl(wsdl());
        Service v2 = service(s, "mixedSvc", null);
        // rests stay empty — toDto treats missing-wsdl + missing-openapi as REST
        Set<Service> versions = new HashSet<>();
        versions.add(v1);
        versions.add(v2);

        ServiceDto dto = aggregator.aggregate(versions, false);
        assertEquals("mixedSvc", dto.getServiceCode());
        assertEquals(2, dto.getVersionCount());
        assertEquals(2, dto.getVersions().size());
        // serviceTypes should contain SOAP and REST
        assertTrue(dto.getServiceTypes().contains("SOAP"));
        assertTrue(dto.getServiceTypes().contains("REST"));
        // versions sorted with null last
        assertEquals(Arrays.asList("v1", null),
                dto.getVersions().stream().map(v -> v.getServiceVersion()).toList());
    }

    @Test
    void testAggregateVersionsFilteredByActiveOnly() {
        Member m = member("PUB", "14151328", "Nahka-Albert");
        Subsystem s = subsystem(m, "sub1");
        Service active = service(s, "svc", "v1");
        Service removed = service(s, "svc", "v2");
        removed.getStatusInfo().setRemoved(LocalDateTime.now());
        Set<Service> versions = new HashSet<>();
        versions.add(active);
        versions.add(removed);

        ServiceDto activeOnly = aggregator.aggregate(versions, false);
        assertEquals(1, activeOnly.getVersionCount());

        ServiceDto all = aggregator.aggregate(versions, true);
        assertEquals(2, all.getVersionCount());
    }

    @Test
    void testAggregateReturnsNullWhenAllFilteredOut() {
        Member m = member("PUB", "14151328", "Nahka-Albert");
        Subsystem s = subsystem(m, "sub1");
        Service removed = service(s, "svc", "v1");
        removed.getStatusInfo().setRemoved(LocalDateTime.now());
        Set<Service> versions = new HashSet<>();
        versions.add(removed);

        ServiceDto dto = aggregator.aggregate(versions, false);
        assertNull(dto, "aggregator must return null when the filter removes every row");
    }

    @Test
    void testPopulatesMemberMetadataFromFirstRow() {
        Member m = member("PUB", "14151328", "Nahka-Albert");
        Subsystem s = subsystem(m, "sub1");
        Service svc = service(s, "svc", "v1");
        Set<Service> versions = new HashSet<>();
        versions.add(svc);

        ServiceDto dto = aggregator.aggregate(versions, false);
        assertEquals("PUB", dto.getMemberClass());
        assertEquals("14151328", dto.getMemberCode());
        assertEquals("Nahka-Albert", dto.getMemberName());
        assertEquals("sub1", dto.getSubsystemCode());
    }

    private Member member(String mc, String code, String name) {
        Member m = new Member();
        m.setXRoadInstance("dev-cs");
        m.setMemberClass(mc);
        m.setMemberCode(code);
        m.setName(name);
        LocalDateTime now = LocalDateTime.now();
        m.setStatusInfo(new StatusInfo(now, now, now, null));
        return m;
    }

    private Subsystem subsystem(Member parent, String code) {
        Subsystem s = new Subsystem();
        s.setSubsystemCode(code);
        s.setMember(parent);
        LocalDateTime now = LocalDateTime.now();
        s.setStatusInfo(new StatusInfo(now, now, now, null));
        return s;
    }

    private Service service(Subsystem parent, String code, String version) {
        Service s = new Service();
        s.setSubsystem(parent);
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

    private Wsdl wsdl() {
        Wsdl w = new Wsdl();
        LocalDateTime now = LocalDateTime.now();
        w.setStatusInfo(new StatusInfo(now, now, now, null));
        return w;
    }
}
