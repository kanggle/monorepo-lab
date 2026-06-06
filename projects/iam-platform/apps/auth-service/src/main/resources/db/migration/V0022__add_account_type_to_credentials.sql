-- TASK-BE-329 (ADR-MONO-021 D1 + D4 step 1)
-- auth-service: add account_type to credentials.
--
-- account_type is the platform-required JWT claim (CONSUMER | OPERATOR) — see
-- platform/contracts/jwt-standard-claims.md L46. ADR-MONO-021 D1 stores it
-- DENORMALIZED on auth_db.credentials (mirroring tenant_id, V0007) so the SAS
-- form-login path (CredentialAuthenticationProvider) can put it in the token
-- without a cross-service call at issuance.
--
-- D4 step 1 phasing: NOT NULL DEFAULT 'CONSUMER'. Existing rows (none in prod;
-- e2e/seed only) backfill to CONSUMER. OPERATOR rows are set explicitly by their
-- provisioning/seed (the operator-credential seed INSERTs set account_type=
-- 'OPERATOR'). The DEFAULT is RETAINED (forward-only, additive un-break): the
-- absent claim previously 403'd every authenticated gateway request; emitting a
-- correct value is strictly net-positive. account-service signup will set
-- CONSUMER explicitly in D2 (follow-up) rather than relying on this default.
ALTER TABLE credentials
    ADD COLUMN account_type VARCHAR(20) NOT NULL DEFAULT 'CONSUMER' AFTER tenant_id;
