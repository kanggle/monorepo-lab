# Task ID

TASK-MONO-141

# Title

Root `docs/project-overview.md` 2026-05-28 reality-alignment sweep — BE-302/305/PC-FE-015/MONO-128 pattern 6회째 (post-Phase-5-LAUNCHED + Phase 6/7/8 closure + ADR backlog catch-up + standalone portfolio 갱신)

# Status

ready

# Owner

monorepo (root tasks/, doc-only)

# Task Tags

<!-- api | event | deploy | code | test | adr | onboarding -->

- onboarding

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

- **depends on**: nothing. All referenced reality is on `origin/main`:
  - Phase 5 LAUNCHED 2026-05-13 — ADR-MONO-003b ACCEPTED, `kanggle/project-template` public + `is_template: true` (TASK-MONO-070).
  - Phase 6 COMPLETE — finance v1 양쪽 (2026-05-19) + erp v1 양쪽 (2026-05-19/20), ADR-MONO-008/016 ACCEPTED + downstream forks CONFIRMED (TASK-MONO-116/121).
  - Phase 7 COMPLETE — console-bff skeleton + Operator Overview + Domain Health (TASK-PC-BE-001/PC-FE-011/PC-FE-013), ADR-MONO-017 ACCEPTED 2026-05-20.
  - Phase 8 MVP COMPLETE — federation hardening cross-product e2e cohort (TASK-MONO-139/140 GREEN 2026-05-26), ADR-MONO-018 ACCEPTED 2026-05-25.
  - 5축 standalone publish (2026-05-09 last batch) — wms / GAP / fan-platform / scm first publish + finance / erp Template fork CONFIRMED (MONO-116/121).
  - ADR backlog — ADR-MONO-003a/003b/004/005/006/007/008/009/010/011/012/012a/013/014/015/016/017/018 모두 `docs/adr/` 에 존재.
- **origin**: surfaced during /audit-memory 2026-05-28 — MEMORY.md index audit + `git fetch --prune` 직후 ready/+review/ 큐 TRUE 0 도달 시점에 root portfolio narrative 의 stale 누적 (last update 2026-05-09 → 19일치 milestone 미반영) 발견. MONO-128 (Phase 5/6/7 reality-alignment 5회째) 의 자연 후속 = post-Phase-8 + post-portfolio-spec-coverage-8/8 6회째 root-layer mirror.
- **prerequisite for**: nothing (this closes the root-level surface of the 6번째 reality-alignment).
- **spec-first**: spec PR (this task md + root INDEX entry, markdown fast-lane) → impl PR (surgical edits in `docs/project-overview.md`) → close chore PR.
- **no ADR** (BE-302 / BE-305 / PC-FE-015 / MONO-128 reality-alignment pattern, 6회째): every governing decision (ADR-MONO-003b / 008 / 013~018) is already ACCEPTED; every Phase 5/6/7/8 chain is merged. This task aligns the root portfolio narrative; competing convention 부재 → ADR-trigger N/A.

---

# Goal

`docs/project-overview.md` 는 monorepo **portfolio front page** — 평가자 / 협업자가 가장 먼저 읽는 monorepo 도메인 구성 + Phase 상태의 single-source snapshot. 2026-05-09 (마지막 의미 있는 변화: BE-272/273/274 closure) 이후 19일간 누적된 stale narrative drift:

