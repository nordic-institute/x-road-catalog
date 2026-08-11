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
import org.json.JSONObject;
import org.niis.xrd4j.common.member.ObjectType;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;

public final class ClientListUtil {

    private ClientListUtil() {
        // Private empty constructor
    }

    public static List<MemberWithName> clientListFromResponse(String url, RestTemplate restTemplate) {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        HttpEntity<Void> requestEntity = new HttpEntity<>(headers);
        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, requestEntity,
                String.class);
        JSONObject bodyJson = new JSONObject(response.getBody());
        JSONArray members = bodyJson.getJSONArray("member");
        List<MemberWithName> clientList = new ArrayList<>();
        for (int i = 0; i < members.length(); i++) {
            JSONObject member = members.getJSONObject(i);
            clientList.add(constructMemberWithName(member));
        }
        return clientList;
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
