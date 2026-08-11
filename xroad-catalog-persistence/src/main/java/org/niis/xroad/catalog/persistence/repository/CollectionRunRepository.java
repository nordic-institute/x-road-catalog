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

import org.niis.xroad.catalog.persistence.entity.CollectionRun;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

/**
 * One row per collector cycle: written at cycle start and finalized, after all fetch work and the
 * recompute, with the per-type MAX(fetched) snapshot, so the lister heartbeat need not scan the
 * big tables per poll. The MAX queries live here because the collector's repository scan covers
 * only this package, not {@code ...persistence.v2.repository}.
 */
public interface CollectionRunRepository extends CrudRepository<CollectionRun, Long> {

    Optional<CollectionRun> findFirstByFinishedIsNotNullOrderByFinishedDesc();

    Optional<CollectionRun> findFirstByFinishedIsNullOrderByStartedDesc();

    @Query(value = "SELECT MAX(fetched) FROM member", nativeQuery = true)
    Instant findLatestMemberFetchedInstant();

    default LocalDateTime findLatestMemberFetched() {
        Instant instant = findLatestMemberFetchedInstant();
        return instant == null ? null : LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
    }

    @Query(value = "SELECT MAX(fetched) FROM subsystem", nativeQuery = true)
    Instant findLatestSubsystemFetchedInstant();

    default LocalDateTime findLatestSubsystemFetched() {
        Instant instant = findLatestSubsystemFetchedInstant();
        return instant == null ? null : LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
    }

    @Query(value = "SELECT MAX(fetched) FROM service", nativeQuery = true)
    Instant findLatestServiceFetchedInstant();

    default LocalDateTime findLatestServiceFetched() {
        Instant instant = findLatestServiceFetchedInstant();
        return instant == null ? null : LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
    }

    @Query(value = "SELECT MAX(fetched) FROM wsdl", nativeQuery = true)
    Instant findLatestWsdlFetchedInstant();

    default LocalDateTime findLatestWsdlFetched() {
        Instant instant = findLatestWsdlFetchedInstant();
        return instant == null ? null : LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
    }

    @Query(value = "SELECT MAX(fetched) FROM open_api", nativeQuery = true)
    Instant findLatestOpenApiFetchedInstant();

    default LocalDateTime findLatestOpenApiFetched() {
        Instant instant = findLatestOpenApiFetchedInstant();
        return instant == null ? null : LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
    }

    @Query(value = "SELECT MAX(fetched) FROM rest", nativeQuery = true)
    Instant findLatestRestFetchedInstant();

    default LocalDateTime findLatestRestFetched() {
        Instant instant = findLatestRestFetchedInstant();
        return instant == null ? null : LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
    }

    @Query(value = "SELECT 1", nativeQuery = true)
    Integer checkConnection();
}
