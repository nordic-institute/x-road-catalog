-- manipulate sequences
ALTER SEQUENCE member_id_seq RESTART WITH 1000;
ALTER SEQUENCE subsystem_id_seq RESTART WITH 1000;
ALTER SEQUENCE service_id_seq RESTART WITH 1000;
ALTER SEQUENCE wsdl_id_seq RESTART WITH 1000;

INSERT INTO member (id, x_road_instance, member_class, member_code, name, created, changed, fetched,  removed)
VALUES (1, 'dev-cs', 'PUB', '14151328', 'Nahka-Albert', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', NULL);
INSERT INTO member (id, x_road_instance, member_class, member_code, name, created, changed, fetched,  removed)
VALUES (2, 'dev-cs', 'PUB', '88855888', 'Suutari Simo', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', NULL);
INSERT INTO member (id, x_road_instance, member_class, member_code, name, created, changed, fetched,  removed)
VALUES (3, 'dev-cs', 'PUB', '11', 'Updated Member', '2016-01-01 00:00:00+02', '2017-02-01 00:00:00+02', '2016-01-01 00:00:00+02', NULL);
INSERT INTO member (id, x_road_instance, member_class, member_code, name, created, changed, fetched,  removed)
VALUES (4, 'dev-cs', 'PUB', '12', 'Updated Subsystem', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', NULL);
INSERT INTO member (id, x_road_instance, member_class, member_code, name, created, changed, fetched,  removed)
VALUES (5, 'dev-cs', 'PUB', '13', 'Updated Service', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', NULL);
INSERT INTO member (id, x_road_instance, member_class, member_code, name, created, changed, fetched,  removed)
VALUES (6, 'dev-cs', 'PUB', '14', 'Updated Wsdl', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', NULL);
INSERT INTO member (id, x_road_instance, member_class, member_code, name, created, changed, fetched,  removed)
VALUES (7, 'dev-cs', 'PUB', '15', 'Updated Everything', '2016-01-01 00:00:00+02', '2017-01-02 00:00:00+02', '2017-01-02 00:00:00+02', NULL);
INSERT INTO member (id, x_road_instance, member_class, member_code, name, created, changed, fetched,  removed)
VALUES (8, 'dev-cs', 'PUB', '14151329', 'Removed item', '2016-01-01 00:00:00+02', '2017-01-02 00:00:00+02', '2017-01-02 00:00:00+02', '2017-01-02 00:00:00+02');

-- V2 Task 1.5 isProvider invariant fixtures (see persistence test-data-template for details).
INSERT INTO member (id, x_road_instance, member_class, member_code, name, created, changed, fetched, removed)
VALUES (20, 'dev-cs', 'PUB', 'only-removed-service', 'Only removed service member',
        '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', NULL);
INSERT INTO member (id, x_road_instance, member_class, member_code, name, created, changed, fetched, removed)
VALUES (21, 'dev-cs', 'PUB', 'svc-under-removed-sub', 'Service under removed subsystem member',
        '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', NULL);
INSERT INTO member (id, x_road_instance, member_class, member_code, name, created, changed, fetched, removed)
VALUES (22, 'dev-cs', 'PUB', 'removed-with-stale', 'Removed member with stale active children',
        '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02');

-- member 1 has 3 subsystems, 2 active and 1 removed one
INSERT INTO subsystem (id, member_id, subsystem_code, created, changed, fetched,  removed)
VALUES (1, 1, 'subsystem_a1', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', NULL);
INSERT INTO subsystem (id, member_id, subsystem_code, created, changed, fetched,  removed)
VALUES (2, 1, 'subsystem_a2', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', NULL);
INSERT INTO subsystem (id, member_id, subsystem_code, created, changed, fetched,  removed)
VALUES (3, 2, 'subsystem_b1', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', NULL);
INSERT INTO subsystem (id, member_id, subsystem_code, created, changed, fetched,  removed)
VALUES (4, 3, 'subsystem_3-1', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', NULL);
INSERT INTO subsystem (id, member_id, subsystem_code, created, changed, fetched,  removed)
VALUES (5, 4, 'subsystem_4-1-changed', '2016-01-01 00:00:00+02', '2017-01-02 00:00:00+02', '2017-01-02 00:00:00+02', NULL);
INSERT INTO subsystem (id, member_id, subsystem_code, created, changed, fetched,  removed)
VALUES (6, 5, 'subsystem_5-1', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', NULL);
INSERT INTO subsystem (id, member_id, subsystem_code, created, changed, fetched,  removed)
VALUES (7, 6, 'subsystem_6-1', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', NULL);
INSERT INTO subsystem (id, member_id, subsystem_code, created, changed, fetched,  removed)
VALUES (8, 7, 'subsystem_7-1', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', NULL);
INSERT INTO subsystem (id, member_id, subsystem_code, created, changed, fetched,  removed)
VALUES (9, 7, 'subsystem_7-2-changed', '2016-01-01 00:00:00+02', '2017-01-02 00:00:00+02', '2017-01-02 00:00:00+02', NULL);
INSERT INTO subsystem (id, member_id, subsystem_code, created, changed, fetched,  removed)
VALUES (10, 7, 'subsystem_7-3', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', NULL);
INSERT INTO subsystem (id, member_id, subsystem_code, created, changed, fetched,  removed)
VALUES (11, 8, 'removed_subsystem', '2016-01-01 00:00:00+02', '2017-01-02 00:00:00+02', '2017-01-02 00:00:00+02', '2017-01-02 00:00:00+02');
INSERT INTO subsystem (id, member_id, subsystem_code, created, changed, fetched,  removed)
VALUES (12, 1, 'subsystem_a3_removed', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02');

-- V2 Task 1.5 isProvider invariant subsystems (paired with members 20-22 above).
INSERT INTO subsystem (id, member_id, subsystem_code, created, changed, fetched, removed)
VALUES (20, 20, 'sub_only_removed_svc', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02',
        '2016-01-01 00:00:00+02', NULL);
INSERT INTO subsystem (id, member_id, subsystem_code, created, changed, fetched, removed)
VALUES (21, 21, 'sub_removed_parent', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02',
        '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02');
INSERT INTO subsystem (id, member_id, subsystem_code, created, changed, fetched, removed)
VALUES (22, 22, 'sub_stale_under_removed_member', '2016-01-01 00:00:00+02',
        '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', NULL);

INSERT INTO service (id, subsystem_id, service_code, service_version, created, changed, fetched,  removed)
VALUES (1, 2, 'testService', 'v1', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', NULL);
INSERT INTO service (id, subsystem_id, service_code, service_version, created, changed, fetched,  removed)
VALUES (2, 1, 'getRandom', 'v1', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', NULL);
INSERT INTO service (id, subsystem_id, service_code, service_version, created, changed, fetched,  removed)
VALUES (3, 6, 'dummy-service_5-1-1-changed', 'v1', '2016-01-01 00:00:00+02', '2017-01-02 00:00:00+02', '2017-01-02 00:00:00+02', NULL);
INSERT INTO service (id, subsystem_id, service_code, service_version, created, changed, fetched,  removed)
VALUES (4, 7, 'dummy-service_6-1-1', 'v1', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', NULL);
INSERT INTO service (id, subsystem_id, service_code, service_version, created, changed, fetched,  removed)
VALUES (5, 8, 'dummy-service_7-1-1-changed', 'v1', '2016-01-01 00:00:00+02', '2017-01-02 00:00:00+02', '2017-01-02 00:00:00+02', NULL);
INSERT INTO service (id, subsystem_id, service_code, service_version, created, changed, fetched,  removed)
VALUES (6, 8, 'dummy-service_7-1-2', 'v1', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', NULL);
INSERT INTO service (id, subsystem_id, service_code, service_version, created, changed, fetched,  removed)
VALUES (7, 9, 'dummy-service_7-2-1', 'v1', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', NULL);
INSERT INTO service (id, subsystem_id, service_code, service_version, created, changed, fetched,  removed)
VALUES (8, 8, 'removed-service_7-1-3', 'v1', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02');
INSERT INTO service (id, subsystem_id, service_code, service_version, created, changed, fetched,  removed)
VALUES (9, 8, 'removed-service_7-1-4', 'v1', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02');
INSERT INTO service (id, subsystem_id, service_code, service_version, created, changed, fetched,  removed)
VALUES (10, 8, 'service-with-null-version', NULL, '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', NULL);
INSERT INTO service (id, subsystem_id, service_code, service_version, created, changed, fetched,  removed)
VALUES (11, 8, 'removed-service_7-1-5', 'v1', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02');
INSERT INTO service (id, subsystem_id, service_code, service_version, created, changed, fetched,  removed)
VALUES (12, 8, 'dummy-service_7-1-5', 'v1', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', NULL);
INSERT INTO service (id, subsystem_id, service_code, service_version, created, changed, fetched,  removed)
VALUES (13, 8, 'dummy-service_7-1-6', 'v1', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', NULL);
INSERT INTO service (id, subsystem_id, service_code, service_version, created, changed, fetched,  removed)
VALUES (14, 8, 'dummy-service_7-1-7', null, '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02');

-- Mixed-type service fixture for V2 Task 8 testing: two versions of 'mixedSvc' under member 1 (Nahka-Albert) / subsystem_a1.
-- v1 has a WSDL (SOAP) and v2 has a REST descriptor. Filtering by serviceType=SOAP must still report versionCount=2.
INSERT INTO service (id, subsystem_id, service_code, service_version, created, changed, fetched, removed)
VALUES (20, 1, 'mixedSvc', 'v1', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', NULL);
INSERT INTO service (id, subsystem_id, service_code, service_version, created, changed, fetched, removed)
VALUES (21, 1, 'mixedSvc', 'v2', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', NULL);

-- V2 Task 1.5 isProvider invariant services (paired with members/subsystems 20-22 above).
INSERT INTO service (id, subsystem_id, service_code, service_version, created, changed, fetched, removed)
VALUES (30, 20, 'only_removed_svc', 'v1', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02',
        '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02');
INSERT INTO service (id, subsystem_id, service_code, service_version, created, changed, fetched, removed)
VALUES (31, 21, 'svc_under_removed_sub', 'v1', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02',
        '2016-01-01 00:00:00+02', NULL);
INSERT INTO service (id, subsystem_id, service_code, service_version, created, changed, fetched, removed)
VALUES (32, 22, 'svc_under_removed_member', 'v1', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02',
        '2016-01-01 00:00:00+02', NULL);
INSERT INTO service (id, subsystem_id, service_code, service_version, created, changed, fetched, removed)
VALUES (33, 1, 'svc_removed_wsdl_only', 'v1', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02',
        '2016-01-01 00:00:00+02', NULL);

-- V2 Task 6 descriptor-endpoint fixtures (single-version services under member 1 / subsystem_a1).
-- Each row pairs with an OpenAPI / WSDL row below to exercise one branch of the descriptor route:
--   service 40 = descJsonSvc          : OpenAPI JSON  -> 200 + application/json
--   service 41 = descYamlSvc          : OpenAPI YAML  -> 200 + application/yaml
--   service 42 = descRestOnlySvc      : no descriptor -> 404 (REST classifier still applies)
--   service 43 = descRemovedWsdlSvc   : only-removed WSDL -> 404 (active-row contract from Task 1.5)
INSERT INTO service (id, subsystem_id, service_code, service_version, created, changed, fetched, removed)
VALUES (40, 1, 'descJsonSvc', 'v1', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02',
        '2016-01-01 00:00:00+02', NULL);
INSERT INTO service (id, subsystem_id, service_code, service_version, created, changed, fetched, removed)
VALUES (41, 1, 'descYamlSvc', 'v1', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02',
        '2016-01-01 00:00:00+02', NULL);
INSERT INTO service (id, subsystem_id, service_code, service_version, created, changed, fetched, removed)
VALUES (42, 1, 'descRestOnlySvc', 'v1', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02',
        '2016-01-01 00:00:00+02', NULL);
INSERT INTO service (id, subsystem_id, service_code, service_version, created, changed, fetched, removed)
VALUES (43, 1, 'descRemovedWsdlSvc', 'v1', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02',
        '2016-01-01 00:00:00+02', NULL);

INSERT INTO wsdl (id, service_id, data, external_id, created, changed, fetched,  removed)
VALUES (3, 4, '<?xml version="1.0" standalone="no"?><wsdl-6-1-1-1-changed/>', '1000', '2016-01-01 00:00:00+02', '2017-01-02 00:00:00+02', '2017-01-02 00:00:00+02', NULL);
INSERT INTO wsdl (id, service_id, data, external_id, created, changed, fetched,  removed)
VALUES (4, 6, '<?xml version="1.0" standalone="no"?><wsdl-7-1-2-1-changed/>', '1001', '2016-01-01 00:00:00+02', '2017-01-02 00:00:00+02', '2017-01-02 00:00:00+02', NULL);
INSERT INTO wsdl (id, service_id, data, external_id, created, changed, fetched,  removed)
VALUES (5, 7, '<?xml version="1.0" standalone="no"?><wsdl-7-2-1-1/>', '1002', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', NULL);
INSERT INTO wsdl (id, service_id, data, external_id, created, changed, fetched,  removed)
VALUES (6, 8, '<?xml version="1.0" standalone="no"?><removed-service_7-1-3-alive-wsdl/>', '3000', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', NULL);
INSERT INTO wsdl (id, service_id, data, external_id, created, changed, fetched,  removed)
VALUES (7, 9, '<?xml version="1.0" standalone="no"?><removed-service_7-1-4-removed-wsdl/>', '3001', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02');

INSERT INTO wsdl (id, service_id, data, external_id, created, changed, fetched,  removed)
VALUES (8, 4, '<?xml version="1.0" standalone="no"?><wsdl-6-1-1-1-changed/>', '9998', '2016-01-01 00:00:00+02', '2017-01-02 00:00:00+02', '2017-01-02 00:00:00+02', NULL);
INSERT INTO wsdl (id, service_id, data, external_id, created, changed, fetched,  removed)
VALUES (9, 4, '<?xml version="1.0" standalone="no"?><wsdl-61-1-1-1-changed/>', '9999', '2016-01-01 00:00:00+02', '2017-01-02 00:00:00+02', '2017-01-02 00:00:00+02', NULL);

INSERT INTO open_api (id, service_id, data, external_id, created, changed, fetched, removed)
VALUES (1, 11, '<openapi>', '3003', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', '2016-01-02 00:00:00+02', '2016-01-01 00:00:00+02');
INSERT INTO open_api (id, service_id, data, external_id, created, changed, fetched, removed)
VALUES (2, 12, '<openapi>', '3004', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', '2016-01-02 00:00:00+02', NULL);
INSERT INTO open_api (id, service_id, data, external_id, created, changed, fetched, removed)
VALUES (3, 13, '<openapi>', '3005', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', '2016-01-02 00:00:00+02', NULL);

INSERT INTO rest (id, service_id, data, external_id, created, changed, fetched, removed)
VALUES (1, 13, '{"endpoint_list": []}}', '3003', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', NULL);
INSERT INTO rest (id, service_id, data, external_id, created, changed, fetched, removed)
VALUES (2, 14, '{"endpoint_list": []}}', '3004', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02');
INSERT INTO rest (id, service_id, data, external_id, created, changed, fetched, removed)
VALUES (3, 1, '{"endpoint_list": []}}', '3005', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02');
INSERT INTO rest (id, service_id, data, external_id, created, changed, fetched, removed)
VALUES (4, 1, '{"endpoint_list": []}}', '3006', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02');

-- Descriptors for V2 Task 8 mixedSvc fixture (service id 20 has WSDL, service id 21 has REST).
INSERT INTO wsdl (id, service_id, data, external_id, created, changed, fetched, removed)
VALUES (20, 20, '<?xml version="1.0" standalone="no"?><wsdl-mixedSvc-v1/>', 'mixedSvc-v1',
        '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', NULL);
INSERT INTO rest (id, service_id, data, external_id, created, changed, fetched, removed)
VALUES (20, 21, '{"endpoint_list": []}', 'mixedSvc-v2',
        '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', NULL);

-- V2 Task 1.5 aggregate-classification fixture: service 33 has a removed WSDL -> must classify as REST.
INSERT INTO wsdl (id, service_id, data, external_id, created, changed, fetched, removed)
VALUES (33, 33, '<?xml version="1.0" standalone="no"?><removed-wsdl-only/>', 'svc_removed_wsdl_only-w1',
        '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02');

-- V2 Task 6 descriptor fixtures: see service rows 40-43 above for intent.
-- service 40 -> active OpenAPI JSON (must be parseable by ObjectMapper.readTree -> application/json)
INSERT INTO open_api (id, service_id, data, external_id, created, changed, fetched, removed)
VALUES (200, 40, '{"openapi":"3.0.0","info":{"title":"Test"},"paths":{}}', 'descJsonSvc-v1-openapi',
        '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', NULL);
-- service 41 -> active OpenAPI YAML (ObjectMapper.readTree throws -> application/yaml fallback)
INSERT INTO open_api (id, service_id, data, external_id, created, changed, fetched, removed)
VALUES (201, 41, 'openapi: 3.0.0
info:
  title: Test
paths: {}
', 'descYamlSvc-v1-openapi',
        '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', NULL);
-- service 43 -> only a removed WSDL row, used to assert 404 on the descriptor endpoint (active-row contract)
INSERT INTO wsdl (id, service_id, data, external_id, created, changed, fetched, removed)
VALUES (43, 43, '<?xml version="1.0" standalone="no"?><removed-only-wsdl-desc/>', 'descRemovedWsdlSvc-v1',
        '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02');

INSERT INTO endpoint (id, service_id, method, path, created, changed, fetched, removed)
VALUES (1, 13, 'GET', '/getData', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', NULL);
INSERT INTO endpoint (id, service_id, method, path, created, changed, fetched, removed)
VALUES (2, 13, 'POST', '/setData', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', NULL);
INSERT INTO endpoint (id, service_id, method, path, created, changed, fetched, removed)
VALUES (3, 12, 'POST', '/setOtherData', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02');

INSERT INTO error_log(id, message, code, created)
VALUES (1, 'Service not found', '500', '2020-05-04 11:41:24.792+03');

INSERT INTO error_log(id, message, code, created, x_road_instance, member_class, member_code, subsystem_code)
VALUES (2, 'Service not found', '500', '2020-05-04 11:41:24.792+03', 'DEV', 'GOV', '1234', 'TestSubsystem');

INSERT INTO error_log(id, message, code, created, x_road_instance, member_class, member_code)
VALUES (3, 'Service not found2', '500', '2020-05-04 11:41:24.792+03', 'DEV', 'GOV', '1234');

INSERT INTO error_log(id, message, code, created, x_road_instance, member_class)
VALUES (4, 'Service not found3', '500', '2020-05-04 11:41:24.792+03', 'DEV', 'GOV');

INSERT INTO error_log(id, message, code, created, x_road_instance)
VALUES (5, 'Service not found6', '500', '2020-05-04 11:41:24.792+03', 'DEV');

INSERT INTO error_log(id, message, code, created)
VALUES (6, 'Service not found6', '500', '2020-05-04 11:41:24.792+03');

INSERT INTO error_log(id, message, code, created)
VALUES (7, 'Service not found7', '500', '2022-01-01 11:41:24.792+03');

INSERT INTO error_log(id, message, code, created, x_road_instance, member_class, member_code, subsystem_code, service_code, service_version)
VALUES (8, 'Fetch of WSDL failed', '500', '2020-05-04 11:41:24.792+03', 'DEV', 'GOV', '1234', 'TestSubsystem', 'testService', 'v1');

INSERT INTO error_log(id, message, code, created, x_road_instance, member_class, member_code, subsystem_code, service_code, service_version)
VALUES (9, 'Fetch of WSDL failed', '500', '2020-05-04 11:41:24.792+03', 'DEV', 'GOV', '1234', 'TestSubsystem', 'testService', 'v2');

INSERT INTO error_log(id, message, code, created, x_road_instance, member_class, member_code, subsystem_code, service_code)
VALUES (10, 'Fetch of REST services failed', '500', '2020-05-04 11:41:24.792+03', 'DEV', 'GOV', '1234', 'TestSubsystem', 'restService');

INSERT INTO wsdl (id, service_id, data, external_id, created, changed, fetched,  removed)
VALUES (1, 1, @file('src/test/resources/wsdl/TestService.wsdl'), '1003', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', NULL);

INSERT INTO wsdl (id, service_id, data, external_id, created, changed, fetched, removed)
VALUES (2, 2, @file('src/test/resources/wsdl/TestService2.wsdl'), '2050', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', NULL);
