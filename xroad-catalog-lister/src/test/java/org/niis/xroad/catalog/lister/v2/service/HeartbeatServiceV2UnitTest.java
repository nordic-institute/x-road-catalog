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
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.catalog.lister.service.CatalogService;
import org.niis.xroad.catalog.lister.v2.dto.HeartbeatV2Dto;
import org.niis.xroad.catalog.persistence.entity.ErrorLog;
import org.niis.xroad.catalog.persistence.repository.ErrorLogRepositoryV2;
import org.niis.xroad.catalog.persistence.repository.MemberRepository;
import org.niis.xroad.catalog.persistence.repository.OpenApiRepository;
import org.niis.xroad.catalog.persistence.repository.RestRepository;
import org.niis.xroad.catalog.persistence.repository.ServiceRepository;
import org.niis.xroad.catalog.persistence.repository.SubsystemRepository;
import org.niis.xroad.catalog.persistence.repository.WsdlRepository;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HeartbeatServiceV2UnitTest {

    private static final LocalDateTime EARLIEST = LocalDateTime.of(2026, 4, 10, 8, 0);
    private static final LocalDateTime MID = LocalDateTime.of(2026, 4, 10, 10, 0);
    private static final LocalDateTime LATEST = LocalDateTime.of(2026, 4, 10, 12, 0);

    @Mock private CatalogService catalogService;
    @Mock private MemberRepository memberRepository;
    @Mock private SubsystemRepository subsystemRepository;
    @Mock private ServiceRepository serviceRepository;
    @Mock private WsdlRepository wsdlRepository;
    @Mock private OpenApiRepository openApiRepository;
    @Mock private RestRepository restRepository;
    @Mock private ErrorLogRepositoryV2 errorLogRepository;

    @InjectMocks private HeartbeatServiceV2 service;

    @BeforeEach
    void wireValueFields() {
        ReflectionTestUtils.setField(service, "appName", "Test Lister");
        ReflectionTestUtils.setField(service, "appVersion", "9.9.9");
    }

    private void allFetchesReturn(LocalDateTime... values) {
        // values[0..5] -> members, subsystems, services, wsdls, openapis, rests in that order.
        when(memberRepository.findLatestFetched()).thenReturn(values[0]);
        when(subsystemRepository.findLatestFetched()).thenReturn(values[1]);
        when(serviceRepository.findLatestFetched()).thenReturn(values[2]);
        when(wsdlRepository.findLatestFetched()).thenReturn(values[3]);
        when(openApiRepository.findLatestFetched()).thenReturn(values[4]);
        when(restRepository.findLatestFetched()).thenReturn(values[5]);
    }

    @Test
    void lastRunErrorsCountsErrorsSinceEarliestLastFetched() {
        // Spec §5.1: anchor is the earliest of the six lastFetched timestamps.
        allFetchesReturn(LATEST, MID, EARLIEST, LATEST, MID, LATEST);
        when(catalogService.checkDatabaseConnection()).thenReturn(Boolean.TRUE);
        Page<ErrorLog> page = new PageImpl<>(List.of(), PageRequest.of(0, 1), 7L);
        when(errorLogRepository.findAnyInRange(eq(EARLIEST), any(LocalDateTime.class), any(Pageable.class)))
                .thenReturn(page);

        HeartbeatV2Dto hb = service.heartbeat();

        ArgumentCaptor<LocalDateTime> sinceCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(errorLogRepository).findAnyInRange(sinceCaptor.capture(), any(LocalDateTime.class),
                any(Pageable.class));
        assertThat(sinceCaptor.getValue()).isEqualTo(EARLIEST);
        assertThat(hb.getLastRunErrors()).isEqualTo(7L);
    }

    @Test
    void lastRunErrorsIsZeroWhenAnyLastFetchedIsNull() {
        // Spec §5.1: if any *LastFetched is null, the count is 0 and the error log is not queried.
        allFetchesReturn(LATEST, MID, EARLIEST, LATEST, null, LATEST);
        when(catalogService.checkDatabaseConnection()).thenReturn(Boolean.TRUE);

        HeartbeatV2Dto hb = service.heartbeat();

        assertThat(hb.getLastRunErrors()).isZero();
        verify(errorLogRepository, never()).findAnyInRange(any(), any(), any());
    }

    @Test
    void lastRunErrorsIsZeroWhenErrorLogQueryThrows() {
        // Issue 2: a transient DB failure on the error-log query must not surface as 500.
        allFetchesReturn(LATEST, MID, EARLIEST, LATEST, MID, LATEST);
        when(catalogService.checkDatabaseConnection()).thenReturn(Boolean.TRUE);
        when(errorLogRepository.findAnyInRange(any(), any(), any()))
                .thenThrow(new DataAccessResourceFailureException("connection refused"));

        HeartbeatV2Dto hb = service.heartbeat();

        assertThat(hb.getLastRunErrors()).isZero();
    }

    @Test
    void dbWorkingIsFalseWhenCheckConnectionThrows() {
        // Issue 2: a JpaSystemException-equivalent during checkDatabaseConnection must degrade
        // to dbWorking=false, not bubble to a 500 from the controller.
        allFetchesReturn(LATEST, MID, EARLIEST, LATEST, MID, LATEST);
        when(catalogService.checkDatabaseConnection())
                .thenThrow(new DataAccessResourceFailureException("DB unavailable"));
        Page<ErrorLog> page = new PageImpl<>(List.of(), PageRequest.of(0, 1), 0L);
        when(errorLogRepository.findAnyInRange(any(), any(), any())).thenReturn(page);

        HeartbeatV2Dto hb = service.heartbeat();

        assertThat(hb.getDbWorking()).isFalse();
        assertThat(hb.getAppWorking()).isTrue();
    }

    @Test
    void heartbeatPopulatesAllSixLastFetchedFields() {
        // Sanity: every field on LastCollectionData is wired to the right repository.
        LocalDateTime t1 = LocalDateTime.of(2026, 4, 10, 1, 0);
        LocalDateTime t2 = LocalDateTime.of(2026, 4, 10, 2, 0);
        LocalDateTime t3 = LocalDateTime.of(2026, 4, 10, 3, 0);
        LocalDateTime t4 = LocalDateTime.of(2026, 4, 10, 4, 0);
        LocalDateTime t5 = LocalDateTime.of(2026, 4, 10, 5, 0);
        LocalDateTime t6 = LocalDateTime.of(2026, 4, 10, 6, 0);
        allFetchesReturn(t1, t2, t3, t4, t5, t6);
        when(catalogService.checkDatabaseConnection()).thenReturn(Boolean.TRUE);
        Page<ErrorLog> page = new PageImpl<>(List.of(), PageRequest.of(0, 1), 0L);
        when(errorLogRepository.findAnyInRange(any(), any(), any())).thenReturn(page);

        HeartbeatV2Dto hb = service.heartbeat();

        assertThat(hb.getLastCollectionData().getMembersLastFetched()).isEqualTo(t1);
        assertThat(hb.getLastCollectionData().getSubsystemsLastFetched()).isEqualTo(t2);
        assertThat(hb.getLastCollectionData().getServicesLastFetched()).isEqualTo(t3);
        assertThat(hb.getLastCollectionData().getWsdlsLastFetched()).isEqualTo(t4);
        assertThat(hb.getLastCollectionData().getOpenapisLastFetched()).isEqualTo(t5);
        assertThat(hb.getLastCollectionData().getRestsLastFetched()).isEqualTo(t6);
        assertThat(hb.getAppName()).isEqualTo("Test Lister");
        assertThat(hb.getAppVersion()).isEqualTo("9.9.9");
    }
}
