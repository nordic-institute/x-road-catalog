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
package org.niis.xroad.catalog.lister.endpoint;

import org.niis.xroad.catalog.persistence.entity.ErrorLog;
import org.niis.xroad.catalog.persistence.entity.Member;
import org.niis.xroad.catalog.persistence.entity.OpenApi;
import org.niis.xroad.catalog.persistence.entity.Rest;
import org.niis.xroad.catalog.persistence.entity.Service;
import org.niis.xroad.catalog.persistence.entity.StatusInfo;
import org.niis.xroad.catalog.persistence.entity.Subsystem;
import org.niis.xroad.catalog.persistence.entity.Wsdl;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class SoapMockDataFactory {

    // Consistent test constants
    public static final LocalDateTime FIXED_TEST_TIME = LocalDateTime.of(2025, 1, 1, 12, 0, 0);
    public static final String DEFAULT_XROAD_INSTANCE = "DEV";

    // External ID prefixes
    public static final String WSDL_PREFIX = "wsdl";
    public static final String REST_PREFIX = "rest";
    public static final String OPENAPI_PREFIX = "openapi";

    // Member class constants
    public static final String MEMBER_CLASS_ORG = "ORG";
    public static final String MEMBER_CLASS_COM = "COM";

    // Common member codes and names
    public static final String GOVT_ORG_CODE = "1234";
    public static final String GOVT_ORG_NAME = "Government Organization";

    public static final String PUBLIC_SERVICES_CODE = "5678";
    public static final String PUBLIC_SERVICES_NAME = "Public Services Org";

    public static final String TEST_COMPANY_CODE = "1234";
    public static final String TEST_COMPANY_NAME = "Test Company Ltd";

    public static final String WEATHER_SERVICES_CODE = "5678";
    public static final String WEATHER_SERVICES_NAME = "Weather Services Inc";

    public static final String LEGACY_SYSTEMS_CODE = "9999";
    public static final String LEGACY_SYSTEMS_NAME = "Legacy Systems Org";

    public static final String DATA_SERVICES_CODE = "7777";
    public static final String DATA_SERVICES_NAME = "Data Services Corp";

    public static final String PROVIDER_MEMBER_CODE = "14151328";
    public static final String PROVIDER_MEMBER_NAME = "Test Provider Organization";

    public static final String NON_PROVIDER_MEMBER_CODE = "88855888";
    public static final String NON_PROVIDER_MEMBER_NAME = "Non-Provider Organization";

    // Pre-defined standard test entities
    public static final Member GOVERNMENT_ORG_MEMBER = createStandardMember(
            MEMBER_CLASS_ORG, GOVT_ORG_CODE, GOVT_ORG_NAME);

    public static final Member PUBLIC_SERVICES_MEMBER = createStandardMember(
            MEMBER_CLASS_ORG, PUBLIC_SERVICES_CODE, PUBLIC_SERVICES_NAME);

    public static final Member TEST_COMPANY_MEMBER = createStandardMember(
            MEMBER_CLASS_COM, TEST_COMPANY_CODE, TEST_COMPANY_NAME);

    public static final Member WEATHER_SERVICES_MEMBER = createStandardMember(
            MEMBER_CLASS_COM, WEATHER_SERVICES_CODE, WEATHER_SERVICES_NAME);

    public static final Member LEGACY_SYSTEMS_MEMBER = createStandardMember(
            MEMBER_CLASS_ORG, LEGACY_SYSTEMS_CODE, LEGACY_SYSTEMS_NAME);

    public static final Member DATA_SERVICES_MEMBER = createStandardMember(
            MEMBER_CLASS_COM, DATA_SERVICES_CODE, DATA_SERVICES_NAME);

    public static final Member PROVIDER_MEMBER = createProviderMember();

    public static final Member NON_PROVIDER_MEMBER = createNonProviderMember();

    private SoapMockDataFactory() {
        // Utility class - prevent instantiation
    }

    // External ID generation methods

    public static String generateWsdlExternalId(String serviceCode, String version) {
        return WSDL_PREFIX + "-" + serviceCode + "-" + version;
    }

    public static String generateRestExternalId(String serviceCode, String version) {
        return REST_PREFIX + "-" + serviceCode + "-" + version;
    }

    public static String generateOpenApiExternalId(String serviceCode, String version) {
        return OPENAPI_PREFIX + "-" + serviceCode + "-" + version;
    }

    // Factory methods for consistent object creation

    public static StatusInfo createStandardStatusInfo() {
        return new StatusInfo(FIXED_TEST_TIME, FIXED_TEST_TIME, FIXED_TEST_TIME, null);
    }

    public static Member createStandardMember(String memberClass, String memberCode, String name) {
        Member member = new Member();
        member.setXRoadInstance(DEFAULT_XROAD_INSTANCE);
        member.setMemberClass(memberClass);
        member.setMemberCode(memberCode);
        member.setName(name);
        member.setStatusInfo(createStandardStatusInfo());
        return member;
    }

    public static Subsystem createStandardSubsystem(String subsystemCode) {
        Subsystem subsystem = new Subsystem();
        subsystem.setSubsystemCode(subsystemCode);
        subsystem.setStatusInfo(createStandardStatusInfo());
        return subsystem;
    }

    public static Service createStandardSoapService(String serviceCode, String version, String wsdlData) {
        Service service = new Service();
        service.setServiceCode(serviceCode);
        service.setServiceVersion(version);
        service.setStatusInfo(createStandardStatusInfo());

        String externalId = generateWsdlExternalId(serviceCode, version);
        Wsdl wsdl = new Wsdl(service, wsdlData, externalId);
        wsdl.setStatusInfo(createStandardStatusInfo());
        service.setWsdl(wsdl);

        return service;
    }

    public static Service createStandardRestService(String serviceCode, String version) {
        Service service = new Service();
        service.setServiceCode(serviceCode);
        service.setServiceVersion(version);
        service.setStatusInfo(createStandardStatusInfo());

        String externalId = generateRestExternalId(serviceCode, version);
        Rest rest = new Rest(service, "REST service data for " + serviceCode, externalId);
        rest.setStatusInfo(createStandardStatusInfo());
        service.setRest(rest);

        return service;
    }

    public static Service createStandardOpenApiService(String serviceCode, String version, String apiData) {
        Service service = new Service();
        service.setServiceCode(serviceCode);
        service.setServiceVersion(version);
        service.setStatusInfo(createStandardStatusInfo());

        String externalId = generateOpenApiExternalId(serviceCode, version);
        OpenApi openApi = new OpenApi(service, apiData, externalId);
        openApi.setStatusInfo(createStandardStatusInfo());
        service.setOpenApi(openApi);

        return service;
    }

    public static ErrorLog createStandardErrorLog(String message, String memberCode,
                                                  String subsystemCode, String serviceCode) {
        ErrorLog errorLog = new ErrorLog();
        errorLog.setMessage(message);
        errorLog.setCode("TEST_ERROR");
        errorLog.setCreated(FIXED_TEST_TIME);
        errorLog.setXRoadInstance(DEFAULT_XROAD_INSTANCE);
        errorLog.setMemberClass(MEMBER_CLASS_ORG);
        errorLog.setMemberCode(memberCode);
        errorLog.setSubsystemCode(subsystemCode);
        errorLog.setServiceCode(serviceCode);
        return errorLog;
    }

    // Pre-defined collections for common test scenarios

    public static List<Member> getStandardMemberList() {
        return Arrays.asList(
                GOVERNMENT_ORG_MEMBER,
                PUBLIC_SERVICES_MEMBER,
                TEST_COMPANY_MEMBER
        );
    }

    public static List<ErrorLog> getStandardErrorList() {
        return Arrays.asList(
                createStandardErrorLog("Service not found", GOVT_ORG_CODE, "TestSubsystem", "TestService"),
                createStandardErrorLog("Error with certificate", GOVT_ORG_CODE, "TestSubsystem", "TestService"),
                createStandardErrorLog("Unknown error", GOVT_ORG_CODE, "TestSubsystem", "TestService"),
                createStandardErrorLog("Access restricted", GOVT_ORG_CODE, "TestSubsystem", "TestService"),
                createStandardErrorLog("Connection refused", GOVT_ORG_CODE, "TestSubsystem", "TestService"),
                createStandardErrorLog("Multiple values returned", GOVT_ORG_CODE, "TestSubsystem", "TestService")
        );
    }

    // Helper methods for creating complex test entities

    private static Member createProviderMember() {
        Member member = createStandardMember(MEMBER_CLASS_ORG, PROVIDER_MEMBER_CODE, PROVIDER_MEMBER_NAME);

        Subsystem subsystem = createStandardSubsystem("TestSubsystem");
        subsystem.setMember(member);

        Service service = createStandardSoapService("TestService", "v1", "This is WSDL");
        service.setSubsystem(subsystem);

        subsystem.setServices(Set.of(service));
        member.setSubsystems(Set.of(subsystem));

        return member;
    }

    private static Member createNonProviderMember() {
        Member member = createStandardMember(MEMBER_CLASS_ORG, NON_PROVIDER_MEMBER_CODE, NON_PROVIDER_MEMBER_NAME);

        // Consumer has subsystem for access control but no services
        Subsystem subsystem = createStandardSubsystem("TestSubsystem");
        subsystem.setMember(member);

        // No services - consumer subsystem is for access control only
        member.setSubsystems(Set.of(subsystem));

        return member;
    }

    // Utility method for creating members with custom services
    public static Member createMemberWithServices(String memberClass, String memberCode, String name,
                                                  Subsystem... subsystems) {
        Member member = createStandardMember(memberClass, memberCode, name);

        // Set up bidirectional relationships
        for (Subsystem subsystem : subsystems) {
            subsystem.setMember(member);
            for (Service service : subsystem.getAllServices()) {
                service.setSubsystem(subsystem);
            }
        }

        member.setSubsystems(new LinkedHashSet<>(Arrays.asList(subsystems)));
        return member;
    }

    // Utility method for creating subsystems with services
    public static Subsystem createSubsystemWithServices(String subsystemCode, Service... services) {
        Subsystem subsystem = createStandardSubsystem(subsystemCode);
        subsystem.setServices(new LinkedHashSet<>(Arrays.asList(services)));
        return subsystem;
    }
}
