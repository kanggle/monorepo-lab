-- TASK-MONO-771 S2b (ADR-MONO-080 D4 · R3) — the account-plane second factor (TOTP).
-- Spec: projects/iam-platform/specs/services/auth-service/data-model.md § account_totp.
--
-- 🔴 Keyed by account_id ALONE (one enrollment per account). tenant_id is the M1 isolation copy of the
-- credential's stored tenant and is NOT a lookup predicate — the decision path reads by account_id only,
-- so a tenant drift can never make an enrollment "invisible" (an invisible enrollment = no second step).
-- The enrollment survives the consumer-pool move (TASK-BE-618 / TASK-MONO-772): that move changes where the
-- credential is stored, not who the person is.
--
-- Separate table, separate key space from admin-service's break-glass admin_operator_totp — never migrated or
-- linked (ADR-MONO-035 O4 + 080 D4: break-glass must keep working while the IdP is down).
--
-- confirmed_at NULL = pending (written by GET /mfa/setup, 10-minute life from created_at). A pending row
-- passes no check: the login second step and step-up read confirmed rows only.
-- last_used_step = the anti-replay counter (AC-0 F5): the TOTP time-step floor(unix/30) last accepted; a code
-- of that step or an earlier one is refused even inside the ±1 window.
-- secret_encrypted = AES-GCM-256 [12-byte IV][ciphertext][16-byte tag], AAD = UTF-8 bytes of account_id.
-- recovery_codes_hashed = JSON array of Argon2id hashes (NULL while pending).
--
-- Empty table → net-zero for every existing login. Forward-only; IF NOT EXISTS is the existence guard.
CREATE TABLE IF NOT EXISTS account_totp (
    account_id             VARCHAR(36)    NOT NULL,
    tenant_id              VARCHAR(32)    NOT NULL,
    secret_encrypted       VARBINARY(255) NOT NULL,
    secret_key_id          VARCHAR(64)    NOT NULL DEFAULT 'v1',
    confirmed_at           DATETIME(6)    NULL,
    recovery_codes_hashed  JSON           NULL,
    last_used_step         BIGINT         NULL,
    last_used_at           DATETIME(6)    NULL,
    created_at             DATETIME(6)    NOT NULL,
    updated_at             DATETIME(6)    NOT NULL,
    version                INT            NOT NULL DEFAULT 0,
    PRIMARY KEY (account_id),
    KEY idx_account_totp_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
