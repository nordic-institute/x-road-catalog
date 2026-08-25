# Base image for X-Road Catalog. Digest-pinned; Dependabot tracks the tag/digest pair.
FROM docker.io/library/eclipse-temurin:25-jre-alpine@sha256:3137541deb3cac6626b5d9a4a2187bc0d6a34312f858bd2c67dd01e732e6b682 \
    AS base-xroad-catalog

# Labels following the OCI Image Spec
LABEL org.opencontainers.image.vendor="Nordic Institute for Interoperability Solutions (NIIS)" \
        org.opencontainers.image.authors="info@niis.org" \
        org.opencontainers.image.title="X-Road Catalog Collector" \
        org.opencontainers.image.description="X-Road Catalog Collector" \
        org.opencontainers.image.licenses="MIT" \
        org.opencontainers.image.source="https://github.com/nordic-institute/x-road-catalog"


# Create user and group
RUN addgroup -S xroad && adduser -S xroad -G xroad

# Set working directory
WORKDIR /app

# Set /app ownership
RUN chown -R xroad:xroad /app

# -------------------- Separator: builder stage, extracts the layered boot jar --------------------
FROM base-xroad-catalog AS builder

COPY xroad-catalog-collector/build/libs/xroad-catalog-collector-*.jar /app/xroad-catalog-collector.jar
RUN java -Djarmode=tools -jar /app/xroad-catalog-collector.jar extract --layers --launcher --destination extracted

# -------------------- Separator: Start of xroad-catalog-collector image --------------------
# Final image for X-Road Catalog Collector
FROM base-xroad-catalog AS xroad-catalog-collector

COPY docker/docker-entrypoint.sh /entrypoint.sh

# Copy extracted layers in cacheable order: least-to-most likely to change between builds.
COPY --from=builder --chown=xroad:xroad /app/extracted/dependencies/ ./
COPY --from=builder --chown=xroad:xroad /app/extracted/spring-boot-loader/ ./
COPY --from=builder --chown=xroad:xroad /app/extracted/snapshot-dependencies/ ./
COPY --from=builder --chown=xroad:xroad /app/extracted/application/ ./

# Switch to non-root user
USER xroad

ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError"

EXPOSE 8090

# Define entry point
ENTRYPOINT ["/entrypoint.sh", "org.springframework.boot.loader.launch.JarLauncher"]

# Health check. Actuator is served on the management port (8090); the collector has no main connector
# (server.port: -1). The start period allows for the Liquibase migrations that run at startup.
HEALTHCHECK --interval=30s --timeout=10s --start-period=90s --retries=3 \
  CMD wget -q -O /dev/null http://localhost:8090/actuator/health/readiness || exit 1
