-- Pre-creates the two application roles that the Liquibase 'users' context grants privileges to.
-- Liquibase never creates database roles; on real installs the operator creates them the same way.
CREATE ROLE xroad_catalog_collector WITH
    LOGIN
    NOSUPERUSER
    NOCREATEDB
    NOCREATEROLE
    NOINHERIT
    NOREPLICATION
    NOBYPASSRLS
    CONNECTION LIMIT -1
    PASSWORD 'xroad_catalog_collector';

CREATE ROLE xroad_catalog_lister WITH
    LOGIN
    NOSUPERUSER
    NOCREATEDB
    NOCREATEROLE
    NOINHERIT
    NOREPLICATION
    NOBYPASSRLS
    CONNECTION LIMIT -1
    PASSWORD 'xroad_catalog_lister';
