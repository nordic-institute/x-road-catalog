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
import org.niis.xroad.catalog.persistence.PersistenceTestApplication;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runs the real Liquibase changelog end to end with {@code contexts=users} to prove that the users-context
 * changesets are grants only: they never create database roles, only grant privileges to roles that already
 * exist. Both the collector and lister roles are pre-created directly against the container - exactly like
 * an operator would on a real install, or an initdb script would in the dev compose - before the Spring
 * context boots and Liquibase runs with only the {@code username} parameters supplied (no passwords, since
 * the changelog no longer needs them). Deliberately does not reuse {@link PostgresTestBase}: creating the
 * {@code xroad_catalog_collector}/{@code xroad_catalog_lister} roles is cluster-global, and this test must
 * not leak those roles into the container shared by every other integration test in the JVM.
 */
@SpringBootTest
class UsersContextChangelogTest {

    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final String COLLECTOR_USERNAME = "it_users_ctx_collector";
    private static final String COLLECTOR_PASSWORD = "collector-pw";
    private static final String LISTER_USERNAME = "it_users_ctx_lister";
    private static final String LISTER_PASSWORD = "li'ster-pw";
    private static final String ABSENT_COLLECTOR_USERNAME = "it_users_ctx_absent_collector";
    private static final String MISSING_ROLE_DATABASE = "users_ctx_missing_role";
    private static final int MAX_CAUSE_DEPTH = 50;

    static {
        POSTGRES.start();
        createRole(COLLECTOR_USERNAME, COLLECTOR_PASSWORD);
        createRole(LISTER_USERNAME, LISTER_PASSWORD);
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
        registry.add("spring.sql.init.mode", () -> "never");

        registry.add("spring.liquibase.enabled", () -> "true");
        registry.add("spring.liquibase.change-log", () -> "classpath:db/changelog/db.changelog-master.xml");
        registry.add("spring.liquibase.contexts", () -> "users");
        registry.add("spring.liquibase.user", POSTGRES::getUsername);
        registry.add("spring.liquibase.password", POSTGRES::getPassword);

        // Only usernames: the users context grants privileges to already-existing roles, it never creates
        // them, so the changelog has no use for passwords.
        registry.add("spring.liquibase.parameters.users.collector.username", () -> COLLECTOR_USERNAME);
        registry.add("spring.liquibase.parameters.users.lister.username", () -> LISTER_USERNAME);
    }

    private static void createRole(String username, String password) {
        String escapedPassword = password.replace("'", "''");
        try (
                Connection superuser = DriverManager.getConnection(
                        POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
                Statement statement = superuser.createStatement()) {
            statement.executeUpdate("CREATE ROLE " + username + " WITH LOGIN PASSWORD '" + escapedPassword + "'");
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to pre-create role " + username, e);
        }
    }

    @Test
    void contextStartsAndRunsOnlyGrantsChangesets() {
        // Reaching this line proves the Spring context (and the Liquibase update it triggers on startup)
        // completed without error against roles it did not create itself.
        Integer totalUsersChangesets = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM databasechangelog WHERE id LIKE '000-users-%'", Integer.class);
        assertEquals(2, totalUsersChangesets, "only the two grants changesets should exist, no create changesets");

        Integer executedGrantsChangesets = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM databasechangelog WHERE id IN "
                        + "('000-users-collector-grants', '000-users-lister-grants') AND exectype = 'EXECUTED'",
                Integer.class);
        assertEquals(2, executedGrantsChangesets, "both grants changesets must have executed");
    }

    @Test
    void grantsAllowCollectorWriteAndListerReadOnlyAccess() throws SQLException {
        try (
                Connection collector = connectAs(COLLECTOR_USERNAME, COLLECTOR_PASSWORD);
                Statement statement = collector.createStatement()) {
            statement.executeUpdate("INSERT INTO member (x_road_instance, member_class, member_code, name, "
                    + "created, changed, fetched) VALUES ('TC', 'GOV', 'users-ctx-it', 'Users context IT', "
                    + "now(), now(), now())");
        }

        try (
                Connection lister = connectAs(LISTER_USERNAME, LISTER_PASSWORD);
                Statement statement = lister.createStatement();
                ResultSet rs = statement.executeQuery(
                        "SELECT COUNT(*) FROM active_member WHERE member_code = 'users-ctx-it'")) {
            assertTrue(rs.next());
            assertEquals(1, rs.getInt(1), "lister must be able to read the row the collector wrote");
        }

        try (
                Connection lister = connectAs(LISTER_USERNAME, LISTER_PASSWORD);
                Statement statement = lister.createStatement()) {
            assertThrows(SQLException.class, () -> statement.executeUpdate(
                    "INSERT INTO member (x_road_instance, member_class, member_code, name, created, changed, "
                            + "fetched) VALUES ('TC', 'GOV', 'lister-write-attempt', 'Should fail', now(), now(), "
                            + "now())"),
                    "lister must not have write access to member");
        }
    }

