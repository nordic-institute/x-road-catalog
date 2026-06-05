-- Additive synthetic rows used ONLY by RepositoryQueryCountTest to exercise
-- query-count invariance assertions. Do NOT load via the shared
-- general-testdata profile - loading only via @Sql(BEFORE_TEST_METHOD) keeps
-- Phase 3 tests (which rely on exact row counts in test-data-template.sql) safe.
--
-- ID space starts at 10000 to avoid collision with the existing sequences
-- (member/subsystem/service/wsdl sequences are reset to 1000 by the shared
-- template; synthetic IDs stay well above that boundary so rollback-per-test
-- never clashes with sequence-generated inserts from other tests).

-- Scenario A: MemberRepositoryV2.findForList (page 1 vs page 20).
-- The shared template ships 7 active members; findForList page size 20
-- would return all rows regardless of pagination without extra fixture.
-- 21 synthetic active PUB members force genuine page differences at size=20.
INSERT INTO member (id, x_road_instance, member_class, member_code, name, created, changed, fetched, removed)
VALUES (10000, 'dev-cs', 'PUB', 'qc-0', 'QC Member 0', '2016-01-01 00:00:00+02',
        '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', NULL);
INSERT INTO member (id, x_road_instance, member_class, member_code, name, created, changed, fetched, removed)
VALUES (10001, 'dev-cs', 'PUB', 'qc-1', 'QC Member 1', '2016-01-01 00:00:00+02',
        '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', NULL);
INSERT INTO member (id, x_road_instance, member_class, member_code, name, created, changed, fetched, removed)
VALUES (10002, 'dev-cs', 'PUB', 'qc-2', 'QC Member 2', '2016-01-01 00:00:00+02',
        '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', NULL);
INSERT INTO member (id, x_road_instance, member_class, member_code, name, created, changed, fetched, removed)
VALUES (10003, 'dev-cs', 'PUB', 'qc-3', 'QC Member 3', '2016-01-01 00:00:00+02',
        '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', NULL);
INSERT INTO member (id, x_road_instance, member_class, member_code, name, created, changed, fetched, removed)
VALUES (10004, 'dev-cs', 'PUB', 'qc-4', 'QC Member 4', '2016-01-01 00:00:00+02',
        '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', NULL);
INSERT INTO member (id, x_road_instance, member_class, member_code, name, created, changed, fetched, removed)
VALUES (10005, 'dev-cs', 'PUB', 'qc-5', 'QC Member 5', '2016-01-01 00:00:00+02',
        '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', NULL);
INSERT INTO member (id, x_road_instance, member_class, member_code, name, created, changed, fetched, removed)
VALUES (10006, 'dev-cs', 'PUB', 'qc-6', 'QC Member 6', '2016-01-01 00:00:00+02',
        '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', NULL);
INSERT INTO member (id, x_road_instance, member_class, member_code, name, created, changed, fetched, removed)
VALUES (10007, 'dev-cs', 'PUB', 'qc-7', 'QC Member 7', '2016-01-01 00:00:00+02',
        '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', NULL);
INSERT INTO member (id, x_road_instance, member_class, member_code, name, created, changed, fetched, removed)
VALUES (10008, 'dev-cs', 'PUB', 'qc-8', 'QC Member 8', '2016-01-01 00:00:00+02',
        '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', NULL);
INSERT INTO member (id, x_road_instance, member_class, member_code, name, created, changed, fetched, removed)
VALUES (10009, 'dev-cs', 'PUB', 'qc-9', 'QC Member 9', '2016-01-01 00:00:00+02',
        '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', NULL);
INSERT INTO member (id, x_road_instance, member_class, member_code, name, created, changed, fetched, removed)
VALUES (10010, 'dev-cs', 'PUB', 'qc-10', 'QC Member 10', '2016-01-01 00:00:00+02',
        '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', NULL);
