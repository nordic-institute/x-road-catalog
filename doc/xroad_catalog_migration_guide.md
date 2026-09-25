# X-Road Catalog Migration Guide — RPM Installation to Containers
Version: 1.0.0
Doc. ID: MG-XRDCAT

---

## Version history <!-- omit in toc -->
| Date       | Version | Description     | Author     |
|------------|---------|-----------------|------------|
| 25.08.2026 | 1.0.0   | Initial version | Raido Kaju |

## Table of Contents <!-- omit in toc -->

<!-- toc -->
<!-- vim-markdown-toc GFM -->

* [License](#license)
* [1. Introduction](#1-introduction)
  * [1.1 Who Needs This Guide](#11-who-needs-this-guide)
  * [1.2 What Changes](#12-what-changes)
* [2. Decide: Migrate the Data or Start Fresh](#2-decide-migrate-the-data-or-start-fresh)
* [3. API Compatibility](#3-api-compatibility)
  * [3.1 REST V1](#31-rest-v1)
  * [3.2 SOAP](#32-soap)
  * [3.3 Removed Functionality](#33-removed-functionality)
* [4. Configuration Translation](#4-configuration-translation)
  * [4.1 Collector](#41-collector)
  * [4.2 Lister and Configuration Client](#42-lister-and-configuration-client)
  * [4.3 TLS Keystore](#43-tls-keystore)
  * [4.4 Settings Without an Equivalent](#44-settings-without-an-equivalent)
* [5. Database Migration](#5-database-migration)
  * [5.1 What the Migration Does to the Schema](#51-what-the-migration-does-to-the-schema)
  * [5.2 Roles](#52-roles)
* [6. Migration Procedure](#6-migration-procedure)
  * [6.1 Pre-checks](#61-pre-checks)
  * [6.2 Backup](#62-backup)
  * [6.3 Prepare the New Database](#63-prepare-the-new-database)
  * [6.4 Prepare the Container Configuration](#64-prepare-the-container-configuration)
  * [6.5 Start the Collector and Verify the Schema Migration](#65-start-the-collector-and-verify-the-schema-migration)
  * [6.6 Start the Lister and Verify the APIs](#66-start-the-lister-and-verify-the-apis)
  * [6.7 Cutover](#67-cutover)
  * [6.8 Rollback](#68-rollback)
* [7. Fresh-Start Procedure](#7-fresh-start-procedure)

<!-- vim-markdown-toc -->
<!-- tocstop -->

## License

This document is licensed under the Creative Commons Attribution-ShareAlike 3.0 Unported License. To view a copy of this license, visit http://creativecommons.org/licenses/by-sa/3.0/.

## 1. Introduction

### 1.1 Who Needs This Guide

This guide is for operators running X-Road Catalog from the RPM packages (`xroad-catalog-collector` and
`xroad-catalog-lister` on RHEL, with a PostgreSQL database created by the packages' `init_database.sql` and
`create_tables_*.sql` scripts) who are moving to the container-based release. New installations should use the
[Installation Guide](xroad_catalog_installation_guide.md) directly.

The guide assumes familiarity with the Installation Guide; it describes only what is specific to moving an existing
installation and its data.

### 1.2 What Changes

| Area                   | RPM installation                                                                  | Container installation                                                                                                                         |
|------------------------|-----------------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------|
| Packaging              | RPMs, systemd units, `/usr/lib/xroad-catalog/*.jar`                               | Images `niis/xroad-catalog-collector` and `niis/xroad-catalog-lister`                                                                          |
| Configuration          | `/etc/xroad/xroad-catalog/*.properties`, `application.conf`                       | Environment variables or a mounted `application.yaml`; key names changed, see [4](#4-configuration-translation)                                |
| Schema management      | SQL scripts run once by the RPM `%post` scriptlet                                 | Liquibase, run by the collector at every start; adopts the existing schema                                                                     |
| Database roles         | Everything connects as `xroad_catalog`                                            | `xroad_catalog` (owner, migrations only), `xroad_catalog_collector` (read/write), `xroad_catalog_lister` (read-only)                           |
| Configuration client   | Separate `xroad-confclient` RPM and systemd unit                                  | Embedded in the lister, configured with `xroad.configuration-client.*` keys                                                                    |
| APIs                   | REST V1 and SOAP                                                                  | REST V2 (new); REST V1 and SOAP retained behind `xroad-catalog.legacy-api.enabled`, off by default; differences, see [3](#3-api-compatibility) |
| FI profile             | Organization / company collection and endpoints                                   | Removed entirely                                                                                                                               |
| Logs                   | journald                                                                          | Container stdout                                                                                                                               |

## 2. Decide: Migrate the Data or Start Fresh

WSDL and OpenAPI documents are re-fetched in the first collection cycle and are not affected by the decision. What a
fresh start loses permanently:

| Lost                                                                           | Consequence                                                                                                                                                                                                                                              |
|--------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `created` / `changed` timestamps of members, subsystems, services, descriptors | Every statistics and report endpoint (V1 `getServiceStatistics`, `getListOfServices`, `getDistinctServiceStatistics`; V2 `/api/v2/reports/*`) is computed from these. A fresh database reports everything as created on the day of the first collection. |
| Soft deleted data with `removed`                                               | Members, subsystems and services that existed and were removed are unknown to the new installation.                                                                                                                                                      |
| Error log                                                                      | Collection error history (90 days by default).                                                                                                                                                                                                           |
| `externalId` handles of WSDL / OpenAPI records                                 | The public identifiers used by SOAP `GetWsdl` and `GetOpenAPI` callers are regenerated.                                                                                                                                                                  |

**Decision rule:** if anyone consumes the statistics or reports, or SOAP clients cache `externalId` values, migrate the
data ([6. Migration Procedure](#6-migration-procedure)). Otherwise a fresh start is simpler
([7. Fresh-Start Procedure](#7-fresh-start-procedure)).

Migrated data arrives without `collection_run` history (a new table), so the V2 heartbeat's `lastCollectionData`
fields stay `null` until the first collection cycle of the new installation has finished. `currentRun` is populated
only *while* a cycle is running (see the [User Guide](xroad_catalog_user_guide.md#332-heartbeat)).

## 3. API Compatibility

### 3.1 REST V1

The V1 REST API is retained unchanged: paths, parameters, response fields and timestamp format are identical. Every V1
response carries a `Deprecation: true` header.

The V1 API is **disabled by default** and answers `404` until `xroad-catalog.legacy-api.enabled=true` is set on the
lister. It is deprecated; plan to move consumers to the V2 API described in the
[User Guide](xroad_catalog_user_guide.md#33-rest-api-v2).

### 3.2 SOAP

The SOAP interface is gated by the same `xroad-catalog.legacy-api.enabled` flag and is served at `/ws/*` as before
(`POST /ws/ListMembers`, `GET /ws/services.wsdl`, ...). Differences from the RPM version:

* `GetOrganizations`, `HasOrganizationChanged`, `GetCompanies` and `HasCompanyChanged` were removed with the FI profile;
  calling them returns an `Unknown service` SOAP fault.
* The X-Road headers are optional when calling the lister directly. With headers present, `iden:serviceCode` selects the
  operation and must name the body's root element; without headers the body's root element is used. A request that
  carries only some of the X-Road headers is rejected.
* The served WSDL no longer lists the four operations above, and its `soap:address` is the fixed
  `http://localhost:8070/ws` rather than the address the request came in on.
* Requests the lister cannot process are answered with HTTP 500 and a SOAP fault: `SOAP-ENV:Server` for an unknown
  operation and `SOAP-ENV:Client` for a body that is not XML, where the RPM lister answered an empty 404 or 400, and
  `SOAP-ENV:Client` naming the parameter for an unparseable `startDateTime` or `endDateTime`.

### 3.3 Removed Functionality

* The **FI profile** (`CATALOG_PROFILE=fi`): organization and company collection from the Finnish registries, the
  `/api/getOrganization/{businessCode}`, `/api/getOrganizationChanges/{businessCode}` and `/api/organizationHeartbeat`
  REST endpoints, the four SOAP operations above, and the ~35 `organization*` / `company*` / `business*` database
  tables. The tables are left untouched by the migration (see [5.1](#51-what-the-migration-does-to-the-schema)).
* All Akka tuning in `application.conf`; the collector now uses Java virtual threads, sized with the
  `xroad-catalog.pool-size.*` properties.

## 4. Configuration Translation

Values move from the properties files under `/etc/xroad/xroad-catalog/` to environment variables of the respective
container (or to a mounted `application.yaml`). The environment variable is derived from the new property name by
uppercasing it and replacing `.` and `-` with `_`.

### 4.1 Collector

From `collector-production.properties` and `catalogdb-production.properties`:

| RPM property                                   | New property                                                                              | Environment variable                                   |
|------------------------------------------------|-------------------------------------------------------------------------------------------|--------------------------------------------------------|
| `xroad-catalog.xroad-instance`                 | `xroad-catalog.target.xroad-instance`                                                     | `XROAD_CATALOG_TARGET_XROAD_INSTANCE`                  |
| `xroad-catalog.member-class`                   | `xroad-catalog.target.member-class`                                                       | `XROAD_CATALOG_TARGET_MEMBER_CLASS`                    |
| `xroad-catalog.member-code`                    | `xroad-catalog.target.member-code`                                                        | `XROAD_CATALOG_TARGET_MEMBER_CODE`                     |
| `xroad-catalog.subsystem-code`                 | `xroad-catalog.target.subsystem-code`                                                     | `XROAD_CATALOG_TARGET_SUBSYSTEM_CODE`                  |
| `xroad-catalog.security-server-host`           | `xroad-catalog.urls.security-server-host`                                                 | `XROAD_CATALOG_URLS_SECURITY_SERVER_HOST`              |
| `xroad-catalog.list-clients-host`              | `xroad-catalog.urls.list-clients-host`                                                    | `XROAD_CATALOG_URLS_LIST_CLIENTS_HOST`                 |
| `xroad-catalog.webservices-endpoint`           | `xroad-catalog.urls.webservices-endpoint`                                                 | `XROAD_CATALOG_URLS_WEBSERVICES_ENDPOINT`              |
| `xroad-catalog.fetch-wsdl-pool-size`           | `xroad-catalog.pool-size.fetch-wsdl`                                                      | `XROAD_CATALOG_POOL_SIZE_FETCH_WSDL`                   |
| `xroad-catalog.fetch-openapi-pool-size`        | `xroad-catalog.pool-size.fetch-openapi`                                                   | `XROAD_CATALOG_POOL_SIZE_FETCH_OPENAPI`                |
| `xroad-catalog.fetch-rest-pool-size`           | `xroad-catalog.pool-size.fetch-rest`                                                      | `XROAD_CATALOG_POOL_SIZE_FETCH_REST`                   |
| `xroad-catalog.list-methods-pool-size`         | `xroad-catalog.pool-size.list-methods`                                                    | `XROAD_CATALOG_POOL_SIZE_LIST_METHODS`                 |
| `xroad-catalog.error-log-length-in-days`       | `xroad-catalog.log-storage.error-log-length-in-days`                                      | `XROAD_CATALOG_LOG_STORAGE_ERROR_LOG_LENGTH_IN_DAYS`   |
| `xroad-catalog.flush-log-time-after-hour`      | `xroad-catalog.log-storage.flush-log-time-after-hour`                                     | `XROAD_CATALOG_LOG_STORAGE_FLUSH_LOG_TIME_AFTER_HOUR`  |
| `xroad-catalog.flush-log-time-before-hour`     | `xroad-catalog.log-storage.flush-log-time-before-hour`                                    | `XROAD_CATALOG_LOG_STORAGE_FLUSH_LOG_TIME_BEFORE_HOUR` |
| `xroad-catalog.collector-interval-min`         | `xroad-catalog.tasks.collector-interval-min`                                              | `XROAD_CATALOG_TASKS_COLLECTOR_INTERVAL_MIN`           |
| `xroad-catalog.fetch-run-unlimited`            | `xroad-catalog.tasks.fetch-run-unlimited`                                                 | `XROAD_CATALOG_TASKS_FETCH_RUN_UNLIMITED`              |
| `xroad-catalog.fetch-time-after-hour`          | `xroad-catalog.tasks.fetch-time-after-hour`                                               | `XROAD_CATALOG_TASKS_FETCH_TIME_AFTER_HOUR`            |
| `xroad-catalog.fetch-time-before-hour`         | `xroad-catalog.tasks.fetch-time-before-hour`                                              | `XROAD_CATALOG_TASKS_FETCH_TIME_BEFORE_HOUR`           |
| `spring.datasource.url`                        | point the host at the new database, append `?socketTimeout=60`                            | `SPRING_DATASOURCE_URL`                                |
| `spring.datasource.username` (`xroad_catalog`) | `spring.datasource.username` — optional, the default is already `xroad_catalog_collector` | `SPRING_DATASOURCE_USERNAME`                           |
| `spring.datasource.password`                   | `spring.datasource.password` (password of `xroad_catalog_collector`)                      | `SPRING_DATASOURCE_PASSWORD`                           |
| —                                              | `spring.liquibase.password` (password of `xroad_catalog`)                                 | `SPRING_LIQUIBASE_PASSWORD`                            |

`xroad-catalog.urls.list-clients-host` and `webservices-endpoint` are derived from the security server host in the new
release, exactly as the RPM properties file derived them. If the RPM file left them at the packaged
`${xroad-catalog.security-server-host}` value, do not copy that value literally — set only
`XROAD_CATALOG_URLS_SECURITY_SERVER_HOST`.

New properties worth setting: the `xroad-catalog.tasks.client-*-timeout-seconds` limits and, with the caveat below,
`xroad-catalog.instance.ignored-subsystem-ids` (e.g. the management subsystem).

On a migrated database, do not list a subsystem that the data already contains. An ignored subsystem is filtered out
before the client list is saved, which is indistinguishable from a subsystem that left the ecosystem, so the first
collection cycle stamps `removed` on it and on all of its services. That is deliberate behaviour of the container
release, not a defect, but it is irreversible within one cycle — decide before the first collection run whether the
subsystem's history may be tombstoned.

### 4.2 Lister and Configuration Client

From `lister-production.properties` and `catalogdb-production.properties`:

| RPM property                                   | New property                                                                           | Environment variable               |
|------------------------------------------------|----------------------------------------------------------------------------------------|------------------------------------|
| `xroad-catalog.shared-params-file`             | unchanged                                                                              | `XROAD_CATALOG_SHARED_PARAMS_FILE` |
| `spring.datasource.url`                        | point the host at the new database, append `?socketTimeout=60`                         | `SPRING_DATASOURCE_URL`            |
| `spring.datasource.username` (`xroad_catalog`) | `spring.datasource.username` — optional, the default is already `xroad_catalog_lister` | `SPRING_DATASOURCE_USERNAME`       |
| `spring.datasource.password`                   | `spring.datasource.password` (password of `xroad_catalog_lister`)                      | `SPRING_DATASOURCE_PASSWORD`       |
| `springdoc.packagesToScan`                     | dropped                                                                                | —                                  |
| —                                              | `xroad-catalog.legacy-api.enabled=true` (to keep V1 and SOAP)                          | `XROAD_CATALOG_LEGACY_API_ENABLED` |

The separate `xroad-confclient` service is gone; the lister downloads the global configuration itself. Its settings
become `xroad.configuration-client.*` properties (see the
[lister README](../xroad-catalog-lister/README.md#optional-configurations-for-configuration-client-features)).
Drop `proxy-configuration-backup-cron`: the lister image has no backup script and its default never fires. The
configuration anchor is mounted into the lister container at
`/etc/xroad/configuration-anchor.xml`.

### 4.3 TLS Keystore

Only if the collector connects to the Security Server over HTTPS. `xroad-catalog.ssl-keystore`,
`xroad-catalog.ssl-keystore-password` and the host CA bundle (`update-ca-trust`) are not used. In the new version, the
keystore and a truststore holding the Security Server's certificate are mounted into the collector container and
activated with `javax.net.ssl.*` JVM flags in a mounted JVM options file — see
[Installation Guide, 7.2](xroad_catalog_installation_guide.md#72-tls-keystore-and-truststore-collector). The existing
`/etc/xroad/xroad-catalog/keystore` file can be reused if it is in PKCS12 format (`keytool -importkeystore` converts a
JKS file).

### 4.4 Settings Without an Equivalent

`catalog-profile.properties` (`CATALOG_PROFILE`), every `fetch-organizations*` / `fetch-companies*` /
`max-organizations-per-request` setting, `application.conf` (Akka), `fetch-wsdl-host` / `fetch-openapi-host` (already
unused in RPM), and the `sshtest` profile. The `xroad-catalog` system user and `/var/log/xroad/` are not used by the
containers.

Everything else left in the properties files is framework plumbing that the images already set correctly; do not carry
it over. That covers `spring.main.web_environment` and `spring.main.allow-bean-definition-overriding` in
`collector-production.properties`; the non-datasource keys of `catalogdb-production.properties`
(`spring.jpa.database`, `spring.datasource.platform`, `spring.jpa.show-sql`, `spring.jpa.hibernate.ddl-auto`,
`spring.database.driverClassName`); `springdoc.api-docs.enabled`, `springdoc.swagger-ui.enabled`,
`springdoc.swagger-ui.path` and the `logging.level.*` keys in `lister-production.properties`; and
`version.properties`, which only records the installed version. Adjust log levels with `LOGGING_LEVEL_*` environment
variables instead ([Installation Guide, 12](xroad_catalog_installation_guide.md#12-logs)).

## 5. Database Migration

### 5.1 What the Migration Does to the Schema

On its first start against an RPM database the collector:

* keeps the eight existing tables (`member`, `subsystem`, `service`, `wsdl`, `open_api`, `rest`, `endpoint`,
  `error_log`) and their sequences;
* adds `member.is_provider` (`boolean NOT NULL DEFAULT false`) and `service.service_type` (`text NOT NULL DEFAULT
  'UNKNOWN'`) and backfills them from existing data;
* creates the indexes, the `collection_run` table and the `active_*` views used by the V2 API;
* grants the application roles their privileges ([5.2](#52-roles)).

Existing rows, their `created` / `changed` / `removed` timestamps and the `external_id` values are untouched. The ~35
FI-profile tables, if present, are ignored: they are not dropped, not read, and can be removed manually at any time
(`DROP TABLE` as the owner) or left in place.

A migrated database cannot be used by the RPM version. **Always migrate into a copy** of the database and keep the
original for rollback.

### 5.2 Roles

The new version requires three roles: `xroad_catalog` (owner), `xroad_catalog_collector` and `xroad_catalog_lister`. The RPM
scripts created only `xroad_catalog` and `xroad_catalog_lister`. The migration **never creates roles**: all three must
exist with known passwords before the first start. No `CREATEROLE` attribute is required on any role.

The collector connects as `xroad_catalog` only for the migration and grants. All tables must be owned by
`xroad_catalog`; the restore in [6.3](#63-prepare-the-new-database) ensures this.

## 6. Migration Procedure

### 6.1 Pre-checks

1. **Profile.** `grep CATALOG_PROFILE /etc/xroad/xroad-catalog/catalog-profile.properties`. If it is `fi`, confirm
   that the organization/company data and endpoints are no longer needed — they do not exist in the new version
   ([3.3](#33-removed-functionality)).
2. **Consumers.** Inventory who calls the lister: SOAP and REST V1 consumers keep working with the legacy flag on;
   everyone should be pointed to V2 over time.
3. **Settings.** Copy everything under `/etc/xroad/xroad-catalog/` aside — the five `*.properties` files
   (`catalog-profile.properties`, `catalogdb-production.properties`, `collector-production.properties`,
   `lister-production.properties`, `version.properties`) and `application.conf`; they are the input to
   [4. Configuration Translation](#4-configuration-translation).
4. **Data at stake**, to calibrate the decision in [2](#2-decide-migrate-the-data-or-start-fresh). The packaged
   `pg_hba.conf` uses `peer` authentication for local connections:

   ```bash
   sudo -u postgres psql -d xroad_catalog
   ```

   ```sql
   SELECT 'member' AS table_name, count(*) FROM member WHERE removed IS NOT NULL
   UNION ALL SELECT 'subsystem', count(*) FROM subsystem WHERE removed IS NOT NULL
   UNION ALL SELECT 'service', count(*) FROM service WHERE removed IS NOT NULL;
   SELECT min(created) FROM member;
   ```

   Record the row counts as well; the restore in [6.3](#63-prepare-the-new-database) is checked against them:

   ```sql
   SELECT 'member' AS table_name, count(*) FROM member
   UNION ALL SELECT 'subsystem', count(*) FROM subsystem
   UNION ALL SELECT 'service', count(*) FROM service
   UNION ALL SELECT 'wsdl', count(*) FROM wsdl
   UNION ALL SELECT 'open_api', count(*) FROM open_api
   UNION ALL SELECT 'rest', count(*) FROM rest
   UNION ALL SELECT 'endpoint', count(*) FROM endpoint
   UNION ALL SELECT 'error_log', count(*) FROM error_log;
   ```

5. **PostgreSQL version.** Note the source version (`SELECT version();`). The target is PostgreSQL 16; a major-version
   jump is handled by `pg_dump` / `pg_restore` ([6.2](#62-backup), [6.3](#63-prepare-the-new-database)), never by
   copying the data directory.
6. **Time zone.** Note the RPM host's time zone (`timedatectl`). Run the new collector, lister and database in the
   same zone as the RPM host, otherwise historical and new timestamps disagree (see
   [Installation Guide, 6.6](xroad_catalog_installation_guide.md#66-time-zone-configuration)).

### 6.2 Backup

Stop the RPM collector so that no collection cycle writes during the dump, then take a full backup of the database and
the configuration. Keep the RPM lister running until cutover.

```bash
sudo systemctl stop xroad-catalog-collector
sudo -u postgres pg_dump -Fc xroad_catalog > xroad_catalog_pre_migration.dump
sudo tar czf xroad-catalog-config.tgz /etc/xroad/xroad-catalog/ /etc/xroad/configuration-anchor.xml
```

Check that the dump file is non-empty before continuing; a failed `pg_dump` leaves a zero-byte file behind.

Leave the RPM installation in place, installed but with the collector stopped, until the cutover has been verified.

### 6.3 Prepare the New Database

On the target PostgreSQL 16 server (a separate server from the RPM database), as a superuser, create the three roles
and an empty database owned by `xroad_catalog`. On a shared server, skip the `CREATE ROLE` statements for roles that
already exist. The passwords go into the container configuration:

```bash
psql -h <new-db-host> -U postgres -d postgres
```

```sql
CREATE ROLE xroad_catalog WITH LOGIN PASSWORD '<owner password>';
CREATE ROLE xroad_catalog_collector WITH
    LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT NOREPLICATION NOBYPASSRLS CONNECTION LIMIT -1
    PASSWORD '<collector password>';
CREATE ROLE xroad_catalog_lister WITH
    LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT NOREPLICATION NOBYPASSRLS CONNECTION LIMIT -1
    PASSWORD '<lister password>';
CREATE DATABASE xroad_catalog OWNER xroad_catalog ENCODING 'UTF8'
    LC_COLLATE 'en_US.UTF-8' LC_CTYPE 'en_US.UTF-8' TEMPLATE template0;  -- the locale the RPM scripts used
\connect xroad_catalog
ALTER SCHEMA public OWNER TO xroad_catalog;
```

Restore the dump so that every object is owned by `xroad_catalog`:

```bash
pg_restore -h <new-db-host> -U xroad_catalog -d xroad_catalog --no-owner --no-privileges \
    xroad_catalog_pre_migration.dump
```

Run these commands with client tools at the **target's** major version (16) rather than the source's; when the target
database is a container, the database image's own tools will do.

`--no-privileges` discards the old grants; the collector creates the required grants on first start. Verify the
restore: re-run the row-count query from [6.1](#61-pre-checks) step 4 — every count must match the source — and check
the table owners:

```bash
psql -h <new-db-host> -U xroad_catalog -d xroad_catalog
```

```sql
SELECT tablename, tableowner FROM pg_tables WHERE schemaname = 'public';   -- all owned by xroad_catalog
```

If the source ran the FI profile, the FI-profile tables can be dropped now or left in place.

### 6.4 Prepare the Container Configuration

Follow the [Installation Guide](xroad_catalog_installation_guide.md) with these migration-specific values:

* Collector: the translated settings from [4.1](#41-collector); `SPRING_LIQUIBASE_PASSWORD` set to the `xroad_catalog`
  password; `SPRING_DATASOURCE_PASSWORD` to the `xroad_catalog_collector` password.
* Lister: `SPRING_DATASOURCE_PASSWORD` set to the `xroad_catalog_lister` password;
  `XROAD_CATALOG_LEGACY_API_ENABLED=true` as long as V1 or SOAP consumers exist; the configuration anchor copied from
  the RPM host, where it is `root:xroad 0640`, made readable by the container user
  ([Installation Guide, 7.3](xroad_catalog_installation_guide.md#73-file-permissions)) and mounted.
* Both, and the database: the same `TZ` as the RPM host had.
* TLS material for the collector, if needed, per [4.3](#43-tls-keystore).
* When copying the Installation Guide's compose example, remove or empty
  `XROAD_CATALOG_INSTANCE_IGNORED_SUBSYSTEM_IDS_*` unless the removal described in [4.1](#41-collector) is intended.

Do not start the lister yet.

### 6.5 Start the Collector and Verify the Schema Migration

Start only the collector and follow its log:

```bash
docker compose up -d xroad-catalog-collector
docker compose logs -f xroad-catalog-collector
```

Liquibase adopts the existing schema: changesets whose objects already exist are recorded as `MARK_RAN`, the rest as
`EXECUTED`. The `UPDATE SUMMARY` block at the end of the log does not tell the two apart, so check `exectype` in
`databasechangelog`:

```bash
psql -h <new-db-host> -U xroad_catalog -d xroad_catalog
```

```sql
SELECT exectype, count(*) FROM databasechangelog GROUP BY 1;
SELECT id, filename, exectype FROM databasechangelog ORDER BY orderexecuted;
```

Both `EXECUTED` and `MARK_RAN` must be present, and no other value. The exact split is release-dependent, so compare
per changeset:

| Changeset                       | Expected `exectype`                                                                             |
|---------------------------------|-------------------------------------------------------------------------------------------------|
| `010-common-tables-*`           | `MARK_RAN` ("table exists")                                                                     |
| `011-common-sequences-*`        | `MARK_RAN`                                                                                      |
| `012-common-indexes-*`          | Mixed: `MARK_RAN` for indexes the RPM scripts created, `EXECUTED` for those new in this release |
| `014-collection-run-sequence`   | `MARK_RAN` — the table's `bigserial` `id` column already created the sequence                   |
| Every other changeset           | `EXECUTED` (`000-users-*`, `*-add-*`, `backfill-*`, `014-collection-run-table`, `015-*`)        |

If a `000-users-*` changeset halts with *"Role '...' must be created before startup"*, the role from
[6.3](#63-prepare-the-new-database) is missing.

Then verify the outcome in the data:

```sql
SELECT is_provider, count(*) FROM member GROUP BY 1;            -- true for members with services
SELECT service_type, count(*) FROM service GROUP BY 1;          -- SOAP / OPENAPI / REST; UNKNOWN without a live descriptor, e.g. removed
SELECT count(*) FROM active_member;                             -- non-removed members
SELECT count(*) FROM active_search_index;
```

Wait for the collector's readiness probe (`docker compose ps` shows *healthy*) and for the initial collection run to
finish — the log reports it, and `collection_run` gains a row. Whether that run collects anything depends on the
`fetch-run-unlimited` value carried over in [4.1](#41-collector): with `false` the collector only fetches between
`xroad-catalog.tasks.fetch-time-after-hour` and `fetch-time-before-hour`, and outside that window a cycle does nothing
and is still recorded as successful
([Installation Guide, 10](xroad_catalog_installation_guide.md#10-post-installation-checks)).

### 6.6 Start the Lister and Verify the APIs

```bash
docker compose up -d xroad-catalog-lister
```

Once healthy:

1. `curl http://<new-host>:8070/api/v2/heartbeat` returns `200`; `lastCollectionData` is populated after the first
   cycle.
2. Compare V1 responses from the new lister against the still-running old lister. The aggregate endpoints
   (`listSecurityServers`, `listDescriptors`, `getServiceStatistics`, `getServiceStatisticsCSV`,
   `getDistinctServiceStatistics`) are byte-identical, so a plain `diff` works:

   ```bash
   diff <(curl -s "http://<old-host>:8070/api/listSecurityServers") \
        <(curl -s "http://<new-host>:8070/api/listSecurityServers")
   diff <(curl -s "http://<old-host>:8070/api/getServiceStatistics?startDate=2026-01-01&endDate=2026-01-31") \
        <(curl -s "http://<new-host>:8070/api/getServiceStatistics?startDate=2026-01-01&endDate=2026-01-31")
   ```

   Identical statistics for past dates confirm the history was migrated. Pick a date range in which the old lister
   reports non-zero counts (start at the `min(created)` noted in [6.1](#61-pre-checks) step 4).

   The list endpoints must be compared semantically, not byte for byte: `getListOfServices`, `getListOfServicesCSV`,
   `getEndpoints` and SOAP `ListMembers` have no `ORDER BY`, so the same records come back in a different order on
   every call. For the JSON endpoints (`getEndpoints`, `getListOfServices`) sort every array at
   every depth; for the CSV variants sort the lines:

   ```bash
   deep='def deep: walk(if type == "array" then sort else . end); deep'
   ep="api/getEndpoints/<xRoadInstance>/<memberClass>/<memberCode>/<subsystemCode>/<serviceCode>"
   diff <(curl -s "http://<old-host>:8070/$ep" | jq -S "$deep") \
        <(curl -s "http://<new-host>:8070/$ep" | jq -S "$deep")

   csv="api/getListOfServicesCSV?startDate=2026-01-01&endDate=2026-01-31"
   diff <(curl -s "http://<old-host>:8070/$csv" | sort) \
        <(curl -s "http://<new-host>:8070/$csv" | sort)
   ```

   A `jq` error on either side leaves `diff` comparing two empty streams, so an error message means the check did not
   run, not that the bodies are equal.

   Expect `fetched` values to have moved on wherever the new collector has already run, and the heartbeat to differ in
   `appVersion`. `listErrors` and SOAP `GetErrors` may carry additional entries logged by the new collector; every entry
   the old lister returns must still be present.
3. `curl "http://<new-host>:8070/api/v2/search?q=<known member name>"` returns hits.
4. With SOAP consumers: `curl http://<new-host>:8070/ws/services.wsdl` serves the WSDL, and a request that worked
   against the RPM host returns the same response from the new one. Compare `ListMembers` element by element, not the
   bytes: its element order is not stable across a dump and restore.

### 6.7 Cutover

1. Stop and disable the RPM units:

   ```bash
   sudo systemctl stop xroad-catalog-lister
   sudo systemctl disable xroad-catalog-lister xroad-catalog-collector
   sudo systemctl stop xroad-confclient; sudo systemctl disable xroad-confclient   # if it was a separate service
   ```

2. Repoint the load balancer / Security Server service address / DNS to the new lister.
3. Inform SOAP consumers of the switch date so they can verify requests still work.
4. Keep the RPM host and the original database untouched for at least one full collection cycle — preferably a few
   days — before decommissioning.

### 6.8 Rollback

The original database is unchanged. To roll back:

1. Stop the containers (`docker compose down`).
2. `sudo systemctl enable --now xroad-catalog-collector xroad-catalog-lister` (and `xroad-confclient` if separate).
3. Repoint the load balancer back.

Data collected by the new collector between cutover and rollback is not transferred back; the RPM collector picks up
the current state of the ecosystem on its next cycle.

## 7. Fresh-Start Procedure

When the history is not needed ([2](#2-decide-migrate-the-data-or-start-fresh)):

1. Do the pre-checks ([6.1](#61-pre-checks)) and the backup ([6.2](#62-backup)).
2. Install per the [Installation Guide](xroad_catalog_installation_guide.md) against an empty database, using the
   translated configuration from [4](#4-configuration-translation).
3. Let one full collection cycle complete; verify `/api/v2/heartbeat` and, with the legacy flag on, `/api/heartbeat`.
4. Cut over and roll back exactly as in [6.7](#67-cutover) and [6.8](#68-rollback). Note that `externalId` values
   served by SOAP `GetWsdl` / `GetOpenAPI` differ from the RPM ones.
