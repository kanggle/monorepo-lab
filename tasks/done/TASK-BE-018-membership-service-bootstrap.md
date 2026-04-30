# Task ID

TASK-BE-018

# Title

membership-service 부트스트랩 — 구독 생명주기, 접근 권한 체크, 만료 스케줄러

# Status

ready

# Owner

backend

# Task Tags

- code
- api
- event
- db

# depends_on

- (없음 — account-service는 WireMock으로 모킹)

---

# Goal

membership-service를 실행 가능한 Spring Boot 애플리케이션으로 초기화하고, 구독 활성화(`POST /api/membership/subscriptions`), 구독 해지(`DELETE /api/membership/subscriptions/{id}`), 내 구독 조회(`GET /api/membership/subscriptions/me`), 내부 접근 권한 체크(`GET /internal/membership/access`)를 구현한다. `SubscriptionStatusMachine`으로 상태 전이를 관리하고, `@Scheduled` 만료 스케줄러가 ACTIVE → EXPIRED 배치 처리를 실행하며, 모든 구독 이벤트는 outbox 패턴으로 Kafka에 발행한다.

---

# Scope

## In Scope

- `apps/membership-service/` 모듈 생성 (`settings.gradle` include 추가)
- 패키지 구조: `presentation / application / domain / infrastructure` ([architecture.md](../../specs/services/membership-service/architecture.md))
- Flyway 마이그레이션: `membership_plans`, `subscriptions`, `subscription_status_history`, `content_access_policies`, `outbox_events` + 초기 데이터 (FREE/FAN_CLUB 플랜)
- 공개 API: 구독 활성화(+Idempotency-Key), 해지, 내 구독 조회
- 내부 API: `/internal/membership/access` (community-service 호출 수신)
- `SubscriptionStatusMachine` 도메인 객체 (NONE→ACTIVE→EXPIRED/CANCELLED)
- `subscription_status_history` append-only 기록 (DB 트리거 포함)
- `AccountStatusChecker` 포트 + `AccountStatusClient` WireMock 기반 구현체
- `PaymentGateway` 포트 + `PaymentGatewayStub` 구현체 (항상 SUCCESS)
- `SubscriptionExpiryScheduler` (@Scheduled, 1시간 주기, `expires_at <= NOW()` 배치)
- Resilience4j CircuitBreaker — AccountStatusClient (fail-closed: 구독 거부)
- Outbox 이벤트: `membership.subscription.activated`, `membership.subscription.expired`, `membership.subscription.cancelled`
- Prometheus Micrometer 메트릭 ([observability.md](../../specs/services/membership-service/observability.md))
- 단위 + slice + 통합 테스트

## Out of Scope

- 실제 결제 PG 연동 (stub만)
- 자동 갱신
- 멀티 아티스트별 독립 구독
- 환불 처리

---

# Acceptance Criteria

- [ ] `./gradlew :apps:membership-service:bootRun` 성공, `/actuator/health` → 200
- [ ] `POST /api/membership/subscriptions` (FAN_CLUB, ACTIVE 계정) → 201 + `membership.subscription.activated` outbox 이벤트 기록
- [ ] 동일 planLevel 중복 구독 → 409 `SUBSCRIPTION_ALREADY_ACTIVE`
- [ ] LOCKED 계정 구독 시도 → 409 `ACCOUNT_NOT_ELIGIBLE` (WireMock: status=LOCKED)
- [ ] account-service 503 시 구독 활성화 → 503 (fail-closed)
- [ ] Idempotency-Key 중복 요청 → 200 + 원래 응답 반환 (재처리 없음)
- [ ] `DELETE /api/membership/subscriptions/{id}` → 204 + ACTIVE→CANCELLED 전이
- [ ] `GET /api/membership/subscriptions/me` → 200 + ACTIVE 구독 목록, activePlanLevel
- [ ] `GET /internal/membership/access?accountId=&requiredPlanLevel=FAN_CLUB` (ACTIVE FAN_CLUB) → `allowed: true`
- [ ] `GET /internal/membership/access` (FREE 플랜) → `allowed: false`
- [ ] 외부 요청이 `/internal/*` 에 도달 불가 (Security 설정)
- [ ] `SubscriptionExpiryScheduler` 실행 시 만료된 ACTIVE 구독 → EXPIRED 전이 + 이벤트
- [ ] `SubscriptionStatusMachine` 불허 전이 (EXPIRED→ACTIVE) → 예외
- [ ] `subscription_status_history` append-only 트리거 동작 검증
- [ ] Flyway 마이그레이션 + 초기 데이터 (FREE/FAN_CLUB 플랜, MEMBERS_ONLY 정책) 정상 실행
- [ ] `./gradlew :apps:membership-service:test` — 모든 테스트 통과

