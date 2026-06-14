# Task ID

TASK-FIN-BE-020

# Title

ledger-service — configurable FX reconciliation tolerance (thirteenth increment): a per-tenant base-leg tolerance so within-threshold FX rounding differences match cleanly instead of raising an AMOUNT_MISMATCH

# Status

review

# Owner

backend

# Task Tags

<!-- api | event | deploy | code | test | adr | onboarding -->

- api
- code
- test

---

# Required Sections (must exist)

- Goal
- Scope (in/out)
- Acceptance Criteria
- Related Specs
- Related Contracts
- Edge Cases
- Failure Scenarios

---

# Dependency Markers

- **extends**: `TASK-FIN-BE-017` (multi-currency reconciliation — the **eleventh** increment, **done**). FIN-BE-017 added the base (FX) leg: when a foreign external line matches on the transaction leg AND carries a bank-reported base (KRW) amount that **differs** from the internal carrying base, the matcher records an `AMOUNT_MISMATCH` discrepancy. That comparison is **exact** — `architecture.md` § Multi-currency reconciliation line ~862 explicitly states "*exact comparison, a configurable FX tolerance is forward-declared*" (also § Increment Scope ~396, ~878). This task is that forward-declared increment.
- **builds on**: `TASK-FIN-BE-010` (reconciliation matcher + F8 no-auto-close) and `TASK-FIN-BE-018` (the twelfth increment) patterns. Reuses the `FxRevaluationPolicy` / `FxSettlementPolicy` **pure domain value-object** style for the new `FxTolerance` policy, and the `ActorContext` audit-actor pattern for the config-mutation audit.
- **(C) self-contained increment — no new error code / status / event / data-model-on-the-matcher**: the within-tolerance path simply **suppresses** the existing `AMOUNT_MISMATCH`; the exceeds-tolerance path is byte-identical to FIN-BE-017. The only persistence addition is a per-tenant tolerance config row (additive Flyway). No ADR — the direction is forward-declared in `architecture.md`.

# Goal

Let an operator configure a **base-leg FX tolerance** per tenant so that small, expected FX **rounding** differences between the bank-reported base (KRW) value and the ledger's carrying base do **not** each raise an `AMOUNT_MISMATCH` discrepancy for manual review, while genuine value gaps **above** the threshold still do.

Today (FIN-BE-017) the base-leg comparison is **exact**: any non-zero base difference on an otherwise-matched foreign line raises an `AMOUNT_MISMATCH` (F8 — recorded, never auto-adjusted). Banks routinely report a base value at their own FX rate that differs from the ledger's carrying rate by a few minor units of rounding; under an exact comparison every such settlement becomes an operator-review discrepancy, drowning the genuine gaps. A configurable tolerance lets the operator say "differences within X are acceptable FX rounding — match cleanly; flag only larger gaps."

**Default = EXACT (net-zero).** The tolerance defaults to zero (no configured row → `FxTolerance.EXACT`), under which the matcher is **byte-identical** to FIN-BE-017. The behaviour changes only for a tenant that has explicitly configured a non-zero tolerance — preserving the regulated/audit-heavy posture (an operator deliberately sets, and the system audits, the threshold).

# Scope

## In Scope

### Domain — `FxTolerance` value object + matcher threading
- New pure domain value object `FxTolerance` (e.g. `domain/reconciliation/` or `domain/money/`), mirroring the `FxRevaluationPolicy` / `FxSettlementPolicy` style:
  - Fields: `toleranceBps` (int, basis points / 만분율 of the **internal carrying base** magnitude) + `absoluteFloorMinor` (long, an absolute floor in base/KRW minor units). Both `≥ 0`.
  - `static FxTolerance EXACT` = `(0, 0)`.
  - `boolean isWithinTolerance(long expectedBaseMinor, long actualBaseMinor)` → `Math.abs(expected - actual) <= max(absoluteFloorMinor, round(Math.abs(expected) * toleranceBps / 10000))`. Under `EXACT`, true **iff** `expected == actual` (so net-zero is preserved exactly — note `max(0, 0) == 0`).
  - The allowed band is `max(bps-derived, floor)` (the **looser** of the two — bps scales with amount, floor backstops tiny amounts). Rounding = half-up on the bps product. Document the rule on the type.
