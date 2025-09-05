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
package org.niis.xroad.catalog.collector.util;

import lombok.extern.slf4j.Slf4j;
import org.json.JSONArray;
import org.json.JSONObject;
import org.niis.xrd4j.common.exception.XRd4JException;
import org.niis.xrd4j.common.member.ConsumerMember;
import org.niis.xrd4j.common.member.ObjectType;
import org.niis.xroad.catalog.collector.service.CatalogService;
import org.niis.xroad.catalog.persistence.entity.ErrorLog;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
public final class MethodListUtil {

    private static final RestTemplate REST_TEMPLATE = new RestTemplate();

    private static SecurityServerMetadata securityServerMetadata;

    private MethodListUtil() {
        // Private empty constructor
    }

    public static List<XRoadIdentifier> methodListFromResponse(XRoadIdentifier clientType,
                                                               String host,
                                                               ConsumerMember consumerMember,
                                                               CatalogService catalogService) throws XRd4JException {
        final String url = host + "/r1/"
                + clientType.getXRoadInstance() + '/'
                + clientType.getMemberClass() + '/'
                + clientType.getMemberCode() + '/'
                + clientType.getSubsystemCode() + "/listMethods";

        String xRoadClientHeader = createHeader(consumerMember);
        List<XRoadIdentifier> restServices = new ArrayList<>();
        JSONObject json = MethodListUtil.getJSON(url, clientType, xRoadClientHeader, catalogService);
        if (json != null) {
            JSONArray serviceList = json.getJSONArray("service");
            for (int i = 0; i < serviceList.length(); i++) {
                JSONObject service = serviceList.getJSONObject(i);
                XRoadIdentifier xRoadIdentifier = XRoadIdentifier.builder()
                        .xRoadInstance(service.optString("xroad_instance"))
                        .memberClass(service.optString("member_class"))
                        .memberCode(service.optString("member_code"))
                        .subsystemCode(service.optString("subsystem_code"))
                        .serviceCode(service.optString("service_code"))
                        .serviceVersion(service.optString("service_version", null))
                        .objectType(service.getEnum(ObjectType.class, "object_type"))
                        .serviceType(service.optString("service_type", null))
                        .build();

                JSONArray endpointList = service.optJSONArray("endpoint_list");
                List<Endpoint> endpoints = new ArrayList<>();
                for (int j = 0; j < endpointList.length(); j++) {
                    JSONObject endpoint = endpointList.getJSONObject(j);
                    endpoints.add(Endpoint.builder().method(endpoint.optString("method"))
                            .path(endpoint.optString("path")).build());
                }
                xRoadIdentifier.setEndpoints(endpoints);
                restServices.add(xRoadIdentifier);
            }
        }

        return restServices;
    }

    public static String openApiFromResponse(XRoadIdentifier clientType,
                                             String host,
                                             ConsumerMember consumerMember,
                                             CatalogService catalogService) {
        final String url = host + "/r1/"
                + clientType.getXRoadInstance() + '/'
                + clientType.getMemberClass() + '/'
                + clientType.getMemberCode() + '/'
                + clientType.getSubsystemCode() + "/getOpenAPI?serviceCode="
                + clientType.getServiceCode();

        String xRoadClientHeader = createHeader(consumerMember);
        JSONObject json = MethodListUtil.getJSON(url, clientType, xRoadClientHeader, catalogService);

        return (json != null) ? json.toString() : "";
    }

    public static List<Endpoint> getEndpointList(
            XRoadIdentifier service) {
        List<Endpoint> endpointList = new ArrayList<>();
        for (Endpoint endpoint : service.getEndpoints()) {
            endpointList.add(Endpoint.builder().method(endpoint.getMethod()).path(endpoint.getPath())
                    .build());
        }
        return endpointList;
    }

    private static String createHeader(ConsumerMember consumerMember) {
        return consumerMember.getXRoadInstance() + '/'
                + consumerMember.getMemberClass() + '/'
                + consumerMember.getMemberCode() + '/'
                + consumerMember.getSubsystemCode();
    }

    private static JSONObject getJSON(String url, XRoadIdentifier client, String xRoadClientHeader,
                                      CatalogService catalogService) {
        HttpHeaders headers = new HttpHeaders();
        List<MediaType> mediaTypes = new ArrayList<>();
        mediaTypes.add(MediaType.APPLICATION_JSON);
        headers.setAccept(mediaTypes);
        headers.set("X-Road-Client", xRoadClientHeader);
        final HttpEntity<String> entity = new HttpEntity<>(headers);
        try {
            ResponseEntity<String> response = REST_TEMPLATE.exchange(url, HttpMethod.GET, entity,
                    String.class);
            return new JSONObject(response.getBody());
        } catch (Exception e) {
            SecurityServerMetadata newSecurityServerMetadata = SecurityServerMetadata.builder()
                    .xRoadInstance(client.getXRoadInstance())
                    .memberClass(client.getMemberClass())
                    .memberCode(client.getMemberCode())
                    .build();
            if (!newSecurityServerMetadata.equals(securityServerMetadata)) {
                log.error("Fetch of REST services failed: {}", e.getMessage());
                ErrorLog errorLog = ErrorLog.builder()
                        .created(LocalDateTime.now())
                        .message("Fetch of REST services failed(url: " + url + "): "
                                + e.getMessage())
                        .code("500")
                        .xRoadInstance(client.getXRoadInstance())
                        .memberClass(client.getMemberClass())
                        .memberCode(client.getMemberCode())
                        .serviceCode((client).getServiceCode())
                        .serviceVersion((client).getServiceVersion())
                        .subsystemCode(client.getSubsystemCode())
                        .build();
                catalogService.saveErrorLog(errorLog);
                securityServerMetadata = SecurityServerMetadata.builder()
                        .xRoadInstance(client.getXRoadInstance())
                        .memberClass(client.getMemberClass())
                        .memberCode(client.getMemberCode())
                        .build();
            }
            return null;
        }
    }
}
