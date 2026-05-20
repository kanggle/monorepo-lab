# Task ID

TASK-MONO-126

# Title

ADR-MONO-017 PROPOSED → ACCEPTED transition + recording — `platform-console-bff` Architecture authorization (doc-only; ADR-014/MONO-110 + ADR-015/MONO-112 + ADR-016/MONO-118 동형 staged-child pattern)

# Status

done

# Owner

architecture / docs

# Task Tags

- docs
- governance
- adr

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

ADR-MONO-017 (`platform-console-bff` Architecture, Phase 7 Aggregation & Cross-Domain Dashboards — PROPOSED published via TASK-MONO-125 #662 / #663 / #664, main `2d4ffbd2`) 을 **ADR-MONO-017 자체가 명기한 staged-child pattern** 절차대로 **ACCEPTED 로 전환**하고 recording 한다. 이는 임의 self-declare 가 아니라 **user-explicit intent 기반 전환**: 사용자가 `/audit-memory` 직후 "다음 작업 추천" → AskUserQuestion 옵션 **"A — ADR-017 ACCEPTED transition (Recommended)"** 선택 (옵션 description: *"이 옵션 선택 자체가 § D6.1 user-explicit intent 'ADR-017 ACCEPTED' 로 간주되어 진행됨"* — ambiguous form 아닌 명시 confirm form). ADR-MONO-014 PROPOSED → ACCEPTED (TASK-MONO-110) + ADR-MONO-015 PROPOSED → ACCEPTED (TASK-MONO-112) + ADR-MONO-016 PROPOSED → ACCEPTED (TASK-MONO-118) 와 **정확히 동형 staged-child transition** 이며 ADR-MONO-013 §D6 phase governance 하위.

본 task = **single PR-shape (doc-only)** — ADR-MONO-014/015 의 ACCEPTED transition 이 PR-A/PR-B 분리 없이 단일 doc-only 임을 정확 답습 (ADR-016/MONO-118 은 후속 부트스트랩 artifact PR-B/MONO-119 가 있어 분리됐으나, ADR-017 의 후속 artifact = `console-bff` Spring Boot skeleton + MVP "Operator Overview" 구현 = **각자 별 task** (TASK-PC-BE-001 + TASK-PC-FE-011) 로 분리). 본 task 는 ADR ACCEPTED authorization 기록만; 실제 artifact 구현은 ADR ACCEPTED → main 후 별 task 가 dependency-correct base 로 진행.

## ACCEPTED 시점 확정된 결정 (D1–D8 finalised)

ADR-MONO-017 PROPOSED 의 CHOSEN-PROPOSED direction 8 축 모두 **그대로 finalised** (변경 0; ADR-014/015 ACCEPTED 시점에 D-decisions 가 PROPOSED 본문 byte-unchanged 였던 것과 정확 동형):

- **D1 = REST orchestrator** (smallest blast radius, zero-retrofit affirming)
- **D2 = Server-side fan-out only** (affirms ADR-013 § D5)
- **D3 = Reuse existing per-domain reads verbatim** (§ 3.3 fifth confirmation)
- **D4 = Per-domain credential rule extended verbatim** (HARD INVARIANT — FE-007..010 never retroactively redefined)
- **D5 = Per-domain circuit-breaker inherited from § 2.5**
- **D6 = `tenant_id` claim pass-through** (producer-side gate authority preserved)
- **D7 = Per-domain fan-out attribution** (reuse ADR-MONO-006 observability stack)
- **D8 = MVP = 1 "Operator Overview" cross-domain dashboard** (ADR-MONO-015 Composed-overview generalised across 5 domains)

---

# Scope

## In Scope (impl PR = doc-only)

ADR-MONO-017 § History + § 6 + § 1.3 의 staged-child pattern 정확 답습 (sibling ADR-014/MONO-110 + ADR-015/MONO-112 + ADR-016/MONO-118):

1. **`docs/adr/ADR-MONO-017-platform-console-bff-architecture.md`**:
   - `**Status:** PROPOSED` → `**Status:** ACCEPTED`
   - `**Date:** 2026-05-20` 유지 (sibling ADR-014/015 ACCEPTED 동일 날짜 reset 패턴 — 같은 날 ACCEPTED 가능; sibling ADR-014 PROPOSED 2026-05-16 + ACCEPTED 2026-05-16 동형)
   - `**History:**` 에 ACCEPTED 줄 append (PROPOSED 줄 byte-unchanged; format: *"ACCEPTED 2026-05-20 (TASK-MONO-126 — user-explicit intent 'ADR-017 ACCEPTED' via AskUserQuestion option A selection direct after /audit-memory 2026-05-20; D1-D8 CHOSEN-PROPOSED finalised byte-unchanged from PROPOSED; ACCEPTED 가 후속 TASK-PC-BE-001 console-bff Spring Boot skeleton + TASK-PC-FE-011 MVP 'Operator Overview' dashboard 의 dependency-correct authorization base 임"*).
   - § 1.3 *Staged PROPOSED → ACCEPTED (history)* 부분에서 *"ACCEPTED transition is a separate follow-up task"* 서술이 ACCEPTED 시점과 정합하도록 **최소** past-tense 정합 (예: *"ACCEPTED transition WAS executed as TASK-MONO-126 ... — D1-D8 finalised byte-unchanged from PROPOSED"*); D1-D8 결정 본문 + § 2 Decision tables + § 3 Consequences + § 4 Alternatives + § 5 Relationship + § 7 Provenance 는 **byte-unchanged** (결정 변경 아님, 상태 정합만; HARDSTOP-04 + sibling ADR-014/015 ACCEPTED-flip 의 § 1.3/§ D6 past-tense 정합 선례 동형).
   - § 6 Status Transition History 에 ACCEPTED row **append** (PROPOSED row byte-unchanged; format: *"2026-05-20 | PROPOSED → ACCEPTED | D1-D8 byte-unchanged (finalised) | "ADR-017 ACCEPTED" via AskUserQuestion option A 2026-05-20 | this PR (TASK-MONO-126)"*); `_PENDING_` row 의 "PROPOSED → ACCEPTED" 가 이 ACCEPTED row 로 대체 (PENDING 자리표시자 → 실제 row 갱신은 append-only 규약상 PENDING 줄을 ACCEPTED 사실로 *replace* 가능 — sibling ADR-016 § 6 의 동일 시점 `Both (Template fork…)` PENDING → CONFIRMED resolution row append 패턴 동형; PROPOSED row byte-unchanged 절대 보존).
2. **`docs/adr/ADR-MONO-003a-d4-override-scope-canonicalization.md`** § 3 audit-trail: row **#23** append — category **"Meta-policy"** (ADR-MONO-017 ACCEPTED transition; **one-off, § D1 미추가** — staged-child ADR ACCEPTED transition 은 각 fresh ADR governed §D6 mechanical 집행, recurring D4 OVERRIDE category 아님; sibling row #22 ADR-017 PROPOSED 동형 / row #18 ADR-016 PROPOSED → row #19 ACCEPTED 자리 / row #15 ADR-008 ACCEPTED 자리 직접 답습). rows #1~#22 byte-unchanged (append-only 절대).

## Out of Scope

- **ADR-MONO-013 추가 amendment** — § D5 parenthetical 과 § History "Phase 7 PROPOSED" Additive note 는 ADR-MONO-017 PROPOSED 단계 (TASK-MONO-125) 에서 이미 처리됨. ACCEPTED transition 은 ADR-MONO-013 을 **byte-unchanged** (sibling ADR-014/015 ACCEPTED transition 이 ADR-013 § D5 / § History 를 byte-unchanged 한 것과 동형 — PROPOSED 단계 amendment 가 final; ACCEPTED 는 자기-ADR 만 flip). ⚠️ 단, ADR-013 § History 의 "Phase 7 PROPOSED" Additive note 가 ACCEPTED 시점에 stale 해질지 정직 검토 필요: 그 note 자체가 *"PROPOSED 2026-05-20 ... ACCEPTED transition + the `console-bff` skeleton + the first cross-domain dashboard MVP implementation are **separate future tasks**"* 라고 명기하므로 **ACCEPTED transition 이 future task** 였던 자기 기록이 reality-align — note 자체는 PROPOSED publish 기록 (역사 시점), byte-unchanged 가 정직 (선례: ADR-013 § History Phase 4/5/6 notes 가 모두 그 시점 publish 기록, ACCEPTED 후 변경 0). 따라서 ADR-013 변경 0.
- **ADR-MONO-002 § D4 forward-pointer** — ADR-MONO-002 은 *new project bootstrap* ordering ADR (`scm → finance → erp → mes`). ADR-017 은 *staged-child of ADR-013* (Phase 7 console-bff aggregation), **새 project bootstrap 아님** (`platform-console` 은 ADR-013 Phase 1 에서 이미 부트스트랩 + `console-bff` 는 그 안의 새 service). ADR-014/015 ACCEPTED transition 이 ADR-002 § D4 미접촉인 것과 정확 동형. ADR-002 byte-unchanged.
- **실 부트스트랩 / 구현 artifact 일체**:
  - `apps/console-bff/` Spring Boot skeleton (Hexagonal `rest-api`) + `console-bff/architecture.md` (ADR-MONO-012 D3 canonical form) + `PROJECT.md` `service_types += rest-api` mutation (§ D5 prescription 실집행) + Traefik `console-bff.local` label + docker-compose wiring = **TASK-PC-BE-001** (platform-console project-internal, post-this-ACCEPTED future task; dependency-correct base = 본 task ACCEPTED 머지된 main, sibling ADR-014→TASK-BE-298 / ADR-015→TASK-PC-FE-005 staged-execution 패턴 동형).
  - MVP "Operator Overview" 첫 cross-domain dashboard = **TASK-PC-FE-011** (platform-console project-internal, post-skeleton future task; `console-integration-contract.md` 새 § 2.4.9 + `features/operator-overview/` + ADR-MONO-015 Composed-overview 5-도메인 일반화).
  - 본 PR 에 **0** (실 artifact 0; doc-only governance).
- ADR-MONO-017 D1–D8 **결정 본문 변경 금지** (Status / History / § 6 / § 1.3 past-tense 정합만 — 결정은 PROPOSED 때 확정됨, ACCEPTED 가 *finalise* 함 = byte-unchanged confirm; sibling ADR-014/015 ACCEPTED 동형).
- ADR-MONO-013/014/015/016 변경 0 (참조·준수만; HARDSTOP-04 discipline).
- 코드 / `projects/` / 빌드 / CI / contract 변경 0 (doc-only governance).
- agent memory 동기화 = repo task 외부 (dispatcher 가 본 task close chore 후 직접; sibling MONO-118 D4 step 동형).

---

# Acceptance Criteria

1. ADR-MONO-017 `Status: PROPOSED → ACCEPTED`; History ACCEPTED 줄 append (PROPOSED 줄 byte-unchanged); § 6 ACCEPTED row append (PROPOSED row byte-unchanged; `_PENDING_` placeholder 자리에 실제 ACCEPTED row replace = sibling ADR-016 § 6 PENDING → resolution 답습; user-explicit intent quote "ADR-017 ACCEPTED" 정확 form).
2. § 1.3 등 PROPOSED-전제 서술이 ACCEPTED 와 정합 (최소 past-tense; D1–D8 결정 본문 + § 2 Decision tables + § 3 Consequences + § 4 Alternatives + § 5 Relationship + § 7 Provenance byte-unchanged — 변경 아님 정합).
3. ADR-MONO-003a § 3 row #23 append (Meta-policy ADR-017 ACCEPTED transition, one-off § D1 미추가, rows #1~#22 byte-unchanged).
4. ADR-MONO-013/014/015/016/002 + 모든 다른 ADR byte-unchanged (HARDSTOP-04 + sibling ADR-014/015 ACCEPTED scope discipline).
5. 코드 / `projects/` / `apps/console-bff/` / `console-integration-contract.md` § 2.4.9 / `features/operator-overview/` 변경 0 — 후속 TASK-PC-BE-001 + TASK-PC-FE-011 의 일.
6. Lifecycle: this task → `ready/` (spec PR 으로 author) → 본문 갱신 + `ready/ → review/` (impl PR) → `review/ → done/` (close chore PR). 루트 strict PR Separation Rule.
7. 본 task 머지 후 ADR-MONO-017 ACCEPTED, `console-bff` skeleton (TASK-PC-BE-001) + MVP "Operator Overview" (TASK-PC-FE-011) 의 dependency-correct authorization base 확립 (sibling ADR-014 → BE-298 / ADR-015 → PC-FE-005 staged-execution 동형).

---

# Related Specs

> Target = monorepo root + `docs/adr/`. Governing: ADR-MONO-017 자체 (자기-staged transition) + ADR-MONO-013 § D6 Phase 7 (parent phase) + ADR-MONO-003a § D1.1 / § D2.1 / § 3 (audit row append) + `platform/hardstop-rules.md` HARDSTOP-04 / HARDSTOP-09.

- `docs/adr/ADR-MONO-017-platform-console-bff-architecture.md` (target — PROPOSED → ACCEPTED flip)
- `docs/adr/ADR-MONO-013-platform-console-foundation.md` § D5 / § D6 Phase 7 — parent
- `docs/adr/ADR-MONO-014-platform-console-operator-auth-token-exchange.md` — staged-child ACCEPTED transition precedent (TASK-MONO-110 single doc-only PR)
- `docs/adr/ADR-MONO-015-platform-console-dashboards-model.md` — staged-child ACCEPTED transition precedent (TASK-MONO-112 single doc-only PR)
- `docs/adr/ADR-MONO-016-erp-platform-bootstrap.md` — staged-child ACCEPTED transition precedent (TASK-MONO-118 PR-A doc-only, PR-B = MONO-119 artifact separated; ADR-017 의 후속 PR-B equivalent = TASK-PC-BE-001 + TASK-PC-FE-011 분리)
- `docs/adr/ADR-MONO-008-finance-platform-bootstrap.md` — TASK-MONO-113 ACCEPTED transition precedent (PR-A doc-only)
- `docs/adr/ADR-MONO-003a-d4-override-scope-canonicalization.md` § 3 — audit-trail (row #23 append)
- `tasks/done/TASK-MONO-118-adr-mono-016-accepted-erp-bootstrap-transition.md` — 가장 가까운 sibling task (직접 답습 reference)
- `tasks/done/TASK-MONO-125-adr-017-console-bff-proposed-authoring.md` — 직전 단계 (PROPOSED authoring)

# Related Skills

- `.claude/skills/` — N/A (ADR governance authoring, dispatcher-direct; agent 미위임, sibling ADR-008/013/014/015/016 ACCEPTED 동형).

---

# Related Contracts

- **None changed** (doc-only ADR + audit-trail row; PROPOSED 단계의 contract scope 정의는 그대로, ACCEPTED 는 *finalise* 만).
- **Cross-referenced** (post-ACCEPTED future scope): `console-integration-contract.md` (TASK-PC-FE-011 가 새 § 2.4.9 BFF composition routes 추가) + `console-bff/architecture.md` (TASK-PC-BE-001 가 새 파일 추가).

---

# Target Service

- N/A (ADR governance, doc-only — monorepo-level).
- Future scope (post-ACCEPTED): `platform-console` `apps/console-bff/` 새 service (`rest-api`) 도입 — TASK-PC-BE-001.

---

# Architecture

- ADR-MONO-017 PROPOSED 의 D1-D8 CHOSEN-PROPOSED direction 이 ACCEPTED 시점에 **byte-unchanged finalised**. 결정 본문 변경 0; 상태 (Status / History / § 6 / § 1.3 past-tense) 정합만.
- staged-child pattern (ADR-MONO-014/015 sibling, ADR-MONO-008/013/016 precedent): PROPOSED scopes architecture; ACCEPTED authorizes execution; post-ACCEPTED skeleton/build 가 별 task. 본 ACCEPTED 가 그 authorization base 를 확립.

---

# Implementation Notes

- **dispatcher-direct authoring** (agent 미위임) — sibling ADR-014/015/016 ACCEPTED transition 이 모두 dispatcher 직접 수행. ADR governance precision 이 결정적 (Edit tool 로 정확 string replace; sibling ADR-016 ACCEPTED transition 의 정확 form 답습).
- **HARDSTOP-04 discipline**: ADR-013 D1-D8 byte-unchanged; ADR-017 D1-D8 byte-unchanged; § 1.3 past-tense 정합만 = 결정 변경 아님 정합. `git diff` 가 객관적으로 검증.
- **append-only**: § History PROPOSED 줄 byte-unchanged; § 6 PROPOSED row byte-unchanged + ACCEPTED row append (단, `_PENDING_` placeholder 자리는 실제 row 로 replace 가능 — sibling ADR-016 § 6 PENDING → CONFIRMED 답습); ADR-003a § 3 rows #1~#22 byte-unchanged + row #23 append.
- **PR Separation**: root `tasks/INDEX.md` strict — spec PR (this file + INDEX) / impl PR (ADR-017 flip + ADR-003a § 3 + lifecycle) / close chore PR (review→done).
- **dependency-correct base**: 후속 TASK-PC-BE-001 + TASK-PC-FE-011 의 base = 본 task ACCEPTED 머지된 main (sibling ADR-014 → BE-298 / ADR-015 → PC-FE-005 staged-execution 동형).
- Branch name must NOT contain the `master` substring.
- **분석 / 구현 모델**: Opus 4.7 (ADR governance precision, dispatcher-direct; sibling ADR-008/013/014/015/016 ACCEPTED 동형 — agent 미위임).

---

# Edge Cases

1. ADR-MONO-017 § 6 의 `_PENDING_` placeholder row 가 그대로 남아 있어 PROPOSED row 와 정합성 깨짐 위험 → impl PR 의 § 6 갱신이 `_PENDING_` 자리를 정확한 ACCEPTED row 로 *replace* (PROPOSED row byte-unchanged 절대 보존; sibling ADR-016 § 6 의 PENDING → CONFIRMED row replace 답습). 본 task 에서 처리.
2. § 1.3 *"ACCEPTED transition is a separate follow-up task (sibling MONO-118/110/112/113 pattern)"* 문구가 ACCEPTED 시점에 자기-기록 (이 task 가 그 follow-up) → past-tense 로 *"ACCEPTED transition WAS executed as TASK-MONO-126 ... ACCEPTED 2026-05-20"* 정합. 결정 본문 (D-decisions) 미접촉.
3. ADR-013 § History 의 "Phase 7 PROPOSED" Additive note (TASK-MONO-125 가 append 함) 가 ACCEPTED 시점에 stale 해질지 검토: note 본문이 *"ACCEPTED transition + the `console-bff` skeleton + the first cross-domain dashboard MVP implementation are **separate future tasks**"* 명기 → ACCEPTED 가 future task 였던 시점 기록 = 역사 시점의 정직 사실, byte-unchanged 가 정답 (sibling ADR-013 § History Phase 4/5/6 notes 가 모두 그 시점 publish 기록, ACCEPTED 후에도 byte-unchanged 인 패턴 동형). ADR-013 변경 0.
4. ADR-MONO-002 § D4 forward-pointer 갱신 검토: ADR-017 = staged-child of ADR-013 (Phase 7 console-bff), **새 project bootstrap 아님** (`platform-console` 은 ADR-013 Phase 1 에서 이미 부트스트랩) → ADR-002 § D4 (`scm → finance → erp → mes`) 미접촉. sibling ADR-014/015 ACCEPTED 가 ADR-002 § D4 미접촉인 것과 정확 동형 (ADR-002 = new project ordering, ADR-014/015/017 = staged-child of ADR-013). ADR-002 변경 0.

---

# Failure Scenarios

1. ADR-MONO-017 D1-D8 결정 본문 변경 (HARDSTOP-04 violation) → reject; ACCEPTED 는 *finalise* (byte-unchanged confirm), 결정 변경 아님. sibling ADR-014/015 ACCEPTED-flip 정확 패턴.
2. ADR-MONO-013/014/015/016 추가 amendment → reject; PROPOSED 단계 (TASK-MONO-125) 가 ADR-013 § D5/§ History 추가 이미 완료, ACCEPTED 단계는 자기-ADR 만 flip (sibling ADR-014/015 ACCEPTED 동형).
3. § History PROPOSED 줄 변경 (append-only 위반) → reject; PROPOSED 줄 byte-unchanged + ACCEPTED 줄 append 만.
4. § 6 PROPOSED row 변경 (append-only 위반) → reject; PROPOSED row byte-unchanged + ACCEPTED row append 만 (`_PENDING_` placeholder replace 는 sibling ADR-016 답습이므로 OK).
5. ADR-MONO-003a § 3 row #23 이 "Meta-policy" 외 category 로 들어감 → reject; sibling row #22 (ADR-017 PROPOSED) + row #18 (ADR-016 PROPOSED) + row #13 (ADR-003b PROPOSED) 모두 Meta-policy; ACCEPTED transition 도 같은 category (one-off, § D1 미추가).
6. 실 artifact (`apps/console-bff/` 등) 가 본 PR 에 leak → reject; doc-only governance, artifact = 후속 task TASK-PC-BE-001 / TASK-PC-FE-011.
7. user-explicit intent 미확보 → reject; AskUserQuestion 옵션 A 선택 자체가 § D6.1 명시 intent 충족 (옵션 description 에 *"이 옵션 선택 자체가 § D6.1 user-explicit intent 'ADR-017 ACCEPTED' 로 간주되어 진행됨"* 명기). 본 task spec 자체가 그 confirm 의 governance recording.

---

# Verification

- `git diff` confirms: only `docs/adr/ADR-MONO-017-...md` (Status / History / § 6 / § 1.3 minimal flip) + `docs/adr/ADR-MONO-003a-...md` (§ 3 row #23 append) + root `tasks/INDEX.md` + task lifecycle file changed.
- ADR-MONO-017 § 1 (Context) + § 2 (Decision D1-D8 tables) + § 3 (Consequences) + § 4 (Alternatives) + § 5 (Relationship) + § 7 (Provenance) byte-identical.
- ADR-MONO-013/014/015/016 + ADR-MONO-002 + 모든 다른 ADR byte-identical.
- ADR-MONO-003a § D1 byte-unchanged; § 3 rows #1~#22 byte-unchanged; row #23 append.
- No code/build/test impact (doc-only); CI markdown fast-lane expected (sibling MONO-118 markdown-only).

---

# Definition of Done

- [ ] ADR-MONO-017 Status `PROPOSED → ACCEPTED`; History ACCEPTED 줄 append (PROPOSED 줄 byte-unchanged); § 6 ACCEPTED row append/replace (PROPOSED row byte-unchanged; `_PENDING_` placeholder → 실제 ACCEPTED row); § 1.3 minimal past-tense 정합 (D1-D8 본문 byte-unchanged).
- [ ] ADR-MONO-003a § 3 row #23 append (Meta-policy, one-off § D1 미추가; rows #1~#22 byte-unchanged).
- [ ] ADR-MONO-013/014/015/016/002 + 모든 다른 ADR byte-unchanged (HARDSTOP-04).
- [ ] 코드 / `projects/` / `apps/console-bff/` / `features/operator-overview/` / `console-integration-contract.md` 변경 0 (후속 TASK-PC-BE-001 + TASK-PC-FE-011 의 일).
- [ ] Lifecycle ready → review → done (3-PR sequence per root strict PR Separation Rule).
- [ ] Cross-references resolve.
- [ ] Ready for review

---

# Notes

- **Recommended impl model**: **Opus 4.7** (분석=Opus 4.7 / 구현 권장=Opus 4.7) — ADR governance precision, dispatcher-direct authoring (sibling ADR-008/013/014/015/016 ACCEPTED 동형 — agent 미위임).
- **분량**: small — ADR-017 ~5-10 line edit (Status / History / § 6 / § 1.3) + ADR-003a § 3 row append + task lifecycle/INDEX.
- **dependency**: 선행 = ADR-017 PROPOSED merged (TASK-MONO-125 #662/#663/#664 main `2d4ffbd2`, gate 충족 2026-05-20). 후속 = TASK-PC-BE-001 (`console-bff` Spring Boot skeleton) + TASK-PC-FE-011 (MVP "Operator Overview" dashboard) — 각 platform-console project-internal future tasks, dependency-correct base = 본 task ACCEPTED 머지된 main.
- **PR Separation**: root `tasks/INDEX.md` strict — spec PR / impl PR / close chore distinct.
- **user-explicit intent provenance**: AskUserQuestion 옵션 A 선택 2026-05-20 (`/audit-memory` 직후 "다음 작업 추천" → header "Next task" → option A "ADR-017 ACCEPTED transition (Recommended)" + description "이 옵션 선택 자체가 § D6.1 user-explicit intent 'ADR-017 ACCEPTED' 로 간주되어 진행됨"). sibling 명시 intent form (ADR-016 "ADR-016 ACCEPTED" / ADR-014 "ACCEPTED 승격 + BE-298 착수" / ADR-015 "ACCEPTED 승격 + FE-005 착수") 정확 동형.
