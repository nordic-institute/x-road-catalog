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
import jakarta.xml.soap.SOAPElement;
import jakarta.xml.soap.SOAPMessage;
import org.junit.jupiter.api.Test;
import org.niis.xrd4j.common.exception.XRd4JException;
import org.niis.xrd4j.common.member.ConsumerMember;
import org.niis.xrd4j.common.member.ProducerMember;
import org.niis.xrd4j.common.message.ServiceRequest;
import org.niis.xrd4j.common.util.SOAPHelper;
import org.niis.xroad.catalog.lister.service.CatalogService;
import org.springframework.mock.web.MockHttpServletRequest;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("removal")
class SOAPAdapterTest {

    private static final String ENVELOPE_START =
            "<SOAP-ENV:Envelope xmlns:SOAP-ENV=\"http://schemas.xmlsoap.org/soap/envelope/\"><SOAP-ENV:Body>";
    private static final String ENVELOPE_END = "</SOAP-ENV:Body></SOAP-ENV:Envelope>";
    private static final String LEGACY_NAMESPACE = "http://xroad.vrk.fi/xroad-catalog-lister";
    private static final String MISMATCH_FAULT_STRING =
            "The X-Road header names service ListMembers but the SOAP body element is IsProvider";

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
    void headerLessRequestIsDispatchedOnThePayloadRoot() {
        SOAPAdapter adapter = new SOAPAdapter(mock(CatalogService.class));
        ServiceRequest<?> request = headerLessRequest("<tns:IsProvider xmlns:tns=\"" + LEGACY_NAMESPACE + "\">"
                + "<tns:xRoadInstance>DEV</tns:xRoadInstance><tns:memberClass>ORG</tns:memberClass>"
                + "<tns:memberCode>14151328</tns:memberCode></tns:IsProvider>");

        assertThatExceptionOfType(XRd4JException.class)
                .isThrownBy(() -> adapter.handleRequest(request))
                .withMessage("Member not found");
        assertThat(request.getProducer().getServiceCode()).isEqualTo("IsProvider");
        assertThat(request.getConsumer()).isNotNull();
        assertThat(request.getId()).isNotBlank();
    }

    @Test
    void headerLessRequestWithAnUnknownPayloadRootReturnsTheUnknownServiceFault() {
        SOAPAdapter adapter = new SOAPAdapter(mock(CatalogService.class));
        ServiceRequest<?> request = headerLessRequest("<tns:Bogus xmlns:tns=\"" + LEGACY_NAMESPACE + "\"/>");

        assertThatExceptionOfType(XRd4JException.class)
                .isThrownBy(() -> adapter.handleRequest(request))
                .withMessage("Unknown service: Bogus");
        assertThat(request.getErrorMessage().getFaultCode()).isEqualTo("SOAP-ENV:Server");
        assertThat(request.getErrorMessage().getFaultString()).isEqualTo("Unknown service: Bogus");
    }

    @Test
    void headerLessRequestWithAnEmptyBodyReportsNoOperationCanBeDetermined() {
        SOAPAdapter adapter = new SOAPAdapter(mock(CatalogService.class));
        ServiceRequest<?> request = headerLessRequest("");

        assertThatExceptionOfType(XRd4JException.class)
                .isThrownBy(() -> adapter.handleRequest(request))
                .withMessage("No service header and no body element");
        assertThat(request.getErrorMessage().getFaultCode()).isEqualTo("SOAP-ENV:Client");
        assertThat(request.getErrorMessage().getFaultString())
                .isEqualTo("Cannot determine the requested operation: the request has no X-Road service header "
                        + "and no SOAP body element");
    }

    @Test
    void serviceCodeFromTheHeaderWinsOverThePayloadRoot() throws XRd4JException {
        SOAPAdapter adapter = new SOAPAdapter(mock(CatalogService.class));
        ServiceRequest<?> request = new ServiceRequest<>(new ConsumerMember("DEV", "COM", "12345"),
                new ProducerMember("DEV", "ListMembers"), "test-request-123");
        request.setSoapMessage(SOAPHelper.toSOAP(ENVELOPE_START
                + "<tns:IsProvider xmlns:tns=\"" + LEGACY_NAMESPACE + "\"/>" + ENVELOPE_END));

        assertThatExceptionOfType(XRd4JException.class)
                .isThrownBy(() -> adapter.handleRequest(request))
                .withMessage(MISMATCH_FAULT_STRING);
        assertThat(request.getProducer().getServiceCode()).isEqualTo("ListMembers");
        assertThat(request.getErrorMessage().getFaultCode()).isEqualTo("SOAP-ENV:Client");
        assertThat(request.getErrorMessage().getFaultString()).isEqualTo(MISMATCH_FAULT_STRING);
    }

