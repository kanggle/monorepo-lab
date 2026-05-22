# Task ID

TASK-MONO-130

# Title

Portfolio-wide `KafkaContainer` → `ConfluentKafkaContainer` migration — Testcontainers 1.20+ 표준 deprecation path 적용. backend `-Xlint:all` audit (post TASK-MONO-129) 잔여 `[deprecation]` warning 의 가장 큰 cluster (17 test files / 7 projects). All sites use `confluentinc/cp-kafka:7.6.0` image → `ConfluentKafkaContainer` (in `org.testcontainers.kafka`) target. API surface preserved via inherited `GenericContainer` methods (`.waitingFor()`, `.withStartupTimeout()`, `.getBootstrapServers()` 모두 inherited).

# Status

ready

# Owner

backend

# Task Tags

<!-- api | event | deploy | code | test | adr | onboarding -->

- code
- test

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

- **depends on**: 없음. Testcontainers 1.20.4+ (`ConfluentKafkaContainer` introduction version) 이미 portfolio 전체 적용 (parent build.gradle 또는 Testcontainers BOM). `org.testcontainers.kafka.ConfluentKafkaContainer` 클래스 사용 가능.
- **origin**: backend `-Xlint:all` audit (post TASK-MONO-129 MockBean 122 occurrence 해소) 잔여 `[deprecation]` warning 의 가장 큰 cluster. `org.testcontainers.containers.KafkaContainer has been deprecated` — Testcontainers 1.20.4+ 가 Confluent Platform images 전용 `ConfluentKafkaContainer` 신규 path 지정 + Apache Kafka KRaft 용 `org.testcontainers.kafka.KafkaContainer` 별도 신규 path 지정. portfolio 가 `confluentinc/cp-kafka:7.6.0` 만 사용 → `ConfluentKafkaContainer` 가 single target.
- **prerequisite for**: 없음 (cleanup-only). LoginController sunset (2026-08-01) + `[serial]` warnings 등 다른 deferred backlog 와 독립.

---

# Goal

Testcontainers 1.20.4+ 가 `org.testcontainers.containers.KafkaContainer` 를 `@Deprecated` 마킹하고 `org.testcontainers.kafka.ConfluentKafkaContainer` (Confluent Platform 전용) 을 standard replacement 로 지정. 본 task 는 portfolio-wide single sweep 으로 17 test files (7 projects: libs/java-test-support + ecommerce/auth + ecommerce/batch + ecommerce/user + erp + finance + scm/procurement + scm/inventory-visibility + scm/tests/e2e + fan/artist + fan/community + fan/tests/e2e + wms/gateway/e2eTest) 의 class + import rename.

**Mechanical pattern (per file)**:

1. `import org.testcontainers.containers.KafkaContainer;` → `import org.testcontainers.kafka.ConfluentKafkaContainer;`.
2. `KafkaContainer` (모든 occurrence) → `ConfluentKafkaContainer` (type reference + constructor call).

**Behavior preservation**:

- `ConfluentKafkaContainer extends GenericContainer<ConfluentKafkaContainer>` — `.waitingFor()`, `.withStartupTimeout()`, `.getBootstrapServers()`, `.start()`, `.stop()`, `withCommand()`, `withEnv()` 모두 `GenericContainer` base 에서 inherited.
- `getBootstrapServers()` 동일 시그니처 + 동일 의미 — Kafka broker 연결 string 반환.
- 단 한 file (libs/java-test-support `AbstractIntegrationTest`) 가 custom `.waitingFor(Wait.forLogMessage(".*\\[KafkaServer id=\\d+\\] started.*", 1))` 사용. `ConfluentKafkaContainer` 가 자체 internal wait strategy 보유하지만 GenericContainer `.waitingFor()` 가 override 가능 — 우선 그대로 유지하고 CI 결과로 검증 (만약 CI 실패 시 `.waitingFor()` 제거 후 `ConfluentKafkaContainer` 내장 wait strategy 위임).

**기대 효과**:

- backend `-Xlint:all` `[deprecation]` warning 중 KafkaContainer 관련 ~17건 (file 마다 ~1-2 instance 누적) 해소.
- Testcontainers 의 future major version 에서 `KafkaContainer` 가 hard-removed 될 시점 사전 대비.
- Portfolio test infrastructure 가 modern Testcontainers API 로 일관화.

---

# Decision authority

