# Task ID

TASK-BE-306

# Title

GAP admin-service self-serve operator profile mutation endpoint (`PATCH /api/admin/operators/me/profile`) — Phase 1 write-path counterpart of TASK-BE-304 read-path; activates self-serve `finance_default_account_id` setter so the BE-304 column has a UI-reachable provisioning path

# Status

done

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

- **depends on**: TASK-BE-304 (DONE — adds `admin_operators.finance_default_account_id VARCHAR(36) NULL` column via V0029, plus `operatorContext.defaultAccountId` registry-surface read). This task is the **write-path sister**: the column already exists; this task adds the mutation surface that lets an operator set / clear their own value through the platform-console UI rather than via DBA `UPDATE` or seed migration.
- **origin**: BE-304 § Out of Scope explicitly defers the setter: *"A setter / admin UI / admin-api mutation endpoint to write `finance_default_account_id` — out of scope. V0028 lands the column NULL by default; an operator profile mutation surface (POST/PATCH on `admin_operators` row) is a future task (admin-api or operator-management feature)."* + TASK-PC-FE-014 § Out of Scope mirror: *"a mutation endpoint or admin UI to set `finance_default_account_id` is out of scope here (TASK-BE-304 ships the column with NULL default; provisioning is via direct seed/DBA today)"*. This task closes that deferred follow-up.
- **prerequisite for**: TASK-PC-BE-004 (console-bff `PATCH /api/console/operators/me/profile` orchestrator endpoint that forwards to this GAP endpoint) and TASK-PC-FE-016 (console-web operators UI input + save handler). Sequential — atomic-cross-project rejected for the same reason as the BE-304 → PC-FE-014 chain (independent CI, independent tests, deferred consumer until producer surface is live).
- **spec-first**: spec PR (this file + `admin-api.md` new endpoint section) → impl PR (use case + controller + IT + unit) → close chore PR (this file `ready → done` + INDEX move).
- **no ADR** (HARDSTOP-09 not triggered): this task adds a single new endpoint to an existing self-serve sibling family (`PATCH /api/admin/operators/me/password` is the established precedent — same authentication boundary, same `X-Operator-Reason` exception, same `204 No Content` shape). No new architectural decision: the architectural decision *"GAP `admin_operators` is the per-operator profile authority"* is recorded in TASK-BE-304 § Decision authority and persists; this task is the missing write half of the same surface.

---

# Goal

TASK-BE-304 lands the `admin_operators.finance_default_account_id` column (V0029) and the read-path emission (`operatorContext.defaultAccountId` on the finance registry item) so platform-console can route to a finance card without `MISSING_PREREQUISITE`. TASK-PC-FE-014 wires the consumer side. **But there is no write path** — today the column can be set only by direct SQL or by Flyway seed migration, which means an operator cannot self-provision their own default finance account through the platform-console UI. The read-path is dead-functional for any operator whose row was not seeded.

Add the missing self-serve write path:

```
PATCH /api/admin/operators/me/profile
Content-Type: application/json
Authorization: Bearer <operator-token>

{
  "operatorContext": {
    "defaultAccountId": "acc-uuid-7"   // or null to clear
  }
}

→ 204 No Content
```

The endpoint updates `admin_operators.finance_default_account_id` of the **calling operator's own row** (`JWT.sub` → operator lookup; no `{operatorId}` path parameter — self-serve only, mirrors `PATCH /api/admin/operators/me/password` exactly). After 204, a subsequent `GET /api/admin/console/registry` reflects the new `operatorContext.defaultAccountId` on the finance product item (or omits it when cleared). An `admin_actions` audit row is appended with `action_code = "OPERATOR_PROFILE_UPDATE"`, `target_type = "OPERATOR"`, `target_id = <self operator_id>`, `outcome = SUCCESS` (the established self-action audit pattern for mutations — same row policy that applies to `me/password` per `admin-api.md § Authentication > X-Operator-Reason in Exceptions sub-tree`: self-flow reason is the constant `"<self_profile_update>"`).

