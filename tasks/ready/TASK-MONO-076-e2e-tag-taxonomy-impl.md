# Task ID

TASK-MONO-076

# Title

ADR-MONO-010 ACCEPTED transition + Phase 2 impl — `@Tag("smoke")` / `@Tag("full")` 도입, gradle `e2eSmokeTest` / `e2eFullTest` task 신설, PR-time CI 4 e2e job 을 smoke 만 실행하도록 전환

# Status

ready

# Owner

monorepo

# Task Tags

- ci
- e2e
- testing
- gradle
- spec
- impl

---

# Goal

ADR-MONO-010 (PROPOSED 2026-05-13, 본 task 가 동반하는 동일 PR 단계의 spec 산출물) 을 ACCEPTED 로 전환하면서 Phase 2 의 실제 코드 변경을 수행한다.

세 분리 가능한 impl 작업:

1. **D2 — gradle**: 4 e2e module 의 `build.gradle` 에 `e2eSmokeTest` + `e2eFullTest` 2 task 신설 (기존 `e2eTest` 유지, 동일 sourceSet 재사용, `includeTags 'smoke'` / `includeTags 'full'` 만 차이).
2. **D5 — annotation rollout**: 4 service 의 15 e2e 단위에 `@Tag("smoke")` 또는 `@Tag("full")` 적용 (1 service 가 method-level granularity 필요 — wms).
3. **D3 — workflow**: `ci.yml` 의 3 PR-time e2e job (`e2e-tests` / `fan-platform-e2e` / `scm-platform-e2e`) 의 gradle target 을 `:…:e2eTest` → `:…:e2eSmokeTest` 로 변경 + timeout 축소. Phase 3 의 nightly 추가 4 job 은 본 task scope **밖** (별도 ADR + task — § Out of Scope).

ADR § D4 에 따라 `platform/testing-strategy.md` 4-insert 갱신도 본 task 의 impl 단계에 포함.

provenance: 사용자 2026-05-13 세션 "e2e 테스트 3단계 전략 Phase 2 시작" — Phase 1 (TASK-MONO-074/075) 종결 직후. Phase 3 (full e2e nightly 이전) 는 본 task 가 정의하는 smoke/full 분류가 전제이며, 본 task DONE 후 별도 ADR + task 로 진행.

---

# Scope

## In Scope

### A. ADR-MONO-010 ACCEPTED 전환

- 사용자 explicit intent ("transition ADR-MONO-010 to ACCEPTED") 수신 후 impl 단계 시작.
- ADR file 헤더 `Status: PROPOSED` → `Status: ACCEPTED`, `History` 라인에 ACCEPTED row 추가, `§ Status transition history` 표 row 2 채움.

### B. Gradle task 신설 (ADR § D2)

4 file 편집:

- `projects/wms-platform/apps/gateway-service/build.gradle` — `tasks.register('e2eSmokeTest', Test)` + `tasks.register('e2eFullTest', Test)`. 기존 `e2eTest` 의 모든 설정 (sourceSets, dependsOn bootJar, system properties, observability wrapper, testLogging) verbatim 복제, `includeTags` 만 차이.
- `projects/fan-platform/tests/e2e/build.gradle` — 동일 패턴. 기존 `e2eTest` 가 reference.
- `projects/scm-platform/tests/e2e/build.gradle` — 동일.
- `projects/global-account-platform/tests/e2e/build.gradle` — 특수 케이스 (default `test` task 가 e2e 를 실행). `e2eSmokeTest` + `e2eFullTest` 신설, 기존 `test` task 에 `excludeTags 'full'` 추가 (test = smoke equivalent 로 PR-time CI 보존).

### C. `@Tag("smoke")` / `@Tag("full")` 적용 (ADR § D5)

15 unit 적용 (ADR § 1.2 audit 표 그대로 따름). 4 service rollout 순서 (D5):

**Step 1 — fan-platform** (3 class, 모두 class-level):

- `projects/fan-platform/tests/e2e/src/test/java/com/example/fanplatform/e2e/scenario/ArtistAndPostFlowE2ETest.java` → `@Tag("smoke")` 클래스에 추가.
- `projects/fan-platform/tests/e2e/src/test/java/com/example/fanplatform/e2e/scenario/MultiTenantIsolationE2ETest.java` → `@Tag("smoke")`.
- `projects/fan-platform/tests/e2e/src/test/java/com/example/fanplatform/e2e/scenario/VisibilityTierE2ETest.java` → `@Tag("full")`.

