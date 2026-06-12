# TASK-BE-293 — Shipping carrier tracking integration (first increment)

**Status:** ready

**Type:** TASK-BE
**Analysis model:** Opus 4.8 / **Recommended impl model:** Opus (transactional status advance + external integration)

---

## Goal

Begin the shipping-service **external carrier integration** that the spec reserves as v2 ("External carrier API 통합 … tracking 자동 수집 — v2, `integration-heavy` trait pattern 적용 시" — [overview.md](../../specs/services/shipping-service/overview.md)). v1 is admin-driven manual status transitions only. This first increment adds a **real outbound carrier-tracking integration** behind a port, and an **admin-triggered `refresh-tracking`** that reads the carrier's latest status and advances the shipment forward — proving the integration deterministically without an unattended scheduler (which is a later increment).

Reuses the proven real-HTTP-adapter pattern (provider-agnostic, `ResilienceClientFactory` RestClient, property-gated mock/real, best-effort/never-throw, MockWebServer unit test). The domain transition rules and event contract are **unchanged** — the refresh advances through the existing linear chain and publishes the existing `ShippingStatusChanged` event.

## Scope

**In scope (shipping-service only):**
1. `CarrierTrackingPort` (application/port) — `Optional<CarrierTrackingSnapshot> fetchLatest(carrier, trackingNumber)`; best-effort contract (empty on any failure, never throws).
2. `HttpCarrierTrackingAdapter` (infrastructure/carrier) — `@ConditionalOnProperty(shipping.carrier.mode=http)`; GETs `${base-url}/track?carrier=&trackingNumber=` with an API-key bearer via a `ResilienceClientFactory` RestClient; reads `status`; catches everything → empty. No carrier SDK.
3. `MockCarrierTrackingAdapter` (infrastructure/carrier) — `@ConditionalOnProperty(mock, matchIfMissing=true)`; returns the configured `shipping.carrier.mock-status` or empty (blank default ⇒ carrier integration OFF = net-zero, the v1 baseline). Exactly one `CarrierTrackingPort` bean per mode.
4. `CarrierStatusMapper` (pure) — maps a carrier raw status (tolerant of vocab/separators) → `ShippingStatus`; unknown/blank → empty.
5. `RefreshTrackingService` (application/service) — admin-gated; loads the shipment; **no-op** when it has no tracking/carrier yet, or the carrier returns empty/unknown, or the carrier status is not ahead of the current status (**forward-only**, shipments never regress); otherwise advances through the linear `PREPARING → SHIPPED → IN_TRANSIT → DELIVERED` chain one valid step at a time and publishes one consolidated `original → final` `ShippingStatusChanged` event.
6. `POST /api/shippings/{shippingId}/refresh-tracking` (controller) — `X-User-Role: ADMIN`; returns the (possibly unchanged) `UpdateShippingStatusResponse`.
7. `application.yml` — `shipping.carrier.{mode,base-url,api-key,mock-status,connect-timeout-ms,read-timeout-ms}`; `mode` defaults to `mock` (net-zero). `build.gradle` — `mockwebserver`/`okhttp` test deps.
8. `overview.md` — record the first carrier increment + the new endpoint; the auto-collect scheduler stays v2.
9. Tests: `CarrierStatusMapperTest` (pure), `HttpCarrierTrackingAdapterTest` (MockWebServer: 2xx→snapshot+bearer/query, 5xx/down/no-status→empty+no-throw), `RefreshTrackingServiceTest` (advance+net event, carrier-unavailable no-op, not-ahead no-op, no-tracking no-op + carrier not called, non-admin rejected).

**Out of scope:**
- The unattended **auto-collect scheduler** (poll all in-flight shipments) — a later increment (avoids ShedLock IT complexity here).
- Carrier **webhook** ingestion; real provider (CJ대한통운 / Lotte) endpoint wiring + credentials.
- Any change to the domain transition rules, the `ShippingStatusChanged` contract, the order/notification consumers, or the existing manual admin endpoints.
- A new dependency beyond the MockWebServer test libs / a carrier SDK.

## Acceptance Criteria

- **AC-1 (advance)** — with `mode=http` (or a `mock-status`), a SHIPPED shipment whose carrier reports `DELIVERED` advances to DELIVERED (history records the intermediate IN_TRANSIT), persists, and publishes one `ShippingStatusChanged(SHIPPED → DELIVERED)`; the endpoint returns 200 with the new status.
- **AC-2 (best-effort no-op)** — a carrier outage / unknown / unmapped status (port empty) leaves the shipment unchanged, no event, **no throw**; the admin request still returns 200 with the current status. The HTTP adapter returns empty on 5xx / transport / timeout / no-`status` body.
- **AC-3 (forward-only)** — a carrier status at or behind the current status is a no-op (shipments never regress).
- **AC-4 (net-zero default)** — `mode` unset/`mock` with a blank `mock-status` ⇒ refresh is a no-op, the carrier port is the mock, exactly one `CarrierTrackingPort` bean; existing shipping behaviour/tests unchanged. A shipment with no tracking/carrier yet is a no-op **without calling the carrier**.
- **AC-5** — non-ADMIN `X-User-Role` ⇒ rejected (AccessDenied), no shipment access. `:shipping-service:check` BUILD SUCCESSFUL; CI shipping integration GREEN (default mock — no regression).

## Related Specs

- `projects/ecommerce-microservices-platform/specs/services/shipping-service/overview.md` (updated here — carrier integration first increment; `integration-heavy` trait)
- `projects/ecommerce-microservices-platform/PROJECT.md` (trait `integration-heavy` — "택배 트래킹 … Circuit breaker, retry, DLQ, idempotent side-effect")

## Related Contracts

- `specs/contracts/http/shipping-api.md` (the new `refresh-tracking` endpoint is additive). `specs/contracts/events/shipping-events.md` — `ShippingStatusChanged` reused unchanged.

## Edge Cases

- **No tracking/carrier yet** (PREPARING) — no-op, carrier not called.
- **Carrier ahead by >1 step** (SHIPPED → carrier says DELIVERED) — advance step-by-step through the linear chain (each a valid single transition appending history); publish one net event.
- **Carrier status == current / behind** — no-op (forward-only).
- **2xx without `status`, 5xx, timeout, connection refused** — best-effort empty, no throw, no mutation.
- **Unknown carrier vocab** — `CarrierStatusMapper` → empty → no-op (surfaces unmapped statuses as inaction, not a crash).

## Failure Scenarios

- **F1 — a carrier hiccup mutating/failing the request** — if the adapter threw or the service applied a bad status, an admin refresh during a carrier outage could regress or error. Guarded by AC-2/AC-3 (best-effort empty + forward-only) + the adapter catch-all.
- **F2 — shipment regression** — a carrier reporting an earlier status must never move a shipment backward. Guarded by AC-3 (ordinal forward-only) + the domain's unidirectional `canTransitionTo`.
- **F3 — net-zero regression** — if `mode` defaulted to a non-empty status, every refresh would advance shipments unbidden. Guarded by AC-4 (`mock` + blank `mock-status` = no-op).
- **F4 — two carrier beans (or zero)** — a mis-gated conditional. Guarded by the mutually-exclusive `@ConditionalOnProperty` (`matchIfMissing=true` on mock).
