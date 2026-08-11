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
package org.niis.xroad.catalog.lister.configuration;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Adds a {@code Deprecation: true} response header to every endpoint whose handler method or
 * controller class is annotated with {@link Deprecated}, so the existing annotations on the V1
 * controllers stay the single source of truth. The value is the draft-form boolean; the final
 * RFC 9745 date form (and an RFC 8594 {@code Sunset} header) can replace it once a removal date
 * is decided. The header must be set in {@code preHandle}: for {@code @ResponseBody} handlers
 * the response is committed before {@code postHandle} runs.
 */
public class DeprecationHeaderInterceptor implements HandlerInterceptor {

    static final String DEPRECATION_HEADER = "Deprecation";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (handler instanceof HandlerMethod handlerMethod && isDeprecated(handlerMethod)) {
            response.setHeader(DEPRECATION_HEADER, "true");
        }
        return true;
    }

    private static boolean isDeprecated(HandlerMethod handlerMethod) {
        return handlerMethod.getMethod().isAnnotationPresent(Deprecated.class)
                || handlerMethod.getBeanType().isAnnotationPresent(Deprecated.class);
    }
}
