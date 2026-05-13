# Task ID

TASK-MONO-078

# Title

ADR-MONO-010 Phase 2 impl 3차 — gap `@Tag("e2e")` precedent 도입 + 5 e2e class `@Tag("smoke")` / `@Tag("full")` 적용 + `tests/e2e/build.gradle` default `test` task 에 `excludeTags 'full'` 추가

# Status

review

# Owner

monorepo

# Task Tags

- ci
- e2e
- testing
- gradle
- impl

---

# Goal

ADR-MONO-010 (ACCEPTED 2026-05-13, PR #428 `cdebdff2`) 의 **D5 step 4** 단독 이행. gap (global-account-platform) 의 e2e 5 class 어디에도 `@Tag("e2e")` umbrella 가 없는 첫 service — 본 task 가 **two-step 마이그레이션** 수행: (a) 5 class 에 `@Tag("e2e")` precedent 도입, (b) 각 class 에 `@Tag("smoke")` × 2 + `@Tag("full")` × 3 분류 적용.

또한 gap `tests/e2e/build.gradle` 은 fan/scm/wms 와 달리 **default `test` task 가 곧 e2e suite** (별도 `e2eTest` 미정의, `ComposeFixture` 기반 docker-compose 직접 실행 방식). PR #428 이 도입한 `e2eSmokeTest` / `e2eFullTest` task family 를 gap 에서도 등록하되, default `test` task 는 보존 + `excludeTags 'full'` 추가 (smoke 와 의미상 동등).

provenance: TASK-MONO-076 close chore 2026-05-13 의 Post-Review Scope Narrow 결정 — gap 의 unique 패턴 (`@Tag("e2e")` 부재, `ComposeFixture` 기반) 때문에 fan/scm/wms 와 다른 변경 면적이 발생, 별도 task 로 분리.

---

# Scope

## In Scope

### A. `@Tag("e2e")` umbrella precedent 도입

gap 의 5 e2e class 어디에도 `@Tag("e2e")` 없음. 4 service e2e module 의 일관성을 위해 도입.

**옵션 검토**:

- **A1 (권장)**: `E2EBase.java` 에 `@Tag("e2e")` 클래스 어노테이션. 모든 subclass 자동 inherit. fan/scm 의 `*E2ETestBase` 패턴과 정렬.
- **A2**: 5 e2e class 각각에 직접 `@Tag("e2e")` 추가. base class 미터치. 더 명시적이지만 boilerplate 5건.

**채택**: A1 (E2EBase 단일 변경 + 5 subclass inherit). `projects/global-account-platform/tests/e2e/src/test/java/com/example/e2e/E2EBase.java` 에 `@Tag("e2e")` + `org.junit.jupiter.api.Tag` import 추가.

### B. 5 e2e class 의 `@Tag("smoke")` / `@Tag("full")` 적용 (D5 step 4)

ADR § 1.2 audit 표 그대로:

| Class | Tag | 근거 (ADR § 1.2) |
|---|---|---|
| `GoldenPathE2ETest` | `@Tag("smoke")` | flagship cross-service flow (JWKS → enrollment → TOTP → refresh → logout), S1+S3+S4 |
| `TenantProvisioningE2ETest` | `@Tag("smoke")` | multi-tenant invariant (S1, 대표 cross-service) |
| `RefreshReuseDetectionE2ETest` | `@Tag("full")` | F5 security edge case (refresh→reuse→chain-invalidate) |
| `DlqHandlingE2ETest` | `@Tag("full")` | F5 DLQ routing + observability |
| `CrossServiceBulkLockE2ETest` | `@Tag("full")` | F3 cross-service projection + awaitility poll |

각 class 에 class-level annotation + `org.junit.jupiter.api.Tag` import 추가.

### C. `build.gradle` 3-task family 등록 + default `test` 의 excludeTags 'full' 추가 (D2 + gap 특수)

`projects/global-account-platform/tests/e2e/build.gradle`:

- 기존 default `test` task 의 `useJUnitPlatform()` 호출을 `useJUnitPlatform { excludeTags 'full' }` 로 변경 — default `test` 는 smoke 와 의미상 동등 (full 제외).
- **이유**: gap 의 CI 가 `gap-integration-tests` job 에서 default `test` task 를 호출할 가능성 + 로컬 dev 의 `./gradlew :…:test` 호출 시 full 의 long-running edge case 제외. PR #428 의 fan/scm 패턴 (default `test` 가 no-op or unit-only) 과 차이 — gap 의 default `test` 가 곧 e2e suite 라는 historical 결정 유지하면서 partition 만 추가.
- 신규 `tasks.register('e2eSmokeTest', Test) { useJUnitPlatform { includeTags 'smoke' } … }` + `tasks.register('e2eFullTest', Test) { useJUnitPlatform { includeTags 'full' } … }` 등록 — fan/scm/wms 의 `baseE2eConfig` closure 패턴 답습.
- 기존 default `test` task 의 모든 설정 (`junit.jupiter.execution.timeout.default = 5m`, `COMPOSE_PROJECT_NAME = gap-e2e`, `-Pobservability=on` wrapper, testLogging) 을 `baseE2eConfig` closure 에 이관.
- `e2eTest` 별도 task 신설 여부: gap 에서는 default `test` 가 이미 그 역할 — **fan/scm 패턴과 일관성 위해 `e2eTest` (umbrella, `includeTags 'e2e'`) 추가 등록 권장** (4 service 동일 task family 보장). 본 task 의 default `test` 는 알리어스 (excludeTags 'full' 이라 smoke 와 동등이지만, ADR § D2 의 `e2eTest` 의미와 다름 — Note: default `test` 는 "smoke + 미태깅" 이고 `e2eTest` umbrella 는 "smoke + full"). 본 task 는 둘 다 등록.

### D. (선택, follow-up 후보) gap PR-time smoke job 신설

ADR-MONO-010 § 6.2 outstanding 으로 명시된 **gap PR-time smoke job 신설** 은 본 task scope **밖**:

- 본 task 는 `e2eSmokeTest` task 등록 + tag 적용까지. CI workflow 에 gap-platform-e2e-smoke job 신설은 별도 task 후보 (`TASK-MONO-079` 또는 본 task 의 follow-up).
- 사유: gap 의 docker-compose 패턴이 fan/scm 의 Testcontainers Network 패턴과 boot-jar plumbing 이 다르며, CI workflow 작업 면적이 클 가능성 — 별도 task 로 격리.

본 task scope 내에서 ci.yml 변경: 없음.

## Out of Scope

- wms impl (D5 step 3) → **TASK-MONO-077** 별도.
- gap PR-time smoke job 신설 → 별도 task 후보 (ADR § 6.2 outstanding).
- Phase 3 nightly full e2e job 신설 → 별도 ADR + task.
- `platform/testing-strategy.md` / `docs/adr/ADR-MONO-010-*.md` 추가 갱신 — PR #428 에서 완료.
- gap의 e2e 외 영역 변경.
- Lint enforcement / class 명 rename.

---

# Acceptance Criteria

- [ ] **A** — `projects/global-account-platform/tests/e2e/src/test/java/com/example/e2e/E2EBase.java` 에 `@Tag("e2e")` 클래스 어노테이션 + `org.junit.jupiter.api.Tag` import 추가.
- [ ] **B** — 5 e2e class 에 class-level `@Tag` 적용:
  - `GoldenPathE2ETest.java` → `@Tag("smoke")` + import.
  - `TenantProvisioningE2ETest.java` → `@Tag("smoke")` + import.
  - `RefreshReuseDetectionE2ETest.java` → `@Tag("full")` + import.
  - `DlqHandlingE2ETest.java` → `@Tag("full")` + import.
  - `CrossServiceBulkLockE2ETest.java` → `@Tag("full")` + import.
- [ ] **C build.gradle**: `baseE2eConfig` closure 도입 + 3-task family 등록 (`e2eTest` umbrella `includeTags 'e2e'` / `e2eSmokeTest` `includeTags 'smoke'` / `e2eFullTest` `includeTags 'full'`). 기존 default `test` task 는 `useJUnitPlatform { excludeTags 'full' }` 로 변경 (smoke 의미상 동등). 기존 `test` 의 설정 (timeout / COMPOSE_PROJECT_NAME / observability wrapper / testLogging) 모두 `baseE2eConfig` closure 또는 default `test` 에 보존.
- [ ] **Self-CI**: `workflows` flag (없음 — 본 PR 은 workflow 미터치) 대신 `gap` flag 또는 `libs` flag 활성으로 gap-integration-tests 또는 관련 job 회귀 가드. default `test` task 가 full 제외하므로 long-running edge case (DLQ / refresh-reuse / bulk-lock) 가 skip 되어 wall-clock 단축 기대.
- [ ] **Back-compat**: `./gradlew :…:test` (default, 이제 smoke 동등) 의 test count 가 fan/scm/wms 의 `e2eSmokeTest` 와 같이 smoke class 만 실행. `./gradlew :…:e2eTest` (새 umbrella) 가 5 class 전부 실행.
- [ ] **Production code 0**: `git diff --stat -- 'projects/global-account-platform/apps/*/src/main/**' 'libs/**/src/main/**'` = 0 rows.
- [ ] task lifecycle ready → in-progress → review (impl PR), 이어서 close chore PR 로 review → done.
- [ ] `tasks/INDEX.md` `## ready` → `## review` → `## done` 동기.

---

# Related Specs

- `docs/adr/ADR-MONO-010-e2e-tag-taxonomy.md` ACCEPTED — D5 step 4 + D1 classification rubric.
- `tasks/done/TASK-MONO-076-e2e-tag-taxonomy-impl.md` Post-Review Scope Narrow 섹션 (직접 선행).
- PR #428 (squash `cdebdff2`) — Phase 2 impl 1차 fan+scm bundle (build.gradle 패턴 reference).
- `platform/testing-strategy.md` § E2E Smoke vs Full (D4 rubric).

# Related Skills

- `.claude/skills/backend/testing-backend/SKILL.md` — JUnit 5 class-level `@Tag` 패턴.

---

# Related Contracts

None — internal test infrastructure 변경.

---

# Target Service

`projects/global-account-platform/tests/e2e/` (e2e tests/ 모듈만).

---

# Architecture

ADR-MONO-010 (ACCEPTED) 의 D5 step 4 단독 이행. ADR 문서 미터치 (§ Status transition history 표 row 4 추가 여부 선택).

---

# Implementation Notes

## gap default `test` task 의 historical 결정

gap 의 `tests/e2e/build.gradle` 은 default `test` 가 e2e suite 자체 (별도 `e2eTest` task 미정의, `ComposeFixture` 가 `@BeforeAll` 에서 docker-compose 시작). 이 패턴은 fan/scm/wms 의 Testcontainers Network 와 달리 docker-compose 직접 사용. CI 의 `gap-integration-tests` job 이 이 default `test` 를 호출.

본 task 의 변경:
- default `test` task: `useJUnitPlatform()` → `useJUnitPlatform { excludeTags 'full' }` — full edge case 제외 + 기존 설정 (5m timeout, COMPOSE_PROJECT_NAME, observability wrapper, testLogging) 모두 보존.
- 신규 task 3개: `e2eTest` (umbrella `includeTags 'e2e'`) + `e2eSmokeTest` (`includeTags 'smoke'`) + `e2eFullTest` (`includeTags 'full'`). 모두 `baseE2eConfig` closure 재사용.

CI 의 `gap-integration-tests` job 은 본 PR 미터치 (default `test` 호출 유지) — full 제외 효과만 받음 (의도된 부분 wall-clock 단축).

## `@Tag("e2e")` umbrella inheritance verify

`E2EBase.java` 에 `@Tag("e2e")` 추가 후 5 subclass 가 inherit. JUnit 5 의 `useJUnitPlatform { includeTags 'e2e' }` 가 subclass method 도 매치. 정상 동작은 본 PR 의 self-CI 가 검증.

## fan/scm 의 `baseE2eConfig` closure 패턴 적용

PR #428 의 fan/scm `baseE2eConfig` 가 reference. gap 의 closure 도 동일 구조 (group / shouldRunAfter / system properties / observability wrapper / testLogging). gap 특수: `COMPOSE_PROJECT_NAME` environment + `docker network create gap-e2e_default || true` 분기 보존.

## Test count expectation

본 PR 머지 후:
- `./gradlew :…:test` → 2 smoke class (GoldenPath + TenantProvisioning) 실행, 3 full class skip.
- `./gradlew :…:e2eSmokeTest` → 동일 (smoke 2 class).
- `./gradlew :…:e2eFullTest` → full 3 class 실행.
- `./gradlew :…:e2eTest` → 5 class 전부 실행 (umbrella).

---

# Edge Cases

- **gap-integration-tests CI job 의 invocation**: 본 task 의 default `test` 변경이 이 job 의 동작에 영향. wall-clock 단축 (full 3 class skip) + green 통과 시 정상. red 시 default `test` 의 excludeTags 변경 revert + 별도 `e2eSmokeTest` 만 등록하는 변형으로 fallback.
- **ComposeFixture lifecycle**: default `test` task 의 `doFirst { docker network create gap-e2e_default || true }` 가 observability=on wrapper 안에 있음. closure 로 이관 시 idempotent 유지.
- **다른 CI job 의 default `test` 의존성**: 현재 gap-integration-tests 외 default `test` 를 호출하는 job 이 없는지 grep verify.

---

# Failure Scenarios

- **5 class 중 어느 class 의 분류가 misjudge**: 본 task impl 시 ADR § 1.2 audit 표 vs 실제 코드 1회 재확인. mismatch 시 ADR audit 표 갱신은 본 task 의 follow-up.
- **`@Tag("e2e")` 부재 환경에서 새 `e2eSmokeTest` 가 smoke method 0개 실행**: E2EBase 에 `@Tag("e2e")` 추가 → 5 subclass 가 inherit → `includeTags 'e2e'` 매치. AC § A 가 catch.
- **default `test` 의 excludeTags 'full' 가 fall-through**: 잘못 형식이면 `useJUnitPlatform { … }` block 의 다른 설정과 충돌. local `./gradlew :…:test --tests *Smoke*` dry-run 1회 verify.

---

# Test Requirements

- **JUnit test report count 검증**: `e2eSmokeTest` 가 2 class (GoldenPath + TenantProvisioning) 보고. `e2eFullTest` 가 3 class (RefreshReuse + DlqHandling + CrossServiceBulkLock) 보고. `e2eTest` 가 5 class 전부.
- **Wall-clock 비교**: 본 PR 머지 후 gap-integration-tests job (default `test` 호출) 의 wall-clock 이 직전 머지 대비 단축 — full 3 class skip 효과 정량화.
- **Production code 0** 검증.

---

# Definition of Done

### Impl PR

- [ ] AC A-C 모두 완료, self-CI green.
- [ ] task lifecycle: ready → in-progress → review.
- [ ] INDEX 동기.

### Close chore PR

- [ ] review → done 이동, INDEX done entry 1-line outcome (PR # + commit + 2 smoke + 3 full class count + wall-clock 단축 측정).

---

# Provenance

- 2026-05-13 TASK-MONO-076 close chore 시 Post-Review Scope Narrow 결정으로 분리 author.
- ADR-MONO-010 D5 step 4 의 단독 이행. gap 은 `@Tag("e2e")` precedent 도입 + default `test` 의 특수성 (docker-compose 직접 사용) 때문에 fan/scm/wms 와 변경 면적이 다름.
- 직접 선행 = TASK-MONO-076 (PR #428 fan+scm bundle).
- ADR § 6.2 outstanding (gap PR-time smoke job 신설) 는 본 task scope 밖 — 후속 follow-up.

분석=Opus 4.7 / 구현 권장=Sonnet 4.6 (mechanical annotation + closure refactor + 잘 정의된 매핑; gap default `test` 특수성도 ADR § D2 gap 부분 + 본 task § Implementation Notes 에 명시되어 명확).
