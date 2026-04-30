# Service Architecture — membership-service

## Service

`membership-service`

## Service Type

`rest-api` — 구독 플랜 관리 및 프리미엄 콘텐츠 접근 제어 서비스.

적용되는 규칙: [platform/service-types/rest-api.md](../../../platform/service-types/rest-api.md)

## Architecture Style

**Layered Architecture + 명시적 상태 기계** — `presentation / application / domain / infrastructure` 4계층. `domain/subscription/status/`가 구독 상태 기계를 소유.

## Why This Architecture

- **구독 상태 전이가 핵심 비즈니스 규칙**: NONE → ACTIVE → EXPIRED / CANCELLED 전이는 결제 완료, 만료 스케줄, 사용자 해지 등 여러 트리거로 발생. 직접 UPDATE 금지; 상태 기계가 전이 유효성 검사 ([rules/traits/transactional.md](../../../rules/traits/transactional.md) T4).
- **접근 제어 단일 소유**: 어떤 플랜이 어떤 콘텐츠를 볼 수 있는지는 이 서비스만 알고 있음. community-service는 내부 HTTP로 위임 — 구독 데이터를 분산 복사하지 않음.
- **감사 추적**: 구독 활성화·만료·해지는 `subscription_status_history`에 append-only 기록 ([rules/traits/audit-heavy.md](../../../rules/traits/audit-heavy.md) A1·A3).
- **이벤트 발행**: `membership.subscription.activated`, `membership.subscription.expired`를 outbox 패턴으로 발행 — 향후 notification-service 연결 기반.

## Internal Structure Rule

```
apps/membership-service/src/main/java/com/example/membership/
├── MembershipApplication.java
├── presentation/
│   ├── SubscriptionController.java      ← 구독 CRUD
│   ├── internal/
│   │   └── ContentAccessController.java ← community-service 호출 수신
│   ├── dto/
│   └── exception/
├── application/
│   ├── ActivateSubscriptionUseCase.java
│   ├── CancelSubscriptionUseCase.java
│   ├── ExpireSubscriptionUseCase.java   ← 스케줄러 호출
│   ├── CheckContentAccessUseCase.java
│   └── event/
│       └── MembershipEventPublisher.java
├── domain/
│   ├── plan/
│   │   ├── MembershipPlan.java          ← 엔터티
│   │   ├── PlanLevel.java               ← enum: FREE / FAN_CLUB
│   │   └── repository/
│   │       └── MembershipPlanRepository.java
│   ├── subscription/
│   │   ├── Subscription.java            ← 엔터티 (aggregate root)
│   │   ├── SubscriptionId.java
│   │   ├── status/
│   │   │   ├── SubscriptionStatus.java  ← enum: ACTIVE / EXPIRED / CANCELLED
│   │   │   └── SubscriptionStatusMachine.java
│   │   └── repository/
│   │       └── SubscriptionRepository.java
│   ├── access/
│   │   ├── ContentAccessPolicy.java     ← 엔터티
│   │   └── repository/
│   │       └── ContentAccessPolicyRepository.java
│   └── account/
│       └── AccountStatusChecker.java    ← account-service 호출 포트(인터페이스)
└── infrastructure/
    ├── persistence/
    │   ├── MembershipPlanJpaEntity.java
    │   ├── SubscriptionJpaEntity.java
    │   ├── SubscriptionStatusHistoryJpaEntity.java
    │   ├── ContentAccessPolicyJpaEntity.java
    │   └── *JpaRepository.java
    ├── kafka/
    │   └── MembershipKafkaProducer.java
    ├── client/
    │   └── AccountStatusClient.java     ← AccountStatusChecker 구현체
    ├── scheduler/
    │   └── SubscriptionExpiryScheduler.java ← @Scheduled 만료 처리
    └── config/
```

## Allowed Dependencies

```
presentation → application → domain
                   ↓
            infrastructure → domain
```

- `domain/account/AccountStatusChecker` — 인터페이스 (port). `infrastructure/client/AccountStatusClient`가 구현
- `application` → [libs/java-messaging](../../../libs/java-messaging) (outbox)
- `infrastructure/scheduler/` → `application/ExpireSubscriptionUseCase` (스케줄러가 use-case 호출)

