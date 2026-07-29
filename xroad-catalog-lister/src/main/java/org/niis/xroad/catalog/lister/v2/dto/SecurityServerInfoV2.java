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
 * A security server parsed from the shared-params XML, with owner and clients resolved against
 * the member/subsystem {@code id} attributes it references.
 *
 * @param serverCode security server code
 * @param address security server address
 * @param owner the owning member
 * @param clients clients registered on the server
 */
public record SecurityServerInfoV2(String serverCode, String address, MemberRef owner, List<ClientRef> clients) {

    /**
     * The owning member — ownership in shared-params is always a member-level reference, never a subsystem.
     *
     * @param memberClass member class
     * @param memberCode member code
     * @param name member display name
     */
    public record MemberRef(String memberClass, String memberCode, String name) { }

    /**
     * A client registered on a security server.
     *
     * @param memberClass member class
     * @param memberCode member code
     * @param subsystemCode subsystem code, {@code null} for a member-level client
     */
    public record ClientRef(String memberClass, String memberCode, String subsystemCode) { }
}
