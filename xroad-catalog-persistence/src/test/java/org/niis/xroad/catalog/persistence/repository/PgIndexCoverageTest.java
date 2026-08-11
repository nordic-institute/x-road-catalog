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

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the index set that serves the V2 hot query paths. A missing index here means a V2
 * endpoint (errors browse, descriptor lookup, reports, member-class guard) has regressed to a
 * sequential scan on a table that grows without bound.
 */
@SpringBootTest
class PgIndexCoverageTest extends PostgresTestBase {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void hotPathIndexesExist() {
        List<String> indexes = jdbcTemplate.queryForList(
                "SELECT indexname FROM pg_indexes WHERE schemaname = 'public'", String.class);
        List<String> required = List.of(
                "idx_error_log_created",
                "idx_error_log_member_created",
                "idx_member_instance_class",
                "idx_wsdl_service_id",
                "idx_open_api_service_id",
                "idx_rest_service_id",
                "idx_endpoint_service_id",
                "idx_member_created",
                "idx_member_removed",
                "idx_subsystem_created",
                "idx_subsystem_removed",
                "idx_service_created",
                "idx_service_removed");
        for (String name : required) {
            assertTrue(indexes.contains(name), "missing index: " + name);
        }
    }
}
