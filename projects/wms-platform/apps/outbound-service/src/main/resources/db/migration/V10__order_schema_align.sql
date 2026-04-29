-- TASK-BE-037 — outbound_order schema alignment.
--
-- V2 created the bootstrap shape with `erp_order_number` as the unique business
-- identifier; the spec
-- (specs/services/outbound-service/domain-model.md §1) renames this to
-- `order_no` and introduces `source`, `notes`, `customer_partner_id`.
-- This migration adds the missing columns. `erp_order_number` and
-- `partner_id` are retained (not dropped) for zero-downtime compatibility.
-- The persistence adapter mirrors `order_no → erp_order_number` and
-- `customer_partner_id → partner_id` on insert until V2's columns can be
-- safely retired in a later migration.
--
-- Add the new domain-model.md §1 columns. All ADDs are nullable initially so
-- existing rows tolerate the migration; the application populates them on
-- every new insert.

ALTER TABLE outbound_order
    ADD COLUMN IF NOT EXISTS order_no            VARCHAR(40),
    ADD COLUMN IF NOT EXISTS source              VARCHAR(20),
    ADD COLUMN IF NOT EXISTS customer_partner_id UUID,
    ADD COLUMN IF NOT EXISTS notes               VARCHAR(1000),
    ADD COLUMN IF NOT EXISTS created_by          VARCHAR(100),
    ADD COLUMN IF NOT EXISTS updated_by          VARCHAR(100);

-- Backfill: copy values from the bootstrap columns where the new ones are NULL.
UPDATE outbound_order SET order_no = erp_order_number WHERE order_no IS NULL;
UPDATE outbound_order SET customer_partner_id = partner_id WHERE customer_partner_id IS NULL;

-- order_no is the new authoritative business identifier — globally unique.
-- Use a partial-unique index so the constraint applies only once values are
-- backfilled (NULLs are excluded by default in PostgreSQL unique indexes).
CREATE UNIQUE INDEX IF NOT EXISTS idx_outbound_order_order_no_unique
    ON outbound_order(order_no);

CREATE INDEX IF NOT EXISTS idx_outbound_order_customer_partner_id
    ON outbound_order(customer_partner_id);

CREATE INDEX IF NOT EXISTS idx_outbound_order_source
    ON outbound_order(source);

-- outbound_order_line: V2 has `requested_qty` and `line_number` already; no
-- additional columns needed for the TASK-BE-037 domain model. Lot id /
-- created_at / sku_id are present.
