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

| Area                 | RPM installation                                                                | Container installation                                                                                               |
|----------------------|---------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------|
| Packaging            | RPMs, systemd units, `/usr/lib/xroad-catalog/*.jar`                             | Images `niis/xroad-catalog-collector` and `niis/xroad-catalog-lister`                                                |
| Configuration        | `/etc/xroad/xroad-catalog/*.properties`, `application.conf`                     | Environment variables or a mounted `application.yaml`; key names changed, see [4](#4-configuration-translation)      |
| Schema management    | SQL scripts run once by the RPM `%post` scriptlet                               | Liquibase, run by the collector at every start; adopts the existing schema                                           |
| Database roles       | Everything connects as `xroad_catalog`                                          | `xroad_catalog` (owner, migrations only), `xroad_catalog_collector` (read/write), `xroad_catalog_lister` (read-only) |
| Configuration client | Separate `xroad-confclient` RPM and systemd unit, `/etc/xroad/conf.d/local.ini` | Embedded in the lister, configured with `xroad.configuration-client.*` keys                                          |
| APIs                 | REST V1 and SOAP                                                                | REST V2 (new); REST V1 and SOAP retained unchanged behind `xroad-catalog.legacy-api.enabled`, off by default         |
| FI profile           | Organization / company collection and endpoints                                 | Removed entirely                                                                                                     |
| Logs                 | journald                                                                        | Container stdout                                                                                                     |

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

Migrated data arrives without `collection_run` history (a new table), so the V2 heartbeat's `lastCollectionData` and
the `currentRun` fields are populated only after the first collection cycle of the new installation.

## 3. API Compatibility

### 3.1 REST V1

The V1 REST API is retained unchanged: paths, parameters, response fields and timestamp formats are identical.
It is **disabled by default** and answers `404` until `xroad-catalog.legacy-api.enabled=true` is set on the lister. The
V1 API is deprecated; plan to move consumers to the V2 API described in the
[User Guide](xroad_catalog_user_guide.md#33-rest-api-v2).

### 3.2 SOAP

The SOAP interface is gated by the same `xroad-catalog.legacy-api.enabled` flag and is served at `/ws/*` as before
(`POST /ws/ListMembers`, `GET /ws/services.wsdl`, ...). Differences from the RPM version:

* `GetOrganizations`, `HasOrganizationChanged`, `GetCompanies` and `HasCompanyChanged` were removed with the FI profile;
  calling them returns an `Unknown service` SOAP fault.
* The `xrd:client` and `xrd:service` SOAP headers are now mandatory, and the operation is selected by the
  `id:serviceCode` header. Requests that pass through a Security Server always carry them; a client calling the lister
  directly without X-Road headers must add them.
* The served WSDL no longer lists the four operations above, and its `soap:address` is the fixed
  `http://localhost:8070/ws` rather than the address the request came in on.

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

| RPM property                                   | New property                                                         | Environment variable                                   |
|------------------------------------------------|----------------------------------------------------------------------|--------------------------------------------------------|
| `xroad-catalog.xroad-instance`                 | `xroad-catalog.target.xroad-instance`                                | `XROAD_CATALOG_TARGET_XROAD_INSTANCE`                  |
| `xroad-catalog.member-class`                   | `xroad-catalog.target.member-class`                                  | `XROAD_CATALOG_TARGET_MEMBER_CLASS`                    |
| `xroad-catalog.member-code`                    | `xroad-catalog.target.member-code`                                   | `XROAD_CATALOG_TARGET_MEMBER_CODE`                     |
| `xroad-catalog.subsystem-code`                 | `xroad-catalog.target.subsystem-code`                                | `XROAD_CATALOG_TARGET_SUBSYSTEM_CODE`                  |
| `xroad-catalog.security-server-host`           | `xroad-catalog.urls.security-server-host`                            | `XROAD_CATALOG_URLS_SECURITY_SERVER_HOST`              |
| `xroad-catalog.list-clients-host`              | `xroad-catalog.urls.list-clients-host`                               | `XROAD_CATALOG_URLS_LIST_CLIENTS_HOST`                 |
| `xroad-catalog.webservices-endpoint`           | `xroad-catalog.urls.webservices-endpoint`                            | `XROAD_CATALOG_URLS_WEBSERVICES_ENDPOINT`              |
| `xroad-catalog.fetch-wsdl-pool-size`           | `xroad-catalog.pool-size.fetch-wsdl`                                 | `XROAD_CATALOG_POOL_SIZE_FETCH_WSDL`                   |
| `xroad-catalog.fetch-openapi-pool-size`        | `xroad-catalog.pool-size.fetch-openapi`                              | `XROAD_CATALOG_POOL_SIZE_FETCH_OPENAPI`                |
| `xroad-catalog.fetch-rest-pool-size`           | `xroad-catalog.pool-size.fetch-rest`                                 | `XROAD_CATALOG_POOL_SIZE_FETCH_REST`                   |
| `xroad-catalog.list-methods-pool-size`         | `xroad-catalog.pool-size.list-methods`                               | `XROAD_CATALOG_POOL_SIZE_LIST_METHODS`                 |
| `xroad-catalog.error-log-length-in-days`       | `xroad-catalog.log-storage.error-log-length-in-days`                 | `XROAD_CATALOG_LOG_STORAGE_ERROR_LOG_LENGTH_IN_DAYS`   |
| `xroad-catalog.flush-log-time-after-hour`      | `xroad-catalog.log-storage.flush-log-time-after-hour`                | `XROAD_CATALOG_LOG_STORAGE_FLUSH_LOG_TIME_AFTER_HOUR`  |
| `xroad-catalog.flush-log-time-before-hour`     | `xroad-catalog.log-storage.flush-log-time-before-hour`               | `XROAD_CATALOG_LOG_STORAGE_FLUSH_LOG_TIME_BEFORE_HOUR` |
| `xroad-catalog.collector-interval-min`         | `xroad-catalog.tasks.collector-interval-min`                         | `XROAD_CATALOG_TASKS_COLLECTOR_INTERVAL_MIN`           |
| `xroad-catalog.fetch-run-unlimited`            | `xroad-catalog.tasks.fetch-run-unlimited`                            | `XROAD_CATALOG_TASKS_FETCH_RUN_UNLIMITED`              |
| `xroad-catalog.fetch-time-after-hour`          | `xroad-catalog.tasks.fetch-time-after-hour`                          | `XROAD_CATALOG_TASKS_FETCH_TIME_AFTER_HOUR`            |
| `xroad-catalog.fetch-time-before-hour`         | `xroad-catalog.tasks.fetch-time-before-hour`                         | `XROAD_CATALOG_TASKS_FETCH_TIME_BEFORE_HOUR`           |
| `spring.datasource.url`                        | `spring.datasource.url` (append `?socketTimeout=60`)                 | `SPRING_DATASOURCE_URL`                                |
| `spring.datasource.username` (`xroad_catalog`) | `spring.datasource.username` (`xroad_catalog_collector`)             | `SPRING_DATASOURCE_USERNAME`                           |
| `spring.datasource.password`                   | `spring.datasource.password` (password of `xroad_catalog_collector`) | `SPRING_DATASOURCE_PASSWORD`                           |
| —                                              | `spring.liquibase.password` (password of `xroad_catalog`)            | `SPRING_LIQUIBASE_PASSWORD`                            |

New properties worth setting: `xroad-catalog.instance.ignored-subsystem-ids` (e.g. the management subsystem) and the
`xroad-catalog.tasks.client-*-timeout-seconds` limits.

### 4.2 Lister and Configuration Client

From `lister-production.properties`:

| RPM property                                   | New property                                                      | Environment variable               |
|------------------------------------------------|-------------------------------------------------------------------|------------------------------------|
| `xroad-catalog.shared-params-file`             | unchanged                                                         | `XROAD_CATALOG_SHARED_PARAMS_FILE` |
| `spring.datasource.url`                        | unchanged (append `?socketTimeout=60`)                            | `SPRING_DATASOURCE_URL`            |
| `spring.datasource.username` (`xroad_catalog`) | `spring.datasource.username` (`xroad_catalog_lister`)             | `SPRING_DATASOURCE_USERNAME`       |
| `spring.datasource.password`                   | `spring.datasource.password` (password of `xroad_catalog_lister`) | `SPRING_DATASOURCE_PASSWORD`       |
| `springdoc.packagesToScan`                     | dropped                                                           | —                                  |
| —                                              | `xroad-catalog.legacy-api.enabled=true` (to keep V1 and SOAP)     | `XROAD_CATALOG_LEGACY_API_ENABLED` |

The separate `xroad-confclient` service is gone; the lister downloads the global configuration itself. What used to be
in `/etc/xroad/conf.d/local.ini` becomes `xroad.configuration-client.*` properties (see the
[lister README](../xroad-catalog-lister/README.md#optional-configurations-for-configuration-client-features)).
Drop `proxy-configuration-backup-cron`. The configuration anchor is mounted into the lister container at
`/etc/xroad/configuration-anchor.xml`.

### 4.3 TLS Keystore

`xroad-catalog.ssl-keystore`, `xroad-catalog.ssl-keystore-password` and the host CA bundle (`update-ca-trust`) are
not used. In the new version, the keystore and a truststore holding the Security Server's certificate are mounted into the
collector container and activated with `javax.net.ssl.*` JVM flags in a mounted JVM options file — see
[Installation Guide, 7.2](xroad_catalog_installation_guide.md#72-tls-keystore-and-truststore-collector). The existing
`/etc/xroad/xroad-catalog/keystore` file can be reused if it is in PKCS12 format (`keytool -importkeystore` converts a
JKS file).

### 4.4 Settings Without an Equivalent

`catalog-profile.properties` (`CATALOG_PROFILE`), every `fetch-organizations*` / `fetch-companies*` setting,
`application.conf` (Akka), `fetch-wsdl-host` / `fetch-openapi-host` (already unused in RPM), and the `sshtest` profile.
The `xroad-catalog` system user and `/var/log/xroad/` are not used by the containers.

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

A migrated database cannot be used by the RPM version. **Always migrate into a copy** of the database and keep the original for
rollback.

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
2. **Consumers.** Inventory who calls the lister: SOAP and REST V1 consumers keep working with the legacy flag on
   (SOAP callers that bypass a Security Server must send the X-Road headers, [3.2](#32-soap)); everyone should be
   pointed to V2 over time.
3. **Settings.** Copy the four `/etc/xroad/xroad-catalog/*.properties` files and `application.conf` aside; they are the
   input to [4. Configuration Translation](#4-configuration-translation).
4. **Data at stake**, to calibrate the decision in [2](#2-decide-migrate-the-data-or-start-fresh):

   ```sql
   SELECT count(*) FROM member WHERE removed IS NOT NULL;
   SELECT min(created) FROM member;
   ```

5. **PostgreSQL version.** Note the source version (`SELECT version();`). The target is PostgreSQL 16; a major-version
   jump is handled by `pg_dump` / `pg_restore` ([6.2](#62-backup), [6.3](#63-prepare-the-new-database)), never by
   copying the data directory.
6. **Time zone.** Note the RPM host's time zone (`timedatectl`). Timestamps are stored as local wall-clock time; the
   new collector, lister and database must run in the same zone, otherwise historical and new timestamps disagree
   (see [Installation Guide, 6.6](xroad_catalog_installation_guide.md#66-time-zone-configuration)).

### 6.2 Backup

Stop the RPM collector so that no collection cycle writes during the dump, then take a full backup of the database and
the configuration. Keep the RPM lister running until cutover.

```bash
sudo systemctl stop xroad-catalog-collector
pg_dump -Fc -U postgres xroad_catalog > xroad_catalog_pre_migration.dump
sudo tar czf xroad-catalog-config.tgz /etc/xroad/xroad-catalog/ /etc/xroad/configuration-anchor.xml
```

Leave the RPM installation in place, installed but with the collector stopped, until the cutover has been verified.

### 6.3 Prepare the New Database

On the target PostgreSQL 16 server (a separate server from the RPM database), as a superuser, create the three roles
and an empty database owned by `xroad_catalog`. On a shared server, skip the `CREATE ROLE` statements for roles that
already exist. The passwords go into the container configuration:

```sql
CREATE ROLE xroad_catalog WITH LOGIN PASSWORD '<owner password>';
CREATE ROLE xroad_catalog_collector WITH
    LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT NOREPLICATION NOBYPASSRLS CONNECTION LIMIT -1
    PASSWORD '<collector password>';
CREATE ROLE xroad_catalog_lister WITH
    LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT NOREPLICATION NOBYPASSRLS CONNECTION LIMIT -1
    PASSWORD '<lister password>';
CREATE DATABASE xroad_catalog OWNER xroad_catalog ENCODING 'UTF8';
\connect xroad_catalog
ALTER SCHEMA public OWNER TO xroad_catalog;
```

Restore the dump so that every object is owned by `xroad_catalog`:

```bash
pg_restore -h <new-db-host> -U xroad_catalog -d xroad_catalog --no-owner --no-privileges \
    xroad_catalog_pre_migration.dump
```

`--no-privileges` discards the old grants; the collector creates the required grants on first start. Verify the
restore:

```sql
SELECT count(*) FROM member;                       -- matches the source
SELECT tablename, tableowner FROM pg_tables WHERE schemaname = 'public';   -- all owned by xroad_catalog
```

If the source ran the FI profile, the FI-profile tables can be dropped now or left in place.

### 6.4 Prepare the Container Configuration

Follow the [Installation Guide](xroad_catalog_installation_guide.md) with these migration-specific values:

* Collector: the translated settings from [4.1](#41-collector); `SPRING_LIQUIBASE_PASSWORD` set to the `xroad_catalog`
  password; `SPRING_DATASOURCE_PASSWORD` to the `xroad_catalog_collector` password.
* Lister: `SPRING_DATASOURCE_PASSWORD` set to the `xroad_catalog_lister` password;
  `XROAD_CATALOG_LEGACY_API_ENABLED=true` as long as V1 or SOAP consumers exist; the configuration anchor copied from
  the RPM host and mounted.
* Both: the same `TZ` as the RPM host had.
* TLS material for the collector per [4.3](#43-tls-keystore).

Do not start the lister yet.

### 6.5 Start the Collector and Verify the Schema Migration

Start only the collector and follow its log. The Liquibase summary in the log must show the adoption pattern:

```bash
docker compose up -d xroad-catalog-collector
docker compose logs -f xroad-catalog-collector
```

| Changeset                                                    | Expected outcome            |
|--------------------------------------------------------------|-----------------------------|
| `000-users-collector-grants`, `000-users-lister-grants`      | Run                         |
| `010-common-tables-*` (eight createTable changesets)         | `MARK_RAN` ("table exists") |
| `010-member-add-is-provider`, `010-service-add-service-type` | Run                         |
| `011-common-sequences-*`                                     | `MARK_RAN`                  |
| `012-common-indexes-*`                                       | Run                         |
| `backfill-member-is-provider`, `backfill-service-type`       | Run                         |
| `014-*` (`collection_run`), `015-*` (views and view grants)  | Run                         |

If a `000-users-*` changeset halts with *"Role '...' must be created before startup"*, the role from
[6.3](#63-prepare-the-new-database) is missing.

Then verify the outcome in the database:

```sql
SELECT is_provider, count(*) FROM member GROUP BY 1;            -- true for members with services
SELECT service_type, count(*) FROM service GROUP BY 1;          -- SOAP / OPENAPI / REST; UNKNOWN only for services without a descriptor
SELECT count(*) FROM active_member;                             -- non-removed members
SELECT count(*) FROM active_search_index;
SELECT id, filename, exectype FROM databasechangelog ORDER BY orderexecuted;
```

Wait for the collector's readiness probe (`docker compose ps` shows *healthy*) and, if the current time is inside the
fetch window, for the first collection cycle to finish — the log reports it, and `collection_run` gains a row.

### 6.6 Start the Lister and Verify the APIs

```bash
docker compose up -d xroad-catalog-lister
```

Once healthy:

1. `curl http://<new-host>:8070/api/v2/heartbeat` returns `200`; `lastCollectionData` is populated after the first
   cycle.
2. Compare a V1 response from the new lister against the still-running old lister — the bodies must be identical,
   apart from data changed by the collection cycle in between:

   ```bash
   diff <(curl -s "http://<old-host>:8070/api/listSecurityServers") \
        <(curl -s "http://<new-host>:8070/api/listSecurityServers")
   diff <(curl -s "http://<old-host>:8070/api/getServiceStatistics?startDate=2026-01-01&endDate=2026-01-31") \
        <(curl -s "http://<new-host>:8070/api/getServiceStatistics?startDate=2026-01-01&endDate=2026-01-31")
   ```

   Identical statistics for past dates confirm the history was migrated.
3. `curl "http://<new-host>:8070/api/v2/search?q=<known member name>"` returns hits.
4. With SOAP consumers: `curl http://<new-host>:8070/ws/services.wsdl` serves the WSDL, and a request that worked
   against the RPM host returns the same response from the new one.

### 6.7 Cutover

1. Stop and disable the RPM units:

   ```bash
   sudo systemctl stop xroad-catalog-lister
   sudo systemctl disable xroad-catalog-lister xroad-catalog-collector
   sudo systemctl stop xroad-confclient && sudo systemctl disable xroad-confclient   # if it was a separate service
   ```

2. Repoint the load balancer / Security Server service address / DNS to the new lister.
3. Inform SOAP consumers of the switch date; client changes might be required due to the SOAP header requirements
   ([3.2](#32-soap)).
4. Keep the RPM host and the original database untouched for at least one full collection cycle — preferably a few
   days — before decommissioning.

### 6.8 Rollback

The original database is unchanged. To roll back:

1. Stop the containers.
2. `sudo systemctl start xroad-catalog-collector xroad-catalog-lister` (and `xroad-confclient` if separate).
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
