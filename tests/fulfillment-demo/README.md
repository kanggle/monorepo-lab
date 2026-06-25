# Fulfillment forward-leg demo (ecommerce ↔ wms, ADR-MONO-022)

**TASK-MONO-200.** A runnable local demo of the **forward leg** of the ecommerce↔wms
order-fulfillment integration: *place an order on the storefront → the warehouse system
automatically gets an outbound order.* This is the runtime counterpart of the test-level
e2e (`projects/ecommerce-microservices-platform/tests/e2e/`, TASK-MONO-195), which
host-synthesises the wms boundary; here the **real** `outbound-service` consumes the
cross-project event.

> **Scope = Option C (forward leg).** Reaching `SHIPPED` needs the manual wms warehouse
> steps (pick → pack → ship), which require an `OUTBOUND_WRITE` IAM token. That is the
> **A/B extension** (see the end). No IAM is needed for this forward-leg demo.

## What runs

The **ecommerce** `docker-compose.yml` provides the bulk of the stack (broker `kafka`,
`redis`, per-service postgres, `order/payment/shipping-service`, `web-store`). This
overlay (`docker-compose.fulfillment-demo.yml`) adds the **one missing cross-project
piece** on the same broker:

| Added service | Role |
|---|---|
| `fulfillment-demo-outbound-postgres` | `outbound_db` |
| `outbound-service` | consumes `ecommerce.fulfillment.requested.v1` → creates an outbound order → emits `outbound.order.received` / `outbound.picking.requested` |

`outbound-service` resolves the event's codes→uuids from its own read-model
(`partner/warehouse/sku_snapshot`), so **master-service is not run** — the demo seeds
those three snapshots directly (`seed/outbound-readmodel.sql`).

## Prerequisites

- Docker (Rancher Desktop / Docker Desktop). On this Windows host:
  `DOCKER_HOST=npipe:////./pipe/docker_engine DOCKER_API_VERSION=1.44`.
- Build the outbound jar the Dockerfile packages (host-prebuilt-jar convention):
  ```bash
  ./gradlew :projects:wms-platform:apps:outbound-service:bootJar
  ```

## 1. Bring up (only the forward-leg services)

```bash
docker compose \
  -f projects/ecommerce-microservices-platform/docker-compose.yml \
  -f tests/fulfillment-demo/docker-compose.fulfillment-demo.yml \
  up -d --build \
  order-service payment-service shipping-service outbound-service
```

(`depends_on` pulls in `kafka`, `redis`, and the per-service postgres automatically.
First boot builds images — allow a few minutes. `COMPOSE_PARALLEL_LIMIT=2` avoids the
BuildKit-under-load crash on memory-constrained hosts.)

Wait until `outbound-service` is healthy:

```bash
docker compose -f projects/ecommerce-microservices-platform/docker-compose.yml \
  -f tests/fulfillment-demo/docker-compose.fulfillment-demo.yml ps
# or:
curl -fsS http://localhost:18084/actuator/health
```

## 2. Seed the wms read-model (after outbound-service is up)

`outbound-service`'s Flyway creates the snapshot tables on boot; seed them so code→uuid
resolution succeeds:

```bash
docker compose -f projects/ecommerce-microservices-platform/docker-compose.yml \
  -f tests/fulfillment-demo/docker-compose.fulfillment-demo.yml \
  exec -T fulfillment-demo-outbound-postgres \
  psql -U outbound -d outbound_db < tests/fulfillment-demo/seed/outbound-readmodel.sql
```

(Seeds `ECOMMERCE-STORE` partner + `WH-MAIN` warehouse + `SKU-APPLE-001` — idempotent.)

## 3. Place an order (the storefront purchase)

The order API is `X-User-Id`-based (no login). Use the seeded SKU as the line `variantId`
so wms can resolve it:

