# Task ID

TASK-PC-FE-031

# Title

operators-admin-profile.spec.ts intermittent failure — `OperatorProfileEditDialog` re-opens with stale `initialDefaultAccountId` before `useSetOperatorProfile` mutation's operators-list invalidation refetch settles. Race between dialog mount (line 86 click) and React Query refetch completion. Same spec also fails intermittently at line 79 (`dialog.toHaveCount(0)` after Save click — close timing). Both are timing-sensitive assertions; 4-dispatch evidence shows 2 pass / 2 fail (different assertion lines per dispatch). Investigate the parent component's dialog-state ↔ list-query coupling, decide between (a) UI fix (block re-open until refetch settles) / (b) mutation fix (await refetch before mutation resolve) / (c) test fix (explicit `waitForResponse`).

# Status

ready

# Owner

frontend

# Task Tags

- code
- fix
- e2e

---

# Dependency Markers

- **depends on**: TASK-BE-312 (closed `bdc98530` — 12th cycle layer; both operators-profile + operators-admin-profile specs PASSED on dispatch `26349452107` and post-cleanup `26350357045`; this race-condition layer was masked by the first 2 successful dispatches but surfaced on auto-trigger nightly `26351846672` + verify re-dispatch `26352613942`).
- **prerequisite of**: nightly main GREEN restoration of Platform Console E2E full-stack job.

---

# Goal

After this fix lands, `operators-admin-profile.spec.ts:46-90` passes **reproducibly on cold runners** (≥ 3 consecutive nightly dispatches PASS). Both timing-sensitive assertions resolve:
- Line 79 `expect(dialog).toHaveCount(0)` — dialog closes deterministically after Save.
- Line 89 `expect(input).toHaveValue('01928c4a-7e9f-7c00-9a40-d2b1f5e8a000')` — re-opened dialog input pre-populates with the just-Saved value WITHOUT relying on Playwright retry semantics.

## Root cause evidence (4-dispatch sample)

| dispatch | result | failure line |
|----------|--------|--------------|
| `26349452107` (BE-312 iter 6, with diagnostic) | 2 passed | — |
| `26350357045` (BE-312 post-cleanup) | 2 passed | — |
| `26351846672` (auto-trigger after BE-312 close chore #794) | 1 failed | line 79 (dialog stays open) |
| `26352613942` (verify re-dispatch on main, no code change) | 1 failed | line 89 (input empty) |

DOM evidence from `26352613942` line 89 retries:
- attempt 1: `<input id=":r7:" value="">` — **fresh React useId() mount → empty input**
- retry 1: `<input id=":r2:" value="01928c4a-..."` — different mount instance, value populated
- retry 2: `<input id=":r2:" value="01928c4a-..."` — same populated state

Different `useId()` ids prove the dialog **unmounted + re-mounted** between attempts. First mount happened before the operators list refetch settled (initialDefaultAccountId still `null` from stale cache); subsequent mounts saw the populated value.

## Hypothesis pool (to narrow during impl)

1. **Operators-list React Query invalidation but no `await` on refetch** — `useSetOperatorProfile.onSuccess` calls `invalidateOperators(qc)` (use-operators.ts:240) which schedules a refetch but does NOT block the mutation `Promise`. When the test clicks Save → dialog closes → user clicks re-open, the cached list may still hold the pre-Save row (`operatorContext` absent → `initialDefaultAccountId=null`). Fix: change mutation to `await qc.invalidateQueries({ ..., refetchType: 'all' })` so the mutation resolves AFTER the refetch completes; the dialog close is then deterministically tied to fresh data.

2. **Dialog mount races re-fetch** — even with proper invalidation, the parent (`OperatorsScreen`?) may pass `initialDefaultAccountId` from the OLD cached value at the moment of re-open click. Fix: parent reads `useOperatorsList().isFetching` and either (a) disables the targetButton while fetching, or (b) defers opening the dialog (`setOpen(true)`) until `isFetching === false`.

3. **`useEffect` deps gap** — `OperatorProfileEditDialog.tsx:108-116` `useEffect(() => { if (open) setValue(initialDefaultAccountId ?? '') }, [open, initialDefaultAccountId])` should re-fire when prop changes. But if the dialog mounted with `initialDefaultAccountId=null` first AND the parent's value updates between renders, React batches the effect run; the input briefly shows `''` until the next effect run. Visible to Playwright as `value=""`. Fix: synchronous `useMemo` for initial value + drop the useState entirely; OR `useLayoutEffect` so the DOM reflects the new value before paint.

4. **Dialog close timing (line 79)** — `useSetOperatorProfile.onSuccess` fires `setDialogOpen(false)` (presumed) but the same effect schedules a list invalidation; if these are in different parent state updates, the dialog stays mounted for an extra render cycle. Fix: batch both state updates with `React.startTransition` OR ensure the parent's `onSuccess` handler sets `open=false` synchronously.

5. **Hidden 5th case** — Save mutation's HTTP 204 may not invalidate the cache the way the comment claims (if the proxy doesn't trigger any of the listed `OPERATORS_KEY` queries). Investigation: trace the actual RQ state with React Query devtools (or `qc.getQueryCache()` log) to confirm the post-Save refetch fires.

