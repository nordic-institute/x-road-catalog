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
package org.niis.xroad.catalog.lister.v2.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class InstanceContextTest {

    private static final String SHARED_PARAMS_XML =
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
            + "<conf xmlns=\"http://x-road.eu/xsd/xroad.xsd\">"
            + "<instanceIdentifier>DEV</instanceIdentifier>"
            + "</conf>";

    @Test
    void returnsInstanceIdWhenFileExists(@TempDir Path tmp) throws Exception {
        Path file = tmp.resolve("shared-params.xml");
        Files.writeString(file, SHARED_PARAMS_XML);

        InstanceContext context = new InstanceContext();
        ReflectionTestUtils.setField(context, "sharedParamsFile", file.toString());

        assertEquals("DEV", context.getCurrentInstance());
    }

    @Test
    void throwsServiceUnavailableWhenFileMissing(@TempDir Path tmp) {
        Path file = tmp.resolve("missing.xml");

        InstanceContext context = new InstanceContext();
        ReflectionTestUtils.setField(context, "sharedParamsFile", file.toString());

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                context::getCurrentInstance);
        assertEquals(503, ex.getStatusCode().value());
    }

    @Test
    void cachesValueAfterFirstSuccessfulLoad(@TempDir Path tmp) throws Exception {
        Path file = tmp.resolve("shared-params.xml");
        Files.writeString(file, SHARED_PARAMS_XML);

        InstanceContext context = new InstanceContext();
        ReflectionTestUtils.setField(context, "sharedParamsFile", file.toString());

        String first = context.getCurrentInstance();

        // Delete the file — a fresh load would now fail. The cached call must succeed.
        Files.delete(file);

        String second = context.getCurrentInstance();
        assertEquals("DEV", second);
        assertSame(first, second);
    }

    @Test
    void recoversAfterFileAppears(@TempDir Path tmp) throws Exception {
        Path file = tmp.resolve("shared-params.xml");

        InstanceContext context = new InstanceContext();
        ReflectionTestUtils.setField(context, "sharedParamsFile", file.toString());

        assertThrows(ResponseStatusException.class, context::getCurrentInstance);

        Files.writeString(file, SHARED_PARAMS_XML);

        assertEquals("DEV", context.getCurrentInstance());
    }
}
