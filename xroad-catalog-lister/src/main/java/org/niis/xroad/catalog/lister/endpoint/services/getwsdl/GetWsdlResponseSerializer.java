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

import jakarta.xml.soap.SOAPElement;
import jakarta.xml.soap.SOAPEnvelope;
import jakarta.xml.soap.SOAPException;
import org.niis.xrd4j.common.message.ServiceResponse;
import org.niis.xrd4j.server.serializer.AbstractServiceResponseSerializer;
import org.w3c.dom.CDATASection;

/**
 * @deprecated Superseded by the V2 REST API ({@code org.niis.xroad.catalog.lister.v2});
 *             V1 is kept for compatibility and is scheduled for removal.
 */
@Deprecated
public class GetWsdlResponseSerializer extends AbstractServiceResponseSerializer<GetWsdlRequest, String> {

    /**
     * Serialize response to the following format:
     *
     * <pre>{@code
     * &lt;xs:element name="GetWsdlResponse"&gt;
     *     &lt;xs:complexType&gt;
     *         &lt;xs:sequence&gt;
     *             &lt;xs:element name="wsdl" type="xs:string"/&gt;
     *         &lt;/xs:sequence&gt;
     *     &lt;/xs:complexType&gt;
     * &lt;/xs:element&gt;
     * }</pre>
     */
    @Override
    protected void serializeResponse(ServiceResponse<GetWsdlRequest, String> response,
                                     SOAPElement soapResponse, SOAPEnvelope envelope) throws SOAPException {
        if (response.getResponseData() != null) {
            SOAPElement wsdlElement = soapResponse.addChildElement(envelope.createName("wsdl"));
            CDATASection wsdlCdata = wsdlElement.getOwnerDocument().createCDATASection(response.getResponseData());
            wsdlElement.appendChild(wsdlCdata);
        }
    }
}
