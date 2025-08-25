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
package org.niis.xroad.catalog.lister.endpoint.services.getwsdl;

import jakarta.xml.soap.Node;
import jakarta.xml.soap.SOAPException;
import jakarta.xml.soap.SOAPMessage;
import org.niis.xrd4j.server.deserializer.AbstractCustomRequestDeserializer;

import static org.w3c.dom.Node.ELEMENT_NODE;

public class GetWsdlRequestDeserializer extends AbstractCustomRequestDeserializer<GetWsdlRequest> {
    @Override
    protected GetWsdlRequest deserializeRequest(Node requestNode, SOAPMessage message) throws SOAPException {
        if (requestNode == null) {
            return null;
        }

        GetWsdlRequest request = new GetWsdlRequest();

        for (int i = 0; i < requestNode.getChildNodes().getLength(); i++) {
            var node = requestNode.getChildNodes().item(i);
            if (node.getNodeType() != ELEMENT_NODE) {
                continue;
            }

            if ("externalId".equals(node.getLocalName())) {
                request.setExternalId(node.getTextContent());
            }
        }

        return request;
    }
}
