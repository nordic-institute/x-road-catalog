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
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class V2ExceptionHandlerTest {

    private final V2ExceptionHandler handler = new V2ExceptionHandler();

    @Test
    void handleIllegalArgumentExceptionReturnsBadRequest() {
        ResponseEntity<ErrorResponse> response =
                handler.handleIllegalArgumentException(new IllegalArgumentException("invalid param"));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(HttpStatus.BAD_REQUEST.value(), response.getBody().getStatus());
        assertEquals("BadRequest", response.getBody().getError());
        assertEquals("invalid param", response.getBody().getMessage());
    }

    @Test
    void handleResourceNotFoundExceptionReturnsNotFound() {
        ResponseEntity<ErrorResponse> response =
                handler.handleResourceNotFoundException(V2ResourceNotFoundException.of("Widget", "abc"));

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(HttpStatus.NOT_FOUND.value(), response.getBody().getStatus());
        assertEquals("NotFound", response.getBody().getError());
        assertEquals("Widget 'abc' not found", response.getBody().getMessage());
    }

    @Test
    void handleMultipleVersionsExceptionReturnsConflictWithSortedVersions() {
        MultipleVersionsException ex = new MultipleVersionsException(
                "Service has multiple versions", Arrays.asList("v2", null, "v1"));

        ResponseEntity<ErrorResponse> response = handler.handleMultipleVersions(ex);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(HttpStatus.CONFLICT.value(), response.getBody().getStatus());
        assertEquals("Conflict", response.getBody().getError());
        assertEquals("Service has multiple versions", response.getBody().getMessage());
        assertEquals(Arrays.asList("v1", "v2", null), response.getBody().getVersions(),
                "versions must be sorted with nullsLast");
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
    void genericExceptionHandlerLogsTheException() {
        Logger logger = (Logger) LoggerFactory.getLogger(V2ExceptionHandler.class);
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
