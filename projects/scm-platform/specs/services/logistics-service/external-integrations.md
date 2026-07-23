# logistics-service — External Integrations

External vendor catalog for `logistics-service`. Required artifact per
`rules/traits/integration-heavy.md` § Required Artifacts (1).

This document declares **every** external system `logistics-service` integrates
with — direction, auth, timeouts, circuit-breaker policy, retry policy,
idempotency, bulkhead, observability hooks. Implementation must match these
declarations; changes here precede code changes (per `CLAUDE.md` Contract Rule).

The marquee integrations are the **two carrier-aggregator dispatch pushes**
(**EasyPost**, **굿스플로**) — real outbound HTTP calls subject to the full
`integration-heavy` rule set (I1–I4, I7–I9). Both sit behind one application port
`ShipmentDispatchPort` (ADR-053 §D2), selected per shipment by `CarrierRouter`
(§D3). **Phase 1 only** — the 3PL fulfillment vendor (ADR-053 §D5) and the
tracking vendor 스윗트래커 (§D6) are Phase 2/3 and appear under § Not In Phase 1.

---

## Catalog Summary

| Vendor | Direction | Protocol | Auth | Required for |
|---|---|---|---|---|
| **EasyPost** (aggregator) | outbound (push) | HTTPS REST | Basic (API key as username, empty password) | international carrier dispatch |
| **굿스플로** (aggregator) | outbound (push) | HTTPS REST | API key header | domestic (KR) carrier dispatch |
| **Kafka cluster** | inbound (consume) | TCP / SASL | mTLS or SCRAM | the `outbound.shipping.confirmed` seam |
| **PostgreSQL** | outbound (DB) | TCP | password | dispatch records + dedupe |
| **Secret Manager** | outbound (config) | HTTPS | service-account / IAM | per-vendor API-key retrieval |

Internal scm/wms services are **not** "external": the wms→logistics seam is the
`outbound.shipping.confirmed` fact event (ADR-052 §D5), consumed over the shared
Kafka cluster — there is **no** synchronous internal REST dependency (ADR-052 §A3).

Both carrier vendors are **aggregators** (one integration → many carriers behind
them: EasyPost → USPS/UPS/FedEx/DHL/…; 굿스플로 → CJ대한통운/한진/롯데/우체국/…).
Selecting an aggregator is deliberate — it is why one adapter yields multi-carrier
reach and why `CarrierRouter` can pick a *carrier* without a per-carrier integration.

---

## 1. EasyPost — Outbound Shipment Dispatch (integration-heavy core)

EasyPost is a carrier-aggregation platform that buys labels and schedules pickup
across international carriers and returns tracking numbers. After a shipment is
confirmed (`outbound.shipping.confirmed`), `logistics-service` pushes it to EasyPost
for international routes.

### 1.1 Endpoint

```
POST {easypost-base}/shipments
```

`{easypost-base}` = `https://api.easypost.com/v2` (per EasyPost API v2). Test vs
production is selected by **which API key** is supplied, not by a different host —
the free **test key** exercises the full flow at no cost (the D8-2 sandbox).

### 1.2 Authentication

- **HTTP Basic** — the API key is the Basic username with an **empty password**
  (`Authorization: Basic base64("{API_KEY}:")`).
- Secret: per-environment in Secret Manager (`easypost-prod`, `easypost-stg`,
  `easypost-test`). Loaded at boot via the `SecretRetriever` port; cached in memory.
- TLS: EasyPost requires **TLS 1.2+**; server cert validated against the system
  trust store; no cert pinning in Phase 1.

> Vendor terms (free-tier / test-key scope) are re-verified against live EasyPost
> docs at implementation (BE-042). See § References.

### 1.3 Adapter Layout

