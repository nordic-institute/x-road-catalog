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

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Service-aggregate row in {@code GET /api/v2/search}. {@code serviceTypes} aggregates all
 * descriptor kinds resolved across active versions (spec §3.2 example).
 *
 * @param memberClass   X-Road member class of the service-owning member
 * @param memberCode    X-Road member code of the service-owning member
 * @param memberName    display name of the service-owning member
 * @param subsystemCode X-Road subsystem code under which the service is published
 * @param serviceCode   X-Road service code (aggregate across versions)
 * @param serviceTypes  descriptor kinds resolved across active versions (e.g. SOAP, OPENAPI, REST)
 */
public record ServiceSearchHit(
        String memberClass,
        String memberCode,
        String memberName,
        String subsystemCode,
        String serviceCode,
        List<String> serviceTypes) implements SearchHit {

    @Override
    @JsonProperty("type")
    public String type() {
        return "service";
    }
}
