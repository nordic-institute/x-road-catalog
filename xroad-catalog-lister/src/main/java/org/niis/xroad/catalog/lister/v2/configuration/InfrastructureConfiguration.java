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
package org.niis.xroad.catalog.lister.v2.configuration;

import org.niis.xroad.catalog.lister.v2.util.RequestIdFilter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

import java.time.Clock;
import java.util.UUID;

/**
 * V2-only cross-cutting infrastructure beans (clock, request-id filter).
 */
@Configuration
public class InfrastructureConfiguration {

    /**
     * System-default-zone clock, not UTC: persisted timestamps are written with zero-arg
     * {@code LocalDateTime.now()} (host-local wall clock) and the V2 JSON serializer stamps the
     * offset from {@code ZoneId.systemDefault()}. This bean must agree, or day-boundary defaults
     * derived from {@code today()} anchor a day off near midnight. Deployment invariant: the
     * collector, the lister and the Postgres session must share one timezone.
     * {@link ConditionalOnMissingBean} lets tests override with a fixed clock.
     */
    @Bean
    @ConditionalOnMissingBean(Clock.class)
    public Clock systemClock() {
        return Clock.systemDefaultZone();
    }

    /**
     * Registers the V2 correlation filter scoped to {@code /api/v2/*} only — V1 and SOAP traffic
     * stay untouched. {@link Ordered#HIGHEST_PRECEDENCE} ensures the {@code requestId} MDC key is
     * present before any downstream filter logs.
     */
    @Bean
    public FilterRegistrationBean<RequestIdFilter> requestIdFilterRegistration() {
        FilterRegistrationBean<RequestIdFilter> reg = new FilterRegistrationBean<>(
                new RequestIdFilter(() -> UUID.randomUUID().toString()));
        reg.addUrlPatterns("/api/v2/*");
        reg.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return reg;
    }
}
