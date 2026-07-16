# TASK-FIN-BE-046 — account-service: declared OAuth2 scope enforcement is absent (read token performs writes)

Status: ready

`(분석=Opus 4.8 / 구현 권장=Opus — 규제 fintech authz semantics + scope→authority 매핑 + 테스트)`

---

## Goal

Close the divergence found by **live full-stack verification** (2026-07-16) of finance-platform's gateway-fronted user path: `specs/integration/iam-integration.md` **§ Token 검증 규칙 #5** declares that each downstream service's `SecurityConfig` **enforces OAuth2 scope** (`Jwt.getClaimAsString("scope")`), and the **§ Error Responses** table declares `403 FORBIDDEN` for "유효 토큰이지만 scope 부족." **Neither happens.** `account-service/.../infrastructure/security/SecurityConfig.java:44` gates `/api/finance/**` with `.authenticated()` only — no per-endpoint scope authority. A `finance.read`-only token performs every write (open account, hold, capture, transfer). This is a least-privilege gap in a `regulated + audit-heavy` fintech platform and a **declared-rule-does-not-reach-enforcement** defect that CI cannot see (no test asserts scope-based denial; `CrossTenantHttpIntegrationTest` covers `tenant_id` only).

This is not a regression from a specific prior task — it is an original gap present since account-service bootstrap (TASK-FIN-BE-001). Related: the gateway `RoleAdmissionFilter` (`libs/java-gateway`, MONO-416/417/419) intentionally does **presence**-admission (`roleOrScope()`), so the value-enforcement locus the spec names is the **service** `SecurityConfig`, not the gateway.

## Live repro (AC-0 already satisfied — reproduced, not inherited)

Lean stack (gateway + account-service + mysql + redis) brought up against the running iam auth-service (issuer `http://auth-service:8081`), gateway on host `:18100`. Token minted with **`scope=finance.read` only** (decoded claim confirmed `"scope":["finance.read"]`, no `roles`):

```
# read-scope token opens an account — a WRITE — and succeeds:
POST /api/finance/accounts  (Bearer <finance.read>, Idempotency-Key set)
  -> HTTP 201  {"data":{"accountId":"019f68b0-91a0-...","status":"PENDING_KYC",...}}

# read-scope token reaches the fund-movement endpoints; only the DOMAIN gate stops it (NOT authz):
POST /api/finance/accounts/{id}/holds     (Bearer <finance.read>) -> 409 ACCOUNT_NOT_ACTIVE   (authz passed)
POST /api/finance/accounts/{id}/transfers (Bearer <finance.read>) -> 404 ACCOUNT_NOT_FOUND     (authz passed)
```

A `403 FORBIDDEN`/scope-denial never occurs. The only barrier between a read-scoped credential and moving money is the orthogonal KYC/ACTIVE state machine — on an ACTIVE account a `finance.read` token could place holds and initiate transfers.

**Everything else in the live sweep passed** (recorded for context, not part of this task): no-token→401, wrong-tenant(erp)→403 `TENANT_FORBIDDEN`, framework 404/405(+`Allow`)/415 all correct (MONO-420/421 confirmed reaching the deployed artifact — not 500), idempotency key-required/replay/conflict all correct, `/kyc/upgrade` operator-only 403 `PERMISSION_DENIED` for the client_credentials (no-`roles`) token.

## Scope

**In:**
- Decide the direction (see AC-1) and implement it.
- If **enforce** (recommended): map the JWT `scope` claim to Spring authorities and require `SCOPE_finance.write` (or method-based equivalent) on mutating `/api/finance/**` endpoints (`POST/PUT/PATCH/DELETE`), require `SCOPE_finance.read` (or `read`-or-`write`) on GET read endpoints. Keep the existing `ActorContextJwtAuthenticationConverter` role behaviour (operator gate for `/kyc/upgrade`) intact and composable.
- Add a test that asserts a `finance.read` token is **denied** (`403 FORBIDDEN`) on a write endpoint and **allowed** on a read endpoint — the missing coverage that let this ship.
- Reconcile the spec: whichever direction is chosen, `iam-integration.md` § Token 검증 규칙 #5 + § Error Responses must end up matching the enforced behaviour.

