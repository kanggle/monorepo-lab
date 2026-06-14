# Task ID

TASK-FIN-BE-021

# Title

ledger-service — cross-currency base-leg matching (fourteenth increment): a base-currency (KRW) external statement line matches a foreign internal line by its carrying base, as a fallback after same-currency matching

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

- **extends**: `TASK-FIN-BE-017` (multi-currency reconciliation — the **eleventh** increment, **done**) and `TASK-FIN-BE-020` (configurable FX tolerance — the **thirteenth** increment, **done**). FIN-BE-017 matches a foreign external line to a foreign internal line on the **transaction (foreign) leg** and adds the base-leg FX check; FIN-BE-020 made that base-leg comparison tolerance-aware. `architecture.md` § Multi-currency reconciliation / § Increment Scope (~line 360) explicitly forward-declares "**cross-currency base-leg matching** (a KRW external statement matched against foreign internal lines by their carrying base)". This task is that forward-declared increment.
- **builds on**: the `ReconciliationMatcher` (FIN-BE-010 1:1 engine; FIN-BE-017 base leg; FIN-BE-020 `FxTolerance`). Reuses the per-tenant `FxTolerance` already resolved + passed into `match(...)` (no new use-case wiring for the tolerance).
- **(C) tight self-contained increment — pure matcher logic + one additive audit flag**: the only persistence touch is an **additive** `cross_currency` boolean on the reconciliation match (audit transparency for a regulated ledger). No new error code / status / event; no new REST; no change to the existing same-currency matching (net-zero for every existing reconciliation). No ADR — the direction is forward-declared in `architecture.md`.

# Goal

Let a **base-currency (KRW) external statement line** reconcile against a **foreign-currency internal ledger line** by the internal line's **carrying base** value, as a **fallback** after same-currency matching is exhausted.

Today the matcher matches 1:1 by `(amountMinor, currency, direction)`: a KRW external line can only match a KRW internal line by exact KRW amount; a foreign external line matches a foreign internal line. But a bank often reports a settlement **in the base currency (KRW)** even though the ledger booked the underlying position as a **foreign-currency** line (which carries a KRW `baseMoney`). Under the current matcher that KRW external line finds no same-currency candidate → `UNMATCHED_EXTERNAL`, and the foreign internal line → `UNMATCHED_INTERNAL` — two spurious discrepancies for one real settlement.

This increment adds a **cross-currency fallback**: for a KRW external line with **no same-currency candidate**, the matcher looks for a not-consumed **foreign** internal line (same direction) whose **carrying base** (`baseMoney`) equals the external KRW amount **within the per-tenant `FxTolerance`** (FIN-BE-020). If found, it is a **cross-currency match** (flagged for audit); otherwise the line stays `UNMATCHED_EXTERNAL` exactly as today.

**Net-zero for existing reconciliations.** Same-currency matching is unchanged and takes **precedence**; the fallback fires only in the genuinely new scenario (a KRW external with no KRW candidate but a carrying-base-matching foreign internal line) that previously produced two unmatched discrepancies. Every existing same-currency / same-foreign-currency reconciliation matches byte-identically.

# Scope

## In Scope

### Domain — matcher cross-currency fallback
- `ReconciliationMatcher.match(...)`: after the existing `findCandidate` (same `(amount, currency, direction)`) returns no candidate **and** the external line is base-currency (`ext.currency() == LedgerReportingCurrency.BASE`), run a **second** lookup `findCrossCurrencyCandidate(...)`:
  - the FIRST not-consumed internal line with `direction == ext.direction()` **AND** `internal.money().currency() != LedgerReportingCurrency.BASE` (a **foreign** line) **AND** `tolerance.isWithinTolerance(internal.baseMoney().minorUnits(), ext.amountMinor())` (the external KRW amount is the base amount; the internal carrying base is `baseMoney`).
  - If found → consume it, `ext.markMatched()`, record a `ReconciliationMatch` carrying `ext.money()` (KRW) + `internal.journalEntryId()`, **flagged cross-currency**. **No** `AMOUNT_MISMATCH` is recorded (for cross-currency the base comparison **is** the match key — within tolerance → clean match; beyond tolerance → not a candidate → the line falls through to `UNMATCHED_EXTERNAL`).
  - If not found → `UNMATCHED_EXTERNAL` exactly as today.
