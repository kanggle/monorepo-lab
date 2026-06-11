-- TASK-SCM-BE-025 (ADR-MONO-027 D5) — provenance + cross-service idempotency for
-- demand-planning-originated DRAFT POs.
--
-- `origin` records who authored the PO (the default operator flow vs the
-- replenishment loop). `source_suggestion_id` is the cross-service idempotency
-- key (S2): the reorder_suggestion that materialized into this PO. Both are
-- additive; existing operator-authored rows backfill to origin='OPERATOR' with
-- a NULL source_suggestion_id. No PO state changes — purely descriptive columns.

ALTER TABLE purchase_orders
    ADD COLUMN origin                VARCHAR(30) NOT NULL DEFAULT 'OPERATOR',
    ADD COLUMN source_suggestion_id  VARCHAR(36);

ALTER TABLE purchase_orders
    ADD CONSTRAINT ck_purchase_orders_origin
        CHECK (origin IN ('OPERATOR', 'DEMAND_PLANNING'));

-- Cross-service idempotency backstop (D5): at most one PO per source suggestion
-- within a tenant. Partial index so the many operator-authored POs (NULL
-- source_suggestion_id) are exempt. This is the structural guarantee behind the
-- service-layer find-or-create; a concurrent double-call trips this and the
-- caller re-reads the winner.
CREATE UNIQUE INDEX uq_po_tenant_source_suggestion
    ON purchase_orders (tenant_id, source_suggestion_id)
    WHERE source_suggestion_id IS NOT NULL;
