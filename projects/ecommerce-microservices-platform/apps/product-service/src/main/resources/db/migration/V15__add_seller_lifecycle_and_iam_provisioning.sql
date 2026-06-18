-- TASK-BE-402 (ADR-MONO-042 — seller onboarding + real IAM provisioning, ADR-030 Step 4 facet f).
--
-- Makes the marketplace seller a REAL provisioned principal instead of a trusted-claim
-- shim: onboarding mints an IAM seller-operator account (fail-soft, D3), so the seller
-- gains a lifecycle (PENDING_PROVISIONING -> ACTIVE; ACTIVE -> SUSPENDED/CLOSED) and a
-- link to its backing account/identity.
--
-- Additive + net-zero (D3 invariant):
--   * `account_id` / `identity_id` are NULLABLE (null until provisioned, or for the
--     default seller which is never provisioned).
--   * `status` already exists (V14, default 'ACTIVE'); all EXISTING sellers stay ACTIVE
--     with null account/identity -> today's behavior is byte-identical (legacy sellers
--     keep operating via the trusted-claim path; only NEW onboarding mints accounts).
--   * the `status` column is widened to hold the longer 'PENDING_PROVISIONING' literal.

-- Widen status to fit the longest lifecycle literal ('PENDING_PROVISIONING' = 20 chars;
-- 24 leaves headroom). VARCHAR widening is a metadata-only, no-rewrite change in Postgres.
ALTER TABLE sellers ALTER COLUMN status TYPE VARCHAR(24);

-- Backing IAM principal linkage (D2/D5). Nullable until provisioned (D3 fail-soft).
ALTER TABLE sellers ADD COLUMN account_id  VARCHAR(64);
ALTER TABLE sellers ADD COLUMN identity_id VARCHAR(64);

-- Existing sellers (incl. each tenant's 'default' seller) remain ACTIVE with null
-- account/identity — no transition, no backfill needed beyond the columns existing
-- (V14 already seeded them ACTIVE). New onboarding writes PENDING_PROVISIONING.
