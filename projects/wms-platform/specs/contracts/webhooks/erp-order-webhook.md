# Webhook Contract — ERP Order Push

Authoritative contract for outbound order push webhooks **received** from
external ERP systems by `outbound-service`. This is an inbound-only contract —
there is no outbound webhook back to ERP in v1.

Per trait `integration-heavy` rule I6, every inbound webhook must verify
signature, timestamp window, and replay-id dedupe. This document specifies
the exact wire format ERP integrators must produce.

Implementation notes for `outbound-service` are in
[`specs/services/outbound-service/external-integrations.md`](../../services/outbound-service/external-integrations.md)
(Open Item) and
[`specs/services/outbound-service/idempotency.md`](../../services/outbound-service/idempotency.md)
(Open Item).

---

## Endpoint

```
POST {gateway-base}/webhooks/erp/order
Content-Type: application/json
```

Routed via `gateway-service` on a **dedicated webhook route**:

- No JWT enforcement (HMAC signature replaces token-based auth).
- No standard rate-limit (separate webhook tier — `(clientIp, webhookEnv)` key,
  much higher quota).
- Body forwarded verbatim to `outbound-service:8084`.
- Direct path on outbound-service: `/webhooks/erp/order` (NOT under `/api/v1/`).

The route is declared in `specs/services/gateway-service/architecture.md` §
Routes.

---

## Request Headers

| Header | Required | Format | Notes |
|---|---|---|---|
| `Content-Type` | yes | `application/json` | UTF-8 |
| `X-Erp-Event-Id` | yes | string ≤ 80 chars | ERP-assigned globally unique id (UUID v4 recommended). Used for replay dedupe |
| `X-Erp-Timestamp` | yes | RFC 3339 / ISO-8601 UTC | Time the ERP system signed the payload. Must be within ±5 min of server clock |
| `X-Erp-Signature` | yes | `sha256=<hex>` | HMAC-SHA256 of the raw request body using the per-environment shared secret. Lowercase hex |
| `X-Erp-Source` | yes | string | ERP environment id (`erp-prod`, `erp-stg`, `erp-dr`). Selects the verification secret |
| `X-Erp-Schema-Version` | no | int | Defaults to `1`. v2 requires content-negotiation handshake |
| `X-Request-Id` | no | string ≤ 80 chars | If absent, gateway generates one. Echoed in response |
| `User-Agent` | recommended | string | For ops audit: ERP integration version |

`Authorization` header (if sent) is **ignored** — HMAC is the auth mechanism.

---

## Request Body

```json
{
  "orderNo": "ORD-20260429-0001",
  "customerPartnerCode": "CUST-001",
  "warehouseCode": "WH01",
  "requiredShipDate": "2026-05-02",
  "notes": "정기 출고 — 냉장 사과 2,000ea",
  "lines": [
    {
      "lineNo": 1,
      "skuCode": "SKU-APPLE-001",
      "lotNo": "L-20260429-A",
      "qtyOrdered": 1000
    },
    {
      "lineNo": 2,
      "skuCode": "SKU-APPLE-002",
      "lotNo": null,
      "qtyOrdered": 1000
    }
  ]
}
```

### Field Reference

