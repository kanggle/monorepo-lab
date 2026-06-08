# ecommerce × wms Fulfillment Loop E2E

**TASK-MONO-195** — ADR-MONO-022 §D7 ④. Cross-project end-to-end proof of the
storefront→warehouse fulfillment loop: *a webstore purchase ships from the
connected warehouse and the order auto-completes to SHIPPED.*

## What it proves

```
POST /api/orders                         (order-service, REST)        → order PENDING
  └─ synthetic product.product.stock-changed (ORDER_RESERVED)         → order CONFIRMED
       └─ order.order.confirmed           (real, order-service outbox)
            └─ shipping-service: create Shipping PREPARING
                 └─ ecommerce.fulfillment.requested.v1 (real, ACL)    ← ASSERTED on broker
                      ··· (wms outbound-service would consume here) ···
  └─ synthetic wms.outbound.shipping.confirmed.v1 (orderNo == orderId)
       └─ shipping-service: Shipping SHIPPED
            └─ shipping.shipping.status-changed (real)
                 └─ order-service: order SHIPPED                      ← ASSERTED via REST
```

**Real services booted:** `order-service` + `shipping-service` (the two ecommerce
services that own the loop; the ACL lives in shipping-service per ADR-MONO-022 §D6).

**Synthesised, not booted:** the wms outbound-service boundary event
(`wms.outbound.shipping.confirmed.v1`). The wms *internal* RECEIVED→SHIPPED saga
(pick/pack/ship + inventory reservation + TMS) is wms-owned coverage already
gated by `FulfillmentRequestedConsumerIT` (TASK-BE-340 / TASK-BE-342). Booting it
here would duplicate that coverage and add boot-ordering flake for a path that is
not the *cross-project* concern. This mirrors the established monorepo idiom
(scm `WmsInventoryAdjustedConsumedE2ETest` — "Failure Scenario B → synthesise the
counterpart event").

The forward event `ecommerce.fulfillment.requested.v1` IS asserted on the broker
(camelCase envelope, `customerPartnerCode=ECOMMERCE-STORE`, `shipTo` populated,
`orderNo==orderId`) — i.e. the exact contract input that produces the wms order
with `source=FULFILLMENT_ECOMMERCE` + `shipTo` (AC-2 input contract).

## Acceptance criteria mapping

| AC | Where |
|---|---|
| AC-1 happy-path loop (CONFIRMED → SHIPPED), correlation by `orderNo` | `FulfillmentLoopE2ETest` steps 1–5 |
| AC-2 wms order `source=FULFILLMENT_ECOMMERCE` + `shipTo` | input contract asserted here (step 3); wms-DB side gated by `FulfillmentRequestedConsumerIT` |
| AC-3 tagged + nightly (not the fast PR lane) | `@Tag("e2e")`+`@Tag("full")`; `nightly-e2e.yml` job `ecommerce-fulfillment-e2e-full` |
| AC-4 documented + graceful skip if a stack is absent (D8) | this README; `@Testcontainers(disabledWithoutDocker=true)` |

## Run it

### Prerequisites
- Docker (Rancher Desktop / Docker Desktop). Without Docker the suite **skips**
  (`disabledWithoutDocker = true`).

### Local (ImageFromDockerfile from bootJars)
```bash
# 1. Build the two service bootJars (done automatically by the e2e task's dependsOn)
./gradlew \
  :projects:ecommerce-microservices-platform:apps:order-service:bootJar \
  :projects:ecommerce-microservices-platform:apps:shipping-service:bootJar

# 2. Run the loop (Windows + Rancher Desktop example)
DOCKER_HOST=npipe:////./pipe/docker_engine DOCKER_API_VERSION=1.44 \
  ./gradlew :projects:ecommerce-microservices-platform:tests:e2e:e2eFullTest
```

The base class assembles a **minimal** image per service from the bootJar
(`temurin:21-jre-alpine` + `java -jar`) — it deliberately drops the production
OTel javaagent (the e2e exercises the event loop, not tracing) which also avoids
the production Dockerfile's build-time network download.

### CI (pre-built images)
Pass pre-built image names to skip the in-test build:
```bash
./gradlew :projects:ecommerce-microservices-platform:tests:e2e:e2eFullTest \
  -Decommerce.e2e.orderImage=<img> -Decommerce.e2e.shippingImage=<img>
```

## Topology

One Postgres (two DBs: `order_db` + `shipping_db` via `init-databases.sql`) + one
Kafka KRaft broker + the two services, all on a shared Testcontainers network.
No Redis, no OIDC/JWKS — the order/shipping REST surface is `X-User-Id`-header
based and the consumers are Kafka-only (verified). order-service runs in the
**default** profile so its `@Profile("!standalone")` StockChanged +
ShippingStatusChanged consumers are active.

## CI cadence

`@Tag("full")` → nightly cron + push-to-main only, **never** the fast PR lane
(ADR-MONO-010/011; cross-project boot cost). Failure shows red on the nightly
badge and does not block PRs (same policy as the other `*-e2e-full` jobs).
