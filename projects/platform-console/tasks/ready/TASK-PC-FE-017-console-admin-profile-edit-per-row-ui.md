# Task ID

TASK-PC-FE-017

# Title

platform-console admin profile-edit per-row UI — `PATCH /api/admin/operators/{operatorId}/profile` proxy + reason-gated dialog in `OperatorsScreen`; consumer adoption of TASK-BE-307 admin-on-behalf-of producer

# Status

ready

# Owner

frontend

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

- **depends on**: TASK-BE-307 (GAP admin-service admin-on-behalf-of profile mutation, **DONE** 2026-05-22 — spec PR #710 `6b4e5e15` / impl PR #711 `4f43e2c7` / close chore #712 `b830c17e`). Without BE-307 merged, this task's proxy would call a producer endpoint that returns 404.
- **depends on (indirectly)**: TASK-BE-306 + TASK-PC-FE-016 (self-serve column + endpoint + UI). The admin path is the cross-operator sister of the self-serve UX completed in those tasks.
- **origin**: TASK-BE-307 § Out of Scope: *"Console-web admin UI input + save handler — separate task `TASK-PC-FE-017` after the BE-307 producer merges."* This task closes that deferred follow-up and completes the 4-leg admin vertical slice (BE-307 producer column reuse + BE-307 producer endpoint + this PC-FE-017 admin consumer UI).
- **prerequisite for**: nothing (this completes the Operator Overview finance card MISSING_PREREQUISITE resolution + admin-on-behalf-of sister chains).
- **spec-first**: spec PR (this file + `console-integration-contract.md § 2.4.3` operations table row 7 + per-endpoint header matrix row 7 + § 3.1 parity matrix row 18 + INDEX) → impl PR (proxy + api fn + dialog + OperatorsScreen integration + tests + parity-matrix fixture row + parity-verification count `17 → 18`) → close chore PR.
- **no ADR** (HARDSTOP-09 not triggered): architectural pattern pre-recorded — same per-domain mutation proxy pattern as TASK-PC-FE-016 (ADR-MONO-017 D2 Option A — single-domain mutations stay per-domain, NOT through console-bff) + same reason-capture + elevated-confirm pattern as `{operatorId}/roles` + `{operatorId}/status` (PC-FE-004 established).

---

# Goal

TASK-BE-307 ships `PATCH /api/admin/operators/{operatorId}/profile` so a SUPER_ADMIN can provision another operator's `finance_default_account_id` from the producer side. But there is no consumer UI — today a SUPER_ADMIN can only call the endpoint via curl or DBA shell. Self-serve UX (PC-FE-016) is complete on `me/profile`, but the **admin-on-behalf-of UX is missing** in `OperatorsScreen`.

Activate the admin write path end-to-end:

1. **console-web proxy route** (`/api/operators/[operatorId]/profile/route.ts`) — same shape as `[operatorId]/status/route.ts`: PATCH-only, zod-validated body, calls `setOperatorProfile(operatorId, defaultAccountId, reason)` from `features/operators/api/operators-api.ts`, returns 204 No Content on success. **Direct forward to GAP** `PATCH /api/admin/operators/{operatorId}/profile` via the hardened `callGapOperators(...)` call site (operator JWT bearer + active tenant header + `X-Operator-Reason` from the form + NO `Idempotency-Key` — mirrors `[operatorId]/status` per producer matrix).
2. **`_proxy.ts` `AdminUpdateProfileBodySchema`** — zod-validated request body: `{ defaultAccountId: string | null, reason: string }`; `defaultAccountId` is opaque (BE-307 validates structurally on the producer); `reason` must be non-empty trimmed.
3. **`features/operators/api/operators-api.ts` `setOperatorProfile(operatorId, defaultAccountId, reason)`** — mirror `changeOperatorStatus`: calls `callGapOperators({ method: 'PATCH', path: '${OPERATORS_PREFIX}/${operatorId}/profile', body: { operatorContext: { defaultAccountId } }, reasonHeader: reason, expectNoContent: true })`. Per producer matrix: `X-Operator-Reason` **required**, `Idempotency-Key` **MUST NOT be sent**.
4. **`OperatorProfileEditDialog.tsx`** (new — sibling of `OperatorConfirmDialog`) — `'use client'` component, single text input "기본 finance 계정 ID" (UUID-like string, nullable) + Clear toggle (sets value to `null`) + reason `<textarea>` (required, non-empty) + Save / Cancel buttons. Same reason-capture + confirm pattern as `OperatorConfirmDialog`, but with a per-attribute input slot. Inline server error mapping for `400 INVALID_REQUEST`, `400 SELF_PROFILE_UPDATE_FORBIDDEN_VIA_ADMIN_PATH`, `403 PERMISSION_DENIED`, `403 TENANT_SCOPE_DENIED`, `404 OPERATOR_NOT_FOUND`, `409 OPTIMISTIC_LOCK_CONFLICT`. Pre-populated with the target operator's current `finance_default_account_id` value (initialState passed as prop; comes from the operators list response — see § Edge Cases for the "current value source" decision).
5. **`OperatorsScreen.tsx` integration** — add a "Profile 편집" button to each operator row's action column (alongside existing role/status buttons). Disabled when the row's `operatorId == self.operatorId` (per BE-307 producer-side `SELF_PROFILE_UPDATE_FORBIDDEN_VIA_ADMIN_PATH`; UI mirrors the producer-side rejection — never surface a button that would always 400). Button opens `<OperatorProfileEditDialog />`; `useMutation` for `setOperatorProfile` invokes after confirm; on success, invalidate the operators list query so the next render reflects any value change.
6. **`OperatorSummaryResponse` extension** (OPTIONAL — see § Edge Cases "current value source" for the decision): if the producer's `GET /api/admin/operators` is extended to include each operator's `operatorContext.defaultAccountId`, the dialog populates from the list response. If NOT extended (current state), the dialog opens with an empty input + "현재 값은 보이지 않습니다" hint, and Save unconditionally overwrites. **Chosen at impl**: open WITHOUT current-value pre-population in v1 (the OPTIONAL producer extension is deferred to a separate task; this task does not block on it).
7. **Tests** — vitest unit: 5 cases on `OperatorProfileEditDialog` (initial empty / typed value + reason → onConfirm payload / Clear toggle + reason → onConfirm null payload / Save disabled when reason empty / Save disabled when input has whitespace-only); 1 unit on `/api/operators/[operatorId]/profile/route.ts` (valid POST → 204, invalid body → 422, downstream error → mapError); 1 unit on `operators-api.ts.setOperatorProfile` (asserts call shape: method=PATCH, path with operatorId, body shape, `X-Operator-Reason` header present, `Idempotency-Key` MUST NOT be sent — header-matrix-drift defense); 1 Playwright e2e — DEFERRED with `test.skip(true)` per PC-FE-016 precedent (harness not stood up; vertical coverage via unit + producer IT).

After this task lands, an authorized SUPER_ADMIN can provision any operator's default finance account through the platform-console UI without DBA SQL — completing the admin-on-behalf-of vertical slice.

# Decision authority (why same `[operatorId]/*` per-row pattern, why separate dialog component, why no current-value pre-population in v1)

- **Why same `[operatorId]/*` per-row pattern (NOT a separate page)**: the producer's `PATCH /api/admin/operators/{operatorId}/profile` follows the established admin {operatorId}/* family (`/roles` + `/status`); the consumer naturally follows. Adding a separate "/operators/admin-profile" page would split the admin operator-management UX across screens — anti-pattern (PC-FE-004 explicitly groups all per-operator mutations into `OperatorsScreen`'s per-row action cluster).
- **Why a separate `OperatorProfileEditDialog` component (NOT extending `OperatorConfirmDialog`)**: `OperatorConfirmDialog` is parametrized over reason + role multi-select. Adding a `defaultAccountId` input slot would parametrize it over yet another shape, growing the dialog's surface for every future per-attribute admin form (`wmsDefaultWarehouseId`, `scmDefaultNodeId`, etc.). A per-attribute dialog scales linearly (one component per attribute), matches single-responsibility, and keeps `OperatorConfirmDialog`'s scope frozen.
- **Why no current-value pre-population in v1**: BE-307's producer surface does not expose the target operator's current `finance_default_account_id` (the producer-side `OperatorView` projection lacks the field on the operators list response). Extending `GET /api/admin/operators` to include `operatorContext` per item is a separate spec-first task; this task does not block on it. The dialog opens with an empty input + a "현재 값은 보이지 않습니다 — 입력값이 그대로 저장됩니다" hint. Save unconditionally writes the input (or null for explicit Clear). Stale value risk is bounded — operators that want the current value can self-serve check via `/operators` (own profile area) and the SUPER_ADMIN can be informed verbally; the producer's audit trail captures every write regardless of stale pre-state.
- **Why client-side `operatorId == self.operatorId` Save-disabled gate (instead of just relying on producer `400 SELF_PROFILE_UPDATE_FORBIDDEN_VIA_ADMIN_PATH`)**: the producer is the authority and the proxy honors its response, but offering a button that always 400s is poor UX. Disable the per-row "Profile 편집" button when the row is the caller's own (the operators list response already carries `caller.operatorId == row.operatorId` info via the session helper). The producer-side rejection is the fail-safe; the UI gate is the UX layer.
- **Why reason+confirm-gated (NOT inline save)**: mirror `[operatorId]/status` precedent — cross-operator mutations require explicit `X-Operator-Reason` per producer matrix. Inline save would either (a) skip the reason (header-matrix-drift defect) or (b) prompt for reason on every keystroke (worse UX). Dialog + reason capture + explicit Save is the established admin-mutation pattern.
- **Why `Idempotency-Key MUST NOT be sent`**: per BE-307 admin-api.md producer matrix — same as `/roles` + `/status`. PATCH is naturally idempotent; sending the key is a header-matrix-drift defect (FE-004 precedent).

