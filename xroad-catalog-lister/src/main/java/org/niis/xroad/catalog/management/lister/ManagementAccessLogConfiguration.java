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
package org.niis.xroad.catalog.management.lister;

import org.springframework.boot.actuate.autoconfigure.web.ManagementContextConfiguration;
import org.springframework.boot.actuate.autoconfigure.web.ManagementContextType;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;

/**
 * Registers {@link ManagementAccessLogFilter} into the management child context only: every request to the
 * management port leaves a trace, while the lister's public REST and SOAP API on the main port stays unlogged.
 *
 * <p><b>Why not {@code server.tomcat.accesslog.*}:</b> the management child context binds the <em>same</em>
 * {@code ServerProperties} as the main context, so {@code enabled} is one shared flag — switching it on for
 * the management port would also log the lister's entire public API. The one management-specific knob,
 * {@code management.server.tomcat.accesslog.prefix} (default {@code management_}), cannot help: Boot's
 * {@code TomcatAccessLogCustomizer} only renames an {@code AccessLogValve} that already exists and returns
 * early when there is none. That rename also breaks the container idiom of writing the access log to stdout
 * ({@code directory: /dev}, {@code prefix: stdout}), because the management context would then target
 * {@code /dev/management_stdout}. A servlet filter sidesteps all of it: no valve is involved, so Boot's
 * customizer early-returns, and the line reaches stdout through the normal logging pipeline.
 *
 * <p><b>When it applies:</b> Boot loads {@link ManagementContextType#CHILD} configurations only into the
 * separate management context, which exists only while the management server runs on its own port. The
 * lister defaults {@code management.server.port} to 8090, so it applies. If {@code management.server.port}
 * is ever set equal to {@code server.port}, Boot reuses the main context, this class is not loaded at all,
 * and no management access log is produced.
 *
 * <p><b>Package placement is load-bearing:</b> {@code @ManagementContextConfiguration} is meta-annotated
 * with {@code @Configuration}, hence with {@code @Component}. In a component-scanned package the main
 * context would pick this up and register the filter on the main port too. Keep it outside
 * {@code ListerDefaultConfiguration}'s {@code @ComponentScan} roots.
 */
@ManagementContextConfiguration(value = ManagementContextType.CHILD, proxyBeanMethods = false)
public class ManagementAccessLogConfiguration {

    /**
     * Maps the filter to every management request. {@link Ordered#HIGHEST_PRECEDENCE} puts it outermost so
     * that requests rejected by a downstream filter, or failing before any handler runs, are still logged
     * with the status the client actually saw.
     */
    @Bean
    public FilterRegistrationBean<ManagementAccessLogFilter> managementAccessLogFilterRegistration() {
        FilterRegistrationBean<ManagementAccessLogFilter> registration =
                new FilterRegistrationBean<>(new ManagementAccessLogFilter());
        registration.addUrlPatterns("/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }
}
