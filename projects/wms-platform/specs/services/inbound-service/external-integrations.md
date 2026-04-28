# inbound-service — External Integrations

External vendor catalog for `inbound-service`. Required artifact per
`rules/traits/integration-heavy.md` § Required Artifacts (1).

This document declares **every** external system `inbound-service` integrates
with — direction, auth, timeouts, circuit-breaker policy, retry policy,
observability hooks. Implementation must match these declarations. Changes
here precede code changes (per `CLAUDE.md` Contract Rule).

---

## Catalog Summary

| Vendor | Direction | Protocol | Auth | Required for |
|---|---|---|---|---|
| **External ERP** | inbound (receive) | HTTPS webhook | HMAC-SHA256 | ASN reception |
| **External ERP** | outbound (push ack) | — | — | **Not in v1** |
| **Kafka cluster** | both | TCP / SASL | mTLS or SCRAM | event publish + master snapshot consume |
| **PostgreSQL** | outbound (DB) | TCP | password | persistence |
| **Redis** | outbound (cache) | TCP | password | idempotency store |
| **Secret Manager** | outbound (config) | HTTPS | service-account / IAM | webhook secret retrieval |

Internal services (`master-service`, `inventory-service`, `gateway-service`)
are not "external" — they live in the same project, share the same Kafka
cluster, and follow internal-event contracts in `specs/contracts/`. They are
documented in
[`specs/services/inbound-service/architecture.md`](architecture.md) §
Dependencies.

---

## 1. External ERP — Inbound Webhook (ASN Push)

The ERP system pushes ASN events to `inbound-service` via HTTPS webhook. This
is the **only** integration path between ERP and WMS in v1; ERP does not
poll any inbound REST endpoint, and we do not push acks back to ERP.

### 1.1 Endpoint

```
POST {gateway-base}/webhooks/erp/asn
```

Routed via `gateway-service` to `inbound-service:8082/webhooks/erp/asn`.

Full wire-level contract:
[`specs/contracts/webhooks/erp-asn-webhook.md`](../../contracts/webhooks/erp-asn-webhook.md).

### 1.2 Authentication

- **HMAC-SHA256** signature over the raw request body.
- Secret: per-environment shared secret in Secret Manager (`erp-prod`,
  `erp-stg`, `erp-dr`).
- Header: `X-Erp-Signature: sha256=<lowercase-hex>`.
- Verified before any other processing — failure returns 401 with no DB writes.

### 1.3 Anti-Replay

- `X-Erp-Timestamp` window: ±5 minutes (configurable
  `inbound.webhook.erp.timestamp-window-seconds`, max 600s).
- `X-Erp-Event-Id` dedupe via `erp_webhook_dedupe` table (7-day retention).

### 1.4 Inbound Side: Failure Modes (per I10)

| Scenario | Response | DB effect | Observable |
|---|---|---|---|
| Bad signature | 401 `WEBHOOK_SIGNATURE_INVALID` | none | metric `inbound.webhook.received.total{result=signature_invalid}` |
| Stale timestamp | 401 `WEBHOOK_TIMESTAMP_INVALID` | none | metric `..{result=timestamp_invalid}` |
| Unknown source | 401 `WEBHOOK_SIGNATURE_INVALID` | none | metric `..{result=signature_invalid}` (no secret available) |
| Schema invalid | 422 `VALIDATION_ERROR` | none | metric `..{result=schema_invalid}` |
| Duplicate event-id | 200 `ignored_duplicate` | dedupe row only (no inbox write) | metric `..{result=duplicate}` |
| Domain validation fails (master ref unknown) | 200 `accepted` synchronously; later inbox `status=FAILED` | inbox row + dedupe row + later FAILED status update | metric `inbound.webhook.processing.failure.total{reason}` |
| DB unavailable during ingest | 503 `SERVICE_UNAVAILABLE` | none | infra-level alert |
| Background processor backlog > 100 PENDING | 200 `accepted` (ingest unaffected) | inbox row queues | gauge `inbound.webhook.inbox.pending.count` alerts |

