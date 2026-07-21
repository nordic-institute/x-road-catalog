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
package org.niis.xroad.catalog.lister.configuration;

import org.niis.xroad.catalog.persistence.repository.V2ReadModelRepository;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Enables every repository in {@code org.niis.xroad.catalog.persistence.repository} for the
 * {@code test} profile (the V1 H2 {@code create-drop} contexts) EXCEPT the V2 read-model
 * repositories whose domain type lives in {@code v2entity} -- identified structurally by extending
 * the {@link V2ReadModelRepository} marker, rather than by a hardcoded class list. {@link
 * V2ProductionConfiguration} deliberately never scans {@code v2entity} under {@code test}, so those
 * interfaces' domain types are not managed JPA types in this context; without this exclusion Spring
 * would try to resolve their {@code EntityInformation} at context-refresh time and fail with
 * {@code IllegalArgumentException: Not a managed type}, even for tests that never touch a V2
 * repository. Because the split is structural, a new {@code v2entity}-bound repository only needs
 * to extend the marker to be routed correctly here and in {@link V2ReadModelLazyRepositoryConfiguration}
 * -- there is no second list to remember to update.
 *
 * <p>Every OTHER repository -- including V1 repositories and the {@code *RepositoryV2}-named ones
 * bound to a V1 entity type ({@code ErrorLogRepositoryV2}, {@code SearchRepository}) -- keeps the
 * default eager bootstrap mode here, so no repository anywhere loses eager fail-fast validation as
 * a side effect of this class existing.
 *
 * <p>This and {@link V2ProductionConfiguration} are mutually exclusive via
 * {@code @Profile("test")} / {@code @Profile("!test")}: exactly one is ever active in a given
 * context, so there is no bean-definition overriding between the two -- each profile gets its own
 * single, non-overlapping {@code @EnableJpaRepositories} declaration for the same base package.
 * {@code ListerDefaultConfiguration} deliberately stops importing {@code
 * PersistenceDefaultConfiguration} so that class's own (unconditional) {@code
 * @EnableJpaRepositories} never also fires.
 */
@Configuration
@Profile("test")
@EnableJpaRepositories(basePackages = "org.niis.xroad.catalog.persistence.repository",
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = V2ReadModelRepository.class))
public class V2RepositoryTestBootstrapConfiguration {
}
