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
package org.niis.xroad.catalog.lister.endpoint.services.listmembers;

import jakarta.xml.soap.Node;
import jakarta.xml.soap.SOAPException;
import jakarta.xml.soap.SOAPMessage;
import org.niis.xrd4j.server.deserializer.AbstractCustomRequestDeserializer;
import org.niis.xroad.catalog.lister.endpoint.services.common.DateTimeUtil;

import static org.w3c.dom.Node.ELEMENT_NODE;

/**
 * @deprecated Superseded by the V2 REST API ({@code org.niis.xroad.catalog.lister.v2}); scheduled for removal.
 */
@Deprecated
public class ListMembersRequestDeserializer extends AbstractCustomRequestDeserializer<ListMembersRequest> {
    @Override
    protected ListMembersRequest deserializeRequest(Node requestNode, SOAPMessage message) throws SOAPException {
        if (requestNode == null) {
            return null;
        }

        ListMembersRequest request = new ListMembersRequest();

        for (int i = 0; i < requestNode.getChildNodes().getLength(); i++) {
            // Note that this will be the w3c Node type rather than the soap package type
            var node = requestNode.getChildNodes().item(i);
            if (node.getNodeType() != ELEMENT_NODE) {
                continue;
            }

            if ("startDateTime".equals(node.getLocalName())) {
                request.setStartDateTime(DateTimeUtil.parseXmlDateTime(node.getTextContent()));
            }

            if ("endDateTime".equals(node.getLocalName())) {
                request.setEndDateTime(DateTimeUtil.parseXmlDateTime(node.getTextContent()));
            }
        }

        return request;
    }
}
