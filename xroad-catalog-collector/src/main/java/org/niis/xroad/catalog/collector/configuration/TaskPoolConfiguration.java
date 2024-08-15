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
package org.niis.xroad.catalog.collector.configuration;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Getter
@Configuration
public class TaskPoolConfiguration {

    // X-Road instance parameters

    @Value("${xroad-catalog.target.xroad-instance}")
    private String xroadInstance;

    @Value("${xroad-catalog.target.member-class}")
    private String memberClass;

    @Value("${xroad-catalog.target.member-code}")
    private String memberCode;

    @Value("${xroad-catalog.target.subsystem-code}")
    private String subsystemCode;

    // Security server URLs

    @Value("${xroad-catalog.urls.security-server-host}")
    private String securityServerHost;

    @Value("${xroad-catalog.urls.webservices-endpoint}")
    private String webservicesEndpoint;

    @Value("${xroad-catalog.urls.list-clients-host}")
    private String listClientsHost;

    // Parameters handling database log storage

    @Value("${xroad-catalog.log-storage.flush-log-time-after-hour:3}")
    private int flushLogTimeAfterHour;

    @Value("${xroad-catalog.log-storage.flush-log-time-before-hour:4}")
    private int flushLogTimeBeforeHour;

    @Value("${xroad-catalog.log-storage.error-log-length-in-days:90}")
    private int errorLogLengthInDays;

    // Parameters controlling how often data is collected from the X-Road instance

    @Value("${xroad-catalog.tasks.collector-interval-min:20}")
    private long collectorInterval;

    @Value("${xroad-catalog.tasks.fetch-run-unlimited:false}")
    private boolean fetchRunUnlimited;

    @Value("${xroad-catalog.tasks.fetch-time-after-hour:3}")
    private int fetchTimeAfterHour;

    @Value("${xroad-catalog.tasks.fetch-time-before-hour:4}")
    private int fetchTimeBeforeHour;

    // Collector internal pool parameters

    @Value("${xroad-catalog.pool-size.list-methods:50}")
    private int listMethodsPoolSize;

    @Value("${xroad-catalog.pool-size.fetch-wsdl:10}")
    private int fetchWsdlPoolSize;

    @Value("${xroad-catalog.pool-size.fetch-openapi:10}")
    private int fetchOpenapiPoolSize;

    @Value("${xroad-catalog.pool-size.fetch-rest:10}")
    private int fetchRestPoolSize;

}
