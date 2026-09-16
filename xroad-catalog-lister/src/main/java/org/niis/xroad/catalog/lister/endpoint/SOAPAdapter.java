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
import jakarta.xml.soap.SOAPBody;
import jakarta.xml.soap.SOAPException;
import jakarta.xml.soap.SOAPHeader;
import jakarta.xml.soap.SOAPMessage;
import lombok.extern.slf4j.Slf4j;
import org.niis.xrd4j.common.exception.XRd4JException;
import org.niis.xrd4j.common.member.ConsumerMember;
import org.niis.xrd4j.common.member.ProducerMember;
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
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMResult;
import javax.xml.transform.dom.DOMSource;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.format.DateTimeParseException;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * @deprecated Superseded by the V2 REST API ({@code org.niis.xroad.catalog.lister.v2}); scheduled for removal.
 */
@Deprecated(forRemoval = true)
@Slf4j
public class SOAPAdapter extends AbstractAdapterServlet {

    private static final Pattern FAULT_ELEMENT = Pattern.compile("<(?:[\\w.-]+:)?Fault[\\s/>]");
    private static final String SOAP_CONTENT_TYPE = "text/xml; charset=UTF-8";
    private static final String INTERNAL_SERVER_ERROR_FAULT_STRING = "Internal server error";

