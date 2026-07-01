# ADR-MONO-004 — Shared Messaging Scaffolding in `libs/java-messaging`

**Status:** ACCEPTED
**Date:** 2026-05-10 (PROPOSED + ACCEPTED, same day, since the implementing PR delivers both the lib API and the first 3 service migrations atomically)
**Decision driver:** TASK-MONO-049. Three wms backend services (`outbound-service`, `inbound-service`, `inventory-service`) carry near-identical 150-line `OutboxPublisher` classes, and seven services across three projects (wms / GAP / scm) each carry their own `EventEnvelope` record + `EventEnvelopeParser` + `EventDedupePort` interface. New service onboarding repeatedly copy-pastes this scaffolding and small drifts (logging messages, metric naming, retry semantics) accumulate.
**Supersedes:** none — this is the first ADR establishing the boundary between transport scaffolding (allowed in `libs/`) and domain events (forbidden).
**Related:** [TASK-MONO-049](../../tasks/review/TASK-MONO-049-libs-java-messaging-outbox.md), [CLAUDE.md § Cross-Project Changes](../../CLAUDE.md), [platform/shared-library-policy.md](../../platform/shared-library-policy.md), [rules/traits/transactional.md](../../rules/traits/transactional.md) §T3 §T8, [ADR-MONO-003](ADR-MONO-003-phase-5-template-extraction-deferred.md) (D2 reset impact).

**Accepted Decisions:**

- **D1 = ACCEPT extraction of transport scaffolding into `libs/java-messaging`.** Generic publisher loop, row contract, envelope record + parser, dedupe port, MDC helpers, metrics interface — all transport-only, all project-agnostic, all reusable across every service that emits or consumes events.
- **D2 = REJECT moving domain event payload classes into `libs/`.** `*.event.*` packages stay per-service (e.g. `com.wms.master.domain.event.WarehouseCreatedEvent`). Per `shared-library-policy.md` § Forbidden, "service-specific domain logic" cannot live in shared libraries; an envelope's `payload` is a `JsonNode` so the contract never sees the domain type.
- **D3 = ADOPT incremental migration (3-of-N services per PR), not big-bang.** TASK-MONO-049 migrates `outbound-service`, `inbound-service`, `inventory-service` (the three wms big services that share the most-evolved publisher pattern). Master-service already uses the existing `OutboxPollingScheduler` base in libs; remaining services (admin-service, GAP services, ecommerce services) are deferred per § Migration Path below.
- **D4 = ACCEPT D2 churn-clock reset for ADR-MONO-003.** This PR touches `libs/java-messaging` (mandatory) and `platform/shared-library-policy.md` + `.claude/skills/messaging/*` (mandatory). The `last_churn` marker resets to **2026-05-10**; next Phase 5 readiness re-evaluation is deferred to **≥ 2026-06-09**. Acceptable trade-off given the cross-project value (300+ lines of duplicated publisher code removed, single source of truth for envelope shape).

---

## 1. Context

### 1.1 Duplication observed across the monorepo

`/refactor-code wms outbound-service` (May 2026) surfaced an identical 150-line publisher class across `outbound-service`, `inbound-service`, and `inventory-service`:

| Concern | Implementation | Drift across services |
|---|---|---|
| Polling loop with `TransactionTemplate` | `publishPending()` | identical |
| Exponential backoff (1s → 30s, multiplier 2) | `nextDelayMillis()` | identical |
| `ProducerRecord` with `eventId` / `eventType` headers | `publishOne()` | identical |
| Mark-as-published in fresh transaction | `transactionTemplate.executeWithoutResult` | identical |
| Pending-rows query (`publishedAt IS NULL` ASC by created) | `findPending(Pageable)` | identical (different entity types) |
| Pending-count gauge | `Gauge.builder("<svc>.outbox.pending.count" …)` | identical pattern, prefix differs |
| Failure counter | `Counter.builder("<svc>.outbox.publish.failure.total" …)` | identical pattern, prefix differs |
| Lag timer | `Timer.builder("<svc>.outbox.lag.seconds" …)` | identical pattern, prefix differs |

