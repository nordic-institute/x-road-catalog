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

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class V2ResourceNotFoundExceptionTest {

    @Test
    void ofRendersSingleIdPart() {
        V2ResourceNotFoundException ex = V2ResourceNotFoundException.of("Member class", "GOV");
        assertThat(ex.getMessage()).isEqualTo("Member class 'GOV' not found");
    }

    @Test
    void ofJoinsMultipleIdPartsWithSlash() {
        V2ResourceNotFoundException ex = V2ResourceNotFoundException.of(
                "Service", "GOV", "1234", "SUBSYSTEM", "service");
        assertThat(ex.getMessage()).isEqualTo("Service 'GOV/1234/SUBSYSTEM/service' not found");
    }

    @Test
    void ofSkipsNullIdParts() {
        V2ResourceNotFoundException ex = V2ResourceNotFoundException.of(
                "Service version", "GOV", "1234", "SUBSYSTEM", "service", null);
        assertThat(ex.getMessage()).isEqualTo("Service version 'GOV/1234/SUBSYSTEM/service' not found");
    }

    @Test
    void ofSupportsMultiWordLabel() {
        V2ResourceNotFoundException ex = V2ResourceNotFoundException.of(
                "Descriptor for service version", "GOV", "1234", "SUBSYSTEM", "service", "v1");
        assertThat(ex.getMessage()).isEqualTo("Descriptor for service version 'GOV/1234/SUBSYSTEM/service/v1' not found");
    }
}
