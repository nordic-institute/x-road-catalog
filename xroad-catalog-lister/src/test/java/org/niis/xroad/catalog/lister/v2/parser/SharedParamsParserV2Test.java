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
import org.niis.xroad.catalog.lister.v2.dto.SecurityServerInfo;
import org.niis.xroad.catalog.lister.v2.dto.SubsystemNameInfo;
import org.niis.xroad.globalconf.model.SharedParametersV2;
import org.niis.xroad.globalconf.model.SharedParametersV3;
import org.niis.xroad.globalconf.model.SharedParametersV4;
import org.niis.xroad.globalconf.model.SharedParametersV5;
import org.xml.sax.SAXParseException;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SharedParamsParserV2Test {

    private static final String V5_FIXTURE = "src/test/resources/shared-params-2.xml";
    private static final String V5_FIXTURE_DEV_CS = "src/test/resources/shared-params-dev-cs.xml";
    private static final String V2_FIXTURE = "src/test/resources/shared-params.xml";
    private static final String UNSUPPORTED_FIXTURE = "src/test/resources/shared-params-unsupported.xml";
    private static final String XXE_FIXTURE = "src/test/resources/shared-params-xxe.xml";
    private static final String NO_SUPPORTED_VERSION_MESSAGE =
            "did not validate against any supported shared-parameters schema version";
    private static final List<String> NEWEST_FIRST_LADDER = List.of("V5", "V4", "V3", "V2");

    private final SharedParamsParserV2 parser = new SharedParamsParserV2();

    @Test
    void testParseMemberClasses() throws Exception {
        // shared-params-2.xml (schema V5) contains <globalSettings> with ORG, COM, GOV member classes
        List<MemberClassInfo> classes = parser.parse(V5_FIXTURE).memberClasses();
        assertEquals(3, classes.size());
        assertTrue(classes.stream().anyMatch(c -> "ORG".equals(c.getCode()) && c.getDescription().contains("Non-profit")));
        assertTrue(classes.stream().anyMatch(c -> "COM".equals(c.getCode())));
        assertTrue(classes.stream().anyMatch(c -> "GOV".equals(c.getCode())));
    }

    @Test
    void testParseSubsystemNames() throws Exception {
        // shared-params-2.xml only validates as schema V5, the only version whose XSD allows
        // <subsystemName>; it has at least one such element
        List<SubsystemNameInfo> names = parser.parse(V5_FIXTURE).subsystemNames();
        names.forEach(n -> {
            assertTrue(n.getMemberClass() != null && !n.getMemberClass().isBlank());
            assertTrue(n.getMemberCode() != null && !n.getMemberCode().isBlank());
            assertTrue(n.getSubsystemCode() != null && !n.getSubsystemCode().isBlank());
            assertTrue(n.getSubsystemName() != null && !n.getSubsystemName().isBlank());
        });
        assertFalse(names.isEmpty(), "test fixture should include at least one subsystemName");
    }

    @Test
    void testParseSubsystemNamesEmptyWhenV2Schema() throws Exception {
        // shared-params.xml only validates as schema V2, whose XSD has no <subsystemName> element
        assertEquals(0, parser.parse(V2_FIXTURE).subsystemNames().size());
    }

    @Test
    void testParseSubsystemNamesFromSecondV5Fixture() throws Exception {
        // shared-params-dev-cs.xml also only validates as schema V5; confirms the newest-first
        // fallback isn't accidentally tied to a single fixture
        List<SubsystemNameInfo> names = parser.parse(V5_FIXTURE_DEV_CS).subsystemNames();
        assertEquals(1, names.size());
        assertEquals("PUB", names.get(0).getMemberClass());
        assertEquals("14151328", names.get(0).getMemberCode());
        assertEquals("subsystem_a1", names.get(0).getSubsystemCode());
        assertEquals("Subsystem A1", names.get(0).getSubsystemName());
    }

    @Test
    void testParseInstanceIdentifier() throws Exception {
        assertEquals("dev-cs", parser.parseInstanceIdentifier(V5_FIXTURE_DEV_CS));
    }

    @Test
    void testParseThrowsWhenDocumentMatchesNoSupportedSchemaVersion() {
        // shared-params-unsupported.xml is well-formed but matches no V2-V5 schema; the parser must
        // fail loudly so SharedParamsCache can tell "not parseable" apart from "genuinely empty".
        IOException exception = assertThrows(IOException.class, () -> parser.parse(UNSUPPORTED_FIXTURE));
        assertTrue(exception.getMessage().contains(NO_SUPPORTED_VERSION_MESSAGE));
    }

    @Test
    void testParseRejectsDoctypeBearingDocumentBeforeReachingTheLibraryUnmarshaller() {
        // shared-params-xxe.xml declares a DOCTYPE with an external entity; the disallow-doctype-decl
        // pre-flight gate must reject it before the unhardened library unmarshaller sees the bytes.
        SAXParseException exception = assertThrows(SAXParseException.class, () -> parser.parse(XXE_FIXTURE));
        assertTrue(exception.getMessage().contains("DOCTYPE is disallowed"));
    }

    @Test
    void testParseSecurityServersResolvesOwnerAndSubsystemClients() throws Exception {
        // shared-params.xml has one securityServer (SS1) owned by member id0 (GOV/1234/ACME) with
        // two clients, both subsystems of the same owner: id1 (MANAGEMENT), id2 (TEST)
        List<SecurityServerInfo> servers = parser.parse(V2_FIXTURE).securityServers();

        assertEquals(1, servers.size());
        SecurityServerInfo ss1 = servers.get(0);
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
        // shared-params-2.xml has 4 Security Servers; niisss01 (owner ORG/2908758-4/NIIS) has two
        // subsystem clients, testagess01 has a single member-level client (COM/1234567-8).
        List<SecurityServerInfo> servers = parser.parse(V5_FIXTURE).securityServers();

        assertEquals(4, servers.size());

        SecurityServerInfo niisss01 = servers.stream().filter(s -> "niisss01".equals(s.serverCode()))
                .findFirst().orElseThrow();
        assertEquals("10.0.0.1", niisss01.address());
        assertEquals("ORG", niisss01.owner().memberClass());
        assertEquals("2908758-4", niisss01.owner().memberCode());
        assertEquals("NIIS", niisss01.owner().name());
        assertEquals(2, niisss01.clients().size());
        assertTrue(niisss01.clients().stream().anyMatch(c -> "Management".equals(c.subsystemCode())));
        assertTrue(niisss01.clients().stream().anyMatch(c -> "MonitoringClient".equals(c.subsystemCode())));

        SecurityServerInfo testagess01 = servers.stream().filter(s -> "testagess01".equals(s.serverCode()))
                .findFirst().orElseThrow();
        assertEquals(1, testagess01.clients().size());
        assertEquals("COM", testagess01.clients().get(0).memberClass());
        assertEquals("1234567-8", testagess01.clients().get(0).memberCode());
        assertNull(testagess01.clients().get(0).subsystemCode(), "a member-level client has no subsystemCode");
    }

    @Test
    void parseUnmarshalsTheDocumentOnceForAllThreeResultSets() throws Exception {
        List<String> attempts = new ArrayList<>();
        SharedParamsParserV2 counting = new SharedParamsParserV2(countingLadder(attempts));

        SharedParamsParserV2.ParsedSharedParams parsed = counting.parse(V5_FIXTURE);

        assertEquals(List.of("V5"), attempts, "one unmarshal per parse, not one per derived result set");
        assertEquals(3, parsed.memberClasses().size());
        assertFalse(parsed.subsystemNames().isEmpty());
        assertEquals(4, parsed.securityServers().size());
    }

    @Test
    void parseRemembersTheMatchingSchemaVersionAndSkipsTheFallbackNextTime() throws Exception {
        List<String> attempts = new ArrayList<>();
        SharedParamsParserV2 counting = new SharedParamsParserV2(countingLadder(attempts));

        counting.parse(V2_FIXTURE);
        assertEquals(NEWEST_FIRST_LADDER, attempts, "the first parse walks the newest-first ladder");

        attempts.clear();
        counting.parse(V2_FIXTURE);
        assertEquals(List.of("V2"), attempts, "the remembered version must be tried first and alone");
    }

    @Test
    void parseFallsBackToTheFullLadderWhenTheRememberedVersionStopsMatching() throws Exception {
        parser.parse(V5_FIXTURE);

        // a V2-schema document must still parse after V5 has been remembered
        List<SecurityServerInfo> servers = parser.parse(V2_FIXTURE).securityServers();
        assertEquals(1, servers.size());
        assertEquals("SS1", servers.get(0).serverCode());
    }

    @Test
    void parseKeepsThrowingForAnUnsupportedDocumentAndRemembersNoVersion() {
        List<String> attempts = new ArrayList<>();
        SharedParamsParserV2 counting = new SharedParamsParserV2(countingLadder(attempts));

        assertTrue(assertThrows(IOException.class, () -> counting.parse(UNSUPPORTED_FIXTURE))
                .getMessage().contains(NO_SUPPORTED_VERSION_MESSAGE));
        assertEquals(NEWEST_FIRST_LADDER, attempts);

        attempts.clear();
        assertTrue(assertThrows(IOException.class, () -> counting.parse(UNSUPPORTED_FIXTURE))
                .getMessage().contains(NO_SUPPORTED_VERSION_MESSAGE));
        assertEquals(NEWEST_FIRST_LADDER, attempts, "a failed parse must not remember a version");
    }

    @Test
    void theDoctypeGateStillRunsAfterAVersionHasBeenRemembered() throws Exception {
        List<String> attempts = new ArrayList<>();
        SharedParamsParserV2 counting = new SharedParamsParserV2(countingLadder(attempts));
        counting.parse(V5_FIXTURE);
        attempts.clear();

        SAXParseException exception = assertThrows(SAXParseException.class, () -> counting.parse(XXE_FIXTURE));

        assertTrue(exception.getMessage().contains("DOCTYPE is disallowed"));
        assertTrue(attempts.isEmpty(), "the gate must reject the document before any unmarshaller reads it");
    }

    @Test
    void parseInstanceIdentifierAlsoRunsTheDoctypeGate() {
        SAXParseException exception = assertThrows(SAXParseException.class,
                () -> parser.parseInstanceIdentifier(XXE_FIXTURE));
        assertTrue(exception.getMessage().contains("DOCTYPE is disallowed"));
    }

    private static List<SharedParamsParserV2.SchemaVersion> countingLadder(List<String> attempts) {
        return List.of(
                counting("V5", attempts, file -> new SharedParametersV5(file, OffsetDateTime.MAX).getSharedParameters()),
                counting("V4", attempts, file -> new SharedParametersV4(file, OffsetDateTime.MAX).getSharedParameters()),
                counting("V3", attempts, file -> new SharedParametersV3(file, OffsetDateTime.MAX).getSharedParameters()),
                counting("V2", attempts, file -> new SharedParametersV2(file, OffsetDateTime.MAX).getSharedParameters()));
    }

    private static SharedParamsParserV2.SchemaVersion counting(String name, List<String> attempts,
            SharedParamsParserV2.VersionUnmarshaller delegate) {
        return new SharedParamsParserV2.SchemaVersion(name, file -> {
            attempts.add(name);
            return delegate.unmarshal(file);
        });
    }
}
