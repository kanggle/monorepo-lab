# TASK-BE-402 — Seller onboarding mints a real IAM seller-operator account (ADR-MONO-042): extend the `Seller` aggregate lifecycle + IAM provisioning (born-unified) + fail-soft + deactivation

**Status:** done

**Type:** TASK-BE
**Analysis model:** Opus 4.8 / **Recommended impl model:** Opus (cross-service IAM provisioning + lifecycle state machine + fail-soft availability)

> ✅ **ADR PREREQUISITE SATISFIED (was HARDSTOP-09).** The seller-onboarding-availability architecture decision is now recorded: **[ADR-MONO-042](../../../../docs/adr/ADR-MONO-042-ecommerce-seller-onboarding-iam-provisioning.md) — ACCEPTED 2026-06-18 (TASK-BE-402, this branch)**. The user explicitly ACCEPTED, **choosing D3 fail-soft** (the availability crux). D1–D6 are finalised and not re-litigated at execution; this task realizes them M1→M2→M3→M4. Realizes the last named **ADR-MONO-030 §3.4 Step 4 facet f** ("셀러 온보딩 흐름 + 실 IAM provisioning").

---

## Goal

Make ecommerce seller onboarding mint a **real IAM seller-operator account** (and a born-unified central identity) instead of leaving the seller as a bare `sellers` row whose authority is a TRUSTED operator-token claim (`X-Seller-Scope`, TASK-BE-363/375) with no account, credential, or identity behind it. Add a real seller lifecycle (`PENDING_PROVISIONING` → `ACTIVE`; `ACTIVE` → `SUSPENDED`/`CLOSED`) and make seller deactivation deactivate the backing account — all **fail-soft** (onboarding never blocks on IAM infra) and **authz net-zero** (the runtime seller-scope path is byte-unchanged). Reuse the admin-service `AccountServiceClient` client_credentials-JWT internal-call blueprint and the ADR-036 born-unified `resolveOrCreate` mint primitive.

## Scope

Realizes ADR-042 D1–D6, M1→M4. **Stays within ecommerce product-service + the named docs/spec/contract files.** Does NOT modify account-service/auth-service/admin-service code (it CALLS existing internal endpoints).

- **M1 (D1) — `Seller` aggregate lifecycle.** `SellerStatus += PENDING_PROVISIONING, SUSPENDED, CLOSED`; add nullable `account_id` + `identity_id` to `Seller` / `SellerJpaEntity`; Flyway migration (next free V-number = **V15**) adds the two nullable columns + `status` already exists; **backfill existing sellers → `ACTIVE` with null account/identity** (behavior-unchanged). Transitions: `register → PENDING_PROVISIONING`; `markProvisioned(accountId, identityId) → ACTIVE`; `suspend → SUSPENDED`; `close → CLOSED`. The per-tenant `default` seller stays `ACTIVE` (D8 anchor; never provisioned — it is the standalone degrade anchor).
- **M2 (D2/D3/D5) — IAM provisioning client + onboarding wiring.** New `IamClientCredentialsTokenProvider` + a seller-provisioning client (e.g. `AccountProvisioningClient`) in product-service infrastructure, mirroring admin `AccountServiceClient`: RestClient → `POST /internal/tenants/{t}/accounts` (role `SELLER`) + `POST /internal/tenants/{t}/identities:resolveOrCreate` (born-unified, `reuseExisting=true`), client_credentials Bearer JWT. Wire `RegisterSellerService.register` to D3 **fail-soft**: create `PENDING_PROVISIONING`, attempt provisioning, → `ACTIVE` + store `accountId`/`identityId` on success; on provisioning failure leave `PENDING_PROVISIONING` (logged `warn`, retryable). Expose a re-provision path (e.g. `RegisterSellerService.provisionPending(sellerId)` / `POST /api/admin/sellers/{id}/provision`) OR document the retry trigger.
- **M3 (D4) — deactivation.** Operator suspend/close (`POST /api/admin/sellers/{id}/suspend` / `/close`) → transition the aggregate + if `account_id` is stored, call account-service `POST /internal/accounts/{accountId}/lock` (suspend) or `PATCH /internal/tenants/{t}/accounts/{accountId}/status` (close/deactivate). Idempotent (re-suspend / re-lock = no-op) and null-safe (legacy/PENDING seller with null `account_id` → state transition without an IAM call = net-zero).
- **M4 (D contracts + spec).** Document the seller-provisioning internal-call usage under `specs/contracts/` (a new `specs/contracts/http/internal/product-to-account.md` or an addition to the marketplace contract); update `specs/features/multi-tenancy-and-marketplace.md §3` seller-lifecycle (onboarding now real, not "보류"); update product-service `architecture.md`; mark ADR-030 §3.4 Step 4 facet f realized by ADR-042/TASK-BE-402.

