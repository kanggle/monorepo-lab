# Task ID

TASK-MONO-326

# Title

CI 워크플로 DRY 리팩토링 — composite action + reusable workflow 로 `ci.yml` / `nightly-e2e.yml` 구조적 중복 제거 (잡 이름 · path-filter 의미 보존)

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

`.github/workflows/` 3개 파일(`ci.yml` 1,910줄 / `nightly-e2e.yml` 1,113줄 / `federation-hardening-e2e.yml` 511줄)은 잡 골격이 대량으로 중복되어 있다. 신규 프로젝트 추가 시 매번 boot-jars / integration / e2e 잡을 복제해야 하고, 복제 누락이 nightly 드리프트로 이어진 실적이 있다(memory `project_nightly_e2e_service_addition_drift` — ecommerce 추가 시 nightly-e2e.yml 4곳 갱신 누락으로 nightly RED).

관측된 중복 축:

1. **스텝 보일러플레이트** — `Checkout → Set up JDK 21 (Temurin) → Set up Gradle`가 15+ 잡에서 동일 반복. 일부 잡은 뒤에 `Verify Docker (docker info)`가 붙고, 일부(boot-jars/frontend)는 안 붙음.
2. **integration-test 잡 8개** — `integration-tests`(wms) / `iam-` / `fan-` / `scm-` / `finance-` / `erp-` / `platform-console-bff-` / `ecommerce-integration-tests`가 `checkout→JDK→Gradle→docker info→:integrationTest→upload` 골격 동일. 차이는 ①`if:` 프로젝트 flag ②gradle task 목록 ③아티팩트 이름/경로 3가지뿐.
3. **e2e 잡** — `e2e-tests`(wms) / `fan-platform-e2e` / `scm-platform-e2e` 가 `download jars → restore paths(mv) → docker info → build java-service-base → build service images → e2eSmokeTest → upload` ~100줄 블록을 공유. `nightly-e2e.yml`의 `wms-platform-e2e-full` / `fan-platform-e2e-full` / `scm-platform-e2e-full` / `iam-e2e-full`이 **같은 패턴을 smoke→full 만 바꿔 재복제**(파일 간 중복 ~7개).
4. **boot-jars 잡 4개** — wms / ecommerce / fan / scm 가 `bootJar 목록 + upload`만 다르고 골격 동일.

이 task 는 GitHub Actions 의 **composite action** 과 **reusable workflow(`workflow_call`)** 로 위 중복을 수렴한다. 단, **잡 이름(= branch protection 의 required status check 이름)** 과 **path-filter 활성 의미(어떤 PR 에서 어떤 잡이 run/skip 되는가)** 를 100% 보존한다.

---

# Scope

## In Scope (단계적 — Phase 별로 독립 PR 가능)

**Phase 1 (저위험 · 잡 이름 불변) — composite action**

- 신규 `.github/actions/setup-java-gradle/action.yml` composite action 생성: `actions/checkout@v4` + `actions/setup-java@v4`(temurin, 21) + `gradle/actions/setup-gradle@v4` 3스텝 캡슐화.
- `ci.yml` 내 모든 JVM 잡(build-and-test, boot-jars ×4, integration ×8, e2e ×3, iam-platform-e2e-smoke)의 해당 3스텝을 composite 호출 1스텝으로 치환. `Verify Docker` 스텝은 composite 밖에 그대로 유지(잡별 유무 상이).
- `nightly-e2e.yml` / `federation-hardening-e2e.yml` 의 동일 3스텝도 같은 composite 로 치환.
- **잡 이름 · 잡 개수 · `if:` 조건 무변경** → required status check 영향 0.

**Phase 2 (중위험 · 잡 이름 보존 매핑 필요) — integration reusable workflow**

- 신규 `.github/workflows/_integration.yml`(reusable, `on: workflow_call`) 생성. inputs: `job-name`(name 보존용), `if-flags`(활성 조건은 호출측 `if:`로 유지), `gradle-tasks`, `artifact-name`, `report-glob`, `repository-guard`.
- `ci.yml` 의 8개 integration 잡을 reusable 호출로 치환. 각 호출 잡의 `name:` 을 **기존 문자열과 정확히 동일**하게 유지(예: `Integration (iam, Testcontainers)`).
- 호출측 `if:` 는 기존 그대로(reusable 내부로 옮기지 않음 — path-filter 의미 보존이 명시적으로 검증 가능하도록).

**Phase 3 (고위험 · 최고효과) — e2e reusable workflow**

