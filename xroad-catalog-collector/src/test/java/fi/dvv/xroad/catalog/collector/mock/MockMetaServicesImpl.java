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
package fi.dvv.xroad.catalog.collector.mock;

import jakarta.annotation.Resource;
import jakarta.jws.WebService;
import jakarta.xml.ws.Holder;
import jakarta.xml.ws.WebServiceContext;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.catalog.collector.wsimport.AllowedMethods;
import org.niis.xroad.catalog.collector.wsimport.AllowedMethodsResponse;
import org.niis.xroad.catalog.collector.wsimport.GetWsdl;
import org.niis.xroad.catalog.collector.wsimport.GetWsdlResponse;
import org.niis.xroad.catalog.collector.wsimport.ListMethods;
import org.niis.xroad.catalog.collector.wsimport.ListMethodsResponse;
import org.niis.xroad.catalog.collector.wsimport.MetaServicesPort;
import org.niis.xroad.catalog.collector.wsimport.XRoadClientIdentifierType;
import org.niis.xroad.catalog.collector.wsimport.XRoadObjectType;
import org.niis.xroad.catalog.collector.wsimport.XRoadServiceIdentifierType;

import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;

/**
 * Mock metaservices -service which answers something valid and semi-reasonable
 * dummy data when queried for listMethods
 */
@WebService(serviceName = "producerPortService", targetNamespace = "http://metadata.x-road.eu/", wsdlLocation = "schema/metaservices.wsdl")
@Slf4j
public class MockMetaServicesImpl implements MetaServicesPort {

    @Resource
    private WebServiceContext ctx;

