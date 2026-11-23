-- Issue #45: upgrade a database built before receipt.status existed. Keeps all data; safe to rerun.
-- Never run DesignSql.sql on a populated database: it drops every table.
-- Receipts that already exist become 'COMPLETED' so the kitchen queue starts empty; new checkouts start 'PENDING'.
-- A rerun changes nothing except restoring the 'PENDING' default (fixes databases built with the old 'COMPLETED' default).
-- Check: SELECT status, COUNT(*) FROM receipt GROUP BY status;  -- history is all COMPLETED right after
BEGIN;

DO $$
BEGIN
    CREATE TYPE order_status AS ENUM ('PENDING', 'COMPLETED');
EXCEPTION
    WHEN duplicate_object THEN NULL;
END $$;

-- Fills existing rows with the constant default (PostgreSQL 11+ does this without rewriting the table).
ALTER TABLE receipt ADD COLUMN IF NOT EXISTS status order_status NOT NULL DEFAULT 'COMPLETED';
ALTER TABLE receipt ALTER COLUMN status SET DEFAULT 'PENDING';

CREATE INDEX IF NOT EXISTS idx_receipt_pending ON receipt (receipt_id) WHERE status = 'PENDING';

COMMIT;
