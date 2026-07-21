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
 * V2-only cross-cutting infrastructure beans (clock, request-id filter, …). Lives apart from
 * {@link JacksonV2Configuration} because that class is intentionally a non-configuration utility
 * holder for the V2 date-time serializer. Lives apart from {@link OpenApiConfiguration} because
 * SpringDoc-grouping concerns are unrelated to runtime infrastructure.
 */
@Configuration
public class V2InfrastructureConfiguration {

    /**
     * System-default-zone clock, not UTC. Every persisted timestamp is written with a zero-arg
     * {@code LocalDateTime.now()} — both in the collector and in entity lifecycle hooks — which
     * captures the JVM host's local wall-clock time, not UTC. V1 reads that same system-default
     * time back unchanged, and the V2 JSON serializer ({@link JacksonV2Configuration}) stamps an
     * offset onto it using {@code ZoneId.systemDefault()}. This bean must agree with all of that,
     * or day-boundary defaults derived from {@code today()} (e.g. the {@code /api/v2/reports/*}
     * and {@code /errors} endpoints) end up anchored a day off whenever the query runs near
     * midnight. Deployment invariant: the collector, the lister, and the Postgres session must all
     * share one timezone. Tests override with a fixed clock via their own
     * {@code @TestConfiguration}; {@link ConditionalOnMissingBean} keeps this production bean from
     * clashing with those overrides.
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
