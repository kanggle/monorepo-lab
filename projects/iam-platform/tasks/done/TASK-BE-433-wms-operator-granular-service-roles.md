# TASK-BE-433 — wms operator entitlement derives granular wms-service roles

**Status:** done
**Type:** TASK-BE
**Analysis model:** Opus 4.8 / **Recommended impl model:** Opus 4.8 (auth issuance hot-path + role-model decision)

## Goal

A wms-entitled operator's assume-tenant token carried only the coarse `WMS_OPERATOR`
role (ADR-MONO-035 O1 derivation). The wms `gateway-service` admits on non-empty `roles`,
so that was *"coarse-but-correct for gateway admission"* — but the wms **services** behind
the gateway authorize on GRANULAR roles:

- `outbound-service`: `OUTBOUND_READ`/`OUTBOUND_WRITE`/`OUTBOUND_ADMIN`
- `inbound-service`, `inventory-service`: `INBOUND_*`, `INVENTORY_*`
- `master-service`: `MASTER_*`

So a `WMS_OPERATOR`-only token passed the gateway yet **403'd at every wms-service write**
(`hasAnyRole("OUTBOUND_WRITE","OUTBOUND_ADMIN")` etc.). O1 only validated gateway
admission; the service-level RBAC gap was never reconciled. (The optionb demo bridged it
with a manual role patch.) Surfaced live in the TASK-BE-431/432 ecommerce↔wms
fulfillment-loop demo: the operator could not pick/pack/ship through outbound-service.

**Decision (user-chosen, Option A):** the derivation emits the granular roles.

## Scope

- `OperatorRoleDerivation.fromEntitledDomains`: the `wms` case emits
  `WMS_OPERATOR + {OUTBOUND,INBOUND,INVENTORY}_{READ,WRITE} + MASTER_READ` — the
  operator-tier roles the wms services authorize on. ADMIN-tier (`*_ADMIN`/`WMS_ADMIN` —
  cancellation, force-saga-fail, master-data writes) is **deliberately excluded** (a
  higher grant, out of scope). Domain-uniform (every wms-entitled operator gets the same
  set) — per-assignment granularity remains the deferred O1-B follow-up.
- `OperatorRoleDerivationTest`: update wms expectations + assert no ADMIN-tier leaks.
- ADR-MONO-035 §3.3: a BE-433 refinement note (no D-row re-decision).

## Acceptance Criteria

- AC-1: `fromEntitledDomains(["wms"])` = `[WMS_OPERATOR, OUTBOUND_READ, OUTBOUND_WRITE,
  INBOUND_READ, INBOUND_WRITE, INVENTORY_READ, INVENTORY_WRITE, MASTER_READ]` (ordered).
- AC-2: no `*_ADMIN` / `WMS_ADMIN` / `MASTER_WRITE` in the wms set.
- AC-3: other domains unchanged (`ecommerce → [ADMIN]`, `scm → [SCM_OPERATOR]`, …);
  union/dedup/order/trim/null behaviour preserved.
- AC-4: `:auth-service:test` GREEN.

## Related

- ADR-MONO-035 O1 (the coarse derivation this refines) / ADR-033 (roles issuance)
- TASK-BE-431/432 (the fulfillment-loop demo that surfaced the gap)
- `outbound-service/architecture.md` § Security (the granular role definitions)

## Out of scope

- Per-assignment operator domain roles (ADR-035 O1-B, already a deferred follow-up).
- ADMIN-tier operator grants (a separate higher entitlement).
- The other domains' granular service roles (scm/erp/finance/mes/fan) — only wms had a
  live service-RBAC gap; the same refinement can apply per-domain later if needed.
