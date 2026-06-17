-- TASK-PC-FE-113 — operator personas for the federation-stack-gated console specs.
--
-- The federation-gated specs
--   federation-ecommerce-sellers-multi.spec.ts  (multi-operator -> ecommerce sellers)
--   federation-omni-all-domains.spec.ts         (omni-operator  -> 5-domain overview)
-- run ONLY against the root `federation-hardening-e2e` demo stack (localhost:3000),
-- because the console-web CI e2e stack (docker-compose.e2e.yml = IAM + finance-account
-- + console-bff + console-web) has none of the domain backends they exercise. This
-- fixture replaces the hand-typed persona seeding the throwaway verify-*.mjs scripts
-- assumed (project memory `env_console_demo_local_redeploy`) with a committed,
-- reproducible seed — so the specs are not "green only on a hand-seeded local stack"
-- (the MONO-250/251 nightly-drift failure class).
--
-- SCOPE: this seeds the OPERATOR PLANE only — the two operator personas + their
-- tenant assignments + roles, across the federation stack's MySQL `auth_db` (login
-- credentials) and `admin_db` (operator identity / assume-tenant grants). The omni-corp
-- TENANT itself and its 5-domain `account_db.tenant_domain_subscription` rows (what makes
-- all 5 overview cards entitled) are ACCOUNT-PLANE data owned by the federation demo
-- stack's own seed (`tests/federation-hardening-e2e/fixtures/seed-omni-*.sql`,
-- root-scoped, intentionally out of this project's scope) — not duplicated here.
--
-- APPLY (federation demo stack up per `env_console_demo_local_redeploy`):
--   docker exec -i federation-hardening-e2e-mysql-1 mysql -uroot -p<root_pw> < seed-federation-personas.sql
-- (root, or any user with access to both auth_db + admin_db.)
--
-- IDEMPOTENT: natural-key resolution (email / oidc_subject) — no hardcoded
-- auto-increment ids — so re-applying is a no-op and it survives a base-seed reorder.
--
-- Password for both personas = `devpassword123!` (IAM V0014 dev Argon2id, shared by all
-- demo accounts — hardcoded test data, no production credential).

-- ---------------------------------------------------------------------------
-- auth_db.credentials — the AuthN source (BE-309 CredentialAuthenticationProvider
-- looks up by email, verifies the Argon2id hash).
-- ---------------------------------------------------------------------------
INSERT INTO auth_db.credentials
    (tenant_id, account_id, email, credential_hash, hash_algorithm, created_at, updated_at, version)
SELECT 'acme-corp', '01928c4a-7e9f-7c00-9a40-d2b1f5e8c300', 'multi-operator@example.com',
       '$argon2id$v=16$m=65536,t=3,p=1$7u/kw4KcLt7/i1nTEzEfsH7kRIraSsh1w9qOB7BhxUMTJdk3Oqp6zBklBlcMzJ4jS0PpgLYN+MW+1HlJF3m7ew$OJzCJkqvkul/EbS2FejjcDPx7Htj2HkAiCz74xcGBeY',
       'argon2id', NOW(6), NOW(6), 0
WHERE NOT EXISTS (SELECT 1 FROM auth_db.credentials WHERE email = 'multi-operator@example.com');

INSERT INTO auth_db.credentials
    (tenant_id, account_id, email, credential_hash, hash_algorithm, created_at, updated_at, version)
SELECT 'omni-corp', '01928c4a-7e9f-7c00-9a40-d2b1f5e8f100', 'omni-operator@example.com',
       '$argon2id$v=16$m=65536,t=3,p=1$7u/kw4KcLt7/i1nTEzEfsH7kRIraSsh1w9qOB7BhxUMTJdk3Oqp6zBklBlcMzJ4jS0PpgLYN+MW+1HlJF3m7ew$OJzCJkqvkul/EbS2FejjcDPx7Htj2HkAiCz74xcGBeY',
       'argon2id', NOW(6), NOW(6), 0
WHERE NOT EXISTS (SELECT 1 FROM auth_db.credentials WHERE email = 'omni-operator@example.com');

-- ---------------------------------------------------------------------------
-- admin_db.admin_operators — operator identity (oidc_subject = email links the OIDC
-- token subject to the operator; ConsoleRegistry resolves the assume-tenant scope).
-- ---------------------------------------------------------------------------
INSERT INTO admin_db.admin_operators
    (operator_id, tenant_id, email, password_hash, display_name, status, oidc_subject,
     finance_default_account_id, created_at, updated_at, version)
