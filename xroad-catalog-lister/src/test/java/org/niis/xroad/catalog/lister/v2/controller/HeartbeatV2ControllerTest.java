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

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.niis.xroad.catalog.lister.v2.dto.CurrentRunDto;
import org.niis.xroad.catalog.lister.v2.dto.HeartbeatV2Dto;
import org.niis.xroad.catalog.lister.v2.dto.LastCollectionDataV2Dto;
import org.niis.xroad.catalog.lister.v2.service.HeartbeatServiceV2;
import org.niis.xroad.catalog.testsupport.lister.v2.RequestIdFilterTestConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(HeartbeatV2Controller.class)
@Import({V2ExceptionHandler.class, V2DispatchExceptionHandler.class, RequestIdFilterTestConfig.class})
class HeartbeatV2ControllerTest {

    private static final String HEARTBEAT_PATH = "/api/v2/heartbeat";
    private static final String JSON_ERROR = "$.error";
    private static final String JSON_STATUS = "$.status";
    private static final String JSON_MESSAGE = "$.message";
    private static final String METHOD_NOT_ALLOWED_ERROR = "MethodNotAllowed";
    private static final String NOT_ACCEPTABLE_ERROR = "NotAcceptable";

    private static final LocalDateTime FETCHED = LocalDateTime.of(2026, 4, 10, 11, 30);
    private static final LocalDateTime SYSTEM_TIME = LocalDateTime.of(2026, 4, 10, 12, 0);
    private static final LocalDateTime STARTED = LocalDateTime.of(2026, 4, 10, 13, 0);
    private static final LocalDateTime PROGRESS_UPDATED = LocalDateTime.of(2026, 4, 10, 13, 25);
    private static final LocalDateTime CONF_EXPIRES = LocalDateTime.of(2026, 4, 11, 12, 0);

