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
package org.niis.xroad.catalog.collector.tasks;

import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.catalog.collector.configuration.TaskPoolConfiguration;
import org.niis.xroad.catalog.collector.service.CatalogService;
import org.niis.xroad.catalog.collector.util.ClientTypeUtil;
import org.niis.xroad.catalog.collector.util.XRoadClient;
import org.niis.xroad.catalog.collector.wsimport.XRoadServiceIdentifierType;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.BlockingQueue;

@Slf4j
@Component
public class FetchWsdlsTask extends BaseFetchTask<XRoadServiceIdentifierType> {

    private CatalogService catalogService;

    private final XRoadClient xroadClient;

    public FetchWsdlsTask(final CatalogService catalogService, final TaskPoolConfiguration taskPoolConfiguration,
            final BlockingQueue<XRoadServiceIdentifierType> wsdlServicesQueue) throws URISyntaxException {
        super(wsdlServicesQueue, taskPoolConfiguration.getFetchWsdlPoolSize());
        this.catalogService = catalogService;

        String xroadInstance = taskPoolConfiguration.getXroadInstance();
        String memberCode = taskPoolConfiguration.getMemberCode();
        String memberClass = taskPoolConfiguration.getMemberClass();
        String subsystemCode = taskPoolConfiguration.getSubsystemCode();
        String webservicesEndpoint = taskPoolConfiguration.getWebservicesEndpoint();

        this.xroadClient = new XRoadClient(
                ClientTypeUtil.toSubsystem(xroadInstance, memberClass, memberCode, subsystemCode),
                new URI(webservicesEndpoint));
    }

    @Override
    protected void fetch(XRoadServiceIdentifierType service) {
        try {
            log.info("Fetching WSDL for {}", ClientTypeUtil.toString(service));
            String wsdl = xroadClient.getWsdl(service, catalogService);
            catalogService.saveWsdl(createSubsystemId(service), createServiceId(service), wsdl);
            log.info("WSDL for {} saved successfully", ClientTypeUtil.toString(service));
        } catch (Exception e) {
            log.error("Failed to fetch WSDL for {}", ClientTypeUtil.toString(service), e);
        }
    }
}
