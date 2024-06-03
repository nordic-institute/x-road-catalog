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

import java.util.List;
import java.util.concurrent.BlockingQueue;

import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.context.ApplicationContext;

import fi.vrk.xroad.catalog.collector.configuration.TaskPoolConfiguration;
import fi.vrk.xroad.catalog.collector.util.ClientTypeUtil;
import fi.vrk.xroad.catalog.collector.util.Endpoint;
import fi.vrk.xroad.catalog.collector.util.MethodListUtil;
import fi.vrk.xroad.catalog.collector.util.XRoadRestServiceIdentifierType;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class FetchRestTask extends BaseFetchTask<XRoadRestServiceIdentifierType> {

    private static final String METHOD = "method";

    private static final String PATH = "path";

    public FetchRestTask(final ApplicationContext applicationContext,
            final BlockingQueue<XRoadRestServiceIdentifierType> restServices) {
        super(applicationContext, restServices,
                applicationContext.getBean(TaskPoolConfiguration.class).getFetchRestPoolSize());
    }

    @Override
    protected void fetch(final XRoadRestServiceIdentifierType service) {
        try {
            log.info("Fetching REST for {}", ClientTypeUtil.toString(service));
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
            log.info("Saved REST for {} successfully", ClientTypeUtil.toString(service));
        } catch (Exception e) {
            log.error("Failed to fetch REST for {}", ClientTypeUtil.toString(service), e);
        }
    }
}
