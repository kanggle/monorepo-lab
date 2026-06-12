# TASK-BE-294 — Shipping carrier webhook ingestion (inbound leg)

**Status:** ready

**Type:** TASK-BE
**Analysis model:** Opus 4.8 / **Recommended impl model:** Opus (inbound idempotent integration + transactional status advance + signature auth)

---

## Goal

Add the **inbound** half of the shipping-service carrier integration that the spec
reserves as v2 ("External carrier API 통합 … webhook 수신" — [overview.md](../../specs/services/shipping-service/overview.md)).
[TASK-BE-293](../done/TASK-BE-293-shipping-carrier-tracking-integration.md) delivered the
**outbound** leg (admin-triggered pull via `CarrierTrackingPort`). This increment adds a
**carrier-pushed webhook** endpoint: the carrier POSTs a delivery event, we verify it,
deduplicate it (carriers retry), and advance the shipment forward — completing the
bidirectional carrier-integration story without an unattended scheduler.

Architecturally **new vs BE-293**: inbound + **idempotent** (delivery-id dedup) +
**signature-authenticated** (no gateway `X-User-Role`; the shared-secret HMAC *is* the
auth). It **reuses** the proven domain pieces unchanged — `CarrierStatusMapper` and the
forward-only multi-step advance (extracted to a shared `ShippingForwardAdvancer` so BE-293's
refresh and this webhook share one implementation). The domain transition rules and the
`ShippingStatusChanged` event contract are **unchanged**.

## Scope

**In scope (shipping-service only):**
1. `ShippingForwardAdvancer` (application/service, pure helper) — advance a `Shipping`
   forward through the linear `PREPARING → SHIPPED → IN_TRANSIT → DELIVERED` chain toward a
   goal, one valid step at a time; forward-only (goal at/behind = unchanged); returns the
   original status on a net change else empty. `RefreshTrackingService` (BE-293) refactored
   to use it — **behaviour-identical**, its existing tests are the safety net.
2. `WebhookDeliveryStore` (application/port) — `boolean registerIfFirst(deliveryId)`:
   true on first sight, false if already processed (idempotency). Best-effort on concurrent
   insert (treated as already-seen).
3. `ProcessCarrierWebhookService` (application/service, `@Transactional`) — dedup-first;
   maps the raw status (`CarrierStatusMapper`); resolves the shipment by **our `shippingId`**
   (the carrier echoes the client-reference we registered — `findById`, no new repo method);
   **no-op** when the delivery is a duplicate, the status is unknown/unmapped, the shipment
   is unknown, has no tracking/carrier yet, or the carrier status is not ahead (forward-only);
   else advances and publishes one consolidated `original → final` `ShippingStatusChanged`.
   Outcome enum `{ADVANCED, IGNORED, DUPLICATE}`.
4. `CarrierWebhookVerifier` (interfaces/rest/security) — HMAC-SHA256 over the raw body vs the
   `X-Carrier-Signature: sha256=<hex>` header, constant-time (`MessageDigest.isEqual`).
   Blank/unset `shipping.carrier.webhook.secret` ⇒ **reject all (fail-closed = net-zero OFF)**.
   Missing/mismatched signature ⇒ `WebhookSignatureException` → 401.
5. `CarrierWebhookController` (interfaces/rest) — `POST /api/shippings/carrier-webhook`,
   `@RequestBody String` (raw body for HMAC); verify → parse → ingest; returns **200** for
   every validly-signed delivery (ADVANCED / IGNORED / DUPLICATE all ack), **401** on bad
   signature, **400** on malformed body. Separate controller (ShippingController untouched).
6. `ProcessedCarrierWebhookJpaEntity` + `…JpaRepository` + `JpaWebhookDeliveryStore`
   (infrastructure/webhook) over a dedicated `processed_carrier_webhooks` table (V5) — kept
   separate from `processed_events` (distinct retention; the event cleanup scheduler must not
   purge webhook dedup markers).
7. `application.yml` — `shipping.carrier.webhook.secret` (blank default = OFF). `overview.md`
   — record the inbound webhook leg + endpoint.
