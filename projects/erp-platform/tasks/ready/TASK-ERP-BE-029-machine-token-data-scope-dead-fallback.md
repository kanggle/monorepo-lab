# TASK-ERP-BE-029 — masterdata-service: machine-token data-scope fallback is dead code (documented flow 403s on scoped ops)

Status: ready

`(분석=Opus 4.8 / 구현 권장=Opus — security-relevant authz policy + masterdata↔read-model 정합)`

---

## Goal

Close a finding from **live full-stack verification** (2026-07-16) of erp-platform's gateway-fronted user path. masterdata-service's `ActorContextJwtAuthenticationConverter` contains a fallback intended to give `client_credentials` machine tokens platform-wide data-scope, but its predicate can **never match a real IAM-issued token** — it is dead code. As a result the **documented dev/machine-token flow 403s on every department-scoped operation**, and the code comment claims a behaviour that does not happen.

Concretely, [`ActorContextJwtAuthenticationConverter.java:44-50`](../../apps/masterdata-service/src/main/java/com/example/erp/masterdata/infrastructure/security/ActorContextJwtAuthenticationConverter.java#L44-L50):

```java
Set<String> roles = extractClaim(jwt, "roles", "role", "scope", "scopes");   // machine token → {"erp.write"}
Set<String> scope = new HashSet<>(AbacDataScope.fromClaimValues(
        jwt.getClaim("org_scope"), jwt.getClaim("data_scope")).tokens());     // machine token → {} (no org_scope/data_scope)
// client_credentials machine tokens default to platform-wide scope.
if (scope.isEmpty() && roles.contains("client_credentials")) {               // ← NEVER true for a real IAM token
    scope = Set.of("*");
}
```

`roles` is built from the `roles`/`role`/`scope`/`scopes` claims, so for a machine token it is `{"erp.write"}` (or `{"erp.read"}`), never containing the literal string `"client_credentials"`. IAM's `client_credentials` grant emits **no** `roles` claim and **no** `org_scope`/`data_scope` claim (verified: `iam-platform` `TenantClaimTokenCustomizer` omits `roles` for cc grants; `V0018__seed_erp_oidc_client.sql` sets no data-scope metadata). So the fallback is unreachable and machine tokens land with an **empty data-scope** → `RoleScopeAuthorizationAdapter` fail-closes to `403 DATA_SCOPE_FORBIDDEN` for any endpoint whose target department is non-null.

**This is NOT the finance FIN-BE-046 gap** — erp *does* enforce `erp.read`/`erp.write` correctly (live-confirmed: `erp.read` → write → 403 `PERMISSION_DENIED`). This is the opposite direction: fail-closed **under**-permit + dead security code, plus a documented flow that doesn't work.

A second, related smell to resolve: masterdata-service treats an **absent** data-scope as fail-closed (deny), while read-model-service's `ReadAuthorizationGate.orgScope()` treats an absent `org_scope` as `OrgScope.platform()` (net-zero, no narrowing). The two services **disagree** on the same claim's absent-semantics. One of them is wrong; this task must make them agree (or document why read vs write legitimately differ).

## Live repro (AC-0 already satisfied — reproduced, not inherited)

Lean stack (gateway + masterdata + mysql + redis) against the running iam auth-service (issuer `http://auth-service:8081`, kafka reused from the same stack), gateway on host `:18101`. Token = documented dev smoke-test flow: `curl -u erp-platform-internal-services-client:erp-dev -d "grant_type=client_credentials&scope=erp.write" …` (decoded: `tenant_id=erp`, `scope=["erp.write"]`, no `roles`, no `org_scope`).

```
POST /api/erp/masterdata/departments  {parentId:null}      (Bearer erp.write) -> 201   (target null → data-scope not consulted)
POST /api/erp/masterdata/departments  {parentId:<root>}    (Bearer erp.write) -> 403 DATA_SCOPE_FORBIDDEN
POST /api/erp/masterdata/employees    {departmentId:<...>}  (Bearer erp.write) -> 403 DATA_SCOPE_FORBIDDEN
POST /api/erp/masterdata/job-grades   {}                    (Bearer erp.write) -> 201   (target null)
```

So a valid documented `erp.write` machine token can create root departments / job-grades / business-partners but **cannot create a child department, an employee, or a cost center** — anything with a department target. The `scope = Set.of("*")` fallback that was meant to prevent exactly this never fires.

Everything else in the sweep was correct (recorded for context): `erp.read`→write 403 `PERMISSION_DENIED` (scope enforced), no-token 401, wrong-tenant(finance) 403 `TENANT_FORBIDDEN`, framework 404/405(+`Allow`)/415 all correct (MONO-420/421 reached erp — not 500), idempotency-key required, audit-log same-transaction.

## Scope

**In:**
- Decide the direction (AC-1) and implement it in masterdata-service.
- Resolve the masterdata (fail-closed) vs read-model (platform-default) absent-data-scope inconsistency — make the two services agree, or document the deliberate read/write asymmetry in both places.
- Add a regression test that drives a real signed **machine-shaped** JWT (scope only, no `org_scope`) through the HTTP layer against a department-scoped endpoint and asserts the chosen behaviour. The existing HTTP integration tests never exercise a write end-to-end with a machine-shaped token (`MasterdataLifecycleIntegrationTest` bypasses HTTP; `DepartmentControllerSliceTest` disables filters) — that is the coverage gap that let this ship.
- Reconcile the docs: whichever direction is chosen, `PROJECT.md` / `iam-integration.md`'s dev smoke-test flow must end up accurate about what the machine token can and cannot do.

**Out:**
- The gateway `RoleAdmissionFilter` and `SecurityConfig` `.authenticated()` shape (correct — value enforcement is intentionally at `RoleScopeAuthorizationAdapter`).
- The scope (read/write) enforcement itself (correct — do not touch `RoleScopeAuthorizationAdapter`'s read/write predicate).
- IAM token issuance, **unless** AC-1 chooses to add an `org_scope`/`token_type` claim to the erp machine client (then the IAM V-seed / customizer change is in scope, cross-project atomic).

## Acceptance Criteria

- **AC-0 (repro gate):** re-run the live repro (or an equivalent Testcontainers HTTP test with a machine-shaped signed JWT — `scope=erp.write`, no `org_scope`) and confirm on current `main` that a child-department/employee create still 403s `DATA_SCOPE_FORBIDDEN` and the fallback still doesn't fire. Numbers here are a 2026-07-16 observation — treat as a hypothesis, re-measure.
- **AC-1 (direction — architecture decision, do not skip; security-relevant):** choose and record:
  - **Option A — activate the intended platform-wide default for machine tokens.** Fix the predicate to detect a real `client_credentials` token reliably (e.g. `sub == aud` [cc tokens have sub==client_id], or absent-`org_scope` **and** no human-role claim, or have IAM add a `token_type`/`gty` claim the converter keys on). Makes the documented flow work and aligns masterdata with read-model's platform-default. **This EXPANDS a machine token to platform-wide writes across all departments — a real privilege grant in an `audit-heavy` + `internal-system` context; requires explicit sign-off that a workload legitimately needs machine-scoped writes.**
  - **Option B — remove the dead fallback + keep fail-closed + fix the docs.** Delete the unreachable `if (… roles.contains("client_credentials"))` block (it is misleading dead security code), keep masterdata fail-closed on absent data-scope, and document that department-scoped writes require an **operator** token carrying `org_scope` (the machine client is read + unscoped-write only). Least-privilege preserved. Then reconcile read-model to the same fail-closed semantics for writes, or document why its reads may default to platform.
  - **Recommendation:** if no v1 workload actually needs the machine client to perform department-scoped writes, prefer **B** (safer; the fallback is dead code either way and the documented flow just needs honest docs). Choose **A** only if such a workload exists and platform-wide machine write is an accepted policy.
- **AC-2:** whichever direction, the dead/unreachable code is gone (no `if` block that can never be true), and the code comment matches actual behaviour.
- **AC-3:** masterdata and read-model no longer silently disagree on absent-data-scope semantics — either they match, or the divergence is documented at both sites with the rationale.
- **AC-4:** regression test (machine-shaped signed JWT → department-scoped endpoint) asserts the chosen outcome; `./gradlew :projects:erp-platform:apps:masterdata-service:check` + `:integrationTest` green (CI Linux authority — local host flakes on `redis:7-alpine` Testcontainers startup under resident-stack load).
- **AC-5:** the documented dev smoke-test (`PROJECT.md` / `iam-integration.md`) is accurate: a reader following it either succeeds (Option A) or is told the truth about the machine token's limits (Option B).

## Related Specs

- `projects/erp-platform/specs/integration/iam-integration.md` — § Token 검증 규칙 #5, dev smoke-test flow, Error Responses.
- `projects/erp-platform/specs/services/masterdata-service/architecture.md` — E6 authorization / data-scope (`org_scope`) model.
- `projects/erp-platform/PROJECT.md` § IAM IdP Integration — the dev token curl.
- `docs/adr/ADR-MONO-025` (AbacDataScope canonical reader — `org_scope`/`data_scope` dual-read).

## Related Contracts

- `projects/erp-platform/specs/contracts/http/masterdata-api.md` — `DATA_SCOPE_FORBIDDEN` (403) row.
- `platform/error-handling.md` — `DATA_SCOPE_FORBIDDEN` code.

## Edge Cases

- A **human operator** token carrying `org_scope` (subtree) must keep working exactly as today — the fix must not widen or narrow the human path.
- A SUPER_ADMIN wildcard (`tenant_id=*`) token — does it carry `org_scope`? If not, Option B's fail-closed would block a legitimately-privileged operator on scoped writes; ensure the wildcard / operator-role path still resolves data-scope (it may already via `isOperator()` / `*` handling — verify).
- Option A's machine-detection predicate must not accidentally match a human token that merely lacks `org_scope` (that would grant a human platform-wide writes). `sub == aud` is a strong cc-only signal; a bare "absent org_scope → platform" is NOT (it would also catch mis-provisioned human tokens).
- read-model's platform-default for absent `org_scope` may be intentionally lenient for **reads** — confirm before changing it; the write side is where fail-closed matters most.

## Failure Scenarios

- **Over-grant (Option A done loosely):** "absent org_scope → platform-wide" applied to all tokens lets any human token without org_scope write every department → privilege escalation. Gate strictly on a cc-only signal + cover with AC-3.
- **Silent divergence persists:** fixing masterdata but leaving read-model's opposite absent-semantics undocumented → the next reader hits the same confusion. AC-3 forces reconciliation.
- **Docs still lie (Option B without AC-5):** removing the dead code but leaving the smoke-test doc implying the machine token can do everything → the finding recurs as a support question.
