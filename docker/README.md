# Docker based development environment

This is a basic implementation of a Docker based local testing environment using Docker Compose.

Currently, it supports running the `xroad-catalog-collector` and `xroad-catalog-lister` services in containers
and a PostgreSQL database alongside `adminer`.

The `compose.yml` file has been configured so that the services can access the `X-Road` compose based environment.

## Running the environment

1. Build the JAR files for the services by running `./gradlew build` in the root of the project.
2. Copy your environments configuration anchor file to the [lister/config](lister/config) directory with the name `configuration-anchor.xml`.
3. By default, the compose environment is configured using the `application.yaml` file inside `<module>/src/main/resources/` directory and overwrites the values using environment variables provided in `compose.yml`. 
   For more information on how to overwrite the configuration files, see the section below.

   Alternatively you can mount your own configuration file to the directory `/app` inside the containers. **NB!**: The entries inside the file may overwritten by the environment variables provided in `compose.yml` file.
4. Keeping the provided X-Road-instance parameters in `compose.yml` like `setting_xroad-catalog.target.subsystem-code=catalog`, make sure to add the subsystem to your X-Road instance and make sure to give it the desired access method (`HTTPS`, `HTTPS NO AUTH`, `HTTP`)
5. Start the environment with `docker compose up -d --build`.
6. (Optional) In order to verify the setup, make sure that the catalog service can access the X-Road service, by running catalog's `/api/getListOfServices` request and check entry "`serviceList`" for a subsystem that contains services.

### Overwriting configuration files by environment variables

You can overwrite the provides configuration files by providing environment variables that start with `setting_`. 
For example, if you want to overwrite the `xroad-catalog.target.xroad-instance` setting in the `application.yaml` file, 
you can set the environment variable `setting_xroad-catalog.target.xroad-instance=DEV` when running the container. The entires inside `application.yaml` 
will be updated with the environment variable values before starting the application.

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
curl -f http://localhost:8090/actuator/health/readiness
```

The readiness group covers `readinessState` and `db`, so a container reports healthy only once its database
connection works. The collector is given a longer start period (90s) than the lister (60s) because it runs the
Liquibase migrations at startup and cannot become ready until they finish.

Note that the health response carries the overall status only. Component details are hidden by default; use the
`dev` Spring profile, which sets `management.endpoint.health.show-components: always`, when you need the
breakdown while debugging locally.

## Shutdown grace period

Both services are configured for graceful shutdown, and `compose.yml` sets `stop_grace_period: 35s` on each.
Docker's default of 10 seconds would SIGKILL the process mid-shutdown and leave an in-flight collection run
unfinalized. The 35s must stay above the application-side shutdown budget described in
[Graceful shutdown and container stop timeout](../xroad-catalog-collector/README.md#graceful-shutdown-and-container-stop-timeout);
raise it if either of the timeouts named there is raised.
