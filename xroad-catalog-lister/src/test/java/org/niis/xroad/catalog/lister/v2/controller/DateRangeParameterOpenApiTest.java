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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "xroad-catalog.shared-params-file=src/test/resources/shared-params-dev-cs.xml",
        // The "test" profile disables SpringDoc by default; force it on so /v3/api-docs/v2
        // actually serves a document.
        "springdoc.api-docs.enabled=true"
})
@ActiveProfiles({"test", "general-testdata"})
class DateRangeParameterOpenApiTest {

    private static final String API_DOCS_V2 = "/v3/api-docs/v2";
    private static final String EXCLUSIVE = "exclusive";
    private static final String STATS_PATH = "$.paths['/api/v2/reports/service-statistics'].get.parameters";
    private static final String CHANGES_PATH = "$.paths['/api/v2/reports/changes'].get.parameters";
    private static final String ERRORS_PATH = "$.paths['/api/v2/browse/errors'].get.parameters";
    private static final String UNTIL_DESCRIPTION = "[?(@.name=='until')].description";
    private static final String SINCE_DESCRIPTION = "[?(@.name=='since')].description";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void dateWindowParametersDocumentTheExclusiveUntilCutoff() throws Exception {
        mockMvc.perform(get(API_DOCS_V2))
                .andExpect(status().isOk())
                .andExpect(jsonPath(STATS_PATH + UNTIL_DESCRIPTION).value(hasItem(containsString(EXCLUSIVE))))
                .andExpect(jsonPath(CHANGES_PATH + UNTIL_DESCRIPTION).value(hasItem(containsString(EXCLUSIVE))))
                .andExpect(jsonPath(ERRORS_PATH + UNTIL_DESCRIPTION).value(hasItem(containsString(EXCLUSIVE))))
                .andExpect(jsonPath(STATS_PATH + SINCE_DESCRIPTION).value(hasItem(containsString("inclusive"))))
                .andExpect(jsonPath(CHANGES_PATH + SINCE_DESCRIPTION).value(hasItem(containsString("inclusive"))))
                .andExpect(jsonPath(ERRORS_PATH + SINCE_DESCRIPTION).value(hasItem(containsString("inclusive"))));
    }

    @Test
    void untilDescriptionSpellsOutThatTheNamedDayIsExcluded() throws Exception {
        mockMvc.perform(get(API_DOCS_V2))
                .andExpect(status().isOk())
                .andExpect(jsonPath(STATS_PATH + UNTIL_DESCRIPTION)
                        .value(hasItem(containsString("is not included"))))
                .andExpect(jsonPath(ERRORS_PATH + UNTIL_DESCRIPTION)
                        .value(hasItem(containsString("is not included"))));
    }

    @Test
    void dateWindowParametersDocumentDefaultsAndTheRangeCap() throws Exception {
        mockMvc.perform(get(API_DOCS_V2))
                .andExpect(status().isOk())
                .andExpect(jsonPath(STATS_PATH + SINCE_DESCRIPTION).value(hasItem(containsString("90 days"))))
                .andExpect(jsonPath(ERRORS_PATH + SINCE_DESCRIPTION).value(hasItem(containsString("90 days"))))
                .andExpect(jsonPath(STATS_PATH + UNTIL_DESCRIPTION).value(hasItem(containsString("Defaults to"))))
                .andExpect(jsonPath(ERRORS_PATH + UNTIL_DESCRIPTION).value(hasItem(containsString("Defaults to"))));
    }
}
