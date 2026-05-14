# procurement-service — Architecture

This document declares the internal architecture of `scm-platform/apps/procurement-service`.
All implementation tasks targeting this service must follow this declaration,
`platform/architecture-decision-rule.md`, and the rule files indexed by
`PROJECT.md`'s declared `domain` and `traits`.

> **Provenance**: Authored retroactively by [TASK-SCM-BE-006](../../../tasks/done/) after
> [TASK-SCM-BE-002](../../../tasks/done/) shipped 89 production files
> (PR #239) without a corresponding spec. Sections describe the **shipped
> implementation** as of the authoring date — discovered drift vs the original
> task specs is documented in the `Drift From Implementation` section.

---

## Identity

| Field | Value |
|---|---|
| Service Name | `procurement-service` |
| Project | `scm-platform` |
| Service Type | `rest-api` (single — see Service Type Composition below) |
| Architecture Style | **Hexagonal** (Ports & Adapters) |
| Domain | scm |
| Traits | transactional, integration-heavy, batch-heavy |
| Primary language / stack | Java 21, Spring Boot 3.4 (Servlet stack) |
| Bounded Context | Procurement (PO + ASN + Supplier master v1) |
| Deployable unit | `apps/procurement-service/` |
| Data store | PostgreSQL `scm_procurement` schema (Flyway) + Redis (idempotency / cache) |
| Event publication | Kafka via transactional outbox |
| Outbound integration | Supplier mock REST adapter (v1); EDI / SFTP siblings deferred to v2 |

### Service Type Composition

`procurement-service` is a single-type `rest-api` service per
`platform/service-types/INDEX.md`. No secondary service-type composition —
all responsibilities (PO lifecycle, supplier outbound calls, webhook ingress)
exposed through synchronous HTTP request/response surface. Kafka publication
is a side effect of REST mutations (transactional outbox) and does not promote
the service to `event-consumer`.

---

## Responsibilities

`procurement-service` owns the **Purchase Order lifecycle** plus the v1 internal
Supplier master. It MUST:

- Issue, submit, acknowledge, confirm, cancel POs and process supplier-issued
  Advance Shipment Notices (ASN) — full state machine in § PO State Machine.
- Maintain a v1 internal `suppliers` master with AES-GCM-encrypted credentials
  (S6). v2 will migrate this responsibility to `supplier-service`.
- Send POs to suppliers through the `SupplierAdapterPort` outbound port,
  carrying a stable `Idempotency-Key` so the supplier dedupes (S2).
- Receive supplier callbacks through two webhook endpoints (PO ack, ASN
  delivery), each verifying the shared `X-Supplier-Signature` and dedupe-ing
  on `(tenantId, supplierAsnRef)`.
- Append every state transition to `po_status_history` and every aggregate
  mutation to `audit_log` (S7).
- Publish `scm.procurement.{po.*, asn.*}.v1` Kafka events through the
  transactional outbox (T2 + T3, S1).
- Enforce tenant isolation defense-in-depth (gateway → JWT validator chain →
  `TenantClaimEnforcer` filter).

It MUST NOT:

- Own `inventory_visibility` read-model state (owned by `inventory-visibility-service`, S5).
- Decide settlement (deferred to `settlement-service`, S3).
- Compute demand forecasts (deferred to `demand-planning-service`, S4).
- Couple to vendor SDKs in domain or application layers (S8 / I7).

---

## Architecture Style Rationale

**Hexagonal (Ports & Adapters)** chosen because:

1. **Multiple inbound channels share the same domain core** — REST controllers
   for buyer/operator commands, two webhook controllers for supplier callbacks,
   and (deferred to v2) Kafka consumers for ASN streams. Adapter swapping
   would break a layered design.
2. **Multiple outbound integrations** — v1 supplier mock REST (`RestSupplierAdapter`),
   v2 EDI / SFTP / vendor-specific adapters all behind one
   `SupplierAdapterPort`. Vendor SDKs MUST stay out of `application/` and
   `domain/` (S8, I7, I8).
3. **Domain logic is framework-free** — `PurchaseOrder` aggregate +
   `PoStatusMachine` enforce invariants without Spring or JPA dependencies in
   the transition logic itself (JPA annotations are present on the entity but
   the state machine is a pure utility).
4. **Testability** — the domain layer is unit-tested without Spring; slice
   tests exercise the persistence adapter; integration tests use Testcontainers
   for the full pipeline (Postgres + Kafka + Redis + WireMock supplier +
   WireMock JWKS).

This choice aligns with `platform/architecture-decision-rule.md` and the
default Hexagonal expectation for `transactional` + `integration-heavy`
services in scm-platform. `gateway-service` is the single intentional
exception (Layered) and documents its trade-off explicitly.

---

## Layer Structure

The on-disk package layout is a Hexagonal variant — `presentation/` is the
inbound web adapter, `infrastructure/` aggregates outbound adapters and config:

```
com.example.scmplatform.procurement/
├── ProcurementServiceApplication.java
├── domain/                     ← Pure Java aggregates + invariants
│   ├── po/
│   │   ├── PurchaseOrder.java                ← aggregate root
│   │   ├── PurchaseOrderLine.java
│   │   ├── Money.java                        ← embedded value object
│   │   ├── repository/PurchaseOrderRepository.java   ← outbound port
│   │   └── status/
│   │       ├── PoStatus.java                 ← 9-state enum
│   │       ├── PoStatusMachine.java          ← transition matrix
│   │       ├── PoStatusHistory.java
│   │       ├── PoStatusHistoryRepository.java
│   │       └── ActorType.java                ← BUYER / OPERATOR / SUPPLIER / SYSTEM
│   ├── asn/
│   │   ├── AdvanceShipmentNotice.java
│   │   ├── AsnLine.java
│   │   └── repository/AsnRepository.java
│   ├── supplier/
│   │   ├── Supplier.java
│   │   ├── SupplierStatus.java
│   │   └── repository/SupplierRepository.java
│   ├── audit/
│   │   ├── AuditLog.java
│   │   └── AuditLogRepository.java
│   └── error/                                ← domain exceptions
│       (PoNotFoundException, PoStatusTransitionInvalidException,
│        AsnOverreceiptException, SupplierUnavailableException, ...)
├── application/                              ← use cases + outbound ports
│   ├── PurchaseOrderApplicationService.java  ← @Transactional command boundaries
│   ├── ActorContext.java
│   ├── PurchaseOrderView.java                ← read-model DTOs
│   ├── AsnView.java
│   ├── command/                              ← use-case input records
│   │   (Draft/Submit/Acknowledge/Confirm/Cancel/ReceiveAsn)
│   ├── event/
│   │   └── ProcurementEventPublisher.java    ← extends BaseEventPublisher (libs/java-messaging)
│   └── port/outbound/
│       ├── SupplierAdapterPort.java          ← supplier integration port
│       ├── IdempotencyStore.java             ← Redis-or-DB dedupe port
│       └── ClockPort.java
├── infrastructure/                           ← outbound adapters + config
│   ├── persistence/jpa/                      ← Spring Data + adapter beans
│   │   (PurchaseOrderJpaRepository, PurchaseOrderRepositoryAdapter, ...)
│   ├── outbox/
│   │   └── ProcurementOutboxPollingScheduler.java
│   ├── supplier/                             ← v1 mock REST adapter
│   │   ├── RestSupplierAdapter.java          ← @Resilience4j-decorated
│   │   ├── SupplierApiClient.java
│   │   └── IdempotencyKeyGenerator.java
│   ├── crypto/
│   │   └── SupplierCredentialsEncryptor.java ← AES-GCM (S6)
│   ├── security/
│   │   ├── SecurityConfig.java
│   │   ├── ServiceLevelOAuth2Config.java
│   │   ├── AllowedIssuersValidator.java
│   │   ├── TenantClaimValidator.java
│   │   ├── ActorContextResolver.java
│   │   └── ActorContextJwtAuthenticationConverter.java
│   └── config/
│       (ClockConfig, JpaConfig)
└── presentation/                             ← inbound web adapter
    ├── controller/
    │   ├── PurchaseOrderController.java        ← /api/procurement/po/**
    │   ├── SupplierAckWebhookController.java   ← /api/procurement/webhooks/supplier-ack
    │   └── AsnWebhookController.java           ← /api/procurement/webhooks/asn
    ├── advice/GlobalExceptionHandler.java      ← domain → HTTP envelope mapping
    ├── dto/                                    ← request / response DTOs
    ├── filter/TenantClaimEnforcer.java         ← service-level fail-closed
    └── security/PublicPaths.java
```

### Allowed dependencies

- `org.springframework.boot:spring-boot-starter-{web,data-jpa,data-redis,validation,actuator,security,oauth2-resource-server}`
- `org.springframework.kafka:spring-kafka`
- `org.flywaydb:flyway-core`, `flyway-database-postgresql`, `org.postgresql:postgresql` (runtime)
- `io.github.resilience4j:resilience4j-{spring-boot3,circuitbreaker,retry,bulkhead}` (2.2.0)
- `io.micrometer:micrometer-registry-prometheus`, `micrometer-tracing-bridge-otel`
- `io.opentelemetry:opentelemetry-exporter-otlp`
- `com.fasterxml.jackson.{core:jackson-databind, datatype:jackson-datatype-jsr310}`
- `net.logstash.logback:logstash-logback-encoder` (prod profile)
- shared libs: `libs:java-common`, `libs:java-web`, `libs:java-messaging`, `libs:java-observability`, `libs:java-security`

### Forbidden dependencies

- Vendor-specific supplier SDKs in `domain/` or `application/` — must be
  hidden behind `infrastructure/supplier/` (S8, I7, I8).
- `inventory-visibility-service` direct calls — `procurement-service` MUST
  NOT consult cross-node inventory snapshots for PO decisions (S5).
- `wms-platform` adapters — wms is a downstream consumer of
  `scm.procurement.po.*` events, not a peer for synchronous calls.
- Persistence frameworks beyond `spring-boot-starter-data-{jpa,redis}` — no
  reactive variants (this is a Servlet-stack service).

### Boundary rules

- `domain/` MUST NOT depend on Spring (annotations on JPA entities are the
  single allowed exception, mandated by Spring Data semantics).
- `application/PurchaseOrderApplicationService` is the **only** transactional
  command boundary — controllers MUST NOT carry `@Transactional`.
- `presentation/controller/` MUST NOT depend on JPA repositories directly —
  every persistence touch flows through `application/` use cases.
- `infrastructure/supplier/RestSupplierAdapter` is the **only** code path
  permitted to import vendor / HTTP client classes. The application layer
  consumes it through `SupplierAdapterPort` exclusively.
- `presentation/filter/TenantClaimEnforcer` MUST be defense-in-depth only —
  the gateway and JWT validator chain are the primary tenant gate; this
  filter exists so a misconfigured gateway cannot break the invariant.

---

## REST endpoints (v1)

All paths are below `/api/procurement/**`. The gateway exposes them under the
external `/api/v1/procurement/**` namespace and rewrites the prefix (see
[`gateway-public-routes.md`](../../contracts/http/gateway-public-routes.md)).

| Method | Path | Auth | Idempotency | Use case |
|---|---|---|---|---|
| `POST` | `/api/procurement/po` | JWT | `Idempotency-Key` required | draft a new PO (DRAFT) |
| `GET` | `/api/procurement/po` | JWT | n/a | list / search POs (paginated) |
| `GET` | `/api/procurement/po/{poId}` | JWT | n/a | fetch one PO |
| `POST` | `/api/procurement/po/{poId}/submit` | JWT | `Idempotency-Key` required | DRAFT → SUBMITTED + supplier dispatch |
| `POST` | `/api/procurement/po/{poId}/confirm` | JWT (operator) | `Idempotency-Key` required | ACKNOWLEDGED → CONFIRMED |
| `POST` | `/api/procurement/po/{poId}/cancel` | JWT | `Idempotency-Key` required | DRAFT/SUBMITTED/ACK → CANCELED |
| `POST` | `/api/procurement/webhooks/supplier-ack` | shared-secret | `(tenantId, poId)` natural | supplier acknowledgement (SUBMITTED → ACK) |
| `POST` | `/api/procurement/webhooks/asn` | shared-secret | `(tenantId, supplierAsnRef)` UNIQUE | supplier ASN delivery (CONFIRMED → PARTIAL/RECEIVED) |
| `GET` | `/actuator/health` | none | n/a | liveness/readiness |
| `GET` | `/actuator/info` | none | n/a | build info |
| `GET` | `/actuator/prometheus` | network-isolated | n/a | metrics scrape (internal docker network only) |

Formal request/response shapes live in
[`procurement-api.md`](../../contracts/http/procurement-api.md).

> **Webhook security (v1)**: a fixed shared secret is verified against the
> `X-Supplier-Signature` header. Per `rules/traits/integration-heavy.md` I6,
> v2 webhook upgrade adds HMAC + timestamp + replay protection — tracked as
> a follow-up. The webhook endpoints themselves are publicly routable (no
> JWT) because the supplier issuing the call has no GAP identity.

---

## PO State Machine

`PoStatus` enum has 9 values; `PoStatusMachine.ensureTransitionAllowed`
enforces the matrix below. Self-transitions are forbidden so callers cannot
silently no-op. `CANCELED` and `CLOSED` are terminal — every outbound
transition is rejected.

```
DRAFT
  ├─(BUYER/OPERATOR submit)→ SUBMITTED
  └─(BUYER/OPERATOR cancel)→ CANCELED ★
SUBMITTED
  ├─(SUPPLIER ack via webhook)→ ACKNOWLEDGED
  └─(BUYER/OPERATOR cancel)→ CANCELED ★
ACKNOWLEDGED
  ├─(OPERATOR confirm)→ CONFIRMED
  └─(BUYER/OPERATOR cancel)→ CANCELED ★
CONFIRMED
  ├─(SYSTEM apply ASN, partial)→ PARTIALLY_RECEIVED
  └─(SYSTEM apply ASN, full in one shot)→ RECEIVED
PARTIALLY_RECEIVED
  └─(SYSTEM apply ASN, complete)→ RECEIVED
RECEIVED
  └─(SYSTEM, deferred to settlement-service)→ SETTLED
SETTLED
  └─(SYSTEM, deferred to settlement-service)→ CLOSED ★
```

★ = terminal.

### Allowed transitions per actor

| Actor | From | To |
|---|---|---|
| BUYER | DRAFT | SUBMITTED, CANCELED |
| BUYER | SUBMITTED | CANCELED |
| BUYER | ACKNOWLEDGED | CANCELED |
| OPERATOR | DRAFT | SUBMITTED, CANCELED |
| OPERATOR | SUBMITTED | CANCELED |
| OPERATOR | ACKNOWLEDGED | CONFIRMED, CANCELED |
| SUPPLIER | SUBMITTED | ACKNOWLEDGED |
| SYSTEM | CONFIRMED | PARTIALLY_RECEIVED, RECEIVED |
| SYSTEM | PARTIALLY_RECEIVED | RECEIVED |
| SYSTEM | RECEIVED | SETTLED |
| SYSTEM | SETTLED | CLOSED |

### State transition history

Every transition writes a `po_status_history` row inside the same transaction
as the PO update (T3, S7):

```
po_status_history(id, po_id, tenant_id, from_status, to_status,
                  actor_account_id, actor_type, reason, occurred_at)
```

`actor_type ∈ {BUYER, OPERATOR, SUPPLIER, SYSTEM}` (CHECK constraint).
History is append-only — no UPDATE / DELETE.

### v1 vs v2 reachability

`SETTLED` and `CLOSED` are declared in `PoStatus` and `PoStatusMachine`, and
the publisher constants `EVENT_PO_CLOSED` / `TOPIC_PO_CLOSED` exist, but no
v1 use case drives those transitions. They become reachable when
`settlement-service` (deferred) issues the SYSTEM-actor commands. Documented
under `Drift From Implementation` to flag this intentional dead surface.

---

## Resilience4j integration patterns

The supplier outbound call is decorated with three Resilience4j primitives,
configured in `application.yml` under the shared `supplier` instance key:

| Primitive | Setting | Value |
|---|---|---|
| `@CircuitBreaker(name="supplier")` | `failure-rate-threshold` | 50% |
| | `slow-call-rate-threshold` | 50% |
| | `slow-call-duration-threshold` | 5s |
| | `sliding-window-type` | TIME_BASED |
| | `sliding-window-size` | 10 |
| | `minimum-number-of-calls` | 5 |
| | `wait-duration-in-open-state` | 10s |
| | `permitted-number-of-calls-in-half-open-state` | 3 |
| | `register-health-indicator` | true |
| `@Retry(name="supplier")` | `max-attempts` | 3 |
| | `wait-duration` | 500ms |
| | `enable-exponential-backoff` | true |
| | `exponential-backoff-multiplier` | 2 |
| | `enable-randomized-wait` | true |
| | `randomized-wait-factor` | 0.5 |
| | `retry-exceptions` | `IOException`, `ResourceAccessException` |
| | `ignore-exceptions` | `HttpClientErrorException` (4xx never retries) |
| `@Bulkhead(name="supplier")` | `max-concurrent-calls` | 20 |
| | `max-wait-duration` | 100ms |

Annotation order on `RestSupplierAdapter.submitPurchaseOrder`:
`@CircuitBreaker(fallbackMethod="submitFallback") @Retry @Bulkhead`. The
fallback method translates `CallNotPermittedException` and exhausted-retry
failures into a domain `SupplierUnavailableException` — caller sees a
business-meaningful exception, never a Resilience4j type.

`I7 / I8` compliance: supplier vendor types never leak past
`infrastructure/supplier/`. The outbound port returns
`SupplierSubmissionResult(supplierReceiptRef, status)` — a translated domain
record, not an HTTP response.

`I9` compliance: bulkhead bounds concurrent supplier calls to 20 per service
instance, preventing a slow supplier from exhausting the request thread pool.

Edge case (Failure Mode #7): a supplier call failure during PO submit MUST
roll back the SUBMITTED state transition. The application service places the
supplier call **before** the state transition inside the same transaction, so
a thrown `SupplierUnavailableException` aborts the commit and the PO stays
DRAFT for retry.

---

## Saga / Long-running Flow (ADR-MONO-005)

Per [ADR-MONO-005](../../../../../docs/adr/ADR-MONO-005-saga-timeout-escalation-dead-letter-policy.md) — **Category B reference implementation** for the monorepo.

| Flow | Category | Resilience4j config | Fail-CLOSED terminal | Metrics | Status |
|---|---|---|---|---|---|
| supplier PO submission (`PurchaseOrder` ↔ supplier API) | **B** (synchronous external, no saga row) | `@CircuitBreaker(supplier)` 50 % failure rate / 10-call sliding window · `@Retry(supplier)` 3 attempts with exponential backoff + random jitter (4xx ignored) · `@Bulkhead(supplier)` semaphore, 20 concurrent | `SupplierUnavailableException` → HTTP 503 `SUPPLIER_UNAVAILABLE`; PO stays `DRAFT` (no state advance on adapter exhaustion) | Resilience4j default metrics (`resilience4j.circuitbreaker.{state,calls,failure_rate}{name="supplier"}`) + supplier call-result tags | **Compliant** (reference impl) |

Source: `RestSupplierAdapter` (`@CircuitBreaker(name="supplier", fallbackMethod="submitFallback")`). Fallback translates `CallNotPermittedException` / transport errors uniformly to `SupplierUnavailableException` so the controller advice can map to 503 regardless of cause class.

---

## Outbox + audit_log invariants

### Transactional outbox (T2 + T3, S1)

Every event publish goes through `ProcurementEventPublisher`, which extends
`BaseEventPublisher` (libs/java-messaging) and writes to the `outbox` table
inside the same `@Transactional` boundary as the state change. Topics and
event-type constants:

| Event type constant | Kafka topic | Aggregate |
|---|---|---|
| `EVENT_PO_SUBMITTED` | `scm.procurement.po.submitted.v1` | purchase_order |
| `EVENT_PO_ACKNOWLEDGED` | `scm.procurement.po.acknowledged.v1` | purchase_order |
| `EVENT_PO_CONFIRMED` | `scm.procurement.po.confirmed.v1` | purchase_order |
| `EVENT_PO_CANCELED` | `scm.procurement.po.canceled.v1` | purchase_order |
| `EVENT_PO_RECEIVED` | `scm.procurement.po.received.v1` | purchase_order |
| `EVENT_PO_CLOSED` | `scm.procurement.po.closed.v1` | purchase_order |
| `EVENT_ASN_RECEIVED` | `scm.procurement.asn.received.v1` | asn |

`ProcurementOutboxPollingScheduler` (extends libs `OutboxPollingScheduler`)
polls every `outbox.polling.interval-ms` (default 1000ms) in batches of
`outbox.polling.batch-size` (default 50). Disabled in slice tests via
`outbox.polling.enabled=false`. Failed publishes increment the
`procurement_outbox_publish_failures_total` Micrometer counter.

Source = `"scm-platform-procurement-service"` on every published envelope.

> **Topic versioning**: `.v1` suffix follows the platform convention (every
> Kafka topic = `eventType` + `.v1`). Breaking changes bump to `.v2` and
> publish to both during the deprecation window.

### Audit log (S7)

`audit_log` is the cross-aggregate audit trail; `po_status_history` is the
PO-specific transition log. Both are append-only (no UPDATE/DELETE) and
written inside the same transaction as the state change:

```
audit_log(id BIGSERIAL, tenant_id, aggregate_type, aggregate_id, action,
          actor_account_id, actor_type, before_state JSONB, after_state JSONB,
          occurred_at TIMESTAMPTZ)
```

Indexes: `(aggregate_type, aggregate_id, occurred_at DESC)` and
`(tenant_id, occurred_at DESC)` for forensic queries.

Use cases that write to `audit_log`:

| Use case | aggregate_type | action |
|---|---|---|
| `draft` | purchase_order | DRAFT |
| `submit` | purchase_order | SUBMIT |
| `acknowledge` | purchase_order | ACKNOWLEDGE |
| `confirm` | purchase_order | CONFIRM |
| `cancel` | purchase_order | CANCEL |
| `receiveAsn` | asn | RECEIVE |

---

## Idempotency (T1, S2)

All mutating REST endpoints require an `Idempotency-Key` header
(`MissingRequestHeaderException` → 400 `IDEMPOTENCY_KEY_REQUIRED` via
`GlobalExceptionHandler`). The shape:

- **Inbound** (REST): `Idempotency-Key` header validated by
  `IdempotencyStore` port — Redis primary (NX-EX), `idempotency_keys` table
  fallback when Redis is offline (Failure Mode #4 fail-CLOSED).
- **Inbound** (webhook): natural keys — `(tenantId, supplierAsnRef)` UNIQUE
  on `advance_shipment_notices` for ASN; `(tenantId, poId, supplierAckRef)`
  semantic dedupe on supplier-ack (already-ACK PO short-circuits).
- **Outbound** (supplier): `IdempotencyKeyGenerator.forSubmission(poId,
  callerKey)` derives a stable per-PO idempotency key passed to the supplier
  via `Idempotency-Key` header (S2 mandate).

`idempotency_keys` table schema:

```
idempotency_keys(idempotency_key, endpoint, tenant_id,
                 payload_hash, response_status, response_body,
                 created_at, expires_at)
PRIMARY KEY (idempotency_key, endpoint, tenant_id)
```

A future cleanup job will purge rows past `expires_at` (deferred — no v1
code path).

---

## Multi-tenancy

Every table carries `tenant_id VARCHAR(64) NOT NULL`; key indexes prefix
`tenant_id` (e.g. `idx_po_tenant_status_created`). Repository methods
**always** accept `tenantId` and embed it in `WHERE`. Cross-tenant reads are
structurally impossible — there is no method that omits tenant.

Defense-in-depth tenant enforcement (3 layers):

1. **Gateway** — `TenantClaimValidator` rejects cross-tenant tokens at JWT
   decode time; `tenant_id ∈ {scm, *}` only.
2. **Service JWT validator chain** — `AllowedIssuersValidator` +
   `TenantClaimValidator` re-run during local JWT decode (the gateway
   forwards the bearer; the service decodes it again).
3. **Service filter** — `TenantClaimEnforcer` (servlet filter, public-paths
   skipped) reads the resolved `JwtAuthenticationToken` and rejects with
   403 `TENANT_FORBIDDEN` if `tenant_id` is missing or not in
   `{scm, *}`.

Webhook endpoints are excluded from JWT enforcement (they have no GAP
identity) but are still tenant-scoped via the request body — the supplier
includes `tenantId` and the database UNIQUE constraint
`(tenant_id, supplier_asn_ref)` is the structural backstop.

---

## Security

### JWT validation (RS256)

Same as `gateway-service` (consistent issuer allow-list):

- Decoder: standard `oauth2-resource-server` against
  `${OIDC_ISSUER_URL:http://gap.local}/oauth2/jwks`.
- Algorithm: RS256 only.
- Validators: `JwtTimestampValidator` (default) +
  `AllowedIssuersValidator` (SAS issuer + legacy `"global-account-platform"`
  during D2-b deprecation window) + `TenantClaimValidator`
  (`tenant_id ∈ {scm, *}`).
- The allowed-issuers value MUST stay byte-identical to
  `gateway-service`'s `application.yml` — drift causes
  gateway-pass / service-401 inconsistencies. Cross-service edit must land
  in the same commit.

### Column-level encryption (S6)

`SupplierCredentialsEncryptor` uses **AES-256-GCM**:

- Layout: `[12-byte IV][ciphertext][16-byte tag]`. AES-GCM auto-appends the
  tag when encryption finalizes.
- Key: 32 bytes from
  `scmplatform.procurement.crypto.supplier-credentials-key` (env override
  `SUPPLIER_CREDENTIALS_KEY`). Dev default is intentionally weak; production
  MUST override.
- Key rotation: per-row `encryption_key_id` column — v1 always `"v1"`. v2
  vault integration enables rotated keys side-by-side.
- Boot self-test: a probe round-trips on application startup; failure
  closes the application context (fail-fast on misconfiguration).

### Public paths (no auth required)

- `/actuator/health`, `/actuator/info`, `/actuator/prometheus`
- `/api/procurement/webhooks/**` (shared-secret verified inside controllers)

All other paths require JWT or are denied (`anyRequest().denyAll()`).

---

## Mandatory Rule mapping (rules/domains/scm.md)

| Rule | Status | Mechanism |
|---|---|---|
| **S1** Multi-leg state transitions are idempotent + transaction-protected | ✅ Applied | `PoStatusMachine` enforced inside `@Transactional` use cases; outbox writes share the same Tx (T2 + T3) |
| **S2** Supplier external calls carry idempotency key | ✅ Applied | `IdempotencyKeyGenerator.forSubmission(poId, callerKey)` → `Idempotency-Key` header on supplier HTTP call |
| **S3** Settlement period lock immutability | N/A v1 | Deferred to `settlement-service`; `SETTLED → CLOSED` transition exists but is unreachable in v1 |
| **S4** Demand forecast reproducibility | N/A | Deferred to `demand-planning-service` |
| **S5** Cross-node inventory visibility eventual consistency | ✅ Applied (negative) | `procurement-service` MUST NOT read `inventory-visibility-service` for PO decisions; explicit Forbidden Dependency |
| **S6** Supplier credentials encryption | ✅ Applied | `SupplierCredentialsEncryptor` AES-GCM column encryption on `supplier_credentials.encrypted_payload` |
| **S7** State transition audit trail | ✅ Applied | `po_status_history` (PO-specific) + `audit_log` (cross-aggregate) — append-only, written inside use-case Tx |
| **S8** Reconciliation discrepancy auto-close forbidden | N/A v1 | Deferred to `settlement-service` (PO/ASN/invoice matching) |

> S5 negative compliance is structural: there is no client / port to
> `inventory-visibility-service`, so the rule cannot be violated.

---

## Trait Rule mapping (rules/traits/)

| Trait Rule | Status | Mechanism |
|---|---|---|
| **T1** Idempotency on mutating endpoints | ✅ | `Idempotency-Key` header required on all REST mutations; `idempotency_keys` table + Redis primary |
| **T2** Atomic state-change + outbox write | ✅ | `ProcurementEventPublisher.writeEvent` runs inside `@Transactional` use case |
| **T3** Outbox table + polling relay | ✅ | `outbox` + `processed_events` tables; `ProcurementOutboxPollingScheduler` polls every 1s |
| **T4** State machine enforced via dedicated module | ✅ | `PoStatusMachine.ensureTransitionAllowed` — no setter mutations |
| **T7** Optimistic locking on aggregates | ✅ | `@Version` on `PurchaseOrder`, `Supplier`, `AdvanceShipmentNotice` |
| **I2** Circuit breaker on external calls | ✅ | `@CircuitBreaker(name="supplier")` 50%/10-call window |
| **I3** Retry with jitter | ✅ | `@Retry(name="supplier")` 3-attempt exponential + randomized 0.5 |
| **I6** Webhook signature + timestamp + replay | ⚠️ Partial | v1 = shared-secret only; HMAC + timestamp + replay deferred to v2 (tracked) |
| **I7** Vendor SDK isolation | ✅ | All HTTP types confined to `infrastructure/supplier/` |
| **I8** Vendor types never reach domain | ✅ | `SupplierSubmissionResult(supplierReceiptRef, status)` translated record |
| **I9** Bulkhead on external calls | ✅ | `@Bulkhead(name="supplier")` 20 concurrent calls |
| **batch-heavy** | N/A | No batch jobs in procurement-service v1; first batch-heavy code lives in `inventory-visibility-service` (`StalenessDetectionScheduler`) |

---

## Dependencies

| Direction | Target | Protocol | Notes |
|---|---|---|---|
| In | scm-platform `gateway-service` | HTTP `/api/v1/procurement/**` (rewritten to `/api/procurement/**`) | tenant-validated JWT |
| In | Supplier (mock) | HTTP webhook `/api/procurement/webhooks/{supplier-ack,asn}` | shared-secret signature |
| Out | PostgreSQL `scm_procurement` | JDBC | 9 tables (suppliers, supplier_credentials, purchase_orders, purchase_order_lines, po_status_history, advance_shipment_notices, asn_lines, audit_log, outbox + processed_events + idempotency_keys) |
| Out | Redis | TCP | idempotency primary store (NX-EX) |
| Out | Kafka | TCP | publishes 7 `scm.procurement.*.v1` topics; producer with `acks=all`, `enable.idempotence=true` |
| Out | Supplier (mock) | HTTPS (configurable) | `RestSupplierAdapter` with Resilience4j; `SUPPLIER_MOCK_BASE_URL` env |
| Out | GAP `/oauth2/jwks` | HTTPS | JWT signature verification (libs/java-security pattern) |
| Out (observability) | OTLP collector | HTTPS | `${OTLP_ENDPOINT}` for traces |

### Master Reads

`procurement-service` does **not** consume any cross-service master events
in v1. Supplier identity is the v1 internal `suppliers` table. v2 will
introduce a `master.supplier.*` event subscription when `supplier-service`
is bootstrapped.

---

## Observability

- Logback pattern includes `traceId`, `requestId`, `tenantId`, `accountId`
  MDC keys (set by `libs/java-observability`).
- Custom Micrometer counters:
  - `procurement_outbox_publish_failures_total` — Kafka publish failures
    from the outbox relay.
  - Resilience4j auto-published metrics:
    `resilience4j_circuitbreaker_state{name="supplier"}`,
    `resilience4j_retry_calls_total{...}`,
    `resilience4j_bulkhead_available_concurrent_calls{name="supplier"}`.
- Tracing: OTLP via `micrometer-tracing-bridge-otel` +
  `opentelemetry-exporter-otlp`; sampling 100% (dev default).
- Prometheus scrape on `/actuator/prometheus`, **internal docker network
  only** — gateway never routes external traffic to `/actuator/prometheus`.

---

## Failure Modes

| # | Situation | Behavior |
|---|---|---|
| 1 | Missing `Idempotency-Key` on mutating REST | 400 `IDEMPOTENCY_KEY_REQUIRED` |
| 2 | Same `Idempotency-Key` with different payload | 422 `IDEMPOTENCY_KEY_MISMATCH` (`IdempotencyKeyMismatchException`) |
| 3 | Cross-tenant JWT (tenant_id ∉ {scm, *}) | 403 `TENANT_FORBIDDEN` (validator chain or filter) |
| 4 | Redis offline during idempotency check | fail-CLOSED → fall back to `idempotency_keys` table; if both unavailable, 503 `IDEMPOTENCY_STORE_UNAVAILABLE` |
| 5 | Supplier circuit OPEN | 503 `SUPPLIER_UNAVAILABLE` (translated by fallback method); PO stays in pre-call status |
| 6 | Supplier 4xx (HttpClientErrorException) | propagates, no retry; mapped to 502 `SUPPLIER_REJECTED` by GlobalExceptionHandler |
| 7 | Supplier transient 5xx | retried up to 3× with exponential + jitter; success → continue, exhaustion → SupplierUnavailableException |
| 8 | Bulkhead saturation | 503 `SUPPLIER_UNAVAILABLE` after 100ms wait |
| 9 | PO state transition not allowed | 409 `PO_STATUS_TRANSITION_INVALID` (`PoStatusTransitionInvalidException`) |
| 10 | ASN over-receipt (cumulative > ordered) | 422 `ASN_OVERRECEIPT` (`AsnOverreceiptException`) |
| 11 | Duplicate ASN webhook (same `supplierAsnRef`) | idempotent — returns the existing ASN with the original response shape |
| 12 | Already-ACK PO receives another supplier-ack webhook | idempotent no-op — returns the PO in its current status |
| 13 | Webhook signature mismatch | 401 `WEBHOOK_SIGNATURE_INVALID` |
| 14 | Crypto key misconfiguration | application context shutdown at startup (boot self-test) |
| 15 | Outbox publish failure | row stays `PENDING`, retried by next polling tick; counter `procurement_outbox_publish_failures_total` increments |
| 16 | Optimistic-lock conflict on PO save | 409 `CONCURRENT_MODIFICATION` (Spring's `OptimisticLockingFailureException` mapping) |

---

## Testing Strategy

- **Unit** (`./gradlew :apps:procurement-service:test`):
  - Domain: `PoStatusMachineTest`, `PurchaseOrderTest` (state invariants),
    `MoneyTest`, `SupplierTest`.
  - Application: `PurchaseOrderApplicationServiceTest` (mock ports).
  - Adapters: `RestSupplierAdapterTest` (mock client), validator unit tests
    (`TenantClaimValidatorTest`, `AllowedIssuersValidatorTest`).
  - Crypto: `SupplierCredentialsEncryptorTest` (round-trip + tamper).
  - Filter: `TenantClaimEnforcerTest`.
- **Slice**: `IdempotencyKeyGeneratorTest`, JPA adapter slices (H2), error
  handler slice.
- **Integration** (`./gradlew :apps:procurement-service:integrationTest`,
  `@Tag("integration")`, Testcontainers + WireMock JWKS + WireMock supplier):
  - PO lifecycle E2E (draft → submit → ack webhook → confirm → asn webhook
    → received → outbox publish → topic verification).
  - Supplier circuit breaker — induce 503s, watch CB OPEN, observe 503
    responses; allow `wait-duration-in-open-state` + half-open reset.
  - Idempotency: same `Idempotency-Key` with same payload returns prior
    response; different payload → 409.
  - Cross-tenant JWT → 403; missing tenant claim → 401.
  - Webhook signature mismatch → 401; correct signature + duplicate
    `supplierAsnRef` → idempotent return.
  - Optimistic-lock concurrency under parallel submit / cancel.

`integrationTest` is excluded from `./gradlew check` so the fast feedback
loop stays Docker-free.

---

## Drift From Implementation (audit findings)

The following spec-vs-code deltas surfaced during retroactive authoring.
None block the spec — they are filed here for transparency and follow-up:

1. **`gateway-public-routes.md` placeholder catalog is stale.** The
   anticipated v1 endpoints (`POST /po/{poId}/asn`) do not match shipped
   code (ASN is delivered via `/api/procurement/webhooks/asn` webhook, not
   a buyer-facing endpoint). [TASK-SCM-BE-008](../../../tasks/ready/) is
   the natural place to reconcile (its scope already covers
   `gateway-public-routes.md` for inventory-visibility — extend to
   procurement in the same PR or file separately).
2. **`scm-procurement-events.md` does not exist.** ~~The 7 published topic
   contracts have no formal spec file. Filed as follow-up TASK-SCM-BE-009
   candidate (event contract authoring — separate from this task because
   it requires payload-schema authoring per topic).~~ **Resolved** by
   [TASK-SCM-BE-009](../../../tasks/done/TASK-SCM-BE-009-procurement-events-contract-authoring.md)
   (PR #341, 2026-05-11). Contract authored at
   [`scm-procurement-events.md`](../../contracts/events/scm-procurement-events.md)
   — envelope shape + 7 per-topic payload schemas + consumer rules.
3. **`SETTLED` / `CLOSED` are dead surface in v1.** Enum values, transition
   matrix entries, event constants, and Kafka topic mappings exist but no
   v1 use case drives them. Intentional — the `settlement-service` (v2)
   will issue the SYSTEM commands. Documented in § PO State Machine §
   v1 vs v2 reachability so future readers do not mistake them for
   incomplete code.
4. **TASK-SCM-BE-002 § Architecture mentioned a `state-machines/po-status.md`
   sub-spec** (per `rules/domains/scm.md` § Required Artifacts #1). This
   architecture.md inlines the PO state machine instead. Splitting into a
   dedicated file is a low-priority follow-up if the section grows past
   maintainable size.
5. **Webhook security is shared-secret only (v1)**, not HMAC + timestamp +
   replay protection per I6. Tracked under § Trait Rule mapping I6
   "Partial". v2 webhook hardening is its own task.

---

## References

- `platform/architecture-decision-rule.md`
- `platform/service-types/rest-api.md`
- `platform/error-handling.md`
- `rules/domains/scm.md` (S1–S8, esp. S1/S2/S6/S7)
- `rules/traits/transactional.md` (T1–T7)
- `rules/traits/integration-heavy.md` (I2/I3/I6/I7/I8/I9)
- [`gateway-service/architecture.md`](../gateway-service/architecture.md)
- [`inventory-visibility-service/architecture.md`](../inventory-visibility-service/architecture.md)
- [`gap-integration.md`](../../integration/gap-integration.md)
- [`gateway-public-routes.md`](../../contracts/http/gateway-public-routes.md)
- [`procurement-api.md`](../../contracts/http/procurement-api.md) (this PR)
- TASK-SCM-BE-002 — bootstrap task (PR #239) that shipped the implementation
- TASK-SCM-BE-002b/c/d — IT cycles (PRs #244/#246/#250)
- TASK-SCM-INT-001b — schema fixes (PR #262, surfaced poNumber + JdbcTypeCode JSON)
- TASK-MONO-049 — `libs:java-messaging` extraction (`BaseEventPublisher`,
  `OutboxPollingScheduler`, `OutboxPublisher`)
- TASK-SCM-BE-006 — this retroactive authoring task
