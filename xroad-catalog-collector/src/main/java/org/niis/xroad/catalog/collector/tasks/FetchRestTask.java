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

import lombok.extern.slf4j.Slf4j;
import org.json.JSONArray;
import org.json.JSONObject;
import org.niis.xroad.catalog.collector.configuration.TaskPoolConfiguration;
import org.niis.xroad.catalog.collector.service.CatalogService;
import org.niis.xroad.catalog.collector.util.IdentifierUtil;
import org.niis.xroad.catalog.collector.util.Endpoint;
import org.niis.xroad.catalog.collector.util.MethodListUtil;
import org.niis.xroad.catalog.collector.util.XRoadIdentifier;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.BlockingQueue;

@Slf4j
@Component
public class FetchRestTask extends BaseFetchTask<XRoadIdentifier> {

    private static final String METHOD = "method";

    private static final String PATH = "path";

    private final CatalogService catalogService;

    public FetchRestTask(final CatalogService catalogService, final TaskPoolConfiguration taskPoolConfiguration,
            final BlockingQueue<XRoadIdentifier> restServicesQueue) {
        super(restServicesQueue, taskPoolConfiguration.getFetchRestPoolSize());
        this.catalogService = catalogService;
    }

    @Override
    protected void fetch(final XRoadIdentifier service) {
        try {
            log.info("Fetching REST for {}", IdentifierUtil.toString(service));
            List<Endpoint> endpointList = MethodListUtil.getEndpointList(service);
            String endpointData = "{\"endpoint_data\":";
            JSONArray endPointsJSONArray = new JSONArray();
            JSONObject endpointJson;
            catalogService.prepareEndpoints(createSubsystemId(service), createServiceId(service));
            for (Endpoint endpoint : endpointList) {
                endpointJson = new JSONObject();
                endpointJson.put(METHOD, endpoint.getMethod());
                endpointJson.put(PATH, endpoint.getPath());
                endPointsJSONArray.put(endpointJson);
                catalogService.saveEndpoint(createSubsystemId(service), createServiceId(service), endpoint.getMethod(),
                        endpoint.getPath());
            }
            endpointData += endPointsJSONArray + "}";
            catalogService.saveRest(createSubsystemId(service), createServiceId(service), endpointData);
            log.info("Saved REST for {} successfully", IdentifierUtil.toString(service));
        } catch (Exception e) {
            log.error("Failed to fetch REST for {}", IdentifierUtil.toString(service), e);
        }
    }
}