| # | Line | Stale | Live reality |
|---|---|---|---|
| 1 | L4 | `갱신 시점: 2026-05-09` + "마지막 의미 있는 변화: BE-272/273/274 closure" | 2026-05-28 + post-Phase-8 / 8/8 cluster spec 정합 / sweep cycle 종결 |
| 2 | L13 | `Phase 5 (Template 추출) DEFERRED ([ADR-MONO-003]) — 30일 churn freeze 후 재평가 ≥ 2026-06-09` | Phase 5 LAUNCHED 2026-05-13 (ADR-MONO-003b ACCEPTED, TASK-MONO-070); ADR-MONO-003 D1 SUPERSEDED-on-launch |
| 3 | L216 | `Domains: wms, ecommerce, saas, fan-platform, scm` (5개) | `fintech` (finance, ADR-008) + `erp` (ADR-016) 누락 — 7 domains |
| 4 | L304-309 ADR table | ADR-MONO-001/002/003 + GAP ADR-001/003/004 만 등록 (6 rows) | ADR-MONO-003a/003b/004/005/006/007/008/009/010/011/012/012a/013/014/015/016/017/018 = 17 추가 ADR 누락 |
| 5 | L317-322 Portfolio standalone | `GAP / scm / fan-platform: monorepo-only 또는 미배포` | 2026-05-09 5축 first publish 완료 (gap/scm/fan all standalone published) + finance/erp = Template fork CONFIRMED (MONO-116/121) |
| 6 | L327-336 Roadmap | Phase 5 = `🔒 DEFERRED ... ≥ 2026-06-09 재평가`; Phase 6 = `🔮 Future Ongoing sync` | Phase 5 = `✅ LAUNCHED 2026-05-13`; Phase 6 = `✅ COMPLETE 2026-05-19/20 (finance + erp)`; new Phase 7 (console-bff LIVE 2026-05-20) + Phase 8 (federation hardening MVP 2026-05-26) rows |
| 7 | L335-338 re-eval gate | `D3 by ADR-MONO-003 ... verify-template-readiness.sh ... ADR-MONO-003 ACCEPTED 또는 ADR-MONO-003a 발행 → extract-template.sh 발사` | ADR-MONO-003 SUPERSEDED-on-launch — Phase 5 LAUNCHED via ADR-MONO-003b; sync cadence = ADR-MONO-003b § D3 |

각 edit 은 single-line or small-block, factual state update. 코드 / 테스트 / 스펙 변경 0. ADR amendment 0.

# Decision authority (why no ADR, why root tasks/, why doc-only)

- **No ADR (BE-302 / BE-305 / PC-FE-015 / MONO-128 reality-alignment pattern, 6회째)**: governing decisions (ADR-MONO-003b / 008 / 013 § D6 Phase 5/6/7/8 / 014 / 015 / 016 / 017 / 018) all ACCEPTED. This task aligns the root portfolio narrative; competing convention 부재 → ADR-trigger N/A (BE-302 메타 규칙: "reality-alignment이면 ADR 불요, convention 선택이면 ADR").
- **Why root `tasks/` (not project-internal)**: `docs/project-overview.md` 는 shared monorepo-level doc (CLAUDE.md § Repository Layout — `docs/` at repo root is shared, no project owns it). Monorepo-level work → root `tasks/` (MONO-128 precedent).
- **Why 3-PR chain**: root strict PR Separation Rule (tasks/INDEX.md § PR Separation Rule). spec / impl / close chore 각 PR 별 commit lifecycle.
- **Why scope-locked 7 areas (not exhaustive)**: 본 sweep 은 *factual state* (Phase status / domain count / ADR list / Roadmap status) update only — sibling stale narrative (e.g. minor wording drift, optional Phase 8 mention enhancement) 가 surface 하면 PR description 에 logged honestly + separate task per scope (BE-302/305 discipline 답습).

---

# Scope

## In Scope

**Spec PR**:

- `tasks/ready/TASK-MONO-141-project-overview-md-2026-05-28-reality-alignment.md` — this task md.
- `tasks/INDEX.md` — ready entry.

**Impl PR (surgical edits, 1 file, doc-only)**:

