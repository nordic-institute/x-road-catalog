-- manipulate sequences
ALTER SEQUENCE member_id_seq RESTART WITH 1000;
ALTER SEQUENCE subsystem_id_seq RESTART WITH 1000;
ALTER SEQUENCE service_id_seq RESTART WITH 1000;
ALTER SEQUENCE wsdl_id_seq RESTART WITH 1000;
ALTER SEQUENCE error_log_id_seq RESTART WITH 1000;

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

INSERT INTO wsdl (id, service_id, data, external_id, created, changed, fetched,  removed)
VALUES (1, 1, @file('src/test/resources/wsdl/TestService.wsdl'), '1003', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', NULL);

INSERT INTO wsdl (id, service_id, data, external_id, created, changed, fetched, removed)
VALUES (2, 2, @file('src/test/resources/wsdl/TestService2.wsdl'), '2050', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', NULL);
