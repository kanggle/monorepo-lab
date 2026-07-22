# Task ID

TASK-MONO-469

# Title

.claude 참조 정합 가드를 agents 로 확대 + skills 는 측정으로 제외 (MONO-468 후속)

# Status

review

# Owner

monorepo (root tasks/ — shared `scripts/` + `.github/workflows/`)

# Task Tags

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

- **origin**: `TASK-MONO-468` 가 남긴 후속 노트("agent/skill 참조 정합으로 확대"). MONO-468 은 확대가 기존 드리프트에 day-one RED 위험 있다고 명시 → **측정 선행 필수**.
- **prerequisite for**: nothing.
- **execution constraint**: `scripts/` + `.github/workflows/ci.yml`. classifier block 대상 아님. agent edit+commit+push+merge 가능.
- **model**: 분석=Opus 4.8 / 구현=Opus 4.8 (predicate FP frontier 측정·판정 필요).

---

# Goal

MONO-468 의 참조 정합 가드를 `.claude/agents/**/*.md` 로 확대하고, **측정 결과에 따라** `.claude/skills/**/SKILL.md` 는 명시적으로 제외(FP 지뢰밭)하여, command+agent 레이어를 매 PR 자동 검증 표면으로 만든다.

---

# Scope

## In Scope

1. **스크립트 rename + 일반화**: `check-command-reference-integrity.sh` → `check-claude-reference-integrity.sh`. 모집단 = `.claude/commands/*.md` + `.claude/agents/**/*.md`(README 제외). extractor/resolver 로직 동일.
2. **predicate 경화**: `ANCHOR_RE` 에서 `config`·`hooks` 제거 — 편재 코드 디렉토리명(Spring `config/` 패키지·React `hooks/` 폴더)과 충돌. 명시 `.claude/config/`·`.claude/hooks/` 형태는 `.claude` 루트로 계속 검사. selftest 에 negative 케이스 pin.
3. **skills 제외 = 측정 근거 문서화**: 스크립트 헤더 + ci.yml 주석에 "skills 스캔 시 11건 전부 FP(코드 디렉토리 `config/`/`hooks/`·생성-예정 예시 `example-frontend.yml`·산문 ellipsis `projects/...`; 2건은 REAL 루트 `.github/`·`projects/` 하라 앵커 조정으로도 구제 불가 = 장르 문제)" 기록. skills 참조 정합은 `/validate-rules`(수동) 유지.
4. **ci.yml 배선 rename**: filter/output `command-refs`→`claude-refs`, job `command-reference-integrity`→`claude-reference-integrity`, script 경로, job name. `.claude/skills/**` 는 trigger 로 유지(command/agent 가 skill 파일을 *가리키므로* skill rename 이 참조를 깰 수 있음).

## Out of Scope

- **skills 스캔** — 측정으로 제외(위). 억지 FP 억제 시도 금지(teaching-doc prose 파싱 = FP 생성기).
- **markdown `#anchor` slug 검증** — day-one RED 위험(GitHub slug 코너). 미포함(MONO-468 과 동일).
- **semantic 드리프트** — `/validate-rules` 유지.

---

# Acceptance Criteria

- [ ] **AC-1 (측정 선행)**: 확대 전 commands+agents+skills 전수 실행 → 결과를 FP/real 판정. **skills 11건 전부 FP 확인**(코드 디렉토리·생성예정 예시·ellipsis), **agents 0건**.
- [ ] **AC-2**: `check-claude-reference-integrity.sh` 가 bash -n·`--selftest`·라이브(commands+agents 24 docs·151 refs) **day-one GREEN**.
- [ ] **AC-3 (bite)**: agent 에 `platform/nonexistent.md` 주입 시 RED, revert 시 GREEN (mutation probe).
- [ ] **AC-4 (predicate 경화)**: bare `config/`·`hooks/query-keys.ts` 미검출(selftest negative pin), 명시 `.claude/config/` 는 계속 검출.
- [ ] **AC-5**: ci.yml YAML OK + rename 완결(구 `command-refs`/`command-reference-integrity`/구 스크립트 경로 잔여 0) + `claude-refs` raw filter.
- [ ] **AC-6 (scope-lock)**: `git diff origin/main` = 스크립트 rename + ci.yml + task lifecycle 만.
- [ ] **AC-7**: 본 PR 에서 `claude-reference-integrity` job 실제 실행 GREEN(스크립트 경로가 filter 매치).

---

# Related Specs

- `scripts/check-claude-reference-integrity.sh`(rename 대상) / `.github/workflows/ci.yml`.
- `.claude/commands/*.md` + `.claude/agents/**/*.md`(가드 대상) / `.claude/skills/**/SKILL.md`(제외, trigger 유지).
- `/validate-rules` § 2-4/2-5(규칙 출처, skills 잔여 커버리지).

# Related Contracts

- None. CI / tooling only.

---

# Edge Cases

- **skills 의 코드 디렉토리 참조** `config/`·`hooks/` — `.claude/config`·`.claude/hooks` 와 충돌 → ANCHOR_RE 에서 제거로 해소(commands 는 코드 구조 논하지 않아 이전엔 안 걸렸음).
- **REAL 루트 하 illustrative 예시** `.github/workflows/example-frontend.yml`·`projects/...` — 앵커 조정으로 구제 불가 = skills 제외의 근거(장르 문제, teaching doc).
- **agents `skills:` frontmatter** — YAML 리스트(backtick/링크 아님)라 extractor 미추출 → FP 없음(short-form `backend/x` 도 `backend/` 비앵커라 미검출, 의도된 out-of-scope).

---

# Failure Scenarios

- **skills 억지 포함** → day-one RED 또는 FP 억제 로직 비대화. 측정이 제외를 지시함.
- **다른 파일 수정** → AC-6 fail.
- **filter 미발동 job skip** → AC-7 fail; 스크립트 경로가 filter 포함.

---

# Verification

- 2026-07-23, `task/mono-469-claude-ref-integrity-agents-skills` 브랜치 (off `main` @ 1a6198d75, MONO-468 close 후).
- **측정 실행**: 확대 초판(commands+agents+skills)=11 findings, 전수 판정 = **전부 FP**(config/hooks 코드디렉토리·example-*.yml 생성예정·projects/... ellipsis), agents=0. → skills 제외 + config/hooks 앵커 제거로 확정.
- 로컬 재검증: bash -n OK / --selftest OK / 라이브 24 docs·151 refs GREEN / agent mutation probe RED→revert GREEN / ci.yml YAML OK / rename 잔여 0.
- 3-dim merge 검증은 close chore 시. 본 PR 에서 `claude-reference-integrity` job 실제 실행 GREEN 확인 예정(AC-7).
- 분석·구현=Opus 4.8.