**This task is producer-only**; the platform-console consumer adoption (BFF orchestrator + UI) is the sequential follow-up.

# Decision authority (why self-serve only, why nested `operatorContext` request shape, why `me/profile` not `me/finance-account`, why audit-row)

- **Why self-serve only (no admin-on-behalf-of)**: v1 lands the simplest vertical slice — an operator sets their own default account. SUPER_ADMIN-acting-on-behalf-of is a strictly larger surface (cross-tenant permission check, target_id != self, no `me/` shortcut, X-Operator-Reason required per the standard admin mutation rule, broader audit row policy) and a future task can layer it on top of this one. The `me/password` precedent (also self-serve-only — there is no `PATCH /api/admin/operators/{operatorId}/password` admin reset endpoint in v1) establishes that self-serve-first is the accepted GAP pattern.
- **Why request body `{ "operatorContext": { "defaultAccountId": ... } }`, not a top-level `{ "defaultAccountId": ... }`**: write contract **mirrors the read contract verbatim** (TASK-BE-304 § Decision authority "Why `operatorContext` nested, not a top-level `defaultAccountId`"). The same extensibility argument applies symmetrically: a future write of `operatorContext.wmsDefaultWarehouseId` reuses the same `operatorContext` carrier with no shape break, and a single round-trip `read → mutate → re-read` (UI flow) keeps the same JSON path on both legs. Top-level `defaultAccountId` would force a write/read mismatch — the UI sends one shape, reads back a different shape — and a polymorphic top-level surface would explode as soon as a second product attribute lands.
- **Why endpoint name `me/profile`, not `me/finance-account` or `me/default-account`**: the resource is the **operator's profile attribute carrier**, of which `operatorContext.defaultAccountId` is the v1 single field. A per-attribute endpoint name (`me/finance-account`) would force a new endpoint for every future attribute (`me/wms-warehouse`, `me/scm-node`), an explosion that defeats the extensible-carrier design. `me/profile` is the stable URL: the request body's `operatorContext` carrier indicates which attribute is being mutated, and PATCH semantics naturally support partial updates (a future request with `{ "operatorContext": { "wmsDefaultWarehouseId": "..." } }` does not touch the finance field). The endpoint is **also** the natural future home for non-`operatorContext` profile attributes (display preferences, locale, etc.) without further naming churn.
- **Why `204 No Content`, not 200 with echo body**: the consumer (console-web) re-fetches the registry after a successful write to update the visible finance-card state (registry is the read-side authority — the same source the overview composition reads). Echoing the operator profile from PATCH would create two paths to the same shape, with inevitable subtle drift; `204` keeps the read authority single-rooted at the registry (consistent with the `me/password` precedent which is also `204` — the new password is never echoed). The consumer-side cost is one additional registry round-trip, which is acceptable.
- **Why audit row is required (and `X-Operator-Reason` is not)**: every mutation against `admin_operators` (or any admin table) is recorded in `admin_actions` per [data-model.md § admin_actions Immutability](../../specs/services/admin-service/data-model.md). Self-action mutations follow the `me/password` precedent: the audit row is written with `reason = "<self_profile_update>"` (parallel to `"<self_enrollment>"` for 2FA enroll/verify), and the request does **not** require the standard `X-Operator-Reason` header (it is a self-flow exception per [admin-api.md § Authentication > X-Operator-Reason in Exceptions sub-tree](../../specs/contracts/http/admin-api.md)). Skipping the audit row entirely would break the "every `admin_operators` mutation has an audit trail" invariant and would make a stale `finance_default_account_id` value un-investigable.
- **Why `validation = opaque on producer`**: TASK-BE-304 § Decision authority already records *"GAP does not verify the id exists in finance"* — the producer-side `VARCHAR(36)` is opaque, and a stale id surfaces honestly at finance-leg call time (`404 ACCOUNT_NOT_FOUND`). The same applies symmetrically on write: the PATCH endpoint accepts the value as opaque (only `StringUtils.hasText` + length-1-36 + `IsControlCharacter`-clean), and does **not** call finance-platform to verify the id. This preserves the GAP↔finance non-coupling invariant on both legs of the surface.

