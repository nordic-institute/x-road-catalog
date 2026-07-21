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
package org.niis.xroad.catalog.collector;

import com.sun.net.httpserver.HttpServer;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.niis.xroad.catalog.collector.configuration.TaskPoolConfiguration;
import org.niis.xroad.catalog.collector.configuration.TestingConfiguration;
import org.niis.xroad.catalog.collector.tasks.RecomputeDenormalizedColumnsTask;
import org.niis.xroad.catalog.persistence.repository.MemberRepository;
import org.niis.xroad.catalog.persistence.testsupport.PostgresTestBase;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises the real collector write path (
 * {@code ListClientsTask} &rarr; {@code CatalogService.saveAllMembersAndSubsystems}) against the
 * genuine Liquibase-managed Postgres schema for the first time, then verifies
 * {@link RecomputeDenormalizedColumnsTask} produces well-formed flags on top of it.
 *
 * <p>The brief this test was written from assumed the collector's {@code MockRestTemplate}
 * bean (declared in {@link TestingConfiguration}) was already wired up to serve {@code
 * /listClients}. It is not: {@code ClientListUtil.clientListFromResponse} calls its own
 * hardcoded {@code RestTemplate} directly and never consumes that Spring bean, and no classpath
 * fixture for a {@code /listClients} response exists anywhere in the repository. Per project
 * invariant, collector production code must stay observably unchanged versus develop outside of
 * this task's two approved additions ({@link RecomputeDenormalizedColumnsTask} and the {@code
 * DefaultTasksInitializer} wiring), so {@code ClientListUtil} is not touched. Instead this test
 * stands up its own minimal, ephemeral-port {@code /listClients} HTTP stub and points {@code
 * xroad-catalog.urls.list-clients-host} at it via {@code @DynamicPropertySource}.
 */
@SpringBootTest(classes = {TestingConfiguration.class, CollectorApplication.class, TaskPoolConfiguration.class},
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class CollectorPostgresIntegrationTest extends PostgresTestBase {

    private static final String LIST_CLIENTS_RESPONSE = "{"
            + "\"member\":["
            + "{\"id\":{\"xroad_instance\":\"TC\",\"member_class\":\"GOV\",\"member_code\":\"M1\","
            + "\"object_type\":\"MEMBER\"},\"name\":\"Member One\"},"
            + "{\"id\":{\"xroad_instance\":\"TC\",\"member_class\":\"GOV\",\"member_code\":\"M1\","
            + "\"subsystem_code\":\"SS1\",\"object_type\":\"SUBSYSTEM\"},\"name\":\"Member One\"}"
            + "]}";

    private static final HttpServer LIST_CLIENTS_SERVER;

    static {
        try {
            LIST_CLIENTS_SERVER = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            LIST_CLIENTS_SERVER.createContext("/listClients", exchange -> {
                byte[] body = LIST_CLIENTS_RESPONSE.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(body);
                }
            });
            LIST_CLIENTS_SERVER.setExecutor(null);
            LIST_CLIENTS_SERVER.start();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to start test /listClients HTTP stub", e);
        }
    }

    @DynamicPropertySource
    static void listClientsProperties(DynamicPropertyRegistry registry) {
        registry.add("xroad-catalog.urls.list-clients-host",
                () -> "http://localhost:" + LIST_CLIENTS_SERVER.getAddress().getPort());
        registry.add("xroad-catalog.tasks.fetch-run-unlimited", () -> "true");
        // application-test.yaml sets this true for the H2 profile (data.sql runs after Hibernate's
        // create-drop). Combined with PostgresTestBase's real Liquibase, it creates a circular
        // entityManagerFactory/liquibase depends-on, so it must be turned back off here.
        registry.add("spring.jpa.defer-datasource-initialization", () -> "false");
        // application.yaml (production) pins spring.liquibase.user/password to the real deployment
        // account with an empty password, which takes precedence over spring.datasource.* for the
        // Liquibase connection. Point Liquibase at the same Testcontainers credentials as the datasource.
        registry.add("spring.liquibase.user", CollectorPostgresIntegrationTest::getDatasourceUsername);
        registry.add("spring.liquibase.password", CollectorPostgresIntegrationTest::getDatasourcePassword);
    }

    @AfterAll
    static void stopListClientsServer() {
        LIST_CLIENTS_SERVER.stop(0);
    }

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private RecomputeDenormalizedColumnsTask recomputeTask;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void collectorWritesToLiquibaseSchemaAndRecomputeProducesFlags() {
        Integer changesets = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM databasechangelog", Integer.class);
        assertTrue(changesets > 0);

        Awaitility.await().atMost(Duration.ofSeconds(30))
                .until(() -> memberRepository.count() > 0);

        recomputeTask.run();
        Integer unset = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM member WHERE is_provider IS NULL", Integer.class);
        assertEquals(0, unset);
        Integer providers = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM member WHERE is_provider", Integer.class);
        assertEquals(0, providers);
    }
}
