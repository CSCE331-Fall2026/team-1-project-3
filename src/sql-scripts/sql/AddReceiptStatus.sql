-- Makes new checkouts start as 'PENDING' (kitchen queue). Run by regen-data.sh after the seed data loads.
-- DesignSql.sql already defaults to 'PENDING', so on a fresh database this is a no-op; safe to rerun.
-- Seeded history is written as 'COMPLETED' explicitly by the generator and is not touched.
BEGIN;

ALTER TABLE receipt ALTER COLUMN status SET DEFAULT 'PENDING';

COMMIT;
