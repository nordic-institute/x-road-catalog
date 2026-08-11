-- Test-scoped padding layered on top of pg/v2-fixture.sql, for the multi-day change-log window
-- regression tests only (ReportsRepositoryV2ChangeLogWindowPgTest). Loaded via @Sql AFTER
-- v2-fixture.sql (which TRUNCATEs and reloads the canonical fixture), so it only ever appends
-- to a freshly-loaded, known state; it must never be loaded by any other test class.
--
-- The canonical fixture has no row where `changed` differs from both `created` and `removed`
-- for a member/subsystem/service, so it cannot expose the "modified between" window bug: every
-- non-removed row has changed == created, and every removed row has changed == removed. All
-- dates here fall in May 2025, after every day used by other tests' point-in-time assertions
-- (countServicesPerDay's Jan 3/9 2025 and Dec 1 2024 snapshots, findServicesCreatedBetween's
-- Jan 3-10 2025 window), so these rows never change an existing test's expected counts.
--
-- Ids start at 7000, well above the canonical fixture's ids (<= 42) and its post-load sequence
-- baseline (1000), and above pg/query-count-padding.sql's range (5000-6000; not loaded together
-- with this script, but kept disjoint regardless).
--
-- M10 (member 7001) / SS10 (subsystem 7011) / svcH (service 7021): created 2025-05-03 10:00,
-- genuinely modified 2025-05-06 10:00, never removed -- created AND changed both fall inside the
-- test window [2025-05-01, 2025-05-10). Under the old created-window guard this row was wrongly
-- excluded from "modified"; it must appear in BOTH the created and modified buckets.
INSERT INTO member (id, x_road_instance, member_class, member_code, name, created, changed, fetched, removed) VALUES
  (7001, 'TEST', 'GOV', 'M10', 'Created-then-modified Member', '2025-05-03 10:00', '2025-05-06 10:00', '2025-06-01 10:00', NULL);

INSERT INTO subsystem (id, member_id, subsystem_code, created, changed, fetched, removed) VALUES
  (7011, 1, 'SS10', '2025-05-03 10:00', '2025-05-06 10:00', '2025-06-01 10:00', NULL);

INSERT INTO service (id, subsystem_id, service_code, service_version, created, changed, fetched, removed) VALUES
  (7021, 11, 'svcH', '1.0', '2025-05-03 10:00', '2025-05-06 10:00', '2025-06-01 10:00', NULL);

-- M11 (member 7002): created-only in the window -- changed == created, never removed. Must
-- appear in "created" only, never in "modified".
INSERT INTO member (id, x_road_instance, member_class, member_code, name, created, changed, fetched, removed) VALUES
  (7002, 'TEST', 'GOV', 'M11', 'Created-only Member', '2025-05-04 10:00', '2025-05-04 10:00', '2025-06-01 10:00', NULL);

-- M12 (member 7003): removed in the window -- created well before the window, changed == removed
-- inside the window. Must appear in "removed" only, never in "created" or "modified".
INSERT INTO member (id, x_road_instance, member_class, member_code, name, created, changed, fetched, removed) VALUES
  (7003, 'TEST', 'GOV', 'M12', 'Removed-in-window Member', '2025-01-01 10:00', '2025-05-05 10:00', '2025-05-05 10:00', '2025-05-05 10:00');

-- M13 (member 7004): genuinely modified in the window, but created well before it (so `created`
-- falls outside [start, end) on its own, independent of this bug). Must appear in "modified" only,
-- never in "created" or "removed". Confirms the fix does not disturb the already-correct case.
INSERT INTO member (id, x_road_instance, member_class, member_code, name, created, changed, fetched, removed) VALUES
  (7004, 'TEST', 'GOV', 'M13', 'Modified-only Member', '2025-01-01 10:00', '2025-05-07 10:00', '2025-06-01 10:00', NULL);

SELECT setval('member_id_seq', 8000);
SELECT setval('subsystem_id_seq', 8000);
SELECT setval('service_id_seq', 8000);
