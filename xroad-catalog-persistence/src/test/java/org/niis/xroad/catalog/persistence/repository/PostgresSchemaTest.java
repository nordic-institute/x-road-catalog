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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class PostgresSchemaTest extends PostgresTestBase {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MemberRepository memberRepository;

    @Test
    void liquibaseCreatesCoreSchema() {
        Integer tables = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'public' "
                        + "AND table_name IN ('member','subsystem','service','wsdl','open_api','rest','endpoint','error_log')",
                Integer.class);
        assertEquals(8, tables);
        Integer changesets = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM databasechangelog", Integer.class);
        assertTrue(changesets > 0, "Liquibase must have executed changesets");
    }

    @Test
    void repositoriesReadLiquibaseSchema() {
        jdbcTemplate.update("INSERT INTO member (x_road_instance, member_class, member_code, name, created, changed, fetched) "
                + "VALUES ('TC', 'GOV', 'schema-probe', 'Schema probe', now(), now(), now())");
        assertNotNull(memberRepository.findActiveByNaturalKey("TC", "GOV", "schema-probe"));
    }

    @Test
    void denormalizedColumnsExistWithLoadBearingDefaults() {
        jdbcTemplate.update("INSERT INTO member (x_road_instance, member_class, member_code, name, created, changed, fetched) "
                + "VALUES ('TC', 'GOV', 'default-probe', 'Default probe', now(), now(), now())");
        Boolean isProvider = jdbcTemplate.queryForObject(
                "SELECT is_provider FROM member WHERE member_code = 'default-probe'", Boolean.class);
        assertEquals(Boolean.FALSE, isProvider);

        jdbcTemplate.update("INSERT INTO subsystem (member_id, subsystem_code, created, changed, fetched) "
                + "SELECT id, 'default-ss', now(), now(), now() FROM member WHERE member_code = 'default-probe'");
        jdbcTemplate.update("INSERT INTO service (subsystem_id, service_code, created, changed, fetched) "
                + "SELECT id, 'default-svc', now(), now(), now() FROM subsystem WHERE subsystem_code = 'default-ss'");
        String serviceType = jdbcTemplate.queryForObject(
                "SELECT service_type FROM service WHERE service_code = 'default-svc'", String.class);
        assertEquals("UNKNOWN", serviceType, "a service inserted before its descriptor is fetched must "
                + "read as unclassified, not as a REST guess");
    }
}
