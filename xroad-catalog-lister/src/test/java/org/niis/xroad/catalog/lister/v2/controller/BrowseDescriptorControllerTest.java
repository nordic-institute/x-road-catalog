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
import org.niis.xroad.catalog.lister.v2.dto.DescriptorPayload;
import org.niis.xroad.catalog.lister.v2.service.ServiceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Optional;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(BrowseDescriptorController.class)
@Import(ApiExceptionHandler.class)
class BrowseDescriptorControllerTest {

    private static final String PUB = "PUB";
    private static final String CODE_14151328 = "14151328";
    private static final String SUBSYSTEM_A1 = "subsystem_a1";
    private static final String SERVICE_MIXED = "mixedSvc";
    private static final String YAML = "application/yaml";

    private static final String VERSION_PATH = "/api/v2/browse/member-classes/PUB/members/14151328"
            + "/subsystems/subsystem_a1/services/mixedSvc/versions/v1/descriptor";
    private static final String SERVICE_PATH = "/api/v2/browse/member-classes/PUB/members/14151328"
            + "/subsystems/subsystem_a1/services/mixedSvc/descriptor";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ServiceService serviceService;

    @Test
    void versionLevelWsdlReturnsXml() throws Exception {
        DescriptorPayload payload = new DescriptorPayload(
                "<wsdl/>".getBytes(StandardCharsets.UTF_8), MediaType.APPLICATION_XML);
        when(serviceService.getVersionDescriptor(PUB, CODE_14151328, SUBSYSTEM_A1, SERVICE_MIXED, "v1"))
                .thenReturn(Optional.of(payload));

        mockMvc.perform(get(VERSION_PATH))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/xml"))
                .andExpect(content().bytes("<wsdl/>".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void versionLevelOpenApiJsonReturnsJson() throws Exception {
        DescriptorPayload payload = new DescriptorPayload(
                "{\"openapi\":\"3.0.0\"}".getBytes(StandardCharsets.UTF_8), MediaType.APPLICATION_JSON);
        when(serviceService.getVersionDescriptor(PUB, CODE_14151328, SUBSYSTEM_A1, SERVICE_MIXED, "v1"))
                .thenReturn(Optional.of(payload));

        mockMvc.perform(get(VERSION_PATH))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/json"));
    }

    @Test
    void versionLevelOpenApiYamlReturnsYaml() throws Exception {
        DescriptorPayload payload = new DescriptorPayload(
                "openapi: 3.0.0\n".getBytes(StandardCharsets.UTF_8), MediaType.parseMediaType(YAML));
        when(serviceService.getVersionDescriptor(PUB, CODE_14151328, SUBSYSTEM_A1, SERVICE_MIXED, "v1"))
                .thenReturn(Optional.of(payload));

        mockMvc.perform(get(VERSION_PATH))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", YAML));
    }

    @Test
    void versionLevelRestOnlyReturns404() throws Exception {
        when(serviceService.getVersionDescriptor(PUB, CODE_14151328, SUBSYSTEM_A1, SERVICE_MIXED, "v1"))
                .thenReturn(Optional.empty());

        mockMvc.perform(get(VERSION_PATH))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("NotFound"));
    }

    @Test
    void versionLevelMissingVersionReturns404() throws Exception {
        when(serviceService.getVersionDescriptor(PUB, CODE_14151328, SUBSYSTEM_A1, SERVICE_MIXED, "v99"))
                .thenReturn(Optional.empty());

        mockMvc.perform(get(VERSION_PATH.replace("/versions/v1/", "/versions/v99/")))
                .andExpect(status().isNotFound());
    }

    @Test
    void versionLevelOnlyRemovedWsdlReturns404() throws Exception {
        // A removed WSDL yields an empty Optional (no active descriptor), hence 404.
        when(serviceService.getVersionDescriptor(PUB, CODE_14151328, SUBSYSTEM_A1,
                "descRemovedWsdlSvc", "v1")).thenReturn(Optional.empty());

        mockMvc.perform(get(VERSION_PATH
                        .replace("/services/mixedSvc/", "/services/descRemovedWsdlSvc/")))
                .andExpect(status().isNotFound());
    }

    @Test
    void versionLevelNullSentinelPassesRawToService() throws Exception {
        // The service layer resolves "null" to a null version internally; the controller is a passthrough.
        DescriptorPayload payload = new DescriptorPayload(
                "{}".getBytes(StandardCharsets.UTF_8), MediaType.APPLICATION_JSON);
        when(serviceService.getVersionDescriptor(PUB, CODE_14151328, SUBSYSTEM_A1, SERVICE_MIXED, "null"))
                .thenReturn(Optional.of(payload));

        mockMvc.perform(get(VERSION_PATH.replace("/versions/v1/", "/versions/null/")))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/json"));
    }

    @Test
    void serviceLevelSingleVersionReturnsDescriptor() throws Exception {
        DescriptorPayload payload = new DescriptorPayload(
                "<wsdl/>".getBytes(StandardCharsets.UTF_8), MediaType.APPLICATION_XML);
        when(serviceService.getServiceLevelDescriptor(PUB, CODE_14151328, SUBSYSTEM_A1, SERVICE_MIXED))
                .thenReturn(Optional.of(payload));

        mockMvc.perform(get(SERVICE_PATH))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/xml"))
                .andExpect(content().bytes("<wsdl/>".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void serviceLevelNoVersionsReturns404() throws Exception {
        when(serviceService.getServiceLevelDescriptor(PUB, CODE_14151328, SUBSYSTEM_A1, SERVICE_MIXED))
                .thenReturn(Optional.empty());

        mockMvc.perform(get(SERVICE_PATH))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("NotFound"));
    }

    @Test
    void serviceLevelSingleVersionWithoutDescriptorReturns404() throws Exception {
        // Service layer returns an empty Optional for both "no versions" and "1 version, no active
        // descriptor" — both must surface as 404 from the controller.
        when(serviceService.getServiceLevelDescriptor(PUB, CODE_14151328, SUBSYSTEM_A1,
                "descRestOnlySvc")).thenReturn(Optional.empty());

        mockMvc.perform(get(SERVICE_PATH.replace("/services/mixedSvc/", "/services/descRestOnlySvc/")))
                .andExpect(status().isNotFound());
    }

    @Test
    void serviceLevelMultipleVersionsReturns409() throws Exception {
        when(serviceService.getServiceLevelDescriptor(PUB, CODE_14151328, SUBSYSTEM_A1, SERVICE_MIXED))
                .thenThrow(new MultipleVersionsException(
                        "Service has multiple versions; pick a specific version via /versions/{serviceVersion}/descriptor",
                        Arrays.asList("v2", "v1")));

        mockMvc.perform(get(SERVICE_PATH))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.versions[0]").value("v2"))
                .andExpect(jsonPath("$.versions[1]").value("v1"));
    }

    @Test
    void serviceLevelMultipleVersionsWithNullPassesVersionsThrough() throws Exception {
        when(serviceService.getServiceLevelDescriptor(PUB, CODE_14151328, SUBSYSTEM_A1, SERVICE_MIXED))
                .thenThrow(new MultipleVersionsException(
                        "Service has multiple versions",
                        Arrays.asList(null, "v1")));

        mockMvc.perform(get(SERVICE_PATH))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.versions[0]").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.versions[1]").value("v1"));
    }
}
