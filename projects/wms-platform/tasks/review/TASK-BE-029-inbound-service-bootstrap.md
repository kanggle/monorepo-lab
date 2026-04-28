# Task ID

TASK-BE-029

# Title

Bootstrap inbound-service with Hexagonal skeleton, Flyway schema, MasterReadModel consumers, and webhook ingest plumbing

# Status

ready

# Owner

backend

# Task Tags

- code
- event
- webhook
- test

---

# Required Sections (must exist)

- Goal
- Scope (in/out)
- Acceptance Criteria
- Related Specs
- Related Contracts
- Edge Cases
- Failure Scenarios

---

# Goal

Stand up the `inbound-service` Spring Boot application skeleton following the
declared Hexagonal architecture, and deliver the infrastructure foundation:

- Flyway-managed DB schema for all tables declared in `domain-model.md`
- MasterReadModel consumers (`MasterWarehouseConsumer`, `MasterZoneConsumer`,
  `MasterLocationConsumer`, `MasterSkuConsumer`, `MasterLotConsumer`,
  `MasterPartnerConsumer`) that receive `master.*` events and upsert
  corresponding snapshots
- ERP webhook **ingest path**: signature/timestamp/dedupe verification +
  `erp_webhook_inbox` write + 200 ack — but no domain processing yet (the
  background processor runs as a no-op stub returning `PENDING → APPLIED`
  with empty Asn create logic; full domain handling lands in TASK-BE-030)
- Redis (idempotency), Kafka (consumer group `inbound-service`), PostgreSQL
  wired and healthy
- EventDedupe table + `EventDedupePort` adapter (used by all future Kafka
  consumers)
- ErpWebhookDedupe + ErpWebhookInbox tables in place
- InboundOutbox table + `OutboxWriter` port stub (publisher process lands in
  TASK-BE-030 alongside the first real outbox event)

On completion `inbound-service` boots, subscribes to `master.*` topics,
keeps a local read-model cache of all 6 master entities, accepts ERP webhook
calls (returning 200 with row written to inbox), and is ready for the ASN
domain implementation in TASK-BE-030.

No ASN / Inspection / Putaway domain logic is in scope for this task. The
webhook controller writes raw payloads to the inbox; the background
processor exists but does not yet call `ReceiveAsnUseCase`.

---

# Scope

## In Scope

- Gradle module under `apps/inbound-service/` (Spring Boot 3.x, Java 21)
- Shared libs wiring: `java-common`, `java-web`, `java-observability`,
  `java-security`, `java-messaging`, `java-test-support`
- Hexagonal package layout per `architecture.md` § Package Structure:
  `adapter/in/{rest,webhook,messaging/consumer}`,
  `adapter/out/{persistence,event,masterref}`,
  `application/{port/{in,out},service,command,result}`,
  `domain/{model,event,service}`, `config/`
- Flyway migrations:
  - `V1__init_master_readmodel.sql` —
    `warehouse_snapshot`, `zone_snapshot`, `location_snapshot`,
    `sku_snapshot`, `lot_snapshot`, `partner_snapshot`
  - `V2__init_asn_tables.sql` — `asn`, `asn_line` (no logic uses them yet,
    but table shape matches `domain-model.md` §1)
  - `V3__init_inspection_tables.sql` — `inspection`, `inspection_line`,
    `inspection_discrepancy`
  - `V4__init_putaway_tables.sql` — `putaway_instruction`, `putaway_line`,
    `putaway_confirmation`
  - `V5__init_outbox_dedupe.sql` — `inbound_outbox`, `inbound_event_dedupe`
  - `V6__init_webhook_inbox.sql` — `erp_webhook_inbox`, `erp_webhook_dedupe`
  - `V7__role_grants.sql` — revoke `UPDATE`, `DELETE` on `inbound_outbox`,
    `inbound_event_dedupe`, `erp_webhook_dedupe`, `putaway_confirmation` from
    app role (W2 append-only invariant for ledger/dedupe tables)
- `EventDedupePort` (out-port) + `EventDedupePersistenceAdapter`
  (implementation) — `Propagation.MANDATORY` so the dedupe row joins the
  caller's TX, matching the inventory-service pattern from TASK-BE-027
