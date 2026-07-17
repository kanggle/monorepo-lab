# TASK-ERP-BE-030 — approval-service data-scope control is structurally unreachable + fail-open; honest de-scope to v2 (ERP-BE-029 sibling)

- **Type**: TASK-ERP-BE
- **Status**: review
- **Service**: approval-service (erp-platform)
- **Domain/traits**: erp / [transactional, audit-heavy, integration-heavy]
- **Analysis model**: Opus 4.8 · **Impl model**: Opus (authz + spec correction) · **Decision**: Option B (de-scope) — user-selected

## Goal

approval-service's spec (approval-api.md + architecture.md, listed as v1.0) promises subject data-scope
confinement — a transition whose subject's owning-department is outside the caller's org subtree → 403
`DATA_SCOPE_FORBIDDEN`, "checked inside `AuthorizationPort.evaluate(...)` BEFORE any repository call."
The code never implemented it. Honest de-scope: remove the dead/unreachable/fail-open implementation
attempt, correct the spec to defer subject data-scope to the v2 `permission-service`, and pin the v1
behavior with a test. This is the sibling of **ERP-BE-029** (which removed a dead machine-token fallback
in masterdata); the approval twin was never reconciled and is worse (never fires, and would fail open).

## AC-0 — Finding (audit, verified 2026-07-17)

- **Structurally unreachable**: `ApprovalApplicationService.authorizeWrite`/`authorizeRead` short-circuit
  `if (actor.canWriteErp()) return;` and otherwise call `authorizationPort.evaluate(actor, …, null)` with a
  **hardcoded `null`** target department. There is no subject→owning-department resolution anywhere
  (`submit` only calls `masterDataPort.isSubjectActive(...)`, a reference-integrity check; `MasterDataPort`
  exposes only `isSubjectActive`, no owning-department lookup). So the data-scope branch in
  `JwtBackedAuthorizationAdapter` (guarded by `targetDepartmentId != null`) is **never reached** — no
  approval endpoint can ever emit `DATA_SCOPE_FORBIDDEN`.
- **Fail-OPEN + wrong shape** even if reached: when `dataScopeDepartmentIds()==null` the deny predicate
  short-circuits (`&& …!= null` false) → falls through to `allow()` (masterdata's sibling fails CLOSED on
  null/empty). And it used a flat `.contains()`, not the subtree containment the architecture promised.
- **Vacuous test**: `JwtBackedAuthorizationAdapterTest.dataScopeDenied/dataScopeAllowed` instantiate the
  adapter directly with a non-null target — they pass in isolation while production never calls it that way.
  The integration harness (`AbstractApprovalIntegrationTest`) mints every token with `org_scope="*"`
  (platform scope), bypassing data-scope entirely. No HTTP test drives a narrow-scope actor against an
  out-of-scope subject.
- **Blast radius (moderate)**: an `erp.write` holder scoped to dept-A can create/submit/route approval
  requests whose subject belongs to dept-B — the confinement the contract promised. Mitigated:
  `approve`/`reject` are still gated by approver-eligibility (Separation of Duties, aggregate guard),
  `withdraw` is submitter-only, reads are participant-filtered. Requires a valid `erp.write` internal token.

## Decision — Option B (de-scope), user-selected

Enforcing it (Option A) would need a NEW cross-service capability: a masterdata owning-department resolver
port + endpoint + HTTP adapter + resilience + subtree logic — a v2-sized feature that introduces new deny
behavior. The control was never built. Per the ERP-BE-029 Option-B precedent (remove dead code + correct
docs, runtime unchanged), and because the JWT does not carry the subject's owning department, subject
data-scope confinement is deferred to the v2 `permission-service`.

## Scope

- **In**: remove the unreachable/fail-open data-scope branch from `JwtBackedAuthorizationAdapter` (role
  check → allow); replace the 2 vacuous adapter tests with one honest inverse test
  (`dataScopeNotEnforcedInV1`) that pins v1 ALLOWS an out-of-subtree target (guards re-introduction);
  correct approval-api.md + architecture.md to state v1 = role/scope authz and defer subject data-scope
  (`DATA_SCOPE_FORBIDDEN`) to the v2 permission-service.
- **Out**: building the v2 control (Option A); removing the `DENY_SCOPE` outcome /
  `DataScopeForbiddenException` / `GlobalExceptionHandler` mapping / error-registry entry — these are
  **retained as the reserved v2 vocabulary** (documented) so the v2 client can emit the code without a
  contract change (keeps `platform/error-handling.md` untouched); the `AuthorizationPort.evaluate`
  signature + `targetDepartmentId` (the documented v2 swap point).

## Acceptance Criteria

- **AC-1**: `JwtBackedAuthorizationAdapter.evaluate` enforces role/scope only (fail-closed on role); the
  `targetDepartmentId` data-scope branch is removed. Javadoc documents the v2 deferral.
- **AC-2**: `DENY_SCOPE`/`DataScopeForbiddenException`/handler mapping/error-registry entry retained,
  documented as reserved-for-v2 (not produced in v1). `platform/error-handling.md` unchanged.
- **AC-3**: `JwtBackedAuthorizationAdapterTest` — the 2 data-scope tests replaced by
  `dataScopeNotEnforcedInV1` (narrow-scope actor + out-of-subtree target → ALLOW). Mutation-checked
  (re-adding a data-scope denial turns it RED). Role tests unchanged.
- **AC-4**: approval-api.md (auth description + § Auth + error table) and architecture.md (DataScope point,
  error list, scenario table, test-plan line) corrected to defer subject data-scope to v2; `DATA_SCOPE_FORBIDDEN`
  reframed as reserved/not-emitted-in-v1.
- **AC-5**: approval-service unit + integration lanes green (runtime unchanged — the removed branch never fired).

## Related Specs / Contracts

- `projects/erp-platform/specs/contracts/http/approval-api.md`, `.../services/approval-service/architecture.md`
- Precedent: **TASK-ERP-BE-029** (masterdata dead machine-token fallback, Option B — remove + correct docs)

## Edge Cases / Failure Scenarios

- A future reader re-adds a data-scope check → `dataScopeNotEnforcedInV1` RED (guard).
- Error-registry drift → DATA_SCOPE_FORBIDDEN kept registered (retained vocabulary), no CI churn.
- v2 permission-service later enforces subtree → the `AuthorizationPort` seam + reserved outcome/exception
  let it emit `DATA_SCOPE_FORBIDDEN` without a contract change.