```
adapter/out/dispatch/
├── EasyPostDispatchAdapter.java     // @Profile("!standalone") implements ShipmentDispatchPort
├── EasyPostShipmentRequest.java     // vendor-shaped DTO (out) — package-private (I8)
├── EasyPostShipmentResponse.java    // vendor-shaped DTO (in)  — package-private (I8)
├── EasyPostShipmentMapper.java      // Dispatch ↔ EasyPost DTO (I7/I8)
├── EasyPostClientProperties.java    // @ConfigurationProperties
└── EasyPostClientConfig.java        // dedicated RestClient + connection pool + Resilience4j
```

The domain calls `ShipmentDispatchPort.dispatch(shipment)` without knowing EasyPost
exists (I7). Vendor DTOs never leak across the port boundary (I8).

### 1.4 Timeouts (I1)

| Stage | Value | Rationale |
|---|---|---|
| `connectTimeout` | **5 s** | hostname resolve + TCP handshake; longer = network/vendor degraded |
| `readTimeout` | **30 s** | label purchase can take several seconds under load; 30s gives headroom |

Spring `RestClient` (synchronous) over Apache HttpClient 5 with a **dedicated**
`PoolingHttpClientConnectionManager`. Named `easypost-client`, **not shared** with
굿스플로 or any other vendor (I9).

### 1.5 Circuit Breaker (I2)

Resilience4j `easyPostDispatchCircuit`: `failureRateThreshold=50%`,
`slidingWindowType=COUNT_BASED`, `slidingWindowSize=20`, `minimumNumberOfCalls=10`,
`waitDurationInOpenState=60s`, `permittedNumberOfCallsInHalfOpenState=3`,
`automaticTransitionFromOpenToHalfOpenEnabled=true`. `recordExceptions` =
transient (5xx, timeout, IO); `ignoreExceptions` = permanent (4xx ≠ 429). State →
gauge `logistics_dispatch_circuit_state{vendor="easypost"}`.

### 1.6 Retry (I3)

Resilience4j `easyPostDispatchRetry`: `maxAttempts=3` (1 + 2), exponential
`2^(n-1)` capped at 4s, **±200ms jitter**. `retryExceptions` = transient (5xx,
429, timeout, IO); `ignoreExceptions` = permanent 4xx (≠429) + `CallNotPermittedException`.
Effective ~1s, ~2s, ~4s (±200ms). On exhaustion → `DISPATCH_FAILED` + operator retry.

### 1.7 Idempotency (I4)

- Header `Idempotency-Key = {shipment.id}` — stable across Resilience4j retry and
  operator `:retry` (identical semantics to the wms interim being relocated,
  ADR-052 §2.7).
- Local ground-truth `dispatch_request_dedupe(request_id PK, sent_at,
  response_snapshot)` (`REQUIRES_NEW` insert). A repeat send reads the snapshot and
  short-circuits with no network call.

> EasyPost supports request idempotency; the exact header/semantics are confirmed
> against live docs at BE-042 and the adapter conforms to whatever EasyPost
> mandates while preserving the `{shipment.id}` key stability above.

### 1.8 Bulkhead (I9)

`PoolingHttpClientConnectionManager` `maxTotal=10`, `defaultMaxPerRoute=10` +
Resilience4j `Bulkhead` (SEMAPHORE) `maxConcurrentCalls=10`, `maxWaitDuration=0`
(fail-fast → `BulkheadFullException` → `DISPATCH_FAILED`). **Dedicated to EasyPost
only — not shared** with 굿스플로, the HTTP server pool, HikariCP, or Kafka pools.

### 1.9 Internal Model Translation (I7, I8)

`EasyPostShipmentMapper` maps `Dispatch`/shipment → `EasyPostShipmentRequest` and
the ack → domain values via `Dispatch.recordAck(...)`: vendor `tracking_code` →
`Dispatch.trackingNo`; vendor selected-rate `carrier` → `Dispatch.carrierCode`;
`status` → `DISPATCHED` / `DISPATCH_FAILED`. Vendor-shaped fields never leak.

---

## 2. 굿스플로 (Goodsflow) — Outbound Shipment Dispatch (domestic)

