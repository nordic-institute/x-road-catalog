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
package fi.vrk.xroad.catalog.persistence;

import fi.vrk.xroad.catalog.persistence.entity.ErrorLog;
import fi.vrk.xroad.catalog.persistence.repository.ErrorLogRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import java.time.LocalDateTime;
import java.time.Month;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
public class ErrorLogRepositoryTest {

    @Autowired
    ErrorLogRepository errorLogRepository;

    @Test
    public void testFindAnySince() {
        LocalDateTime changedAfter = LocalDateTime.of(2020, Month.JANUARY, 1, 0, 0, 0);
        LocalDateTime changedBefore = LocalDateTime.of(2023, Month.JANUARY, 1, 0, 0, 0);
        Set<ErrorLog> errorLogEntries = errorLogRepository.findAny(changedAfter, changedBefore);
        assertEquals(7, errorLogEntries.size());
    }

    @Test
    public void testFindAnyByAllParameters() {
        LocalDateTime startDate = LocalDateTime.of(2020, Month.JANUARY, 1, 0, 0, 0);
        LocalDateTime endDate = LocalDateTime.now();
        Page<ErrorLog> errorLogEntries = errorLogRepository.findAnyByAllParameters(startDate,
                endDate,
                "DEV",
                "GOV",
                "1234",
                "TestSubsystem",
                PageRequest.of(0, 100));
        assertEquals(1, errorLogEntries.getTotalPages());
        assertEquals(1, errorLogEntries.getTotalElements());
    }

    @Test
    public void testFindAnyByMemberCode() {
        LocalDateTime startDate = LocalDateTime.of(2020, Month.JANUARY, 1, 0, 0, 0);
        LocalDateTime endDate = LocalDateTime.now();
        Page<ErrorLog> errorLogEntries = errorLogRepository.findAnyByMemberCode(startDate,
                endDate,
                "DEV",
                "GOV",
                "1234",
                PageRequest.of(0, 100));
        assertEquals(1, errorLogEntries.getTotalPages());
        assertEquals(2, errorLogEntries.getTotalElements());
    }

    @Test
    public void testFindAnyByMemberClass() {
        LocalDateTime startDate = LocalDateTime.of(2020, Month.JANUARY, 1, 0, 0, 0);
        LocalDateTime endDate = LocalDateTime.now();
        Page<ErrorLog> errorLogEntries = errorLogRepository.findAnyByMemberClass(startDate,
                endDate,
                "DEV", "GOV", PageRequest.of(0, 100));
        assertEquals(1, errorLogEntries.getTotalPages());
        assertEquals(3, errorLogEntries.getTotalElements());
    }

    @Test
    public void testFindAnyByInstance() {
        LocalDateTime startDate = LocalDateTime.of(2020, Month.JANUARY, 1, 0, 0, 0);
        LocalDateTime endDate = LocalDateTime.now();
        Page<ErrorLog> errorLogEntries = errorLogRepository.findAnyByInstance(startDate,
                endDate, "DEV", PageRequest.of(0, 100));
        assertEquals(1, errorLogEntries.getTotalPages());
        assertEquals(4, errorLogEntries.getTotalElements());
    }

    @Test
    public void testFindAll() {
        LocalDateTime startDate = LocalDateTime.of(2020, Month.JANUARY, 1, 0, 0, 0);
        LocalDateTime endDate = LocalDateTime.now();
        Page<ErrorLog> errorLogEntries = errorLogRepository.findAnyByCreated(startDate,
                endDate, PageRequest.of(0, 100));
        assertEquals(1, errorLogEntries.getTotalPages());
        assertEquals(7, errorLogEntries.getTotalElements());
    }

}

