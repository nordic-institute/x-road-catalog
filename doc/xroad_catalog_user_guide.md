# X-Road Catalog User Guide
Version: 4.3.0
Doc. ID: UG-XRDCAT

---

## Version history <!-- omit in toc -->
| Date       | Version | Description                                                                     | Author           |
|------------|---------|---------------------------------------------------------------------------------|------------------|
| 21.07.2021 | 1.0.0   | Initial draft                                                                   | Bert Viikmäe     |
| 21.07.2021 | 1.0.1   | Add installation section                                                        | Bert Viikmäe     |
| 22.07.2021 | 1.0.2   | Add X-Road Catalog Collector section                                            | Bert Viikmäe     |
| 23.07.2021 | 1.0.3   | Add X-Road Catalog Lister section                                               | Bert Viikmäe     |
| 23.07.2021 | 1.0.4   | Add X-Road Catalog Persistence section                                          | Bert Viikmäe     |
| 25.08.2021 | 1.0.5   | Add list distinct services endpoint description                                 | Bert Viikmäe     |
| 02.09.2021 | 1.0.6   | Add list errors endpoint description                                            | Bert Viikmäe     |
| 22.09.2021 | 1.0.7   | Update heartbeat endpoint description                                           | Bert Viikmäe     |
| 26.10.2021 | 1.0.8   | Update listErrors endpoint description                                          | Bert Viikmäe     |
| 27.10.2021 | 1.1.0   | Add listSecurityServers and listDescriptors endpoint descriptions               | Bert Viikmäe     |
| 15.12.2021 | 1.1.1   | Update listErrors endpoint description                                          | Bert Viikmäe     |
| 08.02.2022 | 1.2.0   | Add getOrganization and getOrganizationChanges endpoint descriptions            | Bert Viikmäe     |
| 29.07.2022 | 2.0.0   | Substitute since with start and end date parameter and update related chapters  | Bert Viikmäe     |
| 04.10.2022 | 2.1.0   | Add getRest and getEndpoints descriptions                                       | Bert Viikmäe     |
| 15.01.2023 | 3.0.0   | Restructure of the document                                                     | Bert Viikmäe     |
| 22.03.2023 | 4.0.0   | Split document into X-Road Catalog Installation Guide and User Guide            | Petteri Kivimäki |
| 16.08.2023 | 4.1.0   | Update Catalog Lister port number from `8080` to `8070`                         | Petteri Kivimäki |
| 09.09.2023 | 4.2.0   | Update REST endpoint descriptions                                               | Petteri Kivimäki |
| 17.11.2023 | 4.2.1   | Update response of ListMembers and service types for GetServiceType             | Bert Viikmäe     |
| 13.08.2026 | 4.2.2   | Document V1 SOAP/REST endpoints being disabled by default behind a feature flag | Raido Kaju       |
| 25.08.2026 | 4.3.0   | Add the REST API V2 description; remove leftover FI-profile references          | Raido Kaju       |

## Table of Contents <!-- omit in toc -->

<!-- toc -->
<!-- vim-markdown-toc GFM -->

