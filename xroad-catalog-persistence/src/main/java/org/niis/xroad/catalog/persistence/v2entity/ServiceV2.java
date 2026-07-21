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
package org.niis.xroad.catalog.persistence.v2entity;

import org.niis.xroad.catalog.persistence.entity.StatusInfo;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.Immutable;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Read-only V2 read model over the shared {@code service} table. Never persisted; the write path
 * is the collector via the V1 {@code Service} entity. Descriptor blobs are isolated: this entity
 * has no descriptor collections; {@link WsdlV2}/{@link OpenApiV2} carry {@code data} and are
 * touched only by the descriptor-endpoint queries.
 *
 * <p>{@code serviceType} is maintained by the collector recompute and is one of {@code SOAP},
 * {@code OPENAPI}, {@code REST} or {@code UNKNOWN}. The write path inserts a service row before
 * its descriptor is fetched, so a freshly collected row carries the {@code UNKNOWN} column default
 * until the next recompute (accepted staleness &le; one collector interval). {@code UNKNOWN} means
 * "not yet classified" and is deliberately distinct from {@code REST}, which is the settled
 * classification for a service that has no descriptor at all.
 */
@Entity
@Table(name = "service")
@Immutable
@Getter
public class ServiceV2 {
    @Id
    private long id;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "SUBSYSTEM_ID")
    private SubsystemV2 subsystem;
    @Column(nullable = false)
    private String serviceCode;
    private String serviceVersion;
    @Column(name = "service_type", nullable = false)
    @ColumnDefault("'UNKNOWN'")
    private String serviceType;
    @Embedded
    private StatusInfo statusInfo = new StatusInfo();
    @OneToMany(mappedBy = "service", fetch = FetchType.LAZY)
    private Set<EndpointV2> endpoints = new HashSet<>();

    public Set<EndpointV2> getActiveEndpoints() {
        return endpoints.stream()
                .filter(e -> !e.getStatusInfo().isRemoved())
                .collect(Collectors.toSet());
    }
}
