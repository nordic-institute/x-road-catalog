FROM eclipse-temurin:21-jre-alpine

RUN addgroup -S xroad && adduser -S xroad -G xroad

WORKDIR /app

RUN mkdir -p /etc/xroad

VOLUME /etc/xroad

COPY xroad-catalog-lister/build/libs/xroad-catalog-lister-*.jar /app/xroad-catalog-lister.jar

RUN chown -R xroad:xroad /app /etc/xroad

USER xroad

ENTRYPOINT ["java", "-Xms128m", "-Xmx2g", "-jar", "/app/xroad-catalog-lister.jar"]

CMD ["--spring.config.location=/etc/xroad/xroad-catalog/"]

#HEALTHCHECK --interval=30s --timeout=10s --start-period=30s --retries=3 \
#  CMD curl -f http://localhost:8080/actuator/health || exit 1
