# Event Contracts — finance-platform

Index of this project's event contracts and the project's declared choices for the five decisions [`platform/event-driven-policy.md`](../../../../../platform/event-driven-policy.md) delegates to `specs/contracts/events/README.md`. This file states what the code does today; it does not restate the platform rule body.

**Source of this census**: live code (outbox publisher classes, topic/eventType constants), read alongside the 2 contract files below, TASK-MONO-415 (2026-07-15).

---

## 1. Topic Naming

**Consistent** across both producers (account-service, ledger-service): `finance.<domain>.<action>[.<action2>].v1` — dot-separated, lowercase, `.v1` suffix, matching spec exactly.

Examples: `finance.account.opened.v1`, `finance.balance.captured.v1`, `finance.transaction.completed.v1`, `finance.ledger.entry.posted.v1`, `finance.ledger.reconciliation.discrepancy.detected.v1`.

## 2. `eventType` Naming

**Consistent.** Dot-separated lowercase, equal to the topic minus `.v1`: `finance.account.opened`, `finance.transaction.completed`, `finance.ledger.entry.posted`. (Java constant names are SCREAMING_SNAKE_CASE, e.g. `EVENT_ACCOUNT_OPENED`, but the value on the wire is always the dotted string.)

## 3. Serialization

**JSON**, via Jackson (`ObjectMapper.writeValueAsString`) in both `OutboxAccountEventPublisher.java` and `OutboxLedgerEventPublisher.java`. `KafkaTemplate<String, String>`. No `.avsc`/`.proto` files anywhere in the project.

## 4. Schema Registry

**Not used.** No `schema.registry.url`, `SchemaRegistryClient`, or Apicurio config in either service's application config.

## 5. Envelope Shape (informational)

**Diverged between the two producers — a live, unresolved discrepancy:**

- `account-service` emits a **7-field** envelope: `{eventId, eventType, source, occurredAt, schemaVersion, partitionKey, payload}`. Its own spec doc (`finance-account-events.md`) documents a different, 9-field shape including `tenantId`/`aggregateType`/`aggregateId`/`traceId` — the code comment explicitly states the 7-field shape is deliberately preserved from the legacy `BaseEventPublisher` wire format, so the spec doc is stale on this axis.
- `ledger-service` emits an **8-field** envelope: `{eventId, eventType, occurredAt, tenantId, source, aggregateType, aggregateId, payload}` — this one matches its own spec doc (`finance-ledger-events.md`) verbatim (minus `traceId`, which neither service emits).

This is recorded as-is per AC-2 — forcing it into one sentence would make this README lie about what either service actually does.

## 6. Contract Index

| File | Producer / Type |
|---|---|
| `finance-account-events.md` | account-service — producer |
| `finance-ledger-events.md` | ledger-service — producer, and consumer of account-service's `finance.transaction.completed.v1` (`TransactionEventConsumer`, consumer group `finance-ledger-v1`, `@RetryableTopic`) |

---

## Follow-up (not in scope for this README)

- **Envelope shape divergence (§ 5)** between account-service (7 fields) and ledger-service (8 fields) is worth resolving, but envelope reshape is a breaking wire-format change requiring the versioning protocol in `platform/event-driven-policy.md § Contract Rule` plus a consumer migration plan for whichever service moves. Recommend a separate ADR-gated ticket.
- `finance-account-events.md`'s envelope example should be corrected to match the actual 7-field shape, independent of whether the divergence itself is resolved.