## Forbidden Dependencies

- ❌ `presentation/`이 infrastructure를 직접 참조
- ❌ 구독 상태를 `UPDATE subscriptions SET status = ?`로 직접 변경 — 반드시 `SubscriptionStatusMachine.transition()` 경유
- ❌ `presentation/internal/`을 gateway가 공개 경로로 노출
- ❌ 구독 정보를 community-service DB에 복사/캐시

## Boundary Rules

### presentation/
- 공개: `/api/membership/subscriptions/**`
- 내부(`presentation/internal/`): community-service 전용. mTLS 또는 내부 토큰. 게이트웨이 경유 금지
- `/internal/membership/access` — planLevel 기준 접근 허용 여부 반환 (Boolean)

### application/
- `ActivateSubscriptionUseCase`: 계정 ACTIVE 확인(AccountStatusChecker) → 기존 ACTIVE 구독 중복 확인 → Subscription 생성 → `SubscriptionStatusMachine.transition(NONE→ACTIVE)` → `membership.subscription.activated` 이벤트 (outbox)
- `ExpireSubscriptionUseCase`: 만료일 초과 ACTIVE 구독 배치 조회 → `SubscriptionStatusMachine.transition(ACTIVE→EXPIRED)` → 이벤트 발행. **스케줄러에서 호출, 트랜잭션 보장**
- `CheckContentAccessUseCase`: accountId로 ACTIVE 구독 조회 → planLevel ≥ 요청 레벨 여부 반환. membership-service 자체 DB만 조회 (동기 외부 호출 없음)

### domain/subscription/status/
- `SubscriptionStatusMachine.transition(current, target)`:
  - `NONE → ACTIVE` (구독 활성화)
  - `ACTIVE → EXPIRED` (만료 스케줄러)
  - `ACTIVE → CANCELLED` (사용자 해지)
  - 금지: `EXPIRED → ACTIVE` (재활성화는 신규 구독 생성)
  - 금지: `CANCELLED → ACTIVE`

## Integration Rules

- **HTTP 컨트랙트 (외부)**: [specs/contracts/http/membership-api.md](../../contracts/http/membership-api.md)
- **HTTP 컨트랙트 (내부 수신)**: [specs/contracts/http/internal/community-to-membership.md](../../contracts/http/internal/community-to-membership.md)
- **이벤트 발행**: [specs/contracts/events/membership-events.md](../../contracts/events/membership-events.md) — `membership.subscription.activated`, `membership.subscription.expired`
- **퍼시스턴스**: MySQL (`membership_db`) — `membership_plans`, `subscriptions`, `subscription_status_history`, `content_access_policies`, `outbox_events`

## Testing Expectations

| 레이어 | 목적 | 도구 |
|---|---|---|
| Unit | `SubscriptionStatusMachine` 전이 규칙 (허용/불허) | JUnit 5 |
| Repository slice | JPA 쿼리, 만료 배치 조회 | `@DataJpaTest` + Testcontainers (MySQL) |
| Application integration | use-case 트랜잭션 · outbox · 이벤트 | Testcontainers + Kafka |
| Controller slice | 공개/내부 컨트롤러 분리 · DTO validation | `@WebMvcTest` |
| Client mock | AccountStatusClient LOCKED 시 구독 거부 | WireMock |
| Scheduler | 만료 배치 정상 처리 | `@SpringBootTest` + Testcontainers |

**필수 시나리오**: LOCKED 계정 구독 시도 시 409 / 이미 ACTIVE 구독 중복 시 409 / 만료 스케줄러 정상 전이 / EXPIRED 구독 재활성화 시 신규 구독 생성 / 내부 엔드포인트에 외부 요청 불가.

## Change Rule

1. 플랜 레벨 추가 → `PlanLevel` enum + `content_access_policies` 데이터 마이그레이션 선행
2. 결제 게이트웨이 연동 → `ActivateSubscriptionUseCase` 내 결제 확인 단계 추가. Stub은 항상 성공 반환
3. 자동 갱신 추가 → 스케줄러 분리 또는 EXPIRED 전이 시 결제 재시도 포함