    @Test
    void serviceCodeMatchingThePayloadRootIsDispatched() throws XRd4JException {
        SOAPAdapter adapter = new SOAPAdapter(mock(CatalogService.class));
        ServiceRequest<?> request = new ServiceRequest<>(new ConsumerMember("DEV", "COM", "12345"),
                new ProducerMember("DEV", "ListMembers"), "test-request-123");
        request.setSoapMessage(SOAPHelper.toSOAP(ENVELOPE_START
                + "<tns:ListMembers xmlns:tns=\"" + LEGACY_NAMESPACE + "\"/>" + ENVELOPE_END));

        assertThatExceptionOfType(XRd4JException.class)
                .isThrownBy(() -> adapter.handleRequest(request))
                .withMessage("Missing required parameters");
        assertThat(request.getErrorMessage().getFaultString())
                .isEqualTo("startDateTime and endDateTime parameters are missing");
    }

    @Test
    void unparseableDateTimeParameterIsReportedAsAClientFault() throws XRd4JException {
        SOAPAdapter adapter = new SOAPAdapter(mock(CatalogService.class));
        ServiceRequest<?> request = new ServiceRequest<>(new ConsumerMember("DEV", "COM", "12345"),
                new ProducerMember("DEV", "ListMembers"), "test-request-123");
        request.setSoapMessage(SOAPHelper.toSOAP(ENVELOPE_START
                + "<tns:ListMembers xmlns:tns=\"" + LEGACY_NAMESPACE + "\">"
                + "<tns:startDateTime>2020-01-01T01:01:00</tns:startDateTime>"
                + "<tns:endDateTime>not-a-date</tns:endDateTime></tns:ListMembers>" + ENVELOPE_END));

        assertThatExceptionOfType(XRd4JException.class)
                .isThrownBy(() -> adapter.handleRequest(request))
                .withMessage("Invalid endDateTime: not-a-date");
        assertThat(request.getErrorMessage().getFaultCode()).isEqualTo("SOAP-ENV:Client");
        assertThat(request.getErrorMessage().getFaultString()).isEqualTo("Invalid endDateTime: not-a-date");
    }

    @Test
    void errorToSoapReturnsNullWhenSerializationFails() {
        SOAPAdapter adapter = new SOAPAdapter(mock(CatalogService.class));

        assertThat(adapter.errorToSOAP(null, null)).isNull();
    }

    @Test
    void rebuiltResponseHasAFreshEnvelopeAndSelfContainedHeaderElements() throws Exception {
        SOAPMessage source = SOAPHelper.toSOAP("<soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\" "
                + "xmlns:xro=\"http://x-road.eu/xsd/xroad.xsd\">\n   <soapenv:Header>\n      <xro:id>ID1</xro:id>\n"
                + "      <xro:userId>EE1</xro:userId>\n   </soapenv:Header>\n   <soapenv:Body><ns2:R xmlns:ns2=\"urn:t\">"
                + "<ns2:v>1</ns2:v></ns2:R></soapenv:Body>\n</soapenv:Envelope>");

        assertThat(SOAPHelper.toString(SOAPAdapter.rebuildMessage(source))).isEqualTo(
                "<SOAP-ENV:Envelope xmlns:SOAP-ENV=\"http://schemas.xmlsoap.org/soap/envelope/\"><SOAP-ENV:Header>"
                        + "<xro:id xmlns:xro=\"http://x-road.eu/xsd/xroad.xsd\">ID1</xro:id>"
                        + "<xro:userId xmlns:xro=\"http://x-road.eu/xsd/xroad.xsd\">EE1</xro:userId></SOAP-ENV:Header>"
                        + "<SOAP-ENV:Body><ns2:R xmlns:ns2=\"urn:t\"><ns2:v>1</ns2:v></ns2:R></SOAP-ENV:Body></SOAP-ENV:Envelope>");
    }

    @Test
    void rebuiltResponseOfAHeaderLessMessageHasAnEmptyHeaderAndKeepsCdata() throws Exception {
        SOAPMessage source = SOAPHelper.createSOAPMessage();
        source.getSOAPHeader().detachNode();
        SOAPElement wsdl = source.getSOAPBody().addChildElement("wsdl", "ns2", LEGACY_NAMESPACE);
        wsdl.appendChild(wsdl.getOwnerDocument().createCDATASection("<definitions>&amp;</definitions>"));

        assertThat(SOAPHelper.toString(SOAPAdapter.rebuildMessage(source))).isEqualTo(
                "<SOAP-ENV:Envelope xmlns:SOAP-ENV=\"http://schemas.xmlsoap.org/soap/envelope/\"><SOAP-ENV:Header/>"
                        + "<SOAP-ENV:Body><ns2:wsdl xmlns:ns2=\"" + LEGACY_NAMESPACE + "\">"
                        + "<![CDATA[<definitions>&amp;</definitions>]]></ns2:wsdl></SOAP-ENV:Body></SOAP-ENV:Envelope>");
    }

    private static ServiceRequest<?> headerLessRequest(String payload) {
        SOAPMessage message = SOAPHelper.toSOAP(ENVELOPE_START + payload + ENVELOPE_END);
        ServiceRequest<?> request = new ServiceRequest<>();
        request.setSoapMessage(message);
        return request;
    }
}
