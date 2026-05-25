# Task ID

TASK-MONO-138

# Title

ADR-MONO-018 PROPOSED → ACCEPTED transition + recording — `platform-console` Phase 8 Federation Hardening Architecture authorization (doc-only; ADR-014/MONO-110 + ADR-015/MONO-112 + ADR-017/MONO-126 동형 staged-child pattern)

# Status

ready

# Owner

architecture / docs

# Task Tags

- docs
- governance
- adr
- phase-8

---

# Required Sections

- Goal
- Scope (in/out)
- Acceptance Criteria
- Related Specs
- Related Contracts
- Edge Cases
- Failure Scenarios

---

# Goal

ADR-MONO-018 (`platform-console` Phase 8 Federation Hardening Architecture — Cross-Product E2E + Observability Federation + Multi-Tenant Isolation Regression — PROPOSED published via TASK-MONO-137 #832, main squash `744df6db`) 을 **ADR-MONO-018 § D8.1 + § 1.3 자체가 명기한 staged-child pattern** 절차대로 **ACCEPTED 로 전환**하고 recording 한다. 이는 임의 self-declare 가 아니라 **user-explicit intent 기반 전환**: 사용자가 직후 세션 first message 로 *"ADR-MONO-018 ACCEPTED 로 승격 진행해줘"* + *"D8.1 user-explicit intent 'ADR-018 ACCEPTED' + ACCEPTED transition 실행 (sibling: TASK-MONO-110 / -112 / -126 의 동일 패턴)"* 명시 (ambiguous form 아닌 명시 confirm form; § D8.1 *"ADR-018 ACCEPTED" / "Phase 8 federation hardening 시작" / "federation hardening ACCEPTED 승격"* 세 acceptable form 중 첫 form 직접 인용 + sibling 패턴 explicit 인용). ADR-MONO-014 PROPOSED → ACCEPTED (TASK-MONO-110) + ADR-MONO-015 PROPOSED → ACCEPTED (TASK-MONO-112) + ADR-MONO-017 PROPOSED → ACCEPTED (TASK-MONO-126) 와 **정확히 동형 staged-child transition** 이며 ADR-MONO-013 § D6 phase governance 하위.

본 task = **single doc-only PR-shape** (ADR-MONO-014/015/017 의 ACCEPTED transition 이 PR-A/PR-B 분리 없이 단일 doc-only 임을 정확 답습; ADR-016/MONO-118 만 후속 부트스트랩 artifact PR-B/MONO-119 가 있어 분리됐으나, ADR-018 의 후속 artifact = 3 execution task series (cross-product e2e cohort + observability federation impl + multi-tenant isolation regression IT cohort — § 3.3 Future-self step 2/3/4) = **모두 별 task**). 본 task 는 ADR ACCEPTED authorization 기록만; 실제 execution artifact 작성은 ADR ACCEPTED → main 후 별 task series 가 dependency-correct base 로 진행 (sibling ADR-014 → TASK-BE-298 / ADR-015 → TASK-PC-FE-005 / ADR-017 → TASK-PC-BE-001 + TASK-PC-FE-011 staged-execution 동형).

## ACCEPTED 시점 확정된 결정 (D1–D8 finalised)

ADR-MONO-018 PROPOSED 의 CHOSEN-PROPOSED direction 8 축 모두 **그대로 finalised** (변경 0; ADR-014/015/017 ACCEPTED 시점에 D-decisions 가 PROPOSED 본문 byte-unchanged 였던 것과 정확 동형):

- **D1 = root `tasks/` + new `.github/workflows/federation-hardening-e2e.yml`** (nightly cron + workflow_dispatch; § D6 row 8 verbatim location)
- **D2 = Playwright extended on PC-FE-019..031 harness** (zero new harness primitives)
- **D3 = golden path × 5 + Operator Overview + Domain Health composition** (MVP = 7 spec files; § 3.3 "zero retrofit" sixth confirmation)
- **D4 = reuse ADR-006 Vector+VictoriaMetrics + ADR-017 D7 attribution + OTel `trace_id` propagation strengthening** (no stack mutation; HARD INVARIANT — ADR-006 byte-unchanged)
- **D5 = per-domain `TenantClaimValidator` IT × 5 + console-bff D6 tenant pass-through cross-tenant deny IT** (producer-side authority preserved; ADR-017 D6 byte-unchanged)
- **D6 = single PROPOSED bundling 3 sub-axes / execution split per axis** (operational symmetry with § D6 row 8)
- **D7 = gate = Phase 7 LIVE (이미 충족) / ownership = root cross-product + project-internal isolation IT** (Sonnet cohort / Opus isolation per ADR-013 § D6 row 8)
- **D8 = user-explicit intent forms + 2-PR commit pattern** (PR-A doc-only ACCEPTED flip = 본 task / PR-B execution task series = post-ACCEPTED future)

---

# Scope

## In Scope (impl PR = doc-only)

ADR-MONO-018 § History + § 6 + § 1.3 의 staged-child pattern 정확 답습 (sibling ADR-014/MONO-110 + ADR-015/MONO-112 + ADR-017/MONO-126):

1. **`docs/adr/ADR-MONO-018-platform-console-phase-8-federation-hardening.md`**:
   - `**Status:** PROPOSED` → `**Status:** ACCEPTED`
   - `**Date:** 2026-05-25` 유지 (sibling ADR-014/015 ACCEPTED 동일 날짜 reset 패턴 — 같은 날 ACCEPTED 가능; sibling ADR-017 PROPOSED 2026-05-20 + ACCEPTED 2026-05-20 동형; 본 ADR 의 경우 PROPOSED 2026-05-25 + ACCEPTED 2026-05-26 가능 — sibling ADR-014 PROPOSED 2026-05-16 + ACCEPTED 2026-05-16 / ADR-015 PROPOSED 2026-05-16 + ACCEPTED 2026-05-16 / ADR-017 PROPOSED 2026-05-20 + ACCEPTED 2026-05-20 와 다른 형태 — PROPOSED 머지 다음 영업일 ACCEPTED transition 패턴; History/§ 6 에 actual ACCEPTED date 정확 기록 = 정직).
   - `**History:**` 에 ACCEPTED 줄 append (PROPOSED 줄 byte-unchanged; format: *"ACCEPTED 2026-05-26 (TASK-MONO-138 — user-explicit intent 'ADR-018 ACCEPTED' via first-message direct request after PROPOSED #832 squash `744df6db` main merge; D1-D8 CHOSEN-PROPOSED finalised byte-unchanged from PROPOSED; ACCEPTED 가 후속 3 execution task series (cross-product e2e cohort + observability federation impl + multi-tenant isolation regression IT cohort, § 3.3 Future-self step 2/3/4) 의 dependency-correct authorization base 임"*).
   - § 1.3 *Staged PROPOSED → ACCEPTED (history)* 부분에서 *"ACCEPTED transition will be executed as a separate post-PROPOSED task"* 서술이 ACCEPTED 시점과 정합하도록 **최소** past-tense 정합 (예: *"ACCEPTED transition WAS executed as TASK-MONO-138 ... — D1-D8 finalised byte-unchanged from PROPOSED"*); D1-D8 결정 본문 + § 2 Decision tables + § 3 Consequences + § 4 Alternatives + § 5 Relationship + § 7 Provenance 는 **byte-unchanged** (결정 변경 아님, 상태 정합만; HARDSTOP-04 + sibling ADR-014/015/017 ACCEPTED-flip 의 § 1.3 past-tense 정합 선례 동형).
   - § 6 Status Transition History 에 ACCEPTED row **append** (PROPOSED row byte-unchanged; format: *"2026-05-26 | PROPOSED → ACCEPTED | D1-D8 byte-unchanged (finalised) | 'ADR-018 ACCEPTED' via first-message direct request 2026-05-26 (sibling ADR-014/015/017 staged-child ACCEPTED 동형) | this PR (TASK-MONO-138)"*); ADR-018 § 6 에는 _PENDING_ placeholder row 가 없으므로 (PROPOSED 시점 명시 PENDING 자리표시자 미설치) ACCEPTED row 는 단순 append (sibling ADR-017 § 6 의 `_PENDING_` → resolution replace 패턴과 differs — ADR-017 은 PROPOSED PR 시 PENDING placeholder 를 미리 적었고, ADR-018 PROPOSED PR 은 PENDING placeholder 를 적지 않음 = simple append; spec § 3.3 Future-self step 1 이 placeholder 역할).
2. **`docs/adr/ADR-MONO-013-platform-console-foundation.md`**:
   - § History 에 *additive* "Additive note (2026-05-26, ADR-MONO-018 ACCEPTED)" blockquote append (count 6 → 7, ADR-017 PROPOSED→ACCEPTED 동형 패턴 — ADR-017 ACCEPTED transition 시 ADR-013 § History 미접촉이었으나, 본 ADR-018 ACCEPTED 는 *"Phase 8 federation hardening ACCEPTED — execution task series authorised"* 의미를 ADR-013 § History 에 기록 (parent ADR governing § D6 row 8 가 본 ACCEPTED 로 해소; sibling ADR-014/015 ACCEPTED 도 ADR-013 § History 미접촉이었던 것과 differs — 다만 본 ADR-018 의 § D6 row 8 closure 는 ADR-013 Phase roadmap 의 final row 종결 sigificance 가 있어 additive note 1 row append 가 정직 alignment; HARDSTOP-04 D1-D8 byte-unchanged 엄수).
   - **OR** ADR-013 byte-unchanged option (sibling ADR-014/015/017 ACCEPTED scope 정확 답습, ADR-013 § History 미접촉) — 사용자 prompt step 3 *"§ History 에 ADR-018 ACCEPTED additive note blockquote 추가 (count 6 → 7, ADR-017 PROPOSED → ACCEPTED 패턴과 동형)"* 명시 → **option 1 (additive note 1 row) 선택**. ADR-017 PROPOSED→ACCEPTED 시점에 ADR-013 § History 가 변경되었던 패턴 (count 4→5, "Phase 7 PROPOSED" note 의 후속 status flip 의미) 의 mirror — 본 ADR-018 ACCEPTED 도 동형 note. **D1-D8 byte-unchanged 엄수** (HARDSTOP-04).
   - § D5 line 76 sibling parenthetical은 PROPOSED 단계 (TASK-MONO-137 #832) 에서 이미 append 됨 → 본 ACCEPTED 단계 byte-unchanged.
3. **`tasks/INDEX.md`** ready 섹션에 본 task 한 줄 entry 등재 (이미 본 spec PR 에서 함께) → done 섹션은 후속 close-chore PR 에서.

## Out of Scope

- **ADR-MONO-003a § 3 audit-trail row append** — TASK-MONO-137 (ADR-018 PROPOSED publish) 시점에 § 3 row append 가 *intentionally omitted* (sibling MONO-137 #832 commit diff 객관 확인: 3 files = ADR-013 + ADR-018 + task md only; ADR-003a 미수정). 본 ACCEPTED 단계도 동일 symmetry 유지 (PROPOSED 가 row 미추가했으므로 ACCEPTED 도 미추가 = audit-trail row 의 symmetric 누락 보존; sibling ADR-014/015 도 PROPOSED + ACCEPTED 시점 ADR-003a § 3 row 추가 안 했던 패턴과 일치 — ADR-017/MONO-125+126 만이 row #22+#23 양쪽 추가했고 그 외 staged-child 들은 모두 미추가). row 22+#23 의 staged-child precedent 가 *optional* 임을 ADR-014/015/018 의 PROPOSED+ACCEPTED 패턴이 객관 입증. 본 task = **row 미추가 = MONO-137 와 symmetric**.
- **ADR-MONO-014/015/017** 추가 amendment — 모두 staged-child sibling 의 자기-ADR 만 flip 한 것과 정확 동형. ADR-018 ACCEPTED 가 ADR-017 D6 (tenant pass-through) / D7 (per-domain fan-out attribution) 을 byte-unchanged 유지하면서 verify-only invariant 로 inherit (PROPOSED § 3.1 Hard invariants 명시).
- **ADR-MONO-002 § D4 forward-pointer** — ADR-MONO-002 은 *new project bootstrap* ordering ADR (`scm → finance → erp → mes`). ADR-018 은 *staged-child of ADR-013* (Phase 8 federation hardening), **새 project bootstrap 아님** (existing 5 backend domains + platform-console 의 verification axis). ADR-014/015/017 ACCEPTED transition 이 ADR-002 § D4 미접촉인 것과 정확 동형. ADR-002 byte-unchanged.
- **ADR-MONO-006 / 007** — D4 가 explicitly "reuse" ADR-006 (Vector + VictoriaMetrics) / ADR-007 (worktree-ephemeral topology) — *never amend*. PROPOSED § 3.1 + § 3.2 명시. 본 ACCEPTED 단계도 byte-unchanged.
- **실 execution artifact 일체**:
  - **cross-product e2e cohort** (root `tasks/`, post-this-ACCEPTED future task; § 3.3 Future-self step 2): `.github/workflows/federation-hardening-e2e.yml` + 7 Playwright spec files (5 golden-path + Operator Overview composition + Domain Health composition) + docker-compose stack wiring + nightly cron + workflow_dispatch + trace artifact upload. Sonnet 모델 권장 (ADR-013 § D6 row 8 + ADR-018 D7).
  - **observability federation impl** (root `tasks/`, post-this-ACCEPTED future task; § 3.3 Future-self step 3): OTel `trace_id` propagation strengthening at console-web SSR boundary + console-bff fan-out + per-domain downstream call layers; reuses ADR-MONO-006 stack (no stack mutation); cross-product e2e suite asserts the 7-span trace tree assembles. Sonnet 모델 권장.
  - **multi-tenant isolation regression IT cohort** (per-domain project-internal `tasks/` × 5 + platform-console-internal `tasks/` × 1, post-this-ACCEPTED future task; § 3.3 Future-self step 4): per-domain `TenantClaimValidator` IT regression (GAP admin-service + wms + scm + finance + erp) + console-bff D6 tenant pass-through cross-tenant deny IT. Opus 모델 권장 (ADR-013 § D6 row 8 *"isolation → Opus"* explicit elevation).
  - 본 PR 에 **0** (실 artifact 0; doc-only governance).
- ADR-MONO-018 D1–D8 **결정 본문 변경 금지** (Status / History / § 6 / § 1.3 past-tense 정합만 — 결정은 PROPOSED 때 확정됨, ACCEPTED 가 *finalise* 함 = byte-unchanged confirm; sibling ADR-014/015/017 ACCEPTED 동형). **본 ACCEPTED 단계에서 D-decisions 의 mechanics 가 변경되어야 한다면 그 자체가 PROPOSED 본문이 잘못 결정된 것이므로 STOP + 사용자에게 보고 = 정상 path 아님**.
- 코드 / `projects/` / 빌드 / CI / contract 변경 0 (doc-only governance).
- agent memory 동기화 = repo task 외부 (dispatcher 가 본 task close chore 후 직접; sibling MONO-126 동형).

---

# Acceptance Criteria

1. ADR-MONO-018 `Status: PROPOSED → ACCEPTED`; History ACCEPTED 줄 append (PROPOSED 줄 byte-unchanged); § 6 ACCEPTED row append (PROPOSED row byte-unchanged; user-explicit intent quote *"ADR-018 ACCEPTED"* 정확 form; ACCEPTED date = 2026-05-26 actual; PR # placeholder `(this PR)` 가 머지 후 squash hash 로 replace 가능하나 본 PR 시점에는 placeholder OK — sibling ADR-017 § 6 line 167 의 `this PR (TASK-MONO-126)` 답습).
2. ADR-MONO-018 § 1.3 등 PROPOSED-전제 서술이 ACCEPTED 와 정합 (최소 past-tense; D1–D8 결정 본문 + § 2 Decision tables + § 3 Consequences + § 4 Alternatives + § 5 Relationship + § 7 Provenance byte-unchanged — 변경 아님 정합).
3. ADR-MONO-013 § History 에 *additive* "Additive note (2026-05-26, ADR-MONO-018 ACCEPTED)" blockquote append (count 6 → 7); D1-D8 본문 + § D5 line 76 + § D6 row 8 + § D7 + § D8 byte-unchanged (HARDSTOP-04 + sibling ADR-014/015/017 ACCEPTED scope discipline; **단, ADR-013 § History note 추가는 사용자 prompt step 3 명시 — 본 ADR-018 ACCEPTED 의 ADR-013 Phase roadmap final row (Phase 8) closure significance 반영, ADR-017 ACCEPTED transition 이 ADR-013 § History 를 미접촉했던 것과 differ 함을 명시 인식. ACCEPTED transition 의 § History 영향은 ADR-017 PROPOSED → ACCEPTED 사이클의 PROPOSED 가 4→5 note 추가, ACCEPTED 가 byte-unchanged 였던 패턴 vs 본 ADR-018 의 PROPOSED 가 5→6 추가, ACCEPTED 가 6→7 추가 패턴 의 명시 사용자 결정**).
4. ADR-MONO-014/015/017/016/002 + 모든 다른 ADR byte-unchanged (HARDSTOP-04 + sibling ADR-014/015/017 ACCEPTED scope discipline).
5. ADR-MONO-006/007 byte-unchanged (D4 reuses, never amends; PROPOSED § 3.1 명시).
6. 코드 / `projects/` / `apps/` / `.github/workflows/` / `console-integration-contract.md` 변경 0 — 후속 3 execution task series 의 일.
7. Lifecycle: this task → `ready/` (spec PR 으로 author) → 본문 갱신 + `ready/ → review/` (impl PR) → `review/ → done/` (close chore PR). 루트 strict PR Separation Rule.
8. 본 task 머지 후 ADR-MONO-018 ACCEPTED, 후속 3 execution task series (cross-product e2e cohort + observability federation impl + multi-tenant isolation regression IT cohort) 의 dependency-correct authorization base 확립 (sibling ADR-014 → BE-298 / ADR-015 → PC-FE-005 / ADR-017 → PC-BE-001 + PC-FE-011 staged-execution 동형).

---

# Related Specs

> Target = monorepo root + `docs/adr/`. Governing: ADR-MONO-018 자체 (자기-staged transition) + ADR-MONO-013 § D6 Phase 8 (parent phase) + `platform/hardstop-rules.md` HARDSTOP-04 / HARDSTOP-09.

- `docs/adr/ADR-MONO-018-platform-console-phase-8-federation-hardening.md` (target — PROPOSED → ACCEPTED flip)
- `docs/adr/ADR-MONO-013-platform-console-foundation.md` § D5 / § D6 Phase 8 / § History — parent (additive note 1 row append per AC-3)
- `docs/adr/ADR-MONO-014-platform-console-operator-auth-token-exchange.md` — staged-child ACCEPTED transition precedent (TASK-MONO-110 single doc-only PR)
- `docs/adr/ADR-MONO-015-platform-console-dashboards-model.md` — staged-child ACCEPTED transition precedent (TASK-MONO-112 single doc-only PR)
- `docs/adr/ADR-MONO-017-platform-console-bff-architecture.md` — staged-child ACCEPTED transition precedent (TASK-MONO-126 single doc-only PR)
- `tasks/done/TASK-MONO-126-adr-mono-017-accepted-transition.md` — 가장 가까운 sibling task (직접 답습 reference)
- `tasks/done/TASK-MONO-137-adr-mono-018-phase-8-federation-hardening-proposed.md` — 직전 단계 (PROPOSED authoring)

# Related Skills

- `.claude/skills/` — N/A (ADR governance authoring, dispatcher-direct; agent 미위임, sibling ADR-014/015/017 ACCEPTED 동형 — Edit tool 로 정확 string replace + byte-unchanged 검증).

---

# Related Contracts

- **None changed** (doc-only ADR + § History note append; PROPOSED 단계의 contract scope 정의는 그대로, ACCEPTED 는 *finalise* 만).
- **Cross-referenced** (post-ACCEPTED future scope): `.github/workflows/federation-hardening-e2e.yml` (cross-product e2e cohort task 가 새 파일 추가) + 7 Playwright spec files (cohort task) + OTel `trace_id` propagation code (observability federation impl task) + per-domain `TenantClaimValidator` IT regression × 5 (isolation regression IT cohort task) + console-bff D6 tenant pass-through cross-tenant deny IT (cohort task).

---

# Target Service

- N/A (ADR governance, doc-only — monorepo-level).
- Future scope (post-this-ACCEPTED): cross-product e2e cohort (root `.github/workflows/`) + observability federation impl (console-web SSR + console-bff + per-domain downstream) + isolation regression IT (5 producers + 1 console-bff).

---

# Architecture

- ADR-MONO-018 PROPOSED 의 D1-D8 CHOSEN-PROPOSED direction 이 ACCEPTED 시점에 **byte-unchanged finalised**. 결정 본문 변경 0; 상태 (Status / History / § 6 / § 1.3 past-tense) 정합만.
- staged-child pattern (ADR-MONO-014/015/017 sibling): PROPOSED scopes architecture; ACCEPTED authorizes execution; post-ACCEPTED execution task series 가 별 task. 본 ACCEPTED 가 그 authorization base 를 확립.

---

# Implementation Notes

- **dispatcher-direct authoring** (agent 미위임) — sibling ADR-014/015/017 ACCEPTED transition 이 모두 dispatcher 직접 수행. ADR governance precision 이 결정적 (Edit tool 로 정확 string replace; sibling ADR-017 ACCEPTED transition 의 정확 form 답습).
- **HARDSTOP-04 discipline**: ADR-013 D1-D8 byte-unchanged; ADR-017 D1-D8 byte-unchanged; ADR-018 D1-D8 byte-unchanged; § 1.3 past-tense 정합만 = 결정 변경 아님 정합. `git diff` 가 객관적으로 검증.
- **append-only**: § History PROPOSED 줄 byte-unchanged + ACCEPTED 줄 append; § 6 PROPOSED row byte-unchanged + ACCEPTED row append (ADR-018 § 6 에는 _PENDING_ placeholder row 가 없으므로 simple append; sibling ADR-017 § 6 의 `_PENDING_` placeholder → resolution row replace 패턴과 differs); ADR-013 § History additive note 1 row append (count 6 → 7).
- **PR Separation**: root `tasks/INDEX.md` strict — spec PR (this file + INDEX) / impl PR (ADR-018 flip + ADR-013 § History note + lifecycle) / close chore PR (review→done).
- **dependency-correct base**: 후속 3 execution task series 의 base = 본 task ACCEPTED 머지된 main (sibling ADR-014 → BE-298 / ADR-015 → PC-FE-005 / ADR-017 → PC-BE-001 + PC-FE-011 staged-execution 동형).
- Branch name must NOT contain the `master` substring (CLAUDE.md § "Branch name constraint"). 본 task spec PR branch = `chore/mono-138-adr-018-accepted-spec` ✓; impl PR branch 예 = `chore/mono-138-adr-018-accepted-impl` ✓; close chore branch 예 = `chore/mono-138-adr-018-accepted-close` ✓ (모두 substring 부재).
- **BE-303 3-dim merge verification + BE-299 re-stage check 각 단계 적용** (close chore 시 `git mv` 후 Status edit + `git show :<done-path>` 객관 검증; impl PR 머지 후 `gh pr view` state=MERGED + `git log origin/main` tip = squash commit 일치 + `gh pr checks` failing=0).
- **분석 / 구현 모델**: Opus 4.7 (ADR governance precision, dispatcher-direct; sibling ADR-008/013/014/015/016/017 ACCEPTED 동형 — agent 미위임; D1-D8 byte-unchanged verification + sibling ADR pattern 답습 = mechanical 이면서 정확성 결정적).

---

# Edge Cases

1. ADR-MONO-018 § 6 에 `_PENDING_` placeholder row 가 없음 → simple append (sibling ADR-017 § 6 의 PENDING → resolution replace 패턴과 differs; ADR-018 PROPOSED 시점에 § D8 mechanics 가 *"ACCEPTED transition is executed by a separate post-PROPOSED task"* 만 명시하고 § 6 에 PENDING placeholder 자리를 미리 잡지 않음 = simple append 정상). PROPOSED row byte-unchanged 절대 보존.
2. § 1.3 (실제로는 PROPOSED 본문의 § 1.3 *"Why an ADR (HARDSTOP-09) + staged PROPOSED → ACCEPTED"*) 의 *"The ACCEPTED transition will be executed as a separate post-PROPOSED task (the next available `TASK-MONO-1xx`, doc-only) on user-explicit intent"* 문구가 ACCEPTED 시점에 자기-기록 (이 task 가 그 follow-up) → past-tense 로 *"The ACCEPTED transition WAS executed as TASK-MONO-138 ... ACCEPTED 2026-05-26"* 정합. 결정 본문 (D-decisions) 미접촉.
3. ADR-013 § History 의 "Phase 7 PROPOSED → ACCEPTED" 사이의 패턴 (ADR-017 PROPOSED 가 count 4→5 추가, ADR-017 ACCEPTED 가 ADR-013 byte-unchanged) 과 본 ADR-018 의 패턴 (ADR-018 PROPOSED 가 count 5→6 추가, ADR-018 ACCEPTED 가 6→7 추가) 의 differ 한 명시 사용자 결정 (사용자 prompt step 3) — sibling ADR-014/015/017 ACCEPTED 의 ADR-013 § History 미접촉 precedent 와 differs; 본 ADR-018 의 ADR-013 Phase roadmap final row 종결 significance 반영. **D1-D8 byte-unchanged 엄수** (HARDSTOP-04) — § History 의 additive note 만 추가.
4. ADR-MONO-002 § D4 forward-pointer 갱신 검토: ADR-018 = staged-child of ADR-013 (Phase 8 federation hardening), **새 project bootstrap 아님** (existing 5 backend domains + platform-console 의 verification axis) → ADR-002 § D4 (`scm → finance → erp → mes`) 미접촉. sibling ADR-014/015/017 ACCEPTED 가 ADR-002 § D4 미접촉인 것과 정확 동형. ADR-002 변경 0.
5. ADR-006/007 D4 reuse 명시 — D4 가 PROPOSED 본문 line 79 에서 "**NOT a new stack**" + "reuses the [ADR-MONO-006] Vector + VictoriaMetrics" + "Vector + VictoriaMetrics (+ VictoriaLogs / VictoriaTraces per the ADR-006 topology)" 명시; PROPOSED § 3.1 line 125 + § 3.2 line 133 도 "**ADR-MONO-006 observability stack — NOT amended by this ADR**" 명시 → ACCEPTED 시점에도 ADR-006/007 byte-unchanged 유지. (현재 ADR-MONO-006 = `lint-remediation-as-agent-context.md` filename mismatch 가 TASK-MONO-137 메타 ③ self-flag 됨; cross-ADR consistency fix-task 는 별 candidate, 본 task scope 밖.)

---

# Failure Scenarios

1. ADR-MONO-018 D1-D8 결정 본문 변경 (HARDSTOP-04 violation) → reject; ACCEPTED 는 *finalise* (byte-unchanged confirm), 결정 변경 아님. sibling ADR-014/015/017 ACCEPTED-flip 정확 패턴. **본 시나리오 발생 시 STOP + 사용자에게 보고**: PROPOSED 본문이 잘못 결정된 것이므로 정상 ACCEPTED path 아님.
2. ADR-MONO-014/015/017/016 추가 amendment → reject; PROPOSED 단계 (TASK-MONO-137) 가 ADR-013 § D5/§ History 추가 이미 완료, ACCEPTED 단계는 자기-ADR (ADR-018) 만 flip + ADR-013 § History 1 row additive note 만 추가 (sibling ADR-017 ACCEPTED 의 ADR-013 미접촉 precedent 와 명시 differ — AC-3).
3. § History PROPOSED 줄 변경 (append-only 위반) → reject; PROPOSED 줄 byte-unchanged + ACCEPTED 줄 append 만.
4. § 6 PROPOSED row 변경 (append-only 위반) → reject; PROPOSED row byte-unchanged + ACCEPTED row append 만.
5. ADR-MONO-003a § 3 row append → out-of-scope (TASK-MONO-137 PROPOSED publish 가 row 미추가 했으므로 symmetric 유지 = ACCEPTED 도 미추가).
6. 실 execution artifact (`.github/workflows/federation-hardening-e2e.yml` / Playwright spec / OTel propagation code / IT cohort 등) 가 본 PR 에 leak → reject; doc-only governance, artifact = 후속 3 execution task series 의 일.
7. user-explicit intent 미확보 → reject; 사용자 first message *"ADR-MONO-018 ACCEPTED 로 승격 진행해줘"* + *"D8.1 user-explicit intent 'ADR-018 ACCEPTED' + ACCEPTED transition 실행"* 가 § D8.1 명시 intent ("ADR-018 ACCEPTED" / "Phase 8 federation hardening 시작" / "federation hardening ACCEPTED 승격" 세 acceptable form 중 첫 form) 직접 인용 + sibling 패턴 explicit 인용 = 명시 confirm form. 본 task spec 자체가 그 confirm 의 governance recording.
8. ADR-006 / 007 / 013 D1-D8 / 017 D1-D8 byte 가 본 PR 에 변경 → reject; HARDSTOP-04 + PROPOSED § 3.1 명시 invariant 위반.

---

# Verification

- `git diff` confirms: only `docs/adr/ADR-MONO-018-...md` (Status / History / § 6 / § 1.3 minimal flip) + `docs/adr/ADR-MONO-013-...md` (§ History additive note 1 row append) + root `tasks/INDEX.md` + task lifecycle file changed.
- ADR-MONO-018 § 1 (Context) + § 2 (Decision D1-D8 tables) + § 3 (Consequences) + § 4 (Alternatives) + § 5 (Relationship) + § 7 (Provenance) byte-identical.
- ADR-MONO-013 § D5 / § D6 / § D7 / § D8 / D1-D8 body byte-identical; § History 의 prior 6 additive notes byte-identical; ADR-018 ACCEPTED note 1 row append.
- ADR-MONO-014/015/017/016/002/006/007 + 모든 다른 ADR byte-identical.
- ADR-MONO-003a § D1 + § 3 byte-unchanged (out-of-scope per § Scope / AC-5 of Failure Scenarios).
- No code/build/test impact (doc-only); CI markdown fast-lane expected (sibling MONO-126 markdown-only `changes` PASS + 19 SKIPPED 패턴 예측).

---

# Definition of Done

- [ ] ADR-MONO-018 Status `PROPOSED → ACCEPTED`; History ACCEPTED 줄 append (PROPOSED 줄 byte-unchanged); § 6 ACCEPTED row append (PROPOSED row byte-unchanged); § 1.3 minimal past-tense 정합 (D1-D8 본문 byte-unchanged).
- [ ] ADR-MONO-013 § History "Additive note (2026-05-26, ADR-MONO-018 ACCEPTED)" blockquote append (count 6 → 7); D1-D8 본문 + § D5 / § D6 / § D7 / § D8 byte-unchanged (HARDSTOP-04).
- [ ] ADR-MONO-014/015/017/016/006/007/002 + 모든 다른 ADR byte-unchanged (HARDSTOP-04).
- [ ] 코드 / `projects/` / `apps/` / `.github/workflows/` / `console-integration-contract.md` 변경 0 (후속 3 execution task series 의 일).
- [ ] Lifecycle ready → review → done (3-PR sequence per root strict PR Separation Rule).
- [ ] Cross-references resolve.
- [ ] Ready for review

---

# Notes

- **Recommended impl model**: **Opus 4.7** (분석=Opus 4.7 / 구현 권장=Opus 4.7) — ADR governance precision, dispatcher-direct authoring (sibling ADR-008/013/014/015/016/017 ACCEPTED 동형 — agent 미위임).
- **분량**: small — ADR-018 ~5-10 line edit (Status / History / § 6 / § 1.3) + ADR-013 § History 1 row append + task lifecycle/INDEX.
- **dependency**: 선행 = ADR-018 PROPOSED merged (TASK-MONO-137 #832 main `744df6db`, gate 충족 2026-05-25). 후속 = 3 execution task series (cross-product e2e cohort + observability federation impl + multi-tenant isolation regression IT cohort) — 각 root + project-internal future tasks, dependency-correct base = 본 task ACCEPTED 머지된 main.
- **PR Separation**: root `tasks/INDEX.md` strict — spec PR / impl PR / close chore distinct.
- **user-explicit intent provenance**: 사용자 first message 2026-05-26 *"ADR-MONO-018 ACCEPTED 로 승격 진행해줘"* + *"D8.1 user-explicit intent 'ADR-018 ACCEPTED' + ACCEPTED transition 실행 (sibling: TASK-MONO-110 / -112 / -126 의 동일 패턴)"* — § D8.1 명시 acceptable form *"ADR-018 ACCEPTED"* 첫 form 직접 인용 + sibling 패턴 explicit 인용 = ambiguous form 아닌 명시 confirm form. sibling 명시 intent form (ADR-017 *"ADR-017 ACCEPTED"* / ADR-016 *"ADR-016 ACCEPTED"* / ADR-014 *"ACCEPTED 승격 + BE-298 착수"* / ADR-015 *"ACCEPTED 승격 + FE-005 착수"*) 정확 동형.
