# admin-service — Architecture

This document declares the internal architecture of `admin-service`.
All implementation tasks targeting this service must follow this declaration
and `platform/architecture-decision-rule.md`.

---

## Identity

| Field | Value |
|---|---|
| Service name | `admin-service` |
| Service Type | `rest-api` + `event-consumer` (dual; consumer feeds CQRS read model) |
| Architecture Style | **Layered** (deliberate exception — see Rationale) |
| Primary language / stack | Java 21, Spring Boot |
| Bounded Context | **Admin / Operations** (per `rules/domains/wms.md`) |
| Deployable unit | `apps/admin-service/` |
| Data store | PostgreSQL (owned) — read-model + user/role tables |
| Event publication | Kafka via outbox (only for user/role mutations) |
| Event consumption | Kafka with eventId-based dedupe (read-model projection) |

### Service Type Composition

`admin-service` is dual-type:

- `rest-api` for KPI / dashboard queries (read-heavy) and user/role management
  (write — small surface).
- `event-consumer` for **read-model projection** — subscribes to events from
  every WMS service and maintains denormalised tables for fast dashboard queries.
  This is the only CQRS read-side consumer in the platform.

Read both `platform/service-types/rest-api.md` and
`platform/service-types/event-consumer.md`.

---

## Responsibility

`admin-service` owns:

- **User** — operator account identity, profile, status
- **Role** — role definitions and permission sets
- **UserRoleAssignment** — which user holds which role(s) in which warehouse(s)
- **Read Model** — denormalised projection of every other service's events,
  optimised for dashboard / KPI queries
- **Operational Settings** — global / per-warehouse runtime config
  (e.g., reservation TTL, low-stock thresholds) — **published as events** so
  other services can adopt them

It does **not** own any operational business data — those live in master /
inventory / inbound / outbound services. Admin only **projects** them for
display.

---

## Out of Scope

`admin-service` does NOT own:

- Master data (master-service)
- Inventory quantities, ASNs, orders (their respective services)
- Authentication (gateway-service handles JWT issuance / validation;
  admin-service only manages user records that gateway looks up)
- Audit-log enforcement on other services (each service writes its own
  movement / state-history table; admin-service projects these for display
  but does not enforce)
- Notification delivery (notification-service)

---

## Architecture Style: Layered (Deliberate Exception)

### Rationale

The other four WMS services use Hexagonal because their domain logic is
non-trivial and uniform across them. `admin-service` is materially different:

1. **Read-heavy CQRS read-side**: most code is event-projection + query.
   Hexagonal boilerplate (ports for every query) provides little value when
   the "domain" is a denormalised view.
2. **Small write surface** (user/role/settings): straightforward CRUD with
   ordinary validation; no aggregate-spanning invariants.
3. **No saga participation, no external vendor adapters**: integration surface
   is purely internal Kafka consumption.

A **Layered** architecture (controller → service → repository) is sufficient
and reduces cognitive load. This is documented as an explicit `## Overrides`
to the project-wide preference for Hexagonal.

### Override Declaration

> **rule**: `specs/services/master-service/architecture.md` § Architecture Style
> "Hexagonal" — uniform across WMS services
>
> **reason**: admin-service is read-side / CQRS-shaped with minimal domain
> logic; Hexagonal cost not justified
>
> **scope**: `apps/admin-service/` only
>
> **expiry**: revisit when admin-service adds non-trivial write workflows
> (e.g., approval-flow features in v2)

This override is mirrored in `PROJECT.md`'s `## Overrides` section.

### Trade-off Accepted

- Loses the uniformity benefit (mental-model match across services)
- Gains: ~50% less file count, faster iteration on dashboard features
- Risk: feature creep into admin-service that grows domain logic — mitigated
  by service-boundary discipline (push business logic back to owning service)

### Package Structure

```
com.wms.admin/
├── api/                         # REST controllers
│   ├── dashboard/               # InventorySnapshotController, ThroughputController, AlertController
│   ├── user/                    # UserController, RoleController, AssignmentController
│   ├── settings/                # SettingsController
│   └── dto/{request,response}/
├── application/
│   ├── dashboard/               # KPI calculation services
│   ├── user/                    # UserService, RoleService
│   ├── settings/                # SettingsService
│   └── projection/              # Event-projection services (one per source service)
├── domain/                      # Simple POJOs — User, Role, UserRoleAssignment, Setting
├── readmodel/                   # Read-model entities (denormalised)
│   ├── inventory/               # InventorySnapshot, LowStockEntry
│   ├── inbound/                 # AsnSummary, InspectionSummary
│   ├── outbound/                # OrderSummary, ShipmentSummary
│   └── master/                  # WarehouseRef, SkuRef, LocationRef
├── infra/
│   ├── kafka/                   # @KafkaListener consumers
│   ├── persistence/             # JPA repositories + entities
│   └── security/                # JWT propagation, role-based authz wiring
└── config/
```