---

# Scope

## In Scope

**Specs (spec PR)**:

- `projects/platform-console/specs/contracts/console-integration-contract.md § 2.4.3 GAP operators surface`:
  - Operations table (currently 6 rows after PC-FE-016): add row 7 — `PATCH /api/admin/operators/{operatorId}/profile` (admin-on-behalf-of, kind = mutation, required permission = `operator.manage`).
  - Per-endpoint header matrix table (currently 6 rows after PC-FE-016): add row 7 — `PATCH .../{id}/profile | required | MUST NOT send | producer requires reason; admin-on-behalf-of; mirrors {id}/roles + {id}/status header non-uniformity`.
  - Inline note next to the operations table: "self-profile-update via admin path → producer returns 400 `SELF_PROFILE_UPDATE_FORBIDDEN_VIA_ADMIN_PATH`; the console gates the per-row button when the row is self" (UI gate as fail-safe; producer is the authority).
- `projects/platform-console/specs/contracts/console-integration-contract.md § 3.1 parity matrix`:
  - Currently 17 attestation-marker rows (post PC-FE-016). Add row 18 — `operators: admin-set-profile | features/operators / setOperatorProfile | § 2.4.3 | PATCH /api/admin/operators/{operatorId}/profile (admin-api § PATCH {operatorId}/profile, **admin-on-behalf-of**) | M | reason ONLY — Idempotency-Key MUST NOT be sent (mirror /roles + /status non-uniformity) | verified by TASK-PC-FE-017`. Update `parity-verification.test.ts` expected count `17 → 18` (deferred to impl PR per PC-FE-016 precedent — test fixture is test code, not spec text).