```bash
ORDER_ID=$(curl -fsS -X POST http://localhost:8086/api/orders \
  -H 'X-User-Id: demo-user-1' -H 'Content-Type: application/json' \
  -d '{
        "items": [{
          "productId": "PROD-1", "variantId": "SKU-APPLE-001",
          "productName": "Apple", "optionName": "Red",
          "quantity": 2, "unitPrice": 1500
        }],
        "shippingAddress": {
          "recipient": "홍길동", "phone": "010-1234-5678", "zipCode": "06236",
          "address1": "서울특별시 강남구 테헤란로 1", "address2": "10층"
        }
      }' | grep -oE '"orderId":"[^"]+"' | cut -d'"' -f4)
echo "orderId=$ORDER_ID"
```

The order starts **PENDING** — `payment-service` records a PENDING payment but does not
auto-capture, and the product-service order-time stock reservation is not auto-wired at
runtime (the same gap the TASK-MONO-195 test e2e papers over).

## 3.5 Confirm the order (simulate the product stock reservation)

order-service confirms an order on a `product.product.stock-changed` with
`reason=ORDER_RESERVED` (exactly what the e2e synthesises). Publishing one drives
**PENDING → CONFIRMED → `OrderConfirmed` → `ecommerce.fulfillment.requested.v1`**:

```bash
# NOTE (Git-bash on Windows): prefix with MSYS_NO_PATHCONV=1 so the in-container
# /opt/kafka path is not mangled. PowerShell / Linux do not need it.
printf '{"event_id":"demo-evt-1","event_type":"StockChanged","occurred_at":"2026-06-08T00:00:00Z","source":"demo","payload":{"productId":"PROD-1","variantId":"SKU-APPLE-001","previousStock":10,"currentStock":9,"delta":-1,"reason":"ORDER_RESERVED","orderId":"'"$ORDER_ID"'"}}\n' \
 | docker compose \
     -f projects/ecommerce-microservices-platform/docker-compose.yml \
     -f tests/fulfillment-demo/docker-compose.fulfillment-demo.yml \
     exec -T kafka /opt/kafka/bin/kafka-console-producer.sh \
     --bootstrap-server kafka:9092 --topic product.product.stock-changed
```

## 4. Observe the cross-project handoff (3 points)

**(a) ecommerce — order CONFIRMED** (UI or REST):
```bash
curl -fsS http://localhost:8086/api/orders/$ORDER_ID -H 'X-User-Id: demo-user-1'
# status: "CONFIRMED"   (platform-console ecommerce-ops orders surface also lists it)
```

**(b) broker — the forward event + the wms reaction** (any Kafka consumer; e.g. exec into
the broker):
```bash
# ecommerce side emitted it:
docker compose ... exec kafka /opt/kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server kafka:9092 --topic ecommerce.fulfillment.requested.v1 \
  --from-beginning --timeout-ms 8000
# wms reacted (outbound order + picking) :
docker compose ... exec kafka /opt/kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server kafka:9092 --topic outbound.order.received \
  --from-beginning --timeout-ms 8000
```

**(c) wms — the outbound order row** (the warehouse "got the order"):
```bash
docker compose ... exec -T fulfillment-demo-outbound-postgres \
  psql -U outbound -d outbound_db \
  -c "SELECT order_no, source, status FROM outbound_order ORDER BY created_at DESC LIMIT 5;"
# a row with order_no = $ORDER_ID, source = FULFILLMENT_ECOMMERCE, status = PICKING
# (the outbound saga auto-created the order + reserved + emitted picking.requested;
#  it now waits at PICKING for the manual pick/pack/ship — the A/B extension below).
```

That is the demo's "aha": **a storefront purchase automatically created a warehouse
outbound order**, across two independent domain stacks, over Kafka.

> **Validated 2026-06-08** (this host, Rancher Docker): order placed → reservation
> simulated → CONFIRMED → `fulfillment.requested` → `outbound_order` row appeared with
> `source=FULFILLMENT_ECOMMERCE`, `order_no=`the ecommerce orderId, `status=PICKING`.

