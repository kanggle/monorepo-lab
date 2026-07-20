# notification-service — Overview

> 1-pager: responsibilities, public surface, key invariants.

## Service identity

| Field | Value |
|---|---|
| Service name | `notification-service` |
| Project | `ecommerce-microservices-platform` |
| Service Type | `event-consumer` |
| Architecture Style | **Hexagonal** — outbound port per channel (email / SMS / push), see [architecture.md § Architecture Style](architecture.md) |
| Stack | Java 21, Spring Boot 3.4, PostgreSQL, Kafka, external channel SDKs (email / SMS / push providers via outbound ports) |
| Deployable unit | `apps/notification-service/` |
| Bounded Context | `Notification` |
| Persistent stores | PostgreSQL (notification records + templates + user preferences) |
| Event publication | none (consumer-only) |

## Responsibilities

- Consume domain events from 4 upstream services (`order` / `payment` / `shipping` / `auth`) and deliver notifications across multiple channels (email / SMS / push).
- Manage notification templates (admin CRUD; subject / body 변수 치환).
- Manage user channel preferences (opt-in / opt-out per channel — `email_enabled` / `sms_enabled` / `push_enabled`).
- Enforce idempotency on duplicate event consumption — `event_id` dedupe (per `idempotency.md`).
- Respect user opt-out — opted-out channel 은 skip + WARN log.

## Public surface

| Channel | Endpoint / Topic | Auth | Purpose |
|---|---|---|---|
| REST | `GET /api/notifications` | JWT (self) | own notification history |
| REST | `GET /api/users/me/preferences` | JWT (self) | own channel preferences |
| REST | `PUT /api/users/me/preferences` | JWT (self) | update channel preferences |
| REST | `POST /api/admin/notifications/templates` | JWT + ROLE_ADMIN | template CRUD |
| Kafka consume | `order.order.placed` | — | order placement notification |
| Kafka consume | `payment.payment.completed`, `payment.payment.refunded` | — | payment notification |
| Kafka consume | `shipping.shipping.status-changed` | — | shipping status notification |
| Kafka consume | `account.created` (IAM) | — | welcome notification (no PII personalization — emailHash-only event; ADR-MONO-037) |

자세한 spec 은 [`../../contracts/http/notification-api.md`](../../contracts/http/notification-api.md) 참조.

## Key invariants

1. **Idempotent per `(tenant_id, event_id, channel)`** — duplicate event consumption 시 **채널당** 한 번만 발송; unique
   constraint 로 강제 (`uq_notifications_tenant_event_channel`). **"한 번만 발송" 은 이벤트당 한 행이 아니라 채널당 한 행이다** —
   한 이벤트는 invariant 2 에 따라 발송 가능한 채널마다 독립 팬아웃하고 그 행들은 같은 `event_id` 를 공유한다. `event_id` 단독
   unique 는 정상 최초 전송을 거부한다 (TASK-BE-539). `tenant_id` 가 키에 포함되는 이유는 `event_id` 가 producer 단위로만
   고유하기 때문이다.
2. **Respect user opt-out** — `email_enabled = false` 인 사용자에게 email 발송 금지 (channel-별 독립 적용).
3. **Failed delivery retried per policy** — provider 5xx / timeout → retry; 4xx → terminal failure + 로깅 (포기).
4. **No user / order / payment ownership** — notification-service 는 notification meta + 전달 결과만 저장; 다른 service 데이터 직접 cache 금지.
5. **External channel adapters behind outbound port** — domain / application 은 `EmailGatewayPort` / `SmsGatewayPort` / `PushGatewayPort` interface 만 호출; provider SDK 직접 import 금지.

## Owned Data

- notification (`notificationId`, `recipient`, `channel`, `subject`, `body`, `status`, sent / failed timestamps)
- notification template (`templateId`, `type`, `channel`, `subjectTemplate`, `bodyTemplate`)
- user notification preference (`userId`, channel opt-in/out flags)

## Published Interfaces

- [`../../contracts/http/notification-api.md`](../../contracts/http/notification-api.md) (HTTP)
- (no domain events published — consumer-only service)

## Dependent Systems

- Kafka — event consumption (from `order` / `payment` / `shipping` / `auth`)
- PostgreSQL — notification persistence
- external delivery channels — email / SMS / push providers (behind outbound ports)

## Out of scope (v1)

- User profile management — `user-service` (user-service 가 email / phone 의 source-of-truth, notification-service 는 사용만).
- Order / payment / shipping 비즈니스 로직 — 해당 service 소유.
- In-app push (real-time WebSocket) — v2 (`real-time` trait 도입 시).
- Marketing campaign 발송 (대량 push) — v2 (별도 `campaign-service` 분리 후보).
- Notification analytics (open rate / click rate) — v2.