- This task file.
- `projects/platform-console/tasks/INDEX.md` — ready entry.

**Code (impl PR)**:

- `projects/platform-console/apps/console-web/src/app/api/operators/_proxy.ts`: add `AdminUpdateProfileBodySchema = z.object({ defaultAccountId: z.union([z.null(), z.string().trim().min(1).max(36).regex(/^[^\s\x00-\x1f\x7f]+$/)]), reason: z.string().trim().min(1) }).strict()`.
- `projects/platform-console/apps/console-web/src/app/api/operators/[operatorId]/profile/route.ts` (new — mirror `[operatorId]/status/route.ts`): `POST` handler, `runtime = 'nodejs'`, parse body via `AdminUpdateProfileBodySchema`, call `setOperatorProfile(operatorId, body.defaultAccountId, body.reason)`, return `new NextResponse(null, { status: 204 })` on success, `mapError(err, requestId)` on failure.
- `projects/platform-console/apps/console-web/src/features/operators/api/operators-api.ts`: add `setOperatorProfile(operatorId: string, defaultAccountId: string | null, reason: string): Promise<void>` mirroring `changeOperatorStatus` exactly (PATCH, path with operatorId, body `{ operatorContext: { defaultAccountId } }`, reason header, `expectNoContent: true`, NO idempotency).
- `projects/platform-console/apps/console-web/src/features/operators/api/types.ts`: add `AdminUpdateProfileInput` type if needed (or inline in `operators-api.ts`).
- `projects/platform-console/apps/console-web/src/features/operators/components/OperatorProfileEditDialog.tsx` (new, sibling of `OperatorConfirmDialog`): client component, single text input + Clear toggle + reason textarea + Save/Cancel. Inline server error after failed submit (mapped from producer codes per § 2.5). UX safety: do NOT auto-save on input change — explicit Save. Save button disabled when reason is empty OR input has client-side validation errors (whitespace-only / over-36 / control chars).
- `projects/platform-console/apps/console-web/src/features/operators/components/OperatorsScreen.tsx`: import `OperatorProfileEditDialog`. Add per-row "Profile 편집" button in the action column (disabled when `row.operatorId == self.operatorId` per § Decision authority). Add a `useMutation` for `setOperatorProfile` (mirror `editRoles.mutate`); invalidate operators list query on success. Track which row's dialog is open via local state.
- `projects/platform-console/apps/console-web/src/features/operators/hooks/use-operators.ts`: add `useSetOperatorProfile()` hook (mirror `useChangeOperatorStatus`).
- **Tests** (impl PR — vitest + parity matrix):
  - `apps/console-web/tests/unit/features/operators/OperatorProfileEditDialog.test.tsx`: 5 cases per § In Scope.
  - `apps/console-web/tests/unit/api/operators/admin-profile-route.test.ts`: 3 cases (204 / 422 invalid / mapError downstream).
  - `apps/console-web/tests/unit/features/operators/operators-api-set-profile.test.ts`: 2 cases — (a) call shape (method=PATCH, path includes operatorId, body `{ operatorContext: { defaultAccountId } }`, `X-Operator-Reason` header present and equals input, **NO `Idempotency-Key` header**, **NO `X-Operator-Token` header** since this is the GAP admin operator JWT flow, not the BFF); (b) `defaultAccountId: null` → body `{ operatorContext: { defaultAccountId: null } }`.
  - `apps/console-web/tests/unit/parity-matrix.ts`: add row 18.
  - `apps/console-web/tests/unit/parity-verification.test.ts`: update expected count `17 → 18` + any row-18-specific assertions following the row-17 (PC-FE-016 change-profile) pattern.
  - `apps/console-web/tests/e2e/operators-admin-profile.spec.ts`: NEW with `test.skip(true)` documenting the click sequence (mirror PC-FE-016 e2e deferral pattern — harness not stood up; vertical coverage via unit + producer IT).

