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
import org.niis.xroad.catalog.persistence.testsupport.PostgresTestBase;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@Sql(scripts = "classpath:pg/v2-fixture.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class DenormalizationRepositoryTest extends PostgresTestBase {

    @Autowired
    private DenormalizationRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void recomputeSetsProviderFlagsAndServiceTypes() {
        assertEquals(1, repository.recomputeMemberIsProvider());
        // 21/22/23/27 gain a positive-signal classification; 24/25/26 have no active descriptor
        // and no rest row, so they keep the UNKNOWN default and are not touched.
        assertEquals(4, repository.recomputeServiceType());

        assertEquals(Boolean.TRUE, flag("M1"));
        assertEquals(Boolean.FALSE, flag("M2"));
        assertEquals(Boolean.FALSE, flag("M3"));
        assertEquals(Boolean.FALSE, flag("M4"));
        assertEquals("SOAP", type(21));
        assertEquals("OPENAPI", type(22));
        assertEquals("REST", type(23));
        assertEquals("UNKNOWN", type(24));
        assertEquals("UNKNOWN", type(25));
        assertEquals("UNKNOWN", type(26));
        assertEquals("SOAP", type(27));
    }

    @Test
    void recomputeIsIdempotent() {
        repository.recomputeMemberIsProvider();
        repository.recomputeServiceType();
        assertEquals(0, repository.recomputeMemberIsProvider());
        assertEquals(0, repository.recomputeServiceType());
    }

    @Test
    void anomalyQueryFlagsMultipleActiveDescriptors() {
        List<Object[]> anomalies = repository.findServicesWithMultipleActiveDescriptors();
        assertEquals(1, anomalies.size());
        assertEquals(27L, ((Number) anomalies.get(0)[0]).longValue());
    }

    private Boolean flag(String memberCode) {
        return jdbcTemplate.queryForObject("SELECT is_provider FROM member WHERE member_code = ?", Boolean.class, memberCode);
    }

    private String type(long serviceId) {
        return jdbcTemplate.queryForObject("SELECT service_type FROM service WHERE id = ?", String.class, serviceId);
    }
}
