# TASK-PC-FE-102 — split the `ledger-ops/api/ledger-api.ts` god-file into cohesive modules

**Status:** done
**Area:** platform-console / console-web · **Refactor only** (Reduce Module Size / Long File — 0 behavior change, 0 contract change)
**Closure:** PR #1717 squash `8766dccce8cd87ceda4ea12e95481b051f65bb9f`, 3-dim verified (state=MERGED · origin/main tip match · pre-merge all checks pass/skip incl. Frontend lint/build + unit + E2E smoke + Build & Test). 740-line monolith → ledger-client.ts (350) + ledger-reads-api.ts (306) + ledger-reconciliation-api.ts (94) + barrel (26); 12 exports import-stable; F5 dir-walk guards auto-cover new files; 0 test changes; tsc 0 / lint 0 / vitest 162 files · 1943 tests green (baseline count).
**Parent:** continuation of the console-web god-file split series (after TASK-PC-FE-098 `erp-api.ts`, 099 `use-erp-ops.ts`, 100 `ApprovalScreen.tsx`, 101 `OutboundOpsScreen.tsx`). `ledger-api.ts` is the direct twin of `erp-api.ts` (a server-side per-domain read client) and accreted across TASK-PC-FE-072/073/074/075/091/092 — ~740 lines.

## Goal

Split the single ~740-line `features/ledger-ops/api/ledger-api.ts` into cohesive
units **without changing any externally observable behavior** (HTTP calls,
headers, error semantics, log-event strings, Zod-validated shapes, the
no-`Idempotency-Key`/no-429 honesty all preserved). Pure structural move — no
logic edited. Mirrors the PC-FE-098 `erp-api.ts` split exactly.

The module path `@/features/ledger-ops/api/ledger-api` stays the **stable public
surface** via a barrel, so all 12 exported functions resolve unchanged for every
importer (12 `app/api/ledger/**` route handlers + `ledger-state.ts` + 5 test
files) — **0 import-site edits**.

### Split layout
- `api/ledger-client.ts` — the hardened `callLedger<T>()` HTTP core + the
  finance FLAT error-envelope parser (`parseLedgerError`) + the `CallOptions`
  shape + the `pageParams` helper + the per-domain-credential / resilience /
  **"NO 429 handling"** narrative.
- `api/ledger-reads-api.ts` — the nine pure GET reads: `getTrialBalance`,
  `listPeriods`, `getPeriod`, `getJournalEntry`, `getAccountBalance`,
  `getAccountEntries`, `getStatement`, `getPositionLots`, `getFxRates`.
- `api/ledger-reconciliation-api.ts` — the reconciliation cluster:
  `listDiscrepancies`, `getDiscrepancy`, and the ledger's FIRST and ONLY
  mutation `resolveDiscrepancy` (POST .../resolve, no Idempotency-Key).
- `api/ledger-api.ts` — barrel only (`export *` × 2).

`ledger-client.ts` is a leaf (imports no sibling ledger module); the two api
modules import `callLedger` + `pageParams` from it. No circular import.

## Hard constraints (behavior preservation)
- **Public export set unchanged** — the 12 `export async function`s remain
  importable from `@/features/ledger-ops/api/ledger-api`. `callLedger` /
  `pageParams` are `export`ed only from `ledger-client.ts` for the two api
  modules and re-exported by the barrel via `export *` of those modules — but
  `parseLedgerError` / `CallOptions` stay **non-exported** (internal), exactly
  as before; the barrel does NOT re-export `ledger-client.ts`, so `callLedger` /
  `pageParams` are not widened onto the `ledger-api` public surface.
- **Log-event strings byte-identical** — `ledger_no_gap_session` / `ledger_ok` /
  `ledger_unauthorized` / `ledger_forbidden` / `ledger_degraded` /
  `ledger_request_error` / `ledger_timeout` / `ledger_error`, all sanitised
  `logPath` route shapes, and the F7 redaction discipline are moved verbatim.
- **No 429 / no-Idempotency-Key honesty preserved** — the client carries no
  Retry-After / backoff branch and no Idempotency-Key header; the resolve
  mutation stays the only POST.
- **F5 directory-walk guards naturally cover the new files** — three tests
  (`ledger-api` / `ledger-account-api` / `ledger-statement-api`) walk the whole
  `features/ledger-ops/` tree asserting NO `Number()`/`parseFloat()`/`parseInt()`
  on `amount`/`exchangeRate` lines. Because the split moves code verbatim into
  the SAME directory, these guards include the new files automatically and stay
  green — **no test edit needed** (unlike the FE-098 erp single-file 429-guard).
- No change to `shared/api/*`, `shared/lib/*`, the `types` module, route
  handlers, hooks, components, pages, the producer contracts, or any spec.
- **Production code only — 0 test changes.**

## Procedure
1. Baseline (green at authoring): `npx vitest run tests/unit/ledger-*.test.ts` → pass.
2. Create `ledger-client.ts` / `ledger-reads-api.ts` / `ledger-reconciliation-api.ts`
   by moving the verbatim code blocks; convert `ledger-api.ts` to the barrel.
3. `tsc --noEmit` (0) + `next lint` (0) + `npx vitest run` (full) green.

## Acceptance Criteria
- `npx tsc --noEmit` → 0 errors (proves all 12 route handlers + state + tests
  still resolve the moved exports).
- `npx next lint` → 0 warnings/errors.
- `npx vitest run` → all green, **same test count as baseline** (162 files /
  1943 tests), including the F5 directory-walk guards.
- `ledger-api.ts` is a barrel (~26 lines; no logic remains). The former
  ~740-line monolith splits into `ledger-client.ts` (350) +
  `ledger-reads-api.ts` (306) + `ledger-reconciliation-api.ts` (94). Largest
  ledger api module drops 740 → 350 (−53%).
- 0 change to behavior, contracts, specs, route handlers, hooks, components,
  pages, log-event strings.

## Related Specs / Contracts
- `platform/refactoring-policy.md` (Long File / Reduce Module Size,
  behavior-preserving).
- `specs/services/console-web/architecture.md` (Layered by Feature; the split
  stays **feature-internal** to `ledger-ops/api/` — `callLedger` is
  ledger-credential/envelope specific, not promoted to `shared/`).
- `specs/contracts/console-integration-contract.md` § 2.4.7.1 (the preserved
  ledger behavior: per-domain credential reuse of § 2.4.7/§ 2.4.5, FLAT error
  envelope, no-429, resolve-only mutation with no Idempotency-Key).

## Edge Cases
- `pageParams` is used by `listPeriods` + `getAccountEntries` (reads) AND
  `listDiscrepancies` (reconciliation) — it lives in `ledger-client.ts` so both
  api modules import it.
- The resolve mutation (`resolveDiscrepancy`) stays grouped with the
  reconciliation reads (its domain cluster), not the pure-read module.
- The barrel must `export *` only the two api modules — re-exporting
  `ledger-client.ts` would widen `callLedger` / `pageParams` onto the public
  surface (a module-contract change).

## Failure Scenarios
- A changed log-event string → silent observability regression (review the diff).
- Re-exporting `ledger-client.ts` from the barrel → public-surface widening.
- Introducing a circular import (api module ↔ barrel) → keep `ledger-client.ts`
  a leaf; the api modules import the client, never the barrel.
- A stray `Number()`/`parseFloat()` on an `amount` line → the F5 directory-walk
  guards turn RED (they scan the whole feature tree, new files included).
