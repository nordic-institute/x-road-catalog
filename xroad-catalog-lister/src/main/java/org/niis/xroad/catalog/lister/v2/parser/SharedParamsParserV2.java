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

import ee.ria.xroad.common.identifier.ClientId;

import org.niis.xroad.catalog.lister.v2.dto.MemberClassInfo;
import org.niis.xroad.catalog.lister.v2.dto.SecurityServerInfoV2;
import org.niis.xroad.catalog.lister.v2.dto.SubsystemNameInfo;
import org.niis.xroad.globalconf.model.SharedParameters;
import org.niis.xroad.globalconf.model.SharedParametersV2;
import org.niis.xroad.globalconf.model.SharedParametersV3;
import org.niis.xroad.globalconf.model.SharedParametersV4;
import org.niis.xroad.globalconf.model.SharedParametersV5;
import org.springframework.stereotype.Component;
import org.xml.sax.SAXException;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.nio.file.Path;
import java.security.cert.CertificateEncodingException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Parses X-Road global configuration shared-params XML using the {@code configuration-client}
 * library's JAXB model ({@link SharedParameters}) instead of hand-rolled DOM traversal.
 *
 * <p><b>Version handling:</b> the library has no version-detecting entry point, only one
 * unmarshaller class per schema version ({@link SharedParametersV2}..{@link SharedParametersV5}),
 * each performing mandatory, non-bypassable XSD validation on construction. This parser tries the
 * newest version first (V5) and falls back to V4, V3, V2; that strict per-version validation is
 * what guarantees a document written for a newer/unsupported schema throws instead of being
 * silently mis-parsed. {@code SharedParamsCache} caches such a failure as empty for its TTL.
 *
 * <p><b>XXE hardening:</b> the library's own unmarshalling reads the file through a plain,
 * unhardened {@code FileInputStream}/{@code StreamSource} with no DOCTYPE ban and no caller seam
 * to inject an already-hardened {@code Source}. So every file is first run through the same
 * disallow-doctype-decl gate the previous DOM-based parser used, purely to reject a DOCTYPE (the
 * XXE vector) before the library ever sees the bytes; that pre-flight parse result is discarded.
 */
@Component
public class SharedParamsParserV2 {

    @FunctionalInterface
    private interface VersionUnmarshaller {
        SharedParameters unmarshal(Path sharedParamsFile) throws CertificateEncodingException, IOException;
    }

    // Newest supported schema version first; see the version-handling section of the class javadoc.
    private static final List<VersionUnmarshaller> VERSION_UNMARSHALLERS = List.of(
            file -> new SharedParametersV5(file, OffsetDateTime.MAX).getSharedParameters(),
            file -> new SharedParametersV4(file, OffsetDateTime.MAX).getSharedParameters(),
            file -> new SharedParametersV3(file, OffsetDateTime.MAX).getSharedParameters(),
            file -> new SharedParametersV2(file, OffsetDateTime.MAX).getSharedParameters());

    public List<MemberClassInfo> parseMemberClasses(String sharedParamsFile)
            throws ParserConfigurationException, IOException, SAXException {
        SharedParameters.GlobalSettings settings = unmarshal(sharedParamsFile).getGlobalSettings();
        if (settings == null || settings.getMemberClasses() == null) {
            return List.of();
        }
        List<MemberClassInfo> classes = new ArrayList<>();
        for (SharedParameters.MemberClass memberClass : settings.getMemberClasses()) {
            classes.add(MemberClassInfo.builder().code(memberClass.getCode()).description(memberClass.getDescription()).build());
        }
        return classes;
    }

    public List<SubsystemNameInfo> parseSubsystemNames(String sharedParamsFile)
            throws ParserConfigurationException, IOException, SAXException {
        List<SubsystemNameInfo> names = new ArrayList<>();
        for (SharedParameters.Member member : nullToEmpty(unmarshal(sharedParamsFile).getMembers())) {
            String memberClass = member.getMemberClass() != null ? member.getMemberClass().getCode() : null;
            for (SharedParameters.Subsystem subsystem : nullToEmpty(member.getSubsystems())) {
                String subsystemName = subsystem.getSubsystemName();
                if (subsystemName != null && !subsystemName.isBlank()) {
                    names.add(SubsystemNameInfo.builder()
                            .memberClass(memberClass)
                            .memberCode(member.getMemberCode())
                            .subsystemCode(subsystem.getSubsystemCode())
                            .subsystemName(subsystemName)
                            .build());
                }
            }
        }
        return names;
    }

