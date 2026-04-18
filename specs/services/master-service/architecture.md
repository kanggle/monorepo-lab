# master-service — Architecture

This document declares the internal architecture of `master-service`.
All implementation tasks targeting this service must follow this declaration
and `platform/architecture-decision-rule.md`.

---

## Identity

| Field | Value |
|---|---|
| Service name | `master-service` |
| Service Type | `rest-api` |
| Architecture Style | **Hexagonal (Ports & Adapters)** |
| Primary language / stack | Java 21, Spring Boot |
| Bounded Context | **Master Data** (per `rules/domains/wms.md`) |
| Deployable unit | `apps/master-service/` |
| Data store | PostgreSQL (owned, not shared) |
| Event publication | Kafka via outbox (per trait `transactional`, rule T3) |

---

## Responsibility

`master-service` owns the **reference data** used by every other WMS service:

- **Warehouse** — 창고 루트
- **Zone** — 창고 내 구역 (온도대/용도)
- **Location** — 물리적 저장 위치 (`WH01-A-01-02-03` 같은 계층 코드)
- **SKU** — 재고 식별 최소 단위 (+ 기본 UOM, tracking type)
- **Partner** — 공급자/고객 거래처
- **Lot** — 같은 SKU의 제조일·유효기한별 구분 단위

It is the **single system of record** for these entities. Other services (inventory,
inbound, outbound, admin) receive snapshots via events and keep local read-model caches.

---

## Out of Scope

`master-service` does NOT own:

- Inventory quantities (owned by `inventory-service`)
- ASN / purchase order lifecycle (owned by `inbound-service`)
- Customer order lifecycle (owned by `outbound-service`)
- Notification delivery (owned by `notification-service`)
- Lot **balance** (which Lot has how much stock) — only the Lot identity and its SKU association

If a change request introduces any of the above, promote it to the owning service.

---

## Architecture Style: Hexagonal

### Rationale

- WMS traits (`transactional`, `integration-heavy`) demand clear separation between
  domain rules (W3 location code uniqueness, W6 referential integrity before delete)
  and infrastructure (JPA, Kafka, future ERP sync adapters).
- Ports / Adapters express external integration points directly — essential because
  this service is the target of master-data sync from external PIM/ERP in the future.
- Consistent with `inventory-service`, `inbound-service`, `outbound-service` which
  will also use Hexagonal. Uniform mental model across the three write-heavy services.

### Trade-off Accepted

- Master CRUD has modest domain logic. The Hexagonal boilerplate (ports, mappers,
  dedicated domain model classes) costs ~2× file count versus Layered. This is
  accepted as a uniform architecture investment — see commentary in the v1 scope
  decision log.

### Package Structure

Follow `.claude/skills/backend/architecture/hexagonal/SKILL.md` exactly.

```
com.wms.master/
├── adapter/
│   ├── in/
│   │   └── rest/
│   │       ├── controller/      # WarehouseController, ZoneController, ...
│   │       └── dto/{request,response}/
│   └── out/
│       ├── persistence/
│       │   ├── entity/          # JPA entities — package-private
│       │   ├── repository/      # Spring Data JPA repositories
│       │   ├── mapper/          # Domain <-> JPA mappers
│       │   └── adapter/         # *PersistenceAdapter implementing out ports
│       └── event/
│           ├── outbox/          # OutboxEntity, OutboxWriter
│           └── publisher/       # Kafka publisher reading outbox
├── application/
│   ├── port/
│   │   ├── in/                  # Use-case interfaces (CreateWarehouseUseCase, ...)
│   │   └── out/                 # WarehousePersistencePort, MasterEventPort, ...
│   ├── service/                 # Use-case implementations (@Service, @Transactional)
│   ├── command/                 # Input records
│   └── result/                  # Output records
├── domain/
│   ├── model/                   # Pure POJOs: Warehouse, Zone, Location, Sku, Partner, Lot
│   ├── event/                   # Domain events published on state change
│   └── service/                 # Domain services for invariants (LocationCodeValidator, ...)
└── config/                      # Spring configuration, bean wiring
```

### Layer Rules

1. **Domain layer has no framework dependency.** No `@Entity`, no `@Component`,
   no Spring. Pure POJOs enforce invariants via static factories and explicit
   state-transition methods.
2. **Application layer depends only on ports.** Never on adapter classes.
   `@Transactional` boundary lives here.
3. **Adapters depend inward.** They implement outbound ports or call inbound ports.
   Adapter-internal types (JPA entities, Kafka records) never leak into ports.
