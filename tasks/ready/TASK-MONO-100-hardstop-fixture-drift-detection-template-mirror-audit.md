# Task ID

TASK-MONO-100

# Title

HARDSTOP fixture body byte-compare 강화 (drift detection 자동화) + TEMPLATE.md mirror audit (TASK-MONO-099 follow-up bundle)

# Status

ready

# Owner

monorepo

# Task Tags

- monorepo
- hardstop-rules
- fixture-drift-detection
- template-md-audit
- follow-up-mono-099

---

# Goal

TASK-MONO-099 § Out of Scope 의 2 follow-up 묶음 closure:

1. **HARDSTOP fixture body byte-compare 강화**: 현재 `Assert-Stanza` 는 stanza ID + decision + 4-block presence 만 검증, **body content 미검증** → hook hardcoded body 와 `platform/hardstop-rules.md` 사이 manual sync 부담. 새 fixture `hardstop-body-canonical-sync.ps1` 가 platform/hardstop-rules.md 의 10 stanza 를 parse + 5 mechanical detector (HARDSTOP-01/03/05/09/10) × invocation → hook 출력의 `[WHY]` 이후 tail 을 canonical tail 과 byte-compare. drift 시 fixture FAIL.

2. **TEMPLATE.md mirror audit**: TEMPLATE.md 가 CLAUDE.md 의 catalog / stanza body 를 inline mirror 하는지 확인. **Audit 결과 = mirror 없음** (L204/810/831 = generic pointer references only, 별 sync 불필요). 본 task body 에 audit 결과 기록 = 재검토 cost 0.

**design 결정 — `[VIOLATION]` block 은 byte-compare 제외**:
- hook 의 `[VIOLATION]` line 은 dynamic inject (relFromRoot / lineNo / taskId 등 실제 값 치환) + 더 구체적 wording
- platform/hardstop-rules.md 의 `[VIOLATION]` 은 generic placeholder body (`<cwd>`, `<path>`, `<file>:<line>` 등)
- 두 형식은 design 상 의도된 차이 — `[VIOLATION]` 만 stanza ID 검증 (existing `Assert-Stanza`) + 나머지 3 block 만 verbatim 비교 (신규 logic)
- 결과: `[WHY]` + `[REMEDIATION]` + `[REFERENCE]` 3 block 의 drift 가 자동 검출됨 — 본질 invariant 가드는 충족

# Scope

## In Scope

### 1. `_helpers.ps1` 헬퍼 추가 (`.claude/hooks/__tests__/_helpers.ps1`)

신규 함수 3개:

- `Get-CanonicalStanza -RuleId "HARDSTOP-NN"`:
  - platform/hardstop-rules.md 를 읽어서 `^## HARDSTOP-NN — ...` H2 헤더 직후 ``` ... ``` code block body 반환 (trimmed)
  - 없으면 throw

- `Get-StanzaTail -Stanza "..."`:
  - stanza body 에서 `[WHY]` 라인부터 끝까지 반환 (= `[WHY]` + `[REMEDIATION]` + `[REFERENCE]` 3 block)
  - line ending normalize (CRLF → LF) + trailing whitespace trim

- `Assert-StanzaBodyMatchesCanonical -HookOutput $output -RuleId "HARDSTOP-NN"`:
  - hook output 의 reason 필드를 parse → `Get-StanzaTail` 추출
  - canonical stanza 의 `Get-StanzaTail` 추출
  - byte-compare (verbatim). mismatch 시 first-N-line diff 메시지로 throw

### 2. 새 fixture `.claude/hooks/__tests__/hardstop-body-canonical-sync.ps1`

5 mechanical detector × 1 trigger invocation = 5 PASS case:

- HARDSTOP-01: 기존 `hardstop-01-no-project-md.ps1` 의 synth case 동일 — file under `projects/orphan-no-projectmd/apps/foo.md` 에 `Write`
- HARDSTOP-03: shared file (platform/) 에 `projects/wms-platform/` 토큰 reference (annotated allow 없음)
- HARDSTOP-05: tasks/review/ 의 task file body 편집 (non-lifecycle-move)
- HARDSTOP-09: `projects/<synth>/apps/<svc>/src/main/` 편집인데 architecture.md 부재
- HARDSTOP-10: `projects/<synth>/specs/services/<svc>/architecture.md` 에 Service Type 헤더 / Identity table row / Composition H3 모두 부재

