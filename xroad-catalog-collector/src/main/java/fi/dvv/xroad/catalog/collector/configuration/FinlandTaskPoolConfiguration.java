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
package fi.dvv.xroad.catalog.collector.configuration;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Getter
@Configuration
public class FinlandTaskPoolConfiguration {

    // Parameters related to the "fi" profile

    @Value("${xroad-catalog.country.fi.fetch.companies.pool-size:10}")
    private int fetchCompaniesPoolSize;

    @Value("${xroad-catalog.country.fi.fetch.companies.url}")
    private String fetchCompaniesUrl;

    @Value("${xroad-catalog.country.fi.fetch.organizations.pool-size:10}")
    private int fetchOrganizationsPoolSize;

    @Value("${xroad-catalog.country.fi.fetch.organizations.url}")
    private String fetchOrganizationsUrl;

    @Value("${xroad-catalog.country.fi.fetch.external-limit:500}")
    private int fetchExternalLimit;

    @Value("${xroad-catalog.country.fi.fetch.external-update-after-days:7}")
    private int fetchExternalUpdateAfterDays;

    @Value("${xroad-catalog.country.fi.fetch.external-interval-min:20}")
    private long fetchExternalInterval;

    @Value("${xroad-catalog.country.fi.fetch.external-run-unlimited:false}")
    private boolean fetchExternalRunUnlimited;

    @Value("${xroad-catalog.country.fi.fetch.external-time-after-hour:3}")
    private int fetchExternalTimeAfterHour;

    @Value("${xroad-catalog.country.fi.fetch.external-time-before-hour:4}")
    private int fetchExternalTimeBeforeHour;
}
