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
import org.niis.xroad.catalog.lister.v2.dto.HeartbeatV2Dto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.time.Duration;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@TestPropertySource(properties = {
        "xroad-catalog.shared-params-file=src/test/resources/shared-params-dev-cs.xml",
        "xroad-catalog.app-name=X-Road Catalog Lister V2",
        "xroad-catalog.app-version=2.0.0"
})
@ActiveProfiles({"test", "general-testdata"})
public class HeartbeatServiceV2Test {

    @Autowired
    private HeartbeatServiceV2 heartbeatService;

    @Test
    public void testHeartbeatHasExpectedFields() {
        HeartbeatV2Dto hb = heartbeatService.heartbeat();
        assertNotNull(hb);
        assertEquals(Boolean.TRUE, hb.getAppWorking(), "appWorking must be true");
        assertEquals(Boolean.TRUE, hb.getDbWorking(), "dbWorking must be true when DB reachable");
        assertEquals("X-Road Catalog Lister V2", hb.getAppName());
        assertEquals("2.0.0", hb.getAppVersion());
        assertNotNull(hb.getSystemTime(), "systemTime must be populated");
        assertTrue(Duration.between(hb.getSystemTime(), LocalDateTime.now()).abs().toMinutes() < 1,
                "systemTime must be approximately now");
        assertNotNull(hb.getLastCollectionData(), "lastCollectionData must be populated");
        assertNotNull(hb.getLastCollectionData().getRestsLastFetched(),
                "restsLastFetched must be wired and non-null given rest fixture rows");
    }

    @Test
    public void testLastRunErrorsCounted() {
        HeartbeatV2Dto hb = heartbeatService.heartbeat();
        assertTrue(hb.getLastRunErrors() >= 0, "lastRunErrors must be non-negative");
    }
}
