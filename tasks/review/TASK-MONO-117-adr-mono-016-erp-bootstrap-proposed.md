# Task ID

TASK-MONO-117

# Title

ADR-MONO-016 작성 (PROPOSED) — erp-platform Bootstrap Criteria / Integration Mode / Classification / Procedure / Readiness (ADR-MONO-008 동형, self-ACCEPT 금지)

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

erp-platform (7번째 portfolio 프로젝트) 부트스트랩의 fresh ADR 을 **PROPOSED 상태로** 작성한다. ADR-MONO-003a §D2.1 ("새 도메인 부트스트랩 = OVERRIDE 무관, fresh ADR 필수")가 강제하는 ADR 이며, ADR-MONO-008(finance, TASK-MONO-071 이 PROPOSED 작성)·ADR-MONO-003b(Phase 5, TASK-MONO-069 이 PROPOSED 작성)와 **동형 pre-author 패턴**: 지금은 criteria/decision 문서만 PROPOSED, ACCEPTED 전환은 별 task + user-explicit intent 시점 (self-ACCEPT 절대 금지).

선행 gate 해소 확인: finance-platform v1 이 2026-05-19 **양쪽 완전 종결** (monorepo 행위-증명 chain MONO-115→FIN-BE-002→003→004 CI 12/12 + standalone Template fork CONFIRMED + TASK-MONO-116 append-only 기록, ADR-MONO-008 전 항목 해소). ADR-MONO-008 §3.2 / ADR-MONO-002 §D4 가 erp 를 finance 완전 종결 후 다음으로 지정 → 이제 ungated. (직접 트리거: 사용자 2026-05-19 AskUserQuestion "ADR-MONO-009 PROPOSED 초안 작성" 선택 — 단 번호는 아래 §Scope 의 collision 정정 적용.)

## ⚠️ ADR 번호 = ADR-MONO-016 (NOT 009 — naming collision 정정, 객관 검증됨)