- `docs/project-overview.md`:

  - **Edit 1 (L4)**: `갱신 시점: 2026-05-09 (마지막 의미 있는 변화: BE-272/273/274 closure — Cluster A 3/3 + Cluster C 5/5 + token customizer bonus = 9/8 deferred IT 회복, ADR-003/004 ACCEPTED, ADR-MONO-003 D2 ≥ 2026-06-09 갱신).` → `갱신 시점: 2026-05-28 (마지막 의미 있는 변화: Phase 5 LAUNCHED 2026-05-13 + Phase 6 finance/erp v1 양쪽 종결 2026-05-19/20 + Phase 7 console-bff LIVE 2026-05-20 + Phase 8 federation hardening MVP 2026-05-25/26 + portfolio architecture.md spec coverage 8/8 cluster 정합 2026-05-28).`
  - **Edit 2 (L13)**: `현재 단계: Phase 4 catalyst (5 프로젝트 동거) 완료, Phase 5 (Template 추출) DEFERRED ([ADR-MONO-003]) — 30일 churn freeze 후 재평가 ≥ 2026-06-09 (2026-05-09 재평가로 1-2일 시계 reset, BE-273 Phase 2 libs/java-common 변경 = D4 면제 자연 확장).` → `현재 단계: Phase 8 federation hardening MVP COMPLETE (2026-05-26). Phase 5 LAUNCHED 2026-05-13 (ADR-MONO-003b ACCEPTED, kanggle/project-template public). Phase 6 finance + erp v1 양쪽 종결 (2026-05-19/20). Phase 7 console-bff LIVE (2026-05-20). 5/5 backend domains federated via platform-console.`
  - **Edit 3 (L216)**: `Domains: wms, ecommerce, saas, fan-platform, scm` → `Domains: wms, ecommerce, saas, fan-platform, scm, fintech, erp`
  - **Edit 4 (L304-309 ADR table)**: append 17 ADR rows (ADR-MONO-003a/003b/004/005/006/007/008/009/010/011/012/012a/013/014/015/016/017/018) with Status + 핵심 결정 columns. (existing 6 rows byte-unchanged.)
  - **Edit 5 (L317-322 Portfolio table)**: update standalone repos table — wms / ecommerce / GAP / scm / fan-platform / finance / erp 모두 standalone repo 보유 (2026-05-09 batch + Template fork 2026-05-19); platform-console = monorepo-only (no standalone).
  - **Edit 6 (L327-336 Roadmap)**: Phase 5 `🔒 DEFERRED` → `✅ LAUNCHED 2026-05-13`; Phase 6 `🔮 Future` → `✅ COMPLETE 2026-05-19/20 (finance + erp)`; new Phase 7 row (`✅ LIVE 2026-05-20 platform-console federation`); new Phase 8 row (`✅ MVP COMPLETE 2026-05-26 federation hardening`).
  - **Edit 7 (L335 re-eval gate)**: `re-eval gate (D3 by ADR-MONO-003): scripts/verify-template-readiness.sh ... ADR-MONO-003 ACCEPTED 또는 ADR-MONO-003a 발행 → scripts/extract-template.sh 발사.` → `Phase 5 LAUNCH = COMPLETE (ADR-MONO-003b ACCEPTED 2026-05-13). Template sync cadence = ADR-MONO-003b § D3 (월 1회 또는 on-demand). ADR-MONO-003 SUPERSEDED-on-launch — historical reference only.`

## Out of Scope

