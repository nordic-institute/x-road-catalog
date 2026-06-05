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
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.niis.xroad.catalog.lister.v2.dto.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class V2DispatchExceptionHandlerTest {

    private static final String METHOD_NOT_ALLOWED_ERROR = "MethodNotAllowed";

    private final V2DispatchExceptionHandler handler = new V2DispatchExceptionHandler();

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

    @Test
    void nonV2PathRootContextRethrowsSoSpringDefaultHandlesIt() {
        HttpRequestMethodNotSupportedException ex =
                new HttpRequestMethodNotSupportedException("POST", List.of("GET"));
        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        Mockito.when(request.getRequestURI()).thenReturn("/api/default/getServiceStatistics");
        Mockito.when(request.getContextPath()).thenReturn("");

        assertThrows(HttpRequestMethodNotSupportedException.class,
                () -> handler.handleMethodNotSupported(ex, request));
    }

    @Test
    void nonV2PathUnderContextPathRethrows() {
        HttpRequestMethodNotSupportedException ex =
                new HttpRequestMethodNotSupportedException("POST", List.of("GET"));
        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        Mockito.when(request.getRequestURI()).thenReturn("/catalog/api/default/getServiceStatistics");
        Mockito.when(request.getContextPath()).thenReturn("/catalog");

        assertThrows(HttpRequestMethodNotSupportedException.class,
                () -> handler.handleMethodNotSupported(ex, request));
    }

    @Test
    void nullRequestUriRethrows() {
        HttpRequestMethodNotSupportedException ex =
                new HttpRequestMethodNotSupportedException("POST", List.of("GET"));
        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        Mockito.when(request.getRequestURI()).thenReturn(null);
        Mockito.when(request.getContextPath()).thenReturn("");

        assertThrows(HttpRequestMethodNotSupportedException.class,
                () -> handler.handleMethodNotSupported(ex, request));
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

    @Test
    void nonV2PathContentNegotiationFailureRethrows() {
        HttpMediaTypeNotAcceptableException ex =
                new HttpMediaTypeNotAcceptableException(List.of(MediaType.APPLICATION_JSON));
        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        Mockito.when(request.getRequestURI()).thenReturn("/api/default/heartbeat");
        Mockito.when(request.getContextPath()).thenReturn("");

        assertThrows(HttpMediaTypeNotAcceptableException.class,
                () -> handler.handleNotAcceptable(ex, request));
    }

    @Test
    void nullRequestUriOn406Rethrows() {
        HttpMediaTypeNotAcceptableException ex =
                new HttpMediaTypeNotAcceptableException(List.of(MediaType.APPLICATION_JSON));
        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        Mockito.when(request.getRequestURI()).thenReturn(null);
        Mockito.when(request.getContextPath()).thenReturn("");

        assertThrows(HttpMediaTypeNotAcceptableException.class,
                () -> handler.handleNotAcceptable(ex, request));
    }
}