## Tear down

```bash
docker compose -f projects/ecommerce-microservices-platform/docker-compose.yml \
  -f tests/fulfillment-demo/docker-compose.fulfillment-demo.yml down -v
```

---

## Extending to `SHIPPED` — Option B (full on-screen loop, documented, not run here)

The warehouse pick → pack → ship steps are **operator actions** (realistic for a WMS).
As of TASK-PC-FE-057 + TASK-BE-343 the on-screen leg is **real, production code**:

- **`platform-console` → `WMS 출고` menu (`/wms/outbound`)** (TASK-PC-FE-057) lists outbound
  orders and exposes confirm-gated **Pick → Pack → Ship** actions. "Pick" is
  *confirm-as-planned* — it reads the planned picking-request lines (TASK-BE-343
  `GET /api/v1/outbound/orders/{id}/picking-requests`) and confirms the system-planned
  location/qty, so the operator never types warehouse master data.

The forward-leg demo above runs without IAM (Option C). Reaching `SHIPPED` **through the
console menu** needs the auth + inventory plumbing the forward leg skips, so this is a
larger bring-up (**~43 containers**) — documented here, **not executed on this host**
(memory `env_jdtls_oom_cascade` / Docker chunk-codec history make a 43-container live run
a real failure risk; the code itself is CI-gated, so correctness does not depend on it).

### What Option B adds on top of the forward-leg stack

1. **IAM stack** (`iam-platform` auth/account/gateway) — the console authenticates the
   operator (GAP/IAM OIDC) and the wms gateway requires that token (`tenant_id=wms`).
2. **wms `gateway-service`** — routes `/api/v1/outbound/**` to `outbound-service` and
   enforces the IAM JWT (`tenant_id=wms`). The console calls the gateway hostname
   (`WMS_OUTBOUND_BASE_URL=http://wms.local/api/v1/outbound`), not outbound-service direct.
3. **wms `inventory-service`** + seed: stock for `SKU-APPLE-001` at an `ACTIVE` location in
   `WH-MAIN`, so the outbound saga's reservation succeeds (`REQUESTED → RESERVED`). Pick is
   only enabled once the saga is `RESERVED`.
4. **`platform-console`** (`console-bff` + `console-web`) — the operator UI.
5. **`web-store`** (optional) — to place the order from the storefront UI instead of curl.
6. An operator account provisioned with the **`wms` tenant** + the **`OUTBOUND_WRITE`** role
   (the gateway/outbound-service authorize on the JWT role claim).

### On-screen loop (operator)

1. (web-store UI **or** the curl in §3 above) place the order → it auto-creates the wms
   `outbound_order` (`source=FULFILLMENT_ECOMMERCE`, status `PICKING`).
2. Log into **`platform-console`** as the wms operator → open the **`WMS 출고`** menu
   (`/wms/outbound`). The order appears; once inventory has reserved it the saga shows
   `RESERVED` and **Pick** enables.
3. Click **Pick** (confirm-as-planned) → order `PICKED`. Click **Pack** (creates a packing
   unit and seals it) → order `PACKED`. Click **Ship** → order `SHIPPED`; `outbound-service`
   emits `wms.outbound.shipping.confirmed.v1`.
4. The return leg flips the ecommerce side: `shipping-service` → Shipping `SHIPPED` →
   `order-service` → Order **SHIPPED**. Observe in **web-store `/my/orders/{id}`** and
   the **platform-console ecommerce-ops orders + shippings surfaces**.

The backorder auto-cancel/refund (ADR-MONO-022 v2(a)) + inventory reconciliation (v2(b))
legs are likewise observable once IAM + inventory are wired (each has its own return-leg
event already in `origin/main`).

---

## Full loop on the live federation stack — `docker-compose.ecommerce-fulfillment.yml` (TASK-BE-431)

