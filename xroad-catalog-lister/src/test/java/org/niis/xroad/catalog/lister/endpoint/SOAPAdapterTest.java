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
package org.niis.xroad.catalog.lister.endpoint;

import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.niis.xroad.catalog.lister.service.CatalogService;
import org.springframework.mock.web.MockHttpServletRequest;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("removal")
class SOAPAdapterTest {

    private static final String ENVELOPE_START =
            "<SOAP-ENV:Envelope xmlns:SOAP-ENV=\"http://schemas.xmlsoap.org/soap/envelope/\"><SOAP-ENV:Body>";
    private static final String ENVELOPE_END = "</SOAP-ENV:Body></SOAP-ENV:Envelope>";

    @Test
    void successEnvelopeIsNotAFault() {
        String body = ENVELOPE_START + "<ns:ListMembersResponse xmlns:ns=\"urn:test\"/>" + ENVELOPE_END;

        assertThat(SOAPAdapter.isSoapFault(body)).isFalse();
    }

    @Test
    void faultEnvelopeIsAFault() {
        String body = ENVELOPE_START
                + "<SOAP-ENV:Fault><faultcode>SOAP-ENV:Server</faultcode><faultstring>boom</faultstring></SOAP-ENV:Fault>"
                + ENVELOPE_END;

        assertThat(SOAPAdapter.isSoapFault(body)).isTrue();
    }

    @Test
    void faultElementInsideCdataIsNotAFault() {
        String body = ENVELOPE_START
                + "<ns:GetWsdlResponse xmlns:ns=\"urn:test\"><ns:wsdl><![CDATA[<Fault>boom</Fault>]]></ns:wsdl></ns:GetWsdlResponse>"
                + ENVELOPE_END;

        assertThat(SOAPAdapter.isSoapFault(body)).isFalse();
    }

    @Test
    void malformedBodyWithFaultElementIsNotAFault() {
        String body = ENVELOPE_START + "<Fault ";

        assertThat(SOAPAdapter.isSoapFault(body)).isFalse();
    }

    @Test
    void doPostSurvivesWriterFailure() throws IOException {
        SOAPAdapter adapter = new SOAPAdapter(mock(CatalogService.class));
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/ws");
        request.setContentType("text/xml");
        request.setContent("not a soap message".getBytes(StandardCharsets.UTF_8));
        HttpServletResponse response = mock(HttpServletResponse.class);
        when(response.getWriter()).thenThrow(new IOException("broken pipe"));

        assertThatCode(() -> adapter.doPost(request, response)).doesNotThrowAnyException();
        verify(response).setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        verify(response).getWriter();
    }

    @Test
    void errorToSoapReturnsNullWhenSerializationFails() {
        SOAPAdapter adapter = new SOAPAdapter(mock(CatalogService.class));

        assertThat(adapter.errorToSOAP(null, null)).isNull();
    }
}
