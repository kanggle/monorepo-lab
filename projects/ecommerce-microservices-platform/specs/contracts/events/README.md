# Event Contracts — ecommerce-microservices-platform

Index of this project's event contracts and the project's declared choices for the five decisions [`platform/event-driven-policy.md`](../../../../../platform/event-driven-policy.md) delegates to `specs/contracts/events/README.md`. This file states what the code does today; it does not restate the platform rule body — see the canonical file for envelope shape requirements, DLQ policy, retry defaults, idempotency rules, etc.

**Source of this census**: live code (`@KafkaListener`, outbox publisher classes, topic/eventType constants), read alongside the 14 contract files below, TASK-MONO-415 (2026-07-15).

---

## 1. Topic Naming

**Diverged — no single convention.** Three shapes coexist:

| Shape | Example topics | Where |
|---|---|---|
| `<context>.<aggregate>.<event>`, dot-separated, **unversioned** | `order.order.placed`, `product.product.stock-changed`, `payment.payment.completed`, `shipping.shipping.status-changed`, `user.user.withdrawn`, `promotion.coupon.used`, `review.review.created` | mainline convention, majority of services |
| `<domain>.<aggregate>.<fact>.v1`, versioned | `wms.master.sku.v1`, `wms.outbound.order.cancelled.v1` (consumed, wms-owned), `ecommerce.fulfillment.requested.v1` (produced, ACL layer to wms) | cross-project fulfillment loop with wms-platform |
| flat, no namespace | `account.created`, `account.deleted`, `account.status.changed` | IAM-originated account lifecycle topics, consumed by user/order/notification/product services |

This is a genuine, live divergence, not a documentation gap — do not force it into one sentence. Any unification is a cross-service topic rename (breaking change, ADR-gated per the platform Contract Rule) and is out of scope for this README.

## 2. `eventType` Naming

**Mostly consistent PascalCase**, with one documented outlier:

- Mainline: `OrderPlaced`, `PaymentCompleted`, `CouponUsed`, `ReviewCreated`, `UserWithdrawn`, `ShippingStatusChanged` (constants in each service's outbox publisher, verified via `topicFor()`/unit tests).
- Outlier: `settlement-service`'s `SettlementPeriodClosedEvent.EVENT_TYPE = "settlement.period.closed.v1"` — dot-separated with an embedded version, unlike every other producer.
- ACL outlier: `FulfillmentAcl.EVENT_TYPE = "ecommerce.fulfillment.requested"` — dot-separated, wms-shaped, because it is emitted into the wms-owned convention (see § 1).

## 3. Serialization

**JSON**, via Jackson (`ObjectMapper`), everywhere. `KafkaTemplate<String, String>` with `StringSerializer`/`StringSerializer` (verified in `order-service/application.yml` and equivalents). No `.avsc` or `.proto` files exist anywhere in this project (confirmed absent by two independent search methods).

Note: `libs/java-messaging` defines a shared `EventEnvelope` class intended as "the canonical envelope shared by every service in this monorepo" — **zero apps in this project import it.** The envelope actually on the wire is the ad-hoc shape described below, not that shared class. This is a real drift worth knowing about but is out of scope to fix here (it is a libs/ consumer-adoption question, not an events/README declaration).

## 4. Schema Registry

**Not used.** No `schema.registry.url`, `SchemaRegistryClient`, Apicurio, or Confluent serializer config anywhere in this project.

## 5. Envelope Shape (informational — not a delegated decision, but relevant context for anyone using this README)

Also diverged, three ways:
- Mainline (order/payment/product/promotion/review/user/settlement): snake_case — `event_id` / `event_type` / `occurred_at` / `source` / (`tenant_id` on services that added it) / `payload`. Field set grew incrementally per service across separate tickets, not from one shared class.
- Cross-project (wms-facing consumers/ACL): camelCase — `eventId` / `eventType` / `occurredAt` / `aggregateType` / `aggregateId` / `payload`, matching wms's convention.
- IAM account lifecycle events: **flat, no envelope wrapper** — fields sit at the JSON root. `account-lifecycle-subscriptions.md` documents a live defect: 4 consumer DTOs (user-service ×2, order-service, notification-service) model a nested `payload` they never actually receive, so those fields silently deserialize to null.

## 6. Contract Index

| File | Producer / Type |
|---|---|
| `auth-events.md` | auth-service — producer |
| `user-events.md` | user-service — producer |
| `account-lifecycle-subscriptions.md` | consumer-side subscription to IAM-originated `account.*` topics (user/order/notification/product services) |
| `order-events.md` | order-service — producer |
| `payment-events.md` | payment-service — producer |
| `product-events.md` | product-service — producer |
| `promotion-events.md` | promotion-service — producer |
| `review-events.md` | review-service — producer |
| `settlement-events.md` | settlement-service — producer |
| `settlement-subscriptions.md` | consumer-side subscription to settlement-service events |
| `shipping-events.md` | shipping-service — producer |
| `wms-inventory-subscriptions.md` | consumer-side subscription to wms-owned inventory topics |
| `wms-shipment-subscriptions.md` | consumer-side subscription to wms-owned shipment/outbound topics |

Producer→consumer overview is per-file above; this project has too many cross-service subscriptions for a single flat table to stay accurate without duplicating the per-file contracts — read the referenced file for the authoritative producer/consumer list of a given event family.

---

## Follow-up (not in scope for this README)

- **Topic naming unification** (§ 1) and **envelope shape unification** (§ 5) both look like real drift, not intentional design. Neither is fixed here — a rename/reshape is a breaking change requiring the versioning protocol in `platform/event-driven-policy.md § Contract Rule` and a consumer migration plan. Recommend a separate ADR-gated ticket if unification is desired.
- `account-lifecycle-subscriptions.md`'s nested-payload-that-never-arrives defect (§ 5) is a live bug, independent of this ticket's scope.
