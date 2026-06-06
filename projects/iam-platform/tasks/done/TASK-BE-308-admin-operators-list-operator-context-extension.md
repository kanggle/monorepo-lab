# Task ID

TASK-BE-308

# Title

GAP admin-service `GET /api/admin/operators` response per-item `operatorContext` extension — populate each operator's current `finance_default_account_id` so the console admin profile-edit dialog can pre-populate the current value (closes TASK-PC-FE-017 honest gap (c) "no current-value pre-population")

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

- **depends on**: TASK-BE-304 (V0029 `admin_operators.finance_default_account_id` column, **DONE** 2026-05-21 — squash `7e172c50`). The column whose value this task surfaces on the list endpoint.
- **depends on**: TASK-BE-306 (self-serve `PATCH /api/admin/operators/me/profile`, **DONE** 2026-05-21 — squash `22952bfd`). Establishes the carrier shape `operatorContext.defaultAccountId` reused here.
- **depends on**: TASK-BE-307 (admin-on-behalf-of `PATCH /api/admin/operators/{operatorId}/profile`, **DONE** 2026-05-22 — squash `4f43e2c7`). Admin write path that this read path complements.
- **origin**: TASK-PC-FE-017 § Decision authority "Why no current-value pre-population in v1": *"BE-307's producer surface does not expose the target operator's current `finance_default_account_id`... Extending `GET /api/admin/operators` to include `operatorContext` per item is a separate spec-first task; this task does not block on it."* Also TASK-BE-307 § Out of Scope: *"Operators list response extension to include `operatorContext` — that extension to `GET /api/admin/operators` is a separate task (sibling spec extension; trivial — add `operatorContext` to `OperatorSummaryResponse`). Not required for this task."* This task closes that explicitly-deferred follow-up.
- **prerequisite for**: TASK-PC-FE-018 (console-web admin dialog current-value pre-population) — sequential consumer task. Without this list extension, the admin dialog cannot pre-populate the current value, and the v1 UX is forced to open with empty input + "현재 값은 보이지 않습니다" hint (the documented v1 deficit).
- **spec-first**: spec PR (this file + `admin-api.md` § GET /api/admin/operators response shape extension + INDEX) → impl PR (projection + DTO + mapping + bulk-load + IT) → close chore PR.
- **no ADR** (HARDSTOP-09 not triggered): this task adds a single optional response field to an existing read endpoint. No new architectural decision: the column + the carrier shape (`operatorContext.defaultAccountId`) already exist from BE-304 + BE-306. The `@JsonInclude(Include.NON_NULL)` field-level omission discipline is already established by BE-304's registry surface — this task applies the same discipline.

---

# Goal

After BE-304 + BE-306 + BE-307 + PC-FE-014/016/017, an operator can self-provision their `finance_default_account_id` AND a SUPER_ADMIN can provision another operator's value through the platform-console admin UI. **But the admin UI cannot show the target operator's current value** — the producer's `GET /api/admin/operators` list response does not carry it.

Today the console admin dialog (`OperatorProfileEditDialog` from PC-FE-017) opens with an empty input + a "현재 값은 보이지 않습니다 — 입력값이 그대로 저장됩니다" disclaimer. The SUPER_ADMIN cannot see what value is being overwritten — a UX defect explicitly recorded in PC-FE-017 § Decision authority as "out of scope for v1" and deferred to this task.

Add the missing read surface on the list endpoint:

```
GET /api/admin/operators?page=0&size=20
Authorization: Bearer <operator-token>
X-Operator-Reason: <non-empty reason string>

→ 200 OK
{
  "content": [
    {
      "operatorId": "op-1",
      "email": "alice@example.com",
      ...,
      "operatorContext": { "defaultAccountId": "acc-uuid-7" }
    },
    {
      "operatorId": "op-2",
      "email": "bob@example.com",
      ...
      // ← operatorContext key OMITTED entirely when finance_default_account_id IS NULL
    }
  ],
  ...
}
```

