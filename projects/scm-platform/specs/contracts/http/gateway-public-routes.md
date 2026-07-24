---
status: live
last_updated: 2026-06-13
owners: scm-platform/backend
---

# scm-platform — Public Gateway Routes

> **Status**: v1 live catalogue as of 2026-05-11. gateway-service
> (TASK-SCM-BE-001), procurement-service (TASK-SCM-BE-002), and
> inventory-visibility-service (TASK-SCM-BE-003) are all shipped. This file
> was reconciled against the live controller surfaces by TASK-SCM-BE-008
> (inventory-visibility) and TASK-SCM-BE-006 (procurement findings #1).

This document enumerates every externally reachable HTTP route on
`http://scm.local/` (Traefik-routed). All routes are owned by
[gateway-service](../../services/gateway-service/architecture.md) at the
edge; the listed downstream services own the actual handlers once they
exist.

## Authentication contract

Every route under `/api/**` requires:

- `Authorization: Bearer <RS256 JWT>` issued by IAM.
- JWT signature verifies against IAM's JWKS (`http://iam.local/oauth2/jwks`).
- `iss` claim is one of: SAS issuer URL (default `http://iam.local`), legacy
  `iam-platform` (D2-b deprecation window).
- `tenant_id` claim is `scm` or `*` (SUPER_ADMIN wildcard). Any other tenant
  → 403 `TENANT_FORBIDDEN`.

scm v1 = backend only — the **primary** authentication shape is
`grant_type=client_credentials` against the V0013-seeded
`scm-platform-internal-services-client`. Human-user (PKCE / authorization_code)
flow is deferred to v2 when a frontend ships.

### platform-console operator read consumer (ADR-MONO-013 Model B)

The "v1 = backend only / human-flow deferred to v2" statement above scopes
**scm hosting its own frontend** and **registering its own
`scm-platform-user-flow-client`** (V0013 table, DEFERRED). It does **not**
restrict authorized external API consumers. Per
[ADR-MONO-013](../../../../../docs/adr/ADR-MONO-013-platform-console-foundation.md)
(ACCEPTED — § D1 Model B, § D6 Phase 4), the `platform-console` project (a
separate, ADR-MONO-013-governed operator console — **not** an scm frontend) is
a **sanctioned external read consumer** of scm's existing read surface:

| Consumed (read-only) | scm contract |
|---|---|
| `GET /api/v1/procurement/po`, `GET /api/v1/procurement/po/{poId}` | [`procurement-api.md`](./procurement-api.md) |
| `GET /api/v1/inventory-visibility/{snapshot, sku/{sku}, staleness, nodes}` | [`inventory-visibility-api.md`](./inventory-visibility-api.md) |

- **Credential — existing capability, no change.** The console calls these
  **server-side** with a human operator's **IAM `platform-console-web` OIDC
  access token** (RS256, issued by IAM per ADR-001). This token is validated by
  the **already-existing** gateway chain exactly like any IAM RS256 token:
  `AllowedIssuersValidator` + `TenantClaimValidator` (`tenant_id ∈ { scm, * }`)
  + `JwtHeaderEnrichmentFilter` surfacing `X-Token-Type=user` (the human-caller
  shape already specified in [`iam-integration.md`](../../integration/iam-integration.md)
  Edge Case E1/E3). **No new scm OAuth client, no new gateway route, no new
  gateway code, no auth-model change** — this subsection is a reality-alignment
  acknowledgment, not a capability change.
- **Read-only.** The console consumes only the reads above. PO write
  (`/po/{poId}/submit|confirm|cancel`) and the procurement webhooks are
  buyer/machine paths and are **not** console-consumed.
- **Single-org preserved.** scm remains single-organization (the deliberate
  `multi-tenant` non-declaration in [`PROJECT.md`](../../../PROJECT.md) is
  **unaffected**). Tenant scoping stays the IAM `tenant_id` claim enforced by
  the **existing** producer-side `TenantClaimValidator`; cross-tenant tokens are
  rejected `403 TENANT_FORBIDDEN` exactly as today. The console's own
  multi-tenant/audit-heavy obligations are the console's, not scm's.
- **Consumer-only.** scm owns `procurement-api.md` / `inventory-visibility-api.md`
  (authoritative, unchanged). The console-side obligation is specified in
  platform-console
  [`console-integration-contract.md`](../../../../platform-console/specs/contracts/console-integration-contract.md)
  § 2.4.6 (authored by `TASK-PC-FE-008`), reusing its § 2.4.5 per-domain
  credential rule (IAM-token-direct for scm/wms, distinct from IAM's own
  operator-token exchange).
- The deferred `scm-platform-user-flow-client` (V0013 table) stays deferred and
  is **unrelated** — the console uses IAM's **own** `platform-console-web`
  client, never an scm-registered client.

### platform-console operator action consumer — demand-planning replenishment gate (ADR-MONO-027)

The read-consumer subsection above (TASK-SCM-BE-015) is explicitly **read-only**.
The ADR-MONO-027 replenishment loop adds a human **operator gate** — an operator
approves a `SUGGESTED` reorder (which materialises a **DRAFT** PO) or dismisses
it — and that gate is the console's natural operational surface. Per
[ADR-MONO-027](../../../../../docs/adr/ADR-MONO-027-wms-scm-replenishment-loop.md)
§ D2/D5 and ADR-MONO-013 § D1 Model B / § D6, the `platform-console` project is a
**sanctioned external operator-action consumer** of demand-planning's
suggestion surface. This **widens** the SCM-BE-015 read acknowledgment to cover
the demand-planning operator-action routes (TASK-SCM-BE-027):

| Consumed (operator read) | scm contract |
|---|---|
| `GET /api/v1/demand-planning/suggestions`, `GET /api/v1/demand-planning/suggestions/{id}` | [`demand-planning-api.md`](./demand-planning-api.md) |

| Consumed (operator action — the net-new acknowledgment) | scm contract |
|---|---|
| `POST /api/v1/demand-planning/suggestions/{id}/approve`, `POST /api/v1/demand-planning/suggestions/{id}/dismiss` | [`demand-planning-api.md`](./demand-planning-api.md) |

- **Not part of *this* (action) acknowledgment.** The `GET\|PUT /api/v1/demand-planning/policies/{skuCode}`
  and `GET\|PUT /api/v1/demand-planning/sku-supplier-map/{skuCode}` routes are the
  **operator config (seed)** surface, **not** the suggestion operator gate. They are
  acknowledged separately as a console **config** consumer in the next subsection
  (TASK-SCM-BE-028) — they are **not** consumed by this *action* binding.
- **Credential — unchanged, same as the read consumer.** The console calls the
  actions **server-side** with the **same** human-operator **IAM
  `platform-console-web` OIDC access token** (RS256, ADR-001) the reads already
  use — validated by the **already-existing** gateway chain
  (`AllowedIssuersValidator` + `TenantClaimValidator` `tenant_id ∈ { scm, * }` +
  `JwtHeaderEnrichmentFilter` `X-Token-Type=user`). Actions ride the **same**
  token, **not** a privileged exchange: **no new scm OAuth client, no new gateway
  route, no new gateway code, no auth-model change.** scm has no
  operator-token-exchange (unlike IAM's admin-service); the gate is server-side
  `tenant_id=scm` validation plus the DRAFT-PO-only invariant below, not a
  stronger credential.
- **Operator-gate invariant (why exposing this write is safe).** `approve`
  materialises a **DRAFT** PO only (ADR-MONO-027 D5) — **never** an auto-SUBMIT;
  the console action does **not** bypass procurement's existing
  `DRAFT → SUBMITTED` operator step. `dismiss` only releases the open-suggestion
  guard. Both are server-side **idempotent by suggestion state** (re-approve
  returns the existing `poId`, no duplicate PO; re-dismiss is a no-op).
- **Single-org preserved.** scm remains single-organization — the deliberate
  `multi-tenant` non-declaration in [`PROJECT.md`](../../../PROJECT.md) is
  **unaffected**. Tenant scoping stays the IAM `tenant_id` claim enforced by the
  **existing** producer-side `TenantClaimValidator`.
- **Consumer-only.** scm owns [`demand-planning-api.md`](./demand-planning-api.md)
  (authoritative — suggestion read + approve/dismiss shapes, idempotency, error
  codes; **unchanged** by this acknowledgment). The console-side obligation is
  specified in platform-console
  [`console-integration-contract.md`](../../../../platform-console/specs/contracts/console-integration-contract.md)
  § 2.4.6.1 (authored by `TASK-PC-FE-077`), reusing the § 2.4.6 per-domain
  credential rule.

### platform-console operator config (seed) consumer — demand-planning reorder-policy + sku-supplier-map (ADR-MONO-027)

The action subsection above (TASK-SCM-BE-027) deliberately left the
demand-planning **seed** routes out. The replenishment operator gate needs an
operational fix-path: when `approve` fails `SKU_SUPPLIER_UNMAPPED` (no
`sku_supplier_map` row), the operator must be able to add the mapping (and tune
the `reorder_policy`) **from the console** rather than out-of-band. ADR-MONO-027
§ rest-api facet already lists "CRUD the `reorder_policy` / `sku_supplier_map`
seed" as part of the **same operator rest-api surface** as approve. This
subsection acknowledges `platform-console` as a sanctioned **operator config
(seed)** consumer of those routes (TASK-SCM-BE-028):

| Consumed (operator config — the net-new acknowledgment) | scm contract |
|---|---|
| `GET\|PUT /api/v1/demand-planning/policies/{skuCode}` | [`demand-planning-api.md`](./demand-planning-api.md) |
| `GET\|PUT /api/v1/demand-planning/sku-supplier-map/{skuCode}` | [`demand-planning-api.md`](./demand-planning-api.md) |

- **Credential — unchanged, same as the read + action consumers.** The console
  calls these **server-side** with the **same** human-operator **IAM
  `platform-console-web` OIDC access token** the reads/actions use, validated by
  the **already-existing** gateway chain (`AllowedIssuersValidator` +
  `TenantClaimValidator` `tenant_id ∈ { scm, * }` + `JwtHeaderEnrichmentFilter`
  `X-Token-Type=user`). "operator/admin seed" in
  [`demand-planning-api.md`](./demand-planning-api.md) names the *surface*, **not**
  a stronger credential: scm has **no** operator/admin-token split, so config
  rides the same operator token. **No new scm OAuth client, no new gateway route,
  no new gateway code, no new role/scope, no auth-model change.**
- **Config-surface invariant (why exposing this write is safe).** These are
  **upsert (PUT) / inspect (GET) per-SKU** seed rows (`reorder_policy`:
  reorderPoint/safetyStock/reorderQty; `sku_supplier_map`:
  supplierId/defaultOrderQty/leadTimeDays/currency) that feed **future**
  suggestion evaluation only. Editing them does **not** mutate existing
  suggestions or POs, does **not** dispatch anything, and does **not** bypass the
  operator gate. There is **no list route** — access is per-`{skuCode}` GET/PUT.
- **Single-org preserved.** scm remains single-organization — the
  `multi-tenant` non-declaration in [`PROJECT.md`](../../../PROJECT.md) is
  **unaffected**.
- **Consumer-only.** Authoritative shapes/error codes
  (`POLICY_NOT_FOUND`/`MAPPING_NOT_FOUND`/`VALIDATION_ERROR`) stay in
  [`demand-planning-api.md`](./demand-planning-api.md) (**unchanged**). The
  console-side obligation is specified in platform-console
  [`console-integration-contract.md`](../../../../platform-console/specs/contracts/console-integration-contract.md)
  § 2.4.6.2 (authored by `TASK-PC-FE-080`), reusing the § 2.4.6 per-domain
  credential rule.

## Error envelope

All gateway-emitted errors follow `platform/error-handling.md`:

```json
{
  "code": "TENANT_FORBIDDEN",
  "message": "tenant_id 'wms' is not allowed",
  "timestamp": "2026-05-05T00:00:00Z"
}
```

| HTTP | code | When |
|---|---|---|
| 401 | UNAUTHORIZED | missing / expired / invalid signature; tampered token |
| 403 | TENANT_FORBIDDEN | `tenant_id` claim does not match `scm` (and is not `*`) |
| 403 | FORBIDDEN | authorised token but lacks scope/role for the operation (downstream-emitted) |
| 429 | RATE_LIMIT_EXCEEDED | per-account-or-IP quota exhausted; `Retry-After: 1` set |
| 503 | SERVICE_UNAVAILABLE | downstream service unreachable |

## Route catalogue

### `procurement-service` (TASK-SCM-BE-002, shipped)

| Field | Value |
|---|---|
| External path predicate | `Path=/api/v1/procurement/**` |
| Internal target | `${PROCUREMENT_SERVICE_URI:http://procurement-service:8080}` |
| RewritePath | `/api/v1/procurement/(?<segment>.*) → /api/procurement/${segment}` |
| Auth | required (JWT) for `/api/procurement/po/**`; **public** (shared-secret) for `/api/procurement/webhooks/**` |
| Rate limit | `replenishRate=1`, `burstCapacity=120`, key = `accountKeyResolver` |
| Status | live |

Live v1 endpoints (formal contract:
[`procurement-api.md`](./procurement-api.md)):

| Method | External path | Auth | Idempotency |
|---|---|---|---|
| POST | `/api/v1/procurement/po` | JWT | `Idempotency-Key` |
| GET | `/api/v1/procurement/po` | JWT | n/a (search) |
| GET | `/api/v1/procurement/po/{poId}` | JWT | n/a |
| POST | `/api/v1/procurement/po/{poId}/submit` | JWT | `Idempotency-Key` |
| POST | `/api/v1/procurement/po/{poId}/confirm` | JWT | `Idempotency-Key` |
| POST | `/api/v1/procurement/po/{poId}/cancel` | JWT | `Idempotency-Key` |
| POST | `/api/v1/procurement/webhooks/supplier-ack` | shared-secret `X-Supplier-Signature` | `(tenantId, poId)` semantic |
| POST | `/api/v1/procurement/webhooks/asn` | shared-secret `X-Supplier-Signature` | `(tenantId, supplierAsnRef)` UNIQUE |

> **Drift fixed**: the prior placeholder list claimed `POST /po/{poId}/asn`
> (buyer-side) but the shipped implementation delivers ASN via the
> `/api/v1/procurement/webhooks/asn` supplier webhook — caller asymmetry
> matters for clients reading this catalogue. Reconciled by
> TASK-SCM-BE-008 (extending TASK-SCM-BE-006 finding #1).

### `inventory-visibility-service` (TASK-SCM-BE-003, shipped)

| Field | Value |
|---|---|
| External path predicate | `Path=/api/v1/inventory-visibility/**` |
| Internal target | `${INVENTORY_VISIBILITY_SERVICE_URI:http://inventory-visibility-service:8080}` |
| RewritePath | `/api/v1/inventory-visibility/(?<segment>.*) → /api/inventory-visibility/${segment}` |
| Auth | required (JWT) for all endpoints |
| Rate limit | `replenishRate=1`, `burstCapacity=120`, key = `accountKeyResolver` |
| Status | live |

Live v1 endpoints (formal contract:
[`inventory-visibility-api.md`](./inventory-visibility-api.md)). All return
the `meta.warning: "Not for procurement decisions (S5)"` envelope:

| Method | External path | Purpose |
|---|---|---|
| GET | `/api/v1/inventory-visibility/snapshot` | cross-node paginated snapshot list (or single-node when `?nodeId=`) |
| GET | `/api/v1/inventory-visibility/sku/{sku}` | per-SKU cross-node breakdown (Redis cache, `X-Cache` header) |
| GET | `/api/v1/inventory-visibility/staleness` | node-by-node staleness status (FRESH / STALE / UNREACHABLE) |
| GET | `/api/v1/inventory-visibility/nodes` | node list with status (id, externalId, type, name, status) — **public** per TASK-SCM-BE-008 decision (ops dashboard prerequisite) |
| POST | `/api/v1/inventory-visibility/nodes` | explicitly register a `THIRD_PARTY_LOGISTICS` node (201 new / 200 idempotent repeat / 409 `NODE_TYPE_CONFLICT`) — the one mutating endpoint on this read-only API (ADR-MONO-054 §D2, TASK-SCM-BE-046) |
| POST | `/api/v1/inventory-visibility/nodes/{nodeId}/observed-stock` | record an absolute observed-stock reading for an existing `THIRD_PARTY_LOGISTICS` node (200 recorded / 404 `NODE_NOT_FOUND` / 409 `NODE_TYPE_CONFLICT` / 422 `VALIDATION_ERROR`) — read-only ingestion push, no auto-registration (ADR-MONO-054 §D4, TASK-SCM-BE-047) |

### `demand-planning-service` (TASK-SCM-BE-024, shipped — ADR-MONO-027 Phase 1)

| Field | Value |
|---|---|
| External path predicate | `Path=/api/v1/demand-planning/**` |
| Internal target | `${DEMAND_PLANNING_SERVICE_URI:http://demand-planning-service:8080}` |
| RewritePath | `/api/v1/demand-planning/(?<segment>.*) → /api/demand-planning/${segment}` |
| Auth | required (JWT) for all endpoints |
| Rate limit | `replenishRate=1`, `burstCapacity=120`, key = `accountKeyResolver` |
| Status | live |

Live v1 endpoints (formal contract:
[`demand-planning-api.md`](./demand-planning-api.md)). The ADR-MONO-027 operator
gate — `approve` materialises a **DRAFT** PO only (never auto-SUBMIT):

| Method | External path | Publicity | Idempotency |
|---|---|---|---|
| GET | `/api/v1/demand-planning/suggestions` | operator read | n/a (list) |
| GET | `/api/v1/demand-planning/suggestions/{id}` | operator read | n/a |
| POST | `/api/v1/demand-planning/suggestions/{id}/approve` | operator action | server-side by suggestion state (re-approve → existing `poId`) |
| POST | `/api/v1/demand-planning/suggestions/{id}/dismiss` | operator action | server-side by suggestion state (re-dismiss = no-op) |
| GET\|PUT | `/api/v1/demand-planning/policies/{skuCode}` | operator config (console-consumed) | upsert |
| GET\|PUT | `/api/v1/demand-planning/sku-supplier-map/{skuCode}` | operator config (console-consumed) | upsert |

> The `suggestions` read + `approve`/`dismiss` action routes are the
> **platform-console operator-action consumer** surface (TASK-SCM-BE-027); the
> `policies` / `sku-supplier-map` per-SKU seed routes are the **platform-console
> operator config (seed) consumer** surface (TASK-SCM-BE-028). Both are
> acknowledged in the subsections above and ride the same operator IAM OIDC
> token; there is no list route for the seed pair (per-`{skuCode}` only).

### `logistics-service` (TASK-SCM-BE-042, shipped — ADR-MONO-053 Phase 1)

| Field | Value |
|---|---|
| External path predicate | `Path=/api/v1/logistics/**` |
| Internal target | `${LOGISTICS_SERVICE_URI:http://logistics-service:8080}` |
| RewritePath | `/api/v1/logistics/(?<segment>.*) → /api/logistics/${segment}` |
| Auth | required (JWT) for all endpoints |
| Rate limit | `replenishRate=1`, `burstCapacity=120`, key = `accountKeyResolver` |
| Status | live |

Live v1 endpoints (carrier-dispatch operator surface — inspect + re-drive a failed
dispatch; ADR-053 §D2/§D8). The dispatch record itself is created by the wms
`outbound.shipping.confirmed` seam consumer (TASK-SCM-BE-044) — there is **no**
create-dispatch route:

| Method | External path | Purpose | Idempotency |
|---|---|---|---|
| GET | `/api/v1/logistics/dispatches/{id}` | inspect a dispatch by dispatch id | n/a |
| GET | `/api/v1/logistics/dispatches/by-shipment/{shipmentId}` | inspect a dispatch by the shipment it dispatches (`dispatch.shipment_id` unique); no dispatch for that shipment → `404 DISPATCH_NOT_FOUND`. The dispatch-id-free entry point for the wms `:retry-tms-notify` relocation (TASK-SCM-BE-045, ADR-053 §D8) — a `shipmentId`-holding console resolves the dispatch, then calls `:retry`. | n/a |
| POST | `/api/v1/logistics/dispatches/{id}:retry` | re-drive a `DISPATCH_FAILED` dispatch | server-side by dispatch state + `Idempotency-Key={shipment.id}` (already-`DISPATCHED` → cached ack, no vendor call) |

### Local management endpoints

| Path | Auth | Description |
|---|---|---|
| `GET /actuator/health` | none | liveness/readiness probe (Spring Boot defaults) |
| `GET /actuator/info` | none | build info |

`/actuator/prometheus` is **not** publicly exposed — Prometheus scrapes each
service on the internal `scm-platform-net` docker network directly. An
anonymous external request to gateway's `/actuator/prometheus` returns
401 (network isolation contract).

## v2 / deferred routes

These appear once the v2 services bootstrap (separate tasks):

| External path | Owner | Bootstrap task |
|---|---|---|
| `/api/v1/suppliers/**` | supplier-service | deferred |
| `/api/v1/settlement/**` | settlement-service | deferred |
| `/api/v1/admin/**` | admin-service | deferred |

> `/api/v1/logistics/**` (logistics-service) graduated from deferred to **live** in
> TASK-SCM-BE-042 (ADR-MONO-053 Phase 1) — see the route-catalogue entry above.

## References

- [`gateway-service/architecture.md`](../../services/gateway-service/architecture.md)
- [`iam-integration.md`](../../integration/iam-integration.md)
- `platform/api-gateway-policy.md`
- `platform/error-handling.md`
- TASK-SCM-BE-001 — gateway-service bootstrap (this catalogue's authoring task)
- TASK-SCM-BE-002 / TASK-SCM-BE-003 — downstream service bootstraps
- TASK-SCM-BE-024 — demand-planning-service bootstrap (ADR-MONO-027 Phase 1; activated the `/api/v1/demand-planning/**` route)
- TASK-SCM-BE-027 — platform-console operator-**action** consumer acknowledgment (demand-planning replenishment gate) + route-catalogue reconciliation
- TASK-SCM-BE-028 — platform-console operator-**config (seed)** consumer acknowledgment (demand-planning reorder-policy + sku-supplier-map)
- TASK-SCM-BE-042 — logistics-service bootstrap (ADR-MONO-053 Phase 1; activated the `/api/v1/logistics/**` route)
