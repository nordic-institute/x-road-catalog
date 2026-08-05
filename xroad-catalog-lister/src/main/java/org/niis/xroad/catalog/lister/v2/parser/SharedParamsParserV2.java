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

import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.catalog.lister.v2.dto.MemberClassInfo;
import org.niis.xroad.catalog.lister.v2.dto.SecurityServerInfo;
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
import java.util.concurrent.atomic.AtomicReference;

/**
 * Parses X-Road global configuration shared-params XML via the {@code configuration-client}
 * library's JAXB model ({@link SharedParameters}).
 *
 * <p><b>Version handling:</b> the library has no version-detecting entry point, only one
 * unmarshaller class per schema version, each performing mandatory XSD validation on construction.
 * This parser tries the newest version first (V5) and falls back to V4, V3, V2; the strict
 * per-version validation guarantees a document for an unsupported schema throws rather than being
 * silently mis-parsed. The version that matched is remembered and tried first on the next parse, so
 * the fallback ladder is walked once rather than on every call; a remembered version that stops
 * matching (a Central Server upgrade) falls back to the full ladder again.
 *
 * <p><b>XXE hardening:</b> the library unmarshals through a plain, unhardened stream with no
 * DOCTYPE ban and no caller seam to inject a hardened {@code Source}, so every file is first run
 * through a disallow-doctype-decl parse purely to reject a DOCTYPE (the XXE vector) before the
 * library sees the bytes; that pre-flight parse result is discarded.
 */
@Slf4j
@Component
public class SharedParamsParserV2 {

    @FunctionalInterface
    interface VersionUnmarshaller {
        SharedParameters unmarshal(Path sharedParamsFile) throws CertificateEncodingException, IOException;
    }

    /**
     * One supported shared-parameters schema version.
     *
     * @param name         version label used in logs
     * @param unmarshaller library unmarshaller performing that version's XSD validation
     */
    record SchemaVersion(String name, VersionUnmarshaller unmarshaller) { }

    // Newest supported schema version first.
    private static final List<SchemaVersion> SUPPORTED_VERSIONS = List.of(
            new SchemaVersion("V5", file -> new SharedParametersV5(file, OffsetDateTime.MAX).getSharedParameters()),
            new SchemaVersion("V4", file -> new SharedParametersV4(file, OffsetDateTime.MAX).getSharedParameters()),
            new SchemaVersion("V3", file -> new SharedParametersV3(file, OffsetDateTime.MAX).getSharedParameters()),
            new SchemaVersion("V2", file -> new SharedParametersV2(file, OffsetDateTime.MAX).getSharedParameters()));

    /**
     * Everything the V2 read path derives from one shared-params document, all of it from a single
     * unmarshal.
     *
     * @param memberClasses   member classes from {@code globalSettings}
     * @param subsystemNames  subsystem display names, empty for schema versions without them
     * @param securityServers Security Servers with resolved owner and clients
     */
    public record ParsedSharedParams(List<MemberClassInfo> memberClasses, List<SubsystemNameInfo> subsystemNames,
            List<SecurityServerInfo> securityServers) { }

    private final List<SchemaVersion> supportedVersions;

    // Read on every parse, written when the matching version changes
    private final AtomicReference<SchemaVersion> rememberedVersion = new AtomicReference<>();

    public SharedParamsParserV2() {
        this(SUPPORTED_VERSIONS);
    }

    SharedParamsParserV2(List<SchemaVersion> supportedVersions) {
        this.supportedVersions = List.copyOf(supportedVersions);
    }

    public String parseInstanceIdentifier(String sharedParamsFile)
            throws ParserConfigurationException, IOException, SAXException {
        return unmarshal(sharedParamsFile).getInstanceIdentifier();
    }

    /**
     * Unmarshals the document once and derives every result set the caller needs from it.
     *
     * @throws IOException when the file cannot be read or does not validate against any supported
     *         schema version
     */
    public ParsedSharedParams parse(String sharedParamsFile)
            throws ParserConfigurationException, IOException, SAXException {
        SharedParameters params = unmarshal(sharedParamsFile);
        return new ParsedSharedParams(memberClasses(params), subsystemNames(params), securityServers(params));
    }