---

# Scope

## In Scope

**Specs (spec PR)**:

- `projects/global-account-platform/specs/contracts/http/admin-api.md`:
  - Add a new section `## PATCH /api/admin/operators/me/profile` placed **immediately after** the existing `## PATCH /api/admin/operators/me/password` section (sibling self-serve placement; alphabetical and structural neighbor).
  - Body covers: purpose, Auth (operator JWT only — no permission, no `X-Operator-Reason`), Request shape (`operatorContext.defaultAccountId: string | null`), Response (`204 No Content`), Errors (`400 INVALID_REQUEST` for empty/whitespace/over-length, `401 TOKEN_INVALID`, `409 OPTIMISTIC_LOCK_CONFLICT` if `admin_operators.version` race — already covered by the existing `version` column on the row).
  - Add a cross-reference at the existing `### X-Operator-Reason in Exceptions sub-tree` table noting that `PATCH /api/admin/operators/me/profile` follows the same self-flow exemption as `me/password` (audit row written with `reason = "<self_profile_update>"`).
- This task file itself.

**Code (impl PR — out of scope here, listed for the dispatch agent to know the shape)**:

- `projects/global-account-platform/apps/admin-service/src/main/java/com/example/admin/application/profile/UpdateOwnOperatorProfileUseCase.java` (new) — single use case accepting `(callerOperatorId: AdminOperatorId, defaultAccountId: String | null)`.
- `projects/global-account-platform/apps/admin-service/src/main/java/com/example/admin/domain/rbac/AdminOperator.java` — add a `withFinanceDefaultAccountId(String)` factory method that returns a new record instance with the field updated (record is immutable; factory wraps the canonical 7-arg constructor).
- `projects/global-account-platform/apps/admin-service/src/main/java/com/example/admin/infrastructure/persistence/rbac/AdminOperatorJpaRepository.java` — already has `save(AdminOperatorEntity)` from BE-249; reuse. Optimistic-lock conflict surfaces as Spring `ObjectOptimisticLockingFailureException` → map to `409 OPTIMISTIC_LOCK_CONFLICT`.
- `projects/global-account-platform/apps/admin-service/src/main/java/com/example/admin/presentation/console/OperatorProfileController.java` (new, or extend an existing me-controller if a natural one exists — chosen at impl) — `@PatchMapping("/api/admin/operators/me/profile")` + DTO + validation + `204 No Content`.
- Audit-row write: use the existing `AdminActionWriter` (or equivalent) port. `action_code = "OPERATOR_PROFILE_UPDATE"`, `operator_id = self`, `target_type = "OPERATOR"`, `target_id = self.operator_id`, `permission_used = "<self_action>"`, `outcome = SUCCESS`, `detail = null` (no PII; the new value itself is `internal`-class but is intentionally NOT logged into `detail` — the registry-side echo on the next GET is the audit evidence, not the row body).
- Tests:
  - **Unit (`UpdateOwnOperatorProfileUseCaseTest`)**: 4 cases — (a) set to UUID string → `admin_operators.finance_default_account_id` updated, audit row inserted; (b) set to `null` → column cleared; (c) empty string / whitespace-only → `INVALID_REQUEST` thrown (column unchanged); (d) operator not found / soft-deleted between JWT verify and use case → propagates as `OperatorUnauthorizedException` (mapped to 401 — same as me/password).
  - **Slice/Controller (`OperatorProfileControllerSliceTest`)**: 3 cases — (a) valid PATCH → 204, no response body; (b) invalid body (`defaultAccountId` is whitespace) → 400 with code `INVALID_REQUEST`; (c) missing token → 401 `TOKEN_INVALID` (the `OperatorAuthenticationFilter` rejects before the controller is reached).
  - **IT (`OperatorProfileIntegrationTest`)**: 4 cases —
    - (1) PATCH `{"operatorContext":{"defaultAccountId":"acc-uuid-7"}}` → 204; subsequent `GET /api/admin/console/registry` returns finance product item with `"operatorContext":{"defaultAccountId":"acc-uuid-7"}` and exactly **one** occurrence of `operatorContext` substring in the full envelope (no cross-product leakage; same regression guard as TASK-BE-304 AC-3).
    - (2) PATCH `{"operatorContext":{"defaultAccountId":null}}` after (1) → 204; subsequent `GET /api/admin/console/registry` does **not** contain the substring `operatorContext` anywhere in the envelope.
    - (3) PATCH without `Authorization` → 401 `TOKEN_INVALID`.
    - (4) PATCH with whitespace-only `defaultAccountId` → 400 `INVALID_REQUEST`; the column is unchanged (assert by following GET shows the prior value still present).
  - **Audit row IT assertion** (folded into IT case 1): after the successful PATCH, `admin_actions` table contains exactly **one** new row with `action_code = "OPERATOR_PROFILE_UPDATE"`, `operator_id = <self>`, `target_id = <self.operator_id>`, `outcome = "SUCCESS"`, `detail IS NULL` (no PII leakage into the audit `detail` column).

