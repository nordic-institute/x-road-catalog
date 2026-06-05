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
package org.niis.xroad.catalog.persistence.entity;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the V2 active-only descriptor accessors on {@link Service}. These assertions
 * do not need Spring — they exercise the in-memory filtering logic directly against the HashSet
 * backing stores.
 */
public class ServiceTest {

    @Test
    void testGetActiveWsdlPicksLowestIdWhenMultipleActive() {
        Service service = buildService();
        Wsdl older = buildWsdl(100L, false);
        Wsdl newer = buildWsdl(200L, false);
        Set<Wsdl> wsdls = new HashSet<>();
        wsdls.add(newer);
        wsdls.add(older);
        service.setWsdls(wsdls);

        Wsdl result = service.getActiveWsdl();
        assertNotNull(result);
        assertEquals(100L, result.getId(),
                "getActiveWsdl must deterministically return the lowest-id active row");
        assertTrue(service.hasActiveWsdl());
    }

    @Test
    void testGetActiveWsdlSkipsRemovedRows() {
        Service service = buildService();
        Wsdl removed = buildWsdl(50L, true);
        Wsdl active = buildWsdl(300L, false);
        Set<Wsdl> wsdls = new HashSet<>();
        wsdls.add(removed);
        wsdls.add(active);
        service.setWsdls(wsdls);

        Wsdl result = service.getActiveWsdl();
        assertNotNull(result);
        assertEquals(300L, result.getId(),
                "removed row with lower id must not be returned");
    }

    @Test
    void testGetActiveWsdlReturnsNullWhenAllRemoved() {
        Service service = buildService();
        Wsdl removed = buildWsdl(42L, true);
        Set<Wsdl> wsdls = new HashSet<>();
        wsdls.add(removed);
        service.setWsdls(wsdls);

        assertNull(service.getActiveWsdl());
        assertFalse(service.hasActiveWsdl());
    }

    @Test
    void testGetActiveOpenApiPicksLowestIdWhenMultipleActive() {
        Service service = buildService();
        OpenApi older = buildOpenApi(100L, false);
        OpenApi newer = buildOpenApi(200L, false);
        Set<OpenApi> openApis = new HashSet<>();
        openApis.add(newer);
        openApis.add(older);
        service.setOpenApis(openApis);

        OpenApi result = service.getActiveOpenApi();
        assertNotNull(result);
        assertEquals(100L, result.getId());
        assertTrue(service.hasActiveOpenApi());
    }

    @Test
    void testGetActiveRestPicksLowestIdWhenMultipleActive() {
        Service service = buildService();
        Rest older = buildRest(100L, false);
        Rest newer = buildRest(200L, false);
        Set<Rest> rests = new HashSet<>();
        rests.add(newer);
        rests.add(older);
        service.setRests(rests);

        Rest result = service.getActiveRest();
        assertNotNull(result);
        assertEquals(100L, result.getId());
        assertTrue(service.hasActiveRest());
    }

    @Test
    void testHasActiveFalseWhenNoRowsAtAll() {
        Service service = buildService();
        assertFalse(service.hasActiveWsdl());
        assertFalse(service.hasActiveOpenApi());
        assertFalse(service.hasActiveRest());
        assertNull(service.getActiveWsdl());
        assertNull(service.getActiveOpenApi());
        assertNull(service.getActiveRest());
    }

    private Service buildService() {
        Service s = new Service();
        s.setServiceCode("svc");
        s.setServiceVersion("v1");
        LocalDateTime now = LocalDateTime.now();
        s.setStatusInfo(new StatusInfo(now, now, now, null));
        s.setWsdls(new HashSet<>());
        s.setOpenApis(new HashSet<>());
        s.setRests(new HashSet<>());
        return s;
    }

    private Wsdl buildWsdl(long id, boolean removed) {
        Wsdl w = new Wsdl();
        w.setId(id);
        LocalDateTime now = LocalDateTime.now();
        w.setStatusInfo(new StatusInfo(now, now, now, removed ? now : null));
        return w;
    }

    private OpenApi buildOpenApi(long id, boolean removed) {
        OpenApi o = new OpenApi();
        o.setId(id);
        LocalDateTime now = LocalDateTime.now();
        o.setStatusInfo(new StatusInfo(now, now, now, removed ? now : null));
        return o;
    }

    private Rest buildRest(long id, boolean removed) {
        Rest r = new Rest();
        r.setId(id);
        LocalDateTime now = LocalDateTime.now();
        r.setStatusInfo(new StatusInfo(now, now, now, removed ? now : null));
        return r;
    }
}