각 trigger 마다:
1. hook invoke (synth payload)
2. output reason → `Get-StanzaTail` 추출
3. canonical (`Get-CanonicalStanza HARDSTOP-NN` → `Get-StanzaTail`) 추출
4. byte-compare → PASS or throw with diff

### 3. `run-all.ps1` 에 새 fixture 등록

기존 fixture 6개 + 신규 1개 = 7 fixture invocation.

### 4. TASK body 에 TEMPLATE.md mirror audit 결과 기록 (본 task body 만, file edit 없음)

TEMPLATE.md 838 lines / 38 heading. "Hard Stop" / "HARDSTOP" / "Repository Layout" / "Source of Truth" / "Required Workflow" / "Project Classification" 모든 occurrence:
- L204: rules/taxonomy.md 의 Hard Stop 일반 mention (catalog 인용 아님)
- L810: CLAUDE.md Hard Stop rules 일반 mention (Shared/project boundary 설명 맥락)
- L831: pointer description ("CLAUDE.md — operating rules (Repository Layout, Hard Stop rules, Local Network Convention summary)")

**결론**: TEMPLATE.md 는 catalog 또는 stanza body 의 inline mirror 를 보유하지 않음. L831 pointer description 도 catalog 분리 후에도 정확 (CLAUDE.md 에 Hard Stop catalog 가 존재). **별 sync 불필요, verify-only**.

## Out of Scope

- **Hook stanza body 런타임 parsing** (TASK-MONO-099 § Out of Scope #1, ADR 수준 mechanism shift): hook 가 platform/hardstop-rules.md 를 런타임에 읽어서 stanza 생성하면 drift 0 보장. 그러나 hook 의 dynamic inject (`[VIOLATION]` 의 실제 path/line 치환) 와 canonical placeholder body 의 정합화 mechanism 설계 필요 → 별 ADR.
- **`[VIOLATION]` block byte-compare**: design 상 의도된 drift (dynamic inject vs generic placeholder). 의미 invariant 는 stanza ID 동일성 + `[WHY]`/`[REMEDIATION]`/`[REFERENCE]` verbatim 일치로 충분.
- **HARDSTOP-02/04/06/07/08 (semantic detectors)**: 현재 hook 가 trigger 안 함 (judgement 영역). 본 fixture 도 5 mechanical 만 cover. 향후 semantic detector 추가 시 fixture 도 함께 확장.
- **CLAUDE.md catalog 의 condition 한 줄 ↔ platform/hardstop-rules.md 의 `[VIOLATION]` 한 줄 sync**: 의미 일치만 요구 (byte 일치 아님). 별 drift detection 가치 미달.

# Acceptance Criteria

- [ ] `_helpers.ps1` 에 신규 함수 3개 (`Get-CanonicalStanza` / `Get-StanzaTail` / `Assert-StanzaBodyMatchesCanonical`) 추가 — 기존 helper signature 유지.
- [ ] 새 fixture `.claude/hooks/__tests__/hardstop-body-canonical-sync.ps1` 작성 — 5 mechanical detector × 1 invocation = 5 PASS line 출력.
- [ ] `run-all.ps1` 에 새 fixture 등록.
- [ ] `powershell .claude/hooks/__tests__/run-all.ps1` 실행 시 모든 fixture PASS (기존 17 PASS + 신규 5 PASS = 22 PASS).
- [ ] 인위 drift 검출 검증: hook hardcoded body 의 한 character 변경 시 fixture FAIL — diff 메시지 출력 확인 (수동 검증 + revert).
- [ ] TEMPLATE.md 미터치 (verify-only audit 결과 task body 에만 기록).
- [ ] CLAUDE.md / production code / hook runtime behavior / platform/hardstop-rules.md = 0 변경.

# Related Specs

- [`platform/hardstop-rules.md`](../../../platform/hardstop-rules.md) (canonical body source, TASK-MONO-099 산출)
- [`.claude/hooks/hardstop-detect.ps1`](../../../.claude/hooks/hardstop-detect.ps1) (hook hardcoded body, sync 대상)
- [`.claude/hooks/__tests__/_helpers.ps1`](../../../.claude/hooks/__tests__/_helpers.ps1) (helper 확장 대상)
- [`.claude/hooks/__tests__/run-all.ps1`](../../../.claude/hooks/__tests__/run-all.ps1) (fixture runner)
- [`platform/lint-remediation-message-standard.md`](../../../platform/lint-remediation-message-standard.md) (4-block format spec)
- [`TEMPLATE.md`](../../../TEMPLATE.md) (audit-only target)
- TASK-MONO-099 closure context — 본 task 의 직속 follow-up

# Related Contracts

없음.

# Edge Cases

1. **Line ending normalize**: PowerShell `[System.IO.File]::ReadAllText` 는 platform-native EOL 보존. fixture 는 LF/CRLF mixed 가능 → byte-compare 전 양쪽 모두 `.Replace("`r`n", "`n")` 또는 split-by-regex `(?:\r\n|\r|\n)` 으로 normalize. CRLF/LF mismatch 가 false-positive drift fail 트리거하지 않도록 가드.

2. **Trailing whitespace**: canonical stanza 의 ``` code block 끝과 hook here-string 끝의 trailing whitespace / final newline 차이가 byte 다를 수 있음 → `.TrimEnd()` 적용 후 비교.

3. **Backtick escape difference**: hook 의 PowerShell here-string 안 `` `` `` (double backtick) 가 markdown code-inline 출력 — canonical (markdown ``` block 안 single backtick) 와 byte 다를 수 있음. 실제 hook 출력 = single backtick text (PowerShell `` ``` `` → ` 출력). canonical 도 single backtick → 동일.

