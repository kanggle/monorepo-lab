-- TASK-BE-341 (ADR-MONO-023 § 3.3 step 1 — D1 subscription lifecycle state set):
-- Formalize the tenant_domain_subscription.status state machine at the DB layer.
--
-- ADR-019 D2 (V0019) created `status VARCHAR(10) NOT NULL DEFAULT 'ACTIVE'` with
-- no constraint and only ever seeded 'ACTIVE'. ADR-023 D1 defines the explicit
-- subscription lifecycle state set (PENDING | ACTIVE | SUSPENDED | CANCELLED).
-- This migration pins that set with a CHECK constraint so an illegal status is
-- un-storable, mirroring the application-layer SubscriptionStatus enum + its
-- transition guard.
--
-- NET-ZERO (ADR-023 D6 step 1): every existing row is 'ACTIVE' (V0019/V0020
-- seeds), which is in the allowed set — the constraint accepts all current data
-- and the catalog (ADR-019 D4) + entitled_domains (ADR-019 D5 / ADR-020 D3) read
-- paths, which filter status='ACTIVE', are byte-identical. No row is changed.
--
-- MySQL 8.0.16+ enforces CHECK constraints (the Testcontainers image is mysql:8.0).
ALTER TABLE tenant_domain_subscription
    ADD CONSTRAINT chk_tds_status
    CHECK (status IN ('PENDING', 'ACTIVE', 'SUSPENDED', 'CANCELLED'));
