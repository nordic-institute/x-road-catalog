# X-Road Catalog Lister <!-- omit in toc -->

## Table of Contents <!-- omit in toc -->


* [Introduction](#introduction)
* [Configuration](#configuration)
    * [Mandatory to provide](#mandatory-to-provide)
        * [Mandatory Configurations for Data Source and Liquibase](#mandatory-configurations-for-data-source-and-liquibase)
        * [Mandatory Configurations for Common Features](#mandatory-configurations-for-common-features)
    * [Optional Configurations](#optional-configurations)
        * [Optional Configurations for Configuration-Client Features](#optional-configurations-for-configuration-client-features)
        * [Optional Configurations for Legacy API Features](#optional-configurations-for-legacy-api-features)
    * [Fixed-Mandatory Values to include in `application.yaml`](#fixed-mandatory-values-to-include-in-applicationyaml)
        * [Fixed-Mandatory Values for Data Source and Liquibase](#fixed-mandatory-values-for-data-source-and-liquibase)
        * [Fixed-Mandatory Values for Configuration-Client Features](#fixed-mandatory-values-for-configuration-client-features)
        * [Fixed-Mandatory Values for OpenAPI documentation](#fixed-mandatory-values-for-openapi-documentation)
        * [Fixed-Mandatory Values for Spring Boot Framework](#fixed-mandatory-values-for-spring-boot-framework)
        * [Fixed-Mandatory Values for Management Endpoints](#fixed-mandatory-values-for-management-endpoints)
* [Monitoring](#monitoring)
* [Graceful shutdown and container stop timeout](#graceful-shutdown-and-container-stop-timeout)
* [Build](#build)
* [Run](#run)

## Introduction

The purpose of this module is to provide a web service which lists all the X-Road members and the services they provide
together with services descriptions.

A class diagram illustrating X-Road Catalog Lister implementation:

![Catalog Service class diagram](img/class_diagram.png)

See also the [Installation Guide](../doc/xroad_catalog_installation_guide.md) and
[User Guide](../doc/xroad_catalog_user_guide.md).

## Configuration

X-Road Catalog Lister configurations are divided into three groups:

### Mandatory to provide

Values of following configurations are expected to be provided by service's user. Otherwise, service may fail to start.
Configurations are categorized according to their usage into different groups in the following sections.
> [!NOTE]
> Some configuration parameters are required depending on the use-case. For
> example, `spring.liquibase.parameters.users.*`
> parameters are only required if liquibase context include `users`.

#### Mandatory Configurations for Data Source and Liquibase

| Data Source and Liquibase Configurations                                                                                                                              | Defaults | Comment                                                                                                                                                                               | Since |
|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------|----------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-------|
| [spring.datasource.url](https://docs.spring.io/spring-boot/appendix/application-properties/index.html#application-properties.data.spring.datasource.url)              |          |                                                                                                                                                                                       | 1.0.0 |
| [spring.datasource.username](https://docs.spring.io/spring-boot/appendix/application-properties/index.html#application-properties.data.spring.datasource.username)    |          | Must match the pre-created lister role, i.e. the collector module's `spring.liquibase.parameters.users.lister.username`. | 1.0.0 |
| [spring.datasource.password](https://docs.spring.io/spring-boot/appendix/application-properties/index.html#application-properties.data.spring.datasource.password)    |          | Must match the password of the pre-created lister role.                                                                  | 1.0.0 |

#### Mandatory Configurations for Common Features

| Parameter                          | Defaults                                                                   | Description                                                                          | Since |
|------------------------------------|----------------------------------------------------------------------------|--------------------------------------------------------------------------------------|-------|
| `xroad-catalog.shared-params-file` | ${xroad.common.configuration-path}/<INSTANCE_IDENTIFIER>/shared-params.xml | A parameter for setting the path to shared params file exported from X-Road server.  | 1.0.0 |

### Optional Configurations

#### Optional Configurations for Configuration-Client Features

| Configurations                                                 | Defaults                            | Description                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                    | Since |
|----------------------------------------------------------------|-------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-------|
| `xroad.common.configuration-path`                              | /etc/xroad/globalconf/              | Absolute path to the directory where global configuration is stored.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                           | 1.0.0 |
| `xroad.proxy.configuration-anchor-file`                        | /etc/xroad/configuration-anchor.xml | Absolute file name of the configuration anchor that is used to download global configuration.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                  | 1.0.0 |
| `xroad.configuration-client.port`                              | 5665                                | gRPC (TCP) port on which the configuration client process listens.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                             | 1.0.0 |
| `xroad.configuration-client.update-interval`                   | 60                                  | Global configuration download interval in seconds.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                             | 1.0.0 |
| `xroad.configuration-client.admin-port`                        | 5675                                | TCP port on which the configuration client process listens for admin commands.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                 | 1.0.0 |
| `xroad.configuration-client.allowed-federations`               | none                                | A comma-separated list of case-insensitive X-Road instances that fetching configuration anchors is allowed for. This enables federation with the listed instances if the X-Road instance is already federated at the Central Server level . Special value *none*, if present, disables all federation (the default value), while *all* allows all federations if *none* is not present. Example: *allowed-federations=ee,sv* allows federation with example instances *EE* and *Sv* while *allowed-federations=all,none* disables federation. X-Road services `xroad-confclient` and `xroad-proxy` need to be restarted (in that order) for the setting change to take effect. | 1.0.0 |
| `xroad.configuration-client.proxy-configuration-backup-cron`   | 0 15 3 * * ?                        | Cron expression for proxy configuration automatic backup job                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                   | 1.0.0 |
| `xroad.configuration-client.global_conf_tls_cert_verification` | true                                | It is possible to disable the verification of the global configuration download TLS certificate. Should be `true` in production environment                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                    | 1.0.0 |
| `xroad.configuration-client.global_conf_hostname_verification` | true                                | It is possible to disable the hostname verification. Does the hostname specified in the URL match the hostname specified in the Common Name (CN) of the Central Server’s TLS certificate. Should be `true` in production environment                                                                                                                                                                                                                                                                                                                                                                                                                                           | 1.0.0 |

#### Optional Configurations for Legacy API Features

| Parameter                          | Defaults | Description                                                                                                   | Since |
|------------------------------------|----------|---------------------------------------------------------------------------------------------------------------|-------|
| `xroad-catalog.legacy-api.enabled` | `false`  | Enables the deprecated V1 REST API (`/api/*`) and the SOAP endpoint (`/ws/*`). Disabled endpoints return 404. | 4.0.0 |

### Fixed-Mandatory Values to include in `application.yaml`

The following list of configurations must be included in the `application.yaml` file without modifications to their
values.
> [!IMPORTANT]
> If user is not using the `application.yaml` file to customize the configurations, e.g., using spring boot profile or
> k8s
> configmap, service will use the default `application.yaml` which provide these values already.

#### Fixed-Mandatory Values for Data Source and Liquibase

| Data Source and Liquibase Configurations                                                                                                                                             | Defaults                                  | Comment                                                                             | Since |
|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-------------------------------------------|-------------------------------------------------------------------------------------|-------|
| [spring.datasource.driver-class-name](https://docs.spring.io/spring-boot/appendix/application-properties/index.html#application-properties.data.spring.datasource.driver-class-name) | `org.postgresql.Driver`                   |                                                                                     | 1.0.0 |
| [spring.jpa.database](https://docs.spring.io/spring-boot/appendix/application-properties/index.html#application-properties.data.spring.jpa.database)                                 | `POSTGRESQL`                              | Auto detected by default.                                                           | 1.0.0 |
| [spring.jpa.generate-ddl](https://docs.spring.io/spring-boot/appendix/application-properties/index.html#application-properties.data.spring.jpa.generate-ddl)                         | `false`                                   | Always use `false`. Lister application is not supposed to change database structure | 1.0.0 |
| [spring.jpa.open-in-view](https://docs.spring.io/spring-boot/appendix/application-properties/index.html#application-properties.data.spring.jpa.open-in-view)                         | `false`                                   | Preferably `false` in production.                                                   | 1.0.0 |
| [spring.jpa.show-sql](https://docs.spring.io/spring-boot/appendix/application-properties/index.html#application-properties.data.spring.jpa.show-sql)                                 | `false`                                   | Use to debug SQL statements executed. Preferably `false` in production.             | 1.0.0 |
| [spring.jpa.hibernate.ddl-auto](https://docs.spring.io/spring-boot/appendix/application-properties/index.html#application-properties.data.spring.jpa.hibernate.ddl-auto)             | `none`                                    | Keep to `none` to prevent hiberante from trying to update the database.             | 1.0.0 |
| spring.jpa.properties.hibernate.dialect                                                                                                                                              | `org.hibernate.dialect.PostgreSQLDialect` | PostgreSQL is the only supported RDMBS. Don't change                                | 1.0.0 |

#### Fixed-Mandatory Values for Configuration-Client Features

| Parameter                                    | Defaults | Description                                                                  | Since |
|----------------------------------------------|----------|------------------------------------------------------------------------------|-------|
| `xroad-catalog.configuration-client.enabled` | `true`   | A parameter to enable/disable configuration-client scheduler. Do not modify. | 1.0.0 |

#### Fixed-Mandatory Values for OpenAPI documentation

| Spring Boot framework configurations   | Defaults    | Comment                                  | Since |
|----------------------------------------|-------------|------------------------------------------|-------|
| springdoc.api-docs.enabled             | `true`      | Enables OpenApi endpoint                 | 1.0.0 |
| springdoc.swagger-ui.enabled           | `true`      | Enables Swagger-UI                       | 1.0.0 |
| springdoc.swagger-ui.path              | `/api-docs` | Swagger-UI path to be used               | 1.0.0 |
| springdoc.swagger-ui.urls-primary-name | `v2`        | API group shown by default in Swagger-UI | 4.0.0 |

#### Fixed-Mandatory Values for Spring Boot Framework

| Spring Boot framework configurations                                                                                                                                                                 | Defaults   | Comment                                                                                                                                                        | Since |
|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------|-------|
| [spring.lifecycle.timeout-per-shutdown-phase](https://docs.spring.io/spring-boot/appendix/application-properties/index.html#application-properties.core.spring.lifecycle.timeout-per-shutdown-phase) | `30s`      | How long Spring waits for each shutdown phase to complete. See [Graceful shutdown and container stop timeout](#graceful-shutdown-and-container-stop-timeout).   | 4.0.0 |
| [server.shutdown](https://docs.spring.io/spring-boot/appendix/application-properties/index.html#application-properties.server.server.shutdown)                                                       | `graceful` | Requests in flight are allowed to finish before the web context is closed, instead of being dropped on shutdown.                                                | 4.0.0 |

#### Fixed-Mandatory Values for Management Endpoints

The Actuator management endpoints are used for container health checks and for metrics scraping, see
[Monitoring](#monitoring).

| Management endpoint configurations                   | Defaults            | Comment                                                                                                                                                                                                             | Since |
|------------------------------------------------------|---------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-------|
| `management.server.port`                             | `8090`              | The Actuator endpoints are served on this separate connector, not on an application port.                                                                                                                           | 4.0.0 |
| `management.endpoints.web.exposure.include`          | `health,prometheus` | Only these two endpoints are exposed. Endpoints such as `env`, `heapdump`, `loggers` and `threaddump` are deliberately left unexposed.                                                                              | 4.0.0 |
| `management.endpoint.health.probes.enabled`          | `true`              | Enables the `/actuator/health/liveness` and `/actuator/health/readiness` probe endpoints.                                                                                                                           | 4.0.0 |
| `management.endpoint.health.group.readiness.include` | `readinessState,db` | Readiness reflects database reachability. Liveness deliberately does not, so that a database outage does not make the runtime restart an otherwise healthy process.                                                 | 4.0.0 |
| `management.endpoint.health.cache.time-to-live`      | `10s`               | Caches `/actuator/health`.                                                                                                                                                                                          | 4.0.0 |
| `management.endpoint.health.show-components`         | `never`             | Spring Boot defaults to `never`: the health response carries the overall status only, with no component breakdown. The `dev` profile (`application-dev.yaml`) overrides it to `always`, useful for local debugging. | 4.0.0 |

## Monitoring

Besides the REST and SOAP API served on the main server port, the lister serves Spring Boot Actuator endpoints on the
management port (`management.server.port`, `8090` by default):

| Endpoint                     | Purpose                                                                                                |
|------------------------------|--------------------------------------------------------------------------------------------------------|
| `/actuator/health`           | Overall status only, without a component breakdown (see `management.endpoint.health.show-components`). |
| `/actuator/health/liveness`  | Liveness probe. Does not depend on the database, so a database outage does not trigger a restart.      |
| `/actuator/health/readiness` | Readiness probe. Reports `DOWN` while the database is unreachable.                                     |
| `/actuator/prometheus`       | Metrics in the Prometheus text format, to be scraped by a Prometheus-compatible collector.             |

The metrics exported are the standard JVM, process, HTTP server and data source meters provided by Micrometer.

## Graceful shutdown and container stop timeout

Graceful shutdown is enabled (`server.shutdown: graceful`), so on `SIGTERM` requests already in flight are allowed to
finish instead of being dropped, and each shutdown phase is given up to 30 seconds
(`spring.lifecycle.timeout-per-shutdown-phase`).

> [!IMPORTANT]
> The container runtime must allow more time than its default stop timeout, otherwise the process is `SIGKILL`ed in the
> middle of its shutdown and requests in flight are dropped after all. Docker's default stop timeout is only 10 seconds,
> which is less than the shutdown phase timeout: `docker/compose.yml` therefore sets `stop_grace_period: 35s`, and a
> Kubernetes deployment needs an equivalent `terminationGracePeriodSeconds`.

## Build

X-Road Catalog Lister can be built by running:

```bash
../gradlew clean build
```

## Run

X-Road Catalog Lister can be run using Gradle:

```bash
../gradlew bootRun
```

or running it from a JAR file:

```bash
java -jar build/libs/xroad-catalog-lister.jar
```
