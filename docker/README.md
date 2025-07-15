# Docker based development environment

This is a basic implementation of a Docker based local testing environment using Docker Compose.

Currently, it supports running the `xroad-catalog-collector` and `xroad-catalog-lister` services in containers
and a PostgreSQL database alongside `adminer`.

The `compose.yml` file has been configured so that the services can access the `X-Road` compose based environment.

## Running the environment

1. Build the JAR files for the services by running `./gradlew build` in the root of the project.
2. Copy your environments configuration anchor file to the [lister/config](lister/config) directory with the name `configuration-anchor.xml`.
3. Configure the containers by editing the environment variables in the `docker-compose.yml` file, e.g. you can set the `XROAD_INSTANCE`
   variable to your desired X-Road instance name, and the `XROAD_ENVIRONMENT` variable to your desired environment (e.g., `DEV`, `TEST`, etc.). 
   These variables will overwrite the variables set in the configuration files copied in the [docker/MODULE/Dockerfile](lister/Dockerfile) files. For more information refer to the [this](#overwriting-configuration-files-by-environment-variables) section.
4. Start the environment with `docker compose up -d --build`.

### Overwriting configuration files by environment variables

You can overwrite the provides configuration files by providing environment variables that start with `setting_`. 
For example, if you want to overwrite the `xroad-catalog.target.xroad-instance` setting in the `application.yaml` file, 
you can set the environment variable `setting_xroad-catalog.target.xroad-instance=DEV` when running the container. The entires inside `application.yaml` 
will be updated with the environment variable values before starting the application.

## Open ports

There are only two ports open to the host machine:

* `5080` for the `adminer` service web UI for accessing the database.
* `8070` for the `xroad-catalog-lister` service API. This port also allows you to access the `Swagger UI` under path `/api-docs`.
* `4910` for the postgres database.