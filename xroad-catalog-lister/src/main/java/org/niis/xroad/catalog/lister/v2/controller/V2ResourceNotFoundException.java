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

import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Collectors;

public final class V2ResourceNotFoundException extends RuntimeException {

    private V2ResourceNotFoundException(String message) {
        super(message);
    }

    /**
     * Builds a {@code "<label> '<id>' not found"} message from the non-null {@code idParts} joined
     * with {@code "/"}; null parts are skipped so an absent segment does not render as "null".
     */
    @SuppressWarnings("PMD.ShortMethodName")
    public static V2ResourceNotFoundException of(String label, String... idParts) {
        String id = Arrays.stream(idParts)
                .filter(Objects::nonNull)
                .collect(Collectors.joining("/"));
        return new V2ResourceNotFoundException(label + " '" + id + "' not found");
    }
}
