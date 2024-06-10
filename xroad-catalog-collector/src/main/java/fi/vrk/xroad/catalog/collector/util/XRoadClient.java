/**
 * The MIT License
 *
 * Copyright (c) 2023- Nordic Institute for Interoperability Solutions (NIIS)
 * Copyright (c) 2016-2023 Finnish Digital Agency
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package fi.vrk.xroad.catalog.collector.util;

import fi.vrk.xroad.catalog.collector.wsimport.ClientType;
import fi.vrk.xroad.catalog.collector.wsimport.GetWsdl;
import fi.vrk.xroad.catalog.collector.wsimport.GetWsdlResponse;
import fi.vrk.xroad.catalog.collector.wsimport.ListMethods;
import fi.vrk.xroad.catalog.collector.wsimport.ListMethodsResponse;
import fi.vrk.xroad.catalog.collector.wsimport.MetaServicesPort;
import fi.vrk.xroad.catalog.collector.wsimport.ProducerPortService;
import fi.vrk.xroad.catalog.collector.wsimport.XRoadClientIdentifierType;
import fi.vrk.xroad.catalog.collector.wsimport.XRoadIdentifierType;
import fi.vrk.xroad.catalog.collector.wsimport.XRoadObjectType;
import fi.vrk.xroad.catalog.collector.wsimport.XRoadServiceIdentifierType;
import fi.vrk.xroad.catalog.persistence.CatalogService;
import fi.vrk.xroad.catalog.persistence.entity.ErrorLog;
import jakarta.activation.DataHandler;
import jakarta.xml.ws.BindingProvider;
import jakarta.xml.ws.Holder;
import lombok.extern.slf4j.Slf4j;
import org.apache.cxf.endpoint.Client;
import org.apache.cxf.frontend.ClientProxy;
import org.apache.cxf.message.Attachment;
import org.apache.cxf.message.Message;
import org.apache.cxf.transport.http.HTTPConduit;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
public class XRoadClient {

    static final int HTTP_CONNECTION_TIMEOUT = 30000;
    static final int HTTP_RECEIVE_TIMEOUT = 60000;

    static final Map<URI, MetaServicesPort> META_SERVICE_PORTS = new HashMap<>();
    final MetaServicesPort metaServicesPort;

    final XRoadClientIdentifierType clientId;

    public XRoadClient(XRoadClientIdentifierType clientId, URI serverUrl) {
        this.metaServicesPort = getMetaServicesPort(serverUrl);
        final XRoadClientIdentifierType tmp = new XRoadClientIdentifierType();
        copyIdentifierType(tmp, clientId);
        this.clientId = tmp;
    }

    private static synchronized MetaServicesPort getMetaServicesPort(URI serverUrl) {
        /**
         * This is currently a workaround, since the current approach results in all the
         * dispatchers creating a new port, which in turn causes a failure due to HTTP
         * code 429 (Too Many Requests) from W3C's server.
         *
         * An issue is that CXF ports are not thread-safe, however that is
         * mostly for the cases of configuring it, which we do in
         * {@link}fi.vrk.xroad.catalog.collector.util.XRoadClient#getMetaServicesPort(URI)}.
         * Actually using the port in multiple threads to do requests as a client should
         * be safe.
         *
         * Nevertheless, this should be refactored when migrating away from Akka.
         * Currently just a stop-gap solution to get the JAVA21 build version running
         * correctish.
         *
         * A few alternatives I have come across for this are:
         *
         * - Try to use cataloging, which links the online resources to local files.
         * Though this apparently is pretty hit or miss depending on the implementation.
         * Some information suggests CXF does not support this.
         *
         * - Another alternative would be to use XRD4J once it is updated to JAVA21,
         * since it doesn't try to parse the WSDL every time.
         *
         */
        MetaServicesPort port = META_SERVICE_PORTS.get(serverUrl);
        if (!META_SERVICE_PORTS.containsKey(serverUrl)) {
            port = createMetaServicesPort(serverUrl);
            META_SERVICE_PORTS.put(serverUrl, port);
        } else {
            port = META_SERVICE_PORTS.get(serverUrl);
        }
        return port;
    }

    /**
     * Calls the service using JAX-WS endpoints that have been generated from wsdl
     */
    public List<XRoadServiceIdentifierType> getMethods(XRoadClientIdentifierType member,
            CatalogService catalogService) {
        XRoadServiceIdentifierType serviceIdentifierType = new XRoadServiceIdentifierType();
        copyIdentifierType(serviceIdentifierType, member);

        XRoadClientIdentifierType tmpClientId = new XRoadClientIdentifierType();
        copyIdentifierType(tmpClientId, clientId);

        serviceIdentifierType.setServiceCode("listMethods");
        serviceIdentifierType.setServiceVersion("v1");
        serviceIdentifierType.setObjectType(XRoadObjectType.SERVICE);

        ListMethodsResponse response = null;
        try {
            response = metaServicesPort.listMethods(new ListMethods(),
                    holder(tmpClientId),
                    holder(serviceIdentifierType),
                    userId(),
                    queryId(),
                    protocolVersion());
        } catch (Exception e) {
            log.error("Fetch of SOAP services failed: " + e.getMessage());
            ErrorLog errorLog = ErrorLog.builder()
                    .created(LocalDateTime.now())
                    .message("Fetch of SOAP services failed: " + e.getMessage())
                    .code("500")
                    .xRoadInstance(member.getXRoadInstance())
                    .memberClass(member.getMemberClass())
                    .memberCode(member.getMemberCode())
                    .groupCode(member.getGroupCode())
                    .securityCategoryCode(member.getSecurityCategoryCode())
                    .serverCode(member.getServerCode())
                    .serviceCode(member.getServiceCode())
                    .serviceVersion(member.getServiceVersion())
                    .subsystemCode(member.getSubsystemCode())
                    .build();
            catalogService.saveErrorLog(errorLog);
        }
        return response != null ? response.getService() : new ArrayList<>();
    }

    public String getWsdl(XRoadServiceIdentifierType service, CatalogService catalogService) throws Exception {
        XRoadServiceIdentifierType serviceIdentifierType = new XRoadServiceIdentifierType();
        copyIdentifierType(serviceIdentifierType, service);
        XRoadClientIdentifierType tmpClientId = new XRoadClientIdentifierType();
        copyIdentifierType(tmpClientId, clientId);
        serviceIdentifierType.setServiceCode("getWsdl");
        serviceIdentifierType.setServiceVersion("v1");
        serviceIdentifierType.setObjectType(XRoadObjectType.SERVICE);

        final GetWsdl getWsdl = new GetWsdl();
        getWsdl.setServiceCode(service.getServiceCode());
        getWsdl.setServiceVersion(service.getServiceVersion());

        final Holder<GetWsdlResponse> response = new Holder<>();
        final Holder<byte[]> wsdl = new Holder<>();

        try {
            metaServicesPort.getWsdl(getWsdl,
                    holder(tmpClientId),
                    holder(serviceIdentifierType),
                    userId(),
                    queryId(),
                    protocolVersion(),
                    response,
                    wsdl);
        } catch (Exception e) {
            log.error("Fetch of WSDL failed: " + e.getMessage());
            ErrorLog errorLog = ErrorLog.builder()
                    .created(LocalDateTime.now())
                    .message("Fetch of WSDL failed: " + e.getMessage())
                    .code("500")
                    .xRoadInstance(service.getXRoadInstance())
                    .memberClass(service.getMemberClass())
                    .memberCode(service.getMemberCode())
                    .groupCode(service.getGroupCode())
                    .securityCategoryCode(service.getSecurityCategoryCode())
                    .serverCode(service.getServerCode())
                    .serviceCode(service.getServiceCode())
                    .serviceVersion(service.getServiceVersion())
                    .subsystemCode(service.getSubsystemCode())
                    .build();
            catalogService.saveErrorLog(errorLog);
            throw e;
        }

        if (!(wsdl.value instanceof byte[])) {
            DataHandler dh = null;
            final Client client = ClientProxy.getClient(metaServicesPort);
            final Collection<Attachment> attachments = (Collection<Attachment>) client.getResponseContext()
                    .get(Message.ATTACHMENTS);
            if (attachments != null && attachments.size() == 1) {
                dh = attachments.iterator().next().getDataHandler();
            } else {
                log.error("Expected one WSDL attachment");
                ErrorLog errorLog = ErrorLog.builder()
                        .created(LocalDateTime.now())
                        .message("Expected one WSDL attachment")
                        .code("500")
                        .xRoadInstance(service.getXRoadInstance())
                        .memberClass(service.getMemberClass())
                        .memberCode(service.getMemberCode())
                        .groupCode(service.getGroupCode())
                        .securityCategoryCode(service.getSecurityCategoryCode())
                        .serverCode(service.getServerCode())
                        .serviceCode(service.getServiceCode())
                        .serviceVersion(service.getServiceVersion())
                        .subsystemCode(service.getSubsystemCode())
                        .build();
                catalogService.saveErrorLog(errorLog);
            }
            try (ByteArrayOutputStream buf = new ByteArrayOutputStream()) {
                if (dh == null) {
                    throw new IOException("Unable to extract attachment from response context.");
                }
                dh.writeTo(buf);
                return buf.toString(StandardCharsets.UTF_8.name());
            } catch (IOException | NullPointerException e) {
                log.error("Error downloading WSDL: ", e.getMessage());
                ErrorLog errorLog = ErrorLog.builder()
                        .created(LocalDateTime.now())
                        .message("Error downloading WSDL: " + e.getMessage())
                        .code("500")
                        .xRoadInstance(service.getXRoadInstance())
                        .memberClass(service.getMemberClass())
                        .memberCode(service.getMemberCode())
                        .groupCode(service.getGroupCode())
                        .securityCategoryCode(service.getSecurityCategoryCode())
                        .serverCode(service.getServerCode())
                        .serviceCode(service.getServiceCode())
                        .serviceVersion(service.getServiceVersion())
                        .subsystemCode(service.getSubsystemCode())
                        .build();
                catalogService.saveErrorLog(errorLog);
                throw e;
            }
        } else {
            return new String(wsdl.value, StandardCharsets.UTF_8);
        }
    }

    public String getOpenApi(XRoadRestServiceIdentifierType service,
            String host,
            String xRoadInstance,
            String memberClass,
            String memberCode,
            String subsystemCode,
            CatalogService catalogService) {
        ClientType clientType = new ClientType();
        XRoadClientIdentifierType xRoadClientIdentifierType = new XRoadClientIdentifierType();
        xRoadClientIdentifierType.setXRoadInstance(service.getXRoadInstance());
        xRoadClientIdentifierType.setMemberClass(service.getMemberClass());
        xRoadClientIdentifierType.setMemberCode(service.getMemberCode());
        xRoadClientIdentifierType.setSubsystemCode(service.getSubsystemCode());
        xRoadClientIdentifierType.setGroupCode(service.getGroupCode());
        xRoadClientIdentifierType.setServiceCode(service.getServiceCode());
        xRoadClientIdentifierType.setServiceVersion(service.getServiceVersion());
        xRoadClientIdentifierType.setSecurityCategoryCode(service.getSecurityCategoryCode());
        xRoadClientIdentifierType.setServerCode(service.getServerCode());
        xRoadClientIdentifierType.setObjectType(service.getObjectType());
        clientType.setId(xRoadClientIdentifierType);

        return MethodListUtil.openApiFromResponse(clientType, host, xRoadInstance, memberClass, memberCode,
                subsystemCode, catalogService);
    }

    private static Holder<String> queryId() {
        return holder("xroad-catalog-collector-" + UUID.randomUUID());
    }

    private static Holder<String> protocolVersion() {
        return holder("4.0");
    }

    private static Holder<String> userId() {
        return holder("xroad-catalog-collector");
    }

    private static <T> Holder<T> holder(T value) {
        return new Holder<>(value);
    }

    private static MetaServicesPort createMetaServicesPort(URI url) {
        ProducerPortService service = new ProducerPortService();
        MetaServicesPort port = service.getMetaServicesPortSoap11();
        BindingProvider bindingProvider = (BindingProvider) port;
        bindingProvider.getRequestContext().put(BindingProvider.ENDPOINT_ADDRESS_PROPERTY, url.toString());

        final HTTPConduit conduit = (HTTPConduit) ClientProxy.getClient(port).getConduit();
        conduit.getClient().setConnectionTimeout(HTTP_CONNECTION_TIMEOUT);
        conduit.getClient().setReceiveTimeout(HTTP_RECEIVE_TIMEOUT);

        return port;
    }

    private static void copyIdentifierType(XRoadIdentifierType target, XRoadIdentifierType source) {
        target.setGroupCode(source.getGroupCode());
        target.setObjectType(source.getObjectType());
        target.setMemberCode(source.getMemberCode());
        target.setServiceVersion(source.getServiceVersion());
        target.setMemberClass(source.getMemberClass());
        target.setServiceCode(source.getServiceCode());
        target.setSecurityCategoryCode(source.getSecurityCategoryCode());
        target.setServerCode(source.getServerCode());
        target.setXRoadInstance(source.getXRoadInstance());
        target.setSubsystemCode(source.getSubsystemCode());
    }

}
