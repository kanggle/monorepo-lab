# Task ID

TASK-PC-FE-074

# Title

console-web — finance ledger account-level drill reads (trial-balance account → account ledger: balance + entries)

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

- **depends on**: `TASK-PC-FE-072` (the `features/ledger-ops` section — trial balance / periods / journal entry / reconciliation discrepancy queue) **MERGED** → origin/main (impl #1440 squash `85a0162a`), and `TASK-PC-FE-073` (the read+1-mutation carve-out) **MERGED** → origin/main (impl #1447 squash `05ce26d3`). This task adds **two read drills** to that section; it touches no mutation.
- **producer already on main (no cross-project prerequisite)**: the account-drill reads — finance [`ledger-api.md`](../../../finance-platform/specs/contracts/http/ledger-api.md) **§ 2** `GET /api/finance/ledger/accounts/{ledgerAccountCode}/entries` (paginated journal lines posted to one account, most-recent first) and **§ 3** `GET /api/finance/ledger/accounts/{ledgerAccountCode}/balance` (the account's running balance) — are **already authored + merged** (the ledger-service 8th-increment multi-currency surface). They are **unchanged, consumed only**. The ledger shares the finance tenant gate (TASK-FIN-BE-005) — no new finance-side prerequisite.
- **spec-first**: the console-side contract amendment (**§ 2.4.7.1 — promote the account-level drill from "forward-declared" to a surfaced read pair** in `console-integration-contract.md` + the `console-web/architecture.md` ledger-ops account-drill entries) lands **before/with** the code (this task's spec PR). The producer contract is **unchanged**.
- **part of**: the platform-console finance federation — completing the trial-balance UX loop (a trial-balance row's account code becomes the drill key into that account's running balance + paginated ledger lines). The statement-detail read (`GET /reconciliation/statements/{id}`) stays forward-declared.
- **read-only extension → Sonnet**: the slice is a pure read mirror of the established `features/ledger-ops` pattern (FE-072 reads) — id-driven account lookup + browsable drill-in, the same `getDomainFacingToken()` credential, F5 money rendering, flat error envelope, tolerant enum parsing. No new credential model, no mutation, no contract divergence — **Sonnet** for the established FE-mirror; the contract amendment is a forward-declaration promotion (not a new binding shape).

# Goal

Add the **account-level drill** reads to `features/ledger-ops` — the two producer endpoints the FE-072 surface explicitly **forward-declared**:

- **account balance** — `GET /api/finance/ledger/accounts/{ledgerAccountCode}/balance`, `200` → `{ ledgerAccountCode, type, normalSide, debitTotal, creditTotal, balance, balanceSide }` (the account's running balance; `balance = |debitTotal − creditTotal|`, `balanceSide` the side the net falls on). `404 LEDGER_ACCOUNT_NOT_FOUND` when the account code has no ledger account.
- **account entries (paginated)** — `GET /api/finance/ledger/accounts/{ledgerAccountCode}/entries`, `200` → `data` = array of `{ entryId, postedAt, direction, money, counterpartyLines? }` (the journal lines posted to one account, most-recent first), `meta` carries pagination. `404 LEDGER_ACCOUNT_NOT_FOUND`.

The drill makes the **trial-balance account codes clickable** — a trial-balance row's `ledgerAccountCode` selects that account and opens an **Account** view seeded with its balance + first page of entries. The account view also offers a **direct account-code lookup** (mirroring `JournalEntryLookup`) — the `CUSTOMER_WALLET:{accountId}` colon-form code is **URL-encoded** on the path (per `ledger-api.md` § Common shapes). **STRICTLY READ-ONLY** — this slice adds **no** mutation (the ledger's single mutation stays the FE-073 discrepancy resolve).

# Scope

## In Scope

### Spec-first (console-side — lands in this task's spec PR)

- `console-integration-contract.md` **§ 2.4.7.1** — promote the account-level drill (`GET /accounts/{ledgerAccountCode}/{balance,entries}`) from the **"forward-declared"** note to a **surfaced read pair** (add producer rows 7 & 8 to the read table; a new **Account-level drill reads** sub-bullet: credential reuse, F5 multi-currency balance, `404 LEDGER_ACCOUNT_NOT_FOUND`, F7 — the account code is confidential, sanitised out of logs; the colon-form URL-encode note). The statement-detail read stays forward-declared. **Read-only** — no mutation added.
- `console-web/architecture.md` — ledger-ops account-drill entries (banner, tree `api`/`hooks`/`components` — add the account client fns / hooks / `AccountLookup` + `AccountDetail`, References, Testing Strategy entry, `404 LEDGER_ACCOUNT_NOT_FOUND` mapping).

### Code (console-web)

- `features/ledger-ops/api/types.ts` — `AccountBalanceSchema` (`ledgerAccountCode`, `type` / `normalSide` / `balanceSide` tolerant free strings, `debitTotal` / `creditTotal` / `balance` F5 Money), `AccountEntryLineSchema` (`entryId`, `postedAt`, `direction` free string, `money` F5, `counterpartyLines?`), `AccountEntriesResponseSchema` (`{ data: AccountEntryLine[], meta }`), `AccountEntriesQueryParams`.
- `features/ledger-ops/api/ledger-api.ts` — `getAccountBalance(code)` + `getAccountEntries(code, params)` (both `GET`, domain-facing token, **URL-encode** the code on the path, sanitised `logPath` carrying **no** account code, `404 LEDGER_ACCOUNT_NOT_FOUND` → `ApiError`, `503`/timeout → `LedgerUnavailableError`). No mutation, no `Idempotency-Key`, no `X-Tenant-Id`, no 429 branch.
- `features/ledger-ops/api/ledger-state.ts` — seed the account drill when an `?accountCode=` is supplied (the balance + first page of entries); a `404 LEDGER_ACCOUNT_NOT_FOUND` on the seeded code → an inline "no such account" state (mirrors the entry `notFound`); add `accountBalance` / `accountEntries` / `accountNotFound` to `LedgerSectionState`.
- `features/ledger-ops/hooks/use-ledger-ops.ts` — `useAccountBalance(code, initial?)` + `useAccountEntries(code, params, initial?)` read hooks (same `retry: false` / no-refetch-storm posture as the other reads).
- `features/ledger-ops/components/` — `AccountLookup` (account-code input, mirrors `JournalEntryLookup`; the colon-form note) + `AccountDetail` (a balance card: `type` / `normalSide` / `debitTotal` / `creditTotal` / `balance` / `balanceSide` via `formatMoney`; a paginated entries table: `postedAt` / `direction` / `money` / `entryId` link back into the Journal Entry tab). Make `TrialBalanceTable` account codes **clickable** (new optional `onSelectAccount(code)` prop — when absent the table stays plain read text, FE-072 callers unaffected). Add an **Account** tab to `LedgerOpsScreen`; wire the trial-balance code click → select account + switch to the Account tab; a `404` on lookup renders inline (the lookup stays mounted).
- `app/api/ledger/accounts/[ledgerAccountCode]/balance/route.ts` + `app/api/ledger/accounts/[ledgerAccountCode]/entries/route.ts` — same-origin **GET** proxies (attach the domain-facing token server-side, pagination passthrough on entries, `mapLedgerError`).
- `app/(console)/ledger/page.tsx` — read an optional `?accountCode=`; seed the account drill server-side (the `entryId` seed stays).
- `features/ledger-ops/index.ts` — barrel exports (`AccountLookup`, `AccountDetail`, the new types/hooks).
- `shared/api/errors.ts` — `LEDGER_ACCOUNT_NOT_FOUND` inline message (if not already covered by the generic 404 family).

### Tests

- account-drill api: domain-facing token (`getOperatorToken` **absent**); `GET` only (mutation-artifact-free — `Idempotency-Key` / `X-Operator-Reason` / body absent); the colon-form code is **URL-encoded** on the path; F5 — the balance `debitTotal`/`creditTotal`/`balance` + the entry `money` round-trip bit-exact as minor-units strings, **no** `Number()`/`parseFloat()`/`parseInt()` on `amount`; `404 LEDGER_ACCOUNT_NOT_FOUND` / `503` / timeout mapping; a 429 surfaces as a plain `ApiError` (no backoff); the sanitised `logPath` carries **no** account code (F7).
- account-drill proxy: GET routes; pagination passthrough; domain-facing token (not operator); `404` passthrough.
- account-drill state: `?accountCode=` seeds the balance + entries; `404 LEDGER_ACCOUNT_NOT_FOUND` → inline `accountNotFound`; no code → no account seed.
- component: the **Account** tab renders the balance card + entries table (F5 scale-correct); a trial-balance account code **click** drills in (selects the account, switches tab, seeds the read); the lookup colon-form is accepted; `404` inline; axe-clean. The entries table `entryId` links back to the Journal Entry tab.
- regression: §3.1 attestation-marker count stays **16**; the FE-072 read suites + the FE-073 resolve suite + all IAM/wms/scm/finance/erp suites still pass; the ledger surface stays **single-mutation** (no new mutation artifact introduced by this read slice).

## Out of Scope

- The reconciliation **statement-detail** read (`GET /reconciliation/statements/{id}`) — stays forward-declared (a follow-up surface).
- Every ledger **mutation**: `POST /entries` (manual posting), `/revaluations`, `/settlements`, reconciliation `POST /statements` (ingest) — operator-domain, deferred; the FE-073 discrepancy resolve stays the **only** mutation.
- Any change to the finance `ledger-api.md` producer contract (finance owns it — cross-reference only).
- An account-code search/typeahead over the ledger (the ledger has no account list/search GET — id-driven, the same honest constraint as journal entries; the trial balance is the browsable account index).

# Acceptance Criteria

- [ ] Spec PR merged: § 2.4.7.1 account-drill promotion (rows 7 & 8 + the Account-level drill reads sub-bullet) + architecture.md account-drill entries, **before** the impl PR.
- [ ] An operator can drill from a trial-balance account code into that account's **running balance** + **paginated ledger entries**; and can look an account up directly (colon-form URL-encoded).
- [ ] Header matrix is producer-faithful (test-asserted): domain-facing token (**never** `getOperatorToken()`); `GET` only; **no** `Idempotency-Key` / `X-Operator-Reason` / `X-Tenant-Id` / body; the account-drill reads add **no** mutation artifact.
- [ ] F5: the balance + entry money values render scale-correct from the minor-units **string** via `formatMoney`; **no** `Number()`/`parseFloat()`/`parseInt()` is applied to any `amount` anywhere in `features/ledger-ops/` (grep-asserted).
- [ ] `404 LEDGER_ACCOUNT_NOT_FOUND` (unknown code, on seed or lookup) → inline actionable "no such account" (no crash, no re-login).
- [ ] F7: the sanitised `logPath` carries **no** account code; no balance / line / token logging.
- [ ] Resilience: `401` → whole-session re-login; `403` → inline "not scoped"; `503`/timeout → only the ledger section degrades; **no** 429 branch.
- [ ] §3.1 attestation-marker count stays **16**; FE-072 read suites + FE-073 resolve suite + sibling suites regression-green.
- [ ] CI: Build & Test (console-web unit/component + lint + a11y) green; no required check failing at merge.

# Related Specs

- [`console-integration-contract.md` § 2.4.7.1](../../specs/contracts/console-integration-contract.md) (this task — account-drill promotion from forward-declared) + § 2.4.7 (the finance per-domain credential rule reused) + § 2.5 (resilience).
- [`console-web/architecture.md`](../../specs/services/console-web/architecture.md) — ledger-ops account-drill entries + Auth Flow.

# Related Contracts

- finance [`ledger-api.md`](../../../finance-platform/specs/contracts/http/ledger-api.md) **§ 2** `GET /accounts/{ledgerAccountCode}/entries` + **§ 3** `GET /accounts/{ledgerAccountCode}/balance` (authoritative, consumed **read-only**, **unchanged**) + § 4 (the trial-balance account rows that drive the drill — FE-072).
- finance [`iam-integration.md`](../../../finance-platform/specs/integration/iam-integration.md) § *platform-console Operator Read Consumer* (TASK-FIN-BE-005 — the finance tenant gate; already merged).

# Edge Cases

- A trial-balance account code in the colon-form `CUSTOMER_WALLET:{accountId}` → URL-encoded on the producer path (the colon is encoded). The drill round-trips the exact code.
- An account with no postings on a page beyond the last → empty entries page (not an error); the balance still renders.
- A multi-currency account (per-currency `debitTotal`/`creditTotal`) → each money renders scale-correct via `formatMoney` from its string; the `balance`/`balanceSide` are surfaced honestly.
- Unknown / future `type` / `normalSide` / `balanceSide` / `direction` enum value → degrades to a generic label, never a parser throw (tolerant-parser discipline, the same as the FE-072 reads).
- Account code unknown (typo / never-posted) → `404 LEDGER_ACCOUNT_NOT_FOUND` → inline "no such account" (the lookup stays mounted; no crash).
- An entries-table `entryId` click → re-uses the existing Journal Entry tab drill (no new entry endpoint fabricated).
- Operator not finance-eligible → the section is blocked (FE-072) → no account drill reachable.

# Failure Scenarios

- ledger `503` / timeout / network on the balance or entries GET → `LedgerUnavailableError` → the ledger section degrades (the account view shows the degraded notice); shell + IAM/wms/scm/finance-account/erp sections intact.
- ledger `401` (session expired) on the account seed → whole-session re-login.
- ledger `403` (token not finance-scoped) → inline "not scoped".
- Malformed error body → defensive flat-envelope parser, synthetic code, no throw.
- A stray `429` (undocumented) → surfaced `ApiError`, no fabricated backoff (the no-429 honesty, asserted).
- A regression that introduces a `Number()`/`parseFloat()`/`parseInt()` on an `amount` line → the F5 grep test fails (the precision-preservation guard).
