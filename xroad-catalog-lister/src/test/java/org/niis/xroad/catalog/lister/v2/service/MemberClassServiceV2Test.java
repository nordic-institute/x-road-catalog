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
package org.niis.xroad.catalog.lister.v2.service;

import org.junit.jupiter.api.Test;
import org.niis.xroad.catalog.lister.v2.dto.MemberClassDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@TestPropertySource(properties = {"xroad-catalog.shared-params-file=src/test/resources/shared-params-2.xml"})
@ActiveProfiles({"test", "general-testdata"})
public class MemberClassServiceV2Test {

    @Autowired
    private MemberClassServiceV2 memberClassService;

    @Test
    public void testListMemberClassesIncludesPubMemberCount() {
        List<MemberClassDto> classes = memberClassService.list();
        assertNotNull(classes);
        assertFalse(classes.isEmpty());
        boolean pubPresent = classes.stream().anyMatch(c -> "PUB".equals(c.getCode()) && c.getMemberCount() > 0);
        assertTrue(pubPresent, "PUB class present in DB must appear in listing with positive memberCount");
    }

    @Test
    public void testGetByCodeReturnsMatchingClass() {
        MemberClassDto pub = memberClassService.getByCode("PUB");
        assertNotNull(pub, "PUB class should be returned by getByCode");
        assertEquals("PUB", pub.getCode());
        assertTrue(pub.getMemberCount() > 0, "PUB must have a positive member count");
    }

    @Test
    public void testGetByCodeReturnsNullForUnknownCode() {
        assertNull(memberClassService.getByCode("DOES-NOT-EXIST"));
    }

    @Test
    public void testGetByCodeReturnsNullForNullInput() {
        assertNull(memberClassService.getByCode(null));
    }
}
