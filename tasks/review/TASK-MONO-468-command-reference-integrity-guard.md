# Task ID

TASK-MONO-468

# Title

command 레이어 참조 정합 CI 가드 — `.claude/commands/*.md` dead-path/dead-link 자동 검출 (MONO-467 후속)

# Status

review

# Owner

monorepo (root tasks/ — shared `scripts/` + `.github/workflows/` + `.claude/commands/`)

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

- **origin**: `TASK-MONO-467` (2026-07-22 `/validate-rules` 스캔)에서 드러난 구조적 갭 — command 레이어는 **CI 가드 없는 표면**이라 `skills/INDEX.md` dead-path 가 수동 스캔 전까지 방치됐다. `/validate-rules` § 2-4/2-5 는 규칙을 적어 두지만 read-only·수동. 이 저장소가 세 번 대가를 치른 "손-유지 대응을 아무것도 진실과 대조 안 함" 클래스(MONO-360/363/451).
- **prerequisite for**: nothing. 후속 후보 = agent/skill 참조 정합으로 확대(별건 — 지금 확대하면 기존 드리프트에 day-one RED 위험).
- **execution constraint**: `scripts/` + `.github/workflows/ci.yml` + task lifecycle. classifier `.claude/hooks|settings.json` block 대상 아님(commands/ 도 통과). agent 가 edit+commit+push+merge 가능.
- **model**: 분석=Opus 4.8 / 구현=Opus 4.8 (가드 술어 설계 + FP frontier 신중 필요).

---

# Goal

`.claude/commands/*.md` 안의 파일 참조(마크다운 링크 + anchored 인라인 경로)가 실제로 resolve 되는지 매 PR 검사하는 CI 가드를 추가하여, MONO-467 이 수동으로 잡은 dead-reference 클래스를 **머지 전에** 자동 차단한다.

---

# Scope

## In Scope

1. **가드 스크립트 `scripts/check-command-reference-integrity.sh`** (신규).
   - **두 참조 형태** 검출: ① 마크다운 링크 `[text](target)` — 커맨드 파일 기준 상대 resolve(GitHub 렌더 방식), 펜스 코드블록 제외 + target 에 `/` 필수(illustrative bare-filename 제외); ② anchored 인라인 경로 `` `...` `` — 리포루트 앵커 prefix(`platform/`·`rules/`·`libs/`·`tasks/`·`docs/`·`scripts/`·`projects/`·`.claude/`·`.github/` + `.claude/` 전용 서브디렉토리명 `skills/`·`agents/`·`commands/`·`workflows/`·`config/`·`hooks/`)로 시작 + placeholder 메타문자 없음 → 리포루트 resolve.
   - **`skills/INDEX.md`(prefix 누락) = DRIFT + "did you mean '.claude/skills/INDEX.md'?" 힌트** — MONO-467 #1 을 정확히 재현·차단.
   - **`--selftest`**: extractor FP frontier 를 known-positive/negative 로 pin(placeholder `<service>`·glob `*`·project-relative `specs/`·bare word·URL·`#anchor`·펜스 다이어그램·illustrative 링크 전부 IGNORE) + no-match 파일이 `set -euo pipefail` 하 abort 안 하는지(MONO-442 트랩) pin.
   - vacuity 가드(커맨드 0건=vacuous pass 아닌 exit 2).
2. **ci.yml 배선 3곳**: filter output `command-refs`(raw, NOT code-changed AND) + pure-positive paths-filter(`.claude/commands/**`·`.claude/skills/**`·`.claude/agents/**`·`platform/**`·`rules/**`·스크립트) + job `command-reference-integrity`(bash -n → --selftest → guard).

## Out of Scope