    /**
     * The {@code users} changesets guard their grants with a precondition on {@code pg_roles}, so that a missing
     * role fails startup with a message naming the role instead of a bare "role does not exist" SQL error. Boots
     * an isolated context against a throwaway database in the same container, naming a collector role that was
     * never created, and asserts the operator-facing message survives all the way out of the Spring failure.
     */
    @Test
    void missingRoleHaltsStartupWithMessageNamingTheRole() throws SQLException {
        createDatabase(MISSING_ROLE_DATABASE);

        Exception failure = assertThrows(Exception.class, () -> new SpringApplicationBuilder(
                PersistenceTestApplication.class)
                .web(WebApplicationType.NONE)
                .properties(
                        "spring.datasource.url=" + jdbcUrlFor(MISSING_ROLE_DATABASE),
                        "spring.datasource.username=" + POSTGRES.getUsername(),
                        "spring.datasource.password=" + POSTGRES.getPassword(),
                        "spring.datasource.driver-class-name=org.postgresql.Driver",
                        "spring.jpa.hibernate.ddl-auto=none",
                        "spring.sql.init.mode=never",
                        "spring.liquibase.enabled=true",
                        "spring.liquibase.change-log=classpath:db/changelog/db.changelog-master.xml",
                        "spring.liquibase.contexts=users",
                        "spring.liquibase.user=" + POSTGRES.getUsername(),
                        "spring.liquibase.password=" + POSTGRES.getPassword(),
                        "spring.liquibase.parameters.users.collector.username=" + ABSENT_COLLECTOR_USERNAME,
                        "spring.liquibase.parameters.users.lister.username=" + LISTER_USERNAME)
                .run()
                .close());

        String reported = messageChainOf(failure);
        assertTrue(reported.contains(ABSENT_COLLECTOR_USERNAME),
                "startup failure must name the missing role, but was: " + reported);
        assertTrue(reported.contains("must be created before startup"),
                "startup failure must carry the precondition's onFailMessage, but was: " + reported);

        // HALT, not MARK_RAN: the changeset must stay unapplied so it still runs once the role is created.
        int recorded = countUsersChangesetsIn(MISSING_ROLE_DATABASE);
        assertEquals(0, recorded, "no users changeset may be recorded as applied when the role is missing");
    }

    private static String jdbcUrlFor(String database) {
        return "jdbc:postgresql://" + POSTGRES.getHost() + ":" + POSTGRES.getFirstMappedPort() + "/" + database;
    }

    private static void createDatabase(String database) throws SQLException {
        try (
                Connection superuser = DriverManager.getConnection(
                        POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
                Statement statement = superuser.createStatement()) {
            statement.executeUpdate("CREATE DATABASE " + database);
        }
    }

    private static int countUsersChangesetsIn(String database) throws SQLException {
        try (
                Connection connection = DriverManager.getConnection(
                        jdbcUrlFor(database), POSTGRES.getUsername(), POSTGRES.getPassword());
                Statement statement = connection.createStatement();
                ResultSet rs = statement.executeQuery(
                        "SELECT count(*) FROM information_schema.tables WHERE table_name = 'databasechangelog'")) {
            rs.next();
            if (rs.getInt(1) == 0) {
                return 0;
            }
        }
        try (
                Connection connection = DriverManager.getConnection(
                        jdbcUrlFor(database), POSTGRES.getUsername(), POSTGRES.getPassword());
                Statement statement = connection.createStatement();
                ResultSet rs = statement.executeQuery(
                        "SELECT count(*) FROM databasechangelog WHERE id LIKE '000-users-%'")) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private static String messageChainOf(Throwable throwable) {
        StringBuilder chain = new StringBuilder();
        Throwable current = throwable;
        // Depth-bounded rather than null-terminated, so a self-referencing cause cannot spin forever.
        for (int depth = 0; current != null && depth < MAX_CAUSE_DEPTH; depth++) {
            chain.append(current.getMessage()).append(System.lineSeparator());
            current = current.getCause();
        }
        return chain.toString();
    }

    private Connection connectAs(String username, String password) throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), username, password);
    }
}
