FROM eclipse-temurin:21-jre-alpine

COPY xroad-catalog-collector/build/libs/xroad-catalog-collector-*.jar /app/xroad-catalog-collector.jar

VOLUME /etc/xroad/xroad-catalog

CMD java -Xms128m -Xmx2g -jar /app/xroad-catalog-collector.jar --spring.config.location=/etc/xroad/xroad-catalog/