    /**
     * Parses security server information from X-Road global configuration shared-params.xml. The
     * owner and each client of a security server are already-resolved {@link ClientId}s in the
     * library model, so — unlike the previous DOM-based implementation — no id-attribute
     * cross-referencing is needed for them; only the owner's display name requires a lookup
     * against the member list.
     *
     * @return list of {@link SecurityServerInfoV2} objects, one per {@code securityServer} element
     * @throws ParserConfigurationException when there are issues with parsing the file
     * @throws IOException                  when unable to read input file, or when the file does
     *                                       not validate against any supported schema version
     * @throws SAXException                 when there are issues with parsing of XML
     */
    public List<SecurityServerInfoV2> parseSecurityServers(String sharedParamsFile)
            throws ParserConfigurationException, IOException, SAXException {
        SharedParameters params = unmarshal(sharedParamsFile);
        Map<String, SharedParameters.Member> membersByKey = new HashMap<>();
        for (SharedParameters.Member member : nullToEmpty(params.getMembers())) {
            if (member.getId() != null) {
                membersByKey.put(keyOf(member.getId()), member);
            }
        }
        List<SecurityServerInfoV2> result = new ArrayList<>();
        for (SharedParameters.SecurityServer server : nullToEmpty(params.getSecurityServers())) {
            result.add(toSecurityServerInfo(server, membersByKey));
        }
        return result;
    }

    private SecurityServerInfoV2 toSecurityServerInfo(SharedParameters.SecurityServer server,
            Map<String, SharedParameters.Member> membersByKey) {
        SecurityServerInfoV2.MemberRef owner = ownerRef(server.getOwner(), membersByKey);
        List<SecurityServerInfoV2.ClientRef> clients = new ArrayList<>();
        for (ClientId clientId : nullToEmpty(server.getClients())) {
            clients.add(new SecurityServerInfoV2.ClientRef(clientId.getMemberClass(), clientId.getMemberCode(),
                    clientId.getSubsystemCode()));
        }
        return new SecurityServerInfoV2(server.getServerCode(), server.getAddress(), owner, clients);
    }

    private SecurityServerInfoV2.MemberRef ownerRef(ClientId owner, Map<String, SharedParameters.Member> membersByKey) {
        if (owner == null) {
            return new SecurityServerInfoV2.MemberRef(null, null, null);
        }
        SharedParameters.Member member = membersByKey.get(keyOf(owner));
        return new SecurityServerInfoV2.MemberRef(owner.getMemberClass(), owner.getMemberCode(),
                member != null ? member.getName() : null);
    }

    private static String keyOf(ClientId id) {
        return id.getMemberClass() + "|" + id.getMemberCode();
    }

    private static <T> List<T> nullToEmpty(List<T> list) {
        return list != null ? list : List.of();
    }

    /**
     * Unmarshals a shared-params XML file into the library's version-agnostic
     * {@link SharedParameters} model. The file is first run through {@link #rejectUnsafeXml} as
     * an XXE gate, then handed to each of {@link #VERSION_UNMARSHALLERS} newest-first until one
     * accepts it; see the class javadoc for why both steps exist.
     *
     * <p>Each public parse method calls this independently, so it repeats its own newest-first
     * fallback rather than sharing a cached result; a cold cache refresh can therefore attempt
     * several schema validations. Acceptable because {@code SharedParamsCache} TTL-caches and
     * single-flights the refresh off the request path.
     */
    private SharedParameters unmarshal(String sharedParamsFile) throws ParserConfigurationException, IOException, SAXException {
        Path path = Path.of(sharedParamsFile);
        rejectUnsafeXml(path);

        Exception lastFailure = null;
        for (VersionUnmarshaller attempt : VERSION_UNMARSHALLERS) {
            try {
                return attempt.unmarshal(path);
            } catch (CertificateEncodingException | IOException | RuntimeException e) {
                lastFailure = e;
            }
        }
        throw new IOException("Shared-params file " + sharedParamsFile + " did not validate against any "
                + "supported shared-parameters schema version (V2-V5)", lastFailure);
    }

    /**
     * Runs the raw file through the same hardened, DOCTYPE-rejecting parse the previous DOM-based
     * implementation used, purely as a security gate — the resulting {@link org.w3c.dom.Document}
     * is discarded. See the XXE-hardening section of the class javadoc for why this exists
     * alongside the library's own, separately-invoked unmarshalling.
     */
    private void rejectUnsafeXml(Path sharedParamsFile) throws ParserConfigurationException, IOException, SAXException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.newDocumentBuilder().parse(sharedParamsFile.toFile());
    }
}
