-- 25 error_log rows spread across May 2025 for the errors query-count pin. Loaded on top of
-- pg/v2-fixture.sql; ids 900+ stay clear of both fixture rows and the 1000 setval.
INSERT INTO error_log (id, message, code, x_road_instance, member_class, member_code, created)
SELECT 900 + n, 'padding error ' || n, 'ERR', 'TEST', 'GOV', 'M1',
       TIMESTAMP '2025-05-01 08:00:00' + (n || ' hours')::interval
FROM generate_series(1, 25) AS n;
