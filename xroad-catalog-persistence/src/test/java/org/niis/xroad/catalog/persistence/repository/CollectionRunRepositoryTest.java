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
package org.niis.xroad.catalog.persistence.repository;

import org.junit.jupiter.api.Test;
import org.niis.xroad.catalog.persistence.entity.CollectionRun;
import org.niis.xroad.catalog.persistence.testsupport.PostgresTestBase;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Sql(scripts = "classpath:pg/v2-fixture.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class CollectionRunRepositoryTest extends PostgresTestBase {

    @Autowired
    private CollectionRunRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void latestFinishedRunWinsOverNewerUnfinishedRun() {
        CollectionRun finished = new CollectionRun();
        finished.setStarted(LocalDateTime.of(2025, 6, 1, 1, 0));
        finished.setFinished(LocalDateTime.of(2025, 6, 1, 2, 0));
        finished.setSuccess(Boolean.TRUE);
        repository.save(finished);

        CollectionRun inProgress = new CollectionRun();
        inProgress.setStarted(LocalDateTime.of(2025, 6, 2, 1, 0));
        repository.save(inProgress);

        CollectionRun latest = repository.findFirstByFinishedIsNotNullOrderByFinishedDesc().orElseThrow();
        assertEquals(LocalDateTime.of(2025, 6, 1, 2, 0), latest.getFinished());
        assertEquals(Boolean.TRUE, latest.getSuccess());
    }

    @Test
    void findFirstByFinishedIsNullOrderByStartedDescReturnsTheUnfinishedRun() {
        CollectionRun finished = new CollectionRun();
        finished.setStarted(LocalDateTime.of(2025, 6, 1, 1, 0));
        finished.setFinished(LocalDateTime.of(2025, 6, 1, 2, 0));
        finished.setSuccess(Boolean.TRUE);
        repository.save(finished);

        CollectionRun inProgress = new CollectionRun();
        inProgress.setStarted(LocalDateTime.of(2025, 6, 2, 1, 0));
        inProgress.setPendingItems(42);
        inProgress.setProgressUpdated(LocalDateTime.of(2025, 6, 2, 1, 30));
        repository.save(inProgress);

        CollectionRun found = repository.findFirstByFinishedIsNullOrderByStartedDesc().orElseThrow();
        assertNull(found.getFinished());
        assertEquals(LocalDateTime.of(2025, 6, 2, 1, 0), found.getStarted());
        assertEquals(42, found.getPendingItems());
        assertEquals(LocalDateTime.of(2025, 6, 2, 1, 30), found.getProgressUpdated());
    }

    @Test
    void latestFetchedSnapshotsReadTheFixtureMaxima() {
        LocalDateTime expected = LocalDateTime.of(2025, 6, 1, 10, 0);
        assertEquals(expected, repository.findLatestMemberFetched());
        assertEquals(expected, repository.findLatestSubsystemFetched());
        assertEquals(expected, repository.findLatestServiceFetched());
        assertEquals(expected, repository.findLatestWsdlFetched());
        assertEquals(expected, repository.findLatestOpenApiFetched());
        assertEquals(expected, repository.findLatestRestFetched());
        assertTrue(Integer.valueOf(1).equals(repository.checkConnection()));
    }

    @Test
    void latestFetchedSnapshotReturnsNullWhenTableIsEmpty() {
        jdbcTemplate.update("DELETE FROM rest");

        assertNull(repository.findLatestRestFetched());
    }
}
