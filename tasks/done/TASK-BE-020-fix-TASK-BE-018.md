# Task ID

TASK-BE-020

# Title

Fix TASK-BE-018 membership-service: layer violation, security fail-open, reason mismatch, missing tests

# Status

ready

# Owner

backend

# Task Tags

- fix
- code
- test
- db

# depends_on

- TASK-BE-018

---

# Goal

TASK-BE-018 리뷰에서 발견된 Critical 4건을 수정한다:
1. `subscription_status_history.reason` 값 불일치 (`SCHEDULER_EXPIRY` → `SCHEDULED_EXPIRE`)
2. `InternalApiFilter` fail-open 버그 (토큰 미설정 시 내부 API 무방비 노출)
3. `Subscription` 도메인 엔티티 계층 위반 (JPA 어노테이션을 domain 레이어에 직접 배치)
4. 누락된 테스트 레이어 (`@DataJpaTest`, `@SpringBootTest` 통합, 스케줄러 E2E)

---

# Scope

## In Scope

### Critical-1: reason 값 수정
- `ExpireSubscriptionUseCase.java`: `"SCHEDULER_EXPIRY"` → `"SCHEDULED_EXPIRE"`
- `data-model.md` 명세(`reason: USER_SUBSCRIBE / USER_CANCEL / SCHEDULED_EXPIRE`)와 일치시킴

### Critical-2: InternalApiFilter fail-closed 수정
- `InternalApiFilter.java`: 토큰이 unconfigured (null 또는 blank)일 때 `/internal/**` 접근을 허용하지 않고 차단(401 반환)
- 단, `spring.profiles.active=test` 또는 별도 dev 프로파일에서는 bypass 허용 (테스트 호환성)
- `application.yml`: `INTERNAL_API_TOKEN` 기본값을 빈 문자열이 아니라 문서화된 경고 처리로 변경

### Critical-3: Subscription 계층 분리
- `Subscription.java` (domain)에서 모든 JPA 어노테이션 제거 (POJO 도메인 객체로 변환)
- `infrastructure/persistence/SubscriptionJpaEntity.java` 신규 생성 (JPA 어노테이션 이관)
- `SubscriptionRepositoryAdapter.java`: `SubscriptionJpaEntity` ↔ `Subscription` 변환 로직 추가
- `SubscriptionJpaRepository.java`: `Subscription` → `SubscriptionJpaEntity` 대상으로 변경
- `Subscription.unsafeSetExpiresAtForTest()` 접근제한을 `public` → package-private으로 변경

### Critical-4: 누락 테스트 추가
- `@DataJpaTest` + Testcontainers (MySQL):
  - `SubscriptionJpaRepositoryTest`: `findExpirable` 쿼리 (만료된 ACTIVE, FREE 제외, 이미 EXPIRED 제외)
- `@SpringBootTest` + Testcontainers + WireMock 통합:
  - `ActivateSubscriptionIntegrationTest`: ACTIVE 계정 구독 활성화 → 201 + outbox 이벤트 기록
  - `ActivateSubscriptionIntegrationTest`: LOCKED 계정 → 409 `ACCOUNT_NOT_ELIGIBLE` (WireMock: status=LOCKED)
  - `ActivateSubscriptionIntegrationTest`: account-service 503 → 503 fail-closed
  - `ActivateSubscriptionIntegrationTest`: Idempotency-Key 재요청 → 200
- `@SpringBootTest` + Testcontainers 스케줄러:
  - `SubscriptionExpirySchedulerTest`: `expires_at`을 과거로 삽입 → 스케줄러 실행 후 EXPIRED 전이 + outbox 이벤트 확인
  - `SubscriptionExpirySchedulerTest`: FREE 구독 (`expiresAt IS NULL`) 제외 확인

## Out of Scope

- Warning/Suggestion 등급 이슈 (선택적 후속 태스크)
- 새로운 기능 추가

---

# Acceptance Criteria

- [ ] `ExpireSubscriptionUseCase`가 `"SCHEDULED_EXPIRE"` reason을 사용 (data-model.md 명세 일치)
- [ ] `InternalApiFilter`: `INTERNAL_API_TOKEN` 미설정 시 `/internal/**` 요청 → 401 반환 (fail-closed)
- [ ] `InternalApiFilter`: test 프로파일에서 토큰 검사 bypass 가능 (기존 `@WebMvcTest` 호환)
- [ ] `Subscription.java`에 `jakarta.persistence` import 없음
- [ ] `SubscriptionJpaEntity.java` 신규 파일 존재, JPA 어노테이션 보유
- [ ] `SubscriptionRepositoryAdapter`가 `SubscriptionJpaEntity` ↔ `Subscription` 변환 처리
- [ ] `Subscription.unsafeSetExpiresAtForTest()` 접근제한이 package-private
- [ ] `@DataJpaTest` 슬라이스 테스트 통과 — `findExpirable` 쿼리 검증
- [ ] `@SpringBootTest` 통합 테스트 통과 — LOCKED 409, 503 fail-closed, Idempotency replay 200
- [ ] 스케줄러 E2E 테스트 통과 — 만료 전이 + FREE 제외
- [ ] `./gradlew :apps:membership-service:test` — 모든 테스트 통과

---

# Related Specs

- `specs/services/membership-service/architecture.md`
- `specs/services/membership-service/data-model.md`

# Related Contracts

- `specs/contracts/http/membership-api.md`
- `specs/contracts/http/internal/community-to-membership.md`
- `specs/contracts/events/membership-events.md`

---

# Target Service

- `apps/membership-service`

---

# Architecture

`specs/services/membership-service/architecture.md` — `infrastructure/persistence/SubscriptionJpaEntity`가 JPA 어노테이션 소유. domain의 `Subscription`은 POJO aggregate root.

---

# Edge Cases

- `SubscriptionJpaEntity` ↔ `Subscription` 변환 시 `version` 필드 보존 (낙관적 락 유지)
- `Subscription.activate()` factory는 JPA 없이도 동작해야 함 (POJO 생성자)
- `InternalApiFilter` bypass는 `@Profile("test")` 또는 프로퍼티 플래그로 명시적 제어

# Failure Scenarios

- `SubscriptionJpaEntity` 변환 중 `version` 필드 누락 → 낙관적 락 동작 불가 → `@Version` 필드 포함 확인
- 통합 테스트에서 WireMock 포트 충돌 → `@AutoConfigureWireMock(port = 0)` 사용

---

# Test Requirements

- Unit: 기존 `SubscriptionStatusMachineTest`, `ActivateSubscriptionUseCaseTest` 유지
- Repository slice: `@DataJpaTest` + Testcontainers — `findExpirable` 쿼리 검증
- Integration: `@SpringBootTest` + Testcontainers + WireMock — 구독 활성화 전체 시나리오
- Scheduler: `@SpringBootTest` + Testcontainers — 만료 배치 E2E

---

# Definition of Done

- [ ] Critical-1 reason 값 수정 완료
- [ ] Critical-2 InternalApiFilter fail-closed 동작 검증
- [ ] Critical-3 Subscription 도메인 POJO 분리 완료
- [ ] Critical-4 누락 테스트 추가 + 전체 테스트 통과
- [ ] Ready for review
