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
package org.niis.xroad.catalog.lister.v2.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.niis.xroad.catalog.lister.v2.dto.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Advice for framework exceptions raised before handler-method selection, where
 * {@link ApiExceptionHandler}'s basePackages-scoped advice cannot apply. Only {@code /api/v2/}
 * paths receive the V2 {@link ErrorResponse} shape; V1 routes keep Spring's default behavior.
 * The path check strips the servlet context path so it works under a non-root context.
 */
@RestControllerAdvice
public class DispatchExceptionHandler {

    private static final String V2_PREFIX = "/api/v2/";

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException ex,
            HttpServletRequest request) throws HttpRequestMethodNotSupportedException {
        if (!isV2Path(request)) {
            throw ex;
        }
        String message = "HTTP method '" + ex.getMethod() + "' is not supported on this resource";
        ErrorResponse body = ErrorResponse.fromStatus(
                HttpStatus.METHOD_NOT_ALLOWED.value(), "MethodNotAllowed", message);
        // Force Content-Type: application/json on the error body to bypass content negotiation
        // against the rejected client Accept header (same rationale as handleNotAcceptable).
        return ResponseEntity
                .status(HttpStatus.METHOD_NOT_ALLOWED)
                .headers(ex.getHeaders())
                .contentType(MediaType.APPLICATION_JSON)
                .body(body);
    }

    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    public ResponseEntity<ErrorResponse> handleNotAcceptable(
            HttpMediaTypeNotAcceptableException ex,
            HttpServletRequest request) throws HttpMediaTypeNotAcceptableException {
        if (!isV2Path(request)) {
            throw ex;
        }
        // Force Content-Type: application/json so the error body bypasses content negotiation against
        // the rejected Accept header — otherwise the message converter raises a second
        // HttpMediaTypeNotAcceptableException and the client gets an empty 406 body.
        return ResponseEntity
                .status(HttpStatus.NOT_ACCEPTABLE)
                .headers(ex.getHeaders())
                .contentType(MediaType.APPLICATION_JSON)
                .body(ErrorResponse.fromStatus(
                        HttpStatus.NOT_ACCEPTABLE.value(),
                        "NotAcceptable",
                        "Acceptable representation: application/json"));
    }

    /**
     * An unmapped {@code /api/v2/**} path is served by the static-resource handler, which raises this
     * before handler selection. Without it the same API would answer with two different 404 bodies:
     * Boot's default for unknown paths and the V2 shape for unknown resources on a known route.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResourceFound(
            NoResourceFoundException ex,
            HttpServletRequest request) throws NoResourceFoundException {
        String relative = relativePath(request);
        if (relative == null || !relative.startsWith(V2_PREFIX)) {
            throw ex;
        }
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .contentType(MediaType.APPLICATION_JSON)
                .body(ErrorResponse.notFound("No resource found for path '" + relative + "'"));
    }

    private static boolean isV2Path(HttpServletRequest request) {
        String relative = relativePath(request);
        return relative != null && relative.startsWith(V2_PREFIX);
    }

    private static String relativePath(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (uri == null) {
            return null;
        }
        String contextPath = request.getContextPath();
        // Servlet spec: getContextPath() returns "" for root context, otherwise a path starting
        // with "/" without trailing slash. Strip it before matching the V2 prefix.
        return (contextPath == null || contextPath.isEmpty())
                ? uri
                : uri.substring(contextPath.length());
    }
}