Beyond the publisher: 5 services (`outbound-service`, `inbound-service`, `inventory-service`, `master-service`, `admin-service`) each carry their own `EventEnvelope` record and `EventEnvelopeParser`/`EventEnvelopeSerializer`. The on-wire shape is identical (per `specs/contracts/events/event-envelope.schema.json`) but each service re-implemented the serializer.

3 services (`inventory-service`, `inbound-service`, `scm/inventory-visibility-service`) carry an identical `EventDedupePort` interface with the same 3-element `Outcome` enum.

### 1.2 Why the existing libs scaffolding wasn't enough

`libs/java-messaging` already had `OutboxPublisher` (generic poll-loop with `EventSender` lambda), `OutboxPollingScheduler` base class, `OutboxJpaEntity` (a single shared schema), `OutboxWriter`, `BaseEventPublisher`. **Master-service uses it directly** (subclass `MasterOutboxPollingScheduler`).

But the wms big-3 (outbound, inbound, inventory) evolved beyond:

- their tables are JSONB (not TEXT), with `partition_key`, `event_version`, UUID PK (not BIGSERIAL)
- they want explicit metrics tags (`event_type`) per record
- they want backoff-and-skip semantics, not just "stop the batch"
- they need `ProducerRecord` headers carrying `eventId` / `eventType` for downstream consumers' fast-path filters

The existing scaffolding was designed for the simpler v1 pattern (master-service); the wms big-3 represents a v2 evolution. Extracting v2 as `AbstractOutboxPublisher<R extends OutboxRow>` lets both v1 and v2 callers coexist without forcing a schema migration.

### 1.3 Project-agnosticism check

The Hard Stop boundary in `CLAUDE.md`:

> A shared library file (under `platform/`, `rules/`, `.claude/`, `libs/`, `tasks/templates/`, `docs/guides/`) contains project-specific content (service names, API paths, domain entities) — the Library vs Project boundary is broken

A `grep` against the new lib code (excluding generic-English uses of "inbound" in the `EventDedupePort` javadoc) returns zero hits for `wms`, `gap`, `ecommerce`, `outbound`, `master`, `admin`, `fan-platform`, `scm`. Service-specific references in javadoc were generalised to `<service>` placeholders.

---

## 2. Decision

### 2.1 What moves to `libs/java-messaging` (D1)

**New public API surface in `com.example.messaging`:**

| Type | Package | Purpose |
|---|---|---|
| `OutboxRow` (interface) | `outbox` | Contract for a single outbox row. Per-service entities implement this so the generic publisher can drive any table. |
| `OutboxRowEntity` (`@MappedSuperclass`) | `outbox` | Reference JPA mapping for services that don't already have a custom entity. |
| `OutboxRowRepository<R>` (interface) | `outbox` | Narrow repository surface consumed by the publisher (`findPending`, `findById`, `save`, `countPending`). |
| `SpringDataOutboxRowRepository` (helper) | `outbox` | Wraps a Spring Data `JpaRepository` into the publisher contract. |
| `TopicResolver` (interface) | `outbox` | Strategy for `eventType → topic` mapping; per service. |
| `AbstractOutboxPublisher<R>` | `outbox` | Generic poll loop with `TransactionTemplate`, exponential backoff, `ProducerRecord` headers. Subclasses supply `@Scheduled` annotation. |
| `OutboxMetrics` (interface) | `outbox` | Observability contract for publish success / failure / lag. |
| `MicrometerOutboxMetrics` | `outbox` | Reference Micrometer impl with per-service prefix. |
| `EventEnvelope` (record) | `envelope` | Canonical 10-field envelope record (eventId, eventType, eventVersion, occurredAt, producer, aggregateType, aggregateId, traceId, actorId, payload). |
| `EventEnvelopeParser` (`@Component`) | `envelope` | Parses raw JSON to `EventEnvelope`; throws `IllegalArgumentException` on malformed input. |
| `EventDedupePort` (interface) | `dedupe` | T8 idempotent-consumer contract with `Outcome { APPLIED, IGNORED_DUPLICATE, FAILED }`. |
| `MessagingMdc` (helper) | `mdc` | Try-with-resources MDC helpers for `traceId`, `eventId`, `consumerLabel`. |

