# user-service — Architecture

This document declares the internal architecture of `user-service`.
All implementation tasks targeting this service must follow this declaration
and `platform/architecture-decision-rule.md`.

---

## Identity

| Field | Value |
|---|---|
| Service name | `user-service` |
| Project | `ecommerce-microservices-platform` |
| Service Type | `rest-api + event-consumer` (hybrid — see Service Type Composition below) |
| Architecture Style | **Layered Architecture** |
| Domain | ecommerce |
| Primary language / stack | Java 21, Spring Boot |
| Bounded Context | User profile (CRUD-oriented user data management) |
| Deployable unit | `apps/user-service/` |
| Data store | PostgreSQL (owned) |
| Event publication | Kafka via outbox (user.* lifecycle events) |
| Event consumption | `UserSignedUp` from `auth.user.signed-up` (creates initial user profile on auth-service signup) |

### Service Type Composition

`user-service` is a hybrid service per
`platform/service-types/INDEX.md` § Hybrid Cases (REST service that also
consumes events). Primary type is `rest-api`; the secondary `event-consumer`
capability subscribes to `auth.user.signed-up` to bootstrap user profile
records when auth-service issues a new user identity. The primary type
determines the spec read order — applied rules:
[platform/service-types/rest-api.md](../../../../../platform/service-types/rest-api.md).
The secondary capability is documented under "Events" below with topic /
consumer-group details.

---

## Why This Architecture
This service is primarily CRUD-oriented, managing user profile data.

Domain rules are simple: profile field validation, address format checks, and basic constraints (e.g., maximum addresses per user).

There are no complex aggregates or external system integrations that would justify DDD or Hexagonal architecture.

Maintainability and fast development are prioritized.

## Internal Structure Rule
This service uses a layered internal structure.

Recommended internal layers:
- presentation
- application
- domain
- infrastructure

Package organization may follow package-by-layer or package-by-feature if the layered dependency rule is preserved.

## Allowed Dependencies
- presentation -> application
- application -> domain
- application -> infrastructure (via domain-defined interfaces only; concrete implementations are injected by the framework)
- infrastructure -> domain
- infrastructure -> framework and external libraries

## Forbidden Dependencies
- presentation must not access persistence directly
- presentation must not contain business rules
- domain must not depend on web framework code
- domain must not depend on controller/request classes
- repositories must not be called directly from controllers
- application must not import infrastructure utility classes directly
- application must access infrastructure behavior only through domain-layer interfaces or their return types

## Boundary Rules
- controllers handle HTTP mapping, validation entry, and response conversion
- application layer coordinates use-cases and transactions
- domain contains core business rules and entities
- infrastructure handles persistence, security integration, and framework adapters

## Domain Scope
- User profile (nickname, email, phone, profile image URL)
- User addresses (shipping addresses, default address designation)
- Profile status (active, suspended, withdrawn)
- Wishlist (user-product associations, product info fetched from product-service at query time)

## Domain Constraints
- user-service must NOT own authentication credentials (owned by IAM; auth-service decommissioned TASK-BE-132)
- user-service must NOT replicate or cache auth tokens
- user ID is issued by IAM; user-service receives it as an external identifier (formerly issued by auth-service; decommissioned TASK-BE-132)
- profile data is exposed only through published contracts

## Integration Rules
- HTTP behavior must follow published contracts
- events must follow published event contracts
- persistence rules must follow service ownership boundaries
- shared libraries may be used only under shared-library policy
- IAM creates the user ID; user-service creates the profile record upon receiving a `UserSignedUp` event from `auth.user.signed-up` (emitted by IAM; auth-service decommissioned TASK-BE-132)

## Events
- Consumes: `UserSignedUp` (from auth-service) — triggers initial profile creation
- Publishes: `UserProfileUpdated`, `UserWithdrawn`

## Outbox

