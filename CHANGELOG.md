# Change Log

## 4.0.0 - UNRELEASED
- OpenAPI descriptors published as YAML are stored and served (as `application/yaml`), not only JSON ones
- New V2 REST API (`/api/v2`) with pagination, filtering and searching capabilities
- Update to Java 25 (virtual threads) and Spring Boot 3.5
- Database schema now managed by Liquibase
- Legacy V1 REST and SOAP APIs behind a configuration toggle
- Containerisation support