The existing classes (`OutboxPublisher` v1 with `EventSender` lambda, `OutboxPollingScheduler`, `OutboxJpaEntity`, `OutboxWriter`, `ProcessedEventJpaEntity`, `BaseEventPublisher`) are **retained** so master-service and other v1 callers keep working without migration.

### 2.2 What stays per-service (D2)

- `*.domain.event.*` event payload classes (e.g. `WarehouseCreatedEvent`, `OrderReceivedEvent`) — these are domain types; the lib's `EventEnvelope.payload` is `JsonNode` so the lib never sees them.
- Per-service `EventEnvelopeSerializer` classes that walk the domain event hierarchy with pattern-matching `switch` — too tied to per-service event taxonomy to generalise.
- Per-service consumer-side records (e.g. `ProjectionEnvelope`, custom `EventEnvelope` with `sourceTopic` / `aggregateId : UUID` shape) — kept until contract stabilises in a future v2 ADR.
- Per-service outbox writer classes (`OutboxWriterAdapter`) that build the row from domain types.
- Per-service `EventDedupePort` *implementations* — the `@JdbcTypeCode(SqlTypes.JSON)` attributes, retention-cleanup schedulers, and tenant-scoping vary between services.

### 2.3 Migration path (D3)

This PR (TASK-MONO-049) migrates **3 services**:

| Service | What changes |
|---|---|
| `wms-platform/apps/outbound-service` | `OutboundOutboxEntity` implements `OutboxRow`. `OutboxPublisher` becomes a 50-line subclass of `AbstractOutboxPublisher`. ~150 lines of duplicated publisher code removed. |
| `wms-platform/apps/inbound-service` | `InboundOutboxJpaEntity` implements `OutboxRow`. Same publisher swap. |
| `wms-platform/apps/inventory-service` | `InventoryOutboxJpaEntity` implements `OutboxRow`. Same publisher swap (preserves `inventory.low-stock-detected → wms.inventory.alert.v1` special case). |

**Deferred** (separate follow-up tasks recommended):

- `wms-platform/apps/master-service` — already uses lib's v1 `OutboxPollingScheduler`; migrating to v2 (`AbstractOutboxPublisher`) is mechanical but cosmetic. Not gated by anything functional.
- `wms-platform/apps/admin-service` — has `AdminEventDedupeRepository` with richer LWW (last-write-wins) semantics + `LifetimeCounts` reporting that the lib's `EventDedupePort` does not encompass. Either (a) extend `EventDedupePort` with a `Outcome.APPLIED_LATE` case or (b) leave admin's repository per-service. Decision deferred.
- `global-account-platform/apps/auth-service` (and 5 other GAP services) — uses `BaseEventPublisher` (lib v1); migrating to the publisher V2 pattern requires a schema review of `outbox_events` columns vs `OutboxRow` contract. Deferred.
- `ecommerce-microservices-platform` services — 5 services (`order-service`, `promotion-service`, `review-service`, `shipping-service`, `payment-service`) use `OutboxPollingScheduler` extension (lib v1). Same migration shape as master-service; deferred for the same reason.
- `scm-platform/apps/inventory-visibility-service` — uses its own `EventDedupePort`. Mechanical migration to lib's identical-shape `EventDedupePort` is safe but not bundled here.
- `fan-platform` services — use `BaseEventPublisher` (lib v1). Deferred.

