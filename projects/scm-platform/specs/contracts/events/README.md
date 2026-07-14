# Event Contracts — scm-platform

Index of this project's event contracts and the project's declared choices for the five decisions [`platform/event-driven-policy.md`](../../../../../platform/event-driven-policy.md) delegates to `specs/contracts/events/README.md`. This file states what the code does today; it does not restate the platform rule body.

**Source of this census**: live code (outbox publisher classes, topic/eventType constants, `@KafkaListener` consumers), read alongside the 3 contract files below, TASK-MONO-415 (2026-07-15).

---

## 1. Topic Naming

**Diverged — two producing conventions live inside this project, plus a third convention it consumes from wms-platform:**

| Shape | Example topics | Where |
|---|---|---|
| `scm.procurement.<aggregate>.<fact>.v1` — one topic per `eventType` | `scm.procurement.po.submitted.v1`, `scm.procurement.po.acknowledged.v1`, `scm.procurement.asn.received.v1` | procurement-service, transactional outbox, named `TOPIC_*`/`EVENT_*` constants |
| `scm.inventory.alert.v1` — a **single shared topic** for a whole alert family (subtype lives only in `eventType`, not the topic) | `scm.inventory.alert.v1` | inventory-visibility-service, direct `KafkaTemplate.send`, no outbox |
| `wms.inventory.<action>.v1` — wms-owned scheme, scm does not control it | `wms.inventory.received.v1`, `wms.inventory.adjusted.v1`, `wms.inventory.transferred.v1`, `wms.inventory.alert.v1` | consumed only, from wms-platform |

## 2. `eventType` Naming

**Diverged**, matching the topic-naming split:

- procurement family: dot-separated, `scm.`-prefixed, declared as named constants — `scm.procurement.po.submitted`, `scm.procurement.asn.received`.
- inventory-alert family: dot-separated but **drops the `scm.` prefix** and is string-concatenated at publish time rather than declared as a constant — `"inventory.alert." + alertType.toLowerCase()` in `KafkaAlertPublisherAdapter`, producing values like `inventory.alert.snapshot_stale`, `inventory.alert.node_unreachable`.

## 3. Serialization

**Consistent — JSON**, via Jackson, in every publisher/consumer (`OutboxProcurementEventPublisher`, `KafkaAlertPublisherAdapter`, `WmsInventoryReceivedConsumer`, `WmsLowStockAlertConsumer`). No `.avsc`/`.proto` files anywhere in the project.

## 4. Schema Registry

**Not used.** Zero hits for `schema.registry`, `SchemaRegistry`, Apicurio, or Confluent config anywhere; all three services declare only `StringSerializer`/`StringDeserializer`.

## 5. Delivery Guarantee (informational)

Also diverged, riding on the same axis as § 1: procurement uses a transactional outbox (`AbstractOutboxPublisher`, at-least-once); the inventory-visibility alert publisher sends directly via `KafkaTemplate` with no outbox (best-effort/at-most-once — the spec explicitly calls this out as intentional for alerting, not a defect).

## 6. Contract Index

| File | Producer / Type |
|---|---|
| `scm-procurement-events.md` | procurement-service — producer (the clean reference convention: named constants, outbox-backed) |
| `inventory-visibility-subscriptions.md` | inventory-visibility-service — consumer of wms-owned `wms.inventory.*` topics, and producer of `scm.inventory.alert.v1` (the diverged family) |
| `replenishment-subscriptions.md` | demand-planning-service — consumer of `wms.inventory.alert.v1` |

---

## Follow-up (not in scope for this README)

- **The inventory-alert family's topic/eventType convention (§ 1, § 2) diverges from procurement's**, both in naming and in the outbox-vs-direct-send delivery guarantee. This is recorded as a real, live divergence per AC-2 — not silently normalized. If unification is desired, it is a breaking change (topic rename + consumer migration) gated by `platform/event-driven-policy.md § Contract Rule`; recommend a separate ADR-gated ticket rather than folding it into this census.
