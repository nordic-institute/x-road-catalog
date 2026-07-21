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
import org.springframework.data.repository.config.BootstrapMode;

/**
 * Companion to {@link V2RepositoryTestBootstrapConfiguration}: under the {@code test} profile,
 * registers exactly the V2 read-model repositories bound to a {@code v2entity} domain type --
 * every repository extending {@link V2ReadModelRepository} -- which
 * {@link V2RepositoryTestBootstrapConfiguration} explicitly excludes.
 *
 * <p>These beans must still exist under {@code test}: full-context boots wire in the V2
 * controllers, whose services constructor-inject these repositories, so dependency injection needs
 * the bean even when a V1-focused test never calls one of its methods. But {@code v2entity} is
 * never scanned under {@code test} (see {@link V2ProductionConfiguration}), so eagerly resolving
 * {@code EntityInformation} for these at context-refresh time would fail with {@code
 * IllegalArgumentException: Not a managed type}. {@link BootstrapMode#LAZY} defers that resolution
 * to first method call, which none of those tests ever makes.
 *
 * <p>Scoping the lazy bootstrap to only {@link V2ReadModelRepository} implementors keeps every other
 * repository on the eager bootstrap mode {@link V2RepositoryTestBootstrapConfiguration} gives them,
 * so a broken V1 query still fails loudly at context refresh. The two {@code @EnableJpaRepositories}
 * declarations coexist safely because their repository interface sets are disjoint by construction
 * (the exclude filter on one is exactly the include filter on the other, both keyed off the same
 * marker), so there is no bean-name collision, and no hardcoded list to keep in sync as new
 * {@code v2entity}-bound repositories are added -- they only need to extend the marker.
 */
@Configuration
@Profile("test")
@EnableJpaRepositories(basePackages = "org.niis.xroad.catalog.persistence.repository",
        includeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = V2ReadModelRepository.class),
        bootstrapMode = BootstrapMode.LAZY)
public class V2ReadModelLazyRepositoryConfiguration {
}
