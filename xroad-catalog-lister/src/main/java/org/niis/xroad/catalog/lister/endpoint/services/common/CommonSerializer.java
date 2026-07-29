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
package org.niis.xroad.catalog.lister.endpoint.services.common;

import jakarta.xml.soap.SOAPElement;
import jakarta.xml.soap.SOAPEnvelope;
import jakarta.xml.soap.SOAPException;
import org.niis.xroad.catalog.persistence.entity.ErrorLog;
import org.niis.xroad.catalog.persistence.entity.Member;
import org.niis.xroad.catalog.persistence.entity.OpenApi;
import org.niis.xroad.catalog.persistence.entity.Service;
import org.niis.xroad.catalog.persistence.entity.StatusInfo;
import org.niis.xroad.catalog.persistence.entity.Subsystem;
import org.niis.xroad.catalog.persistence.entity.Wsdl;

import java.time.LocalDateTime;

/**
 * @deprecated Superseded by the V2 REST API ({@code org.niis.xroad.catalog.lister.v2}); scheduled for removal.
 */
@Deprecated
@SuppressWarnings({"PMD.AvoidDuplicateLiterals", "PMD.CommentSize", "java:S1192"})
public final class CommonSerializer {

    private CommonSerializer() {

    }

    /**
     * Serialize member into the following XML descriptor format.
     *
     * <pre>{@code
     * &lt;xs:complexType name="Member"&gt;
     *     &lt;xs:sequence&gt;
     *         &lt;xs:element name="xRoadInstance" type="xs:string"/&gt;
     *         &lt;xs:element name="memberClass" type="xs:string"/&gt;
     *         &lt;xs:element name="memberCode" type="xs:string"/&gt;
     *         &lt;xs:element name="name" type="xs:string"/&gt;
     *         &lt;xs:element name="subsystems" type="tns:SubsystemList"/&gt;
     *         &lt;xs:element name="created" type="xs:dateTime"/&gt;
     *         &lt;xs:element name="changed" type="xs:dateTime"/&gt;
     *         &lt;xs:element name="fetched" type="xs:dateTime"/&gt;
     *         &lt;xs:element minOccurs="0" name="removed" type="xs:dateTime"/&gt;
     *     &lt;/xs:sequence&gt;
     * &lt;/xs:complexType&gt;
     * }</pre>
     */
    public static void serialize(final SOAPEnvelope envelope, final SOAPElement parent, final Member member)
            throws SOAPException {
        SOAPElement memberEl = parent.addChildElement(envelope.createName("member"));
        addElementWithValue(envelope, memberEl, "xRoadInstance", member.getXRoadInstance());
        addElementWithValue(envelope, memberEl, "memberClass", member.getMemberClass());
        addElementWithValue(envelope, memberEl, "memberCode", member.getMemberCode());
        addElementWithValue(envelope, memberEl, "name", member.getName());

        SOAPElement subsystemsEl = memberEl.addChildElement(envelope.createName("subsystems"));
        for (var subsystem : member.getAllSubsystems()) {
            serialize(envelope, subsystemsEl, subsystem);
        }

        serialize(envelope, memberEl, member.getStatusInfo());

    }

    /**
     * Serialize subsystem into the following XML description format:
     *
     * <pre>{@code
     * &lt;xs:complexType name="SubsystemList"&gt;
     *     &lt;xs:sequence&gt;
     *         &lt;xs:element maxOccurs="unbounded" minOccurs="0" name="subsystem" type="tns:Subsystem"/&gt;
     *     &lt;/xs:sequence&gt;
     * &lt;/xs:complexType&gt;
     *
     *
     * &lt;xs:complexType name="Subsystem"&gt;
     *     &lt;xs:sequence&gt;
     *         &lt;xs:element name="subsystemCode" type="xs:string"/&gt;
     *         &lt;xs:element name="services" type="tns:ServiceList"/&gt;
     *         &lt;xs:element name="created" type="xs:dateTime"/&gt;
     *         &lt;xs:element name="changed" type="xs:dateTime"/&gt;
     *         &lt;xs:element name="fetched" type="xs:dateTime"/&gt;
     *         &lt;xs:element minOccurs="0" name="removed" type="xs:dateTime"/&gt;
     *     &lt;/xs:sequence&gt;
     * &lt;/xs:complexType&gt;
     * }</pre>
     */
    public static void serialize(final SOAPEnvelope envelope, final SOAPElement parent, final Subsystem subsystem)
            throws SOAPException {
        SOAPElement subsystemEl = parent.addChildElement(envelope.createName("subsystem"));
        addElementWithValue(envelope, subsystemEl, "subsystemCode", subsystem.getSubsystemCode());

        SOAPElement servicesEl = subsystemEl.addChildElement(envelope.createName("services"));
        for (var service : subsystem.getAllServices()) {
            serialize(envelope, servicesEl, service);
        }

        serialize(envelope, subsystemEl, subsystem.getStatusInfo());
    }

