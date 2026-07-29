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
 * Test-only bootstrap. Deliberately does not import {@code PersistenceDefaultConfiguration}: it
 * reproduces the same component/entity scan but declares {@code @EnableJpaRepositories} with
 * {@link BootstrapMode#LAZY}, and excludes that class so its eager repository bootstrap never
 * fires. Eager bootstrap would resolve every repository's {@code EntityInformation} at context
 * refresh, which fails under the H2 {@code general-testdata} profile where only the {@code entity}
 * package can be scanned. Lazy bootstrap defers resolution to the first method call, so H2 tests
 * that never touch a V2 repository never trigger it, while Postgres-backed tests add
 * {@code v2.entity} via their own {@code @EntityScan}.
 */
@SpringBootApplication
@ComponentScan(basePackages = "org.niis.xroad.catalog.persistence", basePackageClasses = Jsr310JpaConverters.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = PersistenceDefaultConfiguration.class))
@EnableJpaRepositories(value = {"org.niis.xroad.catalog.persistence.repository",
        "org.niis.xroad.catalog.persistence.v2.repository"}, bootstrapMode = BootstrapMode.LAZY)
@EntityScan("org.niis.xroad.catalog.persistence.entity")
@Import(ProcessedSqlLoader.class)
public class PersistenceTestApplication {

    public static void main(String[] args) {
        SpringApplication.run(PersistenceTestApplication.class);
    }

}