- Pattern: **Direct Kafka publish via Spring event bridge** (not `libs/java-messaging` transactional outbox — `java-messaging` is not a dependency of user-service).
- Flow: application layer publishes a Spring `ApplicationEvent` (`UserProfileUpdatedSpringEvent` / `UserWithdrawnSpringEvent`); `KafkaUserProfileEventPublisher` (`@EventListener`, `infrastructure/event`) converts and sends directly via `KafkaTemplate`.
- Topic 매핑 (`KafkaUserProfileEventPublisher`):
  - `UserProfileUpdated` → `user.user.profile-updated`
  - `UserWithdrawn` → `user.user.withdrawn`
- Consumed topic: `auth.user.signed-up` (consumer group `user-service`) — handled by `UserSignedUpConsumer`.
- **Note**: the Identity table entry "Event publication | Kafka via outbox" reflects the intended target pattern; the v1 implementation is a direct Spring-event → Kafka bridge without a transactional outbox table. Adding the outbox is a forward-declared improvement.

## Multi-Tenancy & Marketplace (ADR-MONO-030)

> (Originally introduced in TASK-BE-367.) 모델 SoT = [specs/features/multi-tenancy-and-marketplace.md](../../features/multi-tenancy-and-marketplace.md)

user-service adopts the platform's `multi-tenant` trait
([`rules/traits/multi-tenant.md`](../../../../../rules/traits/multi-tenant.md) M1-M7),
inheriting the outer-axis tenant-isolation pattern proven in product-service /
order-service (TASK-BE-357). The `seller_id` inner axis does **not** apply —
user-service owns consumer/profile data, not seller-attributed catalog data.

- **M1 — row-level `tenant_id`**: every persistent entity (`user_profiles`,
  `user_addresses`, `wishlist_items`) carries `tenant_id VARCHAR(64) NOT NULL`,
  stamped once at insert and immutable thereafter (`updatable=false`). It lives in
  the persistence + event layers only; the clean domain model is unchanged.
- **M2 — 3-layer isolation**: (1) the gateway `TenantClaimValidator` entitlement-trust
  gate + `X-Tenant-Id` header injection are owned by **gateway-service** (TASK-BE-357),
  reused unchanged; (2) `TenantContextFilter` (`HIGHEST_PRECEDENCE`) binds the header
  into a request-scoped framework-free `TenantContext` ThreadLocal; (3) every
  repository read filters `WHERE tenant_id = currentTenant()` and every write stamps it.
- **M3 — 404-over-403**: a cross-tenant single-resource read (e.g. `GET /api/admin/users/{id}`
  for another tenant's user) resolves to empty → **404** (existence hidden), never 403.
- **M5 — async propagation**: `UserProfileUpdated` / `UserWithdrawn` envelopes carry
  `tenant_id` (see [user-events.md](../../contracts/events/user-events.md)). The consumed
  `UserSignedUp` envelope's `tenant_id` (optional until auth-service migrates) is bound to
  the context for profile creation; absent → default tenant.
- **M6 — cross-tenant-leak regression IT**: `MultiTenantIsolationIntegrationTest`
  (Testcontainers PostgreSQL) proves tenant A's users/profiles are invisible to a tenant B
  context (list exclusion + 404 single-read) and that a missing `X-Tenant-Id` resolves to
  the default tenant.
- **net-zero / standalone (D8)**: the V4 migration backfills all pre-existing rows to the
  default tenant `'ecommerce'`; an unset context (standalone deploy, background consumer,
  unit test) resolves to that default — so the single-store behavior is byte-identical.
  Multi-tenancy is additive, never a hard runtime dependency. **fail-closed is prohibited**
  (an empty/missing tenant context must resolve to the default, not reject).

> The operator-plane read `GET /api/admin/users` is now tenant-scoped — this is the
> backend prerequisite (ADR-MONO-031 Phase 2a) that gates the platform-console
> users-area absorption (Phase 2b).

## Testing Expectations
Required emphasis:
- controller/API tests
- application service tests
- repository integration tests
- event consumer tests

## Change Rule
Any architectural change to this service must be documented here first before implementation.
