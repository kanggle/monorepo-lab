# Task ID

TASK-PC-FE-018

# Title

platform-console admin profile-edit dialog current-value pre-population — consume TASK-BE-308's `GET /api/admin/operators` per-item `operatorContext` extension to pre-populate the dialog's input with the target operator's current `finance_default_account_id`; closes TASK-PC-FE-017 honest gap (c) "no current-value pre-population in admin profile-edit dialog"

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

- **depends on**: TASK-BE-308 (GAP `GET /api/admin/operators` per-item `operatorContext` extension, **DONE** 2026-05-22 — spec PR #716 `3f8e7093` / impl PR #717 `c0c91969` / close chore #718 `b8e9c196`). Without BE-308 merged, the operators list response would not carry `operatorContext` and this task's pre-population would have nothing to read.
- **depends on (indirectly)**: TASK-PC-FE-017 (console-web admin profile-edit per-row UI, **DONE** 2026-05-22). The dialog component + per-row integration this task augments.
- **origin**: TASK-PC-FE-017 § Decision authority "Why no current-value pre-population in v1" + § Edge Cases "Dialog initial value retrieved from a fake / fabricated source" — explicitly defers current-value pre-population to a separate task once the producer surface (BE-308) lands. Also TASK-BE-307 § Out of Scope "Operators list response extension to include `operatorContext`".
- **prerequisite for**: nothing (this completes the admin profile-edit UX — admin sees what value is being overwritten).
- **spec-first**: spec PR (this file + `console-integration-contract.md § 2.4.3` ops table row 1 note + INDEX) → impl PR (types.ts schema extension + dialog `initialDefaultAccountId` prop + `OperatorsScreen` prop wiring + dialog hint text update + tests) → close chore PR.
- **no ADR** (HARDSTOP-09 not triggered): purely a consumer adoption of a producer field already on main. No architectural decision; no permission change; no header-matrix change; no parity matrix row addition (consumer method/path unchanged — same `GET /api/admin/operators` read endpoint already on the parity matrix row 11 / § 2.4.3 ops table row 1; this task consumes an *optional new field* on the existing response shape).

---

# Goal

After BE-308, each item in `GET /api/admin/operators` carries `operatorContext.defaultAccountId` when the operator has a value set (NULL → field omitted via `@JsonInclude.NON_NULL`). PC-FE-017's admin profile-edit dialog (`OperatorProfileEditDialog`) currently opens with an empty input + a "현재 값은 보이지 않습니다 — 입력값이 그대로 저장됩니다" disclaimer, forcing the SUPER_ADMIN to provision blind.

Activate the read path on the consumer side:

1. **`features/operators/api/types.ts` `OperatorSummarySchema`** — extend the zod schema with an optional `operatorContext: { defaultAccountId?: string }` field. Strict shape: `z.object({ defaultAccountId: z.string().optional() }).optional()` (omits when producer omits — mirrors producer's `@JsonInclude.NON_NULL`).
2. **`OperatorProfileEditDialog.tsx`** — add an `initialDefaultAccountId?: string | null` prop. State initialization on `open` uses the prop (null/undefined → empty input + Clear OFF; non-empty string → pre-populated value + Clear OFF). Hint text replaced: instead of *"현재 값은 보이지 않습니다 — 입력값이 그대로 저장됩니다"*, render *"현재 값: {value or '미설정'}. 입력값이 그대로 저장됩니다. 기본 계정을 비우려면 아래 Clear 토글을 사용하세요. 이 작업은 감사 사유와 함께 기록됩니다."*. The dialog still opens with the current value pre-filled and Clear OFF — the operator can explicitly modify or clear.
3. **`OperatorsScreen.tsx`** — pass `initialDefaultAccountId={profileEditFor?.operatorContext?.defaultAccountId ?? null}` to `<OperatorProfileEditDialog />`. The `profileEditFor` already carries the full `OperatorSummary` (set by the per-row button click); the new `operatorContext` field rides along through the same query response.
4. **Tests** — vitest unit cases:
   - Dialog: (a) `initialDefaultAccountId = undefined` → input empty + hint shows "미설정"; (b) `initialDefaultAccountId = "acc-uuid-7"` → input value = `"acc-uuid-7"` + hint shows the value; (c) typing a new value over the pre-populated one → submit payload carries the new value, NOT the initial; (d) Clear toggle ON over a pre-populated value → submit payload `null`; (e) reset-on-open — opening the dialog twice for different operators picks up each one's initial (no stale state).
   - Types: parse-check that an extra `operatorContext` field on a list item is accepted (forward-compat from main where BE-308 lands the field).

After this task lands, the SUPER_ADMIN sees the current value when opening the admin profile-edit dialog — no more blind provisioning — completing TASK-PC-FE-017's honest gap (c) closure.

# Decision authority (why dialog-side prop vs context, why reset on open, why no parity matrix row)

- **Why `initialDefaultAccountId` prop on the dialog (NOT via Context / per-row pre-fetch)**:
  - The operators list response already carries the value (post BE-308). Reading from the same `OperatorSummary` that opens the dialog is the minimum surface change; no new query, no new context, no N+1.
  - A Context-based approach would add module coupling for a strictly local concern (the dialog is the only consumer of this value). YAGNI; reject.
  - A per-row pre-fetch (`GET /api/admin/operators/{operatorId}/profile`) would require a new producer endpoint — explicitly rejected by BE-307 § Decision authority "avoid speculative endpoint creation" + BE-308 § Decision authority "Why list endpoint not a new dedicated `GET /{id}/profile`".
- **Why reset on each open (NOT carry state across openings)**:
  - Opening the dialog for operator A, cancelling, then opening for operator B must pick up B's initial — not A's. The existing PC-FE-017 implementation already resets state on `open` via `useEffect`; this task extends the reset path to also initialize from the new prop.
  - Mirror PC-FE-017 § Edge Cases "Reset state on open" — same useEffect, new initial source.
- **Why no parity matrix row addition**:
  - The parity matrix row for the operators list (row 11) covers `GET /api/admin/operators`. The new `operatorContext` field is an optional addition to the existing response shape — not a new method/path/operation. No new parity attestation marker.
  - § 2.4.3 ops table row 1 (the operations table — distinct from the § 3.1 parity matrix) carries a one-line cross-ref note that the list response items optionally carry `operatorContext` (consumed by PC-FE-018). This is the right place for a shape-level note; the parity matrix is operation-level.
- **Why pre-populate AND keep Clear toggle visible (NOT auto-set Clear when value is empty)**:
  - The semantic "current value is empty" is distinct from "operator intends to clear". An empty initial means the column is NULL → input opens empty + Clear OFF (no implicit clear-intent). The operator may type a new value to set, or check Clear to remain explicit (Clear sends `null` — even though the column is already NULL, the audit row still records the intent; redundant but harmless).
  - Auto-checking Clear when the initial is empty would convey false intent to the audit log. Reject.
- **Why no producer change**:
  - The producer surface (`operatorContext` on list items) is already on main from BE-308. This task is purely consumer-side. AC-3 will verify 0 byte diff across `projects/global-account-platform/`.

---

# Scope

## In Scope

**Specs (spec PR — this PR)**:

- `projects/platform-console/specs/contracts/console-integration-contract.md § 2.4.3 GAP operators surface`:
  - Operations table row 1 (list) Notes column: add a phrase `; response items optionally carry operatorContext.defaultAccountId per item (producer-side @JsonInclude.NON_NULL — omitted when the operator has no value) — TASK-PC-FE-018 consumes this to pre-populate the admin profile-edit dialog`.
- This task file.
- `projects/platform-console/tasks/INDEX.md` — ready entry.

**Code (impl PR — out of scope here, listed for the dispatch agent to know the shape)**:

- `projects/platform-console/apps/console-web/src/features/operators/api/types.ts`:
  - Add `operatorContext: z.object({ defaultAccountId: z.string().optional() }).optional()` to `OperatorSummarySchema` (strict — extra nested keys rejected; the producer is the authority, the consumer parses only what it documents).
- `projects/platform-console/apps/console-web/src/features/operators/components/OperatorProfileEditDialog.tsx`:
  - Add `initialDefaultAccountId?: string | null` to `OperatorProfileEditDialogProps`.
  - In the `useEffect(... [open])` reset: set `value` to `initialDefaultAccountId ?? ''`; keep `cleared = false`; keep `reason = ''`.
  - Replace the hint text with the new "현재 값: {value or '미설정'}" wording.
- `projects/platform-console/apps/console-web/src/features/operators/components/OperatorsScreen.tsx`:
  - Pass `initialDefaultAccountId={profileEditFor?.operatorContext?.defaultAccountId ?? null}` to `<OperatorProfileEditDialog />`.
- **Tests** (impl PR):
  - `apps/console-web/tests/unit/features/operators/OperatorProfileEditDialog.test.tsx`: 5 new cases per § In Scope #4.
  - `apps/console-web/tests/unit/features/operators/operator-summary-schema.test.ts` (new — if not already covered): parses a list item with `operatorContext.defaultAccountId` AND a list item without `operatorContext` (both valid; strict on nested keys).
  - No new e2e (PC-FE-016/017 precedent — vertical coverage via unit + producer IT).

## Out of Scope

- **Per-row pre-fetch of the profile from a dedicated endpoint** — rejected (see § Decision authority). The list response is the single source.
- **Other `operatorContext` carrier attributes** (e.g. `wmsDefaultWarehouseId`) — same pattern (read + dialog-prop) when the producer adds them. v1 carries only `defaultAccountId`.
- **Audit log surfacing** — out of scope. Producer-side audit rows are authoritative; surfacing them in console is a separate future task.
- **Console-bff orchestrator** — NOT applicable. List endpoint is consumed via the existing per-domain proxy from console-web (FE-002/003/004 pattern); BE-308 producer extension does not change the consumption surface.
- **Producer changes** — none. AC-3 verifies 0 byte diff across `projects/global-account-platform/`.
- **ADR amendment** — none. ADR-MONO-017 D2/D4 unaffected; ADR-MONO-013/014/015/016 unaffected.
- **`parity-matrix.ts` / `parity-verification.test.ts` update** — none. Count stays at 18 (consumer method/path unchanged; this is a shape-level field addition, not a new operation).

# Acceptance Criteria

- **AC-1 (spec PR atomic)**: a single spec PR lands `console-integration-contract.md § 2.4.3` row 1 note + this task file + INDEX with no production code. ≤ 3 modified files. Atomic per the PC-FE-016/017 spec-first pattern.
- **AC-2 (zod schema extension)**: at impl PR, `OperatorSummarySchema` accepts an optional `operatorContext: { defaultAccountId?: string }` field. A list item lacking the field still parses; an item with it parses with the field present.
- **AC-3 (no GAP producer change)**: 0 byte diff across `projects/global-account-platform/` in the impl PR. Producer side (BE-308) is already on main `b8e9c196`.
- **AC-4 (no console-bff change)**: 0 byte diff across `projects/platform-console/apps/console-bff/src/**` in the impl PR. ADR-MONO-017 D2 + § 2.4.9 hard invariant preserved.
- **AC-5 (zero-retrofit other producers)**: 0 byte diff across `projects/{wms,scm,finance,erp,fan,ecommerce}-platform/`. **12회째 confirmation** of ADR-MONO-013 § 3.3 zero-retrofit.
- **AC-6 (dialog pre-population)**: the dialog input is pre-populated with `initialDefaultAccountId` when the prop is a non-empty string; empty input when the prop is null/undefined. Hint text reflects the current value or "미설정".
- **AC-7 (dialog reset on re-open)**: opening the dialog twice for different operators picks up each operator's `initialDefaultAccountId` independently — no stale state. Tested.
- **AC-8 (override / clear preserved)**: typing a new value over the pre-populated one submits the new value; Clear toggle over a pre-populated value submits `null`. Both tested.
- **AC-9 (parity matrix unchanged)**: `parity-verification.test.ts` expected attestation-marker count remains **18** (no new operation row).
- **AC-10 (BE-303 3-dim verified at close chore)**: per CLAUDE.md, close chore opens **only after** impl PR satisfies all three dims (state=MERGED + mergeCommit match + 0 failing pre-merge).
- **AC-11 (BE-299 done re-stage check at close chore)**: per CLAUDE.md, `git mv ready/ → done/` + Status flip + `git add` + `git show :<done-path>` confirms `Status: done`.

# Related Specs

- `projects/platform-console/specs/contracts/console-integration-contract.md § 2.4.3` — extended in this task (ops table row 1 note).
- `projects/platform-console/specs/contracts/console-integration-contract.md § 3.1 parity matrix` — **byte-unchanged**. Row count stays 18.
- `projects/global-account-platform/specs/contracts/http/admin-api.md § GET /api/admin/operators` — **byte-unchanged** (extension landed in BE-308 spec PR #716 squash `3f8e7093`). This consumer task references it.
- `docs/adr/ADR-MONO-017-platform-console-bff-architecture.md` — **byte-unchanged**.

# Related Contracts

- `projects/platform-console/specs/contracts/console-integration-contract.md` — consumer contract extended (note only).
- `projects/global-account-platform/specs/contracts/http/admin-api.md` — producer contract consumed (already on main).

# Edge Cases

- **Operator row has no value (`operatorContext` omitted by producer)** → `initialDefaultAccountId = null` → dialog opens with empty input + hint "현재 값: 미설정".
- **Operator row has a value** → `initialDefaultAccountId = "acc-uuid-7"` → dialog opens with input pre-filled + hint "현재 값: acc-uuid-7".
- **Producer response includes `operatorContext` but `defaultAccountId` is missing inside it (defensive, shouldn't happen post BE-308)** → `initialDefaultAccountId = undefined` → treated as null; dialog opens empty.
- **Operator opens the dialog, cancels, opens for a DIFFERENT operator** → the new operator's `initialDefaultAccountId` populates; no stale state.
- **Operator opens the dialog, sees pre-populated value, clicks Save without changing anything** → submits the same value (effectively a no-op against the column, but the audit row still records the intent — producer-side idempotency; mirrors `/roles` full-replace pattern).
- **Operator opens the dialog, clears via Clear toggle on a non-empty initial** → submits `null`; producer clears the column + audits.
- **Stale list response (BE-308's value is from when the list was fetched; another operator just edited it)** → the dialog's pre-population may be slightly stale. Acceptable — the SUPER_ADMIN sees the value as of fetch time; an explicit refresh / re-open after the prior edit closes the window. Same staleness model as roles + status.
- **Server response shape includes an unexpected key inside `operatorContext`** (e.g. forward-compat `wmsDefaultWarehouseId`) → strict zod object rejects → the list response fails to parse → operators screen shows an inline error. Acceptable — strict-vs-tolerant is a deliberate consumer policy (better fail-fast than silent acceptance of an unknown shape).

# Failure Scenarios

- **Dialog leaks stale state across re-opens** → AC-7 catches. Reject.
- **`initialDefaultAccountId = null` populates the input with the string `"null"`** → AC-6 catches. Reject; use empty string for the input value.
- **Submitting an unchanged pre-populated value sends an empty string** → producer rejects `""` as `400 INVALID_REQUEST`. The dialog logic must trim and pass the string as-is (never `""`); if the input matches the initial AND the operator did not change it, still submit the trimmed value (producer accepts the same-value PATCH as a no-op audit row).
- **`OperatorSummarySchema` loses strict-ness on the new nested object** → AC-2 catches by asserting an unknown nested key is rejected. Reject.
- **Hint text retained as "현재 값은 보이지 않습니다"** → AC-6 catches. Replace it.
- **Reviewer suggests adding a parity matrix row** → reject. The operation is unchanged; the new field is a shape-level addition. § 2.4.3 ops table row 1 carries the cross-ref note.

# Verification

1. Spec PR diff: `git diff --stat origin/main` shows ≤ 3 modified files — this task file + `console-integration-contract.md` + INDEX.
2. Impl PR diff: code + tests under `projects/platform-console/apps/console-web/` only; AC-3 + AC-4 + AC-5 grep zero diff outside.
3. Unit (vitest): `pnpm --filter console-web test` GREEN; 5 new dialog cases + 2 new schema cases included.
4. Build + lint: `pnpm --filter console-web build` GREEN.
5. Self-CI 20/20 GREEN at impl-PR merge time (`gh pr checks <n>` pre-merge snapshot); BE-303 3-dim verified at close chore start.
6. `git log origin/main` tip after impl-PR merge = the squash commit hash returned by `gh pr view <n> --json mergeCommit`.

분석=Opus 4.7 / 구현 권장=Sonnet 4.6 (mechanical prop wiring + zod schema extension + 5 vitest cases; no net-new judgement vs PC-FE-016/017 — deserves Sonnet) / 리뷰=Opus 4.7 (dispatcher 독립 재검증 — AC-1 file count / AC-2 zod schema / AC-3+4+5 byte-diff grep / AC-6+7+8 dialog behavior / AC-9 parity count / AC-10 BE-303 3-dim at close chore / AC-11 BE-299 done re-stage).