## Acceptance Criteria

- **AC-1 (M1)** — `SellerStatus` has `PENDING_PROVISIONING`, `ACTIVE`, `SUSPENDED`, `CLOSED`. `Seller` carries nullable `accountId`/`identityId`. Flyway V15 adds `account_id`/`identity_id` (nullable); existing sellers read back as `ACTIVE` with null account/identity (net-zero — `SellerTest`/IT proves byte-identical legacy behavior). The lifecycle transitions are guarded (illegal transitions throw).
- **AC-2 (M2)** — On onboarding, a seller is created `PENDING_PROVISIONING`; on successful provisioning it becomes `ACTIVE` with `accountId`/`identityId` stored; the provisioning client authenticates with a client_credentials Bearer JWT and calls the two account-service EPs. Unit test (mock account-service) proves the happy path.
- **AC-3 (M2 fail-soft, D3)** — When the provisioning call fails (account-service unavailable / 5xx / timeout), onboarding does NOT fail: the seller stays `PENDING_PROVISIONING` (no exception propagates to the controller), logged `warn`, and is re-provisionable. Unit test proves fail-soft (provisioning failure → seller PENDING, HTTP 201/onboarding success).
- **AC-4 (M2 idempotency, D5)** — Re-onboarding the same seller (same id/email) is harmless: `resolveOrCreate` + the account mint are idempotent on (tenant, email); a re-provision of a PENDING seller converges to ACTIVE without creating a duplicate identity; a stored non-null `accountId`/`identityId` is never silently overwritten. Unit test proves idempotent re-onboard / no-overwrite.
- **AC-5 (M3 deactivation, D4)** — Operator suspend → seller `SUSPENDED` + account `lock` called once; operator close → seller `CLOSED` + account deactivate/status called once. Idempotent (re-suspend = no-op, no second lock call) and null-safe (PENDING/legacy seller with null `accountId` transitions without an IAM call). Unit test proves deactivation + idempotency + null-safety.
- **AC-6 (D6 authz net-zero)** — No change to the gateway, `SellerScopeContextFilter`, `SellerScopeContext`, the ADR-025 axis-2 seller-scoped read, or the identity/role model. The seller-operator account carries the `SELLER` role that (via existing IAM role-derivation + claim forwarding) yields the same `X-Seller-Scope` claim. Verified by inspection + the unchanged seller-scope isolation IT staying GREEN.
- **AC-7 (M4)** — Seller-provisioning internal-call contract documented under `specs/contracts/`; `multi-tenancy-and-marketplace.md §3` updated (real onboarding, not "보류"); product-service `architecture.md` updated; ADR-030 §3.4 Step 4 facet f marked realized.
- **AC-8 (tests)** — `./gradlew :projects:ecommerce-microservices-platform:apps:product-service:test` GREEN. Testcontainers `@Tag("integration")` ITs written + compile-verified (NOT run locally — Docker blocked on the dev Windows host per `project_testcontainers_docker_desktop_blocker`; CI-Linux-authoritative).

## Related Specs

