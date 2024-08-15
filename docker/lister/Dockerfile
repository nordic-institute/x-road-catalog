FROM eclipse-temurin:21-jre-alpine

COPY xroad-catalog-lister/build/libs/xroad-catalog-lister-*.jar /app/xroad-catalog-lister.jar

VOLUME /etc/xroad/xroad-catalog

CMD java -Xms128m -Xmx2g -jar /app/xroad-catalog-lister.jar --spring.config.location=/etc/xroad/xroad-catalog/
