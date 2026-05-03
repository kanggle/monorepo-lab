# Task ID

TASK-MONO-025

# Title

`libs:java-messaging` BaseEventPublisher 의 `event_id` 를 UUID v4 → v7 로 마이그레이션

# Status

ready

# Owner

backend

# Task Tags

- code
- refactor

---

# Required Sections

- Goal
- Scope (in/out)
- Acceptance Criteria
- Related Specs
- Edge Cases
- Failure Scenarios

---

# Goal

`libs/java-messaging/src/main/java/com/example/messaging/event/BaseEventPublisher.java` 의 `eventId` 생성을 `UUID.randomUUID()` (v4) 에서 `com.example.common.UuidV7.randomUuid()` (v7) 으로 교체한다.

UUID v7 은 시간 정렬 가능 (timestamp-prefixed) 으로:
- 컨슈머 측 멱등 dedupe 시 시간순 정렬 → 인덱스 친화적
- 운영 관측 도구 (Kafka UI, Grafana) 에서 시간순 sort 가능
- outbox 의 `created_at` 와 `event_id` 의 시간 순서가 일관됨

이 결정은 [PR #116](https://github.com/kanggle/monorepo-lab/pull/116) 코드 리뷰 (TASK-FAN-BE-002 community-service) 에서 도출됨. fan-platform 의 [community-events.md § Common envelope](../../projects/fan-platform/specs/contracts/events/community-events.md) 가 명시적으로 *"멱등 키: `event_id` (UUID v7)"* 를 spec 으로 못박고 있어 lib 차원에서 정렬 필요.

cross-project 변경이라 별도 monorepo-level task 로 분리. PR #116 에서 처리할 경우 영향 받는 모든 서비스 테스트가 같은 PR 에 묶여 scope 가 폭증.

이 태스크 완료 후:
- `BaseEventPublisher` 가 v7 UUID 를 발행
- 모든 consumer 서비스 (GAP auth/community/membership/security + fan-platform community) 의 단위/통합 테스트가 통과
- 기존에 v4 로 생성된 outbox 행은 그대로 (consumer 멱등 키는 어떤 UUID 든 유효한 멱등성 보장)

---

# Scope

## In Scope

### 1. libs 변경
- `libs/java-messaging/src/main/java/com/example/messaging/event/BaseEventPublisher.java` 의 `eventId` 생성을 `com.example.common.UuidV7.randomUuid().toString()` 으로 교체
- import 추가: `com.example.common.UuidV7`
- libs:java-messaging 의 `build.gradle` 이 이미 `libs:java-common` 에 의존하는지 확인 — 미의존 시 compile 의존성 추가

### 2. libs 단위 테스트
- `BaseEventPublisherTest` (있으면) 또는 신규 테스트로 다음 검증:
  - 생성된 `eventId` 가 RFC 9562 §5.7 의 v7 형식 (version bits = `0111`, variant bits = `10`)
  - 두 번 호출 시 두 번째 ID 가 첫 번째 ID 보다 lexicographic order 로 크다 (timestamp 정렬)
- libs:java-common 의 `UuidV7Test` 가 이미 존재하면 그대로 유지 (이중 검증 불필요)

### 3. 영향받는 consumer 서비스 테스트 재실행
- GAP `auth-service`, `community-service`, `membership-service`, `security-service`
- fan-platform `community-service`
- 각 서비스의 `:test` (단위 + 슬라이스) 가 모두 통과해야 함
- integration test 는 Docker 필요 — Windows native JVM 에서는 skip, CI Linux 에서 실행

### 4. envelope 필드 검증 회귀 테스트 fix
- 만약 어떤 테스트가 `eventId` 를 정확한 문자열로 비교한다면 (랜덤 v4 가 통과해온 코드) 그 비교 방식이 v7 형식 검증으로 바뀌어야 함
- 일반적으로 `assertThat(eventId).matches(UUID_REGEX)` 패턴이라 영향 없을 가능성 높음

## Out of Scope

- 기존 outbox 행 (v4 로 생성된 것) 의 마이그레이션 — consumer 가 멱등 키로만 사용하므로 형식이 섞여도 정확성에 영향 없음
- Kafka 에 이미 발행된 메시지의 `eventId` 변경 — at-least-once 의미상 불가능, 무관
- `BaseEventPublisher` 외의 publisher (서비스가 자체 publisher 를 우회 구현했을 경우) — 검색 후 없으면 OK
- ProcessedEventJpaEntity 의 PK 형식 변경 — 컨슈머가 String 으로 다룬다면 무관
- Frontend (Next.js) — 영향 없음

---

# Acceptance Criteria

- [ ] `BaseEventPublisher.java` 가 `UuidV7.randomUuid()` 사용
- [ ] `./gradlew :libs:java-messaging:test` 통과
- [ ] `./gradlew :projects:global-account-platform:apps:auth-service:test` 통과
- [ ] `./gradlew :projects:global-account-platform:apps:community-service:test` 통과
- [ ] `./gradlew :projects:global-account-platform:apps:membership-service:test` 통과
- [ ] `./gradlew :projects:global-account-platform:apps:security-service:test` 통과
- [ ] `./gradlew :projects:fan-platform:apps:community-service:test` 통과
- [ ] 신규 `BaseEventPublisherTest` 또는 갱신된 단위 테스트가 v7 형식 + 시간 정렬을 검증
- [ ] integration test 는 CI Linux 에서 실행 — 본 task 의 verification 은 단위/슬라이스 까지

---

# Related Specs

- `projects/fan-platform/specs/contracts/events/community-events.md` § Common envelope ("멱등 키: `event_id` (UUID v7)")
- `platform/event-driven-policy.md` (outbox 패턴 일반)
- `libs/java-common` UuidV7 utility (이미 존재한다면 그대로 활용; 없으면 본 task 에서 추가)

# Related Skills

- `.claude/skills/messaging/event-implementation/SKILL.md`
- `.claude/skills/testing/test-strategy/SKILL.md`

---

# Target Service / Component

- `libs/java-messaging/` (수정)
- `libs/java-common/` (UuidV7 가 없으면 추가)
- `projects/global-account-platform/apps/{auth,community,membership,security}-service/` (테스트 재실행)
- `projects/fan-platform/apps/community-service/` (테스트 재실행)

---

# Architecture

`platform/architecture-decision-rule.md` 따름. libs 변경이라 service architecture 영향 없음.

---

# Implementation Notes

- libs:java-common 에 `UuidV7` 가 이미 존재하는지 먼저 확인 (`grep -r "UuidV7" libs/java-common/src/`). 없으면 RFC 9562 §5.7 구현 (Java 21 표준 라이브러리에는 v7 미포함, 수동 구현 필요. 50줄 미만):
  - 48-bit timestamp (Unix epoch ms)
  - 12-bit random_a
  - 4-bit version (0111)
  - 62-bit random_b
  - 2-bit variant (10)
- 동시 호출 시 같은 ms 내에서 발생하는 monotonicity 는 RFC 의 `Method 1: Monotonic Random` 권장이나 v1 단순 구현은 `Method 0: Pure Random` 도 허용 (충돌 확률 매우 낮음).
- 변경 전후 `BaseEventPublisher` 의 public API (`publishEvent(...)`) 는 변경 없음 — 호출자 영향 없음.
- 모든 영향 서비스의 테스트가 통과해야 머지 가능 — root CI 의 `build-and-test` step 이 자동 검증.

---

# Edge Cases

- **시간 역행 (시스템 시계 변경)**: monotonicity 가 깨지면 v7 ID 가 lexicographic 으로 역행할 수 있음. RFC 는 이를 허용 (v7 는 best-effort sortable). 멱등성에는 영향 없음.
- **libs:java-common 의 UuidV7 가 v1 에 없을 경우**: 본 task 가 v7 utility 구현을 포함. 이는 libs:java-common 의 신규 public API 추가 — 별도 메이저 버전 변경 불필요 (additive).
- **테스트 회귀 (envelope 필드 정확 비교)**: 어떤 테스트가 hardcoded UUID v4 를 기대하면 fail. fix 는 v4 fixture → v7 fixture 또는 형식 검증 (regex / `Uuid.fromString().version() == 7`) 으로 교체.

---

# Failure Scenarios

- **UuidV7 구현 버그**: bit 배열이 잘못되어 RFC 9562 검증을 실패하면 일부 consumer 가 ID 를 invalid UUID 로 거부할 수 있음. 본 task 의 단위 테스트가 RFC 형식 검증 포함 → 빌드 단계에서 차단.
- **libs:java-messaging 변경이 어떤 consumer 의 테스트만 깨뜨림**: 그 consumer 의 테스트가 v4 형식에 의존했다는 신호 — 그 테스트도 동시에 fix.
- **builds:e2e (Docker) 가 CI 에서 실패**: integration 변경 없으므로 가능성 낮음. 발생 시 cross-project regression — 본 task 의 fix 가 incomplete 하다는 신호.

---

# Test Requirements

- 단위:
  - `BaseEventPublisherTest` — v7 형식 검증 + 시간 정렬 검증
  - `UuidV7Test` (libs:java-common, 신규 추가 시)
- 슬라이스: 영향 없음 (publisher 인터페이스 동일)
- 통합 (`@Tag("integration")`): 영향받는 5 서비스의 기존 integration test 가 모두 통과해야 함 (CI Linux)

---

# Definition of Done

- [ ] `BaseEventPublisher.java` 변경 + 단위 테스트 추가/갱신
- [ ] libs:java-common 의 UuidV7 utility 존재 확인 (없으면 추가)
- [ ] 영향 5 서비스의 `:test` 통과
- [ ] root CI green (build-and-test + ecommerce-boot-jars + frontend-checks)
- [ ] PR description 에 영향 받은 서비스 명시 + 본 task 가 PR #116 follow-up 임을 링크
- [ ] Ready for review