- **Precedence + determinism**: same-currency exact matching runs first and is unchanged; the cross-currency pass is a strict fallback; both consume candidates in input order (deterministic). A non-base (foreign) external line never triggers the cross-currency pass (the forward-declared direction is **base external → foreign internal** only; foreign-external → KRW-internal is **out of scope**, forward-declarable).
- The matcher stays **pure** (no Spring/JPA); the `FxTolerance` is the one already passed in by `IngestStatementUseCase`.

### Domain/Persistence — cross-currency audit flag (additive)
- `ReconciliationMatch` gains a `boolean crossCurrency` (regulated audit transparency — "this KRW bank line matched a foreign ledger position by carrying base"). Same-currency matches set it `false`.
- Additive Flyway `V8__add_reconciliation_match_cross_currency.sql`: `ALTER TABLE reconciliation_match ADD COLUMN cross_currency BOOLEAN NOT NULL DEFAULT FALSE` (additive + defaulted — **net-zero** for existing rows; no other table change, no CHECK change). Mirror the existing dialect (this DB is **MySQL** — use `BOOLEAN`/`TINYINT(1)` consistent with existing columns; confirm against prior migrations).

### Application / Presentation
- No new use-case (the tolerance is already resolved + threaded). The match-construction path sets `crossCurrency` from the matcher result.
- The reconciliation **match read** response (if a match GET/list exists) exposes `crossCurrency` (additive field). If matches are only surfaced via the discrepancy/statement responses, expose it wherever the matches are returned; otherwise no API shape change beyond the additive flag.

### Contracts + spec
- `projects/finance-platform/specs/contracts/http/reconciliation-api.md` — document the cross-currency fallback semantics (base external → foreign internal by carrying base, within `FxTolerance`, after same-currency matching; flagged `crossCurrency`; no `AMOUNT_MISMATCH` for the cross-currency match). The additive `crossCurrency` match field.
- `projects/finance-platform/specs/services/ledger-service/architecture.md` — add a **"### Cross-currency base-leg matching (fourteenth increment — TASK-FIN-BE-021)"** subsection under Reconciliation; state the fallback rule, the precedence (same-currency first), the `FxTolerance` reuse, the net-zero invariant, the additive `V8` flag, and move "cross-currency base-leg matching" from the **Deferred/forward-declared** lists to **done**. Keep the canonical Identity table + `### Service Type Composition` H3 byte-identical (ADR-MONO-012 D3).

### Tests
- **Matcher unit** (pure, exhaustive): a KRW external with no KRW candidate but a carrying-base-equal foreign internal → **cross-currency match** (crossCurrency=true, no discrepancy), both consumed; same-currency KRW match **takes precedence** when both a KRW internal and a carrying-base foreign internal exist; a KRW external whose amount is **within a configured tolerance** of the foreign carrying base → match; **beyond tolerance** → `UNMATCHED_EXTERNAL` (+ the foreign internal → `UNMATCHED_INTERNAL`, as today); a **foreign** external line never triggers the cross-currency pass; determinism (first not-consumed foreign internal by input order); **net-zero** — every existing same-currency / same-foreign-currency matcher test unchanged.
- **Testcontainers `@SpringBootTest` IT** (the authoritative gate — Docker-free `:check` will NOT catch wiring): ingest a KRW external statement against a foreign (USD) internal position with an equal carrying base → a cross-currency match persisted with `cross_currency = true`, **0** discrepancies; the same KRW external with no carrying-base match → `UNMATCHED_EXTERNAL`; a within-tolerance cross-currency match under a configured `FxTolerance`; the existing FIN-BE-017 / FIN-BE-020 reconciliation IT behaviour **unchanged** (same-currency byte-identical).
- **Regression**: all existing ledger-service unit + IT green (FIN-BE-007..020); the FIN-BE-017 & FIN-BE-020 reconciliation ITs byte-unchanged.