    /**
     * Serialize service into the following XML descriptor format.
     *
     * <pre>{@code
     * &lt;xs:complexType name="Service"&gt;
     *     &lt;xs:sequence&gt;
     *         &lt;xs:element name="serviceCode" type="xs:string"/&gt;
     *         &lt;xs:element name="serviceVersion" type="xs:string"/&gt;
     *         &lt;xs:element name="serviceType" type="xs:string"/&gt;
     *         &lt;xs:choice&gt;
     *             &lt;xs:element name="wsdl" type="tns:WSDL"/&gt;
     *             &lt;xs:element name="openapi" type="tns:OPENAPI"/&gt;
     *         &lt;/xs:choice&gt;
     *         &lt;xs:element name="created" type="xs:dateTime"/&gt;
     *         &lt;xs:element name="changed" type="xs:dateTime"/&gt;
     *         &lt;xs:element name="fetched" type="xs:dateTime"/&gt;
     *         &lt;xs:element minOccurs="0" name="removed" type="xs:dateTime"/&gt;
     *     &lt;/xs:sequence&gt;
     * &lt;/xs:complexType&gt;
     * }</pre>
     */
    public static void serialize(final SOAPEnvelope envelope, final SOAPElement parent, final Service service)
            throws SOAPException {
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
            serialize(envelope, serviceEl, service.getWsdl());
        }

        if (service.getOpenApi() != null) {
            serialize(envelope, serviceEl, service.getOpenApi());
        }