    @Override
    public AllowedMethodsResponse allowedMethods(AllowedMethods allowedMethods,
            Holder<XRoadClientIdentifierType> client,
            Holder<XRoadServiceIdentifierType> service,
            Holder<String> userId,
            Holder<String> id, Holder<String> protocolVersion) {
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public ListMethodsResponse listMethods(ListMethods listMethods,
            Holder<XRoadClientIdentifierType> client,
            Holder<XRoadServiceIdentifierType> service,
            Holder<String> userId,
            Holder<String> id,
            Holder<String> protocolVersion) {
        ListMethodsResponse response = new ListMethodsResponse();
        response.getService().add(generateService("testServiceFoo", "v1", service.value));
        response.getService().add(generateService("testServiceBar", "v1", service.value));
        response.getService().add(generateService("testServiceBaz", "v1", service.value));
        return response;
    }

    @Override
    public void getWsdl(GetWsdl getWsdl, Holder<XRoadClientIdentifierType> client,
            Holder<XRoadServiceIdentifierType> service, Holder<String> userId,
            Holder<String> id,
            Holder<String> protocolVersion, Holder<GetWsdlResponse> getWsdlResponse,
            Holder<byte[]> wsdl) {

        final GetWsdlResponse response = new GetWsdlResponse();
        response.setServiceCode(getWsdl.getServiceCode());
        response.setServiceVersion(getWsdl.getServiceVersion());
        getWsdlResponse.value = response;
        final String tmp = getWSDLForService(getWsdl.getServiceCode(), getWsdl.getServiceVersion());
        wsdl.value = tmp.getBytes(StandardCharsets.UTF_8);
    }

    private XRoadServiceIdentifierType generateService(String serviceCode,
            String serviceVersion,
            XRoadServiceIdentifierType serviceHeader) {
        XRoadServiceIdentifierType service = new XRoadServiceIdentifierType();
        service.setXRoadInstance(serviceHeader.getXRoadInstance());
        service.setMemberClass(serviceHeader.getMemberClass());
        service.setMemberCode(serviceHeader.getMemberCode());
        service.setSubsystemCode(serviceHeader.getSubsystemCode());
        service.setServiceCode(serviceCode);
        service.setServiceVersion(serviceVersion);
        service.setObjectType(XRoadObjectType.SERVICE);
        return service;
    }

    public static String getWSDLForService(String serviceCode, String serviceVersion) {
        return MessageFormat.format(WSDL_TEMPLATE, serviceCode, serviceVersion);
    }

    private static final String WSDL_TEMPLATE =
                    """
                    <wsdl:definitions xmlns:soap="http://schemas.xmlsoap.org/wsdl/soap/"
                                      xmlns:tns="http://vrk-test.x-road.fi/producer"
                                      xmlns:wsdl="http://schemas.xmlsoap.org/wsdl/"
                                      xmlns:xrd="http://x-road.eu/xsd/xroad.xsd"
                                      xmlns:xsd="http://www.w3.org/2001/XMLSchema"
                                      xmlns:id="http://x-road.eu/xsd/identifiers"
                                      name="testService" targetNamespace="http://vrk-test.x-road.fi/producer">
                        <wsdl:types>
                            <!-- Schema for identifiers (reduced) -->
                            <xsd:schema elementFormDefault="qualified"
                                        targetNamespace="http://x-road.eu/xsd/identifiers"
                                        xmlns="http://x-road.eu/xsd/identifiers">
                                <xsd:simpleType name="XRoadObjectType">
                                    <xsd:annotation>
                                        <xsd:documentation>Enumeration for X-Road identifier
                                            types that can be used in requests.
                                        </xsd:documentation>
                                    </xsd:annotation>
                                    <xsd:restriction base="xsd:string">
                                        <xsd:enumeration value="MEMBER" />
                                        <xsd:enumeration value="SUBSYSTEM" />
                                        <xsd:enumeration value="SERVICE" />
                                    </xsd:restriction>
                                </xsd:simpleType>
                                <xsd:element name="xRoadInstance" type="xsd:string">
                                    <xsd:annotation>
                                        <xsd:documentation>Identifies the X-Road instance.
                                            This field is applicable to all identifier
                                            types.
                                        </xsd:documentation>
                                    </xsd:annotation>
                                </xsd:element>
                                <xsd:element name="memberClass" type="xsd:string">
                                    <xsd:annotation>
                                        <xsd:documentation>Type of the member (company,
                                            government institution, private person, etc.)
                                        </xsd:documentation>
                                    </xsd:annotation>
                                </xsd:element>
                                <xsd:element name="memberCode" type="xsd:string">
                                    <xsd:annotation>
                                        <xsd:documentation>Code that uniquely identifies a
                                            member of given member type.
                                        </xsd:documentation>
                                    </xsd:annotation>
                                </xsd:element>
                                <xsd:element name="subsystemCode" type="xsd:string">
                                    <xsd:annotation>
                                        <xsd:documentation>Code that uniquely identifies a
                                            subsystem of given SDSB member.
                                        </xsd:documentation>
                                    </xsd:annotation>
                                </xsd:element>
                                <xsd:element name="serviceCode" type="xsd:string">
                                    <xsd:annotation>
                                        <xsd:documentation>Code that uniquely identifies a
                                            service offered by given SDSB member or
                                            subsystem.
                                        </xsd:documentation>
                                    </xsd:annotation>
                                </xsd:element>
                                <xsd:element name="serviceVersion" type="xsd:string">
                                    <xsd:annotation>
                                        <xsd:documentation>Version of the service.
                                        </xsd:documentation>
                                    </xsd:annotation>
                                </xsd:element>
                                <xsd:attribute name="objectType" type="XRoadObjectType" />
                                <xsd:complexType name="XRoadClientIdentifierType">
                                    <xsd:sequence>
                                        <xsd:element ref="xRoadInstance" />
                                        <xsd:element ref="memberClass" />
                                        <xsd:element ref="memberCode" />
                                        <xsd:element minOccurs="0" ref="subsystemCode" />
                                    </xsd:sequence>
                                    <xsd:attribute ref="objectType" use="required" />
                                </xsd:complexType>
                                <xsd:complexType name="XRoadServiceIdentifierType">
                                    <xsd:sequence>
                                        <xsd:element ref="xRoadInstance" />
                                        <xsd:element ref="memberClass" />
                                        <xsd:element ref="memberCode" />
                                        <xsd:element minOccurs="0" ref="subsystemCode" />
                                        <xsd:element ref="serviceCode" />
                                        <xsd:element minOccurs="0" ref="serviceVersion" />
                                    </xsd:sequence>
                                    <xsd:attribute ref="objectType" use="required" />
                                </xsd:complexType>
                            </xsd:schema>
                            <!-- Schema for request headers -->
                            <xsd:schema xmlns="http://www.w3.org/2001/XMLSchema"
                                        targetNamespace="http://x-road.eu/xsd/xroad.xsd"
                                        elementFormDefault="qualified">
                                <xsd:element name="client" type="id:XRoadClientIdentifierType" />
                                <xsd:element name="service" type="id:XRoadServiceIdentifierType" />
                                <xsd:element name="userId" type="xsd:string" />
                                <xsd:element name="id" type="xsd:string" />
                                <xsd:element name="protocolVersion" type="xsd:string" />
                            </xsd:schema>
                            <!-- Schema for requests (reduced) -->
                            <xsd:schema targetNamespace="http://vrk-test.x-road.fi/producer">
                                <xsd:element name="{0}" nillable="true" />
                                <xsd:element name="{0}Response">
                                    <xsd:complexType>
                                        <xsd:sequence>
                                            <xsd:element name="response">
                                                <xsd:complexType>
                                                    <xsd:sequence>
                                                        <xsd:element name="data" type="xsd:string">
                                                            <xsd:annotation>
                                                                <xsd:documentation>
                                                                    Service response
                                                                </xsd:documentation>
                                                            </xsd:annotation>
                                                        </xsd:element>
                                                    </xsd:sequence>
                                                </xsd:complexType>
                                            </xsd:element>
                                        </xsd:sequence>
                                    </xsd:complexType>
                                </xsd:element>
                            </xsd:schema>
                        </wsdl:types>
                        <wsdl:message name="requestheader">
                            <wsdl:part name="client" element="xrd:client" />
                            <wsdl:part name="service" element="xrd:service" />
                            <wsdl:part name="userId" element="xrd:userId" />
                            <wsdl:part name="id" element="xrd:id" />
                            <wsdl:part name="protocolVersion" element="xrd:protocolVersion" />
                        </wsdl:message>
                        <wsdl:message name="{0}">
                            <wsdl:part name="body" element="tns:{0}"/>
                        </wsdl:message>
                        <wsdl:message name="{0}Response">
                            <wsdl:part name="body" element="tns:{0}Response"/>
                        </wsdl:message>
                        <wsdl:portType name="testServicePortType">
                            <wsdl:operation name="{0}">
                                <wsdl:input message="tns:{0}"/>
                                <wsdl:output message="tns:{0}Response"/>
                            </wsdl:operation>
                        </wsdl:portType>
                        <wsdl:binding name="testServiceBinding" type="tns:testServicePortType">
                            <soap:binding style="document" transport="http://schemas.xmlsoap.org/soap/http" />
                            <wsdl:operation name="{0}">
                                <soap:operation soapAction="" style="document" />
                                <id:version>{1}</id:version>
                                <wsdl:input>
                                    <soap:body parts="body" use="literal"/>
                                    <soap:header message="tns:requestheader" part="client" use="literal"/>
                                    <soap:header message="tns:requestheader" part="service" use="literal"/>
                                    <soap:header message="tns:requestheader" part="userId" use="literal"/>
                                    <soap:header message="tns:requestheader" part="id" use="literal"/>
                                    <soap:header message="tns:requestheader" part="protocolVersion" use="literal"/>
                                </wsdl:input>
                                <wsdl:output>
                                    <soap:body parts="body" use="literal"/>
                                    <soap:header message="tns:requestheader" part="client" use="literal"/>
                                    <soap:header message="tns:requestheader" part="service" use="literal"/>
                                    <soap:header message="tns:requestheader" part="userId" use="literal"/>
                                    <soap:header message="tns:requestheader" part="id" use="literal"/>
                                    <soap:header message="tns:requestheader" part="protocolVersion" use="literal"/>
                                </wsdl:output>
                            </wsdl:operation>
                        </wsdl:binding>
                        <wsdl:service name="testService">
                            <wsdl:port binding="tns:testServiceBinding" name="testServicePort">
                                <soap:address location="SOME-SERVICE_ENDPOINT"/>
                            </wsdl:port>
                        </wsdl:service>
                    </wsdl:definitions>
                    """;
}
