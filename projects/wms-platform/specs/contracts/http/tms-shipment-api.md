# HTTP Contract — TMS Shipment Push (Outbound)

Authoritative contract for the **outbound HTTPS POST** that
`outbound-service` performs against the external **TMS (Transportation
Management System)** vendor after a Shipment is confirmed. This is the
only outbound HTTP integration in v1 outbound-service and the canonical
exercise of `integration-heavy` rules I1–I4, I7–I9 — see
[`../../services/outbound-service/external-integrations.md`](../../services/outbound-service/external-integrations.md)
§ 2 for the implementation-side declarations (timeouts, circuit breaker,
retry, bulkhead, saga coupling).

This is a **vendor-controlled surface**: the wire shape is observed
through the production adapter (`TmsClientAdapter`, `TmsShipmentRequest`,
`TmsShipmentResponse`, `TmsShipmentMapper`) and is treated as *indicative*
until the TMS vendor publishes a fixed schema. When the vendor finalises
its schema, only the adapter DTOs + mapper + this contract change; the
domain (`Shipment`, `OutboundSaga`) and the application port
(`ShipmentNotificationPort`) are insulated per `integration-heavy` I7/I8.

Direction: **outbound** (this service pushes; TMS receives). There is no
matching inbound push from TMS in v1 — carrier-tracking callbacks
(`pickup-completed` / `delivered`) are v2 scope and would land in
`notification-service`, not here.

---

## Endpoint

```
POST {tms-base}/shipments
Content-Type: application/json
Accept: application/json
```

`{tms-base}` is environment-specific, loaded from configuration via
`TmsClientProperties` (`outbound.tms.base-url`):

| Environment | `{tms-base}` (example) |
|---|---|
| prod | `https://tms.example.com/api/v1` |
| stg | `https://tms-stg.example.com/api/v1` |
| dr | `https://tms-dr.example.com/api/v1` |
| dev / local | overrideable per-developer; defaults documented in `application.yml` |

The full URL the adapter calls is `{tms-base}/shipments` — the trailing
path segment `/shipments` is fixed by `TmsClientAdapter.SHIPMENTS_PATH`
and not configurable.

---

## Authentication

- **API key** in header: `X-Tms-Api-Key: <key>`.
- Secret source: per-environment value in Secret Manager (`tms-prod`,
  `tms-stg`, `tms-dr`). v1 dev fallback: env-var `TMS_API_KEY_<ENV>` for
  local testing.
- Loaded at boot via the `SecretRetriever` port and cached in memory;
  injected into the `RestClient` default headers in
  `TmsClientConfig.tmsRestClient(...)` — every adapter call carries the
  key.
- Rotation: two-key window — `current` and `previous` both accepted by TMS
  during cut-over. v1 dev fallback writes a single env-var; staging/prod
  use the Secret Manager rotation primitive
  (see [`../../services/outbound-service/external-integrations.md`](../../services/outbound-service/external-integrations.md)
  § 2.2 and § 6.2).
- TLS: TMS server certificate validated against the JVM system trust
  store. **No certificate pinning** in v1 — vendor certificate roll-overs
  are silent.

`Authorization` header (if sent by any future adapter wiring) is
**ignored** by TMS — API-key header is the auth mechanism.

---

## Request Headers

| Header | Required | Format | Notes |
|---|---|---|---|
| `Content-Type` | yes | `application/json` | UTF-8 body |
| `Accept` | recommended | `application/json` | Adapter sets this on the `RestClient` builder |
| `X-Tms-Api-Key` | yes | string | Per-environment secret, set by `TmsClientConfig` |
| `Idempotency-Key` | yes | UUID (string) | Always `Shipment.id`. Stable for the lifetime of the shipment record — same value on Resilience4j retry, manual retry via `POST /api/v1/outbound/shipments/{id}:retry-tms-notify`, and saga sweeper re-emission |
| `X-Tms-Schema-Version` | no | int | Defaults to `1`. v2 requires explicit content negotiation; v1 omits the header |
| `X-Request-Id` | no | string ≤ 80 chars | Propagated through `OutboundService` if present; echoed in vendor response if vendor supports it (currently not asserted) |
| `User-Agent` | recommended | string | `outbound-service/{version}` for ops audit on vendor logs |