### Layer Rules

1. **Controllers are thin** — validate input, call application service, return
   DTO. No business logic.
2. **Application services own transaction boundary** (`@Transactional`).
   For write paths (user, role, settings), one service method = one TX.
   For read paths, queries may span repositories without TX.
3. **Domain types are simple POJOs** — no rich behaviour beyond construction
   validation. Acceptable here because invariants are minimal.
4. **Read-model entities are JPA entities used directly** in query responses
   (after DTO mapping). No separate domain model layer for projections —
   intentional simplification.
5. **Projection services consume Kafka events and upsert read-model entities**.
   Each external service has its own projection service for clarity.

---

## Dependencies (Inbound)

| Caller | Contract | Purpose |
|---|---|---|
| `gateway-service` | `specs/contracts/http/admin-service-api.md` | Admin UI — dashboards, user mgmt, settings |
| All other WMS services | Events on `wms.master.*`, `wms.inbound.*`, `wms.outbound.*`, `wms.inventory.*` | Read-model feed |

`admin-service` is **not** called by any other WMS service synchronously. It is
purely an admin-facing surface.

> **Auth integration note**: `gateway-service` validates JWTs against an issuer
> external to `admin-service` (Keycloak / Cognito / similar — declared in
> `gateway-service/architecture.md`). `admin-service` stores user **profile**
> and **role assignments** but does NOT issue tokens. User creation here
> typically follows a corresponding identity-provider account creation, kept
> in sync via a manual ops procedure in v1.

---

## Dependencies (Outbound)

v1 outbound dependencies:

- **PostgreSQL** — owned DB
- **Kafka** — consumes from every WMS topic; publishes
  `admin.user.*`, `admin.role.*`, `admin.settings.changed` events
- **Redis** — idempotency-key store, query cache (for hot dashboards)

No external vendors in v1.

---

## Event Publication

Limited to user/role/settings mutations:

| Event | Topic | Trigger |
|---|---|---|
| `admin.user.created` / `.updated` / `.deactivated` | `wms.admin.user.v1` | User mgmt |
| `admin.role.created` / `.updated` / `.deactivated` | `wms.admin.role.v1` | Role mgmt |
| `admin.assignment.granted` / `.revoked` | `wms.admin.assignment.v1` | Assignment changes |
| `admin.settings.changed` | `wms.admin.settings.v1` | Global / per-warehouse setting changes |

Per trait `transactional` rule T3 — outbox pattern same as siblings. Other
services may consume `admin.settings.changed` to react to runtime config (e.g.,
`inventory-service` reading the new reservation TTL).

Full schemas: `specs/contracts/events/admin-events.md` (Open Items).

---

## Event Consumption

Subscribes to **every** WMS topic for read-model projection:

| Subscribed Event | Source Topic | Read-Model Effect |
|---|---|---|
| `master.warehouse.*` | `wms.master.warehouse.v1` | Upsert `warehouse_ref` |
| `master.zone.*` | `wms.master.zone.v1` | Upsert `zone_ref` |
| `master.location.*` | `wms.master.location.v1` | Upsert `location_ref` |
| `master.sku.*` | `wms.master.sku.v1` | Upsert `sku_ref` |
| `master.partner.*` | `wms.master.partner.v1` | Upsert `partner_ref` |
| `master.lot.*` | `wms.master.lot.v1` | Upsert `lot_ref` |
| `inbound.asn.received` / `.cancelled` / `.closed` | `wms.inbound.*` | Update `asn_summary` |
| `inbound.inspection.completed` | `wms.inbound.inspection.completed.v1` | Append `inspection_summary` |
| `inbound.putaway.completed` | `wms.inbound.putaway.completed.v1` | Increment `throughput_inbound_daily` |
| `outbound.order.received` / `.cancelled` | `wms.outbound.*` | Update `order_summary` |
| `outbound.shipping.confirmed` | `wms.outbound.shipping.confirmed.v1` | Append `shipment_summary`, increment `throughput_outbound_daily` |
| `inventory.adjusted` | `wms.inventory.adjusted.v1` | Update `inventory_snapshot`, append `adjustment_audit` |
| `inventory.transferred` | `wms.inventory.transferred.v1` | Update both source/target `inventory_snapshot` rows |
| `inventory.reserved` / `.released` / `.confirmed` / `.received` | `wms.inventory.*` | Update `inventory_snapshot` aggregates |
| `inventory.low-stock-detected` | `wms.inventory.alert.v1` | Append `alert_log`; surface in alert dashboard |

### Consumer Rules

