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
import org.springframework.data.repository.config.BootstrapMode;

/**
 * Companion to {@link V2RepositoryTestBootstrapConfiguration}: under the {@code test} profile,
 * registers exactly the {@link ReadModelRepository} implementors that class excludes. The beans
 * must exist because V2 services constructor-inject them in full-context boots, but their
 * {@code v2.entity} domain types are never scanned under {@code test}
 * ({@link V2ProductionConfiguration}), so eager {@code EntityInformation} resolution would fail
 * with {@code Not a managed type}. {@link BootstrapMode#LAZY} defers resolution to first method
 * call, which those tests never make. The include filter here is exactly the other class's exclude
 * filter, so the two repository sets stay disjoint with no hardcoded list to sync.
 */
@Configuration
@Profile("test")
@EnableJpaRepositories(basePackages = "org.niis.xroad.catalog.persistence.v2.repository",
        includeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = ReadModelRepository.class),
        bootstrapMode = BootstrapMode.LAZY)
public class V2ReadModelLazyRepositoryConfiguration {
}
