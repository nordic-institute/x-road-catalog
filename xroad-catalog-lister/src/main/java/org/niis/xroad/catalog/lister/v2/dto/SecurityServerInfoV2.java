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

import java.util.List;

/**
 * A security server parsed from the shared-params XML, with its owner and clients resolved
 * against the member/subsystem {@code id} attributes referenced by the security server element.
 *
 * @param serverCode X-Road security server code
 * @param address    security server network address
 * @param owner      the member that owns this security server
 * @param clients    members/subsystems registered as clients on this security server
 */
public record SecurityServerInfoV2(String serverCode, String address, MemberRef owner, List<ClientRef> clients) {

    /**
     * The member that owns a security server. Never refers to a subsystem — ownership in
     * shared-params is always a member-level reference.
     *
     * @param memberClass X-Road member class
     * @param memberCode  X-Road member code
     * @param name        member display name
     */
    public record MemberRef(String memberClass, String memberCode, String name) { }

    /**
     * A client registered on a security server. {@code subsystemCode} is {@code null} when the
     * client reference resolves to a member rather than one of its subsystems.
     *
     * @param memberClass   X-Road member class
     * @param memberCode    X-Road member code
     * @param subsystemCode X-Road subsystem code, or {@code null} for a member-level client
     */
    public record ClientRef(String memberClass, String memberCode, String subsystemCode) { }
}
