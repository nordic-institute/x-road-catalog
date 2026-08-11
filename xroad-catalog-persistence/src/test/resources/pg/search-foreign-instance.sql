-- Layered on top of pg/v2-fixture.sql: a member/subsystem/service tree belonging to a different
-- X-Road instance ('OTHER'), the shape a repointed environment or imported data leaves behind.
-- Every name matches the same queries as the 'TEST' rows, so a search that is not instance-scoped
-- returns these rows and the follow-up /browse call 404s.
INSERT INTO member (id, x_road_instance, member_class, member_code, name, created, changed, fetched, removed) VALUES
  (5, 'OTHER', 'GOV', 'M5', 'Provider Member', '2025-01-01 10:00', '2025-01-01 10:00', '2025-06-01 10:00', NULL);

INSERT INTO subsystem (id, member_id, subsystem_code, created, changed, fetched, removed) VALUES
  (15, 5, 'SS5', '2025-01-02 10:00', '2025-01-02 10:00', '2025-06-01 10:00', NULL);

INSERT INTO service (id, subsystem_id, service_code, service_version, created, changed, fetched, removed) VALUES
  (28, 15, 'svcG', '1.0', '2025-01-10 10:00', '2025-01-10 10:00', '2025-06-01 10:00', NULL);

INSERT INTO rest (id, service_id, data, external_id, created, changed, fetched, removed) VALUES
  (37, 28, '{"endpoint_list":[]}', 'ext-r-37', '2025-01-10 11:00', '2025-01-10 11:00', '2025-06-01 10:00', NULL);
