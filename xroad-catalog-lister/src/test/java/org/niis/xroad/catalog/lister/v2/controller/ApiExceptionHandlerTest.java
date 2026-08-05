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

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.niis.xroad.catalog.lister.v2.dto.ErrorResponse;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.web.method.annotation.ExceptionHandlerMethodResolver;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiExceptionHandlerTest {

    private static final String NOT_READY_REASON =
            "X-Road instance identifier not yet available; configuration-client may still be initializing";

    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    @Test
    void handleBadRequestReturnsBadRequestWithTheValidationMessage() {
        ResponseEntity<ErrorResponse> response =
                handler.handleBadRequest(new BadRequestException("invalid param"));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(HttpStatus.BAD_REQUEST.value(), response.getBody().getStatus());
        assertEquals("BadRequest", response.getBody().getError());
        assertEquals("invalid param", response.getBody().getMessage());
    }

    @Test
    void badRequestExceptionResolvesToTheBadRequestHandler() {
        ExceptionHandlerMethodResolver resolver = new ExceptionHandlerMethodResolver(ApiExceptionHandler.class);
        Method resolved = resolver.resolveMethod(new BadRequestException("invalid param"));

        assertNotNull(resolved);
        assertEquals("handleBadRequest", resolved.getName());
    }

    @Test
    void unexpectedIllegalArgumentExceptionResolvesToTheGenericHandler() {
        // Only V2 validation failures are client errors; an internal precondition failure must not
        // be reported as 400 with its invariant message.
        ExceptionHandlerMethodResolver resolver = new ExceptionHandlerMethodResolver(ApiExceptionHandler.class);
        Method resolved = resolver.resolveMethod(
                new IllegalArgumentException("versionRows must not be null or empty"));

        assertNotNull(resolved);
        assertEquals("handleException", resolved.getName());
    }

    @Test
    void unexpectedIllegalArgumentExceptionResponseHidesTheInternalMessage() {
        ResponseEntity<ErrorResponse> response = handler.handleException(
                new IllegalArgumentException("versionRows must not be null or empty"));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Internal server error", response.getBody().getMessage());
    }

    @Test
    void handleResourceNotFoundExceptionReturnsNotFound() {
        ResponseEntity<ErrorResponse> response =
                handler.handleResourceNotFoundException(ResourceNotFoundException.of("Widget", "abc"));

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(HttpStatus.NOT_FOUND.value(), response.getBody().getStatus());
        assertEquals("NotFound", response.getBody().getError());
        assertEquals("Widget 'abc' not found", response.getBody().getMessage());
    }

    @Test
    void handleMultipleVersionsExceptionReturnsConflictWithVersions() {
        MultipleVersionsException ex = new MultipleVersionsException(
                "Service has multiple versions", Arrays.asList("v2", null, "v1"));

        ResponseEntity<ErrorResponse> response = handler.handleMultipleVersions(ex);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(HttpStatus.CONFLICT.value(), response.getBody().getStatus());
        assertEquals("Conflict", response.getBody().getError());
        assertEquals("Service has multiple versions", response.getBody().getMessage());
        assertEquals(Arrays.asList("v2", null, "v1"), response.getBody().getVersions(),
                "versions must be passed through in the order given");
    }

    @Test
    void handleExceptionReturnsInternalServerError() {
        ResponseEntity<ErrorResponse> response =
                handler.handleException(new RuntimeException("unexpected"));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR.value(), response.getBody().getStatus());
        assertEquals("InternalServerError", response.getBody().getError());
        assertEquals("Internal server error", response.getBody().getMessage());
    }

    @Test
    void handleTypeMismatchReturnsBadRequest() {
        org.springframework.core.MethodParameter parameter = org.mockito.Mockito.mock(
                org.springframework.core.MethodParameter.class);
        MethodArgumentTypeMismatchException ex = new MethodArgumentTypeMismatchException(
                "abc", Integer.class, "page", parameter, new NumberFormatException("nope"));

        ResponseEntity<ErrorResponse> response = handler.handleTypeMismatch(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(HttpStatus.BAD_REQUEST.value(), response.getBody().getStatus());
        assertEquals("BadRequest", response.getBody().getError());
        assertTrue(response.getBody().getMessage().contains("page"),
                "message should reference the parameter name");
        assertTrue(response.getBody().getMessage().contains("abc"),
                "message should reference the rejected value");
    }

    @Test
    void handleBindExceptionReturnsBadRequestWithFirstFieldError() {
        // MapBindingResult avoids BeanWrapper introspection — BeanPropertyBindingResult would
        // attempt to look up `page`/`size` properties on the target Object and fail at setup.
        org.springframework.validation.MapBindingResult bindingResult =
                new org.springframework.validation.MapBindingResult(new java.util.HashMap<>(), "pageOpts");
        bindingResult.rejectValue("page", "typeMismatch", "Invalid Integer");
        bindingResult.rejectValue("size", "typeMismatch", "Invalid Integer");
        BindException ex = new BindException(bindingResult);

        ResponseEntity<ErrorResponse> response = handler.handleBindException(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("BadRequest", response.getBody().getError());
        assertTrue(response.getBody().getMessage().contains("page"),
                "message should surface the first field error");
    }

    @Test
    void genericExceptionResponseNeverEchoesTheExceptionMessage() {
        ResponseEntity<ErrorResponse> response =
                handler.handleException(new IllegalStateException("/etc/xroad/globalconf/DEV/shared-params.xml"));
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals("Internal server error", response.getBody().getMessage());
    }

    @Test
    void responseStatusExceptionKeepsItsStatusAndReason() {
        ResponseEntity<ErrorResponse> response = handler.handleResponseStatusException(
                new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, NOT_READY_REASON));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE.value(), response.getBody().getStatus());
        assertEquals("ServiceUnavailable", response.getBody().getError());
        assertEquals(NOT_READY_REASON, response.getBody().getMessage());
    }

    @Test
    void responseStatusExceptionKeepsClientErrorStatus() {
        ResponseEntity<ErrorResponse> response = handler.handleResponseStatusException(
                new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "slow down"));

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("TooManyRequests", response.getBody().getError());
        assertEquals("slow down", response.getBody().getMessage());
    }

    @Test
    void responseStatusExceptionWithoutReasonFallsBackToTheReasonPhrase() {
        ResponseEntity<ErrorResponse> response = handler.handleResponseStatusException(
                new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE));

        assertNotNull(response.getBody());
        assertEquals("Service Unavailable", response.getBody().getMessage());
    }

    @Test
    void responseStatusExceptionIsNotLoggedAsAnUnhandledError() {
        Logger logger = (Logger) LoggerFactory.getLogger(ApiExceptionHandler.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            handler.handleResponseStatusException(
                    new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, NOT_READY_REASON));
            assertTrue(appender.list.stream()
                            .noneMatch(event -> event.getLevel() == ch.qos.logback.classic.Level.ERROR),
                    "a not-ready 503 is expected operational state, not an ERROR-level fault");
        } finally {
            logger.detachAppender(appender);
        }
    }

    @Test
    void responseStatusExceptionHandlerWinsOverTheCatchAll() {
        // Spring resolves the most specific @ExceptionHandler; assert that here so the catch-all
        // cannot silently reclaim ResponseStatusException and turn intended statuses into 500s.
        ExceptionHandlerMethodResolver resolver = new ExceptionHandlerMethodResolver(ApiExceptionHandler.class);
        Method resolved = resolver.resolveMethod(
                new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, NOT_READY_REASON));

        assertNotNull(resolved);
        assertEquals("handleResponseStatusException", resolved.getName());
    }

    @Test
    void genericExceptionHandlerLogsTheException() {
        Logger logger = (Logger) LoggerFactory.getLogger(ApiExceptionHandler.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            IllegalStateException ex = new IllegalStateException("test error");
            handler.handleException(ex);
            assertEquals(1, appender.list.size(), "exactly one log event should be recorded");
            ILoggingEvent event = appender.list.get(0);
            assertEquals(ch.qos.logback.classic.Level.ERROR, event.getLevel());
            assertNotNull(event.getThrowableProxy(), "logged event should contain the exception");
        } finally {
            logger.detachAppender(appender);
        }
    }
}