- 신규 `.github/workflows/_platform-e2e.yml`(reusable) 생성. inputs: `project-dir`, `services`(리스트/JSON), `image-prefix`, `gradle-e2e-task`(`e2eSmokeTest` | `e2eFullTest`), `boot-jars-artifact`, `job-name`, `timeout`.
- `ci.yml` 의 `e2e-tests` / `fan-platform-e2e` / `scm-platform-e2e` 를 reusable 호출로 치환.
- `nightly-e2e.yml` 의 `wms-platform-e2e-full` / `fan-platform-e2e-full` / `scm-platform-e2e-full` 를 **동일 reusable** 호출로 치환(smoke↔full 은 `gradle-e2e-task` input 차이).
- 각 호출 잡의 `name:` 을 기존과 정확히 일치.

## Out of Scope

- `iam-platform-e2e-smoke` / `iam-e2e-full`(ComposeFixture 방식, 수동 이미지 빌드 없음) 의 reusable 통합 — 패턴이 달라 별도 판단(Phase 3 후 후보).
- `frontend-e2e-smoke` / `frontend-unit-tests` / `frontend-checks` 의 Node/pnpm 스텝 composite 화 — 별도 후보(JVM 축과 무관).
- `observability-footprint` 잡 — 단독 특수 잡, 손대지 않음.
- `changes`(paths-filter) 잡의 filter 정의 변경 — **금지**. negation quirk(MONO-074/075) 재도입 위험. 본 task 는 filter 를 읽기만 하고 수정하지 않는다.
- gradle task 자체 / 테스트 코드 변경.
- 3 Phase 를 반드시 한 PR 로 묶는 것 — Phase 별 독립 PR 허용(feedback: PR 묶음 케이스별 자유).

---

# Acceptance Criteria

- [ ] **Phase 1**: `.github/actions/setup-java-gradle/action.yml` 생성, `ci.yml`/`nightly-e2e.yml`/`federation-hardening-e2e.yml` 의 JVM 잡이 composite 호출로 치환. `git diff` 상 잡 이름 · `if:` · 잡 개수 무변경.
- [ ] **Phase 1 self-CI**: 워크플로 self-change → 전 잡 activate → full pipeline GREEN(회귀 0). composite 치환 전후 각 잡의 실행 스텝 semantics 동일.
- [ ] **Phase 2**: `_integration.yml` reusable 생성, 8개 integration 잡이 호출로 치환. 치환된 각 잡의 required-check `name:` 문자열이 main 브랜치 보호 설정의 기존 check 이름과 **정확히 일치**(리네이밍 0).
- [ ] **Phase 3**: `_platform-e2e.yml` reusable 생성, ci.yml 3개 smoke + nightly 3개 full 잡이 동일 reusable 호출로 치환. e2e 잡 `name:` 보존.
- [ ] **의미 보존 회귀 대조**: 각 Phase PR 에서, 리팩토링 전후로 "동일 파일 변경 셋에 대해 동일 잡 집합이 run/skip 되는가"를 CI 매트릭스 실측으로 확인(최소 ①markdown-only PR = 전 skip ②본 워크플로 self-change = 전 run 두 케이스).
- [ ] **required status check 무손상**: 잡 이름 변경으로 인해 main 브랜치 보호의 required check 가 "missing/expected" 상태로 빠지지 않음(리네이밍한 경우 브랜치 보호 설정도 동반 갱신 — 단, 본 task 는 리네이밍 0 를 목표).
- [ ] `tasks/INDEX.md` `## done` 에 Phase 별 outcome 1-line(각 close chore 시점).

---

# Related Specs

- `docs/guides/monorepo-workflow.md` § CI Job Areas (path-filter mechanics — human ref, SoT 아님)
- `tasks/done/TASK-MONO-074-ci-e2e-skip-and-force-full-flags.md` (path-filter 의미 — 본 task 가 보존해야 할 대상)
- `tasks/done/TASK-MONO-075-fix-mono-074-markdown-skip-paths-filter-quirk.md` (negation quirk — 재도입 금지 근거)
- `tasks/done/TASK-MONO-307-ecommerce-integration-ci-lane.md`, `tasks/done/TASK-MONO-319-*.md` (integration lane 추가 패턴 — reusable 로 수렴할 대상)
- Memory `project_ci_path_filter_074_075_quirk`, `project_nightly_e2e_service_addition_drift`

# Related Skills

N/A — CI infrastructure edit (YAML + composite/reusable workflow authoring).

---

# Related Contracts

None.

---

# Target Service

N/A — shared CI configuration only (`.github/` 는 monorepo-level 공유 경로).

---

# Architecture

N/A — 단일 도메인 의사결정 없음. GitHub Actions 표준 재사용 프리미티브(composite action + `workflow_call`) 적용. ADR 동반하지 않음(구조적 리팩토링, 동작 의미 불변).