- EventId dedupe via `admin_event_dedupe(event_id PK, event_type, processed_at, outcome)` — 30 days
- DLT topics, 3 retries, exponential backoff + jitter
- Partition key irrelevant for projection (read-model is eventually consistent
  per aggregate)
- **Late-event tolerance**: if events arrive out of order (rare with single
  partition per aggregate, but possible across topics), projections use
  **last-write-wins** based on event timestamp, **not** consumption time.
  Each read-model row carries `last_event_at`; updates with older `eventTime`
  are dropped.

---

## Read-Model Projection Pattern

For each subscribed topic, one `*ProjectionService` class:

```java
@Service
class InventoryProjectionService {
    @Transactional
    @KafkaListener(topics = "wms.inventory.adjusted.v1", groupId = "admin-projection")
    void onAdjusted(InventoryAdjustedEvent ev, MessageHeaders headers) {
        if (dedupe.alreadyProcessed(ev.eventId())) return;
        var snapshot = repo.findByLocationAndSku(ev.locationId(), ev.skuId())
                           .orElseGet(() -> InventorySnapshot.empty(...));
        if (ev.eventTime().isAfter(snapshot.lastEventAt())) {
            snapshot.applyAdjustment(ev);
            repo.save(snapshot);
        }
        dedupe.markProcessed(ev.eventId(), APPLIED);
    }
}
```

Key properties:

- **Idempotent**: dedupe + last-write-wins
- **Crash-safe**: dedupe insert in same TX as projection update
- **Replayable**: a "rebuild from offset 0" mode for recovery (manual ops
  procedure documented in `read-model-rebuild.md` — Open Items)

---

## Idempotency

### Synchronous (REST)

- `Idempotency-Key` on user / role / settings mutations (POST/PUT/PATCH/DELETE)
- Storage: Redis `admin:idempotency:{key}`, TTL 24h, scope `(key, method, path)`
- Dashboard query endpoints are pure reads — no idempotency required

### Asynchronous (Kafka)

- `admin_event_dedupe` table per above
- Combined with last-write-wins for projection idempotency

---

## Concurrency Control

### Optimistic Locking

`User`, `Role`, `UserRoleAssignment`, `Setting` aggregates carry `version`.
Conflicts → HTTP 409 `CONFLICT`.

### Read-Model

Read-model entities also carry `version` to detect concurrent projection
updates from parallel consumer threads. With single-partition-per-aggregate
ordering, conflicts should be near-zero, but the safety net stays.

---

## Key Domain Invariants

Mostly trivial here; this service has small domain logic:

| Invariant | Source | Error code |
|---|---|---|
| User email globally unique within tenant | derived | `USER_EMAIL_DUPLICATE` |
| Role code globally unique | derived | `ROLE_CODE_DUPLICATE` |
| Cannot deactivate user with active assignments (force flag override) | derived | `USER_HAS_ACTIVE_ASSIGNMENTS` |
| Cannot delete role assigned to active users | derived | `ROLE_IN_USE` |
| Setting value must satisfy declared schema (per setting key) | derived | `SETTING_VALIDATION_ERROR` |
| User status transitions: `ACTIVE` ↔ `INACTIVE` only | T4 | `STATE_TRANSITION_INVALID` |

---

## Persistence

- Database: PostgreSQL (one logical DB per service)
- Migrations: Flyway, `apps/admin-service/src/main/resources/db/migration/`
- Outbox: `admin_outbox`
- Event dedupe: `admin_event_dedupe`
- Read-model tables: `*_ref`, `*_summary`, `*_snapshot`, `throughput_*`,
  `alert_log` (full layout in `domain-model.md` — Open Items)
- User/role tables: `admin_user`, `admin_role`, `admin_user_role_assignment`
- Settings: `admin_setting(key PK, scope, value_json, version, updated_at)`

---

## Observability

- Standard REST + consumer + outbox-lag metrics (same baseline as siblings)
- **Projection-specific**:
  - `admin.projection.lag.seconds{source_service,topic}` — event time → applied
    time
  - `admin.projection.dropped.count{reason=stale|duplicate}` — late-event drops
    and dedupe hits
  - `admin.projection.error.count{topic}` — exception rate per consumer
- **Dashboard-specific**:
  - `admin.query.latency.p95{endpoint}` — slow dashboards visible
  - `admin.query.cache.hit.rate` — Redis query-cache effectiveness

---

## Security

### Roles (built-in v1)

- `WMS_VIEWER` — read-only dashboards, query endpoints
- `WMS_OPERATOR` — operational role (this is the role granted to most users;
  read everywhere + write in inventory / inbound / outbound)
