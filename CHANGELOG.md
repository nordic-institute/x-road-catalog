# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

<!-- Placeholder for unreleased changes -->

## [4.0.0] - TBD

<!-- Final release date and complete changelog to be filled at release cut -->

### Added

- New V2 REST API (`/api/v2`) with pagination and streaming access
- Docker-based delivery

### Changed

- Rewrite on Java 21 (virtual threads) and Spring Boot 3.5
- Database schema now managed by Liquibase
- Legacy V1 REST and SOAP APIs behind a config toggle, disabled by default for new installs
