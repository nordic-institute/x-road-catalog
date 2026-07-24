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
import org.niis.xroad.catalog.lister.v2.dto.SecurityServerInfoV2;
import org.niis.xroad.catalog.lister.v2.dto.SubsystemNameInfo;
import org.xml.sax.SAXParseException;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SharedParamsParserV2Test {

    private final SharedParamsParserV2 parser = new SharedParamsParserV2();

    @Test
    void testParseMemberClasses() throws Exception {
        // shared-params-2.xml (schema V5) contains <globalSettings> with ORG, COM, GOV member classes
        List<MemberClassInfo> classes = parser.parseMemberClasses("src/test/resources/shared-params-2.xml");
        assertEquals(3, classes.size());
        assertTrue(classes.stream().anyMatch(c -> "ORG".equals(c.getCode()) && c.getDescription().contains("Non-profit")));
        assertTrue(classes.stream().anyMatch(c -> "COM".equals(c.getCode())));
        assertTrue(classes.stream().anyMatch(c -> "GOV".equals(c.getCode())));
    }

    @Test
    void testParseSubsystemNames() throws Exception {
        // shared-params-2.xml only validates as schema V5, the only version whose XSD allows
        // <subsystemName>; it has at least one such element
        List<SubsystemNameInfo> names = parser.parseSubsystemNames("src/test/resources/shared-params-2.xml");
        names.forEach(n -> {
            assertTrue(n.getMemberClass() != null && !n.getMemberClass().isBlank());
            assertTrue(n.getMemberCode() != null && !n.getMemberCode().isBlank());
            assertTrue(n.getSubsystemCode() != null && !n.getSubsystemCode().isBlank());
            assertTrue(n.getSubsystemName() != null && !n.getSubsystemName().isBlank());
        });
        assertTrue(!names.isEmpty(), "test fixture should include at least one subsystemName");
    }

    @Test
    void testParseSubsystemNamesEmptyWhenV2Schema() throws Exception {
        // shared-params.xml only validates as schema V2, whose XSD has no <subsystemName> element
        List<SubsystemNameInfo> names = parser.parseSubsystemNames("src/test/resources/shared-params.xml");
        assertEquals(0, names.size());
    }

    @Test
    void testParseSubsystemNamesFromSecondV5Fixture() throws Exception {
        // shared-params-dev-cs.xml also only validates as schema V5; confirms the newest-first
        // fallback isn't accidentally tied to a single fixture
        List<SubsystemNameInfo> names = parser.parseSubsystemNames("src/test/resources/shared-params-dev-cs.xml");
        assertEquals(1, names.size());
        assertEquals("PUB", names.get(0).getMemberClass());
        assertEquals("14151328", names.get(0).getMemberCode());
        assertEquals("subsystem_a1", names.get(0).getSubsystemCode());
        assertEquals("Subsystem A1", names.get(0).getSubsystemName());
    }

    @Test
    void testParseThrowsWhenDocumentMatchesNoSupportedSchemaVersion() {
        // shared-params-unsupported.xml is well-formed XML but doesn't conform to any of the
        // V2-V5 shared-parameters schemas; the parser must fail loudly rather than silently
        // returning empty/partial data, so SharedParamsCache can tell "not parseable" apart from
        // "genuinely empty". Assert the specific exception thrown once the newest-first V5->V2
        // fallback is exhausted, not just "some exception", so an incidental failure (e.g. a
        // missing fixture file) can't slip past this test.
        IOException exception = assertThrows(IOException.class,
                () -> parser.parseMemberClasses("src/test/resources/shared-params-unsupported.xml"));
        assertTrue(exception.getMessage().contains("did not validate against any supported shared-parameters schema version"));
    }

    @Test
    void testParseRejectsDoctypeBearingDocumentBeforeReachingTheLibraryUnmarshaller() {
        // shared-params-xxe.xml declares a DOCTYPE with an external entity; the pre-flight,
        // disallow-doctype-decl gate must reject it before configuration-client's own (unhardened)
        // unmarshalling path ever sees the bytes. Assert the specific SAXParseException/message the
        // gate produces, proving the rejection came from the gate and not, say, one of the library's
        // own schema unmarshallers rejecting the DOCTYPE for an unrelated reason.
        SAXParseException exception = assertThrows(SAXParseException.class,
                () -> parser.parseMemberClasses("src/test/resources/shared-params-xxe.xml"));
        assertTrue(exception.getMessage().contains("DOCTYPE is disallowed"));
    }

    @Test
    void testParseSecurityServersResolvesOwnerAndSubsystemClients() throws Exception {
        // shared-params.xml has one securityServer (SS1) owned by member id0 (GOV/1234/ACME) with
        // two clients, both subsystems of the same owner: id1 (MANAGEMENT), id2 (TEST)
        List<SecurityServerInfoV2> servers = parser.parseSecurityServers("src/test/resources/shared-params.xml");

        assertEquals(1, servers.size());
        SecurityServerInfoV2 ss1 = servers.get(0);
        assertEquals("SS1", ss1.serverCode());
        assertEquals("10.18.150.48", ss1.address());
        assertEquals("GOV", ss1.owner().memberClass());
        assertEquals("1234", ss1.owner().memberCode());
        assertEquals("ACME", ss1.owner().name());

        assertEquals(2, ss1.clients().size());
        assertTrue(ss1.clients().stream().allMatch(c -> "GOV".equals(c.memberClass()) && "1234".equals(c.memberCode())));
        assertTrue(ss1.clients().stream().anyMatch(c -> "MANAGEMENT".equals(c.subsystemCode())));
        assertTrue(ss1.clients().stream().anyMatch(c -> "TEST".equals(c.subsystemCode())));
    }

    @Test
    void testParseSecurityServersResolvesMemberAndSubsystemClientsFromLargerFixture() throws Exception {
        // shared-params-2.xml has 4 security servers; niisss01 is owned by member id0
        // (ORG/2908758-4/NIIS) with clients id3 (Management subsystem) and id2 (MonitoringClient
        // subsystem); testagess01 is owned by id10 with a single member-level client id6 (COM/1234567-8).
        List<SecurityServerInfoV2> servers = parser.parseSecurityServers("src/test/resources/shared-params-2.xml");

        assertEquals(4, servers.size());

        SecurityServerInfoV2 niisss01 = servers.stream().filter(s -> "niisss01".equals(s.serverCode()))
                .findFirst().orElseThrow();
        assertEquals("10.0.0.1", niisss01.address());
        assertEquals("ORG", niisss01.owner().memberClass());
        assertEquals("2908758-4", niisss01.owner().memberCode());
        assertEquals("NIIS", niisss01.owner().name());
        assertEquals(2, niisss01.clients().size());
        assertTrue(niisss01.clients().stream().anyMatch(c -> "Management".equals(c.subsystemCode())));
        assertTrue(niisss01.clients().stream().anyMatch(c -> "MonitoringClient".equals(c.subsystemCode())));

        SecurityServerInfoV2 testagess01 = servers.stream().filter(s -> "testagess01".equals(s.serverCode()))
                .findFirst().orElseThrow();
        assertEquals(1, testagess01.clients().size());
        assertEquals("COM", testagess01.clients().get(0).memberClass());
        assertEquals("1234567-8", testagess01.clients().get(0).memberCode());
        assertNull(testagess01.clients().get(0).subsystemCode(), "a member-level client has no subsystemCode");
    }
}
