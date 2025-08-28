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
package org.niis.xroad.catalog.collector.tasks;

import jakarta.xml.soap.SOAPException;
import lombok.extern.slf4j.Slf4j;
import org.niis.xrd4j.common.exception.XRd4JException;
import org.niis.xrd4j.common.member.ConsumerMember;
import org.niis.xroad.catalog.collector.configuration.TaskPoolConfiguration;
import org.niis.xroad.catalog.collector.service.CatalogService;
import org.niis.xroad.catalog.collector.util.ClientTypeUtil;
import org.niis.xroad.catalog.collector.util.Endpoint;
import org.niis.xroad.catalog.collector.util.MethodListUtil;
import org.niis.xroad.catalog.collector.util.XRoadClient;
import org.niis.xroad.catalog.collector.util.XRoadIdentifier;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.BlockingQueue;

@Slf4j
@Component
public class FetchOpenApiTask extends BaseFetchTask<XRoadIdentifier> {

    private final String xroadSecurityServerHost;

    private final ConsumerMember consumerMember;

    private final CatalogService catalogService;

    private final XRoadClient xroadClient;

    public FetchOpenApiTask(final CatalogService catalogService, final TaskPoolConfiguration taskPoolConfiguration,
                            final BlockingQueue<XRoadIdentifier> openApiServicesQueue) throws XRd4JException, SOAPException {
        super(openApiServicesQueue, taskPoolConfiguration.getFetchOpenapiPoolSize());
        this.catalogService = catalogService;

        this.xroadSecurityServerHost = taskPoolConfiguration.getSecurityServerHost();
        this.consumerMember = new ConsumerMember(
                taskPoolConfiguration.getXroadInstance(),
                taskPoolConfiguration.getMemberClass(),
                taskPoolConfiguration.getMemberCode(),
                taskPoolConfiguration.getSubsystemCode());

        String webservicesEndpoint = taskPoolConfiguration.getWebservicesEndpoint();

        this.xroadClient = new XRoadClient(consumerMember, webservicesEndpoint);
    }

    @Override
    protected void fetch(final XRoadIdentifier service) {
        try {
            log.info("Fetching OpenApi for {}", ClientTypeUtil.toString(service));
            String openApi = xroadClient.getOpenApi(service, xroadSecurityServerHost, consumerMember, catalogService);
            catalogService.saveOpenApi(createSubsystemId(service), createServiceId(service), openApi);
            List<Endpoint> endpointList = MethodListUtil.getEndpointList(service);
            catalogService.prepareEndpoints(createSubsystemId(service), createServiceId(service));
            for (Endpoint endpoint : endpointList) {
                catalogService.saveEndpoint(createSubsystemId(service), createServiceId(service), endpoint.getMethod(),
                        endpoint.getPath());
            }
            log.info("Saved OpenApi for {} successfully", ClientTypeUtil.toString(service));
        } catch (Exception e) {
            log.error("Failed to fetch OpenAPI for {}", ClientTypeUtil.toString(service), e);
        }
    }
}