* [License](#license)
* [1. Introduction](#1-introduction)
  * [1.1 Target Audience](#11-target-audience)
* [2. X-Road Catalog Collector](#2-x-road-catalog-collector)
* [3. X-Road Catalog Lister](#3-x-road-catalog-lister)
    * [3.1 SOAP endpoints](#31-soap-endpoints)
        * [3.1.1 List all members](#311-list-all-members)
        * [3.1.2 Retrieve WSDL descriptions](#312-retrieve-wsdl-descriptions)   
        * [3.1.3 Retrieve OPENAPI descriptions](#313-retrieve-openapi-descriptions)
        * [3.1.4 Get service type](#314-get-service-type) 
        * [3.1.5 Check if member is provider](#315-check-if-member-is-provider)  
        * [3.1.6 List errors](#316-list-errors)
    * [3.2 REST endpoints](#32-rest-endpoints)          
        * [3.2.1 List service statistics](#321-list-service-statistics) 
        * [3.2.2 List service statistics in CSV format](#322-list-service-statistics-in-csv-format)
        * [3.2.3 List services](#323-list-services)  
        * [3.2.4 List services in CSV format](#324-list-services-in-csv-format)  
        * [3.2.5 Check heartbeat](#325-check-heartbeat)  
        * [3.2.6 List distinct service statistics](#326-list-distinct-service-statistics)  
        * [3.2.7 List errors](#327-list-errors) 
        * [3.2.8 List Security Servers](#328-list-security-servers) 
        * [3.2.9 List descriptors](#329-list-descriptors) 
        * [3.2.10 Get endpoints](#3210-get-endpoints)
        * [3.2.11 Get Rest](#3211-get-rest)
    * [3.3 REST API V2](#33-rest-api-v2)
        * [3.3.1 Common conventions](#331-common-conventions)
        * [3.3.2 Heartbeat](#332-heartbeat)
        * [3.3.3 Browse endpoints](#333-browse-endpoints)
        * [3.3.4 Descriptor endpoints](#334-descriptor-endpoints)
        * [3.3.5 Error log endpoints](#335-error-log-endpoints)
        * [3.3.6 List endpoints](#336-list-endpoints)
        * [3.3.7 Search](#337-search)
        * [3.3.8 Reports](#338-reports)
* [4. X-Road Catalog Persistence](#4-x-road-catalog-persistence)
                     
<!-- vim-markdown-toc -->
<!-- tocstop -->

## License

This document is licensed under the Creative Commons Attribution-ShareAlike 3.0 Unported License. To view a copy of this license, visit http://creativecommons.org/licenses/by-sa/3.0/.

## 1. Introduction

X-Road Catalog is an [X-Road](https://github.com/nordic-institute/X-Road/) extension that collects information on 
members, subsystems and services from an X-Road ecosystem and provides a REST and SOAP interfaces to access the data.

X-Road Catalog consists of three modules:

* X-Road Catalog Collector
    * Collects information from the X-Road ecosystem and stores it to a database.
* X-Road Catalog Lister
    * Provides REST (and, optionally, deprecated SOAP) interfaces offering information collected by the collector.
    * Can be used as an X-Road service (X-Road headers are in place).
* X-Road Catalog Persistence
    * Library used to persist and read persisted data.
    * Used by the X-Road Catalog Collector and X-Road Catalog Lister modules.

### 1.1 Target Audience

The intended audience of this user guide are X-Road Operators responsible for managing and configuring the X-Road Central 
Server and related services. The document is intended for readers with a good knowledge of Linux server management, 
computer networks, and the X-Road principles.

## 2. X-Road Catalog Collector

The purpose of this module is to collect members, subsystems and services from the X-Road ecosystem and store them to the PostgreSQL database. 

More information about the [X-Road Catalog Collector](../xroad-catalog-collector/README.md) module.

## 3. X-Road Catalog Lister

The purpose of this module is to provide a web service which lists all the X-Road members and the services they provide together with service descriptions.

More information about the [X-Road Catalog Lister](../xroad-catalog-lister/README.md) module.

The lister offers three interfaces. The **REST API V2** ([3.3](#33-rest-api-v2)) is always served. The **SOAP**
([3.1](#31-soap-endpoints)) and **REST V1** ([3.2](#32-rest-endpoints)) interfaces are deprecated and disabled by
default.

### 3.1 SOAP endpoints

> [!NOTE]
> The SOAP interface is deprecated and disabled by default. Set
> `xroad-catalog.legacy-api.enabled=true` to serve it.

The main SOAP endpoints the module  provides with the `default` [profile](../BUILD.md#profiles): 

* `ListMembers` - get a list all the members the Catalog Collector has stored to the db.
* `GetWsdl` - retrieve a WSDL description for a given service.
* `GetOpenAPI` - retrieve an OpenAPI description for a given service.
* `GetServiceType` - retrieve the service type (`SOAP`, `REST` or `OPENAPI3`) for a given service.
* `IsProvider` - check is a given member a service provider.
* `GetErrors` - get a list of errors related to fetching data from different apis and Security Servers.

### 3.1.1 List all members

In order to list all members and related subsystems and services, a request in XML format has to be sent to the respective SOAP endpoint:

```bash
curl -k -d @servicerequest.xml --header "Content-Type: text/xml" -X POST http://<SERVER_ADDRESS>:8070/ws/ListMembers
```

**Note!** Replace the `SERVER_ADDRESS` placeholder with the server address on which the X-Road Catalog Lister is running on, e.g., `localhost`.

Contents of the example `servicerequest.xml` file:
```xml
<soapenv:Envelope 
xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" 
xmlns:xro="http://x-road.eu/xsd/xroad.xsd" 
xmlns:iden="http://x-road.eu/xsd/identifiers" 
xmlns:xrcl="http://xroad.vrk.fi/xroad-catalog-lister">
   <soapenv:Header>
      <xro:protocolVersion>4.x</xro:protocolVersion>
      <xro:id>ID11234</xro:id>
      <xro:userId>EE1234567890</xro:userId>
      <xro:client iden:objectType="MEMBER">
         <iden:xRoadInstance>FI</iden:xRoadInstance>
         <iden:memberClass>GOV</iden:memberClass>
         <iden:memberCode>1710128-9</iden:memberCode>
      </xro:client>
      <xro:service iden:objectType="SERVICE">
         <iden:xRoadInstance>FI</iden:xRoadInstance>
         <iden:memberClass>GOV</iden:memberClass>
         <iden:memberCode>1710128-9</iden:memberCode>
         <iden:subsystemCode>SS1</iden:subsystemCode>
         <iden:serviceCode>ListMembers</iden:serviceCode>
         <iden:serviceVersion>v1</iden:serviceVersion>
      </xro:service>
   </soapenv:Header>
   <soapenv:Body>
      <xrcl:ListMembers>
         <xrcl:startDateTime>2020-01-01</xrcl:startDateTime>
         <xrcl:endDateTime>2022-01-01</xrcl:endDateTime>
      </xrcl:ListMembers>
   </soapenv:Body>
</soapenv:Envelope>
```

Contents of the XML response of the request
```xml
<?xml version="1.0" encoding="UTF-8"?>
<SOAP-ENV:Envelope xmlns:SOAP-ENV="http://schemas.xmlsoap.org/soap/envelope/">
  <SOAP-ENV:Header>
    <xro:protocolVersion xmlns:xro="http://x-road.eu/xsd/xroad.xsd">4.x</xro:protocolVersion>
    <xro:id xmlns:xro="http://x-road.eu/xsd/xroad.xsd">ID11234</xro:id>
    <xro:userId xmlns:xro="http://x-road.eu/xsd/xroad.xsd">EE1234567890</xro:userId>
    <xro:client xmlns:xro="http://x-road.eu/xsd/xroad.xsd" xmlns:iden="http://x-road.eu/xsd/identifiers" iden:objectType="MEMBER">
      <iden:xRoadInstance>FI</iden:xRoadInstance>
      <iden:memberClass>GOV</iden:memberClass>
      <iden:memberCode>1710128-9</iden:memberCode>
    </xro:client>
    <xro:service xmlns:xro="http://x-road.eu/xsd/xroad.xsd" xmlns:iden="http://x-road.eu/xsd/identifiers" iden:objectType="SERVICE">
      <iden:xRoadInstance>FI</iden:xRoadInstance>
      <iden:memberClass>GOV</iden:memberClass>
      <iden:memberCode>1710128-9</iden:memberCode>
      <iden:subsystemCode>SS1</iden:subsystemCode>
      <iden:serviceCode>ListMembers</iden:serviceCode>
      <iden:serviceVersion>v1</iden:serviceVersion>
    </xro:service>
  </SOAP-ENV:Header>
  <SOAP-ENV:Body>
    <ns2:ListMembersResponse xmlns:ns2="http://xroad.vrk.fi/xroad-catalog-lister">
      <ns2:memberList>
        <ns2:member>
          <ns2:xRoadInstance>DEV</ns2:xRoadInstance>
          <ns2:memberClass>GOV</ns2:memberClass>
          <ns2:memberCode>1234</ns2:memberCode>
          <ns2:name>ACME</ns2:name>
          <ns2:subsystems>
            <ns2:subsystem>
              <ns2:subsystemCode>MANAGEMENT</ns2:subsystemCode>
              <ns2:services>
                <ns2:service>
                  <ns2:serviceCode>clientReg</ns2:serviceCode>
                  <ns2:wsdl>
                    <ns2:externalId>1584692751893_da8be621-5d6b-4920-91c9-d8c359dddbad</ns2:externalId>
                    <ns2:created>2020-03-20T10:25:51.892+02:00</ns2:created>
                    <ns2:changed>2020-03-20T10:25:51.892+02:00</ns2:changed>
                    <ns2:fetched>2020-03-20T12:31:09.188+02:00</ns2:fetched>
                  </ns2:wsdl>
                  <ns2:created>2020-03-20T10:25:51.632+02:00</ns2:created>
                  <ns2:changed>2020-03-20T10:25:51.632+02:00</ns2:changed>
                  <ns2:fetched>2020-03-20T12:31:07.223+02:00</ns2:fetched>
                </ns2:service>
                <ns2:service>
                  <ns2:serviceCode>respa.tampere.fi</ns2:serviceCode>
                  <ns2:created>2020-03-20T10:25:51.632+02:00</ns2:created>
                  <ns2:changed>2020-03-20T10:25:51.632+02:00</ns2:changed>
                  <ns2:fetched>2020-03-20T12:31:07.223+02:00</ns2:fetched>
                </ns2:service>
                <ns2:service>
                  <ns2:serviceCode>authCertDeletion</ns2:serviceCode>
                  <ns2:wsdl>
                    <ns2:externalId>1584692751942_ab002cbd-bbbd-43c7-a311-b0dc5adf3af1</ns2:externalId>
                    <ns2:created>2020-03-20T10:25:51.936+02:00</ns2:created>
                    <ns2:changed>2020-03-20T10:25:51.936+02:00</ns2:changed>
                    <ns2:fetched>2020-03-20T12:31:09.009+02:00</ns2:fetched>
                  </ns2:wsdl>
                  <ns2:created>2020-03-20T10:25:51.632+02:00</ns2:created>
                  <ns2:changed>2020-03-20T10:25:51.632+02:00</ns2:changed>
                  <ns2:fetched>2020-03-20T12:31:07.223+02:00</ns2:fetched>
                </ns2:service>
                <ns2:service>
                  <ns2:serviceCode>clientDeletion</ns2:serviceCode>
                  <ns2:wsdl>
                    <ns2:externalId>1584692751908_5bdde30d-3a5f-42c0-9f45-d884f5810996</ns2:externalId>
                    <ns2:created>2020-03-20T10:25:51.906+02:00</ns2:created>
                    <ns2:changed>2020-03-20T10:25:51.906+02:00</ns2:changed>
                    <ns2:fetched>2020-03-20T12:31:07.383+02:00</ns2:fetched>
                  </ns2:wsdl>
                  <ns2:created>2020-03-20T10:25:51.632+02:00</ns2:created>
                  <ns2:changed>2020-03-20T10:25:51.632+02:00</ns2:changed>
                  <ns2:fetched>2020-03-20T12:31:07.223+02:00</ns2:fetched>
                </ns2:service>
                <ns2:service>
                  <ns2:serviceCode>ownerChange</ns2:serviceCode>
                  <ns2:wsdl>
                    <ns2:externalId>1584692751888_07141c5a-bfe0-4c84-b621-e5e4a9db01fa</ns2:externalId>
                    <ns2:created>2020-03-20T10:25:51.884+02:00</ns2:created>
                    <ns2:changed>2020-03-20T10:25:51.884+02:00</ns2:changed>
                    <ns2:fetched>2020-03-20T12:31:07.479+02:00</ns2:fetched>
                  </ns2:wsdl>
                  <ns2:created>2020-03-20T10:25:51.632+02:00</ns2:created>
                  <ns2:changed>2020-03-20T10:25:51.632+02:00</ns2:changed>
                  <ns2:fetched>2020-03-20T12:31:07.223+02:00</ns2:fetched>
                </ns2:service>
                <ns2:service>
                  <ns2:serviceCode>PetStoreNew</ns2:serviceCode>
                  <ns2:created>2020-03-20T10:25:51.632+02:00</ns2:created>
                  <ns2:changed>2020-03-20T10:25:51.632+02:00</ns2:changed>
                  <ns2:fetched>2020-03-20T12:31:07.223+02:00</ns2:fetched>
                </ns2:service>
              </ns2:services>
              <ns2:created>2020-03-20T10:25:51.055+02:00</ns2:created>
              <ns2:changed>2020-03-20T10:25:51.055+02:00</ns2:changed>
              <ns2:fetched>2020-03-20T12:31:01.394+02:00</ns2:fetched>
            </ns2:subsystem>
            <ns2:subsystem>
              <ns2:subsystemCode>TEST</ns2:subsystemCode>
              <ns2:services />
              <ns2:created>2020-03-20T10:25:51.055+02:00</ns2:created>
              <ns2:changed>2020-03-20T10:25:51.055+02:00</ns2:changed>
              <ns2:fetched>2020-03-20T12:31:01.394+02:00</ns2:fetched>
            </ns2:subsystem>
          </ns2:subsystems>
          <ns2:created>2020-03-20T10:25:51.055+02:00</ns2:created>
          <ns2:changed>2020-03-20T10:25:51.055+02:00</ns2:changed>
          <ns2:fetched>2020-03-20T12:31:01.394+02:00</ns2:fetched>
        </ns2:member>
      </ns2:memberList>
    </ns2:ListMembersResponse>
  </SOAP-ENV:Body>
</SOAP-ENV:Envelope>
```

The XML response has a `<SOAP-ENV:Body>` element with the following structure:

* `ListMembersResponse`
  * `memberList`
    * `member`
      * `subsystems`
        * `subsystem`
          * `subsystemCode`
          * `services`
            * `service`
            * `serviceCode`
            * `wsdl` (if the given service description is a WSDL description)
              * `externalId`

In addition, each subsystem, service and wsdl contains also fields `created`, `changed`, `fetched` and `removed`, 
reflecting the creation, change, fetch and removal (when a subsystem/service/wsdl was fetched by X-Road Catalog Collector to the DB) dates.

### 3.1.2 Retrieve WSDL descriptions

In order to retrieve a WSDL service description, a request in XML format has to be sent to the respective SOAP endpoint:

```bash
curl -k -d @wsdlrequest.xml --header "Content-Type: text/xml" -X POST http://<SERVER_ADDRESS>:8070/ws/GetWsdl
```

**Note!** Replace the `SERVER_ADDRESS` placeholder with the server address on which the X-Road Catalog Lister is running on, e.g., `localhost`.

Contents of the example `wsdlrequest.xml` file:
```xml
<soapenv:Envelope 
xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" 
xmlns:xro="http://x-road.eu/xsd/xroad.xsd" 
xmlns:iden="http://x-road.eu/xsd/identifiers" 
xmlns:xrcl="http://xroad.vrk.fi/xroad-catalog-lister">
   <soapenv:Header>
      <xro:protocolVersion>4.x</xro:protocolVersion>
      <xro:id>ID11234</xro:id>
      <xro:userId>EE1234567890</xro:userId>
      <xro:client iden:objectType="MEMBER">
         <iden:xRoadInstance>FI</iden:xRoadInstance>
         <iden:memberClass>GOV</iden:memberClass>
         <iden:memberCode>1710128-9</iden:memberCode>
      </xro:client>
      <xro:service iden:objectType="SERVICE">
         <iden:xRoadInstance>FI</iden:xRoadInstance>
         <iden:memberClass>GOV</iden:memberClass>
         <iden:memberCode>1710128-9</iden:memberCode>
         <iden:subsystemCode>SS1</iden:subsystemCode>
         <iden:serviceCode>ListMembers</iden:serviceCode>
         <iden:serviceVersion>v1</iden:serviceVersion>
      </xro:service>
   </soapenv:Header>
   <soapenv:Body>
      <xrcl:GetWsdl>
         <xrcl:externalId>1584692751908_5bdde30d-3a5f-42c0-9f45-d884f5810996</xrcl:externalId>
      </xrcl:GetWsdl>
   </soapenv:Body>
</soapenv:Envelope>
```

In the request, the `externalId` field identifies the WSDL to be retrieved.

The response of the given request is in XML format, containing the WSDL service description.

### 3.1.3 Retrieve OPENAPI descriptions

In order to retrieve an OPENAPI service descriptions, a request in XML format has to be sent to the respective SOAP endpoint:

```bash
curl -k -d @openapirequest.xml --header "Content-Type: text/xml" -X POST http://<SERVER_ADDRESS>:8070/ws/GetOpenAPI
```

**Note!** Replace the `SERVER_ADDRESS` placeholder with the server address on which the X-Road Catalog Lister is running on, e.g., `localhost`.

Contents of the example `openapirequest.xml` file:
```xml
<soapenv:Envelope 
xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" 
xmlns:xro="http://x-road.eu/xsd/xroad.xsd" 
xmlns:iden="http://x-road.eu/xsd/identifiers" 
xmlns:xrcl="http://xroad.vrk.fi/xroad-catalog-lister">
   <soapenv:Header>
      <xro:protocolVersion>4.x</xro:protocolVersion>
      <xro:id>ID11234</xro:id>
      <xro:userId>EE1234567890</xro:userId>
      <xro:client iden:objectType="MEMBER">
         <iden:xRoadInstance>FI</iden:xRoadInstance>
         <iden:memberClass>GOV</iden:memberClass>
         <iden:memberCode>1710128-9</iden:memberCode>
      </xro:client>
      <xro:service iden:objectType="SERVICE">
         <iden:xRoadInstance>FI</iden:xRoadInstance>
         <iden:memberClass>GOV</iden:memberClass>
         <iden:memberCode>1710128-9</iden:memberCode>
         <iden:subsystemCode>SS1</iden:subsystemCode>
         <iden:serviceCode>ListMembers</iden:serviceCode>
         <iden:serviceVersion>v1</iden:serviceVersion>
      </xro:service>
   </soapenv:Header>
   <soapenv:Body>
      <xrcl:GetOpenAPI>
         <xrcl:externalId>1584692752414_504b3ad4-eca3-4b96-8b21-71209225cfc8</xrcl:externalId>
      </xrcl:GetOpenAPI>
   </soapenv:Body>
</soapenv:Envelope>
```

In the request, the `externalId` field identifies the OPENAPI to be retrieved.

The response of the given request is in XML format, containing the OPENAPI service description.

### 3.1.4 Get service type

In order to retrieve service type information, a request in XML format has to be sent to the respective SOAP endpoint:

```bash
curl -k -d @GetServiceTypeRequest.xml --header "Content-Type: text/xml" -X POST http://<SERVER_ADDRESS>:8070/ws/GetServiceType
```

**Note!** Replace the `SERVER_ADDRESS` placeholder with the server address on which the X-Road Catalog Lister is running on, e.g., `localhost`.

Contents of the example `GetServiceTypeRequest.xml` file:
```xml
<soapenv:Envelope 
xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" 
xmlns:xro="http://x-road.eu/xsd/xroad.xsd" 
xmlns:iden="http://x-road.eu/xsd/identifiers" 
xmlns:xrcl="http://xroad.vrk.fi/xroad-catalog-lister">
   <soapenv:Header>
      <xro:protocolVersion>4.x</xro:protocolVersion>
      <xro:id>ID11234</xro:id>
      <xro:userId>EE1234567890</xro:userId>
      <xro:client iden:objectType="MEMBER">
         <iden:xRoadInstance>FI</iden:xRoadInstance>
         <iden:memberClass>GOV</iden:memberClass>
         <iden:memberCode>1710128-9</iden:memberCode>
      </xro:client>
      <xro:service iden:objectType="SERVICE">
         <iden:xRoadInstance>FI</iden:xRoadInstance>
         <iden:memberClass>GOV</iden:memberClass>
         <iden:memberCode>1710128-9</iden:memberCode>
         <iden:subsystemCode>SS1</iden:subsystemCode>
         <iden:serviceCode>ListMembers</iden:serviceCode>
         <iden:serviceVersion>v1</iden:serviceVersion>
      </xro:service>
   </soapenv:Header>
   <soapenv:Body>
      <xrcl:GetServiceType>
         <xrcl:xRoadInstance>DEV</xrcl:xRoadInstance>
         <xrcl:memberClass>GOV</xrcl:memberClass>
         <xrcl:memberCode>1234</xrcl:memberCode>
         <xrcl:serviceCode>authCertDeletion</xrcl:serviceCode>
         <xrcl:subsystemCode>MANAGEMENT</xrcl:subsystemCode>
         <xrcl:serviceVersion>v1</xrcl:serviceVersion>
      </xrcl:GetServiceType>
   </soapenv:Body>
</soapenv:Envelope>
```

The following request fields need to be filled:

* `xRoadInstance` - X-Road Instance name, e.g. `DEV`.
* `memberClass` - member class, e.g., `GOV`.
* `memberCode` -  member code, e.g., `1234`.
* `serviceCode` - service code, e.g., `authCertDeletion`-
* `subsystemCode` - subsystem code, e.g., `MANAGEMENT`.
* `serviceVersion` - service version, e.g., `v1`.

The XML response has a `<SOAP-ENV:Body>` element with the following structure:

* `GetServiceTypeResponse`
  * `type` (values: `REST`/`OPENAPI3`/`WSDL`/`UNKNOWN`)
                   
### 3.1.5 Check if member is provider

In order to check if a given X-Road member (Security Server) is a provider, a request in XML format has to be sent to the respective SOAP endpoint:

```bash
curl -k -d @IsProviderRequest.xml --header "Content-Type: text/xml" -X POST http://<SERVER_ADDRESS>:8070/ws/IsProvider
```

**Note!** Replace the `SERVER_ADDRESS` placeholder with the server address on which the X-Road Catalog Lister is running on, e.g., `localhost`.

Contents of the example `IsProviderRequest.xml` file:
```xml
<soapenv:Envelope 
xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" 
xmlns:xro="http://x-road.eu/xsd/xroad.xsd" 
xmlns:iden="http://x-road.eu/xsd/identifiers" 
xmlns:xrcl="http://xroad.vrk.fi/xroad-catalog-lister">
   <soapenv:Header>
      <xro:protocolVersion>4.x</xro:protocolVersion>
      <xro:id>ID11234</xro:id>
      <xro:userId>EE1234567890</xro:userId>
      <xro:client iden:objectType="MEMBER">
         <iden:xRoadInstance>FI</iden:xRoadInstance>
         <iden:memberClass>GOV</iden:memberClass>
         <iden:memberCode>1710128-9</iden:memberCode>
      </xro:client>
      <xro:service iden:objectType="SERVICE">
         <iden:xRoadInstance>FI</iden:xRoadInstance>
         <iden:memberClass>GOV</iden:memberClass>
         <iden:memberCode>1710128-9</iden:memberCode>
         <iden:subsystemCode>SS1</iden:subsystemCode>
         <iden:serviceCode>ListMembers</iden:serviceCode>
         <iden:serviceVersion>v1</iden:serviceVersion>
      </xro:service>
   </soapenv:Header>
   <soapenv:Body>
      <xrcl:IsProvider>
         <xrcl:xRoadInstance>DEV</xrcl:xRoadInstance>
         <xrcl:memberClass>GOV</xrcl:memberClass>
         <xrcl:memberCode>1234</xrcl:memberCode>
      </xrcl:IsProvider>
   </soapenv:Body>
</soapenv:Envelope>
```

The following request fields need to be filled:

* `xRoadInstance` - X-Road Instance name, e.g., `DEV`.
* `memberClass` - member class, e.g., `GOV`.
* `memberCode` -  member code, e.g., `1234`.

The XML response has a `<SOAP-ENV:Body>` element with the following structure:

* `IsProviderResponse`
  * `provider` (values: `true`/`false`)

### 3.1.6 List errors

In order to fetch information about errors in the X-Road Catalog, a request in XML format has to be sent to the respective SOAP endpoint:

```bash
curl -k -d @GetErrorsRequest.xml --header "Content-Type: text/xml" -X POST http://<SERVER_ADDRESS>:8070/ws/GetErrors
```

**Note!** Replace the `SERVER_ADDRESS` placeholder with the server address on which the X-Road Catalog Lister is running on, e.g., `localhost`.

Contents of the example `GetErrorsRequest.xml` file:
```xml
<soapenv:Envelope
        xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/"
        xmlns:xro="http://x-road.eu/xsd/xroad.xsd"
        xmlns:iden="http://x-road.eu/xsd/identifiers"
        xmlns:xrcl="http://xroad.vrk.fi/xroad-catalog-lister">
    <soapenv:Header>
        <xro:protocolVersion>4.x</xro:protocolVersion>
        <xro:id>ID11234</xro:id>
        <xro:userId>EE1234567890</xro:userId>
        <xro:client iden:objectType="MEMBER">
            <iden:xRoadInstance>FI</iden:xRoadInstance>
            <iden:memberClass>GOV</iden:memberClass>
            <iden:memberCode>1710128-9</iden:memberCode>
        </xro:client>
        <xro:service iden:objectType="SERVICE">
            <iden:xRoadInstance>FI</iden:xRoadInstance>
            <iden:memberClass>GOV</iden:memberClass>
            <iden:memberCode>1710128-9</iden:memberCode>
            <iden:subsystemCode>SS1</iden:subsystemCode>
            <iden:serviceCode>ListMembers</iden:serviceCode>
            <iden:serviceVersion>v1</iden:serviceVersion>
        </xro:service>
    </soapenv:Header>
    <soapenv:Body>
        <xrcl:GetErrors>
            <xrcl:startDateTime>2020-01-01</xrcl:startDateTime>
            <xrcl:endDateTime>2022-01-01</xrcl:endDateTime>
        </xrcl:GetErrors>
    </soapenv:Body>
</soapenv:Envelope>
```
The following request fields need to be filled:

* `startDateTime` - date after which to list errors, e.g., `2020-01-01`.
* `endDateTime` - date before which to list errors, e.g., `2022-01-01`.

The XML response has a `<SOAP-ENV:Body>` element with the following structure:

* `GetErrorsResponse`
    * `errorLogList`
        * `errorLog`
            * `message`
            * `code`
            * `created`

## 3.2 REST endpoints

> [!NOTE]
> The V1 REST API is deprecated and disabled by default. Set
> `xroad-catalog.legacy-api.enabled=true` to serve it.

The main endpoints provided by the default [profile](../BUILD.md#profiles):

* `getServiceStatistics` - request a list of statistics, consisting of numbers of SOAP/REST services over time.
* `getServiceStatisticsCSV` - request a list of statistics in CSV format, consisting of numbers of SOAP/REST services over time.
* `getListOfServices` - request a list of members and related subsystems, services and Security Servers over time.
* `getListOfServicesCSV` - request a list of members and related subsystems, services and Security Servers in CSV format.
* `getDistinctServiceStatistics` - request a list of statistics, consisting of numbers of distinct services over time.
* `listErrors` - list errors for a given member or subsystem, supports pagination.
* `heartbeat` - request the heartbeat of X-Road Catalog.
* `listSecurityServers` - list Security Servers and related information.
* `listDescriptors` - list subsystems.
* `getRest` - request a list of endpoints for a REST type of service.
* `getEndpoints` - request a list of endpoints for a `REST` or `OPENAPI3` type of service.

### 3.2.1 List service statistics

In order to fetch information about service statistics in the X-Road Catalog, an HTTP request has to be sent to a respective REST endpoint:

```bash
curl "http://<SERVER_ADDRESS>:8070/api/getServiceStatistics?startDate=<START_DATE>&endDate=<END_DATE>" -H "Content-Type: application/json"
```

The required request parameters are:

* `SERVER_ADDRESS` - the server address on which the X-Road Catalog Lister is running on, e.g., `localhost`.
* `START_DATE` - an optional parameter(a string in format `YYYY-MM-DD`), if not used, today's date will be assumed.
* `END_DATE` - an optional parameter(a string in format `YYYY-MM-DD`), if not used, today's date will be assumed.

Response in JSON:
```json
{
   "serviceStatisticsList":[
      {
         "created": "2022-07-01T00:00:00",
         "numberOfSoapServices":0,
         "numberOfRestServices":0,
         "numberOfOpenApiServices":0
      },
      {
         "created": "2022-07-02T00:00:00",
         "numberOfSoapServices":0,
         "numberOfRestServices":0,
         "numberOfOpenApiServices":0
      }
   ]
}
```

The response has the following fields:

* `serviceStatisticsList`
    * `created`
    * `numberOfSoapServices`
    * `numberOfRestServices`
    * `numberOfOpenApiServices`

### 3.2.2 List service statistics in CSV format

In order to fetch information about service statistics in the X-Road Catalog, an HTTP request has to be sent to a respective REST endpoint:

```bash
curl "http://<SERVER_ADDRESS>:8070/api/getServiceStatisticsCSV?startDate=<START_DATE>&endDate=<END_DATE>" -H "Content-Type: text/csv" --output service_statistics.csv
```

The required request parameters are:

* `SERVER_ADDRESS` - the server address on which the X-Road Catalog Lister is running on, e.g., `localhost`.
* `START_DATE` - an optional parameter(a string in format `YYYY-MM-DD`), if not used, today's date will be assumed.
* `END_DATE` - an optional parameter(a string in format `YYYY-MM-DD`), if not used, today's date will be assumed.

Response is a file `service_statistics.csv` with the following content:

```csv
Date,Number of REST services,Number of SOAP services,Number of OpenApi services
2022-07-01T00:00,0,0,0,0
2022-07-02T00:00,0,0,0,0
```

### 3.2.3 List services

In order to fetch information about services in the X-Road Catalog, an HTTP request has to be sent to a respective REST endpoint:

```bash
curl "http://<SERVER_ADDRESS>:8070/api/getListOfServices?startDate=<START_DATE>&endDate=<END_DATE>" -H "Content-Type: application/json"
```

The required request parameters are:

* `SERVER_ADDRESS` - the server address on which the X-Road Catalog Lister is running on, e.g., `localhost`.
* `START_DATE` - an optional parameter(a string in format `YYYY-MM-DD`), if not used, today's date will be assumed.
* `END_DATE` - an optional parameter(a string in format `YYYY-MM-DD`), if not used, today's date will be assumed.

Response in JSON:

```json
{
   "memberData":[
      {
         "date": "2021-08-24T00:00:00",
         "memberDataList":[
            {
               "created": "2021-08-24T16:20:26",
               "memberClass":"COM",
               "memberCode":"222",
               "name":"ACME",
               "provider":false,
               "subsystemList":[
                  {
                     "created": "2022-02-03T14:10:25",
                     "subsystemCode":"FRUIT",
                     "active":true,
                     "serviceList":[
                        
                     ]
                  }
               ],
               "xroadInstance":"DEV"
            },
            {
               "created": "2021-08-24T16:20:26",
               "memberClass":"COM",
               "memberCode":"12345",
               "name":"Company",
               "provider":false,
               "subsystemList":[
                  
               ],
               "xroadInstance":"DEV"
            }
         ]
      },
      {
         "date": "2021-08-25T00:00:00",
         "memberDataList":[
            {
               "created": "2021-08-24T16:20:26",
               "memberClass":"COM",
               "memberCode":"222",
               "name":"ACME",
               "provider":false,
               "subsystemList":[
                  {
                     "created": "2021-02-03T14:10:25",
                     "subsystemCode":"FRUIT",
                     "active":true,
                     "serviceList":[
                        
                     ]
                  }
               ],
               "xroadInstance":"DEV"
            },
            {
               "created": "2021-08-24T16:20:26",
               "memberClass":"COM",
               "memberCode":"12345",
               "name":"Company",
               "provider":false,
               "subsystemList":[
                  
               ],
               "xroadInstance":"DEV"
            }
         ]
      }
   ],
   "securityServerData":[
      {
         "serverCode":"SS1",
         "address":"SS1",
         "memberClass":"GOV",
         "memberCode":"1234",
         "xroadInstance":"DEV"
      },
      {
         "serverCode":"ss4",
         "address":"ss4",
         "memberClass":"GOV",
         "memberCode":"1234",
         "xroadInstance":"DEV"
      }
   ]
}
```

The response has the following fields:

* `memberData`
    * `date`
    * `memberDataList`
        * `created`
        * `memberClass`
        * `memberCode`
        * `name`
        * `subsystemList`
            * `created`
            * `subsystemCode`
            * `active` (values: `true`/`false`)
            * `serviceList`
                * `created`
                * `serviceCode`
                * `serviceVersion`
                * `active` (values: `true`/`false`)
        * `xroadInstance`
* `securityServerData`
    * `serverCode`
    * `address`
    * `memberClass`
    * `memberCode`

### 3.2.4 List services in CSV format

In order to fetch information about services in the X-Road Catalog, an HTTP request has to be sent to a respective REST endpoint:

```bash
curl "http://<SERVER_ADDRESS>:8070/api/getListOfServicesCSV?startDate=<START_DATE>&endDate=<END_DATE>" -H "Content-Type: text/csv" --output list_of_services.csv
```

The required request parameters are:

* `SERVER_ADDRESS` - the server address on which the X-Road Catalog Lister is running on, e.g., `localhost`.
* `START_DATE` - an optional parameter(a string in format `YYYY-MM-DD`), if not used, today's date will be assumed.
* `END_DATE` - an optional parameter(a string in format `YYYY-MM-DD`), if not used, today's date will be assumed.

Response is a file `list_of_services.csv` with the following content:

```csv
Date,XRoad instance,Member class,Member code,Member name,Member created,Subsystem code,Subsystem created,Subsystem active,Service code,Service version,Service created,Service active
2021-08-24T00:00,,,,,,,,,,,,
"",DEV,COM,222,ACME,2021-08-24T16:20:26.830,FRUIT,2022-02-03T14:10:25.712,true,,,,
"",DEV,COM,12345,Company,2021-08-24T16:20:26.830,,,,,,,
2021-08-25T00:00,,,,,,,,,,,,
"",DEV,COM,222,ACME,2021-08-24T16:20:26.830,FRUIT,2022-02-03T14:10:25.712,true,,,,
"",DEV,COM,12345,Company,2021-08-24T16:20:26.830,,,,,,,
"",Security server (SS) info:,,,,,,,,,,,
instance,member class,member code,server code,address,,,,,,,,
DEV,GOV,1234,SS1,SS1,,,,,,,,
DEV,GOV,1234,ss4,ss4,,,,,,,,
```

### 3.2.5 Check heartbeat

In order to fetch X-Road Catalog heartbeat information, an HTTP request has to be sent to a respective REST endpoint:

```bash
curl "http://<SERVER_ADDRESS>:8070/api/heartbeat" -H "Content-Type: application/json"
```

The required request parameters are:

* `SERVER_ADDRESS` - the server address on which the X-Road Catalog Lister is running on, e.g., `localhost`.

Response in JSON:
```json
{"appWorking":true,
 "dbWorking":true,
 "appName":"X-Road Catalog Lister",
 "appVersion":"1.2.1",
 "systemTime":[2021,9,20,10,12,15,132000000],
 "lastCollectionData":
 {"membersLastFetched":[2021,9,20,10,8,51,380000000],
  "subsystemsLastFetched":[2021,9,20,10,8,51,380000000],
  "servicesLastFetched":[2021,9,1,15,32,51,123000000],
  "wsdlsLastFetched":[2021,9,1,15,32,53,87000000],
  "openapisLastFetched":[2020,11,22,22,12,32,202000000]
 }
}
```

The response has the following fields:

* `appWorking`
* `dbWorking`
* `appName`
* `appVersion`
* `systemTime`
* `lastCollectionData`
    * `membersLastFetched`
    * `subsystemsLastFetched`
    * `servicesLastFetched`
    * `wsdlsLastFetched`
    * `openapisLastFetched`

### 3.2.6 List distinct service statistics

In order to fetch information about distinct service statistics in the X-Road Catalog, an HTTP request has to be sent to a respective REST endpoint:

```bash
curl "http://<SERVER_ADDRESS>:8070/api/getDistinctServiceStatistics?startDate=<START_DATE>&endDate=<END_DATE>" -H "Content-Type: application/json"
```

The required request parameters are:

* `SERVER_ADDRESS` - the server address on which the X-Road Catalog Lister is running on, e.g., `localhost`.
* `START_DATE` - an optional parameter(a string in format `YYYY-MM-DD`), if not used, today's date will be assumed.
* `END_DATE` - an optional parameter(a string in format `YYYY-MM-DD`), if not used, today's date will be assumed.

Response in JSON:
```json
{
   "distinctServiceStatisticsList":[
      {
         "created": "2020-11-20T00:00:00",
         "numberOfDistinctServices":3
      },
      {
         "created": "2020-11-21T00:00:00",
         "numberOfDistinctServices":3
      },
      {
         "created": "2020-11-22T00:00:00",
         "numberOfDistinctServices":3
      },
      {
         "created": "2020-11-23T00:00:00",
         "numberOfDistinctServices":3
      },
      {
         "created": "2020-11-24T00:00:00",
         "numberOfDistinctServices":3
      },
      {
         "created": "2020-11-25T00:00:00",
         "numberOfDistinctServices":3
      },
      {
         "created": "2020-11-26T00:00:00",
         "numberOfDistinctServices":3
      },
      {
         "created": "2020-11-27T00:00:00",
         "numberOfDistinctServices":3
      },
      {
         "created": "2020-11-28T00:00:00",
         "numberOfDistinctServices":3
      },
      {
         "created": "2020-11-29T00:00:00",
         "numberOfDistinctServices":3
      },
      {
         "created": "2020-11-30T00:00:00",
         "numberOfDistinctServices":3
      }
   ]
}
```

The response has the following fields:

* `serviceStatisticsList`
    * `created`
    * `numberOfDistinctServices`

### 3.2.7 List errors

In order to fetch information about errors during data harvesting in the X-Road Catalog, an HTTP request has to be sent to a respective REST endpoint:

List errors for a given subsystem:
```bash
curl "http://<SERVER_ADDRESS>:8070/api/listErrors/<INSTANCE>/<MEMBER_CLASS>/<MEMBER_CODE>/<SUBSYSTEM_CODE>?startDate=<START_DATE>&endDate=<END_DATE>" -H "Content-Type: application/json"
```

List errors for a given member:
```bash
curl "http://<SERVER_ADDRESS>:8070/api/listErrors/<INSTANCE>/<MEMBER_CLASS>/<MEMBER_CODE>?startDate=<START_DATE>&endDate=<END_DATE>" -H "Content-Type: application/json"
```

List errors for a given member class:
```bash
curl "http://<SERVER_ADDRESS>:8070/api/listErrors/<INSTANCE>/<MEMBER_CLASS>?startDate=<START_DATE>&endDate=<END_DATE>" -H "Content-Type: application/json"
```

List errors for a given instance:
```bash
curl "http://<SERVER_ADDRESS>:8070/api/listErrors/<INSTANCE>?startDate=<START_DATE>&endDate=<END_DATE>" -H "Content-Type: application/json"
```

List errors for all the instances and members:
```bash
curl "http://<SERVER_ADDRESS>:8070/api/listErrors?startDate=<START_DATE>&endDate=<END_DATE>" -H "Content-Type: application/json"
```

List errors for a given subsystem with pagination:
```bash
curl "http://<SERVER_ADDRESS>:8070/api/listErrors/<INSTANCE>/<MEMBER_CLASS>/<MEMBER_CODE>/<SUBSYSTEM_CODE>?startDate=<START_DATE>&endDate=<END_DATE>&page=<PAGE_NUMBER>&limit=<NO_OF_ERRORS_PER_PAGE>" -H "Content-Type: application/json"
```

List errors for a given member with pagination:
```bash
curl "http://<SERVER_ADDRESS>:8070/api/listErrors/<INSTANCE>/<MEMBER_CLASS>/<MEMBER_CODE>?startDate=<START_DATE>&endDate=<END_DATE>&page=<PAGE_NUMBER>&limit=<NO_OF_ERRORS_PER_PAGE>" -H "Content-Type: application/json"
```

List errors for a given member class with pagination:
```bash
curl "http://<SERVER_ADDRESS>:8070/api/listErrors/<INSTANCE>/<MEMBER_CLASS>?startDate=<START_DATE>&endDate=<END_DATE>&page=<PAGE_NUMBER>&limit=<NO_OF_ERRORS_PER_PAGE>" -H "Content-Type: application/json"
```

List errors for a given instance with pagination:
```bash
curl "http://<SERVER_ADDRESS>:8070/api/listErrors/<INSTANCE>?startDate=<START_DATE>&endDate=<END_DATE>&page=<PAGE_NUMBER>&limit=<NO_OF_ERRORS_PER_PAGE>" -H "Content-Type: application/json"
```

List errors for all the instances and members with pagination:
```bash
curl "http://<SERVER_ADDRESS>:8070/api/listErrors?startDate=<START_DATE>&endDate=<END_DATE>&page=<PAGE_NUMBER>&limit=<NO_OF_ERRORS_PER_PAGE>" -H "Content-Type: application/json"
```

Example request:
```bash
curl "http://localhost:8900/api/listErrors/DEV/GOV/1234?startDate=2021-08-24&endDate=2021-08-25&page=0&limit=10" -H "Content-Type: application/json"
```

The request parameters are:

* `SERVER_ADDRESS` - the server address on which the X-Road Catalog Lister is running on, e.g., `localhost`.
* `INSTANCE` - name of X-Road instance, e.g., `DEV`.
* `MEMBER_CLASS` - member class, e.g., `GOV`.
* `MEMBER_CODE` - member code, e.g., `1234`.
* `SUBSYSTEM_CODE` - subsystem code, e.g., `TEST`.
* `START_DATE` - (*optional*) a string in format `YYYY-MM-DD`, if not used, today's date will be assumed.
* `END_DATE` - (*optional*) a string in format `YYYY-MM-DD`, if not used, today's date will be assumed.
* `PAGE_NUMBER` - the number of page of the fetched results.
* `NO_OF_ERRORS_PER_PAGE` - number of errors per fetched page.

Response in JSON:
```json
{
   "pageNumber":0,
   "pageSize":10,
   "numberOfPages":2,
   "errorLogList":[
      {
         "id":38,
         "message":"Fetch of REST services failed(url: http://ss3/r1/DEV/GOV/1234/TEST/listMethods): 500 Server Error",
         "code":"500",
         "created": "2020-08-24T16:21:13",
         "memberClass":"GOV",
         "memberCode":"1234",
         "subsystemCode":"TEST",
         "groupCode":"",
         "serviceCode":"",
         "serviceVersion":null,
         "securityCategoryCode":"",
         "serverCode":"",
         "xroadInstance":"DEV"
      },
      {
         "id":39,
         "message":"Fetch of REST services failed(url: http://ss3/r1/DEV/GOV/1234/TESTCLIENT/listMethods): 500 Server Error",
         "code":"500",
         "created": "2020-08-24T16:21:13",
         "memberClass":"GOV",
         "memberCode":"1234",
         "subsystemCode":"TESTCLIENT",
         "groupCode":"",
         "serviceCode":"",
         "serviceVersion":null,
         "securityCategoryCode":"",
         "serverCode":"",
         "xroadInstance":"DEV"
      },
      {
         "id":40,
         "message":"Fetch of REST services failed(url: http://ss3/r1/DEV/GOV/1234/MASTER/listMethods): 500 Server Error",
         "code":"500",
         "created": "2020-08-24T16:21:13",
         "memberClass":"GOV",
         "memberCode":"1234",
         "subsystemCode":"MASTER",
         "groupCode":"",
         "serviceCode":"",
         "serviceVersion":null,
         "securityCategoryCode":"",
         "serverCode":"",
         "xroadInstance":"DEV"
      },
      {
         "id":41,
         "message":"Fetch of REST services failed(url: http://ss3/r1/DEV/GOV/1234/MANAGEMENT/listMethods): 500 Server Error",
         "code":"500",
         "created": "2020-08-24T16:31:39",
         "memberClass":"GOV",
         "memberCode":"1234",
         "subsystemCode":"MANAGEMENT",
         "groupCode":"",
         "serviceCode":"",
         "serviceVersion":null,
         "securityCategoryCode":"",
         "serverCode":"",
         "xroadInstance":"DEV"
      },
      {
         "id":42,
         "message":"Fetch of REST services failed(url: http://ss3/r1/DEV/GOV/1234/TESTCLIENT/listMethods): 500 Server Error",
         "code":"500",
         "created": "2020-08-24T16:31:39",
         "memberClass":"GOV",
         "memberCode":"1234",
         "subsystemCode":"TESTCLIENT",
         "groupCode":"",
         "serviceCode":"",
         "serviceVersion":null,
         "securityCategoryCode":"",
         "serverCode":"",
         "xroadInstance":"DEV"
      },
      {
         "id":43,
         "message":"Fetch of REST services failed(url: http://ss3/r1/DEV/GOV/1234/MANAGEMENT/listMethods): 500 Server Error",
         "code":"500",
         "created": "2020-08-24T16:34:46",
         "memberClass":"GOV",
         "memberCode":"1234",
         "subsystemCode":"MANAGEMENT",
         "groupCode":"",
         "serviceCode":"",
         "serviceVersion":null,
         "securityCategoryCode":"",
         "serverCode":"",
         "xroadInstance":"DEV"
      },
      {
         "id":44,
         "message":"Fetch of REST services failed(url: http://ss3/r1/DEV/GOV/1234/TESTCLIENT/listMethods): 500 Server Error",
         "code":"500",
         "created": "2020-08-24T16:34:46",
         "memberClass":"GOV",
         "memberCode":"1234",
         "subsystemCode":"TESTCLIENT",
         "groupCode":"",
         "serviceCode":"",
         "serviceVersion":null,
         "securityCategoryCode":"",
         "serverCode":"",
         "xroadInstance":"DEV"
      },
      {
         "id":45,
         "message":"Fetch of REST services failed(url: http://ss3/r1/DEV/GOV/1234/TESTCLIENT/listMethods): 500 Server Error",
         "code":"500",
         "created": "2020-08-24T16:36:25",
         "memberClass":"GOV",
         "memberCode":"1234",
         "subsystemCode":"TESTCLIENT",
         "groupCode":"",
         "serviceCode":"",
         "serviceVersion":null,
         "securityCategoryCode":"",
         "serverCode":"",
         "xroadInstance":"DEV"
      },
      {
         "id":46,
         "message":"Fetch of REST services failed(url: http://ss3/r1/DEV/GOV/1234/TESTCLIENT/listMethods): 500 Server Error",
         "code":"500",
         "created": "2020-08-24T16:39:30",
         "memberClass":"GOV",
         "memberCode":"1234",
         "subsystemCode":"TESTCLIENT",
         "groupCode":"",
         "serviceCode":"",
         "serviceVersion":null,
         "securityCategoryCode":"",
         "serverCode":"",
         "xroadInstance":"DEV"
      },
      {
         "id":47,
         "message":"Fetch of REST services failed(url: http://ss1/r1/DEV/GOV/1234/TESTCLIENT/listMethods): 500 Server Error",
         "code":"500",
         "created": "2020-08-24T16:41:34",
         "memberClass":"GOV",
         "memberCode":"1234",
         "subsystemCode":"TESTCLIENT",
         "groupCode":"",
         "serviceCode":"",
         "serviceVersion":null,
         "securityCategoryCode":"",
         "serverCode":"",
         "xroadInstance":"DEV"
      }
   ]
}
```

The response has the following fields:

* `pageNumber`
* `pageSize`
* `numberOfPages`
* `errorLogList`
    * `id`
    * `message`
    * `code`
    * `created`
    * `memberClass`
    * `memberCode`
    * `subsystemCode`
    * `groupCode`
    * `serviceCode`
    * `serviceVersion`
    * `securityCategoryCode`
    * `serverCode`
    * `xroadInstance`

### 3.2.8 List Security Servers

In order to fetch information about Security Servers in the X-Road Catalog, an HTTP request has to be sent to a respective REST endpoint:

```bash
curl "http://<SERVER_ADDRESS>:8070/api/listSecurityServers" -H "Content-Type: application/json"
```

The required request parameters are:

* `SERVER_ADDRESS` - the server address on which the X-Road Catalog Lister is running on, e.g., `localhost`.

Response:
```json
{
  "securityServerDataList": [
    {
      "owner": {
        "memberClass": "GOV",
        "memberCode": "1234",
        "name": "ACME",
        "subsystemCode": null
      },
      "serverCode": "SS1",
      "address": "SS1",
      "clients": [
        {
          "memberClass": "GOV",
          "memberCode": "1234",
          "name": "ACME",
          "subsystemCode": "MANAGEMENT"
        }
      ]
    },
    {
      "owner": {
        "memberClass": "GOV",
        "memberCode": "1234",
        "name": "ACME",
        "subsystemCode": null
      },
      "serverCode": "ss4",
      "address": "ss4",
      "clients": [
        {
          "memberClass": "GOV",
          "memberCode": "1234",
          "name": "ACME",
          "subsystemCode": "THESUBSYSTEM"
        },
        {
          "memberClass": "COM",
          "memberCode": "222",
          "name": "FRUIT",
          "subsystemCode": null
        }
      ]
    }
  ]
}
```

The response has the following fields:

* `securityServerDataList`
    * `owner`
        * `memberClass`
        * `memberCode`
        * `name`
        * `subsystemCode`
    * `serverCode`
    * `address`
    * `clients`
        * `memberClass`
        * `memberCode`
        * `name`
        * `subsystemCode`

The **owner** property indicates the owner member of the Security Server

The **clients** property provides a list of clients using the Security Server, where a client can be considered a member 
when their `subsystemCode` is `null`.


### 3.2.9 List descriptors

In order to fetch information about subsystem descriptions in the X-Road Catalog, an HTTP request has to be sent to a respective REST endpoint:

```bash
curl "http://<SERVER_ADDRESS>:8070/api/listDescriptors" -H "Content-Type: application/json"
```

The required parameters are:

* `SERVER_ADDRESS` - the server address on which the X-Road Catalog Lister is running on, e.g., `localhost`.

Response:

```json
[
    {
        "x_road_instance":"DEV",
        "subsystem_name":{
            "et":"Subsystem Name ET",
            "en":"Subsystem Name EN"
        },
        "email":[
            {
                "name":"Firstname Lastname",
                "email":"yourname@yourdomain"
            }
        ],
        "member_class":"GOV",
        "member_code":"1234",
        "member_name":"ACME",
        "subsystem_code":"MANAGEMENT"
    },
    {
        "x_road_instance":"DEV",
        "subsystem_name":{
            "et":"Subsystem Name ET",
            "en":"Subsystem Name EN"
        },
        "email":[
            {
                "name":"Firstname Lastname",
                "email":"yourname@yourdomain"
            }
        ],
        "member_class":"GOV",
        "member_code":"1234",
        "member_name":"ACME",
        "subsystem_code":"TEST"
    }
]
```

The response has the following fields:

A list of:

* `x_road_instance`
* `member_class`
* `member_code`
* `member_name`
* `subsystem_code`
* `subsystem_name`
* `et`
* `en`

A list of emails with:

* `name`
* `email address`

The **subsystem_name** property indicates a user-friendly name of the subsystem, in addition to the more technical 
`subsystem_code` property. In the current implementation, the `subsystem_name` property contains default values, because 
X-Road currently does not provide such information, but the fields are still required for the X-Road Metrics to operate 
correctly.

The **email** property is a list consisting of name of a contact person and their e-mail address. 
In the current implementation, the property contains default values, because X-Road currently does not provide such 
information, but the fields are still required for the X-Road Metrics to operate correctly.

### 3.2.10 Get endpoints

In order to fetch information about service endpoints belonging to a specific `OPENAPI3` or `REST` service in the X-Road Catalog, 
an HTTP request has to be sent to a respective REST endpoint:

```bash
curl "http://<SERVER_ADDRESS>:8070/api/getEndpoints/<INSTANCE>/<MEMBER_CLASS>/<MEMBER_CODE>/<SUBSYSTEM_CODE>/<SERVICE_CODE>" -H "Content-Type: application/json"
```

The required parameters are:

* `SERVER_ADDRESS` - the server address on which the X-Road Catalog Lister is running on, e.g., `localhost`.
* `INSTANCE` - name of X-Road instance, e.g., `DEV`.
* `MEMBER_CLASS` - member class, e.g., `GOV`.
* `MEMBER_CODE` - member code, e.g., `1234`.
* `SUBSYSTEM_CODE` - subsystem code, e.g., `TEST`.
* `SERVICE_CODE` - service code, e.g., `CATALOG_HEARTBEAT`.

Example request:
```bash
curl "http://localhost:8070/api/getEndpoints/DEV/GOV/1234/TEST/CATALOG_HEARTBEAT" -H "Content-Type: application/json"
```

Response:

```json
{
  "listOfServices":[
    {
      "memberClass":"GOV",
      "memberCode":"1234",
      "subsystemCode":"TEST",
      "serviceCode":"CATALOG_HEARTBEAT",
      "serviceVersion":null,
      "endpointList":[
        {
          "method":"GET",
          "path":"/heartbeat"
        }
      ],
      "xroadInstance":"DEV"
    }
  ]
}
```

The response has the following fields:

* `xroadInstance`
* `memberClass`
* `memberCode`
* `subsystemCode`
* `serviceCode`
* `serviceVersion`
* `endpointList`
    * `method`
    * `path`

### 3.2.11 Get Rest

In order to fetch information about a specific `REST` service in the X-Road Catalog, an HTTP request has to be sent to a respective REST endpoint:

```bash
curl "http://<SERVER_ADDRESS>:8070/api/getRest/<INSTANCE>/<MEMBER_CLASS>/<MEMBER_CODE>/<SUBSYSTEM_CODE>/<SERVICE_CODE>" -H "Content-Type: application/json"
```

The required request parameters are:

* `SERVER_ADDRESS` - the server address on which the X-Road Catalog Lister is running on, e.g., `localhost`.
* `INSTANCE` - name of X-Road instance, e.g., `DEV`.
* `MEMBER_CLASS` - member class, e.g., `GOV`.
* `MEMBER_CODE` - member code, e.g., `1234`.
* `SUBSYSTEM_CODE` - subsystem code, e.g., `TEST`.
* `SERVICE_CODE` - service code, e.g., `CATALOG_HEARTBEAT`.

Example request:
```bash
curl "http://localhost:8070/api/getRest/DEV/GOV/1234/TEST/CATALOG_HEARTBEAT" -H "Content-Type: application/json"
```

Response:

```json
{
  "listOfServices":[
    {
      "memberClass":"GOV",
      "memberCode":"1234",
      "subsystemCode":"TEST",
      "serviceCode":"CATALOG_HEARTBEAT",
      "serviceVersion":null,
      "endpointList":[
        {
          "method":"GET",
          "path":"/heartbeat"
        }
      ],
      "xroadInstance":"DEV"
    }
  ]
}
```


The response has the following fields:

* `xroadInstance`
* `memberClass`
* `memberCode`
* `subsystemCode`
* `serviceCode`
* `serviceVersion`
* `endpointList`
    * `method`
    * `path`

### 3.3 REST API V2

The V2 REST API is served under `/api/v2` and is always enabled; the `xroad-catalog.legacy-api.enabled` flag does not
affect it. It serves only **active** entities — members, subsystems, services and descriptors that have not been
removed from the X-Road ecosystem — scoped to the X-Road instance configured for the lister. Member class
descriptions, subsystem names and Security Server data are taken from the instance's global configuration
(`shared-params.xml`).

Every V2 endpoint is a `GET`; responses are `application/json`, except the two descriptor endpoints
([3.3.4](#334-descriptor-endpoints)) which return the stored document itself. Every V2 response carries a
server-generated `X-Request-Id` header; the same value appears in the lister's log lines. Inbound `X-Request-Id`
headers are ignored.

Service types are reported as `SOAP`, `OPENAPI`, `REST` or `UNKNOWN`; the V1 API reports the first two as `WSDL` and
`OPENAPI3`.

Path variables (`memberClass`, `memberCode`, `subsystemCode`, `serviceCode`) are matched exactly and case-sensitively.
Where a path addresses a service **version**, the literal string `null` selects the version without a version label.

An interactive description is served by the Swagger UI at `http://<SERVER_ADDRESS>:8070/api-docs`; the OpenAPI
document of the V2 API is at `/v3/api-docs/v2`. (The `v1` group, `/v3/api-docs/v1`, exists only while the legacy API
is enabled.)

#### 3.3.1 Common conventions

**Pagination.** Collection endpoints wrap their rows in one envelope:

```json
{
  "items": [ ... ],
  "totalCount": 42,
  "page": 1,
  "size": 20,
  "totalPages": 3
}
```

* `items` - the rows of this page.
* `totalCount` - the number of matching rows across all pages.
* `page` - the **1-based** page number served.
* `size` - the page size applied.
* `totalPages` - the number of pages (`0` when `totalCount` is `0`).

Endpoints marked *not paginated* below return the same envelope with only `items` and `totalCount`.

Paginated endpoints accept:

| Parameter   | Type    | Default           | Notes                                                                                |
|-------------|---------|-------------------|--------------------------------------------------------------------------------------|
| `page`      | integer | `1`               | 1-based; values below 1 are rejected with `400`.                                     |
| `size`      | integer | `20`              | `1`–`200`; values outside the range are rejected with `400`.                         |
| `sortBy`    | string  | endpoint-specific | Only on endpoints that list allowed values; an unknown field is rejected with `400`. |
| `sortOrder` | string  | `asc`             | `asc` or `desc`, case-insensitive.                                                   |

Sort order is deterministic, so page contents are stable between requests.

**Date windows (`since` / `until`).** The error and report endpoints select a window of calendar days. Both parameters
are optional dates in `YYYY-MM-DD` format, interpreted in the lister's local time zone, and the window is
half-open: `since` is inclusive (from 00:00 of that day) and `until` is **exclusive** (up to, but not including, 00:00
of that day). To include a given day, pass the following day as `until`. Defaults:

* `until` - tomorrow (the current day is included).
* `since` - `until` minus one day on the error endpoints (i.e. today only), `until` minus seven days on the report
  endpoints (the trailing week).

`since` must not be after `until`, and the window must not exceed 90 days; both violations are rejected with `400`.

**Timestamps.** `created`, `changed`, `fetched` and similar fields are ISO-8601 date-times with the lister's UTC
offset, e.g. `"2026-04-01T10:15:30+03:00"`. Report `date` fields are plain `YYYY-MM-DD` strings.

**Errors.** Every V2 error response uses one JSON body:

```json
{
  "status": 404,
  "error": "NotFound",
  "message": "Member 'GOV/1234567-8' not found"
}
```

| Status | When                                                                                                                                                               |
|--------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `400`  | Invalid or missing query parameter: pagination out of range, unknown sort field or order, malformed or too wide date window, invalid `serviceType`, too short `q`. |
| `404`  | The addressed member class, member, subsystem, service, version or descriptor does not exist (or is not active), or the path is unknown.                           |
| `405`  | A method other than `GET`.                                                                                                                                         |
| `406`  | An `Accept` header that excludes `application/json`.                                                                                                               |
| `409`  | The service-level descriptor shortcut was used on a service with several versions ([3.3.4](#334-descriptor-endpoints)); the body adds a `versions` array.          |
| `503`  | The lister has not yet loaded the global configuration (`shared-params.xml`); retry after startup completes.                                                       |
| `500`  | Unexpected failure; the message is always `Internal server error`, details are in the log.                                                                         |

#### 3.3.2 Heartbeat

##### GET /api/v2/heartbeat

Returns the health of the lister and a summary of the collector's most recent activity. Responds `200` when both
`appWorking` and `dbWorking` are `true`, otherwise `503` with the same body.

```bash
curl "http://<SERVER_ADDRESS>:8070/api/v2/heartbeat"
```

```json
{
  "appWorking": true,
  "dbWorking": true,
  "appName": "X-Road Catalog Lister",
  "appVersion": "4.0.0",
  "systemTime": "2026-04-01T10:15:30+03:00",
  "lastCollectionData": {
    "membersLastFetched": "2026-04-01T09:00:12+03:00",
    "subsystemsLastFetched": "2026-04-01T09:00:12+03:00",
    "servicesLastFetched": "2026-04-01T09:01:40+03:00",
    "wsdlsLastFetched": "2026-04-01T09:03:05+03:00",
    "openapisLastFetched": "2026-04-01T09:03:05+03:00",
    "restsLastFetched": "2026-04-01T09:03:05+03:00"
  },
  "lastRunErrors": 3,
  "descriptorAnomalies": 0,
  "globalConfExpired": false,
  "globalConfExpiresAt": "2026-04-02T09:00:00+03:00",
  "currentRun": {
    "started": "2026-04-01T10:00:00+03:00",
    "pendingItems": 37,
    "progressUpdated": "2026-04-01T10:15:02+03:00"
  }
}
```

* `appWorking` - `true` whenever the application answers.
* `dbWorking` - result of a database connectivity check.
* `appName`, `appVersion` - the running lister.
* `systemTime` - current server time.
* `lastCollectionData` - per entity type, when the latest *finished* collection run last fetched it; every field is
  `null` until a run has finished.
* `lastRunErrors` - number of error log entries written since the latest finished run started.
* `descriptorAnomalies` - number of active services with more than one active descriptor; values above `0` indicate
  inconsistent collector data, see the collector log.
* `globalConfExpired` - `true` when the downloaded global configuration is past its expiry (data is still served).
* `globalConfExpiresAt` - expiry time of the downloaded global configuration; `null` until the first download.
* `currentRun` - present only while a collection cycle is running: `started`, `pendingItems` (work items left) and
  `progressUpdated` (last change of `pendingItems`). A `pendingItems` value that does not decrease while
  `progressUpdated` advances indicates a stalled collector; a stale `progressUpdated` indicates an aborted run.

#### 3.3.3 Browse endpoints

The browse endpoints navigate the hierarchy member class → member → subsystem → service → version. Every parent
segment of a path is verified to exist as an active entity, otherwise the response is `404`.

##### GET /api/v2/browse/member-classes

Lists every member class: those declared in `shared-params.xml` and those with at least one active member. Sorted by
`code`; not paginated; no parameters.

```bash
curl "http://<SERVER_ADDRESS>:8070/api/v2/browse/member-classes"
```

```json
{
  "items": [
    { "code": "COM", "description": "Commercial", "memberCount": 12 },
    { "code": "GOV", "description": "Government", "memberCount": 2 }
  ],
  "totalCount": 2
}
```

* `code` - member class code.
* `description` - description from `shared-params.xml`, `null` when not declared there.
* `memberCount` - number of active members in the class.

##### GET /api/v2/browse/member-classes/{memberClass}

Returns one member class with the fields above. `404` when the code is neither declared nor in use.

##### GET /api/v2/browse/member-classes/{memberClass}/members

Paginated list of the active members of a class.

| Parameter                   | Default | Notes                                                |
|-----------------------------|---------|------------------------------------------------------|
| `page`, `size`, `sortOrder` |         | See [3.3.1](#331-common-conventions).                |
| `sortBy`                    | `name`  | Allowed: `name`, `memberCode`, `created`, `changed`. |

```bash
curl "http://<SERVER_ADDRESS>:8070/api/v2/browse/member-classes/GOV/members?page=1&size=20&sortBy=name&sortOrder=asc"
```

```json
{
  "items": [
    {
      "memberClass": "GOV",
      "memberCode": "1234567-8",
      "name": "Tax Authority",
      "provider": true,
      "subsystemCount": 2,
      "serviceCount": 5,
      "created": "2025-11-03T08:12:44+02:00",
      "changed": "2026-03-20T14:02:10+02:00",
      "fetched": "2026-04-01T09:00:12+03:00"
    }
  ],
  "totalCount": 1,
  "page": 1,
  "size": 20,
  "totalPages": 1
}
```

Member fields:

* `memberClass`, `memberCode`, `name`
* `provider` - `true` when the member has at least one subsystem with an active service.
* `subsystemCount`, `serviceCount` - active subsystems and services of the member.
* `created`, `changed`, `fetched` - when the member was first seen, last changed and last confirmed by the collector.

##### GET /api/v2/browse/member-classes/{memberClass}/members/{memberCode}

Returns one active member.

| Parameter | Type    | Default | Notes                                                                                      |
|-----------|---------|---------|--------------------------------------------------------------------------------------------|
| `full`    | boolean | `false` | `true` embeds the member's subsystems, each with its services and their version summaries. |

```bash
curl "http://<SERVER_ADDRESS>:8070/api/v2/browse/member-classes/GOV/members/1234567-8?full=true"
```

```json
{
  "memberClass": "GOV",
  "memberCode": "1234567-8",
  "name": "Tax Authority",
  "provider": true,
  "subsystemCount": 1,
  "serviceCount": 1,
  "created": "2025-11-03T08:12:44+02:00",
  "changed": "2026-03-20T14:02:10+02:00",
  "fetched": "2026-04-01T09:00:12+03:00",
  "subsystems": [
    {
      "memberClass": "GOV",
      "memberCode": "1234567-8",
      "memberName": "Tax Authority",
      "subsystemCode": "TaxServices",
      "subsystemName": "Tax services",
      "serviceCount": 1,
      "created": "2025-11-03T08:12:44+02:00",
      "changed": "2026-03-20T14:02:10+02:00",
      "fetched": "2026-04-01T09:00:12+03:00",
      "services": [
        {
          "memberClass": "GOV",
          "memberCode": "1234567-8",
          "memberName": "Tax Authority",
          "subsystemCode": "TaxServices",
          "serviceCode": "getTaxReport",
          "serviceTypes": ["OPENAPI"],
          "versionCount": 1,
          "versions": [
            {
              "serviceVersion": "v1",
              "serviceType": "OPENAPI",
              "created": "2025-11-03T08:14:02+02:00",
              "changed": "2026-02-11T11:30:00+02:00",
              "fetched": "2026-04-01T09:01:40+03:00"
            }
          ]
        }
      ]
    }
  ]
}
```

Without `full=true` the response is the member object alone. Subsystems and services are sorted by code.

##### GET /api/v2/browse/member-classes/{memberClass}/members/{memberCode}/subsystems

Active subsystems of a member, ordered by `subsystemCode`; not paginated. `404` when the member does not exist; an
empty `items` list when it has no active subsystems.

```bash
curl "http://<SERVER_ADDRESS>:8070/api/v2/browse/member-classes/GOV/members/1234567-8/subsystems"
```

```json
{
  "items": [
    {
      "memberClass": "GOV",
      "memberCode": "1234567-8",
      "memberName": "Tax Authority",
      "subsystemCode": "TaxServices",
      "subsystemName": "Tax services",
      "serviceCount": 3,
      "created": "2025-11-03T08:12:44+02:00",
      "changed": "2026-03-20T14:02:10+02:00",
      "fetched": "2026-04-01T09:00:12+03:00"
    }
  ],
  "totalCount": 1
}
```

Subsystem fields: `memberClass`, `memberCode`, `memberName`, `subsystemCode`, `subsystemName` (from
`shared-params.xml`, `null` when not declared), `serviceCount`, `created`, `changed`, `fetched`.

##### GET /api/v2/browse/member-classes/{memberClass}/members/{memberCode}/subsystems/{subsystemCode}

Returns one active subsystem with the fields above.

##### GET /api/v2/browse/member-classes/{memberClass}/members/{memberCode}/security-servers

Security Servers **owned** by the member, from `shared-params.xml`, with their client lists; not paginated.

```bash
curl "http://<SERVER_ADDRESS>:8070/api/v2/browse/member-classes/GOV/members/1234567-8/security-servers"
```

```json
{
  "items": [
    {
      "serverCode": "SS1",
      "address": "ss1.example.org",
      "owner": { "memberClass": "GOV", "memberCode": "1234567-8", "name": "Tax Authority" },
      "clients": [
        { "memberClass": "GOV", "memberCode": "1234567-8", "subsystemCode": "TaxServices" },
        { "memberClass": "COM", "memberCode": "9876543-2", "subsystemCode": null }
      ]
    }
  ],
  "totalCount": 1
}
```

* `serverCode`, `address` - the Security Server.
* `owner` - the owning member.
* `clients` - registered clients; `subsystemCode` is `null` for a member-level client.

##### GET /api/v2/browse/member-classes/{memberClass}/members/{memberCode}/subsystems/{subsystemCode}/services

Services of a subsystem, one item per `serviceCode` with all its versions, ordered by `serviceCode`; not paginated.

```bash
curl "http://<SERVER_ADDRESS>:8070/api/v2/browse/member-classes/GOV/members/1234567-8/subsystems/TaxServices/services"
```

```json
{
  "items": [
    {
      "memberClass": "GOV",
      "memberCode": "1234567-8",
      "memberName": "Tax Authority",
      "subsystemCode": "TaxServices",
      "serviceCode": "getTaxReport",
      "serviceTypes": ["SOAP", "OPENAPI"],
      "versionCount": 2,
      "versions": [
        { "serviceVersion": "v1", "serviceType": "SOAP",    "created": "2025-11-03T08:14:02+02:00", "changed": "2026-02-11T11:30:00+02:00", "fetched": "2026-04-01T09:01:40+03:00" },
        { "serviceVersion": "v2", "serviceType": "OPENAPI", "created": "2026-01-15T09:00:00+02:00", "changed": "2026-01-15T09:00:00+02:00", "fetched": "2026-04-01T09:01:40+03:00" }
      ]
    }
  ],
  "totalCount": 1
}
```

Service fields:

* `memberClass`, `memberCode`, `memberName`, `subsystemCode`, `serviceCode`
* `serviceTypes` - the distinct types among the versions: `SOAP`, `OPENAPI`, `REST` or `UNKNOWN`. `UNKNOWN` means the
  service has been discovered but its description not yet fetched; it resolves within one collection interval.
* `versionCount`, `versions` - the active versions, each with `serviceVersion` (`null` for an unlabeled version, sorted
  last), `serviceType`, `created`, `changed`, `fetched`.

##### GET /api/v2/browse/member-classes/{memberClass}/members/{memberCode}/subsystems/{subsystemCode}/services/{serviceCode}

Returns one service with the fields above. `404` when the service has no active version.

##### GET /api/v2/browse/member-classes/{memberClass}/members/{memberCode}/subsystems/{subsystemCode}/services/{serviceCode}/versions

All active versions of a service with their endpoints; not paginated.

```bash
curl "http://<SERVER_ADDRESS>:8070/api/v2/browse/member-classes/GOV/members/1234567-8/subsystems/TaxServices/services/getTaxReport/versions"
```

```json
{
  "items": [
    {
      "serviceVersion": "v2",
      "serviceType": "OPENAPI",
      "hasDescriptor": true,
      "endpoints": [
        { "method": "GET", "path": "/reports/{year}" },
        { "method": "POST", "path": "/reports" }
      ],
      "created": "2026-01-15T09:00:00+02:00",
      "changed": "2026-01-15T09:00:00+02:00",
      "fetched": "2026-04-01T09:01:40+03:00"
    }
  ],
  "totalCount": 1
}
```

Version fields:

* `serviceVersion` - `null` for an unlabeled version.
* `serviceType` - `SOAP`, `OPENAPI`, `REST` or `UNKNOWN`.
* `hasDescriptor` - `true` when a WSDL or OpenAPI document is available ([3.3.4](#334-descriptor-endpoints)).
* `endpoints` - `method` and `path` pairs for `REST` and `OPENAPI` services; empty for `SOAP`.
* `created`, `changed`, `fetched`

##### GET /api/v2/browse/member-classes/{memberClass}/members/{memberCode}/subsystems/{subsystemCode}/services/{serviceCode}/versions/{serviceVersion}

Returns one version with the fields above. Use `null` as `{serviceVersion}` for the unlabeled version.

#### 3.3.4 Descriptor endpoints

The descriptor endpoints return the stored service description document itself rather than JSON: a WSDL as
`application/xml`, an OpenAPI document as `application/json` or `application/yaml` depending on how the service
publishes it.

##### GET /api/v2/browse/member-classes/{memberClass}/members/{memberCode}/subsystems/{subsystemCode}/services/{serviceCode}/versions/{serviceVersion}/descriptor

The descriptor of one service version. `404` when the version does not exist or has no descriptor.

```bash
curl "http://<SERVER_ADDRESS>:8070/api/v2/browse/member-classes/GOV/members/1234567-8/subsystems/TaxServices/services/getTaxReport/versions/v1/descriptor" --output getTaxReport-v1.wsdl
```

##### GET /api/v2/browse/member-classes/{memberClass}/members/{memberCode}/subsystems/{subsystemCode}/services/{serviceCode}/descriptor

Shortcut for services with exactly one active version. `404` when the service has no version or its only version has
no descriptor; `409` when the service has several versions; the body lists them:

```json
{
  "status": 409,
  "error": "Conflict",
  "message": "Service has multiple versions; pick a specific version via /versions/{serviceVersion}/descriptor",
  "versions": ["v1", "v2"]
}
```

A `null` element in `versions` denotes the unlabeled version (`/versions/null/descriptor`).

#### 3.3.5 Error log endpoints

Errors recorded by the collector, filtered by the path segments. Path segments are filters only: an unknown member or
subsystem yields an empty page, not `404`.

* `GET /api/v2/browse/errors` - all errors of the instance.
* `GET /api/v2/browse/member-classes/{memberClass}/errors`
* `GET /api/v2/browse/member-classes/{memberClass}/members/{memberCode}/errors`
* `GET /api/v2/browse/member-classes/{memberClass}/members/{memberCode}/subsystems/{subsystemCode}/errors`
* `GET /api/v2/browse/member-classes/{memberClass}/members/{memberCode}/subsystems/{subsystemCode}/services/{serviceCode}/errors`
* `GET /api/v2/browse/member-classes/{memberClass}/members/{memberCode}/subsystems/{subsystemCode}/services/{serviceCode}/versions/{serviceVersion}/errors`

| Parameter      | Default              | Notes                                                                     |
|----------------|----------------------|---------------------------------------------------------------------------|
| `since`        | `until` minus 1 day  | See [3.3.1](#331-common-conventions); the defaults select today's errors. |
| `until`        | tomorrow (exclusive) | At most 90 days after `since`.                                            |
| `page`, `size` | `1`, `20`            |                                                                           |
| `sortBy`       | `created`            | Allowed: `created`, `code`.                                               |
| `sortOrder`    | `desc`               | Newest first by default.                                                  |

```bash
curl "http://<SERVER_ADDRESS>:8070/api/v2/browse/member-classes/GOV/members/1234567-8/errors?since=2026-03-25&until=2026-04-02"
```

```json
{
  "items": [
    {
      "message": "Fetch of WSDL failed: 500 Internal Server Error",
      "code": "500",
      "memberClass": "GOV",
      "memberCode": "1234567-8",
      "subsystemCode": "TaxServices",
      "serviceCode": "getTaxReport",
      "serviceVersion": "v1",
      "created": "2026-03-31T09:03:05+03:00"
    }
  ],
  "totalCount": 1,
  "page": 1,
  "size": 20,
  "totalPages": 1
}
```

* `message` - the error as recorded by the collector.
* `code` - typically the HTTP status the collector received.
* `memberClass`, `memberCode`, `subsystemCode`, `serviceCode`, `serviceVersion` - the entity the error concerns; the
  finer-grained fields are `null` for errors at a higher level.
* `created` - when the error was recorded.

> [!NOTE]
> Error messages are stored verbatim and can contain the internal address of the service that failed. See the
> [Installation Guide](xroad_catalog_installation_guide.md#23-trust-assumptions) before exposing these endpoints to
> parties who must not learn internal addresses.

#### 3.3.6 List endpoints

Flat, instance-wide, paginated listings.

##### GET /api/v2/list/security-servers

All Security Servers of the instance from `shared-params.xml`, with their client count.

| Parameter                   | Default      | Notes                                 |
|-----------------------------|--------------|---------------------------------------|
| `page`, `size`, `sortOrder` |              | See [3.3.1](#331-common-conventions). |
| `sortBy`                    | `serverCode` | Allowed: `serverCode`, `address`.     |

```bash
curl "http://<SERVER_ADDRESS>:8070/api/v2/list/security-servers?sortBy=address"
```

```json
{
  "items": [
    {
      "serverCode": "SS1",
      "address": "ss1.example.org",
      "owner": { "memberClass": "GOV", "memberCode": "1234567-8", "name": "Tax Authority" },
      "clientCount": 4
    }
  ],
  "totalCount": 1,
  "page": 1,
  "size": 20,
  "totalPages": 1
}
```

##### GET /api/v2/list/members

Active members of the instance.

| Parameter                   | Type    | Default | Notes                                                       |
|-----------------------------|---------|---------|-------------------------------------------------------------|
| `memberClass`               | string  | none    | Exact-match filter.                                         |
| `provider`                  | boolean | none    | `true` or `false` filters on provider status; omitted: all. |
| `sortBy`                    | string  | `name`  | Allowed: `name`, `memberCode`, `created`, `changed`.        |
| `page`, `size`, `sortOrder` |         |         | See [3.3.1](#331-common-conventions).                       |

```bash
curl "http://<SERVER_ADDRESS>:8070/api/v2/list/members?memberClass=GOV&provider=true&sortBy=changed&sortOrder=desc"
```

The response is the pagination envelope of member objects as in [3.3.3](#333-browse-endpoints).

##### GET /api/v2/list/subsystems

Active subsystems of the instance.

| Parameter                   | Type   | Default         | Notes                                           |
|-----------------------------|--------|-----------------|-------------------------------------------------|
| `memberClass`               | string | none            | Exact-match filter.                             |
| `sortBy`                    | string | `subsystemCode` | Allowed: `subsystemCode`, `created`, `changed`. |
| `page`, `size`, `sortOrder` |        |                 | See [3.3.1](#331-common-conventions).           |

```bash
curl "http://<SERVER_ADDRESS>:8070/api/v2/list/subsystems?memberClass=GOV"
```

The response is the pagination envelope of subsystem objects as in [3.3.3](#333-browse-endpoints).

##### GET /api/v2/list/services

Active services of the instance, one item per subsystem and `serviceCode`, always ordered by `serviceCode` (no
`sortBy` / `sortOrder`).

| Parameter      | Type   | Default | Notes                                                                                                             |
|----------------|--------|---------|-------------------------------------------------------------------------------------------------------------------|
| `memberClass`  | string | none    | Exact-match filter.                                                                                               |
| `serviceType`  | string | none    | `SOAP`, `REST`, `OPENAPI` or `UNKNOWN` (case-sensitive); matches services with at least one version of that type. |
| `page`, `size` |        |         | See [3.3.1](#331-common-conventions).                                                                             |

```bash
curl "http://<SERVER_ADDRESS>:8070/api/v2/list/services?serviceType=OPENAPI&size=50"
```

The response is the pagination envelope of service objects as in [3.3.3](#333-browse-endpoints).

#### 3.3.7 Search

##### GET /api/v2/search

Case-insensitive substring search over active member codes and names, subsystem codes and service codes. The result
is a mixed list of hits discriminated by `type`, ordered by the matched value.

| Parameter      | Type   | Required | Notes                                                          |
|----------------|--------|----------|----------------------------------------------------------------|
| `q`            | string | yes      | At least 3 characters; `%`, `_` and `\` are matched literally. |
| `page`, `size` |        | no       | See [3.3.1](#331-common-conventions).                          |

```bash
curl "http://<SERVER_ADDRESS>:8070/api/v2/search?q=tax"
```

```json
{
  "items": [
    { "type": "member", "memberClass": "GOV", "memberCode": "1234567-8", "name": "Tax Authority", "provider": true },
    { "type": "subsystem", "memberClass": "GOV", "memberCode": "1234567-8", "memberName": "Tax Authority", "subsystemCode": "TaxServices" },
    { "type": "service", "memberClass": "GOV", "memberCode": "1234567-8", "memberName": "Tax Authority", "subsystemCode": "TaxServices", "serviceCode": "getTaxReport", "serviceTypes": ["REST"] }
  ],
  "totalCount": 3,
  "page": 1,
  "size": 20,
  "totalPages": 1
}
```

Hit shapes:

* `type: "member"` - `memberClass`, `memberCode`, `name`, `provider`.
* `type: "subsystem"` - `memberClass`, `memberCode`, `memberName`, `subsystemCode`.
* `type: "service"` - `memberClass`, `memberCode`, `memberName`, `subsystemCode`, `serviceCode`, `serviceTypes`.

On large catalogs see [Search performance](xroad_catalog_installation_guide.md#15-search-performance) in the
Installation Guide.

#### 3.3.8 Reports

Both report endpoints accept the date window parameters of [3.3.1](#331-common-conventions), with `since` defaulting to
`until` minus seven days. The V2 reports are JSON only; the CSV variants exist in the V1 API only.

##### GET /api/v2/reports/service-statistics

Per-day counts of active services by type over the window: exactly one row per calendar day, in chronological order,
zero-filled; not paginated (at most 90 rows). Services of type `UNKNOWN` are not counted.

```bash
curl "http://<SERVER_ADDRESS>:8070/api/v2/reports/service-statistics?since=2026-04-01&until=2026-04-03"
```

```json
{
  "items": [
    { "date": "2026-04-01", "soapServices": 120, "restServices": 85, "openApiServices": 43 },
    { "date": "2026-04-02", "soapServices": 121, "restServices": 85, "openApiServices": 44 }
  ],
  "totalCount": 2
}
```

##### GET /api/v2/reports/changes

Paginated per-day change log over the window. Each item is one day with `created`, `modified` and `removed` groups,
each holding `members`, `subsystems` and `services` buckets. Days without changes are omitted, so `totalCount` counts
days with events. Chronological, oldest first.

| Parameter      | Default              |
|----------------|----------------------|
| `since`        | `until` minus 7 days |
| `until`        | tomorrow (exclusive) |
| `page`, `size` | `1`, `20`            |

```bash
curl "http://<SERVER_ADDRESS>:8070/api/v2/reports/changes?since=2026-04-01&until=2026-04-08"
```

```json
{
  "items": [
    {
      "date": "2026-04-01",
      "created": {
        "members":    { "count": 1, "items": [ { "memberClass": "GOV", "memberCode": "1234567-8", "name": "Tax Authority" } ] },
        "subsystems": { "count": 0, "items": [] },
        "services":   { "count": 0, "items": [] }
      },
      "modified": {
        "members":    { "count": 0, "items": [] },
        "subsystems": { "count": 1, "items": [ { "memberClass": "GOV", "memberCode": "1234567-8", "memberName": "Tax Authority", "subsystemCode": "TaxServices" } ] },
        "services":   { "count": 0, "items": [] }
      },
      "removed": {
        "members":    { "count": 0, "items": [] },
        "subsystems": { "count": 0, "items": [] },
        "services":   { "count": 1, "items": [ { "memberClass": "GOV", "memberCode": "1234567-8", "memberName": "Tax Authority", "subsystemCode": "TaxServices", "serviceCode": "legacyLookup", "serviceVersion": "v1", "serviceType": "SOAP" } ] }
      }
    }
  ],
  "totalCount": 1,
  "page": 1,
  "size": 20,
  "totalPages": 1
}
```

* `date` - the day.
* `created`, `modified`, `removed` - each with `members`, `subsystems` and `services` buckets of `count` and `items`;
  all nine buckets are always present.
* Member items carry `memberClass`, `memberCode`, `name`; subsystem items add `memberName` and `subsystemCode`;
  service items add `serviceCode`, `serviceVersion` and `serviceType`.

### 4. X-Road Catalog Persistence

The purpose of the module is to persist and read persisted data. Used by the X-Road Catalog Collector and X-Road 
Catalog Lister modules.

More information about the [X-Road Catalog Persistence](../xroad-catalog-persistence/README.md) module.
