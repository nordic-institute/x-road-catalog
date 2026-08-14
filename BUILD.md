# Building X-Road Catalog

## License <!-- omit in toc -->

This document is licensed under the Creative Commons Attribution-ShareAlike 3.0 Unported License. 
To view a copy of this license, visit <http://creativecommons.org/licenses/by-sa/3.0/>.

## About

Developing and building the X-Road Catalog software requires an Ubuntu or a RHEL host. If you are using some other
operating system (e.g. Windows or MacOS), the easiest option is to first install Ubuntu 22.04 or RHEL7 into a virtual
machine.

**Tools**

Required for building:
* OpenJDK / JDK version 21
* Gradle
* Docker

Recommended for development environment:
* Docker
* [LXD](https://linuxcontainers.org/lxd/)
    * For setting up a local X-Road instance.
* Ansible
    * For automating the [X-Road ecosystem installation](https://github.com/nordic-institute/X-Road/tree/develop/ansible).

The development environment should have at least 8GB of memory and 20GB of free disk space (applies to a virtual machine
as well), especially if you set up a local X-Road ecosystem.

**Prerequisites**

* Checkout the `x-road-catalog` repository.
* The directory structure should look like this:

    ```
    - <BASE_DIR>
     |-- xroad-catalog-collector
     |-- xroad-catalog-lister
     |-- xroad-catalog-persistence
    ```
* The build scripts assumes the above directory structure.

## Build

To build all modules, run the following command in the root directory:

```bash
./gradlew clean build
```

To build a specific module:

* **Build X-Road Catalog Collector**  
 See [xroad-catalog-collector/README.md](xroad-catalog-collector/README.md#build) for details.

* **Build X-Road Catalog Lister**  
See [xroad-catalog-lister/README.md](xroad-catalog-lister/README.md#build) for details.

* **Build X-Road Catalog Persistence**  
See [xroad-catalog-persistence/README.md](xroad-catalog-persistence/README.md#build) for details.

## Versioning and releases

The project version is single-sourced in the `version=` line of the root `gradle.properties`. Bump it by
editing that single line; Gradle's automatic `version` property propagates the new value to every
subproject, including the generated `xroad-catalog-lister/build/resources/main/version.properties` used by
the heartbeat endpoints. The old `update_version.sh` script has been removed since it targeted a
`build.gradle` file that no longer exists after the Kotlin DSL migration.

`xroad-catalog-lister/src/main/resources/version.properties` is a template expanded by the build's
`processResources` task and must not be edited by hand — only the `xroad-catalog.app-name` line is literal.
The packaged `version.properties` is regenerated on the next build, so no other file needs editing.

When changing dependencies (e.g. editing `gradle/libs.versions.toml`), refresh the dependency lockfiles:

```
./gradlew dependencies :xroad-catalog-persistence:dependencies \
    :xroad-catalog-collector:dependencies :xroad-catalog-lister:dependencies \
    --write-locks
```

Tag the release commit on `develop` with an annotated tag:

```
git tag -a vX.Y.Z -m "X-Road Catalog X.Y.Z" && git push origin vX.Y.Z
```
