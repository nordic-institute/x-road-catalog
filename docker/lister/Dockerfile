# Base image for X-Road Catalog
FROM eclipse-temurin:21-jre-alpine AS base-xroad-catalog

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

# Define volumes
VOLUME /etc/xroad

# -------------------- Separator: Start of xroad-catalog-lister image --------------------
# Final image for X-Road Catalog Lister
FROM base-xroad-catalog AS xroad-catalog-lister

RUN apk add --no-cache curl && \
    curl -L https://github.com/mikefarah/yq/releases/latest/download/yq_linux_amd64 \
    -o /usr/bin/yq && \
    chmod +x /usr/bin/yq && \
    apk add --no-cache bash

# Copy application JAR file
COPY xroad-catalog-lister/build/libs/xroad-catalog-lister-*.jar /app/xroad-catalog-lister.jar
COPY docker/docker-entrypoint.sh /entrypoint.sh
COPY xroad-catalog-lister/src/main/resources/application.yaml /app/application.yaml

# Switch to non-root user
USER xroad

# Define entry point and default command
ENTRYPOINT ["/entrypoint.sh", "java", "-Xms128m", "-Xmx2g", "-jar", "/app/xroad-catalog-lister.jar"]
CMD ["--spring.config.location=/app/"]

# Health check. Actuator is served on the management port (8090), not on the API port 8080.
# The start period allows for the X-Road configuration client to come up alongside the web context.
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
  CMD curl -f http://localhost:8090/actuator/health/readiness || exit 1
