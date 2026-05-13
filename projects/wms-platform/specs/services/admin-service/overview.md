# admin-service — Overview

> 1-pager: responsibilities, public surface, key invariants.

## Service identity

| Field | Value |
|---|---|
| Service name | `admin-service` |
| Project | `wms-platform` |
| Service Type | `rest-api` + `event-consumer` (dual; CQRS read-side) |
| Architecture Style | **Layered** (deliberate exception — read-heavy CQRS, minimal domain logic) — see [architecture.md § Architecture Style Rationale](architecture.md) |
| Stack | Java 21, Spring Boot 3.4, PostgreSQL, Kafka (consumer + outbox for user/role events), Spring Data JPA |
| Deployable unit | `apps/admin-service/` |
| Bounded Context | `Admin / Operations` |
| Persistent stores | PostgreSQL (User / Role / UserRoleAssignment + denormalised ReadModel projection tables + OperationalSettings) + Kafka outbox (user/role events only) |
| Event publication | `admin.user.created.v1`, `admin.role.assigned.v1`, `admin.settings.changed.v1` etc. (per [`admin-events.md`](../../contracts/events/admin-events.md)) |

## Responsibilities

- Own **operator user identity, role definitions, warehouse-scoped role assignments**.
- Project events from all 5 WMS services (master / inventory / inbound / outbound / notification) into denormalised read-model for KPI / dashboard queries.
- Manage **operational settings** (reservation TTL, low-stock thresholds, saga retry budget) — published as events for consumer services.
- Provide dashboard / KPI REST API backed by read-model — **no cross-service joins at query time** (CQRS rationale).
- eventId-based dedupe on all projection-side event consumption (T8).

## Public surface

| Channel | Endpoint / Topic | Auth | Purpose |
|---|---|---|---|
| REST | `POST/GET /api/v1/admin/users/**` | JWT + ROLE_ADMIN | user CRUD |
| REST | `POST/GET /api/v1/admin/roles/**` | JWT + ROLE_ADMIN | role CRUD + warehouse-scoped assignment |
| REST | `GET/PUT /api/v1/admin/settings/**` | JWT + ROLE_ADMIN | operational settings |
| REST | `GET /api/v1/admin/dashboard/**` | JWT + ROLE_ADMIN | KPI / 대시보드 query (read-model 기반) |
| Kafka consume | `master.*`, `inventory.*`, `inbound.*`, `outbound.*`, `notification.delivered.*` | — | read-model projection 소스 |
| Kafka publish | `admin.user.*`, `admin.role.*`, `admin.settings.*` | — | downstream service 의 권한 / 설정 갱신 |

자세한 spec 은 [`../../contracts/http/admin-service-api.md`](../../contracts/http/admin-service-api.md) + [`../../contracts/events/admin-events.md`](../../contracts/events/admin-events.md) 참조.

## Key invariants

1. **Read-model is projection-only** — `admin-service` 는 operational data 의 authoritative source 가 절대 아님; sibling service 가 SoT.
2. **Projection idempotency** — 같은 `eventId` 두 번 적용해도 같은 read-model row 생성 (T8); replay 시 안전.
3. **User/role mutation + outbox atomic** (T3) — admin.user.* / admin.role.* event 가 user / role 쓰기와 한 TX.
4. **Authentication owned by gateway-service** — `admin-service` 는 user record 관리만; JWT 발급 / 검증은 `gateway-service` 책임.
5. **Settings 변경은 event 로만 propagate** — consumer service 는 event 수신 후 자기 config 갱신; HTTP polling 금지.

## Owned Data

- User, Role, UserRoleAssignment rows.
- OperationalSettings rows.
- Denormalised ReadModel projection tables (sibling event 기반, projection-only).

## Published Interfaces

- [`../../contracts/http/admin-service-api.md`](../../contracts/http/admin-service-api.md) (HTTP)
- [`../../contracts/events/admin-events.md`](../../contracts/events/admin-events.md) — user / role / settings events

## Dependent Systems

- PostgreSQL — user / role / projection persistence
- Kafka — event consumption (5 sibling) + publication (user/role/settings)
- `gateway-service` — JWT validation references user records (read-only)

## Out of scope (v1)

- Notification channel preference UI — `notification-service` v2.
- Audit-log enforcement on sibling services — 각 service 가 자기 history 소유.
- Authentication / JWT issuance — `gateway-service` (또는 외부 IdP).
- Master data mutation — `master-service`.
- 실시간 stream KPI (sub-second) — v2 (real-time trait 도입 시).