`X-Request-Id` is informational — TMS is not required to honour it. The
authoritative dedupe key is `Idempotency-Key`.

---

## Request Body

```json
{
  "shipmentId": "0192a5b3-9f3e-7c0e-9f0a-c0f5e8c7d1a2",
  "shipmentNo": "SHP-20260510-0001",
  "carrierCode": null,
  "shippedAt": "2026-05-10T13:42:11Z",
  "orderId": "0192a5b2-7d4c-7b3a-bf5e-a13f0e9c2c11"
}
```

Production source of truth: `TmsShipmentRequest`
(`apps/outbound-service/src/main/java/com/wms/outbound/adapter/out/tms/TmsShipmentRequest.java`)
— a `record` annotated with `@JsonInclude(JsonInclude.Include.NON_NULL)`,
so `null` fields are **omitted from the wire payload**, not serialised as
`"field": null`.

### Field Reference

| Field | Type | Required | Nullable | Validation | Source |
|---|---|---|---|---|---|
| `shipmentId` | UUID (v7) | yes | no | Globally unique. Same value as the request's `Idempotency-Key` header and the future `tmsRequestId` correlation handle (the field name `tmsRequestId` is **vendor-assigned** on the response — not the same value) | `Shipment.id` |
| `shipmentNo` | string (1..40) | yes | no | Format `SHP-YYYYMMDD-NNNN`. Human-readable operational handle for support / runbook lookups | `Shipment.shipment_no` |
| `carrierCode` | string (1..20) \| null | conditional | yes | `null` on the **first** push (TMS assigns the carrier and returns it in the response). On manual retry after a vendor-side `REJECTED`, the operator may pre-populate this — but v1 does not implement that flow | `Shipment.carrier_code` |
| `shippedAt` | string (RFC 3339 / ISO-8601 UTC) | yes | no | `Shipment.shipped_at` — the moment the shipment was confirmed in WMS (saga `confirm-shipping` commit). TMS clock skew is accepted | `Shipment.shipped_at` (Instant) |
| `orderId` | UUID (v7) | yes | no | Original WMS order id. TMS uses this purely as a correlation handle in its UI; we don't expect it back in the response | `Order.id` (= `Shipment.order_id`) |

### Forbidden Fields in v1

- `customerPartnerCode`, `warehouseCode`, `pickupAddress`, `deliveryAddress`,
  `lines`, `weight`, `volume`, `requestedPickupDate` — all routing
  metadata is vendor-managed: TMS resolves it from `shipmentId` against
  its own order book that we feed via a separate (non-WMS) integration in
  v2. v1 keeps the payload minimal.
- Any field not in the table above. Adding fields requires bumping
  `X-Tms-Schema-Version` to `2` and a corresponding adapter PR.

---

## Response — 2xx Success

```json
{
  "tmsRequestId": "TMS-2026-05-10-AB12CD",
  "trackingNumber": "1Z999AA10123456784",
  "carrierCode": "UPS",
  "status": "ACCEPTED",
  "message": "Shipment accepted; carrier dispatched"
}
```

Production source of truth: `TmsShipmentResponse`
(`apps/outbound-service/src/main/java/com/wms/outbound/adapter/out/tms/TmsShipmentResponse.java`)
— a `record` annotated with `@JsonIgnoreProperties(ignoreUnknown = true)`,
which **silently drops** any fields the vendor adds that we don't track.
This is an intentional vendor-extension tolerance: TMS-internal operational
metadata, carrier-detail extensions, and audit fields can be added by the
vendor without breaking the adapter.

### Field Reference