## Decision authority — defer

Spec does NOT pre-select an implementation option. Mirroring the TASK-BE-312 / TASK-BE-311 / TASK-PC-FE-029 / 030 investigation-first cycle pattern:

The impl PR's first commit adds focused diagnostic instrumentation:
- `useSetOperatorProfile` — log `onSuccess` entry + the `invalidateQueries` return value + timing.
- `OperatorsScreen` (or wherever dialog open is managed) — log `dialogOpen` transitions + the `currentDefaultAccountId` value at the moment of re-open click.
- `OperatorProfileEditDialog` — log `useEffect` re-runs with `[open, initialDefaultAccountId]` values.

The next dispatch's docker compose log dump (console-web container) confirms which layer surfaces the staleness. Subsequent commits apply the targeted fix.

---

# Scope

## In Scope

- `projects/platform-console/apps/console-web/src/features/operators/` — diagnostic + targeted fix (whichever hypothesis lands).
- `projects/platform-console/apps/console-web/tests/e2e/operators-admin-profile.spec.ts` — test-side fix ONLY if hypothesis 5 lands OR if a UX-acceptable race window exists that warrants explicit `waitForResponse`.
- This task md + project `tasks/INDEX.md` ready entry.

## Out of Scope

- `OperatorProfileEditDialog`'s dialog UI styling / layout (only its useEffect / state-init wiring is in scope).
- `useSetOperatorProfile`'s API contract (POST body shape, error envelope) — those are PC-FE-017's surface and unchanged.
- BE-308 producer-side `operatorContext` extension — confirmed correct by BE-312 saga (the JWT + tenant + PII chain proved the producer chain is healthy).
- Server-side changes (admin-service / console-bff / finance-account-service) — race is purely client-side.

---

# Acceptance Criteria

- [ ] **AC-1 (functional, primary)** — Three consecutive nightly `workflow_dispatch` runs (or 3 consecutive scheduled nightlies) of `Nightly E2E (full-stack web-store + 4 backend full suites)` on the fix branch result in `Platform Console E2E full-stack` job SUCCESS with both specs `2 passed` (i.e. 3/3 PASS rate for the operators-admin-profile spec). Verified via `gh run view <id>` Playwright reporter output.
- [ ] **AC-2 (functional, secondary)** — `operators-admin-profile.spec.ts:79` passes WITHOUT relying on Playwright's `--retries` semantics. Manually run with `--retries=0` locally OR on CI; spec still passes.
- [ ] **AC-3 (functional, tertiary)** — `operators-admin-profile.spec.ts:89` passes WITHOUT relying on Playwright's `--retries` semantics. Same verification as AC-2.
- [ ] **AC-4 (regression check — operators-profile spec)** — `operators-profile.spec.ts` continues to PASS post-fix (no regression on the BE-312-resolved self-serve flow).
- [ ] **AC-5 (hard invariant — console-bff byte-unchanged, ADR-MONO-017 D4)** — `git diff --stat origin/main -- projects/platform-console/apps/console-bff/` = empty.
- [ ] **AC-6 (hard invariant — server-side services byte-unchanged)** — `git diff --stat origin/main -- projects/global-account-platform/apps/ projects/finance-platform/apps/` = empty (race is purely client-side; no server-side concession).
- [ ] **AC-7 (hard invariant — 5 other producers byte-unchanged)** — `git diff --stat origin/main -- 'projects/{wms,scm,erp,fan,ecommerce-microservices,finance}-platform/'` = empty (**28th zero-retrofit**).
- [ ] **AC-8 (hard invariant — workflow + docker-compose.e2e.yml byte-unchanged)** — `git diff --stat origin/main -- .github/workflows/ projects/platform-console/docker-compose.e2e.yml` = empty.
- [ ] **AC-9 (diagnostic cleanup)** — `git grep -n 'TASK-PC-FE-031.*diagnostic' projects/platform-console/apps/console-web/src/` returns 0 lines post-merge.
- [ ] **AC-10 (regression check — vitest)** — push CI `Frontend unit tests (ecommerce + fan-platform, vitest)` GREEN; any new test on the parent component or hook surface continues to pass.
- [ ] **AC-11 (BE-303 3-dim merge verification)** — close chore PR authored only after impl PR's 3-dim verification passes.

---

# Related Specs

