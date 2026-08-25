# Docker based development environment

This is a basic implementation of a Docker based local testing environment using Docker Compose.

Currently, it supports running the `xroad-catalog-collector` and `xroad-catalog-lister` services in containers
and a PostgreSQL database alongside `adminer`.

The `compose.yml` file has been configured so that the services can access the `X-Road` compose based environment.

## Running the environment

1. Build the JAR files for the services by running `./gradlew clean build` in the root of the project. Use `clean`:
   the Dockerfiles `COPY` `build/libs/*.jar` by wildcard, so a stale jar left over from an earlier build would get
   baked into the image.
2. Copy your environment's configuration anchor file to the [lister/config](lister/config) directory with the name
   `configuration-anchor.xml`.
3. `compose.yml` is the living example of how the containers are configured; see [Configuration](#configuration)
   below for how to change it.
4. Keeping the provided X-Road-instance parameters in `compose.yml` like `XROAD_CATALOG_TARGET_SUBSYSTEM_CODE=catalog`,
   make sure to add the subsystem to your X-Road instance and make sure to give it the desired access method (`HTTPS`,
   `HTTPS NO AUTH`, `HTTP`).
5. Start the environment with `docker compose up -d --build`. On first start the database container creates the
   two application roles from `db/init-app-roles.sql`; init scripts only run on an empty data volume, so after
   changing them reset the database with `docker compose down -v`.
6. (Optional) Verify the setup with `curl http://localhost:8070/api/v2/heartbeat`: once the collector has completed
   a run against your X-Road instance, the `lastCollectionData` timestamps are populated.

## Configuration

The containers use Spring's standard configuration mechanisms. Each image ships a packaged `application.yaml` with
the application defaults, and every value in it is overridable two ways:

* **Environment variables** — this is what `compose.yml` uses; treat it as the living example.
* **A mounted config file** — Spring's default external-config location `/app/config/application.yaml` applies
  inside the container. Mount it read-only; nothing in the image rewrites configuration files.

### Environment variable naming

Standard Spring relaxed binding applies to `spring.*` and `management.*` keys, for example:

* `spring.datasource.url` -> `SPRING_DATASOURCE_URL`
* `management.endpoint.health.show-components` -> `MANAGEMENT_ENDPOINT_HEALTH_SHOW_COMPONENTS`

For this application's own `xroad.*` and `xroad-catalog.*` keys, replace every `.` and `-` with `_` and uppercase the
result; a literal `_` already present in the key survives, for example:

* `xroad-catalog.target.xroad-instance` -> `XROAD_CATALOG_TARGET_XROAD_INSTANCE`
* `xroad.configuration-client.global_conf_tls_cert_verification` ->
  `XROAD_CONFIGURATION_CLIENT_GLOBAL_CONF_TLS_CERT_VERIFICATION`

List-valued keys take an index suffix: `xroad-catalog.instance.ignored-subsystem-ids[0]` ->
`XROAD_CATALOG_INSTANCE_IGNORED_SUBSYSTEM_IDS_0`.

**Caveat:** only `xroad.*` keys defined in the packaged lister `application.yaml` are passed to the X-Road
configuration client. A key without a packaged default cannot be introduced via an environment variable — set it in
a mounted config file instead.

### JVM tuning

Both images default to:

```
JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError"
```

`MaxRAMPercentage` sizes the heap from the container's memory limit, so each service needs one. Without a limit
the JVM reads the host's total RAM instead and every service sizes its heap at 75% of it.

An operator-set `JAVA_TOOL_OPTIONS` replaces this whole string, not just the parts you name.

### Dev profile

`compose.yml` does not activate the `dev` Spring profile — the profile would repoint the lister's port and
datasource. For verbose logging, set `LOGGING_LEVEL_*` environment variables instead.

## TLS material and configuration anchor

| What                              | Container path                                                                      |
|-----------------------------------|-------------------------------------------------------------------------------------|
| Configuration anchor (lister)     | `/etc/xroad/configuration-anchor.xml` (ro)                                          |
| Keystore / truststore (collector) | `/etc/xroad/catalog/ssl/keystore.p12`, `/etc/xroad/catalog/ssl/truststore.p12` (ro) |
| JVM options file (secrets)        | `/etc/xroad/catalog/jvm-options` (ro)                                               |

### JVM options file

The entrypoint script (`docker/docker-entrypoint.sh`, copied to `/entrypoint.sh` in the image) reads this file at
container start, if present, and splits its contents on whitespace into extra `java` flags (globbing is disabled, so
paths containing spaces are not supported). The default path is `/etc/xroad/catalog/jvm-options`; override it with
the `JAVA_OPTS_FILE` environment variable. It fails closed: if the path is mounted as something other than a regular
file (e.g. a directory from a missing bind-mount source), or `JAVA_OPTS_FILE` names a path that doesn't exist, the
container refuses to start rather than silently running without the flags.

Example file, mounted read-only at that path, to configure mTLS towards a Security Server:

```
-Djavax.net.ssl.keyStore=/etc/xroad/catalog/ssl/keystore.p12
-Djavax.net.ssl.keyStorePassword=<keystore-password>
-Djavax.net.ssl.keyStoreType=PKCS12
-Djavax.net.ssl.trustStore=/etc/xroad/catalog/ssl/truststore.p12
-Djavax.net.ssl.trustStorePassword=<truststore-password>
-Djavax.net.ssl.trustStoreType=PKCS12
```

> [!IMPORTANT]
> `-Djavax.net.ssl.trustStore` REPLACES the JVM's default `cacerts` trust store for the whole JVM — with a truststore
> configured, publicly-issued certificates are no longer trusted unless they are added to it.

> [!WARNING]
> Never put these flags in `JAVA_TOOL_OPTIONS`: the JVM echoes its full value, passwords included, into the
> container log at every start (`Picked up JAVA_TOOL_OPTIONS: ...`). The passwords do remain visible in the
> container's process arguments (`/proc/1/cmdline`, `docker top`), so restrict exec/inspect access accordingly.

### Bind-mount permissions

Docker creates missing bind-mount directories as `root:root`; the application runs as the non-root `xroad` user.
Mounted files must therefore be readable by that user — e.g. world-readable (`0444`) or owned by a matching uid. A
root-owned `0400` host file is not.

`/etc/xroad/globalconf` — the lister's downloaded global configuration — is stored in the container's writable
layer. Mount a volume there if you want it to persist across container recreation.

## Open ports

There are only three ports open to the host machine:

* `5080` for the `adminer` service web UI for accessing the database.
  * Use the configuration below to access the web UI:
    * System: PostgreSQL
    * Server: `xrd-catalog-db`
    * Username: `xroad_catalog`
    * Password: `secret`
    * Database: `xroad_catalog`
* `8070` for the `xroad-catalog-lister` service API. This port also allows you to access the `Swagger UI` under path `/api-docs`.
* `4910` for the postgres database.

Both catalog services also listen on port `8090` for their management endpoints, but that port is deliberately
**not** published to the host. It is reachable only from the compose networks, and only the `health` and
`prometheus` endpoints are exposed there — `env`, `heapdump`, `loggers` and `threaddump` are not.

## Health checks

Both images define a `HEALTHCHECK` against the readiness probe on the management port:

```
wget -q -O /dev/null http://localhost:8090/actuator/health/readiness
```

The readiness group covers `readinessState` and `db`, so a container reports healthy only once its database
connection works. The collector is given a longer start period (90s) than the lister (60s) because it runs the
Liquibase migrations at startup and cannot become ready until they finish.

Note that the health response carries the overall status only. Component details are hidden by default; `compose.yml`
sets `MANAGEMENT_ENDPOINT_HEALTH_SHOW_COMPONENTS=always` for the lister so you get the breakdown while debugging
locally — set the same environment variable on the collector if you need it there too.

## Shutdown grace period

Both services are configured for graceful shutdown, and `compose.yml` sets `stop_grace_period: 35s` on each.
Docker's default of 10 seconds would SIGKILL the process mid-shutdown and leave an in-flight collection run
unfinalized. The 35s must stay above the application-side shutdown budget described in
[Graceful shutdown and container stop timeout](../xroad-catalog-collector/README.md#graceful-shutdown-and-container-stop-timeout);
raise it if either of the timeouts named there is raised.