옛 메모리·ADR-MONO-008 §3.2·ADR-MONO-002 §D4 의 "erp = ADR-MONO-009 candidate" 표현은 **stale**: `docs/adr/ADR-MONO-009-chrome-devtools-mcp-visual-regression.md` 가 **이미 존재** (OpenAI Harness gap #4, 기존 PROPOSED, TASK-MONO-072). `ls docs/adr/ | grep ADR-MONO` 객관 실측 = ADR-MONO-001~015 **contiguous** → erp 부트스트랩 ADR 의 정확한 번호 = **ADR-MONO-016** (다음 free). 사용자 발화의 "009" 는 stale 메모리 기반 shorthand 이며 substance(=erp 부트스트랩 PROPOSED ADR)는 불변 — 식별자만 사실 정정(scope 변경 아님).

## ⚠️ 바인딩 제약: ADR-MONO-013 (ACCEPTED) — erp = backend-only, UI=platform-console parity (자체 admin SPA 아님)

[ADR-MONO-013](../../docs/adr/ADR-MONO-013-platform-console-foundation.md) (ACCEPTED, Model B) 가 unified `platform-console` 을 portfolio enterprise suite "gap·scm·wms + **future erp**·finance" 의 **유일 UI** 로 명시한다. 따라서 7축 메모리의 옛 erp v1 "admin SPA(대시보드/결재함/마스터관리/통합조회)" framing 은 **ADR-013 에 의해 SUPERSEDED**: erp 는 자체 frontend-app 을 갖지 않고, UI = platform-console parity slice (GAP 가 ADR-013 §D2/§3.3 으로 backend-only IdP 가 된 것과 정확히 동형 — 메모리 `project_platform_console_adr_013`). ADR-MONO-016 은 이를 반드시 인코딩해야 한다 (ACCEPTED ADR 위배 = HARDSTOP-04-class 충돌; ADR-016 의 service_types 에서 frontend-app 제외, UI 책임은 platform-console parity row 로).

후속 영향: 본 task 머지 = erp 부트스트랩의 criteria/decision 문서 확보 (PROPOSED). 이후 user-explicit intent 시점에 별 task 가 ACCEPTED 전환 + 실 부트스트랩 (ADR-008→TASK-MONO-113/114 패턴). erp = portfolio 7축의 마지막(mes 는 의도적 드롭, 메모리 `project_portfolio_7axis_architecture`).

---

# Scope

## In Scope (impl PR 가 수행)

### 1. `docs/adr/ADR-MONO-016-erp-platform-bootstrap.md` 신규 작성 (Status: PROPOSED)

ADR-MONO-008 구조를 **동형 미러** (§1 Context / §2 Decision D1–D6 / §3 Consequences / §4 Alternatives / §5 Relationship / §6 Status Transition History append-only / §7 Provenance). erp 특화 내용:

- **§Status** = `PROPOSED`, History = `PROPOSED <date> (TASK-MONO-117 — bootstrap criteria pre-authored; NOT the bootstrap authorisation)`. **ACCEPTED 줄 작성 금지** (ADR-008 §1.3 reasoning 동형 — user 가 본 세션에 부트스트랩 intent 발화 안 함; criteria 문서는 결정 전 PROPOSED 가 정답).
- **D1 — Integration mode**: A(standalone-only) / B(monorepo-only) / C(both) 표 (ADR-008 D1 동형, erp 맥락으로 pros/cons/narrative 재서술). Default recommendation at ACCEPTED = Option C (finance 선례), 단 ACCEPTED 시점 user judgement.
- **D2 — Project classification**: 후보 domain = `erp` (rules/taxonomy.md L75 "Enterprise Resource Planning … 회계·구매·재고·HR 통합") — 단 7축 책임 경계상 erp 는 **도메인 비즈니스 로직 미보유**(procurement/inventory/order 는 각 서비스 소유), erp = 마스터데이터(부서/직원/직급/비용센터/거래처) + 결재 워크플로우 + 통합 read model. ADR-008 가 `fintech` 선택+ADR-SoT-over-7축-framing 한 것과 동형으로, domain=`erp` 채택 + v1 scope 를 7축 경계로 제약 명시. **Trait stack**: rules/taxonomy.md §Traits 의 11 trait 에 대해서만 선택 (ADR-008 D5.2 교훈 — taxonomy 미존재 trait 선택 시 PR-B 에서 HARDSTOP-02). ADR-MONO-002 §D4 가 erp 의 새 stress 로 "workflow-heavy + multi-module integration" 지목 → trait 후보를 taxonomy 와 대조해 확정(예: `transactional` + 통합 관련 trait; "workflow-heavy" 가 taxonomy trait 인지 검증, 아니면 가장 근접한 정식 trait + scope 문장으로 표현). **service_types**: `rest-api` (+ 통합 read model 이 event 구독이면 `event-consumer`); **frontend-app 제외** (위 ADR-013 바인딩 — UI=platform-console parity).
- **D3 — Initial service skeleton scope**: v1 첫 서비스 1개 후보 표 (예: `master-data-service` 부서/직원/비용센터/거래처 vs `approval-service` 결재 워크플로우). Default recommendation + 나머지 v2/deferred. Hexagonal (portfolio 표준).
- **D3.x / 명시 항목 — UI reconciliation**: erp UI = platform-console parity slice (ADR-013 §3/§D7.4 parity-checklist + ADR-015 composed overview 패턴), erp 는 backend-only. GAP backend-only 선례 인용. 이는 D-block 의 명시 결정 항목으로 박을 것 (Alternative 로 "erp 자체 admin SPA" = ADR-013 위배로 Rejected 기록).
- **D4 — Procedure**: ADR-008 §D4 동형 (pre-bootstrap / Template flow `gh repo create kanggle/erp-platform --template kanggle/project-template` / monorepo integration / Recording: §6 row + ADR-MONO-003a §3 row + ADR-MONO-002 §D4 forward-pointer + memory). classifier-blocked outward-facing op(외부 fork) 는 user-셸 hand-off + PENDING 추적 + 완료 시 append-only resolution recording (TASK-MONO-116 선례 = 메모리 `project_portfolio_7axis_architecture` 메타).
- **D5 — Readiness criteria** 표 (D5.1 `erp` domain in taxonomy 확인 / D5.2 trait stack 확정 / D5.3 첫 서비스 결정 / D5.4 integration mode / D5.5 user-explicit intent / D5.6 Template repo 무변경 informational) — ADR-008 §D5 동형.
- **D6 — ACCEPTED transition mechanics**: D6.1 user-explicit intent forms / D6.2 commit pattern (PR-A doc / PR-B artifact) / D6.3 §6 row format — ADR-008 §D6 동형.
- **§4 Alternatives**: erp skip→mes (ADR-002 §D4 ordering 위배, Rejected) / no-ADR (ADR-003a §D2.1 위배, Rejected) / erp 자체 admin SPA (ADR-013 위배, Rejected, but D-block 에 backend-only 로 기록) / ACCEPTED now (user intent 부재 → PROPOSED, ADR-008 §4.5 동형).
- **§5 Relationship** 표 (ADR-002 / ADR-003b / ADR-008 / ADR-013 / 본 ADR) + new-session reading order.
- **§6 Status Transition History** = "Append-only." + 표 헤더 + `created PROPOSED` row 1개 (Option/Classification TBD, PR backfill placeholder — ADR-008 §6 2026-05-13 row 동형).
- **§7 Provenance** + 분석/구현 모델 주석.

### 2. Forward-pointer append (append-only, 신규 결정 아님 — reality-alignment)

- **ADR-MONO-002 §D4**: 기존 scm→finance progression pointer 에 finance→erp 추가 (TASK-MONO-113 이 scm→finance pointer append 한 것과 정확히 동형; 기존 줄 byte-unchanged, 신규 줄/문구만 append).
- **ADR-MONO-008** 의 erp 언급 지점(§3.2 또는 §Related/§5): "erp = ADR-MONO-009 candidate" stale 표현이 있으면 **ADR-MONO-016** 으로 정정 (009=Chrome DevTools 별건 — reality-alignment, append-only 우선; 만약 해당 ADR 이 append-only 섹션이면 정정 줄 append, 본문 rewrite 면 최소 식별자 교정 + 근거 주석). impl 이 ADR-008 실제 문구 확인 후 최소 정정.
- ADR-MONO-003a §3 audit-trail: **본 task 단계에서는 row 미추가** (그 row 는 ADR-016 이 ACCEPTED+부트스트랩될 때 — ADR-008 의 #15/#16 가 MONO-113/114 에서 추가된 것과 동형; PROPOSED 작성은 ADR-003b PROPOSED(row #13) 처럼 §3 row 1개 = "ADR-MONO-016 PROPOSED publish" Meta-policy/criteria-pre-author category 만; impl 이 ADR-003a §3 기존 패턴(#13 ADR-003b PROPOSED publish row) 확인 후 동형 1 row append, one-off, §D1 미추가).

### 3. Lifecycle

spec PR (본 task 파일 + 루트 INDEX ready) ↔ impl PR (task ready→review + ADR-MONO-016 신규 + forward-pointer append) ↔ close chore (review→done). agent memory 동기화(7축/template-strategy/MEMORY.md 의 erp= 표현 최종화)는 impl 머지 후 repo 외부.

## Out of Scope

- **ACCEPTED 전환 / 실 부트스트랩 절대 금지** — `gh repo create kanggle/erp-platform`, `projects/erp-platform/` 트리, PROJECT.md, 첫 서비스 skeleton, settings.gradle, GAP seed 등 **일체 없음**. 본 task = PROPOSED criteria 문서뿐 (ADR-008→MONO-071 이 artifact 0 였던 것과 동형).
- ADR-MONO-009 (Chrome DevTools) 파일 수정 0 (별건, 무관).
- ADR-MONO-013/014/015 (platform-console) 수정 0 — 본 ADR 이 그 ACCEPTED 결정을 **참조·준수**할 뿐, 변경하지 않음 (binding consumer).
- 코드 / `projects/` / 빌드 / CI / contract 변경 0 (doc-only governance).
- erp domain rule(`rules/domains/erp.md`) 작성 — ADR-008 의 fintech.md 가 PR-B(부트스트랩 artifact)에서 작성된 것과 동형 = ACCEPTED+부트스트랩 단계, 본 PROPOSED task 범위 아님.

---

# Acceptance Criteria

1. `docs/adr/ADR-MONO-016-erp-platform-bootstrap.md` 신규, **Status: PROPOSED** (ACCEPTED 줄/표 row 0). ADR-008 §1–§7 구조 동형, erp 특화 D1–D6 + §6 append-only 표 + `created PROPOSED` row 1개.
2. ADR 번호 = **016** (009 아님 — collision 정정; ADR-MONO-001~015 contiguous 객관 확인 후 016 채택, ADR 본문에 collision 정정 근거 1문장).
3. ADR-016 D-block 이 **ADR-MONO-013 바인딩 준수 명시**: erp = backend-only, UI=platform-console parity slice, service_types frontend-app 제외, "erp 자체 admin SPA"=§4 Rejected (ADR-013 위배). ADR-013/014/015 파일 무변경.
4. D2 trait stack 이 rules/taxonomy.md §Traits 11-trait 와 대조됨 (taxonomy 미존재 trait 미선택 — ADR-008 D5.2/HARDSTOP-02 교훈 명시).
5. Forward-pointer: ADR-MONO-002 §D4 finance→erp append (기존 줄 byte-unchanged) + ADR-MONO-008 stale "ADR-MONO-009 candidate"→016 정정 + ADR-MONO-003a §3 "ADR-016 PROPOSED publish" 1 row (ADR-003b #13 동형, one-off §D1 미추가). 전부 append/최소-정정, 기존 decision 본문 불변.
6. **self-ACCEPT 0**: 본 task 산출물 어디에도 ADR-016 ACCEPTED 선언 없음; §1.x 에 "PROPOSED ≠ '언젠가 부트스트랩', user-explicit intent 시점에 별 task 가 ACCEPT" 명기 (ADR-008 §1.3 동형).
7. doc-only: git diff = ADR-016 신규 + ADR-002/008/003a forward-pointer + (lifecycle: spec=task+INDEX / impl=task move). 코드/projects/빌드/CI 0.
8. Goal 이 트리거(finance 종결 + 사용자 AskUserQuestion 선택) + collision 정정 + ADR-013 바인딩 근거 인용 (root INDEX Review Rule).

---

# Related Specs

- [ADR-MONO-008](../../docs/adr/ADR-MONO-008-finance-platform-bootstrap.md) — **동형 미러 원본** (finance bootstrap ADR; §1–§7 구조·D1–D6·§6 append-only·PROPOSED→ACCEPTED 2단계 패턴)
- [ADR-MONO-002](../../docs/adr/ADR-MONO-002-phase-4-template-extraction-trigger.md) §D4 — ordering parent (scm→finance→**erp**→mes; finance→erp forward-pointer append 대상)
- [ADR-MONO-003a](../../docs/adr/ADR-MONO-003a-d4-override-scope-canonicalization.md) §D2.1 — 새 도메인 부트스트랩 = fresh ADR 강제 (본 ADR 이 그 fresh ADR); §3 audit-trail (PROPOSED publish row 1개, ADR-003b #13 동형)
- [ADR-MONO-013](../../docs/adr/ADR-MONO-013-platform-console-foundation.md) — **바인딩 제약** (ACCEPTED Model B, "future erp" = platform-console rendered; erp backend-only); + ADR-MONO-014/015 (console operator-auth / dashboards refine — 참조만)
- [ADR-MONO-009](../../docs/adr/ADR-MONO-009-chrome-devtools-mcp-visual-regression.md) — **번호 collision 출처** (Chrome DevTools MCP, 별건; 본 task 가 016 채택 근거)
- [TASK-MONO-116](../done/TASK-MONO-116-finance-external-fork-resolution-recording.md) — 선행 gate 해소 (finance 양쪽 완전 종결); classifier-blocked PENDING→append-only resolution recording 메타 선례
- [TASK-MONO-071] / [TASK-MONO-069] — ADR-008 / ADR-003b PROPOSED pre-author 패턴 선례 (done/)

---

# Related Contracts

- 없음 (governance ADR 작성, 외부 contract / API / event 무관).

---

# Target Service / Component

- `docs/adr/ADR-MONO-016-erp-platform-bootstrap.md` (신규)
- `docs/adr/ADR-MONO-002-*.md` §D4 (forward-pointer append)
- `docs/adr/ADR-MONO-008-*.md` (stale "009 candidate"→016 최소 정정)
- `docs/adr/ADR-MONO-003a-*.md` §3 (PROPOSED publish row 1개 append)
- (no production / project / build / CI change)

---

# Edge Cases

1. **번호 재-collision**: impl 직전 `ls docs/adr/ | grep -oE 'ADR-MONO-[0-9]+'` 재실측 — 016 이 여전히 free 인지 확인 (다른 세션이 016 선점 가능성). 점유 시 다음 free + ADR 본문 근거 갱신.
2. **trait taxonomy 불일치**: "workflow-heavy" 등이 taxonomy §Traits 11-trait 에 없으면 (ADR-008 의 event-driven=HARDSTOP-02 선례) 정식 trait 만 D2 에 박고, 미존재 개념은 scope 문장으로. impl 이 taxonomy §Traits 실측 후 확정.
3. **ADR-008 의 erp 언급이 append-only 섹션**: ADR-008 §6 가 append-only — 만약 "009 candidate" 표현이 §6 안이면 rewrite 금지, 신규 줄로 정정 노트 append. impl 이 ADR-008 실제 위치 확인 후 append-only 우선 정정.
4. **ADR-002 §D4 forward-pointer 위치**: TASK-MONO-113 이 추가한 scm→finance pointer 형태를 grep 으로 찾아 동형 finance→erp 추가 (기존 L byte-unchanged, append-only). 형태 불명확 시 ADR-002 §D4 본문 read 후 최소 추가.
5. **mes 재제안 유혹**: erp 다음 mes 는 메모리 `project_portfolio_7axis_architecture` 에서 **의도적 드롭**. ADR-016 §4/§5 에 "erp = portfolio 마지막, mes 드롭" 명기 (재제안 차단).

---

# Failure Scenarios

## A. ADR-016 작성 중 ADR-013/014/015 와 모순 발견

→ ADR-013/014/015 는 ACCEPTED (binding). 모순 시 ADR-016 을 그들에 **종속**시켜 해소 (erp=backend-only/console-parity). 그래도 본질 모순이면 (예: erp 가 구조상 자체 SPA 필수) **STOP + 사용자 보고** — ADR-013 개정은 본 task scope 아님 (별 ADR-amendment task). green-wash(모순 은폐) 금지.

## B. self-ACCEPT 유혹 / user 가 "바로 ACCEPTED 로" 요청

→ ADR-008 §4.5 / §1.3 동형 reasoning 으로 PROPOSED 고수. user-explicit intent 가 명확해도 ACCEPTED 전환은 **별 task** (ADR-008→MONO-113 패턴; criteria 작성 ↔ 인가 분리가 ADR-003a/008 의 정합 근거). 본 task 산출물은 PROPOSED only.

## C. 번호/ADR-013-binding 정정에 대한 scope 확대 압력

→ collision 정정(009→016)과 ADR-013 binding 은 **사실 정정·기존 ACCEPTED 준수**일 뿐 scope 확대 아님 (사용자 intent=erp 부트스트랩 PROPOSED ADR 불변). 추가로 발견되는 별개 drift(예: 다른 문서의 "009 candidate")는 동일 reality-alignment class 면 forward-pointer 정정에 포함, 다른 class 면 STOP+별 follow-up (TASK-MONO-116 Failure Scenario C 패턴).

---

# Test Requirements

- impl PR `git diff` = ADR-016 신규 + ADR-002/008/003a 최소 forward-pointer/정정, 코드/projects/빌드/CI 0; ADR-013/014/015/009 파일 무변경 (diff 부재 실측).
- ADR-016 Status=PROPOSED 단언 (ACCEPTED 문자열 0 grep).
- ADR-016 D-block 의 ADR-013 binding(backend-only/console-parity) 명시 + taxonomy §Traits 대조 근거 포함.
- markdown lint green; §6 표 컬럼 일관; ADR-008 구조 parity (섹션 누락 0).

---

# Definition of Done

- [ ] ADR-MONO-016 신규 (PROPOSED, ADR-008 동형 §1–§7, erp D1–D6, §6 append-only + created-PROPOSED row)
- [ ] 번호 collision 정정(016, 009≠erp 근거) + ADR-013 binding(backend-only/console-parity) 인코딩
- [ ] forward-pointer: ADR-002 §D4 finance→erp + ADR-008 "009"→016 + ADR-003a §3 PROPOSED-publish row (전부 append/최소, 기존 decision 불변)
- [ ] self-ACCEPT 0 (ACCEPTED 미선언, PROPOSED-only 명기)
- [ ] doc-only diff scope 확인
- [ ] Ready for review

---

# Notes

- **Recommended impl model**: **Opus** (분석=Opus 4.7 / 구현 권장=Opus 4.7) — meta-policy ADR authoring: D1 integration-mode / D2 erp classification(7축 경계 vs taxonomy `erp` 광의 reconcile) / D3 첫 서비스 / **ADR-013 binding reconciliation** / collision 정정 = interpretive judgement 다수. ADR-008(TASK-MONO-071) "분석=Opus / 구현=Opus, meta-policy authoring" 와 동형, dispatcher 직접 작성(governance ADR 는 agent 미위임 — ADR-008/013/015 선례).
- **분량**: medium — ADR-008(~273 line) 동형 1 신규 파일 + 3 ADR forward-pointer 최소 append.
- **dependency**:
  - `선행`: TASK-MONO-116 (#612/#613/#614, main `0aa03aa7`) 머지 = finance 양쪽 완전 종결 = ADR-008 §3.2 erp gate 해소 (객관 검증됨). spec PR=본 파일만; impl PR=ADR-016+pointer.
  - `후속`: 없음(본 task = PROPOSED only). ACCEPTED 전환+실 부트스트랩 = 별 task, user-explicit intent 시점 (ADR-008→MONO-113/114 패턴). erp = 7축 마지막(mes 드롭).
- **green-wash 금지 연계**: 번호 collision(009 점유) 을 숨기지 않고 정직 정정(016) + ADR-013 binding 을 회피 않고 명시 준수 = 사용자 발화 premise 의 stale 부분을 정직 교정하되 substance(erp 부트스트랩 PROPOSED ADR) 충실 이행. ADR-013 모순 발견 시 은폐 말고 STOP(Failure Scenario A).
- **self-ACCEPT 절대 금지 (재강조)**: 본 세션/모든 세션 표준 — ADR 는 dispatcher 가 unilaterally ACCEPTED 선언 안 함. PROPOSED 작성까지가 본 task, 인가는 사용자.