- [`projects/platform-console/PROJECT.md`](../../PROJECT.md).
- [`projects/platform-console/apps/console-web/tests/e2e/operators-admin-profile.spec.ts`](../../apps/console-web/tests/e2e/operators-admin-profile.spec.ts) — failing spec at line 79 + line 89 (race-condition).
- [`projects/platform-console/apps/console-web/src/features/operators/components/OperatorProfileEditDialog.tsx`](../../apps/console-web/src/features/operators/components/OperatorProfileEditDialog.tsx) — investigation surface (useEffect deps + initialDefaultAccountId prop wiring).
- [`projects/platform-console/apps/console-web/src/features/operators/hooks/use-operators.ts`](../../apps/console-web/src/features/operators/hooks/use-operators.ts) — `useSetOperatorProfile` onSuccess invalidation timing.
- [`projects/platform-console/apps/console-web/src/features/operators/components/OperatorsScreen.tsx`](../../apps/console-web/src/features/operators/components/OperatorsScreen.tsx) — parent component owning dialog open state + currentDefaultAccountId prop.
- [`projects/global-account-platform/tasks/done/TASK-BE-312-admin-service-operator-context-refresh-after-save.md`](../../../global-account-platform/tasks/done/TASK-BE-312-admin-service-operator-context-refresh-after-save.md) — 12th cycle layer predecessor (closure narrative + saga lessons).

# Related Contracts

- None. Race is client-internal React Query timing; producer + BFF contracts already correct.

# Related Skills

- None additional.

---

# Edge Cases

- **`useSetOperatorProfile` mutation Promise resolution timing** — if the mutation resolves BEFORE invalidation refetch completes, the parent's onSuccess callback fires immediately, dialog closes, user can re-open before fresh data arrives.
- **React 19+ automatic batching** — `setDialogOpen(false)` + `setSomeOtherState(...)` may batch differently than expected; race symptoms can shift between line 79 (close timing) and line 89 (re-open data) depending on batch grouping.
- **Operator list query staleTime** — if the cached list has `staleTime > 0`, a fresh `useOperatorsList` mount during dialog re-open might return cached data even if invalidated; the refetch may not block the render.
- **Multiple consecutive Save+re-open cycles** — the test does ONE Save + ONE re-open. Real users may do multiple cycles in succession; the fix should handle N cycles, not just N=1.

# Failure Scenarios

- **AC-1 fails after 3 dispatches** — race persists; either the fix didn't address the root cause OR a sibling timing issue surfaces. Re-enter diagnostic; do NOT just bump Playwright timeouts.
- **AC-2/3 pass with `--retries=0` BUT only intermittently** — the fix narrows but doesn't close the race window. Escalate to UI-side blocking (hypothesis 2 — disable targetButton while refetching).

---

# Test Requirements

- Existing vitest cases on `OperatorProfileEditDialog` + `useSetOperatorProfile` continue to pass.
- Add 1 new vitest case asserting the dialog's `initialDefaultAccountId` prop update propagates to the input `value` (covers hypothesis 3's `useEffect` deps gap).
- AC-1 verification = 3 consecutive `workflow_dispatch` on impl branch (each ≤ 30 min). Cumulative ~90 min dispatch budget for AC-1.

---

# Definition of Done

- [ ] Three PRs landed in order: spec PR (this), impl PR, close-chore PR.
- [ ] AC-1 through AC-11 all checked off in close-chore PR description.
- [ ] 3/3 PASS rate empirically established before close chore start.

---

# 메타 (intended)

① **13th cycle layer in TASK-MONO-014 chain** — BE-312's "TERMINAL" status was premature; this layer surfaced via the auto-trigger nightly fired right after BE-312's close chore #794 merge. The "TERMINAL" memory entry should be revised to "TERMINAL excluding flake-class layers; PC-FE-031 = 13th = client-side race condition". Cycle pattern continues at 13-layer scale.

② **Cycle pattern handles flake-class failures as well as deterministic failures** — Layer 13 is qualitatively different from layers 1-12 (timing-sensitive, intermittent, not deterministically reproducible on first dispatch). The investigation-first pattern still applies: diagnostic instrumentation surfaces the race timing; targeted fix locks the timing. The pattern is class-agnostic.

③ **Investigation-first cycle pattern, 5th consecutive task** — PC-FE-027/028/BE-311/PC-FE-029/030/BE-312/PC-FE-031 all use the diagnostic-first → fix → cleanup shape. The pattern is now the default for cycle-pattern layers when the root cause is non-obvious.

④ **3-iter floor when failing tier has observability** (PC-FE-029 lesson) — console-web emits structured JSON logs; diagnostic instrumentation routes RQ + dialog state transitions into the docker compose log dump. Expected 3-iter (diagnostic + fix + cleanup) provided hypothesis 1 or 2 lands; 7+ iter possible if hypothesis 3 (useEffect deps) requires React-internal investigation.

⑤ **28th zero-retrofit invariant** — fix lives entirely within `projects/platform-console/apps/console-web/`. ADR-MONO-017 D4 HARD INVARIANT continued (default AC-5 says console-bff byte-unchanged; AC-6 server-side byte-unchanged).

⑥ **Memory update post-cycle** — after PC-FE-031 close, audit-memory cycle to capture: layer 13 closure, dialog-mount-vs-refetch race condition pattern, cycle pattern 13+ layer realization across 4 days, "TERMINAL" status nuance (flake class postponed but not absent).

분석=Opus 4.7 / 구현 권장=Opus 4.7 (React Query timing + useEffect deps require careful reasoning; not a mechanical fix).
