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
package org.niis.xroad.catalog.persistence;

import org.niis.xroad.catalog.persistence.configuration.PersistenceDefaultConfiguration;
import org.niis.xroad.catalog.persistence.configuration.ProcessedSqlLoader;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.convert.threeten.Jsr310JpaConverters;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.data.repository.config.BootstrapMode;

/**
 * Test-only bootstrap. This intentionally does not {@code @Import(PersistenceDefaultConfiguration.class)}
 * — it reproduces the same component/entity scan but declares {@code @EnableJpaRepositories} with
 * {@link BootstrapMode#LAZY}, and excludes {@code PersistenceDefaultConfiguration} itself from its
 * component scan so that class's own (eager) {@code @EnableJpaRepositories} never also fires.
 * Eager bootstrap would make every repository bean (including V2 read-model repositories bound to
 * a {@code v2entity}-package domain type such as {@code MemberRepositoryV2}) resolve its
 * {@code EntityInformation} at context-refresh time, which fails under the H2
 * {@code general-testdata} profile: that profile's {@code hibernate.ddl-auto: create-drop} can
 * only ever scan the {@code entity} package (adding {@code v2entity} there reintroduces the
 * dual-mapping schema-drop collision the V2 entity package split was designed to avoid). Lazy
 * bootstrap defers a repository's {@code EntityInformation} resolution to its first method call,
 * so H2 tests that never invoke a V2 repository never trigger it; Postgres-backed tests that add
 * {@code v2entity} to their own test-class-level {@code @EntityScan} (see
 * {@code ReadModelEntityTest}) resolve it successfully on first use.
 */
@SpringBootApplication
@ComponentScan(basePackages = "org.niis.xroad.catalog.persistence", basePackageClasses = Jsr310JpaConverters.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = PersistenceDefaultConfiguration.class))
@EnableJpaRepositories(value = "org.niis.xroad.catalog.persistence.repository", bootstrapMode = BootstrapMode.LAZY)
@EntityScan("org.niis.xroad.catalog.persistence.entity")
@Import(ProcessedSqlLoader.class)
public class PersistenceTestApplication {

    public static void main(String[] args) {
        SpringApplication.run(PersistenceTestApplication.class);
    }

}
