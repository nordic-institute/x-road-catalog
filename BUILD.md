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
