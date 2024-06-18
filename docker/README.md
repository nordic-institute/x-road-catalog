# Docker based development environment

This is a basic implementation of a Docker based local testing environment using Docker Compose.

Currently, it supports running the `xroad-catalog-collector` and `xroad-catalog-lister` services in containers
alongside the `xroad-confclient`(required by the lister service) and a PostgreSQL database alongside `adminer`.

The `compose.yml` file has been configured so that the services can access the `X-Road` compose based environment.

## Running the environment

1. Build the JAR files for the services by running `./gradlew build` in the root of the project.
2. Copy your environments configuration anchor file to the `confclient/data` directory with the name `configuration-anchor.xml`.
3. Create the configurations under `collector/config` and `lister/config` directories to suite your needs by renaming the `sample` files.
4. Start the environment with `docker compose up -d --build`.

## Open ports

There are only two ports open to the host machine:

* `5080` for the `adminer` service web UI for accessing the database.
* `8070` for the `xroad-catalog-lister` service API. This port also allows you to access the `Swagger UI` under path `/api-docs`.
