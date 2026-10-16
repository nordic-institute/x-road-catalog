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
package org.niis.xroad.catalog.lister.v2.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class RequestIdFilterTest {

    private static final String FIXED_GENERATED = "00000000-0000-0000-0000-000000000001";

    private RequestIdFilter filter;

    @BeforeEach
    void setUp() {
        filter = new RequestIdFilter(() -> FIXED_GENERATED);
        MDC.clear();
    }

    @Test
    void generatesIdWhenHeaderMissing() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v2/heartbeat");
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilter(req, res, new MockFilterChain());

        assertThat(res.getHeader("X-Request-Id")).isEqualTo(FIXED_GENERATED);
        assertThat(MDC.get("requestId")).isNull();
    }

    @Test
    void ignoresInboundHeaderAndUsesGeneratedId() throws Exception {
        // Defense-in-depth: the inbound value is never trusted, never echoed, never logged.
        // Even a benign-looking client header is replaced by the server-generated value.
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v2/heartbeat");
        req.addHeader("X-Request-Id", "client-supplied.123_abc");
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilter(req, res, new MockFilterChain());

        assertThat(res.getHeader("X-Request-Id")).isEqualTo(FIXED_GENERATED);
    }

    @Test
    void ignoresInboundHeaderEvenWhenItContainsControlCharacters() throws Exception {
        // The inbound payload could be a log-injection attempt — but since we never read it,
        // the filter doesn't need to validate it; it just substitutes the generated value.
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v2/heartbeat");
        req.addHeader("X-Request-Id", "evil\nFAKE LOG LINE");
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilter(req, res, new MockFilterChain());

        assertThat(res.getHeader("X-Request-Id")).isEqualTo(FIXED_GENERATED);
    }

    @Test
    void cleansMdcEvenWhenChainThrows() {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v2/heartbeat");
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain throwingChain = new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest request,
                                 jakarta.servlet.ServletResponse response) {
                throw new IllegalStateException("boom");
            }
        };

        try {
            filter.doFilter(req, res, throwingChain);
        } catch (Exception ignored) { /* expected */ }

        assertThat(MDC.get("requestId")).isNull();
    }
}