| Field | Type | Required | Nullable | Validation | Resolved To |
|---|---|---|---|---|---|
| `orderNo` | string (1..40) | yes | no | Globally unique. Pattern: `ORD-\d{8}-\d+` recommended but not enforced. Duplicate → `409 ORDER_NO_DUPLICATE` (returned asynchronously via inbox) | `Order.order_no` |
| `customerPartnerCode` | string (1..40) | yes | no | Must resolve to an `ACTIVE` Partner with `partner_type ∈ {CUSTOMER, BOTH}` in `MasterReadModel`. Else inbox FAILED with `PARTNER_INVALID_TYPE` | `Order.customer_partner_id` |
| `warehouseCode` | string (1..20) | yes | no | Must resolve to an `ACTIVE` Warehouse in `MasterReadModel`. Else inbox FAILED with `WAREHOUSE_NOT_FOUND` | `Order.warehouse_id` |
| `requiredShipDate` | string (`YYYY-MM-DD`) | no | yes | If present, must be ≥ today's date in `Asia/Seoul` | `Order.required_ship_date` |
| `notes` | string (≤ 1000) | no | yes | Free text | `Order.notes` |
| `lines` | array | yes | no | ≥ 1 element. `lineNo` unique within request | `OrderLine` rows |
| `lines[].lineNo` | int (≥ 1) | yes | no | 1-indexed; unique within request | `OrderLine.line_no` |
| `lines[].skuCode` | string (1..40) | yes | no | Must resolve to an `ACTIVE` SKU. Else inbox FAILED with `SKU_INACTIVE` | `OrderLine.sku_id` |
| `lines[].lotNo` | string (≤ 40) \| null | conditional | yes | If SKU is LOT-tracked AND lot is specified by ERP, provide `lotNo`. Resolved to existing `Lot` if matched; else `lot_id = null`, `lot_no` carried as text and resolved by operator at pick time. Non-LOT-tracked SKU with non-null `lotNo` → inbox FAILED with `VALIDATION_ERROR` | `OrderLine.lot_id` (resolved) |
| `lines[].qtyOrdered` | int (> 0) | yes | no | EA. Max 1,000,000 per line in v1 | `OrderLine.qty_ordered` |

### Forbidden Fields in v1

- `lines[].uomCode` — v1 assumes EA only. Future expansion via
  `X-Erp-Schema-Version: 2`.
- `lines[].unitPrice`, `lines[].currency` — pricing is owned by ERP, not WMS.
- `multiWarehouse: true` — v1 single-warehouse per order.
- `priority` — order priority / wave assignment is v2.

Unknown fields are **accepted and ignored** (forward-compatibility), but their
presence is logged at INFO with `{eventId, unknownField}` for ops visibility.

---

## Signature Computation

ERP MUST compute the signature as:

```
signature = "sha256=" + lower(hex(HMAC_SHA256(secret, raw_request_body)))
```

Where:

- `secret`: per-environment shared secret (`X-Erp-Source` selects which one).
  Provisioned via Secret Manager; v1 supports env-var fallback for `dev` only.
- `raw_request_body`: the exact bytes of the HTTP request body, before any
  parsing or pretty-printing. The signature is over the byte sequence WMS
  receives — not over a re-serialised JSON.
- The hex output is lowercase. Uppercase hex is rejected.

`outbound-service` recomputes the signature using the matching secret and
compares with constant-time string equality. Mismatch → `401`.

### Reference Implementation (Java pseudo-code)

```java
String body = new String(rawRequestBytes, StandardCharsets.UTF_8);
Mac mac = Mac.getInstance("HmacSHA256");
mac.init(new SecretKeySpec(secret.getBytes(UTF_8), "HmacSHA256"));
byte[] hmac = mac.doFinal(rawRequestBytes);
String expected = "sha256=" + HexFormat.of().withLowerCase().formatHex(hmac);
boolean ok = MessageDigest.isEqual(
    expected.getBytes(UTF_8),
    request.getHeader("X-Erp-Signature").getBytes(UTF_8));
```

---

## Timestamp Verification

`X-Erp-Timestamp` must satisfy:

```
abs(serverNow - X-Erp-Timestamp) ≤ 5 minutes
```

Failure modes:

- Header absent → `401 WEBHOOK_TIMESTAMP_INVALID`
- Unparseable timestamp → `401 WEBHOOK_TIMESTAMP_INVALID`
- Outside ±5 min window → `401 WEBHOOK_TIMESTAMP_INVALID`

The window is configurable per env (`outbound.webhook.erp.timestamp-window-seconds`)
with a 300-second default. Production may NOT widen beyond 600 seconds.

---

## Replay Dedupe

`X-Erp-Event-Id` is upserted into `erp_order_webhook_dedupe` table within
the same TX as the inbox write:

```sql
INSERT INTO erp_order_webhook_dedupe (event_id, received_at)
VALUES (?, now())
ON CONFLICT (event_id) DO NOTHING
RETURNING event_id;
```