## Out of Scope

- **Admin-on-behalf-of mutation (`PATCH /api/admin/operators/{operatorId}/profile`, SUPER_ADMIN-only)** — explicitly future task. v1 self-serve only.
- **Console-bff orchestrator endpoint (`PATCH /api/console/operators/me/profile`)** — separate task `TASK-PC-BE-004`. This Phase 1 task lands the producer surface and the column-mutation path; the console-bff write proxy is the sequential follow-up (mirror of BE-304 → PC-FE-014 read-path sequence).
- **Console-web UI input + save handler** — separate task `TASK-PC-FE-016` after the console-bff orchestrator merges.
- **`operatorContext` write extensibility to non-finance attributes** — the request schema reserves the `operatorContext` carrier for future per-product attributes, but v1 accepts **only** `operatorContext.defaultAccountId`. A request body with unknown keys under `operatorContext` (e.g. `{ "operatorContext": { "wmsDefaultWarehouseId": "..." } }`) → 400 `INVALID_REQUEST` (Jackson `FAIL_ON_UNKNOWN_PROPERTIES` on the request DTO; permissive read on the response side stays unchanged).
- **Validation against finance-platform** — see § Decision authority "Why `validation = opaque on producer`". A stale id surfaces honestly at finance-leg call time. No cross-service round-trip on the GAP write path.
- **Idempotency-Key header** — PATCH is naturally idempotent for the same body (the same final state regardless of repetition); explicit `Idempotency-Key` would be over-engineered for a single-field self-serve setter. If a future audit-row-deduplication requirement arises, a separate task adds it (mirror of BE-051 `Idempotency-Key` for outbound shipment commands).
- **CSRF token** — `/api/admin/**` is operator JWT bearer, not session-cookie; CSRF is not in the admin-service threat model (same as `me/password`).
- **ADR amendment** — none. ADR-MONO-017 D4 HARD INVARIANT (per-domain credential rule) is unaffected — this endpoint is GAP-internal, not a console-bff outbound. ADR-MONO-013 / 014 / 015 / 016 / D6 — none touched.

# Acceptance Criteria