- `WMS_ADMIN` — read everywhere + admin-service write (user / role / settings)
- `WMS_SUPERADMIN` — also can override soft constraints (force-deactivate users
  with assignments, force-fail saga, etc.)

### Authorisation

- Authorisation enforced **inside admin-service application layer** (not in
  controllers). Spring Security method-level checks (`@PreAuthorize`).
- Role-to-permission mapping stored in `admin_role.permissions_json`.
- Other services receive role claims in JWT; they enforce their own
  finer-grained permissions (e.g., `INVENTORY_WRITE` mapped from
  `WMS_OPERATOR`).

### PII Handling

- User email + name → considered **operational PII** (low sensitivity per
  PROJECT.md `data_sensitivity: internal`)
- Standard internal-data handling (TLS in transit, at-rest encryption per
  platform standard, no logging of PII fields)

---

## Testing Requirements

### Unit
- User / role / setting domain validations
- Projection idempotency (apply same event twice → same result)
- Last-write-wins logic with out-of-order events

### Application Service (port fakes / repo fakes)
- Happy path + every domain error per use-case
- Outbox row written in same TX for write paths
- Idempotency-Key behavior on mutation endpoints

### Persistence Adapter (Testcontainers Postgres)
- All repo methods
- Read-model tables: upsert behaviour, version conflicts

### REST Controllers (`@WebMvcTest`)
- All endpoints in `admin-service-api.md`
- Authorization: each role's allowed/denied surface

### Consumers (Testcontainers Kafka)
- Each projection consumer: happy path, dedupe-hit, out-of-order event drop,
  poison → DLT
- Replay test: consume same offset range twice → identical read-model state

### Contract Tests
- All endpoints in `admin-service-api.md`
- All published event schemas (`admin.*`)
- All consumed event schemas (cross-link to other services' contracts)

### Failure-mode
- Same `Idempotency-Key` POST twice → identical result
- Same eventId consumed twice → single projection update
- Out-of-order event arrival → newer state preserved
- Read-model rebuild from offset 0 → matches forward-projection state

---

## Read-Model Rebuild Procedure

Manual ops procedure (documented in `specs/services/admin-service/runbooks/read-model-rebuild.md` —
Open Items):

1. Stop projection consumers (scale `admin-service` down to 0 for projection
   group)
2. Truncate `*_summary`, `*_snapshot`, `*_ref` tables (keep user / role /
   settings + dedupe table for safety)
3. Reset Kafka consumer-group offset to earliest for the projection group
4. Scale `admin-service` back up; consumers replay
5. Monitor `admin.projection.lag.seconds` until lag returns to steady state
6. Smoke-test dashboards

This is **not** scheduled — only triggered after schema migration of read-model
tables or detected drift.

---

## Extensibility Notes

- **Approval workflows** (e.g., adjustment > N requires manager approval) —
  v2 feature; would add domain logic non-trivial enough to revisit the
  Layered-vs-Hexagonal decision.
- **Multi-tenant** — out of v1 (PROJECT.md). Adding requires `tenant_id` on
  every aggregate + projection.
- **External SSO / SCIM** — sync user records from an identity provider. v1
  manual; v2 introduces an inbound port.
- **Time-series read-model** — current snapshots only. v2 may add a TimescaleDB
  hypertable for historical KPI trend queries.

---

## Open Items (Before First Implementation Task)

These must be completed before any `TASK-BE-*` targeting `admin-service` is
moved to `tasks/ready/`:

1. `specs/services/admin-service/domain-model.md` — User, Role, Assignment,
   Setting + read-model table layout (consolidated)
2. `specs/contracts/http/admin-service-api.md` — REST endpoints (dashboards,
   user, role, settings)
3. `specs/contracts/events/admin-events.md` — published event schemas
4. `specs/services/admin-service/idempotency.md` — REST + event-dedupe
5. `specs/services/admin-service/runbooks/read-model-rebuild.md` — manual
   replay procedure
6. Register new error codes in `platform/error-handling.md`:
   `USER_EMAIL_DUPLICATE`, `ROLE_CODE_DUPLICATE`, `USER_HAS_ACTIVE_ASSIGNMENTS`,
   `ROLE_IN_USE`, `SETTING_VALIDATION_ERROR`
7. Add a gateway route for `admin-service` in `gateway-service`
8. Add `## Overrides` entry in `PROJECT.md` declaring Layered exception for
   admin-service

---

## References

- `CLAUDE.md`, `PROJECT.md`
- `rules/domains/wms.md` — Admin / Operations bounded context
- `rules/traits/transactional.md` — T1, T3, T5, T7, T8 (mutation paths)
- `platform/architecture-decision-rule.md`
- `platform/service-types/rest-api.md`
- `platform/service-types/event-consumer.md`
- All sibling services' architecture.md (consumed via events)
