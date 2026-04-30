-- !!! DEV/LOCAL ONLY — DO NOT run in production. Loaded via spring.flyway.locations only in non-prod profiles. !!!
-- TASK-BE-029-3: seed dev SUPER_ADMIN operator with a fixed Argon2id password
-- hash so the /api/admin/auth/login endpoint has a usable identity in local
-- and integration environments.
--
-- admin_operators.password_hash already exists (V0004 / V0007 created it
-- NOT NULL). This migration is seed-only: it does NOT alter the schema.
--
-- Test password (plaintext, dev-only): "devpassword123!"
-- Encoded hash: Argon2id (m=65536, t=3, p=1, 32B output, 16B salt) via
--   com.gap.security.password.Argon2idPasswordHasher (default parameters).
--
-- The INSERT IGNORE + dependent INSERT IGNORE keep the migration idempotent
-- on re-runs and safe on environments where an operator with this UUID is
-- already present.

INSERT IGNORE INTO admin_operators (
    operator_id,
    email,
    password_hash,
    display_name,
    status,
    created_at,
    updated_at,
    version
) VALUES (
    '00000000-0000-7000-8000-00000000dev1',
    'dev-super-admin@example.com',
    '$argon2id$v=16$m=65536,t=3,p=1$7u/kw4KcLt7/i1nTEzEfsH7kRIraSsh1w9qOB7BhxUMTJdk3Oqp6zBklBlcMzJ4jS0PpgLYN+MW+1HlJF3m7ew$OJzCJkqvkul/EbS2FejjcDPx7Htj2HkAiCz74xcGBeY',
    'Dev Super Admin',
    'ACTIVE',
    NOW(6),
    NOW(6),
    0
);

-- Bind the dev operator to SUPER_ADMIN (require_2fa=TRUE after V0013).
INSERT IGNORE INTO admin_operator_roles (operator_id, role_id, granted_at, granted_by)
SELECT o.id, r.id, NOW(6), NULL
  FROM admin_operators o
  JOIN admin_roles r ON r.name = 'SUPER_ADMIN'
 WHERE o.operator_id = '00000000-0000-7000-8000-00000000dev1';
