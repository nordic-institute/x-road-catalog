# X-Road Catalog Installation Guide
Version: 2.0.0
Doc. ID: IG-XRDCAT

---

## Version history <!-- omit in toc -->
| Date       | Version | Description                                                                              | Author                            |
|------------|---------|------------------------------------------------------------------------------------------|-----------------------------------|
| 22.03.2023 | 1.0.0   | Export installation-related parts from the X-Road Catalog User Guide                     | Petteri Kivimäki                  |
| 16.08.2023 | 1.1.0   | Add instructions to install and configure the `xroad-conflient` module                   | Petteri Kivimäki                  |
| 09.09.2023 | 1.2.0   | Remove instructions to install the `xroad-conflient` module manually                     | Petteri Kivimäki                  |
| 24.09.2023 | 1.3.0   | Add instructions to disable the automatic backup job run by the `xroad-conflient` module | Petteri Kivimäki                  |
| 10.06.2024 | 1.3.1   | Add information about default values for configurable properties                         | Raido Kaju                        |
| 14.06.2024 | 1.3.2   | Update information about company and organization task properties                        | Raido Kaju                        |
| 14.08.2024 | 1.3.3   | Update information about Initial Configuration                                           | Mohamed Elbeltagy                 |
| 10.02.2026 | 1.3.4   | Remove FI-profile related documentation                                                  | Pauline Dimmek, Mohamed Elbeltagy |
| 25.08.2026 | 2.0.0   | Rewrite for the container-based distribution                                             | Raido Kaju                        |

## Table of Contents <!-- omit in toc -->

<!-- toc -->
<!-- vim-markdown-toc GFM -->

