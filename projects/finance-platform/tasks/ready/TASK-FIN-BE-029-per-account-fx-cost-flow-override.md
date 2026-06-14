# TASK-FIN-BE-029 — Per-account FX cost-flow method override

- **Status**: ready
- **Project**: finance-platform
- **Service**: ledger-service
- **Domain / traits**: fintech / [transactional, regulated, audit-heavy]
- **Increment**: 21st ledger increment (ADR-001 follow-up — per-account cost-flow granularity)
- **Analysis model**: Opus 4.8 / **Implementation model**: Opus (domain data-model + resolution precedence)

## Goal

Let an operator override the FX cost-flow method (`WEIGHTED_AVERAGE` | `FIFO`) **per ledger
account**, layered on top of the existing per-tenant default (TASK-FIN-BE-023). A settlement
resolves the effective method with the precedence:

```
account override (tenant, ledgerAccountCode)  >  tenant default (tenant)  >  WEIGHTED_AVERAGE
```

This is the natural granularity extension of ADR-001 D1: a tenant may keep most accounts on the
weighted-average default but pin a specific FX clearing account to FIFO (or vice-versa).

## Scope

**In scope** (ledger-service only; mirror the TASK-FIN-BE-023 surface exactly):

1. **Additive migration** `V11__add_fx_cost_flow_account_config.sql` — a **new** table
   `fx_cost_flow_account_config` with composite PK `(tenant_id, ledger_account_code)`,
   `method VARCHAR(20) NOT NULL DEFAULT 'WEIGHTED_AVERAGE'` with the same
   `CHECK (method IN ('WEIGHTED_AVERAGE','FIFO'))`, `updated_by` / `updated_at` audit columns.
   **No** change to any existing table, **no** existing CHECK change, **no** backfill (net-zero).
   InnoDB / utf8mb4 / `DATETIME(6)` — parity with V9.
2. **Domain aggregate** `FxCostFlowAccountConfig` (JPA entity, composite id via `@IdClass`
   `FxCostFlowAccountConfigId`) reusing the existing `CostFlowMethod` enum + repository port
   `FxCostFlowAccountConfigRepository` (`findByTenantIdAndAccountCode`, `findByTenantId` (list,
   ordered by `ledger_account_code`), `save`, `deleteByTenantIdAndAccountCode` returning a
   `boolean` "existed") + JPA adapter — mirror `FxCostFlowConfig` / its repository/adapter.
3. **Resolution wiring** — `SettleForeignPositionUseCase` resolves the effective method as
   `accountOverride.or(tenantDefault).orElse(WEIGHTED_AVERAGE)` instead of the current
   tenant-only lookup. Extract the precedence into a small **pure** static helper so it is
   unit-testable without Testcontainers. **Net-zero**: when no account override row exists the
   result is identical to today (tenant default, else WEIGHTED_AVERAGE).
4. **Application use cases**: `GetFxCostFlowAccountConfigsUseCase` (list a tenant's overrides),
   `SetFxCostFlowAccountConfigUseCase` (validate method via `CostFlowMethod.fromString` **before**
   any persist → `VALIDATION_ERROR` 400; upsert last-write-wins; write audit row
   `FX_COST_FLOW_ACCOUNT_METHOD_SET` in the **same `@Transactional`**),
   `DeleteFxCostFlowAccountConfigUseCase` (delete override; write audit row
   `FX_COST_FLOW_ACCOUNT_METHOD_CLEARED`; idempotent — deleting a non-existent override is a
   200 no-op that still need not error). Audit `aggregateType = "FxCostFlowAccountConfig"`,
   `aggregateId = tenantId + ":" + ledgerAccountCode`, detail includes the account + method.
5. **REST** on the existing `SettlementController` (`/api/finance/ledger/settlements`),
   tenant-scoped via `ActorContext` exactly like the tenant-level endpoints:
   - `GET  /cost-flow-config/accounts` — list the tenant's account overrides (array; empty when
     none).
   - `PUT  /cost-flow-config/accounts/{ledgerAccountCode}` — upsert an override (body
     `{ "method": "FIFO" }`); unknown method → 400 `VALIDATION_ERROR`.
   - `DELETE /cost-flow-config/accounts/{ledgerAccountCode}` — remove the override (200; the
     account falls back to the tenant default). Returns the cleared account code + a `cleared`
     boolean.
6. **DTOs / views**: `FxCostFlowAccountConfigView` (+ a list wrapper or `List<...>`),
   request/response DTOs mirroring `FxCostFlowConfigRequest/Response` (audit fields `NON_NULL`).