INSERT INTO member (id, x_road_instance, member_class, member_code, name, created, changed, fetched, removed)
VALUES (10011, 'dev-cs', 'PUB', 'qc-11', 'QC Member 11', '2016-01-01 00:00:00+02',
        '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', NULL);
INSERT INTO member (id, x_road_instance, member_class, member_code, name, created, changed, fetched, removed)
VALUES (10012, 'dev-cs', 'PUB', 'qc-12', 'QC Member 12', '2016-01-01 00:00:00+02',
        '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', NULL);
INSERT INTO member (id, x_road_instance, member_class, member_code, name, created, changed, fetched, removed)
VALUES (10013, 'dev-cs', 'PUB', 'qc-13', 'QC Member 13', '2016-01-01 00:00:00+02',
        '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', NULL);
INSERT INTO member (id, x_road_instance, member_class, member_code, name, created, changed, fetched, removed)
VALUES (10014, 'dev-cs', 'PUB', 'qc-14', 'QC Member 14', '2016-01-01 00:00:00+02',
        '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', NULL);
INSERT INTO member (id, x_road_instance, member_class, member_code, name, created, changed, fetched, removed)
VALUES (10015, 'dev-cs', 'PUB', 'qc-15', 'QC Member 15', '2016-01-01 00:00:00+02',
        '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', NULL);
INSERT INTO member (id, x_road_instance, member_class, member_code, name, created, changed, fetched, removed)
VALUES (10016, 'dev-cs', 'PUB', 'qc-16', 'QC Member 16', '2016-01-01 00:00:00+02',
        '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', NULL);
INSERT INTO member (id, x_road_instance, member_class, member_code, name, created, changed, fetched, removed)
VALUES (10017, 'dev-cs', 'PUB', 'qc-17', 'QC Member 17', '2016-01-01 00:00:00+02',
        '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', NULL);
INSERT INTO member (id, x_road_instance, member_class, member_code, name, created, changed, fetched, removed)
VALUES (10018, 'dev-cs', 'PUB', 'qc-18', 'QC Member 18', '2016-01-01 00:00:00+02',
        '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', NULL);
INSERT INTO member (id, x_road_instance, member_class, member_code, name, created, changed, fetched, removed)
VALUES (10019, 'dev-cs', 'PUB', 'qc-19', 'QC Member 19', '2016-01-01 00:00:00+02',
        '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', NULL);
INSERT INTO member (id, x_road_instance, member_class, member_code, name, created, changed, fetched, removed)
VALUES (10020, 'dev-cs', 'PUB', 'qc-20', 'QC Member 20', '2016-01-01 00:00:00+02',
        '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', NULL);

