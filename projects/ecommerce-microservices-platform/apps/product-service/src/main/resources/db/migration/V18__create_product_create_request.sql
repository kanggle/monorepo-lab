-- TASK-BE-536 — POST /api/admin/products has no client key, no dedupe, and no
-- name-uniqueness guard. RegisterProductService.register creates a brand-new
-- Product (random UUID) + a fresh stock ledger on every call, so a replayed
-- request creates a second product with a second stock ledger.
--
-- A name-uniqueness natural key is deliberately NOT used: two genuinely different
-- products can legitimately share a name (the census flagged this as an F1 risk —
-- a naive uniqueness guard would block a legitimate second product), so only a
-- client-supplied key can tell "a retry of the registration I already performed"
-- apart from "a second, intentionally-named-the-same product".
--
-- Scoped to (tenant_id, idempotency_key) — there is no product row yet to scope
-- the key to at request time (unlike the stock/refund endpoints, which scope to an
-- existing row). tenant_id keeps the key namespace per-tenant (ADR-MONO-030 M1).
--
-- One row per accepted registration request. UNIQUE (tenant_id, idempotency_key) is:
--   * the replay key — a same-key retry finds this row and returns the ALREADY
--     created product_id instead of creating a second product; and
--   * the CONCURRENCY backstop — two simultaneous duplicates both miss the SELECT,
--     but only one INSERT can commit. The loser gets DataIntegrityViolationException
--     -> 409 IDEMPOTENCY_KEY_CONFLICT.
--
-- `name` is recorded so a same-key replay with a DIFFERENT product name is
-- rejected (409) rather than silently returning the first product's id.
--
-- NO TTL, deliberately (mirrors payment_refund_request, TASK-BE-535).

CREATE TABLE product_create_request (
    id              BIGSERIAL    PRIMARY KEY,
    tenant_id       VARCHAR(64)  NOT NULL,
    idempotency_key VARCHAR(255) NOT NULL,
    name            VARCHAR(255) NOT NULL,
    product_id      UUID         NOT NULL,
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_product_create_request_key UNIQUE (tenant_id, idempotency_key)
);

CREATE INDEX idx_product_create_request_tenant ON product_create_request (tenant_id, created_at);