- `ReconciliationMatcher.match(...)` gains an `FxTolerance tolerance` parameter. The base-leg block (currently `ext.baseAmount().minorUnits() != internal.baseMoney().minorUnits()`) becomes `!tolerance.isWithinTolerance(internal.baseMoney().minorUnits(), ext.baseAmount().minorUnits())`. **Everything else unchanged** — the transaction-leg match is still recorded; KRW lines / base-less lines never fire; `EXACT` reproduces FIN-BE-017 exactly. The matcher stays pure (no Spring/JPA).

### Persistence — per-tenant tolerance config (additive Flyway)
- New table `reconciliation_fx_tolerance` (Flyway `V7__add_reconciliation_fx_tolerance.sql`, **additive** — no change to existing tables, no CHECK change):
  - `tenant_id VARCHAR PK`, `tolerance_bps INT NOT NULL DEFAULT 0` (CHECK `>= 0`), `floor_minor BIGINT NOT NULL DEFAULT 0` (CHECK `>= 0`), `updated_by VARCHAR NOT NULL`, `updated_at TIMESTAMPTZ NOT NULL` (audit columns — regulated/audit-heavy).
  - **No row → EXACT** (the use-case treats absence as `FxTolerance.EXACT`); a backfill is **not** required (net-zero for existing tenants).
- A domain aggregate/repository port + JPA adapter for the config (mirror an existing simple aggregate, e.g. the period/config repositories). Follow the Hexagonal layer rules in `architecture.md`.

### Application — resolve + audit
- `IngestStatementUseCase` resolves the running tenant's `FxTolerance` (repository lookup; absent → `EXACT`) and passes it into `ReconciliationMatcher.match(...)`.
- A `GetFxTolerance` + `SetFxTolerance` (upsert) use-case pair. `SetFxTolerance` takes the `ActorContext` (the `updated_by` = `actor.subject() ?? actor.tenantId()`, same identity rule as the journal/period mutations) and validates `bps ≥ 0`, `floor ≥ 0` (→ `VALIDATION_ERROR`).

### Presentation — operator config REST (on `ReconciliationController` or a sibling)
- `GET /api/v1/ledger/reconciliation/fx-tolerance` → the tenant's tolerance (`{ toleranceBps, floorMinor, updatedBy?, updatedAt? }`), or the EXACT default (`{ toleranceBps: 0, floorMinor: 0 }`) when unset.
- `PUT /api/v1/ledger/reconciliation/fx-tolerance` (operator upsert; body `{ toleranceBps, floorMinor }`) → `200` the persisted config. Tenant-scoped (`actor.tenantId()`); audited (`updated_by`/`updated_at`). Negative values → `400/422 VALIDATION_ERROR`. (Confirm the exact base path against the existing reconciliation routes.)

### Contracts + spec
- `projects/finance-platform/specs/contracts/http/reconciliation-api.md` — document the new GET/PUT fx-tolerance routes + the within-tolerance matcher semantics (within → matched, no `AMOUNT_MISMATCH`; exceeds → `AMOUNT_MISMATCH` as today). **No new error code / status / event.**
- `projects/finance-platform/specs/services/ledger-service/architecture.md` — add a **"### FX reconciliation tolerance (thirteenth increment — TASK-FIN-BE-020)"** subsection under Reconciliation, stating the `FxTolerance` model (bps + floor, looser-of band), the EXACT/net-zero default, the F8 invariant (within-tolerance still **records the transaction-leg match**; it only suppresses the base-leg discrepancy — it never auto-posts a correction), the additive `V7` migration, and moving "configurable FX tolerance" from the **Deferred/forward-declared** lists to **done**. Keep the canonical Identity table + `### Service Type Composition` H3 byte-identical (ADR-MONO-012 D3).

