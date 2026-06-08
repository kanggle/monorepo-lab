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
`redis`, per-service postgres, `order/payment/shipping-service`, `admin-dashboard`). This
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
  order-service payment-service shipping-service admin-dashboard outbound-service
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
# status: "CONFIRMED"   (admin-dashboard at http://localhost:3001 also lists it)
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

## Extending to `SHIPPED` (the A/B options — not wired here)

The warehouse pick/pack/ship steps are **operator actions** (realistic for a WMS) and the
`outbound-service` REST endpoints require an `OUTBOUND_WRITE` JWT, so they need IAM:

1. Add the IAM stack (`iam-platform` auth/account/gateway) + the wms `gateway-service`.
2. Mint an `OUTBOUND_WRITE`-scoped token (client-credentials).
3. Seed `inventory-service` stock for `SKU-APPLE-001` @ `WH-MAIN` so the reserve succeeds
   (the saga advances REQUESTED → RESERVED).
4. Call `POST /api/v1/outbound/.../{picking,packing}/confirm` then
   `POST /…/{orderId}/shipments` → `outbound-service` emits
   `wms.outbound.shipping.confirmed.v1` → `shipping-service` flips Shipping SHIPPED →
   `order-service` flips the Order **SHIPPED** (observe via `admin-dashboard`).

Option **B** additionally drives steps 4 through the **platform-console** wms ops UI and
places the order via the **web-store** UI — a full on-screen loop (~43 containers, higher
host cost). The backorder auto-cancel/refund (v2(a)) + inventory reconciliation (v2(b))
legs are likewise observable once IAM + inventory are wired.
