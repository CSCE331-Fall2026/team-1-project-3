-- Issue #45: upgrade for a database built BEFORE DesignSql.sql had receipt.status. Fresh databases don't need it.
-- Never run DesignSql.sql on a populated database: it drops every table.
-- Receipts that already exist become 'COMPLETED' so the kitchen queue starts empty
-- (PostgreSQL 11+ stores a constant default without rewriting the table).
-- The default then changes so new checkouts start as 'PENDING' without changing the checkout INSERT.
-- Run once. A second run stops at "column status already exists" and the transaction rolls back, changing nothing.
-- Check: SELECT status, COUNT(*) FROM receipt GROUP BY status;  -- history is all COMPLETED right after
BEGIN;

ALTER TABLE receipt ALTER COLUMN status SET DEFAULT 'PENDING';

COMMIT;
