# Task ID

TASK-MONO-116

# Title

finance-platform 외부 Template-fork 완료 기록 — ADR-MONO-008 §6 + ADR-MONO-003a §3 append-only resolution row (Option C standalone side 확정)

# Status

done

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

finance-platform 부트스트랩 (ADR-MONO-008 ACCEPTED, D1 **Option C** = Template fork + monorepo direct-include) 의 **유일 잔여 미해소 항목**인 외부 `kanggle/finance-platform` Template-fork 가 2026-05-19 사용자 셸에서 생성 완료되었다. 이를 두 append-only governance audit-trail 에 **정직하게 기록**하여 Option C 의 standalone side 를 확정한다.

PR-B (TASK-MONO-114) 머지 시점에는 이 fork 가 classifier-blocked outward-facing op 로 user 에게 hand-off 되어 **PENDING** 상태로 정직하게 기록되었고 (green-wash 금지 — monorepo side 만 landed, standalone side 미확정 명기), [ADR-MONO-008](../../docs/adr/ADR-MONO-008-finance-platform-bootstrap.md) §6 2026-05-18 ACCEPTED row 와 [ADR-MONO-003a](../../docs/adr/ADR-MONO-003a-d4-override-scope-canonicalization.md) §3 row #16 양쪽이 "PENDING / not-yet-confirmed" 로 기록 중이다. 본 task 는 그 PENDING 을 **새 row append 로 해소** (기존 row 불변 — append-only).

**객관 검증 (이미 수행, 본 task 의 기록 근거)**: `gh repo view kanggle/finance-platform --json name,isTemplate,createdAt,templateRepository,visibility,url` →
- `templateRepository: {name: project-template, owner: kanggle}` = **Template-derived 객관 증명** (단순 repo 아님 — `kanggle/project-template` 에서 파생된 진짜 Template fork; ADR-MONO-008 D1 Option C 요건 정확 충족)
- `visibility: PUBLIC`, `isTemplate: false` (인스턴스로 올바름), `createdAt: 2026-05-19T03:42:33Z`, `url: https://github.com/kanggle/finance-platform`
- 부수: stale remote ref 10개 user 셸 prune 완료 (`git ls-remote --heads origin` non-main = EMPTY, origin = `main` 단독).

**이것은 신규 결정이 아니다** — 완료된 PENDING operational hand-off 의 reality-alignment 기록일 뿐이다. competing convention 부재 → **신규 ADR 불요** (ADR-MONO-003a §3 말미 "When these land, append rows here. No re-authorisation needed (covered by D1.2)." 가 명시적으로 재인가 불요를 인가; BE-290/302 메타: drift/recording-fix 의 ADR 필요 판정 = competing convention 존재 여부, 없으면 reality-alignment).

후속 영향:
- finance v1 이 **양쪽 모두** 완전 종결 — monorepo side (행위-증명 chain: MONO-115 → FIN-BE-002 → 003 → 004, finance-integration-tests CI 12/12) + standalone side (Template fork). ADR-MONO-008 의 모든 항목 해소.
- `project_monorepo_template_strategy.md` 가 기록할 "Phase 6 first downstream Template usage CONFIRMED" 가 비로소 사실이 됨 (그간 PENDING — agent memory 동기화는 본 repo task 외부에서 수행, §Notes 참조).
- erp-platform (7번째, ADR-MONO-009 candidate) 의 선행 gate (ADR-MONO-008 §3.2 "finance 완전 종결") 가 해소 — 본 task 머지 후 erp ADR PROPOSED 작성 경로가 ungated (단 self-ACCEPT 금지, 별 task/사용자 승인).

---

# Scope

## In Scope

### 1. `docs/adr/ADR-MONO-008-finance-platform-bootstrap.md` §6 — append-only resolution row

§6 "Status Transition History" (명시 **"Append-only"** + L260 "append-only, no rewrite") 에 2026-05-18 ACCEPTED row **아래** 신규 row 1개 append:

- Date `2026-05-19`, Transition `Option C standalone side CONFIRMED` (ACCEPTED 재선언 아님 — 동일 ACCEPTED 결정의 standalone artifact 완료 기록), Option `C (Both)`, Classification `(unchanged)`, Standalone/Monorepo/Both `Both — standalone CONFIRMED`, User-intent `사용자 "a 했어" (옵션 A: 외부 fork + ref prune 실행, AskUserQuestion "외부 fork 선행" 선택)`, PR(s) `<impl PR #> / close <#>`.
- Cell 본문: 객관 검증 인용 (`templateRepository=kanggle/project-template`, PUBLIC, `createdAt 2026-05-19T03:42:33Z`, `isTemplate:false`, url) + "2026-05-18 row 의 PENDING 이 본 row 로 해소; 기존 row 불변 (append-only)" 명기.
- (선택) L260 의 backfill 주석 형식과 일관되게, 신규 row 가 append-only 원칙을 따랐음을 1줄 보강 가능 (기존 주석 문장 불변 — 새 문장 append 만).

