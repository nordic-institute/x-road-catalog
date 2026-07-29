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

import org.niis.xroad.catalog.persistence.v2.repository.ReadModelRepository;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Enables every V1 and V2 repository for the {@code test} profile (H2 {@code create-drop}
 * contexts) EXCEPT the V2 read-model repositories, identified structurally by the
 * {@link ReadModelRepository} marker. Their {@code v2.entity} domain types are never scanned under
 * {@code test} ({@link V2ProductionConfiguration}), so without this exclusion Spring fails at
 * context refresh with {@code IllegalArgumentException: Not a managed type}, even for tests that
 * never touch a V2 repository. A new {@code v2.entity}-bound repository only needs to extend the
 * marker to be routed correctly here and in {@link V2ReadModelLazyRepositoryConfiguration}.
 *
 * <p>Mutually exclusive with {@link V2ProductionConfiguration} via {@code @Profile("test")} /
 * {@code @Profile("!test")}, so each context gets exactly one {@code @EnableJpaRepositories}
 * declaration for these base packages.
 */
@Configuration
@Profile("test")
@EnableJpaRepositories(basePackages = {"org.niis.xroad.catalog.persistence.repository",
        "org.niis.xroad.catalog.persistence.v2.repository"},
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = ReadModelRepository.class))
public class V2RepositoryTestBootstrapConfiguration {
}
