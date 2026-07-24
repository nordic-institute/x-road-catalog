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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import org.niis.xroad.catalog.lister.v2.configuration.JacksonV2Configuration;
import org.niis.xroad.catalog.persistence.entity.StatusInfo;
import org.niis.xroad.catalog.persistence.v2entity.EndpointV2;
import org.niis.xroad.catalog.persistence.v2entity.ServiceV2;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * One active version of a service. {@code serviceType} is {@code SOAP}, {@code OPENAPI},
 * {@code REST} or {@code UNKNOWN} — the latter meaning the service has been collected but not yet
 * classified, which resolves within one collector interval. {@code hasDescriptor} is {@code true}
 * only for SOAP and OPENAPI.
 */
@Getter
@Builder
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.ALWAYS)
public class ServiceVersionDto {
    private static final String SOAP_SERVICE_TYPE = "SOAP";
    private static final String OPENAPI_SERVICE_TYPE = "OPENAPI";

    private final String serviceVersion;
    private final String serviceType;
    private final boolean hasDescriptor;
    private final List<EndpointDto> endpoints;
    @JsonSerialize(using = JacksonV2Configuration.OffsetLocalDateTimeSerializer.class)
    private final LocalDateTime created;
    @JsonSerialize(using = JacksonV2Configuration.OffsetLocalDateTimeSerializer.class)
    private final LocalDateTime changed;
    @JsonSerialize(using = JacksonV2Configuration.OffsetLocalDateTimeSerializer.class)
    private final LocalDateTime fetched;
    @JsonSerialize(using = JacksonV2Configuration.OffsetLocalDateTimeSerializer.class)
    private final LocalDateTime removed;

    /**
     * {@code serviceType} is read straight from the denormalized column maintained by the collector
     * recompute (a descriptor fetched mid-collection-cycle surfaces after the next recompute — an
     * accepted staleness window, during which the type reads {@code UNKNOWN}). {@code hasDescriptor}
     * is asserted only for the two types that carry one: SOAP has a WSDL, OPENAPI has an OpenAPI
     * document. REST is the settled descriptor-less type and UNKNOWN is not yet classified, so both
     * report {@code false} rather than claiming a descriptor that may not exist.
     */
    public static ServiceVersionDto from(ServiceV2 service) {
        String serviceType = service.getServiceType();
        List<EndpointDto> endpoints = new ArrayList<>();
        for (EndpointV2 e : service.getActiveEndpoints()) {
            endpoints.add(EndpointDto.builder()
                    .method(e.getMethod())
                    .path(e.getPath())
                    .removed(e.getStatusInfo().getRemoved())
                    .build());
        }
        StatusInfo info = service.getStatusInfo();
        return ServiceVersionDto.builder()
                .serviceVersion(service.getServiceVersion())
                .serviceType(serviceType)
                .hasDescriptor(SOAP_SERVICE_TYPE.equals(serviceType) || OPENAPI_SERVICE_TYPE.equals(serviceType))
                .endpoints(endpoints)
                .created(info.getCreated())
                .changed(info.getChanged())
                .fetched(info.getFetched())
                .removed(info.getRemoved())
                .build();
    }
}