- Insert succeeded → first delivery; proceed to inbox.
- Insert conflicted → duplicate; respond `200 OK` with
  `{status: "ignored_duplicate"}` immediately, no inbox write.

Retention: 7 days (ERP retry windows are short; no need for the 30-day
inventory consumer dedupe horizon). Nightly cron deletes expired rows.

---

## Response Codes

### 200 OK — Accepted

```json
{
  "status": "accepted",
  "eventId": "<X-Erp-Event-Id echo>",
  "orderNo": "ORD-20260429-0001",
  "receivedAt": "2026-04-29T11:00:00.123Z"
}
```

`orderNo` is included for ERP-side audit trail (matches what ERP sent).

### 200 OK — Duplicate

```json
{
  "status": "ignored_duplicate",
  "eventId": "<X-Erp-Event-Id echo>",
  "previouslyReceivedAt": "2026-04-29T10:55:32.000Z"
}
```

200 (not 409) because the operation is idempotent from the ERP's perspective.
ERP retrying a webhook is expected; we don't want them to alarm on a "new"
error code.

### 401 Unauthorized

```json
{
  "code": "WEBHOOK_SIGNATURE_INVALID",
  "message": "HMAC signature mismatch",
  "timestamp": "2026-04-29T11:00:00Z"
}
```

`code` values: `WEBHOOK_SIGNATURE_INVALID`, `WEBHOOK_TIMESTAMP_INVALID`,
`WEBHOOK_REPLAY_DETECTED` (reserved for v2; not used in v1).

### 422 Unprocessable Entity — Schema validation failed

```json
{
  "code": "VALIDATION_ERROR",
  "message": "lines[0].qtyOrdered must be > 0",
  "timestamp": "2026-04-29T11:00:00Z",
  "details": {
    "field": "lines[0].qtyOrdered",
    "rejectedValue": 0
  }
}
```

422 is used for **schema** problems detected synchronously in the controller.
Domain-validation problems (unknown customer partner, deactivated SKU) are
detected **asynchronously** by the background processor and surfaced via:

- The `erp_order_webhook_inbox.status = FAILED` row (visible on the admin
  dashboard).
- The `outbound.webhook.processing.failure` metric.

The ERP itself receives **200 OK** for any payload that passes signature +
timestamp + dedupe + schema. Domain failures don't propagate back over HTTP —
ERP would not be able to do anything useful with them at request time.

### 503 Service Unavailable

```json
{
  "code": "SERVICE_UNAVAILABLE",
  "message": "Webhook ingest temporarily unavailable. Retry with same X-Erp-Event-Id.",
  "timestamp": "2026-04-29T11:00:00Z"
}
```

Returned when:

- Primary database is down.
- Inbox table is read-only (failover in progress).

ERP should retry with the same `X-Erp-Event-Id` (idempotent). Recommend
retry with exponential backoff starting at 30s, max 5 attempts, then escalate
to ops via ERP's own DLQ.

---

## Processing Order

```
┌──────────────────────────────────────────────────────────────────────────┐
│ 1. Read X-Erp-Source → look up matching secret                           │
│ 2. Verify X-Erp-Timestamp window  → 401 if outside                       │
│ 3. Verify X-Erp-Signature (HMAC)  → 401 if mismatch                      │
│ 4. Schema-validate body           → 422 if invalid                       │
│ 5. INSERT INTO erp_order_webhook_dedupe → conflict ⇒ 200 ignored_dup     │
│ 6. INSERT INTO erp_order_webhook_inbox  → status=PENDING                 │
│ 7. Commit TX (steps 5+6 in same TX)                                      │
│ 8. Return 200 accepted                                                   │
└──────────────────────────────────────────────────────────────────────────┘

(asynchronously, every 1s)
┌──────────────────────────────────────────────────────────────────────────┐
│ background processor:                                                    │
│   for each row WHERE status=PENDING (LIMIT 50):                          │
│     run ReceiveOrderUseCase                                              │
│       ├─ resolve master refs (Partner, Warehouse, SKU, Lot)              │
│       ├─ create Order + OrderLines                                       │
│       ├─ create OutboundSaga (state = REQUESTED)                         │
│       ├─ write outbox: outbound.order.received                           │
│       ├─ write outbox: outbound.picking.requested                        │
│       └─ update inbox.status=APPLIED (or FAILED + reason)               │
└──────────────────────────────────────────────────────────────────────────┘
```

