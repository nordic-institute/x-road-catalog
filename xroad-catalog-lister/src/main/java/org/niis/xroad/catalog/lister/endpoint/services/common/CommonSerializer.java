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
import fi.dvv.xroad.catalog.persistence.entity.Address;
import fi.dvv.xroad.catalog.persistence.entity.BusinessAddress;
import fi.dvv.xroad.catalog.persistence.entity.BusinessAuxiliaryName;
import fi.dvv.xroad.catalog.persistence.entity.BusinessIdChange;
import fi.dvv.xroad.catalog.persistence.entity.BusinessLine;
import fi.dvv.xroad.catalog.persistence.entity.BusinessName;
import fi.dvv.xroad.catalog.persistence.entity.Company;
import fi.dvv.xroad.catalog.persistence.entity.CompanyForm;
import fi.dvv.xroad.catalog.persistence.entity.ContactDetail;
import fi.dvv.xroad.catalog.persistence.entity.Email;
import fi.dvv.xroad.catalog.persistence.entity.Language;
import fi.dvv.xroad.catalog.persistence.entity.Liquidation;
import fi.dvv.xroad.catalog.persistence.entity.Organization;
import fi.dvv.xroad.catalog.persistence.entity.OrganizationDescription;
import fi.dvv.xroad.catalog.persistence.entity.OrganizationName;
import fi.dvv.xroad.catalog.persistence.entity.PhoneNumber;
import fi.dvv.xroad.catalog.persistence.entity.PostOfficeBoxAddress;
import fi.dvv.xroad.catalog.persistence.entity.RegisteredEntry;
import fi.dvv.xroad.catalog.persistence.entity.RegisteredOffice;
import fi.dvv.xroad.catalog.persistence.entity.StreetAddress;
import fi.dvv.xroad.catalog.persistence.entity.WebPage;
import org.niis.xroad.catalog.persistence.entity.ErrorLog;
import org.niis.xroad.catalog.persistence.entity.Member;
import org.niis.xroad.catalog.persistence.entity.OpenApi;
import org.niis.xroad.catalog.persistence.entity.Service;
import org.niis.xroad.catalog.persistence.entity.StatusInfo;
import org.niis.xroad.catalog.persistence.entity.Subsystem;
import org.niis.xroad.catalog.persistence.entity.Wsdl;

import java.time.LocalDateTime;

