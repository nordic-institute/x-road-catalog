-- Test-scoped padding layered on top of pg/v2-fixture.sql, for PgQueryCountTest only.
-- Purpose: pg/v2-fixture.sql has only 3 active members and 1 active subsystem, so at page
-- size 20 Spring Data's PageableExecutionUtils.getPage() derives totalElements from
-- content.size() (offset == 0 && pageSize > content.size()) and skips the COUNT query
-- entirely -- 1 statement instead of the 2 (rows + count) the plan requires. This script
-- adds enough additional active members/subsystems that page size 20 can never hold the
-- full result set, forcing a real COUNT query at every page size the test exercises.
-- Loaded via @Sql AFTER v2-fixture.sql (which TRUNCATEs and reloads the canonical fixture),
-- so it only ever appends to a freshly-loaded, known state; it must never be loaded by any
-- other test class. Ids start at 5000, well above the canonical fixture's ids (<= 42) and
-- its post-load sequence baseline (1000), so there is no collision either way.
INSERT INTO member (id, x_road_instance, member_class, member_code, name, created, changed, fetched, removed)
SELECT 5000 + gs, 'TEST', 'PAD', 'PM' || gs, 'Padding Member ' || gs,
       TIMESTAMP '2025-05-01 10:00', TIMESTAMP '2025-05-01 10:00', TIMESTAMP '2025-06-01 10:00', NULL
FROM generate_series(1, 21) AS gs;

INSERT INTO subsystem (id, member_id, subsystem_code, created, changed, fetched, removed)
SELECT 5100 + gs, 5000 + gs, 'PADSS' || gs,
       TIMESTAMP '2025-05-01 10:00', TIMESTAMP '2025-05-01 10:00', TIMESTAMP '2025-06-01 10:00', NULL
FROM generate_series(1, 21) AS gs;

SELECT setval('member_id_seq', 6000);
SELECT setval('subsystem_id_seq', 6000);
