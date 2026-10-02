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

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.niis.xrd4j.common.member.ObjectType;
import org.niis.xroad.catalog.collector.exception.CatalogCollectorRuntimeException;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public final class ClientListUtil {

    private static final int BODY_EXCERPT_LENGTH = 200;
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private ClientListUtil() {
        // Private empty constructor
    }

    public static List<MemberWithName> clientListFromResponse(String url, RestTemplate restTemplate) {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        HttpEntity<Void> requestEntity = new HttpEntity<>(headers);
        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, requestEntity,
                String.class);
        JSONArray members = parseBody(response).getJSONArray("member");
        List<MemberWithName> clientList = new ArrayList<>();
        for (int i = 0; i < members.length(); i++) {
            JSONObject member = members.getJSONObject(i);
            clientList.add(constructMemberWithName(member));
        }
        return clientList;
    }

    /**
     * A Security Server answers listClients with 2xx and a non-JSON body when, for example, its global
     * configuration has expired; a 4xx/5xx already surfaces as Spring's {@code HttpStatusCodeException}
     * with status and body. The message therefore names what came back, not only where the JSON
     * tokenizer stopped, so an administrator can tell an expired global configuration, a proxy error page
     * and a wrong port apart.
     */
    private static JSONObject parseBody(ResponseEntity<String> response) {
        String body = response.getBody() == null ? "" : response.getBody();
        try {
            return new JSONObject(body);
        } catch (JSONException e) {
            MediaType contentType = response.getHeaders().getContentType();
            throw new CatalogCollectorRuntimeException("listClients answered HTTP " + response.getStatusCode()
                    + " with a body that is not JSON (" + (contentType == null ? "no Content-Type" : "Content-Type: " + contentType)
                    + "): " + excerpt(body), e);
        }
    }

    private static String excerpt(String body) {
        String collapsed = WHITESPACE.matcher(body).replaceAll(" ").trim();
        if (collapsed.isEmpty()) {
            return "(empty body)";
        }
        if (collapsed.length() <= BODY_EXCERPT_LENGTH) {
            return collapsed;
        }
        return collapsed.substring(0, BODY_EXCERPT_LENGTH) + "...";
    }

    private static MemberWithName constructMemberWithName(final JSONObject member) {
        JSONObject jsonId = member.getJSONObject("id");
        XRoadIdentifier id = XRoadIdentifier.builder()
                .xRoadInstance(jsonId.getString("xroad_instance"))
                .memberClass(jsonId.getString("member_class"))
                .memberCode(jsonId.getString("member_code"))
                .subsystemCode(jsonId.optString("subsystem_code", null))
                .objectType(jsonId.getEnum(ObjectType.class, "object_type"))
                .build();
        return new MemberWithName(member.getString("name"), id);
    }

}
