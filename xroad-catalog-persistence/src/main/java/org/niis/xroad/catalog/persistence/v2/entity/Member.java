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
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.Immutable;

import java.util.HashSet;
import java.util.Set;

/**
 * Read-only V2 read model over the {@code active_member} view. Never persisted; the write path
 * is the collector via the V1 {@code Member} entity.
 *
 * <p>{@code isProvider} is maintained by the collector recompute; mid-cycle rows carry the column
 * default until the next recompute (staleness of at most one collector interval).
 *
 * <p>Named {@code MemberV2} because the default entity name would collide with the V1
 * {@code Member} when both packages share a persistence unit ({@code DuplicateMappingException});
 * JPQL must reference {@code MemberV2}.
 */
@Entity(name = "MemberV2")
@Table(name = "active_member")
@Immutable
@Getter
public class Member {
    @Id
    private long id;
    @Column(nullable = false)
    private String xRoadInstance;
    @Column(nullable = false)
    private String memberClass;
    @Column(nullable = false)
    private String memberCode;
    @Column(nullable = false)
    private String name;
    @Column(name = "is_provider", nullable = false)
    @ColumnDefault("false")
    private boolean isProvider;
    @Embedded
    private StatusInfo statusInfo = new StatusInfo();
    @OneToMany(mappedBy = "member", fetch = FetchType.LAZY)
    private Set<Subsystem> subsystems = new HashSet<>();
}