**Step 2 — scm-platform** (6 class, 모두 class-level):

- `projects/scm-platform/tests/e2e/src/test/java/com/example/scmplatform/e2e/scenario/ProcurementHappyPathE2ETest.java` → `@Tag("smoke")`.
- `projects/scm-platform/tests/e2e/src/test/java/com/example/scmplatform/e2e/scenario/CrossTenantIsolationE2ETest.java` → `@Tag("smoke")`.
- `projects/scm-platform/tests/e2e/src/test/java/com/example/scmplatform/e2e/scenario/AsnReceiveE2ETest.java` → `@Tag("full")`.
- `projects/scm-platform/tests/e2e/src/test/java/com/example/scmplatform/e2e/scenario/SupplierAckWebhookE2ETest.java` → `@Tag("full")`.
- `projects/scm-platform/tests/e2e/src/test/java/com/example/scmplatform/e2e/scenario/SupplierCircuitBreakerE2ETest.java` → `@Tag("full")`.
- `projects/scm-platform/tests/e2e/src/test/java/com/example/scmplatform/e2e/scenario/WmsInventoryAdjustedConsumedE2ETest.java` → `@Tag("full")`.

**Step 3 — wms gateway-service** (1 class, 5 nested → method-level):

- `projects/wms-platform/apps/gateway-service/src/e2eTest/java/com/wms/gateway/e2e/GatewayMasterE2ETest.java`:
  - Nested `HappyPath` 의 `getWarehousesListWithValidJwt` + `postWarehouseWithValidJwt` 각각 `@Tag("smoke")`.
  - Nested `UnauthenticatedRejection` 의 401 method → `@Tag("smoke")`.
  - Nested `RateLimit` 의 800-burst method → `@Tag("full")`.
  - Nested `MasterDownPropagation` 의 503-when-down method → `@Tag("full")`.
  - 클래스 레벨 `@Tag("smoke")` / `@Tag("full")` 적용 안 함 (mixed-bucket — ADR § D1.2).
  - **기존 클래스에 `@Tag("e2e")` 부재** — 클래스 또는 base `E2EBase.java` 에 추가 필요 (현재 wms 의 e2eTest sourceSet 은 build.gradle 의 `useJUnitPlatform()` 이 무조건 모두 실행, includeTags 부재). 일관성을 위해 `E2EBase` 에 `@Tag("e2e")` 추가, GatewayMasterE2ETest 의 nested 5 method 에 smoke/full 추가.

**Step 4 — gap** (5 class, two-step 마이그레이션):

- gap 의 5 e2e class 어디에도 `@Tag("e2e")` 없음. 먼저 5 class 모두에 `@Tag("e2e")` 추가 (umbrella), 그 후 smoke/full 분류 추가:
  - `projects/global-account-platform/tests/e2e/src/test/java/com/example/e2e/GoldenPathE2ETest.java` → `@Tag("e2e")` + `@Tag("smoke")`.
  - `projects/global-account-platform/tests/e2e/src/test/java/com/example/e2e/TenantProvisioningE2ETest.java` → `@Tag("e2e")` + `@Tag("smoke")`.
  - `projects/global-account-platform/tests/e2e/src/test/java/com/example/e2e/RefreshReuseDetectionE2ETest.java` → `@Tag("e2e")` + `@Tag("full")`.
  - `projects/global-account-platform/tests/e2e/src/test/java/com/example/e2e/DlqHandlingE2ETest.java` → `@Tag("e2e")` + `@Tag("full")`.
  - `projects/global-account-platform/tests/e2e/src/test/java/com/example/e2e/CrossServiceBulkLockE2ETest.java` → `@Tag("e2e")` + `@Tag("full")`.

### D. Workflow YAML 갱신 (ADR § D3 PR-time only)

`.github/workflows/ci.yml`:

