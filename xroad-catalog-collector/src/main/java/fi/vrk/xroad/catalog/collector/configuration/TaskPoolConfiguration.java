/**
 *
 *  The MIT License
 *
 *  Copyright (c) 2023- Nordic Institute for Interoperability Solutions (NIIS)
 *  Copyright (c) 2016-2023 Finnish Digital Agency
 *
 *  Permission is hereby granted, free of charge, to any person obtaining a copy
 *  of this software and associated documentation files (the "Software"), to deal
 *  in the Software without restriction, including without limitation the rights
 *  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  copies of the Software, and to permit persons to whom the Software is
 *  furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in
 *  all copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 *  THE SOFTWARE.
 *
 */
package fi.vrk.xroad.catalog.collector.configuration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import lombok.Getter;

@Getter
@Configuration
public class TaskPoolConfiguration {

    // X-Road instance parameters

    @Value("${xroad-catalog.xroad-instance}")
    private String xroadInstance;

    @Value("${xroad-catalog.member-class}")
    private String memberClass;

    @Value("${xroad-catalog.member-code}")
    private String memberCode;

    @Value("${xroad-catalog.subsystem-code}")
    private String subsystemCode;

    // Security server URLs

    @Value("${xroad-catalog.security-server-host}")
    private String securityServerHost;

    @Value("${xroad-catalog.webservices-endpoint}")
    private String webservicesEndpoint;

    @Value("${xroad-catalog.list-clients-host}")
    private String listClientsHost;

    @Value("${xroad-catalog.fetch-wsdl-host}")
    private String fetchWsdlHost;

    @Value("${xroad-catalog.fetch-openapi-host}")
    private String fetchOpenapiHost;

    // Parameters related to the "fi" profile

    @Value("${xroad-catalog.fetch-organizations-url}")
    private String fetchOrganizationsUrl;

    @Value("${xroad-catalog.fetch-companies-url}")
    private String fetchCompaniesUrl;

    @Value("${xroad-catalog.fetch-external-limit:500}")
    private int fetchExternalLimit;

    @Value("${xroad-catalog.fetch-external-update-after-days:7}")
    private int fetchExternalUpdateAfterDays;

    @Value("${xroad-catalog.fetch-external-interval-min:20}")
    private long fetchExternalInterval;

    @Value("${xroad-catalog.fetch-external-run-unlimited:false}")
    private boolean fetchExternalRunUnlimited;

    @Value("${xroad-catalog.fetch-external-time-after-hour:3}")
    private int fetchExternalTimeAfterHour;

    @Value("${xroad-catalog.fetch-external-time-before-hour:4}")
    private int fetchExternalTimeBeforeHour;

    @Value("${xroad-catalog.fetch-organizations-pool-size:10}")
    private int fetchOrganizationsPoolSize;

    @Value("${xroad-catalog.fetch-companies-pool-size:10}")
    private int fetchCompaniesPoolSize;

    // Parameters handling database log storage

    @Value("${xroad-catalog.flush-log-time-after-hour:3}")
    private int flushLogTimeAfterHour;

    @Value("${xroad-catalog.flush-log-time-before-hour:4}")
    private int flushLogTimeBeforeHour;

    @Value("${xroad-catalog.error-log-length-in-days:90}")
    private int errorLogLengthInDays;

    // Parameters controlling how often data is collected from the X-Road instance

    @Value("${xroad-catalog.fetch-run-unlimited:false}")
    private boolean fetchRunUnlimited;

    @Value("${xroad-catalog.fetch-time-after-hour:3}")
    private int fetchTimeAfterHour;

    @Value("${xroad-catalog.fetch-time-before-hour:4}")
    private int fetchTimeBeforeHour;

    // Collector internal pool parameters

    @Value("${xroad-catalog.collector-interval-min:20}")
    private long collectorInterval;

    @Value("${xroad-catalog.list-methods-pool-size:50}")
    private int listMethodsPoolSize;

    @Value("${xroad-catalog.fetch-wsdl-pool-size:10}")
    private int fetchWsdlPoolSize;

    @Value("${xroad-catalog.fetch-openapi-pool-size:10}")
    private int fetchOpenapiPoolSize;

    @Value("${xroad-catalog.fetch-rest-pool-size:10}")
    private int fetchRestPoolSize;

}
