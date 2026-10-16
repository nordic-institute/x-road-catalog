-- Minimal fixture for ProductionV2WiringIntegrationTest: proves MemberRepositoryV2 resolves and
-- executes a real query (not just that the context boots) against the genuine Liquibase schema.
TRUNCATE endpoint, rest, open_api, wsdl, service, subsystem, member RESTART IDENTITY CASCADE;

INSERT INTO member (id, x_road_instance, member_class, member_code, name, created, changed, fetched, removed) VALUES
  (1, 'TEST-WIRING', 'GOV', 'M1', 'Wiring Test Member', '2025-01-01 10:00', '2025-01-01 10:00', '2025-06-01 10:00', NULL);

SELECT setval('member_id_seq', 1000);