---

# Implementation Notes

## 불변식 (반드시 지킬 것)

1. **잡 이름 보존** — `jobs.<id>.name` 문자열은 branch protection required-check 의 키다. reusable 호출 잡은 `name:` 을 기존과 **byte-identical** 로 지정. 부득이 변경 시 → GitHub 브랜치 보호 설정의 required checks 목록도 동반 갱신해야 하며, 이는 리포 관리자 권한이 필요하므로 **기본 목표는 리네이밍 0**.
2. **path-filter 활성 조건은 호출측 `if:` 로 유지** — reusable workflow 내부로 `if:` 를 옮기지 않는다. `needs.changes.outputs.*` gating 이 diff 에서 그대로 보이도록 하여 의미 보존을 눈으로 검증 가능하게.
3. **`changes` 잡/필터 무수정** — MONO-074/075 의 pure-positive `code-changed` + outputs-AND 설계는 negation quirk 를 의도적으로 회피한 것. 절대 손대지 않음.
4. **`Verify Docker` 는 composite 밖** — docker info 스텝은 잡마다 유무가 다르므로 composite 에 포함하지 않는다.

## reusable workflow 제약

- `workflow_call` 은 호출측 `needs.<job>.outputs` 를 input 으로 전달받을 수 있으나, `if:` 는 호출 잡(caller) 레벨에서 평가된다. 따라서 gating 은 caller 에 남기고, reusable 은 "실행 본문"만 담는다.
- reusable workflow 는 `secrets: inherit` 필요 여부 확인(현재 e2e/integration 잡은 secret 미사용으로 보이나 실측).
- `github.repository == 'kanggle/monorepo-lab'` 가드는 caller `if:` 에 유지(포트폴리오 추출 리포에서 skip).

## 검증 어려움

- 워크플로 변경은 로컬 dry-run 불가에 가깝다(actionlint 로 정적 검증 + push 후 CI 실측이 권위). `actionlint` 가 있으면 pre-push 로 YAML/표현식 오류를 잡는다(없으면 self-CI 가 catch).
- Phase 별로 push → CI 매트릭스 관측 → 리팩토링 전 잡 목록과 대조하는 루프.

---

# Edge Cases

- **composite action 내부 `docker info` 누락**: boot-jars/frontend 잡은 원래 docker info 가 없음 → composite 에 넣으면 안 됨. 넣었을 경우 불필요 스텝이 붙지만 기능은 무해(그래도 semantics 보존 위해 제외).
- **reusable workflow 의 `runs-on` 위치**: reusable 내부 잡에서 `runs-on` 지정 → caller 는 `uses:` 만. timeout/runner 를 input 으로 뺄지 고정할지 결정.
- **artifact 이름 충돌**: 동일 reusable 을 여러 프로젝트가 호출 → `artifact-name` input 으로 유니크 보장(현재 이름 그대로 전달).
- **nightly 와 ci 가 같은 reusable 호출**: `gradle-e2e-task` = `e2eSmokeTest`(ci) vs `e2eFullTest`(nightly) 분기. full task 가 nightly 에 존재하는지 gradle 측 실재 확인(ADR-MONO-010/011 — 일부 "not yet wired" 주석 존재).
- **잡 이름에 특수문자**: 기존 name 에 괄호/쉼표 포함(`Integration (iam, Testcontainers)`). YAML 인용 정확히.
- **federation-hardening-e2e.yml 의 스텝 미세 차이**: 다른 두 파일과 JDK/Gradle 스텝이 정말 동일한지 확인 후에만 composite 적용(미세 차이 있으면 제외).

---

# Failure Scenarios

- **required check 이름 변경 → main merge 영구 block**: reusable 치환 시 잡 이름이 조금이라도 바뀌면 branch protection 이 해당 check 를 영원히 기다림(pending) → 어떤 PR 도 머지 불가. 완화: 리네이밍 0 목표 + 각 Phase PR 을 머지 전에 "checks 목록"을 기존과 대조.
- **actionlint/CI 표현식 오류**: reusable input 참조 오타 → 워크플로 자체가 invalid → 전 잡 실패. 완화: self-CI 가 즉시 catch, Phase 별 소단위 PR.
- **의미 드리프트**: gating `if:` 를 reusable 내부로 잘못 옮겨 어떤 프로젝트 PR 에서 잡이 skip 되어야 하는데 run(또는 반대). 완화: `if:` 를 caller 에 유지 + 회귀 대조 AC.
- **negation quirk 재발**: `changes` 필터를 "정리"하려다 negation 재도입 → markdown-only PR 이 다시 e2e 트리거. 완화: filter 무수정(Out of Scope 명시).
- **Phase 3 이 nightly 를 깨뜨림**: nightly full 잡과 ci smoke 잡의 이미지 빌드/경로 restore 미세 차이를 reusable 이 흡수 못 함 → nightly RED(관측 지연). 완화: Phase 3 을 nightly 관측 1 사이클 후 확정, `project_nightly_e2e_service_addition_drift` 체크리스트 대조.

