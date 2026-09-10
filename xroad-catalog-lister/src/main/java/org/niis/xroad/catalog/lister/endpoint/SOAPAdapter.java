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

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import jakarta.xml.soap.SOAPException;
import jakarta.xml.soap.SOAPMessage;
import lombok.extern.slf4j.Slf4j;
import org.niis.xrd4j.common.exception.XRd4JException;
import org.niis.xrd4j.common.message.ErrorMessage;
import org.niis.xrd4j.common.message.ServiceRequest;
import org.niis.xrd4j.common.message.ServiceResponse;
import org.niis.xrd4j.common.util.SOAPHelper;
import org.niis.xrd4j.server.AbstractAdapterServlet;
import org.niis.xroad.catalog.lister.endpoint.services.geterrors.GetErrorsService;
import org.niis.xroad.catalog.lister.endpoint.services.getopenapi.GetOpenAPIService;
import org.niis.xroad.catalog.lister.endpoint.services.getservicetype.GetServiceTypeService;
import org.niis.xroad.catalog.lister.endpoint.services.getwsdl.GetWsdlService;
import org.niis.xroad.catalog.lister.endpoint.services.isprovider.IsProviderService;
import org.niis.xroad.catalog.lister.endpoint.services.listmembers.ListMembersService;
import org.niis.xroad.catalog.lister.service.CatalogService;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.regex.Pattern;

/**
 * @deprecated Superseded by the V2 REST API ({@code org.niis.xroad.catalog.lister.v2}); scheduled for removal.
 */
@Deprecated(forRemoval = true)
@Slf4j
public class SOAPAdapter extends AbstractAdapterServlet {

    private static final Pattern FAULT_ELEMENT = Pattern.compile("<(?:[\\w.-]+:)?Fault[\\s/>]");

    private final transient ListMembersService listMembersService;
    private final transient GetErrorsService getErrorsService;
    private final transient GetOpenAPIService getOpenAPIService;
    private final transient GetServiceTypeService getServiceTypeService;
    private final transient GetWsdlService getWsdlService;
    private final transient IsProviderService isProviderService;

    
    public SOAPAdapter(CatalogService catalogService) {
        super();
        this.listMembersService = new ListMembersService(catalogService);
        this.getErrorsService = new GetErrorsService(catalogService);
        this.getOpenAPIService = new GetOpenAPIService(catalogService);
        this.getServiceTypeService = new GetServiceTypeService(catalogService);
        this.getWsdlService = new GetWsdlService(catalogService);
        this.isProviderService = new IsProviderService(catalogService);
    }

    @Override
    @SuppressWarnings("unchecked")
    protected ServiceResponse handleRequest(ServiceRequest request) throws SOAPException, XRd4JException {
        return switch (request.getProducer().getServiceCode()) {
            case "ListMembers" -> listMembersService.execute(request);
            case "GetErrors" -> getErrorsService.execute(request);
            case "GetOpenAPI" -> getOpenAPIService.execute(request);
            case "GetServiceType" -> getServiceTypeService.execute(request);
            case "GetWsdl" -> getWsdlService.execute(request);
            case "IsProvider" -> isProviderService.execute(request);
            default -> {
                request.setErrorMessage(new ErrorMessage("SOAP-ENV:Server",
                        "Unknown service: " + request.getProducer().getServiceCode(), null, null));
                throw new XRd4JException("Unknown service: " + request.getProducer().getServiceCode());
            }
        };
    }

    @Override
    protected String getWSDLPath() {
        return "services.wsdl";
    }

    /**
     * Restores the HTTP status Spring-WS used for SOAP faults. XRD4J writes every response, faults
     * included, with HTTP 200 and keeps its response writing private, so the body is buffered here
     * and the status is set to 500 when the envelope carries a fault, before the body is written out.
     */
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) {
        BufferedResponse buffered = new BufferedResponse(response);
        try {
            super.doPost(request, buffered);
        } catch (ServletException | IOException e) {
            log.error("Unable to process the SOAP request", e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            return;
        }
        String body = buffered.getBody();
        if (isSoapFault(body)) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
        try (PrintWriter writer = response.getWriter()) {
            writer.write(body);
        } catch (IOException e) {
            log.warn("Unable to write the SOAP response", e);
        }
    }

    /**
     * Adds the {@code xml:lang="en"} attribute Spring-WS put on {@code faultstring}, so faults keep
     * the exact shape the original implementation produced.
     */
    @Override
    protected SOAPMessage errorToSOAP(ErrorMessage errorMessage, ServiceRequest request) {
        SOAPMessage message = super.errorToSOAP(errorMessage, request);
        if (message == null) {
            return null;
        }
        try {
            NodeList faultStrings = message.getSOAPBody().getElementsByTagName("faultstring");
            for (int i = 0; i < faultStrings.getLength(); i++) {
                ((Element) faultStrings.item(i)).setAttributeNS(XMLConstants.XML_NS_URI, "xml:lang", "en");
            }
        } catch (SOAPException e) {
            log.warn("Unable to set the faultstring language", e);
        }
        return message;
    }

    static boolean isSoapFault(String body) {
        if (!FAULT_ELEMENT.matcher(body).find()) {
            return false;
        }
        try {
            SOAPMessage message = SOAPHelper.toSOAP(body);
            return message != null && message.getSOAPBody().hasFault();
        } catch (SOAPException e) {
            log.warn("Unable to inspect the SOAP response for a fault", e);
            return false;
        }
    }

    private static final class BufferedResponse extends HttpServletResponseWrapper {
        private final StringWriter buffer = new StringWriter();
        private final PrintWriter writer = new PrintWriter(buffer);

        BufferedResponse(HttpServletResponse response) {
            super(response);
        }

        @Override
        public PrintWriter getWriter() {
            return writer;
        }

        String getBody() {
            writer.flush();
            return buffer.toString();
        }
    }

    /**
     * Restores the {@code GET /ws/services.wsdl} retrieval URL that Spring-WS served originally.
     * XRD4J's {@link AbstractAdapterServlet} serves the WSDL only when a {@code wsdl} query
     * parameter is present, so the path-style request is rewritten to look like {@code GET /ws?wsdl}
     * and delegated to the parent, keeping its content-type and error handling.
     */
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        if (request.getParameter("wsdl") == null && "/services.wsdl".equals(request.getPathInfo())) {
            super.doGet(new HttpServletRequestWrapper(request) {
                @Override
                public String getParameter(String name) {
                    return "wsdl".equals(name) ? "" : super.getParameter(name);
                }
            }, response);
            return;
        }
        super.doGet(request, response);
    }
}
