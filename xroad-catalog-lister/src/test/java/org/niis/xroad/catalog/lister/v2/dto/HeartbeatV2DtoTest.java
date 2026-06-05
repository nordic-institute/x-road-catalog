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
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import static org.assertj.core.api.Assertions.assertThat;

class HeartbeatV2DtoTest {

    private static final LocalDateTime FETCHED = LocalDateTime.of(2026, 4, 10, 11, 30);
    private static final LocalDateTime SYSTEM_TIME = LocalDateTime.of(2026, 4, 10, 12, 0);

    private static String expected(LocalDateTime ldt) {
        return ldt.atZone(ZoneId.systemDefault()).toOffsetDateTime()
                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }

    @Test
    void serializedTimestampsAreExactIsoOffsetStrings() throws Exception {
        LastCollectionDataV2Dto last = LastCollectionDataV2Dto.builder()
                .membersLastFetched(FETCHED).subsystemsLastFetched(FETCHED)
                .servicesLastFetched(FETCHED).wsdlsLastFetched(FETCHED)
                .openapisLastFetched(FETCHED).restsLastFetched(FETCHED)
                .build();
        HeartbeatV2Dto hb = HeartbeatV2Dto.builder()
                .appWorking(Boolean.TRUE).dbWorking(Boolean.TRUE)
                .appName("X-Road Catalog Lister").appVersion("3.0.0")
                .systemTime(SYSTEM_TIME).lastCollectionData(last).lastRunErrors(3L)
                .build();

        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        String json = mapper.writeValueAsString(hb);

        // Each timestamp serializes to the exact ISO_OFFSET_DATE_TIME computed for the JVM
        // default zone — pins both the format and the per-field assignment.
        assertThat(json).contains("\"systemTime\":\"" + expected(SYSTEM_TIME) + "\"");
        assertThat(json).contains("\"membersLastFetched\":\"" + expected(FETCHED) + "\"");
        assertThat(json).contains("\"subsystemsLastFetched\":\"" + expected(FETCHED) + "\"");
        assertThat(json).contains("\"servicesLastFetched\":\"" + expected(FETCHED) + "\"");
        assertThat(json).contains("\"wsdlsLastFetched\":\"" + expected(FETCHED) + "\"");
        assertThat(json).contains("\"openapisLastFetched\":\"" + expected(FETCHED) + "\"");
        assertThat(json).contains("\"restsLastFetched\":\"" + expected(FETCHED) + "\"");

        // Defense-in-depth: no field renders as a bare LocalDateTime (no offset).
        assertThat(json).doesNotContainPattern("\"\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\"");
    }

    @Test
    void serializedNullTimestampsRenderAsJsonNull() throws Exception {
        // The @JsonSerialize annotation does not break null handling — Jackson short-circuits
        // null values to JSON null before invoking the configured custom serializer.
        HeartbeatV2Dto hb = HeartbeatV2Dto.builder()
                .appWorking(Boolean.TRUE).dbWorking(Boolean.TRUE)
                .appName("X-Road Catalog Lister").appVersion("3.0.0")
                .systemTime(null)
                .lastCollectionData(LastCollectionDataV2Dto.builder().build())
                .lastRunErrors(0L)
                .build();

        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        String json = mapper.writeValueAsString(hb);

        assertThat(json).contains("\"systemTime\":null");
        assertThat(json).contains("\"membersLastFetched\":null");
        assertThat(json).contains("\"subsystemsLastFetched\":null");
        assertThat(json).contains("\"servicesLastFetched\":null");
        assertThat(json).contains("\"wsdlsLastFetched\":null");
        assertThat(json).contains("\"openapisLastFetched\":null");
        assertThat(json).contains("\"restsLastFetched\":null");
    }
}
