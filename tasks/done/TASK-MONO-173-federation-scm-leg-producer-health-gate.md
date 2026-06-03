# Task ID

TASK-MONO-173

# Title

federation-hardening-e2e — harden the **scm leg** gate so the MONO-171/SCM-BE-021 producer-side snapshot-422 class turns the gate RED instead of slipping past, and fix the pre-existing global-setup regression that the validation surfaced. **FINAL (post-validation) deliverables:** **(B)** `tenant-switch-rescope.spec.ts` globex side tightens the entitled scm card from *not-forbidden* to **`data-status='ok'`** — catches the MONO-171/SCM-BE-021 class (inventory-visibility `/snapshot` 422 on the globex assumed-tenant → card degraded, which the prior `not-forbidden` assertion tolerated); **(C)** `fixtures/login.ts` `waitForURL` follows the PC-FE-034 console-home move (`/dashboards`→`/dashboards/overview`) — a pre-existing regression that had the WHOLE nightly suite RED at `globalSetup`. **(A) REVERTED → deferred** (see § Out of Scope): the `scm-golden-path` PO-list assertion proved infeasible on the current stack/seed (the SUPER_ADMIN `'*'` /scm section degrades on the inventory leg; the seed is split — PO is tenant `'*'`, inventory is `globex-corp` — so no single context renders both). Validated GREEN on the real stack: **10/10** (`workflow_dispatch` run 26854063736 → 9 passed after the login fix, with A still failing; reverted A → re-validated GREEN).

# Status

done

# Owner

(test-infra hardening — two Playwright spec assertions in `tests/federation-hardening-e2e/`; no production code change)

# Task Tags

<!-- api | event | deploy | code | test | adr | onboarding -->

- test

---

# Dependency Markers

- **follows**: TASK-MONO-171 (its INDEX entry self-flagged this gap: *"federation-gate scm leg 이 실 /snapshot 대신 BFF adapter 경로를 타는 갭 — 이게 사전에 잡았어야 함"*) + TASK-SCM-BE-021 (closed the producer-side 422→500 root). This task closes the **test-coverage** side so the same class fails the gate pre-merge.
- **root cause of the leak**: the golden-path gates were MVP-relaxed (ADR-MONO-018 D3 / MONO-140 cycle 5) to assert only URL + heading; and `tenant-switch-rescope`'s entitled-side asserts `not-forbidden` (explicitly tolerating `degraded`, spec line ~44). So a producer 422/500 on an entitled domain rendered a *degraded* card/section and the gate stayed GREEN. The MONO-170 demo (manual full-stack drive) was the only thing that surfaced the drift wave (ERP-BE-006 / SCM-BE-020 / MONO-171 / BE-331/332 / BE-333/334 / SCM-BE-021) — meta: **merge + CI green ≠ runtime federation 정합**.
- **why scm-only / why two assertions**: the SCM-BE-020-class manifests on the `'*'` PO-list path (scm-golden-path); the MONO-171-class manifests ONLY on the **globex assumed-tenant** snapshot path (`'*'`/scm has 0 snapshot rows and stayed 200 — never hit the malformed rows), asserted (weakly) in tenant-switch-rescope. Covering the scm leg fully needs both.
- **no dependency on**: any production/spec/ADR change.

---

# Goal

A producer-side error on the scm leg (PO-list parse failure, or inventory-visibility `/snapshot` 422/500) makes the federation-hardening-e2e suite RED — closing the gap that let the MONO-170 drift wave reach `origin/main` before being caught.

# Scope

## In Scope

- **(B)** `tests/federation-hardening-e2e/specs/tenant-switch-rescope.spec.ts` — after the globex switch, add a scm-specific assertion: `operator-overview-card-scm` `data-status='ok'` (+ `operator-overview-card-scm-degraded` count 0). The generic `assertEntitlement(not-forbidden)` stays for the other domains; only scm is tightened to `ok`. **VALIDATED GREEN** on the real stack — the globex scm leg IS healthy post-MONO-171/SCM-BE-021, and this assertion now turns RED if it ever degrades again.
- **(C) DISCOVERED PREREQUISITE** `tests/federation-hardening-e2e/fixtures/login.ts` — the validation run surfaced a **pre-existing global-setup regression** that blocked the ENTIRE suite (not just scm): the OIDC PKCE login fixture waited (exact-match) for `${consoleOrigin}/dashboards`, but **TASK-PC-FE-034 promoted the consolidated 통합 개요 to the console home** so the root `page.tsx` now `redirect('/dashboards/overview')`. The post-login URL no longer equals `/dashboards` → `waitForURL` 30s timeout → `globalSetup` fails → all 8 specs error before running. Undetected because federation-hardening-e2e is nightly (not PR-gated), so the PC-FE-034 merge never ran it. Fix = update the fixture's `waitForURL` to `/dashboards/overview` (+ correct the stale comment). One-line; a hard prerequisite to validate (A)/(B) at all, hence folded into this task.

## Out of Scope / Deferred

