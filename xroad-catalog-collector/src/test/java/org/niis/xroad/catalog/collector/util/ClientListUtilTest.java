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

import org.json.JSONException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xrd4j.common.member.ObjectType;
import org.niis.xroad.catalog.collector.exception.CatalogCollectorRuntimeException;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClientListUtilTest {

    private static final String URL = "http://ss0:8080/listClients";
    private static final int EXCERPT_LENGTH = 200;

    @Mock
    private RestTemplate restTemplate;

    private void securityServerAnswers(ResponseEntity<String> response) {
        when(restTemplate.exchange(eq(URL), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class))).thenReturn(response);
    }

    private CatalogCollectorRuntimeException fetchFails() {
        return assertThrows(CatalogCollectorRuntimeException.class, () -> ClientListUtil.clientListFromResponse(URL, restTemplate));
    }

    @Test
    void jsonBodyIsParsedIntoTheClientList() {
        securityServerAnswers(ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body("{\"member\":[{\"id\":{"
                + "\"xroad_instance\":\"DEV\",\"member_class\":\"COM\",\"member_code\":\"1234\",\"subsystem_code\":\"Test\","
                + "\"object_type\":\"SUBSYSTEM\"},\"name\":\"Test member\"}]}"));

        List<MemberWithName> clients = ClientListUtil.clientListFromResponse(URL, restTemplate);

        assertEquals(1, clients.size());
        assertEquals("Test member", clients.get(0).getName());
        assertEquals(ObjectType.SUBSYSTEM, clients.get(0).getId().getObjectType());
        assertEquals("Test", clients.get(0).getId().getSubsystemCode());
    }

    /**
     * A Security Server with an expired global configuration answers listClients with 2xx and an error
     * document; the message must carry the status, the content type and the body, whitespace collapsed.
     */
    @Test
    void nonJsonBodyWithA2xxStatusIsReportedWithStatusContentTypeAndBodyExcerpt() {
        securityServerAnswers(ResponseEntity.ok().contentType(MediaType.TEXT_HTML)
                .body("<html>\n  <body>\n    global_conf_outdated\n  </body>\n</html>\n"));

        CatalogCollectorRuntimeException e = fetchFails();

        assertEquals("listClients answered HTTP 200 OK with a body that is not JSON (Content-Type: text/html): "
                + "<html> <body> global_conf_outdated </body> </html>", e.getMessage());
        assertInstanceOf(JSONException.class, e.getCause());
    }

    @Test
    void bodyExcerptIsBoundedAndAMissingContentTypeIsNamed() {
        securityServerAnswers(ResponseEntity.ok().body("x".repeat(EXCERPT_LENGTH * 2)));

        CatalogCollectorRuntimeException e = fetchFails();

        assertTrue(e.getMessage().contains("(no Content-Type): "), e.getMessage());
        assertTrue(e.getMessage().endsWith("x".repeat(EXCERPT_LENGTH) + "..."), e.getMessage());
        assertFalse(e.getMessage().contains("x".repeat(EXCERPT_LENGTH + 1)), e.getMessage());
    }

    @Test
    void emptyBodyIsNamedInsteadOfAnEmptyExcerpt() {
        securityServerAnswers(ResponseEntity.noContent().build());

        CatalogCollectorRuntimeException e = fetchFails();

        assertEquals("listClients answered HTTP 204 NO_CONTENT with a body that is not JSON (no Content-Type): (empty body)",
                e.getMessage());
    }
}
