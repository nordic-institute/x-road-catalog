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
package org.niis.xroad.catalog.lister.v2.converter;

import org.junit.jupiter.api.Test;
import org.niis.xroad.catalog.lister.v2.dto.ErrorLogDto;
import org.niis.xroad.catalog.persistence.entity.ErrorLog;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class ErrorLogConverterTest {

    private final ErrorLogConverter converter = new ErrorLogConverter();

    @Test
    void testConvertFullyPopulatedErrorLog() {
        LocalDateTime created = LocalDateTime.parse("2020-05-04T11:41:24");
        ErrorLog entity = ErrorLog.builder()
                .id(1L)
                .message("Fetch of WSDL failed")
                .code("500")
                .created(created)
                .xRoadInstance("DEV")
                .memberClass("GOV")
                .memberCode("1234")
                .subsystemCode("TestSubsystem")
                .serviceCode("testService")
                .serviceVersion("v1")
                .build();

        ErrorLogDto dto = converter.toDto(entity);

        assertEquals("Fetch of WSDL failed", dto.getMessage());
        assertEquals("500", dto.getCode());
        assertEquals("GOV", dto.getMemberClass());
        assertEquals("1234", dto.getMemberCode());
        assertEquals("TestSubsystem", dto.getSubsystemCode());
        assertEquals("testService", dto.getServiceCode());
        assertEquals("v1", dto.getServiceVersion());
        assertEquals(created, dto.getCreated());
    }

    @Test
    void testConvertPreservesNullServiceVersion() {
        ErrorLog entity = ErrorLog.builder()
                .id(2L)
                .message("Fetch of REST services failed")
                .code("500")
                .created(LocalDateTime.parse("2020-05-04T11:41:24"))
                .memberClass("GOV")
                .memberCode("1234")
                .subsystemCode("TestSubsystem")
                .serviceCode("restService")
                .serviceVersion(null)
                .build();

        ErrorLogDto dto = converter.toDto(entity);

        assertNull(dto.getServiceVersion());
        assertEquals("restService", dto.getServiceCode());
    }
}