굿스플로 is a Korean carrier-aggregation platform (접수/운송장 발행) fronting the
domestic carriers (CJ대한통운, 한진, 롯데, 우체국, …). `logistics-service` pushes
domestic-route shipments to 굿스플로, which issues the waybill and hands off to the
selected domestic carrier.

### 2.1 Endpoint

```
POST {goodsflow-base}/shipments      (booking / 운송장 발행)
```

`{goodsflow-base}` is environment-specific — a dedicated **test environment**
(`https://test-api.goodsflow.io`) exists and is used for stg/dev, distinct from
production. Exact resource paths + payload are confirmed against the 굿스플로 OPEN
API at implementation (BE-042); this document fixes the **integration policy**,
which is vendor-independent.

### 2.2 Authentication

- **API key** in header (vendor-specified header name, confirmed at BE-042).
- Secret: per-environment in Secret Manager (`goodsflow-prod`, `goodsflow-test`).
- TLS: server cert validated against the system trust store.

> 굿스플로 issues **real waybills**; the test environment is what makes
> credential-free dispatch **rehearsal** possible. Whether test-env key issuance
> requires a business contract is an onboarding question resolved before BE-042 —
> if it gates, EasyPost (confirmed free sandbox) carries Phase-1 proof alone and
> 굿스플로 lands when onboarded. See § References.

### 2.3 Adapter Layout

```
adapter/out/dispatch/
├── GoodsflowDispatchAdapter.java    // @Profile("!standalone") implements ShipmentDispatchPort
├── GoodsflowShipmentRequest.java    // vendor-shaped DTO (out) — package-private
├── GoodsflowShipmentResponse.java   // vendor-shaped DTO (in)  — package-private
├── GoodsflowShipmentMapper.java     // Dispatch ↔ 굿스플로 DTO (I7/I8)
├── GoodsflowClientProperties.java
└── GoodsflowClientConfig.java       // dedicated RestClient + pool + Resilience4j
```

### 2.4 Timeouts (I1)

| Stage | Value |
|---|---|
| `connectTimeout` | **5 s** |
| `readTimeout` | **30 s** |

Dedicated `PoolingHttpClientConnectionManager` named `goodsflow-client`, **not
shared** with EasyPost (I9).

### 2.5 Circuit Breaker (I2)

Resilience4j `goodsflowDispatchCircuit` — same policy shape as §1.5
(50% / 20 / 60s / half-open 3), independent instance. State → gauge
`logistics_dispatch_circuit_state{vendor="goodsflow"}`.

### 2.6 Retry (I3)

Resilience4j `goodsflowDispatchRetry` — same shape as §1.6 (3 attempts, ~1/2/4s,
±200ms jitter), independent instance. On exhaustion → `DISPATCH_FAILED`.

### 2.7 Idempotency (I4)

Same policy as §1.7 — `Idempotency-Key = {shipment.id}` (or the 굿스플로-mandated
dedup mechanism, whichever the vendor honours) + the **same** local
`dispatch_request_dedupe` table (one table, keyed by `request_id = shipment.id`;
the vendor a request went to is recorded in the snapshot, so a shipment cannot be
double-dispatched **across** vendors either).

### 2.8 Bulkhead (I9)

Dedicated pool `maxTotal=10` + Resilience4j `Bulkhead` (SEMAPHORE)
`maxConcurrentCalls=10`, fail-fast. **Dedicated to 굿스플로 only — not shared with
EasyPost.**

### 2.9 Internal Model Translation (I7, I8)

`GoodsflowShipmentMapper` maps `Dispatch` → `GoodsflowShipmentRequest` and the
waybill ack → `Dispatch.recordAck(...)` (운송장번호 → `trackingNo`; carrier →
`carrierCode`). Vendor DTOs never cross the port.

---

## 3. Dispatch Failure States & Saga Coupling (both vendors)

