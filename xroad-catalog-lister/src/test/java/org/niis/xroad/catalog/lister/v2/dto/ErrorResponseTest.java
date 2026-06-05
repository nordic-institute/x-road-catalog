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
package org.niis.xroad.catalog.lister.v2.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ErrorResponseTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void ofProducesCorrectFieldsAndSerializesToJson() throws Exception {
        var response = ErrorResponse.fromStatus(400, "BadRequest", "msg");

        assertEquals(400, response.getStatus());
        assertEquals("BadRequest", response.getError());
        assertEquals("msg", response.getMessage());
        assertNull(response.getVersions());

        String json = objectMapper.writeValueAsString(response);
        assertTrue(json.contains("\"status\":400"));
        assertTrue(json.contains("\"error\":\"BadRequest\""));
        assertTrue(json.contains("\"message\":\"msg\""));
        assertFalse(json.contains("versions"));
    }

    @Test
    void badRequestShortcut() {
        var response = ErrorResponse.badRequest("msg");

        assertEquals(400, response.getStatus());
        assertEquals("BadRequest", response.getError());
        assertEquals("msg", response.getMessage());
        assertNull(response.getVersions());
    }

    @Test
    void notFoundShortcut() {
        var response = ErrorResponse.notFound("msg");

        assertEquals(404, response.getStatus());
        assertEquals("NotFound", response.getError());
        assertEquals("msg", response.getMessage());
        assertNull(response.getVersions());
    }

    @Test
    void conflictIncludesVersionsList() throws Exception {
        List<String> versions = Arrays.asList("v1", null);
        var response = ErrorResponse.conflict("msg", versions);

        assertEquals(409, response.getStatus());
        assertEquals("Conflict", response.getError());
        assertEquals("msg", response.getMessage());
        assertEquals(versions, response.getVersions());

        String json = objectMapper.writeValueAsString(response);
        assertTrue(json.contains("\"versions\""));
    }
}
