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
package org.niis.xroad.catalog.persistence.repository;

import org.junit.jupiter.api.Test;
import org.niis.xroad.catalog.persistence.entity.ErrorLog;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

@SpringBootTest
@ActiveProfiles({"test", "general-testdata"})
public class ErrorLogRepositoryV2Test {

    @Autowired
    ErrorLogRepositoryV2 errorLogRepository;

    @Test
    public void testFindAnyByService() {
        LocalDateTime start = LocalDateTime.parse("2020-01-01T00:00:00");
        LocalDateTime end = LocalDateTime.parse("2021-01-01T00:00:00");
        Page<ErrorLog> errors = errorLogRepository.findAnyByService(
                start, end, "GOV", "1234", "TestSubsystem", "testService",
                PageRequest.of(0, 100));
        assertNotNull(errors);
        assertEquals(2, errors.getTotalElements());
        errors.getContent().forEach(e -> {
            assertEquals("GOV", e.getMemberClass());
            assertEquals("1234", e.getMemberCode());
            assertEquals("TestSubsystem", e.getSubsystemCode());
            assertEquals("testService", e.getServiceCode());
        });
        Set<String> versions = errors.getContent().stream()
                .map(ErrorLog::getServiceVersion)
                .collect(Collectors.toSet());
        assertEquals(Set.of("v1", "v2"), versions);
    }

    @Test
    public void testFindAnyByServiceExclusiveUpperBound() {
        LocalDateTime start = LocalDateTime.parse("2020-01-01T00:00:00");
        LocalDateTime end = LocalDateTime.parse("2020-05-04T11:41:24.792");
        Page<ErrorLog> errors = errorLogRepository.findAnyByService(
                start, end, "GOV", "1234", "TestSubsystem", "testService",
                PageRequest.of(0, 100));
        assertEquals(0, errors.getTotalElements());
    }

    @Test
    public void testFindAnyByVersionWithVersion() {
        LocalDateTime start = LocalDateTime.parse("2020-01-01T00:00:00");
        LocalDateTime end = LocalDateTime.parse("2021-01-01T00:00:00");
        Page<ErrorLog> errors = errorLogRepository.findAnyByVersion(
                start, end, "GOV", "1234", "TestSubsystem", "testService", "v1",
                PageRequest.of(0, 100));
        assertEquals(1, errors.getTotalElements());
        errors.getContent().forEach(e -> assertEquals("v1", e.getServiceVersion()));
    }

    @Test
    public void testFindAnyByNullVersion() {
        LocalDateTime start = LocalDateTime.parse("2020-01-01T00:00:00");
        LocalDateTime end = LocalDateTime.parse("2021-01-01T00:00:00");
        Page<ErrorLog> errors = errorLogRepository.findAnyByNullVersion(
                start, end, "GOV", "1234", "TestSubsystem", "restService",
                PageRequest.of(0, 100));
        assertEquals(1, errors.getTotalElements());
        errors.getContent().forEach(e -> assertNull(e.getServiceVersion()));
    }

    @Test
    public void testFindAnyInRange() {
        LocalDateTime start = LocalDateTime.parse("2020-01-01T00:00:00");
        LocalDateTime end = LocalDateTime.parse("2021-01-01T00:00:00");
        Page<ErrorLog> errors = errorLogRepository.findAnyInRange(start, end, PageRequest.of(0, 100));
        assertEquals(9, errors.getTotalElements());
    }

    @Test
    public void testFindAnyByMemberClass() {
        LocalDateTime start = LocalDateTime.parse("2020-01-01T00:00:00");
        LocalDateTime end = LocalDateTime.parse("2021-01-01T00:00:00");
        Page<ErrorLog> errors = errorLogRepository.findAnyByMemberClass(
                start, end, "GOV", PageRequest.of(0, 100));
        assertEquals(6, errors.getTotalElements());
    }

    @Test
    public void testFindAnyByMember() {
        LocalDateTime start = LocalDateTime.parse("2020-01-01T00:00:00");
        LocalDateTime end = LocalDateTime.parse("2021-01-01T00:00:00");
        Page<ErrorLog> errors = errorLogRepository.findAnyByMember(
                start, end, "GOV", "1234", PageRequest.of(0, 100));
        assertEquals(5, errors.getTotalElements());
    }

    @Test
    public void testFindAnyBySubsystem() {
        LocalDateTime start = LocalDateTime.parse("2020-01-01T00:00:00");
        LocalDateTime end = LocalDateTime.parse("2021-01-01T00:00:00");
        Page<ErrorLog> errors = errorLogRepository.findAnyBySubsystem(
                start, end, "GOV", "1234", "TestSubsystem", PageRequest.of(0, 100));
        assertEquals(4, errors.getTotalElements());
    }
}