- `specs/features/multi-tenancy-and-marketplace.md` §3 (seller axis — the lifecycle section M4 updates from "보류" to real onboarding)
- `specs/services/product-service/architecture.md` (the seller aggregate's owning service — M4 updates)
- `docs/adr/ADR-MONO-042-ecommerce-seller-onboarding-iam-provisioning.md` (the ACCEPTED decisions D1–D6)
- `docs/adr/ADR-MONO-030-ecommerce-multivendor-marketplace-saas.md` §3.4 Step 4 facet f (the deferred facet realized) + D3 (the seller aggregate placement)
- `docs/adr/ADR-MONO-036-born-unified-identity-provisioning.md` P1/P2 (the `resolveOrCreate` fail-soft mint reused)

## Related Contracts

- **Consumed (account-service internal EPs — verified present, NOT modified by this task):**
  - `POST /internal/tenants/{tenantId}/accounts` — `TenantProvisioningController` → `ProvisionAccountUseCase`. Request `{email, password, displayName, locale, timezone, roles:["SELLER"], operatorId}`; response `{accountId, tenantId, email, status, roles, createdAt}`.
  - `POST /internal/tenants/{tenantId}/identities:resolveOrCreate` — `ResolveOrCreateIdentityController`. Request `{email, reuseExisting:true}`; response `{identityId, outcome}`.
  - `POST /internal/accounts/{accountId}/lock` — `AccountLockController`. Request `{reason:"ADMIN_LOCK", operatorId, ...}`.
  - `PATCH /internal/tenants/{tenantId}/accounts/{accountId}/status` — `TenantProvisioningController#changeStatus`. Request `{status, operatorId}`.
- **Authored (M4):** `specs/contracts/http/internal/product-to-account.md` (or marketplace-contract addition) — the product-service → account-service seller-provisioning call usage (client_credentials JWT, the four EPs above, fail-soft semantics).
- `specs/contracts/http/product-api.md` — the `/api/admin/sellers` surface (register + new provision/suspend/close if added).

## Edge Cases

- **account-service down at onboarding** → seller created `PENDING_PROVISIONING`, onboarding returns success (201), `warn` logged, re-provisionable (D3 fail-soft).
- **identity mint succeeds but account mint fails (or vice-versa)** → seller stays `PENDING_PROVISIONING`; re-provision only fills the null id(s); no duplicate identity (idempotent `resolveOrCreate`).
- **re-onboard / re-provision an already-ACTIVE seller** → no-overwrite of stored `accountId`/`identityId`; no duplicate account/identity (idempotent on (tenant, email)).
- **suspend/close a seller with null `account_id`** (pre-ADR legacy or still-PENDING) → state transition only, NO IAM call (net-zero / null-safe).
- **re-suspend an already-SUSPENDED seller** → no-op, no second `lock` call (idempotent).
- **the per-tenant `default` seller** → stays `ACTIVE`, never provisioned (the standalone single-store degrade anchor, D8) — `ensureDefaultSeller` must not attempt provisioning.
- **standalone profile (no platform IAM)** → provisioning is fail-soft; sellers stay `PENDING_PROVISIONING` (or the default-seller `ACTIVE` path serves the single-store degrade) — onboarding never hard-fails.
- **same email already a consumer** → `resolveOrCreate` converges the seller-operator onto the same central identity (born-unified, D5; same-origin issuance, not auto-merge).

## Failure Scenarios

- **F1 — fail-soft regression (onboarding blocks on IAM down).** If provisioning failure propagates and onboarding 5xx's, that violates D3. Guard: the provisioning call is wrapped fail-soft (mirroring admin `resolveOrCreateIdentity`'s swallow-to-null); AC-3 unit test asserts onboarding succeeds when account-service is unavailable.
- **F2 — duplicate account/identity on re-onboard.** If re-provision creates a second account/identity, that violates idempotency. Guard: rely on account-service's `uk_identities_tenant_email` race-safety + the account mint's idempotency; no-overwrite of stored non-null ids; AC-4 test.
- **F3 — cosmetic deactivation (account stays active).** If suspend/close only flips the label and the backing account can still authenticate, the seller-scope claim survives. Guard: D4 locks/deactivates the stored account; AC-5 test asserts the lock/status call is made (once) when `account_id` is present.
- **F4 — authz model drift.** If a new seller-authz axis is introduced or the gateway/filter changes, D6 net-zero is violated. Guard: AC-6 — no change to the runtime seller-scope path; the existing seller-scope isolation IT stays GREEN unchanged.
- **F5 — cross-DB write.** If product-service writes account_db directly, ADR-001 is violated. Guard: D2-A — all account/identity mutation is via the account-service internal EPs over client_credentials JWT.
- **F6 — missing seller-capable role.** If account-service rejects a `SELLER` role (allowlist), provisioning fails. Verified NOT the case: `AccountRoleName` accepts any `^[A-Z][A-Z0-9_]*$` ≤64; `SELLER` is accepted. If a future allowlist lands, this becomes a HARDSTOP finding (report, do not silently extend account-service).

## Notes

- **Verify-don't-assume (carried from ADR-042):** before wiring, re-confirm the four account-service EPs exist as mapped (they were verified present 2026-06-18). If a needed EP is missing or a needed role is rejected, **STOP (HARDSTOP)** and report rather than inventing/extending account-service.
- product-service has **no resilience4j** dependency today (unlike admin-service). The provisioning client may use a plain `RestClient` + manual try/catch fail-soft (the admin `@Retry`/`@CircuitBreaker` annotations are optional; do NOT add resilience4j just to mirror them — the fail-soft swallow is what matters).
- Testcontainers ITs are locally blocked (Windows host, `project_testcontainers_docker_desktop_blocker`); write + compile-verify, let CI-Linux run them.