| Field | Type | Required | Nullable | Notes | Translated To |
|---|---|---|---|---|---|
| `tmsRequestId` | string (≤ 80) | yes | no | **Vendor-assigned** correlation handle. Distinct from `Shipment.id` (which we sent as `shipmentId`); kept in `TmsAcknowledgement.requestId` for logging / vendor support tickets | `TmsAcknowledgement.requestId` (audit only — not stored on `Shipment`) |
| `trackingNumber` | string (1..40) | conditional | yes | Carrier-issued tracking number. Required on `ACCEPTED`. `null` on `PENDING_CARRIER_ASSIGNMENT` (TMS has accepted but not yet dispatched a carrier) | `Shipment.tracking_no` |
| `carrierCode` | string (1..20) | conditional | yes | Required on `ACCEPTED`. `null` on `PENDING_CARRIER_ASSIGNMENT`. Vendor-assigned even if the request supplied `null` | `Shipment.carrier_code` |
| `status` | string (enum) | yes | no | One of `ACCEPTED` / `PENDING_CARRIER_ASSIGNMENT` / `REJECTED`. See success classification table below | `Shipment.tms_status` (via `TmsShipmentMapper`) |
| `message` | string (≤ 500) | no | yes | Free-text vendor message. Carried into structured logs (`tms_request_succeeded` / `tms_request_failed`); not surfaced to end-users | log-only |

### `status` Success Classification

Production source of truth: `TmsShipmentMapper.isSuccess(...)`. The
adapter routes the response to `markNotified` / `markFailed` solely based
on this column.

| `status` value | Adapter outcome | `Shipment.tms_status` | Saga outcome |
|---|---|---|---|
| `ACCEPTED` | success | `NOTIFIED` | eventually `COMPLETED` (after `inventory.confirmed`) |
| `PENDING_CARRIER_ASSIGNMENT` | success | `NOTIFIED` | same as `ACCEPTED` — TMS has accepted and will dispatch async |
| `REJECTED` | not-success (no exception thrown) | `NOTIFY_FAILED` | `SHIPPED_NOT_NOTIFIED` — manual retry endpoint clears |
| (missing / unknown) | success (fail-open default) | `NOTIFIED` | same as `ACCEPTED` |

**Fail-open default rationale**: a 2xx response with an unrecognised
`status` value means the vendor added a new enum value we have not
adapted yet. Treating it as success avoids flapping the saga into
`SHIPPED_NOT_NOTIFIED` on every push during a vendor enum extension —
the `TmsShipmentMapper.isSuccess` switch's `default` branch returns
`true` deliberately. If a new vendor enum value actually means failure,
this surfaces as a customer-visible "shipment confirmed but never
arrived" incident which ops would catch via the carrier portal.

---

## Response — 4xx Errors

Production source of truth:
`TmsClientAdapter.postToTms(...)` `onStatus(is4xxClientError, ...)`.

| HTTP status | Adapter classification | Retried? | Saga outcome | Notes |
|---|---|---|---|---|
| 400 | `TmsPermanentException` | no | `SHIPPED_NOT_NOTIFIED`, `failure_reason=TMS_VALIDATION_REJECTED` | Schema-level rejection. Domain bug — alert ops |
| 401 | `TmsPermanentException` | no | `SHIPPED_NOT_NOTIFIED`, alert ops | API-key invalid / rotated. Trigger Secret Manager refresh |
| 403 | `TmsPermanentException` | no | `SHIPPED_NOT_NOTIFIED`, alert ops | Vendor-side authz. Account config issue |
| 404 | `TmsPermanentException` | no | `SHIPPED_NOT_NOTIFIED`, alert ops | Vendor endpoint moved or account not provisioned |
| **409** | **success-equivalent** | **no** | **`COMPLETED` after `inventory.confirmed`** | **Vendor-honoured `Idempotency-Key` — body parses as a normal ack, treated identically to 2xx**. See `TmsClientAdapter.postToTms` `onStatus` 409 fall-through and `external-integrations.md` § 2.11 |
| 422 | `TmsPermanentException` | no | `SHIPPED_NOT_NOTIFIED`, `failure_reason=TMS_SCHEMA_REJECTED` | Schema-version mismatch. Triggers `X-Tms-Schema-Version` negotiation review |
| 429 | `TmsTransientException` | **yes** | `SHIPPED_NOT_NOTIFIED` only if all 3 retries exhausted | Rate-limited. Resilience4j retry with exp backoff applies |
| other 4xx | `TmsPermanentException` (defensive) | no | `SHIPPED_NOT_NOTIFIED`, alert ops | Defensive translation in `RestClientResponseException` catch block |