`docker-compose.optionb-live.yml` (above) runs outbound on `redpanda` and seeds the order
directly to `RESERVED`, so it never exercises the ecommerce link **or** the inventory
reservation. `docker-compose.ecommerce-fulfillment.yml` wires the **real** loop on the
running `federation-hardening-e2e` stack: it puts both wms `outbound-service` **and**
`inventory-service` on `ecommerce-kafka` (the broker the ecommerce shipping/product/order
services use), so the cross-domain topics actually connect.

Bring-up + drive (after `bootJar` of both wms services):

```bash
docker compose -p federation-hardening-e2e \
  -f tests/fulfillment-demo/docker-compose.ecommerce-fulfillment.yml up -d
# seed (UUIDs MUST agree across outbound read-model + inventory stock):
docker exec -i fulfillment-demo-outbound-postgres  psql -U outbound  -d outbound_db  < tests/fulfillment-demo/seed/outbound-readmodel.sql
docker exec -i fulfillment-demo-inventory-postgres psql -U inventory -d inventory_db < tests/fulfillment-demo/seed/inventory-ecommerce-stock.sql
# place an order with the seeded SKU as variantId, then publish the confirm event
# (product.product.stock-changed reason=ORDER_RESERVED) — see §3 / §3.5 above.
```

**Verified live (2026-06-25)** — the forward leg + the BE-431 reservation fix end-to-end:
order CONFIRMED → `ecommerce.fulfillment.requested.v1` → outbound order `PICKING` →
`outbound.picking.requested` → **inventory reserved** (stock `100 → 98`, reservation
`RESERVED`) → saga `RESERVED`. Before BE-431 the picking.requested consumer NPE'd on the
absent `pickingRequestId`/`inventoryId` wire fields, so the saga never left `REQUESTED`.

### Gotchas hit while wiring this (all real, documented here)

1. **TASK-BE-431** — outbound→inventory `picking.requested` wire mismatch. Fixed in this
   branch (the consumer resolves the inventory row from the natural key).
2. **inventory-service boot collision** — it ships `com.wms.inventory…OutboxPublisher`
   **and** pulls the shared `com.example.messaging.outbox.OutboxAutoConfiguration`; both
   claim the bean name `outboxPublisher` → `BeanDefinitionOverrideException` at boot. Only
   surfaces running as a real app (the ITs don't hit it). Worked around here with
   `SPRING_AUTOCONFIGURE_EXCLUDE=com.example.messaging.outbox.OutboxAutoConfiguration`
   — a proper fix (rename the local bean or exclude in inventory's own config) is a
   separate follow-up.
3. **Read-model dedup** — `findBy*Code` resolution throws on duplicate snapshot rows;
   seed exactly one row per code with the UUIDs that match the inventory stock seed.
4. **`docker restart` reverts compose env** on this host — recreating outbound resets it
   to the `optionb-live.yml` definition (redpanda). Use `compose up --force-recreate`,
   never `docker restart`, to keep the `ecommerce-kafka` config.

### What still needs the wms gateway (pick → pack → ship-confirm → ecommerce SHIPPED)

The outbound write endpoints require `OUTBOUND_WRITE` on the JWT `roles` claim. The
operator's wms assume-tenant token carries only `WMS_OPERATOR`; the **wms `gateway-service`
translates `WMS_OPERATOR` → `OUTBOUND_*`** before forwarding. This compose calls
outbound directly (no wms gateway), so the operator pick/pack/ship leg (and thus the
return-leg flip to ecommerce `SHIPPED`) needs either the wms gateway added or the
operator granted `OUTBOUND_WRITE` directly. The order is left staged at `RESERVED` /
`PICKING`; driving Pick→Pack→Ship from the console `/wms/outbound` (which goes through the
wms gateway) closes the loop — `WmsShippingConfirmedConsumer` is already subscribed on
`ecommerce-kafka`.
