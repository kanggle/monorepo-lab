# Task ID

TASK-MONO-177

# Title

`docs/project-overview.md` reality-alignment (11th) — record, since the 10th alignment (TASK-MONO-172, 2026-06-03): the **erp read-model-service going LIVE** (TASK-ERP-BE-007 — corrects the §2.8 factual error that still lists `read-model-service` as v2-deferred) + console integrated read card (PC-FE-049), the **org_scope data-scope 3-stage authz chain** (BE-336 delegated `erp.write` scope / BE-337 assume-tenant `["*"]` bridge / BE-338 membership source `operator_tenant_assignment.org_scope` / ERP-BE-008 erp subtree consume / BE-339 admin management API / PC-FE-050 console setting UI), console erp masters write parity (PC-FE-046~048), operators/audit active-tenant scoping (MONO-175 / PC-FE-043), self-service move to account (PC-FE-045), and the demo redpanda broker (MONO-174).

# Status

done

# Owner

(docs reconcile — surgical edits to `docs/project-overview.md` only; no code, no spec, no ADR content change)

# Task Tags

<!-- api | event | deploy | code | test | adr | onboarding -->

- onboarding

---

# Dependency Markers

- **follows**: TASK-MONO-172 (10th reality-alignment, prior snapshot 2026-06-03). Periodic monorepo-level docs reconcile (11th).
- **reflects (already-merged work, no behaviour change here)**: ERP-BE-007 / ERP-BE-008, BE-336 / BE-337 / BE-338 / BE-339, PC-FE-043 / PC-FE-045 / PC-FE-046 / PC-FE-047 / PC-FE-048 / PC-FE-049 / PC-FE-050, MONO-174 / MONO-175 / MONO-176.
- **no dependency on**: any new code/spec/ADR. `docs/project-overview.md` is a human-facing snapshot (CLAUDE.md § Source of Truth: NOT read by agents as truth); this task only re-aligns it with `origin/main` reality + project memory.

---

# Goal

`docs/project-overview.md` accurately snapshots the monorepo as of 2026-06-05 — the "갱신 시점" header, §2.2 GAP (org_scope authz source), §2.6 console (erp write/read surface + org_scope setting UI + active-tenant scoping), §2.8 erp (read-model-service LIVE — corrects the v2-deferred error), §7 ADR table (ADR-020 D3 amendment), and §9 roadmap (Phase 8+ row) reflect the post-10th-alignment merged work.

# Scope

## In Scope

`docs/project-overview.md` only — 7 surgical edits:
1. **Header 갱신 시점** 2026-06-03 → 2026-06-05 + new "마지막 의미 있는 변화" clause (read-model-service LIVE + org_scope 3-stage authz chain + console erp write parity + active-tenant scoping + self-service move + demo redpanda).
2. **§2.2 GAP** — org_scope authz source/propagation note (BE-336 delegated `erp.write` scope → BE-337 assume-tenant `["*"]` bridge → BE-338 membership-derived org_scope source + BE-339 admin management API).
3. **§2.6 console** — erp masters write parity (PC-FE-046~048) + integrated read card (PC-FE-049) + org_scope setting UI (PC-FE-050) + operators/audit active-tenant scoping (MONO-175 / PC-FE-043) + self-service move to account (PC-FE-045) + demo redpanda broker (MONO-174).
4. **§2.8 erp service map** — ADD `read-model-service` row (rest-api + event-consumer; masterdata change-event consumer → employee org-view projection; ERP-BE-007).
5. **§2.8 erp v2-deferred line** — REMOVE `read-model-service (통합 조회)` from v2-deferred (it is now LIVE) + a v1.1 note (read-model-service first increment + org_scope subtree data-scope consumption, ERP-BE-007/008).
6. **§7 ADR table** — ADR-MONO-020 row: D3 amendment (2026-06-05) membership-derived org_scope (BE-338 source / BE-339 admin API / ERP-BE-008 erp consume / PC-FE-050 console UI).
7. **§9 roadmap** — Phase 8+ row: append the read-model-service LIVE + org_scope 3-stage authz chain + console erp write/read parity.

## Out of Scope

- Any code/spec/ADR change. No new ADR.
- Memory files (already updated in their respective task closures).
- The deferred items that remain deferred (approval-service, permission-service, etc.) — left as deferred; full integrated read-model view (business-partner etc.) noted as read-model v2.
- Live full-stack org_scope smoke (deferred; named as deferred, not claimed done).

# Acceptance Criteria

- [ ] **AC-1** Header "갱신 시점" = 2026-06-05 and its clause names read-model-service LIVE + org_scope 3-stage chain + console erp write parity + active-tenant scoping.
- [ ] **AC-2** §2.8 erp lists `read-model-service` in the service map and NO LONGER lists it under v2-deferred (the factual error is corrected); §2.2 GAP carries the org_scope authz source; §2.6 console carries the erp write/read/org_scope surface; §7 ADR-020 row carries the D3 amendment; §9 Phase 8+ row reflects the chain.
- [ ] **AC-3** Diff confined to `docs/project-overview.md` (+ task lifecycle). No code/spec/ADR change. Markdown links resolve.

# Related Specs

- N/A (docs snapshot). Source reality = `origin/main` git log + project memory `project_platform_console_adr_013` / `project_gap_idp_promotion` / `project_refactor_sweep_status`.

# Edge Cases

- The doc explicitly states it changes only at "명시적 갱신 시점" and that git log + the header date are the drift-resolution authority (§ trailing note) — this task is exactly such an explicit alignment.
- read-model-service is a FIRST increment (employee org-view only); the full integrated read-model view (business-partner etc.) remains read-model v2 — must not over-claim "통합 조회 완성".

# Failure Scenarios

- Over-claiming completeness: avoided — read-model-service is recorded as a first increment (v1.1), full integrated view is noted as v2; live full-stack org_scope smoke is named deferred.

# Test Requirements

- Docs-only; no build/test. Visual diff review + link sanity.

# Definition of Done

- [ ] 7 surgical edits applied to `docs/project-overview.md`.
- [ ] Diff confined; no code/spec/ADR change.
- [ ] Task md + root `tasks/INDEX.md` updated.
- [ ] Reviewed + merged (3-dim verified).

---

분석=Opus 4.8 / 구현=Opus(직접, docs reconcile). 11th reality-alignment. **메타: read-model-service 가 §2.8 에서 'v2 deferred' 로 남아있던 사실 오류(ERP-BE-007 로 이미 라이브)를 교정한 회차 — SoT 가 출하한 서비스를 미발행으로 잘못 분류하던 drift 해소.**