Field-level `@JsonInclude(Include.NON_NULL)` discipline (already established by BE-304's registry surface): operators with NULL `finance_default_account_id` get `operatorContext` omitted from their item entirely — not `null`, not `{}`. Operators with a value get `{ "defaultAccountId": "<uuid>" }`.

This is **producer-only**; the console-web admin dialog change to consume the new field is the sequential follow-up TASK-PC-FE-018.

# Decision authority (why list endpoint not a new dedicated `GET /{id}/profile`, why optional/omitted shape, why no PII risk, why no permission tightening)

- **Why extend `GET /api/admin/operators` (NOT a new `GET /api/admin/operators/{operatorId}/profile`)**:
  - **Single round-trip**: the operators screen already fetches the list to render rows. Adding a per-row pre-fetch of a dedicated `/profile` GET would be N+1 (one list call + N profile calls) for a value that's already producer-side cheap to bulk-load. The list-item extension is the minimum surface change.
  - **No incremental authorization complexity**: the list endpoint already requires `operator.manage`. The same permission gates the new field — no new endpoint, no new permission key, no new audit code path.
  - **Sibling precedent**: `GET /api/admin/console/registry` (TASK-BE-304) already exposes `operatorContext` per product item. Extending the operators list with the same shape on each operator item follows the established carrier-symmetry pattern (admin-api.md § "carrier shape 대칭성" note added in this spec PR).
  - **No dedicated profile-read endpoint speculative creation**: BE-307 § Decision authority explicitly says *"a dedicated `GET /api/admin/operators/{operatorId}/profile` endpoint is not needed for the v1 UX, and adding it is a separate task if the consumer needs it (avoid speculative endpoint creation)."* The consumer (PC-FE-018) does NOT need it — the list-item extension fully satisfies the dialog pre-population requirement.
- **Why optional (omitted) when NULL (NOT `null` literal, NOT `{}`)**:
  - **Established discipline**: BE-304 § Decision authority "Why `@JsonInclude(Include.NON_NULL)` field-level" + § Failure Scenarios "Emitting `operatorContext: null` (literal null) for operators with no value" → reject. Field-level `@JsonInclude(Include.NON_NULL)` is the canonical pattern. Operators with NULL column → no `operatorContext` key in JSON.
  - **No new shape to remember**: console-web reads list response items the same way it reads registry response items — `item.operatorContext?.defaultAccountId` works for both, with the optional chaining returning `undefined` when the key is absent.
  - **Test surface**: AC-3 IT asserts `body.content[i]` JSON for a NULL-column operator does NOT contain the substring `operatorContext` (substring count = 0).
- **Why no PII risk in adding the field**:
  - `defaultAccountId` is an opaque finance-platform account UUID (`VARCHAR(36)`, no business semantics inside GAP). Per BE-304 § Decision authority, GAP treats it as an opaque external identifier — no validation, no cross-service lookup. Exposing it to a SUPER_ADMIN who already has `operator.manage` is symmetric to all other column visibility in the same response (email, roles, status — all visible).
  - The endpoint is already gated to `operator.manage` (SUPER_ADMIN only). No new authorization surface.
- **Why no permission tightening (e.g. a separate `operator.read_profile` key)**:
  - `operator.manage` is the producer's coarsest-grained operator-management permission; all existing fields on the response require it. The new field's sensitivity is equivalent to `roles` and `status` — adding a finer-grained read permission for a single optional field would create permission-catalog clutter without a real boundary (any caller authorized to `roles`/`status` can already do an admin-on-behalf-of profile write per BE-307).
- **Why bulk-load (NOT lazy per-item)**:
  - JPA projection on `AdminOperatorJpaEntity` already includes `financeDefaultAccountId` (added by BE-304). The list endpoint's existing query loads the whole entity row; no N+1 problem because the column lives on the same row that's already SELECT-ed. The fix is a single mapping edit in `OperatorAdminController.toResponse(...)` and the `OperatorQueryService.OperatorSummary` projection — no new SQL.

---

# Scope

## In Scope

**Specs (spec PR — this PR)**:

- `projects/global-account-platform/specs/contracts/http/admin-api.md § GET /api/admin/operators`:
  - Extend Response 200 example with `operatorContext: { "defaultAccountId": "acc-uuid-7" }` on the example item.
  - Add a new "Response item shape" table listing every field with its type + conditions (Always / Absent-when-NULL); `operatorContext` row carries the field-level `@JsonInclude(Include.NON_NULL)` discipline note + cross-references TASK-BE-304/306/307/PC-FE-018.
  - Add a "carrier shape 대칭성" callout block: same shape as registry response finance item / me/profile request body / {operatorId}/profile request body.
  - Add an empty Side Effects line ("없음 (read).") for documentation symmetry with sibling endpoints.
- This task file.
- `projects/global-account-platform/tasks/INDEX.md` — ready entry for this task.

**Code (impl PR — out of scope here, listed for the dispatch agent to know the shape)**:

- `projects/global-account-platform/apps/admin-service/src/main/java/com/example/admin/application/port/AdminOperatorPort.java` — review `OperatorView` (or whichever DTO carries list-item-projection) → add `financeDefaultAccountId` (if not already carried; BE-306 added the column to the entity, but the list-side projection may not have surfaced it). Use existing record / immutable shape; no new copy / setter.
- `projects/global-account-platform/apps/admin-service/src/main/java/com/example/admin/application/OperatorQueryService.java` (or equivalent — locate via grep on `getOperators` + `listOperators` + `OperatorSummary`):
  - Extend the projection / mapping that the list endpoint emits to carry `financeDefaultAccountId`. The existing query already SELECTs the whole `admin_operators` row (entity-bound); no SQL change.
- `projects/global-account-platform/apps/admin-service/src/main/java/com/example/admin/presentation/OperatorAdminController.java`:
  - In `toResponse(...)` (or whichever method maps the projection to `OperatorSummaryResponse`), add the mapping: `view.financeDefaultAccountId() != null ? new OperatorContextResponse(view.financeDefaultAccountId()) : null`.
  - Note: `OperatorContextResponse` already exists or is a near-duplicate of `ProductOperatorContextResponse` (BE-304). Decision at impl: reuse if shape-identical OR add a new tiny record. Prefer reuse — rename if necessary to make the cross-domain shape clear (e.g. `OperatorContextResponse` for both registry items AND operators list items — they ARE the same shape).
- `projects/global-account-platform/apps/admin-service/src/main/java/com/example/admin/presentation/dto/OperatorSummaryResponse.java`:
  - Add `OperatorContextResponse operatorContext` field with **field-level** `@JsonInclude(JsonInclude.Include.NON_NULL)` annotation (BE-304 § Decision authority "Why field-level"; Spring Boot 3.4 Jackson 기본 config honors field-level annotation on record components — direct precedent: BE-304 AC-2 IT PASS proves it).
- **Tests** (impl PR — out of scope here, listed for shape):
  - **Unit (`OperatorAdminControllerTest` or `OperatorQueryServiceTest`)**: 3 new cases —
    - (a) operator with non-null `financeDefaultAccountId` → response item carries `operatorContext.defaultAccountId = <value>`.
    - (b) operator with NULL `financeDefaultAccountId` → response item has `operatorContext = null` (Java-side; serialization omits via `@JsonInclude.NON_NULL`).
    - (c) operator with whitespace-only value (corrupted DB state, defensive) → response item ALSO emits null (matching BE-304's whitespace-treats-as-absent discipline; reuse BE-304 helper if available).
  - **IT (`OperatorListIntegrationTest` or equivalent — locate; if absent, add per-test UUID hermetic pattern from BE-306 § cycle 2)**: 2 new cases —
    - (1) seed: one operator with `financeDefaultAccountId = "acc-uuid-X"`, another with NULL → list response items: first carries `"operatorContext":{"defaultAccountId":"acc-uuid-X"}`, second has NO `operatorContext` substring in its item body (use BE-304 AC-3 substring-count assertion pattern).
    - (2) page boundary: 2 pages, mixed NULL/non-NULL across pages — page 0 + page 1 both correctly emit / omit per-item; pagination math unaffected.

## Out of Scope

- **A dedicated `GET /api/admin/operators/{operatorId}/profile` endpoint** — explicitly NOT created. See § Decision authority "Why list endpoint not a new dedicated".
- **Other `operatorContext` carrier attributes** (e.g. `wmsDefaultWarehouseId`, `scmDefaultNodeId`) — same expansion pattern (column + list emission + admin write endpoint) but separate task per attribute. v1 carries `defaultAccountId` only on this endpoint.
- **Permission tightening (e.g. `operator.read_profile`)** — rejected. `operator.manage` is the established gate.
- **N+1 hardening or query plan changes** — none needed; the column is already on the entity row that the existing query SELECTs.
- **Admin operators search by `defaultAccountId`** (e.g. `?defaultAccountId=acc-uuid-X` query parameter) — out of scope. Not requested; speculative; rejected per BE-307 § Decision authority "avoid speculative endpoint creation".
- **Audit row for read of profile field** — out of scope. Read endpoints do not write `admin_actions` rows in admin-service (existing convention). The field is part of an existing read response; no new audit code path.
- **Validation against finance-platform** — opaque value (same as BE-304/306/307). Stale ids surface via finance `404 ACCOUNT_NOT_FOUND` on the eventual BFF call.
- **Console-bff orchestrator** — explicitly NOT applicable. List endpoint is the existing FE-002/003/004 surface; per ADR-MONO-017 D2 Option A + § 2.4.9.1 the operators surface is consumed via per-domain proxy from console-web, not console-bff. This task does not touch console-bff.
- **ADR amendment** — none. ADR-MONO-017 D2/D4 unaffected. ADR-MONO-013 / 014 / 015 / 016 unaffected.

# Acceptance Criteria

- **AC-1 (spec PR atomic)**: spec PR lands **exactly three files** — this task file + `admin-api.md` + `tasks/INDEX.md`. No code under `apps/`. Verified by `git diff --stat origin/main` showing 3 changed files.
- **AC-2 (response shape)**: the new "Response item shape" table in `admin-api.md` lists every field with its type + condition. `operatorContext` row's "조건" column references `@JsonInclude(Include.NON_NULL)` + cross-references TASK-BE-308/PC-FE-018.
- **AC-3 (carrier symmetry note)**: a "carrier shape 대칭성" callout block is added immediately after the response item shape table, naming all 4 surfaces that share the shape (list response + registry response + me/profile request + admin/{id}/profile request).
- **AC-4 (no scope creep)**: zero edits to `data-model.md`, `architecture.md`, `rbac.md`, `security.md`, `console-registry-api.md`, or any `projects/platform-console/**` file in this spec PR. Verified by `git diff --stat origin/main -- projects/global-account-platform/specs/services/ projects/platform-console/` returning 0 changed files.
- **AC-5 (impl PR — sequential)**: a follow-up impl PR (held separately, **not part of this spec PR**) lands the projection + DTO + mapping + tests. Same spec-first pattern as BE-304/306/307.
- **AC-6 (`@JsonInclude(Include.NON_NULL)` at impl)**: at impl PR, `OperatorSummaryResponse` carries the field-level annotation on `operatorContext`; IT asserts the JSON body of a NULL-column operator item does NOT contain the substring `operatorContext`.
- **AC-7 (zero-retrofit producer invariant — verified at impl PR)**: 0 byte diff across `projects/{wms,scm,finance,erp,fan,ecommerce}-platform/` in either spec PR or impl PR (this is a GAP-only change).
- **AC-8 (D4 HARD INVARIANT preserved — verified at impl PR)**: 0 byte diff across `projects/platform-console/apps/console-bff/src/**` in the impl PR. ADR-MONO-017 § D4 (per-domain credential sealed switch) is GAP-external and is not touched here.
- **AC-9 (BE-303 3-dim verified at close chore)**: per `CLAUDE.md § Task Rules`, the close chore PR is opened **only after** the impl PR satisfies all three: (a) `gh pr view <impl-PR> --json state,mergedAt,mergeCommit,statusCheckRollup` returns `state=MERGED` AND `statusCheckRollup` shows 0 failing required checks; (b) `git log origin/main` tip matches the squash commit; (c) `gh pr checks <impl-PR>` pre-merge snapshot had 0 failing required checks.
- **AC-10 (BE-299 done re-stage check at close chore)**: per CLAUDE.md, `git mv review/ → done/` stages a Status=`review` blob; the close chore must edit the file's Status line to `done` AND `git add <done-path>` AND verify with `git show :<done-path>` that the staged blob reads `Status: done` (not `review`).

# Related Specs

- `projects/global-account-platform/specs/contracts/http/admin-api.md § GET /api/admin/operators` — extended in this task (response shape + carrier symmetry note). Authoritative producer contract.
- `projects/global-account-platform/specs/contracts/http/admin-api.md § PATCH /api/admin/operators/me/profile` (BE-306 producer) — **byte-unchanged**. Carrier shape source.
- `projects/global-account-platform/specs/contracts/http/admin-api.md § PATCH /api/admin/operators/{operatorId}/profile` (BE-307 producer) — **byte-unchanged**. Admin write counterpart.
- `projects/global-account-platform/specs/contracts/http/console-registry-api.md` — **byte-unchanged**. Sibling carrier surface (BE-304); cross-referenced.
- `projects/global-account-platform/specs/services/admin-service/data-model.md` — **byte-unchanged**. Column already exists from BE-304 V0029.
- `projects/global-account-platform/specs/services/admin-service/architecture.md` / `rbac.md` / `security.md` — **byte-unchanged**.
- `projects/platform-console/specs/contracts/console-integration-contract.md` — **NOT modified in this task** — the consumer-side note (§ 2.4.3 ops table row 1 list-notes) is added in TASK-PC-FE-018's spec PR.

# Related Contracts

- `projects/global-account-platform/specs/contracts/http/admin-api.md` — the producer contract being extended.

# Edge Cases

- **Operator row with `finance_default_account_id IS NULL`** → response item has NO `operatorContext` key (field-level `@JsonInclude.NON_NULL` omits).
- **Operator row with `finance_default_account_id = ""` (defensive — DB state should reject via DTO validation on write, but the read path should tolerate)** → treat as null/omit. Reuse BE-304's whitespace-treats-as-absent helper if available; otherwise mirror its logic inline.
- **Operator row with whitespace-only `finance_default_account_id`** → same as empty string. Treat as null/omit.
- **Operator row with a value over 36 chars (defensive — write path rejects; read path encounters legacy data)** → still emit the value verbatim (defensive read; producer write-path validation is authoritative for what gets stored; the read endpoint reflects what's there).
- **List response with mixed NULL/non-NULL across pages** → page 0 has 5 with values + 5 NULL; page 1 has 3 with values + 7 NULL. Each item per page independently omits or emits. Pagination math unaffected (no `WHERE financeDefaultAccountId IS NOT NULL` filter; the field is informational, not a filter).
- **Cross-tenant target visibility (caller is non-platform-scope, operator list response includes operators in the caller's tenant only — existing behavior, BE-249)** → unchanged. The new field is exposed for each row that's already in scope; no scope widening.
- **Operator self-row in the list response** (the caller's own operator appears in the list) → `operatorContext.defaultAccountId` is shown for the self-row too (mirrors registry surface — same shape). The consumer UI's self-row disable is for the *write* button, not the *read* display (PC-FE-017 § Edge Cases).

# Failure Scenarios

- **Projection misses the column** (e.g. `OperatorQueryService.OperatorSummary` is hand-built and forgot to copy `financeDefaultAccountId` from the entity) → IT case (1) catches by asserting the JSON body of a non-NULL operator contains `"operatorContext":{"defaultAccountId":"<value>"}`. Reject in review.
- **Field added without `@JsonInclude.NON_NULL`** → IT case (1) catches by asserting the JSON body of a NULL-column operator does NOT contain `operatorContext`. Reject in review.
- **`OperatorContextResponse` collides with `ProductOperatorContextResponse` (BE-304)** → both are the same shape `{ defaultAccountId?: string }`. Reuse OR rename one to a shared `OperatorContextResponse` (which IS the right name across both consumers — the registry's "Product" prefix is the legacy quirk). At impl, choose: rename the BE-304 type to drop the `Product` prefix OR add a sibling record (declaration cost ~5 lines). Either is acceptable; pick whichever minimizes diff.
- **A reviewer suggests filtering the list by `defaultAccountId` (`?defaultAccountId=...` query param)** → reject. See § Out of Scope. Speculative; not requested by PC-FE-018.
- **A reviewer suggests `operator.read_profile` permission tightening** → reject. See § Decision authority "Why no permission tightening".
- **A reviewer suggests emitting `"operatorContext": null` (literal null) instead of omitting the key** → reject. Inconsistent with BE-304 + carrier-symmetry note. The whole point of `@JsonInclude.NON_NULL` is to omit, not emit null.
- **A reviewer suggests adding a new endpoint `GET /api/admin/operators/{operatorId}/profile`** → reject. See § Decision authority. Speculative; the list-item extension fully satisfies the consumer requirement.

# Verification

1. Spec PR diff: `git diff --stat origin/main` shows exactly **3** changed files — this task file + `admin-api.md` + `tasks/INDEX.md`.
2. The new "Response item shape" table appears in `admin-api.md` immediately after the Response 200 JSON example of `GET /api/admin/operators`.
3. The "carrier shape 대칭성" callout block appears immediately after the shape table, naming all 4 surfaces.
4. Impl PR (separate, after spec PR merges): `./gradlew :admin-service:test` (unit + slice) green; `./gradlew :admin-service:integrationTest` (IT) green including the 2 new operator-list cases.
5. Impl PR `Self CI` GREEN at merge time (`gh pr checks <n>` pre-merge snapshot); BE-303 3-dim verified at close chore start.
6. `git log origin/main` tip after impl-PR merge = the squash commit hash returned by `gh pr view <n> --json mergeCommit`.

분석=Opus 4.7 / 구현 권장=Sonnet 4.6 (mechanical projection + DTO field + mapping + IT case; the shape decision is pre-recorded in this spec; no net-new judgement vs BE-304/306/307 — deserves Sonnet) / 리뷰=Opus 4.7 (dispatcher 독립 재검증 — AC-1 file count / AC-2 shape table / AC-3 carrier symmetry / AC-4 no scope creep grep / AC-6 `@JsonInclude.NON_NULL` IT assertion / AC-7+8 zero-retrofit + console-bff grep / BE-303 3-dim at close chore / BE-299 done re-stage).
