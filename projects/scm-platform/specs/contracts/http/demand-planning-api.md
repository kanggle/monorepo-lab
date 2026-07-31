# API Contract — demand-planning-service

ADR-MONO-027. Base path `/api/v1/demand-planning` (via gateway-service).
All responses use the scm `{ data, meta }` envelope; errors `{ code, message, timestamp }`
(`libs/java-web.ErrorResponse`).

> **Error-envelope change — TASK-SCM-BE-055 / ADR-MONO-058 § D2.** This line previously
> read `{ code, message, details? }`. Two corrections, both to make it describe what the
> service emits:
> - **`timestamp` added.** Until D2 adoption this service's controller advice emitted only
>   `{ code, message }`, in violation of `platform/error-handling.md § Error Response
>   Format` ("the three fields above must always be present") and inconsistent with its own
>   security-layer `HttpErrorResponseWriter`, which already emitted `timestamp` on 401/403.
>   A 404/422 and a 401 from the same service therefore had different shapes. Adopting the
>   shared `ErrorResponse` closes that. **This is an additive, observable change** — a
>   client parsing `code`/`message` is unaffected; a client asserting an exact key set is
>   not.
> - **`details?` removed.** A repo-wide grep found **zero** call sites populating it in
>   this service; the field never existed on its envelope record at all, so no response it
>   ever emitted carried the key. The `details?` here was aspirational, not observed.
Auth: OAuth2 RS (RS256, IAM JWKS), `tenant_id=scm` fail-closed + entitlement-trust
dual-accept. **Operator surface — tenant-gated, no role split.** These routes are
consumed by the platform-console operator (IAM `platform-console-web` token); the
gate is server-side `tenant_id=scm` (+ entitlement) plus the DRAFT-PO-only
invariant, **not** a stronger credential — scm enforces no operator/admin `roles`
check on demand-planning (the "operator/admin seed" label below names the
*surface*, not a required role). This is consistent with the roles-only identity
model (ADR-MONO-032/035; `account_type`/`X-Account-Type` removed) and is canonical
in [`gateway-public-routes.md`](./gateway-public-routes.md) § platform-console
operator-action / config consumer. (Contrast: procurement `confirm` *does* require
`roles ∋ OPERATOR` — see [`procurement-api.md`](./procurement-api.md) § Actor model.)

## Route publicity

| Route | Publicity |
|---|---|
| `GET /suggestions`, `GET /suggestions/{id}` | gateway-public (operator read) |
| `POST /suggestions/{id}/approve`, `/dismiss` | gateway-public (operator action) |
| `GET\|PUT /policies/{skuCode}`, `GET\|PUT /sku-supplier-map/{skuCode}` | gateway-public (operator/admin seed) |

No mutating webhook; no machine-to-machine write surface in v1 (the only inbound
write is the Kafka consumer, not REST).

## Endpoints

### `GET /api/v1/demand-planning/suggestions`

List reorder suggestions. Paginated.

Query: `status` (`SUGGESTED|APPROVED|MATERIALIZED|DISMISSED`, optional), `skuCode`
(optional), `page`, `size`.

`200`:
```json
{
  "data": [
    {
      "id": "0192...",
      "skuCode": "SKU-APPLE-001",
      "warehouseId": "uuid",
      "supplierId": "uuid",
      "suggestedQty": 100,
      "status": "SUGGESTED",
      "source": "ALERT",
      "triggerAvailableQty": 5,
      "materializedPoId": null,
      "createdAt": "2026-06-11T10:05:00Z"
    }
  ],
  "meta": { "page": 0, "size": 20, "totalElements": 1, "totalPages": 1 }
}
```

> **Wire-shape decision (ADR-MONO-058 § D3 / TASK-SCM-BE-056):** this endpoint's
> `data`/`meta`-split envelope is **preserved unchanged** — `data` stays the bare
> suggestion array, `meta` stays the `{ page, size, totalElements, totalPages }`
> projection. This is `demand-planning-service`'s consistently-applied `ApiEnvelope`
> convention (also used, sans pagination, by `getSuggestion`/`approve`/`dismiss`),
> not a shape invented for this one endpoint — normalizing it away to the
> single-object `content`/`page`/`size`/... shape used by `procurement-service`/
> `inventory-visibility-service` would be a broader service-wide contract change
> outside this task's pagination scope. What changed is **internal only**: the
> controller now builds a `PageQuery` and both `data` and `meta` are populated from
> the shared `PageResult` (via `PageMeta.from(PageResult)`) instead of extracting
> fields directly off Spring Data's `Page<ReorderSuggestion>`. Zero bytes of wire
> response change.

