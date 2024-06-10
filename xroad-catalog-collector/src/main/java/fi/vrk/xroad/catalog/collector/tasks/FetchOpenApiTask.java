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
package fi.vrk.xroad.catalog.collector.tasks;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.concurrent.BlockingQueue;

import org.springframework.context.ApplicationContext;

import fi.vrk.xroad.catalog.collector.configuration.TaskPoolConfiguration;
import fi.vrk.xroad.catalog.collector.util.ClientTypeUtil;
import fi.vrk.xroad.catalog.collector.util.Endpoint;
import fi.vrk.xroad.catalog.collector.util.MethodListUtil;
import fi.vrk.xroad.catalog.collector.util.XRoadClient;
import fi.vrk.xroad.catalog.collector.util.XRoadRestServiceIdentifierType;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class FetchOpenApiTask extends BaseFetchTask<XRoadRestServiceIdentifierType> {

    private final String xroadSecurityServerHost;

    private final String xroadInstance;

    private final String memberCode;

    private final String memberClass;

    private final String subsystemCode;

    private final String webservicesEndpoint;

    private final XRoadClient xroadClient;

    public FetchOpenApiTask(final ApplicationContext applicationContext,
            final BlockingQueue<XRoadRestServiceIdentifierType> openApiServices) throws URISyntaxException {
        super(applicationContext, openApiServices,
                applicationContext.getBean(TaskPoolConfiguration.class).getFetchOpenapiPoolSize());

        TaskPoolConfiguration taskPoolConfiguration = applicationContext.getBean(TaskPoolConfiguration.class);
        this.xroadSecurityServerHost = taskPoolConfiguration.getSecurityServerHost();
        this.xroadInstance = taskPoolConfiguration.getXroadInstance();
        this.memberCode = taskPoolConfiguration.getMemberCode();
        this.memberClass = taskPoolConfiguration.getMemberClass();
        this.subsystemCode = taskPoolConfiguration.getSubsystemCode();
        this.webservicesEndpoint = taskPoolConfiguration.getWebservicesEndpoint();

        this.xroadClient = new XRoadClient(
                ClientTypeUtil.toSubsystem(xroadInstance, memberClass, memberCode, subsystemCode),
                new URI(webservicesEndpoint));
    }

    @Override
    protected void fetch(final XRoadRestServiceIdentifierType service) {
        try {
            log.info("Fetching OpenApi for {}", ClientTypeUtil.toString(service));
            String openApi = xroadClient.getOpenApi(service, xroadSecurityServerHost, xroadInstance, memberClass,
                    memberCode, subsystemCode, catalogService);
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