    private static String expectedOffset(LocalDateTime ldt) {
        return ldt.atZone(ZoneId.systemDefault()).toOffsetDateTime()
                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private HeartbeatServiceV2 heartbeatService;

    private HeartbeatV2Dto fullyPopulated() {
        LastCollectionDataV2Dto last = LastCollectionDataV2Dto.builder()
                .membersLastFetched(FETCHED).subsystemsLastFetched(FETCHED)
                .servicesLastFetched(FETCHED).wsdlsLastFetched(FETCHED)
                .openapisLastFetched(FETCHED).restsLastFetched(FETCHED)
                .build();
        return HeartbeatV2Dto.builder()
                .appWorking(Boolean.TRUE).dbWorking(Boolean.TRUE)
                .appName("X-Road Catalog Lister").appVersion("3.0.0")
                .systemTime(SYSTEM_TIME).lastCollectionData(last).lastRunErrors(3L)
                .globalConfExpiresAt(CONF_EXPIRES)
                .build();
    }

    @Test
    void heartbeatReturnsFullPayloadWithExactOffsetTimestamps() throws Exception {
        when(heartbeatService.heartbeat()).thenReturn(fullyPopulated());

        mockMvc.perform(get(HEARTBEAT_PATH))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.appWorking").value(true))
                .andExpect(jsonPath("$.dbWorking").value(true))
                .andExpect(jsonPath("$.appName").value("X-Road Catalog Lister"))
                .andExpect(jsonPath("$.appVersion").value("3.0.0"))
                .andExpect(jsonPath("$.systemTime").value(expectedOffset(SYSTEM_TIME)))
                .andExpect(jsonPath("$.lastCollectionData.membersLastFetched").value(expectedOffset(FETCHED)))
                .andExpect(jsonPath("$.lastCollectionData.subsystemsLastFetched").value(expectedOffset(FETCHED)))
                .andExpect(jsonPath("$.lastCollectionData.servicesLastFetched").value(expectedOffset(FETCHED)))
                .andExpect(jsonPath("$.lastCollectionData.wsdlsLastFetched").value(expectedOffset(FETCHED)))
                .andExpect(jsonPath("$.lastCollectionData.openapisLastFetched").value(expectedOffset(FETCHED)))
                .andExpect(jsonPath("$.lastCollectionData.restsLastFetched").value(expectedOffset(FETCHED)))
                .andExpect(jsonPath("$.lastRunErrors").value(3))
                .andExpect(jsonPath("$.globalConfExpired").value(false))
                .andExpect(jsonPath("$.globalConfExpiresAt").value(expectedOffset(CONF_EXPIRES)))
                .andExpect(jsonPath("$.currentRun").value(Matchers.nullValue()));

        verify(heartbeatService).heartbeat();
        verifyNoMoreInteractions(heartbeatService);
    }

    @Test
    void heartbeatIncludesCurrentRunWithOffsetTimestampsWhenCycleInProgress() throws Exception {
        CurrentRunDto currentRun = CurrentRunDto.builder()
                .started(STARTED).pendingItems(37).progressUpdated(PROGRESS_UPDATED)
                .build();
        HeartbeatV2Dto hb = fullyPopulated();
        hb.setCurrentRun(currentRun);
        when(heartbeatService.heartbeat()).thenReturn(hb);

        mockMvc.perform(get(HEARTBEAT_PATH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentRun.started").value(expectedOffset(STARTED)))
                .andExpect(jsonPath("$.currentRun.pendingItems").value(37))
                .andExpect(jsonPath("$.currentRun.progressUpdated").value(expectedOffset(PROGRESS_UPDATED)));
    }

    @Test
    void heartbeatRendersCurrentRunAsJsonNullWhenNoCycleInProgress() throws Exception {
        when(heartbeatService.heartbeat()).thenReturn(fullyPopulated());

        mockMvc.perform(get(HEARTBEAT_PATH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentRun").value(Matchers.nullValue()))
                // Pin "key present, value null" distinctly from the key being absent altogether.
                .andExpect(content().string(Matchers.containsString("\"currentRun\":null")));
    }

    @Test
    void expiredGlobalConfIsReportedButNeverCauses503() throws Exception {
        HeartbeatV2Dto hb = fullyPopulated();
        hb.setGlobalConfExpired(true);
        when(heartbeatService.heartbeat()).thenReturn(hb);

        mockMvc.perform(get(HEARTBEAT_PATH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.globalConfExpired").value(true))
                .andExpect(jsonPath("$.globalConfExpiresAt").value(expectedOffset(CONF_EXPIRES)));
    }

    @Test
    void unknownGlobalConfExpiryRendersFalseAndJsonNull() throws Exception {
        HeartbeatV2Dto hb = fullyPopulated();
        hb.setGlobalConfExpiresAt(null);
        when(heartbeatService.heartbeat()).thenReturn(hb);

        mockMvc.perform(get(HEARTBEAT_PATH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.globalConfExpired").value(false))
                .andExpect(jsonPath("$.globalConfExpiresAt").value(Matchers.nullValue()))
                .andExpect(content().string(Matchers.containsString("\"globalConfExpiresAt\":null")));
    }

    @Test
    void heartbeatReturns503WhenDbDown() throws Exception {
        HeartbeatV2Dto unhealthy = fullyPopulated();
        unhealthy.setDbWorking(Boolean.FALSE);
        when(heartbeatService.heartbeat()).thenReturn(unhealthy);

        mockMvc.perform(get(HEARTBEAT_PATH))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.dbWorking").value(false))
                .andExpect(jsonPath("$.appWorking").value(true));
    }

    @Test
    void heartbeatReturns503WhenAppNotWorking() throws Exception {
        HeartbeatV2Dto unhealthy = fullyPopulated();
        unhealthy.setAppWorking(Boolean.FALSE);
        when(heartbeatService.heartbeat()).thenReturn(unhealthy);

        mockMvc.perform(get(HEARTBEAT_PATH))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.appWorking").value(false));
    }

    @Test
    void heartbeatReturns200WhenHealthy() throws Exception {
        when(heartbeatService.heartbeat()).thenReturn(fullyPopulated());

        mockMvc.perform(get(HEARTBEAT_PATH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dbWorking").value(true))
                .andExpect(jsonPath("$.appWorking").value(true));
    }

    @Test
    void heartbeatExposesAllSixLastFetchedFieldsAsJsonNullBeforeFirstCollection() throws Exception {
        // When any *LastFetched is null (first run) lastRunErrors is 0; all six LastCollectionData
        // fields plus systemTime render as JSON null so the client can show the empty state.
        HeartbeatV2Dto hb = HeartbeatV2Dto.builder()
                .appWorking(Boolean.TRUE).dbWorking(Boolean.TRUE)
                .appName("X-Road Catalog Lister").appVersion("3.0.0")
                .systemTime(null)
                .lastCollectionData(LastCollectionDataV2Dto.builder().build())
                .lastRunErrors(0L)
                .build();
        when(heartbeatService.heartbeat()).thenReturn(hb);

        mockMvc.perform(get(HEARTBEAT_PATH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lastRunErrors").value(0))
                .andExpect(jsonPath("$.systemTime").value(Matchers.nullValue()))
                .andExpect(jsonPath("$.lastCollectionData.membersLastFetched").value(Matchers.nullValue()))
                .andExpect(jsonPath("$.lastCollectionData.subsystemsLastFetched").value(Matchers.nullValue()))
                .andExpect(jsonPath("$.lastCollectionData.servicesLastFetched").value(Matchers.nullValue()))
                .andExpect(jsonPath("$.lastCollectionData.wsdlsLastFetched").value(Matchers.nullValue()))
                .andExpect(jsonPath("$.lastCollectionData.openapisLastFetched").value(Matchers.nullValue()))
                .andExpect(jsonPath("$.lastCollectionData.restsLastFetched").value(Matchers.nullValue()))
                // Pin "key present, value null" by inspecting raw JSON for one timestamp from
                // each level — distinguishes JSON null from the key being absent altogether.
                .andExpect(content().string(Matchers.containsString("\"systemTime\":null")))
                .andExpect(content().string(Matchers.containsString("\"membersLastFetched\":null")))
                .andExpect(content().string(Matchers.containsString("\"restsLastFetched\":null")));
    }

    @Test
    void heartbeatIgnoresExtraQueryParams() throws Exception {
        // Heartbeat takes no query parameters; extras are silently ignored and the service
        // is invoked exactly once regardless.
        when(heartbeatService.heartbeat()).thenReturn(fullyPopulated());

        mockMvc.perform(get(HEARTBEAT_PATH)
                        .param("since", "2026-01-01")
                        .param("includeRemoved", "true")
                        .param("page", "5"))
                .andExpect(status().isOk());

        verify(heartbeatService).heartbeat();
        verifyNoMoreInteractions(heartbeatService);
    }

    @Test
    void postToHeartbeatReturns405() throws Exception {
        mockMvc.perform(post(HEARTBEAT_PATH))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath(JSON_STATUS).value(405))
                .andExpect(jsonPath(JSON_ERROR).value(METHOD_NOT_ALLOWED_ERROR))
                .andExpect(jsonPath(JSON_MESSAGE, Matchers.containsString("'POST'")))
                .andExpect(header().string("Allow", "GET"));
    }

    @Test
    void putToHeartbeatReturns405() throws Exception {
        mockMvc.perform(put(HEARTBEAT_PATH))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath(JSON_STATUS).value(405))
                .andExpect(jsonPath(JSON_ERROR).value(METHOD_NOT_ALLOWED_ERROR))
                .andExpect(header().string("Allow", "GET"));
    }

    @Test
    void deleteToHeartbeatReturns405() throws Exception {
        mockMvc.perform(delete(HEARTBEAT_PATH))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath(JSON_STATUS).value(405))
                .andExpect(jsonPath(JSON_ERROR).value(METHOD_NOT_ALLOWED_ERROR))
                .andExpect(header().string("Allow", "GET"));
    }

    @Test
    void postToHeartbeatUnderContextPathReturns405() throws Exception {
        // The dispatch handler must path-discriminate /api/v2/* even when the app is deployed
        // under a non-root context (e.g. /catalog/api/v2/heartbeat).
        mockMvc.perform(post("/catalog" + HEARTBEAT_PATH).contextPath("/catalog"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath(JSON_STATUS).value(405))
                .andExpect(jsonPath(JSON_ERROR).value(METHOD_NOT_ALLOWED_ERROR))
                .andExpect(header().string("Allow", "GET"));
    }

    @Test
    void unsupportedAcceptHeaderReturns406WithV2ErrorEnvelope() throws Exception {
        // HttpMediaTypeNotAcceptableException maps to a V2 ErrorResponse on /api/v2/* paths.
        when(heartbeatService.heartbeat()).thenReturn(fullyPopulated());

        mockMvc.perform(get(HEARTBEAT_PATH).accept(MediaType.TEXT_PLAIN))
                .andExpect(status().isNotAcceptable())
                .andExpect(jsonPath(JSON_STATUS).value(406))
                .andExpect(jsonPath(JSON_ERROR).value(NOT_ACCEPTABLE_ERROR))
                .andExpect(jsonPath(JSON_MESSAGE, Matchers.containsString("application/json")));
    }

    @Test
    void postWithUnsupportedAcceptHeaderReturns405WithV2ErrorEnvelope() throws Exception {
        // The 405 response must carry the V2 envelope even when Accept excludes JSON; without an
        // explicit Content-Type the converter re-negotiates against Accept: text/plain and drops the body.
        mockMvc.perform(post(HEARTBEAT_PATH).accept(MediaType.TEXT_PLAIN))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath(JSON_STATUS).value(405))
                .andExpect(jsonPath(JSON_ERROR).value(METHOD_NOT_ALLOWED_ERROR))
                .andExpect(jsonPath(JSON_MESSAGE, Matchers.containsString("'POST'")))
                .andExpect(header().string("Allow", "GET"));
    }

    @Test
    void heartbeatResponseCarriesServerGeneratedRequestId() throws Exception {
        when(heartbeatService.heartbeat()).thenReturn(fullyPopulated());
        mockMvc.perform(get(HEARTBEAT_PATH))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Request-Id", "fixed-id-from-test"));
    }

    @Test
    void heartbeatIgnoresInboundRequestIdAndUsesServerGenerated() throws Exception {
        // Defense-in-depth: client-supplied X-Request-Id is never trusted, never echoed,
        // never logged. The server's own generated value is what reaches the response.
        when(heartbeatService.heartbeat()).thenReturn(fullyPopulated());
        mockMvc.perform(get(HEARTBEAT_PATH).header("X-Request-Id", "trace-abc-123"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Request-Id", "fixed-id-from-test"));
    }
}
