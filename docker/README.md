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

There are only two ports open to the host machine:

* `5080` for the `adminer` service web UI for accessing the database.
  * Use the configuration below to access the web UI:
    * System: PostgreSQL
    * Server: `xrd-catalog-db`
    * Username: `xroad_catalog`
    * Password: `secret`
    * Database: `xroad_catalog`
* `8070` for the `xroad-catalog-lister` service API. This port also allows you to access the `Swagger UI` under path `/api-docs`.
* `4910` for the postgres database.