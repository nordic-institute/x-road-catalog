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

import org.niis.xroad.catalog.persistence.configuration.PersistenceDefaultConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;

/**
 * Does not {@code @Import(PersistenceDefaultConfiguration.class)} -- it reproduces that class's
 * component/entity scan directly and excludes {@code PersistenceDefaultConfiguration} itself from
 * the component scan so that class's own {@code @EnableJpaRepositories} (which knows nothing about
 * the lister's V1/V2 dual entity-scan split) never fires. Repository enabling is instead split
 * across mutually exclusive {@code @Profile}-gated classes -- {@link V2ProductionConfiguration} and
 * its {@code test}-profile siblings -- each contributing its own {@code @EnableJpaRepositories}
 * declaration (one per profile); see their javadoc. This
 * mirrors the same restructuring already applied to {@code CollectorDefaultConfiguration} for the
 * analogous problem of a V2 read-model repository not being resolvable against every context.
 */
@Configuration
@ComponentScan(basePackages = {"org.niis.xroad.catalog.lister", "org.niis.xroad.catalog.persistence"},
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = PersistenceDefaultConfiguration.class))
@EntityScan("org.niis.xroad.catalog.persistence.entity")
public class ListerDefaultConfiguration {
}
