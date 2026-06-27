# membership-service — Architecture

This document declares the internal architecture of `membership-service`.
All implementation tasks targeting this service must follow this declaration
and `platform/architecture-decision-rule.md`.

---

## Identity

| Field | Value |
|---|---|
| Service name | `membership-service` |
| Project | `iam-platform` |
| Service Type | `rest-api` (single — see Service Type Composition below) |
| Architecture Style | **Layered Architecture + 명시적 상태 기계** |
| Domain | saas |
| Primary language / stack | Java 21, Spring Boot |
| Bounded Context | Membership (구독 플랜 + 프리미엄 콘텐츠 접근 제어) |
| Deployable unit | `apps/membership-service/` |
| Data store | MySQL (owned) |
| Event publication | Kafka via outbox (subscription.* lifecycle events) |
| Event consumption | none (single-type rest-api) |

### Service Type Composition

`membership-service` is a single-type `rest-api` service per
`platform/service-types/INDEX.md`. 구독 플랜 관리 및 프리미엄 콘텐츠 접근
제어 서비스. 적용되는 규칙:
[platform/service-types/rest-api.md](../../../../../platform/service-types/rest-api.md).

---

## Architecture Style

**Layered Architecture + 명시적 상태 기계** — `presentation / application / domain / infrastructure` 4계층. `domain/subscription/status/`가 구독 상태 기계를 소유.

## Why This Architecture

- **구독 상태 전이가 핵심 비즈니스 규칙**: NONE → ACTIVE → EXPIRED / CANCELLED 전이는 결제 완료, 만료 스케줄, 사용자 해지 등 여러 트리거로 발생. 직접 UPDATE 금지; 상태 기계가 전이 유효성 검사 ([rules/traits/transactional.md](../../../../../rules/traits/transactional.md) T4).
- **접근 제어 단일 소유**: 어떤 플랜이 어떤 콘텐츠를 볼 수 있는지는 이 서비스만 알고 있음. community-service는 내부 HTTP로 위임 — 구독 데이터를 분산 복사하지 않음.
- **감사 추적**: 구독 활성화·만료·해지는 `subscription_status_history`에 append-only 기록 ([rules/traits/audit-heavy.md](../../../../../rules/traits/audit-heavy.md) A1·A3).
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
- `application` → [libs/java-messaging](../../../../../libs/java-messaging) (outbox)
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
- **퍼시스턴스**: MySQL (`membership_db`) — `membership_plans`, `subscriptions`, `subscription_status_history`, `content_access_policies`, `outbox` (v1, KEEP-auto-config), `membership_outbox` (v2, TASK-BE-454)

### Outbox (v2)

> TASK-BE-454 — outbox v1 → v2 migration (in-worktree auth-service / finance account-service MySQL precedent, ADR-MONO-004 § 5).
>
> - **Write path**: `application.event.MembershipEventPublisher` is now a **port**; the impl `infrastructure.outbox.OutboxMembershipEventPublisher` builds the canonical 7-field envelope (`{eventId, eventType, source="membership-service", occurredAt, schemaVersion=1, partitionKey, payload}` — **byte-identical** to the v1 `BaseEventPublisher.writeEvent` wire) and persists a `membership_outbox` row (`infrastructure.persistence.MembershipOutboxJpaEntity implements OutboxRow`, MySQL `CHAR(36)` UUIDv7 PK = envelope `eventId`) inside the caller's `@Transactional`. Each event's `eventType`+`payload` come VERBATIM from the domain factories (`Subscription#buildActivatedEvent()` / `buildExpiredEvent()` / `buildCancelledEvent()`); key = `accountId`.
> - **Relay**: `infrastructure.outbox.MembershipOutboxPublisher extends AbstractOutboxPublisher<MembershipOutboxJpaEntity>` — `@Component`, no `@ConditionalOnProperty` gate (the v1 `MembershipOutboxPollingScheduler` had none; `@EnableScheduling` already on `MembershipApplication`). Plain `MicrometerOutboxMetrics(registry,"membership")` (the v1 scheduler had no custom failure counter) + `membership.outbox.pending.count` gauge. `topicFor` ported VERBATIM from the v1 `resolveTopic` — iam topics are **bare** (no `.v1` suffix): each `membership.*` event → identically-named topic; reject-unmapped.
> - **KEEP-auto-config**: the lib `OutboxAutoConfiguration` is NOT excluded — the v1 `outbox` (BIGINT/status) + `processed_events` tables are retained (still EntityScanned, required under `ddl-auto=validate`). In-flight v1 `outbox` rows at cutover are abandoned (low-volume, re-derivable).
> - **Migration**: `db/migration/V0007__membership_outbox_v2.sql`.

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
