# shipping-service — External Integrations

External vendor catalog for `shipping-service`. Required artifact per
`rules/traits/integration-heavy.md` § Required Artifacts (1).

This document declares **every** external system `shipping-service` integrates
with — direction, auth, timeouts, resilience policy, observability hooks.
Implementation must match these declarations; changes here precede code changes
(per `CLAUDE.md` Contract Rule).

The marquee integration is the **logistics aggregator** (물류 중개 플랫폼,
[ADR-007](../../../docs/adr/ADR-007-logistics-aggregator-carrier-integration.md) D2).
ADR-007 fixed the *shape* (provider-agnostic single port + aggregator behind it);
this document fixes the **concrete aggregator = [Delivery Tracker](https://tracker.delivery)**
(`tracker.delivery`) and its wire-level contract.

> **Why Delivery Tracker**: it is a free SaaS tracking aggregator that normalises
> many Korean carriers (CJ대한통운·한진·롯데·우체국 …) behind one GraphQL API and one
> OAuth2 credential — exactly the "one endpoint / one key / one unified status
> scheme" model ADR-007 D2 chose. Free `client_id`/`client_secret` from
> `console.tracker.delivery` (no carrier contract required), so the portfolio can
> demonstrate a **real** integration. The unified status scheme maps onto the
> four-state shipping domain via `CarrierStatusMapper`.

---

## Catalog Summary

| Vendor | Direction | Protocol | Auth | Required for |
|---|---|---|---|---|
| **Delivery Tracker** (aggregator) | outbound (pull) | HTTPS GraphQL | OAuth2 `client_credentials` → Bearer | carrier status refresh + auto-collect |
| **Delivery Tracker** (aggregator) | inbound (thin-ping webhook) | HTTPS REST | shared path-token (no HMAC in vendor v1) | push "status changed → re-pull" |
| **Generic aggregator webhook** (legacy, TASK-BE-294) | inbound (status-carrying webhook) | HTTPS REST | HMAC-SHA256 (`X-Carrier-Signature`) | direct-connect / alternate aggregator |
| **Kafka cluster** | both | TCP / SASL | SCRAM / mTLS | `OrderConfirmed` consume + `ShippingStatusChanged` publish |
| **PostgreSQL** | outbound (DB) | TCP | password | shipping aggregate + dedup/ShedLock |

Internal services (`order-service`, `notification-service`, `gateway-service`)
are not "external" — same project, same Kafka cluster, internal-event contracts
in `specs/contracts/`.

---

## 1. Delivery Tracker — Outbound GraphQL Pull (integration-heavy core)

After a shipment is `SHIPPED` with a `carrier` + `trackingNumber`, both the
admin `refresh-tracking` (TASK-BE-293) and the unattended auto-collect scheduler
(TASK-BE-360) read the latest carrier status from the aggregator via
`CarrierTrackingPort`. The Delivery Tracker adapter is the real `http`-family
implementation (TASK-BE-364).

### 1.1 Endpoints

```
POST https://auth.tracker.delivery/oauth2/token     # OAuth2 token endpoint
POST https://apis.tracker.delivery/graphql          # GraphQL tracking endpoint
```

Both env-overridable: `shipping.carrier.delivery-tracker.{auth-url,graphql-url}`.

### 1.2 Authentication — OAuth2 client_credentials

- Token request: `POST {auth-url}` with `Authorization: Basic base64(clientId:clientSecret)`
  and body `grant_type=client_credentials` (`application/x-www-form-urlencoded`).
- Response `access_token` (JWT, short-lived) is cached in memory and reused until
  near expiry, then re-fetched — a hand-rolled token provider mirroring the
  `GapClientCredentialsTokenProvider` pattern (ADR-005 workload identity).
- GraphQL calls carry `Authorization: Bearer {access_token}`.
- Credentials are **env-injected** (`shipping.carrier.delivery-tracker.client-id`,
  `…client-secret`) — no hardcoding. **Blank either credential = adapter disabled
  (net-zero)**, identical to `mode=mock` (ADR-007 D4).

### 1.3 The GraphQL Query

```graphql
query GetTrackLastEvent($carrierId: ID!, $trackingNumber: String!) {
  track(carrierId: $carrierId, trackingNumber: $trackingNumber) {
    lastEvent { time status { code } }
  }
}
```

- `carrierId` = Delivery Tracker reverse-DNS carrier id (`kr.cjlogistics`,
  `kr.hanjin`, `kr.lotte`, `kr.epost`, …). The shipping `carrier` field carries
  this id directly; shipment identity is keyed by `trackingNumber`/`shippingId`,
  never by the returned carrier id (ADR-007 D2).
- Only `lastEvent.status.code` is consumed (event history is out of scope v1).
- The adapter is **best-effort / never-throw** (`CarrierTrackingPort` contract):
  GraphQL transport error, non-2xx, `track == null`, missing `lastEvent`, or a
  GraphQL `errors[]` payload all return `Optional.empty()` (a no-op refresh) — a
  carrier hiccup never fails the admin request or mutates the shipment.

### 1.4 Status Mapping (`CarrierStatusMapper`, TASK-BE-362 + BE-364)

Delivery Tracker `TrackEventStatusCode` (unified scheme) → domain `ShippingStatus`.
Added to the existing aggregator table (English enum is absorbed by the existing
vocab-tolerant normaliser):

| `lastEvent.status.code` | → `ShippingStatus` | Note |
|---|---|---|
| `INFORMATION_RECEIVED` | (unmapped → no-op) | waybill registered, not yet in custody; shipment is already `SHIPPED` locally |
| `AT_PICKUP` | `SHIPPED` | aggregator took custody |
| `IN_TRANSIT` | `IN_TRANSIT` | moving between hubs |
| `OUT_FOR_DELIVERY` | `IN_TRANSIT` | out for last-mile |
| `AVAILABLE_FOR_PICKUP` | `IN_TRANSIT` | at pickup point / locker |
| `ATTEMPT_FAIL` | (unmapped → no-op) | delivery attempt failed; forward-only, no regress |
| `DELIVERED` | `DELIVERED` | handed to recipient |
| `EXCEPTION` / `UNKNOWN` / `NOT_FOUND` | (unmapped → no-op) | best-effort empty |

An unmapped **non-blank** code is observable (`carrier_status_unmapped` counter +
WARN with the raw code, TASK-BE-362 F1) so a new/changed Delivery Tracker code
never silently stalls a shipment. Forward-only advance is enforced downstream
(`ShippingForwardAdvancer`) — a backward/skip mapping is ignored.

### 1.5 Resilience (I1–I3)

| Property | Value | Rationale |
|---|---|---|
| `connectTimeout` | **2 s** | aggregator must resolve + handshake fast |
| `readTimeout` | **5 s** (track), **5 s** (token) | tracker.delivery p99 < 1s; 5s = generous buffer (vendor recommends ≤15s) |
| Retry | none in v1 (best-effort no-op absorbs failure) | the scheduler re-polls next tick; admin can re-press refresh — retry would only delay the no-op |
| Circuit breaker | none in v1 | best-effort empty already fails fast & safe; revisit if call volume grows |

`RestClient` built via `ResilienceClientFactory` (timeouts), reused for both the
token and GraphQL calls. No carrier/GraphQL SDK is added (plain `RestClient` +
hand-built request/response DTOs).

### 1.6 Failure Modes

| Scenario | Adapter result | Observable |
|---|---|---|
| Token endpoint 4xx/5xx/timeout | `Optional.empty()` (no GraphQL call) | WARN `delivery_tracker_token_failed`; `carrier_tracking_fetch{result=auth_failed}` |
| GraphQL transport/timeout/5xx | `Optional.empty()` | WARN; `…{result=transport_failed}` |
| GraphQL 200 with `errors[]` | `Optional.empty()` | WARN (raw error); `…{result=graphql_error}` |
| `track == null` / no `lastEvent` | `Optional.empty()` | DEBUG; `…{result=no_event}` |
| Mapped status | forward-advance + `ShippingStatusChanged` | `…{result=advanced}` |
| Non-blank unmapped status | no-op | `carrier_status_unmapped{code}` (BE-362) |
| Blank credential (disabled) | adapter bean not active | n/a (net-zero) |

---

## 2. Delivery Tracker — Inbound Thin-Ping Webhook (TASK-BE-365, follow-on)

Delivery Tracker's `Mutation.registerTrackWebhook(carrierId, trackingNumber,
callbackUrl, expirationTime)` registers a callback. **Important vendor
constraint**: the callback body carries only `{ carrierId, trackingNumber }` —
**no status, no signature** (the vendor states HMAC is "contact us" only). This
is a different paradigm from the legacy status-carrying HMAC webhook (§3).

Handling (when BE-365 lands):

- Callback is a **thin ping** ("something changed → re-pull"): on receipt the
  service runs the §1 GraphQL `track` query for `{carrierId, trackingNumber}` and
  forward-advances using the **same** `CarrierAdvanceProcessor` as pull/scheduler.
- Authentication: since the vendor signs nothing, the `callbackUrl` embeds an
  unguessable **path-token** shared secret
  (`/api/shippings/carrier-webhook/delivery-tracker/{token}`); a mismatched/blank
  token → 401 (fail-closed). The body is treated as untrusted (re-pull is the
  source of truth, so a spoofed ping at worst triggers a redundant best-effort
  read).
- Idempotency: re-pull is naturally idempotent (forward-only advance); no
  `deliveryId` is available from the vendor, so dedup keys on
  `(trackingNumber, last-known-status)` to suppress redundant work.

**Status: not implemented in BE-364** — this section specifies the target so the
adapter (§1) and the legacy webhook (§3) stay coherent. BE-364 ships outbound
pull only; the existing BE-360 scheduler already converges shipments without the
webhook (webhook is a latency optimisation, not a correctness requirement).

---

## 3. Generic Aggregator Webhook — Inbound HMAC (TASK-BE-294, retained)

The original `POST /api/shippings/carrier-webhook` accepts a **status-carrying**
payload `{ deliveryId, shippingId, status }` authenticated by **HMAC-SHA256**
(`X-Carrier-Signature: sha256=<hex>`, `shipping.carrier.webhook.secret`,
blank = fail-closed). Idempotent by `deliveryId`
(`processed_carrier_webhooks`, retention sweep TASK-BE-361).

This path is **retained** for a direct-connect carrier or an alternate aggregator
that pushes signed status events (the common industry shape). Delivery Tracker
does not use this path (its webhook is the thin-ping of §2). Gateway public-route
exposure: TASK-BE-359. Both inbound paths share `ShippingForwardAdvancer`.

---

## 4. Kafka Cluster

- **Consume**: `order.order.confirmed` (→ idempotent shipping record creation).
- **Publish**: `shipping.shipping.status-changed` (`ShippingStatusChanged`,
  via `libs/java-messaging` transactional outbox) — consumed by
  `notification-service`.
- Auth SASL/SCRAM (dev/stg) / mTLS (prod); manual ACK after TX commit;
  outbox publisher observes committed rows (no distributed TX, `transactional` T2).

Full schemas: [`specs/contracts/events/shipping-events.md`](../../contracts/events/shipping-events.md).

---

## 5. PostgreSQL

Outbound (read+write), one logical DB. Owns the shipping aggregate + status
history, the Kafka outbox table, `processed_carrier_webhooks` (V5, webhook
dedup), and `shedlock` (V6, auto-collect single-instance lock). HikariCP,
Flyway migrations under `apps/shipping-service/src/main/resources/db/migration/`.
JPA entities package-private in the persistence adapter (domain stays pure,
invariant 5).

---

## 6. Aggregated Resilience Policy

| Vendor | Timeout (connect/read) | Retry | Idempotency | Auth | Failure mode |
|---|---|---|---|---|---|
| **Delivery Tracker (pull)** | **2s / 5s** | none (best-effort no-op) | forward-only advance | OAuth2 `client_credentials` (cached token) | `Optional.empty()` no-op + metric |
| Delivery Tracker (thin-ping, BE-365) | (re-pull = §1) | none | re-pull idempotent | path-token | 401 on bad token; re-pull no-op |
| Generic webhook (HMAC) | n/a (we receive) | n/a (caller-side) | `deliveryId` dedup | HMAC-SHA256 | 401 fail-closed |
| Kafka producer | (broker) | outbox absorbs | `eventId` downstream | SCRAM/mTLS | outbox stays unpublished |
| Kafka consumer | (broker session) | 3 in-process then DLT | `orderId` unique + dedup | SCRAM/mTLS | `<topic>.DLT` |
| PostgreSQL | 5s / (statement) | TX-retry on conflict | n/a | password | failure → 5xx |

No pool is shared across vendors (`integration-heavy` I9): the Delivery Tracker
`RestClient`, Kafka producer/consumer pools, and HikariCP are independent.

---

## 7. Observability (Cross-Vendor)

| Metric | Vendor | Description |
|---|---|---|
| `carrier_tracking_fetch{result=advanced\|no_event\|auth_failed\|transport_failed\|graphql_error}` | DT-pull | counter of pull outcomes |
| `carrier_status_unmapped{code}` | DT-pull | non-blank unmapped status (BE-362 F1) |
| `carrier_auto_collect_processed{outcome}` | DT-pull | per-tick scheduler counts (BE-360) |
| `delivery_tracker_token_failed` | DT-pull | OAuth2 token fetch failures |
| `shipping.webhook.received{result}` | webhook | HMAC webhook outcomes (BE-294/359) |
| `shipping.outbox.pending.count` | Kafka-out | unpublished outbox rows |

Logs (structured): `delivery_tracker_track_*` (started/succeeded/failed),
`carrier_status_unmapped` (WARN, raw code). Tracing: the GraphQL call is a child
span `carrier.track` of the refresh / scheduler trace, carrying `shippingId`,
`carrierId`, `trackingNumber`.

---

## 8. Test Suite (per `integration-heavy` I10)

| Path | Framework |
|---|---|
| **Delivery Tracker pull adapter** | **MockWebServer** — OAuth2 token (200 / 401), GraphQL (mapped status, unmapped, `errors[]`, `track:null`, timeout). No real tracker.delivery in tests |
| OAuth2 token provider | MockWebServer — token cache reuse, near-expiry re-fetch, fail → empty |
| `CarrierStatusMapper` | unit — Delivery Tracker enum table + unmapped → empty |
| Auto-collect scheduler | existing BE-360 slice (ShedLock guard) re-runs GREEN over the new adapter |
| Kafka publish / consume | Testcontainers Kafka |
| net-zero regression | blank credential → adapter inactive; `mode=mock` path unchanged |

Demo (non-test): either free `console.tracker.delivery` credentials for a real
call, or a local GraphQL stub mirroring the `track` response shape (key-free,
reproducible). The adapter is written to the real contract regardless.

---

## 9. Not In v1 (BE-364 scope boundary)

- Thin-ping webhook (§2) — TASK-BE-365 follow-on.
- GraphQL event **history** (only `lastEvent.status.code` consumed).
- Circuit breaker / retry on the pull adapter (best-effort no-op suffices).
- `registerTrackWebhook` registration lifecycle (expiry renewal) — with §2.
- wms TMS dispatch integration (ADR-007 D3 — separate domain/owner).
- Multi-aggregator routing (one aggregator per deployment in v1).

---

## References

- [`ADR-007`](../../../docs/adr/ADR-007-logistics-aggregator-carrier-integration.md) — aggregator decision (D1 single-port, D2 aggregator, D3 wms separation, D4 net-zero)
- [`overview.md`](overview.md) — service responsibilities + carrier integration summary
- [`architecture.md`](architecture.md) — Architecture Style, Dependencies, Testing Requirements
- [`specs/contracts/http/shipping-api.md`](../../contracts/http/shipping-api.md) — published HTTP surface (refresh-tracking, carrier-webhook)
- [`specs/contracts/events/shipping-events.md`](../../contracts/events/shipping-events.md) — `ShippingStatusChanged`
- `.claude/skills/backend/external-http-integration` — provider-agnostic HTTP adapter playbook (MONO-234)
- ADR-005 (workload identity) — `client_credentials` token provider precedent
- `rules/traits/integration-heavy.md` — I1–I10
- [Delivery Tracker docs](https://tracker.delivery/en/docs/tracking-api) — vendor wire contract
