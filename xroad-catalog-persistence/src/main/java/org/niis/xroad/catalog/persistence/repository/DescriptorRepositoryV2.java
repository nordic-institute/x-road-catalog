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

import org.niis.xroad.catalog.persistence.v2entity.WsdlV2;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * V2 read-model repository for service descriptor blobs (WSDL / OpenAPI / REST). Used only by the
 * descriptor endpoints and the heartbeat freshness check — descriptor {@code data} must never ride
 * along the service tree/list queries in {@link ServiceRepositoryV2}, so it is exposed here as
 * plain {@code String} projections, ordered by id, rather than as entity associations.
 */
public interface DescriptorRepositoryV2 extends Repository<WsdlV2, Long>, V2ReadModelRepository {

    @Query("SELECT w.data FROM WsdlV2 w WHERE w.serviceId = :serviceId AND w.statusInfo.removed IS NULL ORDER BY w.id")
    List<String> findActiveWsdlData(@Param("serviceId") long serviceId);

    @Query("SELECT o.data FROM OpenApiV2 o WHERE o.serviceId = :serviceId AND o.statusInfo.removed IS NULL ORDER BY o.id")
    List<String> findActiveOpenApiData(@Param("serviceId") long serviceId);

    @Query("SELECT MAX(w.statusInfo.fetched) FROM WsdlV2 w")
    LocalDateTime findLatestWsdlFetched();

    @Query("SELECT MAX(o.statusInfo.fetched) FROM OpenApiV2 o")
    LocalDateTime findLatestOpenApiFetched();

    @Query("SELECT MAX(r.statusInfo.fetched) FROM RestV2 r")
    LocalDateTime findLatestRestFetched();
}
