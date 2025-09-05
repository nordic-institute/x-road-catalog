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
package fi.dvv.xroad.catalog.lister.endpoint.services.getorganizations;

import jakarta.xml.soap.SOAPElement;
import jakarta.xml.soap.SOAPEnvelope;
import jakarta.xml.soap.SOAPException;
import org.niis.xrd4j.common.message.ServiceResponse;
import org.niis.xrd4j.server.serializer.AbstractServiceResponseSerializer;
import org.niis.xroad.catalog.lister.endpoint.services.common.CommonSerializer;
import fi.dvv.xroad.catalog.persistence.entity.Organization;

@SuppressWarnings("checkstyle:JavadocStyle")
public class GetOrganizationsResponseSerializer extends AbstractServiceResponseSerializer<GetOrganizationsRequest, Iterable<Organization>> {

    /**
     * Serialize response to the following format:
     *
     * <pre>{@code
     * <xs:complexType name="OrganizationList">
     *      <xs:sequence>
     *          <xs:element maxOccurs="unbounded" minOccurs="0" name="organization" type="tns:Organization"/>
     *      </xs:sequence>
     * </xs:complexType>
     * }</pre>
     */
    @Override
    protected void serializeResponse(ServiceResponse<GetOrganizationsRequest, Iterable<Organization>> response,
                                     SOAPElement soapResponse, SOAPEnvelope envelope) throws SOAPException {
        SOAPElement data = soapResponse.addChildElement(envelope.createName("organizationList"));
        for (var organization : response.getResponseData()) {
            CommonSerializer.serialize(envelope, data, organization);
        }
    }

}
