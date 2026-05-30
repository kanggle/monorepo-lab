-- TASK-BE-325 (ADR-MONO-019 § 3.3 step 2 — first REAL customer tenant):
-- Seed acme-corp as a B2B_ENTERPRISE ACTIVE tenant and register its N:M
-- domain subscriptions.  This is intentionally net-POSITIVE: the catalog's
-- tenants[] for finance and wms will include 'acme-corp' alongside the
-- domain-slug self-subscriptions seeded in V0019.
--
-- Contrast with V0019 backward-compat self-subscriptions (net-zero shims
-- where each domain-slug tenant subscribes to its own domain to reproduce
-- the legacy slug binding byte-identically).  acme-corp is a *real customer*
-- that subscribes to multiple domains — the pattern the switcher is designed
-- to surface as a human-readable name ("Acme Corporation").
--
-- Subscription choices:
--   acme-corp → finance  (ACTIVE)
--   acme-corp → wms      (ACTIVE)
-- gap is NOT seeded: ProductCatalog.bindsAllTenants=true federates acme-corp
--   automatically; a gap row here would double-count it.
-- scm / erp are deliberately NOT subscribed: acme-corp is not entitled to
--   them and should be rejected by those domain gates.
--
-- FK constraint (fk_tds_tenant): tenant INSERT must precede subscription
-- INSERTs — both are in this file in the correct order.
-- INSERT IGNORE keeps the migration idempotent (mirrors V0014–V0019 pattern).

INSERT IGNORE INTO tenants (tenant_id, display_name, tenant_type, status, created_at, updated_at)
VALUES ('acme-corp', 'Acme Corporation', 'B2B_ENTERPRISE', 'ACTIVE', NOW(6), NOW(6));

INSERT IGNORE INTO tenant_domain_subscription (tenant_id, domain_key, status, created_at, updated_at)
VALUES ('acme-corp', 'finance', 'ACTIVE', NOW(6), NOW(6)),
       ('acme-corp', 'wms',     'ACTIVE', NOW(6), NOW(6));