The dispatch runs in the `shipping.confirmed` **consumer**, after the wms shipping
TX already committed and published the event (ADR-052 §D5 — the seam is a fact
event, so the vendor call is never inside a distributed TX, T2).

```
1. ShippingConfirmedConsumer receives outbound.shipping.confirmed:
   - dedup (eventId T8 + shipment_id) → create Dispatch(PENDING)
   - CarrierRouter selects vendor (region) → ShipmentDispatchPort.dispatch(...)
2. on success: Dispatch.recordAck → DISPATCHED (tracking_no, carrier_code); ack Kafka
3. on failure (retry/circuit/bulkhead exhausted, or vendor 4xx):
   - Dispatch → DISPATCH_FAILED, failure_reason set; ack Kafka (NOT a consume failure)
   - alert logistics_dispatch_failed_total > 0
```

Recovery: `POST /api/v1/logistics/dispatches/{id}:retry` re-invokes the same vendor
with the same `Idempotency-Key`; on success `DISPATCH_FAILED → DISPATCHED`. Naturally
idempotent (an already-`DISPATCHED` dispatch returns the cached ack, no vendor call).
This is the relocation of the wms `SHIPPED_NOT_NOTIFIED` + `:retry-tms-notify` shape
(ADR-053 §D8); wms's outbound saga completes at *shipped + event published*.

### 4xx / Permanent Failures (both vendors)

| Vendor status | Treatment | Dispatch outcome |
|---|---|---|
| 2xx | success | `DISPATCHED` |
| 400 / 422 | no retry (domain/schema bug — alert) | `DISPATCH_FAILED`, reason set |
| 401 / 403 | no retry (secret/onboarding issue — alert ops) | `DISPATCH_FAILED` |
| 409 (idempotent dup) | treat as success (I4) | `DISPATCHED` (cached ack) |
| 429 | retry per backoff | if exhausted: `DISPATCH_FAILED` |
| 5xx / timeout / IO | retry per backoff | if exhausted: `DISPATCH_FAILED` |

---

## 4. Kafka Cluster (the seam)

- **Inbound (consume):** `wms.outbound.shipping.confirmed.v1`, group
  `scm-logistics-v1`, `auto-offset-reset=earliest`, `enable-auto-commit=false`,
  `isolation-level=read_committed`, manual ack after the dispatch TX commits.
  `DefaultErrorHandler` backoff `[1s,2s,4s]` → DLT. Contract:
  `specs/contracts/events/logistics-dispatch-subscriptions.md`.
- **Outbound (publish):** none in Phase 1 (no outbox — ADR-053 §D2; see
  `architecture.md` § Outbox).
- Auth: SASL/SCRAM-SHA-512 (dev/stg), mTLS (prod), per `platform/security-rules.md`.

## 5. PostgreSQL

Outbound (read+write). One logical DB per service — `scm_logistics` schema. HikariCP
(pool 20, connect timeout 5s). Flyway V1 baseline (`dispatch`,
`dispatch_request_dedupe`, `processed_events`). JPA entities package-private inside
the persistence adapter; domain models are pure POJOs (Hexagonal).

## 6. Secret Manager

Outbound (read). Per-environment API keys: `easypost-{prod,stg,test}`,
`goodsflow-{prod,test}`. v1 dev fallback via env vars
(`EASYPOST_API_KEY_<ENV>`, `GOODSFLOW_API_KEY_<ENV>`). Cached at boot; missing key
→ that vendor's adapter fails on first call → `DISPATCH_FAILED` + ops alert (the
other vendor is unaffected — keys and pools are per-vendor).

---

## 7. Aggregated Resilience Policy

