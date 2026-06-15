# notification-service — Architecture

This document declares the internal architecture of `notification-service`.
All implementation tasks targeting this service must follow this declaration
and `platform/architecture-decision-rule.md`.

---

## Identity

| Field | Value |
|---|---|
| Service name | `notification-service` |
| Project | `ecommerce-microservices-platform` |
| Service Type | `event-consumer` (single — see Service Type Composition below) |
| Architecture Style | **Hexagonal Architecture** (Ports & Adapters) |
| Domain | ecommerce |
| Primary language / stack | Java 21, Spring Boot |
| Bounded Context | Notification delivery (email / SMS / push 외부 채널 통합) |
| Deployable unit | `apps/notification-service/` |
| Data store | PostgreSQL (delivery state + dedupe) |
| Event publication | none (terminal consumer — see § Events) |
| Event consumption | Kafka (order/payment/shipping lifecycle source topics) |

### Service Type Composition

`notification-service` is a single-type `event-consumer` service per
`platform/service-types/INDEX.md`. ecommerce 의 lifecycle 이벤트 소비 후 외부
채널 (email / SMS / push) 로 발송. Hexagonal 으로 외부 vendor adapter 격리.
적용되는 규칙:
[platform/service-types/event-consumer.md](../../../../../platform/service-types/event-consumer.md).

---

## Why This Architecture
Notification service integrates with multiple external channels (email, SMS, push) that may change independently.

Hexagonal architecture isolates the core notification logic from external channel adapters, making it easy to swap or add channels without modifying business rules.

The primary flow is event-driven (inbound ports from Kafka) with outbound ports to external delivery systems.

## Internal Structure Rule
This service uses a hexagonal internal structure.

Recommended internal areas:
- adapter/in/rest (HTTP controllers, GlobalExceptionHandler)
- adapter/in/event (Kafka event consumers, event record classes)
- adapter/in/kafka (Kafka consumer configuration)
- adapter/out/persistence (JPA entities, repositories, mappers)
- adapter/out/external (email sender, SMS sender, push sender)
- application/port/in (inbound port interfaces)
- application/port/out (outbound port interfaces)
- application/service (use-case implementations)
- application/command (input records)
- application/result (output records)
- application/page (pagination DTOs)
- domain/model (domain entities, value objects)
- domain/exception (domain exceptions)

Key domain concepts:
- Entities: Notification, NotificationTemplate, UserNotificationPreference
- Value Objects: NotificationChannel (EMAIL, SMS, PUSH), NotificationStatus (PENDING, SENT, FAILED), TemplateType
- Ports (inbound): SendNotificationUseCase, QueryNotificationUseCase, ManageTemplateUseCase, ManagePreferenceUseCase
- Ports (outbound): NotificationSender, NotificationRepository, TemplateRepository, PreferenceRepository

## Allowed Dependencies
- adapter/in -> application (inbound ports)
- adapter/out -> application (outbound ports)
- application -> domain
- domain depends on nothing

## Forbidden Dependencies
- domain must not depend on framework, persistence, or external channel details
- adapters must not contain business logic
- application must not directly reference adapter implementations
- inbound adapters must not call outbound adapters directly

## Boundary Rules
- inbound adapters handle HTTP mapping, Kafka message deserialization, and delegation to use-cases
- application layer coordinates notification orchestration and template rendering
- domain layer owns notification rules and preference constraints
- outbound adapters implement channel-specific delivery and persistence

## Domain Scope
- Notification (recipient, channel, subject, body, status, sent/failed timestamps)
- NotificationTemplate (type, channel, subject template, body template, variables)
- UserNotificationPreference (user reference, channel opt-in/out settings)

## Domain Constraints
- notification-service must NOT own user profile or order data
- Notifications must respect user channel preferences (opt-out channels skipped)
- Failed notifications must be retried according to retry policy
- Duplicate event consumption must not produce duplicate notifications (idempotency via event_id)

## Integration Rules
- Event consumption must follow published event contracts
- HTTP behavior must follow published contracts
- External channel adapters must be behind outbound port interfaces
- Shared libraries may be used only under shared-library policy

## Events
- Consumes:
  - `OrderPlaced` from `order.order.placed` (order-service)
  - `PaymentCompleted` from `payment.payment.completed` (payment-service)
  - `ShippingStatusChanged` from `shipping.shipping.status-changed` (shipping-service)
  - IAM `account.created` (onboarding WELCOME — sent without PII personalization since the event is emailHash-only; ADR-MONO-037 P1). Replaces the retired `auth.user.signed-up` (auth-service decommissioned TASK-BE-132). Handler: `AccountCreatedEventConsumer`.
- Publishes: none
- Consumer group: `notification-service`

## Multi-Tenancy (ADR-MONO-030 Step 4 / TASK-BE-370)

notification-service adopts the platform's `multi-tenant` trait
([`rules/traits/multi-tenant.md`](../../../../../rules/traits/multi-tenant.md) M1-M7),
inheriting the outer-axis tenant-isolation pattern proven in product-service /
order-service (TASK-BE-357), user-service (TASK-BE-367), promotion-service
(TASK-BE-368), and shipping-service (TASK-BE-369). The `seller_id` inner axis does
**not** apply — notification-service is a terminal consumer with no seller-attributed
data of its own.

- **M1 — row-level `tenant_id`**: `notifications` and `processed_events` records carry
  `tenant_id VARCHAR(64) NOT NULL` where applicable, stamped from the consumed event
  envelope's `tenant_id`. V5 migration backfills all pre-existing rows to the default
  tenant `'ecommerce'`.
- **M2 — 3-layer isolation**: (1) gateway entitlement-trust gate + `X-Tenant-Id` header
  owned by **gateway-service** (TASK-BE-357), reused unchanged; (2) `TenantContextFilter`
  (`HIGHEST_PRECEDENCE`) binds the header into a request-scoped `TenantContext` ThreadLocal;
  (3) any HTTP read surfaces filter `WHERE tenant_id = currentTenant()`.
- **M3 — 404-over-403**: cross-tenant notification query resolves to empty → **404**,
  never 403.
- **M5 — async propagation**: consumed ecommerce event envelopes (`order.*`, `payment.*`,
  `shipping.*`) carry `tenant_id`; IAM `account.created` carries `tenantId` in the
  **payload**. The notification record is stamped with the source event's tenant. Absent → default tenant.
- **net-zero / standalone (D8)**: V5 migration backfills all pre-existing rows to the
  default tenant `'ecommerce'`; an unset context resolves to that default — single-store
  behavior byte-identical. Multi-tenancy is additive; **fail-closed is prohibited**.

> SoT = [specs/features/multi-tenancy-and-marketplace.md](../../features/multi-tenancy-and-marketplace.md) §2, ADR-MONO-030.

## Testing Expectations
Required emphasis:
- use-case / application service tests
- inbound adapter tests (Kafka consumer, HTTP controller)
- outbound adapter tests (mocked external channels)
- template rendering tests
- idempotency tests

## Change Rule
Any architectural change to this service must be documented here first before implementation.
