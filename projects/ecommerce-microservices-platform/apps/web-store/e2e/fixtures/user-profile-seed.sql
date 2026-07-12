-- =============================================================================
-- web-store full-stack e2e — ecommerce user-service profile seed (TASK-MONO-373)
-- =============================================================================
-- Runtime data fixture (NOT a Flyway migration). Applied to the ecommerce
-- `user-postgres` container AFTER user-service Flyway has run and BEFORE the
-- Playwright run. Sibling of `iam-consumer-seed.sql`, which seeds the SAME
-- identity on the IAM side (auth_db.credentials).
--
-- WHY THIS IS NEEDED
--   In production a user_profiles row is created by an EVENT: IAM publishes
--   `account.created`, and user-service's AccountCreatedConsumer (groupId
--   user-service) calls UserProfile.createMinimal(accountId).
--
--   The e2e IAM stack cannot produce that event. Its consumer identity is
--   inserted straight into auth_db.credentials by iam-consumer-seed.sql (no
--   account.created is ever published), and auth-service runs with
--   KAFKA_BOOTSTRAP_SERVERS=127.0.0.1:9999 (a closed port — it never produces).
--   So the profile row would simply never exist.
--
--   Without it, WishlistService.addItem() short-circuits:
--       if (!userProfileRepository.existsByUserId(command.userId()))
--           throw new UserProfileNotFoundException(...)
--   and wishlist.spec.ts fails on the very first "찜 추가" click. (The FK
--   wishlist_items.user_id -> user_profiles.user_id would reject it anyway.)
--
-- WHY user_id IS THIS PARTICULAR UUID
--   The gateway forwards X-User-Id <- jwt.sub verbatim, and since ADR-MONO-040
--   Phase 3B (TASK-MONO-299) the SAS `sub` IS the account UUID —
--   TenantClaimTokenCustomizer.alignSubToAccountId() overrides the framework
--   default (which was the login email, the latent production bug that
--   TASK-MONO-291 uncovered). So `user_id` here MUST equal
--   `auth_db.credentials.account_id` from iam-consumer-seed.sql. If these two
--   files ever disagree, wishlist writes 404 — and that is the failure MONO-291
--   spent a whole task diagnosing. Keep them equal.
--
-- Shape mirrors UserProfile.createMinimal(): email/name NULL (V5 relaxed both),
-- status ACTIVE, tenant_id 'ecommerce' (V4, the default tenant).
--
-- Re-runnable: ON CONFLICT DO NOTHING (uq_user_profiles_user_id).
-- =============================================================================

INSERT INTO user_profiles (
    id,
    user_id,
    email,
    name,
    status,
    tenant_id,
    created_at,
    updated_at
) VALUES (
    '01928c4a-7e9f-7c00-9a40-d2b1f5e8e0f1',
    '01928c4a-7e9f-7c00-9a40-d2b1f5e8e001',  -- = credentials.account_id = jwt.sub
    'e2e-consumer@example.com',
    'E2E Consumer',
    'ACTIVE',
    'ecommerce',
    NOW(),
    NOW()
) ON CONFLICT (user_id) DO NOTHING;