- `e2e-tests` job (~L901): `./gradlew :projects:wms-platform:apps:gateway-service:e2eTest` → `:e2eSmokeTest`. `timeout-minutes: 60` → `20`. Job name 도 `E2E (gateway-master live-pair smoke, Testcontainers)` 로 갱신 권장.
- `fan-platform-e2e` job (~L995): `:projects:fan-platform:tests:e2e:e2eTest` → `:e2eSmokeTest`. `timeout-minutes: 20` → `10`.
- `scm-platform-e2e` job (~L1097): `:projects:scm-platform:tests:e2e:e2eTest` → `:e2eSmokeTest`. `timeout-minutes: 20` → `10`.
- 헤더 주석 블록 (L29-L66) 의 e2e job 카탈로그 갱신 — Phase 2 가 `e2eSmokeTest` 만 PR-time 실행함을 명시 + ADR-MONO-010 reference.

### E. `platform/testing-strategy.md` 갱신 (ADR § D4)

4 insert:

1. Test Pyramid block 의 `[E2E / Contract]` row 를 `[E2E (full) / Contract]` 상위 + `[E2E (smoke)]` 하위로 split, one-line note 추가.
2. "Test Types" 섹션의 Integration Tests 다음, Event Consumer 이전에 "E2E Smoke vs Full" 신규 subsection 삽입 (S1-S4 + F1-F6 rubric + granularity rule + 미태깅=full default + ADR-MONO-010 pointer).
3. "Naming Conventions" 표에 2 row 추가: `*SmokeE2ETest` / `*FullE2ETest` (RECOMMENDED, not required).
4. "Rules" 섹션에 1 rule 추가: 모든 `*E2ETestBase` extending class 는 class-level 또는 method-level 로 `@Tag("smoke")` 또는 `@Tag("full")` 표시. 미태깅 = full (conservative).

### F. INDEX 갱신

- `docs/adr/INDEX.md` Status `PROPOSED` → `ACCEPTED` (ADR-MONO-010 row).
- `tasks/INDEX.md` `## ready` 에서 본 task 제거, `## review` 로 (impl PR 단계) → `## done` 으로 (close chore 단계).

## Out of Scope

- **Phase 3 — nightly cadence + push-to-main full-e2e job 4개 신설** (`nightly-e2e.yml` 에 `wms-platform-e2e-full` + `gap-e2e-full` + `fan-platform-e2e-full` + `scm-platform-e2e-full`). **별도 ADR (Phase 3) + 별도 task**. Phase 2 가 partition 을 authorise 하고, Phase 3 가 cadence 를 authorise 함 (ADR § 3.6 alternatives 거부 사유 참조).
- **Lint enforcement** — `validate-rules` 가 모든 `*E2ETest.java` 의 `smoke|full` tag 존재를 grep-assert. **out of scope** (ADR § 6.3 outstanding).
- **gap PR-time smoke job 신설** — gap 은 현재 PR-time e2e job 자체가 없음 (gap-integration-tests 만 있음). 본 task 의 D 단계에서 gap-platform-e2e-smoke 신규 job 신설하지 **않음** (gap docker-compose 패턴이 Testcontainers Network 패턴과 boot-jar plumbing 이 다르며, 별도 job 신설은 § 6.2 outstanding).
- **Naming suffix migration** — 기존 class 명 `GoldenPathE2ETest` → `GoldenPathSmokeE2ETest` 같은 rename. RECOMMENDED 만, 본 task scope 외 (§ 6.4 outstanding).
- **Cost-budget telemetry** — § 6.5 outstanding.
- 서비스 production code 미터치 (모든 변경은 test 코드 + build.gradle + workflow yaml + spec).

---

# Acceptance Criteria

### Spec PR (본 PR — ADR PROPOSED + task ready)

- [ ] `docs/adr/ADR-MONO-010-e2e-tag-taxonomy.md` 가 `Status: PROPOSED` 로 존재.
- [ ] `docs/adr/INDEX.md` 에 ADR-MONO-010 PROPOSED row 추가.
- [ ] `tasks/ready/TASK-MONO-076-e2e-tag-taxonomy-impl.md` 가 본 파일 그대로 존재.
- [ ] `tasks/INDEX.md` `## ready` 에 본 task 1-line entry.
- [ ] CI green (markdown-only PR 이므로 Phase 1 markdown-skip → e2e SKIP, build-and-test SKIP, changes job 만 trigger).

