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
package org.niis.xroad.catalog.persistence.v2.repository;

import org.niis.xroad.catalog.persistence.v2.entity.Wsdl;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * V2 read-model repository for descriptor blobs (WSDL/OpenAPI). Descriptor {@code data} must never
 * ride along the service tree/list queries in {@link ServiceRepository}, so it is exposed only
 * here, as plain {@code String} projections ordered by id.
 */
public interface DescriptorRepository extends Repository<Wsdl, Long>, ReadModelRepository {

    @Query("SELECT w.data FROM WsdlV2 w WHERE w.serviceId = :serviceId ORDER BY w.id")
    List<String> findActiveWsdlData(@Param("serviceId") long serviceId);

    @Query("SELECT o.data FROM OpenApiV2 o WHERE o.serviceId = :serviceId ORDER BY o.id")
    List<String> findActiveOpenApiData(@Param("serviceId") long serviceId);
}