* [License](#license)
* [1. Introduction](#1-introduction)
  * [1.1 Target Audience](#11-target-audience)
  * [1.2 Distribution Format](#12-distribution-format)
* [2. Deployment Model](#2-deployment-model)
  * [2.1 Components and Network Flows](#21-components-and-network-flows)
  * [2.2 Security Posture and Network Exposure](#22-security-posture-and-network-exposure)
  * [2.3 Trust Assumptions](#23-trust-assumptions)
* [3. Prerequisites](#3-prerequisites)
* [4. Container Images](#4-container-images)
* [5. Database Setup](#5-database-setup)
  * [5.1 Database Roles](#51-database-roles)
  * [5.2 Schema Management](#52-schema-management)
* [6. Configuration](#6-configuration)
  * [6.1 Configuration Mechanisms](#61-configuration-mechanisms)
  * [6.2 Environment Variable Naming](#62-environment-variable-naming)
  * [6.3 Collector Configuration](#63-collector-configuration)
  * [6.4 Lister Configuration](#64-lister-configuration)
  * [6.5 JVM Options](#65-jvm-options)
  * [6.6 Time Zone Configuration](#66-time-zone-configuration)
  * [6.7 Collector Timeouts](#67-collector-timeouts)
* [7. Volumes and Mounted Files](#7-volumes-and-mounted-files)
  * [7.1 Configuration Anchor (Lister)](#71-configuration-anchor-lister)
  * [7.2 TLS Keystore and Truststore (Collector)](#72-tls-keystore-and-truststore-collector)
  * [7.3 File Permissions](#73-file-permissions)
* [8. Ports](#8-ports)
* [9. Running the Services](#9-running-the-services)
  * [9.1 Startup Order](#91-startup-order)
  * [9.2 Docker Compose Example](#92-docker-compose-example)
  * [9.3 Resource Limits and Stop Timeout](#93-resource-limits-and-stop-timeout)
* [10. Post-Installation Checks](#10-post-installation-checks)
* [11. Monitoring](#11-monitoring)
* [12. Logs](#12-logs)
* [13. Backup and Restore](#13-backup-and-restore)
* [14. Upgrading](#14-upgrading)
* [15. Search Performance](#15-search-performance)

<!-- vim-markdown-toc -->
<!-- tocstop -->

## License

This document is licensed under the Creative Commons Attribution-ShareAlike 3.0 Unported License. To view a copy of this license, visit http://creativecommons.org/licenses/by-sa/3.0/.

## 1. Introduction

X-Road Catalog is an [X-Road](https://github.com/nordic-institute/X-Road/) extension that collects information on
members, subsystems and services from an X-Road ecosystem and provides a REST interface to access the data.

X-Road Catalog consists of two deployable services:

* X-Road Catalog Collector
  * Collects information from the X-Road ecosystem through a Security Server and stores it to a database.
  * Owns the database schema: it runs the Liquibase migrations at startup.
* X-Road Catalog Lister
  * Provides a REST interface offering the information collected by the collector.
  * Embeds the X-Road configuration client, which downloads the global configuration of the ecosystem.

### 1.1 Target Audience

The intended audience of this guide are X-Road Operators responsible for managing and configuring the X-Road Central
Server and related services. The document is intended for readers with a good knowledge of Linux server management,
Docker, computer networks, and the X-Road principles.

### 1.2 Distribution Format

X-Road Catalog is distributed as container images only. Pre-existing installations migrating from RPM packages should follow the
[Migration Guide](xroad_catalog_migration_guide.md) in addition to this document.

## 2. Deployment Model

### 2.1 Components and Network Flows

![X-Road Catalog production](../img/xroad_catalog_production.png)

A deployment consists of the two application containers and a PostgreSQL database:

| Component  | Talks to                                                                                                                   | Listens on                                    |
|------------|----------------------------------------------------------------------------------------------------------------------------|-----------------------------------------------|
| Collector  | PostgreSQL (read/write, plus schema migration); the Security Server (`listClients`, `listMethods`, WSDL/OpenAPI downloads) | Management port `8090` (health, metrics) only |
| Lister     | PostgreSQL (read-only); the Central Server(s) named by the configuration anchor (global configuration download)            | API port `8070`; management port `8090`       |
| PostgreSQL | —                                                                                                                          | `5432`                                        |

The collector connects to the Security Server as the catalog's own X-Road subsystem (see
[6.3 Collector Configuration](#63-collector-configuration)); that subsystem must be registered on the Security
Server. The lister needs no Security Server: it reads the ecosystem's
shared parameters from the global configuration it downloads itself.

### 2.2 Security Posture and Network Exposure

> [!IMPORTANT]
> **The lister provides no authentication, no authorization, no TLS termination and no rate limiting — by design.**
> Its API port must never be exposed directly to an untrusted network. Every request that reaches the lister is
> answered.

The intended deployment places the lister behind one (or more) of:

* **A Security Server**, publishing the catalog as an X-Road service. This is the primary model: X-Road provides
  authentication, authorization and transport security, and the consumer sees a regular X-Road service.
* **A reverse proxy** (TLS termination, client authentication, rate limiting, request logging) when the catalog is
  offered outside X-Road, e.g. as a public service directory.
* **Network-level restriction** (firewall, private network) limiting the callers to the components above.

The management port (`8090`) of both services is meant for the container runtime's health checks and for a metrics
scraper only. Keep it internal to the container network; do not publish it. Only the `health`
(with its `liveness` / `readiness` groups) and `prometheus` endpoints are exposed there.

The collector has no API at all; it only serves the management port.

The database should accept connections from the two application containers only, and each application must connect
with its own role ([5.1 Database Roles](#51-database-roles)): the lister's role is read-only, so a compromise of the
lister cannot alter collected data.

### 2.3 Trust Assumptions

The deployment relies on the following trust assumptions:

* **The collector trusts what the Security Server tells it.** Service identifiers and WSDL/OpenAPI URLs returned by
  the Security Server are downloaded without further validation of their targets.
* **Collection errors are stored and served verbatim.** When a fetch fails, the collector records the error message
  it received, which can include the internal URL of the service that failed. These records are served by the V2
  `/api/v2/browse/**/errors` endpoints and, when the legacy API is enabled, by V1 `/api/listErrors` and SOAP
  `GetErrors`. A deployment that offers the catalog to parties who must not learn internal service addresses should
  restrict or filter those endpoints at the proxy or Security Server layer.

## 3. Prerequisites

* **Docker Engine** with Docker Compose.
* **PostgreSQL 16** (or newer). Either a managed database or a suitable `postgres` container image.
* Network access from the collector to the Security Server's client-side port (`8080` for HTTP, `8443` for HTTPS) and
  from the lister to the Central Server's global configuration download address (HTTP `80` or HTTPS `443`).
* The **configuration anchor** file of the X-Road ecosystem, obtained from the X-Road operator or downloaded from
  the Central Server.
* An X-Road **subsystem for the catalog** registered on the Security Server that the collector uses.

## 4. Container Images

The images are published to Docker Hub:

| Image                          | Module    |
|--------------------------------|-----------|
| `niis/xroad-catalog-collector` | Collector |
| `niis/xroad-catalog-lister`    | Lister    |

Images are tagged with the X-Road Catalog release version (`niis/xroad-catalog-lister:<VERSION>`). Pin an explicit
version tag in production. The collector and lister must run the same version.

Properties common to both images:

* The application runs as the non-root user `xroad`.
* The image has no entrypoint other than `/entrypoint.sh`, see [6.5 JVM Options](#65-jvm-options).
* A `HEALTHCHECK` against the readiness probe on the management port is built in, see [11. Monitoring](#11-monitoring).

## 5. Database Setup

Creating the database and its roles is the operator's responsibility — the application only manages the schema
*inside* an existing database.

### 5.1 Database Roles

Three roles take part in a deployment:

| Role                      | Used by                        | Privileges                                                   |
|---------------------------|--------------------------------|--------------------------------------------------------------|
| `xroad_catalog`           | Collector, for Liquibase only  | Owner of the database and every object in it                 |
| `xroad_catalog_collector` | Collector, for the application | Read/write on all tables, sequences and functions            |
| `xroad_catalog_lister`    | Lister                         | `SELECT` on all tables and views, `EXECUTE` on all functions |

The role names are configurable (see the `spring.liquibase.parameters.users.*` properties in
[6.3 Collector Configuration](#63-collector-configuration)); the defaults are used throughout this guide.

Create the database owner, the two application roles and the database before the first start. As a PostgreSQL
superuser:

```sql
CREATE ROLE xroad_catalog WITH LOGIN PASSWORD '<owner password>';
CREATE ROLE xroad_catalog_collector WITH
    LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT NOREPLICATION NOBYPASSRLS CONNECTION LIMIT -1
    PASSWORD '<collector password>';
CREATE ROLE xroad_catalog_lister WITH
    LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT NOREPLICATION NOBYPASSRLS CONNECTION LIMIT -1
    PASSWORD '<lister password>';
CREATE DATABASE xroad_catalog OWNER xroad_catalog ENCODING 'UTF8';
```

Each application keeps a pool of 10 connections by default (`spring.datasource.hikari.maximum-pool-size` in
[6.3](#63-collector-configuration) and [6.4](#64-lister-configuration)), so the two application roles need about 20 of
PostgreSQL's default 100 `max_connections`. The roles deliberately have no connection limit: after a host crash the
previous instance's connections may linger until TCP keepalive expires, and a restart must not be refused meanwhile.

The owner role needs no `SUPERUSER` or `CREATEROLE` attribute: ownership of the database is sufficient for the
migrations and for granting the application roles their privileges. The two application roles must exist before the
collector starts — the collector refuses to start, naming the missing role, if one of them does not.

On PostgreSQL 15 and newer the `public` schema is no longer writable by every role, so also make the owner the schema
owner (as superuser, connected to the new database):

```sql
ALTER SCHEMA public OWNER TO xroad_catalog;
```

### 5.2 Schema Management

The **collector** creates and evolves the schema with [Liquibase](https://www.liquibase.org/) at every startup,
connecting as `spring.liquibase.user` (`xroad_catalog`). The migration includes granting the application roles their
privileges (the Liquibase `users` context, enabled by default). The lister never modifies the schema; it needs the
schema to exist, so it must not be started before the collector's first start has completed (see
[9.1 Startup Order](#91-startup-order)).

The collector logs a summary of the applied changesets at each start (`spring.liquibase.show-summary: verbose`).

## 6. Configuration

### 6.1 Configuration Mechanisms

Each image ships a packaged `application.yaml` with the application defaults. Every value in it can be overridden in
either of two ways, and the two can be combined:

* **Environment variables** — the recommended way for the small number of installation-specific values. The examples
  in this guide use them.
* **A mounted configuration file** at `/app/config/application.yaml` (Spring Boot's default external configuration
  location inside the container). Mount it read-only; nothing in the image writes configuration files. A mounted file
  is the right choice when a key has no packaged default or when list-valued keys become unwieldy as environment
  variables.

The complete configuration reference of each module, including the *fixed* values that a mounted file must repeat
unchanged, is in the module READMEs:

* [X-Road Catalog Collector — Configuration](../xroad-catalog-collector/README.md#configuration)
* [X-Road Catalog Lister — Configuration](../xroad-catalog-lister/README.md#configuration)

> [!NOTE]
> Do not activate the `dev` Spring profile in a deployment. It repoints ports and data sources for local
> development and enables verbose logging. For more verbose logging in a deployment, set `LOGGING_LEVEL_*`
> environment variables instead.

### 6.2 Environment Variable Naming

Standard Spring Boot relaxed binding applies to the `spring.*`, `server.*`, `management.*` and `logging.*` keys:
uppercase the key and replace `.` and `-` with `_`, for example:

* `spring.datasource.url` → `SPRING_DATASOURCE_URL`
* `management.endpoint.health.show-components` → `MANAGEMENT_ENDPOINT_HEALTH_SHOW_COMPONENTS`

For the application's own `xroad.*` and `xroad-catalog.*` keys the same rule applies; a literal `_` already present in
the key survives, for example:

* `xroad-catalog.target.xroad-instance` → `XROAD_CATALOG_TARGET_XROAD_INSTANCE`
* `xroad.configuration-client.global_conf_tls_cert_verification` →
  `XROAD_CONFIGURATION_CLIENT_GLOBAL_CONF_TLS_CERT_VERIFICATION`

List-valued keys take an index suffix: `xroad-catalog.instance.ignored-subsystem-ids[0]` →
`XROAD_CATALOG_INSTANCE_IGNORED_SUBSYSTEM_IDS_0`.

> [!NOTE]
> Only `xroad.*` keys that have a packaged default in the lister's `application.yaml` are passed on to the embedded
> X-Road configuration client. A key without a packaged default cannot be introduced via an environment variable —
> set it in a mounted configuration file instead.

### 6.3 Collector Configuration

The values below have no usable default and must be provided. The table lists the property name and the
corresponding environment variable.

| Property                                  | Environment variable                      | Value                                                                                                                           |
|-------------------------------------------|-------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------|
| `spring.datasource.url`                   | `SPRING_DATASOURCE_URL`                   | `jdbc:postgresql://<db-host>:5432/xroad_catalog?socketTimeout=60` — keep the `socketTimeout`, see [6.7](#67-collector-timeouts) |
| `spring.datasource.password`              | `SPRING_DATASOURCE_PASSWORD`              | Password of `xroad_catalog_collector`                                                                                           |
| `spring.liquibase.password`               | `SPRING_LIQUIBASE_PASSWORD`               | Password of the database owner `xroad_catalog`                                                                                  |
| `xroad-catalog.target.xroad-instance`     | `XROAD_CATALOG_TARGET_XROAD_INSTANCE`     | Instance identifier of the X-Road ecosystem, e.g. `FI`                                                                          |
| `xroad-catalog.target.member-class`       | `XROAD_CATALOG_TARGET_MEMBER_CLASS`       | Member class of the catalog's own subsystem                                                                                     |
| `xroad-catalog.target.member-code`        | `XROAD_CATALOG_TARGET_MEMBER_CODE`        | Member code of the catalog's own subsystem                                                                                      |
| `xroad-catalog.target.subsystem-code`     | `XROAD_CATALOG_TARGET_SUBSYSTEM_CODE`     | Subsystem code of the catalog's own subsystem                                                                                   |
| `xroad-catalog.urls.security-server-host` | `XROAD_CATALOG_URLS_SECURITY_SERVER_HOST` | URL of the Security Server client port, e.g. `http://ss.example.org:8080/` or `https://ss.example.org:8443/`                    |

Commonly adjusted optional values:

| Property                                               | Environment variable                                   | Default                   | Purpose                                                                                                                                                                                          |
|--------------------------------------------------------|--------------------------------------------------------|---------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `spring.datasource.username`                           | `SPRING_DATASOURCE_USERNAME`                           | `xroad_catalog_collector` | Application role name, if renamed                                                                                                                                                                |
| `spring.liquibase.user`                                | `SPRING_LIQUIBASE_USER`                                | `xroad_catalog`           | Database owner role name, if renamed                                                                                                                                                             |
| `spring.liquibase.parameters.users.collector.username` | `SPRING_LIQUIBASE_PARAMETERS_USERS_COLLECTOR_USERNAME` | `xroad_catalog_collector` | Role that is granted read/write privileges; must equal the collector's `spring.datasource.username`                                                                                              |
| `spring.liquibase.parameters.users.lister.username`    | `SPRING_LIQUIBASE_PARAMETERS_USERS_LISTER_USERNAME`    | `xroad_catalog_lister`    | Role that is granted read-only privileges; must equal the lister's `spring.datasource.username`                                                                                                  |
| `xroad-catalog.instance.ignored-subsystem-ids`         | `XROAD_CATALOG_INSTANCE_IGNORED_SUBSYSTEM_IDS_<n>`     | empty                     | Subsystems to skip, as `INSTANCE:CLASS:CODE:SUBSYSTEM`; typically the management subsystem                                                                                                       |
| `xroad-catalog.tasks.collector-interval-min`           | `XROAD_CATALOG_TASKS_COLLECTOR_INTERVAL_MIN`           | `20`                      | Minutes between collection cycles                                                                                                                                                                |
| `xroad-catalog.tasks.fetch-run-unlimited`              | `XROAD_CATALOG_TASKS_FETCH_RUN_UNLIMITED`              | `false`                   | `true` to collect around the clock instead of only between the `fetch-time-*` hours                                                                                                              |
| `xroad-catalog.tasks.fetch-time-after-hour`            | `XROAD_CATALOG_TASKS_FETCH_TIME_AFTER_HOUR`            | `3`                       | Start hour of the daily collection window (local time of the container, see [6.6](#66-time-zone-configuration))                                                                                  |
| `xroad-catalog.tasks.fetch-time-before-hour`           | `XROAD_CATALOG_TASKS_FETCH_TIME_BEFORE_HOUR`           | `4`                       | End hour of the daily collection window (local time of the container, see [6.6](#66-time-zone-configuration))                                                                                    |
| `xroad-catalog.log-storage.error-log-length-in-days`   | `XROAD_CATALOG_LOG_STORAGE_ERROR_LOG_LENGTH_IN_DAYS`   | `90`                      | Retention of collection error records                                                                                                                                                            |
| `spring.datasource.hikari.maximum-pool-size`           | `SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE`           | `10`                      | Database connection pool size. Raise when `hikaricp_connections_pending` stays above zero or `hikaricp_connections_timeout_total` increments in the Prometheus output, see [11](#11-monitoring). |

The full list, including the worker pool sizes and the log-flush window, is in the
[collector README](../xroad-catalog-collector/README.md#optional-configurations).

### 6.4 Lister Configuration

| Property                           | Environment variable               | Value                                                                                                                                                      |
|------------------------------------|------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `spring.datasource.url`            | `SPRING_DATASOURCE_URL`            | `jdbc:postgresql://<db-host>:5432/xroad_catalog?socketTimeout=60`                                                                                          |
| `spring.datasource.password`       | `SPRING_DATASOURCE_PASSWORD`       | Password of `xroad_catalog_lister`                                                                                                                         |
| `xroad-catalog.shared-params-file` | `XROAD_CATALOG_SHARED_PARAMS_FILE` | `/etc/xroad/globalconf/<INSTANCE_IDENTIFIER>/shared-params.xml`, with the instance identifier filled in, e.g. `/etc/xroad/globalconf/FI/shared-params.xml` |

Commonly adjusted optional values:

| Property                                     | Environment variable                         | Default | Purpose                                                                                                                                                                                          |
|----------------------------------------------|----------------------------------------------|---------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `xroad-catalog.legacy-api.enabled`           | `XROAD_CATALOG_LEGACY_API_ENABLED`           | `false` | `true` serves the deprecated V1 REST API (`/api/*`) and the SOAP endpoint (`/ws/*`). Disabled, they answer 404.                                                                                  |
| `server.port`                                | `SERVER_PORT`                                | `8070`  | API port inside the container, also used by the RPM packages. Prefer publishing `8070` to another host port over changing this.                                                                  |
| `spring.datasource.hikari.maximum-pool-size` | `SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE` | `10`    | Database connection pool size. Raise when `hikaricp_connections_pending` stays above zero or `hikaricp_connections_timeout_total` increments in the Prometheus output, see [11](#11-monitoring). |

The configuration anchor path (`xroad.proxy.configuration-anchor-file`, `/etc/xroad/configuration-anchor.xml`) and the
global configuration directory (`xroad.common.configuration-path`, `/etc/xroad/globalconf`) have defaults matching the
mount points in [7. Volumes and Mounted Files](#7-volumes-and-mounted-files) and normally need no change.

### 6.5 JVM Options

Both images set:

```bash
JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError"
```

`MaxRAMPercentage` sizes the heap from the container's memory limit, so **every service needs a memory limit**
(see [9.3](#93-resource-limits-and-stop-timeout)). Without one the JVM reads the host's total memory and each service
sizes its heap at 75% of it. An operator-set `JAVA_TOOL_OPTIONS` replaces the whole string, not just the parts named.

Additional JVM flags — in particular any that carry secrets — go into a **JVM options file** instead of
`JAVA_TOOL_OPTIONS`. The image's entrypoint reads `/etc/xroad/catalog/jvm-options` at container start, if present, and
appends its whitespace-separated contents to the `java` command line (path overridable with the `JAVA_OPTS_FILE`
environment variable; paths containing spaces are not supported). The entrypoint fails closed: if the path exists but
is not a regular file (typically a directory created by a bind mount whose host source was missing), or
`JAVA_OPTS_FILE` names a path that does not exist, the container refuses to start.

> [!WARNING]
> Never put passwords in `JAVA_TOOL_OPTIONS`: the JVM prints its full value into the container log at every start
> (`Picked up JAVA_TOOL_OPTIONS: ...`). Flags from the options file are not printed, but they remain visible in the
> container's process arguments (`docker top`, `/proc/1/cmdline`), so restrict `exec`/`inspect` access to the
> containers accordingly.

### 6.6 Time Zone Configuration

X-Road Catalog stores every timestamp in the collector's local wall-clock time, not UTC. The lister reads and
serializes those timestamps back using the same convention, so the collector, the lister and the PostgreSQL database
**must all run in the same time zone**. If they don't, the day-boundary defaults used by the `/api/v2/reports/*` and
`/errors` endpoints (which default to "today" in the server's local time) will be off by a day for requests made near
midnight, and the collector's daily fetch and log-flush windows shift accordingly.

The images default to UTC. Either run everything in UTC, or set the same time zone on all three services — by
bind-mounting `/etc/localtime:/etc/localtime:ro` from the host, or by setting the same explicit `TZ` environment
variable on each. The configured time zone of a host can be checked with `timedatectl`.

### 6.7 Collector Timeouts

Outbound HTTP and SOAP calls to the Security Server are bounded by
`xroad-catalog.tasks.client-connect-timeout-seconds` (default `10`) and
`xroad-catalog.tasks.client-read-timeout-seconds` (default `60`).

Database calls are **not** bounded by those settings. Set the JDBC driver's `socketTimeout` parameter (in seconds) on
the collector's datasource URL to a value larger than the slowest expected write; without it a stalled connection
can block a fetch worker indefinitely:

```text
jdbc:postgresql://<db-host>:5432/xroad_catalog?socketTimeout=60
```

For the lister the parameter is recommended.

## 7. Volumes and Mounted Files

| What                                                     | Container | Container path                                                                 | Mode |
|----------------------------------------------------------|-----------|--------------------------------------------------------------------------------|------|
| Configuration anchor                                     | Lister    | `/etc/xroad/configuration-anchor.xml`                                          | ro   |
| Downloaded global configuration                          | Lister    | `/etc/xroad/globalconf/`                                                       | rw   |
| Keystore / truststore for the Security Server connection | Collector | `/etc/xroad/catalog/ssl/keystore.p12`, `/etc/xroad/catalog/ssl/truststore.p12` | ro   |
| JVM options file (secret flags)                          | Either    | `/etc/xroad/catalog/jvm-options`                                               | ro   |
| External configuration file (optional)                   | Either    | `/app/config/application.yaml`                                                 | ro   |

### 7.1 Configuration Anchor (Lister)

The lister's embedded configuration client downloads the ecosystem's global configuration from the Central Server(s)
listed in the configuration anchor. Mount the anchor read-only at `/etc/xroad/configuration-anchor.xml`.

The downloaded global configuration is written to `/etc/xroad/globalconf`, which lives in the container's writable
layer by default. The lister cannot serve requests until the first download has completed and it has read
`shared-params.xml` from there, so mounting a persistent volume at `/etc/xroad/globalconf` shortens restarts and keeps
the lister usable through a temporary Central Server outage.

### 7.2 TLS Keystore and Truststore (Collector)

Whether the collector needs TLS material depends on how its subsystem is configured on the Security Server:

* **`HTTP`** connection type: none.
* **`HTTPS NO AUTH`**: the Security Server's internal TLS certificate must be trusted. Add it to a PKCS12 truststore.
* **`HTTPS`**: additionally the collector must present a client certificate, which is added to the Security Server as
  an internal TLS certificate of the subsystem (Security Server UI: *Clients* → the catalog subsystem → *Internal
  servers* → *Internal TLS certificates* → *Add*).

Create the client key pair and export its certificate for the Security Server:

```bash
keytool -alias xroad-catalog -genkeypair -keystore keystore.p12 -storetype PKCS12 \
    -validity 7300 -keyalg RSA -keysize 2048 -sigalg SHA256withRSA -dname C=<COUNTRY_CODE>,CN=xroad-catalog
keytool -keystore keystore.p12 -storetype PKCS12 -exportcert -rfc -alias xroad-catalog > xroad-catalog.cer
```

Fetch the Security Server's certificate and import it into a truststore:

```bash
openssl s_client -showcerts -connect <SECURITY_SERVER>:8443 </dev/null 2>/dev/null \
    | openssl x509 -outform PEM > security-server.pem
keytool -importcert -noprompt -alias security-server -file security-server.pem \
    -keystore truststore.p12 -storetype PKCS12
```

TLS client authentication and server trust are configured at the JVM level through the `javax.net.ssl.*` system
properties, supplied in the JVM options file ([6.5](#65-jvm-options)) so that the passwords stay out of the logs:

```text
-Djavax.net.ssl.keyStore=/etc/xroad/catalog/ssl/keystore.p12
-Djavax.net.ssl.keyStorePassword=<keystore-password>
-Djavax.net.ssl.keyStoreType=PKCS12
-Djavax.net.ssl.trustStore=/etc/xroad/catalog/ssl/truststore.p12
-Djavax.net.ssl.trustStorePassword=<truststore-password>
-Djavax.net.ssl.trustStoreType=PKCS12
```

Mount the keystore, the truststore and the options file read-only at the paths in the table above.

The truststore replaces the JVM's default trust store, which has no side effects because the Security Server is the
only TLS endpoint the collector connects to.

### 7.3 File Permissions

The application runs as the non-root user `xroad`. Every mounted file must be readable by that user — for example
world-readable (`0444`), or owned by the uid the image assigns to `xroad`. A root-owned `0400` host file is not
readable and the service fails at startup. Docker creates missing bind-mount directories as `root:root`; a directory
mounted at a path where a file is expected trips the entrypoint's fail-closed check ([6.5](#65-jvm-options)).

## 8. Ports

| Port   | Container | Purpose                                                                                                              | Publish?                                                                                               |
|--------|-----------|----------------------------------------------------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------|
| `8070` | Lister    | REST API (`/api/v2/**`), Swagger UI (`/api-docs`), and when enabled the legacy V1 REST (`/api/*`) and SOAP (`/ws/*`) | Only towards the Security Server / reverse proxy, see [2.2](#22-security-posture-and-network-exposure) |
| `8090` | Lister    | Management: `/actuator/health`, `/actuator/health/liveness`, `/actuator/health/readiness`, `/actuator/prometheus`    | No — container network only                                                                            |
| `8090` | Collector | Management, same endpoints                                                                                           | No — container network only                                                                            |

`8070` is the same port the RPM packages served the lister on, so existing consumers need no change.

## 9. Running the Services

### 9.1 Startup Order

1. **PostgreSQL**, with the roles and database from [5. Database Setup](#5-database-setup).
2. **Collector.** On its first start it creates the schema and grants the application roles their privileges; its
   readiness probe turns `UP` once the migrations have finished and the database is reachable.
3. **Lister**, once the collector reports ready. Started earlier, the lister stays not-ready until the schema and
   grants exist and retries under the restart policy.

Docker Compose expresses this with `depends_on` and `condition: service_healthy`.

### 9.2 Docker Compose Example

A minimal production-shaped `compose.yml` against an externally managed PostgreSQL. Replace the placeholders; secrets
are shown inline for simplicity and should come from an `.env` file or the runtime's secret mechanism in practice.

```yaml
services:
  xroad-catalog-collector:
    image: niis/xroad-catalog-collector:<VERSION>
    restart: unless-stopped
    mem_limit: 2g
    stop_grace_period: 35s
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://db.example.org:5432/xroad_catalog?socketTimeout=60
      SPRING_DATASOURCE_PASSWORD: <collector password>
      SPRING_LIQUIBASE_PASSWORD: <owner password>
      XROAD_CATALOG_TARGET_XROAD_INSTANCE: FI
      XROAD_CATALOG_TARGET_MEMBER_CLASS: GOV
      XROAD_CATALOG_TARGET_MEMBER_CODE: "1234567-8"
      XROAD_CATALOG_TARGET_SUBSYSTEM_CODE: catalog
      XROAD_CATALOG_URLS_SECURITY_SERVER_HOST: https://ss.example.org:8443/
      XROAD_CATALOG_INSTANCE_IGNORED_SUBSYSTEM_IDS_0: FI:GOV:1234567-8:MANAGEMENT
      TZ: Europe/Helsinki
    volumes:
      - ./collector/jvm-options:/etc/xroad/catalog/jvm-options:ro
      - ./collector/keystore.p12:/etc/xroad/catalog/ssl/keystore.p12:ro
      - ./collector/truststore.p12:/etc/xroad/catalog/ssl/truststore.p12:ro

  xroad-catalog-lister:
    image: niis/xroad-catalog-lister:<VERSION>
    restart: unless-stopped
    mem_limit: 2g
    stop_grace_period: 35s
    depends_on:
      xroad-catalog-collector:
        condition: service_healthy
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://db.example.org:5432/xroad_catalog?socketTimeout=60
      SPRING_DATASOURCE_PASSWORD: <lister password>
      XROAD_CATALOG_SHARED_PARAMS_FILE: /etc/xroad/globalconf/FI/shared-params.xml
      TZ: Europe/Helsinki
    volumes:
      - ./lister/configuration-anchor.xml:/etc/xroad/configuration-anchor.xml:ro
      - globalconf:/etc/xroad/globalconf
    ports:
      # Bind to an address reachable only by the Security Server / reverse proxy.
      - "10.0.0.5:8070:8070"

volumes:
  globalconf:
```

Start with `docker compose up -d`. [docker/README.md](../docker/README.md) contains a complete development
environment, including a PostgreSQL container with a role-initialization script, that can serve as a further
reference.

### 9.3 Resource Limits and Stop Timeout

* **Memory limit:** required on both services, because the JVM sizes its heap from it ([6.5](#65-jvm-options)).
  Use `2g` per service. Larger ecosystems may need more, as the collector's need grows with the number of services
  and its worker pool sizes.
* **Stop timeout:** both services shut down gracefully on `SIGTERM`. Docker's default stop timeout of 10 seconds is
  too short; set `stop_grace_period: 35s` or more. The shutdown budget behind that value is described in the module
  READMEs: [collector](../xroad-catalog-collector/README.md#graceful-shutdown-and-container-stop-timeout),
  [lister](../xroad-catalog-lister/README.md#graceful-shutdown-and-container-stop-timeout).
* **Restart policy:** `unless-stopped`. The JVM exits on `OutOfMemoryError` (`-XX:+ExitOnOutOfMemoryError`) and
  relies on the runtime to restart it.

## 10. Post-Installation Checks

The checks use the lister port `8070` as published in the [compose example](#92-docker-compose-example); substitute
the port published in your deployment.

1. Both containers report healthy:

   ```bash
   docker compose ps
   ```

2. The collector's log shows the Liquibase summary and the start of the first collection cycle:

   ```bash
   docker compose logs xroad-catalog-collector | grep -iE "liquibase|UPDATE SUMMARY|collect"
   ```

3. The lister's heartbeat answers `200`. `lastCollectionData` is populated once the collector has completed a full
   cycle, which on a fresh install happens within `xroad-catalog.tasks.collector-interval-min` provided the current
   time is inside the fetch window (or `fetch-run-unlimited` is `true`):

   ```bash
   curl -s http://<lister-host>:8070/api/v2/heartbeat
   ```

4. Data is served:

   ```bash
   curl -s "http://<lister-host>:8070/api/v2/list/members?page=1&size=5"
   ```

5. The Swagger UI is reachable at `http://<lister-host>:8070/api-docs`.

If the heartbeat stays without collection data, check the collector log for errors against the Security Server
(access rights of the catalog subsystem, TLS trust) and the V2 error endpoint `GET /api/v2/browse/errors`.

## 11. Monitoring

Both services expose Spring Boot Actuator endpoints on the management port `8090`:

| Endpoint                     | Purpose                                                                                                          |
|------------------------------|------------------------------------------------------------------------------------------------------------------|
| `/actuator/health`           | Overall status only (`{"status":"UP"}`); component details are hidden by default                                 |
| `/actuator/health/liveness`  | Liveness probe. Independent of the database, so a database outage does not cause restarts                        |
| `/actuator/health/readiness` | Readiness probe. `DOWN` while the database is unreachable (and, in the collector, until the migrations have run) |
| `/actuator/prometheus`       | Metrics in Prometheus text format                                                                                |

The images' built-in `HEALTHCHECK` polls the readiness probe every 30 seconds with a start period of 90 seconds for
the collector (migrations) and 60 seconds for the lister (global configuration download).

Beyond the standard JVM, process, HTTP and data source meters, the collector exports:

| Metric                                                    | Type  | Use                                                               |
|-----------------------------------------------------------|-------|-------------------------------------------------------------------|
| `xroad_catalog_collection_cycle_duration_seconds`         | Timer | Duration of a full collection cycle, tagged `success=true\|false` |
| `xroad_catalog_collection_last_success_timestamp_seconds` | Gauge | Alert when `time() - value` exceeds a few collection intervals    |

To see the component breakdown of `/actuator/health` while debugging, set
`MANAGEMENT_ENDPOINT_HEALTH_SHOW_COMPONENTS=always`.

## 12. Logs

Both services log to standard output in a single-line, human-readable format; there are no log files inside the
containers. Use the runtime's log facility:

```bash
docker compose logs -f --since 1h xroad-catalog-collector
docker compose logs -f --since 1h xroad-catalog-lister
```

Lister log lines carry the request correlation id in the level field (`INFO [<requestId>]`); the same id is returned
to V2 API callers in the `X-Request-Id` response header. Log levels are adjusted per logger with environment variables,
e.g. `LOGGING_LEVEL_ORG_NIIS_XROAD_CATALOG=DEBUG`.

## 13. Backup and Restore

Back up the database and the mounted configuration files (compose file, `.env`, configuration anchor,
keystore/truststore, JVM options file). The downloaded global configuration is re-fetched automatically and needs no
backup.

Back up with `pg_dump` (any role that can read the whole database; the owner is the natural choice):

```bash
pg_dump -Fc -h <db-host> -U xroad_catalog xroad_catalog > xroad_catalog_$(date +%F).dump
```

Restore into an empty database that has the roles from [5.1](#51-database-roles), then start the collector
(which re-applies any missing migrations and grants) before the lister:

```bash
pg_restore -h <db-host> -U xroad_catalog -d xroad_catalog --no-owner --role=xroad_catalog xroad_catalog_<date>.dump
```

Do not run a backup or a restore while a collection cycle is running: stop the collector, or schedule the backup
outside the collection window. The `created`/`changed`/`removed` history used by the statistics and reports
endpoints cannot be recollected.

## 14. Upgrading

1. Read the release notes for the target version; note any configuration changes.
2. Back up the database ([13](#13-backup-and-restore)).
3. Change the image tags of **both** services to the new version.
4. Recreate the **collector first** and wait until it is healthy: it runs the schema migrations of the new version.
5. Recreate the **lister**.

With Compose, `docker compose up -d` after editing the tags does this in the right order when the lister has
`depends_on: … condition: service_healthy` on the collector. Never run a newer lister against a schema that an older
collector maintains, and do not run two collector versions against one database.

Rolling back an application version is done the same way in reverse (collector first) *if* the release notes state
that the schema change is backward compatible; otherwise restore the backup taken in step 2.

## 15. Search Performance

The `/api/v2/search` endpoint uses substring matching (`LIKE '%query%'`) on member names/codes, subsystem codes and
service codes, which is not index-assisted by default. For large ecosystems, create the `pg_trgm` extension and GIN
trigram indexes manually; X-Road Catalog does not create them.

The search matches against the lowercased values of `member.name`, `member.member_code`, `subsystem.subsystem_code`
and `service.service_code`, so the trigram indexes must be built on the same `LOWER(...)` expressions. As the database
superuser:

```sql
CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE INDEX idx_member_name_trgm ON member USING gin (LOWER(name) gin_trgm_ops);
CREATE INDEX idx_member_code_trgm ON member USING gin (LOWER(member_code) gin_trgm_ops);
CREATE INDEX idx_subsystem_code_trgm ON subsystem USING gin (LOWER(subsystem_code) gin_trgm_ops);
CREATE INDEX idx_service_code_trgm ON service USING gin (LOWER(service_code) gin_trgm_ops);
```
