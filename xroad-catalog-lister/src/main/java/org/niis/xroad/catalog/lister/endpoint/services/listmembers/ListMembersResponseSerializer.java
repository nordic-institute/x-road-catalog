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

import jakarta.xml.soap.SOAPElement;
import jakarta.xml.soap.SOAPEnvelope;
import jakarta.xml.soap.SOAPException;
import org.niis.xrd4j.common.message.ServiceResponse;
import org.niis.xrd4j.server.serializer.AbstractServiceResponseSerializer;
import org.niis.xroad.catalog.lister.endpoint.services.listmembers.types.ListMembersRequest;
import org.niis.xroad.catalog.persistence.entity.Member;
import org.niis.xroad.catalog.persistence.entity.OpenApi;
import org.niis.xroad.catalog.persistence.entity.Service;
import org.niis.xroad.catalog.persistence.entity.StatusInfo;
import org.niis.xroad.catalog.persistence.entity.Subsystem;
import org.niis.xroad.catalog.persistence.entity.Wsdl;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

@SuppressWarnings("checkstyle:JavadocStyle")
public class ListMembersResponseSerializer extends AbstractServiceResponseSerializer<ListMembersRequest, Iterable<Member>> {

    /**
     * Serialize response to the following format:
     *
     * <pre>{@code
     * <xs:complexType name="MemberList">
     *      <xs:sequence>
     *          <xs:element maxOccurs="unbounded" minOccurs="0" name="member" type="tns:Member"/>
     *      </xs:sequence>
     * </xs:complexType>
     * }</pre>
     */
    @Override
    protected void serializeResponse(ServiceResponse<ListMembersRequest, Iterable<Member>> response,
                                     SOAPElement soapResponse, SOAPEnvelope envelope) throws SOAPException {
        SOAPElement data = soapResponse.addChildElement(envelope.createName("memberList"));
        for (var member : response.getResponseData()) {
            serializeMember(envelope, data, member);
        }
    }


    /**
     * Serialize member into the following XML descriptor format.
     *
     * <pre>{@code
     * <xs:complexType name="Member">
     *     <xs:sequence>
     *         <xs:element name="xRoadInstance" type="xs:string"/>
     *         <xs:element name="memberClass" type="xs:string"/>
     *         <xs:element name="memberCode" type="xs:string"/>
     *         <xs:element name="name" type="xs:string"/>
     *         <xs:element name="subsystems" type="tns:SubsystemList"/>
     *         <xs:element name="created" type="xs:dateTime"/>
     *         <xs:element name="changed" type="xs:dateTime"/>
     *         <xs:element name="fetched" type="xs:dateTime"/>
     *         <xs:element name="removed" type="xs:dateTime"/>
     *     </xs:sequence>
     * </xs:complexType>
     * }</pre>
     *
     *
     */
    private void serializeMember(final SOAPEnvelope envelope, final SOAPElement parent, final Member member) throws SOAPException {
        SOAPElement memberEl = parent.addChildElement(envelope.createName("member"));
        addElementWithValue(envelope, memberEl, "xRoadInstance", member.getXRoadInstance());
        addElementWithValue(envelope, memberEl, "memberClass", member.getMemberClass());
        addElementWithValue(envelope, memberEl, "memberCode", member.getMemberCode());
        addElementWithValue(envelope, memberEl, "name", member.getName());

        SOAPElement subsystemsEl = memberEl.addChildElement(envelope.createName("subsystems"));
        for (var subsystem : member.getAllSubsystems()) {
            serializeSubsystem(envelope, subsystemsEl, subsystem);
        }

        serializeStatusInfo(envelope, memberEl, member.getStatusInfo());

    }


