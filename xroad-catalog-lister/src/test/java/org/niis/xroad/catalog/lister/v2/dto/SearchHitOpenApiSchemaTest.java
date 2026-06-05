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

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "xroad-catalog.shared-params-file=src/test/resources/shared-params-dev-cs.xml",
        // The "test" profile disables SpringDoc by default
        // (xroad-catalog-lister/src/test/resources/application-test.yaml). Force it on for this test
        // so /v3/api-docs/v2 actually serves a document.
        "springdoc.api-docs.enabled=true"
})
@ActiveProfiles({"test", "general-testdata"})
class SearchHitOpenApiSchemaTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void searchHitSchemaIsPolymorphicWithDiscriminator() throws Exception {
        // Canonical OpenAPI 3 polymorphism: a discriminator on the base + allOf-extending subtypes.
        // A parallel `oneOf` on the same schema is redundant and creates a circular reference that
        // breaks Swagger UI's resolver ("Elements in allOf must be objects"), so it must NOT be present.
        mockMvc.perform(get("/v3/api-docs/v2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.schemas.SearchHit.oneOf").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.SearchHit.discriminator.propertyName").value("type"))
                .andExpect(jsonPath("$.components.schemas.SearchHit.discriminator.mapping.member").exists())
                .andExpect(jsonPath("$.components.schemas.SearchHit.discriminator.mapping.subsystem").exists())
                .andExpect(jsonPath("$.components.schemas.SearchHit.discriminator.mapping.service").exists())
                .andExpect(jsonPath("$.components.schemas.MemberSearchHit.allOf[0].$ref")
                        .value("#/components/schemas/SearchHit"))
                .andExpect(jsonPath("$.components.schemas.SubsystemSearchHit.allOf[0].$ref")
                        .value("#/components/schemas/SearchHit"))
                .andExpect(jsonPath("$.components.schemas.ServiceSearchHit.allOf[0].$ref")
                        .value("#/components/schemas/SearchHit"));
    }

    @Test
    void memberSearchHitProviderIsPrimitiveBoolean() throws Exception {
        // SpringDoc emits the per-subtype schema as allOf[ ref(SearchHit), {type:object, properties:{...}} ].
        // The field-level checks therefore live under allOf[1].properties — not at the top level.
        // The plan-suggested $.required[?(@=='provider')] check is omitted because SpringDoc does not
        // mark primitive booleans as "required" in this Jackson/SpringDoc combination; the absence of
        // "nullable: true" plus "type: boolean" is what enforces the no-Boolean-wrapper invariant.
        mockMvc.perform(get("/v3/api-docs/v2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(
                        "$.components.schemas.MemberSearchHit.allOf[1].properties.provider.type").value("boolean"))
                .andExpect(jsonPath(
                        "$.components.schemas.MemberSearchHit.allOf[1].properties.provider.nullable").doesNotExist());
    }
}