| Vendor | Timeout (conn/read) | Circuit | Retry | Idempotency | Bulkhead | Recovery |
|---|---|---|---|---|---|---|
| **EasyPost** | 5s / 30s | 50% over 20, open 60s | 3, 1/2/4s + ±200ms | `Idempotency-Key={shipment.id}` + `dispatch_request_dedupe` | Pool 10 (dedicated) | `DISPATCH_FAILED` + `:retry` |
| **굿스플로** | 5s / 30s | 50% over 20, open 60s | 3, 1/2/4s + ±200ms | same key + same dedupe table | Pool 10 (dedicated) | `DISPATCH_FAILED` + `:retry` |
| Kafka consumer | (broker session) | n/a | 3 in-process [1,2,4]s | `eventId` dedup + `shipment_id` guard | Spring default | `<topic>.DLT` |
| PostgreSQL | 5s / (statement) | n/a | 0 | n/a | HikariCP 20 | 5xx |
| Secret Manager | 3s / 5s | n/a | 3 exp-backoff | n/a | n/a | boot-fail / cached |

**No pool is shared across vendors** (integration-heavy I9): EasyPost and 굿스플로
each own a dedicated HttpClient pool + Resilience4j instance set; Kafka and Hikari
pools are separate again.

---

## 8. Test Suite (integration-heavy I10)

| Path | Framework |
|---|---|
| **EasyPost adapter** | **WireMock** — success, timeout, 5xx, 4xx, 429, circuit-open, bulkhead-full, idempotency-replay |
| **굿스플로 adapter** | **WireMock** — same matrix, independent stub |
| `CarrierRouter` | unit — domestic→굿스플로, international→EasyPost, unmapped-region→default vendor |
| seam consumer | Testcontainers Kafka — `shipping.confirmed` → dispatch upsert + dedup, malformed → DLT, vendor-failure → `DISPATCH_FAILED` + `:retry` recovery |
| `:retry` endpoint | slice `@WebMvcTest` — replay idempotency (no second vendor call for already-`DISPATCHED`) |
| Secret Manager | mock `SecretRetriever` |

Standalone profile (`StandaloneDispatchAdapter`) so local/CI runs need no vendor
credentials. Tests never reach a real vendor (WireMock / stub only).

---

## 9. Not In Phase 1

- **3PL fulfillment vendor** (품고 / ShipBob …) — Phase 2, gated on ADR-052 §D8-3;
  a `ThirdPartyFulfillmentPort`, not a `ShipmentDispatchPort` adapter (a 3PL
  fulfills the whole order, it is not a last-mile carrier). Its inventory node is
  `THIRD_PARTY_LOGISTICS` in inventory-visibility-service.
- **스윗트래커 (tracking)** — Phase 3 optional; a `ShipmentTrackingPort` on the
  **tracking** axis (read-only carrier scan status), forwarding to
  notification-service (ADR-052 §11, ADR-053 §D6). Not a dispatch vendor.
- **Outbound webhook / return-label / rating APIs** — not Phase 1.

---

## References

- [`architecture.md`](architecture.md) — service composition, ports, `CarrierRouter`, `FulfillmentRouter` seam
- [`specs/contracts/events/logistics-dispatch-subscriptions.md`](../../contracts/events/logistics-dispatch-subscriptions.md) — the seam subscription contract
- [`ADR-MONO-053`](../../../../../docs/adr/ADR-MONO-053-logistics-service-multimodal-fulfillment.md) — §D2 (dispatch port), §D3 (CarrierRouter), §D7 (phasing), §D8 (wms retirement)
- [`ADR-MONO-052`](../../../../../docs/adr/ADR-MONO-052-transport-context-map.md) — §D5 (fact-event seam), §D7 (interim relocation source), §2.7/§2.10 (idempotency + post-commit shape being relocated)
- `projects/wms-platform/specs/services/outbound-service/external-integrations.md` §2 — the TMS integration this catalog inherits its structure from
- `rules/traits/integration-heavy.md` — I1–I10
- `platform/security-rules.md` — Secret Manager policy · `platform/observability.md` — required integration metrics
- EasyPost API docs (auth, shipments, idempotency) — re-verify current terms at BE-042
- 굿스플로 OPEN API (test env `test-api.goodsflow.io`) — confirm paths/auth/onboarding at BE-042