4. **One inbound port per use-case group.** Example: `WarehouseCrudUseCase` groups
   create / update / deactivate / findById / list. Read-heavy queries may have a
   separate `WarehouseQueryUseCase`.
5. **Mappers are adapter-internal.** Domain `Warehouse` ↔ `WarehouseJpaEntity` lives
   in `adapter/out/persistence/mapper/` and is package-private.

---

## Dependencies (Inbound)

| Caller | Contract | Purpose |
|---|---|---|
| `gateway-service` | `specs/contracts/http/master-service-api.md` | External admin/ops UI calls |
| `inventory-service` | `specs/contracts/http/master-service-api.md` | Look up Location / SKU during inventory operations |
| `inbound-service` | Events + `master-service-api.md` | Validate SKU / Partner on ASN; subscribe to master changes for cache |
| `outbound-service` | Events + `master-service-api.md` | Validate SKU / Partner on order; subscribe for cache |
| `admin-service` | `master-service-api.md` | Admin dashboards |

No circular sync dependencies. `master-service` does NOT call any other WMS service synchronously in v1.

---

## Dependencies (Outbound)

v1 outbound dependencies:

- **PostgreSQL** — owned DB
- **Kafka** — event publication (via outbox)

No external systems in v1. When ERP/PIM sync is introduced later, a new outbound
port (`SkuSyncPort`) and its adapter must be added — see extensibility note below.

---

## Event Publication

All master data changes publish events via the **transactional outbox pattern**
(trait `transactional`, rule T3):

| Event | Topic | Purpose |
|---|---|---|
| `master.warehouse.created` | `wms.master.warehouse.v1` | Downstream cache seed |
| `master.warehouse.updated` | `wms.master.warehouse.v1` | Downstream cache invalidation |
| `master.warehouse.deactivated` | `wms.master.warehouse.v1` | Downstream cache invalidation |
| `master.zone.created` / `.updated` / `.deactivated` | `wms.master.zone.v1` | Same |
| `master.location.created` / `.updated` / `.deactivated` | `wms.master.location.v1` | Same |
| `master.sku.created` / `.updated` / `.deactivated` | `wms.master.sku.v1` | Same |
| `master.partner.created` / `.updated` / `.deactivated` | `wms.master.partner.v1` | Same |
| `master.lot.created` / `.expired` | `wms.master.lot.v1` | Downstream awareness |

Full event schemas: `specs/contracts/events/master-events.md`.

---

## Idempotency

All mutating endpoints (POST, PUT, PATCH, DELETE) accept `Idempotency-Key` header
per trait `transactional` rule T1. Implementation:

- Storage: Redis (`master:idempotency:{key}` — response snapshot)
- TTL: 24 hours
- Scope: `(Idempotency-Key, method, path)` tuple
- Full strategy: `specs/services/master-service/idempotency.md` (to be authored before
  implementation per Required Artifacts — see Open Items below)

---

## Concurrency Control

All aggregates carry a `version` column for optimistic locking (trait `transactional`
rule T5). JPA `@Version`, bumped on every UPDATE. Conflicts surface as
`CONFLICT` per `platform/error-handling.md`.

No pessimistic locks in v1.

---

## Key Domain Invariants

Enforced at the domain layer, surfaced via dedicated error codes from
`rules/domains/wms.md`:

| Invariant | Source | Error code |
|---|---|---|
| Location code globally unique | wms.md W3 | `LOCATION_CODE_DUPLICATE` |
| Location belongs to exactly one Zone, which belongs to exactly one Warehouse | wms.md context diagram | `ZONE_NOT_FOUND` / `WAREHOUSE_NOT_FOUND` |
| SKU code globally unique within tenant | wms.md | `SKU_CODE_DUPLICATE` (add to `platform/error-handling.md`) |
| Cannot deactivate SKU if any Lot or Inventory references it | wms.md W6 | `REFERENCE_INTEGRITY_VIOLATION` |
| Cannot deactivate Location if Inventory references it | wms.md W6 | Same |
| Lot must reference an existing, non-deactivated SKU | derived | `SKU_NOT_FOUND` |
| Lot expiry date (if set) must be >= manufactured date | derived | `VALIDATION_ERROR` |
| `tracking_type=LOT` SKU requires every inbound to carry a Lot (enforced downstream, not here) | wms.md | — |

