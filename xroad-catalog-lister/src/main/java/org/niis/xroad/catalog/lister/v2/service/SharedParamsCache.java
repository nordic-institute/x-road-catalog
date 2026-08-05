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
import org.niis.xroad.catalog.lister.v2.dto.SecurityServerInfo;
import org.niis.xroad.catalog.lister.v2.dto.SubsystemNameInfo;
import org.niis.xroad.catalog.lister.v2.parser.SharedParamsParserV2;
import org.niis.xroad.catalog.lister.v2.parser.SharedParamsParserV2.ParsedSharedParams;
import org.niis.xroad.globalconf.model.ConfigurationPartMetadata;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * TTL cache over shared-params.xml parse results used for per-request enrichment (subsystem names,
 * member class descriptions, Security Servers). The 60s TTL matches the global-conf refresh cadence.
 *
 * <p>A single immutable {@link Snapshot} built from one document parse is published via an
 * {@link AtomicReference}, so readers never see a partially-built result. Refresh runs under a
 * {@code tryLock} with a double-checked expiry test: one thread re-parses while the others keep
 * being served the expired snapshot, so a re-parse never queues up in-flight requests. The very
 * first load has no snapshot to serve and therefore does wait. A parse failure is cached as an
 * empty snapshot for the TTL window, degrading enrichment without failing the endpoints that use it.
 *
 * <p>The instance identifier is cached separately for the process lifetime in its own
 * {@link AtomicReference}, keeping it off the refresh lock: {@link #getCurrentInstance()} raises 503
 * until shared-params.xml first becomes readable (the gap between startup and the first
 * configuration-client sync); a published instance ID never changes.
 *
 * <p>Each refresh also reads the configuration client's {@code .metadata} sidecar for the
 * global-conf expiry, surfaced via {@link #globalConfExpiry()} — staleness is flagged, data is
 * still served.
 */
@Slf4j
@Service
public class SharedParamsCache {

    private static final int TTL_SECONDS = 60;
    private static final Duration TTL = Duration.ofSeconds(TTL_SECONDS);
    private static final String METADATA_SUFFIX = ".metadata";

    private final SharedParamsParserV2 parser;
    private final Clock clock;
    private final String sharedParamsFile;

    // Publishes fully-built immutable snapshots to readers without requiring the refresh lock.
    // Maintenance contract: ALL TTL-cached state must live inside the immutable Snapshot record —
    // never add a sibling field that has to stay consistent with it. Readers must take exactly one
    // snapshot reference per logical operation (memberClasses() shows the pattern); a second get()
    // may observe a different parse. Writes are serialized by refreshLock, so plain get()/set() are
    // sufficient — do not introduce CAS here.
    private final AtomicReference<Snapshot> snapshot = new AtomicReference<>(Snapshot.notLoaded());

    // Read on nearly every V2 request, written at most once; deliberately not behind refreshLock.
    // First-writer-wins via compareAndSet; a duplicate load during a startup race is benign.
    private final AtomicReference<String> cachedInstance = new AtomicReference<>();

    private final Lock refreshLock = new ReentrantLock();

    public SharedParamsCache(SharedParamsParserV2 parser, Clock clock,
            @Value("${xroad-catalog.shared-params-file}") String sharedParamsFile) {
        this.parser = parser;
        this.clock = clock;
        this.sharedParamsFile = sharedParamsFile;
    }

    /**
     * Resolves the X-Road instance identifier from shared-params.xml, cached for the process
     * lifetime after the first successful load. Lock-free once loaded; a startup race can load twice,
     * which is harmless because the value never changes and the first writer wins.
     *
     * <p>Contract: only a SUCCESSFUL load is ever cached. A failed load (config not yet synced)
     * throws before the compareAndSet, so the next call retries — do not replace this with a
     * memoizing supplier, which would cache the failure and leave the endpoint permanently broken.
     *
     * @throws ResponseStatusException 503 while the file does not exist yet
     * @throws IllegalStateException   when the file exists but cannot be parsed
     */
    public String getCurrentInstance() {
        String instance = cachedInstance.get();
        if (instance == null) {
            cachedInstance.compareAndSet(null, loadInstance());
            instance = cachedInstance.get();
        }
        return instance;
    }

    private String loadInstance() {
        if (!Files.exists(Path.of(sharedParamsFile))) {
            log.warn("shared-params.xml not yet available at {} — configuration-client may still be initializing",
                    sharedParamsFile);
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "X-Road instance identifier not yet available; configuration-client may still be initializing");
        }
        try {
            String instance = parser.parseInstanceIdentifier(sharedParamsFile);
            if (instance == null || instance.isBlank()) {
                throw new IllegalStateException(
                        "shared-params.xml is missing <instanceIdentifier>: " + sharedParamsFile);
            }
            log.info("Loaded X-Road instance identifier '{}' from {}", instance, sharedParamsFile);
            return instance.trim();
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Failed to load X-Road instance identifier from " + sharedParamsFile, e);
        }
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
     * Reads descriptions and codes from a single {@link Snapshot}, never from two different parses
     * across a TTL expiry.
     */
    public MemberClasses memberClasses() {
        Snapshot s = current();
        return new MemberClasses(s.memberClassDescriptions(), s.memberClassCodes());
    }

    public List<SecurityServerInfo> securityServers() {
        return current().securityServers();
    }

    /**
     * Expiry state of the downloaded global configuration, from the {@code .metadata} sidecar.
     * {@code expiresAt} is null (and {@code expired} false) when the sidecar is missing or
     * unreadable — expiry is unknown, not assumed. {@code expired} is evaluated at call time so a
     * conf that lapses mid-TTL-window is still reported.
     */
    public GlobalConfExpiry globalConfExpiry() {
        Instant expiresAt = current().globalConfExpiresAt();
        return new GlobalConfExpiry(expiresAt != null && clock.instant().isAfter(expiresAt), expiresAt);
    }

    private Snapshot current() {
        Snapshot existing = snapshot.get();
        if (clock.instant().isAfter(existing.expiresAt())) {
            return refresh(existing);
        }
        return existing;
    }

    /**
     * Stampede guard: the lock plus the double-checked expiry test ensure concurrent callers hitting
     * an expired TTL trigger at most one re-parse. A caller that loses the race is served the
     * expired snapshot instead of waiting, since data one TTL window old beats a blocked request.
     * There is nothing to serve before the first load, so that one call does wait — an empty
     * snapshot is indistinguishable from a genuinely empty configuration.
     */
    private Snapshot refresh(Snapshot expired) {
        if (expired.loaded()) {
            if (!refreshLock.tryLock()) {
                return expired;
            }
        } else {
            refreshLock.lock();
        }
        try {
            Instant now = clock.instant();
            Snapshot existing = snapshot.get();
            if (!now.isAfter(existing.expiresAt())) {
                // another thread refreshed while this one waited for the lock
                return existing;
            }
            Snapshot next = parseSnapshot(now.plus(TTL), readGlobalConfExpiration(now));
            snapshot.set(next);
            return next;
        } finally {
            refreshLock.unlock();
        }
    }

    /**
     * Reads the global-conf expiration instant from the {@code .metadata} sidecar, warning when it
     * has passed (at most once per TTL window, since this runs only on refresh). Read failures
     * degrade to "expiry unknown" (null).
     */
    private Instant readGlobalConfExpiration(Instant now) {
        Path metadataFile = Path.of(sharedParamsFile + METADATA_SUFFIX);
        if (!Files.exists(metadataFile)) {
            log.debug("Global-conf metadata file {} not found; expiry unknown", metadataFile);
            return null;
        }
        try (InputStream in = Files.newInputStream(metadataFile)) {
            OffsetDateTime expirationDate = ConfigurationPartMetadata.read(in).getExpirationDate();
            if (expirationDate == null) {
                log.debug("Global-conf metadata file {} has no expirationDate; expiry unknown", metadataFile);
                return null;
            }
            Instant expiresAt = expirationDate.toInstant();
            if (now.isAfter(expiresAt)) {
                log.warn("Global configuration is expired since {} — shared-params-derived data may be stale; "
                        + "check the configuration client logs and Central Server reachability", expiresAt);
            }
            return expiresAt;
        } catch (Exception e) {
            log.debug("Failed to read global-conf metadata from {}; expiry unknown", metadataFile, e);
            return null;
        }
    }

    private Snapshot parseSnapshot(Instant expiresAt, Instant globalConfExpiresAt) {
        try {
            ParsedSharedParams parsed = parser.parse(sharedParamsFile);
            Map<String, String> descriptions = new HashMap<>();
            Set<String> codes = new HashSet<>();
            for (MemberClassInfo info : parsed.memberClasses()) {
                descriptions.put(info.getCode(), info.getDescription());
                codes.add(info.getCode());
            }
            return new Snapshot(true, expiresAt, subsystemNameLookup(parsed.subsystemNames()),
                    Map.copyOf(descriptions), Set.copyOf(codes), List.copyOf(parsed.securityServers()),
                    globalConfExpiresAt);
        } catch (Exception e) {
            log.warn("Failed to parse shared-params file {}; caching empty result for {}s", sharedParamsFile,
                    TTL_SECONDS, e);
            return Snapshot.empty(expiresAt, globalConfExpiresAt);
        }
    }

    private static SubsystemNameLookup subsystemNameLookup(List<SubsystemNameInfo> names) {
        Map<String, String> byKey = new HashMap<>();
        for (SubsystemNameInfo info : names) {
            byKey.put(keyOf(info.getMemberClass(), info.getMemberCode(), info.getSubsystemCode()),
                    info.getSubsystemName());
        }
        return (memberClass, memberCode, subsystemCode) -> byKey.get(keyOf(memberClass, memberCode, subsystemCode));
    }

    private static String keyOf(String memberClass, String memberCode, String subsystemCode) {
        return memberClass + "|" + memberCode + "|" + subsystemCode;
    }

    /**
     * Member-class descriptions (code to description) and codes from a single {@link Snapshot}.
     *
     * @param descriptions member-class code to description
     * @param codes member-class codes
     */
    public record MemberClasses(Map<String, String> descriptions, Set<String> codes) { }

    /**
     * Global-conf expiry state for the heartbeat.
     *
     * @param expired   true when the global configuration has passed its expiration date
     * @param expiresAt expiration instant, or null when unknown
     */
    public record GlobalConfExpiry(boolean expired, Instant expiresAt) { }

    /**
     * {@code loaded} tells a snapshot that is empty because a parse produced nothing (or failed, and
     * is cached as such for the TTL) apart from the startup placeholder that was never parsed at all.
     */
    private record Snapshot(boolean loaded, Instant expiresAt, SubsystemNameLookup subsystemNames,
            Map<String, String> memberClassDescriptions, Set<String> memberClassCodes,
            List<SecurityServerInfo> securityServers, Instant globalConfExpiresAt) {

        static Snapshot empty(Instant expiresAt, Instant globalConfExpiresAt) {
            return new Snapshot(true, expiresAt, (memberClass, memberCode, subsystemCode) -> null,
                    Map.of(), Set.of(), List.of(), globalConfExpiresAt);
        }

        static Snapshot notLoaded() {
            return new Snapshot(false, Instant.MIN, (memberClass, memberCode, subsystemCode) -> null,
                    Map.of(), Set.of(), List.of(), null);
        }
    }
}