Steps 1–4 run **before** any DB writes. The order matters for security:
signature verification before any work prevents DoS via unauthenticated
load.

---

## Curl Example

```bash
BODY='{"orderNo":"ORD-20260429-0001","customerPartnerCode":"CUST-001","warehouseCode":"WH01","lines":[{"lineNo":1,"skuCode":"SKU-APPLE-001","qtyOrdered":100}]}'
SECRET='dev-erp-secret'
TIMESTAMP=$(date -u +%Y-%m-%dT%H:%M:%SZ)
SIGNATURE="sha256=$(printf '%s' "$BODY" | openssl dgst -sha256 -hmac "$SECRET" -hex | awk '{print $2}')"

curl -X POST "https://gateway.example.com/webhooks/erp/order" \
  -H "Content-Type: application/json" \
  -H "X-Erp-Source: erp-stg" \
  -H "X-Erp-Event-Id: $(uuidgen)" \
  -H "X-Erp-Timestamp: $TIMESTAMP" \
  -H "X-Erp-Signature: $SIGNATURE" \
  -d "$BODY"
```

---

## Failure-mode Test Cases (per `integration-heavy` I10)

The webhook controller must have integration tests covering each:

| Case | Expected |
|---|---|
| Valid signature + timestamp + new event-id | 200 `accepted`; row in `erp_order_webhook_inbox` with `status=PENDING` |
| Signature header absent | 401 `WEBHOOK_SIGNATURE_INVALID` |
| Signature mismatch (wrong secret) | 401 `WEBHOOK_SIGNATURE_INVALID` |
| Signature uppercase hex | 401 `WEBHOOK_SIGNATURE_INVALID` |
| Timestamp absent | 401 `WEBHOOK_TIMESTAMP_INVALID` |
| Timestamp 6 minutes old | 401 `WEBHOOK_TIMESTAMP_INVALID` |
| Timestamp 6 minutes in future | 401 `WEBHOOK_TIMESTAMP_INVALID` |
| Schema invalid (missing `orderNo`) | 422 `VALIDATION_ERROR` |
| Schema invalid (`lines` empty array) | 422 `VALIDATION_ERROR` |
| Schema invalid (`qtyOrdered = 0`) | 422 `VALIDATION_ERROR` |
| Duplicate event-id | 200 `ignored_duplicate`; no second inbox row |
| Source header references unknown env | 401 `WEBHOOK_SIGNATURE_INVALID` (no secret available to verify) |
| Backend slow (artificial 5s commit delay) | Still returns 200 within ERP timeout (commit is fast — only inbox + dedupe) |
| Body byte-modified after signing (proxy adds whitespace) | 401 `WEBHOOK_SIGNATURE_INVALID` (signature is over raw bytes) |

These tests use a Spring Boot integration test calling the controller directly
— no external ERP system involved.

---

## Domain Validation Outcomes (asynchronous)

Once the inbox row is processed, the resulting `Order` may have one of these
outcomes — none of which propagate back to ERP via HTTP:

| Outcome | inbox.status | Visible to ops |
|---|---|---|
| Happy path: Order + saga created, outbox events published | `APPLIED` | New order in `OutboundDashboard`; saga in `REQUESTED` state |
| Customer partner not in master OR not type CUSTOMER/BOTH | `FAILED` (reason=`PARTNER_INVALID_TYPE`) | "Pending master sync" queue on dashboard |
| Warehouse not in master | `FAILED` (reason=`WAREHOUSE_NOT_FOUND`) | Same |
| `orderNo` already exists | `FAILED` (reason=`ORDER_NO_DUPLICATE`) | Manual investigation flag |
| At least one SKU is INACTIVE | `FAILED` (reason=`SKU_INACTIVE`) | "Master mismatch" queue |
| LOT-tracked SKU with invalid or expired lot | `FAILED` (reason=`LOT_REQUIRED` or `LOT_EXPIRED`) | "Master mismatch" queue |
| Lines from multiple warehouses (v1 restriction) | `FAILED` (reason=`WAREHOUSE_MISMATCH`) | Configuration error flag |
| Master snapshot still catching up (eventual consistency window) | retry on next processor cycle | Transient — usually self-resolves |

