-- TASK-MONO-750 (ADR-MONO-079 ACCEPTED — A, D4-A): the `fan-platform` tenant subscribes to the
-- `fan` domain, so a PLATFORM operator who assumes `fan-platform` is derived `FAN_OPERATOR`
-- (auth-service OperatorRoleDerivation — the `fan` arm, unreachable until this row existed).
--
-- Same shape as V0022 (ecommerce): the tenant row already exists (V0009, tenant_type=B2C_CONSUMER,
-- ACTIVE); only the subscription row is added. INSERT IGNORE + the WHERE-EXISTS guard against
-- `tenants` keep it idempotent and a no-op where `fan-platform` is not registered.
--
-- What this row does NOT open, by design (measured in TASK-MONO-750 AC-0):
--   * a CUSTOMER operator: admin-service refuses every non-platform-scope operator's assume of
--     `fan-platform` (OperatorAssignmentCheckUseCase), and refuses creating an assignment row to
--     it (ManageOperatorAssignmentUseCase) — rider R3, platform operators only.
--   * community / membership / notification: each refuses a FAN_OPERATOR token on every end-user
--     path (ADR-MONO-059's excluded option B — authoring as an artist — stays closed).
--   * a customer tenant subscribing `fan` itself: admin-service refuses `fan` on any tenant but
--     `fan-platform` (ManageSubscriptionUseCase), and the fan services never trust
--     `entitled_domains` anyway.
--
-- Side effect, known and harmless: fan consumer tokens (authorization_code on the fan web client,
-- tenant `fan-platform`) now carry `entitled_domains=["fan"]` — the same way ecommerce consumer
-- tokens carry ["ecommerce"] since V0022. No fan edge or service reads that claim.
INSERT IGNORE INTO tenant_domain_subscription (tenant_id, domain_key, status, created_at, updated_at)
SELECT t.tenant_id, 'fan', 'ACTIVE', NOW(6), NOW(6)
FROM tenants t
WHERE t.tenant_id = 'fan-platform';
