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

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies that every {@code @PathVariable("serviceVersion")} on V2 browse controllers ships an
 * {@code @Parameter} description documenting the {@code "null"} sentinel literal in the generated
 * OpenAPI document. Without this, third-party clients reading the spec have no way of knowing that
 * the literal {@code "null"} URL segment maps to a Java null on the server.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "xroad-catalog.shared-params-file=src/test/resources/shared-params-dev-cs.xml",
        "springdoc.api-docs.enabled=true"
})
@ActiveProfiles({"test", "general-testdata"})
class BrowseServiceVersionOpenApiSchemaTest {

    private static final String VERSION_PATH = "/api/v2/browse/member-classes/{memberClass}/members/{memberCode}"
            + "/subsystems/{subsystemCode}/services/{serviceCode}/versions/{serviceVersion}";
    private static final String VERSION_DESCRIPTOR_PATH = VERSION_PATH + "/descriptor";
    private static final String VERSION_ERRORS_PATH = VERSION_PATH + "/errors";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void browseServiceVersionParameterDescriptionDocumentsNullSentinel() throws Exception {
        mockMvc.perform(get("/v3/api-docs/v2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(
                        "$.paths['" + VERSION_PATH + "'].get.parameters[?(@.name=='serviceVersion')].description")
                        .value(Matchers.hasItem(Matchers.containsString("\"null\""))));
    }

    @Test
    void browseServiceVersionDescriptorParameterDescriptionDocumentsNullSentinel() throws Exception {
        mockMvc.perform(get("/v3/api-docs/v2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(
                        "$.paths['" + VERSION_DESCRIPTOR_PATH + "']"
                                + ".get.parameters[?(@.name=='serviceVersion')].description")
                        .value(Matchers.hasItem(Matchers.containsString("\"null\""))));
    }

    @Test
    void browseServiceVersionErrorsParameterDescriptionDocumentsNullSentinel() throws Exception {
        mockMvc.perform(get("/v3/api-docs/v2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(
                        "$.paths['" + VERSION_ERRORS_PATH + "']"
                                + ".get.parameters[?(@.name=='serviceVersion')].description")
                        .value(Matchers.hasItem(Matchers.containsString("\"null\""))));
    }
}