- 6 master snapshot consumers — `MasterWarehouseConsumer`,
  `MasterZoneConsumer`, `MasterLocationConsumer`, `MasterSkuConsumer`,
  `MasterLotConsumer`, `MasterPartnerConsumer`. Each:
  - Consumes the matching `wms.master.*.v1` topic
  - Calls `EventDedupePort.process(eventId, eventType, () -> upsert(...))`
  - Upserts via `ON CONFLICT DO UPDATE WHERE snapshot.master_version <
    EXCLUDED.master_version` (DB-level version guard)
  - Maps `*.deactivated` → `status=INACTIVE`, `*.reactivated` →
    `status=ACTIVE`, `master.lot.expired` → `status=EXPIRED`
- `MasterReadModelPort` (out-port) + `MasterReadModelPersistenceAdapter` —
  `findWarehouse(id)`, `findZone(id)`, `findLocation(id)`, `findSku(id)`,
  `findLot(id)`, `findPartner(id)` — each returns `Optional<Snapshot>`
- DLQ wiring: `DefaultErrorHandler` with 3-retry exponential backoff
  `[1s, 2s, 4s]` + `DeadLetterPublishingRecoverer` for all consumers (shared
  baseline)
- `ErpAsnWebhookController` (`POST /webhooks/erp/asn`):
  - HMAC-SHA256 signature verification using per-environment secret resolved
    via `WebhookSecretPort` (from `X-Erp-Source` header)
  - Timestamp window check (±5 min, configurable)
  - Atomic `INSERT INTO erp_webhook_dedupe ON CONFLICT DO NOTHING` +
    `INSERT INTO erp_webhook_inbox` in one TX
  - Schema-validate body against `webhooks/erp-asn-webhook.md` (Bean
    Validation on a `@RequestBody` DTO)
  - Returns 200 `{status: "accepted"|"ignored_duplicate", eventId, ...}`
- `WebhookSecretPort` + an env-var-backed adapter for v1 (Secret Manager
  adapter is v2 — declared but unimplemented, env-var fallback wired)
- `ErpWebhookInboxProcessor` (`@Scheduled`, every 1s under non-test profile):
  - Selects `erp_webhook_inbox` rows `WHERE status='PENDING' LIMIT 50`
  - **Stub body**: marks each row `APPLIED` without creating an Asn (real
    domain logic lands in TASK-BE-030)
  - Logs `webhook_inbox_processed` per row
- Redis idempotency infrastructure: `IdempotencyStore` port + Redis adapter
  (key layout: `inbound:idempotency:{method}:{path_hash}:{key}`, TTL
  86400s) — naming pinned to spec, matching the TASK-BE-025 lesson
- JWT resource server config (via `java-security`); roles `INBOUND_READ`,
  `INBOUND_WRITE`, `INBOUND_ADMIN`. SecurityConfig permits `/webhooks/erp/asn`
  without auth.
- Kafka `OutboxWriter` port stub (real publisher in TASK-BE-030)
- Observability baseline: MDC `traceId` / `requestId` / `actorId`,
  Micrometer metrics, OTel propagation on Kafka consumer + webhook ingest
  span `webhook.erp.asn.ingest`
- `GET /actuator/health` and `GET /actuator/info`
- Dev-profile seed: `V99__seed_dev_masterref.sql` — minimal warehouse +
  location + sku + partner + lot snapshots; activated only under Spring
  profile `dev` or `standalone`
- Unit tests for: master consumer version-guard, HMAC verification,
  timestamp window check, body hash canonicalization (idempotency-key)
- `@SpringBootTest` smoke: boots, health `UP`, all Flyway migrations
  applied, webhook controller reachable

## Out of Scope

- ASN reception domain logic (TASK-BE-030)
- Inspection lifecycle (TASK-BE-031)
- Putaway lifecycle (TASK-BE-032)
- Close + cancel + integration tests (TASK-BE-033)
- Outbox publisher implementation (table created here; publisher logic
  starts in TASK-BE-030)
- Webhook inbox FAILED retry endpoint (v2 — admin REST)
- Gateway route wiring (separate `TASK-INT-*`); the gateway side of the
  `/webhooks/erp/asn` route lives in `gateway-service` config under a
  follow-up task
- Real Secret Manager adapter (v2)

---

# Acceptance Criteria

- [ ] `./gradlew :apps:inbound-service:build` passes cleanly
- [ ] Service boots against Docker Compose (Postgres + Kafka + Redis) and
  `./gradlew bootRun`
- [ ] `GET /actuator/health` returns `200 UP` when all dependencies are
  reachable
- [ ] All 7 Flyway migrations apply cleanly on boot (V1–V7), plus V99 under
  `dev`
- [ ] Append-only role grants: `UPDATE`/`DELETE` rejected for app role on
  `inbound_outbox`, `inbound_event_dedupe`, `erp_webhook_dedupe`,
  `putaway_confirmation`; verified by Testcontainers test asserting
  rejection on app-role connection
