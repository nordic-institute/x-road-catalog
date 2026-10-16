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
 * Adds the {@code persistence.v2.entity} read-model package to the entity scan ({@link EntityScan}
 * packages accumulate across configuration classes) and enables the V1 and V2 repositories for every
 * profile except {@code test}. The gate matters for two reasons: each context must get exactly one
 * {@code @EnableJpaRepositories} declaration for these base packages, and the {@code test} profile
 * supplies its own; and under H2 create-drop Hibernate would materialize the Liquibase-managed
 * {@code active_*} views as free-standing empty tables, so H2 tests touching the V2 read model would
 * silently see no data — real V2 read-model coverage runs against Postgres/Liquibase contexts.
 */
@Configuration
@Profile("!test")
@EntityScan("org.niis.xroad.catalog.persistence.v2.entity")
@EnableJpaRepositories({"org.niis.xroad.catalog.persistence.repository",
        "org.niis.xroad.catalog.persistence.v2.repository"})
public class ProductionConfigurationV2 {
}