### Tests
- **Matcher unit** (pure, exhaustive — the FIN-BE-010/017 lane): `EXACT` reproduces FIN-BE-017 (any non-zero base diff → `AMOUNT_MISMATCH`); a non-zero tolerance: diff **within** band → match, **no** discrepancy; diff **exactly at** the band edge → within (≤); diff **above** band → `AMOUNT_MISMATCH` (expected/actual/currency unchanged from FIN-BE-017); bps-vs-floor "looser wins" (small amount where floor > bps band, and large amount where bps band > floor); KRW line / base-less line never fire regardless of tolerance.
- **`FxTolerance` value-object unit**: the band arithmetic + half-up rounding + `EXACT` equality.
- **Testcontainers `@SpringBootTest` IT** (the authoritative gate — Docker-free `:check` will NOT catch wiring): persisted config resolve (set bps/floor → ingest a foreign statement with a within-tolerance base diff → **0** `AMOUNT_MISMATCH`, the transaction match recorded; raise the diff above tolerance → `AMOUNT_MISMATCH` appears); GET/PUT round-trip + audit columns populated (`updated_by` = actor); absent config → EXACT (existing FIN-BE-017 IT behaviour unchanged); negative bps/floor → `VALIDATION_ERROR`.
- **Regression**: all existing ledger-service unit + IT green (FIN-BE-007..019); the FIN-BE-017 reconciliation IT byte-unchanged under the EXACT default.

## Out of Scope