The 409 row is the **only** 4xx that does not throw. Production code
intentionally returns from the `onStatus` handler without throwing
(`if (status == 409) return;`), letting RestClient fall through to body
extraction — the vendor body is a normal `TmsShipmentResponse` with the
original `tmsRequestId` and status set by the vendor's idempotent replay.

---

## Response — 5xx Errors + Transport-Level Failures

| Class | Adapter classification | Retried? | Saga outcome |
|---|---|---|---|
| 500 / 502 / 503 / 504 | `TmsTransientException(status=<status>)` | **yes** | `SHIPPED_NOT_NOTIFIED` only if all 3 retries exhausted |
| Connection timeout (`SocketTimeoutException`) | `TmsTransientException(status=0)` | yes | same |
| Read timeout (`SocketTimeoutException`) | `TmsTransientException(status=0)` | yes | same |
| DNS failure / connection refused (`IOException` via `ResourceAccessException`) | `TmsTransientException(status=-1)` | yes | same |
| Resilience4j circuit OPEN (`CallNotPermittedException`) | translated by fallback to `ExternalServiceUnavailableException` | n/a (fast-fail) | `SHIPPED_NOT_NOTIFIED` |
| Resilience4j bulkhead saturated (`BulkheadFullException`) | same as circuit OPEN | n/a (fast-fail) | `SHIPPED_NOT_NOTIFIED` |

Retry policy (per `external-integrations.md` § 2.6): max 3 attempts (1
initial + 2 retries), exp backoff `~1s, ~2s, ~4s` ±200ms jitter. After
exhaustion, the adapter's `notifyFallback(...)` always throws
`ExternalServiceUnavailableException` — the listener uniformly routes to
`Shipment.markFailed(...)`.

The `status=0` / `status=-1` markers on `TmsTransientException` are
adapter-internal sentinels for "no HTTP response received" — they distinguish
timeouts (0) from connection-level IO errors (-1) in metrics
(`outbound.tms.request.count{result=timeout}` vs `{result=server_5xx}`).

---

## Idempotency Semantics

`Idempotency-Key: {shipment.id}` is the single coordination point between
WMS and TMS for safe retries.

### Contract Expectations (vendor side)

- TMS guarantees that a repeat call carrying the same `Idempotency-Key`
  and the same body returns the **original** ack (same `tmsRequestId`,
  same `trackingNumber`, same `status`).
- If TMS is mid-process when a repeat call arrives:
  - 409 + a normal ack body → adapter treats as success (see 4xx table).
  - 202 + a normal ack body with `status=PENDING_CARRIER_ASSIGNMENT` →
    adapter treats as success (see Success Classification).
- If the request body differs for the same `Idempotency-Key`, vendor
  behaviour is unspecified. The adapter never varies the body for a given
  shipment, so this case should not arise.

### Client-Side Last-Line-of-Defense: `tms_request_dedupe`

The adapter does not fully trust the vendor's idempotency: a local
`tms_request_dedupe` table caches the first successful response per
`shipmentId`. Subsequent invocations short-circuit before any network
call.

Schema (Flyway `V13__tms_request_dedupe.sql`):

```
tms_request_dedupe
├── request_id          UUID PK   -- = Shipment.id
├── sent_at             TIMESTAMP -- adapter's wall-clock at first successful 2xx
└── response_snapshot   JSONB     -- serialised TmsAcknowledgement
```