    /**
     * Serialize subsystem into the following XML description format:
     *
     * <pre>{@code
     * <xs:complexType name="SubsystemList">
     *     <xs:sequence>
     *         <xs:element maxOccurs="unbounded" minOccurs="0" name="subsystem" type="tns:Subsystem"/>
     *     </xs:sequence>
     * </xs:complexType>
     *
     *
     * <xs:complexType name="Subsystem">
     *     <xs:sequence>
     *         <xs:element name="subsystemCode" type="xs:string"/>
     *         <xs:element name="services" type="tns:ServiceList"/>
     *         <xs:element name="created" type="xs:dateTime"/>
     *         <xs:element name="changed" type="xs:dateTime"/>
     *         <xs:element name="fetched" type="xs:dateTime"/>
     *         <xs:element name="removed" type="xs:dateTime"/>
     *     </xs:sequence>
     * </xs:complexType>
     * }</pre>
     *
     */
    private void serializeSubsystem(final SOAPEnvelope envelope, final SOAPElement parent, final Subsystem subsystem) throws SOAPException {
        SOAPElement subsystemEl = parent.addChildElement(envelope.createName("subsystem"));
        addElementWithValue(envelope, subsystemEl, "subsystemCode", subsystem.getSubsystemCode());
        
        SOAPElement servicesEl = subsystemEl.addChildElement(envelope.createName("services"));
        for (var service : subsystem.getAllServices()) {
            serializeService(envelope, servicesEl, service);
        }
        
        serializeStatusInfo(envelope, subsystemEl, subsystem.getStatusInfo());
    }

    private void serializeService(final SOAPEnvelope envelope, final SOAPElement parent, final Service service) throws SOAPException {
        SOAPElement serviceEl = parent.addChildElement(envelope.createName("service"));
        addElementWithValue(envelope, serviceEl, "serviceCode", service.getServiceCode());
        addElementWithValue(envelope, serviceEl, "serviceVersion", service.getServiceVersion());
        
        String serviceType;
        if (service.hasWsdl()) {
            serviceType = "SOAP";
        } else if (service.hasOpenApi()) {
            serviceType = "OPENAPI";
        } else if (service.hasRest()) {
            serviceType = "REST";
        } else {
            serviceType = "UNKNOWN";
        }
        addElementWithValue(envelope, serviceEl, "serviceType", serviceType);
        
        if (service.getWsdl() != null) {
            serializeWsdl(envelope, serviceEl, service.getWsdl());
        }
        
        if (service.getOpenApi() != null) {
            serializeOpenApi(envelope, serviceEl, service.getOpenApi());
        }
        
        serializeStatusInfo(envelope, serviceEl, service.getStatusInfo());
    }

    private void serializeWsdl(final SOAPEnvelope envelope, final SOAPElement parent, final Wsdl wsdl) throws SOAPException {
        SOAPElement wsdlEl = parent.addChildElement(envelope.createName("wsdl"));
        addElementWithValue(envelope, wsdlEl, "externalId", wsdl.getExternalId());
        serializeStatusInfo(envelope, wsdlEl, wsdl.getStatusInfo());
    }

    private void serializeOpenApi(final SOAPEnvelope envelope, final SOAPElement parent, final OpenApi openApi) throws SOAPException {
        SOAPElement openApiEl = parent.addChildElement(envelope.createName("openapi"));
        addElementWithValue(envelope, openApiEl, "externalId", openApi.getExternalId());
        serializeStatusInfo(envelope, openApiEl, openApi.getStatusInfo());
    }

    private void serializeStatusInfo(final SOAPEnvelope envelope, final SOAPElement parent, final StatusInfo statusInfo)
            throws SOAPException {
        addElementWithValue(envelope, parent, "created", statusInfo.getCreated());
        addElementWithValue(envelope, parent, "changed", statusInfo.getChanged());
        addElementWithValue(envelope, parent, "fetched", statusInfo.getFetched());
        addElementWithValue(envelope, parent, "removed", statusInfo.getRemoved());
    }

    private void addElementWithValue(final SOAPEnvelope envelope, final SOAPElement parent,
                                     final String name, final String value) throws SOAPException {
        if (value == null) {
            return;
        }

        SOAPElement element = parent.addChildElement(envelope.createName(name));
        element.setTextContent(value);
    }

    private void addElementWithValue(final SOAPEnvelope envelope, final SOAPElement parent,
                                     final String name, final LocalDateTime value) throws SOAPException {
        addElementWithValue(envelope, parent, name, formatLocalDateTime(value));
    }

    private String formatLocalDateTime(final LocalDateTime localDateTime) {
        if (localDateTime == null) {
            return null;
        }

        ZonedDateTime zonedTime = localDateTime.atZone(ZoneId.systemDefault());
        return zonedTime.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }
}
