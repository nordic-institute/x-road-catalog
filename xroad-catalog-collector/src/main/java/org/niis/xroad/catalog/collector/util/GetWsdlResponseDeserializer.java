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
package org.niis.xroad.catalog.collector.util;

import jakarta.xml.soap.AttachmentPart;
import jakarta.xml.soap.Node;
import jakarta.xml.soap.SOAPException;
import jakarta.xml.soap.SOAPMessage;
import lombok.extern.slf4j.Slf4j;
import org.niis.xrd4j.client.deserializer.AbstractResponseDeserializer;

import java.nio.charset.StandardCharsets;

@Slf4j
public class GetWsdlResponseDeserializer extends AbstractResponseDeserializer<GetWsdlRequest, String> {

    public GetWsdlResponseDeserializer() {
        super();
        this.isMetaServiceResponse = true;
    }

    @Override
    protected GetWsdlRequest deserializeRequestData(Node requestNode) throws SOAPException {
        // Not important for us
        return null;
    }

    @Override
    protected String deserializeResponseData(Node responseNode, SOAPMessage message) throws SOAPException {
        // The actual response content is not important, what we want is the WSDL that is in the attachment
        if (message.countAttachments() != 1) {
            log.warn("GetWSDL expected exactly one attachment as a response, got {}", message.countAttachments());
            // Let's throw here so that the error ends up in the error log as well
            throw new SOAPException(String.format("GetWSDL expected exactly one attachment as a response, got %s",
                    message.countAttachments()));
        }
        AttachmentPart wsdlAttachment = message.getAttachments().next();
        return new String(wsdlAttachment.getRawContentBytes(), StandardCharsets.UTF_8);
    }
}
