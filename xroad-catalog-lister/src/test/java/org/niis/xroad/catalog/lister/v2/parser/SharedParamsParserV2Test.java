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
package org.niis.xroad.catalog.lister.v2.parser;

import org.junit.jupiter.api.Test;
import org.niis.xroad.catalog.lister.v2.dto.MemberClassInfo;
import org.niis.xroad.catalog.lister.v2.dto.SubsystemNameInfo;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SharedParamsParserV2Test {

    private final SharedParamsParserV2 parser = new SharedParamsParserV2();

    @Test
    void testParseMemberClasses() throws Exception {
        // shared-params-2.xml contains <globalSettings> with ORG, COM, GOV member classes
        List<MemberClassInfo> classes = parser.parseMemberClasses("src/test/resources/shared-params-2.xml");
        assertEquals(3, classes.size());
        assertTrue(classes.stream().anyMatch(c -> "ORG".equals(c.getCode()) && c.getDescription().contains("Non-profit")));
        assertTrue(classes.stream().anyMatch(c -> "COM".equals(c.getCode())));
        assertTrue(classes.stream().anyMatch(c -> "GOV".equals(c.getCode())));
    }

    @Test
    void testParseSubsystemNames() throws Exception {
        // After step 1, shared-params-2.xml has at least one <subsystemName> element
        List<SubsystemNameInfo> names = parser.parseSubsystemNames("src/test/resources/shared-params-2.xml");
        // Either empty (v4-style xml without <subsystemName>) or one-or-more entries
        names.forEach(n -> {
            assertTrue(n.getMemberClass() != null && !n.getMemberClass().isBlank());
            assertTrue(n.getMemberCode() != null && !n.getMemberCode().isBlank());
            assertTrue(n.getSubsystemCode() != null && !n.getSubsystemCode().isBlank());
            assertTrue(n.getSubsystemName() != null && !n.getSubsystemName().isBlank());
        });
        // The step-1 extension should ensure at least one entry here
        assertTrue(!names.isEmpty(), "test fixture should include at least one subsystemName");
    }

    @Test
    void testParseSubsystemNamesEmptyWhenV4Schema() throws Exception {
        // shared-params.xml (v4) has no <subsystemName> elements
        List<SubsystemNameInfo> names = parser.parseSubsystemNames("src/test/resources/shared-params.xml");
        assertEquals(0, names.size());
    }
}
