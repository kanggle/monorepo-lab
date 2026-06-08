# Task ID

TASK-MONO-195

# Title

ADR-MONO-022 §D7 ④ — End-to-end scenario "storefront purchase → connected warehouse ships → order auto-SHIPPED" across ecommerce + wms (cross-project fulfillment loop).

# Status

done

# Owner

claude (Opus 4.8) — cross-project e2e (federation/integration harness). Gated on TASK-BE-340 + TASK-BE-341 both merged.

# Task Tags

- test

---

# Dependency Markers

- **선행**: TASK-BE-340 (wms) + TASK-BE-341 (ecommerce) both merged to main.
- **맥락**: ADR-MONO-010 (e2e tag taxonomy), ADR-MONO-011 (nightly full e2e).

# Goal

A runnable e2e proving the full loop: place/confirm an ecommerce order → `ecommerce.fulfillment.requested.v1` → wms creates outbound order → saga ships → `wms.outbound.shipping.confirmed.v1` (with `orderNo`) → ecommerce Shipping SHIPPED → Order SHIPPED. Both stacks on the shared broker.

# Scope

## In Scope
- e2e scenario under the integration harness (extend `tests/federation-hardening-e2e/` or a dedicated cross-project compose) that boots ecommerce shipping-service/order-service + wms outbound-service/inventory-service/master-service + shared Kafka, seeds `ECOMMERCE-STORE` partner + default warehouse + a mapped SKU with stock, drives the loop, asserts Order=SHIPPED + Shipping tracking populated + wms Order present with `shipTo`.
- `@Tag` per ADR-MONO-010; nightly per ADR-MONO-011 (cross-project boot is heavy).
- Backorder branch (optional): insufficient wms stock → `BACKORDERED` → `order.cancelled` → ecommerce alert (assert Shipping stays PREPARING flagged).

## Out of Scope
- product↔wms inventory reconciliation (D4 v2). Auto-refund.

# Acceptance Criteria

- AC-1: Happy-path loop asserted end to end (Order CONFIRMED → SHIPPED) with correlation by `orderNo`.
- AC-2: wms outbound order created with `source=FULFILLMENT_ECOMMERCE` + `shipTo` populated.
- AC-3: Tagged + wired into nightly (not the fast PR lane) — cross-project boot cost.
- AC-4: Harness documented (README) — how to run locally; graceful skip if a stack is absent (D8).

# Related Specs

- ADR-MONO-022; `fulfillment-events.md`; `ecommerce-fulfillment-subscriptions.md`; `wms-shipment-subscriptions.md`; ADR-MONO-010/011.

# Related Contracts

- Exercises all of ADR-MONO-022's topics end to end.

# Edge Cases

- Eventual consistency: poll/await with timeout (saga + outbox polling have latency).
- Shared broker topic isolation: distinct consumer groups (`outbound-service`, `shipping-service-wms`).

# Failure Scenarios

- Flaky on boot ordering (scm-gateway-style JWKS fail-fast) → start dependencies healthy first; document.
- Putting this in the fast PR lane → CI time blowout. Must be nightly/tagged (AC-3).

---

# Implementation Notes (done 2026-06-08)

**Scope decision (user-approved via AskUserQuestion, "ecommerce-owned + wms 경계 합성"):**
the harness is **ecommerce-owned** (`projects/ecommerce-microservices-platform/tests/e2e/`,
ACL owner = ecommerce per ADR-MONO-022 §D6) and boots the two **real** ecommerce
services (order-service + shipping-service) on a shared Kafka + Postgres
(Testcontainers Network). The **wms boundary event**
(`wms.outbound.shipping.confirmed.v1`) is **host-synthesised, not booted** — per
the monorepo cross-project idiom (scm `WmsInventoryAdjustedConsumedE2ETest` —
"Failure Scenario B → synthesise the counterpart"). Rationale: the wms internal
RECEIVED→SHIPPED saga (pick/pack/ship + inventory.reserved + TMS + OUTBOUND_WRITE
JWT) is **non-cross-project** coverage already gated by `FulfillmentRequestedConsumerIT`
(TASK-BE-340/342); booting it here would duplicate that and add boot-ordering flake.

**What runs (`FulfillmentLoopE2ETest`, `@Tag("e2e")`+`@Tag("full")`):**
POST `/api/orders` → synthetic `product.product.stock-changed` (ORDER_RESERVED) →
order CONFIRMED → real `order.order.confirmed` → shipping creates PREPARING +
emits real `ecommerce.fulfillment.requested.v1` (**asserted on the broker**:
camelCase, `customerPartnerCode=ECOMMERCE-STORE`, `warehouseCode=WH-MAIN`, `shipTo`
populated, `orderNo==orderId`) → synthetic `wms.outbound.shipping.confirmed.v1`
(orderNo correlation) → shipping SHIPPED → real `shipping.shipping.status-changed`
→ order SHIPPED (**asserted via REST GET `/api/orders/{id}`**).

**AC mapping:** AC-1 ✅ (CONFIRMED→SHIPPED loop, orderNo correlation). AC-2 ✅
input contract asserted here (forward event `source`-producing payload: ECOMMERCE-STORE
+ shipTo); the wms-DB side (`source=FULFILLMENT_ECOMMERCE` + shipTo persisted) stays
gated by `FulfillmentRequestedConsumerIT`. AC-3 ✅ `@Tag("full")` → `nightly-e2e.yml`
job `ecommerce-fulfillment-e2e-full` (not the fast PR lane). AC-4 ✅ README +
`@Testcontainers(disabledWithoutDocker=true)` graceful skip (D8).

**No auth/Redis needed:** order/shipping REST is `X-User-Id`-header based and the
consumers are Kafka-only — so no JWKS stand-in (unlike scm/iam/fan/wms gateway
suites). order-service runs in the **default** profile so its `@Profile("!standalone")`
StockChanged + ShippingStatusChanged consumers are active. Local image build uses a
**minimal** inline Dockerfile (bootJar + `java -jar`, no OTel javaagent) to avoid the
production Dockerfile's build-time download; CI builds the same minimal image via
Docker CLI and passes `-Decommerce.e2e.{order,shipping}Image` (Docker 28 +
ImageFromDockerfile hang workaround).

**Verification:** local run GREEN — `1 test, 0 failures, 0 skipped, 0 errors`
(BUILD SUCCESSFUL, Rancher Docker 29.1.3, full cold boot ~20m incl. 2 image builds).