- **AC-1 (spec PR atomic)**: spec PR lands **exactly two files** modified — this task file (`tasks/ready/TASK-BE-306-...`) and `specs/contracts/http/admin-api.md`. No `data-model.md` edit (the column already exists from V0029; no schema change; the `admin_actions` row uses an existing `action_code` enum extension which is application-level, not DDL). No code under `apps/`. Verified by `git diff --stat origin/main` showing exactly 2 files.
- **AC-2 (endpoint surface)**: the new `## PATCH /api/admin/operators/me/profile` section in `admin-api.md` includes all of: purpose, Auth (operator JWT only, no permission), Request body shape with table, Response (`204 No Content`), Errors table (`400 INVALID_REQUEST` / `401 TOKEN_INVALID` / `409 OPTIMISTIC_LOCK_CONFLICT`), and a cross-reference into the existing `### X-Operator-Reason in Exceptions sub-tree` table.
- **AC-3 (no scope creep)**: zero edits to `data-model.md`, `architecture.md`, `rbac.md`, `security.md`, `console-registry-api.md`, or any `projects/platform-console/**` file in this spec PR. Verified by `git diff --stat origin/main -- projects/global-account-platform/specs/services/ projects/platform-console/` returning 0 changed files.
- **AC-4 (impl PR — sequential)**: a follow-up impl PR (held separately, **not part of this spec PR**) lands the use case + controller + IT + unit. Same spec-first pattern as TASK-BE-304.
- **AC-5 (zero-retrofit producer invariant — verified at impl PR)**: 0 byte diff across `projects/{wms,scm,finance,erp,fan,ecommerce}-platform/` in either spec PR or impl PR (this is a GAP-only change). ADR-MONO-013 § 3.3 invariant preserved.
- **AC-6 (D4 HARD INVARIANT preserved — verified at impl PR)**: 0 byte diff across `projects/platform-console/apps/console-bff/src/**` in the impl PR. ADR-MONO-017 § D4 (per-domain credential sealed switch) is GAP-external and is not touched here.
- **AC-7 (BE-303 3-dim verified at close chore)**: per [`CLAUDE.md § Task Rules`](../../../../CLAUDE.md), the close chore PR is opened **only after** the impl PR satisfies all three: (a) `gh pr view <impl-PR> --json state,mergedAt,mergeCommit,statusCheckRollup` returns `state=MERGED` AND `statusCheckRollup` shows 0 failing required checks; (b) `git log origin/main` tip matches the squash commit; (c) `gh pr checks <impl-PR>` pre-merge snapshot had 0 failing required checks. A close chore opened against a CI-RED impl PR is a defect (TASK-PC-FE-011 PR #672 precedent).
- **AC-8 (BE-299 done re-stage check at close chore)**: per CLAUDE.md, `git mv review/ → done/` stages a Status=`review` blob; the close chore must edit the file's Status line to `done` AND `git add <done-path>` AND verify with `git show :<done-path>` that the staged blob reads `Status: done` (not `review`). A done/ file with Status: `review` is a defect (TASK-BE-299 precedent).

# Related Specs

- `projects/global-account-platform/specs/contracts/http/admin-api.md` — extended in this task (new endpoint section + X-Operator-Reason exceptions table cross-reference).
- `projects/global-account-platform/specs/services/admin-service/data-model.md` § admin_operators — column already exists (TASK-BE-304, V0029); **byte-unchanged** in this task.
- `projects/global-account-platform/specs/services/admin-service/data-model.md` § admin_actions — schema unchanged; this task adds a new `action_code` value `"OPERATOR_PROFILE_UPDATE"` which is application-level enum extension (admin_actions `action_code` is VARCHAR(40) + app-enforced enum per the existing pattern; same approach as BE-027 `DENIED` outcome extension).
- `projects/global-account-platform/specs/services/admin-service/architecture.md` — Identity / Service Type Composition unchanged; this task adds no new layer (use case + controller within existing Hexagonal layout).
- `projects/global-account-platform/specs/contracts/http/console-registry-api.md` — **byte-unchanged**. The read-side `operatorContext.defaultAccountId` emission rule (TASK-BE-304) naturally reflects the new write via the same column.

# Related Contracts

- `projects/global-account-platform/specs/contracts/http/admin-api.md` — the producer contract being extended.
- `projects/platform-console/specs/contracts/console-integration-contract.md` — **NOT modified in this task** — the consumer-side write-path note (and §-2.4.9.X "Phase 3 — self-serve provisioning") is added in TASK-PC-BE-004's spec PR. This Phase 1 keeps the producer contract authoritative and lets the consumer contract trail by one PR (deliberate decoupling — same pattern as TASK-BE-304).

# Edge Cases

- **`defaultAccountId` is `null` (explicit clear)** → column set to NULL; audit row written with `action_code = "OPERATOR_PROFILE_UPDATE"`, `detail = null` (the new state — cleared — is the audit evidence). Next registry GET omits `operatorContext`.
- **`defaultAccountId` is an empty string** → treated identically to whitespace-only — `400 INVALID_REQUEST`. To clear, the client must send `null` explicitly. This avoids the ambiguity of "is empty string a clear-intent or a typo".
- **`defaultAccountId` is over 36 characters** → `400 INVALID_REQUEST` (the column is `VARCHAR(36)`; the validation is producer-side before persistence).
- **`defaultAccountId` contains control characters or whitespace inside (e.g. `"acc 1"`)** → `400 INVALID_REQUEST` (the value is treated as an opaque identifier, but it must be a single non-control-character token).
- **Request body missing `operatorContext` key entirely (`{}`)** → `400 INVALID_REQUEST`. PATCH against this endpoint requires the carrier; a body-shape mismatch is not silently a no-op.
- **Request body has `operatorContext` present but empty (`{"operatorContext":{}}`)** → `400 INVALID_REQUEST`. Same reasoning: empty carrier is shape-mismatch, not intent.
- **Request body has unknown keys under `operatorContext` (e.g. `{"operatorContext":{"wmsDefaultWarehouseId":"x"}}`)** → `400 INVALID_REQUEST` (Jackson `FAIL_ON_UNKNOWN_PROPERTIES` on the request DTO). v1 accepts only `defaultAccountId`.
- **Optimistic-lock conflict on `admin_operators.version`** → `409 OPTIMISTIC_LOCK_CONFLICT`. Practical occurrence: two browser tabs of the same operator racing each other. The client retries (the column race is naturally idempotent for the same target value; for different target values, last-writer-wins after the client refreshes).
- **Operator soft-deleted between JWT verify and use case execution** → `401 TOKEN_INVALID` (same as me/password — the JWT is rejected because the operator row is no longer ACTIVE).
- **JWT with `tenant_id = '*'` (platform-scope operator)** → permitted; the column is per-operator-row, not per-tenant. Platform-scope operators can still self-provision their own default finance account.
- **The new audit row INSERT fails (DB transient)** → use case throws → the row UPDATE on `admin_operators` is **rolled back** (single transaction wraps both writes — same `@Transactional` boundary as the existing tenant lifecycle commands). The client sees `500` (or whatever the transient surface is); the column is **not** mutated without an audit trail. This is the canonical audit-heavy A3 invariant.

# Failure Scenarios

- **Use case writes the audit row but skips the column UPDATE** → AC-2 IT case (1) catches it (the post-PATCH GET sees no value change; the IT fails). Fix: the use case must order writes as `UPDATE column → INSERT audit row`, both in the same transaction; an audit row with no observable state change is a leak.
- **Use case writes the column UPDATE but skips the audit row** → AC-2 audit-row IT assertion catches it (no `admin_actions` row inserted after PATCH). Fix: same single-transaction policy.
- **The audit row's `detail` column contains the new `defaultAccountId` value** → BE-249 / R1 pattern violation (PII leakage into `detail` is forbidden; here it is `internal`-class but still NOT logged into `detail` — the audit subject is *that the value changed*, the value itself lives in the operator row). **Reject in review** if `detail` is set to the new value. The detail column stays NULL for this action_code.
- **A reviewer suggests skipping the audit row "because it's self-action"** → reject; the `me/password` precedent writes an audit row with `reason = "<self_enrollment>"` (2FA enroll is structurally identical to a self-mutation), and the `admin_actions` invariant is "every mutation has a row" not "every cross-operator action has a row". **Reject** scope reduction.
- **A reviewer suggests adding `Idempotency-Key` to "harden" the endpoint** → reject; PATCH against a single-field setter is naturally idempotent (the column reaches the same final state on retry). The audit row write is **not** deduplicated, but that is the desired behavior — each user-initiated PATCH is a distinct intent, and a duplicate audit row from a retry is a faithful record of the duplicate intent. **Idempotency-Key** is over-engineered here; if a real requirement surfaces, a follow-up task adds it (mirror of BE-051 for outbound shipment commands).
- **A reviewer suggests cross-service verification of `defaultAccountId` against finance-platform** → reject; this directly violates the GAP↔finance non-coupling invariant recorded in TASK-BE-304 § Decision authority and § 3.3 zero-retrofit. Stale value surfaces honestly via finance `404 ACCOUNT_NOT_FOUND` at finance-leg call time.
- **A reviewer suggests `200 OK` with echo body instead of `204`** → reject. See § Decision authority "Why `204 No Content`, not 200 with echo body". The registry GET is the read authority; PATCH is fire-and-re-read, identical to `me/password`.
- **A reviewer suggests collapsing the request body to top-level `{ "defaultAccountId": ... }`** → reject. See § Decision authority "Why request body `{ operatorContext: { defaultAccountId } }`". The write contract must mirror the read contract.
- **The endpoint accepts unknown nested keys under `operatorContext` and silently no-ops on them** → § Edge Cases "unknown keys under `operatorContext`"; v1 must be `FAIL_ON_UNKNOWN_PROPERTIES = true` so the schema is closed. A silent no-op is a future-compat trap (the operator believes they wrote a value; the read shows no change).

# Verification

1. Spec PR diff: `git diff --stat origin/main` shows exactly **2** changed files — this task file + `admin-api.md`. No `data-model.md`, no `architecture.md`, no `console-registry-api.md`, no `projects/platform-console/**` files.
2. The new `## PATCH /api/admin/operators/me/profile` section appears in `admin-api.md` immediately after `## PATCH /api/admin/operators/me/password`. The existing `### X-Operator-Reason in Exceptions sub-tree` table contains the cross-reference to `me/profile`.
3. Impl PR (separate, after spec PR merges): `./gradlew :admin-service:test` (unit) green; `./gradlew :admin-service:integrationTest` (IT) green including the 4 new `OperatorProfileIntegrationTest` cases + audit-row IT assertion.
4. Impl PR `Self CI` 20/20 GREEN at merge time (`gh pr checks <n>` pre-merge snapshot); BE-303 3-dim verified at close chore start.
5. `git log origin/main` tip after impl-PR merge = the squash commit hash returned by `gh pr view <n> --json mergeCommit`.

분석=Opus 4.7 / 구현 권장=Opus 4.7 (cross-stratum: new HTTP endpoint + DTO validation + single-transaction audit-row write + AdminOperator record factory + optimistic-lock conflict mapping + 4 IT cases including audit-row assertion + zero-retrofit invariant + D4 HARD INVARIANT preservation — multiple integration seams; deserves Opus judgement) / 리뷰=Opus 4.7 (dispatcher 독립 재검증 — AC-1 spec-PR-only / AC-2 endpoint surface completeness / AC-3 no scope creep / AC-5 zero-retrofit grep / AC-6 D4 byte-unchanged grep / AC-7 BE-303 3-dim verify at close chore / AC-8 BE-299 done re-stage verify at close chore).