---

# Test Requirements

- **Self-CI (각 Phase PR)**: `.github/workflows/**` 변경 → `workflows` flag → full pipeline. 전 잡 GREEN 이어야 머지.
- **회귀 대조**: markdown-only PR(전 skip) + self-change PR(전 run) 두 케이스의 잡 집합을 리팩토링 전과 대조.
- **actionlint**(가용 시): pre-push 정적 검증.
- **Phase 3 nightly 관측**: 머지 후 최소 1 nightly 사이클 GREEN 확인(cron '0 18 * * *' UTC).

---

# Definition of Done

- [ ] Phase 1 composite action 도입 + JVM 잡 치환, self-CI GREEN.
- [ ] Phase 2 integration reusable + 8잡 치환, 잡 이름 보존 확인.
- [ ] Phase 3 e2e reusable + ci 3 smoke / nightly 3 full 치환, 잡 이름 보존 + nightly 1 사이클 GREEN.
- [ ] 각 Phase 회귀 대조(run/skip 집합) 기록.
- [ ] `tasks/INDEX.md` `## done` Phase 별 outcome entry.
- [ ] task 파일 `Status: ready → review → done`(Phase 전량 완료 시).
- [ ] memory: reusable-workflow 도입으로 `project_nightly_e2e_service_addition_drift` 완화 사실 반영(신규 프로젝트 추가 절차 간소화).

---

# Progress (2026-07-04, PR #2201, branch mono-326-ci-workflow-dry)

- **Phase 1 ✅ merged-pending** — `.github/actions/setup-java-gradle` composite; ci.yml 17 JVM 잡 치환. self-CI 22/22 GREEN. ci.yml −136.
- **Phase 2 ✅ merged-pending** — `.github/workflows/_integration.yml` reusable; 8 integration 잡 → 콜러. self-CI 22/22 GREEN(1 flake rerun: ecommerce IT SQLState 08001, 무관). ci.yml −135. 체크명 `Integration (…) / integration`.
- **Phase 3a ✅ merged-pending** — `.github/workflows/_platform-e2e.yml` reusable(`services` JSON 구동); ci.yml e2e smoke 3잡(wms/fan/scm) → 콜러. self-CI 22/22 GREEN(3 e2e 실제 실행). ci.yml −207. 체크명 `E2E (…) / e2e`.
- **Phase 3b ⏳ 구현완료·nightly 런타임 검증대기** — reusable에 `boot-jars-mode`(download|build) 추가; nightly-e2e.yml e2e-full 3잡 → 동일 reusable(mode=build). nightly는 `pull_request` 미트리거 → mode=download는 PR에서 재검증 GREEN, **mode=build는 머지 후 첫 nightly cron(UTC 18:00)이 권위 검증**(사용자 결정 2026-07-04). nightly −130.

누적: ci.yml 1,910→1,432(−478, −25%), nightly 1,113→983(−130). main 무보호·ruleset 없음 확인(reusable 체크명 접미사 무해).

발견(별건, 미수정): `projects/platform-console/apps/console-web/tests/unit/ledger-api.test.ts:579` — wall-clock 의존 flake(`not.toContain('13.5')`가 로그 타임스탬프 `…13.5xx`에 걸림). 별도 fix task 후보.

Close 조건: 머지 → nightly cron 1사이클 wms/fan/scm-platform-e2e-full GREEN 확인 → review/→done/ + INDEX.md done entry.

---

# Provenance

Surfaced 2026-07-04 사용자 세션 — e2e path-filter gating 논의 직후 "리팩토링 할 부분 확인" 요청. ci.yml/nightly-e2e.yml/federation-hardening-e2e.yml 3파일 ~3,500줄에서 boot-jars/integration/e2e 잡의 구조적 중복 관측. 직접 선행 = 없음(신규). 보존 대상 = TASK-MONO-074/075(path-filter 의미) + memory `project_nightly_e2e_service_addition_drift`.

분석=Opus 4.8 / 구현 권장=Opus (잡 이름 · path-filter 의미 보존 검증이 걸린 cross-cutting CI 리팩토링; required-check 결합으로 되돌리기 어려움. Phase 1 composite 는 Sonnet 로도 충분하나 Phase 2/3 reusable 통합은 Opus 권장).