- **Why monorepo-level (`tasks/`) and NOT project-internal**: libs/java-test-support 영향 (shared library, monorepo-level) + 6 projects 동시 touch. CLAUDE.md § Cross-Project Changes atomic 원칙 — MONO-129 의 mirror precedent.
- **Why single impl PR (NOT per-project split)**: mechanical pattern 100% 동일 (class + import rename). MONO-129 가 동일 단일-atomic-PR 패턴 1 cycle GREEN 으로 검증. 6+ projects split → 12-cycle overhead 회피.
- **Why no spec/contract change**: test infrastructure only. production behavior / API contract 영향 0.
- **Why no ADR**: HARDSTOP-09 not triggered. Testcontainers framework upgrade migration path = shared-library-policy.md 의 "framework upgrade migration" 정상 lifecycle. 새 architectural decision 아님.
- **Why `ConfluentKafkaContainer` (NOT `org.testcontainers.kafka.KafkaContainer`)**: portfolio 가 100% `confluentinc/cp-kafka:7.6.0` image 사용 — Apache Kafka KRaft `KafkaContainer` (`apache/kafka:VERSION` image 용) 와 직접 호환 불가. `ConfluentKafkaContainer` 가 Confluent platform 전용 신규 path, drop-in target.
- **Why keep `.waitingFor()` initially**: 단 1 file 에서만 사용 (libs/java-test-support). 안전한 marginal change 보존 후 CI 결과로 검증. 만약 CI 가 timeout 또는 무관 log 매치 fail 시 `.waitingFor()` 제거 후 `ConfluentKafkaContainer` 내장 wait strategy 위임 (별 follow-up cycle, MONO-129 동형 retry pattern).

---

# Scope

## In Scope

**Specs (spec PR — this PR)**:

- 본 task file.
- `tasks/INDEX.md` — root INDEX ready entry.

**Code (impl PR — out of scope here, dispatch shape)**:

17 test files across 8 modules (모두 동일 mechanical pattern):

- **libs/java-test-support** (1 file): `AbstractIntegrationTest.java` (shared base; cascades to GAP + scm + finance + wms via inherit).
- **fan-platform** (3 files): `artist-service/ArtistServiceIntegrationBase`, `community-service/CommunityServiceIntegrationBase`, `tests/e2e/FanPlatformE2ETestBase`.
- **erp-platform** (1 file): `masterdata-service/AbstractMasterdataIntegrationTest`.
- **finance-platform** (1 file): `account-service/AbstractAccountIntegrationTest`.
- **scm-platform** (3 files): `procurement-service/AbstractProcurementIntegrationTest`, `inventory-visibility-service/AbstractInventoryVisibilityIntegrationTest`, `tests/e2e/ScmPlatformE2ETestBase`.
- **wms-platform** (1 file): `gateway-service/E2EBase` (e2eTest source set).
- **ecommerce-microservices-platform** (7 files): `auth-service/UserWithdrawalIntegrationTest` + `AuthSignupKafkaPublishEventIntegrationTest`, `batch-worker/AbstractIntegrationTest`, `user-service/WishlistIntegrationTest` + `UserProfileIntegrationTest` + `AddressIntegrationTest` + `UserWithdrawnEventIntegrationTest` + `UserSignedUpConsumerIntegrationTest` + `UserProfileUpdatedEventIntegrationTest`.

(상세 list 는 `git grep "import org.testcontainers.containers.KafkaContainer" --name-only` 가 권위)

## Out of Scope

- LoginController sunset (별 task — production endpoint, 2026-08-01).
- `[serial]` warnings (exception class `serialVersionUID` 부재) — 별 audit category.
- Apache Kafka KRaft 로의 image 변경 (`apache/kafka:VERSION`) — Confluent Platform image 유지.
- `KafkaContainer` 의 다른 API (`.withListener()`, `.withTransactionTimeout()` etc.) 변경 — 사용처 0.

---

# Acceptance Criteria

**AC-1** — 17 test files 모두 `import org.testcontainers.containers.KafkaContainer;` → `import org.testcontainers.kafka.ConfluentKafkaContainer;` 교체 완료.

**AC-2** — 17 test files 의 모든 `KafkaContainer` reference (type declaration + constructor `new KafkaContainer(...)`) → `ConfluentKafkaContainer` (~17-20 occurrences total).

**AC-3** — Repo-wide grep `import org\.testcontainers\.containers\.KafkaContainer` 가 production code + test code 에 **0 match** (`*.java`).

**AC-4** — Repo-wide grep `\bKafkaContainer\b` 가 production code + test code 에 **0 match** (`*.java`). Historical task md 또는 reference doc 에서의 mention 허용.

**AC-5** — `import org.testcontainers.kafka.ConfluentKafkaContainer;` 가 17 file 전체에 추가 (각 1줄).

**AC-6** — LOCAL `compileTestJava` (또는 `compileE2eTestJava`) 영향 받는 8 modules BUILD SUCCESSFUL.

**AC-7** — CI Linux 의 모든 `Integration (X, Testcontainers)` job + E2E 관련 jobs GREEN (gap / scm / wms / erp / finance / ecommerce backend / fan-platform / scm-platform e2e / fan-platform e2e). `getBootstrapServers()` 같은 API 가 inherited 라 spring property registration 호환.

**AC-8** — Production code byte-unchanged: `git diff --stat origin/main -- 'projects/*/src/main/'` + `git diff --stat origin/main -- 'libs/*/src/main/'` 둘 다 empty.

