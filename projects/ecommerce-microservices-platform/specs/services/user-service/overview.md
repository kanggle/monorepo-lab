# user-service — Overview

> 1-pager: responsibilities, public surface, key invariants.

## Service identity

| Field | Value |
|---|---|
| Service name | `user-service` |
| Project | `ecommerce-microservices-platform` |
| Service Type | `rest-api` |
| Architecture Style | **Layered** — see [architecture.md § Architecture Style](architecture.md) |
| Stack | Java 21, Spring Boot 3.4, PostgreSQL, Kafka (direct publish, no outbox in v1) |
| Deployable unit | `apps/user-service/` |
| Bounded Context | `User Profile` |
| Persistent stores | PostgreSQL (user profile + shipping addresses) |
| Event publication | `user.user.profile-updated` (UserProfileUpdated), `user.user.withdrawn` (UserWithdrawn) |

## Responsibilities

- Create a **minimal** user profile on IAM `account.created` consumption (`email`/`name` sourced later from the OIDC token — the event is emailHash-only; [ADR-MONO-037](../../../../../docs/adr/ADR-MONO-037-ecommerce-account-lifecycle-projection.md) P1).
- Manage user profile query and update (`email`, `name`, `nickname`, `phone`, `profileImageUrl`).
- Own shipping address CRUD — add / update / delete / list + default address designation.
- React to IAM `account.deleted` (two-phase): grace entry (`anonymized=false`) → status `WITHDRAWN` + publish `UserWithdrawn`; post-grace (`anonymized=true`) → anonymize profile PII (the TASK-BE-258 obligation; ADR-MONO-037 P2/P3).
- Admin user list + detail query (read-only v1).

## Public surface

| Channel | Endpoint / Topic | Auth | Purpose |
|---|---|---|---|
| REST | `GET /api/users/me` | JWT (self) | own profile |
| REST | `PUT /api/users/me` | JWT (self) | update own profile |
| REST | `GET /api/users/me/addresses` | JWT (self) | list addresses |
| REST | `POST /api/users/me/addresses` | JWT (self) | add address |
| REST | `PUT /api/users/me/addresses/{id}` | JWT (self) | update address (+ default toggle) |
| REST | `DELETE /api/users/me/addresses/{id}` | JWT (self) | delete address |
| REST | `GET /api/admin/users` | JWT + ROLE_ADMIN | admin user list |
| Kafka consume | `account.created` (IAM) | — | minimal profile bootstrap |
| Kafka consume | `account.deleted` (IAM) | — | two-phase: withdraw (grace) / anonymize (post-grace) |
| Kafka publish | `user.user.profile-updated`, `user.user.withdrawn` | — | downstream consumers (order / notification) |

자세한 spec 은 [`../../contracts/http/user-api.md`](../../contracts/http/user-api.md) + [`../../contracts/events/user-events.md`](../../contracts/events/user-events.md) 참조.

## Key invariants

1. **No credential ownership** — user-service 는 password / hash / JWT token 직접 보관 / 검증 금지; IAM (iam-platform) 가 owner (in-tree auth-service 는 TASK-BE-132 으로 폐기).
2. **`userId` is external identifier** — IAM 가 발급한 ID 를 그대로 사용, 자체 sequence 생성 안 함.
3. **No token cache** — JWT / refresh token 을 user-service 가 cache 하지 않음.
4. **Profile data exposed via published contracts only** — DB 직접 조회 금지; HTTP / event 만 통과.
5. **Presentation ↛ persistence** — controller 가 repository 직접 호출 금지 (architecture.md § Layered Rules).

## Owned Data

- user profile (`userId`, `email`, `name`, `nickname`, `phone`, `profileImageUrl`, `status`)
- shipping addresses (`label`, `recipientName`, `phone`, `zipCode`, `address1`, `address2`, `isDefault`)

## Published Interfaces

- [`../../contracts/http/user-api.md`](../../contracts/http/user-api.md) (HTTP)
- [`../../contracts/events/user-events.md`](../../contracts/events/user-events.md) — `UserProfileUpdated`, `UserWithdrawn`

## Dependent Systems

- PostgreSQL — user profile persistence
- Kafka — event consumption + publication
- ~~`auth-service`~~ (deprecated) → IAM (event source: `account.created` / `account.deleted`; ADR-MONO-037)

## Out of scope (v1)

- Authentication / credential management — `auth-service` (deprecated) → IAM.
- Order processing — `order-service`.
- Payment processing — `payment-service`.
- Product catalog — `product-service`.
- Loyalty / membership tier — v2 (`membership-service` 도입 시).
- Outbox pattern — v1 은 단순 direct publish (소비자가 idempotent 처리); 신뢰성 강화 시 v2 에서 outbox 적용.
