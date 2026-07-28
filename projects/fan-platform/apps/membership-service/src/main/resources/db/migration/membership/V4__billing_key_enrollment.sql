-- TASK-FAN-BE-033 / ADR-002: billing-key enrollment (real recurring billing).
--
-- Stores the vendor-opaque billing key a fan registered once so the auto-renew
-- scheduler can charge it server-side on the membership's renewal date. Decoupled
-- from `memberships` / `MembershipStatus` (ADR-002 §D1 — no state-machine change).
--
-- `billing_key_encrypted` holds an AES-GCM base64 envelope (ADR-002 §D5), NOT the
-- plaintext key — encryption is applied at the JPA layer
-- (BillingKeyEncryptionConverter). VARCHAR(1024): the envelope of a <=512-char key
-- (bounded by the request @Size) is ~720 base64 chars; the fixed length matches the
-- entity's @Column(length=1024) so Hibernate ddl-auto=validate maps cleanly.
CREATE TABLE billing_key_enrollments (
    id                    VARCHAR(36)   PRIMARY KEY,
    tenant_id             VARCHAR(64)   NOT NULL,
    account_id            VARCHAR(36)   NOT NULL,
    tier                  VARCHAR(20)   NOT NULL,
    billing_key_encrypted VARCHAR(1024) NOT NULL,
    active                BOOLEAN       NOT NULL,
    created_at            TIMESTAMPTZ   NOT NULL,
    version               BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT ck_bke_tier CHECK (tier IN ('MEMBERS_ONLY', 'PREMIUM'))
);

-- At most ONE active enrollment per (tenant, account, tier) — the "no double
-- chargeable enrollment" invariant (ADR-002 §D1). A partial unique index (only
-- WHERE active) lets a fan re-enroll a tier over time: prior rows are soft-
-- deactivated (active=false) and stay for history, and only the live one is
-- constrained. This is the race-safe guard behind the use case's check-then-replace.
CREATE UNIQUE INDEX uq_bke_active_account_tier
    ON billing_key_enrollments (tenant_id, account_id, tier)
    WHERE active;

-- Auto-renew scheduler scan: active rows only. Shrinks toward the count of live
-- enrollments regardless of deactivated history.
CREATE INDEX idx_bke_active_created_at
    ON billing_key_enrollments (created_at)
    WHERE active;