## Out of Scope

- **Foreign external → KRW internal** cross matching (the reverse direction) — the forward-declaration is specifically base-external → foreign-internal; the reverse is a separate forward-declarable increment.
- **FIFO / lot-level cost basis** — a separate, larger increment (new lot data model); not this task.
- Fuzzy / N:M / split matching; period reopen; a live FX rate feed; per-currency-pair tolerance granularity.
- Any change to the transaction-leg same-currency matching, the `UNMATCHED_*` classification, the F8 no-auto-close invariant, the period lock, or existing FX revaluation/settlement/tolerance behaviour.
- Recording an `AMOUNT_MISMATCH` for a cross-currency match (the base comparison **is** the match key — within tolerance matches cleanly, beyond tolerance does not match at all).
- A console (platform-console) surface — backend + contract only.

# Acceptance Criteria

- [ ] `ReconciliationMatcher` adds a cross-currency fallback: a base-currency (KRW) external line with **no same-currency candidate** matches the first not-consumed **foreign** internal line (same direction) whose carrying `baseMoney` is **within the per-tenant `FxTolerance`** of the external KRW amount; otherwise `UNMATCHED_EXTERNAL` as today. Same-currency matching is unchanged and takes **precedence**. The matcher stays pure.
- [ ] A cross-currency match records a `ReconciliationMatch` (KRW money + internal journalEntryId) flagged `crossCurrency = true`; **no** `AMOUNT_MISMATCH` for the cross-currency match. Beyond-tolerance → no match (falls through to UNMATCHED).
- [ ] Additive `V8` adds `cross_currency BOOLEAN NOT NULL DEFAULT FALSE` to `reconciliation_match` (no other table/CHECK change); existing rows + same-currency matches → `false` (net-zero).
- [ ] `crossCurrency` exposed wherever matches are surfaced (additive field).
- [ ] `reconciliation-api.md` + `architecture.md` (fourteenth-increment subsection; forward-declaration moved to done; D3 canonical form intact) updated; spec link-lint clean; `validate-rules` no new inconsistency.
- [ ] Matcher unit (incl. precedence, tolerance, determinism, net-zero) + Testcontainers IT all green; FIN-BE-007..020 regression green (FIN-BE-017 & FIN-BE-020 ITs unchanged). F8 preserved (no auto-post; the matcher only records matches/discrepancies).

# Related Specs

> Target project = `finance-platform`. Target service = `ledger-service`. Service Type per `ledger-service/architecture.md` § Service Type Composition. Follow `platform/entrypoint.md`; load `rules/domains/fintech.md` + traits `transactional` / `regulated` / `audit-heavy`.

- `projects/finance-platform/specs/services/ledger-service/architecture.md` § Multi-currency reconciliation (11th) + § FX reconciliation tolerance (13th) + § Reconciliation (4th) + § Increment Scope (the forward-declaration this closes) — **changed** (fourteenth-increment subsection)
- `projects/finance-platform/specs/contracts/http/reconciliation-api.md` — **changed** (cross-currency fallback + `crossCurrency` flag)
- `docs/adr/ADR-MONO-008-finance-platform-bootstrap.md` (governing finance ADR; v2 double-entry ledger)
- `rules/domains/fintech.md` (regulated / audit-heavy mandatory rules — the cross-currency match audit flag)
- `projects/finance-platform/PROJECT.md` (domain=fintech, traits=[transactional, regulated, audit-heavy])
- `projects/finance-platform/tasks/done/TASK-FIN-BE-017-…` + `TASK-FIN-BE-020-…` (the base-leg + tolerance precedents reused)

