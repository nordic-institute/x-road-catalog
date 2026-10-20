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
package org.niis.xroad.catalog.persistence.v2.entity;

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

/**
 * Read-only V2 read model over the {@code active_service} view; the write path is the collector
 * via the V1 {@code Service} entity. Descriptor blobs never load through this entity —
 * {@link Wsdl}/{@link OpenApi} carry {@code data} and are touched only by descriptor queries.
 *
 * <p>{@code serviceType} (SOAP/OPENAPI/REST/UNKNOWN) is maintained by the collector recompute; a
 * freshly collected row carries the {@code UNKNOWN} default until the next recompute (staleness of
 * at most one collector interval). {@code UNKNOWN} means "not yet classified", distinct from
 * {@code REST}, the settled classification for a service with no descriptor.
 *
 * <p>Named {@code ServiceV2} because the default entity name would collide with the V1
 * {@code Service} when both packages share a persistence unit ({@code DuplicateMappingException});
 * JPQL must reference {@code ServiceV2}.
 */
@Entity(name = "ServiceV2")
@Table(name = "active_service")
@Immutable
@Getter
public class Service {
    @Id
    private long id;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "SUBSYSTEM_ID")
    private Subsystem subsystem;
    @Column(nullable = false)
    private String serviceCode;
    private String serviceVersion;
    @Column(name = "service_type", nullable = false)
    @ColumnDefault("'UNKNOWN'")
    private String serviceType;
    @Embedded
    private StatusInfo statusInfo = new StatusInfo();
    @OneToMany(mappedBy = "service", fetch = FetchType.LAZY)
    private Set<Endpoint> endpoints = new HashSet<>();
}
