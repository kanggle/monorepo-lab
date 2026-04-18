# Event-Driven Policy

Defines rules for asynchronous event communication between services. This document states platform-wide principles and formats. Concrete event registries (producer→consumer mappings) are declared per project in `specs/contracts/events/`, not here.

---

# Principles

- **Events represent facts that have already happened** (past tense). Example shape: `<Aggregate><PastTenseVerb>` (generic illustrations: `ResourceCreated`, `StateTransitioned`, `<Aggregate>Deactivated`). Concrete names are project-specific and defined in `specs/contracts/events/`.
- **Events are immutable** once published.
- **Producers own their events** and are responsible for the event contract.
- **Consumers must not assume ordering** unless explicitly guaranteed by the topic configuration (typically per-partition ordering when partition key = aggregate id).

---

# Broker

- Default message broker: **Apache Kafka**.
- Topic naming: `{domain}.{aggregate}.{version}` (e.g., `wms.master.sku.v1`, `ecom.order.v1`) OR `{service}.{aggregate}.{version}` — the project chooses one convention and documents it in its `specs/contracts/events/README.md`.
- Dead-letter topic per source topic: `<topic>.dlq`.

---

# Event Envelope Format

All events MUST follow this JSON envelope. Additional fields are allowed as long as the base fields are present.

```json
{
  "eventId": "string (UUID)",
  "eventType": "string",
  "eventVersion": 1,
  "occurredAt": "string (ISO 8601 UTC)",
  "source": "string (producing service name)",
  "aggregateType": "string",
  "aggregateId": "string",
  "traceId": "string",
  "actorId": "string or null",
  "payload": { }
}
```

| Field | Description |
|---|---|
| `eventId` | Unique per event. Consumers dedupe on this |
| `eventType` | Matches the naming convention declared by the project in `specs/contracts/events/README.md` (PascalCase or dot-separated, chosen per project) |
| `eventVersion` | Integer schema version for the `eventType`. Breaking changes bump this |
| `occurredAt` | Time the event occurred (commit time), not publish time |
| `source` | Producing service name as declared in `PROJECT.md` |
| `aggregateType` | Kind of aggregate (e.g., `order`, `sku`, `user`) |
| `aggregateId` | Aggregate instance id — used as Kafka partition key |
| `traceId` | OTel trace id from the triggering operation |
| `actorId` | JWT subject or `null` for system-originated events |
| `payload` | Event-specific data |

Serialization: JSON by default. Binary encoding (Avro, Protobuf) is allowed when the project chooses it and documents the choice in `specs/contracts/events/README.md`.

---

# Contract Rule

- **Every event MUST have a published contract** under `specs/contracts/events/`.
- Consumers implement their logic against the **published contract**, not the producer's internal model.
- **Breaking changes** require:
  1. A new `eventVersion` number, and
  2. A parallel topic (e.g., `<topic>.v2`) with a coexistence period during which both versions are published, and
  3. Consumer migration deadline documented in the contract.
- The project may instead choose to use a schema registry (Schema Registry, Apicurio) — this is declared in `specs/contracts/events/README.md`.

---

# Producer Rules

- **Transactional Outbox** pattern is required for any producer whose domain state change must stay consistent with event publication (see `rules/traits/transactional.md` T3). Events are written to an outbox table in the same DB transaction as the state change; a separate process forwards outbox rows to Kafka.
- Events MUST NOT be published from inside a database transaction that may still roll back (except via outbox, which is write-to-outbox, not write-to-broker).
- Publisher MUST retry broker failures with exponential backoff; rows are deleted from outbox only after broker acknowledgment.
- Publisher metrics: `outbox.pending.count`, `outbox.lag.seconds`, `outbox.publish.failure.total`.

---

# Consumer Rules

- **Idempotency is mandatory** — processing the same event twice MUST produce the same business state.
- **Deduplication strategy** (priority order):
  1. Natural idempotency (upsert by event-derived key)
  2. `eventId` dedupe table with TTL ≥ 24h
  3. Optimistic concurrency on the target aggregate
- **Consumer failures MUST NOT cause silent data loss** — use DLQ + alerting.

## Retry Policy (default)

| Parameter | Default | Notes |
|---|---|---|
| Max retries | 3 | Before sending to DLQ |
| Backoff strategy | Exponential with jitter | Base interval × 2^attempt + random jitter |
| Base interval | 1 second | First retry after ~1s, second after ~2s, third after ~4s |
| Max interval | 30 seconds | Cap |

Projects may override these defaults per consumer in `specs/services/<service>/architecture.md` under "Subscribed Topics".

## DLQ Policy

- DLQ topic naming: `<original-topic>.dlq`.
- DLQ messages MUST retain original envelope fields (`eventId`, `eventType`, `occurredAt`, `source`).
- Add error metadata to the DLQ message: `error_message`, `retry_count`, `failed_at`, `consumer_group`.
- DLQ depth > 0 MUST trigger an alert (see `observability.md`).
- **Manual replay**: DLQ messages can be replayed to the original topic after the root cause is resolved.

## Error Classification

| Error Type | Action |
|---|---|
| Transient (network timeout, DB connection, broker unavailable) | Retry with backoff |
| Deserialization failure (unknown schema version, malformed JSON) | Send to DLQ immediately (no retry) |
| Business rule violation (handler rejects the event) | Send to DLQ immediately (no retry) |
| Unknown / unhandled exception | Retry, then DLQ after max retries |

---

# Schema Versioning

- Additive changes (new optional field) keep the same `eventVersion`.
- Breaking changes (renamed/removed field, type change, semantic change) bump `eventVersion` AND publish on a new topic version (`<topic>.v<N>`).
- Producers MUST NOT reuse a topic version after removing it.
- See `cross-cutting/api-versioning.md` for deprecation timelines.

---

# Trace Propagation

- OTel context MUST propagate through Kafka headers (`traceparent`, `tracestate`) — use `KafkaPropagator` or equivalent.
- Each consumed event creates a span linked to the producer's span.
- `traceId` in the envelope is redundant with the OTel trace id but kept for human-readable correlation in logs.

---

# Ordering Guarantees

- Per-partition ordering is the only guarantee Kafka provides. Cross-partition ordering is **not** guaranteed.
- Partition key SHOULD be the aggregate id — this guarantees per-aggregate ordering.
- If cross-aggregate ordering is needed, the consumer must resolve order via `occurredAt` or aggregate version, not via arrival order.

---

# Project-Level Event Registry

The concrete list of producers, event types, and consumers is **project-specific** and lives in:

- `specs/contracts/events/<aggregate>.md` — one file per aggregate's event family, declaring schemas for every event it emits
- `specs/contracts/events/README.md` — index of all event contracts in the project, including a producer→consumer overview table
- `specs/services/<service>/architecture.md` — per-service, declares "Published Events" and "Subscribed Topics"

This document does **not** enumerate specific events. Platform rules only; concrete catalog in the project specs.

---

# Change Rule

- New event types or changes to existing events MUST update the contract in `specs/contracts/events/` before implementation.
- Breaking changes MUST follow the versioning protocol in this document.
- Platform-wide envelope changes (this document) require consensus across all projects using it and a migration plan.