- **Cross-currency base-leg matching** (a KRW external statement matched against foreign internal lines by their carrying base) — the other forward-declared reconciliation follow-up; a separate increment.
- **FIFO / lot-level cost basis** — a separate, larger increment (new lot data model); not this task.
- Bulk / all-positions revaluation; fuzzy / N:M / split matching; period reopen; a live FX rate feed.
- Any change to the transaction-leg matching, the UNMATCHED_* classification, the F8 no-auto-close invariant, the period lock, or existing FX revaluation/settlement.
- A console (platform-console) surface for the tolerance config — backend + contract only here (a console binding, if wanted, is a separate platform-console task, like the scm seed-config precedent).
- Per-currency-pair or per-account tolerance granularity — v1 is **per-tenant** (one tolerance for the tenant's reconciliation); finer granularity is forward-declarable if needed.

# Acceptance Criteria

- [ ] `FxTolerance` value object (`toleranceBps` + `absoluteFloorMinor`, `EXACT` default, `isWithinTolerance` = looser-of-band with half-up rounding) implemented + unit-tested; `EXACT` ⟺ exact equality.
- [ ] `ReconciliationMatcher.match(...)` threads `FxTolerance`; under `EXACT` the matcher output is **byte-identical** to FIN-BE-017 (net-zero); within-tolerance → transaction match recorded + **no** `AMOUNT_MISMATCH`; exceeds → `AMOUNT_MISMATCH` unchanged.
- [ ] Per-tenant `reconciliation_fx_tolerance` persisted via additive `V7` (no existing-table change, no CHECK change); absent row → `EXACT`; `bps`/`floor` ≥ 0 enforced.
- [ ] `IngestStatementUseCase` resolves the tenant tolerance and passes it to the matcher; the config mutation is audited (`updated_by`/`updated_at`, `ActorContext` identity rule).
- [ ] `GET` + `PUT /api/v1/ledger/reconciliation/fx-tolerance` tenant-scoped; GET returns EXACT default when unset; PUT upserts + audits; negative values → `VALIDATION_ERROR`. No new error code / status / event.
- [ ] `reconciliation-api.md` + `architecture.md` (thirteenth-increment subsection; tolerance moved from forward-declared to done; D3 canonical form intact) updated; spec link-lint clean; `validate-rules` no new inconsistency.
- [ ] Matcher unit + `FxTolerance` unit + Testcontainers IT all green; FIN-BE-007..019 regression green (FIN-BE-017 IT unchanged under EXACT). F8 (no auto-close / no balancing post) preserved.

# Related Specs

> Target project = `finance-platform`. Target service = `ledger-service`. Service Type per `ledger-service/architecture.md` § Service Type Composition. Follow `platform/entrypoint.md`; load `rules/domains/fintech.md` + traits `transactional` / `regulated` / `audit-heavy`.

- `projects/finance-platform/specs/services/ledger-service/architecture.md` § Multi-currency reconciliation (eleventh increment) + § Reconciliation (fourth) + § Increment Scope (the forward-declaration this closes) — **changed** (thirteenth-increment subsection)
- `projects/finance-platform/specs/contracts/http/reconciliation-api.md` — **changed** (GET/PUT fx-tolerance + within-tolerance semantics)
- `docs/adr/ADR-MONO-008-finance-platform-bootstrap.md` (the governing finance ADR; v2 double-entry ledger)
- `rules/domains/fintech.md` (regulated / audit-heavy mandatory rules — the tolerance config audit)
- `projects/finance-platform/PROJECT.md` (domain=fintech, traits=[transactional, regulated, audit-heavy])

---

# Related Contracts

- **Changed (this task)**: `reconciliation-api.md` — GET/PUT `fx-tolerance` + the within-tolerance base-leg semantics.
- **Unchanged**: the `finance.ledger.reconciliation.discrepancy.detected.v1` event (no new type — within-tolerance simply does not emit; exceeds emits `AMOUNT_MISMATCH` as today); the ingest request shape (the optional `baseAmount` from FIN-BE-017 is unchanged).

---

# Edge Cases

- A tenant with **no** config row → `FxTolerance.EXACT`; reconciliation byte-identical to FIN-BE-017 (the dominant net-zero path).
- `bps` band and `floor` both set → the **looser** (larger) band applies (small amounts protected by floor, large amounts by bps).
- Base difference **exactly equals** the band → **within** (`<=`, inclusive) → matched, no discrepancy.
- A KRW line, or a foreign line without a bank-reported `baseAmount`, → no base-leg check regardless of tolerance (unchanged).
- Negative `bps`/`floor` on PUT → `VALIDATION_ERROR` (DB CHECK is the structural backstop).
- A within-tolerance match still **records the transaction-leg match** (the settlement IS identified) — tolerance suppresses only the base-leg *discrepancy*, never the match, and never auto-posts an FX correction (F8).
- Concurrent PUT + ingest: the ingest resolves the tolerance at run time; last-write-wins on the config row (audited `updated_at`).

# Failure Scenarios

- The matcher is made impure (reads the repo directly) → architecture violation; the tolerance MUST be passed in as a value object (the use-case resolves it).
- `EXACT` accidentally treats a 0-difference as outside tolerance (off-by-one in the `<=`) → FIN-BE-017 regression; the net-zero unit test gates it.
- Within-tolerance path drops the transaction-leg match (not just the discrepancy) → settlement no longer identified; unit test asserts the match is still recorded.
- Tolerance applied to the transaction (foreign) leg instead of only the base (KRW) leg → wrong; the foreign leg stays an exact `(amount, currency, direction)` match.
- `V7` alters an existing table or adds a CHECK that rejects existing `AMOUNT_MISMATCH` rows → migration regression; `V7` must be purely additive (new table only).
- The config PUT is not audited (`updated_by` null) → regulated/audit-heavy violation; IT asserts the audit columns.
- Docker-free `:check` passes but Testcontainers IT fails (wiring) → the IT is authoritative; do not close on `:check` alone (`feedback_spring_boot_diagnostic_patterns`).

---

# Recommended Implementation Model

- **Opus** — fintech regulated domain, reconciliation matcher logic + an audited per-tenant config + Flyway + Testcontainers IT. Dispatch `Agent(subagent_type="backend-engineer", model="opus", ...)` with absolute worktree paths; the dispatcher independently re-verifies the EXACT net-zero invariant + F8 + the audit columns before any close.

---

# Definition of Done

- [ ] `FxTolerance` value object + matcher threading (EXACT = net-zero) + per-tenant persisted config (additive V7) + resolve + GET/PUT audited REST
- [ ] `reconciliation-api.md` + `architecture.md` (thirteenth increment; forward-declaration closed; D3 intact) updated; link-lint + `validate-rules` clean
- [ ] Matcher unit + `FxTolerance` unit + Testcontainers IT green; FIN-BE-007..019 regression green; F8 preserved
- [ ] Acceptance Criteria all satisfied
- [ ] Ready for review
