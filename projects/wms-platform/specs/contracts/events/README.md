# Event Contracts — wms-platform

Index of this project's event contracts and the project's declared choices for the five decisions [`platform/event-driven-policy.md`](../../../../../platform/event-driven-policy.md) delegates to `specs/contracts/events/README.md`. This file states what the code does today; it does not restate the platform rule body.

**Source of this census**: live code (outbox publisher classes, envelope serializer classes, topic/eventType constants), read alongside the 8 contract files below, TASK-MONO-415 (2026-07-15).

---

## 1. Topic Naming

**Consistent.** `wms.<service>.<family>.v<N>`, dot-separated, lowercase, versioned. Matches spec exactly across every service — topic literals are built via a `TOPIC_PREFIX` + version suffix, or hardcoded per-service in `application.yml`.

Examples: `wms.master.warehouse.v1`, `wms.outbound.picking.requested.v1`, `wms.admin.assignment.v1`, `wms.notification.delivered.v1`.

Cross-project topics produced for ecommerce-microservices-platform to consume (`ecommerce.fulfillment.requested.v1`, `ecommerce.shipping.manual-confirm-requested.v1`) follow the same `<domain>.<x>.v1` shape but under the ecommerce namespace — not a wms-internal divergence, just evidence the shared `<domain>.<aggregate>.<fact>.v<N>` pattern is used by both sides of that integration.

## 2. `eventType` Naming

**Mostly consistent**, with one confirmed, intentional outlier: dot-separated `<service>.<aggregate>.<action>` — e.g. `master.sku.updated`, `outbound.shipping.confirmed`, `admin.user.created`. The outlier, `inventory.low-stock-detected` (hyphenated action instead of `inventory.alert.detected`), is confirmed both in code (`OutboxPublisher.java`) and in its own spec as a deliberate, named exception — not drift.

## 3. Serialization

**Consistent — JSON**, via Jackson (`ObjectMapper.writeValueAsString`), with a hand-built `LinkedHashMap` envelope in each service's `EventEnvelopeSerializer`/equivalent. Kafka producer config everywhere uses `StringSerializer`. No `.avsc`/`.proto` files anywhere in the project.

## 4. Schema Registry

**Not used.** Zero hits for `io.confluent`, `SchemaRegistry`, `apicurio`, `schema.registry` across the whole project. Every contract file states the same forward-looking caveat verbatim: *"Serialization: JSON. Future Avro/Protobuf migration possible but not v1."*

## 5. Contract Index

| File | Producer / Type |
|---|---|
| `master-events.md` | master-service — producer |
| `inventory-events.md` | inventory-service — producer (including the `inventory.low-stock-detected` naming exception, § 2) |
| `inbound-events.md` | inbound-service — producer |
| `outbound-events.md` | outbound-service — producer |
| `admin-events.md` | admin-service — producer |
| `notification-events.md` | notification-service — producer |
| `notification-subscriptions.md` | notification-service — consumer-side subscription overview |
| `ecommerce-fulfillment-subscriptions.md` | outbound-service — consumer of ecommerce-microservices-platform's `ecommerce.fulfillment.requested.v1` / `ecommerce.shipping.manual-confirm-requested.v1` |

---

## Follow-up (not in scope for this README)

- None identified beyond the already-documented, intentional `inventory.low-stock-detected` naming exception (§ 2) — this project's conventions are the cleanest and most internally consistent of the seven platforms surveyed; no unification work is needed.
