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
package org.niis.xroad.catalog.lister.endpoint.services.hascompanychanged;

import jakarta.xml.soap.SOAPElement;
import jakarta.xml.soap.SOAPEnvelope;
import jakarta.xml.soap.SOAPException;
import org.niis.xrd4j.common.message.ServiceResponse;
import org.niis.xrd4j.server.serializer.AbstractServiceResponseSerializer;

public class HasCompanyChangedResponseSerializer extends AbstractServiceResponseSerializer<HasCompanyChangedRequest, CompanyChangeResult> {

    /**
     * Serialize response to the following format:
     *
     * &lt;xs:element name="HasCompanyChangedResponse"&gt;
     *     &lt;xs:complexType&gt;
     *         &lt;xs:sequence&gt;
     *             &lt;xs:element name="changed" type="xs:boolean"/&gt;
     *             &lt;xs:element name="changedValueList" type="tns:ChangedValueList"/&gt;
     *         &lt;/xs:sequence&gt;
     *     &lt;/xs:complexType&gt;
     * &lt;/xs:element&gt;
     *
     * &lt;xs:complexType name="ChangedValueList"&gt;
     *     &lt;xs:sequence&gt;
     *         &lt;xs:element maxOccurs="unbounded" minOccurs="0" name="changedValue" type="tns:ChangedValue"/&gt;
     *     &lt;/xs:sequence&gt;
     * &lt;/xs:complexType&gt;
     *
     * &lt;xs:complexType name="ChangedValue"&gt;
     *     &lt;xs:sequence&gt;
     *         &lt;xs:element name="name" type="xs:string"/&gt;
     *     &lt;/xs:sequence&gt;
     * &lt;/xs:complexType&gt;
     */
    @Override
    protected void serializeResponse(ServiceResponse<HasCompanyChangedRequest, CompanyChangeResult> response,
                                     SOAPElement soapResponse, SOAPEnvelope envelope) throws SOAPException {
        CompanyChangeResult result = response.getResponseData();
        
        SOAPElement changedEl = soapResponse.addChildElement(envelope.createName("changed"));
        changedEl.setTextContent(Boolean.toString(result.isChanged()));
        
        SOAPElement changedValueListEl = soapResponse.addChildElement(envelope.createName("changedValueList"));
        for (String changedValueName : result.getChangedValueNames()) {
            SOAPElement changedValueEl = changedValueListEl.addChildElement(envelope.createName("changedValue"));
            SOAPElement nameEl = changedValueEl.addChildElement(envelope.createName("name"));
            nameEl.setTextContent(changedValueName);
        }
    }
}