SELECT 'multi-operator', 'acme-corp', 'multi-operator@example.com',
       '$argon2id$v=16$m=65536,t=3,p=1$7u/kw4KcLt7/i1nTEzEfsH7kRIraSsh1w9qOB7BhxUMTJdk3Oqp6zBklBlcMzJ4jS0PpgLYN+MW+1HlJF3m7ew$OJzCJkqvkul/EbS2FejjcDPx7Htj2HkAiCz74xcGBeY',
       'Multi Operator', 'ACTIVE', 'multi-operator@example.com',
       '01928c4a-7e9f-7c00-9a40-d2b1f5e8a200', NOW(6), NOW(6), 0
WHERE NOT EXISTS (SELECT 1 FROM admin_db.admin_operators WHERE oidc_subject = 'multi-operator@example.com');

INSERT INTO admin_db.admin_operators
    (operator_id, tenant_id, email, password_hash, display_name, status, oidc_subject,
     finance_default_account_id, created_at, updated_at, version)
SELECT 'omni-operator', 'omni-corp', 'omni-operator@example.com',
       '$argon2id$v=16$m=65536,t=3,p=1$7u/kw4KcLt7/i1nTEzEfsH7kRIraSsh1w9qOB7BhxUMTJdk3Oqp6zBklBlcMzJ4jS0PpgLYN+MW+1HlJF3m7ew$OJzCJkqvkul/EbS2FejjcDPx7Htj2HkAiCz74xcGBeY',
       'Omni Operator', 'ACTIVE', 'omni-operator@example.com',
       '01928c4a-7e9f-7c00-9a40-d2b1f5e8f000', NOW(6), NOW(6), 0
WHERE NOT EXISTS (SELECT 1 FROM admin_db.admin_operators WHERE oidc_subject = 'omni-operator@example.com');

-- ---------------------------------------------------------------------------
-- admin_db.admin_operator_roles — base ADMIN role (role_id=1) per home tenant.
-- ---------------------------------------------------------------------------
INSERT INTO admin_db.admin_operator_roles (operator_id, role_id, granted_at, tenant_id)
SELECT o.id, 1, NOW(6), 'acme-corp'
FROM admin_db.admin_operators o
WHERE o.oidc_subject = 'multi-operator@example.com'
  AND NOT EXISTS (
    SELECT 1 FROM admin_db.admin_operator_roles r WHERE r.operator_id = o.id AND r.role_id = 1
  );

INSERT INTO admin_db.admin_operator_roles (operator_id, role_id, granted_at, tenant_id)
SELECT o.id, 1, NOW(6), 'omni-corp'
FROM admin_db.admin_operators o
WHERE o.oidc_subject = 'omni-operator@example.com'
  AND NOT EXISTS (
    SELECT 1 FROM admin_db.admin_operator_roles r WHERE r.operator_id = o.id AND r.role_id = 1
  );

-- ---------------------------------------------------------------------------
-- admin_db.operator_tenant_assignment — the assume-tenant grants the specs switch into.
--   multi-operator -> ecommerce  (the sellers spec's POST /api/tenant target)
--   omni-operator  -> omni-corp  (the 5-domain spec's target)
-- (The base federation seed grants multi-operator its acme/globex/initech rows; only
--  the spec-relevant grants are asserted here.)
-- ---------------------------------------------------------------------------
INSERT INTO admin_db.operator_tenant_assignment (operator_id, tenant_id, granted_at)
SELECT o.id, 'ecommerce', NOW(6)
FROM admin_db.admin_operators o
WHERE o.oidc_subject = 'multi-operator@example.com'
  AND NOT EXISTS (
    SELECT 1 FROM admin_db.operator_tenant_assignment a
    WHERE a.operator_id = o.id AND a.tenant_id = 'ecommerce'
  );

INSERT INTO admin_db.operator_tenant_assignment (operator_id, tenant_id, granted_at)
SELECT o.id, 'omni-corp', NOW(6)
FROM admin_db.admin_operators o
WHERE o.oidc_subject = 'omni-operator@example.com'
  AND NOT EXISTS (
    SELECT 1 FROM admin_db.operator_tenant_assignment a
    WHERE a.operator_id = o.id AND a.tenant_id = 'omni-corp'
  );
