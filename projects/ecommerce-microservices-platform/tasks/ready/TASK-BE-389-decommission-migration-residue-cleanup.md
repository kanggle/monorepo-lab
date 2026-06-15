# TASK-BE-389 — Decommission/migration residue cleanup (stale auth-service Swagger refs, dead standalone route, doc/alias fixes)

**Status:** ready

**Type:** TASK-BE
**Analysis model:** Opus 4.8 / **Recommended impl model:** Sonnet 4.6 (low-risk, net-behavior-zero code/doc cleanup — no domain logic, no contract/schema/ADR change)

---

## Goal

Remove the **code-side residue** left by two already-landed migrations whose *spec* side was reconciled in the recent ecommerce drift sweep (PRs #1603/#1606/#1612/#1620–#1631/#1634): the **auth-service decommission** (TASK-BE-132 — auth-service excluded from the build, tokens now issued by IAM/GAP OIDC) and the **`account_type` → `roles` migration** (ADR-MONO-032 D5 step 4). The app code still carries stale references that mislead developers and a dead gateway route. This task is the **code-side mirror** of TASK-BE-388's spec-side note — purely housekeeping, net-behavior-zero.

The migration itself is complete and correct — no enforcement code reads `account_type`, every guard keys on `roles`. The residue is cosmetic/dead-config only.

## Scope

**In scope** (4 sites, all ecommerce-internal):

1. **Stale Swagger security-scheme description — 10 services.** Each `OpenApiConfig.java` line 35 reads:
   `.description("auth-service 가 발급한 access token 을 입력하세요 (Bearer 접두어 제외, 토큰 값만).")`
   auth-service is decommissioned; tokens are issued by the IAM (GAP) OIDC issuer. Update the description to name the correct issuer. Files (all at `apps/<svc>/.../config/OpenApiConfig.java`, line 35):
   order, user, product, payment, shipping, review, promotion, settlement, search, notification.
   This text was introduced by TASK-BE-379 (Swagger JWT bearer scheme) using the obsolete issuer name — fresh drift.

2. **Dead gateway standalone route to auth-service.** `apps/gateway-service/src/main/resources/application-standalone.yml` lines 12–15 define `id: auth-service → Path=/api/auth/**` proxying to a decommissioned service that no longer exists. Remove the route block. The standalone profile is local-dev/e2e only; nothing serves `/api/auth/**` (login is OIDC against IAM/GAP), so removal is net-behavior-zero (the route currently proxies to a non-existent upstream).

3. **`product-service` `SellerScopeContextFilter` javadoc typo.** Javadoc (line ~21) says `Runs at {@code HIGHEST_PRECEDENCE - 1}` but the annotation (line ~29) is `@Order(Ordered.HIGHEST_PRECEDENCE + 1)`. The sibling filters in order-service/settlement-service correctly say `+ 1` in both. Fix the javadoc to `+ 1` (the annotation is correct — runs immediately *after* `TenantContextFilter`).

4. **`web-store` `LoginForm` redundant `AccountTypeMismatch` alias.** `src/features/auth/ui/LoginForm.tsx` line 35: `code === 'account_type_mismatch' || code === 'AccountTypeMismatch'`. Only the snake_case `account_type_mismatch` is ever emitted (by `src/shared/auth/auth.ts` redirect); the PascalCase alias is a dead defensive branch. Remove the `|| code === 'AccountTypeMismatch'` clause. **Keep** the snake_case `account_type_mismatch` code itself — it is the intentional UI-contract error string (the operator-on-web-store rejection message), retained per the earlier sweep.

**Out of scope (unchanged):**

- **auth-service `src/` tree (178 tracked files).** `settings.gradle` explicitly preserves it ("Source code preserved for history"). Removal would contradict that intentional decision → not touched here (would need its own decision).
- **`account_type` documentation comments** in `AdminProductController`/`AdminProductImageController`/`AdminOrderController` javadoc and gateway `AccountTypeEnforcementFilter`/`JwtHeaderEnrichmentFilter` — these *document the removal* and are the irreducible way to record the migration; keep.
- **Gateway test helpers** (`JwtTestHelper`, `GatewayIntegrationTest`) that stamp `account_type` on tokens to assert the roles-only path **rejects** them — correct regression tests; keep.
- **Duplicated infrastructure** (`TenantContext`, `SellerScopeContext`, `EventDeduplicationChecker`, `KafkaConsumerConfig`, `OpenApiConfig` boilerplate) — handled separately (TASK-MONO-270, monorepo-level libs promotion).
- No spec, no contract, no Flyway, no ADR, no domain logic.

## Acceptance Criteria

- **AC-1** — No live ecommerce `OpenApiConfig` Swagger description references `auth-service` as the token issuer; all 10 name the IAM (GAP) OIDC issuer. `grep -r "auth-service 가 발급한" apps/` returns 0.
- **AC-2** — `application-standalone.yml` has no `auth-service` route; the remaining route blocks (product/user/search/order/payment/shipping) are byte-unchanged.
- **AC-3** — `product-service` `SellerScopeContextFilter` javadoc and `@Order` annotation agree (`HIGHEST_PRECEDENCE + 1`).
- **AC-4** — `LoginForm.tsx` matches only `account_type_mismatch`; the `AccountTypeMismatch` alias is gone; the snake_case code and its message are retained.
- **AC-5 (net-behavior-zero)** — No domain logic, no authorization decision, no contract/schema/ADR changes. `compileJava` GREEN for all touched services; `web-store` `tsc` + `next lint` GREEN. No `.java` enforcement path altered.

## Related Specs

- `projects/ecommerce-microservices-platform/specs/integration/iam-integration.md` (token issuer = IAM/GAP OIDC — already reconciled; this task aligns the code's Swagger text to it).
- `projects/ecommerce-microservices-platform/specs/services/gateway-service/architecture.md` (standalone profile — no auth-service route expected post-decommission).

## Related Contracts

- `platform/contracts/jwt-standard-claims.md` (issuer authority — informational).
- No contract file changes.

## Edge Cases

- **Swagger description is UI doc only** — changing it cannot affect token validation (the security scheme `type: http, scheme: bearer` is unchanged; only `.description()` text changes).
- **Standalone route removal under an active e2e** — if any standalone/e2e smoke expects `/api/auth/**` to route, it would already be failing (upstream gone). Verify no e2e/standalone fixture depends on the auth-service route before removal (grep `AUTH_SERVICE_URL` / `/api/auth` under e2e + compose).
- **LoginForm alias** — confirm via grep that no code path emits the PascalCase `AccountTypeMismatch` before removing the branch (auth.ts emits only snake_case).

## Failure Scenarios

- **F1 — over-removal** — deleting the `account_type_mismatch` code/message (UI contract) instead of just the PascalCase alias would break the operator-on-web-store rejection UX. Guarded by AC-4 (keep snake_case).
- **F2 — standalone breakage** — removing a route that an e2e fixture still references. Guarded by the Edge-Case grep before removal.
- **F3 — touching the preserved auth-service tree** — out of scope; guarded by Scope exclusion.
