# Task ID

TASK-MONO-200

# Title

ecommerce↔wms fulfillment **forward-leg on-screen demo** (ADR-MONO-022): a runnable local stack where a storefront/REST purchase auto-creates a wms outbound order — the cross-project handoff made observable. Reuses the proven ecommerce compose + adds the one missing piece (wms `outbound-service` containerized) on a shared broker + a read-model seed + a runbook.

# Status

done

# Owner

(unassigned) — monorepo-level demo infra (root `tests/fulfillment-demo/` + a wms `outbound-service` Dockerfile). 분석=Opus 4.8 / 구현 권장=Sonnet (compose / seed / Dockerfile — no domain logic).

# Task Tags

- chore
- docs

---

# Dependency Markers

- **선행**: the ADR-022 arc (MONO-193/195/196/197/198, all merged) — this makes the *runtime* loop observable, complementing the test-level e2e (MONO-195) which host-synthesised the wms boundary.
- **맥락**: user asked "화면에서도 확인 가능?" → AskUserQuestion **Option C (forward-leg observable demo)** chosen over A (full REST loop +IAM) / B (full 43-container UI) / D (runbook only). Low-risk, no IAM, ~+2 containers.

# Goal

Make the **forward leg** of ADR-MONO-022 observable on a developer machine without the test harness: place an order on the ecommerce stack (REST `X-User-Id`, or web-store) → payment auto-confirms → `OrderConfirmed` → shipping ACL emits `ecommerce.fulfillment.requested.v1` → **wms `outbound-service` auto-creates an outbound order + emits `outbound.order.received` / `outbound.picking.requested`** on the SAME broker. The "aha": *I bought something and the warehouse system automatically got an order.* (Reaching SHIPPED needs the manual wms pick/pack/ship operator steps, which require an `OUTBOUND_WRITE` IAM token — out of scope for C; documented as the A/B extension.)

# Background (verified 2026-06-08)

- ecommerce `docker-compose.yml` containerises the full backend (order/payment/shipping have Dockerfiles + per-service postgres + `ecommerce-kafka`). **Reused as-is.**
- ecommerce order = `X-User-Id` header only (no auth); payment-service auto-pays on `OrderPlaced` → order CONFIRMED → `OrderConfirmed` → shipping `fulfillment.requested`. **Forward leg is automatic, no synthesised events.**
- wms backend is **not** containerised in `wms-platform/docker-compose.yml` (infra-only; backends via bootRun). 6 wms apps have Dockerfiles — **but `outbound-service` (the one the demo needs) does not.** → this task adds it (mirrors `inventory-service/Dockerfile`).
- `outbound-service` resolves codes→uuids from its OWN read-model (`partner_snapshot` / `warehouse_snapshot` / `sku_snapshot`) — so **master-service is NOT needed**; the demo seeds those snapshots directly (the exact recipe `FulfillmentRequestedConsumerIT.seedMaster()` uses: `ECOMMERCE-STORE` partner + `WH-MAIN` warehouse + `SKU-APPLE-001`).
- `outbound-service` is an OAuth2 Resource Server via `jwk-set-uri` (lazy — fetched on first JWT decode), so it **starts without IAM**; the consume path carries no JWT. Needs redis (idempotency) → reuse `ecommerce-redis`.

# Scope

