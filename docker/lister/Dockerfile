# Base image for X-Road Catalog. Digest-pinned; Dependabot tracks the tag/digest pair.
FROM docker.io/library/eclipse-temurin:25-jre-alpine@sha256:3137541deb3cac6626b5d9a4a2187bc0d6a34312f858bd2c67dd01e732e6b682 \
    AS base-xroad-catalog

# Labels following the OCI Image Spec
LABEL org.opencontainers.image.vendor="Nordic Institute for Interoperability Solutions (NIIS)" \
        org.opencontainers.image.authors="info@niis.org" \
        org.opencontainers.image.title="X-Road Catalog Lister" \
        org.opencontainers.image.description="X-Road Catalog Lister" \
        org.opencontainers.image.licenses="MIT" \
        org.opencontainers.image.source="https://github.com/nordic-institute/x-road-catalog"


# Create user and group
RUN addgroup -S xroad && adduser -S xroad -G xroad

# Set working directory
WORKDIR /app

# Create necessary directories and set permissions
RUN mkdir -p /etc/xroad \
    && chown -R xroad:xroad /app /etc/xroad

# -------------------- Separator: builder stage, extracts the layered boot jar --------------------
FROM base-xroad-catalog AS builder

COPY xroad-catalog-lister/build/libs/xroad-catalog-lister-*.jar /app/xroad-catalog-lister.jar
RUN java -Djarmode=tools -jar /app/xroad-catalog-lister.jar extract --layers --launcher --destination extracted

# -------------------- Separator: Start of xroad-catalog-lister image --------------------
# Final image for X-Road Catalog Lister
FROM base-xroad-catalog AS xroad-catalog-lister

COPY docker/docker-entrypoint.sh /entrypoint.sh

# Copy extracted layers in cacheable order: least-to-most likely to change between builds.
COPY --from=builder --chown=xroad:xroad /app/extracted/dependencies/ ./
COPY --from=builder --chown=xroad:xroad /app/extracted/spring-boot-loader/ ./
COPY --from=builder --chown=xroad:xroad /app/extracted/snapshot-dependencies/ ./
COPY --from=builder --chown=xroad:xroad /app/extracted/application/ ./

# Switch to non-root user
USER xroad

ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError"

EXPOSE 8080 8090

# Define entry point
ENTRYPOINT ["/entrypoint.sh", "org.springframework.boot.loader.launch.JarLauncher"]

# Health check. Actuator is served on the management port (8090), not on the API port 8080.
# The start period allows for the X-Road configuration client to come up alongside the web context.
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
  CMD wget -q -O /dev/null http://localhost:8090/actuator/health/readiness || exit 1
