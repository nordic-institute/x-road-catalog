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
package org.niis.xroad.catalog.collector.tasks;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.niis.xroad.catalog.persistence.repository.DenormalizationRepository;
import org.niis.xroad.catalog.persistence.repository.projection.DescriptorAnomalyRow;
import org.slf4j.LoggerFactory;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RecomputeDenormalizedColumnsTaskTest {

    private final DenormalizationRepository repository = Mockito.mock(DenormalizationRepository.class);
    private final RecomputeDenormalizedColumnsTask task = new RecomputeDenormalizedColumnsTask(repository);

    @Test
    void runsBothRecomputesAndAnomalyScan() {
        when(repository.findServicesWithMultipleActiveDescriptors()).thenReturn(List.of());
        task.run();
        verify(repository).recomputeMemberIsProvider();
        verify(repository).recomputeServiceType();
        verify(repository).findServicesWithMultipleActiveDescriptors();
    }

    @Test
    void neverPropagatesExceptions() {
        when(repository.recomputeMemberIsProvider()).thenThrow(new RuntimeException("db down"));
        assertDoesNotThrow(task::run);
    }

    @Test
    void logsWarningForServiceWithMultipleActiveDescriptors() {
        DescriptorAnomalyRow anomalyRow = new DescriptorAnomalyRow() {
            @Override
            public long getServiceId() {
                return 99L;
            }

            @Override
            public String getMemberClass() {
                return "GOV";
            }

            @Override
            public String getMemberCode() {
                return "1234";
            }

            @Override
            public String getSubsystemCode() {
                return "SUBSYS1";
            }

            @Override
            public String getServiceCode() {
                return "getData";
            }

            @Override
            public String getServiceVersion() {
                return "v2";
            }

            @Override
            public long getWsdlCount() {
                return 1L;
            }

            @Override
            public long getOpenapiCount() {
                return 2L;
            }
        };
        when(repository.findServicesWithMultipleActiveDescriptors()).thenReturn(List.of(anomalyRow));

        Logger taskLogger = (Logger) LoggerFactory.getLogger(RecomputeDenormalizedColumnsTask.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        taskLogger.addAppender(appender);

        try {
            assertDoesNotThrow(task::run);
        } finally {
            taskLogger.detachAppender(appender);
        }

        List<ILoggingEvent> warnings = appender.list.stream().filter(event -> event.getLevel() == Level.WARN).toList();
        assertEquals(1, warnings.size());

        String message = warnings.get(0).getFormattedMessage();
        assertTrue(message.contains("service id=99"));
        assertTrue(message.contains("GOV:1234:SUBSYS1:getData"));
        assertTrue(message.contains("version v2"));
        assertTrue(message.contains("wsdl=1"));
        assertTrue(message.contains("openApi=2"));

        assertTrue(appender.list.stream().noneMatch(event -> event.getLevel() == Level.ERROR));
    }
}
