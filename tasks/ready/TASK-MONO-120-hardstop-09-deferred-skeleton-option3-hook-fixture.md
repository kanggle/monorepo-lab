# Task ID

TASK-MONO-120

# Title

`hardstop-detect.ps1` HARDSTOP-09 over-fire on rule-sanctioned deferred-skeleton (Option-3) — recognition guard + fixture

# Status

ready

# Owner

monorepo

# Task Tags

- monorepo
- hardstop-rules
- hook-source-fix
- hardstop-09-over-fire
- deferred-skeleton-option3
- bootstrap-tooling
- audit-trigger-closure

---

# Goal

`.claude/hooks/hardstop-detect.ps1` 의 HARDSTOP-09 detector branch (≈ L242, regex `^projects/(?<proj>[^/]+)/apps/(?<svc>[^/]+)/src/main/` → `if (-not (Test-Path $archPath -PathType Leaf))`) 는 **path-only heuristic** 이다. 새 service source 파일이 `specs/services/<svc>/architecture.md` 부재 상태에서 생성되면 무조건 HARDSTOP-09 fire 한다. 그러나 이는 `platform/hardstop-rules.md` HARDSTOP-09 **Remediation Option 3** 가 명시적으로 허용하는 **rule-sanctioned deferred-skeleton bootstrap** (ADR-MONO-008 / ADR-MONO-016 § D6.2 PR-B 패턴) 을 false-positive 로 차단한다.

Canonical Option 3 (`platform/hardstop-rules.md`, verbatim):

> "If the decision is reversible and local (single class / single endpoint), implement with an inline comment citing the choice + one-line reason and file a follow-up `tasks/ready/` task to backfill the architecture.md update."

deferred-skeleton bootstrap 은 정확히 이 형태다: ZERO 비즈니스 로직 skeleton + `application.yml` (architecture decision 0) + **inline HARDSTOP-09 Option-3 citation** + `tasks/ready/TASK-*-BE-001` (AC-1 = `architecture.md` own). hook 의 path-only 검사는 inline citation 도 follow-up task 도 보지 못해 over-fire 한다.