- [ ] All 6 master snapshot consumers upsert correctly on `*.created` /
  `*.updated` / `*.deactivated` / `*.reactivated`; verified by
  Testcontainers Kafka tests
- [ ] `MasterLotConsumer` handles `master.lot.expired` → `status=EXPIRED`
- [ ] DB version guard: a `master.location.updated` event with
  `master_version <= cached` is silently ignored (no upsert, no error); a
  later event with higher version applies
- [ ] `EventDedupePort.process(eventId, eventType, runnable)` inserts and
  invokes the runnable on first call; on second call with same `eventId`
  returns `IGNORED_DUPLICATE` without re-executing the runnable
- [ ] EventDedupe write + master snapshot upsert are in the **same**
  `@Transactional` boundary (`Propagation.MANDATORY` on the dedupe adapter)
- [ ] DLQ routing: an unparseable master event is routed to
  `wms.master.location.v1.DLT` after 3 retries; test verifies the DLT
  receives the message
- [ ] `IdempotencyStore` Redis adapter: `store(key, response)` persists;
  `get(key)` retrieves within TTL; literal key shape matches
  `inbound:idempotency:{method}:{path_hash}:{key}` (pinned by an
  `IdempotencyStoreTest` assertion, mirroring the TASK-BE-025 pattern)
- [ ] Webhook controller smoke: valid HMAC + timestamp + new event-id →
  200 `accepted`, row in `erp_webhook_inbox` with `status=PENDING`, row in
  `erp_webhook_dedupe`
- [ ] Webhook controller failure-modes (per
  `webhooks/erp-asn-webhook.md` § Failure-mode Test Cases): all 12 cases
  pass — the table-driven test set is the gate
- [ ] Webhook controller does NOT enforce JWT (anonymous route); SecurityConfig
  uses `permitAll()` for `/webhooks/erp/asn`
- [ ] Inbox processor stub: a PENDING row gets `status=APPLIED` on the next
  scheduler tick (verified via Testcontainers test with disabled scheduler
  → manually-triggered process call); no Asn row created (stub behavior)
- [ ] JWT roles loaded correctly; a request to a future-protected endpoint
  without a bearer token returns `401` (verified via `@WebMvcTest` stub
  endpoint)
- [ ] `MasterReadModelPort` methods return empty Optional when not in cache
  for any of the 6 entities
- [ ] Structured logs contain `traceId`, `eventId`, `consumer` in MDC on
  every consumer log line
- [ ] Webhook ingest log contains `eventId`, `source` but NOT raw body or
  signature value
- [ ] Unit tests for HMAC verification, timestamp window, body hash
  canonicalization pass
- [ ] All Testcontainers tests pass in CI (Linux); Windows dev uses
  `@Testcontainers(disabledWithoutDocker=true)` where needed

---

# Related Specs

> **Before reading Related Specs**: Follow `platform/entrypoint.md` Step 0
> — read `PROJECT.md`, then load `rules/common.md` plus `rules/domains/wms.md`,
> `rules/traits/transactional.md`, `rules/traits/integration-heavy.md`.

- `platform/architecture.md`
- `platform/architecture-decision-rule.md`
- `platform/shared-library-policy.md`
- `platform/testing-strategy.md`
- `platform/service-types/rest-api.md`
- `platform/service-types/event-consumer.md`
- `platform/security-rules.md` — Secret Manager / env-var fallback
- `specs/services/inbound-service/architecture.md`
- `specs/services/inbound-service/domain-model.md` — §6 EventDedupe, §5
  InboundOutbox, §7 ErpWebhookInbox, §8 ErpWebhookDedupe, §9 MasterReadModel
- `specs/services/inbound-service/idempotency.md` — §1 REST, §2 Webhook,
  §3 Kafka consumer
- `specs/services/inbound-service/external-integrations.md` — §1 ERP webhook,
  §2 Kafka, §3 PostgreSQL, §4 Redis, §5 Secret Manager
- `specs/services/inbound-service/state-machines/asn-status.md` — for ASN
  table column shape (status enum values)
- `rules/domains/wms.md` — W2 (append-only ledger), W6 (master ref integrity)
- `rules/traits/transactional.md` — T3 (outbox table), T8 (eventId dedupe)
- `rules/traits/integration-heavy.md` — I1–I3 (retry), I5 (DLQ), I6
  (webhook reception), I8 (model translation), I10 (test fakes)

