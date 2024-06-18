FROM eclipse-temurin:21-jre-alpine

COPY xroad-catalog-lister/build/libs/xroad-catalog-lister-*.jar /app/xroad-catalog-lister.jar

VOLUME /etc/xroad/xroad-catalog

ENV CATALOG_PROFILE=default

CMD java -Xms128m -Xmx2g -Dserver.port=8070 -Dspring.profiles.active=production -Dspring.profiles.include=$CATALOG_PROFILE -jar /app/xroad-catalog-lister.jar --spring.config.location=/etc/xroad/xroad-catalog/ --spring.config.name=lister,catalogdb
