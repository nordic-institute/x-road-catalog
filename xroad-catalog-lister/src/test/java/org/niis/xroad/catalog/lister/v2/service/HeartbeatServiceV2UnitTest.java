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
import org.niis.xroad.catalog.persistence.entity.CollectionRun;
import org.niis.xroad.catalog.persistence.repository.CollectionRunRepository;
import org.niis.xroad.catalog.persistence.repository.ErrorLogRepositoryV2;
import org.springframework.dao.DataAccessResourceFailureException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HeartbeatServiceV2UnitTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-04-10T13:30:00Z"), ZoneOffset.UTC);

    @Mock private CollectionRunRepository collectionRunRepository;
    @Mock private ErrorLogRepositoryV2 errorLogRepository;

    private HeartbeatServiceV2 service;

    @BeforeEach
    void setUp() {
        service = new HeartbeatServiceV2("Test Lister", "9.9.9", collectionRunRepository, errorLogRepository,
                FIXED_CLOCK);
    }

    @Test
    void heartbeatServesTimestampsFromLatestFinishedRun() {
        CollectionRun run = new CollectionRun();
        run.setStarted(LocalDateTime.of(2025, 6, 1, 1, 0));
        run.setFinished(LocalDateTime.of(2025, 6, 1, 2, 0));
        run.setSuccess(Boolean.TRUE);
        run.setMembersLastFetched(LocalDateTime.of(2025, 6, 1, 1, 10));
        run.setWsdlsLastFetched(LocalDateTime.of(2025, 6, 1, 1, 40));
        when(collectionRunRepository.findFirstByFinishedIsNotNullOrderByFinishedDesc())
                .thenReturn(Optional.of(run));
        when(collectionRunRepository.checkConnection()).thenReturn(1);
        when(errorLogRepository.countInRange(eq(LocalDateTime.of(2025, 6, 1, 1, 0)), any()))
                .thenReturn(3L);

        HeartbeatV2Dto dto = service.heartbeat();

        assertEquals(LocalDateTime.of(2025, 6, 1, 1, 10), dto.getLastCollectionData().getMembersLastFetched());
        assertEquals(LocalDateTime.of(2025, 6, 1, 1, 40), dto.getLastCollectionData().getWsdlsLastFetched());
        assertEquals(3L, dto.getLastRunErrors());
        assertEquals(Boolean.TRUE, dto.getDbWorking());
    }

    @Test
    void heartbeatWithNoFinishedRunReportsNullsAndZeroErrors() {
        when(collectionRunRepository.findFirstByFinishedIsNotNullOrderByFinishedDesc())
                .thenReturn(Optional.empty());
        when(collectionRunRepository.checkConnection()).thenReturn(1);

        HeartbeatV2Dto dto = service.heartbeat();

        assertNull(dto.getLastCollectionData().getMembersLastFetched());
        assertEquals(0L, dto.getLastRunErrors());
        verifyNoInteractions(errorLogRepository);
    }

    @Test
    void databaseFailureReportsDbNotWorking() {
        when(collectionRunRepository.findFirstByFinishedIsNotNullOrderByFinishedDesc())
                .thenReturn(Optional.empty());
        when(collectionRunRepository.checkConnection()).thenThrow(new RuntimeException("down"));

        assertEquals(Boolean.FALSE, service.heartbeat().getDbWorking());
    }

    @Test
    void collectionRunLookupFailureDegradesToNullRatherThanThrowing() {
        when(collectionRunRepository.findFirstByFinishedIsNotNullOrderByFinishedDesc())
                .thenThrow(new DataAccessResourceFailureException("boom"));
        when(collectionRunRepository.checkConnection()).thenReturn(1);

        HeartbeatV2Dto dto = service.heartbeat();

        assertNull(dto.getLastCollectionData().getMembersLastFetched());
        assertEquals(0L, dto.getLastRunErrors());
        verifyNoInteractions(errorLogRepository);
    }

    @Test
    void lastRunErrorsIsZeroWhenErrorLogQueryThrows() {
        CollectionRun run = new CollectionRun();
        run.setStarted(LocalDateTime.of(2025, 6, 1, 1, 0));
        run.setFinished(LocalDateTime.of(2025, 6, 1, 2, 0));
        when(collectionRunRepository.findFirstByFinishedIsNotNullOrderByFinishedDesc())
                .thenReturn(Optional.of(run));
        when(collectionRunRepository.checkConnection()).thenReturn(1);
        when(errorLogRepository.countInRange(any(), any()))
                .thenThrow(new DataAccessResourceFailureException("connection refused"));

        HeartbeatV2Dto dto = service.heartbeat();

        assertEquals(0L, dto.getLastRunErrors());
    }

    @Test
    void dbWorkingIsFalseWhenCheckConnectionReturnsUnexpectedValue() {
        when(collectionRunRepository.findFirstByFinishedIsNotNullOrderByFinishedDesc())
                .thenReturn(Optional.empty());
        when(collectionRunRepository.checkConnection()).thenReturn(0);

        HeartbeatV2Dto dto = service.heartbeat();

        assertEquals(Boolean.FALSE, dto.getDbWorking());
    }

    @Test
    void heartbeatPopulatesAllSixLastFetchedFields() {
        CollectionRun run = new CollectionRun();
        run.setStarted(LocalDateTime.of(2026, 4, 10, 0, 0));
        run.setFinished(LocalDateTime.of(2026, 4, 10, 0, 30));
        LocalDateTime t1 = LocalDateTime.of(2026, 4, 10, 1, 0);
        LocalDateTime t2 = LocalDateTime.of(2026, 4, 10, 2, 0);
        LocalDateTime t3 = LocalDateTime.of(2026, 4, 10, 3, 0);
        LocalDateTime t4 = LocalDateTime.of(2026, 4, 10, 4, 0);
        LocalDateTime t5 = LocalDateTime.of(2026, 4, 10, 5, 0);
        LocalDateTime t6 = LocalDateTime.of(2026, 4, 10, 6, 0);
        run.setMembersLastFetched(t1);
        run.setSubsystemsLastFetched(t2);
        run.setServicesLastFetched(t3);
        run.setWsdlsLastFetched(t4);
        run.setOpenapisLastFetched(t5);
        run.setRestsLastFetched(t6);
        when(collectionRunRepository.findFirstByFinishedIsNotNullOrderByFinishedDesc())
                .thenReturn(Optional.of(run));
        when(collectionRunRepository.checkConnection()).thenReturn(1);
        when(errorLogRepository.countInRange(any(), any())).thenReturn(0L);

        HeartbeatV2Dto hb = service.heartbeat();

        assertEquals(t1, hb.getLastCollectionData().getMembersLastFetched());
        assertEquals(t2, hb.getLastCollectionData().getSubsystemsLastFetched());
        assertEquals(t3, hb.getLastCollectionData().getServicesLastFetched());
        assertEquals(t4, hb.getLastCollectionData().getWsdlsLastFetched());
        assertEquals(t5, hb.getLastCollectionData().getOpenapisLastFetched());
        assertEquals(t6, hb.getLastCollectionData().getRestsLastFetched());
        assertEquals("Test Lister", hb.getAppName());
        assertEquals("9.9.9", hb.getAppVersion());
        assertEquals(LocalDateTime.now(FIXED_CLOCK), hb.getSystemTime());
    }

    @Test
    void currentRunIsPopulatedFromInProgressCollectionRun() {
        CollectionRun inProgress = new CollectionRun();
        inProgress.setStarted(LocalDateTime.of(2026, 4, 10, 13, 0));
        inProgress.setPendingItems(37);
        inProgress.setProgressUpdated(LocalDateTime.of(2026, 4, 10, 13, 25));
        when(collectionRunRepository.findFirstByFinishedIsNotNullOrderByFinishedDesc())
                .thenReturn(Optional.empty());
        when(collectionRunRepository.findFirstByFinishedIsNullOrderByStartedDesc())
                .thenReturn(Optional.of(inProgress));
        when(collectionRunRepository.checkConnection()).thenReturn(1);

        HeartbeatV2Dto dto = service.heartbeat();

        assertNotNull(dto.getCurrentRun(), "currentRun must be populated while a cycle is in progress");
        assertEquals(LocalDateTime.of(2026, 4, 10, 13, 0), dto.getCurrentRun().getStarted());
        assertEquals(37, dto.getCurrentRun().getPendingItems());
        assertEquals(LocalDateTime.of(2026, 4, 10, 13, 25), dto.getCurrentRun().getProgressUpdated());
    }

    @Test
    void currentRunIsNullWhenNoCycleInProgress() {
        when(collectionRunRepository.findFirstByFinishedIsNotNullOrderByFinishedDesc())
                .thenReturn(Optional.empty());
        when(collectionRunRepository.findFirstByFinishedIsNullOrderByStartedDesc())
                .thenReturn(Optional.empty());
        when(collectionRunRepository.checkConnection()).thenReturn(1);

        HeartbeatV2Dto dto = service.heartbeat();

        assertNull(dto.getCurrentRun(), "currentRun must be null when no cycle is in progress");
    }

    @Test
    void currentRunLookupFailureDegradesToNullRatherThanThrowing() {
        when(collectionRunRepository.findFirstByFinishedIsNotNullOrderByFinishedDesc())
                .thenReturn(Optional.empty());
        when(collectionRunRepository.findFirstByFinishedIsNullOrderByStartedDesc())
                .thenThrow(new DataAccessResourceFailureException("boom"));
        when(collectionRunRepository.checkConnection()).thenReturn(1);

        HeartbeatV2Dto dto = service.heartbeat();

        assertNull(dto.getCurrentRun(), "a failed currentRun lookup must degrade to null, not throw");
    }
}