`master-service` checks referential integrity against **its own** data only. Inventory
and inbound references are validated by the owning services via their own event
subscriptions — master-service publishes `deactivation.requested` style events when
needed (out of v1 scope; v1 does a local-only check).

> **v1 simplification**: W6 cross-service check is **local-only** in v1 —
> `master-service` blocks deactivation if its own child records exist (e.g., Zone
> deactivation blocked while active Locations remain). Cross-service inventory check
> is deferred to v2 (likely via a `deactivation-requested` saga). Documented here to
> avoid ambiguity during review.

---

## Persistence

- Database: PostgreSQL (one logical DB per service; no cross-service reads)
- Migrations: Flyway, `apps/master-service/src/main/resources/db/migration/`
- Outbox table: `master_outbox` with columns `id, aggregate_type, aggregate_id, event_type, payload, created_at, published_at`

Full schema lives in migration scripts. High-level tables per entity are defined in
`specs/services/master-service/domain-model.md`.

---

## Observability

Per `service-types/rest-api.md`:

- Metrics: request rate, error rate, latency per endpoint
- Traces: OTel propagation on all inbound and outbound calls
- Logs: structured JSON with `traceId`, `requestId`, `actorId` in MDC
- Business metrics:
  - `master.mutation.count{entity,operation}` — creates / updates / deactivations
  - `master.outbox.lag.seconds` — time from commit to publish
  - `master.idempotency.hit.rate` — cached vs fresh responses

---

## Security

- All endpoints (except health/info) require JWT bearer token validated by
  `gateway-service` and forwarded as headers.
- Authorization in the application layer — not in controllers.
- Roles (v1 baseline):
  - `MASTER_READ` — GET endpoints
  - `MASTER_WRITE` — POST / PUT / PATCH / DELETE
  - `MASTER_ADMIN` — deactivation and hard-delete (v2) operations
- Refer to `.claude/skills/backend/jwt-auth/SKILL.md` for validation wiring.

No PII stored. Partner contact info (email/phone) is operational contact data,
not consumer personal data.

---

## Testing Requirements

Per `platform/testing-strategy.md` and `service-types/rest-api.md`:

- Domain model: unit tests for every invariant (factory, state transition, validation)
- Application service: tests against in-memory port fakes; covers happy path + every
  domain error
- Persistence adapter: slice tests with Testcontainers Postgres
- REST controller: `@WebMvcTest` per controller
- Contract tests: every endpoint in `specs/contracts/http/master-service-api.md`
  verified against the implementation
- Event tests: outbox row written in same transaction as state change; publisher
  publishes exactly once per row
- Idempotency tests: repeated POST with same key returns identical response

---

## Extensibility Notes

Known evolution paths (not part of v1 — documented to guide v2 decisions):

- **ERP / PIM sync**: introduce `SkuSyncPort` (outbound) + adapter; `SkuSyncScheduler`
  inbound adapter for periodic pulls. Does not require architecture change.
- **Multi-warehouse / multi-tenant**: PROJECT.md declares single-tenant. Multi-tenant
  promotion requires `tenant_id` on every aggregate and a new trait declaration.
- **Serial-number tracking** (`tracking_type=SERIAL`): not in v1. Adding requires a new
  aggregate `SerialNumber`, not just a SKU flag.

---

## Open Items (Before First Implementation Task)

These must be completed before any `TASK-BE-*` targeting `master-service` is moved to
`tasks/ready/`:

1. `specs/services/master-service/domain-model.md` — entities, fields, relationships,
   invariants, state per entity
2. `specs/contracts/http/master-service-api.md` — REST endpoints
3. `specs/contracts/events/master-events.md` — event schemas
4. `specs/services/master-service/idempotency.md` — idempotency key strategy
5. Register new error codes (`SKU_CODE_DUPLICATE`, `REFERENCE_INTEGRITY_VIOLATION`) in
   `platform/error-handling.md`
6. Add a gateway route for `master-service` in `gateway-service`

---

## References

- `CLAUDE.md` — workflow and rule priority
- `PROJECT.md` — domain/traits that activate rule layers
- `rules/domains/wms.md` — Master Data bounded context, W1–W6
- `rules/traits/transactional.md` — T1–T8
- `rules/traits/integration-heavy.md` — external integration patterns (future ERP sync)
- `platform/architecture.md` — system-level architecture
- `platform/service-types/rest-api.md` — rest-api mandatory requirements
- `platform/architecture-decision-rule.md` — architecture declaration rules
- `.claude/skills/backend/architecture/hexagonal/SKILL.md` — implementation patterns