### 2. `docs/adr/ADR-MONO-003a-d4-override-scope-canonicalization.md` §3 — append-only audit-trail row #17

§3 audit-trail (L181 "When these land, append rows here. No re-authorisation needed") 에 row #16 **아래** row #17 append:

- `| 17 | #<impl PR> | 2026-05-19 | <impl squash hash> | New domain bootstrap | finance-platform 외부 Template-fork CONFIRMED (TASK-MONO-116). ADR-MONO-008 D1 Option C standalone side 완료 — row #16 의 "PENDING (classifier-blocked)" 해소. \`gh repo view kanggle/finance-platform\` 객관 검증: templateRepository=kanggle/project-template, PUBLIC, createdAt 2026-05-19. **Same one-off category as #15/#16** (does NOT add to §D1 — ADR-MONO-008-governed). 기존 row #15/#16 불변 (append-only). |`

## Out of Scope

- **기존 §6 / §3 어떤 row 의 본문도 수정 금지** — append-only 절대 준수 (2026-05-18 §6 row 의 "PENDING" 문구, §3 row #16 의 "PENDING" 문구 모두 historical record 로 보존; 해소는 새 row 로만).
- 신규 ADR 작성 금지 (reality-alignment, 신규 결정 무 — §Goal 근거).
- erp-platform / ADR-MONO-009 관련 일체 (별 task; 본 task 머지 후 ungated 되나 self-ACCEPT 금지).
- production / 코드 / `projects/finance-platform/` 트리 / `settings.gradle` / `ci.yml` / `scripts/sync-portfolio.sh` 변경 0 — 이들은 PR-B (MONO-114) 에서 이미 landed (monorepo side); 외부 fork 는 external repo 라 monorepo 변경 무.
- agent memory 파일 (`project_monorepo_template_strategy.md` / `project_portfolio_7axis_architecture.md` / `MEMORY.md`) 은 repo task scope 아님 — dispatcher 가 본 task 머지 후 직접 동기화 (ADR-MONO-008 D4 step 20; §Notes 참조).
- `kanggle/finance-platform` 외부 repo 내부 내용/구조 검수 — Option C standalone side 의 "존재 + Template-derived" 확정이 본 task 범위; standalone repo 의 후속 진화는 그 repo 의 독립 책임 (ADR-MONO-003b §D3).

---

# Acceptance Criteria

1. ADR-MONO-008 §6 에 2026-05-19 resolution row 1개 **append** (2026-05-18 ACCEPTED row 아래, 기존 모든 row 본문 byte-unchanged). 객관 fork 검증 인용 포함.
2. ADR-MONO-003a §3 에 row #17 **append** (row #16 아래, 기존 #1~#16 본문 byte-unchanged). #15/#16 one-off category 일관.
3. **append-only 검증**: impl PR diff 가 두 ADR 파일에서 **순수 추가 라인만** (기존 라인 삭제/수정 0 — `git diff` 가 `+` 라인만, context 외 `-` 라인 0).
4. 신규 ADR 파일 0개 생성. ADR cross-reference (§6 ↔ §3 ↔ TASK-MONO-114 close row) 정합.
5. 코드/`projects/`/빌드/CI/sync-portfolio 변경 0 (doc-only). git diff scope = 정확히 2 ADR 파일 (+ lifecycle: spec PR=task+INDEX, impl PR=task move).
6. Goal 이 ADR-MONO-008 + 트리거(사용자 옵션 A 실행) + 객관 검증 근거 인용 (root INDEX Review Rule — recording task 도 출처 명시).
7. Lifecycle: spec PR (본 파일 + 루트 INDEX ready) ↔ impl PR (task move + 2 ADR append) 분리 (PR Separation Rule). close chore (review→done) 는 impl PR 머지 후.

---

# Related Specs

- [ADR-MONO-008](../../docs/adr/ADR-MONO-008-finance-platform-bootstrap.md) §6 (append 대상 #1) + §D1 Option C + §D4 step 18-20 (Recording) + §3.2/3.3
- [ADR-MONO-003a](../../docs/adr/ADR-MONO-003a-d4-override-scope-canonicalization.md) §3 (append 대상 #2, L181 "no re-authorisation needed") + §D2.1 (new domain bootstrap)
- [TASK-MONO-113](../done/TASK-MONO-113-adr-mono-008-accepted-finance-bootstrap-transition.md) — PR-A (ADR-008 ACCEPTED transition; §6 ACCEPTED row 작성)
- [TASK-MONO-114](../done/TASK-MONO-114-finance-platform-bootstrap-artifact.md) — PR-B (bootstrap artifact; §6 PR-B backfill + §3 row #16 "PENDING" 작성 — 본 task 가 그 PENDING 해소)
- [TASK-MONO-115](../done/TASK-MONO-115-finance-integration-ci-job.md) — finance v1 monorepo-side surfaced backlog 의 sibling (CI IT job); 본 task = 잔여 backlog (외부 fork) 해소 → finance v1 surfaced backlog 2/2 완전 종결

---

# Related Contracts

- 없음 (governance doc append, 외부 contract / API / event 무관).

---

# Target Service / Component

- `docs/adr/ADR-MONO-008-finance-platform-bootstrap.md` (§6 append)
- `docs/adr/ADR-MONO-003a-d4-override-scope-canonicalization.md` (§3 append)
- (no production / project / build / CI change)

---

# Implementation Notes

- **Append-only 절대 규율**: 두 §섹션 모두 명시적 append-only. impl 은 기존 row 의 어떤 문자도 변경하지 않고 **신규 행만 삽입**. impl 후 `git diff` 로 두 파일이 순수 add-only 인지 자가검증 (AC-3) — context 라인 외 `-` 0.
- ADR-MONO-008 §6 row 형식 = §D6.3 table 컬럼 (`Date | Transition | Option | Classification | Standalone/Monorepo/Both | User intent quote | PR(s)`). 신규 row 의 Transition 은 "ACCEPTED" 재기록 아님 — 동일 ACCEPTED 결정의 standalone artifact 완료 (예: `Option C standalone side CONFIRMED`). PR # 는 spec PR open 시 / close chore 시 backfill (기존 backfill 관례 = L260 "PR-A/PR-B numbers backfilled at PR open / close chore — append-only, no rewrite" 와 동일 — 단 본 row 의 backfill 도 새 문자 추가지 기존 row 수정이 아님).
- ADR-MONO-003a §3 row #17: 번호 = 16 다음. 형식 = 기존 행 (`| N | #PR | date | hash | category | desc |`). category = `New domain bootstrap` (#15/#16 동일, one-off — §D1 미추가 명기).
- **신규 ADR 불요 판정 근거** (impl PR description 에 명시): competing convention 부재 + ADR-003a §3 L181 명시적 재인가 불요 인가 + BE-290/302 메타 (reality-alignment recording = ADR 불요; document/accept = no-ADR).
- Lifecycle: spec PR = 본 task 파일 ready/ + 루트 INDEX `## ready` 갱신 (impl code 0). impl PR = task ready→in-progress→review (별 commit) + 2 ADR append (별 commit). close chore = 3-step bundle 1 commit (git mv review→done + Status review→done + INDEX `## review` 제거 / `## done` 1줄, prefix `(impl PR #N 머지, hash)`) — BE-299 staged-blob 재검증.
- agent memory 동기화 (ADR-MONO-008 D4 step 20: `project_monorepo_template_strategy.md` "first downstream Template usage CONFIRMED" + `project_portfolio_7axis_architecture.md` + `MEMORY.md`) = repo task 외부 작업 — dispatcher 가 본 task 머지 검증 후 직접 수행 (이전 finance chain 과 동일 패턴; repo PR 에 memory 미포함).

---

# Edge Cases

1. **Append-only 위반 risk**: Edit tool 이 기존 row 를 건드리면 §6/§3 audit-trail 무결성 훼손. 완화: Edit old_string 을 "append 지점 직전 안정 anchor + 그 뒤"로 잡아 기존 row 텍스트를 old_string 에 포함시키되 new_string 에서 **그 텍스트를 그대로 보존 + 신규 row 만 뒤에 추가**; impl 후 `git diff` add-only 자가검증 (AC-3).
2. **fork 가 추후 삭제/rename 될 가능성**: §6/§3 row 는 2026-05-19 시점 historical snapshot (audit-trail 본질). 추후 외부 repo 변경은 별 사건 — 본 row 는 "그 시점에 Template-derived fork 가 존재했다"는 사실 기록이므로 무효화되지 않음 (append-only audit-trail 의 의도된 성질).
3. **§3 는 D4-OVERRIDE audit-trail**: row append 가 D4 OVERRIDE 재인가를 요구하지 않음 — L181 "No re-authorisation needed (covered by D1.2)" 가 명시. #14(Phase-5)/#15/#16(new-domain) one-off 선례와 동일 — §D1 enumeration 에 카테고리 추가 안 함.
4. **PR # backfill 시점**: spec PR 에서는 §6/§3 미변경 (task 파일만). impl PR 에서 ADR append (PR# 는 impl PR open 후 알 수 있으므로, impl 시 placeholder → 동 PR 내 backfill commit 또는 close chore backfill; 기존 #15/#16 backfill 관례 답습 — 모두 add-only).

---

# Failure Scenarios

## A. 외부 fork 가 실제로는 Template-derived 가 아니거나 부재

→ 이 경우 Option C standalone side 미충족 → **거짓 기록 금지 (green-wash 금지)**. STOP, ADR append 하지 않고 사용자에게 정직 보고 (PENDING 유지). **이미 객관 검증으로 배제됨**: `gh repo view` 가 `templateRepository: {name: project-template, owner: kanggle}` + PUBLIC 반환 (§Goal). impl 직전 1회 재확인 (BE-303 정신 — 사용자 진술 ≠ 검증; 단 이미 1차 객관 검증 완료).

## B. impl diff 가 기존 row 를 수정 (append-only 위반)

→ AC-3 자가검증에서 `git diff` 에 context 외 `-` 라인 발견 시 즉시 revert, 기존 row 보존 + 신규 row 만 재-append. 머지 전 반드시 add-only 입증 (impl PR description 에 `git diff --stat` + add-only 단언).

## C. 다른 PENDING 잔재 발견 (예: 다른 doc/스크립트에도 외부 fork PENDING 언급)

→ Failure Scenario A 패턴 (honest chain): 동일 recording class 면 본 task 에서 같이 해소 (scope 내 — "외부 fork 완료 기록"); 다른 class (예: standalone repo 내부 구조 결손) 면 STOP + 별 follow-up task, scope 확장/green-wash 금지. (현재 알려진 PENDING = §6 + §3 + agent memory 3곳; 앞 2개 = 본 task, memory = repo 외부.)

---

# Test Requirements

- impl PR `git diff` = 정확히 `docs/adr/ADR-MONO-008-*.md` + `docs/adr/ADR-MONO-003a-*.md` 2파일, **add-only** (AC-3; PR description 에 `git diff --stat` + "0 existing-line modification" 단언).
- 신규 ADR 파일 0, 코드/projects/빌드/CI 변경 0.
- ADR cross-ref 정합 (§6 신규 row ↔ §3 row #17 ↔ MONO-114 close row 의 PENDING 문구가 이제 "해소됨"으로 추적 가능).
- markdown lint (해당 시) green; 표 컬럼 수 일관.

---

# Definition of Done

- [ ] ADR-MONO-008 §6 resolution row append (2026-05-18 row 불변)
- [ ] ADR-MONO-003a §3 row #17 append (#1~#16 불변)
- [ ] `git diff` add-only 자가검증 통과 (기존 라인 수정 0)
- [ ] 신규 ADR 0 / 코드·projects·빌드·CI 변경 0 / diff scope = 2 ADR 파일
- [ ] impl PR description 에 객관 fork 검증 인용 + add-only 단언 + 신규-ADR-불요 근거 명시
- [ ] Ready for review

---

# Notes

- **Recommended impl model**: **Sonnet** (분석=Opus 4.7 / 구현 권장=Sonnet 4.6) — 2개 ADR 표에 append-only row 추가, 설계 결정 무 (신규 ADR 불요 판정은 본 spec 이 이미 근거 확정). 단 append-only 무결성 = 정밀 요구 → impl 후 dispatcher add-only 자가검증 필수 (BE-301).
- **분량**: very small — 2 파일, 각 1 row append (+ §6 선택적 주석 1문장 append).
- **dependency**:
  - `선행`: TASK-MONO-113 (#593, ADR-008 ACCEPTED) + TASK-MONO-114 (#595, PR-B; §6 PENDING + §3 #16 PENDING 작성) 머지 완료 = origin/main; 외부 fork = 사용자 2026-05-19 셸 실행 완료 (객관 검증됨, §Goal).
  - `후속`: 없음 — 본 task 머지 = finance v1 의 monorepo-side + standalone-side **양쪽 완전 종결**, ADR-MONO-008 전 항목 해소. erp-platform (ADR-MONO-009 candidate) 의 ADR-008 §3.2 선행 gate 해소 → 별 task 로 ADR-009 PROPOSED 작성 ungated (self-ACCEPT 금지).
- **green-wash 금지 연계**: PR-B (MONO-114) 가 standalone side 를 정직하게 PENDING 으로 남긴 것의 정직한 종결 — silently drop 되지 않고 §6/§3/memory 가 추적 중이었음. 본 task 가 객관 검증 (fork = 진짜 Template-derived) 후에만 기록 (검증 없는 "완료" 선언 = green-wash, Failure Scenario A 로 차단).
- **append-only 보존 이유**: §6/§3 는 audit-trail — "2026-05-18 에 PENDING 이었다"는 사실 자체가 정직성 기록 (classifier-blocked op 를 숨기지 않고 hand-off + 추적했음의 증거). 그 row 를 덮어쓰면 그 정직성 history 가 사라짐. 해소는 반드시 새 row 로.
