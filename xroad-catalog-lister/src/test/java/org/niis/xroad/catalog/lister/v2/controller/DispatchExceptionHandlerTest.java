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
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mockito;
import org.niis.xroad.catalog.lister.v2.dto.ErrorResponse;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DispatchExceptionHandlerTest {

    private static final String METHOD_NOT_ALLOWED_ERROR = "MethodNotAllowed";
    private static final String NOT_FOUND_ERROR = "NotFound";
    private static final String UNKNOWN_V2_PATH = "/api/v2/nonexistent";

    private final DispatchExceptionHandler handler = new DispatchExceptionHandler();

    @Test
    void v2PathRootContextReturnsErrorResponseWithAllowHeader() throws Exception {
        HttpRequestMethodNotSupportedException ex =
                new HttpRequestMethodNotSupportedException("POST", List.of("GET"));
        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        Mockito.when(request.getRequestURI()).thenReturn("/api/v2/list/security-servers");
        Mockito.when(request.getContextPath()).thenReturn("");

        ResponseEntity<ErrorResponse> response = handler.handleMethodNotSupported(ex, request);

        assertEquals(HttpStatus.METHOD_NOT_ALLOWED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(HttpStatus.METHOD_NOT_ALLOWED.value(), response.getBody().getStatus());
        assertEquals(METHOD_NOT_ALLOWED_ERROR, response.getBody().getError());
        assertTrue(response.getBody().getMessage().contains("POST"),
                "message should reference the rejected method");
        assertEquals("GET", response.getHeaders().getFirst("Allow"),
                "Allow header must propagate from HttpRequestMethodNotSupportedException");
    }

    @Test
    void v2PathUnderContextPathStillReturnsErrorResponse() throws Exception {
        HttpRequestMethodNotSupportedException ex =
                new HttpRequestMethodNotSupportedException("POST", List.of("GET"));
        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        Mockito.when(request.getRequestURI()).thenReturn("/catalog/api/v2/list/security-servers");
        Mockito.when(request.getContextPath()).thenReturn("/catalog");

        ResponseEntity<ErrorResponse> response = handler.handleMethodNotSupported(ex, request);

        assertEquals(HttpStatus.METHOD_NOT_ALLOWED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(METHOD_NOT_ALLOWED_ERROR, response.getBody().getError());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("methodNotSupportedRethrowCases")
    void handleMethodNotSupportedRethrowsForNonV2OrNullPaths(String requestUri, String contextPath) {
        HttpRequestMethodNotSupportedException ex =
                new HttpRequestMethodNotSupportedException("POST", List.of("GET"));
        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        Mockito.when(request.getRequestURI()).thenReturn(requestUri);
        Mockito.when(request.getContextPath()).thenReturn(contextPath);

        assertThrows(HttpRequestMethodNotSupportedException.class,
                () -> handler.handleMethodNotSupported(ex, request));
    }

    private static Stream<Arguments> methodNotSupportedRethrowCases() {
        return Stream.of(
                Arguments.of(Named.of("non-V2 path at root context rethrows so Spring default handles it",
                        "/api/default/getServiceStatistics"), ""),
                Arguments.of(Named.of("non-V2 path under context path rethrows",
                        "/catalog/api/default/getServiceStatistics"), "/catalog"),
                Arguments.of(Named.of("null request URI rethrows", null), "")
        );
    }

    @Test
    void v2PathContentNegotiationFailureReturnsErrorResponse() throws Exception {
        HttpMediaTypeNotAcceptableException ex =
                new HttpMediaTypeNotAcceptableException(List.of(MediaType.APPLICATION_JSON));
        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        Mockito.when(request.getRequestURI()).thenReturn("/api/v2/heartbeat");
        Mockito.when(request.getContextPath()).thenReturn("");

        ResponseEntity<ErrorResponse> response = handler.handleNotAcceptable(ex, request);

        assertEquals(HttpStatus.NOT_ACCEPTABLE, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(HttpStatus.NOT_ACCEPTABLE.value(), response.getBody().getStatus());
        assertEquals("NotAcceptable", response.getBody().getError());
        assertTrue(response.getBody().getMessage().contains("application/json"),
                "message should advertise the supported media type");
    }

    @Test
    void v2PathContentNegotiationUnderContextPathReturnsErrorResponse() throws Exception {
        HttpMediaTypeNotAcceptableException ex =
                new HttpMediaTypeNotAcceptableException(List.of(MediaType.APPLICATION_JSON));
        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        Mockito.when(request.getRequestURI()).thenReturn("/catalog/api/v2/heartbeat");
        Mockito.when(request.getContextPath()).thenReturn("/catalog");

        ResponseEntity<ErrorResponse> response = handler.handleNotAcceptable(ex, request);

        assertEquals(HttpStatus.NOT_ACCEPTABLE, response.getStatusCode());
        assertEquals("NotAcceptable", response.getBody().getError());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("notAcceptableRethrowCases")
    void handleNotAcceptableRethrowsForNonV2OrNullPaths(String requestUri, String contextPath) {
        HttpMediaTypeNotAcceptableException ex =
                new HttpMediaTypeNotAcceptableException(List.of(MediaType.APPLICATION_JSON));
        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        Mockito.when(request.getRequestURI()).thenReturn(requestUri);
        Mockito.when(request.getContextPath()).thenReturn(contextPath);

        assertThrows(HttpMediaTypeNotAcceptableException.class,
                () -> handler.handleNotAcceptable(ex, request));
    }

    private static Stream<Arguments> notAcceptableRethrowCases() {
        return Stream.of(
                Arguments.of(Named.of("non-V2 path content negotiation failure rethrows",
                        "/api/default/heartbeat"), ""),
                Arguments.of(Named.of("null request URI on 406 rethrows", null), "")
        );
    }

    @Test
    void v2PathUnknownResourceReturnsErrorResponse() throws Exception {
        NoResourceFoundException ex = new NoResourceFoundException(HttpMethod.GET, UNKNOWN_V2_PATH);
        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        Mockito.when(request.getRequestURI()).thenReturn(UNKNOWN_V2_PATH);
        Mockito.when(request.getContextPath()).thenReturn("");

        ResponseEntity<ErrorResponse> response = handler.handleNoResourceFound(ex, request);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(HttpStatus.NOT_FOUND.value(), response.getBody().getStatus());
        assertEquals(NOT_FOUND_ERROR, response.getBody().getError());
        assertTrue(response.getBody().getMessage().contains(UNKNOWN_V2_PATH),
                "message should reference the requested path");
    }

    @Test
    void v2PathUnknownResourceUnderContextPathReturnsErrorResponse() throws Exception {
        NoResourceFoundException ex = new NoResourceFoundException(HttpMethod.GET, UNKNOWN_V2_PATH);
        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        Mockito.when(request.getRequestURI()).thenReturn("/catalog" + UNKNOWN_V2_PATH);
        Mockito.when(request.getContextPath()).thenReturn("/catalog");

        ResponseEntity<ErrorResponse> response = handler.handleNoResourceFound(ex, request);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(NOT_FOUND_ERROR, response.getBody().getError());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("noResourceFoundRethrowCases")
    void handleNoResourceFoundRethrowsForNonV2OrNullPaths(String requestUri, String contextPath, String exceptionPath) {
        NoResourceFoundException ex = new NoResourceFoundException(HttpMethod.GET, exceptionPath);
        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        Mockito.when(request.getRequestURI()).thenReturn(requestUri);
        Mockito.when(request.getContextPath()).thenReturn(contextPath);

        assertThrows(NoResourceFoundException.class,
                () -> handler.handleNoResourceFound(ex, request));
    }

    private static Stream<Arguments> noResourceFoundRethrowCases() {
        return Stream.of(
                Arguments.of(Named.of("non-V2 path unknown resource rethrows",
                        "/api/default/nonexistent"), "", "/api/default/nonexistent"),
                Arguments.of(Named.of("null request URI on 404 rethrows", null), "", UNKNOWN_V2_PATH)
        );
    }
}