    /**
     * Stands in for the X-Road identifiers XRD4J validates as non-empty when a header-less request is
     * dispatched from the SOAP body. It never reaches the client: the response header only carries the
     * request's own header elements and the response element name comes from the service code alone.
     */
    private static final String SYNTHETIC_IDENTIFIER = "xroad-catalog";

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
        if (request.getProducer() == null) {
            dispatchOnPayloadRoot(request);
        }
        ListerService service = switch (request.getProducer().getServiceCode()) {
            case "ListMembers" -> listMembersService;
            case "GetErrors" -> getErrorsService;
            case "GetOpenAPI" -> getOpenAPIService;
            case "GetServiceType" -> getServiceTypeService;
            case "GetWsdl" -> getWsdlService;
            case "IsProvider" -> isProviderService;
            default -> {
                request.setErrorMessage(new ErrorMessage("SOAP-ENV:Server",
                        "Unknown service: " + request.getProducer().getServiceCode(), null, null));
                throw new XRd4JException("Unknown service: " + request.getProducer().getServiceCode());
            }
        };
        rejectPayloadRootMismatch(request);
        ServiceResponse response;
        try {
            response = service.execute(request);
        } catch (DateTimeParseException e) {
            request.setErrorMessage(new ErrorMessage(ListerService.FAULT_CODE_CLIENT, e.getMessage(), null, null));
            throw new XRd4JException(e.getMessage());
        }
        if (response != null && response.getSoapMessage() != null) {
            response.setSoapMessage(rebuildMessage(response.getSoapMessage()));
        }
        return response;
    }

    /**
     * XRD4J builds the response envelope as a clone of the request envelope, so its prefix, namespace
     * declarations and whitespace would follow the caller; Spring-WS started from a fresh message and
     * copied each header element separately, which is the byte shape 3.x clients received.
     */
    static SOAPMessage rebuildMessage(SOAPMessage source) throws SOAPException {
        SOAPMessage target = SOAPHelper.createSOAPMessage();
        SOAPHeader sourceHeader = source.getSOAPHeader();
        if (sourceHeader != null) {
            Transformer transformer = newIdentityTransformer();
            SOAPHeader targetHeader = target.getSOAPHeader();
            NodeList headerChildren = sourceHeader.getChildNodes();
            for (int i = 0; i < headerChildren.getLength(); i++) {
                Node child = headerChildren.item(i);
                if (child.getNodeType() != Node.ELEMENT_NODE) {
                    continue;
                }
                try {
                    transformer.transform(new DOMSource(child), new DOMResult(targetHeader));
                } catch (TransformerException e) {
                    throw new SOAPException("Unable to copy the SOAP header element " + child.getNodeName(), e);
                }
            }
        }
        SOAPBody targetBody = target.getSOAPBody();
        NodeList bodyChildren = source.getSOAPBody().getChildNodes();
        for (int i = 0; i < bodyChildren.getLength(); i++) {
            Node child = bodyChildren.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                targetBody.appendChild(target.getSOAPPart().importNode(child, true));
            }
        }
        return target;
    }

    private static Transformer newIdentityTransformer() throws SOAPException {
        try {
            return TransformerFactory.newInstance().newTransformer();
        } catch (TransformerException e) {
            throw new SOAPException("Unable to create an identity transformer", e);
        }
    }

    /**
     * Restores the dispatch the original implementation used for callers that send no X-Road header:
     * XRD4J deserialises an empty {@code <Header/>} into a request without producer, consumer or id, so
     * the operation is taken from the SOAP body's payload root and the identifiers are synthesised here.
     */
    private static void dispatchOnPayloadRoot(ServiceRequest<?> request) throws SOAPException, XRd4JException {
        String operation = payloadRootName(request);
        if (operation == null) {
            request.setErrorMessage(new ErrorMessage("SOAP-ENV:Client",
                    "Cannot determine the requested operation: the request has no X-Road service header and no "
                            + "SOAP body element", null, null));
            throw new XRd4JException("No service header and no body element");
        }
        request.setProducer(new ProducerMember(SYNTHETIC_IDENTIFIER, operation));
        request.setConsumer(new ConsumerMember(SYNTHETIC_IDENTIFIER, SYNTHETIC_IDENTIFIER, SYNTHETIC_IDENTIFIER));
        request.setId(UUID.randomUUID().toString());
    }

    /**
     * Reports the mismatch that XRD4J would otherwise surface as {@code Request body is missing.}: the
     * operation is taken from the X-Road header, so a body element naming another operation cannot be
     * deserialised. An empty body is left to XRD4J.
     */
    private static void rejectPayloadRootMismatch(ServiceRequest<?> request) throws SOAPException, XRd4JException {
        String serviceCode = request.getProducer().getServiceCode();
        String operation = payloadRootName(request);
        if (operation == null || operation.equals(serviceCode)) {
            return;
        }
        String message = "The X-Road header names service " + serviceCode + " but the SOAP body element is " + operation;
        request.setErrorMessage(new ErrorMessage("SOAP-ENV:Client", message, null, null));
        throw new XRd4JException(message);
    }

    private static String payloadRootName(ServiceRequest<?> request) throws SOAPException {
        NodeList children = request.getSoapMessage().getSOAPBody().getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                return child.getLocalName() != null ? child.getLocalName() : child.getNodeName();
            }
        }
        return null;
    }

    @Override
    protected String getWSDLPath() {
        return "services.wsdl";
    }

    /**
     * Restores the HTTP status Spring-WS used for SOAP faults. XRD4J writes every response, faults
     * included, with HTTP 200 and keeps its response writing private, so the body is buffered here
     * and the status is set to 500 when the envelope carries a fault, before the body is written out.
     * <p>
     * XRD4J turns only its own exceptions into faults; anything else thrown by a service (a database
     * failure, for instance) would otherwise escape to the container and be answered with Spring
     * Boot's JSON error page. Spring-WS answered those with a Server fault, so that is done here too.
     */
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) {
        BufferedResponse buffered = new BufferedResponse(response);
        String body;
        try {
            super.doPost(request, buffered);
            body = buffered.getBody();
        } catch (ServletException | IOException | RuntimeException e) {
            log.error("Unable to process the SOAP request", e);
            response.setContentType(SOAP_CONTENT_TYPE);
            body = internalServerErrorFault();
        }
        if (isSoapFault(body)) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
        try (PrintWriter writer = response.getWriter()) {
            writer.write(body);
        } catch (IOException e) {
            log.warn("Unable to write the SOAP response", e);
        }
    }

    private String internalServerErrorFault() {
        SOAPMessage fault = errorToSOAP(new ErrorMessage("SOAP-ENV:Server", INTERNAL_SERVER_ERROR_FAULT_STRING, null, null), null);
        return fault == null ? "" : SOAPHelper.toString(fault);
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