**(g)(1) 사건 (TASK-MONO-119 erp-platform PR-B)**: backend-engineer agent 가 `projects/erp-platform/apps/masterdata-service/src/main/resources/application.yml` 작성 시 hook 이 HARDSTOP-09 fire. dispatcher (BE-301) adjudication: Option-3 가 canonical rule 에 존재 + finance 선례 객관 확인 (`git log --diff-filter=A` → finance `architecture.md` 최초 등장 = TASK-FIN-BE-001 #597 `5a4aae42`, **PR-B `d2b579f2` 아님** → finance PR-B 도 deferred-skeleton 이었고 동일 over-fire 대상이었음) + application.yml = ZERO architecture decision + inline citation 존재 + TASK-ERP-BE-001 가 architecture.md own → 실질 rule-compliant → **NOT reverted**, hook over-fire = 진짜 tooling defect → 투명 공개 + 본 follow-up (사용자 option A).

**Non-determinism**: finance PR-B 머지 시 hook 이 visible block 없이 통과, erp PR-B 는 agent 의 Bash route 가 PreToolUse hook 을 통과하며 loud flag. 동일 입력에 firing 이 비결정적 = detector 신뢰성 결함. 본 fix 는 over-fire 제거 **및** Option-3 케이스 판정을 결정적으로 만든다.

**핵심 원칙 — bypass 가 아니라 rule 집행**: Option 3 는 HARDSTOP-09 의 rule-sanctioned escape 다. hook 이 well-formed Option-3 citation + 실제 follow-up task 를 인식해 allow 하는 것은 rule 을 **우회**하는 게 아니라 rule 을 **그대로 집행**하는 것이다 (사람 reviewer 가 substance 를 검증하는 절차는 불변). 따라서 fix 는 HARDSTOP-09 를 약화시키지 않는다 — citation/task 부재 시 fire 는 그대로 유지 (fail-closed).

# Scope

## In Scope

### 1. `.claude/hooks/hardstop-detect.ps1` HARDSTOP-09 branch — Option-3 recognition guard

HARDSTOP-09 fire 직전에 **두 조건 동시 충족** 시 allow:

1. **Inline Option-3 citation** — simulated content (`$simContent`, 기존 Edit/Write simulation 산출물 재사용) 에 canonical Option-3 citation marker 존재. 권장 stable token regex (대소문자 무시, 주석 위치 무관):
   `HARDSTOP-09\b[^\n]{0,80}\bOption\s*3\b` (citation 은 한 줄 안에 `HARDSTOP-09` + `Option 3` 를 포함하도록 강제 — 우연 매치 방지).
2. **Follow-up `tasks/ready/` task 존재** — 해당 service 의 architecture.md backfill 을 소유하는 ready task. 검사: `tasks/ready/` **및** `projects/<proj>/tasks/ready/` 글롭의 `TASK-*-BE-*.md` 중 본문에 `architecture.md` 문자열을 포함하는 파일 ≥ 1 (repo 작업트리 기준; hook 은 이미 `$repoRoot` 보유).

두 조건 모두 충족 → HARDSTOP-09 suppress (allow), 그 외 (하나라도 미충족) → 기존대로 fire (**fail-closed default 보존**).

기존 path-only `if (-not (Test-Path $archPath -PathType Leaf))` 블록 **안에서**, stanza emit 직전에 위 guard 를 삽입 — `architecture.md` 가 실제 존재하면 애초에 이 블록 진입 안 함 (정상 path 0 영향). guard 는 architecture.md 부재 + skeleton 케이스에서만 평가.

### 2. 새 fixture `.claude/hooks/__tests__/hardstop-09-deferred-skeleton-option3.ps1`

회귀 가드, **2 PASS line** (positive + negative):

- **Positive (over-fire 제거)**: synth tree → `projects/<synth>/apps/<svc>/src/main/resources/application.yml` (inline `# HARDSTOP-09 Option 3: ...` citation 포함) + `projects/<synth>/tasks/ready/TASK-<X>-BE-001.md` (본문에 `architecture.md` 참조) 생성 → Write/Edit invocation → hook **allow** (pre-fix: HARDSTOP-09 stanza fire = FAIL).
- **Negative (HARDSTOP-09 enforcement 보존)**: 동일 synth skeleton 이나 citation **부재** (또는 follow-up task 부재) → hook **여전히 HARDSTOP-09 fire** (blanket bypass 아님 입증). Assert-Stanza HARDSTOP-09.

### 3. `.claude/hooks/__tests__/run-all.ps1` 등록

기존 fixture 集 + 1 (hardstop-09-deferred-skeleton-option3 추가). 총 PASS line 수 = 기존 + 2 (positive + negative).

## Out of Scope

- **HARDSTOP-09 의 일반 약화 X** — guard 는 정확히 형성된 Option-3 deferred-skeleton (citation + follow-up task 동시) 케이스만 suppress. 그 외 모든 architecture-decision-missing 케이스는 fire 유지.
- **다른 HARDSTOP detector** (01/03/05/10) 미터치.
- **Option 1 / Option 2 remediation 의 hook 인식** — Option 1(=spec 선작성) 은 architecture.md 가 생기므로 애초에 fire 안 함; Option 2(=Hard Stop emit) 는 의도된 fire. 본 task 는 Option 3 만.
- **CR-only / mixed line ending normalize** — MONO-102 가 별도 처리한 simulation 영역. 본 task 는 `$simContent` 를 그대로 소비 (MONO-102 fix 의 산출물 신뢰).
- **Hook unit-test framework 신설** — 기존 `_helpers.ps1` (`Invoke-Hook` / `Assert-Allowed` / `Assert-Stanza`) 패턴 답습.
- **platform/hardstop-rules.md HARDSTOP-09 canonical 본문 변경 X** — Option 3 는 이미 canonical 에 존재; 본 task 는 hook 이 그것을 인식하게 만들 뿐. (canonical 변경은 ADR-class governance — 본 task 아님.)

# Acceptance Criteria

- [ ] `.claude/hooks/hardstop-detect.ps1` HARDSTOP-09 branch 에 2-조건 (inline Option-3 citation regex + follow-up `tasks/ready/` task glob) AND guard 삽입 — 둘 다 충족 시에만 suppress, 그 외 fire (fail-closed).
- [ ] citation regex 가 `HARDSTOP-09` + `Option 3` 동일 라인 동시 포함 강제 (우연 단독 토큰 매치로 suppress 되지 않음).
- [ ] follow-up task 검사가 root `tasks/ready/` **및** `projects/<proj>/tasks/ready/` 양쪽 글롭을 커버.
- [ ] 새 fixture `hardstop-09-deferred-skeleton-option3.ps1` — positive(allow) + negative(여전히 fire) 2 PASS line.
- [ ] `run-all.ps1` 등록, 전체 PASS line = 기존 + 2, 全 PASS.
- [ ] Pre-fix 회귀 round-trip (수동, commit log 에만 기록·file 미터치): hook fix 만 `git stash` → positive fixture FAIL (HARDSTOP-09 stanza fire) + negative fixture PASS → `git stash pop` 후 둘 다 PASS.
- [ ] finance/erp 선례 정합 검증 (commit log 기록): `git log --diff-filter=A -- projects/finance-platform/apps/account-service/src/main/resources/db/.../architecture.md` 부재 + architecture.md 최초 등장 commit 이 PR-B(`d2b579f2`) 아님을 재확인 (deferred-skeleton 이 정상 패턴이었음을 detector test 가 보증).
- [ ] HARDSTOP-09 stanza body (4-block) 미터치 (MONO-099 catalog ↔ MONO-100 fixture body sync 가드 보존).
- [ ] platform/hardstop-rules.md / production code / 기존 fixture assertion shape = 0 변경.

# Related Specs

- [`.claude/hooks/hardstop-detect.ps1`](../../.claude/hooks/hardstop-detect.ps1) — fix target (HARDSTOP-09 branch ≈ L242, regex `^projects/(?<proj>[^/]+)/apps/(?<svc>[^/]+)/src/main/`)
- [`platform/hardstop-rules.md`](../../platform/hardstop-rules.md) — HARDSTOP-09 canonical incl. **Remediation Option 3** (미터치, hook 이 인식할 대상 텍스트의 SoT)
- [`.claude/hooks/__tests__/_helpers.ps1`](../../.claude/hooks/__tests__/_helpers.ps1) — `Invoke-Hook` / `Assert-Allowed` / `Assert-Stanza` 재사용
- [`.claude/hooks/__tests__/run-all.ps1`](../../.claude/hooks/__tests__/run-all.ps1) — fixture 등록 update
- [`tasks/done/TASK-MONO-102-hardstop-detect-crlf-lf-simulation-fix.md`](../done/TASK-MONO-102-hardstop-detect-crlf-lf-simulation-fix.md) — 동類 hook-source-fix + fixture + run-all 선례 (구조 답습)
- [`tasks/done/TASK-MONO-061-hardstop-detect-orphan-fail-open.md`](../done/TASK-MONO-061-hardstop-detect-orphan-fail-open.md) — hardstop-detect false-positive fail-open 선례 (HARDSTOP-01)
- [`tasks/done/TASK-MONO-119-erp-platform-bootstrap-artifact.md`](../done/TASK-MONO-119-erp-platform-bootstrap-artifact.md) — (g)(1) 사건 발생 task; done one-liner 에 over-fire adjudication 전체 기록
- [`docs/adr/ADR-MONO-016-erp-platform-bootstrap.md`](../../docs/adr/ADR-MONO-016-erp-platform-bootstrap.md) § D6.2 — deferred-skeleton PR-B 패턴 정의
- [`docs/adr/ADR-MONO-008-finance-platform-bootstrap.md`](../../docs/adr/ADR-MONO-008-finance-platform-bootstrap.md) § D6.2 — 동일 패턴 1차 적용 (finance, over-fire 잠재 대상이었음)
- 메모리 reference: `project_monorepo_template_strategy.md` / `project_portfolio_7axis_architecture.md` (bootstrap deferred-skeleton 메타)

# Related Contracts

없음.

# Edge Cases

1. **Citation spoofing 우려**: 누군가 architecture decision 을 실제로 내포한 파일에 가짜 `# HARDSTOP-09 Option 3` 주석을 달아 suppress 시도. 완화: Option 3 자체가 "reversible and local" + reviewer substance 검증을 전제하는 rule-sanctioned escape — hook 의 역할은 well-formed citation + 실제 follow-up task 의 **존재** 확인이지 substance 판정이 아니다. substance 는 종전대로 PR review 가 막는다. hook 이 이를 인식하는 것은 rule 집행이지 약화가 아님 (Goal § "bypass 가 아니라 rule 집행"). 추가로 follow-up task 필수 조건이 단순 주석-한-줄 spoof 의 비용을 올림 (실제 ready task 파일 + architecture.md 참조 본문 필요).

2. **follow-up task 가 architecture.md 를 직접 언급 안 함**: AC 가 `architecture.md` literal substring 을 요구. ADR-008/016 § D6.2 가 PR-B 의 first task AC-1 = architecture.md own 을 강제하므로 정상 패턴에서 항상 충족. 미충족 = 비정상 → fire 가 옳음 (fail-closed).

3. **여러 service skeleton 동시 생성**: bootstrap 은 통상 단일 first service. 다중이어도 각 파일 invocation 별 평가 — citation 있는 파일만 suppress, 없는 파일은 fire. 의도된 동작.

4. **glob 비용**: `tasks/ready/` + `projects/*/tasks/ready/` 글롭은 작은 디렉터리 (수십 파일). hook PreToolUse 호출당 1회. micro-overhead.

5. **citation 이 simContent 에 없으나 file 에 이미 있음 (순수 다른 영역 Edit)**: `$simContent` 는 Edit 후 예상 전체 내용 (MONO-102 normalize fix 산출). 따라서 기존 file 에 citation 이 있고 다른 라인을 Edit 해도 simContent 에 citation 포함 → 정상 suppress. 단 그 경우 architecture.md 가 이미 backfill 됐다면 애초에 HARDSTOP-09 branch 진입 안 함.

6. **fail-open vs fail-closed**: MONO-061 은 HARDSTOP-01 orphan 을 fail-**open** (오탐 시 통과) 처리했다. 본 task 는 정반대 — guard 미충족 시 fire 유지 (fail-**closed**). HARDSTOP-09 는 architecture decision governance 라 보수적 default 가 안전; suppress 는 두 강한 신호 동시일 때만.

# Failure Scenarios

A. **정상 case 회귀 (architecture.md 존재)**: `Test-Path $archPath` true → HARDSTOP-09 branch 미진입 → guard 평가 자체 안 됨. 기존 fixture 全 PASS 보존 (run-all 회귀 0).

B. **negative fixture 가 실수로 allow**: citation/task 부재인데 suppress → HARDSTOP-09 무력화. negative fixture 가 정확히 이를 가드 (Assert-Stanza HARDSTOP-09 필수). 두 조건 AND 로직 단위 검증.

C. **classifier 가 hook 자체 수정 차단**: `.claude/hooks/` 안 safety hook 의 AI self-modification 은 auto-mode classifier 가 hard-block (ADR-MONO-003a § 3 row #14: `protect-main-branch.ps1` allowlist edit "manually applied by operator" 선례). 구현 중 차단되면 **우회 금지 — STOP, 정확한 diff 를 operator 에게 전달**(Phase-5 / row #14 절차). fixture / run-all / 본 task 문서는 classifier 영향 없음 (hook source 아님). Implementation Notes 참조.

D. **CI 회귀**: `.ps1` 변경 → `code-changed` filter true → pipeline 활성하나 hook 은 PreToolUse(Edit/Write) 로컬 전용, CI runtime 영향 0. `run-all.ps1` developer-run only (CI-gated 아님). 회귀 0 기대.

E. **citation regex 과대 매치**: `HARDSTOP-09\b[^\n]{0,80}\bOption\s*3\b` 가 hardstop-rules.md 본문 인용을 포함한 무관 파일을 매치. 그러나 guard 는 HARDSTOP-09 branch (= `projects/<p>/apps/<s>/src/main/` 신규 파일 + architecture.md 부재) 안에서만 평가 — 일반 doc/spec Edit 는 진입 자체 안 함. 잔여 위험 무시 가능.

---

# Implementation Notes (작성 시 참고)

- 분석=Opus 4.7 / 구현 권장=**Sonnet 4.6** (focused hook guard + 2-line fixture + run-all 등록, low judgment; (g)(1) adjudication 은 이미 MONO-119 에서 종결 — 본 task 는 mechanical hardening).
- **classifier 차단 대비 (필수 숙지)**: `.claude/hooks/hardstop-detect.ps1` 은 safety hook → AI self-modification 이 auto-mode classifier 에 hard-block 될 수 있음. 차단 시 **재구성하여 우회 시도 절대 금지** — 정확한 unified diff 를 사용자(operator)에게 제시하고 STOP (ADR-MONO-003a § 3 row #14 / Phase-5 선례; 표준 규율). fixture/run-all/task md 는 agent 적용 가능 (hook source 아님). PR 은 hook diff 가 operator-applied 인지 agent-applied 인지 commit 본문에 정직 명기.
- D4 OVERRIDE 적용 — hook-source hardening 은 refactor/governance-tooling cycle 의 자연 연장, MONO-060/061/096/102 sibling (ADR-MONO-003a § D1.1). 닫을 때 § 3 audit row append (one-off, MONO-102 동형 — § D1 enumeration 미추가).
- Lifecycle = ready → review (impl PR) → done (close chore). spec PR = 본 문서 authoring (별 PR, PR Separation Rule "Never bundle task spec authoring with implementation").
- 묶음 근거 = single impl PR (hook guard + fixture + run-all 등록 — 작은 scope, 한 closure).
- **가치**: 본 fix 후 mes(드롭됨) 외 신규 도메인 bootstrap 은 없으나, 향후 임의 deferred-skeleton (single-service v2 추가 등) 시 HARDSTOP-09 over-fire 재발 차단 + detector 비결정성 제거. (g)(1)-class loud-flag 의 재발 자체를 봉쇄.