The deferred set is **explicitly** not in scope for this PR. Each follow-up task can be implemented independently because the lib v1 API stays in place. Atomic-PR rule per `CLAUDE.md` § Cross-Project Changes is satisfied for the 3-service set chosen here; the rule applies to the changes shipped in one PR, not to the universe of theoretically-related future migrations.

### 2.4 Boundary policing

Following the Hard Stop pattern in `CLAUDE.md`:

```
grep -rE "wms|gap|ecommerce|outbound|inbound|inventory|master|admin|fan-platform|scm" \
  libs/java-messaging/src/main/java/
```

Must return **zero matches** beyond:
- generic English uses of "inbound" / "inbound event" in javadoc (allowable; the words mean "incoming")
- the pre-existing `BaseEventPublisher.java` javadoc reference to "account and admin publishers" (introduced before this ADR; cleanup deferred to a follow-up cleanup task, NOT bundled here per the no-fix-while-here rule)

Per-PR review enforces this manually until automated in `scripts/verify-template-readiness.sh` Check 1.

---

## 3. Alternatives Considered

| Alternative | Why rejected |
|---|---|
| **Keep per-service publishers (status quo)** | 150 lines × 3 → 450 lines duplicated; every drift becomes a 3-PR fix. Onboarding new services requires copy-paste. |
| **"Contrib" module pattern (each service publishes its own publisher to a `libs/contrib/<service>` namespace)** | Inverts the dependency direction: `libs/` would depend on `projects/<name>`. Direct violation of `shared-library-policy.md` § Dependency Rule. |
| **Code generation (apt processor / KSP that emits per-service publisher subclass)** | Adds a build-time toolchain dependency that no other lib in the monorepo uses. Justifiable only if the duplication count grows beyond ~10 services; at 3, hand-written subclasses are simpler and reviewable. |
| **Move domain events into `libs/` too (full migration)** | Crosses the bounded-context line. Forces every service depending on `libs/java-messaging` to also depend on every domain's event package, which couples unrelated services through the library. Direct violation of `shared-library-policy.md` § Forbidden. |
| **Single shared `outbox` table for all services (lib owns the schema)** | Per-service tables matter for incident isolation (one service's runaway publisher cannot starve another's), Flyway ownership, tenant scoping. Per-table-per-service is preserved. |

---

## 4. Consequences

### 4.1 Code surface

- **Removed**: ~450 lines of publisher duplication across the 3 migrated services. ~20 lines removed per service after subclass-based wiring.
- **Added** in `libs/java-messaging`: 8 new types + 3 unit-test files (12 tests, all passing).
- **Net**: lib gains ~600 LOC, services lose ~450 LOC; net +150 LOC monorepo-wide, but with a single source of truth.

### 4.2 Test coverage

- Lib unit tests: 39 / 39 pass (12 new + 27 existing). No integration tests added — the publisher loop is unit-testable with an in-memory repo and a mocked `KafkaTemplate`.
- Per-service unit tests: outbound-service / inbound-service / inventory-service all green after migration.
- Per-service integration tests: not run locally per the Rancher Desktop blocker (memory `project_testcontainers_docker_desktop_blocker.md`); CI Linux runner is the IT verifier.

### 4.3 Backward compatibility

- `OutboxPublisher` (v1, `EventSender` lambda), `OutboxPollingScheduler`, `OutboxJpaEntity`, `OutboxWriter`, `ProcessedEventJpaEntity`, `BaseEventPublisher`, `OutboxFailureHandler`, `OutboxMetricsAutoConfiguration` — all retained. Master-service, GAP services, ecommerce services, fan-platform services, scm services continue working without code change.
- The `outbox` Spring Data JPA table managed by `OutboxJpaConfig` is unaffected; it's a different table from the per-service `<service>_outbox` tables that now implement `OutboxRow`.

