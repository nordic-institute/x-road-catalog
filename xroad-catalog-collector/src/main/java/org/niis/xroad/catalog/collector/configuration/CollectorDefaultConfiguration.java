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
package org.niis.xroad.catalog.collector.configuration;

import org.niis.xroad.catalog.persistence.configuration.PersistenceDefaultConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Does not {@code @Import(PersistenceDefaultConfiguration.class)} — it reproduces that class's
 * component/entity scan directly and excludes {@code PersistenceDefaultConfiguration} itself from
 * the component scan so its own (unfiltered) {@code @EnableJpaRepositories} never also fires.
 * {@code @EnableJpaRepositories} here excludes every {@code *RepositoryV2} interface: those are V2
 * read-model repositories bound to {@code v2entity}-package domain types (e.g. {@code MemberV2}
 * for {@code MemberRepositoryV2}), which this application's {@code @EntityScan} never registers
 * (the collector writes only through the V1 entities). Without the exclusion, Spring would try to
 * resolve a V2 repository's {@code EntityInformation} against a domain type that isn't a managed
 * type in this context and fail at startup, even though the collector never uses that repository.
 */
@Configuration
@ComponentScan(basePackages = {"org.niis.xroad.catalog.collector", "org.niis.xroad.catalog.persistence"},
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = PersistenceDefaultConfiguration.class))
@EnableJpaRepositories(value = "org.niis.xroad.catalog.persistence.repository",
        excludeFilters = @ComponentScan.Filter(type = FilterType.REGEX, pattern = ".*RepositoryV2"))
@EntityScan("org.niis.xroad.catalog.persistence.entity")
public class CollectorDefaultConfiguration {
}
