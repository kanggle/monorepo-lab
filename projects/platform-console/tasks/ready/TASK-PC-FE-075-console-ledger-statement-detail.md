# Task ID

TASK-PC-FE-075

# Title

console-web — finance ledger reconciliation statement-detail read (statement → matches + discrepancies hub)

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

- **depends on**: `TASK-PC-FE-072` (the `features/ledger-ops` section — incl. the reconciliation discrepancy queue/detail) **MERGED**, `TASK-PC-FE-073` (the discrepancy *resolve* mutation) **MERGED**, and `TASK-PC-FE-074` (the account-level drill reads) **MERGED** → origin/main. This task adds the **last forward-declared ledger read**; it touches no mutation.
- **producer already on main (no cross-project prerequisite)**: the statement-detail read — finance [`reconciliation-api.md`](../../../finance-platform/specs/contracts/http/reconciliation-api.md) **§ 3** `GET /api/finance/ledger/reconciliation/statements/{id}` (statement detail + its matches + discrepancies) — is **already authored + merged** (TASK-FIN-BE-010, the 4th ledger increment). It is **unchanged, consumed only**. The ledger shares the finance tenant gate (TASK-FIN-BE-005) — no new finance-side prerequisite.
- **spec-first**: the console-side contract amendment (**§ 2.4.7.1 — promote the statement-detail read from "forward-declared" to a surfaced read** in `console-integration-contract.md` + the `console-web/architecture.md` ledger-ops statement entries) lands **before/with** the code (this task's spec PR). The producer contract is **unchanged**.
- **part of**: the platform-console finance federation — **completing the ledger console read surface** (with this read, every ledger producer read is surfaced; only the producer's non-existent endpoints — a statement/account list/search — remain absent, and the ledger mutations beyond the FE-073 resolve stay out of scope). The statement view closes the reconciliation context loop: a matched line's `journalEntryId` drills into the journal-entry view, a recorded discrepancy drills into the discrepancy detail (where FE-073 resolve lives).

# Goal

Add the **reconciliation statement-detail** read to `features/ledger-ops` — the one producer read the FE-072/074 surface still forward-declared:

- **statement detail** — `GET /api/finance/ledger/reconciliation/statements/{id}`, `200` → `{ statementId, ledgerAccountCode, source, statementDate, matchedCount, discrepancyCount, matches: [ { statementLineExternalRef, journalEntryId, money } ], discrepancies: [ <discrepancy> ] }` (the § 1 ingest `data` shape). `404 RECONCILIATION_STATEMENT_NOT_FOUND` when unknown / not in tenant.

The statement view is the **reconciliation source hub**: an operator looks up a statement by id (the ledger has **no** statement list/search GET — id-driven, the same honest constraint as journal entries + accounts; statement ids come from the ingest the operator's integration ran — ingest is **out of console scope**), and the console renders the statement header (account / source / date / matched-count / discrepancy-count) + a **matches** table (each `journalEntryId` drills into the existing Journal Entry tab) + a **discrepancies** table (each `discrepancyId` drills into the existing reconciliation discrepancy detail, where the FE-073 resolve affordance lives — incl. the 11th-increment FX-difference `AMOUNT_MISMATCH` carrying both `externalRef` + `journalEntryId`). **STRICTLY READ-ONLY** — this slice adds **no** mutation (the ledger's single mutation stays the FE-073 discrepancy resolve).

# Scope

## In Scope

### Spec-first (console-side — lands in this task's spec PR)

- `console-integration-contract.md` **§ 2.4.7.1** — promote the statement-detail read (`GET /reconciliation/statements/{id}`) from the **"remains forward-declared"** note to a **surfaced read** (add producer **row 9** to the read table; a new **Reconciliation statement-detail read** sub-bullet: credential reuse, the matches/discrepancies cross-links, `404 RECONCILIATION_STATEMENT_NOT_FOUND`, F7 — the statementId is confidential, sanitised out of logs; the id-driven honest constraint). With this read, **no producer read remains forward-declared** (only the non-existent statement/account list/search and the out-of-scope ledger mutations beyond the FE-073 resolve). **Read-only** — no mutation added.
- `console-web/architecture.md` — ledger-ops statement entries (banner, tree `api`/`hooks`/`components` — add `getStatement` / `useStatement` / `StatementLookup` + `StatementDetail`, References, `404 RECONCILIATION_STATEMENT_NOT_FOUND` mapping).

### Code (console-web)

- `features/ledger-ops/api/types.ts` — `StatementMatchSchema` (`statementLineExternalRef`, `journalEntryId`, `money` F5), `StatementSchema` (`statementId`, `ledgerAccountCode`, `source` / `statementDate` strings, `matchedCount` / `discrepancyCount` numbers, `matches: StatementMatch[]`, `discrepancies: Discrepancy[]` — reuse the existing tolerant `DiscrepancySchema`). Tolerant passthrough for unknown fields.
- `features/ledger-ops/api/ledger-api.ts` — `getStatement(statementId)` (GET, domain-facing token, **URL-encode** the id on the path, sanitised `logPath` carrying **no** statementId, `404 RECONCILIATION_STATEMENT_NOT_FOUND` → `ApiError`, `503`/timeout → `LedgerUnavailableError`). No mutation, no `Idempotency-Key`, no `X-Tenant-Id`, no 429 branch.
- `features/ledger-ops/api/ledger-state.ts` — seed the statement when a `?statementId=` is supplied; a `404 RECONCILIATION_STATEMENT_NOT_FOUND` on the seeded id → an inline "no such statement" state (mirrors the entry / account `notFound`); add `statement` / `statementNotFound` to `LedgerSectionState`.
- `features/ledger-ops/hooks/use-ledger-ops.ts` — `useStatement(statementId, initial?)` read hook (same `retry: false` / no-refetch-storm posture).
- `features/ledger-ops/components/` — `StatementLookup` (statementId input, mirrors `JournalEntryLookup` / `AccountLookup`; id-driven note) + `StatementDetail` (header card: account / source / statementDate / matchedCount / discrepancyCount; a **matches** table with `money` via `formatMoney` + `journalEntryId` linking back into the Journal Entry tab; a **discrepancies** table reusing the discrepancy row render — `type` / amounts / status — with `discrepancyId` linking into the reconciliation discrepancy detail). Wire the statement lookup + detail **into the existing 대사 (reconciliation) tab** (above the discrepancy queue) — no new tab; the statement's discrepancy rows set the selected discrepancy (drilling the existing detail, where FE-073 resolve lives), and a match-row `journalEntryId` switches to the Journal Entry tab. A `404` on lookup renders inline (the lookup stays mounted).
- `app/api/ledger/reconciliation/statements/[statementId]/route.ts` — same-origin **GET** proxy (attach the domain-facing token server-side, `mapLedgerError`).
- `app/(console)/ledger/page.tsx` — read an optional `?statementId=`; seed the statement server-side (the `entryId` + `accountCode` seeds stay).
- `features/ledger-ops/index.ts` — barrel exports (`StatementLookup`, `StatementDetail`, the new types/hook).
- `shared/api/errors.ts` — `RECONCILIATION_STATEMENT_NOT_FOUND` inline message.

### Tests

- statement api: domain-facing token (`getOperatorToken` **absent**); `GET` only (mutation-artifact-free — `Idempotency-Key` / `X-Operator-Reason` / body absent); the id is **URL-encoded** on the path; F5 — the match `money` round-trips bit-exact as a minor-units string, **no** `Number()`/`parseFloat()`/`parseInt()` on `amount`; `404 RECONCILIATION_STATEMENT_NOT_FOUND` / `503` / timeout mapping; a 429 surfaces as a plain `ApiError` (no backoff); the sanitised `logPath` carries **no** statementId (F7).
- statement proxy: GET route; domain-facing token (not operator); `404` passthrough.
- statement state: `?statementId=` seeds the statement; `404 RECONCILIATION_STATEMENT_NOT_FOUND` → inline `statementNotFound`; no id → no statement seed.
- component: the statement detail renders header + matches table (F5 scale-correct) + discrepancies table; a match-row `journalEntryId` click switches to the Journal Entry tab with that entryId; a discrepancy-row click selects that discrepancy in the existing detail; `404` inline; axe-clean.
- regression: §3.1 attestation-marker count stays **16**; the FE-072/074 read suites + the FE-073 resolve suite + all IAM/wms/scm/finance/erp suites still pass; the ledger surface stays **single-mutation** (no new mutation artifact).

## Out of Scope

- Any ledger **mutation**: `POST /entries`, `/revaluations`, `/settlements`, reconciliation `POST /statements` (ingest) — operator-domain, deferred; the FE-073 discrepancy resolve stays the **only** mutation.
- A statement **list/search** GET over the ledger (the producer has none — id-driven, the same honest constraint as entries + accounts).
- Any change to the finance `reconciliation-api.md` producer contract (finance owns it — cross-reference only).
- Re-running ingest / matching from the console.

# Acceptance Criteria

- [ ] Spec PR merged: § 2.4.7.1 statement-detail promotion (read-table **row 9** + the Reconciliation statement-detail read sub-bullet) + architecture.md statement entries, **before** the impl PR.
- [ ] An operator can look up a reconciliation **statement by id** and see its header + matches + discrepancies; a match-row `journalEntryId` drills into the Journal Entry view; a discrepancy-row drills into the discrepancy detail.
- [ ] Header matrix is producer-faithful (test-asserted): domain-facing token (**never** `getOperatorToken()`); `GET` only; **no** `Idempotency-Key` / `X-Operator-Reason` / `X-Tenant-Id` / body; the statement read adds **no** mutation artifact.
- [ ] F5: the match `money` values render scale-correct from the minor-units **string** via `formatMoney`; **no** `Number()`/`parseFloat()`/`parseInt()` on any `amount` anywhere in `features/ledger-ops/` (grep-asserted).
- [ ] `404 RECONCILIATION_STATEMENT_NOT_FOUND` (unknown id, on seed or lookup) → inline actionable "no such statement" (no crash, no re-login).
- [ ] F7: the sanitised `logPath` carries **no** statementId; no balance / line / token logging.
- [ ] Resilience: `401` → whole-session re-login; `403` → inline "not scoped"; `503`/timeout → only the ledger section degrades; **no** 429 branch.
- [ ] §3.1 attestation-marker count stays **16**; FE-072/074 read suites + FE-073 resolve suite + sibling suites regression-green.
- [ ] CI: Build & Test (console-web unit/component + lint + a11y) green; no required check failing at merge.

# Related Specs

- [`console-integration-contract.md` § 2.4.7.1](../../specs/contracts/console-integration-contract.md) (this task — statement-detail promotion from forward-declared) + § 2.4.7 (the finance per-domain credential rule reused) + § 2.5 (resilience).
- [`console-web/architecture.md`](../../specs/services/console-web/architecture.md) — ledger-ops statement entries + Auth Flow.

# Related Contracts

- finance [`reconciliation-api.md`](../../../finance-platform/specs/contracts/http/reconciliation-api.md) **§ 3** `GET /reconciliation/statements/{id}` (authoritative, consumed **read-only**, **unchanged**) + § 1 (the statement `data` shape) + § 4/§ 5 (the discrepancy read shapes from FE-072, reused in the statement's discrepancies array).
- finance [`iam-integration.md`](../../../finance-platform/specs/integration/iam-integration.md) § *platform-console Operator Read Consumer* (TASK-FIN-BE-005 — the finance tenant gate; already merged).

# Edge Cases

- A statement with `matchedCount: 0` (all lines unmatched) → matches table empty, discrepancies table populated (not an error).
- A statement with `discrepancyCount: 0` (fully reconciled) → discrepancies table empty, matches table populated.
- A multi-currency statement match (`money` in USD) → renders scale-correct via `formatMoney` from its string.
- The 11th-increment FX-difference `AMOUNT_MISMATCH` discrepancy appears **both** in the statement's `discrepancies` (carrying `externalRef` + `journalEntryId`) AND its matched line in `matches` (the transaction leg reconciled) — both surfaced honestly.
- A match-row `journalEntryId` click → re-uses the existing Journal Entry tab drill (no new entry endpoint fabricated); a discrepancy-row click → re-uses the existing reconciliation discrepancy detail.
- Statement id unknown (typo / wrong tenant) → `404 RECONCILIATION_STATEMENT_NOT_FOUND` → inline "no such statement" (the lookup stays mounted; no crash).
- Operator not finance-eligible → the section is blocked (FE-072) → no statement lookup reachable.

# Failure Scenarios

- ledger `503` / timeout / network on the statement GET → `LedgerUnavailableError` → the ledger section degrades (the statement view shows the degraded notice); shell + IAM/wms/scm/finance-account/erp sections intact.
- ledger `401` (session expired) on the statement seed → whole-session re-login.
- ledger `403` (token not finance-scoped) → inline "not scoped".
- Malformed error body → defensive flat-envelope parser, synthetic code, no throw.
- A stray `429` (undocumented) → surfaced `ApiError`, no fabricated backoff (the no-429 honesty, asserted).
- A regression that introduces a `Number()`/`parseFloat()`/`parseInt()` on an `amount` line → the F5 grep test fails (the precision-preservation guard).
