# Task ID

TASK-MONO-312

# Title

`libs/java-messaging` outbox v1 dead-code 제거 — 6개 v1 클래스 삭제 + `OutboxAutoConfiguration`/`OutboxJpaConfig` v1 잔재 정리 (v2 공유 인프라 보존)

# Status

review

# Owner

backend

# Task Tags

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

# Goal

Outbox v1→v2 마이그레이션 SWEEP가 2026-06-27 전 플랫폼 100% 완료되면서(#1997~#2004; ADR-MONO-004 §6 capstone) `libs/java-messaging`의 **v1 outbox 클래스군이 프로덕션 consumer 0건**이 되었다. origin/main 기준 정밀 확인:

- `src/main`에서 v1 FQN(`com.example.messaging.outbox.{OutboxPublisher,OutboxPollingScheduler,OutboxWriter,OutboxJpaEntity,OutboxJpaRepository}`, `com.example.messaging.event.BaseEventPublisher`)을 **import·extends 하는 프로덕션 코드 0건**. 남은 `extends BaseEventPublisher` 매치는 전부 "Previously extended …" javadoc.
- v1 `OutboxJpaRepository` — `src/main` 0건, `src/test` 0건 (v2 IT 전환 완료: relay/원자성 IT가 v2 repo로 교체됨).

이 task는 6개 v1 클래스 + 대응 lib 단위테스트를 삭제하고, **삭제 클래스를 참조하는 보존 config 2곳을 정확히 수술**한다. v2가 재사용하는 공유 인프라(`AbstractOutboxPublisher`, `OutboxRow`, `OutboxRowRepository`, `MicrometerOutboxMetrics`, `ProcessedEventJpaEntity`/`ProcessedEventJpaRepository`, `OutboxAutoConfiguration` 클래스 자체)는 **건드리지 않는다**.

**근거**: 2026-06-28 outbox v2 sweep 후속 dead-code 식별 (미티켓 → 본 task로 정식화).

---

# Scope

## In Scope

### A. lib v1 클래스 삭제 (`libs/java-messaging/src/main`)

| 삭제 대상 | 사망 근거 |
|---|---|
| `com/example/messaging/outbox/OutboxPublisher.java` | v1 relay, 프로덕션 extends/bean 0 |
| `com/example/messaging/outbox/OutboxPollingScheduler.java` | v1 relay base, 프로덕션 extends 0 |
| `com/example/messaging/outbox/OutboxWriter.java` | v1 write port, 유일 consumer = `BaseEventPublisher`(동반 삭제) |
| `com/example/messaging/outbox/OutboxJpaEntity.java` | v1 엔티티, 프로덕션 참조 0 (orm.xml override만 — In Scope D에서 제거) |
| `com/example/messaging/outbox/OutboxJpaRepository.java` | v1 repo, main·test 0건 |
| `com/example/messaging/event/BaseEventPublisher.java` | v1 publisher base, 프로덕션 extends 0 (전부 v2 `AbstractOutboxPublisher`로 이주) |

### B. lib v1 단위테스트 삭제 (`libs/java-messaging/src/test`)

- `outbox/OutboxPublisherTest.java`
- `outbox/OutboxPollingSchedulerTest.java`
- `outbox/OutboxWriterTest.java`
- `event/BaseEventPublisherTest.java`
- (보존: `outbox/AbstractOutboxPublisherTest.java` = v2)

### C. lib 보존 config 수술 (클래스는 유지, v1 잔재만 제거)

1. **`outbox/OutboxAutoConfiguration.java`** — v1 `@Bean outboxWriter(...)` + `@Bean outboxPublisher(...)` 두 메서드 제거. **클래스 자체는 보존**(아래 Out-of-Scope 참조: 수십 개 서비스가 `exclude = OutboxAutoConfiguration.class`로 클래스명을 하드코딩 → 삭제 시 그 서비스들 컴파일 깨짐). 두 bean 제거 후 빈 `@AutoConfiguration` 셸이 되면 그대로 둔다.
2. **`outbox/OutboxJpaConfig.java`** — `@EntityScan(basePackageClasses = {OutboxJpaEntity.class, ProcessedEventJpaEntity.class})`에서 **`OutboxJpaEntity.class`만 제거**, `ProcessedEventJpaEntity.class`는 **반드시 유지**(v2 inbox 멱등성 dedup 엔티티, 라이브). 클래스 보존.
3. **`outbox/OutboxSchedulerConfig.java`** — `OutboxPollingScheduler`를 javadoc에서만 참조. 구현자가 컴파일/사용처를 확인하여: 보존 consumer가 있으면 javadoc만 수정, 아무 서비스도 import 안 하면(= bean dead) 삭제. 판정은 컴파일러 + `git grep OutboxSchedulerConfig`로.

### D. 프로젝트 측 필수 편집 (컴파일/부팅 결합)

- **`projects/iam-platform/apps/security-service/src/main/resources/META-INF/orm.xml`** — `<entity class="com.example.messaging.outbox.OutboxJpaEntity">` table-name override 블록 제거(삭제 클래스 참조 → Hibernate 부팅 실패 방지). security-service는 자체 `SecurityOutboxJpaEntity`(v2) 사용.

## Out of Scope

- **서비스 `exclude = OutboxAutoConfiguration.class` 정리** — 수십 개 서비스에 산재한 vestigial exclude. 빈 셸 auto-config를 exclude하는 것은 무해 → 제거하면 cross-project churn만 발생. **보존**(별도 cosmetic task 후보).
- **javadoc 코멘트 scrub** — `OutboxConfig.java`/`*Application.java`의 "v1 relay … is gone" 류 설명 주석(promotion·artist·community·membership·procurement·wms-inventory 등). 컴파일 무관, 역사 기록 가치 → 보존.
- v2 공유 인프라 일체: `AbstractOutboxPublisher`, `OutboxRow(Repository)`, `MicrometerOutboxMetrics`, `ProcessedEventJpaEntity(Repository)`, `OutboxAutoConfiguration` 클래스 셸.
- 서비스별 v2 publisher/엔티티/마이그레이션.

---

# Acceptance Criteria

- [ ] **AC-1 (삭제 완료)** — In Scope A의 6개 클래스 + B의 4개 테스트 파일이 `git rm` 되어 트리에 없다.
- [ ] **AC-2 (v1 잔재 grep clean)** — `git grep -E "com\.example\.messaging\.(outbox\.(OutboxPublisher|OutboxPollingScheduler|OutboxWriter|OutboxJpaEntity|OutboxJpaRepository)|event\.BaseEventPublisher)"`가 **코드(`src/`)에서 0건**(done-task md/javadoc 코멘트는 허용; orm.xml은 AC-4로 0건).
- [ ] **AC-3 (config 보존+수술)** — `OutboxAutoConfiguration.java`·`OutboxJpaConfig.java` 파일은 존재하며, 전자는 v1 @Bean 2개 없음, 후자의 `@EntityScan`은 `ProcessedEventJpaEntity.class`를 **포함**하고 `OutboxJpaEntity.class`를 **불포함**.
- [ ] **AC-4 (orm.xml)** — security-service `orm.xml`에 `OutboxJpaEntity` 참조 0건.
- [ ] **AC-5 (lib 빌드 GREEN)** — `./gradlew :libs:java-messaging:test` 통과(잔존 테스트가 삭제 클래스 미참조).
- [ ] **AC-6 (전 서비스 컴파일 GREEN)** — `./gradlew compileJava compileTestJava` (또는 CI 빌드 매트릭스)가 전 프로젝트 GREEN — `OutboxAutoConfiguration` exclude 참조가 깨지지 않음을 보장.
- [ ] **AC-7 (iam security 부팅 회귀 가드)** — security-service `:check`(Testcontainers `@SpringBootTest` IT 포함)가 orm.xml 편집 후 GREEN — 삭제된 엔티티 매핑이 컨텍스트 기동을 깨지 않음.

---

# Related Specs

> **Before reading Related Specs**: Follow `platform/entrypoint.md` Step 0 — read each touched project's `PROJECT.md`, then load `rules/common.md` + matching `rules/domains/<domain>.md`/`rules/traits/<trait>.md`. 본 task는 shared `libs/` 변경 + iam-platform 1편집 = 단일 atomic PR(`CLAUDE.md § Cross-Project Changes`).

- `platform/shared-library-policy.md` (libs/ project-agnostic 유지)
- `platform/testing-strategy.md`
- `docs/adr/ADR-MONO-004` §6 (outbox v2 sweep capstone)

# Related Skills

- `.claude/skills/messaging/outbox-pattern/` (v1/v2 분기 이해)
- `.claude/skills/refactor-code/` (behavior-preserving 삭제)

---

# Related Contracts

- 없음 — 와이어 포맷/이벤트 컨트랙트 변경 없음(v1 클래스는 이미 미발행, 순수 dead-code 삭제).

---

# Target Service

- `libs/java-messaging` (주 변경)
- `iam-platform / security-service` (orm.xml 1편집)

---

# Architecture

- v2 outbox 구조: 각 서비스 `*OutboxPublisher extends AbstractOutboxPublisher<*OutboxEntity>` + 서비스-로컬 `OutboxRow` 엔티티. 본 task는 그 위에 남은 v1 레거시 표면을 제거.

---

# Implementation Notes

- **삭제 순서**: 먼저 C(config 수술: `OutboxAutoConfiguration` @Bean 제거 + `OutboxJpaConfig` EntityScan 트림) + D(orm.xml) → 그 다음 A/B(클래스·테스트 삭제). 역순이면 중간 상태에서 config가 없는 클래스를 참조해 컴파일 깨짐.
- **`OutboxAutoConfiguration` 절대 삭제 금지**: `git grep -l "OutboxAutoConfiguration" -- 'projects/**/src/main/**'`로 ~수십 서비스가 `@SpringBootApplication(exclude=...)`/`@EnableAutoConfiguration(exclude=...)`에 클래스명을 직접 박아둠을 확인. 빈 셸로 남겨 모든 exclude 참조를 유효하게 유지.
- **`ProcessedEventJpaEntity` 절대 EntityScan에서 빼지 말 것**: v2 consumer 멱등성(inbox dedup)이 이 엔티티 스캔에 의존. `OutboxJpaConfig`의 두 엔티티 중 `OutboxJpaEntity`만 제거.
- `OutboxSchedulerConfig`는 보수적으로: 사용처 0 확인 시에만 삭제, 불확실하면 javadoc만 정리하고 보존.

---

# Edge Cases

- `OutboxSchedulerConfig`가 v2 `@Scheduled` relay의 `ThreadPoolTaskScheduler` bean을 제공 중일 수 있음 → 삭제 전 import/사용 grep 필수.
- security-service orm.xml이 `OutboxJpaEntity`를 다른 v2 엔티티와 같은 `<entity-mappings>`에 섞어 둔 경우, 해당 `<entity>` 블록만 정밀 제거(파일 전체 삭제 금지).
- done-task md/spec architecture.md의 v1 클래스명 언급은 역사 기록 → 수정 대상 아님(AC-2의 grep는 `src/` 한정).

---

# Failure Scenarios

- orm.xml 미편집 후 클래스 삭제 → security-service Hibernate `MappingException`(클래스 미발견)으로 컨텍스트 기동 실패. AC-7이 적발.
- `OutboxAutoConfiguration` 클래스째 삭제 → 수십 서비스 `exclude=` 참조가 `cannot find symbol`로 전 매트릭스 컴파일 RED. AC-6이 적발.
- `OutboxJpaConfig` EntityScan에서 `ProcessedEventJpaEntity`까지 실수로 제거 → v2 멱등성 dedup 엔티티 미스캔으로 consumer 서비스 부팅/중복처리 회귀(단위테스트 미적발, 풀부팅 IT만 적발).

---

# Test Requirements

- `./gradlew :libs:java-messaging:test` (AC-5)
- 전 프로젝트 `compileJava`/`compileTestJava` 또는 CI 빌드 매트릭스 (AC-6)
- iam security-service `:check` Testcontainers IT (AC-7)
- 신규 테스트 불필요 — 순수 삭제(behavior-preserving). 회귀 가드는 기존 빌드/부팅 게이트로 충분.

---

# Definition of Done

- [ ] In Scope A/B 삭제 완료, C/D 편집 완료
- [ ] AC-1~AC-7 전부 통과
- [ ] v2 공유 인프라(AbstractOutboxPublisher·ProcessedEvent*·OutboxAutoConfiguration 셸) 무변경 확인
- [ ] 단일 atomic PR(libs/ + iam security orm.xml), `refactor(lib):` 스코프
- [ ] Ready for review