### 4.4 D2 churn impact (ADR-MONO-003)

This PR resets the shared-library churn clock:

- **Last shared-library churn before this PR**: 2026-05-09 (BE-273 Phase 2, `libs/java-common` HTTP/1.1 force).
- **Last shared-library churn after this PR**: 2026-05-10 (this PR — `libs/java-messaging`, `platform/shared-library-policy.md`, `.claude/skills/messaging/*`).
- **Phase 5 (Template extraction) re-evaluation deferred**: `≥ 2026-06-09 + 1 day` per ADR-MONO-003 D4 30-day churn-freeze.

The trade-off is justified: this is a structural change that removes duplication permanently, not a fix-while-here. Future Template extraction inherits the cleaner library, which improves the `verify-template-readiness.sh` Check 3 outcome (less drift to filter).

### 4.5 Forward compatibility

Future V2 work (separate ADRs):

- Schema-registry-backed envelope (Avro / Protobuf) — the `EventEnvelope` record's `payload : JsonNode` would become a typed wrapper. Migrating callers is a single-record-type substitution.
- Compaction-keyed topics — the `TopicResolver` interface already supports per-row routing; adding compaction is a configuration concern, not a lib API change.
- Bulk publish (multiple rows per Kafka batch) — `AbstractOutboxPublisher.publishOne` could be augmented with a `publishBatch` hook in a backward-compatible way (default: call `publishOne` in a loop, as today).

---

## 4.6 Batch Resilience amendment (TASK-MONO-050, 2026-05-11)

