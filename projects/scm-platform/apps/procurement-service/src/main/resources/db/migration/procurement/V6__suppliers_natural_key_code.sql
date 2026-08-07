-- TASK-SCM-BE-059 AC-3 — give `suppliers` a natural key.
--
-- Measured before writing this: the table's ONLY unique constraint was the
-- primary key (`id`), and `name` was NOT unique. So "call it twice with the
-- same supplier and you get one row" had nothing to key on — the idempotency
-- the contract requires was structurally impossible, not merely unimplemented.
--
-- `code` is the natural key the contract (procurement-api.md § POST
-- /api/procurement/suppliers) fixes: `[A-Z0-9][A-Z0-9_-]*`, unique per tenant.
--
-- Backfill: `UPPER(id)`. Uniqueness is inherited from the PK (globally unique,
-- so certainly unique within a tenant), and uppercasing keeps every backfilled
-- value inside the contract's character class — both UUIDs (e2e / production
-- rows) and the demo seed's literal id (`SUP-DEMO-01`). No row can be left
-- NULL, so the NOT NULL below cannot fail on existing data.
ALTER TABLE suppliers ADD COLUMN code VARCHAR(64);

UPDATE suppliers SET code = UPPER(id) WHERE code IS NULL;

ALTER TABLE suppliers ALTER COLUMN code SET NOT NULL;

ALTER TABLE suppliers ADD CONSTRAINT ux_suppliers_tenant_code UNIQUE (tenant_id, code);
