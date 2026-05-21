# Task ID

TASK-BE-307

# Title

GAP admin-service admin-on-behalf-of operator profile mutation endpoint (`PATCH /api/admin/operators/{operatorId}/profile`) — SUPER_ADMIN can provision another operator's `operatorContext.defaultAccountId` (and future profile carrier attributes); cross-operator counterpart of TASK-BE-306 self-serve endpoint

# Status

review

# Owner

backend

# Task Tags

<!-- api | event | deploy | code | test | adr | onboarding -->

- api
- code
- test

---

# Required Sections (must exist)

- Goal
- Scope (in/out)
- Acceptance Criteria
- Related Specs
- Related Contracts
- Edge Cases
- Failure Scenarios

---

# Dependency Markers

- **depends on**: TASK-BE-306 (DONE — spec PR #704 / impl PR #705 / close chore #706; `admin_operators.finance_default_account_id` column + `OPERATOR_PROFILE_UPDATE` action_code + `AdminOperator.withFinanceDefaultAccountId` factory + `AdminOperatorPort.changeFinanceDefaultAccountId` port + `UpdateOwnOperatorProfileUseCase` self-serve use case all already on main `22952bfd`). This task is the **cross-operator sister** — reuses the column, the action_code, the domain factory, and the port method; adds a parallel admin-on-behalf-of use case + new controller endpoint.
- **origin**: TASK-BE-306 § Out of Scope explicit: *"Admin-on-behalf-of mutation (`PATCH /api/admin/operators/{operatorId}/profile`, SUPER_ADMIN-only) — explicitly future task. v1 self-serve only."* This task closes that deferred follow-up.
- **prerequisite for**: TASK-PC-FE-017 (console-web admin profile-edit UI — SUPER_ADMIN-gated per-row action in `OperatorsScreen`). Sequential — atomic-cross-project rejected for the same reason as the BE-304 → PC-FE-014 / BE-306 → PC-FE-016 chains (independent CI, independent tests, deferred consumer until producer surface is live).
- **spec-first**: spec PR (this file + `admin-api.md` new endpoint section) → impl PR (use case + controller + IT + unit) → close chore PR (this file `ready → done` + INDEX move).
- **no ADR** (HARDSTOP-09 not triggered): this task adds a single new endpoint to the existing admin operators-management family (`PATCH /api/admin/operators/{operatorId}/roles` + `PATCH /api/admin/operators/{operatorId}/status` are the established peers — same authentication boundary, same `operator.manage` permission, same `X-Operator-Reason` requirement, same `{operatorId}` path-variable shape, same audit-row policy). No new architectural decision: the column, the action_code, the port, and the self-serve precedent already exist (TASK-BE-306). This task is the missing admin half of the same surface.

---

# Goal

TASK-BE-306 ships the self-serve `PATCH /api/admin/operators/me/profile` endpoint and TASK-PC-FE-016 ships its UI. An operator can now provision their own `finance_default_account_id` through the console UI. **But there is no admin path** — a SUPER_ADMIN cannot provision another operator's default account (e.g. during operator onboarding, or when an operator's UI is unreachable, or when bulk-updating an organization's defaults). Today the only admin-on-behalf-of options are DBA SQL on `admin_operators.finance_default_account_id` or a Flyway seed migration — both off-the-console-rails operational anti-patterns.

Add the missing admin-on-behalf-of surface:

```
PATCH /api/admin/operators/{operatorId}/profile
Content-Type: application/json
Authorization: Bearer <operator-token>
X-Operator-Reason: <non-empty reason string>

{
  "operatorContext": {
    "defaultAccountId": "acc-uuid-7"   // or null to clear
  }
}

→ 204 No Content
```

The endpoint updates `admin_operators.finance_default_account_id` of the operator identified by the `{operatorId}` path variable (NOT the caller — that path is `/me/profile`, BE-306). Caller must hold `operator.manage` permission (granted only to `SUPER_ADMIN`) and present a non-empty `X-Operator-Reason`. Target operator must be in the caller's tenant scope (`admin_operators.tenant_id == caller.tenant_id` OR `caller.tenant_id == '*'` platform-scope per ADR-002).

**Self via admin path is forbidden** — calling `PATCH /api/admin/operators/{self.operator_id}/profile` returns `400 SELF_PROFILE_UPDATE_FORBIDDEN_VIA_ADMIN_PATH`. The caller must use `/me/profile` (BE-306) for self-serve. This keeps the two surfaces cleanly separated for audit-trail clarity (self-action audit rows use `reason = "<self_profile_update>"` constant; cross-operator audit rows use the caller-typed reason string).

An `admin_actions` audit row is appended with `action_code = "OPERATOR_PROFILE_UPDATE"` (reuse — same as BE-306; the `target_id != operator_id` field tells admin-from-self apart), `operator_id = caller`, `target_type = "OPERATOR"`, `target_id = {operatorId}` (the operated-on operator), `permission_used = "operator.manage"`, `outcome = SUCCESS`, `detail = null` (no PII; the new state is observable via the next GET).

**This task is producer-only**; the console-web admin UI (per-row "Profile 편집" action in `OperatorsScreen`) is the sequential follow-up (TASK-PC-FE-017).

# Decision authority (why admin path forbids self, why reuse action_code, why X-Operator-Reason required, why tenant-scoped, why no Idempotency-Key)

- **Why admin path forbids self (use `/me/profile` for self-serve)**:
  - **Audit clarity**: BE-306 audit rows use `reason = "<self_profile_update>"` constant (self-flow exempt from X-Operator-Reason per `admin-api.md § Authentication > X-Operator-Reason in Exceptions sub-tree`). Admin-on-behalf-of audit rows use the caller-typed reason string (cross-operator action requires explicit operator intent). If self via admin path were allowed, the audit log would have two reason-string formats for self-action — indistinguishable at query time, defeating the purpose of the self-flow exemption.
  - **Permission clarity**: me/profile = no permission needed (valid operator token sufficient); admin/{id}/profile = `operator.manage` required. If self via admin path were allowed, callers would need `operator.manage` to edit their own profile, which contradicts the self-serve UX premise.
  - **UI clarity**: the console-web side (PC-FE-017) gates the admin per-row action on `operator.manage` permission; a SUPER_ADMIN trying to edit their own profile through the admin path would see the same dialog twice (self in `OperatorsScreen` per-row action + self in the `me/profile` form below). Force separation at the producer for monotonic UI flow.
  - **Precedent**: `PATCH /api/admin/operators/{operatorId}/status § Errors` already includes `400 SELF_SUSPEND_FORBIDDEN` (self-suspend forbidden via admin path). Same pattern: cross-operator-only admin surface, self-flow goes elsewhere. This task adds `400 SELF_PROFILE_UPDATE_FORBIDDEN_VIA_ADMIN_PATH` — exactly the same shape, exactly the same reasoning.
- **Why reuse `action_code = "OPERATOR_PROFILE_UPDATE"` (NOT a new `ADMIN_OPERATOR_PROFILE_UPDATE`)**:
  - The semantic action is identical (column UPDATE on `admin_operators.finance_default_account_id`). The actor differs (self vs cross-operator), but the action_code names the *what* not the *who*. `target_id != operator_id` distinguishes admin-on-behalf-of from self at query time without a new enum value.
  - Existing precedent: `OPERATOR_STATUS_CHANGE` action_code is used for both self-status-change scenarios (none in v1 — self-status is forbidden) and cross-operator status changes. Action code = semantic action, actor differentiation = (operator_id, target_id) tuple.
- **Why `X-Operator-Reason` required** (NOT the self-flow exemption):
  - Cross-operator mutation = "다른 대상에 대한 운영 명령" per `admin-api.md § X-Operator-Reason in Exceptions sub-tree`. Self-flow exemption applies *only* to "요청자 본인의 인증 플로우". This is not a self-flow.
  - All sibling admin endpoints (`/roles`, `/status`) require `X-Operator-Reason`. Symmetry preserved.
- **Why tenant-scoped (target must be in caller's tenant scope)**:
  - ADR-002 `admin_operators.tenant_id` + `*` sentinel governs all admin operator-management actions. `/roles` + `/status` already enforce: caller tenant `*` may target any operator; caller tenant `<X>` may target operators with tenant `<X>` only.
  - Cross-tenant escalation via profile mutation would be a privilege escalation hole if relaxed (e.g. SUPER_ADMIN of tenant A sets a tenant-B operator's default to a tenant-B finance account that the actor never had access to — though the value is opaque, the *audit trail manipulation* is the real risk).
  - **403 `TENANT_SCOPE_DENIED`** (same code as other admin endpoints).
- **Why NO `Idempotency-Key` (mirror `/roles` + `/status`, NOT `POST /operators` create)**:
  - PATCH is naturally idempotent for the same body (final column state is identical regardless of retry).
  - `/roles` + `/status` are the precedent peers — both PATCH, both require `X-Operator-Reason`, both omit `Idempotency-Key`. This task follows that pattern exactly.
  - A retried admin profile mutation produces a duplicate audit row, which is a *faithful record* of the duplicate intent (same reasoning as BE-306). If a real deduplication requirement surfaces later, a follow-up task adds the key (mirror of BE-051 for outbound shipment commands).
- **Why no separate "Profile Read" endpoint** (caller relies on `GET /api/admin/console/registry` for self read, but NOT for another operator's profile):
  - **OUT OF SCOPE here** — the v1 console-web UI gets the target operator's current profile value either from the operators list response (if extended — see PC-FE-017 spec) OR by reading it after a PATCH and re-fetching. A dedicated `GET /api/admin/operators/{operatorId}/profile` endpoint is not needed for the v1 UX, and adding it is a separate task if the consumer needs it (avoid speculative endpoint creation).
  - If the consumer (PC-FE-017) needs the current value for the dialog's initial state, the operators list (`GET /api/admin/operators`) can be extended in a sibling spec task to include `operatorContext` on each item — same shape as the registry response. That extension is OUT OF SCOPE here.

---

# Scope

## In Scope

**Specs (spec PR)**:

- `projects/global-account-platform/specs/contracts/http/admin-api.md`:
  - Add a new section `## PATCH /api/admin/operators/{operatorId}/profile` placed **immediately after** the existing `## PATCH /api/admin/operators/{operatorId}/status` section (sibling admin {operatorId}/* placement; structural neighbor).
  - Body covers: purpose, Auth (operator JWT + `operator.manage` permission), Headers (Authorization + X-Operator-Reason required), Path Variable, Request shape (`operatorContext.defaultAccountId: string | null`), Response (`204 No Content`), Errors table (`400 INVALID_REQUEST` / `400 SELF_PROFILE_UPDATE_FORBIDDEN_VIA_ADMIN_PATH` / `400 REASON_REQUIRED` / `401 TOKEN_INVALID` / `403 PERMISSION_DENIED` / `403 TENANT_SCOPE_DENIED` / `404 OPERATOR_NOT_FOUND` / `409 OPTIMISTIC_LOCK_CONFLICT`), Side Effects (column UPDATE + `admin_actions` row with reused `OPERATOR_PROFILE_UPDATE` action_code, `target_id = {operatorId}`, `permission_used = "operator.manage"`).
- This task file itself.

**Code (impl PR — out of scope here, listed for the dispatch agent to know the shape)**:

- `projects/global-account-platform/apps/admin-service/src/main/java/com/example/admin/application/UpdateOperatorProfileUseCase.java` (new) — admin-on-behalf-of use case. Signature: `void update(AdminOperatorId callerInternalId, AdminOperatorId targetOperatorPublicId, String defaultAccountId, String reason)`.
  - Lookup target operator via `AdminOperatorPort` (existing port from BE-306 reuse).
  - Cross-tenant check: target's tenant must match caller's, OR caller is `*` platform-scope (use existing `TenantScopeDeniedException` pattern from `/roles` + `/status`).
  - Self check: if `target.internalId == caller.internalId` → throw new `SelfProfileUpdateForbiddenException` (mapped to 400 `SELF_PROFILE_UPDATE_FORBIDDEN_VIA_ADMIN_PATH`).
  - Apply `target.withFinanceDefaultAccountId(defaultAccountId)` (reuse BE-306 record factory).
  - Save via `AdminOperatorPort.changeFinanceDefaultAccountId(target.internalId, defaultAccountId, Instant.now())` (reuse BE-306 port method).
  - Append audit row via existing `AdminActionAuditor` port: `action_code = OPERATOR_PROFILE_UPDATE` (reuse), `operator_id = caller.internalId`, `target_type = "OPERATOR"`, `target_id = target.operator_id` (public UUID), `permission_used = "operator.manage"`, `outcome = SUCCESS`, `reason = <X-Operator-Reason header value>`, `detail = null`.
  - `@Transactional` single transaction wraps both writes.
- `projects/global-account-platform/apps/admin-service/src/main/java/com/example/admin/application/exception/SelfProfileUpdateForbiddenException.java` (new — mirror `SelfSuspendForbiddenException`).
- `projects/global-account-platform/apps/admin-service/src/main/java/com/example/admin/presentation/dto/UpdateOperatorProfileAdminRequest.java` (new — same shape as BE-306's `UpdateOperatorProfileRequest` — `{ operatorContext: { defaultAccountId } }`; reuse the existing custom `@JsonCreator(Map)` key-presence detection pattern OR refactor BE-306's DTO into a shared type if cleaner — chosen at impl).
- `projects/global-account-platform/apps/admin-service/src/main/java/com/example/admin/presentation/OperatorAdminController.java` — add `@PatchMapping("/api/admin/operators/{operatorId}/profile")` handler:
  - `@PathVariable String operatorId`
  - `@RequestHeader("X-Operator-Reason") @NotBlank String reason` (existing reason-required pattern; absent → 400 REASON_REQUIRED via `ReasonRequiredException` mapping)
  - `@RequestBody @Valid UpdateOperatorProfileAdminRequest body`
  - `@RequiresPermission("operator.manage")` (existing aspect — `RequiresPermissionAspect`)
  - Auth principal = caller operator (mirror `/roles` + `/status` handler)
  - Returns `204 No Content`
- `projects/global-account-platform/apps/admin-service/src/main/java/com/example/admin/presentation/advice/AdminExceptionHandler.java` — verify `SelfProfileUpdateForbiddenException` → 400 `SELF_PROFILE_UPDATE_FORBIDDEN_VIA_ADMIN_PATH` is mapped (if not present, ADD); `TenantScopeDeniedException` → 403 `TENANT_SCOPE_DENIED` (already mapped from BE-249); `OperatorNotFoundException` → 404 `OPERATOR_NOT_FOUND` (already mapped); `OptimisticLockingFailureException` → 409 `OPTIMISTIC_LOCK_CONFLICT` (already mapped from BE-306).
- Tests:
  - **Unit (`UpdateOperatorProfileUseCaseTest`)**: 5 cases —
    - (a) cross-tenant target (target.tenant=X, caller.tenant=*) → set succeeds (platform-scope authority); audit row inserted with target_id ≠ caller.
    - (b) same-tenant target (target.tenant=X, caller.tenant=X) → set succeeds.
    - (c) cross-tenant target (target.tenant=X, caller.tenant=Y) → throws `TenantScopeDeniedException`; no save, no audit.
    - (d) target not found → throws `OperatorNotFoundException`; no save, no audit.
    - (e) self via admin path (target.internalId == caller.internalId) → throws `SelfProfileUpdateForbiddenException`; no save, no audit.
  - **Slice/Controller (`OperatorAdminControllerTest`)**: 5 new cases —
    - (a) valid admin PATCH → 204; X-Operator-Reason captured.
    - (b) missing X-Operator-Reason → 400 `REASON_REQUIRED`.
    - (c) caller lacks `operator.manage` → 403 `PERMISSION_DENIED`.
    - (d) target not found → 404 `OPERATOR_NOT_FOUND`.
    - (e) self via admin path → 400 `SELF_PROFILE_UPDATE_FORBIDDEN_VIA_ADMIN_PATH`.
  - **IT (`OperatorProfileAdminIntegrationTest`)** — separate file from BE-306's `OperatorProfileIntegrationTest` (which covers self-serve me/profile):
    - (1) SUPER_ADMIN platform-scope caller (`tenant='*'`) sets target's defaultAccountId → 204; subsequent registry GET *as target operator* shows finance item with new `operatorContext.defaultAccountId`; `admin_actions` table contains row with `action_code=OPERATOR_PROFILE_UPDATE`, `operator_id=caller`, `target_id=target.operator_id`, `outcome=SUCCESS`, `reason=<the X-Operator-Reason value>`.
    - (2) Same-tenant SUPER_ADMIN sets target → 204 (verifies non-platform-scope still works within tenant).
    - (3) Cross-tenant caller targets out-of-scope operator → 403 `TENANT_SCOPE_DENIED`; no column change.
    - (4) Self via admin path → 400 `SELF_PROFILE_UPDATE_FORBIDDEN_VIA_ADMIN_PATH`; no column change; no audit row.
    - (5) Missing X-Operator-Reason → 400 `REASON_REQUIRED`; no column change.
    - (Audit row IT assertions folded into cases 1 + 2 — assert reason is the caller-typed string, NOT `<self_profile_update>` constant.)

## Out of Scope

- **Self via admin path acceptance** — explicitly rejected (forced separation; see § Decision authority).
- **Operators list response extension to include `operatorContext`** — the admin UI may need the current value to populate the dialog's initial state. That extension to `GET /api/admin/operators` is a separate task (sibling spec extension; trivial — add `operatorContext` to `OperatorSummaryResponse`). Not required for this task; PC-FE-017 can read the current value via post-save GET re-fetch initially.
- **A dedicated `GET /api/admin/operators/{operatorId}/profile` endpoint** — out of scope. Not needed for v1 (see § Decision authority "Why no separate Profile Read endpoint"). If a future consumer truly needs it, a separate task.
- **Other `operatorContext` carrier attributes (wmsDefaultWarehouseId etc.)** — same producer infrastructure (column + endpoint) but separate task per attribute (paired with the consumer activation task per the BE-304 → BE-306 → PC-FE-014 → PC-FE-016 pattern).
- **Bulk profile-set for multiple operators** — out of scope. Mirror of `POST /api/admin/accounts/bulk-lock` would be a separate task; v1 single-target only.
- **Validation against finance-platform** — same as BE-306: opaque value, no cross-service round-trip; stale id surfaces via finance `404 ACCOUNT_NOT_FOUND` on the eventual BFF call.
- **Console-bff orchestrator** — explicitly NOT applicable (same reason as PC-FE-016 — single-domain mutation; ADR-MONO-017 D2 Option A + § 2.4.9 hard invariant + me/password precedent). PC-FE-017 will use the console-web direct proxy pattern (`/api/operators/[operatorId]/profile/route.ts`).
- **ADR amendment** — none. ADR-MONO-017 D4 HARD INVARIANT unaffected (GAP-internal endpoint, not a console-bff outbound). ADR-MONO-013 / 014 / 015 / 016 / D6 — none touched.

# Acceptance Criteria

- **AC-1 (spec PR atomic)**: spec PR lands **exactly two files** modified — this task file + `specs/contracts/http/admin-api.md`. No `data-model.md` edit (column + action_code already exist from BE-304 + BE-306). No code under `apps/`. Verified by `git diff --stat origin/main` showing exactly 2 files.
- **AC-2 (endpoint surface)**: the new `## PATCH /api/admin/operators/{operatorId}/profile` section in `admin-api.md` includes all of: purpose, Auth + Required permission, Headers, Path Variable, Request body shape with table, Response (`204 No Content`), Errors table (all 8 codes above), Side Effects.
- **AC-3 (placement)**: the new section is placed **immediately after** `## PATCH /api/admin/operators/{operatorId}/status` and **before** `## PATCH /api/admin/operators/me/password` (admin {operatorId}/* family stays grouped; me/* family stays grouped; sibling structural neighbor).
- **AC-4 (no scope creep)**: zero edits to `data-model.md`, `architecture.md`, `rbac.md`, `security.md`, `console-registry-api.md`, or any `projects/platform-console/**` file in this spec PR. Verified by `git diff --stat origin/main -- projects/global-account-platform/specs/services/ projects/platform-console/` returning 0 changed files.
- **AC-5 (impl PR — sequential)**: a follow-up impl PR (held separately, **not part of this spec PR**) lands the use case + controller + IT + unit. Same spec-first pattern as BE-306.
- **AC-6 (zero-retrofit producer invariant — verified at impl PR)**: 0 byte diff across `projects/{wms,scm,finance,erp,fan,ecommerce}-platform/` in either spec PR or impl PR (this is a GAP-only change).
- **AC-7 (D4 HARD INVARIANT preserved — verified at impl PR)**: 0 byte diff across `projects/platform-console/apps/console-bff/src/**` in the impl PR. ADR-MONO-017 § D4 (per-domain credential sealed switch) is GAP-external and is not touched here.
- **AC-8 (BE-303 3-dim verified at close chore)**: per `CLAUDE.md § Task Rules`, the close chore PR is opened **only after** the impl PR satisfies all three: (a) `gh pr view <impl-PR> --json state,mergedAt,mergeCommit,statusCheckRollup` returns `state=MERGED` AND `statusCheckRollup` shows 0 failing required checks; (b) `git log origin/main` tip matches the squash commit; (c) `gh pr checks <impl-PR>` pre-merge snapshot had 0 failing required checks.
- **AC-9 (BE-299 done re-stage check at close chore)**: per CLAUDE.md, `git mv review/ → done/` stages a Status=`review` blob; the close chore must edit the file's Status line to `done` AND `git add <done-path>` AND verify with `git show :<done-path>` that the staged blob reads `Status: done` (not `review`).

# Related Specs

- `projects/global-account-platform/specs/contracts/http/admin-api.md` — extended in this task (new endpoint section). Authoritative producer contract.
- `projects/global-account-platform/specs/contracts/http/admin-api.md § PATCH /api/admin/operators/me/profile` — **byte-unchanged**. Self-serve sibling (BE-306 producer). This task's admin path is its cross-operator counterpart.
- `projects/global-account-platform/specs/contracts/http/admin-api.md § PATCH /api/admin/operators/{operatorId}/status` — **byte-unchanged**. Sibling admin {operatorId}/* endpoint; the SELF_SUSPEND_FORBIDDEN precedent for self-via-admin-path rejection.
- `projects/global-account-platform/specs/services/admin-service/data-model.md` — **byte-unchanged**. Column + action_code already exist from BE-304 + BE-306.
- `projects/global-account-platform/specs/services/admin-service/architecture.md` / `rbac.md` / `security.md` — **byte-unchanged**.
- `projects/platform-console/specs/contracts/console-integration-contract.md` — **NOT modified in this task** — the consumer-side row (§ 2.4.3 ops table row 7 + per-endpoint header matrix row 7 + § 3.1 parity matrix row 18) is added in TASK-PC-FE-017's spec PR.

# Related Contracts

- `projects/global-account-platform/specs/contracts/http/admin-api.md` — the producer contract being extended.

# Edge Cases

- **`defaultAccountId` is null (explicit clear)** → column set to NULL; audit row inserted; reason captured (caller-typed). Next registry GET (for the target operator) omits `operatorContext`.
- **`defaultAccountId` is an empty string** → `400 INVALID_REQUEST` (same as BE-306 — clear intent requires explicit null).
- **`defaultAccountId` over 36 chars / whitespace / control chars** → `400 INVALID_REQUEST` (same DTO validation as BE-306).
- **Request body missing `operatorContext` carrier** → `400 INVALID_REQUEST` (same DTO validation as BE-306 — empty body / empty carrier rejected).
- **`X-Operator-Reason` header missing or empty** → `400 REASON_REQUIRED` (existing `ReasonRequiredException` mapping; mirror `/roles` + `/status`).
- **Caller lacks `operator.manage` permission** → `403 PERMISSION_DENIED` (existing `RequiresPermissionAspect` rejection).
- **Caller not authenticated (no JWT or invalid)** → `401 TOKEN_INVALID` (`OperatorAuthenticationFilter` rejects before controller).
- **Target operator does not exist (operatorId malformed or deleted)** → `404 OPERATOR_NOT_FOUND`.
- **Target operator in different tenant (caller is non-platform-scope)** → `403 TENANT_SCOPE_DENIED` (existing `TenantScopeDeniedException` mapping).
- **Self via admin path (caller hits `/api/admin/operators/{caller.operator_id}/profile`)** → `400 SELF_PROFILE_UPDATE_FORBIDDEN_VIA_ADMIN_PATH`. The use case detects this BEFORE the tenant check or column write (cheapest check first). Audit row is **NOT** written for this rejection (mirror SELF_SUSPEND_FORBIDDEN behavior — pre-permission-evaluation rejection; though the producer may optionally record a DENIED audit row if the existing `OperatorAdminController` aspect does so for similar `RequiresPermission` rejections; chosen at impl based on existing precedent).
- **Optimistic-lock conflict on `admin_operators.version` (caller racing another admin)** → `409 OPTIMISTIC_LOCK_CONFLICT`; client retries.
- **Caller is platform-scope (`tenant='*'`) operating on a non-platform target** → permitted (sentinel authority — same as `/roles` + `/status`).
- **Caller has `operator.manage` but is not SUPER_ADMIN** (theoretically possible if RBAC role assignments deviate) → permitted (permission-based check is authoritative; role is informational). RBAC granting `operator.manage` only to SUPER_ADMIN is enforced in `rbac.md` seed; this endpoint trusts the producer-side seed.
- **Target operator's row deleted between use-case lookup and save (race)** → `OperatorNotFoundException` propagates from `AdminOperatorPort.changeFinanceDefaultAccountId`; controller returns 404. No audit row.

# Failure Scenarios

- **Use case writes the column UPDATE but skips the audit row** → IT case (1) audit-row assertion fails. Fix: single `@Transactional` ordering (UPDATE → INSERT audit row).
- **Use case writes the audit row with `target_id = caller.operator_id` instead of `target.operator_id`** → IT case (1) audit-row assertion catches (asserts `target_id != caller`). Fix: pass `target.operator_id` explicitly to the audit writer.
- **Self-check applied AFTER tenant check (wrong order)** → if a SUPER_ADMIN of tenant X hits `/profile/{their-own-id}` and their tenant evaluation returns first, an inconsistent error code surfaces (TENANT_SCOPE_DENIED on self instead of SELF_PROFILE_UPDATE_FORBIDDEN). Reject in review — self-check must come first.
- **`action_code` differentiation from BE-306 self-serve via a new enum value (e.g. `ADMIN_OPERATOR_PROFILE_UPDATE`)** → reject in review (see § Decision authority). Differentiation is via `(operator_id, target_id)` tuple; new enum value would create querying complexity for downstream observers.
- **A reviewer suggests adding `Idempotency-Key`** → reject. Same reasoning as BE-306 + sibling `/roles` + `/status` precedent.
- **A reviewer suggests allowing self via admin path "for completeness"** → reject. See § Decision authority "Why admin path forbids self". The SELF_SUSPEND_FORBIDDEN precedent is the established pattern.
- **Audit row's `detail` field contains the new defaultAccountId value** → BE-306 § Failure Scenarios already rejects this; reject here too.
- **Cross-tenant escalation via accidentally permissive query (e.g. lookup uses `operator_id` without tenant filter)** → IT case (3) catches by asserting 403 + no column change. Fix: existing `AdminOperatorPort.findByPublicId` MUST not widen scope; tenant check happens in the use case after lookup. If the port lookup returns a target outside scope, the use case throws `TenantScopeDeniedException` before the save.

# Verification

1. Spec PR diff: `git diff --stat origin/main` shows exactly **2** changed files — this task file + `admin-api.md`.
2. The new `## PATCH /api/admin/operators/{operatorId}/profile` section appears in `admin-api.md` immediately after `## PATCH /api/admin/operators/{operatorId}/status` and before `## PATCH /api/admin/operators/me/password`.
3. Impl PR (separate, after spec PR merges): `./gradlew :admin-service:test` (unit + slice) green; `./gradlew :admin-service:integrationTest` (IT) green including the 5 new `OperatorProfileAdminIntegrationTest` cases + audit-row assertions.
4. Impl PR `Self CI` 20/20 GREEN at merge time (`gh pr checks <n>` pre-merge snapshot); BE-303 3-dim verified at close chore start.
5. `git log origin/main` tip after impl-PR merge = the squash commit hash returned by `gh pr view <n> --json mergeCommit`.

분석=Opus 4.7 / 구현 권장=Opus 4.7 (cross-stratum: new admin-on-behalf-of use case + cross-tenant guard + self-via-admin guard + reason header binding + audit-row caller/target/reason mapping + permission aspect integration + 5 IT cases including cross-tenant + self-via-admin-path negatives — multiple integration seams; the cross-tenant + self-rejection guards are net-new judgement vs BE-306; deserves Opus) / 리뷰=Opus 4.7 (dispatcher 독립 재검증 — AC-1/AC-3 placement / AC-4 no scope creep grep / AC-6/AC-7 zero-retrofit grep / cross-tenant guard order / audit-row target_id vs operator_id distinction / BE-303 3-dim at close chore / BE-299 done re-stage).
