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

import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.catalog.lister.v2.dto.ErrorResponse;
import org.niis.xroad.catalog.lister.v2.exception.BadRequestException;
import org.niis.xroad.catalog.lister.v2.exception.MultipleVersionsException;
import org.niis.xroad.catalog.lister.v2.exception.ResourceNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

@Slf4j
@RestControllerAdvice(basePackages = "org.niis.xroad.catalog.lister.v2")
public class ApiExceptionHandler {

    /**
     * Only V2 validation failures are echoed as 400. An unexpected {@link IllegalArgumentException}
     * has no handler of its own, so the catch-all below answers 500 without leaking its message.
     */
    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ErrorResponse> handleBadRequest(BadRequestException ex) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.badRequest(ex.getMessage()));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParam(MissingServletRequestParameterException ex) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.badRequest("Query parameter '" + ex.getParameterName() + "' is required"));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        String message = "Invalid value for query parameter '" + ex.getName() + "': '"
                + (ex.getValue() == null ? "" : ex.getValue()) + "'";
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.badRequest(message));
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<ErrorResponse> handleBindException(BindException ex) {
        // Surface the first field error; the full BindingResult stays in logs. Global-only errors
        // collapse to the generic fallback — no V2 route raises object-level errors.
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(fe -> "Invalid value for parameter '" + fe.getField() + "': "
                        + (fe.getRejectedValue() == null ? "null" : fe.getRejectedValue()))
                .orElse("Invalid request parameter");
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.badRequest(message));
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleResourceNotFoundException(ResourceNotFoundException ex) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.notFound(ex.getMessage()));
    }

    @ExceptionHandler(MultipleVersionsException.class)
    public ResponseEntity<ErrorResponse> handleMultipleVersions(MultipleVersionsException ex) {
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(ErrorResponse.conflict(ex.getMessage(), ex.getVersions()));
    }

    /**
     * Keeps the status a {@link ResponseStatusException} carries. Without this the
     * {@link Exception} catch-all below would claim it — {@code ExceptionHandlerExceptionResolver}
     * runs before {@code ResponseStatusExceptionResolver} — turning deliberate signals such as the
     * 503 raised while shared-params.xml is not yet readable into a 500.
     */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatusException(ResponseStatusException ex) {
        HttpStatusCode statusCode = ex.getStatusCode();
        HttpStatus resolved = HttpStatus.resolve(statusCode.value());
        String reason = reasonOf(ex, resolved);
        if (statusCode.is5xxServerError()) {
            // Expected operational state (e.g. the startup window before the first global-conf sync),
            // so no stack trace and not ERROR level; the raising site logs the diagnostic context.
            log.warn("V2 request could not be served ({}): {}", statusCode.value(), reason);
        } else {
            log.debug("V2 request rejected ({}): {}", statusCode.value(), reason);
        }
        return ResponseEntity
                .status(statusCode)
                .body(ErrorResponse.fromStatus(statusCode.value(), errorCodeOf(resolved), reason));
    }

    private static String errorCodeOf(HttpStatus status) {
        if (status == null) {
            return "Error";
        }
        return status.getReasonPhrase().replace(" ", "");
    }

    private static String reasonOf(ResponseStatusException ex, HttpStatus resolved) {
        if (ex.getReason() != null) {
            return ex.getReason();
        }
        if (resolved != null) {
            return resolved.getReasonPhrase();
        }
        return "Request failed";
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(Exception ex) {
        log.error("Unhandled exception serving V2 request", ex);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.fromStatus(
                        HttpStatus.INTERNAL_SERVER_ERROR.value(),
                        "InternalServerError",
                        "Internal server error"));
    }
}