**AC-9** — Zero ADR drift: `git diff --stat origin/main -- 'docs/adr/'` empty.

**AC-10** — Zero spec/contract drift: `git diff --stat origin/main -- 'projects/*/specs/' 'platform/' 'rules/'` empty.

---

# Related Specs

- `libs/java-test-support/src/testFixtures/java/com/example/testsupport/integration/AbstractIntegrationTest.java` — shared base 의 KafkaContainer field type 가 변경되지만 inherited API 동일 — subclass 호출처 byte-unchanged.
- 본 task 가 spec 변경 없이 implementation-only.

---

# Related Contracts

- 없음. Test infrastructure cleanup, API/event contract 영향 없음.

---

# Edge Cases

- **`.waitingFor(Wait.forLogMessage(...))` custom wait strategy in libs/java-test-support**: 1 file only. `ConfluentKafkaContainer` 가 `GenericContainer` 의 `.waitingFor()` 를 inherit — code change 0. CI 결과로 검증 (cp-kafka:7.6.0 log 가 `[KafkaServer id=\d+] started` 형태 emit 시 `.waitingFor()` 가 정확 wait, 형태 다르면 `ConfluentKafkaContainer` 의 internal strategy 가 fallback).
- **`KafkaContainer<?>` generic 사용**: grep 검증 — 0 (모든 사용처 가 raw `KafkaContainer`).
- **Reflection-based access** (`Class.forName("KafkaContainer")` etc.): 0 (grep verified, unlikely in test code).
- **Multi-line constructor split**: 일부 file 이 `new KafkaContainer(\n DockerImageName.parse(...))` 형태 사용 — PowerShell `-replace` 가 substring match 라 multi-line 영향 없음 (식별자 단위 교체).
- **Cross-project shared base cascade**: libs/java-test-support `AbstractIntegrationTest` 의 type 변경이 subclass 호출처 byte-unchanged 인지 검증 — subclass 는 `KAFKA.start()`, `KAFKA.getBootstrapServers()` 만 호출, type 노출 없음.

---

# Failure Scenarios

- **F1 — `ConfluentKafkaContainer` 가 Testcontainers BOM 버전 호환 안 됨**: build.gradle Testcontainers BOM 검증 — ≥ 1.20.4 필요. portfolio 의 build.gradle 가 이 버전 또는 그 이상이어야 함. 검증 not pass → 해당 module hold + 별 task.
- **F2 — `ConfluentKafkaContainer` 의 internal wait strategy 가 cp-kafka:7.6.0 와 부호 — CI Integration timeout**: AC-7 CI 가 catch. 발생 시 `.waitingFor()` retain (libs file 만 해당) 또는 모든 file 에 `.waitingFor(Wait.forLogMessage(...))` 명시 추가.
- **F3 — Test runtime cross-broker race condition**: `getBootstrapServers()` 가 inherited 동일 시그니처라 spring property 호환. unlikely.
- **F4 — `KafkaContainer` 의 deprecated method 가 `ConfluentKafkaContainer` 에 부재**: 사용처 grep — `KAFKA.withCommand()` / `.withStartupTimeout()` / `.withEnv()` 만 사용 → 모두 GenericContainer inherited. unlikely.

---

# Implementation hints (dispatch agent)

1. `git checkout -b task/mono-130-impl-kafkacontainer-confluentkafkacontainer-migration`.
2. PowerShell .NET I/O sweep (MONO-129 reference pattern):
   ```
   $files = git grep -l "import org\.testcontainers\.containers\.KafkaContainer" -- '*.java'
   foreach ($file in $files) {
       $content = [System.IO.File]::ReadAllText($file, [System.Text.Encoding]::UTF8)
       $content = $content -replace 'import org\.testcontainers\.containers\.KafkaContainer;', 'import org.testcontainers.kafka.ConfluentKafkaContainer;'
       $content = $content -replace '\bKafkaContainer\b', 'ConfluentKafkaContainer'
       [System.IO.File]::WriteAllText($file, $content, [System.Text.UTF8Encoding]::new($false))
   }
   ```
3. LOCAL verify:
   ```
   git grep "import org\.testcontainers\.containers\.KafkaContainer" -- '*.java'  # expect 0
   git grep "\bKafkaContainer\b" -- '*.java'  # expect 0
   ./gradlew compileTestJava --no-daemon -q  # expect BUILD SUCCESSFUL
   ```
4. Push branch + open PR; CI Linux 가 권위.
5. If CI Integration timeout/fail (F2 scenario): retry cycle — libs/java-test-support `AbstractIntegrationTest` 의 `.waitingFor(...)` retain 또는 모든 file 에 wait 명시.
6. BE-303 3-dim 객관 머지 검증 후 close chore.

---

# 분석 / 구현 / 리뷰 모델 권장

- **분석=Opus 4.7 / 구현 권장=Opus 4.7 (직접; MONO-129 mirror pattern, mechanical sweep + CI iteration possibility)**.
