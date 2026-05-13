# Task ID

TASK-MONO-077

# Title

ADR-MONO-010 Phase 2 impl 2차 — wms `GatewayMasterE2ETest` method-level `@Tag("smoke")` / `@Tag("full")` 적용 + `E2EBase` umbrella `@Tag("e2e")` 도입 + `e2e-tests` CI job target/timeout 전환

# Status

done

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

ADR-MONO-010 (ACCEPTED 2026-05-13, PR #428 `cdebdff2`) 의 **D5 step 3** 단독 이행. wms gateway-service e2e 의 단일 클래스 (`GatewayMasterE2ETest`) 안 5 nested 시나리오가 smoke + full 으로 혼재 — ADR § D1.2 의 method-level granularity 규칙이 처음 적용되는 케이스. 작업 면적은 fan/scm 의 class-level rollout (PR #428) 대비 method-level annotation 5건 + base class 1 umbrella 추가 + `e2e-tests` job target/timeout/report-path 변경 + (선택) `build.gradle` 의 3-task family 등록.

provenance: TASK-MONO-076 close chore 2026-05-13 의 Post-Review Scope Narrow 결정 — multi-PR 책임을 단일 task 로 유지하지 않고 wms 를 분리. ADR § D5 step 3 의 D2 + D3 + D5 sub-step 만 본 task 가 다룸 (D4 testing-strategy.md 갱신은 PR #428 에서 이미 완료).

---

# Scope

## In Scope

### A. wms e2eTest `build.gradle` 3-task family 등록 (D2)

- `projects/wms-platform/apps/gateway-service/build.gradle` 의 기존 `e2eTest` task 를 `baseE2eConfig` closure 기반의 3-task family (`e2eTest` umbrella / `e2eSmokeTest` / `e2eFullTest`) 로 재구성 — fan/scm 의 PR #428 패턴 그대로 답습.
- 단, wms 는 `sourceSets { e2eTest { … } }` 별도 source set 구조 (fan/scm 의 `tests/e2e/` 단일 sourceSet 과 차이) — `testClassesDirs = sourceSets.e2eTest.output.classesDirs` + `classpath = sourceSets.e2eTest.runtimeClasspath` 를 3 task 각각에 명시 필요. 이 한 줄만이 fan/scm 패턴과 다름.

### B. `@Tag("e2e")` umbrella 도입 (E2EBase)

- `projects/wms-platform/apps/gateway-service/src/e2eTest/java/com/wms/gateway/e2e/E2EBase.java` 클래스에 `@Tag("e2e")` 적용 + `org.junit.jupiter.api.Tag` import. 모든 e2e test 가 자동으로 umbrella 상속.
- fan/scm 의 `*E2ETestBase` 가 이미 보유한 패턴 정렬 (3 e2e module 모두 일관).

### C. `GatewayMasterE2ETest` method-level `@Tag` 적용 (D5 step 3)

`projects/wms-platform/apps/gateway-service/src/e2eTest/java/com/wms/gateway/e2e/GatewayMasterE2ETest.java`:

- `org.junit.jupiter.api.Tag` import 추가.
- **class-level smoke/full 적용 안 함** (mixed-bucket — ADR § D1.2).
- 5 nested 시나리오의 method-level annotation:

| Nested | Method | Tag | 근거 (ADR § 1.2 audit) |
|---|---|---|---|
| `HappyPath` | `getWarehousesListWithValidJwt` | `@Tag("smoke")` | S1 happy GET, S2 deterministic, S4 < 30s |
| `HappyPath` | `postWarehouseWithValidJwt` (또는 같은 nested 의 두 번째 method — 실제 코드 확인 필요) | `@Tag("smoke")` | S1 happy POST + outbox→Kafka, 대표 write path |
| `UnauthenticatedRejection` | 401 method | `@Tag("smoke")` | cheap auth gate, S4 충족 |
| `RateLimit` | 800-burst method | `@Tag("full")` | F1 burst load |
| `MasterDownPropagation` | 503-when-master-down method | `@Tag("full")` | F2 container-pause |

method 이름이 ADR § 1.2 audit 표와 다를 수 있으니 impl 단계에서 `GatewayMasterE2ETest.java` 의 nested 클래스 + `@Test` method 구조 1회 재확인 후 적용.

### D. CI workflow (D3 — wms 부분)

`.github/workflows/ci.yml`:

- `e2e-tests` job (~L901) gradle target `:projects:wms-platform:apps:gateway-service:e2eTest` → `:e2eSmokeTest`.
- `timeout-minutes: 60` → `20`. job name 에 "smoke" 명시 (예: "E2E (gateway-master live-pair smoke, Testcontainers)").
- "Upload e2e test reports on failure" step 의 report path: `…/build/reports/tests/e2eTest/` → `…/build/reports/tests/e2eSmokeTest/`, `…/build/test-results/e2eTest/` → `…/build/test-results/e2eSmokeTest/`.
- 헤더 주석 § "E2E smoke vs full split" (PR #428 에서 추가됨) 안에 wms timeout 변경 (60 → 20) 반영. (현재 주석은 "60 → 20 wms" 를 이미 명시했을 수 있으니 확인 후 idempotent edit.)

## Out of Scope

- gap impl (D5 step 4) → **TASK-MONO-078** 별도.
- `platform/testing-strategy.md` 추가 갱신 — PR #428 에서 4 insert 모두 완료. 본 task 는 spec 미터치.
- `docs/adr/ADR-MONO-010-e2e-tag-taxonomy.md` 갱신 — Status 는 PR #428 에서 ACCEPTED 이미 적용; § Status transition history 표에 본 PR 의 row 3 추가 여부는 선택 (audit-trail 강화 목적이면 추가 권장).
- Phase 3 nightly full e2e job 신설 — 별도 ADR + task.
- Lint enforcement / class 명 rename.
- wms 의 e2e 외 다른 영역 변경.

---

# Acceptance Criteria

- [ ] **A** — `projects/wms-platform/apps/gateway-service/build.gradle` 에 `baseE2eConfig` closure + 3-task family (`e2eTest` umbrella / `e2eSmokeTest` / `e2eFullTest`) 등록. 각 task 에 `testClassesDirs = sourceSets.e2eTest.output.classesDirs` + `classpath = sourceSets.e2eTest.runtimeClasspath` 명시 (wms 특수성 — sourceSets-split).
- [ ] **B** — `E2EBase.java` 에 `@Tag("e2e")` 클래스 어노테이션 + `org.junit.jupiter.api.Tag` import 추가.
- [ ] **C** — `GatewayMasterE2ETest` 의 5 nested method 에 method-level `@Tag` 적용 (3 smoke + 2 full). 클래스 자체에는 smoke/full annotation 없음. `org.junit.jupiter.api.Tag` import 추가.
- [ ] **D** — `ci.yml` 의 `e2e-tests` job: gradle target `:e2eSmokeTest`, timeout 60 → 20, job name 에 "smoke" 명시, report path `e2eTest` → `e2eSmokeTest`.
- [ ] **Self-CI**: `workflows` flag + `wms` flag (또는 `libs` flag) 활성 → full pipeline 회귀 가드. `e2e-tests` job 의 wall-clock 측정 (smoke 3 method 만 실행, full 2 method skip — JUnit test report count 검증).
- [ ] **Smoke budget**: cold-start 제외한 smoke 3 method 의 평균 wall-clock 이 ADR § D1 의 ≤ 30s 충족.
- [ ] **Back-compat**: `./gradlew :…:e2eTest` (umbrella) 가 여전히 smoke + full 둘 다 실행 (5 method 전부) — local dev 회귀 0.
- [ ] **Production code 0**: `git diff --stat -- 'projects/wms-platform/apps/*/src/main/**' 'libs/**/src/main/**'` = 0 rows.
- [ ] task lifecycle ready → in-progress → review (impl PR), 이어서 close chore PR 로 review → done.
- [ ] `tasks/INDEX.md` `## ready` → `## review` (impl PR) → `## done` (close chore PR) 동기.

---

# Related Specs

- `docs/adr/ADR-MONO-010-e2e-tag-taxonomy.md` ACCEPTED — D5 step 3 + D1 method-level granularity + D2 sourceSet 재사용.
- `tasks/done/TASK-MONO-076-e2e-tag-taxonomy-impl.md` Post-Review Scope Narrow 섹션 (직접 선행, 본 task 가 분리된 사유).
- PR #428 (squash `cdebdff2`) — Phase 2 impl 1차 fan+scm bundle (본 task 의 패턴 reference).
- `platform/testing-strategy.md` § E2E Smoke vs Full (D4 rubric — 본 task 의 분류 검증 기준).

# Related Skills

- `.claude/skills/backend/testing-backend/SKILL.md` — JUnit 5 method-level `@Tag` 패턴.

---

# Related Contracts

None — internal test infrastructure 변경.

---

# Target Service

`projects/wms-platform/apps/gateway-service/` (e2e source set 만).

---

# Architecture

ADR-MONO-010 (ACCEPTED) 의 D5 step 3 단독 이행. 본 task 는 ADR 갱신 미터치 (ACCEPTED 상태 보존). § Status transition history 표에 row 3 추가 여부 선택.

---

# Implementation Notes

## wms 의 sourceSets-split 특수성

fan/scm 의 `tests/e2e/` 모듈은 standalone Gradle 모듈 (default `test` 또는 `e2eTest` 가 directly 매핑). wms 의 e2e 는 `apps/gateway-service` 안의 `e2eTest` source set — `sourceSets { e2eTest { java.srcDir 'src/e2eTest/java' … } }` 으로 별도 정의. 따라서 새 `e2eSmokeTest` / `e2eFullTest` task 도:

```groovy
testClassesDirs = sourceSets.e2eTest.output.classesDirs
classpath = sourceSets.e2eTest.runtimeClasspath
```

명시 필요. fan/scm 의 `baseE2eConfig` 는 이 두 줄 없음 (single test source set).

## method-level `@Tag` semantics

JUnit 5 의 `useJUnitPlatform { includeTags 'smoke' }` 는 method 단위 필터링. nested 클래스 자체는 instantiate 되지만 untagged method 는 SKIPPED report (실행 자체 안 됨). 따라서 `:e2eSmokeTest` 실행 시 `GatewayMasterE2ETest` 의 5 method 중 smoke-tagged 3개만 실행, full 2개는 SKIPPED. Test report row count 가 검증 입력.

## @Tag("e2e") umbrella 도입의 영향

`E2EBase` 에 `@Tag("e2e")` 추가 시 subclass `GatewayMasterE2ETest` 도 inherits — 새 `e2eTest` task (umbrella, `includeTags 'e2e'`) 가 정상 동작. 기존에는 `useJUnitPlatform()` 만 호출했으므로 모든 test 실행 (tag filter 없음); 새 `e2eTest` task 는 `includeTags 'e2e'` 를 가지므로 base class 의 umbrella tag 필수.

## 기존 e2eTest task 의 보존

build.gradle 의 기존 `tasks.register('e2eTest', Test) { … }` 블록을 `baseE2eConfig` closure 기반으로 재구성. 본 task 의 변경 면적은 fan/scm 패턴 (closure + 3 task) 과 거의 동일.

---

# Edge Cases

- **GatewayMasterE2ETest 의 nested 클래스 구조 확인**: ADR § 1.2 audit 표는 "Nested 1: HappyPath" 등 추정. impl 시 첫 단계에 실제 코드 grep 으로 `@Nested` + `@Test` 카운트 + method 이름 확인.
- **dockerd 부재 시 silent skip**: `@Testcontainers(disabledWithoutDocker = true)` 가 E2EBase 에 있다면 dockerd 없는 환경에서 SKIPPED report. PR #428 의 wms `e2e-tests` job 이 39s 만에 끝난 것이 dockerd 부재 silent skip 일 가능성 점검 — 실제 boot 가 정상이면 자연 wall-clock 보고.
- **method-level `@Tag("smoke")` + class-level `@Tag("e2e")` 의 상호작용**: JUnit 5 는 method 의 @Tag 가 class 의 @Tag 을 inherit + override. method `@Tag("smoke")` + base class `@Tag("e2e")` = method 가 두 tag 모두 보유. `includeTags 'smoke'` 필터는 method 가 smoke 보유 시 매치. 정상.

---

# Failure Scenarios

- **Method 이름이 ADR audit 표와 다름**: AC § C 의 method-level 매핑 표가 실제 코드와 mismatch 면 impl 시 코드 우선 + ADR audit 표 갱신은 별도 follow-up.
- **wms e2eTest task 의 inline 설정 누락**: `baseE2eConfig` closure 로 re-factor 시 기존 `dependsOn ':projects:wms-platform:apps:master-service:bootJar'` 누락하면 boot jar 부재로 ImageFromDockerfile hang. closure 안 dependsOn 명시 verify.
- **observability=on wrapper 호환성**: 기존 `e2eTest` 의 `-Pobservability=on` 분기 가 closure 안에도 정확히 옮겨가는지 verify. PR #428 의 fan/scm 패턴 그대로 답습.

---

# Test Requirements

- **JUnit test report count 검증**: `e2eSmokeTest` 의 test report 가 3 method (HappyPath 2 + UnauthenticatedRejection 1) 만 보고. `e2eFullTest` 가 2 method (RateLimit + MasterDownPropagation) 만 보고. `e2eTest` (umbrella) 가 5 method 전부 보고.
- **Wall-clock 비교**: PR #428 의 wms `e2e-tests` job wall-clock (39s, 모든 test 포함) 대비 본 PR 의 `e2e-tests` job (smoke 만) 더 짧거나 동일.
- **Production code 0** 검증.

---

# Definition of Done

### Impl PR

- [ ] AC A-D 모두 완료, self-CI green.
- [ ] task lifecycle: ready → in-progress → review.
- [ ] INDEX 동기.

### Close chore PR

- [ ] review → done 이동, INDEX done entry 1-line outcome (PR # + commit + smoke method count + wall-clock 측정).

---

# Provenance

- 2026-05-13 TASK-MONO-076 close chore 시 Post-Review Scope Narrow 결정으로 분리 author.
- ADR-MONO-010 D5 step 3 의 단독 이행.
- 직접 선행 = TASK-MONO-076 (PR #428 fan+scm bundle).

분석=Opus 4.7 / 구현 권장=Sonnet 4.6 (mechanical annotation + closure refactor + ci.yml edit; rubric + sourceSet pattern 모두 PR #428 에서 검증된 기반).
