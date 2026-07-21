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

/**
 * Marker for repositories whose domain type lives in {@code org.niis.xroad.catalog.persistence.v2entity}.
 * {@code v2entity} is deliberately not scanned as a JPA managed-type package under the {@code test}
 * profile (see {@code V2ProductionConfiguration} in the lister module), so a repository extending
 * this marker cannot resolve its {@code EntityInformation} eagerly there; the lister's
 * {@code V2RepositoryTestBootstrapConfiguration} / {@code V2ReadModelLazyRepositoryConfiguration}
 * pair uses this marker (via {@code FilterType.ASSIGNABLE_TYPE}) to split repositories between the
 * default eager bootstrap and {@code BootstrapMode.LAZY} under that profile, without maintaining a
 * hardcoded class list that a new {@code v2entity}-bound repository could silently fall outside of.
 *
 * <p>Every {@code *RepositoryV2} interface bound to a {@code v2entity} type must extend this marker
 * in addition to {@link org.springframework.data.repository.Repository}. Repositories named
 * {@code *RepositoryV2} but bound to a V1 entity (e.g. {@code ErrorLogRepositoryV2}) must NOT extend
 * it, since those stay eager under {@code test} like any other V1 repository.
 */
public interface V2ReadModelRepository {
}