8. Tests: `ShippingForwardAdvancerTest` (advance multi-step / not-ahead / behind = unchanged),
   `CarrierWebhookVerifierTest` (valid HMAC / mismatch / missing / unconfigured), 
   `ProcessCarrierWebhookServiceTest` (advance+event, duplicate=no-op+no event, unknown status,
   unknown shipment, no tracking, not-ahead), `CarrierWebhookControllerTest` slice
   (200 advanced, 401 bad signature, 400 malformed). `RefreshTrackingServiceTest` unchanged & green.

**Out of scope:**
- The unattended **auto-collect scheduler** (poll in-flight shipments) — later increment (ShedLock).
- Real provider (CJ대한통운 / Lotte) webhook payload mapping + credential provisioning; gateway
  public-route config for the webhook path.
- Webhook dedup-marker **retention/cleanup** sweep (the table grows; pruning is a follow-on).
- Any change to the domain transition rules, the `ShippingStatusChanged` contract, the
  order/notification consumers, the manual admin endpoints, or BE-293's carrier port/adapters.

## Acceptance Criteria

- **AC-1 (advance)** — a validly-signed delivery whose mapped status is ahead advances the
  shipment step-by-step (history records intermediates), persists, publishes one
  `ShippingStatusChanged(original → final)`, returns 200.
- **AC-2 (idempotent)** — re-delivering the same `deliveryId` is a no-op (DUPLICATE): no
  second advance, no second event; returns 200. A failed processing (rollback) leaves the
  delivery un-recorded so a retry can re-process (dedup shares the service transaction).
- **AC-3 (forward-only / best-effort no-op)** — unknown/unmapped status, unknown shipment, a
  shipment with no tracking/carrier yet, or a status at/behind current ⇒ unchanged, no event,
  200. Shipments never regress.
- **AC-4 (signature auth, fail-closed)** — missing or mismatched `X-Carrier-Signature` ⇒ 401,
  no shipment access. Blank/unset `shipping.carrier.webhook.secret` ⇒ **all** webhooks 401
  (net-zero OFF by default). Valid HMAC-SHA256 ⇒ accepted. Malformed body (post-verify) ⇒ 400.
- **AC-5** — `:shipping-service:check` BUILD SUCCESSFUL (unit + slice); CI Build & Test GREEN.
  Default config (blank secret) = net-zero: existing shipping behaviour/tests unchanged.

## Related Specs

- `projects/ecommerce-microservices-platform/specs/services/shipping-service/overview.md` (updated here — inbound webhook leg; `integration-heavy` trait)
- `projects/ecommerce-microservices-platform/PROJECT.md` (trait `integration-heavy` — "택배 트래킹 … idempotent side-effect")

## Related Contracts

- `specs/contracts/http/shipping-api.md` (the new `carrier-webhook` endpoint is additive — inbound, signature-authenticated).
- `specs/contracts/events/shipping-events.md` — `ShippingStatusChanged` reused unchanged.

## Edge Cases

- **Duplicate delivery** (carrier retry) — dedup by `deliveryId` → DUPLICATE no-op, 200.
- **Carrier ahead by >1 step** — advance step-by-step appending history; one net event.
- **Status == current / behind** — no-op (forward-only).
- **Unknown shipment / unknown carrier vocab / no tracking yet** — best-effort no-op, 200.
- **Malformed JSON / blank deliveryId|shippingId|status** — 400 (after signature passes).
- **Missing / wrong signature / unconfigured secret** — 401, no shipment access.

## Failure Scenarios

- **F1 — replay** — a re-sent delivery double-advancing a shipment. Guarded by AC-2 (deliveryId dedup) + forward-only.
- **F2 — forged webhook** — an unauthenticated caller mutating shipments. Guarded by AC-4 (HMAC, fail-closed on unset secret).
- **F3 — regression** — a carrier reporting an earlier status moving a shipment backward. Guarded by AC-3 (forward-only) + the domain's unidirectional `canTransitionTo`.
- **F4 — dedup marker leak on failure** — recording the delivery before a failed advance would wedge a stuck shipment. Guarded by AC-2 (dedup + advance share one transaction; rollback un-records).
- **F5 — net-zero regression** — a default-on secret enabling unsigned mutation. Guarded by AC-4 (blank secret = reject all).