    private List<MemberClassInfo> memberClasses(SharedParameters params) {
        SharedParameters.GlobalSettings settings = params.getGlobalSettings();
        if (settings == null || settings.getMemberClasses() == null) {
            return List.of();
        }
        List<MemberClassInfo> classes = new ArrayList<>();
        for (SharedParameters.MemberClass memberClass : settings.getMemberClasses()) {
            classes.add(MemberClassInfo.builder().code(memberClass.getCode()).description(memberClass.getDescription()).build());
        }
        return classes;
    }

    private List<SubsystemNameInfo> subsystemNames(SharedParameters params) {
        List<SubsystemNameInfo> names = new ArrayList<>();
        for (SharedParameters.Member member : nullToEmpty(params.getMembers())) {
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
     * One {@link SecurityServerInfo} per {@code securityServer} element. Owner and clients are
     * already-resolved {@link ClientId}s in the library model; only the owner's display name needs a
     * lookup against the member list.
     */
    private List<SecurityServerInfo> securityServers(SharedParameters params) {
        Map<String, SharedParameters.Member> membersByKey = new HashMap<>();
        for (SharedParameters.Member member : nullToEmpty(params.getMembers())) {
            if (member.getId() != null) {
                membersByKey.put(keyOf(member.getId()), member);
            }
        }
        List<SecurityServerInfo> result = new ArrayList<>();
        for (SharedParameters.SecurityServer server : nullToEmpty(params.getSecurityServers())) {
            result.add(toSecurityServerInfo(server, membersByKey));
        }
        return result;
    }

    private SecurityServerInfo toSecurityServerInfo(SharedParameters.SecurityServer server,
            Map<String, SharedParameters.Member> membersByKey) {
        SecurityServerInfo.MemberRef owner = ownerRef(server.getOwner(), membersByKey);
        List<SecurityServerInfo.ClientRef> clients = new ArrayList<>();
        for (ClientId clientId : nullToEmpty(server.getClients())) {
            clients.add(new SecurityServerInfo.ClientRef(clientId.getMemberClass(), clientId.getMemberCode(),
                    clientId.getSubsystemCode()));
        }
        return new SecurityServerInfo(server.getServerCode(), server.getAddress(), owner, clients);
    }

    private SecurityServerInfo.MemberRef ownerRef(ClientId owner, Map<String, SharedParameters.Member> membersByKey) {
        if (owner == null) {
            return new SecurityServerInfo.MemberRef(null, null, null);
        }
        SharedParameters.Member member = membersByKey.get(keyOf(owner));
        return new SecurityServerInfo.MemberRef(owner.getMemberClass(), owner.getMemberCode(),
                member != null ? member.getName() : null);
    }

    private static String keyOf(ClientId id) {
        return id.getMemberClass() + "|" + id.getMemberCode();
    }

    private static <T> List<T> nullToEmpty(List<T> list) {
        return list != null ? list : List.of();
    }

    /**
     * Runs the security gate, then the remembered version and, only if that no longer matches, the
     * rest of the newest-first ladder. Each XSD-validating attempt reads the whole file, so the
     * remembered version keeps a steady-state parse at one attempt.
     */
    private SharedParameters unmarshal(String sharedParamsFile) throws ParserConfigurationException, IOException, SAXException {
        Path path = Path.of(sharedParamsFile);
        rejectUnsafeXml(path);

        SchemaVersion remembered = rememberedVersion.get();
        Exception lastFailure = null;
        if (remembered != null) {
            try {
                return remembered.unmarshaller().unmarshal(path);
            } catch (CertificateEncodingException | IOException | RuntimeException e) {
                lastFailure = e;
            }
        }
        for (SchemaVersion candidate : supportedVersions) {
            if (candidate.equals(remembered)) {
                continue;
            }
            try {
                SharedParameters params = candidate.unmarshaller().unmarshal(path);
                log.info("Shared-params file {} matched schema version {}", sharedParamsFile, candidate.name());
                rememberedVersion.set(candidate);
                return params;
            } catch (CertificateEncodingException | IOException | RuntimeException e) {
                lastFailure = e;
            }
        }
        throw new IOException("Shared-params file " + sharedParamsFile + " did not validate against any "
                + "supported shared-parameters schema version (V2-V5)", lastFailure);
    }

    /**
     * Hardened, DOCTYPE-rejecting parse used purely as a security gate; the resulting document is
     * discarded before the library's own unhardened unmarshalling reads the file.
     */
    private void rejectUnsafeXml(Path sharedParamsFile) throws ParserConfigurationException, IOException, SAXException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.newDocumentBuilder().parse(sharedParamsFile.toFile());
    }
}