4. **HARDSTOP-09/10 fixture synth — architecture.md 부재 vs 존재**: HARDSTOP-09 는 architecture.md 부재 trigger. fixture 가 `projects/<orphan>/apps/<svc>/src/main/Foo.java` synth 시 architecture.md 가 없는 임시 트리 구성. existing `hardstop-09-architecture-missing.ps1` synth 패턴 답습.

5. **HARDSTOP-10 fixture synth — Service Type 부재 vs 존재**: HARDSTOP-10 fixture 는 `projects/<synth>/specs/services/<svc>/architecture.md` 에 Service Type header / Identity table row / Composition H3 **모두 부재** synth content 작성. existing `hardstop-10-service-type-missing.ps1` 의 positive case 답습.

6. **fixture 사이의 PROJECT.md / .git 충돌**: 5 trigger 모두 다른 임시 트리 사용 (Guid 기반 unique path). 동일 root 공유 시 race. existing fixture 의 `try { ... } finally { Remove-Item }` cleanup 패턴 답습.

# Failure Scenarios

A. **canonical parsing 실패** (platform/hardstop-rules.md 의 H2 헤더 / code block 형식 변경): `Get-CanonicalStanza` 가 regex match 실패 → throw. fixture 가 모두 FAIL → 정상 drift detection (canonical 형식 자체가 drift).

B. **hook 가 stanza emit 안 함** (false negative): synth 가 trigger 조건 못 맞춤. fixture 가 empty output 처리 → Assert-Stanza 단계에서 throw. 검증 = 기존 5 fixture 가 이미 detect trigger 보장 (positive case PASS 검증된 patterns 답습).

C. **CRLF/LF mismatch false-positive**: line ending normalize 미적용 시 byte-compare 가 EOL 차이로 fail. 검출 = fixture FAIL 메시지의 diff 확인. fix = `Get-StanzaTail` 안 `Replace("`r`n", "`n")` + `.TrimEnd()`.

D. **`[VIOLATION]` line 이 hook output 의 어디까지 차지하는지 모호**: 본 design 은 `[WHY]` 라인부터 끝까지 tail 로 정의. hook 의 `[VIOLATION]` 가 multi-line 일 가능성 — hook code 확인 결과 모두 single-line. 안전.

E. **인위 drift 검출 verification 시 회귀 검출 안 됨**: AC 검증을 위해 의도적 1 character drift 후 fixture FAIL 확인 → revert. 만약 fixture 가 detect 못 하면 byte-compare 로직 자체가 broken. revert 후 task 재설계.

---

# Implementation Notes (작성 시 참고)

- 분석=Opus 4.7 / 구현 권장=Sonnet 4.6 (mechanical fixture extension + regex parsing, low judgment after design)
- D4 OVERRIDE 적용 — TASK-MONO-099 follow-up bundle, governance polish 연장선 (ADR-MONO-003a § D1.1)
- Lifecycle = ready → review 직접 (single-PR closure 16차)
- 묶음 근거 = feedback_pr_bundling — TASK-MONO-099 의 § Out of Scope 2 후보 (drift hygiene 동일 도메인)