---

# Related Contracts

- **Changed (this task)**: `reconciliation-api.md` — cross-currency fallback semantics + the additive `crossCurrency` match field.
- **Unchanged**: the `finance.ledger.reconciliation.discrepancy.detected.v1` event (no new type — a cross-currency match emits no discrepancy); the ingest request shape; the `AMOUNT_MISMATCH` / `UNMATCHED_*` types.

---

# Edge Cases

- A KRW external line with **both** a same-currency KRW internal candidate **and** a carrying-base-matching foreign internal line → the **same-currency** match wins (precedence); the foreign line stays for its own matching.
- A KRW external whose amount is **within a configured tolerance** (not exact) of a foreign carrying base → cross-currency match (consistent with FIN-BE-020); **beyond** tolerance → no cross-currency candidate → `UNMATCHED_EXTERNAL` + the foreign internal → `UNMATCHED_INTERNAL` (as today).
- Under `FxTolerance.EXACT` (the default) cross-currency matching requires **exact** carrying-base equality.
- Multiple foreign internal lines with the same carrying base → first not-consumed by input order (deterministic).
- A **foreign** external line → never enters the cross-currency pass (out of scope direction); matches same-foreign-currency as FIN-BE-017.
- A foreign internal line **without** a meaningful base (KRW internal where `baseMoney == money`) is not a cross-currency target for a KRW external — but note a KRW internal IS a same-currency candidate and is consumed by `findCandidate` first; the cross-currency pass only considers `currency != BASE` internals.

# Failure Scenarios

- The matcher is made impure (reads the repo) → architecture violation; the tolerance MUST stay a passed-in value object.
- Cross-currency matching changes a same-currency reconciliation result → net-zero regression; the existing same-currency matcher tests + the FIN-BE-017/020 ITs gate it.
- The cross-currency pass runs **before** same-currency matching (wrong precedence) → a KRW external could grab a foreign internal while its real KRW candidate goes unmatched; the precedence unit test gates it.
- A cross-currency match also records an `AMOUNT_MISMATCH` → wrong (the base comparison is the match key); unit test asserts 0 discrepancies on a within-tolerance cross-currency match.
- `V8` alters an existing column / adds a CHECK / is non-defaulted → migration regression; `V8` must be a single additive defaulted column.
- The `crossCurrency` flag is not persisted / not exposed → audit-transparency gap; the IT asserts `cross_currency = true` on the persisted match.
- Docker-free `:check` passes but Testcontainers IT fails (wiring) → the IT is authoritative; do not close on `:check` alone (`feedback_spring_boot_diagnostic_patterns`). Beware the shared-Kafka cross-class IT predicate collision (FIN-BE-020 trap) — use a distinct `ledgerAccountCode` for new IT events.

---

# Recommended Implementation Model

- **Opus** — fintech regulated domain, reconciliation matcher logic (precedence + tolerance + determinism) + additive audit flag + Testcontainers IT. Dispatch `Agent(subagent_type="backend-engineer", model="opus", ...)` with absolute worktree paths; the dispatcher independently re-verifies the net-zero invariant (existing same-currency matching unchanged), the precedence, F8, and the persisted `cross_currency` flag before any close.

---

# Definition of Done

- [ ] Matcher cross-currency fallback (base external → foreign internal by carrying base, within `FxTolerance`, after same-currency, deterministic) + `crossCurrency` flag + additive `V8`
- [ ] `reconciliation-api.md` + `architecture.md` (fourteenth increment; forward-declaration closed; D3 intact) updated; link-lint + `validate-rules` clean
- [ ] Matcher unit + Testcontainers IT green; FIN-BE-007..020 regression green; F8 + net-zero preserved
- [ ] Acceptance Criteria all satisfied
- [ ] Ready for review