Persistence is performed via `TmsRequestDedupePersistenceAdapter` in a
**`Propagation.REQUIRES_NEW`** transaction so the saga's main TX is
already committed by the time the snapshot is written (per
`external-integrations.md` § 2.7 / § 2.10).

Adapter flow (production `TmsClientAdapter.notify(...)`):

1. `SELECT response_snapshot FROM tms_request_dedupe WHERE request_id = shipmentId`.
2. **Hit** → return `TmsAcknowledgement.fromSnapshot(snapshot)` — no HTTP call.
3. **Miss** → POST to TMS with `Idempotency-Key=shipmentId`.
   - On 2xx success: `INSERT tms_request_dedupe(...)` (REQUIRES_NEW),
     return ack.
   - On `REJECTED` status or permanent failure: **do not insert**; let
     the exception / failure propagate to the saga.

### Retention

- **Vendor side**: vendor-controlled (typical: 24h–7d). Not load-bearing
  for WMS correctness — the client-side table is the ground truth.
- **Client side** (`tms_request_dedupe`): **no scheduled purge in v1**.
  Each row is ~200 bytes; growth is bounded by shipment volume. v2 may
  add a quarterly partition / drop policy.

### Distinguished From REST `Idempotency-Key`

WMS exposes its own `Idempotency-Key` on inbound REST mutations (e.g.
`POST /api/v1/outbound/orders` — see `outbound-service-api.md`), backed
by Redis with 24h TTL under the `outbound:idempotency:*` key prefix. This
is a **different** dedupe layer (caller ↔ WMS) and operates on Redis,
not Postgres. The two layers do not share storage and have different
TTL policies; their `Idempotency-Key` values are independent.

---

## Vendor Schema Versioning

`X-Tms-Schema-Version` header (default `1`, omitted on v1 calls).

When the TMS vendor publishes a v2 schema:

1. Bump the header on adapter calls and the version-negotiation header
   logic in `TmsClientConfig`.
2. Update `TmsShipmentRequest` / `TmsShipmentResponse` records.
3. Update `TmsShipmentMapper.toRequest` / `toAcknowledgement` /
   `isSuccess`.
4. Update this contract: § Request Body, § Response 2xx, § `status`
   Success Classification.

