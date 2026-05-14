# Task ID

TASK-MONO-099

# Title

CLAUDE.md refactor — Hard Stop Rules 4-block body 를 `platform/hardstop-rules.md` 로 분리, catalog 축약

# Status

done

# Owner

monorepo

# Task Tags

- monorepo
- claude-md-refactor
- hardstop-rules
- catalog-pattern

---

# Goal

CLAUDE.md (319 lines) 의 가장 큰 단일 섹션 `# Hard Stop Rules` (123 lines, 전체의 38.6%) 의 4-block 본문 10개를 `platform/hardstop-rules.md` 로 외부 분리.

CLAUDE.md § Hard Stop Rules 는 1-line catalog (rule ID + condition + reference link) 로 축약. canonical 본문은 `platform/hardstop-rules.md` 가 source-of-truth.

OpenAI Harness Engineering "AGENTS.md 100라인 목차" 패턴 (TASK-MONO-057 답습) 의 두 번째 적용 — TASK-MONO-057 가 312→209 lines (-33%) 단축 후, promote PR (#508, branch master constraint + `git fetch origin main` first) + ADR-MONO-012 D3 governance 갱신으로 +110 lines 누적 → 본 task 가 catalog 분리로 ~221 lines 회복 + 향후 promotion 여유 확보.

# Scope

## In Scope

1. **`platform/hardstop-rules.md` 신설** (~140 lines):
   - intro paragraph (lint-remediation-message-standard.md cross-ref + CLAUDE.md catalog cross-ref + hook source-of-truth 위치 명시)
   - HARDSTOP-01~10 10개 stanza (현재 CLAUDE.md L138-256 의 4-block body 그대로 verbatim 이동, 문자 0 변경)
   - 각 stanza 위에 `## HARDSTOP-NN — <one-line condition>` H2 anchor (CLAUDE.md catalog 의 deep-link target)

2. **CLAUDE.md § Hard Stop Rules 축약** (123 → ~25 lines):
   - 기존 prologue 한 줄 유지 ("Stop immediately if any of the conditions below holds. Every Hard Stop emission MUST follow the 4-block format defined in [`platform/lint-remediation-message-standard.md`](platform/lint-remediation-message-standard.md) — prose stops are not acceptable.")
   - 추가: "Canonical 4-block body for each rule lives in [`platform/hardstop-rules.md`](platform/hardstop-rules.md). The catalog below names each trigger; click through for the full stanza an agent must emit."
   - HARDSTOP-01~10 catalog table (3 column: ID / Condition / Reference):
     ```
     | ID | Condition | Reference |
     |---|---|---|
     | HARDSTOP-01 | No `PROJECT.md` locatable | [hardstop-rules.md#hardstop-01](platform/hardstop-rules.md#hardstop-01) |
     | HARDSTOP-02 | `PROJECT.md` missing/unparseable or unknown domain/trait | [hardstop-rules.md#hardstop-02](...) |
     | ... | ... | ... |
     ```

3. **`.claude/hooks/hardstop-detect.ps1` docstring 갱신** (1 line edit, behavior 변경 0):
   - L8 `Stanza body source = CLAUDE.md § Hard Stop Rules (single source of truth).` → `Stanza body source = platform/hardstop-rules.md (single source of truth, mirrored as a catalog in CLAUDE.md § Hard Stop Rules).`
   - L11 동일 path reference 갱신.
   - hook 의 PowerShell here-string body (실제 stanza emission) 은 미터치 — hardcoded body 가 platform/hardstop-rules.md 와 동일 verbatim 유지 (drift detection mechanism shift 는 별 task).

4. **`platform/lint-remediation-message-standard.md` cross-ref 갱신** (1 line edit):
   - L34 `[CLAUDE.md § Hard Stop Rules](../CLAUDE.md#hard-stop-rules)` → `[platform/hardstop-rules.md](hardstop-rules.md)` (canonical body 의 새 위치).
   - CLAUDE.md catalog 도 함께 cross-ref 가능 (보조 — primary는 hardstop-rules.md).

5. **HARDSTOP-06/07 reference 자기 참조 점검**:
   - HARDSTOP-06 의 `[REFERENCE] CLAUDE.md § Source of Truth Priority + CLAUDE.md § Core Principles` — CLAUDE.md 안의 sibling section reference → verbatim 유지 (catalog 분리는 § Hard Stop Rules 자체만, § Core Principles / § Source of Truth Priority 는 변경 없음).
   - HARDSTOP-07 의 `[REFERENCE] CLAUDE.md § Task Rules + tasks/INDEX.md § Move Rules` — 동일, verbatim 유지.

## Out of Scope

- **Hook stanza body 의 외부 file 런타임 parsing**: hook 가 platform/hardstop-rules.md 를 런타임에 읽어서 stanza 생성하는 mechanism shift 는 별 task (drift detection 0 으로 reduce 가능 but substantial design change). 현재는 hook hardcoded body ↔ platform/hardstop-rules.md ↔ CLAUDE.md catalog 의 3-way manual sync 유지 (TASK-MONO-057 catalog 답습 — 같은 manual-sync 모델).
- **HARDSTOP fixture 의 stanza body 비교 강화**: 현재 `Assert-Stanza` 가 ID + decision + 4 block presence 만 검증 (body content 미검증). body drift 자동 검출은 별 task 후보.
- **Other CLAUDE.md sections 정리**: Recommending Tasks 마지막 단락 / Cross-Project Changes / Layer Rules 등 cosmetic cleanup 은 본 scope 외.
- **TEMPLATE.md mirror**: TEMPLATE.md 가 CLAUDE.md 의 일부 catalog 를 mirror 하는지 확인 후 필요 시 별 task (예상 = mirror 없음, TEMPLATE.md 는 Local Network Convention master + extraction guide).
- **ADR**: 본 task = "OpenAI Harness 패턴 재적용" — ADR-MONO-006 의 후속 (lint-remediation message standard 의 catalog↔body 분리). 별 ADR 없이 D4 OVERRIDE precedent (ADR-MONO-003a § D1.1) 답습.

# Acceptance Criteria

- [ ] `wc -l CLAUDE.md` ≤ 230 (현재 319, Hard Stop catalog 축약으로 ~98 lines 감축 + 미세 변동 허용).
- [ ] `wc -l platform/hardstop-rules.md` ≥ 130 (10 stanza × ~12 line + intro/anchor overhead).
- [ ] `grep -c "^### HARDSTOP-" CLAUDE.md` = 0 (기존 inline H3 stanza 모두 제거).
- [ ] `grep -c "^## HARDSTOP-" platform/hardstop-rules.md` = 10 (10 stanza H2 anchor).
- [ ] `grep -c "\\[VIOLATION\\] HARDSTOP-" platform/hardstop-rules.md` = 10 (10 stanza body intact).
- [ ] `grep -c "\\[VIOLATION\\] HARDSTOP-" CLAUDE.md` = 0 (catalog 만 남음).
- [ ] `grep "platform/hardstop-rules.md" CLAUDE.md` ≥ 1 (catalog → body cross-ref 존재).
- [ ] `.claude/hooks/__tests__/run-all.ps1` (5 fixture: HARDSTOP-01/03/05/09/10) 모두 PASS — hook stanza body 미터치 + fixture assertion shape 미터치 = 회귀 0.
- [ ] CLAUDE.md catalog 의 10 row 가 `platform/hardstop-rules.md` 의 10 H2 anchor 와 1:1 일치 (수동 diff 검증).
- [ ] `.claude/hooks/hardstop-detect.ps1` 의 docstring `Stanza body source` line 이 새 path 로 갱신됨.
- [ ] `platform/lint-remediation-message-standard.md` L34 의 cross-ref 가 새 path 로 갱신됨.
- [ ] CI: markdown-only path-filter 자연 검증 — `changes` job PASS / 다른 모든 job SKIP (axis A 패턴 답습, MONO-091/093~098 precedent).

# Related Specs

- [`CLAUDE.md`](../../../CLAUDE.md) § Hard Stop Rules (current location, soon catalog-only)
- [`platform/lint-remediation-message-standard.md`](../../../platform/lint-remediation-message-standard.md) (4-block format definition)
- [`platform/hardstop-rules.md`](../../../platform/hardstop-rules.md) (신설 — canonical 4-block body)
- [`.claude/hooks/hardstop-detect.ps1`](../../../.claude/hooks/hardstop-detect.ps1) (hook docstring 갱신)
- [`docs/adr/ADR-MONO-006-lint-remediation-as-agent-context.md`](../../../docs/adr/ADR-MONO-006-lint-remediation-as-agent-context.md) (rationale ADR)
- 메모리 reference: `project_b_common_rule_refactor_done.md` (TASK-MONO-057 CLAUDE.md split 첫 사례), `reference_openai_harness_engineering.md` § monorepo-lab 갭 매핑

# Related Contracts

없음 (production code / API / event contract 0 변경).

# Edge Cases

1. **Anchor naming**: `platform/hardstop-rules.md#hardstop-01` ↔ `## HARDSTOP-01 — ...` H2 — GitHub anchor 생성 규칙은 H2 text 의 lowercase + hyphenate. `HARDSTOP-01 — No PROJECT.md locatable` → `#hardstop-01--no-projectmd-locatable` 가 됨. catalog cross-ref 는 짧은 anchor `#hardstop-01` 만 쓰면 GitHub 가 first-match 로 resolve — 정상. 단, 추후 H2 text 변경 시 anchor drift 가능 → catalog row 의 cross-ref 만 anchor 짧게 (`#hardstop-NN`) 유지하고 H2 의 `— condition` 부분은 미터치.

2. **Hook hardcoded body 와 platform/hardstop-rules.md drift**: 본 task 는 첫 인스턴스 (CLAUDE.md inline body → platform/hardstop-rules.md verbatim 이동). 이후 hook body 변경 시 두 곳 다 갱신 필요 — manual sync 부담. drift 자동 검출은 별 task.

3. **catalog ↔ body sync**: CLAUDE.md catalog 의 condition 한 줄과 platform/hardstop-rules.md 의 stanza `[VIOLATION]` 한 줄이 의미상 동일해야 함. condition 변경 시 두 곳 갱신.

4. **HARDSTOP-06/07 cross-ref**: 본문이 `CLAUDE.md § X` 를 가리키는 부분은 평행 sibling section reference (catalog 분리는 § Hard Stop Rules 만, § Core Principles / § Task Rules 등은 CLAUDE.md 안에 그대로). 따라서 stanza body 의 `[REFERENCE] CLAUDE.md § Source of Truth Priority` 같은 라인은 verbatim 보존 — broken ref 안 됨.

5. **fixture 미검증 영역**: `Assert-Stanza` 는 stanza body 의 실 텍스트를 검증 안 함 (ID + 4-block presence + decision 만). 본 task 가 body 를 CLAUDE.md → platform/hardstop-rules.md verbatim 이동해도 fixture PASS. 단 hook 의 hardcoded body 는 미터치 — fixture 가 그 body 를 그대로 가드. 다음 hook body 변경 시 fixture 가 detect 못 하는 영역은 본 task 의 scope 외.

# Failure Scenarios

A. **CLAUDE.md catalog 의 anchor link 가 깨짐** (예: H2 text 변경 시 anchor 자동 drift): catalog 의 cross-ref `[hardstop-rules.md#hardstop-NN](platform/hardstop-rules.md#hardstop-NN)` 가 broken. 검출 = markdown link checker (`/refactor-spec all --dry-run` 의 dead-ref check) + 수동 클릭 검증.

B. **lint-remediation-message-standard.md L34 cross-ref 갱신 누락**: 새 path 로 안 갱신 시 standard 가 verbose CLAUDE.md anchor 를 가리킴 (현재 동일). 검출 = `grep "CLAUDE.md#hard-stop-rules" platform/lint-remediation-message-standard.md` exit 0.

C. **hook docstring 갱신 누락**: hook L8/L11 의 "Stanza body source = CLAUDE.md" 가 stale. 검출 = `grep "CLAUDE.md.*single source of truth" .claude/hooks/hardstop-detect.ps1` exit 0.

D. **stanza 본문 verbatim 보존 실패** (이동 중 typo / formatting drift): hook hardcoded body 와 platform/hardstop-rules.md 의 4-block 이 어긋남. 검출 = 본 task PR 내 수동 diff (CLAUDE.md before 의 4-block ↔ platform/hardstop-rules.md after 의 4-block 가 문자 단위 동일).

E. **TEMPLATE.md 또는 다른 shared file 이 CLAUDE.md § Hard Stop Rules 를 deep-link**: 별 cross-ref 가 깨질 수 있음. 검출 = `grep -rn "CLAUDE.md.*hard-stop\\|CLAUDE.md.*HARDSTOP\\|CLAUDE.md.*Hard Stop" platform/ rules/ .claude/ TEMPLATE.md docs/guides/` — 발견 시 같은 PR 에 갱신.

---

# Implementation Notes (작성 시 참고)

- 분석=Opus 4.7 / 구현 권장=Sonnet 4.6 (mechanical body move, single-file edit, low judgment). 단 catalog 작성은 condition 요약이라 Opus 도 OK.
- D4 OVERRIDE 적용 — refactor-spec / governance polish 의 연장선 (MONO-091/093 sibling, ADR-MONO-003a § D1.1).
- Lifecycle = ready → review 직접 (in-progress 우회, single-PR closure 패턴 답습 — MONO-094~098 precedent). 그러나 본 task 는 CLAUDE.md (전 세션 base context) 변경이라 spec PR 와 impl PR 분리도 합리적 — author 결정.