**Out:**
- gateway `RoleAdmissionFilter` value-enforcement (its presence-admission is by design — ADR/MONO-416/417/419); do not change `libs/java-gateway`.
- `ledger-service` scope posture (separate service; verify + ticket separately if the same gap exists there — this task is account-service-scoped).
- Any change to the tenant gate or operator/role checks (those are correct — live-confirmed).

## Acceptance Criteria

- **AC-0 (repro gate):** before implementing, re-run the live repro above (or an equivalent Testcontainers HTTP test with a `finance.read`-only signed JWT) and confirm the write **still** succeeds on current `main`. The numbers in this ticket are a 2026-07-16 observation — treat them as a hypothesis and re-measure; if `main` already enforces scope by the time this is picked up, close as already-fixed.
- **AC-1 (direction — architecture decision, do not skip):** choose and record in the task/PR one of:
  - **Option 1 — enforce (recommended).** The spec is authoritative (Source-of-Truth > code) and a distinct `finance.write` scope already exists and is separately issued — strong signal that least-privilege is intended. Make `SecurityConfig` require write scope on mutations.
  - **Option 2 — correct the spec.** If a deliberate decision is made that finance v1 scope is coarse (presence-only at the gateway; tenant + role are the real gates), amend `iam-integration.md` #5 + the error table to stop claiming value-based scope enforcement, and record the rationale (ideally an ADR note). This weakens the documented posture and should be an explicit, owned decision — not the default.
- **AC-2:** implemented behaviour matches the (possibly amended) spec exactly. If Option 1: `finance.read` token → write endpoint → `403 FORBIDDEN` with the platform error envelope; `finance.write` token → write endpoint → normal domain behaviour; read endpoints unchanged for read tokens.
- **AC-3:** a regression test asserts the read-token-denied-on-write / allowed-on-read matrix (the coverage gap that hid this). Prefer the existing `CrossTenantHttpIntegrationTest` HTTP+signed-JWT harness style so it exercises the real filter chain.
- **AC-4:** `./gradlew :projects:finance-platform:apps:account-service:check` green; no change to tenant/operator behaviour (existing tests stay green).
- **AC-5:** if Option 1, confirm the operator-only `/kyc/upgrade` path still returns `403 PERMISSION_DENIED` for a scope-only client_credentials token (role gate must not be shadowed by the new scope gate).

## Related Specs

- `projects/finance-platform/specs/integration/iam-integration.md` § Token 검증 규칙 #5 (line ~97), § Error Responses (line ~127) — the declared-but-unenforced control.
- `projects/finance-platform/specs/services/account-service/architecture.md` — security section.
- `projects/finance-platform/PROJECT.md` — traits `regulated + audit-heavy` (why least-privilege matters here).

## Related Contracts

- `projects/finance-platform/specs/contracts/http/account-api.md` — error-code table (`FORBIDDEN` mapping).
- `platform/error-handling.md` — `FORBIDDEN` / `403` envelope.

## Edge Cases

- A token carrying **both** `finance.read` and `finance.write` → writes allowed, reads allowed.
- A SUPER_ADMIN wildcard token (`tenant_id=*`) — does it carry finance scopes? If not, enforcing write scope could lock out a legitimately-privileged operator token. Decide whether `*`-tenant or a `roles`-bearing operator token bypasses the scope gate (role OR scope, mirroring the gateway's `roleOrScope` intent) — align with the gateway so the two layers do not disagree about validity.
- The client_credentials token has `scope` but no `roles`; the human/operator OIDC token (console read consumer, ADR-MONO-013) has `roles` and `X-Token-Type=user` — ensure the chosen predicate does not break the console's **read** consumption (`GET` surfaces must stay reachable for the console operator token).
- `X-Scopes` header mentioned in spec #5 as an alternative source — confirm whether any upstream sets it; if not, enforce off the JWT claim only and drop the header mention.

## Failure Scenarios

- **Over-enforce:** requiring `SCOPE_finance.write` on GETs, or requiring scope where a role-only operator token is legitimate, breaks the console read consumer or operator flows → gate on **read-or-write / role-or-scope**, and cover with AC-3/AC-5 tests.
- **Shadowed role gate:** a coarse `denyAll` on missing scope could pre-empt the `/kyc/upgrade` operator 403 with a different code → keep the operator check reachable (AC-5).
- **Spec drift the other way:** choosing Option 2 but leaving the error table's `403 FORBIDDEN scope 부족` row → the doc still claims a control that doesn't exist. Amend both #5 and the table together.
