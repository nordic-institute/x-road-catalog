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
package org.niis.xroad.catalog.lister.v2.converter;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.niis.xroad.catalog.persistence.entity.Member;
import org.niis.xroad.catalog.persistence.entity.OpenApi;
import org.niis.xroad.catalog.persistence.entity.Rest;
import org.niis.xroad.catalog.persistence.entity.Service;
import org.niis.xroad.catalog.persistence.entity.StatusInfo;
import org.niis.xroad.catalog.persistence.entity.Subsystem;
import org.niis.xroad.catalog.persistence.entity.Wsdl;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ServiceClassifierTest {

    private static final String SERVICE_CODE = "testSvc";
    private static final String SUBSYSTEM_CODE = "subA";
    private static final String MEMBER_CODE = "14151328";
    private static final String MEMBER_CLASS = "PUB";
    private static final String INSTANCE = "dev-cs";

    private final ServiceClassifier classifier = new ServiceClassifier();
    private Logger classifierLogger;
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void attachLogAppender() {
        classifierLogger = (Logger) LoggerFactory.getLogger(ServiceClassifier.class);
        appender = new ListAppender<>();
        appender.start();
        classifierLogger.addAppender(appender);
    }

    @AfterEach
    void detachLogAppender() {
        classifierLogger.detachAppender(appender);
    }

    @Test
    void testWsdlOnlyActiveClassifiesAsSoap() {
        Service svc = buildService();
        svc.setWsdl(buildWsdl(false));
        assertEquals("SOAP", classifier.resolveType(svc));
        assertTrue(classifier.hasDescriptor(svc));
        assertNoWarnings();
    }

    @Test
    void testOpenApiOnlyActiveClassifiesAsOpenApi() {
        Service svc = buildService();
        svc.setOpenApi(buildOpenApi(false));
        assertEquals("OPENAPI", classifier.resolveType(svc));
        assertTrue(classifier.hasDescriptor(svc));
        assertNoWarnings();
    }

    @Test
    void testRestOnlyActiveClassifiesAsRestWithoutDescriptor() {
        Service svc = buildService();
        svc.setRest(buildRest(false));
        assertEquals("REST", classifier.resolveType(svc));
        assertFalse(classifier.hasDescriptor(svc),
                "Rest entity does not count as a descriptor for V2 /descriptor endpoint");
        assertNoWarnings();
    }

    @Test
    void testDescriptorLessServiceDefaultsToRest() {
        Service svc = buildService();
        assertEquals("REST", classifier.resolveType(svc));
        assertFalse(classifier.hasDescriptor(svc));
        assertNoWarnings();
    }

    @Test
    void testAllDescriptorsRemovedClassifiesAsRest() {
        Service svc = buildService();
        svc.setWsdl(buildWsdl(true));
        svc.setOpenApi(buildOpenApi(true));
        assertEquals("REST", classifier.resolveType(svc));
        assertFalse(classifier.hasDescriptor(svc),
                "only removed descriptor rows must not count toward hasDescriptor");
        assertNoWarnings();
    }

    @Test
    void testWsdlAndOpenApiBothActiveClassifiesAsSoapAndWarns() {
        Service svc = buildService();
        svc.setWsdl(buildWsdl(false));
        svc.setOpenApi(buildOpenApi(false));

        assertEquals("SOAP", classifier.resolveType(svc),
                "X-Road invariant violation must fall through to SOAP (priority)");

        List<ILoggingEvent> warnings = appender.list.stream()
                .filter(e -> e.getLevel() == Level.WARN)
                .toList();
        assertEquals(1, warnings.size(), "exactly one WARN must fire");
        String message = warnings.get(0).getFormattedMessage();
        assertTrue(message.contains(MEMBER_CODE),
                "WARN must include the member code identifier, got: " + message);
        assertTrue(message.contains(SERVICE_CODE),
                "WARN must include the service code identifier, got: " + message);
        assertTrue(message.contains(SUBSYSTEM_CODE),
                "WARN must include the subsystem code identifier, got: " + message);
    }

    @Test
    void testHasDescriptorFalseForRestOnly() {
        Service svc = buildService();
        svc.setRest(buildRest(false));
        assertFalse(classifier.hasDescriptor(svc));
    }

    @Test
    void testHasDescriptorTrueWhenActiveWsdlOrOpenApi() {
        Service wsdlSvc = buildService();
        wsdlSvc.setWsdl(buildWsdl(false));
        assertTrue(classifier.hasDescriptor(wsdlSvc));

        Service openApiSvc = buildService();
        openApiSvc.setOpenApi(buildOpenApi(false));
        assertTrue(classifier.hasDescriptor(openApiSvc));
    }

    private void assertNoWarnings() {
        long warnCount = appender.list.stream()
                .filter(e -> e.getLevel() == Level.WARN)
                .count();
        assertEquals(0L, warnCount, "unexpected WARN emitted: " + appender.list);
    }

    private Service buildService() {
        Member member = new Member();
        member.setXRoadInstance(INSTANCE);
        member.setMemberClass(MEMBER_CLASS);
        member.setMemberCode(MEMBER_CODE);
        Subsystem sub = new Subsystem();
        sub.setSubsystemCode(SUBSYSTEM_CODE);
        sub.setMember(member);

        Service s = new Service();
        s.setSubsystem(sub);
        s.setServiceCode(SERVICE_CODE);
        s.setServiceVersion("v1");
        LocalDateTime now = LocalDateTime.now();
        s.setStatusInfo(new StatusInfo(now, now, now, null));
        s.setWsdls(new HashSet<>());
        s.setOpenApis(new HashSet<>());
        s.setRests(new HashSet<>());
        return s;
    }

    private Wsdl buildWsdl(boolean removed) {
        Wsdl w = new Wsdl();
        LocalDateTime now = LocalDateTime.now();
        w.setStatusInfo(new StatusInfo(now, now, now, removed ? now : null));
        return w;
    }

    private OpenApi buildOpenApi(boolean removed) {
        OpenApi o = new OpenApi();
        LocalDateTime now = LocalDateTime.now();
        o.setStatusInfo(new StatusInfo(now, now, now, removed ? now : null));
        return o;
    }

    private Rest buildRest(boolean removed) {
        Rest r = new Rest();
        LocalDateTime now = LocalDateTime.now();
        r.setStatusInfo(new StatusInfo(now, now, now, removed ? now : null));
        return r;
    }
}
