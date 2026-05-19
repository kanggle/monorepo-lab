# Task ID

TASK-MONO-118

# Title

ADR-MONO-016 PROPOSED → ACCEPTED transition + recording — erp-platform 부트스트랩 authorization (PR-A, doc-only; ADR-008/TASK-MONO-113 동형)

# Status

review

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

ADR-MONO-016 (erp-platform Bootstrap, PROPOSED published via TASK-MONO-117 #615/#616/#617, main `3a307cb8`) 을 **ADR-016 §D6 가 규정한 governed transition** 절차대로 **ACCEPTED 로 전환**하고 recording 한다. 이는 임의 self-declare 가 아니라 **user-explicit intent 기반 전환** — 사용자가 `"ADR-016 ACCEPTED"` 발화 (ADR-016 §D6.1 의 명시 intent form 정확 충족; ambiguous form 아님) + 후속 AskUserQuestion 으로 **D1=Option C (Both)** / **D3=`masterdata-service`** 확정. ADR-008 PROPOSED→ACCEPTED (TASK-MONO-113, PR-A doc-only) 와 정확히 동형이며 ADR-003b (TASK-MONO-070) 선례와 같은 staged 패턴.

본 task = **PR-A (doc-only)**. 실 부트스트랩 artifact (외부 fork / `projects/erp-platform/` / 첫 서비스 skeleton / `rules/domains/erp.md` / 배선) 는 **TASK-MONO-119 (PR-B)** 가 별도 수행 (ADR-016 §D6.2; ADR-008 §D6.2 → MONO-113/114 분리 동형). PR-B 의 dependency-correct base = 본 PR-A 머지된 main (ADR ACCEPTED 가 PR-B 존재의 authorization).

## ACCEPTED 시점 확정된 결정 (D5 평가)

- **D5.4 / D1 = Option C (Both)** — Template fork `kanggle/erp-platform` + monorepo `projects/erp-platform/` direct-include (finance ADR-008 D1 선례 동형; AskUserQuestion 사용자 명시 선택).
- **D5.3 / D3 = `masterdata-service`** — 부서/직원/직급/비용센터/거래처 마스터 (Hexagonal); `approval-service` = v2 (ADR-008 ledger v2 deferral 동형).
- **D5.2 / D2 trait stack = `[internal-system, transactional, audit-heavy]`** — `rules/taxonomy.md` §Traits 11-trait 대조 완료 (모두 catalog trait; "workflow-heavy"=non-trait → 미선언, HARDSTOP-02 회피, ADR-008 D5.2 동형). domain = `erp` (taxonomy L75). service_types = `[rest-api]` (**frontend-app 제외** — ADR-013 바인딩, UI=platform-console parity slice; event-consumer 는 통합 read model 서비스 도입 시 v2). data_sensitivity = confidential (사내 인사/조직/비용 마스터).
- **D5.1** `erp` domain taxonomy L75 확인 / **D5.5** user-explicit intent = `"ADR-016 ACCEPTED"` / **D5.6** Template repo informational / **D5.7** ADR-013 바인딩 재확인 (frontend-app 제외, parity-slice 계획 — PR-B 가 platform-console parity row 실제 추가, 본 PR-A 는 ADR 기록만).

---

# Scope

## In Scope (impl PR = PR-A, doc-only)

ADR-016 §D6 + ADR-008→MONO-113 정확 동형:

1. **`docs/adr/ADR-MONO-016-erp-platform-bootstrap.md`**:
   - `**Status:** PROPOSED` → `**Status:** ACCEPTED`
   - `**History:**` 에 ACCEPTED 줄 append (PROPOSED 줄 byte-unchanged; `ACCEPTED 2026-05-19 (TASK-MONO-118 — §D6.1 user-explicit intent "ADR-016 ACCEPTED" 충족; D5.1–D5.7 평가; D1 Option C / D2 [internal-system,transactional,audit-heavy] / D3 masterdata-service; bootstrap artifact = PR-B / TASK-MONO-119)`).
   - §1.3 / §4.5 등 "PROPOSED ≠ 부트스트랩 / self-ACCEPT 금지" 서술이 ACCEPTED 시점과 정합하도록 **최소** past-tense 정합 (ADR-013/014/015 ACCEPTED flip 의 §1.3/§D6 past-tense 정합 선례 동형; D1–D6 결정 본문 불변 — 결정 변경 아님, 상태 정합만).
   - §6 Status Transition History 에 ACCEPTED row **append** (§D6.3 포맷; `2026-05-19 | ACCEPTED | C (Both) | erp / [internal-system, transactional, audit-heavy] / [rest-api] | Both | "ADR-016 ACCEPTED" (+ AskUserQuestion D1=C/D3=masterdata-service 확정) | PR-A #<n> / PR-B (TASK-MONO-119)`); 2026-05-19 created-PROPOSED row **byte-unchanged** (append-only 절대).
2. **`docs/adr/ADR-MONO-002-phase-4-template-extraction-trigger.md`** §D4 forward-pointer: 2026-05-19 finance→erp block 아래 신규 block append (erp 단계 **ACCEPTED 진행**; 다음 = mes = 의도적 드롭/deferred-final). 기존 모든 dated block byte-unchanged (append-only; ADR-008→MONO-113 의 §D4 append 동형).
3. **`docs/adr/ADR-MONO-003a-d4-override-scope-canonicalization.md`** §3 audit-trail: row #19 append — category **"New domain bootstrap"** (ADR-016 ACCEPTED transition; **one-off, §D1 미추가** — new-domain bootstrap 은 각 fresh ADR governed, recurring D4 OVERRIDE category 아님; row #15 ADR-008 ACCEPTED 동형). rows #1~#18 byte-unchanged (append-only).

## Out of Scope

- **실 부트스트랩 artifact 일체** = TASK-MONO-119 (PR-B): 외부 `kanggle/erp-platform` fork / `projects/erp-platform/` 트리 / `masterdata-service` skeleton / `rules/domains/erp.md` / `.claude/config` erp link / settings.gradle·package.json·ci.yml·sync-portfolio 배선 / GAP erp seed. 본 PR-A 에 **0**.
- ADR-016 D1–D6 **결정 본문 변경 금지** (상태/History/§6/past-tense 정합만 — 결정은 PROPOSED 때 확정됨).
- ADR-013/014/015/009 변경 0 (참조·준수만).
- 코드 / `projects/` / 빌드 / CI / contract 변경 0 (doc-only governance).
- agent memory 동기화 (7축/template-strategy/MEMORY.md) = repo task 외부 (ADR-016 §D4 step 9 / ADR-008 D4 step 20 동형; dispatcher 가 PR-B 종결 후 직접).

---

# Acceptance Criteria

1. ADR-016 `Status: PROPOSED → ACCEPTED`; History ACCEPTED 줄 append (PROPOSED 줄 불변); §6 ACCEPTED row append (D6.3 포맷, D1=C/D2/D3/intent quote 정확), created-PROPOSED row byte-unchanged.
2. §1.3/§4.5 등 PROPOSED-전제 서술이 ACCEPTED 와 정합 (최소 past-tense; D1–D6 결정 본문 byte-unchanged — 변경 아님 정합).
3. ADR-002 §D4 erp-ACCEPTED forward-pointer append (기존 block 불변); ADR-003a §3 row #19 append (New-domain-bootstrap, one-off §D1 미추가, #1~#18 불변).
4. self-declare 아님 입증: Goal/History/§6 가 user-explicit intent `"ADR-016 ACCEPTED"` + AskUserQuestion D1/D3 확정 인용 (ADR-016 §D6.1 충족 form 명시).
5. doc-only: git diff = ADR-016 + ADR-002 + ADR-003a 3 파일 (+lifecycle). 코드/projects/빌드/CI/ADR-013·14·15·09 변경 0.
6. PR-B (TASK-MONO-119) 가 본 PR-A 머지 main 을 dependency-correct base 로 함을 Notes 명시.

---

# Related Specs

- [ADR-MONO-016](../../docs/adr/ADR-MONO-016-erp-platform-bootstrap.md) — 전환 대상 (§Status/§History/§6/§D5/§D6)
- [TASK-MONO-117](../done/TASK-MONO-117-adr-mono-016-erp-bootstrap-proposed.md) — ADR-016 PROPOSED 작성 (선행, done/)
- [ADR-MONO-008](../../docs/adr/ADR-MONO-008-finance-platform-bootstrap.md) §D6 + [TASK-MONO-113](../done/TASK-MONO-113-adr-mono-008-accepted-finance-bootstrap-transition.md) — **PR-A doc-only ACCEPTED transition 정확 동형 선례**
- [ADR-MONO-002](../../docs/adr/ADR-MONO-002-phase-4-template-extraction-trigger.md) §D4 / [ADR-MONO-003a](../../docs/adr/ADR-MONO-003a-d4-override-scope-canonicalization.md) §D2.1+§3 — forward-pointer + audit-trail
- [ADR-MONO-013](../../docs/adr/ADR-MONO-013-platform-console-foundation.md) — 바인딩 (erp backend-only; D5.7 재확인 대상, 본 PR-A 무변경)
- TASK-MONO-119 — PR-B bootstrap artifact (후속, dependency = 본 PR-A 머지)

---

# Related Contracts

- 없음 (governance ADR 전환, contract 무관).

---

# Target Service / Component

- `docs/adr/ADR-MONO-016-*.md` (Status/History/§6/past-tense) · `docs/adr/ADR-MONO-002-*.md` §D4 · `docs/adr/ADR-MONO-003a-*.md` §3
- (no code / project / build / CI)

---

# Edge Cases

1. **past-tense 정합 over-reach**: §1.3/§4.5 의 PROPOSED-전제 문장은 정합 최소 수정만 — D1–D6 결정 표/본문은 byte-unchanged (ADR-013→MONO-108 의 "D1–D8 불변, §1.3/§D6 past-tense" 선례 따름). 결정 재서술 = scope 위반.
2. **§6 append-only**: created-PROPOSED row 절대 불변; ACCEPTED 는 신규 row. ADR-008 §6 2026-05-13/05-18 row 패턴 동형.
3. **ADR-002 §D4 다중 dated block**: 2026-05-13/05-18/05-19(finance→erp) block 모두 byte-unchanged; erp-ACCEPTED 는 신규 dated block.
4. **PR# backfill**: §6 ACCEPTED row 의 PR-A # 는 close chore backfill (§-sanctioned, ADR-008 §6 관례 동형).

---

# Failure Scenarios

## A. ADR-016 ACCEPTED 가 ADR-013/014/015 와 모순 노출

→ ADR-013 binding (erp backend-only) 은 ADR-016 D-block 에 이미 인코딩됨. 전환 중 신규 모순 발견 시 STOP+사용자 보고 (ADR-013 개정 = 별 ADR, 본 task scope 아님). green-wash(모순 은폐) 금지.

## B. self-ACCEPT 의심 / intent 불명확 주장

→ 사용자 `"ADR-016 ACCEPTED"` = ADR-016 §D6.1 의 명시 form 정확 일치 (ambiguous "erp는 언제?" 아님) + AskUserQuestion D1/D3 확정 = governed transition 입증 완비. ADR-008 §D6.1 / TASK-MONO-113 과 동일 근거. 의심 시 Goal/History 의 intent quote 로 resolve.

## C. PR-A/PR-B 경계 침범 압력

→ 본 task 는 PR-A doc-only. artifact(fork/skeleton/배선)가 같이 가면 ADR-016 §D6.2 / ADR-008 PR-A↔PR-B 분리 위반 + dependency-base 오류. artifact = TASK-MONO-119 전담. 침범 시 STOP.

---

# Test Requirements

- git diff = 3 ADR 파일만 (+lifecycle). ADR-013/14/15/09·코드·projects·빌드·CI 무변경 (diff 부재 실측).
- ADR-016 `Status: ACCEPTED` grep 확인 + created-PROPOSED row + History PROPOSED 줄 byte-unchanged.
- ADR-002/003a append-only (비-context 삭제 = 정합 최소 수정분만; pre-existing dated block/row byte-unchanged 실측).
- markdown lint green; §6/§3 표 컬럼 일관.

---

# Definition of Done

- [ ] ADR-016 ACCEPTED (Status/History append/§6 row append/past-tense 정합, 결정 본문 불변)
- [ ] ADR-002 §D4 erp-ACCEPTED pointer + ADR-003a §3 #19 append (one-off, pre-existing 불변)
- [ ] user-explicit intent + D1/D3 확정 인용 (self-declare 아님 입증)
- [ ] doc-only diff scope; ADR-013/14/15/09 무변경
- [ ] PR-B(MONO-119) dependency base = 본 PR-A 머지 Notes 명시
- [ ] Ready for review

---

# Notes

- **Recommended impl model**: **Opus** (분석=Opus 4.7 / 구현 권장=Opus 4.7) — governed ADR transition (D5 평가 + §D6.1 intent adjudication + append-only/past-tense 정합 규율), ADR-008/TASK-MONO-113 + ADR-013/TASK-MONO-108 선례 동형, dispatcher-direct (governance ADR agent 미위임).
- **분량**: small — 3 ADR 파일 최소 수정 (ADR-008→MONO-113 doc-only 동형).
- **dependency**: `선행` = TASK-MONO-117 (#615/#616/#617, ADR-016 PROPOSED published, main `3a307cb8`) 머지 완료. `후속` = **TASK-MONO-119 (PR-B bootstrap artifact)** — dependency-correct base = 본 PR-A 머지된 main (ADR ACCEPTED = artifact authorization; ADR-008 PR-A #593 → PR-B #595 base 동형).
- **self-ACCEPT 금지 (재확인)**: 본 전환은 dispatcher unilateral 선언 아님 — 사용자 `"ADR-016 ACCEPTED"` 명시 발화가 §D6.1 충족. dispatcher 역할 = ADR-016 §D6 절차의 mechanical 집행 + append-only/정합 규율 준수.
- agent memory (7축/template-strategy/MEMORY.md erp= 상태) 동기화 = PR-B(MONO-119) 종결 후 dispatcher 직접 (repo task 외부, ADR-016 §D4 step 9 / ADR-008 D4 step 20 동형).
