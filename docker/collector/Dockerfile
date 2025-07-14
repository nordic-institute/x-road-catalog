# Base image for X-Road Catalog
FROM eclipse-temurin:21-jre-alpine AS base-xroad-catalog

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

# Create necessary directories and set permissions
RUN mkdir -p /etc/xroad \
    && chown -R xroad:xroad /app /etc/xroad

# Define volumes
VOLUME /etc/xroad

# -------------------- Separator: Start of xroad-catalog-collector image --------------------
# Final image for X-Road Catalog Collector
FROM base-xroad-catalog AS xroad-catalog-collector

RUN apk add --no-cache curl && \
    curl -L https://github.com/mikefarah/yq/releases/latest/download/yq_linux_amd64 \
    -o /usr/bin/yq && \
    chmod +x /usr/bin/yq

# Copy application JAR file
COPY xroad-catalog-collector/build/libs/xroad-catalog-collector-*.jar /app/xroad-catalog-collector.jar
COPY docker/docker-entrypoint.sh /entrypoint.sh
COPY docker/collector/config/ /app/

# Switch to non-root user
USER xroad

# Define entry point and default command
ENTRYPOINT ["/entrypoint.sh", "java", "-Xms128m", "-Xmx2g", "-jar", "/app/xroad-catalog-collector.jar"]
CMD ["--spring.config.location=/app/"]

# Health check
#HEALTHCHECK --interval=30s --timeout=10s --start-period=30s --retries=3 \
#  CMD curl -f http://localhost:8080/actuator/health || exit 1
