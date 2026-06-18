---
id: TASK-FIN-BE-042
title: "Per-tenant FX rate override (특수 계약환율) — ADR-002 deferred"
status: done
service: ledger-service
tags: [code, test, migration, multi-tenant, regulated, fx]
analysis_model: "Opus 4.8"
impl_model: "Opus 4.8"
created: 2026-06-19
---

# TASK-FIN-BE-042 — Per-tenant FX rate override (특수 계약환율)

## Goal

Realize the deferred ADR-002 (`ADR-002-realtime-fx-rate-feed.md`) item **per-tenant override (특수
계약환율)**: allow a tenant to configure a **contract FX rate** for a currency pair that overrides
the tenant-agnostic market rate from the feed, during FX resolution. Market `fx_rate_quote` stays
global (tenant-agnostic); the override is a tenant-scoped layer on top, resolved at read time.

## Reference patterns (existing per-tenant config in this service — mirror)

- **`reconciliation_fx_tolerance`** — `V7__add_reconciliation_fx_tolerance.sql` +
  `domain/reconciliation/ReconciliationFxToleranceConfig.java`: tenant-scoped PK, audit cols
  (`updated_by`, `updated_at`), **absence of a row = default/net-zero** (no backfill).
- **`fx_cost_flow_config`** — `V9__add_fx_cost_flow_config.sql` +
  `domain/journal/FxCostFlowConfig.java`: same shape (tenant PK, last-write-wins upsert, audit,
  absence = WEIGHTED_AVERAGE default).

Reuse this exact idiom: tenant-scoped row, operator upsert (last-write-wins), audit columns,
**absence = no override = fall through to the feed (net-zero, today's behavior)**.

## Target (current state)

- Resolution: `apps/ledger-service/.../application/ResolveEffectiveFxRate.java` — current precedence:
  (1) operator-supplied `providedRate` (manual) → use as-is; (2) else feed-enabled? → latest
  `fx_rate_quote` (staleness-checked) or `FX_RATE_UNAVAILABLE` 422. Called by
  `SettleForeignPositionUseCase` + `RevalueForeignBalanceUseCase`.
- `fx_rate_quote` (`V12`) is tenant-agnostic (market). Current Flyway max = **V13**; TASK-FIN-BE-041
  (sibling, parallel) owns **V14** → **this task uses V15**.
- Tenant context: this service is multi-tenant (the FxTolerance/FxCostFlow configs are tenant-keyed)
  — use the SAME mechanism those configs use to resolve the current tenant.

## Scope

1. **V15 migration** — `V15__add_fx_rate_override.sql`: table `fx_rate_override` with PK
   `(tenant_id, base_currency, foreign_currency)`, `rate DECIMAL(20,8) NOT NULL` (same unit as
   `fx_rate_quote.rate` — base-minor-per-foreign-minor), `effective_from`/optional validity if it
   fits the FxTolerance idiom (keep minimal — match the existing config tables; a simple
   always-effective override is acceptable for v1), audit `updated_by VARCHAR(64)`,
   `updated_at DATETIME(6)`. MySQL/InnoDB/utf8mb4. **NO backfill** (absence = no override).
   **Use V15 (NOT V14 — V14 is TASK-FIN-BE-041's ShedLock table).**
2. **Domain + repository** — `FxRateOverride` entity + repository
   `findOverride(tenantId, base, foreign) → Optional<FxRateOverride>`. Mirror
   `ReconciliationFxToleranceConfig`/`FxCostFlowConfig` shape.
3. **Resolution precedence** — extend `ResolveEffectiveFxRate`:
   **manual `providedRate` > per-tenant override (contract) > feed market rate > FX_RATE_UNAVAILABLE**.
   The override is looked up by current tenant + currency pair; when present, return it with a
   distinct `source` tag (e.g. `"override:contract"`, `fromFeed=false`) for audit; when absent, fall
   through to the existing feed path UNCHANGED (net-zero). The manual operator-supplied rate still
   wins over the override (explicit input is the most specific).
4. **Operator REST** — admin endpoints to upsert / read the override (mirror the FxTolerance /
   FxCostFlow admin surface: `PUT`/`GET` under the finance ledger admin path, operator-plane,
   tenant-scoped, audit `updated_by`). 422/validation on a non-positive rate / unknown currency.
5. **Spec** — `specs/services/ledger-service/architecture.md` FX section: document the override
   layer + the new precedence; `ADR-002-realtime-fx-rate-feed.md` §3 roadmap: mark "per-tenant
   override (특수 계약환율)" realized (TASK-FIN-BE-042). **NOTE**: sibling TASK-FIN-BE-041 edits the
   SAME `architecture.md` FX section (ShedLock lines) on its own branch — touch only the
   override/resolution lines; the merge reconciles the two (small, known overlap).
6. **Tests** — unit: precedence (manual wins; override present → override + `source=override:*`,
   `fromFeed=false`; override absent → feed path unchanged; tenant A's override does NOT apply to
   tenant B); override upsert/read; non-positive rate rejected. (No Testcontainers needed for the
   precedence unit; the repository slice IT can be CI-only.)

## Acceptance Criteria

- AC-1: precedence is **manual > per-tenant override > feed > FX_RATE_UNAVAILABLE**; an override row
  for `(tenant, base, foreign)` is used (with an audit `source` tag, `fromFeed=false`) when no manual
  rate is supplied.
- AC-2: **absence of an override = net-zero** — resolution falls through to the existing feed path,
  byte-identical to today (no behavior change for tenants without a contract rate).
- AC-3: override is **tenant-scoped** — tenant A's override never applies to tenant B.
- AC-4: operator can upsert/read the override (tenant-scoped, audit `updated_by`/`updated_at`,
  last-write-wins); non-positive/invalid rate → 422.
- AC-5: build + unit tests GREEN — `./gradlew :projects:finance-platform:apps:ledger-service:test`.
- AC-6: `architecture.md` FX section documents the override + precedence; ADR-002 per-tenant-override
  deferred item marked realized.

## Related Specs / Contracts

- `projects/finance-platform/docs/adr/ADR-002-realtime-fx-rate-feed.md` (per-tenant override deferral)
- `specs/services/ledger-service/architecture.md` (FX section)
- Reference: `ReconciliationFxToleranceConfig` (V7) + `FxCostFlowConfig` (V9) — tenant-config idiom
- `rules/traits/` finance traits (regulated/audit-heavy — money = no float, audit cols, F5 string rate)

## Edge Cases

- **No override row**: fall through to feed (net-zero, today's behavior) — the primary path.
- **Manual rate supplied AND an override exists**: manual wins (most specific operator intent).
- **Override stale/validity**: keep v1 minimal (always-effective) unless the FxTolerance idiom
  includes validity; do not over-engineer a temporal model (defer if needed, document).
- **Money precision**: `rate` DECIMAL(20,8), no float/double anywhere; F5 string serialization in the
  response DTO (match `FxRateHistoryResponse`).

## Failure Scenarios

- **Override leaks across tenants**: the lookup MUST be tenant-scoped (AC-3) — test tenant A vs B.
- **Override silently shadows the feed unexpectedly**: the override applies ONLY when a row exists for
  the exact `(tenant, base, foreign)`; absence = feed. Make the precedence + source tag explicit +
  audited so an operator can see WHY a contract rate was used.
- **Flyway version clash**: this task owns **V15**; sibling TASK-FIN-BE-041 owns **V14** (ShedLock) —
  do NOT use V14 here.
- **Float money**: regulated finance — grep-zero float/double on the rate path.
