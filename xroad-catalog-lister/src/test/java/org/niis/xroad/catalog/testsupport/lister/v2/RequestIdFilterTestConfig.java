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
package org.niis.xroad.catalog.testsupport.lister.v2;

import org.niis.xroad.catalog.lister.v2.util.RequestIdFilter;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * Pins the {@link RequestIdFilter}'s id generator to a known value for {@code @WebMvcTest}
 * controller slices that exercise correlation header behaviour.
 * <p>
 * Lives outside the {@code org.niis.xroad.catalog.lister} package on purpose: the lister's
 * explicit {@code @ComponentScan} does not inherit the boot-test {@code TypeExcludeFilter}
 * customisation, so a {@code @TestConfiguration} under that root would be auto-loaded into every
 * {@code @SpringBootTest} context, registering the filter on {@code /*} and leaking the
 * {@code X-Request-Id} header onto V1 endpoints.
 */
@TestConfiguration
public class RequestIdFilterTestConfig {

    public static final String FIXED_ID = "fixed-id-from-test";

    @Bean
    public RequestIdFilter requestIdFilter() {
        return new RequestIdFilter(() -> FIXED_ID);
    }
}
