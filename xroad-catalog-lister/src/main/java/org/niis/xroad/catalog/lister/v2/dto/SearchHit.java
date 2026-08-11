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
package org.niis.xroad.catalog.lister.v2.dto;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.swagger.v3.oas.annotations.media.DiscriminatorMapping;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Sealed hierarchy of {@code GET /api/v2/search} rows, discriminated by {@code type}.
 *
 * <p>Each subtype exposes a literal {@code type()} accessor because the {@code List<T>} inside
 * {@code PagedCollectionResponse} erases to {@code List<Object>}, hiding this interface's
 * {@link JsonTypeInfo} from Jackson during serialization; {@link JsonTypeInfo.As#EXISTING_PROPERTY}
 * lets deserialization read that same property without writing a duplicate. The {@link Schema}
 * annotation declares only the discriminator — subtypes already extend this base via SpringDoc's
 * {@code allOf} inheritance, and a parallel {@code oneOf} creates a circular reference that breaks
 * Swagger UI's resolver ({@code Elements in allOf must be objects}).
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "type", visible = true)
@JsonSubTypes({
        @JsonSubTypes.Type(value = MemberSearchHit.class, name = "member"),
        @JsonSubTypes.Type(value = SubsystemSearchHit.class, name = "subsystem"),
        @JsonSubTypes.Type(value = ServiceSearchHit.class, name = "service")
})
@Schema(
        type = "object",
        discriminatorProperty = "type",
        discriminatorMapping = {
                @DiscriminatorMapping(value = "member", schema = MemberSearchHit.class),
                @DiscriminatorMapping(value = "subsystem", schema = SubsystemSearchHit.class),
                @DiscriminatorMapping(value = "service", schema = ServiceSearchHit.class)
        }
)
public sealed interface SearchHit permits MemberSearchHit, SubsystemSearchHit, ServiceSearchHit {
    String type();
}