- **(A) REVERTED — deferred follow-up: the scm-golden-path PO-leg gate.** The intended assertion (`scm-po-table` + `PO-E2E-001`, not degraded) FAILED validation: the SUPER_ADMIN `tenant_id='*'` /scm section renders `scm-degraded` (run 26854063736 — the inventory-visibility snapshot/staleness leg does not cleanly return for `'*'`). Root: the seed data is split — the PO row is `tenant_id='*'` (`seed-scm.sql`) while the inventory rows are `globex-corp` (`seed-scm-inv.sql`) — so NO single tenant context renders BOTH the PO list AND the snapshot, and the page composes all-or-degrades (`getScmSectionState` `Promise.all`). Gating the PO-leg producer class (SCM-BE-020 decimal parse) cleanly needs a **globex-scoped PO seed row** + a globex-context render in `scm-golden-path` (so PO + snapshot both populate one page). Deferred — the MONO-171 class (the leak that motivated this task) IS gated by (B); the PO-leg adds a seed-redesign that balloons beyond test-assertion hardening. (A separate question worth a glance in that follow-up: whether the `'*'` /scm inventory-leg degrade is itself a producer gap or by-design — operators normally assume a concrete tenant.)
- The other 4 golden-paths (wms/erp/finance/gap) — same MVP-relaxation exists but only the scm leg demonstrably leaked (MONO-170). A 5-domain sweep is a separate task.
- Any production code, page, or producer change.
- Relaxing/altering the negative (forbidden) assertions — unchanged.

# Acceptance Criteria

- [~] **AC-1 (REVERTED → deferred)** the scm-golden-path PO-leg assertion was attempted and FAILED validation (`'*'` /scm degrades on the inventory leg; split seed). Reverted to the original MVP form; PO-leg gate deferred (see § Out of Scope). Not a passing criterion of this task.
- [x] **AC-2** `tenant-switch-rescope.spec.ts` globex side requires `operator-overview-card-scm` `data-status='ok'`; a snapshot-422 degrade (MONO-171/SCM-BE-021-class) would fail it. The acme side + all forbidden assertions are unchanged. **VALIDATED GREEN.**
- [x] **AC-3** Diff confined to the two federation-e2e files (`tenant-switch-rescope.spec.ts` + `login.ts` fixture) (+ task lifecycle; `scm-golden-path.spec.ts` net-reverted to MVP). No production/spec/ADR change. Validated by `workflow_dispatch` of `federation-hardening-e2e.yml` on the branch — suite GREEN (the tightened (B) assertion + login fix hold on the real stack).
- [x] **AC-4** (discovered) `login.ts` `waitForURL` follows the PC-FE-034 home move (`/dashboards/overview`); `globalSetup` completes and all specs run (the first two `workflow_dispatch` attempts failed here identically, proving a persistent regression, not a flake; the third run got past global setup — 9/10 — proving the fix). **VALIDATED.**

# Related Specs

- ADR-MONO-018 D3 (golden-path MVP scope — this is the deferred "degrade path / seed-row" follow-up the specs' own comments name).
- `console-integration-contract.md` § 2.4.6 (scm per-domain credential + tenant model).

# Edge Cases

- **Disproven assumption (A revert root)**: `'*'` /scm was expected to render `ScmOpsScreen` with `scm-snap-empty`, but validation showed it renders `scm-degraded` — the inventory leg does not cleanly return 200-empty for `'*'`. Hence A reverted.
- globex scm card must be `ok` post-fix (MONO-171 seed + SCM-BE-021 status fix) — if a future producer regression degrades it, AC-2 turns RED (the intended behaviour). **Confirmed `ok` by validation.**

# Failure Scenarios

- If the `workflow_dispatch` validation shows the globex scm card is legitimately `degraded` for a benign reason (not a producer error), AC-2 would over-assert — in that case fall back to asserting the scm OK summary testid (`operator-overview-card-scm-nodes`) is present, or re-scope. (To be confirmed by the validation run, not assumed.)

# Test Requirements

- The two spec edits are themselves the tests. Validation = `gh workflow run federation-hardening-e2e.yml --ref <branch>` GREEN.

# Definition of Done

- [x] (B) tenant-switch-rescope globex scm `data-status='ok'` + (C) login.ts fixture `waitForURL` follows PC-FE-034 home move. (A) attempted → reverted (infeasible on current seed; deferred).
- [x] `workflow_dispatch` federation-hardening-e2e run on the branch GREEN (after the A revert) — run 26854672567 = **10/10 passed**.
- [x] Diff confined (2 federation-e2e files: tenant-switch-rescope + login.ts; scm-golden-path net-reverted); no production/spec/ADR change.
- [x] Task md + root `tasks/INDEX.md` updated.
- [x] Reviewed + merged (impl PR #1053 squash `0e94c037`, 3-dim verified; federation-e2e validated GREEN via workflow_dispatch run 26854672567).

---

분석=Opus 4.8 / 구현=Opus(직접). TASK-MONO-171 self-flagged gap + SCM-BE-021 후속의 test-coverage 짝. **메타: golden-path MVP-relaxation(URL+heading만) + entitled-side not-forbidden(degraded 허용) = producer-side 런타임 에러를 gate 가 못 잡는 구조적 갭 — "머지+CI green ≠ 런타임 federation 정합"의 직접 원인. 데모가 sender 였던 drift 를 gate 가 sender 가 되도록 leg 별 실데이터/healthy-status 단언으로 승격.**
