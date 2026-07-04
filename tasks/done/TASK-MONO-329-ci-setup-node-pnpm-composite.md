# Task ID

TASK-MONO-329

# Title

CI `setup-node-pnpm` composite action — 프런트 잡의 pnpm+Node 셋업 중복 제거 + 버전 단일화 (MONO-326 setup-java-gradle의 Node 판)

# Status

done

# Owner

monorepo

# Task Tags

- ci
- chore

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

# Goal

`TASK-MONO-326`이 JVM 쪽 셋업을 `setup-java-gradle` composite로 단일화했으나 **프런트(Node/pnpm) 쪽은 out-of-scope로 남겼다.** `pnpm/action-setup@v4`(v9.15.0) + `actions/setup-node@v4`(node 20, pnpm 캐시) 블록이 워크플로 전반에 반복된다(전 파일 합 6곳 uniform + 1곳 no-cache 변이).

이 task는 `setup-java-gradle`의 Node 판인 **`.github/actions/setup-node-pnpm` composite**를 만들어 그 4줄 블록을 1스텝으로 접고, **Node20/pnpm9.15.0 버전을 단일 소스화**한다(버전 올릴 때 N곳→1곳). MONO-326 Phase 1과 동일하게 **ci.yml 프런트 잡부터**(PR self-CI 검증 가능) 적용한다.

---

# Scope

## In Scope

- `.github/actions/setup-node-pnpm/action.yml` composite 생성: pnpm 9.15.0 + Node 20 + pnpm 캐시. `cache-dependency-path`는 잡마다 다르므로 **입력**으로.
- `ci.yml`의 프런트 3잡(`frontend-unit-tests` / `frontend-e2e-smoke` / `frontend-checks`)의 `Set up pnpm` + `Set up Node 20` 2스텝 → composite 호출 1스텝으로 치환. `checkout`은 잡에 유지(로컬 composite 로드 전제).
- 잡 이름·`if:` 게이팅·잡 개수 무변경 → 동작 보존.

## Out of Scope

- **nightly-e2e.yml / federation-hardening-e2e.yml의 Node 셋업(2 uniform + 1 no-cache 변이 + 1 fed)** — `pull_request` 미트리거라 PR 검증 불가. MONO-326이 JVM composite도 같은 이유로 ci.yml만 했음. → **별도 "두 composite(java-gradle + node-pnpm)를 nightly/fed로 확장" 패스**로 유예.
- nightly 782 블록(캐시 없는 `setup-node` 변이) — composite 규격(cache 필수)과 달라 손대지 않음.
- `pnpm install` 스텝(13×) — working-dir·frozen/no-frozen 제각각이라 composite 이득 적음. 제외.
- reusable workflow(프런트 잡은 하는 일이 이질적이라 부적합).

---

# Acceptance Criteria

- [ ] `.github/actions/setup-node-pnpm/action.yml` 생성(pnpm 9.15.0 + Node 20 + cache, `cache-dependency-path` 입력).
- [ ] `ci.yml` 프런트 3잡의 pnpm+Node 2스텝이 composite 호출로 치환, 각 잡의 `cache-dependency-path` 값 보존(unit/e2e-smoke=3경로, checks=2경로).
- [ ] 잡 이름·`if:`·개수 무변경. `ci.yml`에 잔존 `pnpm/action-setup@v4` = 0(프런트 3잡 기준), composite 참조 = 3.
- [ ] self-CI GREEN — `frontend-unit-tests`·`frontend-e2e-smoke`·`frontend-checks` 3잡이 composite로 정상 실행(동작 보존 입증).
- [ ] js-yaml로 ci.yml + action.yml 파싱 유효.

---

# Related Specs

- `tasks/done/TASK-MONO-326-ci-workflow-dry-refactor.md` (setup-java-gradle composite — 이 task의 JVM 짝, 동일 패턴)
- Memory `project_mono_326_ci_workflow_dry`

# Related Skills

N/A — CI YAML 편집.

---

# Related Contracts

None.

---

# Target Service

N/A — shared CI configuration only.

---

# Architecture

N/A — GitHub Actions composite action(로컬). 동작 불변, ADR 없음.

---

# Implementation Notes

- **로컬 composite = checkout 선행 필수** — action 파일이 디스크에 있어야 로드됨. checkout은 잡에 남김(setup-java-gradle과 동일).
- `cache-dependency-path`는 멀티라인 문자열 가능 → `${{ inputs.cache-dependency-path }}`로 그대로 전달(setup-node가 개행 분리).
- ci.yml의 `frontend-unit-tests`·`frontend-e2e-smoke` 셋업 블록은 3경로로 **byte-동일** → 한 번에 치환 가능. `frontend-checks`는 2경로로 별도.
- 로컬 검증: `npx --yes js-yaml`로 파싱만(python·jq 부재). 실검증=CI.

---

# Edge Cases

- 잡마다 `cache-dependency-path` 다름(3/3/2 경로) → 입력으로 보존.
- nightly 782 블록은 cache 없음(변이) → composite 대상 아님(out-of-scope).
- checkout 누락 시 로컬 composite 로드 실패 → 모든 프런트 잡이 checkout 보유 확인.

---

# Failure Scenarios

- composite 경로 오타/cache 입력 누락 → 프런트 잡 실패, self-CI가 즉시 catch.
- 캐시 경로 변질 → 캐시 미스(성능만, 기능 무해)지만 값 보존으로 회피.
- 버전 문자열 오기입(9.15.0/20) → composite 단일 소스라 한 곳만 확인하면 됨(이득의 이면).

---

# Test Requirements

- Self-CI(workflows flag → full pipeline): `frontend-unit-tests`·`frontend-e2e-smoke`·`frontend-checks` GREEN.
- js-yaml 파싱.

---

# Definition of Done

- [ ] composite 생성 + ci.yml 프런트 3잡 치환.
- [ ] self-CI GREEN(프런트 3잡 동작 보존).
- [ ] `tasks/INDEX.md` done entry(close chore 시).

---

# Provenance

Surfaced 2026-07-04 — MONO-326(CI 본문 DRY) 후 사용자와 "메뉴/기능 검증(unit·component·integration) 층 리팩토링" 논의. integration은 MONO-326 Phase 2에서 이미 reusable화됨 → 남은 건 프런트 Node/pnpm 셋업 중복(7곳, 6 uniform). setup-java-gradle의 Node 판 composite로 결정. ci.yml부터(검증 가능), nightly/fed는 유예. task-id: 326 mine·327 타세션(ADR-045)·328 mine(backlog) → 329.

분석=Opus 4.8 / 구현 권장=Sonnet (mechanical composite 추출 + 잘 정의된 AC; setup-java-gradle에서 검증된 패턴).