Surfaced by TASK-BE-136 (PR #345, payment-service outbox migration) self-review: the v1 `OutboxPublisher.publishPendingEvents` loop calls `break` on the first row whose `EventSender.send` returns `false`. A single poison-pill row (e.g. unknown `event_type` for which no subclass's `resolveTopic` matches) returns `false` permanently; on every subsequent polling cycle, `findPendingWithLock` returns the same row first and the loop breaks again → the entire batch stalls indefinitely until manual operator intervention.

**Amendment** (TASK-MONO-050, lands together with this ADR update):

- `OutboxPublisher.EventSender.send` reshaped from `boolean` to a 3-value `SendOutcome` enum (`SUCCESS` / `FAILURE_TRANSIENT` / `FAILURE_PERMANENT`).
- `OutboxPollingScheduler.sendToKafka` classifies exceptions:
  - `IllegalArgumentException` from `resolveTopic` → `FAILURE_PERMANENT`
  - `EventSerializationException` from envelope path → `FAILURE_PERMANENT`
  - Any other `Exception` (broker timeout, `KafkaException`, etc.) → `FAILURE_TRANSIENT`
- `OutboxPublisher.publishPendingEvents` dispatches per outcome:
  - `SUCCESS` → `row.markPublished()` + continue
  - `FAILURE_TRANSIENT` → keep `PENDING` + `return` (preserve retry-storm avoidance against broker-wide outages)
  - `FAILURE_PERMANENT` → `row.markFailed()` + continue (drain remainder; `findPendingWithLock` filters on `status='PENDING'` so the FAILED row is naturally excluded from future polls)
- New `OutboxJpaEntity.markFailed()` (status → `FAILED`, `publishedAt` → terminal timestamp). Failure reason is captured at the call site via `log.error` (eventType + aggregateId), not persisted on-row — avoids requiring `outbox.failure_reason` column migration across the 13 existing service Flyway schemas. Operators correlate FAILED rows with logs by `eventType` + `aggregateId` + `publishedAt`.
- New `OutboxPollingScheduler.onPermanentFailure(eventType, aggregateId, exception)` hook (default = log-only). Subclasses can opt in to publish to a dead-letter topic or fire an alert.

**API breaking surface (intentional, semver not applicable to internal lib):**

- `EventSender.send` signature change forces compile error on any subclass that overrides `sendToKafka` directly (none in the current 13 subclasses — they all only override `resolveTopic` + `onKafkaSendFailure`, so blast radius is zero).
- `OutboxJpaEntity.markFailed(String reason)` → `markFailed()` (no-arg) — the reason argument was never persisted; replaced by call-site logging.

**Verification:** `:libs:java-messaging:test` 45/45 pass (4 new outcome-dispatch + 4 new classification tests + 1 existing rewrite). `order-service` + `payment-service` (the two services with direct `EventSender` test invocations) regress green (260/260 + 98/98). Other 11 subclasses verified by `compileJava`; their unit tests deferred to CI Linux runner (per `feedback_refactor_code_baseline_it.md` Windows paging blocker).

---

## 4.7 Poller Lock-Contention amendment (TASK-MONO-211, 2026-06-10)

Surfaced by TASK-MONO-207 (federation-e2e spec flakiness) and pinned by TASK-MONO-210 (deterministic 500 on a write-heavy admin e2e): `OutboxPublisher.publishPendingEvents` is `@Transactional` at the default **REPEATABLE READ**, and `OutboxJpaRepository.findPendingWithLock` is a `SELECT … WHERE status='PENDING' … FOR UPDATE`. Under REPEATABLE READ that `FOR UPDATE` takes **next-key/gap locks over the PENDING range**, and because the Kafka publish (`kafkaTemplate.send(...).get()`, synchronous) runs *inside* the same transaction while the lock is held, a slow/warming broker makes the poller hold those gap locks for the duration of the blocking `.get()`. Concurrent **business `INSERT`s into `outbox`** (emitted by any domain mutation) then block on the gap lock for up to `innodb_lock_wait_timeout` (50s) → MySQL `1205` → `PessimisticLockingFailureException` → the business write 500s. The compose-log dump in the MONO-210 run showed exactly this (`outbox` INSERT lock-wait while the poller held its lock during a cold-stack Kafka publish).

**Amendment** (TASK-MONO-211, lands together with this ADR update):

- `OutboxPublisher.publishPendingEvents` is annotated `@Transactional(isolation = Isolation.READ_COMMITTED)`. Under READ COMMITTED InnoDB does **not** take gap locks — `SELECT … FOR UPDATE` locks only the rows it actually matches/returns, never the gaps — so a concurrent business INSERT of a new PENDING row is no longer blocked by the poller. A slow Kafka now only degrades **poller throughput** (its own batch transaction waits on `.get()`), the intended degrade mode, instead of failing unrelated business writes.

**Semantics preserved (no consumer-visible change):**

- **Single-poller exclusivity / no double-publish** — the `FOR UPDATE` still row-locks the claimed PENDING rows for the transaction's duration; only the *gap* locks (which blocked INSERTs) are dropped.
- **At-least-once** — a row is marked `PUBLISHED` only after the broker ACK, in the same transaction; a failure leaves it `PENDING` for the next poll.
- **FIFO** — `ORDER BY created_at` is unchanged; the batch is read once then written (no re-read), so READ COMMITTED's weaker repeatable-read guarantee is irrelevant to correctness here.
- The § 4.6 batch-resilience dispatch (SUCCESS/TRANSIENT/PERMANENT) is byte-unchanged.

**Alternatives considered (deferred):**

- **claim-then-publish** (mark rows `CLAIMED` in a short tx → commit/release locks → publish outside any tx → mark `PUBLISHED`): the fully structural fix (no lock held across blocking I/O at all), but introduces a new `CLAIMED` state + crash-recovery semantics across all 13+ service schemas. READ COMMITTED removes the observed contention with a one-line, schema-free, semantics-preserving change; claim-then-publish remains the future option if multi-instance throughput demands it.
- **`SELECT … FOR UPDATE SKIP LOCKED`**: only helps when *multiple* poller instances contend for the same rows; today each service runs a single `@Scheduled` poller, and SKIP LOCKED does not remove the gap locks that block business INSERTs. Orthogonal to this fix; revisit alongside horizontal poller scaling.

**Affected blast radius:** the change is in the shared `libs/java-messaging` poller, so it applies to every service's outbox uniformly. It is isolation-scoped to the poll transaction only — business transactions keep their own default isolation.

**Verification:** `:libs:java-messaging:test` green incl. a new reflection guard asserting `publishPendingEvents` stays `@Transactional(READ_COMMITTED)`; behavioural proof via the federation-hardening-e2e workflow (the MONO-207 + MONO-210 specs run without the lock-wait 500). The MONO-210 spec's `beforeAll` warm-up gate stays as defence-in-depth.

---

## 5. Verification

- `./gradlew :libs:java-messaging:test` — 39/39 PASS (12 new + 27 existing).
- `./gradlew :projects:wms-platform:apps:outbound-service:test` — PASS.
- `./gradlew :projects:wms-platform:apps:inbound-service:test` — PASS.
- `./gradlew :projects:wms-platform:apps:inventory-service:test` — PASS.
- `./gradlew :projects:wms-platform:apps:master-service:test :projects:wms-platform:apps:admin-service:test` — PASS (regression check; these services not migrated but share the lib).
- `./gradlew :projects:wms-platform:apps:notification-service:test :projects:wms-platform:apps:gateway-service:test` — PASS.
- Hard Stop boundary `grep` — clean (zero genuine service-name leaks; only generic-English "inbound" plus the pre-existing `BaseEventPublisher.java` javadoc reference noted in § 2.4).
- Integration test verification deferred to CI Linux runner per the Rancher Desktop blocker; the CI matrix is the authoritative verifier for the 3 migrated services' IT suites.

---

## 6. Outstanding follow-ups

| Follow-up | Trigger | Owner |
|---|---|---|
| ~~Migrate `master-service` to `AbstractOutboxPublisher` v2~~ — **DONE (TASK-BE-438)**: V8 `master_outbox` (UUID PK + `occurred_at` + `retries`/`last_error`), `MasterOutboxPublisher extends AbstractOutboxPublisher`, write-path + metrics cutover. Was labelled "cosmetic" but was in fact a schema migration + write-path rewrite. | ~~Cosmetic; defer until next bundle~~ | ~~wms backlog~~ |
| ~~Migrate `finance-platform/account-service` to `AbstractOutboxPublisher` v2~~ — **DONE (TASK-FIN-BE-045)**: V2 `account_outbox` (CHAR(36) UUID PK), `AccountEventPublisher` split into a port + `OutboxAccountEventPublisher` impl, `AccountOutboxPublisher extends AbstractOutboxPublisher`, lib auto-configs excluded, metrics swap. Wire (topics + 7-field envelope) preserved exactly. **finance-platform is now 100% v2** (ledger-service was already v2 via TASK-FIN-BE-009). | ~~per-project backlog~~ | ~~finance backlog~~ |
| ~~**ecommerce services to v2**~~ — **DONE — ecommerce is now 100% v2** (TASK-BE-444 promotion, -445 review, -446 shipping, -447 settlement, -448 order, -449 payment; PRs #1987–#1992). Each: `V<n>__<svc>_outbox` (UUID PK + `occurred_at` + `retries`/`last_error`, mirror `master_outbox`), write-path rewritten to persist the v2 row directly + `<Svc>OutboxPublisher extends AbstractOutboxPublisher`, metrics swap, lib `OutboxAutoConfiguration` **retained** (v1 `outbox`/`processed_events` kept for EntityScan validate — the ecommerce keep-auto-config stance, vs the account exclude stance). Wire (topics verbatim incl. shipping's mixed `.v1`/no-suffix set + envelope payload byte-identical + key=aggregateId) preserved exactly; order/payment custom `event_publish_failure_total` preserved by wrapping the lib `OutboxMetrics`. **Correction:** the ecommerce producers use the lib **`OutboxWriter`** write path (NOT `BaseEventPublisher`) — a single-axis swap, simpler than master/account. order + payment CI-IT-lane verified (Testcontainers); the other four are unit + CI-validated-pattern-mirror (no ecommerce IT lane for them). | ~~Schema review per service~~ | ~~per-project backlog~~ |
| ~~Migrate GAP (now **iam-platform**) services + fan-platform services off `BaseEventPublisher` to v2 (dual-axis: v1 `OutboxPollingScheduler` relay + `BaseEventPublisher` write path)~~ — **DONE.** **fan-platform 100% v2** (TASK-FAN-BE-020 membership, -021 community, -022 artist; PR #2001). **iam-platform 100% v2** (TASK-BE-450 auth, -451 account, -452 admin, -453 security, -454 membership, -455 community; bundled PR #2003 squash `c0800f557`). Each: new `<svc>_outbox` (MySQL `CHAR(36)` for auth/account/admin, Postgres for the rest), v1 concrete publisher → port + `Outbox<Svc>EventPublisher` impl, `<Svc>OutboxPublisher extends AbstractOutboxPublisher`; lib `OutboxAutoConfiguration` retained (keep-auto-config stance). Wire preserved byte-for-byte per publisher (envelope vs flat vs self-built-envelope determined individually). Both CI-IT-lane verified (Integration (iam/fan, Testcontainers) GREEN). | ~~Schema review per service~~ | ~~per-project backlog~~ |
| ~~Migrate `scm/inventory-visibility-service.EventDedupePort` to lib's `EventDedupePort`~~ — superseded by full **scm-platform 100% v2** (TASK-SCM-BE-032 procurement, PR #1997) + **erp-platform 100% v2** (TASK-ERP-BE-025 approval, -026 masterdata, PR #1999), both dual-axis (MySQL CHAR(36)). | ~~Trivial rename~~ | ~~scm backlog~~ |
| **✅ ADR-MONO-004 § 6 v1→v2 outbox sweep COMPLETE (2026-06-27).** Every platform is now 100% v2: wms · finance · ecommerce · scm · erp · fan · iam. The lib v1 path (`OutboxPublisher`/`OutboxPollingScheduler`/`BaseEventPublisher`/`OutboxWriter`) is retained only as abandoned-on-cutover `ddl-auto=validate` stubs (no live v1 producer or relay remains in any service). | — | — |
| ~~Decide admin-service `AdminEventDedupeRepository` fate (extend lib `EventDedupePort` with `APPLIED_LATE` or keep custom)~~ — **RESOLVED: keep custom (TASK-MONO-321, 2026-07-01).** The two interfaces serve different purposes: the lib `EventDedupePort` is a simple run-once wrapper (`process(eventId, type, Runnable) → {APPLIED, IGNORED_DUPLICATE, FAILED}`), whereas `AdminEventDedupeRepository` is a **projection-specific** contract with out-of-order LWW handling (`markStale` / `IGNORED_DUPLICATE_LATE`), lifetime aggregate counts (`countLifetime` — powers `/operations/projection-status`) and lag-probe queries (`maxProcessedAtByEventType`). Folding those admin-projection concerns into the shared lib port would bloat the shared contract with service-specific semantics (shared-library-policy boundary, HARDSTOP-03-adjacent). No code change. | ~~Architectural call~~ | ~~wms admin-service backlog~~ |
| ~~Cleanup pre-existing javadoc reference in `BaseEventPublisher.java` ("account and admin publishers")~~ — **OBSOLETE (TASK-MONO-321, 2026-07-01): `BaseEventPublisher.java` was removed by TASK-MONO-312 (lib outbox v1 dead-code removal).** No file remains to clean; the follow-up is void. | ~~Trivial; bundle with the next libs/java-messaging PR~~ | ~~shared backlog~~ |
