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

import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.catalog.lister.v2.converter.SubsystemNameLookup;
import org.niis.xroad.catalog.lister.v2.dto.MemberClassInfo;
import org.niis.xroad.catalog.lister.v2.dto.SecurityServerInfoV2;
import org.niis.xroad.catalog.lister.v2.dto.SubsystemNameInfo;
import org.niis.xroad.catalog.lister.v2.parser.SharedParamsParserV2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Caches the shared-params XML parse results for a fixed TTL so per-request enrichment (subsystem
 * names, member class descriptions, security servers) does not re-parse the file on every call.
 *
 * <p>The 60s TTL matches the global-conf refresh cadence — polling more often than that is
 * pointless since the underlying file cannot have changed. A single immutable {@link Snapshot} is
 * held behind a {@code volatile} field; refreshing swaps in a brand new snapshot rather than
 * mutating one in place, so a reader never observes a partially-built result. Refreshing happens
 * under a {@code synchronized} block with a double-checked expiry test, so concurrent callers past
 * the TTL trigger at most one re-parse, and a parse failure is cached as an empty snapshot for the
 * remainder of the TTL window rather than being retried on every subsequent call.
 */
@Slf4j
@Service
public class SharedParamsCache {

    private static final int TTL_SECONDS = 60;
    private static final Duration TTL = Duration.ofSeconds(TTL_SECONDS);

    private final SharedParamsParserV2 parser;
    private final Clock clock;
    private final String sharedParamsFile;

    // volatile is intentional: it publishes a fully-built immutable Snapshot to all readers without
    // requiring them to take the refresh() lock — see the class javadoc for the full argument.
    @SuppressWarnings("PMD.AvoidUsingVolatile")
    private volatile Snapshot snapshot = Snapshot.empty(Instant.MIN);

    public SharedParamsCache(SharedParamsParserV2 parser, Clock clock,
            @Value("${xroad-catalog.shared-params-file}") String sharedParamsFile) {
        this.parser = parser;
        this.clock = clock;
        this.sharedParamsFile = sharedParamsFile;
    }

    public SubsystemNameLookup subsystemNames() {
        return current().subsystemNames();
    }

    public Map<String, String> memberClassDescriptions() {
        return current().memberClassDescriptions();
    }

    public Set<String> memberClassCodes() {
        return current().memberClassCodes();
    }

    /**
     * Joint read of descriptions and codes from a single {@link Snapshot}, so callers that need
     * both never risk sourcing them from two different parses across a TTL expiry.
     */
    public MemberClasses memberClasses() {
        Snapshot s = current();
        return new MemberClasses(s.memberClassDescriptions(), s.memberClassCodes());
    }

    public List<SecurityServerInfoV2> securityServers() {
        return current().securityServers();
    }

    private Snapshot current() {
        Snapshot existing = snapshot;
        if (clock.instant().isAfter(existing.expiresAt())) {
            return refresh();
        }
        return existing;
    }

    /**
     * Single-flight / stampede-protection guard: {@code synchronized} plus the double-checked
     * expiry test below ensure that when many concurrent callers hit an expired TTL at once, only
     * the first one through the lock actually re-parses the file — every other thread that was
     * waiting on the lock finds the snapshot already fresh and returns it instead of triggering a
     * redundant, concurrent re-parse of its own.
     */
    private synchronized Snapshot refresh() {
        Instant now = clock.instant();
        Snapshot existing = snapshot;
        if (!now.isAfter(existing.expiresAt())) {
            // another thread refreshed while this one waited for the lock
            return existing;
        }
        Snapshot next = parseSnapshot(now.plus(TTL));
        snapshot = next;
        return next;
    }

    private Snapshot parseSnapshot(Instant expiresAt) {
        try {
            SubsystemNameLookup names = parseSubsystemNames();
            Map<String, String> descriptions = new HashMap<>();
            Set<String> codes = new HashSet<>();
            for (MemberClassInfo info : parser.parseMemberClasses(sharedParamsFile)) {
                descriptions.put(info.getCode(), info.getDescription());
                codes.add(info.getCode());
            }
            List<SecurityServerInfoV2> servers = parser.parseSecurityServers(sharedParamsFile);
            return new Snapshot(expiresAt, names, Map.copyOf(descriptions), Set.copyOf(codes), List.copyOf(servers));
        } catch (Exception e) {
            log.warn("Failed to parse shared-params file {}; caching empty result for {}s", sharedParamsFile,
                    TTL_SECONDS, e);
            return Snapshot.empty(expiresAt);
        }
    }

    private SubsystemNameLookup parseSubsystemNames() throws ParserConfigurationException, IOException, SAXException {
        Map<String, String> byKey = new HashMap<>();
        for (SubsystemNameInfo info : parser.parseSubsystemNames(sharedParamsFile)) {
            byKey.put(keyOf(info.getMemberClass(), info.getMemberCode(), info.getSubsystemCode()),
                    info.getSubsystemName());
        }
        return (memberClass, memberCode, subsystemCode) -> byKey.get(keyOf(memberClass, memberCode, subsystemCode));
    }

    private static String keyOf(String memberClass, String memberCode, String subsystemCode) {
        return memberClass + "|" + memberCode + "|" + subsystemCode;
    }

    /**
     * Member-class descriptions and codes read from the same {@link Snapshot}.
     *
     * @param descriptions member-class code to description
     * @param codes        every known member-class code
     */
    public record MemberClasses(Map<String, String> descriptions, Set<String> codes) { }

    private record Snapshot(Instant expiresAt, SubsystemNameLookup subsystemNames,
            Map<String, String> memberClassDescriptions, Set<String> memberClassCodes,
            List<SecurityServerInfoV2> securityServers) {

        static Snapshot empty(Instant expiresAt) {
            return new Snapshot(expiresAt, (memberClass, memberCode, subsystemCode) -> null,
                    Map.of(), Set.of(), List.of());
        }
    }
}