# Related Skills

- `.claude/skills/backend/architecture/hexagonal/SKILL.md`
- `.claude/skills/backend/springboot-api/SKILL.md`
- `.claude/skills/backend/jwt-auth/SKILL.md`
- `.claude/skills/backend/observability-metrics/SKILL.md`
- `.claude/skills/backend/testing-backend/SKILL.md`
- `.claude/skills/database/schema-change-workflow/SKILL.md`
- `.claude/skills/database/migration-strategy/SKILL.md`
- `.claude/skills/messaging/idempotent-consumer/SKILL.md`
- `.claude/skills/messaging/event-implementation/SKILL.md`
- `.claude/skills/service-types/event-consumer-setup/SKILL.md`
- `.claude/skills/testing/testcontainers/SKILL.md`

---

# Related Contracts

- `specs/contracts/events/master-events.md` — §1 Warehouse, §2 Zone, §3
  Location, §4 SKU, §5 Partner, §6 Lot (all consumed for MasterReadModel)
- `specs/contracts/webhooks/erp-asn-webhook.md` — full webhook wire format
  (this task implements the receive side; payload schema validation derives
  from the field reference table)

---

# Target Service

- `inbound-service`

---

# Architecture

Follow:

- `specs/services/inbound-service/architecture.md` (Hexagonal, dual rest-api +
  event-consumer + webhook receiver)
- Both `platform/service-types/rest-api.md` and
  `platform/service-types/event-consumer.md` apply — documented exception in
  the architecture (`inbound-service` is a 3-surface receiver: REST API,
  event consumer, webhook receiver)

---

# Implementation Notes

- **DB role grants (W2)**: `V7__role_grants.sql` must contain `REVOKE UPDATE,
  DELETE ON inbound_outbox, inbound_event_dedupe, erp_webhook_dedupe,
  putaway_confirmation FROM <app_role>;` where `<app_role>` is the
  application user. Verification test must use the same role (not
  superuser).
