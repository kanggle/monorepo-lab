# Task ID

TASK-MONO-173

# Title

federation-hardening-e2e — harden the **scm leg** golden-path/switch gates so a producer-side error (the MONO-170 demo-surfaced drift class) turns the gate RED instead of slipping past. Two scm-only assertions: **(A)** `scm-golden-path.spec.ts` (SUPER_ADMIN `tenant_id='*'`) asserts the PO list actually renders (`scm-po-table` + seed `PO-E2E-001`, not the degraded panel) — catches the SCM-BE-020-class (PO-leg parse failure → whole-section degrade); **(B)** `tenant-switch-rescope.spec.ts` globex side tightens the entitled scm card from *not-forbidden* to **`data-status='ok'`** — catches the MONO-171/SCM-BE-021-class (inventory-visibility `/snapshot` 422 on the globex assumed-tenant → card degraded, which the current `not-forbidden` assertion tolerates).

# Status

review

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

- **(A)** `tests/federation-hardening-e2e/specs/scm-golden-path.spec.ts` — replace the MVP-relaxed (URL + heading only) body with: assert no error panel (`scm-degraded` / `scm-not-eligible` / `scm-forbidden` / `scm-ratelimited` absent), assert `scm-po-table` visible + `PO-E2E-001` row text visible. (The PO seed `seed-scm.sql` is `tenant_id='*'`, explicitly "minimum shape to satisfy scm-golden-path.spec.ts" — so `'*'` returns it. The page renders `ScmOpsScreen` only if PO **and** snapshot **and** staleness all returned 200; `'*'` snapshot is 0 rows → `scm-snap-empty`, still non-degraded.)
- **(B)** `tests/federation-hardening-e2e/specs/tenant-switch-rescope.spec.ts` — after the globex switch, add a scm-specific assertion: `operator-overview-card-scm` `data-status='ok'` (+ `operator-overview-card-scm-degraded` count 0). The generic `assertEntitlement(not-forbidden)` stays for the other domains; only scm is tightened to `ok`.

## Out of Scope

- The other 4 golden-paths (wms/erp/finance/gap) — same MVP-relaxation exists but only the scm leg demonstrably leaked (MONO-170). A 5-domain sweep is a separate task.
- Any production code, page, or producer change.
- Relaxing/altering the negative (forbidden) assertions — unchanged.

# Acceptance Criteria

- [x] **AC-1** `scm-golden-path.spec.ts` asserts `scm-po-table` + `PO-E2E-001` visible and the absence of every scm error panel; a PO-leg degrade (SCM-BE-020-class) would fail it.
- [x] **AC-2** `tenant-switch-rescope.spec.ts` globex side requires `operator-overview-card-scm` `data-status='ok'`; a snapshot-422 degrade (MONO-171/SCM-BE-021-class) would fail it. The acme side + all forbidden assertions are unchanged.
- [ ] **AC-3** Diff confined to the two spec files (+ task lifecycle). No production/spec/ADR change. Validated by a `workflow_dispatch` run of `federation-hardening-e2e.yml` on the task branch — suite GREEN with the tightened assertions (the legs are healthy post-MONO-171/SCM-BE-021), proving the assertions hold on the real stack (not just stricter-on-paper). — **pending validation run**

# Related Specs

- ADR-MONO-018 D3 (golden-path MVP scope — this is the deferred "degrade path / seed-row" follow-up the specs' own comments name).
- `console-integration-contract.md` § 2.4.6 (scm per-domain credential + tenant model).

# Edge Cases

- `'*'` snapshot returns 0 rows → `scm-snap-empty` (not degraded) → `ScmOpsScreen` still renders → AC-1 holds.
- globex scm card must be `ok` post-fix (MONO-171 seed + SCM-BE-021 status fix) — if a future producer regression degrades it, AC-2 turns RED (the intended behaviour).

# Failure Scenarios

- If the `workflow_dispatch` validation shows the globex scm card is legitimately `degraded` for a benign reason (not a producer error), AC-2 would over-assert — in that case fall back to asserting the scm OK summary testid (`operator-overview-card-scm-nodes`) is present, or re-scope. (To be confirmed by the validation run, not assumed.)

# Test Requirements

- The two spec edits are themselves the tests. Validation = `gh workflow run federation-hardening-e2e.yml --ref <branch>` GREEN.

# Definition of Done

- [ ] (A) scm-golden-path + (B) tenant-switch-rescope globex side hardened.
- [ ] `workflow_dispatch` federation-hardening-e2e run on the branch GREEN (assertions hold on the real stack).
- [ ] Diff confined; no production/spec/ADR change.
- [ ] Task md + root `tasks/INDEX.md` updated.
- [ ] Reviewed + merged (3-dim verified).

---

분석=Opus 4.8 / 구현=Opus(직접). TASK-MONO-171 self-flagged gap + SCM-BE-021 후속의 test-coverage 짝. **메타: golden-path MVP-relaxation(URL+heading만) + entitled-side not-forbidden(degraded 허용) = producer-side 런타임 에러를 gate 가 못 잡는 구조적 갭 — "머지+CI green ≠ 런타임 federation 정합"의 직접 원인. 데모가 sender 였던 drift 를 gate 가 sender 가 되도록 leg 별 실데이터/healthy-status 단언으로 승격.**
