GRANT USAGE ON SCHEMA public TO ${users.lister.username};
GRANT SELECT ON ALL TABLES IN SCHEMA public TO ${users.lister.username};
GRANT EXECUTE ON ALL FUNCTIONS IN SCHEMA public TO ${users.lister.username};
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT ON TABLES TO ${users.lister.username};
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT EXECUTE ON FUNCTIONS TO ${users.lister.username};
