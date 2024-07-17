DROP DATABASE xroad_catalog;

DROP USER xroad_catalog;

CREATE USER xroad_catalog
    WITH PASSWORD 'secret';

CREATE DATABASE xroad_catalog
    WITH TEMPLATE = template0
    ENCODING = 'UTF8'
    LC_COLLATE = 'en_US.UTF-8'
    LC_CTYPE = 'en_US.UTF-8'
    OWNER xroad_catalog;