## In Scope
1. **`projects/wms-platform/apps/outbound-service/Dockerfile`** (NEW) — mirror `inventory-service/Dockerfile` (port 8084, `outbound-service.jar`). Fills a real gap (only un-Dockerfiled wms service).
2. **`tests/fulfillment-demo/docker-compose.fulfillment-demo.yml`** — an overlay (used with `-f` over the ecommerce compose) adding: `fulfillment-demo-outbound-postgres` (outbound_db) + `outbound-service` (build from #1) + a `wms-seed` init container, all on `ecommerce-net`, `KAFKA_BOOTSTRAP_SERVERS=ecommerce-kafka:9092`, `REDIS_HOST=ecommerce-redis`.
3. **`tests/fulfillment-demo/seed/outbound-readmodel.sql`** — the `partner_snapshot`/`warehouse_snapshot`/`sku_snapshot` seed (ECOMMERCE-STORE / WH-MAIN / SKU-APPLE-001 ACTIVE).
4. **`tests/fulfillment-demo/README.md`** — runbook: bootJar prereq, bring-up command (only the needed services), place-order curl (`X-User-Id`), and the 3 observation points (ecommerce admin-dashboard order CONFIRMED + broker `ecommerce.fulfillment.requested.v1` → wms `outbound.order.received`/`outbound.picking.requested` + the outbound DB order row). Plus the A/B extension notes (IAM + pick/pack/ship → SHIPPED).

## Out of Scope (documented in the runbook)
- The return leg to SHIPPED (manual wms pick/pack/ship — needs an OUTBOUND_WRITE IAM token; the A/B options).
- inventory-service RESERVED step (optional bonus — needs inventory stock seed + location resolution; named).
- platform-console / web-store-driven UI walkthrough (the B option, 43 containers).
- Any wms domain-logic change (the Dockerfile is pure packaging).

# Acceptance Criteria

- AC-1: `outbound-service/Dockerfile` builds an image from the bootJar (mirrors the sibling pattern; `./gradlew :…:outbound-service:bootJar` is the documented prereq — host-prebuilt-jar convention).
- AC-2: `docker compose -f <ecommerce> -f <overlay> up` brings up `outbound-service` GREEN (health 200) against `ecommerce-kafka` + `ecommerce-redis` + its own postgres, with the read-model seed applied.
- AC-3: A `POST /api/orders` (X-User-Id) on order-service → order reaches CONFIRMED (payment auto) and `ecommerce.fulfillment.requested.v1` is emitted (observable on the broker).
- AC-4: outbound-service consumes it → an `outbound_order` row exists for the orderNo + `outbound.order.received` / `outbound.picking.requested` are emitted (observable on the broker / in the outbound DB).
- AC-5: README documents the bring-up + the 3 observation points + the A/B extension to SHIPPED. No IAM required for C.

# Related Specs

- `docs/adr/ADR-MONO-022-ecommerce-wms-fulfillment-integration.md` + the fulfillment contract files (unchanged).

# Related Contracts

- None changed (the demo exercises the existing `ecommerce.fulfillment.requested.v1` + wms `outbound.*` contracts at runtime).

# Edge Cases / Failure Scenarios

- bootJar not built → Dockerfile COPY fails (documented prereq; the GAP host-prebuilt-jar trap).
- topic-metadata race (consumer subscribes before topic exists) → redpanda/ecommerce-kafka `auto.create.topics` + earliest offset mitigate; the README notes a re-place if the first order races boot.
- outbound JWKS fetch attempted on a REST call (none in C) → would fail without IAM; consume path unaffected.

# Impact on `projects/<name>/`

- `projects/wms-platform/apps/outbound-service/` — new `Dockerfile` only (packaging; no code).
- root `tests/fulfillment-demo/` — new demo dir (compose overlay + seed + README).

# Notes

Reuses the proven ecommerce compose (the bulk of the stack) + the one missing wms piece, so the net new surface is small and low-risk (the C choice). The full UI / SHIPPED walkthrough (A/B) is documented as the extension but not built.

---

# Implementation Notes (DONE 2026-06-08 — validated end-to-end on this host)

**Deliverables**: `projects/wms-platform/apps/outbound-service/Dockerfile` (mirrors inventory's) + `tests/fulfillment-demo/{docker-compose.fulfillment-demo.yml, seed/outbound-readmodel.sql, README.md}`.

**The forward leg ran end-to-end on real running containers** (Rancher Docker, shared `ecommerce-kafka`):
- `outbound-service` image built from the new Dockerfile + bootJar; booted **without IAM** (lazy `jwk-set-uri` Resource Server) + Flyway-migrated + its Kafka consumers joined the shared broker — health `UP`.
- Seed applied cleanly (`partner/warehouse/sku_snapshot`, 3 inserts) against the real outbound DB.
- `POST /api/orders` (X-User-Id) → **PENDING**; one synthetic `product.product.stock-changed`(ORDER_RESERVED) → order **CONFIRMED** → `OrderConfirmed` → `ecommerce.fulfillment.requested.v1`.
- **wms `outbound-service` consumed it → `outbound_order` row: `order_no`=the ecommerce orderId, `source=FULFILLMENT_ECOMMERCE`, `status=PICKING`.** The cross-project handoff is real + observable.

**Discoveries baked into the README** (honest runtime gaps the test e2e papered over):
- order auto-confirm is NOT automatic at runtime — `payment-service` only records a PENDING payment (no auto-capture) and the product order-time reservation is not wired → the demo publishes the same `stock-changed(ORDER_RESERVED)` the e2e synthesises (step 3.5).
- ecommerce backends are `expose`-only behind traefik → the overlay publishes `order-service:8086` + `shipping-service:8090` to the host so the runbook works without traefik.
- `outbound-service` was the only wms app without a Dockerfile (added here).
- Git-bash on Windows mangles the in-container `/opt/kafka` path → README notes `MSYS_NO_PATHCONV=1`.

**Scope kept to C**: SHIPPED needs the manual wms pick/pack/ship (OUTBOUND_WRITE IAM token) — documented as the A/B extension, not built. Stack torn down after validation (`down -v`).
