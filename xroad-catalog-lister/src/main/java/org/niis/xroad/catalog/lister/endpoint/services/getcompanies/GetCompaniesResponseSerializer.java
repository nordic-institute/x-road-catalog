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
package org.niis.xroad.catalog.lister.endpoint.services.getcompanies;

import jakarta.xml.soap.SOAPElement;
import jakarta.xml.soap.SOAPEnvelope;
import jakarta.xml.soap.SOAPException;
import org.niis.xrd4j.common.message.ServiceResponse;
import org.niis.xrd4j.server.serializer.AbstractServiceResponseSerializer;
import org.niis.xroad.catalog.lister.endpoint.services.common.CommonSerializer;
import fi.dvv.xroad.catalog.persistence.entity.Company;

public class GetCompaniesResponseSerializer extends AbstractServiceResponseSerializer<GetCompaniesRequest, Iterable<Company>> {

    /**
     * Serialize response to the following format:
     *
     * &lt;xs:complexType name="CompanyList"&gt;
     *      &lt;xs:sequence&gt;
     *          &lt;xs:element maxOccurs="unbounded" minOccurs="0" name="company" type="tns:Company"/&gt;
     *      &lt;/xs:sequence&gt;
     * &lt;/xs:complexType&gt;
     */
    @Override
    protected void serializeResponse(ServiceResponse<GetCompaniesRequest, Iterable<Company>> response,
                                     SOAPElement soapResponse, SOAPEnvelope envelope) throws SOAPException {
        SOAPElement data = soapResponse.addChildElement(envelope.createName("companyList"));
        for (var company : response.getResponseData()) {
            CommonSerializer.serialize(envelope, data, company);
        }
    }

}