- **`putaway_confirmation` is append-only** (per `domain-model.md` §4 "No
  updates after creation") — included in the role grant alongside the
  outbox/dedupe tables.
- **EventDedupePort signature**: `Outcome process(UUID eventId, String
  eventType, Runnable runnable)` returning `APPLIED` or `IGNORED_DUPLICATE`.
  The adapter declares `@Transactional(propagation = Propagation.MANDATORY)`
  to ensure the dedupe row joins the caller's TX. Lift directly from
  inventory-service's `EventDedupePersistenceAdapter`.
- **Version guard for snapshots**: use `ON CONFLICT (id) DO UPDATE SET ...
  WHERE snapshot.master_version < EXCLUDED.master_version`. No
  application-layer version check needed.
- **Outbox table**: create `inbound_outbox` in V5 (all columns per
  `domain-model.md §5`). The publisher process is **not** implemented in
  this task — the table is created and the `OutboxWriter` port stub exists.
- **Consumer group**: `spring.kafka.consumer.group-id=inbound-service`.
- **Webhook secret resolution**: `WebhookSecretPort.resolveSecret(String
  source)` returns `Optional<String>`. v1 implementation reads
  `ERP_WEBHOOK_SECRET_<SOURCE_UPPER>` env var (e.g., `ERP_WEBHOOK_SECRET_ERP_PROD`).
  Missing env var → `Optional.empty()` → controller responds 401 (no secret
  to verify against).
- **HMAC verification**: use `MessageDigest.isEqual` for constant-time
  compare (per `webhooks/erp-asn-webhook.md` § Reference Implementation).
  The signature is computed over the **raw request body bytes** — capture
  via `ContentCachingRequestWrapper` or equivalent before Jackson parses.
- **Webhook controller TX**: dedupe insert + inbox insert must be in **one**
  `@Transactional` method. ON CONFLICT on dedupe row → skip inbox write,
  return 200 ignored_duplicate.
- **Inbox processor stub**: `@Scheduled(fixedDelay = 1000)`. Pulls up to 50
  PENDING rows, marks each APPLIED. Stub: just sets the status, doesn't
  call ReceiveAsnUseCase yet. The unit test uses `@TestPropertySource(properties = "inbound.webhook.inbox.processor.enabled=false")`
  to disable the scheduler and invoke the processor directly.
- **No Asn / Inspection / Putaway domain methods in this task** — the
  tables exist (V2–V4), but no domain code touches them. They're created
  here so future tasks don't need to bundle migrations with feature work.

---

# Edge Cases

- `master.location.deactivated` received before `master.location.created`
  (out-of-order startup) — consumer upserts with `status=INACTIVE`;
  subsequent `created` event has lower `master_version` and is ignored by
  version guard
- Duplicate `master.sku.updated` event (broker redelivery) —
  `EventDedupePort` returns `IGNORED_DUPLICATE`; no double upsert
- Redis unavailable at idempotency store write — fail closed: return `503`;
  do not skip idempotency (T1)
- Kafka unavailable at startup — consumer retries connection per Spring
  Kafka retry policy; health probe reflects `DOWN` until connected
- `lot_snapshot` for a lot whose SKU is not yet in `sku_snapshot` — insert
  is allowed; SKU snapshot arrives eventually via `master.sku.created`
- Webhook arrives during DB failover — fast-path TX fails → 503 with
  `SERVICE_UNAVAILABLE` per `external-integrations.md` §1.4; ERP retries
  with same `X-Erp-Event-Id`
- ERP signs body with secret for `erp-prod` but sends with `X-Erp-Source:
  erp-stg` — staging secret won't match → 401 `WEBHOOK_SIGNATURE_INVALID`
  (correct)
- Webhook payload is valid HMAC but the JSON body is `null` or empty —
  schema validation rejects with 422 `VALIDATION_ERROR` (does not write to
  inbox/dedupe)
- Inbox processor crashes mid-loop — TX rolls back per row; re-scan picks up
  unchanged PENDING rows on next tick

---

# Failure Scenarios

- **Postgres down at boot** — health `DOWN`; readiness probe fails; no
  traffic routed
- **Kafka down for master consumer** — consumer lag grows;
  `LocationSnapshot` becomes stale; future ASN/inspection logic relying on
  cached state proceeds with stale data (eventual consistency, documented
  in `domain-model.md §9`)
- **Redis down for idempotency** — fail closed with `503`; do not serve
  partial idempotency
- **Malformed master event in topic** — routed to DLT after 3 retries;
  alert triggered via DLT depth metric
- **V7 role grant migration fails** (insufficient DB privileges during
  Flyway) — boot fails; operator must grant `REVOKE` privilege to migration
  user
- **Webhook secret missing for declared source** — webhook returns 401;
  `inbound.webhook.received.total{result=signature_invalid}` increments;
  ops alerted via signature-invalid threshold
- **Inbox table grows unbounded** (processor failing or disabled) — gauge
  `inbound.webhook.inbox.pending.count` alerts at >100; ops investigates

---

# Test Requirements

## Unit Tests

- `EventDedupePort` adapter: first call applies runnable, second call skips
  — using in-memory repository fake. (Adapter integration is in slice
  tests below.)
- 6 master consumers: version-guard logic (lower version ignored, higher
  applied), status mapping (`deactivated` → `INACTIVE`, `reactivated` →
  `ACTIVE`, `master.lot.expired` → `EXPIRED`)
- HMAC verifier: valid signature passes, mismatch fails, uppercase hex fails,
  constant-time compare verified by metric (no fast-fail on first byte
  mismatch)
- Timestamp window verifier: in-window passes, ±5min boundary cases, out of
  window fails
- Body hash canonicalizer: order-independent, stable across whitespace

## Slice / Integration Tests

- Flyway migration test (Testcontainers Postgres): all 7 migrations apply;
  4 append-only tables reject UPDATE/DELETE on app-role connection
- All 6 master consumers (Testcontainers Kafka): publish `master.X.created`
  → snapshot upserted; publish same event again → dedupe skips; publish
  `master.X.deactivated` → status = INACTIVE
- DLT routing: publish a non-JSON message → verify it arrives on
  `wms.master.location.v1.DLT`
- Webhook controller (`@SpringBootTest` against Testcontainers Postgres):
  table-driven test covering all 12 cases from
  `webhooks/erp-asn-webhook.md` § Failure-mode Test Cases
- `IdempotencyStore` Redis adapter (Testcontainers Redis): literal key
  shape pinned with `assertThat(redis.keys("*")).contains("inbound:idempotency:POST:...")`
- Inbox processor stub: PENDING row → APPLIED after a manual processor
  invocation (scheduler disabled in test profile)

## Smoke Test

- `@SpringBootTest` with Testcontainers (Postgres + Kafka + Redis): boots,
  health UP, all migrations applied, all 6 consumers subscribe without
  error, webhook controller responds to a valid POST

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests added and passing
- [ ] Contracts not changed (no mutation endpoints yet)
- [ ] Specs not changed (bootstrap only)
- [ ] Ready for review