**Out of scope for the domain**: `Shipment`, `OutboundSaga`,
`ShipmentNotificationPort`, `TmsAcknowledgement` (port-side record) are
v1/v2-agnostic and **must not change** as part of a vendor schema bump
(per `integration-heavy` I7/I8 — vendor adapter is the integration
boundary; internal model translation is the adapter's job).

If a v2 bump *requires* a domain change, that is a separate, larger
decision and should be recorded in
`projects/wms-platform/docs/adr/` rather than absorbed silently.

---

## Out of Scope (v1)

- **TMS quote / rating API** — pre-shipment carrier-cost lookup. v2
  scope. Would land as a new `TmsQuotePort` and a new contract file.
- **Pickup-completion / delivery-completion push** from TMS back to WMS.
  v2 routing target = `notification-service` (carrier-tracking events).
  Not handled by `outbound-service`.
- **Multi-tenant TMS** — per-customer-partner TMS accounts. v1 assumes
  one TMS account per environment.
- **mTLS instead of API key** for vendor auth. v1 uses single-direction
  HTTPS with API-key header; vendor does not currently expose an mTLS
  endpoint.
- **Certificate pinning** for the TMS host. v1 trusts the JVM system
  trust store; vendor certificate roll-overs are silent. If pinning is
  required, it would be configured at `TmsClientConfig` level (Apache
  HttpClient 5 has the primitive).
- **TMS-side schema discovery** (e.g. OpenAPI fetch on boot). v1 hard-codes
  the wire shape in the adapter DTOs; schema drift is detected at runtime
  via 422 responses (see § Response 4xx).
- **TMS webhook signing key validation** — only outbound push in v1; no
  inbound webhook from TMS to verify.

---

## References

### Implementation-Side Spec (Companion)

- [`../../services/outbound-service/external-integrations.md`](../../services/outbound-service/external-integrations.md)
  § 2 — Timeouts (§ 2.4), Circuit Breaker (§ 2.5), Retry (§ 2.6),
  Idempotency (§ 2.7), Bulkhead (§ 2.8), Internal Model Translation
  (§ 2.9), Failure States and Saga Coupling (§ 2.10), 4xx / Permanent
  Failures (§ 2.11), Observability (§ 2.12).
- [`../../services/outbound-service/architecture.md`](../../services/outbound-service/architecture.md)
  — § TMS Integration (port boundary, `RestClient` adoption rationale,
  Hexagonal alignment).
- [`../../services/outbound-service/idempotency.md`](../../services/outbound-service/idempotency.md)
  — § 1 REST Idempotency (`Idempotency-Key` Redis layer for inbound).
  The vendor-side `tms_request_dedupe` Postgres layer is **not** in this
  file in v1; it lives in `external-integrations.md` § 2.7 only.
- [`../../services/outbound-service/sagas/outbound-saga.md`](../../services/outbound-service/sagas/outbound-saga.md)
  — § 2.5 Step 5 (Confirm Shipping → SHIPPED) and § 4 Saga Recovery
  (Sweeper). The notify-failure path transitioning `SHIPPED →
  SHIPPED_NOT_NOTIFIED` is documented in `saga-status.md` rather than
  here.
- [`../../services/outbound-service/state-machines/saga-status.md`](../../services/outbound-service/state-machines/saga-status.md)
  — `SHIPPED_NOT_NOTIFIED` row, `STUCK_RECOVERY_FAILED` terminal state,
  manual retry → `COMPLETED` transition.

### Sibling HTTP / Webhook Contracts (Pattern)

- [`../webhooks/erp-order-webhook.md`](../webhooks/erp-order-webhook.md)
  — sibling external-system wire contract (direction: inbound webhook
  rather than outbound push, but the same section structure
  / authentication / idempotency layout).
- [`outbound-service-api.md`](outbound-service-api.md) — WMS's own
  outbound REST surface (companion: declares the
  `POST /api/v1/outbound/shipments/{id}:retry-tms-notify` manual retry
  endpoint that re-invokes this TMS contract).

### Rule References

- [`rules/traits/integration-heavy.md`](../../../../rules/traits/integration-heavy.md)
  — I1 (timeout), I2 (circuit breaker), I3 (retry), I4 (idempotency),
  I7 (vendor adapter), I8 (internal model translation), I9 (bulkhead).
- [`platform/security-rules.md`](../../../../platform/security-rules.md)
  — Secret Manager + API-key rotation policy.

### Production Code Anchors (Wire Source-of-Truth)

- `apps/outbound-service/src/main/java/com/wms/outbound/adapter/out/tms/TmsClientAdapter.java`
  — adapter entrypoint, `postToTms(...)` flow, 4xx/5xx classification,
  fallback translation.
- `apps/outbound-service/src/main/java/com/wms/outbound/adapter/out/tms/TmsShipmentRequest.java`
  — request record (5 fields, `@JsonInclude(NON_NULL)`).
- `apps/outbound-service/src/main/java/com/wms/outbound/adapter/out/tms/TmsShipmentResponse.java`
  — response record (5 fields, `@JsonIgnoreProperties(ignoreUnknown=true)`).
- `apps/outbound-service/src/main/java/com/wms/outbound/adapter/out/tms/TmsShipmentMapper.java`
  — `Shipment ↔ TmsShipmentRequest` mapping + `status` success
  classification (`isSuccess` switch).
- `apps/outbound-service/src/main/java/com/wms/outbound/adapter/out/tms/TmsClientConfig.java`
  — `RestClient` bean + Apache HttpClient 5 connection pool wiring.
- `apps/outbound-service/src/main/resources/db/migration/V13__tms_request_dedupe.sql`
  — client-side dedupe schema (referenced in § Idempotency Semantics).
