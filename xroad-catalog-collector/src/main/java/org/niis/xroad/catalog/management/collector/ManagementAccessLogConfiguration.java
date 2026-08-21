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
package org.niis.xroad.catalog.management.collector;

import org.springframework.boot.actuate.autoconfigure.web.ManagementContextConfiguration;
import org.springframework.boot.actuate.autoconfigure.web.ManagementContextType;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;

/**
 * Registers {@link ManagementAccessLogFilter} on the management port only.
 *
 * <p>A servlet filter rather than {@code server.tomcat.accesslog.*}, because the management context binds the
 * same {@code ServerProperties} as the main context: enabling the Tomcat access log turns it on for both.
 *
 * <p>{@link ManagementContextType#CHILD} configurations are loaded only while the management server runs on
 * its own port. Setting {@code management.server.port} equal to {@code server.port} makes Boot reuse the main
 * context, and this class is then not loaded at all.
 *
 * <p>Keep this class outside {@code CollectorDefaultConfiguration}'s {@code @ComponentScan} roots:
 * {@code @ManagementContextConfiguration} is a {@code @Configuration}, so a scanned package would register
 * the filter in the main context as well.
 */
@ManagementContextConfiguration(value = ManagementContextType.CHILD, proxyBeanMethods = false)
public class ManagementAccessLogConfiguration {

    /**
     * {@link Ordered#HIGHEST_PRECEDENCE} puts the filter outermost, so requests rejected downstream or never
     * reaching a handler (401, 404) are logged too, with the status the client actually saw.
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
