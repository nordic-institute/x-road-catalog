CREATE ROLE ${users.collector.username} NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT LOGIN NOREPLICATION NOBYPASSRLS CONNECTION LIMIT -1 PASSWORD '${users.collector.password}';
GRANT USAGE ON SCHEMA public TO ${users.collector.username};
GRANT ALL ON ALL TABLES IN SCHEMA public TO ${users.collector.username};
GRANT ALL ON ALL FUNCTIONS IN SCHEMA public TO ${users.collector.username};
GRANT ALL ON ALL SEQUENCES IN SCHEMA public TO ${users.collector.username};
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON TABLES TO ${users.collector.username};
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON FUNCTIONS TO ${users.collector.username};
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON SEQUENCES TO ${users.collector.username};