### Impl PR (ACCEPTED 전환 후 단일 또는 묶음 PR — D5 bundling 정책 따름)

- [ ] **B**: 4 build.gradle 에 `e2eSmokeTest` + `e2eFullTest` 2 task 신설, 기존 `e2eTest` 보존, sourceSets/dependsOn/system properties/observability wrapper 모두 verbatim 복제, `includeTags` 만 다름.
- [ ] **B (gap 특수)**: gap `tests/e2e/build.gradle` 의 default `test` task 에 `useJUnitPlatform { excludeTags 'full' }` 추가.
- [ ] **C step 1 fan**: 3 e2e class 에 `@Tag("smoke")` × 2 + `@Tag("full")` × 1 적용. JUnit imports + 위치 일관 (class-level annotation, base class `@Tag("e2e")` 위 또는 아래).
- [ ] **C step 2 scm**: 6 e2e class 에 `@Tag("smoke")` × 1 + `@Tag("full")` × 5 적용.
- [ ] **C step 3 wms**: `E2EBase.java` 에 `@Tag("e2e")` 추가 + `GatewayMasterE2ETest` 의 5 nested method 에 `@Tag("smoke")` × 3 + `@Tag("full")` × 2 method-level 적용 (class-level smoke/full 적용 안 함).
- [ ] **C step 4 gap**: 5 e2e class 에 `@Tag("e2e")` precedent 도입 + `@Tag("smoke")` × 2 + `@Tag("full")` × 3 적용.
- [ ] **D**: `ci.yml` 의 3 PR-time e2e job gradle target 이 `e2eSmokeTest` 로 변경, timeout 축소, job name 갱신, 헤더 주석 ADR-MONO-010 reference + Phase 2 설명 추가.
- [ ] **E**: `platform/testing-strategy.md` 4 insert 완료 (Test Pyramid split / 새 subsection / naming convention 2 row / Rules 1 entry).
- [ ] **F**: ADR-MONO-010 `Status: ACCEPTED` 로 전환 + `§ Status transition history` 표 row 2 채움, `docs/adr/INDEX.md` row Status PROPOSED → ACCEPTED, `tasks/INDEX.md` 의 본 task 1-line `## ready` → `## review` 이동.
- [ ] **Verification**: 4 e2e module 각각 `./gradlew :…:e2eSmokeTest` 실행 시 smoke-tagged 만 실행됨 (test report row count 확인). `./gradlew :…:e2eFullTest` 시 full-tagged 만 실행. `./gradlew :…:e2eTest` (legacy) 시 둘 다 실행 (back-compat).
- [ ] **Self-CI**: 4 service 의 PR-time e2e job 이 새 task 이름으로 GREEN. 회귀 0. smoke-tagged 분류된 test 가 실제로 cold-start 제외 30s 이내 완료되는지 wall-clock 측정 (ADR § 5 검증 #2 + § 4.3 cost-budget 입력).
- [ ] **No service code touched**: `git diff --stat projects/*/apps/` 가 0 rows (test 코드 + build.gradle 만 변경).
- [ ] **Production code 0**: `git diff --stat -- '*/src/main/**'` 이 0 rows.

### Close chore PR (impl PR merge 후)

- [ ] task file `Status: review → done`.
- [ ] `git mv tasks/review/TASK-MONO-076-*.md tasks/done/TASK-MONO-076-*.md`.
- [ ] `tasks/INDEX.md` `## review` 에서 entry 제거, `## done` 에 1-line outcome (PR # + commit hash + smoke 8 / full 11 분포 실측 + CI green 결과 요약 + wall-clock 측정값).

---

# Related Specs

- `docs/adr/ADR-MONO-010-e2e-tag-taxonomy.md` (본 task 가 ACCEPTED 로 전환)
- `tasks/done/TASK-MONO-074-ci-e2e-skip-and-force-full-flags.md` (Phase 1 직접 선행)
- `tasks/done/TASK-MONO-075-fix-mono-074-markdown-skip-paths-filter-quirk.md` (Phase 1 fix)
- `tasks/done/TASK-MONO-045-ci-path-filter-and-nightly-e2e.md` (baseline path-filter + nightly ecommerce 분리 prior art)
- `tasks/done/TASK-FAN-INT-001-fan-platform-live-trio-e2e.md` (fan-platform e2e module reference)
- `tasks/done/TASK-SCM-INT-001-procurement-e2e.md` (scm-platform e2e module reference)
- `tasks/done/TASK-BE-041c-gap-e2e-suite.md` (gap e2e module reference; precise task ID 확인 필요)
- `platform/testing-strategy.md` (D4 amendment target)
- Memory `project_ci_path_filter_074_075_quirk.md` (Phase 1 노하우)

# Related Skills

- `.claude/skills/backend/testing-backend/SKILL.md` — JUnit 5 `@Tag` + Gradle `includeTags` 패턴 precedent.

---

# Related Contracts

None — internal test infrastructure 변경.

---

# Target Service

N/A — cross-cutting test policy, 4 service e2e module + shared workflow + shared spec.

---

# Architecture

ADR-MONO-010 ACCEPTED 후 본 task 가 운영화. 본 task 는 ADR 동반이지만 ADR 자체는 spec PR 단계에서 PROPOSED 로 별도 author (본 PR 에서 ADR + task spec 함께 land; ACCEPTED 전환은 별도 impl PR — 통상 single bundled PR 가능 (`tasks/INDEX.md` § PR Separation Rule 의 spec+impl 분리 규칙은 단일 task 적용; 본 task 의 spec 단계는 ADR PROPOSED + ready/ task 만 다루므로 production code 0).

---

# Implementation Notes

## JUnit 5 `@Tag` + Gradle `includeTags` 패턴

기존 사용처 (이미 검증된 precedent):

- `projects/scm-platform/tests/e2e/build.gradle:79` — `useJUnitPlatform { includeTags 'e2e' }`.
- `projects/fan-platform/tests/e2e/build.gradle:82` — 동일.

신규:

```groovy
tasks.register('e2eSmokeTest', Test) {
    description = '@Tag("smoke") subset — runs every PR'
    group = 'verification'
    useJUnitPlatform { includeTags 'smoke' }
    // 기존 e2eTest 의 나머지 설정 (sourceSets [wms 만], dependsOn bootJar, system properties,
    // observability wrapper, testLogging) verbatim 복제
}
tasks.register('e2eFullTest', Test) {
    description = '@Tag("full") subset — runs nightly + push to main'
    group = 'verification'
    useJUnitPlatform { includeTags 'full' }
    // 동일 inheritance
}
```

기존 `e2eTest` 는 `includeTags 'e2e'` 유지 — back-compat 으로 smoke + full 둘 다 (umbrella `@Tag("e2e")` 가 base class 또는 본 task 의 step 4 에서 도입).

## wms `e2eTest` source set 의 특수성

wms gateway-service 의 `e2eTest` source set 은 다른 3 module 과 달리 `useJUnitPlatform()` 이 (현재 시점) tag filter 없음 (`build.gradle:117`). 따라서 wms 의 `e2eSmokeTest` / `e2eFullTest` 는 `includeTags 'smoke'` / `'full'` 만 분류 기준. `@Tag("e2e")` 가 wms 의 모든 test 에 부재해도 (E2EBase 가 아직 미적용) 새 task 는 includeTags 만 보므로 작동.

Step 3 에서 E2EBase 에 `@Tag("e2e")` 를 추가하면 다른 3 module 과 일관성 회복.

## gap 특수성 — default `test` task 가 e2e 를 실행

gap `tests/e2e/build.gradle` 의 default `test` task 가 e2e 를 실행 (다른 3 module 은 default `test` 가 no-op 또는 unit-only). 본 task 는 default `test` 를 **유지** (CI 의 기존 `gap-integration-tests` 또는 별도 job 이 이 task 를 호출할 수 있음 — 변경 추적 필요).

옵션 1 (권장): default `test` 에 `excludeTags 'full'` 추가 → test = smoke 와 동등.
옵션 2: 새 `e2eSmokeTest` / `e2eFullTest` 만 신설하고 default `test` 는 그대로 (smoke + full 모두 실행). CI side 가 새 task 로 마이그레이션.

옵션 1 채택. 단, gap CI side 가 default `test` 를 어떻게 호출하는지 본 task 의 D 단계 사전 점검 필요 (`.github/workflows/ci.yml` `gap-integration-tests` job 의 gradle command 확인).

## back-compat `e2eTest` task

각 module 의 기존 `e2eTest` task 는 변경 없이 유지. CI 의 e2e job 만 새 `e2eSmokeTest` 를 호출하도록 변경. 로컬 dev 가 `./gradlew :…:e2eTest` 를 호출하면 여전히 smoke + full 모두 실행 (umbrella `@Tag("e2e")` 가 모든 e2e test 에 적용된 후).

## Method-level `@Tag` 의 JUnit 5 동작

wms `GatewayMasterE2ETest` 의 nested 클래스 안 method 에 `@Tag` 적용 시 JUnit 5 는 method-level tag 로 인식. `useJUnitPlatform { includeTags 'smoke' }` 는 method 단위로 필터링 → nested 클래스 자체가 실행되더라도 그 안 untagged method 는 실행 안 됨. precedent: JUnit 5 Platform User Guide § Tag.

ADR-MONO-010 § D1.2 가 권장하는 패턴: class-level smoke/full 적용 안 함 (mixed-bucket), method-level 만 사용. 결과: `:e2eSmokeTest` 실행 시 wms 의 nested 5 method 중 smoke-tagged 3개만 실행됨.

---

# Edge Cases

- **태그 안 한 e2e test**: `@Tag("e2e")` 만 있고 smoke/full 미부착 → `useJUnitPlatform { includeTags 'smoke' }` 는 실행 안 함, `{ includeTags 'full' }` 도 실행 안 함. **conservative default = full (ADR § D1.3)** 와 일치하려면, gradle `excludeTags 'smoke'` 으로 e2eFullTest 를 정의하거나, junit-platform.properties 의 `junit.jupiter.execution.tags.default = full` 옵션 사용. **결정**: 단순 `includeTags 'full'` 유지 + 마이그레이션 책임 = 본 task 가 모든 15 unit 명시적 분류 → 미태깅 0 보장. lint 는 § 6.3 outstanding.
- **mixed class-level + method-level**: GatewayMasterE2ETest 가 class-level smoke 면서 nested method 에 full 이 섞이면 의미 충돌. ADR § D1.2 가 "mixed 클래스에선 class-level smoke/full 적용 안 함" 으로 정의. step 3 implementation 시 이 규칙 엄수.
- **`@Tag("e2e")` 와 `@Tag("smoke")` 동시 적용**: 정상 동작 (JUnit 5 multi-tag). `:e2eSmokeTest` 는 smoke 만 필터, e2e 는 noise.
- **observability=on wrapper 와의 상호작용**: 본 task 의 새 task 는 observability wrapper block 도 verbatim 복제. `-Pobservability=on -Dgradle ..:e2eSmokeTest` 가 정상 작동.
- **CI bootJar artifact**: 새 task 들이 기존 `e2eTest` 와 같은 bootJar dependsOn 을 가져야 (`dependsOn ':projects:...:bootJar'`). 복제 시 누락하면 CI 에서 boot jar 없음 → ImageFromDockerfile 폴백 hang.
- **gap default test task 변경의 cross-impact**: gap CI 의 다른 job 이 default `test` 를 호출한다면 `excludeTags 'full'` 추가가 그 job 의 동작을 의도치 않게 변경. D 단계 사전 점검 필요.
- **squash-merge rename gotcha**: TASK-MONO-074/075 학습 — `tasks/ready/TASK-MONO-076-….md` 가 squash 시 file move 로만 보일 수 있음. close chore 시 `git mv` 명시.

---

# Failure Scenarios

- **CI smoke job 이 full test 도 끌어옴**: `includeTags 'smoke'` 가 misconfigured (oneToOne 오타) → test report 가 expected count 와 불일치. Verification step 6 (test report row count) 가 catch.
- **smoke test 가 실제로는 full 수준 비용**: 분류 misjudgement. Self-CI wall-clock 이 cold-start 제외 30s 초과 시 발견. 그 test 를 full 로 재분류 (별도 follow-up fix task — 본 PR 의 cycle 안 회복 가능하면 추가 commit, 안 되면 새 ready/ task).
- **Phase 1 markdown-skip 회귀**: spec PR (markdown-only) 가 잘못 e2e 트리거 → TASK-MONO-075 fix 의 outputs-AND code-changed filter 가 작동 안 함. self-CI 가 catch.
- **JUnit 5 의 includeTags + excludeTags 상호작용**: 같은 useJUnitPlatform 블록 안 둘 다 사용 시 우선순위 차이 trap. impl 시 동일 task 안 둘 다 사용 회피 (e2eSmokeTest = includeTags only, e2eFullTest = includeTags only).
- **gap `@Tag("e2e")` precedent 도입의 의도치 않은 side-effect**: gap 5 e2e class 가 default `test` 로 실행되고 있는데, 새 `@Tag("e2e")` umbrella 가 추가되면 다른 tag filter (`integration` 등) 와 충돌 가능. 사전 grep 필요.

---

# Test Requirements

- **Verification 1 — partition counts**: 본 task 의 ACCEPTED 후 verify
  - fan: 2 smoke + 1 full
  - scm: 1 smoke + 5 full
  - wms: 3 smoke + 2 full (method-level)
  - gap: 2 smoke + 3 full
  - total: 8 smoke + 11 full = 19 (wms 의 nested 5 method 가 5로 카운트).
- **Verification 2 — task isolation**: 4 module 각각 `:e2eSmokeTest` / `:e2eFullTest` 가 정확히 분류된 test 만 실행 (test report 의 `tests` 카운트).
- **Verification 3 — back-compat**: `:e2eTest` (legacy) 가 smoke + full 둘 다 실행.
- **Verification 4 — Self-CI**: 3 PR-time e2e job 이 새 task name + 단축 timeout 으로 GREEN.
- **Verification 5 — Wall-clock**: smoke test 의 cold-start 제외 wall-clock 이 30s 이내 (ADR § D1 + § 4.3 cost-budget).
- **Verification 6 — Production code 0**: `git diff --stat -- '*/src/main/**'` rows = 0.

---

# Definition of Done

### Spec PR (본 PR)

- [ ] `docs/adr/ADR-MONO-010-e2e-tag-taxonomy.md` PROPOSED 존재 + `docs/adr/INDEX.md` row + 본 task ready/ 존재 + `tasks/INDEX.md` `## ready` entry.
- [ ] markdown-only PR 이므로 e2e SKIP, changes + build-and-test SKIP, lint workflow 만 활성 → 의도된 동작 검증 (`workflows` flag 없음).
- [ ] commit + push + (사용자 명시 요청 시 PR open; 평소 push 까지만).

### Impl PR

- [ ] ADR-MONO-010 ACCEPTED 전환 + 4 build.gradle 변경 + 4 service 의 15 e2e unit `@Tag` 적용 + `ci.yml` 3 PR-time e2e job target/timeout 변경 + `platform/testing-strategy.md` 4 insert + `docs/adr/INDEX.md` Status 갱신 + `tasks/INDEX.md` `## ready` → `## review`.
- [ ] Self-CI 4 service 의 e2e job 새 task name 으로 GREEN.
- [ ] partition count 6 verification 모두 PASS.
- [ ] Smoke cost-budget 측정값 close chore 시점 기록.

### Close chore PR

- [ ] task file Status `review` → `done` + `git mv` + `tasks/INDEX.md` `## review` 제거 / `## done` append 1-line outcome.

---

# Provenance

- 사용자 2026-05-13 세션 "e2e 3단계 전략 Phase 2 시작" — Phase 1 (TASK-MONO-074/075) 종결 직후 바로 진행.
- Phase 1 = TASK-MONO-074 (PR #422) + TASK-MONO-075 fix (PR #425) — paths-filter @v3 quirk 해결, 16 / 15 job 회귀 가드 통과.
- Phase 3 = full e2e nightly 이전 — 본 task 가 정의하는 partition 이 전제. Phase 2 DONE 후 별도 ADR + task.

분석=Opus 4.7 / 구현 권장=Opus 4.7 (spec PR 부분 — ADR 설계 + classification rubric 판단 작업 위주). impl 단계는 분석=Opus 4.7 / 구현 권장=Sonnet 4.6 (mechanical annotation rollout + 잘 정의된 acceptance criteria; rubric 은 ADR 에 이미 명시).