@SuppressWarnings({"PMD.AvoidDuplicateLiterals", "PMD.CommentSize"})
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
     *
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

    /**
     * Serialize Organization into the following XML descriptor format.
     *
     * <pre>{@code
     * &lt;xs:complexType name="Organization"&gt;
     *     &lt;xs:sequence&gt;
     *         &lt;xs:element name="organizationType" type="xs:string"/&gt;
     *         &lt;xs:element name="publishingStatus" type="xs:string"/&gt;
     *         &lt;xs:element name="businessCode" type="xs:string"/&gt;
     *         &lt;xs:element name="guid" type="xs:string"/&gt;
     *         &lt;xs:element name="organizationNames" type="tns:OrganizationNameList"/&gt;
     *         &lt;xs:element name="organizationDescriptions" type="tns:OrganizationDescriptionList"/&gt;
     *         &lt;xs:element name="emails" type="tns:EmailList"/&gt;
     *         &lt;xs:element name="phoneNumbers" type="tns:PhoneNumberList"/&gt;
     *         &lt;xs:element name="webPages" type="tns:WebPageList"/&gt;
     *         &lt;xs:element name="addresses" type="tns:AddressList"/&gt;
     *         &lt;xs:element name="created" type="xs:dateTime"/&gt;
     *         &lt;xs:element name="changed" type="xs:dateTime"/&gt;
     *         &lt;xs:element name="fetched" type="xs:dateTime"/&gt;
     *         &lt;xs:element minOccurs="0" name="removed" type="xs:dateTime"/&gt;
     *     &lt;/xs:sequence&gt;
     * &lt;/xs:complexType&gt;
     * }</pre>
     */
    public static void serialize(final SOAPEnvelope envelope, final SOAPElement parent,
                                   final Organization organization) throws SOAPException {
        SOAPElement organizationEl = parent.addChildElement(envelope.createName("organization"));
        addElementWithValue(envelope, organizationEl, "organizationType", organization.getOrganizationType());
        addElementWithValue(envelope, organizationEl, "publishingStatus", organization.getPublishingStatus());
        addElementWithValue(envelope, organizationEl, "businessCode", organization.getBusinessCode());
        addElementWithValue(envelope, organizationEl, "guid", organization.getGuid());

        SOAPElement organizationNamesEl =
                organizationEl.addChildElement(envelope.createName("organizationNames"));
        for (var organizationName : organization.getAllOrganizationNames()) {
            serialize(envelope, organizationNamesEl, organizationName);
        }

        SOAPElement organizationDescriptionsEl =
                organizationEl.addChildElement(envelope.createName("organizationDescriptions"));
        for (var organizationDescription : organization.getAllOrganizationDescriptions()) {
            serialize(envelope, organizationDescriptionsEl, organizationDescription);
        }

        SOAPElement emailsEl = organizationEl.addChildElement(envelope.createName("emails"));
        for (var email : organization.getAllEmails()) {
            serialize(envelope, emailsEl, email);
        }

        SOAPElement phoneNumbersEl = organizationEl.addChildElement(envelope.createName("phoneNumbers"));
        for (var phoneNumber : organization.getAllPhoneNumbers()) {
            serialize(envelope, phoneNumbersEl, phoneNumber);
        }

        SOAPElement webPagesEl = organizationEl.addChildElement(envelope.createName("webPages"));
        for (var webPage : organization.getAllWebPages()) {
            serialize(envelope, webPagesEl, webPage);
        }

        SOAPElement addressesEl = organizationEl.addChildElement(envelope.createName("addresses"));
        for (var address : organization.getAllAddresses()) {
            serialize(envelope, addressesEl, address);
        }

        serialize(envelope, organizationEl, organization.getStatusInfo());
    }

    /**
     * Serialize OrganizationName into the following XML descriptor format.
     *
     * <pre>{@code
     * &lt;xs:complexType name="OrganizationName"&gt;
     *     &lt;xs:sequence&gt;
     *         &lt;xs:element name="language" type="xs:string"/&gt;
     *         &lt;xs:element name="type" type="xs:string"/&gt;
     *         &lt;xs:element name="value" type="xs:string"/&gt;
     *         &lt;xs:element name="created" type="xs:dateTime"/&gt;
     *         &lt;xs:element name="changed" type="xs:dateTime"/&gt;
     *         &lt;xs:element name="fetched" type="xs:dateTime"/&gt;
     *         &lt;xs:element minOccurs="0" name="removed" type="xs:dateTime"/&gt;
     *     &lt;/xs:sequence&gt;
     * &lt;/xs:complexType&gt;
     * }</pre>
     */
    public static void serialize(final SOAPEnvelope envelope, final SOAPElement parent,
                                   final OrganizationName organizationName) throws SOAPException {
        SOAPElement organizationNameEl = parent.addChildElement(envelope.createName("organizationName"));
        addElementWithValue(envelope, organizationNameEl, "language", organizationName.getLanguage());
        addElementWithValue(envelope, organizationNameEl, "type", organizationName.getType());
        addElementWithValue(envelope, organizationNameEl, "value", organizationName.getValue());
        serialize(envelope, organizationNameEl, organizationName.getStatusInfo());
    }

    /**
     * Serialize OrganizationDescription into the following XML descriptor format.
     *
     * <pre>{@code
     * &lt;xs:complexType name="OrganizationDescription"&gt;
     *     &lt;xs:sequence&gt;
     *         &lt;xs:element name="language" type="xs:string"/&gt;
     *         &lt;xs:element name="type" type="xs:string"/&gt;
     *         &lt;xs:element name="value" type="xs:string"/&gt;
     *         &lt;xs:element name="created" type="xs:dateTime"/&gt;
     *         &lt;xs:element name="changed" type="xs:dateTime"/&gt;
     *         &lt;xs:element name="fetched" type="xs:dateTime"/&gt;
     *         &lt;xs:element minOccurs="0" name="removed" type="xs:dateTime"/&gt;
     *     &lt;/xs:sequence&gt;
     * &lt;/xs:complexType&gt;
     * }</pre>
     */
    public static void serialize(final SOAPEnvelope envelope, final SOAPElement parent,
                                   final OrganizationDescription organizationDescription) throws SOAPException {
        SOAPElement organizationDescriptionEl =
                parent.addChildElement(envelope.createName("organizationDescription"));
        addElementWithValue(envelope, organizationDescriptionEl, "language", organizationDescription.getLanguage());
        addElementWithValue(envelope, organizationDescriptionEl, "type", organizationDescription.getType());
        addElementWithValue(envelope, organizationDescriptionEl, "value", organizationDescription.getValue());
        serialize(envelope, organizationDescriptionEl, organizationDescription.getStatusInfo());
    }

    /**
     * Serialize Email into the following XML descriptor format.
     *
     * <pre>{@code
     * &lt;xs:complexType name="Email"&gt;
     *     &lt;xs:sequence&gt;
     *         &lt;xs:element name="language" type="xs:string"/&gt;
     *         &lt;xs:element name="description" type="xs:string"/&gt;
     *         &lt;xs:element name="value" type="xs:string"/&gt;
     *         &lt;xs:element name="created" type="xs:dateTime"/&gt;
     *         &lt;xs:element name="changed" type="xs:dateTime"/&gt;
     *         &lt;xs:element name="fetched" type="xs:dateTime"/&gt;
     *         &lt;xs:element minOccurs="0" name="removed" type="xs:dateTime"/&gt;
     *     &lt;/xs:sequence&gt;
     * &lt;/xs:complexType&gt;
     * }</pre>
     */
    public static void serialize(final SOAPEnvelope envelope, final SOAPElement parent, final Email email)
            throws SOAPException {
        SOAPElement emailEl = parent.addChildElement(envelope.createName("email"));
        addElementWithValue(envelope, emailEl, "language", email.getLanguage());
        addElementWithValue(envelope, emailEl, "description", email.getDescription());
        addElementWithValue(envelope, emailEl, "value", email.getValue());
        serialize(envelope, emailEl, email.getStatusInfo());
    }

    /**
     * Serialize PhoneNumber into the following XML descriptor format.
     *
     * <pre>{@code
     * &lt;xs:complexType name="PhoneNumber"&gt;
     *     &lt;xs:sequence&gt;
     *         &lt;xs:element name="language" type="xs:string"/&gt;
     *         &lt;xs:element name="additionalInformation" type="xs:string"/&gt;
     *         &lt;xs:element name="serviceChargeType" type="xs:string"/&gt;
     *         &lt;xs:element name="chargeDescription" type="xs:string"/&gt;
     *         &lt;xs:element name="prefixNumber" type="xs:string"/&gt;
     *         &lt;xs:element name="number" type="xs:string"/&gt;
     *         &lt;xs:element name="isFinnishServiceNumber" type="xs:boolean"/&gt;
     *         &lt;xs:element name="created" type="xs:dateTime"/&gt;
     *         &lt;xs:element name="changed" type="xs:dateTime"/&gt;
     *         &lt;xs:element name="fetched" type="xs:dateTime"/&gt;
     *         &lt;xs:element minOccurs="0" name="removed" type="xs:dateTime"/&gt;
     *     &lt;/xs:sequence&gt;
     * &lt;/xs:complexType&gt;
     * }</pre>
     */
    public static void serialize(final SOAPEnvelope envelope, final SOAPElement parent,
                                   final PhoneNumber phoneNumber) throws SOAPException {
        SOAPElement phoneNumberEl = parent.addChildElement(envelope.createName("phoneNumber"));
        addElementWithValue(envelope, phoneNumberEl, "language", phoneNumber.getLanguage());
        addElementWithValue(envelope, phoneNumberEl, "additionalInformation",
                phoneNumber.getAdditionalInformation());
        addElementWithValue(envelope, phoneNumberEl, "serviceChargeType", phoneNumber.getServiceChargeType());
        addElementWithValue(envelope, phoneNumberEl, "chargeDescription", phoneNumber.getChargeDescription());
        addElementWithValue(envelope, phoneNumberEl, "prefixNumber", phoneNumber.getPrefixNumber());
        addElementWithValue(envelope, phoneNumberEl, "number", phoneNumber.getNumber());
        addElementWithValue(envelope, phoneNumberEl, "isFinnishServiceNumber",
                phoneNumber.getIsFinnishServiceNumber());
        serialize(envelope, phoneNumberEl, phoneNumber.getStatusInfo());
    }

    /**
     * Serialize WebPage into the following XML descriptor format.
     *
     * <pre>{@code
     * &lt;xs:complexType name="WebPage"&gt;
     *     &lt;xs:sequence&gt;
     *         &lt;xs:element name="language" type="xs:string"/&gt;
     *         &lt;xs:element name="url" type="xs:string"/&gt;
     *         &lt;xs:element name="value" type="xs:string"/&gt;
     *         &lt;xs:element name="created" type="xs:dateTime"/&gt;
     *         &lt;xs:element name="changed" type="xs:dateTime"/&gt;
     *         &lt;xs:element name="fetched" type="xs:dateTime"/&gt;
     *         &lt;xs:element minOccurs="0" name="removed" type="xs:dateTime"/&gt;
     *     &lt;/xs:sequence&gt;
     * &lt;/xs:complexType&gt;
     * }</pre>
     */
    public static void serialize(final SOAPEnvelope envelope, final SOAPElement parent, final WebPage webPage)
            throws SOAPException {
        SOAPElement webPageEl = parent.addChildElement(envelope.createName("webPage"));
        addElementWithValue(envelope, webPageEl, "language", webPage.getLanguage());
        addElementWithValue(envelope, webPageEl, "url", webPage.getUrl());
        addElementWithValue(envelope, webPageEl, "value", webPage.getValue());
        serialize(envelope, webPageEl, webPage.getStatusInfo());
    }

    /**
     * Serialize Address into the following XML descriptor format.
     *
     * <pre>{@code
     * &lt;xs:complexType name="Address"&gt;
     *     &lt;xs:sequence&gt;
     *         &lt;xs:element name="country" type="xs:string"/&gt;
     *         &lt;xs:element name="type" type="xs:string"/&gt;
     *         &lt;xs:element name="subType" type="xs:string"/&gt;
     *         &lt;xs:element name="streetAddresses" type="tns:StreetAddressList"/&gt;
     *         &lt;xs:element name="postOfficeBoxAddresses" type="tns:PostOfficeBoxAddressList"/&gt;
     *         &lt;xs:element name="created" type="xs:dateTime"/&gt;
     *         &lt;xs:element name="changed" type="xs:dateTime"/&gt;
     *         &lt;xs:element name="fetched" type="xs:dateTime"/&gt;
     *         &lt;xs:element minOccurs="0" name="removed" type="xs:dateTime"/&gt;
     *     &lt;/xs:sequence&gt;
     * &lt;/xs:complexType&gt;
     * }</pre>
     */
    public static void serialize(final SOAPEnvelope envelope, final SOAPElement parent, final Address address)
            throws SOAPException {
        SOAPElement addressEl = parent.addChildElement(envelope.createName("address"));
        addElementWithValue(envelope, addressEl, "country", address.getCountry());
        addElementWithValue(envelope, addressEl, "type", address.getType());
        addElementWithValue(envelope, addressEl, "subType", address.getSubType());

        SOAPElement streetAddressesEl = addressEl.addChildElement(envelope.createName("streetAddresses"));
        for (var streetAddress : address.getAllStreetAddresses()) {
            serialize(envelope, streetAddressesEl, streetAddress);
        }

        SOAPElement postOfficeBoxAddressesEl = addressEl.addChildElement(envelope.createName("postOfficeBoxAddresses"));
        for (var postOfficeBoxAddress : address.getAllPostOfficeBoxAddresses()) {
            serialize(envelope, postOfficeBoxAddressesEl, postOfficeBoxAddress);
        }

        serialize(envelope, addressEl, address.getStatusInfo());
    }

    /**
     * Serialize Company into the following XML descriptor format.
     *
     * <pre>{@code
     * &lt;xs:complexType name="Company"&gt;
     *     &lt;xs:sequence&gt;
     *         &lt;xs:element name="companyForm" type="xs:string"/&gt;
     *         &lt;xs:element name="detailsUri" type="xs:string"/&gt;
     *         &lt;xs:element name="businessId" type="xs:string"/&gt;
     *         &lt;xs:element name="name" type="xs:string"/&gt;
     *         &lt;xs:element name="registrationDate" type="xs:dateTime"/&gt;
     *         &lt;xs:element name="businessAddresses" type="tns:BusinessAddressList"/&gt;
     *         &lt;xs:element name="businessAuxiliaryNames" type="tns:BusinessAuxiliaryNameList"/&gt;
     *         &lt;xs:element name="businessIdChanges" type="tns:BusinessIdChangeList"/&gt;
     *         &lt;xs:element name="businessLines" type="tns:BusinessLineList"/&gt;
     *         &lt;xs:element name="businessNames" type="tns:BusinessNameList"/&gt;
     *         &lt;xs:element name="companyForms" type="tns:CompanyFormList"/&gt;
     *         &lt;xs:element name="contactDetails" type="tns:ContactDetailList"/&gt;
     *         &lt;xs:element name="languages" type="tns:LanguageList"/&gt;
     *         &lt;xs:element name="liquidations" type="tns:LiquidationList"/&gt;
     *         &lt;xs:element name="registeredEntries" type="tns:RegisteredEntryList"/&gt;
     *         &lt;xs:element name="registeredOffices" type="tns:RegisteredOfficeList"/&gt;
     *         &lt;xs:element name="created" type="xs:dateTime"/&gt;
     *         &lt;xs:element name="changed" type="xs:dateTime"/&gt;
     *         &lt;xs:element name="fetched" type="xs:dateTime"/&gt;
     *         &lt;xs:element minOccurs="0" name="removed" type="xs:dateTime"/&gt;
     *     &lt;/xs:sequence&gt;
     * &lt;/xs:complexType&gt;
     * }</pre>
     */
    public static void serialize(final SOAPEnvelope envelope, final SOAPElement parent, final Company company)
            throws SOAPException {
        SOAPElement companyEl = parent.addChildElement(envelope.createName("company"));
        addElementWithValue(envelope, companyEl, "companyForm", company.getCompanyForm());
        addElementWithValue(envelope, companyEl, "detailsUri", company.getDetailsUri());
        addElementWithValue(envelope, companyEl, "businessId", company.getBusinessId());
        addElementWithValue(envelope, companyEl, "name", company.getName());
        addElementWithValue(envelope, companyEl, "registrationDate", company.getRegistrationDate());

        SOAPElement businessAddressesEl =
                companyEl.addChildElement(envelope.createName("businessAddresses"));
        for (var businessAddress : company.getAllBusinessAddresses()) {
            serialize(envelope, businessAddressesEl, businessAddress);
        }

        SOAPElement businessAuxiliaryNamesEl =
                companyEl.addChildElement(envelope.createName("businessAuxiliaryNames"));
        for (var businessAuxiliaryName : company.getAllBusinessAuxiliaryNames()) {
            serialize(envelope, businessAuxiliaryNamesEl, businessAuxiliaryName);
        }

        SOAPElement businessIdChangesEl = companyEl.addChildElement(envelope.createName("businessIdChanges"));
        for (var businessIdChange : company.getAllBusinessIdChanges()) {
            serialize(envelope, businessIdChangesEl, businessIdChange);
        }

        SOAPElement businessLinesEl = companyEl.addChildElement(envelope.createName("businessLines"));
        for (var businessLine : company.getAllBusinessLines()) {
            serialize(envelope, businessLinesEl, businessLine);
        }

        SOAPElement businessNamesEl = companyEl.addChildElement(envelope.createName("businessNames"));
        for (var businessName : company.getAllBusinessNames()) {
            serialize(envelope, businessNamesEl, businessName);
        }

        SOAPElement companyFormsEl = companyEl.addChildElement(envelope.createName("companyForms"));
        for (var companyForm : company.getAllCompanyForms()) {
            serialize(envelope, companyFormsEl, companyForm);
        }

        SOAPElement contactDetailsEl = companyEl.addChildElement(envelope.createName("contactDetails"));
        for (var contactDetail : company.getAllContactDetails()) {
            serialize(envelope, contactDetailsEl, contactDetail);
        }

        SOAPElement languagesEl = companyEl.addChildElement(envelope.createName("languages"));
        for (var language : company.getAllLanguages()) {
            serialize(envelope, languagesEl, language);
        }

        SOAPElement liquidationsEl = companyEl.addChildElement(envelope.createName("liquidations"));
        for (var liquidation : company.getAllLiquidations()) {
            serialize(envelope, liquidationsEl, liquidation);
        }

        SOAPElement registeredEntriesEl = companyEl.addChildElement(envelope.createName("registeredEntries"));
        for (var registeredEntry : company.getAllRegisteredEntries()) {
            serialize(envelope, registeredEntriesEl, registeredEntry);
        }

        SOAPElement registeredOfficesEl = companyEl.addChildElement(envelope.createName("registeredOffices"));
        for (var registeredOffice : company.getAllRegisteredOffices()) {
            serialize(envelope, registeredOfficesEl, registeredOffice);
        }

        serialize(envelope, companyEl, company.getStatusInfo());
    }

    /**
     * Serialize BusinessName into the following XML descriptor format.
     *
     * <pre>{@code
     * &lt;xs:complexType name="BusinessName"&gt;
     *     &lt;xs:sequence&gt;
     *         &lt;xs:element name="source" type="xs:long"/&gt;
     *         &lt;xs:element name="ordering" type="xs:long"/&gt;
     *         &lt;xs:element name="version" type="xs:long"/&gt;
     *         &lt;xs:element name="name" type="xs:string"/&gt;
     *         &lt;xs:element name="language" type="xs:string"/&gt;
     *         &lt;xs:element name="registrationDate" type="xs:dateTime"/&gt;
     *         &lt;xs:element name="endDate" type="xs:dateTime"/&gt;
     *         &lt;xs:element name="created" type="xs:dateTime"/&gt;
     *         &lt;xs:element name="changed" type="xs:dateTime"/&gt;
     *         &lt;xs:element name="fetched" type="xs:dateTime"/&gt;
     *         &lt;xs:element minOccurs="0" name="removed" type="xs:dateTime"/&gt;
     *     &lt;/xs:sequence&gt;
     * &lt;/xs:complexType&gt;
     * }</pre>
     */
    public static void serialize(final SOAPEnvelope envelope, final SOAPElement parent,
                                   final BusinessName businessName) throws SOAPException {
        SOAPElement businessNameEl = parent.addChildElement(envelope.createName("businessName"));
        addElementWithValue(envelope, businessNameEl, "source", businessName.getSource());
        addElementWithValue(envelope, businessNameEl, "ordering", businessName.getOrdering());
        addElementWithValue(envelope, businessNameEl, "version", businessName.getVersion());
        addElementWithValue(envelope, businessNameEl, "name", businessName.getName());
        addElementWithValue(envelope, businessNameEl, "language", businessName.getLanguage());
        addElementWithValue(envelope, businessNameEl, "registrationDate", businessName.getRegistrationDate());
        addElementWithValue(envelope, businessNameEl, "endDate", businessName.getEndDate());
        serialize(envelope, businessNameEl, businessName.getStatusInfo());
    }

    /**
     * Serialize ContactDetail into the following XML descriptor format.
     *
     * <pre>{@code
     * &lt;xs:complexType name="ContactDetail"&gt;
     *     &lt;xs:sequence&gt;
     *         &lt;xs:element name="source" type="xs:long"/&gt;
     *         &lt;xs:element name="version" type="xs:long"/&gt;
     *         &lt;xs:element name="language" type="xs:string"/&gt;
     *         &lt;xs:element name="value" type="xs:string"/&gt;
     *         &lt;xs:element name="type" type="xs:string"/&gt;
     *         &lt;xs:element name="registrationDate" type="xs:dateTime"/&gt;
     *         &lt;xs:element name="endDate" type="xs:dateTime"/&gt;
     *         &lt;xs:element name="created" type="xs:dateTime"/&gt;
     *         &lt;xs:element name="changed" type="xs:dateTime"/&gt;
     *         &lt;xs:element name="fetched" type="xs:dateTime"/&gt;
     *         &lt;xs:element minOccurs="0" name="removed" type="xs:dateTime"/&gt;
     *     &lt;/xs:sequence&gt;
     * &lt;/xs:complexType&gt;
     * }</pre>
     */
    public static void serialize(final SOAPEnvelope envelope, final SOAPElement parent,
                                   final ContactDetail contactDetail) throws SOAPException {
        SOAPElement contactDetailEl = parent.addChildElement(envelope.createName("contactDetail"));
        addElementWithValue(envelope, contactDetailEl, "source", contactDetail.getSource());
        addElementWithValue(envelope, contactDetailEl, "version", contactDetail.getVersion());
        addElementWithValue(envelope, contactDetailEl, "language", contactDetail.getLanguage());
        addElementWithValue(envelope, contactDetailEl, "value", contactDetail.getValue());
        addElementWithValue(envelope, contactDetailEl, "type", contactDetail.getType());
        addElementWithValue(envelope, contactDetailEl, "registrationDate", contactDetail.getRegistrationDate());
        addElementWithValue(envelope, contactDetailEl, "endDate", contactDetail.getEndDate());
        serialize(envelope, contactDetailEl, contactDetail.getStatusInfo());
    }

    /**
     * Serialize BusinessAddress into the following XML descriptor format.
     *
     * <pre>{@code
     * &lt;xs:complexType name="BusinessAddress"&gt;
     *     &lt;xs:sequence&gt;
     *         &lt;xs:element name="source" type="xs:long"/&gt;
     *         &lt;xs:element name="version" type="xs:long"/&gt;
     *         &lt;xs:element name="careOf" type="xs:string"/&gt;
     *         &lt;xs:element name="street" type="xs:string"/&gt;
     *         &lt;xs:element name="postCode" type="xs:string"/&gt;
     *         &lt;xs:element name="city" type="xs:string"/&gt;
     *         &lt;xs:element name="language" type="xs:string"/&gt;
     *         &lt;xs:element name="type" type="xs:long"/&gt;
     *         &lt;xs:element name="country" type="xs:string"/&gt;
     *         &lt;xs:element name="registrationDate" type="xs:dateTime"/&gt;
     *         &lt;xs:element name="endDate" type="xs:dateTime"/&gt;
     *         &lt;xs:element name="created" type="xs:dateTime"/&gt;
     *         &lt;xs:element name="changed" type="xs:dateTime"/&gt;
     *         &lt;xs:element name="fetched" type="xs:dateTime"/&gt;
     *         &lt;xs:element minOccurs="0" name="removed" type="xs:dateTime"/&gt;
     *     &lt;/xs:sequence&gt;
     * &lt;/xs:complexType&gt;
     * }</pre>
     */
    public static void serialize(final SOAPEnvelope envelope, final SOAPElement parent,
                                   final BusinessAddress businessAddress) throws SOAPException {
        SOAPElement businessAddressEl = parent.addChildElement(envelope.createName("businessAddress"));
        addElementWithValue(envelope, businessAddressEl, "source", businessAddress.getSource());
        addElementWithValue(envelope, businessAddressEl, "version", businessAddress.getVersion());
        addElementWithValue(envelope, businessAddressEl, "careOf", businessAddress.getCareOf());
        addElementWithValue(envelope, businessAddressEl, "street", businessAddress.getStreet());
        addElementWithValue(envelope, businessAddressEl, "postCode", businessAddress.getPostCode());
        addElementWithValue(envelope, businessAddressEl, "city", businessAddress.getCity());
        addElementWithValue(envelope, businessAddressEl, "language", businessAddress.getLanguage());
        addElementWithValue(envelope, businessAddressEl, "type", businessAddress.getType());
        addElementWithValue(envelope, businessAddressEl, "country", businessAddress.getCountry());
        addElementWithValue(envelope, businessAddressEl, "registrationDate", businessAddress.getRegistrationDate());
        addElementWithValue(envelope, businessAddressEl, "endDate", businessAddress.getEndDate());
        serialize(envelope, businessAddressEl, businessAddress.getStatusInfo());
    }

    /**
     * Serialize BusinessAuxiliaryName into the following XML descriptor format.
     *
     * <pre>{@code
     * &lt;xs:complexType name="BusinessAuxiliaryName"&gt;
     *     &lt;xs:sequence&gt;
     *         &lt;xs:element name="source" type="xs:long"/&gt;
     *         &lt;xs:element name="ordering" type="xs:long"/&gt;
     *         &lt;xs:element name="version" type="xs:long"/&gt;
     *         &lt;xs:element name="name" type="xs:string"/&gt;
     *         &lt;xs:element name="language" type="xs:string"/&gt;
     *         &lt;xs:element name="registrationDate" type="xs:dateTime"/&gt;
     *         &lt;xs:element name="endDate" type="xs:dateTime"/&gt;
     *         &lt;xs:element name="created" type="xs:dateTime"/&gt;
     *         &lt;xs:element name="changed" type="xs:dateTime"/&gt;
     *         &lt;xs:element name="fetched" type="xs:dateTime"/&gt;
     *         &lt;xs:element minOccurs="0" name="removed" type="xs:dateTime"/&gt;
     *     &lt;/xs:sequence&gt;
     * &lt;/xs:complexType&gt;
     * }</pre>
     */
    public static void serialize(final SOAPEnvelope envelope, final SOAPElement parent,
                                   final BusinessAuxiliaryName businessAuxiliaryName) throws SOAPException {
        SOAPElement businessAuxiliaryNameEl = parent.addChildElement(envelope.createName("businessAuxiliaryName"));
        addElementWithValue(envelope, businessAuxiliaryNameEl, "source", businessAuxiliaryName.getSource());
        addElementWithValue(envelope, businessAuxiliaryNameEl, "ordering", businessAuxiliaryName.getOrdering());
        addElementWithValue(envelope, businessAuxiliaryNameEl, "version", businessAuxiliaryName.getVersion());
        addElementWithValue(envelope, businessAuxiliaryNameEl, "name", businessAuxiliaryName.getName());
        addElementWithValue(envelope, businessAuxiliaryNameEl, "language", businessAuxiliaryName.getLanguage());
        addElementWithValue(envelope, businessAuxiliaryNameEl, "registrationDate", businessAuxiliaryName.getRegistrationDate());
        addElementWithValue(envelope, businessAuxiliaryNameEl, "endDate", businessAuxiliaryName.getEndDate());
        serialize(envelope, businessAuxiliaryNameEl, businessAuxiliaryName.getStatusInfo());
    }

    /**
     * Serialize BusinessIdChange into the following XML descriptor format.
     *
     * <pre>{@code
     * &lt;xs:complexType name="BusinessIdChange"&gt;
     *     &lt;xs:sequence&gt;
     *         &lt;xs:element name="source" type="xs:long"/&gt;
     *         &lt;xs:element name="description" type="xs:string"/&gt;
     *         &lt;xs:element name="reason" type="xs:string"/&gt;
     *         &lt;xs:element name="changeDate" type="xs:string"/&gt;
     *         &lt;xs:element name="change" type="xs:string"/&gt;
     *         &lt;xs:element name="oldBusinessId" type="xs:string"/&gt;
     *         &lt;xs:element name="newBusinessId" type="xs:string"/&gt;
     *         &lt;xs:element name="language" type="xs:string"/&gt;
     *         &lt;xs:element name="created" type="xs:dateTime"/&gt;
     *         &lt;xs:element name="changed" type="xs:dateTime"/&gt;
     *         &lt;xs:element name="fetched" type="xs:dateTime"/&gt;
     *         &lt;xs:element minOccurs="0" name="removed" type="xs:dateTime"/&gt;
     *     &lt;/xs:sequence&gt;
     * &lt;/xs:complexType&gt;
     * }</pre>
     */
    public static void serialize(final SOAPEnvelope envelope, final SOAPElement parent,
                                   final BusinessIdChange businessIdChange) throws SOAPException {
        SOAPElement businessIdChangeEl = parent.addChildElement(envelope.createName("businessIdChange"));
        addElementWithValue(envelope, businessIdChangeEl, "source", businessIdChange.getSource());
        addElementWithValue(envelope, businessIdChangeEl, "description", businessIdChange.getDescription());
        addElementWithValue(envelope, businessIdChangeEl, "reason", businessIdChange.getReason());
        addElementWithValue(envelope, businessIdChangeEl, "changeDate", businessIdChange.getChangeDate());
        addElementWithValue(envelope, businessIdChangeEl, "change", businessIdChange.getChange());
        addElementWithValue(envelope, businessIdChangeEl, "oldBusinessId", businessIdChange.getOldBusinessId());
        addElementWithValue(envelope, businessIdChangeEl, "newBusinessId", businessIdChange.getNewBusinessId());
        addElementWithValue(envelope, businessIdChangeEl, "language", businessIdChange.getLanguage());
        serialize(envelope, businessIdChangeEl, businessIdChange.getStatusInfo());
    }

    /**
     * Serialize BusinessLine into the following XML descriptor format.
     *
     * <pre>{@code
     * &lt;xs:complexType name="BusinessLine"&gt;
     *     &lt;xs:sequence&gt;
     *         &lt;xs:element name="source" type="xs:long"/&gt;
     *         &lt;xs:element name="ordering" type="xs:long"/&gt;
     *         &lt;xs:element name="version" type="xs:long"/&gt;
     *         &lt;xs:element name="name" type="xs:string"/&gt;
     *         &lt;xs:element name="language" type="xs:string"/&gt;
     *         &lt;xs:element name="registrationDate" type="xs:dateTime"/&gt;
     *         &lt;xs:element name="endDate" type="xs:dateTime"/&gt;
     *         &lt;xs:element name="created" type="xs:dateTime"/&gt;
     *         &lt;xs:element name="changed" type="xs:dateTime"/&gt;
     *         &lt;xs:element name="fetched" type="xs:dateTime"/&gt;
     *         &lt;xs:element minOccurs="0" name="removed" type="xs:dateTime"/&gt;
     *     &lt;/xs:sequence&gt;
     * &lt;/xs:complexType&gt;
     * }</pre>
     */
    public static void serialize(final SOAPEnvelope envelope, final SOAPElement parent,
                                   final BusinessLine businessLine) throws SOAPException {
        SOAPElement businessLineEl = parent.addChildElement(envelope.createName("businessLine"));
        addElementWithValue(envelope, businessLineEl, "source", businessLine.getSource());
        addElementWithValue(envelope, businessLineEl, "ordering", businessLine.getOrdering());
        addElementWithValue(envelope, businessLineEl, "version", businessLine.getVersion());
        addElementWithValue(envelope, businessLineEl, "name", businessLine.getName());
        addElementWithValue(envelope, businessLineEl, "language", businessLine.getLanguage());
        addElementWithValue(envelope, businessLineEl, "registrationDate", businessLine.getRegistrationDate());
        addElementWithValue(envelope, businessLineEl, "endDate", businessLine.getEndDate());
        serialize(envelope, businessLineEl, businessLine.getStatusInfo());
    }

    /**
     * Serialize CompanyForm into the following XML descriptor format.
     *
     * <pre>{@code
     * &lt;xs:complexType name="CompanyForm"&gt;
     *     &lt;xs:sequence&gt;
     *         &lt;xs:element name="source" type="xs:long"/&gt;
     *         &lt;xs:element name="version" type="xs:long"/&gt;
     *         &lt;xs:element name="name" type="xs:string"/&gt;
     *         &lt;xs:element name="language" type="xs:string"/&gt;
     *         &lt;xs:element name="type" type="xs:long"/&gt;
     *         &lt;xs:element name="registrationDate" type="xs:dateTime"/&gt;
     *         &lt;xs:element name="endDate" type="xs:dateTime"/&gt;
     *         &lt;xs:element name="created" type="xs:dateTime"/&gt;
     *         &lt;xs:element name="changed" type="xs:dateTime"/&gt;
     *         &lt;xs:element name="fetched" type="xs:dateTime"/&gt;
     *         &lt;xs:element minOccurs="0" name="removed" type="xs:dateTime"/&gt;
     *     &lt;/xs:sequence&gt;
     * &lt;/xs:complexType&gt;
     * }</pre>
     */
    public static void serialize(final SOAPEnvelope envelope, final SOAPElement parent,
                                   final CompanyForm companyForm) throws SOAPException {
        SOAPElement companyFormEl = parent.addChildElement(envelope.createName("companyForm"));
        addElementWithValue(envelope, companyFormEl, "source", companyForm.getSource());
        addElementWithValue(envelope, companyFormEl, "version", companyForm.getVersion());
        addElementWithValue(envelope, companyFormEl, "name", companyForm.getName());
        addElementWithValue(envelope, companyFormEl, "language", companyForm.getLanguage());
        addElementWithValue(envelope, companyFormEl, "type", companyForm.getType());
        addElementWithValue(envelope, companyFormEl, "registrationDate", companyForm.getRegistrationDate());
        addElementWithValue(envelope, companyFormEl, "endDate", companyForm.getEndDate());
        serialize(envelope, companyFormEl, companyForm.getStatusInfo());
    }

    /**
     * Serialize Language into the following XML descriptor format.
     *
     * <pre>{@code
     * &lt;xs:complexType name="Language"&gt;
     *     &lt;xs:sequence&gt;
     *         &lt;xs:element name="source" type="xs:long"/&gt;
     *         &lt;xs:element name="version" type="xs:long"/&gt;
     *         &lt;xs:element name="name" type="xs:string"/&gt;
     *         &lt;xs:element name="language" type="xs:string"/&gt;
     *         &lt;xs:element name="registrationDate" type="xs:dateTime"/&gt;
     *         &lt;xs:element name="endDate" type="xs:dateTime"/&gt;
     *         &lt;xs:element name="created" type="xs:dateTime"/&gt;
     *         &lt;xs:element name="changed" type="xs:dateTime"/&gt;
     *         &lt;xs:element name="fetched" type="xs:dateTime"/&gt;
     *         &lt;xs:element minOccurs="0" name="removed" type="xs:dateTime"/&gt;
     *     &lt;/xs:sequence&gt;
     * &lt;/xs:complexType&gt;
     * }</pre>
     */
    public static void serialize(final SOAPEnvelope envelope, final SOAPElement parent, final Language language)
            throws SOAPException {
        SOAPElement languageEl = parent.addChildElement(envelope.createName("language"));
        addElementWithValue(envelope, languageEl, "source", language.getSource());
        addElementWithValue(envelope, languageEl, "version", language.getVersion());
        addElementWithValue(envelope, languageEl, "name", language.getName());
        addElementWithValue(envelope, languageEl, "language", language.getLanguage());
        addElementWithValue(envelope, languageEl, "registrationDate", language.getRegistrationDate());
        addElementWithValue(envelope, languageEl, "endDate", language.getEndDate());
        serialize(envelope, languageEl, language.getStatusInfo());
    }

    /**
     * Serialize Liquidation into the following XML descriptor format.
     *
     * <pre>{@code
     * &lt;xs:complexType name="Liquidation"&gt;
     *     &lt;xs:sequence&gt;
     *         &lt;xs:element name="source" type="xs:long"/&gt;
     *         &lt;xs:element name="version" type="xs:long"/&gt;
     *         &lt;xs:element name="name" type="xs:string"/&gt;
     *         &lt;xs:element name="language" type="xs:string"/&gt;
     *         &lt;xs:element name="type" type="xs:long"/&gt;
     *         &lt;xs:element name="registrationDate" type="xs:dateTime"/&gt;
     *         &lt;xs:element name="endDate" type="xs:dateTime"/&gt;
     *         &lt;xs:element name="created" type="xs:dateTime"/&gt;
     *         &lt;xs:element name="changed" type="xs:dateTime"/&gt;
     *         &lt;xs:element name="fetched" type="xs:dateTime"/&gt;
     *         &lt;xs:element minOccurs="0" name="removed" type="xs:dateTime"/&gt;
     *     &lt;/xs:sequence&gt;
     * &lt;/xs:complexType&gt;
     * }</pre>
     */
    public static void serialize(final SOAPEnvelope envelope, final SOAPElement parent,
                                   final Liquidation liquidation) throws SOAPException {
        SOAPElement liquidationEl = parent.addChildElement(envelope.createName("liquidation"));
        addElementWithValue(envelope, liquidationEl, "source", liquidation.getSource());
        addElementWithValue(envelope, liquidationEl, "version", liquidation.getVersion());
        addElementWithValue(envelope, liquidationEl, "name", liquidation.getName());
        addElementWithValue(envelope, liquidationEl, "language", liquidation.getLanguage());
        addElementWithValue(envelope, liquidationEl, "type", liquidation.getType());
        addElementWithValue(envelope, liquidationEl, "registrationDate", liquidation.getRegistrationDate());
        addElementWithValue(envelope, liquidationEl, "endDate", liquidation.getEndDate());
        serialize(envelope, liquidationEl, liquidation.getStatusInfo());
    }

    /**
     * Serialize RegisteredEntry into the following XML descriptor format.
     *
     * <pre>{@code
     * &lt;xs:complexType name="RegisteredEntry"&gt;
     *     &lt;xs:sequence&gt;
     *         &lt;xs:element name="description" type="xs:string"/&gt;
     *         &lt;xs:element name="status" type="xs:long"/&gt;
     *         &lt;xs:element name="register" type="xs:long"/&gt;
     *         &lt;xs:element name="language" type="xs:string"/&gt;
     *         &lt;xs:element name="authority" type="xs:long"/&gt;
     *         &lt;xs:element name="registrationDate" type="xs:dateTime"/&gt;
     *         &lt;xs:element name="endDate" type="xs:dateTime"/&gt;
     *         &lt;xs:element name="created" type="xs:dateTime"/&gt;
     *         &lt;xs:element name="changed" type="xs:dateTime"/&gt;
     *         &lt;xs:element name="fetched" type="xs:dateTime"/&gt;
     *         &lt;xs:element minOccurs="0" name="removed" type="xs:dateTime"/&gt;
     *     &lt;/xs:sequence&gt;
     * &lt;/xs:complexType&gt;
     * }</pre>
     */
    public static void serialize(final SOAPEnvelope envelope, final SOAPElement parent,
                                   final RegisteredEntry registeredEntry) throws SOAPException {
        SOAPElement registeredEntryEl = parent.addChildElement(envelope.createName("registeredEntry"));
        addElementWithValue(envelope, registeredEntryEl, "description", registeredEntry.getDescription());
        addElementWithValue(envelope, registeredEntryEl, "status", registeredEntry.getStatus());
        addElementWithValue(envelope, registeredEntryEl, "register", registeredEntry.getRegister());
        addElementWithValue(envelope, registeredEntryEl, "language", registeredEntry.getLanguage());
        addElementWithValue(envelope, registeredEntryEl, "authority", registeredEntry.getAuthority());
        addElementWithValue(envelope, registeredEntryEl, "registrationDate", registeredEntry.getRegistrationDate());
        addElementWithValue(envelope, registeredEntryEl, "endDate", registeredEntry.getEndDate());
        serialize(envelope, registeredEntryEl, registeredEntry.getStatusInfo());
    }

    /**
     * Serialize RegisteredOffice into the following XML descriptor format.
     *
     * <pre>{@code
     * &lt;xs:complexType name="RegisteredOffice"&gt;
     *     &lt;xs:sequence&gt;
     *         &lt;xs:element name="source" type="xs:long"/&gt;
     *         &lt;xs:element name="ordering" type="xs:long"/&gt;
     *         &lt;xs:element name="version" type="xs:long"/&gt;
     *         &lt;xs:element name="name" type="xs:string"/&gt;
     *         &lt;xs:element name="language" type="xs:string"/&gt;
     *         &lt;xs:element name="registrationDate" type="xs:dateTime"/&gt;
     *         &lt;xs:element name="endDate" type="xs:dateTime"/&gt;
     *         &lt;xs:element name="created" type="xs:dateTime"/&gt;
     *         &lt;xs:element name="changed" type="xs:dateTime"/&gt;
     *         &lt;xs:element name="fetched" type="xs:dateTime"/&gt;
     *         &lt;xs:element minOccurs="0" name="removed" type="xs:dateTime"/&gt;
     *     &lt;/xs:sequence&gt;
     * &lt;/xs:complexType&gt;
     * }</pre>
     */
    public static void serialize(final SOAPEnvelope envelope, final SOAPElement parent,
                                   final RegisteredOffice registeredOffice) throws SOAPException {
        SOAPElement registeredOfficeEl = parent.addChildElement(envelope.createName("registeredOffice"));
        addElementWithValue(envelope, registeredOfficeEl, "source", registeredOffice.getSource());
        addElementWithValue(envelope, registeredOfficeEl, "ordering", registeredOffice.getOrdering());
        addElementWithValue(envelope, registeredOfficeEl, "version", registeredOffice.getVersion());
        addElementWithValue(envelope, registeredOfficeEl, "name", registeredOffice.getName());
        addElementWithValue(envelope, registeredOfficeEl, "language", registeredOffice.getLanguage());
        addElementWithValue(envelope, registeredOfficeEl, "registrationDate", registeredOffice.getRegistrationDate());
        addElementWithValue(envelope, registeredOfficeEl, "endDate", registeredOffice.getEndDate());
        serialize(envelope, registeredOfficeEl, registeredOffice.getStatusInfo());
    }

    /**
     * Serialize StreetAddress into the following XML descriptor format.
     *
     * <pre>{@code
     * &lt;xs:complexType name="StreetAddress"&gt;
     *     &lt;xs:sequence&gt;
     *         &lt;xs:element name="streetNumber" type="xs:string"/&gt;
     *         &lt;xs:element name="postalCode" type="xs:string"/&gt;
     *         &lt;xs:element name="latitude" type="xs:string"/&gt;
     *         &lt;xs:element name="longitude" type="xs:string"/&gt;
     *         &lt;xs:element name="coordinateState" type="xs:string"/&gt;
     *         &lt;xs:element name="created" type="xs:dateTime"/&gt;
     *         &lt;xs:element name="changed" type="xs:dateTime"/&gt;
     *         &lt;xs:element name="fetched" type="xs:dateTime"/&gt;
     *         &lt;xs:element minOccurs="0" name="removed" type="xs:dateTime"/&gt;
     *     &lt;/xs:sequence&gt;
     * &lt;/xs:complexType&gt;
     * }</pre>
     */
    public static void serialize(final SOAPEnvelope envelope, final SOAPElement parent,
                                   final StreetAddress streetAddress) throws SOAPException {
        SOAPElement streetAddressEl = parent.addChildElement(envelope.createName("streetAddress"));
        addElementWithValue(envelope, streetAddressEl, "streetNumber", streetAddress.getStreetNumber());
        addElementWithValue(envelope, streetAddressEl, "postalCode", streetAddress.getPostalCode());
        addElementWithValue(envelope, streetAddressEl, "latitude", streetAddress.getLatitude());
        addElementWithValue(envelope, streetAddressEl, "longitude", streetAddress.getLongitude());
        addElementWithValue(envelope, streetAddressEl, "coordinateState", streetAddress.getCoordinateState());
        serialize(envelope, streetAddressEl, streetAddress.getStatusInfo());
    }

    /**
     * Serialize PostOfficeBoxAddress into the following XML descriptor format.
     *
     * <pre>{@code
     * &lt;xs:complexType name="PostOfficeBoxAddress"&gt;
     *     &lt;xs:sequence&gt;
     *         &lt;xs:element name="postalCode" type="xs:string"/&gt;
     *         &lt;xs:element name="created" type="xs:dateTime"/&gt;
     *         &lt;xs:element name="changed" type="xs:dateTime"/&gt;
     *         &lt;xs:element name="fetched" type="xs:dateTime"/&gt;
     *         &lt;xs:element minOccurs="0" name="removed" type="xs:dateTime"/&gt;
     *     &lt;/xs:sequence&gt;
     * &lt;/xs:complexType&gt;
     * }</pre>
     */
    public static void serialize(final SOAPEnvelope envelope, final SOAPElement parent,
                                   final PostOfficeBoxAddress postOfficeBoxAddress) throws SOAPException {
        SOAPElement postOfficeBoxAddressEl = parent.addChildElement(envelope.createName("postOfficeBoxAddress"));
        addElementWithValue(envelope, postOfficeBoxAddressEl, "postalCode", postOfficeBoxAddress.getPostalCode());
        serialize(envelope, postOfficeBoxAddressEl, postOfficeBoxAddress.getStatusInfo());
    }

}