---

# Related Specs

- `specs/services/membership-service/architecture.md`
- `specs/services/membership-service/overview.md`
- `specs/services/membership-service/data-model.md`
- `specs/services/membership-service/dependencies.md`
- `specs/services/membership-service/observability.md`

# Related Skills

- `.claude/skills/service-types/rest-api-setup/SKILL.md`
- `.claude/skills/backend/springboot-api/SKILL.md`
- `.claude/skills/backend/validation/SKILL.md`
- `.claude/skills/database/schema-change-workflow/SKILL.md`

---

# Related Contracts

- `specs/contracts/http/membership-api.md`
- `specs/contracts/http/internal/community-to-membership.md`
- `specs/contracts/events/membership-events.md`

---

# Target Service

- `apps/membership-service`

---

# Architecture

`specs/services/membership-service/architecture.md` — Layered + 명시적 구독 상태 기계. `AccountStatusChecker`와 `PaymentGateway` 도메인 포트로 외부 의존성 역전. `SubscriptionExpiryScheduler`는 application use-case를 호출하여 트랜잭션 경계 보장.

---

# Edge Cases

- 만료 스케줄러 멱등: 이미 EXPIRED인 구독은 재처리 시 건너뜀 (status = ACTIVE인 것만 처리)
- 구독 해지 직후 접근 체크: CANCELLED 즉시 반영 → `allowed: false`
- FREE 플랜 구독: `expiresAt: null` (영구), 만료 스케줄러 대상 제외
- Idempotency-Key TTL 24시간 만료 후 동일 key 재요청 → 신규 처리

---

# Failure Scenarios

- account-service 503 → CircuitBreaker OPEN → fallback: 503 반환 (구독 활성화 거부)
- MySQL 미기동 → 앱 기동 실패
- Kafka 장애 → outbox 저장 완료, 발행 지연 허용
- 스케줄러 실행 중 DB 장애 → 다음 주기에 재처리 (멱등 보장)

---

# Test Requirements

- Unit: `SubscriptionStatusMachine` 전이 규칙 전체, `Subscription` 도메인 객체 생성 규칙
- Slice: `@WebMvcTest` — SubscriptionController, 내부 ContentAccessController
- Repository: `@DataJpaTest` + Testcontainers — 만료 배치 쿼리 (`expires_at <= NOW() AND status = ACTIVE`)
- Integration: `@SpringBootTest` + Testcontainers + WireMock — 구독 활성화→접근 체크, Idempotency-Key, CircuitBreaker fail-closed
- Scheduler: `@SpringBootTest` + Testcontainers — 만료 처리 E2E (시간 조작: `expires_at`을 과거로 삽입)

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests added and passing
- [ ] Contracts match (membership-api.md, community-to-membership.md, membership-events.md)
- [ ] Flyway migration + 초기 데이터 적용
- [ ] `subscription_status_history` immutability verified
- [ ] 내부 엔드포인트 외부 접근 차단 확인
- [ ] Prometheus metrics 엔드포인트 노출 확인
- [ ] Ready for review
