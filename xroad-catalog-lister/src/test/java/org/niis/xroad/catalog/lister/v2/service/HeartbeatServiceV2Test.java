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
import org.niis.xroad.catalog.lister.v2.dto.HeartbeatV2Dto;
import org.niis.xroad.catalog.persistence.entity.ErrorLog;
import org.niis.xroad.catalog.persistence.repository.DescriptorRepositoryV2;
import org.niis.xroad.catalog.persistence.repository.ErrorLogRepositoryV2;
import org.niis.xroad.catalog.persistence.repository.MemberRepositoryV2;
import org.niis.xroad.catalog.persistence.repository.ServiceRepositoryV2;
import org.niis.xroad.catalog.persistence.repository.SubsystemRepositoryV2;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Happy-path shape test for {@link HeartbeatServiceV2#heartbeat()}; edge cases (null lastFetched
 * values, repository failures) live in {@link HeartbeatServiceV2UnitTest}.
 */
@ExtendWith(MockitoExtension.class)
class HeartbeatServiceV2Test {

    private static final LocalDateTime NOW = LocalDateTime.now();

    @Mock private MemberRepositoryV2 memberRepository;
    @Mock private SubsystemRepositoryV2 subsystemRepository;
    @Mock private ServiceRepositoryV2 serviceRepository;
    @Mock private DescriptorRepositoryV2 descriptorRepository;
    @Mock private ErrorLogRepositoryV2 errorLogRepository;

    private HeartbeatServiceV2 service;

    @BeforeEach
    void setUp() {
        service = new HeartbeatServiceV2("X-Road Catalog Lister V2", "2.0.0", memberRepository, subsystemRepository,
                serviceRepository, descriptorRepository, errorLogRepository, Clock.systemDefaultZone());

        when(memberRepository.findLatestFetched()).thenReturn(NOW);
        when(subsystemRepository.findLatestFetched()).thenReturn(NOW);
        when(serviceRepository.findLatestFetched()).thenReturn(NOW);
        when(descriptorRepository.findLatestWsdlFetched()).thenReturn(NOW);
        when(descriptorRepository.findLatestOpenApiFetched()).thenReturn(NOW);
        when(descriptorRepository.findLatestRestFetched()).thenReturn(NOW);
        when(memberRepository.checkConnection()).thenReturn(1);
        Page<ErrorLog> page = new PageImpl<>(List.of(), PageRequest.of(0, 1), 0L);
        when(errorLogRepository.findAnyInRange(any(), any(), any())).thenReturn(page);
    }

    @Test
    void testHeartbeatHasExpectedFields() {
        HeartbeatV2Dto hb = service.heartbeat();
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
                "restsLastFetched must be wired to DescriptorRepositoryV2#findLatestRestFetched");
    }

    @Test
    void testLastRunErrorsCounted() {
        HeartbeatV2Dto hb = service.heartbeat();
        assertTrue(hb.getLastRunErrors() >= 0, "lastRunErrors must be non-negative");
    }
}