## Out of Scope

- **Current-value pre-population**: requires producer-side extension to `GET /api/admin/operators` (include `operatorContext.defaultAccountId` per item). Separate spec-first task (sibling extension; trivial — add a column to `OperatorSummaryResponse`). v1 opens dialog with empty input + a hint that the current value is not visible.
- **Bulk admin-set-profile UI**: out of scope. Producer-side bulk endpoint is also out of scope per BE-307 § Out of Scope. v1 single-target per dialog.
- **Console-bff orchestrator**: explicitly NOT applicable (ADR-MONO-017 D2 Option A + § 2.4.9 hard invariant — single-domain mutation stays per-domain). Same pattern as PC-FE-016.
- **Other `operatorContext` attributes** (wmsDefaultWarehouseId etc.): v1 dialog accepts only `defaultAccountId`. A future task adds per-attribute fields (each gated on its own column existence + producer write endpoint).
- **Self-via-admin handling beyond UI gate**: producer is authoritative (`400 SELF_PROFILE_UPDATE_FORBIDDEN_VIA_ADMIN_PATH`); UI's self-row button-disabled is UX only. If client gate is bypassed (devtools), producer returns 400 → dialog surfaces the error inline.
- **Audit-log visibility for admin actions in the console**: out of scope. `admin_actions` rows are written producer-side; surfacing them in console UI is a separate future task.
- **ADR amendment**: none. ADR-MONO-017 D2 Option A + ADR-MONO-013 D5 me/password precedent + PC-FE-016 me/profile precedent + FE-004 {operatorId}/* per-row mutation precedent all govern. HARDSTOP-09 not triggered.

# Acceptance Criteria

- **AC-1 (spec PR atomic)**: a single spec PR lands `console-integration-contract.md § 2.4.3 + § 3.1` edits + this task file + INDEX with no production code. Atomic per the BE-307 / PC-FE-016 / BE-306 spec-first pattern. The `parity-matrix.ts` + `parity-verification.test.ts` source updates are deferred to the impl PR per the PC-FE-016 precedent.
- **AC-2 (proxy route shape)**: the new `/api/operators/[operatorId]/profile/route.ts` is structurally identical to `/api/operators/[operatorId]/status/route.ts` (line count within ±25%, same imports from `_proxy.ts`, same `runtime = 'nodejs'`, same `mapError` + `newRequestId` flow, same Next.js params handling).
- **AC-3 (no console-bff change)**: 0 byte diff across `projects/platform-console/apps/console-bff/src/**` in the impl PR. ADR-MONO-017 D2 + § 2.4.9 hard invariant preserved.
- **AC-4 (no GAP producer change)**: 0 byte diff across `projects/global-account-platform/**` in the impl PR. BE-307 producer is already on main `4f43e2c7`.
- **AC-5 (zero-retrofit other producers)**: 0 byte diff across `projects/{wms,scm,finance,erp,fan,ecommerce}-platform/`. **10회째 confirmation** of ADR-MONO-013 § 3.3 zero-retrofit (Phase 2/4/5/6/7-skeleton/7-MVP/7-health/7-write-self-be/7-write-admin-be/this 7-write-admin-fe).
- **AC-6 (parity matrix count)**: `parity-verification.test.ts` expected attestation-marker count = **18** post-merge (was 17 after PC-FE-016). Test PASSES.
- **AC-7 (header-matrix-drift defense)**: `operators-api-set-profile.test.ts` asserts `X-Operator-Reason` is present AND `Idempotency-Key` is **NOT** sent. Sending the key is a producer-matrix-drift defect (FE-004 precedent — same defense pattern as `[operatorId]/roles` + `[operatorId]/status` tests).
- **AC-8 (self-row UI gate)**: the per-row "Profile 편집" button is disabled when `row.operatorId == self.operatorId`. Tested in `OperatorsScreen` slice/integration (existing slice tests can be extended OR a new case added).
- **AC-9 (BE-303 3-dim verified at close chore)**: per CLAUDE.md, close chore opens **only after** impl PR satisfies all three dims (state=MERGED + mergeCommit match + 0 failing pre-merge).
- **AC-10 (BE-299 done re-stage check at close chore)**: per CLAUDE.md, `git mv review/ → done/` + Status flip + `git add` + `git show :<done-path>` confirms `Status: done`.

# Related Specs

- `projects/platform-console/specs/contracts/console-integration-contract.md § 2.4.3` — extended in this task (ops table row 7 + per-endpoint header matrix row 7).
- `projects/platform-console/specs/contracts/console-integration-contract.md § 3.1` — parity matrix row 18 added (count `17 → 18`).
- `projects/platform-console/specs/contracts/console-integration-contract.md § 2.4.9.1 / § 2.4.9.2` — **byte-unchanged**. Composition-route hard invariant preserved; this task does not touch console-bff.
- `projects/global-account-platform/specs/contracts/http/admin-api.md § PATCH /api/admin/operators/{operatorId}/profile` — **byte-unchanged** (authored in BE-307 spec PR #710; this consumer task references it).
- `docs/adr/ADR-MONO-017-platform-console-bff-architecture.md` — **byte-unchanged**. D2 Option A + § 2.4.9 hard invariant directly support this task's no-console-bff-hop pattern.

# Related Contracts

- `projects/platform-console/specs/contracts/console-integration-contract.md` — consumer contract extended.
- `projects/global-account-platform/specs/contracts/http/admin-api.md` — producer contract consumed (already on main after BE-307).

# Edge Cases

- **Target operator row is the caller's own** → UI button is **disabled** (UX gate). If gate bypassed via devtools, producer returns `400 SELF_PROFILE_UPDATE_FORBIDDEN_VIA_ADMIN_PATH`; dialog surfaces the error inline.
- **Target operator does not exist** (e.g. concurrent deletion) → producer returns `404 OPERATOR_NOT_FOUND`; proxy maps; dialog surfaces "Operator not found"; the operators list is invalidated on the next render anyway.
- **Cross-tenant target** (caller non-platform, target tenant ≠ caller tenant) → producer returns `403 TENANT_SCOPE_DENIED`; dialog surfaces "선택한 운영자에 대한 권한이 없습니다"; operators list query is invalidated (the stale list may have included a target outside scope due to race).
- **Caller missing `operator.manage`** → operators screen as a whole is already permission-gated by PC-FE-004; an unauthorized caller never reaches this dialog. If somehow reached (devtools), producer returns `403 PERMISSION_DENIED` → forced re-login or inline error.
- **`X-Operator-Reason` header sent as whitespace** → client zod rejects (`trim().min(1)`); UI surfaces inline. If client bypassed, producer `400 REASON_REQUIRED` → dialog surfaces.
- **Body sent without the `defaultAccountId` field** → proxy zod rejects (422); UI's Save button validation prevents this client-side.
- **Optimistic-lock conflict** (caller racing another admin OR target self-edits via me/profile concurrently) → producer `409 OPTIMISTIC_LOCK_CONFLICT`; dialog surfaces "동시 변경 충돌 — 다시 시도하세요".
- **Caller `tenant='*'` (platform-scope) targeting a tenant-scoped operator** → permitted (sentinel authority); dialog proceeds; producer accepts.
- **Empty input + Clear toggle** → submits `defaultAccountId: null`; producer accepts (explicit clear).
- **Whitespace-only input** → client zod rejects; UI prevents submit.

# Failure Scenarios

- **`setOperatorProfile` sends `Idempotency-Key`** → producer accepts but the header-matrix-drift defect surfaces; AC-7 test catches it. Reject in review.
- **`OperatorProfileEditDialog` auto-saves on input change** → audit-row pollution (every keystroke writes a row); reject in review — Save must be explicit.
- **The per-row "Profile 편집" button visible on self-row** → AC-8 fails. Reject in review — UI must disable the button via `row.operatorId === self.operatorId` check.
- **Dialog initial value retrieved from a fake / fabricated source** (e.g. localStorage) → the v1 design explicitly opens with empty input + hint. A fabricated initial value is worse than no value (operator may believe the current state is empty when it isn't). Reject any "current value fake" in review.
- **Reviewer suggests routing through console-bff for "consistency"** → reject. Same reasoning as PC-FE-016 — ADR-MONO-017 D2 Option A + § 2.4.9 hard invariant explicitly govern single-domain mutations stay per-domain.
- **Reviewer suggests skipping the per-row button on self-row entirely** (vs disabling) → debatable. Disabled-with-tooltip explains *why* ("자가 변경은 /operators 자기 영역에서"); hidden silently confuses the SUPER_ADMIN who expects symmetry. Choose disabled+tooltip.
- **Reviewer suggests reusing `OperatorConfirmDialog` by adding a slot** → reject. See § Decision authority "Why a separate dialog component". Single-responsibility wins; `OperatorConfirmDialog` scope stays frozen.
- **Save unconditionally overwrites without showing the current value** → AC-mention: the v1 design EXPLICITLY does this; SUPER_ADMIN is responsible for communicating with the operator. The "현재 값은 보이지 않습니다" hint is the user-visible disclaimer. **DO NOT add fake / placeholder current-value strings** to the dialog.

# Verification

1. Spec PR diff: `git diff --stat origin/main` shows ≤ 3 modified files — this task file + `console-integration-contract.md` + INDEX.
2. Impl PR diff: code + tests under `projects/platform-console/apps/console-web/` only; AC-3 + AC-4 + AC-5 grep zero diff outside.
3. Unit (vitest): `pnpm --filter console-web test` GREEN; new tests included.
4. Build + lint (vitest + tsc + eslint): `pnpm --filter console-web build` GREEN.
5. Self-CI 20/20 GREEN at impl-PR merge time (`gh pr checks <n>` pre-merge snapshot); BE-303 3-dim verified at close chore start.
6. `git log origin/main` tip after impl-PR merge = the squash commit hash returned by `gh pr view <n> --json mergeCommit`.

분석=Opus 4.7 / 구현 권장=Opus 4.7 (cross-stratum: new proxy route + new client dialog + zod schema + tanstack-query useMutation wiring + per-row self-gate + 4 vitest test files + parity matrix count update + console-integration-contract surgical edits; the dialog + integration parts are net new — deserves Opus judgement) / 리뷰=Opus 4.7 (dispatcher 독립 재검증 — AC-2 source-shape grep / AC-3+4+5 byte-diff grep / AC-6 parity count assertion / AC-7 header-matrix-drift grep / AC-8 self-row UI gate / AC-9 BE-303 3-dim at close chore / AC-10 BE-299 done re-stage).