- **Any other line in `docs/project-overview.md`** — scope-locked to the 7 edit areas above. Sibling stale (e.g. L83 "ecommerce GAP migration 향후 TASK-MONO-020" — accurate per current state, not edited; L122 platform-console Phase 8 mention enhancement — already lists Phase 7 LIVE, Phase 8 add 은 minor optional; L271 traefik routing table — service-side, scope 외) = honest log in PR description, separate task if needed.
- **Other monorepo-level docs** (`README.md`, `TEMPLATE.md`, `CLAUDE.md`, `docs/guides/*`, `platform/*.md`, `rules/*.md`, ADRs themselves) — byte-unchanged.
- **ADR amendment** — none. ADRs are sources cited by the table, not edited here.
- **Code, tests, schema, specs/, projects/** — all byte-unchanged.

# Acceptance Criteria

- **AC-1 (spec PR atomic)**: spec PR contains exactly 2 files (this task md + root `tasks/INDEX.md` entry). No production code, no spec md edits. Markdown path-filter fast-lane.
- **AC-2 (impl PR surgical, scope-locked)**: impl PR `git diff --stat origin/main` shows exactly **1 file modified** (`docs/project-overview.md`) + lifecycle moves (ready→review for task md + INDEX update). No code, no test, no other doc. ≤80 line delta across the 7 edits.
- **AC-3 (no ADR amendment)**: `git diff --stat origin/main -- docs/adr/` = **empty**.
- **AC-4 (no projects/* re-touch)**: `git diff --stat origin/main -- projects/` = **empty**.
- **AC-5 (no other root doc re-touch)**: `git diff --stat origin/main -- README.md TEMPLATE.md CLAUDE.md docs/guides/ platform/ rules/ libs/` = **empty**.
- **AC-6 (factual state only, no architectural claim)**: each edit reflects *what's already merged on origin/main* — no new architectural intent introduced. ADR list = grep `docs/adr/ADR-MONO-*.md`. Phase status = memory `project_monorepo_template_strategy.md` + ADR-MONO-013 § D6.
- **AC-7 (CI green)**: markdown fast-lane only (`changes` + `Frontend E2E smoke` pass; all Gradle / Integration jobs skipped). No code/test job triggered. BE-303 3-dim verified at close chore.

# Related Specs

- `docs/project-overview.md` § 1 한 줄 요약 + § 2.9 향후 도메인 + § 4 AI-Driven 개발 시스템 + § 7 주요 ADR / 결정 + § 8 Portfolio 배포 + § 9 향후 로드맵 — the edited sections.
- `docs/adr/ADR-MONO-003b-phase-5-launch-criteria.md` — Phase 5 LAUNCHED SoT.
- `docs/adr/ADR-MONO-008-finance-platform-bootstrap.md` — finance bootstrap.
- `docs/adr/ADR-MONO-013-platform-console-foundation.md` — Phase 5/6/7/8 roadmap SoT.
- `docs/adr/ADR-MONO-016-erp-platform-bootstrap.md` — erp bootstrap.
- `docs/adr/ADR-MONO-017-platform-console-bff-architecture.md` — Phase 7 console-bff.
- `docs/adr/ADR-MONO-018-platform-console-phase-8-federation-hardening.md` — Phase 8 federation hardening.
- `scripts/sync-portfolio.sh` `PROJECT_REMOTES` — standalone repo list SoT.

# Related Contracts

- None. This task is portfolio-narrative-doc only; no contract change.

# Edge Cases

- **Sibling stale narrative discovered during impl** — logged in PR description for separate task, not silently bundled (BE-302/305 discipline).
- **ADR amendment 유혹** (e.g. "future erp ADR" references in ADR-MONO-013 § D6 row 6 — historical-PROPOSED-time text frozen per HARDSTOP-04) — out of scope. ADRs byte-unchanged.
- **`PROJECT.md` / `projects/<name>/*` 동반 update 유혹** — out of scope (project-internal). Separate project task if stale.
- **Korean narrative consistency**: `docs/project-overview.md` 는 Korean-primary; AFTER text 도 Korean 유지.

# Failure Scenarios

- **The impl PR re-touches `docs/adr/*`** → AC-3 fail (HARDSTOP-04 immutability violation). **Reject in review.**
- **The impl PR re-touches `projects/*`** → AC-4 fail. **Reject** — sibling stale in project-internal docs is a separate project-internal task.
- **The impl PR re-touches `README.md` / `TEMPLATE.md` / `CLAUDE.md` / `docs/guides/`** → AC-5 fail. **Reject** — sibling stale in those files is a separate root task.
- **AFTER text introduces a new architectural claim** (e.g. "Phase 9 will use ...") → over-correction; AFTER text only updates *factual state* (what's merged, what remains future).
- **A reviewer requests ADR amendment for "future erp ADR" references** → reject per § Decision authority; HARDSTOP-04 immutability — additive notes only, separate staged ADR if architectural change.

# Verification

1. Spec PR diff: exactly 2 files (this task md + root INDEX entry). `git diff --stat origin/main -- docs/` is **empty** in spec PR.
2. Impl PR diff: `docs/project-overview.md` + lifecycle moves only. `git diff --stat origin/main -- projects/ docs/adr/ docs/guides/ README.md TEMPLATE.md CLAUDE.md platform/ rules/ libs/` is **empty** in impl PR.
3. Markdown fast-lane: `changes` job + `Frontend E2E smoke` pass; all Gradle / Integration / build jobs SKIPPED.
4. AC-3 / AC-4 / AC-5 grep zero.
5. AC-6 factual state — for each edit, the AFTER text grep matches in source ADR / memory.
6. Self-CI 20/20 GREEN (markdown fast-lane); BE-303 3-dim verified at close chore start.

분석=Opus 4.7 / 구현 권장=Sonnet 4.6 (BE-302/305/PC-FE-015/MONO-128 mechanical reality-alignment, 7 surgical doc edits in 1 file; no judgement beyond already-merged-fact citation) — or executed directly in this session given the well-defined AFTER text and single-file scope / 리뷰=Opus 4.7 (inline self-review + AC-2 surgical-diff + AC-3/4/5/6 scope-lock verification + BE-303 3-dim).
