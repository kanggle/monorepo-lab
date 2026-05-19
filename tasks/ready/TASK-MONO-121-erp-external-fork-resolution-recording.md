# Task ID

TASK-MONO-121

# Title

erp-platform 외부 Template-fork 완료 기록 — ADR-MONO-016 §6 + ADR-MONO-003a §3 append-only resolution row (Option C standalone side 확정)

# Status

ready

# Owner

devops / docs

# Task Tags

- docs
- governance

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

erp-platform 부트스트랩 (ADR-MONO-016 ACCEPTED, D1 **Option C** = Template fork + monorepo direct-include) 의 **유일 잔여 미해소 항목**인 외부 `kanggle/erp-platform` Template-fork 가 2026-05-19 사용자 셸에서 생성 완료되었다. 이를 두 append-only governance audit-trail 에 **정직하게 기록**하여 Option C 의 standalone side 를 확정한다. TASK-MONO-116 (finance 동형) 의 erp 버전이다.

PR-B (TASK-MONO-119) 머지 시점에는 이 fork 가 classifier-blocked outward-facing op 로 user 에게 hand-off 되어 **standalone side PENDING** 으로 정직하게 기록되었고 (green-wash 금지 — monorepo side 만 landed, standalone side 미확정 명기; MONO-119 task AC + close-chore one-liner #621 + agent memory 가 추적 중), [ADR-MONO-016](../../docs/adr/ADR-MONO-016-erp-platform-bootstrap.md) §6 2026-05-19 ACCEPTED row 의 `Both (Template fork kanggle/erp-platform + monorepo ...)` 가 결정(decision)으로만 기록돼 있고 standalone artifact 의 실제 생성은 미확정 상태였다. 본 task 는 그 standalone-side 미확정을 **새 row append 로 해소** (기존 row 불변 — append-only).

**객관 검증 (이미 수행, dispatcher 독립 재확인 완료, 본 task 의 기록 근거)**: `gh repo view kanggle/erp-platform --json templateRepository,visibility,isTemplate,createdAt,name,owner` →
- `templateRepository: {name: project-template, owner: kanggle}` = **Template-derived 객관 증명** (단순 repo 아님 — `kanggle/project-template` 에서 파생된 진짜 Template fork; ADR-MONO-016 D1 Option C 요건 정확 충족)
- `visibility: PUBLIC`, `isTemplate: false` (인스턴스로 올바름), `createdAt: 2026-05-19T10:01:11Z`, `owner: kanggle`, `name: erp-platform`
- 사용자 셸 `gh repo create kanggle/erp-platform --template kanggle/project-template --public --clone` 실행 (`--clone` 단계만 "Name already exists" 로 erred — repo 는 원격 이미 생성됨, 기록 대상은 원격 repo 존재이므로 무관; 부수: 중첩 클론 미생성 = 깨끗).

**이것은 신규 결정이 아니다** — 완료된 PENDING operational hand-off 의 reality-alignment 기록일 뿐이다. competing convention 부재 → **신규 ADR 불요** (ADR-MONO-003a §3 말미 "When these land, append rows here. No re-authorisation needed (covered by D1.2)." 가 명시적으로 재인가 불요를 인가; BE-290/302 메타: drift/recording-fix 의 ADR 필요 판정 = competing convention 존재 여부, 없으면 reality-alignment). TASK-MONO-116 (finance 동형, §3 row #17) 이 직전 선례.

**정직 명기 (green-wash 금지) — 본 task 가 해소하지 *않는* 것**:
- 사용자 셸의 stale remote ref prune 명령은 **실행 실패**했다 (`git -C monorepo-lab ...` 가 `C:\Users\kangdow\dev\project` 기준이라 monorepo-lab 경로 못 찾음 — `fatal: cannot change to 'monorepo-lab'`). 따라서 stale remote ref 는 **여전히 잔존** — 본 task scope 아님, 별도 user-shell 재-handoff (정확 경로). 본 task 는 fork 존재만 기록하고 ref prune 을 "완료"로 기록하지 **않는다** (MONO-116 은 ref prune 완료를 기록했으나 erp 는 미완 — 사실 그대로).

후속 영향:
- erp v1 standalone side 확정 → erp-platform = monorepo-side (부트스트랩 #619/#620/#621 + (g)(1) follow-up MONO-120 #622~#626 완전 종결) + standalone-side (Template fork) **양쪽 완전 종결**. ADR-MONO-016 의 모든 항목 해소.
- erp = portfolio 마지막 도메인 (mes 의도적 드롭) → 신규 도메인 부트스트랩 ADR 더 이상 없음. Template downstream 실사용 = finance(MONO-116) + erp(본 task) **2회 CONFIRMED** = Phase 5 "전략 설계대로 작동" 신호 2차 실증.

---

# Scope

## In Scope

### 1. `docs/adr/ADR-MONO-016-erp-platform-bootstrap.md` §6 — append-only resolution row

§6 "Status Transition History" (명시 **"Append-only."**) 의 2026-05-19 ACCEPTED row (line 222) **아래**, 기존 주석 블록 (line 224 PROPOSED-note / line 226 ACCEPTED-note) **위**에 신규 row 1개 append:

- Date `2026-05-19`, Transition `Option C standalone side CONFIRMED` (ACCEPTED 재선언 아님 — 동일 ACCEPTED 결정의 standalone artifact 완료 기록), Option `C (Both)`, Classification `(unchanged)`, Standalone/Monorepo/Both `Both — standalone CONFIRMED`, User-intent `사용자 2026-05-19 셸 옵션 A 실행 (gh repo create --template; AskUserQuestion "외부 fork 선행" 선택 연장선)`, PR(s) `<impl PR #> / close <#>`.
- Cell 본문: 객관 검증 인용 (`templateRepository=kanggle/project-template`, owner `kanggle`, PUBLIC, `isTemplate:false`, `createdAt 2026-05-19T10:01:11Z`) + "2026-05-19 ACCEPTED row 의 `Both (Template fork...)` 가 decision 으로 기록돼 있던 것의 standalone artifact 실생성 확정; 기존 row(221/222) + 주석(224/226) 불변 (append-only)" 명기.
- (선택) ACCEPTED-note (226) 형식과 일관되게, 신규 row 가 append-only 원칙을 따랐음을 1줄 보강 가능 (기존 주석 문장 불변 — 새 문장 append 만, 기존 주석 블록 뒤).

### 2. `docs/adr/ADR-MONO-003a-d4-override-scope-canonicalization.md` §3 — append-only audit-trail row #21

§3 audit-trail (L181-class "When these land, append rows here. No re-authorisation needed") 에 row #20 (TASK-MONO-120 hook-hardening) **아래** row #21 append:

- `| 21 | #<impl PR> | 2026-05-19 | <impl squash hash> | New domain bootstrap | erp-platform 외부 Template-fork CONFIRMED (TASK-MONO-121). ADR-MONO-016 D1 Option C standalone side 완료 — §6 2026-05-19 ACCEPTED row 의 \`Both (Template fork...)\` decision 의 standalone artifact 실생성 확정. \`gh repo view kanggle/erp-platform\` 객관 검증: templateRepository=kanggle/project-template, owner kanggle, PUBLIC, isTemplate false, createdAt 2026-05-19T10:01:11Z. **Same one-off category as #15/#16/#17/#19** (does NOT add to §D1 — ADR-MONO-016-governed; finance #17 동형). erp = portfolio 마지막 도메인 — 신규 도메인 부트스트랩 row 더 이상 없음. 기존 row #1~#20 본문 불변 (append-only). |`
- 번호 정합 주의: row #20 = TASK-MONO-120 (hook-hardening, D4 OVERRIDE). row #21 = 본 task (erp fork CONFIRMED, New domain bootstrap). 카테고리·의미 상이 — 번호만 순차. row #19 의 "scope out a § 3 row #20" 는 *erp PR-B artifact* 한정이었음 (이미 row #20 본문이 명시 해소); 본 row #21 은 erp **fork-CONFIRMED** 으로 또 다른 의미 — 명시 명료화.

## Out of Scope

- **기존 §6 / §3 어떤 row·주석의 본문도 수정 금지** — append-only 절대 준수 (ADR-016 §6 221/222 row + 224/226 주석, ADR-003a §3 #1~#20 모두 historical record 로 보존; 해소는 새 row 로만).
- 신규 ADR 작성 금지 (reality-alignment, 신규 결정 무 — §Goal 근거).
- **stale remote ref prune** — 사용자 셸 명령 실패 (경로 오류), 여전히 잔존. 본 task 는 이를 "완료"로 기록하지 않으며 해소하지도 않음 (별도 user-shell 재-handoff; green-wash 금지 — 미완을 완료로 적지 않음).
- production / 코드 / `projects/erp-platform/` 트리 / `settings.gradle` / `ci.yml` / `scripts/sync-portfolio.sh` 변경 0 — monorepo side 는 PR-B (MONO-119 #620) 에서 이미 landed; 외부 fork 는 external repo.
- agent memory 파일 (`project_monorepo_template_strategy.md` / `project_portfolio_7axis_architecture.md` / `MEMORY.md`) 은 repo task scope 아님 — dispatcher 가 본 task 머지 후 직접 동기화 (ADR-MONO-016 §D4 step 9 / ADR-008 D4 step 20 동형; §Notes 참조).
- `kanggle/erp-platform` 외부 repo 내부 내용/구조 검수 — Option C standalone side 의 "존재 + Template-derived" 확정이 본 task 범위; standalone repo 후속 진화는 그 repo 독립 책임 (ADR-MONO-003b §D3).

---

# Acceptance Criteria

1. ADR-MONO-016 §6 에 2026-05-19 resolution row 1개 **append** (2026-05-19 ACCEPTED row 아래·기존 주석 블록 위 또는 주석 뒤 일관 위치, 기존 모든 row·주석 본문 byte-unchanged). 객관 fork 검증 인용 포함.
2. ADR-MONO-003a §3 에 row #21 **append** (row #20 아래, 기존 #1~#20 본문 byte-unchanged). #15/#16/#17/#19 one-off category 일관, #20(hook)과 의미 구분 명기.
3. **append-only 검증**: impl PR diff 가 두 ADR 파일에서 **순수 추가 라인만** (기존 라인 삭제/수정 0 — `git diff --numstat` 가 두 파일 모두 `N 0` 형태, context 외 `-` 라인 0).
4. 신규 ADR 파일 0개 생성. ADR cross-reference (§6 신규 row ↔ §3 row #21 ↔ TASK-MONO-119 close row 의 standalone-PENDING 추적) 정합.
5. 코드/`projects/`/빌드/CI/sync-portfolio 변경 0 (doc-only). git diff scope = 정확히 2 ADR 파일 (+ lifecycle: spec PR=task+INDEX, impl PR=task move).
6. stale remote ref prune 을 "완료"로 기록하지 **않음** (사실 미완 — green-wash 금지). Goal 이 이 정직 단서를 명기.
7. Lifecycle: spec PR (본 파일 + 루트 INDEX ready) ↔ impl PR (task move + 2 ADR append) 분리 (PR Separation Rule). close chore (review→done) 는 impl PR 머지 후.

---

# Related Specs

- [ADR-MONO-016](../../docs/adr/ADR-MONO-016-erp-platform-bootstrap.md) §6 (append 대상 #1) + §D1 Option C + §D4 step 9 (memory) + §D6
- [ADR-MONO-003a](../../docs/adr/ADR-MONO-003a-d4-override-scope-canonicalization.md) §3 (append 대상 #2, "no re-authorisation needed") + §D2.1 (new domain bootstrap)
- [TASK-MONO-116](../done/TASK-MONO-116-finance-external-fork-resolution-recording.md) — finance 동형 직전 선례 (구조·근거·append-only 규율 1:1 미러; §3 row #17)
- [TASK-MONO-118](../done/TASK-MONO-118-adr-mono-016-accepted-erp-bootstrap-transition.md) — ADR-016 ACCEPTED transition (§6 ACCEPTED row 작성; §3 row #19)
- [TASK-MONO-119](../done/TASK-MONO-119-erp-platform-bootstrap-artifact.md) — PR-B bootstrap artifact (standalone side PENDING 정직 명기 — 본 task 가 해소)
- [TASK-MONO-120](../done/TASK-MONO-120-hardstop-09-deferred-skeleton-option3-hook-fixture.md) — §3 row #20 (hook-hardening; 본 task row #21 과 번호 인접·의미 구분)

---

# Related Contracts

- 없음 (governance doc append, 외부 contract / API / event 무관).

---

# Target Service / Component

- `docs/adr/ADR-MONO-016-erp-platform-bootstrap.md` (§6 append)
- `docs/adr/ADR-MONO-003a-d4-override-scope-canonicalization.md` (§3 row #21 append)
- (no production / project / build / CI change)

---

# Implementation Notes

- **Append-only 절대 규율**: 두 §섹션 모두 명시적 append-only. impl 은 기존 row·주석의 어떤 문자도 변경하지 않고 **신규 행만 삽입**. impl 후 `git diff --numstat` 로 두 파일이 순수 add-only (`N 0`) 인지 자가검증 (AC-3) — context 외 `-` 0. ADR-016 §6 는 row 222 와 주석 224/226 사이 또는 226 뒤 일관 위치에 1 row append (기존 표 컬럼 7개 = `Date|Transition|Option|Classification|Standalone/Monorepo/Both|User intent quote|PR(s)`).
- ADR-MONO-003a §3 row #21: 번호 = 20 다음. category = `New domain bootstrap` (#15/#16/#17/#19 동일, one-off — §D1 미추가 명기). #20(hook-hardening) 과 의미 구분 1줄 명기 (audit-trail 내부 정합).
- **신규 ADR 불요 판정 근거** (impl PR description 에 명시): competing convention 부재 + ADR-003a §3 "no re-authorisation needed" 명시 인가 + BE-290/302 메타 + TASK-MONO-116 직전 동형 선례.
- **정직 미완 기록**: stale ref prune 실패는 §Goal·§Scope-Out 에 명기됨 — impl/close 어디서도 "ref prune 완료" 로 적지 말 것 (MONO-116 과의 유일 실질 차이; green-wash 금지).
- Lifecycle: spec PR = 본 task 파일 ready/ + 루트 INDEX `## ready` 갱신 (impl code 0). impl PR = task ready→review (lifecycle commit) + 2 ADR append (별 commit). close chore = 3-step bundle 1 commit (git mv review→done + Status review→done [BE-299 staged-blob 재검증] + INDEX `## review` 제거 / `## done` 1줄 prefix `(impl PR #N 머지, hash)` + §6/§3 PR# backfill = §-sanctioned).
- agent memory 동기화 (`project_monorepo_template_strategy.md` "Phase 6 downstream Template usage 2회차 CONFIRMED — erp" + `project_portfolio_7axis_architecture.md` + `MEMORY.md`) = repo task 외부 — dispatcher 가 본 task 머지 검증 후 직접 수행 (finance MONO-116 동일 패턴; repo PR 에 memory 미포함).

---

# Edge Cases

1. **Append-only 위반 risk**: Edit tool 이 기존 row/주석을 건드리면 §6/§3 audit-trail 무결성 훼손. 완화: Edit old_string 을 "append 지점 직전 안정 anchor + 그 뒤"로 잡아 기존 텍스트를 old_string 에 포함하되 new_string 에서 **그대로 보존 + 신규 row 만 뒤에 추가**; impl 후 `git diff --numstat` add-only 자가검증 (AC-3).
2. **fork 가 추후 삭제/rename 될 가능성**: §6/§3 row 는 2026-05-19 시점 historical snapshot (audit-trail 본질). 추후 외부 repo 변경은 별 사건 — 본 row 는 "그 시점에 Template-derived fork 가 존재했다"는 사실 기록이라 무효화되지 않음.
3. **§3 는 D4-OVERRIDE audit-trail**: row append 가 D4 OVERRIDE 재인가를 요구하지 않음 — "No re-authorisation needed" 명시. #14/#15/#16/#17/#19 one-off 선례와 동일 — §D1 enumeration 미추가.
4. **PR # backfill 시점**: spec PR 에서는 §6/§3 미변경 (task 파일만). impl PR 에서 ADR append (PR# placeholder → close chore backfill; 기존 backfill 관례 답습 — 모두 add-only).
5. **row #20 vs #21 번호 혼동**: row #20 = MONO-120 hook-hardening (D4 OVERRIDE), row #21 = 본 task erp fork CONFIRMED (New domain bootstrap). 카테고리 상이, 번호만 순차 — row #21 본문이 이를 1줄 명시 (감사 추적 reader 혼동 차단). row #19 의 "scope out §3 row #20" = erp-artifact 한정(이미 #20 본문이 해소)과 무관.

---

# Failure Scenarios

## A. 외부 fork 가 실제로는 Template-derived 가 아니거나 부재

→ Option C standalone side 미충족 → **거짓 기록 금지 (green-wash 금지)**. STOP, ADR append 하지 않고 사용자에게 정직 보고 (PENDING 유지). **이미 객관 검증으로 배제됨**: dispatcher 독립 `gh repo view` 가 `templateRepository: {name: project-template, owner: kanggle}` + PUBLIC + createdAt 2026-05-19T10:01:11Z 반환 (§Goal). impl 직전 1회 재확인 (BE-303 정신).

## B. impl diff 가 기존 row/주석을 수정 (append-only 위반)

→ AC-3 자가검증에서 `git diff --numstat` 에 두 파일이 `N 0` 아니거나 context 외 `-` 발견 시 즉시 revert, 기존 보존 + 신규 row 만 재-append. 머지 전 add-only 입증 (impl PR description 에 `--numstat` + add-only 단언).

## C. 다른 PENDING/미완 잔재 발견

→ honest chain: 동일 recording class 면 본 task 에서 같이 해소; 다른 class 면 STOP + 별 follow-up, scope 확장/green-wash 금지. **알려진 미완 = stale remote ref prune (사용자 명령 경로 실패)** — 이는 명시적으로 §Scope-Out + 별도 user-shell 재-handoff (본 task 가 "완료"로 적지 않음). agent memory = repo 외부 (dispatcher 직접).

---

# Test Requirements

- impl PR `git diff --numstat` = 정확히 `docs/adr/ADR-MONO-016-*.md` + `docs/adr/ADR-MONO-003a-*.md` 2파일, 각 **`N 0` (add-only)** (AC-3; PR description 에 numstat + "0 existing-line modification" 단언).
- 신규 ADR 파일 0, 코드/projects/빌드/CI 변경 0.
- ADR cross-ref 정합 (§6 신규 row ↔ §3 row #21 ↔ MONO-119 close row 의 standalone-PENDING 이 이제 "해소됨"으로 추적 가능).
- markdown lint (해당 시) green; 표 컬럼 수 일관 (§6 7-col / §3 6-col).

---

# Definition of Done

- [ ] ADR-MONO-016 §6 resolution row append (221/222 row + 224/226 주석 불변)
- [ ] ADR-MONO-003a §3 row #21 append (#1~#20 불변, #20 과 의미 구분 명기)
- [ ] `git diff --numstat` add-only 자가검증 통과 (기존 라인 수정 0)
- [ ] 신규 ADR 0 / 코드·projects·빌드·CI 변경 0 / diff scope = 2 ADR 파일
- [ ] impl PR description 에 객관 fork 검증 인용 + add-only 단언 + 신규-ADR-불요 근거 + stale-ref-prune 미완 정직 명기
- [ ] Ready for review

---

# Notes

- **Recommended impl model**: **Sonnet** (분석=Opus 4.7 / 구현 권장=Sonnet 4.6) — 2개 ADR 표에 append-only row 추가, 설계 결정 무 (신규 ADR 불요 판정은 본 spec 이 근거 확정, TASK-MONO-116 직전 동형). append-only 무결성 정밀 요구 → impl 후 dispatcher add-only 자가검증 필수 (BE-301).
- **분량**: very small — 2 파일, 각 1 row append (+ §6 선택 주석 1문장 append).
- **dependency**:
  - `선행`: TASK-MONO-118 (#619, ADR-016 ACCEPTED; §6 ACCEPTED row + §3 #19) + TASK-MONO-119 (#620, PR-B; standalone PENDING 정직 명기) 머지 완료 = origin/main; 외부 fork = 사용자 2026-05-19 셸 실행 완료 (dispatcher 독립 객관 검증됨, §Goal).
  - `후속`: 없음 — 본 task 머지 = erp v1 monorepo-side + standalone-side **양쪽 완전 종결**, ADR-MONO-016 전 항목 해소. erp = portfolio 마지막 도메인 (mes 드롭) → 신규 도메인 부트스트랩 ADR 더 이상 없음.
- **green-wash 금지 연계**: PR-B (MONO-119) 가 standalone side 를 정직 PENDING 으로 남긴 것의 정직한 종결 — silently drop 되지 않고 §6(decision)/MONO-119 AC/close-one-liner/memory 가 추적 중이었음. 객관 검증 (fork = 진짜 Template-derived) 후에만 기록. **추가 정직 단서**: 동반 stale-ref-prune 은 사용자 명령 경로 실패로 미완 — 본 task 가 "완료"로 적지 않고 별도 재-handoff (MONO-116 과 유일 실질 차이, 사실 그대로).
- **append-only 보존 이유**: §6/§3 는 audit-trail — "standalone side 가 decision-만-기록 상태였다"는 사실 자체가 정직성 기록 (classifier-blocked op 를 숨기지 않고 hand-off + 추적했음의 증거). 해소는 반드시 새 row 로.
- **Template downstream 2회차**: finance(MONO-116, §3 #17) + erp(본 task, §3 #21) = `kanggle/project-template` downstream 실사용 2회 CONFIRMED. ADR-MONO-003b Phase 5 "전략 설계대로 작동" 신호 2차 실증 (1차=finance). erp = 마지막 도메인이므로 이 패턴의 마지막 인스턴스.
