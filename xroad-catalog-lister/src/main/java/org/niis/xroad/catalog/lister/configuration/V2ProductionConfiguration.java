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

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Adds the {@code v2entity} read-model package to the lister's entity scan (on top of the
 * {@code entity} package {@code ListerDefaultConfiguration} already registers -- {@link EntityScan}
 * packages accumulate across every {@code @EntityScan}-annotated class in a context) and enables
 * every repository in {@code org.niis.xroad.catalog.persistence.repository} -- V1 and V2 alike --
 * for every profile except {@code test}. Both declarations share that single {@code !test} gate and
 * don't otherwise interact, so they live in one class rather than two.
 *
 * <p>The gate matters: the V1 H2 {@code create-drop} {@code test}-profile contexts boot the same
 * {@link org.niis.xroad.catalog.lister.ListerApplication} as production, so scanning {@code
 * v2entity} there too would map two {@code @Entity} classes to the same table (e.g. {@code member})
 * and fail schema generation -- exactly the collision the {@code v2entity} split avoids. Production
 * never activates {@code test}, so real deployments scan {@code v2entity} and resolve the V2
 * read-model repositories eagerly like any other repository, no lazy bootstrap needed.
 *
 * <p>This and {@link V2RepositoryTestBootstrapConfiguration} / {@link
 * V2ReadModelLazyRepositoryConfiguration} are mutually exclusive via {@code @Profile("!test")} /
 * {@code @Profile("test")}, so there is no bean-definition overriding between them.
 * {@code ListerDefaultConfiguration} deliberately skips importing {@code
 * PersistenceDefaultConfiguration} so that class's unconditional {@code @EnableJpaRepositories}
 * never also fires.
 */
@Configuration
@Profile("!test")
@EntityScan("org.niis.xroad.catalog.persistence.v2entity")
@EnableJpaRepositories("org.niis.xroad.catalog.persistence.repository")
public class V2ProductionConfiguration {
}