Tests covering each: see
[`specs/contracts/webhooks/erp-asn-webhook.md`](../../contracts/webhooks/erp-asn-webhook.md)
§ "Failure-mode Test Cases".

### 1.5 Backpressure

- Webhook accepts requests as fast as the DB can write
  `erp_webhook_inbox` + `erp_webhook_dedupe` rows (~1 ms each).
- The **background processor** — not the webhook — does the heavy domain
  work. It runs every 1s, picks up `LIMIT 50` PENDING rows.
- If ERP saturates the webhook, the backlog grows (not a request failure).
- Gauge `inbound.webhook.inbox.pending.count > 100` is an alert; ops scales
  the processor cron interval down to 0.5s, or temporarily increases the
  batch size.

### 1.6 ERP-side Retry Expectations

We do not control ERP retry behavior, but document our expectations so the
integration team can compare with their ERP vendor's behavior:

- ERP retries on 5xx and connection failure with exponential backoff (we
  recommend min 30s, max 5 attempts).
- ERP retries on 503 with same `X-Erp-Event-Id`.
- ERP must NOT retry on 401 / 422 — those are caller-side fixes.
- After retries exhaust, ERP escalates to **its own** DLQ; ops investigates
  via the integration's runbook.

### 1.7 Outbound to ERP (NOT IN v1)

Pushing receipt acks back to ERP is out of scope for v1. ERP polls our
`GET /api/v1/inbound/asns?status=CLOSED` (synchronous read) for
reconciliation. v2 may add an outbound webhook (`POST {erp-base}/wms/ack`).
When introduced:

- Outbound HTTP client: WebClient with `connectTimeout=5s`, `readTimeout=30s`
- Circuit breaker: Resilience4j `erpAckCircuit`, fail-rate-threshold=50%,
  sliding-window=60s
- Retry: 3 attempts, exponential backoff with jitter (1s → 2s → 4s ±20%)
- Idempotency: outbound payload includes our `eventId` (= the inbound outbox
  event id) so ERP can dedupe

These are placeholders — fully specified when v2 is scheduled.

---

## 2. Kafka Cluster

### 2.1 Direction

- **Outbound (publish)**: 6 topics for inbound events (see
  `specs/contracts/events/inbound-events.md` § Topic Layout).
- **Inbound (consume)**: 6 master-data topics
  (`wms.master.{warehouse,zone,location,sku,lot,partner}.v1`).

### 2.2 Connection

- Bootstrap: `kafka.brokers` (env-driven, 3+ brokers in prod).
- Auth: SASL/SCRAM-SHA-512 in dev/staging; mTLS in prod (per
  `platform/security-rules.md`).
- Client property `client.id = inbound-service-{instance-id}` for broker-side
  monitoring.

### 2.3 Producer Config (outbox publisher)

```yaml
spring.kafka.producer:
  acks: all                       # await all in-sync replicas
  retries: 5                      # broker-side retries (additive to outbox-level retry)
  enable-idempotence: true        # exactly-once-on-broker semantics
  max-in-flight-requests-per-connection: 5
  compression-type: lz4
  request-timeout-ms: 30000
  delivery-timeout-ms: 120000
  properties:
    linger.ms: 5
    batch.size: 16384
```

Outbox publisher (separate from broker retries):

