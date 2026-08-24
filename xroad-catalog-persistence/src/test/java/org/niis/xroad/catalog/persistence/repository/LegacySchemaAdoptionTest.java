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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.MountableFile;

import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proves that the Liquibase changelog adopts a pre-existing legacy master-RPM database rather than
 * failing against it. Deliberately does not reuse {@link PostgresTestBase}: this test pre-loads
 * master's real {@code create_tables_fi.sql} into the container <em>before</em> Liquibase ever runs,
 * so it needs its own container that nothing else in the JVM has touched yet.
 *
 * <p>The container's own superuser (the default testcontainers {@code test}/{@code test} credentials)
 * is used only to provision the legacy environment: it creates the {@code xroad_catalog} owner role as
 * a plain, non-superuser, non-CREATEROLE role (mirroring master's {@code init_database.sql}), the
 * {@code xroad_catalog_lister} and {@code xroad_catalog_collector} roles, and the {@code xroad_catalog}
 * database, then loads master's {@code create_tables_fi.sql} verbatim via {@code psql} inside the
 * container (the file's {@code \connect} and {@code SET} lines are psql-only and cannot run through a
 * JDBC script runner). The fi variant is used deliberately: it proves the ~35 orphaned Finnish
 * organisation/company tables are harmless to leave behind. Pre-creating both application roles is not
 * a legacy-adoption special case: the Liquibase changelog never creates database roles on any install,
 * so this mirrors what an operator (or the dev compose's initdb script) must always do beforehand.</p>
 *
 * <p>The Spring context is then started with the datasource connecting as the pre-created collector
 * role and Liquibase running as the non-CREATEROLE {@code xroad_catalog} owner with
 * {@code contexts=users}. Since the users-context changesets only grant privileges to already-existing
 * roles, the owner never needs CREATEROLE. Context startup is itself the adoption run: if Liquibase
 * cannot cope with the pre-existing schema, every test below fails at context creation.</p>
 */
@SpringBootTest
class LegacySchemaAdoptionTest {

    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final String LEGACY_DATABASE = "xroad_catalog";
    private static final String OWNER_USERNAME = "xroad_catalog";
    private static final String OWNER_PASSWORD = "legacy-owner-pw";
    private static final String COLLECTOR_USERNAME = "xroad_catalog_collector";
    private static final String COLLECTOR_PASSWORD = "legacy-collector-pw";
    private static final String LISTER_USERNAME = "xroad_catalog_lister";
    private static final String LISTER_PASSWORD = "legacy-lister-pw";

    private static final String LEGACY_JDBC_URL;

    static {
        POSTGRES.start();
        LEGACY_JDBC_URL = "jdbc:postgresql://" + POSTGRES.getHost() + ":"
                + POSTGRES.getMappedPort(PostgreSQLContainer.POSTGRESQL_PORT) + "/" + LEGACY_DATABASE;
        try {
            provisionLegacyDatabase();
        } catch (IOException | InterruptedException e) {
            throw new IllegalStateException("Failed to provision the legacy master database", e);
        }
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static void provisionLegacyDatabase() throws IOException, InterruptedException {
        // Reproduces master's init_database.sql: a plain (non-superuser, non-CREATEROLE) owner role,
        // a lister role, and a database owned by that plain role. xroad_catalog_collector has no
        // master-era counterpart; it is pre-created here exactly as the documented operator step
        // requires on every install, since the Liquibase changelog never creates database roles.
        // Each statement is sent as its own simple-query message: combining CREATE DATABASE with the
        // preceding CREATE ROLE statements in a single -c string wraps them in an implicit transaction
        // block, which PostgreSQL rejects for CREATE DATABASE.
        execAsProvisioningSuperuser(POSTGRES.getDatabaseName(),
                "CREATE ROLE " + OWNER_USERNAME + " LOGIN PASSWORD '" + OWNER_PASSWORD
                        + "' NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT;"
                        + "CREATE ROLE " + LISTER_USERNAME + " LOGIN PASSWORD '" + LISTER_PASSWORD
                        + "' NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT;"
                        + "CREATE ROLE " + COLLECTOR_USERNAME + " LOGIN PASSWORD '" + COLLECTOR_PASSWORD
                        + "' NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT;");
        execAsProvisioningSuperuser(POSTGRES.getDatabaseName(),
                "CREATE DATABASE " + LEGACY_DATABASE + " OWNER " + OWNER_USERNAME + ";");

        // The file's own leading "\connect xroad_catalog;" switches databases, so it does not matter
        // which database psql is initially pointed at.
        POSTGRES.copyFileToContainer(MountableFile.forClasspathResource("legacy/create_tables_fi.sql"),
                "/tmp/create_tables_fi.sql");
        exec("psql", "-U", POSTGRES.getUsername(), "-d", POSTGRES.getDatabaseName(),
                "-v", "ON_ERROR_STOP=1", "-f", "/tmp/create_tables_fi.sql");

        // Seed rows on the legacy schema, before the addColumn/backfill changesets ever run:
        // - adopted-provider has a subsystem with a service that has a wsdl -> is_provider=true, SOAP.
        // - adopted-bare has no subsystem -> is_provider=false.
        // - adopted-nodesc-svc has no wsdl/open_api/rest row -> service_type=UNKNOWN.
        execAsProvisioningSuperuser(LEGACY_DATABASE,
                "INSERT INTO member (x_road_instance, member_class, member_code, name, created, changed, fetched) "
                        + "VALUES ('TC','GOV','adopted-provider','Adopted Provider', now(), now(), now()),"
                        + "('TC','GOV','adopted-bare','Adopted Bare', now(), now(), now());"
                        + "INSERT INTO subsystem (member_id, subsystem_code, created, changed, fetched) "
                        + "SELECT id, 'adopted-ss', now(), now(), now() FROM member WHERE member_code = 'adopted-provider';"
                        + "INSERT INTO service (subsystem_id, service_code, created, changed, fetched) "
                        + "SELECT id, 'adopted-soap-svc', now(), now(), now() FROM subsystem WHERE subsystem_code = 'adopted-ss';"
                        + "INSERT INTO service (subsystem_id, service_code, created, changed, fetched) "
                        + "SELECT id, 'adopted-nodesc-svc', now(), now(), now() FROM subsystem WHERE subsystem_code = 'adopted-ss';"
                        + "INSERT INTO wsdl (service_id, data, external_id, created, changed, fetched) "
                        + "SELECT id, 'wsdl-data', 'adopted-wsdl-ext', now(), now(), now() "
                        + "FROM service WHERE service_code = 'adopted-soap-svc';");
    }

    private static void execAsProvisioningSuperuser(String database, String sql) throws IOException, InterruptedException {
        exec("psql", "-U", POSTGRES.getUsername(), "-d", database, "-v", "ON_ERROR_STOP=1", "-c", sql);
    }

    private static void exec(String... command) throws IOException, InterruptedException {
        Container.ExecResult result = POSTGRES.execInContainer(command);
        if (result.getExitCode() != 0) {
            throw new IllegalStateException("Command failed: " + String.join(" ", command)
                    + "\n--- stdout ---\n" + result.getStdout() + "\n--- stderr ---\n" + result.getStderr());
        }
    }

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        // The application datasource connects as the pre-created collector role, exactly as the
        // collector service does against a real adopted database.
        registry.add("spring.datasource.url", () -> LEGACY_JDBC_URL);
        registry.add("spring.datasource.username", () -> COLLECTOR_USERNAME);
        registry.add("spring.datasource.password", () -> COLLECTOR_PASSWORD);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
        registry.add("spring.sql.init.mode", () -> "never");

        // Liquibase runs as the legacy database's own owner: a plain role with no CREATEROLE.
        registry.add("spring.liquibase.enabled", () -> "true");
        registry.add("spring.liquibase.change-log", () -> "classpath:db/changelog/db.changelog-master.xml");
        registry.add("spring.liquibase.contexts", () -> "users");
        registry.add("spring.liquibase.user", () -> OWNER_USERNAME);
        registry.add("spring.liquibase.password", () -> OWNER_PASSWORD);

        // Only usernames: the users context grants privileges to already-existing roles, it never
        // creates them, so the changelog has no use for passwords.
        registry.add("spring.liquibase.parameters.users.collector.username", () -> COLLECTOR_USERNAME);
        registry.add("spring.liquibase.parameters.users.lister.username", () -> LISTER_USERNAME);
    }

    @Test
    void usersContextChangesetsGrantPrivilegesToThePreExistingRoles() {
        Integer totalUsersChangesets = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM databasechangelog WHERE id LIKE '000-users-%'", Integer.class);
        assertEquals(2, totalUsersChangesets, "only the two grants changesets should exist, no create changesets");

        assertEquals("EXECUTED", exectype("000-users-collector-grants"));
        assertEquals("EXECUTED", exectype("000-users-lister-grants"));
    }

    @Test
    void commonTablesChangesetsAdoptLegacyTablesAndAddOnlyTheMissingColumns() {
        assertEquals("MARK_RAN", exectype("010-common-tables-member"),
                "member already exists in the legacy schema, so createTable must be skipped");
        assertEquals("MARK_RAN", exectype("010-common-tables-service"),
                "service already exists in the legacy schema, so createTable must be skipped");
        assertEquals("EXECUTED", exectype("010-member-add-is-provider"),
                "is_provider is new, so the guarded addColumn changeset must run");
        assertEquals("EXECUTED", exectype("010-service-add-service-type"),
                "service_type is new, so the guarded addColumn changeset must run");
    }

    @Test
    void backfillCollectionRunAndActiveViewChangesetsAllExecute() {
        Integer backfillExecuted = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM databasechangelog WHERE id LIKE 'backfill-%' AND exectype = 'EXECUTED'",
                Integer.class);
        assertEquals(2, backfillExecuted, "both V2 backfill changesets must run against the adopted data");

        // The table changeset actually creates collection_run: EXECUTED. The companion sequence
        // changeset is redundant even on a brand-new install, because Liquibase's own bigserial column
        // already creates a same-named sequence as a side effect of the table changeset above, so it
        // is expected to be skipped as MARK_RAN here too, adoption or not.
        assertEquals("EXECUTED", exectype("014-collection-run-table"));
        assertEquals("MARK_RAN", exectype("014-collection-run-sequence"));

        Integer activeViewsExecuted = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM databasechangelog WHERE id LIKE '015-%' AND exectype = 'EXECUTED'",
                Integer.class);
        assertEquals(9, activeViewsExecuted, "the 8 active views plus their grants changeset are all new");
    }

    @Test
    void denormalizedColumnsHaveCorrectTypesAndBackfilledLoadBearingValues() {
        var isProviderColumn = jdbcTemplate.queryForMap(
                "SELECT data_type, is_nullable, column_default FROM information_schema.columns "
                        + "WHERE table_name = 'member' AND column_name = 'is_provider'");
        assertEquals("boolean", isProviderColumn.get("data_type"));
        assertEquals("NO", isProviderColumn.get("is_nullable"));

        var serviceTypeColumn = jdbcTemplate.queryForMap(
                "SELECT data_type, is_nullable FROM information_schema.columns "
                        + "WHERE table_name = 'service' AND column_name = 'service_type'");
        assertEquals("text", serviceTypeColumn.get("data_type"));
        assertEquals("NO", serviceTypeColumn.get("is_nullable"));

        assertEquals(Boolean.TRUE, jdbcTemplate.queryForObject(
                "SELECT is_provider FROM member WHERE member_code = 'adopted-provider'", Boolean.class),
                "a member with a live subsystem/service must backfill as a provider");
        assertEquals(Boolean.FALSE, jdbcTemplate.queryForObject(
                "SELECT is_provider FROM member WHERE member_code = 'adopted-bare'", Boolean.class),
                "a member with no subsystem must backfill as not a provider");
        assertEquals("SOAP", jdbcTemplate.queryForObject(
                "SELECT service_type FROM service WHERE service_code = 'adopted-soap-svc'", String.class),
                "a service with a wsdl must backfill as SOAP");
        assertEquals("UNKNOWN", jdbcTemplate.queryForObject(
                "SELECT service_type FROM service WHERE service_code = 'adopted-nodesc-svc'", String.class),
                "a service with no descriptor must backfill as UNKNOWN");
    }

    @Test
    void collectionRunTableAndAllActiveViewsExistAndServeSeededLiveRows() {
        Integer collectionRunTables = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables "
                        + "WHERE table_schema = 'public' AND table_name = 'collection_run'", Integer.class);
        assertEquals(1, collectionRunTables);

        Integer activeViews = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.views WHERE table_schema = 'public' AND table_name IN "
                        + "('active_member','active_subsystem','active_service','active_wsdl','active_open_api',"
                        + "'active_rest','active_endpoint','active_search_index')", Integer.class);
        assertEquals(8, activeViews);

        Integer activeMemberRows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM active_member WHERE member_code IN ('adopted-provider','adopted-bare')",
                Integer.class);
        assertEquals(2, activeMemberRows, "both seeded legacy members must be visible through the new active view");

        Integer searchIndexRows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM active_search_index WHERE member_code IN ('adopted-provider','adopted-bare')",
                Integer.class);
        assertTrue(searchIndexRows >= 2, "the seeded legacy members must be indexed by the new search view");
    }

    @Test
    void orphanedFinnishTableSurvivesAdoptionUntouched() {
        Integer organizationTables = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables "
                        + "WHERE table_schema = 'public' AND table_name = 'organization'", Integer.class);
        assertEquals(1, organizationTables, "the fi-only organization table must survive adoption untouched");

        Integer organizationGuidColumns = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns "
                        + "WHERE table_name = 'organization' AND column_name = 'guid'", Integer.class);
        assertEquals(1, organizationGuidColumns, "the legacy fi columns must be left exactly as master defined them");
    }

    @Test
    void listerRoleReachedByGrantsCanSelectFromActiveViews() throws SQLException {
        try (
                Connection lister = DriverManager.getConnection(LEGACY_JDBC_URL, LISTER_USERNAME, LISTER_PASSWORD);
                ResultSet rs = lister.createStatement().executeQuery(
                        "SELECT COUNT(*) FROM active_member WHERE member_code = 'adopted-provider'")) {
            assertTrue(rs.next());
            assertEquals(1, rs.getInt(1),
                    "the grants changeset must have reached the pre-existing lister role, not just a freshly created one");
        }
    }

    private String exectype(String changesetId) {
        return jdbcTemplate.queryForObject(
                "SELECT exectype FROM databasechangelog WHERE id = ?", String.class, changesetId);
    }
}