- **Semantic 드리프트**(MONO-467 #2 우선순위 순서·#3 dispatch 문구·#4 anchor 라벨) — 영어 주장 의미 대조라 FP 생성기가 됨. `/validate-rules`(수동) 유지. 스크립트 헤더에 명시.
- **markdown `#anchor` slug 검증** — GitHub slug 알고리즘 코너(유니코드·이모지·중복 heading)로 day-one RED 위험. 미포함.
- **agent/skill 파일 참조 정합** — 같은 갭이나 확대 시 기존 드리프트 day-one RED 위험. 후속.

---

# Acceptance Criteria

- [ ] **AC-1**: `scripts/check-command-reference-integrity.sh` 가 `bash -n` 통과 + `--selftest` GREEN + 현재 트리(11 커맨드·115 참조)에서 **day-one GREEN**.
- [ ] **AC-2 (bite 증명)**: 커맨드에 `skills/INDEX.md`(bare) 주입 시 가드 RED + did-you-mean 힌트, revert 시 GREEN (mutation probe 로 검증).
- [ ] **AC-3 (FP 없음)**: 펜스 코드블록의 ASCII 다이어그램(`Agent[R-1](worktree-1)`)·illustrative 예시(`[Title](file.md)`)·placeholder 경로 전부 미검출.
- [ ] **AC-4**: ci.yml YAML 파싱 OK + `command-refs` filter raw(code-changed AND 아님) + job 이 --selftest 를 필수 first-step 로 실행.
- [ ] **AC-5 (scope-lock)**: `git diff origin/main` = 신규 스크립트 + ci.yml + task lifecycle 파일만.
- [ ] **AC-6**: 본 PR 에서 `command-reference-integrity` job 이 실제 실행되어 GREEN(스크립트 경로가 filter 매치 → job 발동).

---

# Related Specs

- `.claude/commands/*.md` (가드 대상 11 파일).
- `platform/lint-remediation-message-standard.md` / `/validate-rules` § 2-4/2-5 (규칙 출처).
- `.github/workflows/ci.yml` (배선), 기존 가드 정경 참조: `scripts/check-index-queue-drift.sh`·`check-controller-slice-naming.sh`·`check-adr-index-drift.sh`.

# Related Contracts

- None. CI / tooling only.

---

# Edge Cases

- **펜스 코드블록 내 링크** — GitHub 미렌더 → 링크 추출에서 제외(awk fence 토글). 다이어그램 `](worktree-1)` FP 10건이 첫 런에서 실증됨 → 수정.
- **illustrative bare-filename 링크**(`[Title](file.md)`, `[x](...)`) — target 에 `/` 필수 규칙으로 제외.
- **grep no-match + pipefail** — 링크/코드 없는 파일에서 `grep` exit 1 이 pipefail 전파→abort(MONO-442). `|| true` + no-match selftest 로 pin.
- **project-relative `specs/`·`apps/`** — 커맨드는 여러 프로젝트에서 실행되므로 리포루트 단일 파일 아님 → anchored 아님으로 미검출(FP 회피).

---

# Failure Scenarios

- **가드가 day-one RED** — 기존 참조가 실제로 깨졌다는 뜻(진짜 버그=보너스 수확) 또는 FP(predicate 정제). 첫 런은 GREEN 확인됨.
- **다른 파일 수정** → AC-5 fail; 본 task 는 스크립트 + ci.yml + lifecycle 한정.
- **filter 미발동으로 job skip** → AC-6 fail; 스크립트 경로가 filter 에 포함되어 본 PR 에서 발동 보장.

---

# Verification

- 2026-07-23, `task/mono-468-command-ref-integrity-guard` 브랜치 (off `main` @ 96e3bc040, MONO-467 close 후).
- 로컬 검증 완료: bash -n OK / --selftest OK / 라이브 11파일·115참조 GREEN / mutation probe(bare `skills/INDEX.md`) RED+힌트→revert GREEN / ci.yml YAML OK.
- `git diff origin/main --stat` = `scripts/check-command-reference-integrity.sh`(신규) + `.github/workflows/ci.yml` + task/INDEX.
- 3-dim merge 검증은 close chore 시. 본 PR 에서 `command-reference-integrity` job 실제 실행 GREEN 확인 예정(AC-6).
- 분석·구현=Opus 4.8.