7. **Docs**: extend `specs/contracts/http/ledger-api.md` (cost-flow-config section) with the
   three account-level endpoints; add a "Per-account override" subsection to the
   "FX cost-flow method config" section of `specs/services/ledger-service/architecture.md`
   noting the precedence + the additive V11 table.

**Out of scope**: changing the weighted-average / FIFO math (only *which* config row is read
changes); any console/front-end surface; any change to the per-tenant table or endpoints.

## Acceptance Criteria

- **AC-1 — Additive migration.** `V11__add_fx_cost_flow_account_config.sql` creates only the new
  table (composite PK, CHECK, audit columns). No existing table/CHECK touched; no backfill. The
  service boots with Flyway clean on a fresh MySQL (Testcontainers).
- **AC-2 — Resolution precedence (unit).** A pure static resolver returns: account override when
  present; else the tenant default; else `WEIGHTED_AVERAGE`. Unit-tested for all three cases
  (no Testcontainers) in `SettleForeignPositionUseCaseTest` (or a dedicated resolver test).
- **AC-3 — Account override elevates to FIFO (IT).** With the tenant **unset** (default
  weighted-average) and an account override set to `FIFO`, settling a multi-lot position on that
  account consumes the open lots oldest-first (FIFO carrying basis), i.e. the override is read by
  the settlement path. (Reuse the FIN-BE-025 FIFO IT setup; assert lots consumed.)
- **AC-4 — Config lifecycle (IT).** `GET .../accounts` empty → `[]`; `PUT
  .../accounts/{code}` FIFO → 200 with method + audit; `GET` reflects it; DB audit columns +
  `audit_log` row `FX_COST_FLOW_ACCOUNT_METHOD_SET` populated; `DELETE` → 200 and the override
  row is gone (`audit_log` `FX_COST_FLOW_ACCOUNT_METHOD_CLEARED`); `PUT` unknown method
  (`"LIFO"`) → 400 `VALIDATION_ERROR`, nothing persisted.
- **AC-5 — Net-zero.** No account override + no tenant config → settlement is byte-identical to
  FIN-BE-028 (weighted-average). All existing settlement / FIFO / revaluation / reconciliation
  ITs stay GREEN. The per-tenant `cost-flow-config` endpoints + table are unchanged.
- **AC-6 — Tenant isolation.** Account overrides are row-level isolated by `tenant_id`; tenant A's
  override for a code is invisible to tenant B (the composite PK + `findByTenantId` enforce it).

## Related Specs

- `projects/finance-platform/docs/adr/ADR-001-fx-cost-flow-method-fifo-lot-tracking.md` (D1 — the
  per-tenant method config this generalises to per-account granularity)
- `projects/finance-platform/specs/services/ledger-service/architecture.md` § FX cost-flow method
  config / FIFO settlement consumption
- TASK-FIN-BE-023 (per-tenant config — the mirror), TASK-FIN-BE-025 (FIFO settlement consumption —
  the consumer of the resolved method)

## Related Contracts

- `projects/finance-platform/specs/contracts/http/ledger-api.md` — cost-flow-config endpoints
  (extend with the three account-level routes)

## Edge Cases

- Account override = `WEIGHTED_AVERAGE` while tenant default = `FIFO` → that account settles
  weighted-average (override wins, can *downgrade* as well as upgrade).
- DELETE on a non-existent override → 200 no-op (`cleared=false`), no audit row OR an audit row
  with a "no-op" note — pick the simpler; do not 404 (idempotent operator action).
- Unknown `ledgerAccountCode` on PUT — the override is stored regardless (the per-tenant config
  has no FK to accounts either; an operator may pre-configure a code). Do **not** validate the
  account exists (parity with the tenant-level config which is keyed only by tenant).
- Unknown method string → `VALIDATION_ERROR` 400 before any persist (mirror FIN-BE-023 AC).

## Failure Scenarios

- Invalid method on PUT → 400 `VALIDATION_ERROR`, nothing persisted (validation before persist).
- A FIFO-resolved account whose open lots are short (`Σremaining < |F_settle|`) → the existing
  safe fallback to weighted-average in `computeFifo` applies unchanged (no net-non-zero) — the
  resolution change does not alter the fallback.
- Two concurrent PUTs for the same `(tenant, code)` → last-write-wins on the composite PK (parity
  with the per-tenant upsert).
