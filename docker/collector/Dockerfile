FROM eclipse-temurin:21-jre-alpine

COPY xroad-catalog-collector/build/libs/xroad-catalog-collector-*.jar /app/xroad-catalog-collector.jar

VOLUME /etc/xroad/xroad-catalog

ENV CATALOG_PROFILE=default

CMD java -Xms128m -Xmx2g -Dspring.profiles.active=base,production -Dspring.profiles.include=$CATALOG_PROFILE -jar /app/xroad-catalog-collector.jar --spring.config.location=/etc/xroad/xroad-catalog/ --spring.config.name=collector,catalogdb