        serialize(envelope, serviceEl, service.getStatusInfo());
    }

    /**
     * Serialize WSDL into the following XML descriptor format.
     *
     * <pre>{@code
     * &lt;xs:complexType name="WSDL"&gt;
     *     &lt;xs:sequence&gt;
     *         &lt;xs:element name="externalId" type="xs:string"/&gt;
     *         &lt;xs:element name="created" type="xs:dateTime"/&gt;
     *         &lt;xs:element name="changed" type="xs:dateTime"/&gt;
     *         &lt;xs:element name="fetched" type="xs:dateTime"/&gt;
     *         &lt;xs:element minOccurs="0" name="removed" type="xs:dateTime"/&gt;
     *     &lt;/xs:sequence&gt;
     * &lt;/xs:complexType&gt;
     * }</pre>
     */
    public static void serialize(final SOAPEnvelope envelope, final SOAPElement parent, final Wsdl wsdl)
            throws SOAPException {
        SOAPElement wsdlEl = parent.addChildElement(envelope.createName("wsdl"));
        addElementWithValue(envelope, wsdlEl, "externalId", wsdl.getExternalId());
        serialize(envelope, wsdlEl, wsdl.getStatusInfo());
    }

    /**
     * Serialize OpenAPI into the following XML descriptor format.
     *
     * <pre>{@code
     * &lt;xs:complexType name="OPENAPI"&gt;
     *     &lt;xs:sequence&gt;
     *         &lt;xs:element name="externalId" type="xs:string"/&gt;
     *         &lt;xs:element name="created" type="xs:dateTime"/&gt;
     *         &lt;xs:element name="changed" type="xs:dateTime"/&gt;
     *         &lt;xs:element name="fetched" type="xs:dateTime"/&gt;
     *         &lt;xs:element minOccurs="0" name="removed" type="xs:dateTime"/&gt;
     *     &lt;/xs:sequence&gt;
     * &lt;/xs:complexType&gt;
     * }</pre>
     */
    public static void serialize(final SOAPEnvelope envelope, final SOAPElement parent, final OpenApi openApi)
            throws SOAPException {
        SOAPElement openApiEl = parent.addChildElement(envelope.createName("openapi"));
        addElementWithValue(envelope, openApiEl, "externalId", openApi.getExternalId());
        serialize(envelope, openApiEl, openApi.getStatusInfo());
    }

    /**
     * Serialize StatusInfo fields into parent element.
     * StatusInfo fields are embedded directly into parent elements for Member, Subsystem, Service, WSDL, and OPENAPI types.
     *
     * <pre>{@code
     * &lt;xs:element name="created" type="xs:dateTime"/&gt;
     * &lt;xs:element name="changed" type="xs:dateTime"/&gt;
     * &lt;xs:element name="fetched" type="xs:dateTime"/&gt;
     * &lt;xs:element minOccurs="0" name="removed" type="xs:dateTime"/&gt;
     * }</pre>
     */
    public static void serialize(final SOAPEnvelope envelope, final SOAPElement parent,
                                 final StatusInfo statusInfo) throws SOAPException {
        addElementWithValue(envelope, parent, "created", statusInfo.getCreated());
        addElementWithValue(envelope, parent, "changed", statusInfo.getChanged());
        addElementWithValue(envelope, parent, "fetched", statusInfo.getFetched());
        if (statusInfo.getRemoved() != null) {
            addElementWithValue(envelope, parent, "removed", statusInfo.getRemoved());
        }
    }

    private static void addElementWithValue(final SOAPEnvelope envelope, final SOAPElement parent,
                                            final String name, final String value) throws SOAPException {
        if (value == null) {
            return;
        }

        SOAPElement element = parent.addChildElement(envelope.createName(name));
        element.setTextContent(value);
    }

    private static void addElementWithValue(final SOAPEnvelope envelope, final SOAPElement parent,
                                            final String name, final LocalDateTime value) throws SOAPException {
        addElementWithValue(envelope, parent, name, DateTimeUtil.localDateTimeToXsdDateTime(value));
    }

    private static void addElementWithValue(final SOAPEnvelope envelope, final SOAPElement parent,
                                            final String name, final Boolean value) throws SOAPException {
        addElementWithValue(envelope, parent, name, value == null ? null : value.toString());
    }

    private static void addElementWithValue(final SOAPEnvelope envelope, final SOAPElement parent,
                                            final String name, final Long value) throws SOAPException {
        addElementWithValue(envelope, parent, name, value == null ? null : value.toString());
    }

    /**
     * Serialize ErrorLog into the following XML descriptor format.
     *
     * <pre>{@code
     * &lt;xs:complexType name="ErrorLog"&gt;
     *     &lt;xs:sequence&gt;
     *         &lt;xs:element name="message" type="xs:string"/&gt;
     *         &lt;xs:element name="code" type="xs:string"/&gt;
     *         &lt;xs:element name="xRoadInstance" type="xs:string"/&gt;
     *         &lt;xs:element name="memberClass" type="xs:string"/&gt;
     *         &lt;xs:element name="memberCode" type="xs:string"/&gt;
     *         &lt;xs:element name="subsystemCode" type="xs:string"/&gt;
     *         &lt;xs:element name="groupCode" type="xs:string"/&gt;
     *         &lt;xs:element name="serviceCode" type="xs:string"/&gt;
     *         &lt;xs:element name="serviceVersion" type="xs:string"/&gt;
     *         &lt;xs:element name="securityCategoryCode" type="xs:string"/&gt;
     *         &lt;xs:element name="serverCode" type="xs:string"/&gt;
     *         &lt;xs:element name="created" type="xs:dateTime"/&gt;
     *     &lt;/xs:sequence&gt;
     * &lt;/xs:complexType&gt;
     * }</pre>
     */
    public static void serialize(final SOAPEnvelope envelope, final SOAPElement parent, final ErrorLog errorLog)
            throws SOAPException {
        SOAPElement errorLogEl = parent.addChildElement(envelope.createName("errorLog"));
        addElementWithValue(envelope, errorLogEl, "message", errorLog.getMessage());
        addElementWithValue(envelope, errorLogEl, "code", errorLog.getCode());
        addElementWithValue(envelope, errorLogEl, "xRoadInstance", errorLog.getXRoadInstance());
        addElementWithValue(envelope, errorLogEl, "memberClass", errorLog.getMemberClass());
        addElementWithValue(envelope, errorLogEl, "memberCode", errorLog.getMemberCode());
        addElementWithValue(envelope, errorLogEl, "subsystemCode", errorLog.getSubsystemCode());
        addElementWithValue(envelope, errorLogEl, "groupCode", errorLog.getGroupCode());
        addElementWithValue(envelope, errorLogEl, "serviceCode", errorLog.getServiceCode());
        addElementWithValue(envelope, errorLogEl, "serviceVersion", errorLog.getServiceVersion());
        addElementWithValue(envelope, errorLogEl, "securityCategoryCode", errorLog.getSecurityCategoryCode());
        addElementWithValue(envelope, errorLogEl, "serverCode", errorLog.getServerCode());
        addElementWithValue(envelope, errorLogEl, "created", errorLog.getCreated());
    }
}
