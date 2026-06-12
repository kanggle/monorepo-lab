# Task ID

TASK-PC-FE-072

# Title

console-web — finance ledger operations console section (read-only: trial balance + accounting periods + journal entry + reconciliation discrepancy queue)

# Status

ready

# Owner

frontend

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

- **depends on**: `TASK-PC-FE-009` (finance `account-service` console section) — establishes the finance per-domain client + per-domain-credential + flat-envelope + read-only + F5-money pattern this slice **reuses** (do not re-derive it). **MERGED** → origin/main. The `console-integration-contract.md` **§ 2.4.7** (FE-009 finance account binding) is on main; **§ 2.4.7.1** (this task) **reuses** it for the ledger (a second finance-product service), exactly as **§ 2.4.5.1** (wms outbound) reuses **§ 2.4.5** (wms admin) under one wms credential gate.
- **producer already on main (no cross-project prerequisite)**: the finance `ledger-service` read producer contracts — [`ledger-api.md`](../../../finance-platform/specs/contracts/http/ledger-api.md) (trial-balance / periods / entry-by-id) and [`reconciliation-api.md`](../../../finance-platform/specs/contracts/http/reconciliation-api.md) (discrepancy queue) — are **already authored + merged** (TASK-FIN-BE-007…017, the 11 ledger increments). They are **unchanged, consumed read-only** here. The ledger shares the **same finance tenant gate** as the account-service (the merged finance `iam-integration.md` § *platform-console Operator Read Consumer*, TASK-FIN-BE-005) — so there is **no** new finance-side spec-first prerequisite (unlike FE-009, which needed FIN-BE-005 first). This task can sit in `ready/` directly.
- **part of**: the platform-console finance federation — making the **eleven ledger increments** (FIN-BE-007…017: double-entry posting, period close, manual posting, reconciliation, multi-currency journals, FX revaluation, FX settlement, multi-currency reconciliation) **operator-visible** for the first time. This is the **second service of the `finance` product** bound by the console (alongside the FE-009 `account-service`), proving a single federated product can bind **multiple producer services** under one credential + eligibility gate.
- **spec-first**: the console-side contract extension (**new § 2.4.7.1 finance ledger binding** in `console-integration-contract.md` + `console-web/architecture.md` `features/ledger-ops` module entries + env `LEDGER_BASE_URL`/`LEDGER_TIMEOUT_MS`) lands **before/with** the code (this task's spec PR). The finance `ledger-api.md` / `reconciliation-api.md` are **unchanged** (cross-reference only — finance owns them).
- **read-surface mirror → Sonnet/Opus**: this slice **reuses** the proven FE-009 finance pattern verbatim (no new credential model, no new envelope, no cross-project spec) — but the breadth (4 views, 6 GET endpoints, the multi-currency F5 `money`+`exchangeRate`+`baseAmount` triple-field discipline, the eligibility-block + resilience taxonomy) and the fidelity required of the read-only/credential/precision invariants put it at **Opus** for the implementation (the same reasoning that kept the FE-009 contract-bearing slice on Opus).

# Goal

Build the console's **finance ledger operations section** — a server-side, tenant-scoped, **strictly read-only** section over finance's existing `ledger-service` read surface, making the eleven ledger increments operator-visible:

- **trial balance** — `GET /api/finance/ledger/trial-balance` (per-account debit/credit totals + base-currency (KRW) totals; grand base totals; `inBalance` — the live double-entry invariant, incl. the 8th-increment multi-currency base consolidation).
- **accounting periods** — `GET /api/finance/ledger/periods` (paginated list) + `GET /api/finance/ledger/periods/{periodId}` (detail incl. the close `snapshot` when CLOSED — the 2nd-increment period close).
- **journal entry** — `GET /api/finance/ledger/entries/{entryId}` (lines carry `money` + `exchangeRate` + `baseAmount`; `source.sourceType ∈ {TRANSACTION, MANUAL, REVALUATION, SETTLEMENT}` — surfaces the 5th manual-posting, 8th multi-currency, 9th FX-revaluation, 10th FX-settlement increments; `balanced`).
- **reconciliation discrepancy queue** — `GET /api/finance/ledger/reconciliation/discrepancies?status=OPEN|RESOLVED` (paginated) + `GET .../discrepancies/{id}` (detail incl. `resolution` when RESOLVED — surfaces the 4th/6th/7th reconciliation increments + the **11th-increment FX-difference** `AMOUNT_MISMATCH` discrepancy that carries both `externalRef` and `journalEntryId`).

The ledger's operator **mutation** endpoints (`POST /entries` manual posting, `POST /revaluations`, `POST /settlements`, reconciliation `POST /statements` ingest + `…/resolve`) are domain journal-movement / operator-domain mutations (`Idempotency-Key`-gated, fintech F1) — **explicitly out of scope** (not silently dropped), exactly as the FE-009 finance account write surface is. Every ledger call is a pure `GET`.

**Honest ledger read-surface constraint (recorded, not papered over)**: trial-balance + period list are **index-style** browsable reads (no input — tenant-scoped from the JWT claim); the journal-entry read is **id-driven** (`GET /entries/{entryId}` — there is **no** list/search GET over entries at this increment, the same honest constraint as the FE-009 account surface); the discrepancy queue is a **status-filtered list**. Account-level drill (`GET /accounts/{ledgerAccountCode}/{balance,entries}`) and statement-detail (`GET /reconciliation/statements/{id}`) **exist in the producer but are forward-declared** for a follow-up surface — **do not fabricate** them here.

Auth reuses the FE-009 finance per-domain pattern: the ledger sits behind the **same `finance.local` gateway** as the account-service on a distinct `/api/finance/ledger/**` path; it validates the **same** IAM RS256 JWT (ADR-001), `tenant_id ∈ {finance,*}` from the JWT claim, `finance.read` scope — the **domain-facing IAM OIDC access token** (`getDomainFacingToken()`), **never** the IAM operator-token-exchange (§ 2.6). Eligibility reuses the **finance product** (`productKey=finance`) — one registry product, two services.

# Scope

## In Scope

### Spec-first (console-side — lands in this task's spec PR)

- `console-integration-contract.md` **§ 2.4.7.1** finance ledger operations surface (cross-reference, not a redefinition) — the 6 GET endpoints, credential reuse (§ 2.4.7), eligibility reuse (finance product), read-only binding, F5 multi-currency obligations, flat-envelope resilience, producer immutability, not-a-§3-row.
- `console-web/architecture.md` — Phase banner, `features/ledger-ops` internal-structure tree entry, Integration Rules bullet, Testing Strategy entry, References entry; env `LEDGER_BASE_URL` (default `http://finance.local`) + `LEDGER_TIMEOUT_MS` (default `5000`).

### Code (console-web)

- `src/shared/config/env.ts` — add `LEDGER_BASE_URL` (default `http://finance.local`) + `LEDGER_TIMEOUT_MS` (default `5000`), wired through `getServerEnv()`.
- `src/features/ledger-ops/api/ledger-api.ts` — server-only client (mirror `finance-ops/api/finance-api.ts`): `getTrialBalance()`, `listPeriods({page,size})`, `getPeriod(periodId)`, `getJournalEntry(entryId)`, `listDiscrepancies({status,page,size})`, `getDiscrepancy(id)`. Credential = `getDomainFacingToken()` (NEVER `getOperatorToken()`); no `X-Tenant-Id`; flat finance error envelope parser; `AbortController` hard timeout; **no 429 branch**; confidential/F7 logging (requestId + sanitised path only).
- `src/features/ledger-ops/api/types.ts` — Zod schemas: `MoneySchema` (string amount), `JournalLineSchema` (money + `exchangeRate` string + `baseAmount`), `JournalEntrySchema` (source.sourceType, lines, balanced), `TrialBalanceSchema`, `PeriodSchema` (+ snapshot), `DiscrepancySchema` (type, status, resolution?). Tolerant enums (`.catch`).
- `src/features/ledger-ops/api/ledger-state.ts` — server initial-state assembler (mirror `finance-state.ts`): eligibility gate + per-view seed + resilience boundary (401 redirect / 403 forbidden / 404 notFound / 503 degraded).
- `src/features/ledger-ops/hooks/use-ledger-ops.ts` — TanStack Query read hooks (sane staleTime, no auto-refetch).
- `src/features/ledger-ops/components/` — `TrialBalanceTable`, `PeriodsTable`, `PeriodDetail`, `JournalEntryLookup`, `JournalEntryDetail`, `DiscrepancyQueue`, `DiscrepancyDetail`, `LedgerOpsScreen` shell. Money rendered via `formatMoney` from minor-units string (scale-correct, never float). WCAG AA.
- `src/features/ledger-ops/index.ts` — barrel export.
- `src/app/(console)/ledger/page.tsx` — server component, registry-driven eligibility (`productKey=finance`), mirror `finance/page.tsx` (degraded / not-eligible / forbidden / degraded states).
- `src/app/api/ledger/**` — same-origin GET-only proxy routes: `trial-balance`, `periods`, `periods/[periodId]`, `entries/[entryId]`, `reconciliation/discrepancies`, `reconciliation/discrepancies/[id]`; `_proxy.ts` error mapper (`LedgerUnavailableError`).
- `src/shared/api/errors.ts` — add `LedgerUnavailableError` (mirror `FinanceUnavailableError`).
- nav registration — add a Ledger entry under the finance domain in the console sidebar nav.

### Tests

- unit: credential (`getDomainFacingToken`, `getOperatorToken` path **absent**); all GET, **mutation artifacts 0** (no Idempotency-Key/X-Operator-Reason/body/ledger-write); **F5 money grep assertion** (no `Number()`/`parseFloat`/`parseInt` on `amount`/`exchangeRate` anywhere in `features/ledger-ops/`); large-minor-units + exact-rate (`"13.5"`) bit-exact round-trip; flat error envelope parse; **no 429 branch**; confidential/F7 (no balance/line/code/token logging); eligibility block (no fabricated cross-tenant call); resilience taxonomy (401/403/404/503).
- component: TrialBalanceTable (base totals + inBalance), PeriodsTable/PeriodDetail (close snapshot), JournalEntryLookup/Detail (sourceType + multi-currency lines), DiscrepancyQueue/Detail (status filter + AMOUNT_MISMATCH FX-difference matched pair); honest state surfacing; unknown-enum degrade.
- regression: §3.1 attestation-marker count stays **16** (FE-006 no-drift guard); per-domain credential rule holds; existing finance/iam/wms/scm/erp routes unchanged; new ledger nav + route resolve.

## Out of Scope

- Any ledger **mutation** (`POST /entries`, `/revaluations`, `/settlements`, reconciliation ingest/resolve) — operator-domain, deferred (no console write parity for the ledger at this increment).
- Account-level drill (`GET /accounts/{ledgerAccountCode}/{balance,entries}`) and statement-detail (`GET /reconciliation/statements/{id}`) — **forward-declared** (a follow-up surface; do not fabricate).
- Any change to the finance `ledger-api.md` / `reconciliation-api.md` producer contracts (finance owns them — cross-reference only).
- A BFF change — the `console-bff` Operator Overview / Domain Health composition (§ 2.4.9.x) is separate and already carries a finance card; this section is a direct console-web → ledger-service read (like FE-009 finance), no BFF leg.

# Acceptance Criteria

- [ ] Spec PR merged: § 2.4.7.1 ledger binding + architecture.md entries + env, **before** the impl PR.
- [ ] `features/ledger-ops` renders trial balance, accounting periods (list + detail/snapshot), journal-entry-by-id, and the reconciliation discrepancy queue (list + detail) — all read-only, server-side, tenant-scoped.
- [ ] Credential = `getDomainFacingToken()`; `getOperatorToken()` path **absent** (test-asserted); no `X-Tenant-Id` sent.
- [ ] **Mutation artifacts 0** (test-asserted): no Idempotency-Key, X-Operator-Reason, request body, or ledger write call anywhere in the section.
- [ ] **F5 multi-currency money**: `money` + `exchangeRate` + `baseAmount` rendered precision-exact from minor-units string; **no** `Number()`/`parseFloat`/`parseInt` on `amount`/`exchangeRate` (grep-asserted); large-value + exact-rate bit-exact round-trip.
- [ ] Eligibility reuses `productKey=finance`; not-eligible → block with no fabricated cross-tenant call.
- [ ] Resilience: 401 → whole-session re-login; 403 → inline; 404 (entry/period/discrepancy not found) → inline actionable; 503/timeout → only the ledger section degrades; no 429 branch.
- [ ] Honest state surfacing: sourceType / period status / discrepancy type+status (incl. the 11th-increment AMOUNT_MISMATCH FX-difference matched pair); unknown enums degrade to a generic label, no throw.
- [ ] §3.1 attestation-marker count stays **16**; existing routes + per-domain credential rule unaffected (regression green).
- [ ] CI: Build & Test (console-web unit/component + lint + a11y) green; no required check failing at merge.

# Related Specs

- [`console-integration-contract.md` § 2.4.7.1](../../specs/contracts/console-integration-contract.md) (this task — finance ledger binding) + § 2.4.7 (FE-009 finance account, reused) + § 2.4.5/§ 2.4.5.1 (wms two-service precedent) + § 2.5 (resilience) + § 2.7 (assume-tenant token).
- [`console-web/architecture.md`](../../specs/services/console-web/architecture.md) — `features/ledger-ops` module + Auth Flow per-domain credential + env.
- [`ADR-MONO-013`](../../../../docs/adr/ADR-MONO-013-platform-console-foundation.md) (Model B single-UI federation) + [`ADR-MONO-020`](../../../../docs/adr/ADR-MONO-020-platform-console-assume-tenant.md) (domain-facing assume-tenant token).

# Related Contracts

- finance [`ledger-api.md`](../../../finance-platform/specs/contracts/http/ledger-api.md) — `GET /trial-balance`, `/periods[/{id}]`, `/entries/{id}` (authoritative, consumed read-only, **unchanged**).
- finance [`reconciliation-api.md`](../../../finance-platform/specs/contracts/http/reconciliation-api.md) — `GET /reconciliation/discrepancies[/{id}]` (authoritative, consumed read-only, **unchanged**).
- finance [`iam-integration.md`](../../../finance-platform/specs/integration/iam-integration.md) § *platform-console Operator Read Consumer* (TASK-FIN-BE-005 — the finance tenant gate the ledger shares with the account-service; the spec-first basis, already merged).

# Edge Cases

- Trial balance / period list on an empty tenant (no postings) → empty index, `inBalance: true`, no crash.
- Journal entry id unknown / not in tenant → `404 JOURNAL_ENTRY_NOT_FOUND` → inline actionable (lookup form stays mounted).
- Period OPEN (no `snapshot`) vs CLOSED (snapshot present) — render both honestly; `snapshot: null` is not an error.
- Multi-currency journal line — `money` (foreign) + `exchangeRate` (e.g. `"13.5"`) + `baseAmount` (KRW); a base-currency line has `exchangeRate "1"` and `baseAmount == money`; a 9th-increment revaluation line has `money.amount "0"` (foreign) with a non-zero KRW `baseAmount` — all rendered precision-exact.
- Discrepancy queue with `?status=` absent → all; `AMOUNT_MISMATCH` FX-difference carries both `externalRef` and `journalEntryId` and appears in `matches` too (settlement reconciled, FX gap flagged) — render the matched pair, never auto-adjusted.
- Unknown / future `sourceType` / period `status` / discrepancy `type`/`status` enum → generic label, no parser throw.
- Operator not finance-eligible → section blocked, no ledger call fabricated.

# Failure Scenarios

- ledger `503` / timeout / network → **only** the ledger section degrades; the console shell + IAM / wms / scm / finance-account / erp sections stay intact.
- ledger `401` (IAM OIDC session expired) → whole-session re-login (no partial authed state, no per-section degrade).
- ledger `403 TENANT_FORBIDDEN` (token not finance-scoped) → inline "not available / not scoped" (no crash, no re-login loop).
- Registry (`getCatalog`) failure on the page eligibility pre-flight → degraded shell (never block on an unproven negative); registry `401` → re-login.
- A stray `429` from the ledger (undocumented) → falls through to the default error path as a surfaced `ApiError` → degrade, **never** a fabricated `Retry-After` / backoff (test-asserted absence).
- Malformed / non-JSON error body → synthetic code, never a parser throw (defensive flat-envelope parser).
