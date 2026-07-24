# TASK-SCM-BE-045 — logistics dispatch lookup by shipmentId (D8 prerequisite)

**Status:** done
**Type:** TASK-SCM-BE
**Depends on / 전제:** [TASK-SCM-BE-044](../done/TASK-SCM-BE-044-shipping-confirmed-consumer-fulfillment-router.md) **done** (the consumer that creates dispatch rows; `DispatchPersistencePort.findByShipmentId` already exists for its layer-2 dedup) · [ADR-MONO-053](../../../../docs/adr/ADR-MONO-053-logistics-service-multimodal-fulfillment.md) **ACCEPTED** §D8.
**후속 / blocks:** the **D8 wms TMS retirement** monorepo task (wms `:retry-tms-notify` removal + platform-console repoint). That task **cannot proceed without this endpoint** — see § Why.

> **Small, purely additive slice.** One new read endpoint + its contract row + a slice test. No behaviour change to any existing path, no schema change, no new dependency. It exists to unblock D8 safely: it lands **alone** (nothing breaks), so the D8 cross-project retirement can then be a single atomic PR.

---

## Why (the D8 blocker this removes)

`platform-console`'s operator action **"TMS 재시도"** currently proxies
`POST /api/wms/outbound/{orderId}/retry-tms` → resolves the order's `shipmentId`
from the wms admin read-model → `POST /shipments/{shipmentId}:retry-tms-notify`
on **wms** (TASK-PC-FE-087, `console-integration-contract.md` §2.4.5.1 op 10).

ADR-053 §D8 relocates that recovery surface to logistics
`POST /api/v1/logistics/dispatches/{id}:retry`. But the console holds a
**`shipmentId`**, and logistics is keyed by **dispatch id** — today there is
**no way to get from a shipmentId to a dispatch**, so the console repoint is
impossible and the wms endpoint cannot be retired.

The domain already has the mapping (`dispatch.shipment_id` is UNIQUE and
`DispatchPersistencePort.findByShipmentId` is used by the BE-044 consumer's
layer-2 dedup). This task simply **exposes it as a read endpoint**.

## Scope

**In scope:**

1. **Contract first** — add the new route row to
   `projects/scm-platform/specs/contracts/http/gateway-public-routes.md`
   § `logistics-service` endpoint table (currently lists only `GET /{id}` and
   `POST /{id}:retry`). Contract precedes implementation (CLAUDE.md Contract Rule).
2. **Endpoint** — `GET /api/v1/logistics/dispatches/by-shipment/{shipmentId}`
   (external) → `GET /api/logistics/dispatches/by-shipment/{shipmentId}` (internal,
   via the existing gateway RewritePath). Returns the **same** `DispatchResponse`
   envelope as `GET /{id}`; **404 `DISPATCH_NOT_FOUND`** when no dispatch exists for
   that shipment. Literal `by-shipment` segment (not a `?shipmentId=` filter) so no
   list semantics are introduced and there is no ambiguity against the `{id}` path.
3. **Wiring** — `DispatchController` method delegating to the existing
   `DispatchPersistencePort.findByShipmentId(...)`. Add the port/adapter method only
   if it is not already exposed on the port interface (the consumer uses it, so it
   most likely already is — **do not duplicate it**).
4. **Tests** — `@WebMvcTest DispatchController` slice: found → `200` with the same
   body shape as `GET /{id}`; absent → `404 DISPATCH_NOT_FOUND`. Tenant fail-closed
   is already covered by the existing security config (no new auth surface — same
   controller, same `/api/v1/logistics/**` gateway route + OAuth2 RS rules).

**Out of scope:**
- The wms `:retry-tms-notify` retirement and the console repoint → **the D8 monorepo task** (this endpoint is its prerequisite, nothing more).
- Any change to `POST /{id}:retry` semantics, the dispatch state machine, vendors, or the consumer.
- A dispatch **list/search** surface (pagination, filters) — not needed; the console resolves exactly one shipment.
- A dedicated `logistics-api.md` contract file — logistics routes are documented in `gateway-public-routes.md` as shipped by BE-042; keep that home (a split is a separate refactor-spec decision).

## Acceptance Criteria

- [ ] `gateway-public-routes.md` § `logistics-service` table lists the new `GET .../dispatches/by-shipment/{shipmentId}` row **before** the code lands (contract-first), with its 404 behaviour noted.
- [ ] `GET /api/logistics/dispatches/by-shipment/{shipmentId}` returns `200` + the same `DispatchResponse` envelope as `GET /{id}` for an existing dispatch.
- [ ] Unknown / no-dispatch shipmentId → `404 DISPATCH_NOT_FOUND` (the already-registered error code — **no new code**).
- [ ] Slice test covers both branches; existing `GET /{id}` and `:retry` tests still pass unchanged.
- [ ] No new error code, no schema/Flyway change, no new dependency, no change to the consumer or dispatch state machine.
- [ ] scm Integration + Build & Test CI lanes **GREEN** (CI is authority for IT).

## Related Specs

- `projects/scm-platform/specs/services/logistics-service/architecture.md` § Service Type Compliance → rest-api (operator surface: inspect + re-drive; this adds a second inspect key)
- `docs/adr/ADR-MONO-053-logistics-service-multimodal-fulfillment.md` §D8 (the relocation this unblocks)

## Related Contracts

- `projects/scm-platform/specs/contracts/http/gateway-public-routes.md` § `logistics-service` — **must be updated first** (the endpoint table at the `/api/v1/logistics/**` entry)
- `projects/platform-console/specs/contracts/console-integration-contract.md` §2.4.5.1 op 10 — the **consumer-to-be**; **not modified by this task** (the console still calls wms until the D8 task flips it)

## Edge Cases

- **`shipment_id` is UNIQUE** — the lookup returns at most one dispatch; do not model it as a collection.
- **A shipment with no dispatch is normal**, not an error condition per se: it means the seam event has not arrived (or wms never shipped it). Return `404 DISPATCH_NOT_FOUND` so the console can render an inline actionable state — mirroring the console's existing `SHIPMENT_NOT_FOUND` handling.
- **Do not re-add a port method that already exists.** The BE-044 consumer already uses `findByShipmentId` for layer-2 dedup; reuse it rather than adding a parallel query.
- **No auth divergence** — same controller/route prefix, so the existing `tenant_id=scm` fail-closed + entitlement dual-accept applies unchanged. Do not add a bespoke gate.

## Failure Scenarios

- **A — Scope creep into D8.** If the wms retirement or the console repoint appears in this diff, split it out; this task must be safely mergeable **alone**.
- **B — Contract written after code.** The `gateway-public-routes.md` row must precede/accompany the endpoint (specs win). A code-only endpoint is undocumented API surface.
- **C — New error code invented.** Use the already-registered `DISPATCH_NOT_FOUND`; adding a code would need registry updates in `rules/domains/scm.md` + `platform/error-handling.md` for no benefit.
- **D — List semantics leak in.** A paginated/filterable collection endpoint is out of scope and would widen the public surface without a consumer.

---

**Recommended models** (분석=Opus 4.8 / 구현 권장): one additive read endpoint reusing an existing port method + a contract row + a slice test — mechanical and well-bounded → **Sonnet** sufficient. Escalate only if the port method turns out to be absent and the persistence layer needs a new query.