Ops re-triggers via:

- Direct ERP re-emission with same `event_id` after the dedupe TTL has expired
  (7 days)
- Admin inbox retry endpoint (v2)

---

## Versioning

- `X-Erp-Schema-Version: 1` is the only version accepted in v1. Absence
  defaults to 1.
- v2 requires a coordinated rollout: ERP and outbound-service deploy together,
  with v1 accepted in parallel for a 30-day deprecation window.
- Within v1, additive payload fields are forward-compatible and ignored if
  unknown.
- Header renaming (e.g., `X-Erp-Signature` → `X-Webhook-Signature`) requires a
  major version bump.

---

## Security Notes

- Secrets MUST live in Secret Manager. `dev` profile may use
  `OUTBOUND_ERP_WEBHOOK_SECRET_<env>` env vars for local testing only.
- Secret rotation procedure (per ERP environment): two valid secrets in
  parallel for the rotation window (`current` + `previous`); both attempted in
  signature verification with current first. After ERP cuts over to the new
  secret, remove `previous` from the secret store.
- Webhook endpoint is exposed on the public internet via `gateway-service` —
  rate-limited at the gateway with a separate higher-quota tier.
- The endpoint MUST NOT echo the secret, signature header value, or the raw
  body in error responses or logs (only metadata: event-id, timestamp, source).

---

## Observability

| Metric | Description |
|---|---|
| `outbound.webhook.received.total{result}` | Counter; result ∈ `accepted \| signature_invalid \| timestamp_invalid \| duplicate \| schema_invalid` |
| `outbound.webhook.processing.lag.seconds` | Histogram; from inbox `received_at` to `processed_at` |
| `outbound.webhook.processing.failure.total{reason}` | Counter; reason ∈ domain error codes |
| `outbound.webhook.inbox.pending.count` | Gauge; alerts at > 100 |
| `outbound.webhook.dedupe.hit.rate` | Computed from `received.total{result=duplicate}` / total |

Logs (structured JSON, INFO level, exclude raw body and signature value):

- `webhook_accepted` `{eventId, source, orderNo, receivedAt}`
- `webhook_signature_invalid` `{eventId, source}` (WARN)
- `webhook_timestamp_invalid` `{eventId, source, drift_seconds}` (WARN)
- `webhook_duplicate` `{eventId, source, previouslyReceivedAt}` (INFO)
- `webhook_inbox_processed` `{eventId, status, orderId|null, failureReason|null}` (INFO/WARN)

---

## Not In v1

- Outbound webhooks back to ERP (we don't notify ERP of shipment dispatch;
  that is handled via TMS only).
- mTLS instead of HMAC.
- JWT-bearer auth instead of HMAC.
- Multi-warehouse split orders.
- Bulk order push via webhook (one order per request only).
- Webhook GET endpoints — webhook is POST-only.
- Order modification webhook (ERP order amendment after initial push is out
  of scope v1).

---

## References

- `specs/services/outbound-service/architecture.md` § Webhook Reception
- `specs/services/outbound-service/external-integrations.md` (Open Item)
- `specs/services/outbound-service/idempotency.md` (Open Item)
- `specs/services/gateway-service/architecture.md` — webhook route declaration
- `specs/contracts/webhooks/erp-asn-webhook.md` — sibling webhook (identical pattern)
- `rules/traits/integration-heavy.md` — I6 (webhook reception pattern)
- `platform/error-handling.md` — webhook error codes
- `platform/api-gateway-policy.md` — gateway rate-limit tier policy
- `platform/security-rules.md` — Secret Manager usage