- Reads `inbound_outbox` rows where `published_at IS NULL`.
- Publishes one row per `KafkaTemplate.send()` call.
- On broker ACK: sets `published_at = now()`.
- On broker NACK: leaves `published_at = null`. Next scheduled run retries.
- Backoff between retries: exponential 1s → 2s → 4s → 8s → 16s (capped).
- Metrics: `inbound.outbox.pending.count`, `inbound.outbox.lag.seconds` (now -
  oldest unpublished row's `created_at`), `inbound.outbox.publish.failure.total`.

### 2.4 Consumer Config (master snapshot)

```yaml
spring.kafka.consumer:
  group-id: inbound-service
  auto-offset-reset: earliest
  enable-auto-commit: false
  isolation-level: read_committed
  max-poll-records: 50
  fetch-max-bytes: 5242880          # 5 MiB
  session-timeout-ms: 45000
  heartbeat-interval-ms: 10000
  properties:
    spring.json.trusted.packages: com.wms.inbound.adapter.in.messaging
```

Per-listener:
- `concurrency: 3` (matches partition count for master topics).
- Manual ACK after successful TX commit.
- `DefaultErrorHandler` with backoff `[1s, 2s, 4s]` then DLT routing.

### 2.5 Failure Modes

| Scenario | Behavior |
|---|---|
| Broker unreachable on publish | Outbox publisher retries; `inbound_outbox.pending.count` grows; alerts at >100 |
| Broker partition leader election | Producer waits up to `delivery-timeout-ms`; transparent in steady state |
| Consumer poll returns stale leader | Auto-recovery on next rebalance; observable via `KafkaConsumer` metrics |
| Consumer hits unparseable record | Routed to DLT immediately; alert on `kafka.consumer.dlt.records.total > 0` |
| Consumer hits transient DB failure | Retried 3 times then routed to DLT; ops re-emits via DLT replay |
| Network partition between service and Kafka | Producer keeps outbox backlog; consumer pauses. Recovery is automatic on partition heal |

### 2.6 No Distributed Transactions (T2)

- Outbox is local-DB-only. Kafka publish is a separate step that observes the
  committed outbox row. There is no XA transaction across DB and Kafka.
- Consumer commits offset only after the domain TX commits — at-least-once
  semantics are inherent.

---

## 3. PostgreSQL

### 3.1 Direction

Outbound (read + write). One logical DB per service —
`inbound_service_db`. Owned exclusively; no other service connects.

### 3.2 Connection

- HikariCP via Spring Boot.
- `spring.datasource.url=jdbc:postgresql://{host}:{port}/inbound_service_db`.
- Pool size: 20 connections (default), tunable via `spring.datasource.hikari.maximum-pool-size`.
- Connection timeout: 5s.
- Validation timeout: 5s.

### 3.3 Migrations

- Flyway, baseline V1 — see `apps/inbound-service/src/main/resources/db/migration/`.
- Naming: `V{n}__{description}.sql`.
- Repeatable: `R__{description}.sql` for views (none in v1).

### 3.4 Failure Modes

| Scenario | Behavior |
|---|---|
| DB connection pool exhausted | Request blocks up to `connection-timeout`; then 503 `SERVICE_UNAVAILABLE` |
| DB read replica lag (when using replica) | v1 reads from primary only; no replica involvement |
| DB master failover | Application reconnects via DNS / pgbouncer; brief 503 window. Outbox publisher resumes from where it stopped |
| Migration failure on startup | Pod fails to start (CrashLoopBackOff). Manual rollback via Flyway `clean` + `migrate` |

### 3.5 No Direct ORM Access from Domain

Per Hexagonal architecture (`platform/architecture.md` and
`.claude/skills/backend/architecture/hexagonal/SKILL.md`): JPA entities are
package-private inside the persistence adapter. Domain models are pure POJOs
without `@Entity` / `@Table` annotations.

---

## 4. Redis

### 4.1 Direction

Outbound (read + write). Used for:

- REST `Idempotency-Key` storage (24h TTL).
- (Future) Webhook ingest backpressure throttling — not v1.

### 4.2 Connection

- Spring Data Redis with Lettuce client.
- Single Redis instance in dev; sentinel cluster in staging/prod.
- Connection timeout: 2s.
- Command timeout: 1s.
- Key prefix convention: `inbound:idempotency:{method}:{path_hash}:{idempotency_key}`.

### 4.3 Failure Modes

| Scenario | Behavior (idempotency surface) |
|---|---|
| Redis unreachable during write | REST endpoint fails closed → 503 `SERVICE_UNAVAILABLE` (matches inventory-service convention) |
| Redis unreachable during read | Same |
| Redis evicts entry due to memory pressure | Treat as cache miss — request flows to use-case. Domain unique constraint backstops (asn_no, etc.) prevent double mutations |

### 4.4 Choice of Failure Mode

`inbound-service` REST idempotency **fails closed** (matches inventory). This
is the opposite of the gateway's rate limiter, which fails open.
Justification: idempotency is a correctness boundary; a missed dedupe lets a
mutation execute twice. Rate-limiting is a soft protection layer; a missed
limit briefly degrades cost, not correctness.

---

## 5. Secret Manager

### 5.1 Direction

Outbound (read). Used for:

- Per-environment ERP webhook HMAC secrets (`erp-prod`, `erp-stg`, `erp-dr`).
- Future: outbound ERP API tokens (v2).

### 5.2 Provider

- v1 dev: env-var fallback (`ERP_WEBHOOK_SECRET_<ENV>`) for local testing.
- v1 prod: AWS Secrets Manager (or equivalent — concrete provider chosen at
  deploy time).
- Refresh cadence: cached at boot; `SIGHUP`-style refresh via Actuator
  `RefreshScope` endpoint (admin-only).
- Rotation procedure: two-secret window — `current` and `previous` both
  acceptable during ERP cut-over. Documented in
  [`specs/contracts/webhooks/erp-asn-webhook.md`](../../contracts/webhooks/erp-asn-webhook.md)
  § Security Notes.

### 5.3 Failure Modes

| Scenario | Behavior |
|---|---|
| Secret Manager unreachable at boot | Pod fails to start (no fallback in prod). Health check fails |
| Secret Manager unreachable at refresh | Old (cached) secret continues to work; refresh logs WARN. Ops triggers manual re-fetch |
| Secret value missing for declared `X-Erp-Source` | Webhook with that source gets 401 `WEBHOOK_SIGNATURE_INVALID` (no secret to compare against) |

---

## 6. Aggregated Resilience Policy

| Vendor | Timeout (connect / read) | Circuit Breaker | Retry (count, base, max) | Idempotency | DLQ / Recovery |
|---|---|---|---|---|---|
| ERP webhook (in) | n/a (we receive) | n/a | n/a (ERP-side) | `X-Erp-Event-Id` 7d dedupe | inbox `FAILED` queue |
| Kafka producer | 5s / 30s | n/a (outbox absorbs) | 5 broker, exp-backoff | `eventId` (downstream) | outbox stays unpublished |
| Kafka consumer | (broker session) | n/a | 3 in-process, [1,2,4]s | `eventId` 30d dedupe | `<topic>.DLT` |
| PostgreSQL | 5s / (statement) | n/a (failure → 5xx) | 0 (TX retry on 409 only) | n/a | n/a |
| Redis | 2s / 1s | n/a (failure → 5xx) | 0 (fail-closed) | n/a | n/a |
| Secret Manager | 3s / 5s | n/a (boot-only path) | 3, exp-backoff | n/a | n/a |

Bulkhead (`integration-heavy` I9): Kafka producer/consumer use Spring's
default thread pools, which are isolated from the HTTP server's request
threadpool. Adding a future ERP-outbound HTTP client (v2) requires a
**dedicated** Resilience4j `ThreadPoolBulkhead` with its own pool — not
shared with PostgreSQL connection pool or HTTP server pool.

---

## 7. Observability

Per `rules/traits/integration-heavy.md` § Interaction with Common Rules:

| Metric | Vendor | Description |
|---|---|---|
| `inbound.webhook.received.total{result}` | ERP-in | Counter of webhook outcomes |
| `inbound.webhook.processing.lag.seconds` | ERP-in | Histogram of inbox-receipt-to-applied lag |
| `inbound.webhook.processing.failure.total{reason}` | ERP-in | Counter by domain failure code |
| `inbound.webhook.inbox.pending.count` | ERP-in | Gauge of PENDING inbox rows |
| `inbound.webhook.dedupe.hit.rate` | ERP-in | Computed metric |
| `inbound.outbox.pending.count` | Kafka-out | Gauge of unpublished outbox rows |
| `inbound.outbox.lag.seconds` | Kafka-out | Histogram of oldest unpublished row age |
| `inbound.outbox.publish.failure.total` | Kafka-out | Counter of publish failures |
| `inbound.consumer.received.total{topic, outcome}` | Kafka-in | Counter by topic + applied/duplicate/failed |
| `inbound.consumer.dlt.records.total{topic}` | Kafka-in | Counter; alerts at >0 |
| `inbound.event.dedupe.hit.rate` | Kafka-in | Computed metric |
| `inbound.event.dedupe.table.size` | Kafka-in | Gauge of dedupe row count |

Logs (structured JSON, INFO level — see
[`idempotency.md`](idempotency.md) § Observability for the precise event
keys).

Tracing (OTel):

- Webhook ingest creates a root span `webhook.erp.asn.ingest` with
  `event.id` and `source` attributes.
- Background processor inherits the trace via `traceId` carried through the
  `erp_webhook_inbox` row (added in v2 if not present in v1).
- Outbound Kafka publish carries the trace via `traceparent` Kafka header.

---

## 8. Test Suite (per `integration-heavy` I10)

All external-integration paths must have failure-mode tests using fakes:

| Path | Test framework |
|---|---|
| ERP webhook ingest | Spring Boot integration test (controller called directly), no real ERP |
| Background processor | Testcontainers PostgreSQL + fake `MasterReadModel` snapshots |
| Kafka producer (outbox) | Testcontainers Kafka |
| Kafka consumer (master snapshots) | Testcontainers Kafka, poison-record case routes to DLT |
| Redis idempotency | Testcontainers Redis |
| Secret Manager | Mock `SecretRetriever` interface (port adapter) |

Tests for production vendor SDKs (e.g., AWS Secrets Manager client) use
LocalStack or equivalent — the service code does not reach real AWS during
test runs.

---

## 9. Per-Vendor Runbook Pointers

When an integration breaks in production, ops follows the per-vendor runbook
(stored in `docs/runbooks/<vendor>.md` of the deploying repo, NOT in this
spec):

- ERP webhook outage → `docs/runbooks/erp-webhook.md`
  - Drains PENDING inbox, escalates to ERP integration owner
- Kafka cluster outage → `docs/runbooks/kafka.md`
- PostgreSQL primary failover → `docs/runbooks/postgres.md`

These are operational documents, not specs. Linked here for completeness.

---

## 10. Not In v1

- Outbound webhook to ERP (push ack)
- mTLS instead of HMAC for inbound webhook
- Multi-tenant ERP (one secret per customer-supplier instead of per-env)
- Scanner / RFID adapter (separate I-XX integration when v2 is scoped)
- Notification provider integration (handled by `notification-service`, not
  inbound-service)

---

## References

- `specs/services/inbound-service/architecture.md` — Dependencies, Webhook
  Reception
- `specs/contracts/webhooks/erp-asn-webhook.md` — wire-level webhook contract
- `specs/services/inbound-service/idempotency.md` — REST + webhook + Kafka
  dedupe strategies
- `specs/contracts/events/inbound-events.md` — outbound Kafka schemas
- `rules/traits/integration-heavy.md` — I1–I10 (especially I1, I2, I5, I6, I7,
  I9, I10)
- `platform/api-gateway-policy.md` — webhook routing tier
- `platform/security-rules.md` — Secret Manager policy
- `platform/observability.md` — required metrics for integrations
- `messaging/outbox-pattern/SKILL.md`
- `messaging/idempotent-consumer/SKILL.md`
