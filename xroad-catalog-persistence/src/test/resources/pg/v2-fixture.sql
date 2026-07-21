-- THE canonical Postgres fixture reused by every PG repository test in Phases B-E.
-- Loaded per test method via @Sql(scripts = "classpath:pg/v2-fixture.sql",
-- executionPhase = BEFORE_TEST_METHOD). Explicit ids everywhere (identity columns would
-- otherwise collide with Hibernate's *_ID_SEQ sequences), and setval bumps at the end so
-- entity writes stay collision-free. is_provider/service_type are deliberately NOT set -
-- rows carry the column defaults (is_provider=false, service_type=UNKNOWN, i.e. the
-- freshly-collected state) until a test runs the recompute, which doubles as recompute
-- verification. Tests that assert on either column must recompute first; see the
-- @BeforeEach in the PG repository tests.
--
-- Ground truth encoded by this fixture (referenced by every later PG test):
-- is_provider: M1=true, M2=false (no services), M3=false (removed), M4=false (only a
-- removed subsystem).
-- service_type: 21=SOAP, 22=OPENAPI (WSDL removed), 23=REST (rest row 36), 24/25/26=UNKNOWN
-- (no active descriptor and no rest row), 27=SOAP (anomaly: active WSDL and OpenAPI).
TRUNCATE endpoint, rest, open_api, wsdl, service, subsystem, member, error_log RESTART IDENTITY CASCADE;

INSERT INTO member (id, x_road_instance, member_class, member_code, name, created, changed, fetched, removed) VALUES
  (1, 'TEST', 'GOV', 'M1', 'Provider Member',           '2025-01-01 10:00', '2025-01-01 10:00', '2025-06-01 10:00', NULL),
  (2, 'TEST', 'GOV', 'M2', 'Empty Member',              '2025-02-01 10:00', '2025-02-01 10:00', '2025-06-01 10:00', NULL),
  (3, 'TEST', 'COM', 'M3', 'Removed Member',            '2025-01-01 10:00', '2025-03-01 10:00', '2025-03-01 10:00', '2025-03-01 10:00'),
  (4, 'TEST', 'COM', 'M4', 'Removed-subsystem Member',  '2025-01-01 10:00', '2025-01-01 10:00', '2025-06-01 10:00', NULL);

INSERT INTO subsystem (id, member_id, subsystem_code, created, changed, fetched, removed) VALUES
  (11, 1, 'SS1', '2025-01-02 10:00', '2025-01-02 10:00', '2025-06-01 10:00', NULL),
  (12, 1, 'SS2', '2025-01-02 10:00', '2025-02-15 10:00', '2025-02-15 10:00', '2025-02-15 10:00'),
  (13, 3, 'SS3', '2025-01-02 10:00', '2025-01-02 10:00', '2025-06-01 10:00', NULL),
  (14, 4, 'SS4', '2025-01-02 10:00', '2025-02-20 10:00', '2025-02-20 10:00', '2025-02-20 10:00');

INSERT INTO service (id, subsystem_id, service_code, service_version, created, changed, fetched, removed) VALUES
  (21, 11, 'svcA', '1.0', '2025-01-03 10:00', '2025-01-03 10:00', '2025-06-01 10:00', NULL),
  (22, 11, 'svcA', '2.0', '2025-01-04 10:00', '2025-01-04 10:00', '2025-06-01 10:00', NULL),
  (23, 11, 'svcB', NULL,  '2025-01-05 10:00', '2025-01-05 10:00', '2025-06-01 10:00', NULL),
  (24, 11, 'svcC', '1.0', '2025-01-06 10:00', '2025-04-01 10:00', '2025-04-01 10:00', '2025-04-01 10:00'),
  (25, 13, 'svcD', '1.0', '2025-01-07 10:00', '2025-01-07 10:00', '2025-06-01 10:00', NULL),
  (26, 14, 'svcE', '1.0', '2025-01-08 10:00', '2025-01-08 10:00', '2025-06-01 10:00', NULL),
  (27, 11, 'svcF', '1.0', '2025-01-09 10:00', '2025-01-09 10:00', '2025-06-01 10:00', NULL);

INSERT INTO wsdl (id, service_id, data, external_id, created, changed, fetched, removed) VALUES
  (31, 21, '<wsdl>svcA-1</wsdl>', 'ext-w-31', '2025-01-03 11:00', '2025-01-03 11:00', '2025-06-01 10:00', NULL),
  (32, 22, '<wsdl>svcA-2</wsdl>', 'ext-w-32', '2025-01-04 11:00', '2025-02-01 10:00', '2025-02-01 10:00', '2025-02-01 10:00'),
  (34, 27, '<wsdl>svcF</wsdl>',   'ext-w-34', '2025-01-09 11:00', '2025-01-09 11:00', '2025-06-01 10:00', NULL);

INSERT INTO open_api (id, service_id, data, external_id, created, changed, fetched, removed) VALUES
  (33, 22, '{"openapi":"3.0.0"}', 'ext-o-33', '2025-01-04 12:00', '2025-01-04 12:00', '2025-06-01 10:00', NULL),
  (35, 27, '{"openapi":"3.0.0"}', 'ext-o-35', '2025-01-09 12:00', '2025-01-09 12:00', '2025-06-01 10:00', NULL);

INSERT INTO rest (id, service_id, data, external_id, created, changed, fetched, removed) VALUES
  (36, 23, '{"endpoint_list":[]}', 'ext-r-36', '2025-01-05 11:00', '2025-01-05 11:00', '2025-06-01 10:00', NULL);

INSERT INTO endpoint (id, service_id, method, path, created, changed, fetched, removed) VALUES
  (41, 23, 'GET',  '/foo', '2025-01-05 11:00', '2025-01-05 11:00', '2025-06-01 10:00', NULL),
  (42, 23, 'POST', '/bar', '2025-01-05 11:00', '2025-03-01 10:00', '2025-03-01 10:00', '2025-03-01 10:00');

SELECT setval('member_id_seq', 1000);
SELECT setval('subsystem_id_seq', 1000);
SELECT setval('service_id_seq', 1000);
SELECT setval('wsdl_id_seq', 1000);
SELECT setval('open_api_id_seq', 1000);
SELECT setval('rest_id_seq', 1000);
SELECT setval('endpoint_id_seq', 1000);
SELECT setval('error_log_id_seq', 1000);