### `GET /api/v1/demand-planning/suggestions/{id}`

`200` single suggestion (shape above). `404 SUGGESTION_NOT_FOUND`.

### `POST /api/v1/demand-planning/suggestions/{id}/approve`

Operator approves → resolves `sku_supplier_map` → procurement creates a **DRAFT**
PO (ADR-027 D5) → suggestion `MATERIALIZED` with `materializedPoId`.

- **Idempotent**: re-approving (or a `MATERIALIZED` suggestion) returns the
  existing `poId` — no duplicate PO.
- Body: optional `{ "note": "..." }`.

`200`:
```json
{ "data": { "id": "0192...", "status": "MATERIALIZED", "poId": "uuid", "poStatus": "DRAFT" }, "meta": {} }
```

Errors:
| Code | HTTP | When |
|---|---|---|
| `SKU_SUPPLIER_UNMAPPED` | 422 | no `sku_supplier_map` row; suggestion stays `SUGGESTED`, no PO. |
| `SUGGESTION_ALREADY_MATERIALIZED` | 409 / 200-idempotent | already materialized — returns existing `poId` (idempotent), or 409 if a hard conflict. |
| `SUGGESTION_NOT_FOUND` | 404 | |
| `INVALID_SUGGESTION_STATE` | 422 | suggestion is `DISMISSED` (cannot approve a dismissed suggestion). |

> The created PO is **DRAFT only** — never auto-SUBMITted. The operator dispatches
> it via procurement's existing `DRAFT → SUBMITTED` flow (unchanged).

### `POST /api/v1/demand-planning/suggestions/{id}/dismiss`

Operator dismisses. `* → DISMISSED`; releases the open-suggestion guard.
Body: optional `{ "reason": "..." }`. `200` `{ data: { id, status: "DISMISSED" } }`.
Idempotent (re-dismiss = no-op). `INVALID_SUGGESTION_STATE` (422) if `MATERIALIZED`.

### `GET|PUT /api/v1/demand-planning/policies/{skuCode}`

Seed/inspect the reorder policy.

`PUT` body:
```json
{ "reorderPoint": 10, "safetyStock": 5, "reorderQty": 100 }
```
`200` returns the upserted policy. `GET` → `200` or `404 POLICY_NOT_FOUND`.

### `GET|PUT /api/v1/demand-planning/sku-supplier-map/{skuCode}`

Seed/inspect the SKU→supplier mapping.

`PUT` body:
```json
{ "supplierId": "uuid", "defaultOrderQty": 100, "leadTimeDays": 7, "currency": "KRW" }
```
`200` returns the upserted mapping. `GET` → `200` or `404 MAPPING_NOT_FOUND`.

## Error codes (additions to `rules/domains/scm.md`)

| Code | HTTP | Meaning |
|---|---|---|
| `SKU_SUPPLIER_UNMAPPED` | 422 | SKU has no supplier mapping; cannot reorder. |
| `SUGGESTION_ALREADY_MATERIALIZED` | 409 | suggestion already has a PO (idempotent path returns existing). |
| `SUGGESTION_NOT_FOUND` | 404 | |
| `INVALID_SUGGESTION_STATE` | 422 | operation invalid for the suggestion's current status. |
| `POLICY_NOT_FOUND` / `MAPPING_NOT_FOUND` | 404 | seed lookups. |

Standard scm codes (`VALIDATION_ERROR`, `TENANT_FORBIDDEN`, `INTERNAL_ERROR`) apply
as usual.

## References

- [ADR-MONO-027](../../../../../docs/adr/ADR-MONO-027-wms-scm-replenishment-loop.md) D2/D5
- [`demand-planning-service/architecture.md`](../../services/demand-planning-service/architecture.md)
- [`procurement-api.md`](./procurement-api.md) § DRAFT-PO-from-suggestion (the materialization target)
- [`data-model.md`](../../services/demand-planning-service/data-model.md)
