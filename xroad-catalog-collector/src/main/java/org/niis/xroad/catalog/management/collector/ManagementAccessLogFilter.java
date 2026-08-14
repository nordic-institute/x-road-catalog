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

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * Access log for the management port. Emits exactly one INFO line per request through the normal
 * SLF4J pipeline (and therefore to stdout in a container), so probes, scrapes and manual pokes at
 * the actuator surface leave a trace.
 *
 * <p>The line is written from a {@code finally} block, so a request that blows up mid-chain is
 * still recorded; the status is read after the chain so it reflects what the container actually
 * decided. Requests that never reach a handler (404, 401) are logged too, because the filter is
 * registered at {@code HIGHEST_PRECEDENCE} on {@code /*}.
 *
 * <p>The request target is logged verbatim as received. That is safe from log injection: Tomcat
 * rejects control characters in the request line, and neither {@code getRequestURI()} nor
 * {@code getQueryString()} percent-decodes, so an encoded {@code %0A} stays encoded.
 */
@Slf4j
public class ManagementAccessLogFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long startNanos = System.nanoTime();
        try {
            chain.doFilter(request, response);
        } finally {
            long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
            log.info("Management request {} {} {} -> {} in {} ms",
                    request.getRemoteAddr(), request.getMethod(), requestTarget(request), response.getStatus(), elapsedMillis);
        }
    }

    private static String requestTarget(HttpServletRequest request) {
        String queryString = request.getQueryString();
        return queryString == null ? request.getRequestURI() : request.getRequestURI() + '?' + queryString;
    }
}
