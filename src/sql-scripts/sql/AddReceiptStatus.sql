-- Issue #45: kitchen order status on receipt. Run once, after DesignSql.sql, on each database.
-- Receipts that already exist become 'COMPLETED' so the kitchen queue starts empty
-- (PostgreSQL 11+ stores a constant default without rewriting the table).
-- The default then changes so new checkouts start as 'PENDING' without changing the checkout INSERT.
BEGIN;

ALTER TABLE receipt
    ADD COLUMN status VARCHAR(16) NOT NULL DEFAULT 'COMPLETED'
        CONSTRAINT chk_receipt_status CHECK (status IN ('PENDING', 'COMPLETED'));

ALTER TABLE receipt ALTER COLUMN status SET DEFAULT 'PENDING';

-- The kitchen queue reads only pending rows; keeps that off a full scan of the receipt history.
CREATE INDEX idx_receipt_pending ON receipt (receipt_id) WHERE status = 'PENDING';

COMMIT;