-- Scenario A continued: attach one active subsystem + one active service per synthetic member so
-- that touchAllChildren(members) walks Member -> Subsystem -> Service and exercises the child
-- fetch path. Without these children the entity graph's deeper paths would not show N+1 behavior.
INSERT INTO subsystem (id, member_id, subsystem_code, created, changed, fetched, removed)
VALUES (10000, 10000, 'qc-sub', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', NULL);
INSERT INTO subsystem (id, member_id, subsystem_code, created, changed, fetched, removed)
VALUES (10001, 10001, 'qc-sub', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', NULL);
INSERT INTO subsystem (id, member_id, subsystem_code, created, changed, fetched, removed)
VALUES (10002, 10002, 'qc-sub', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', NULL);
INSERT INTO subsystem (id, member_id, subsystem_code, created, changed, fetched, removed)
VALUES (10003, 10003, 'qc-sub', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', NULL);
INSERT INTO subsystem (id, member_id, subsystem_code, created, changed, fetched, removed)
VALUES (10004, 10004, 'qc-sub', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', NULL);
INSERT INTO subsystem (id, member_id, subsystem_code, created, changed, fetched, removed)
VALUES (10005, 10005, 'qc-sub', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', NULL);
INSERT INTO subsystem (id, member_id, subsystem_code, created, changed, fetched, removed)
VALUES (10006, 10006, 'qc-sub', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', NULL);
INSERT INTO subsystem (id, member_id, subsystem_code, created, changed, fetched, removed)
VALUES (10007, 10007, 'qc-sub', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', NULL);
INSERT INTO subsystem (id, member_id, subsystem_code, created, changed, fetched, removed)
VALUES (10008, 10008, 'qc-sub', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', NULL);
INSERT INTO subsystem (id, member_id, subsystem_code, created, changed, fetched, removed)
VALUES (10009, 10009, 'qc-sub', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', NULL);
INSERT INTO subsystem (id, member_id, subsystem_code, created, changed, fetched, removed)
VALUES (10010, 10010, 'qc-sub', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', NULL);
INSERT INTO subsystem (id, member_id, subsystem_code, created, changed, fetched, removed)
VALUES (10011, 10011, 'qc-sub', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', NULL);
INSERT INTO subsystem (id, member_id, subsystem_code, created, changed, fetched, removed)
VALUES (10012, 10012, 'qc-sub', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', NULL);
INSERT INTO subsystem (id, member_id, subsystem_code, created, changed, fetched, removed)
VALUES (10013, 10013, 'qc-sub', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', NULL);
INSERT INTO subsystem (id, member_id, subsystem_code, created, changed, fetched, removed)
VALUES (10014, 10014, 'qc-sub', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', NULL);
INSERT INTO subsystem (id, member_id, subsystem_code, created, changed, fetched, removed)
VALUES (10015, 10015, 'qc-sub', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', NULL);
INSERT INTO subsystem (id, member_id, subsystem_code, created, changed, fetched, removed)
VALUES (10016, 10016, 'qc-sub', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', NULL);
INSERT INTO subsystem (id, member_id, subsystem_code, created, changed, fetched, removed)
VALUES (10017, 10017, 'qc-sub', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', NULL);
INSERT INTO subsystem (id, member_id, subsystem_code, created, changed, fetched, removed)
VALUES (10018, 10018, 'qc-sub', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', NULL);
INSERT INTO subsystem (id, member_id, subsystem_code, created, changed, fetched, removed)
VALUES (10019, 10019, 'qc-sub', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', NULL);

-- Scenario B: SubsystemRepositoryV2.findForList (page 1 vs page 20).
-- 21 additional active subsystems parented on member 10020 so the same page-size invariance
-- can be asserted for Subsystem.services (Set<Service> with FetchType.EAGER) without
-- bleeding into scenario A's member count.
INSERT INTO subsystem (id, member_id, subsystem_code, created, changed, fetched, removed)
VALUES (10100, 10020, 'qc-many-sub-0', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', NULL);
INSERT INTO subsystem (id, member_id, subsystem_code, created, changed, fetched, removed)
VALUES (10101, 10020, 'qc-many-sub-1', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', NULL);
INSERT INTO subsystem (id, member_id, subsystem_code, created, changed, fetched, removed)
VALUES (10102, 10020, 'qc-many-sub-2', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', NULL);
INSERT INTO subsystem (id, member_id, subsystem_code, created, changed, fetched, removed)
VALUES (10103, 10020, 'qc-many-sub-3', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', NULL);
INSERT INTO subsystem (id, member_id, subsystem_code, created, changed, fetched, removed)
VALUES (10104, 10020, 'qc-many-sub-4', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', NULL);
INSERT INTO subsystem (id, member_id, subsystem_code, created, changed, fetched, removed)
VALUES (10105, 10020, 'qc-many-sub-5', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', NULL);
INSERT INTO subsystem (id, member_id, subsystem_code, created, changed, fetched, removed)
VALUES (10106, 10020, 'qc-many-sub-6', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', NULL);
INSERT INTO subsystem (id, member_id, subsystem_code, created, changed, fetched, removed)
VALUES (10107, 10020, 'qc-many-sub-7', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', NULL);
INSERT INTO subsystem (id, member_id, subsystem_code, created, changed, fetched, removed)
VALUES (10108, 10020, 'qc-many-sub-8', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', NULL);
INSERT INTO subsystem (id, member_id, subsystem_code, created, changed, fetched, removed)
VALUES (10109, 10020, 'qc-many-sub-9', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', NULL);
INSERT INTO subsystem (id, member_id, subsystem_code, created, changed, fetched, removed)
VALUES (10110, 10020, 'qc-many-sub-10', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', NULL);
INSERT INTO subsystem (id, member_id, subsystem_code, created, changed, fetched, removed)
VALUES (10111, 10020, 'qc-many-sub-11', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', NULL);
INSERT INTO subsystem (id, member_id, subsystem_code, created, changed, fetched, removed)
VALUES (10112, 10020, 'qc-many-sub-12', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', NULL);
INSERT INTO subsystem (id, member_id, subsystem_code, created, changed, fetched, removed)
VALUES (10113, 10020, 'qc-many-sub-13', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', NULL);
INSERT INTO subsystem (id, member_id, subsystem_code, created, changed, fetched, removed)
VALUES (10114, 10020, 'qc-many-sub-14', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', NULL);
INSERT INTO subsystem (id, member_id, subsystem_code, created, changed, fetched, removed)
VALUES (10115, 10020, 'qc-many-sub-15', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', NULL);
INSERT INTO subsystem (id, member_id, subsystem_code, created, changed, fetched, removed)
VALUES (10116, 10020, 'qc-many-sub-16', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', NULL);
INSERT INTO subsystem (id, member_id, subsystem_code, created, changed, fetched, removed)
VALUES (10117, 10020, 'qc-many-sub-17', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', NULL);
INSERT INTO subsystem (id, member_id, subsystem_code, created, changed, fetched, removed)
VALUES (10118, 10020, 'qc-many-sub-18', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', NULL);
INSERT INTO subsystem (id, member_id, subsystem_code, created, changed, fetched, removed)
VALUES (10119, 10020, 'qc-many-sub-19', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', NULL);
INSERT INTO subsystem (id, member_id, subsystem_code, created, changed, fetched, removed)
VALUES (10120, 10020, 'qc-many-sub-20', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', NULL);

-- One active service per scenario-A synthetic subsystem (touchAllChildren traversal).
-- ID range 10000-10019 -> one-version services for the per-member walk.
INSERT INTO service (id, subsystem_id, service_code, service_version, created, changed, fetched, removed)
VALUES (10000, 10000, 'qcSvc', 'v1', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', NULL);
INSERT INTO service (id, subsystem_id, service_code, service_version, created, changed, fetched, removed)
VALUES (10001, 10001, 'qcSvc', 'v1', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', NULL);
INSERT INTO service (id, subsystem_id, service_code, service_version, created, changed, fetched, removed)
VALUES (10002, 10002, 'qcSvc', 'v1', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', NULL);
INSERT INTO service (id, subsystem_id, service_code, service_version, created, changed, fetched, removed)
VALUES (10003, 10003, 'qcSvc', 'v1', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', NULL);
INSERT INTO service (id, subsystem_id, service_code, service_version, created, changed, fetched, removed)
VALUES (10004, 10004, 'qcSvc', 'v1', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', NULL);
INSERT INTO service (id, subsystem_id, service_code, service_version, created, changed, fetched, removed)
VALUES (10005, 10005, 'qcSvc', 'v1', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', NULL);
INSERT INTO service (id, subsystem_id, service_code, service_version, created, changed, fetched, removed)
VALUES (10006, 10006, 'qcSvc', 'v1', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', NULL);
INSERT INTO service (id, subsystem_id, service_code, service_version, created, changed, fetched, removed)
VALUES (10007, 10007, 'qcSvc', 'v1', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', NULL);
INSERT INTO service (id, subsystem_id, service_code, service_version, created, changed, fetched, removed)
VALUES (10008, 10008, 'qcSvc', 'v1', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', NULL);
INSERT INTO service (id, subsystem_id, service_code, service_version, created, changed, fetched, removed)
VALUES (10009, 10009, 'qcSvc', 'v1', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', NULL);
INSERT INTO service (id, subsystem_id, service_code, service_version, created, changed, fetched, removed)
VALUES (10010, 10010, 'qcSvc', 'v1', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', NULL);
INSERT INTO service (id, subsystem_id, service_code, service_version, created, changed, fetched, removed)
VALUES (10011, 10011, 'qcSvc', 'v1', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', NULL);
INSERT INTO service (id, subsystem_id, service_code, service_version, created, changed, fetched, removed)
VALUES (10012, 10012, 'qcSvc', 'v1', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', NULL);
INSERT INTO service (id, subsystem_id, service_code, service_version, created, changed, fetched, removed)
VALUES (10013, 10013, 'qcSvc', 'v1', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', NULL);
INSERT INTO service (id, subsystem_id, service_code, service_version, created, changed, fetched, removed)
VALUES (10014, 10014, 'qcSvc', 'v1', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', NULL);
INSERT INTO service (id, subsystem_id, service_code, service_version, created, changed, fetched, removed)
VALUES (10015, 10015, 'qcSvc', 'v1', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', NULL);
INSERT INTO service (id, subsystem_id, service_code, service_version, created, changed, fetched, removed)
VALUES (10016, 10016, 'qcSvc', 'v1', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', NULL);
INSERT INTO service (id, subsystem_id, service_code, service_version, created, changed, fetched, removed)
VALUES (10017, 10017, 'qcSvc', 'v1', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', NULL);
INSERT INTO service (id, subsystem_id, service_code, service_version, created, changed, fetched, removed)
VALUES (10018, 10018, 'qcSvc', 'v1', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', NULL);
INSERT INTO service (id, subsystem_id, service_code, service_version, created, changed, fetched, removed)
VALUES (10019, 10019, 'qcSvc', 'v1', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', NULL);

-- Scenario C: ServiceRepositoryV2.findActiveByMemberServiceAndSubsystem single-version service.
-- Placed under scenario-B host member 10020 / subsystem 10100 so scenario-A touchAllChildren
-- doesn't see extra services. serviceCode 'qcSingleSvc' has exactly one version.
INSERT INTO service (id, subsystem_id, service_code, service_version, created, changed, fetched, removed)
VALUES (10100, 10100, 'qcSingleSvc', 'v1', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', NULL);

-- Scenario C continued: a synthetic 2-version service used to exercise the
-- wsdls/openApis/rests/endpoints graph path on findActiveByMemberServiceAndSubsystem.
-- The entity graph must collapse child fetches regardless of version count (query count
-- must be invariant between the 1-version and 2-version lookups).
INSERT INTO service (id, subsystem_id, service_code, service_version, created, changed, fetched, removed)
VALUES (10200, 10101, 'qcMultiSvc', 'v1', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', NULL);
INSERT INTO service (id, subsystem_id, service_code, service_version, created, changed, fetched, removed)
VALUES (10201, 10101, 'qcMultiSvc', 'v2', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', NULL);

-- Scenario C descriptors: a WSDL per version so the four-path entity graph
-- (wsdls/openApis/rests/endpoints) has at least one child row to materialize per version.
INSERT INTO wsdl (id, service_id, data, external_id, created, changed, fetched, removed)
VALUES (10200, 10200, '<?xml version="1.0" standalone="no"?><qc-multi-v1/>', 'qc-multi-v1',
        '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', NULL);
INSERT INTO wsdl (id, service_id, data, external_id, created, changed, fetched, removed)
VALUES (10201, 10201, '<?xml version="1.0" standalone="no"?><qc-multi-v2/>', 'qc-multi-v2',
        '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', NULL);
INSERT INTO wsdl (id, service_id, data, external_id, created, changed, fetched, removed)
VALUES (10100, 10100, '<?xml version="1.0" standalone="no"?><qc-single-v1/>', 'qc-single-v1',
        '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', '2016-01-01 00:00:00+02', NULL);
