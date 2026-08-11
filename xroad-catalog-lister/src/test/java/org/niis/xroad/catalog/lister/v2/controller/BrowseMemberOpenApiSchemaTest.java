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

import static org.hamcrest.Matchers.containsInAnyOrder;
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
class BrowseMemberOpenApiSchemaTest {

    private static final String API_DOCS_V2 = "/v3/api-docs/v2";
    private static final String MEMBER_RESPONSE_SCHEMA =
            "$.paths['/api/v2/browse/member-classes/{memberClass}/members/{memberCode}']"
                    + ".get.responses['200'].content['application/json'].schema";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void getMemberDeclaresBothResponseShapes() throws Exception {
        mockMvc.perform(get(API_DOCS_V2))
                .andExpect(status().isOk())
                .andExpect(jsonPath(MEMBER_RESPONSE_SCHEMA + ".oneOf[*].$ref").value(containsInAnyOrder(
                        "#/components/schemas/MemberDto",
                        "#/components/schemas/FullMemberDto")))
                .andExpect(jsonPath("$.components.schemas.MemberDto").exists())
                .andExpect(jsonPath("$.components.schemas.FullMemberDto").exists());
    }
}
