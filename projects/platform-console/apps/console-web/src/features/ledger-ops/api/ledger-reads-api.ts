import {
  TrialBalanceSchema,
  type TrialBalance,
  JournalEntrySchema,
  type JournalEntry,
  PeriodSchema,
  type Period,
  PeriodsResponseSchema,
  type PeriodsResponse,
  type PeriodsQueryParams,
  AccountBalanceSchema,
  type AccountBalance,
  AccountEntriesResponseSchema,
  type AccountEntriesResponse,
  type AccountEntriesQueryParams,
  StatementSchema,
  type Statement,
  PositionLotsResponseSchema,
  type PositionLotsResponse,
  FxRatesResponseSchema,
  type FxRatesResponse,
} from './types';
import { callLedger, pageParams } from './ledger-client';

// ---------------------------------------------------------------------------
// trial balance — GET /api/finance/ledger/trial-balance
//   ledger-api.md § 4 envelope = { data: TrialBalance, meta }. READ-ONLY.
//   Index-style browsable read (no input — tenant-scoped from the JWT).
// ---------------------------------------------------------------------------

export async function getTrialBalance(): Promise<TrialBalance> {
  return callLedger(
    {
      path: '/api/finance/ledger/trial-balance',
      logPath: '/api/finance/ledger/trial-balance',
    },
    (json) => {
      const env = (json ?? {}) as { data?: unknown };
      return TrialBalanceSchema.parse(env.data);
    },
  );
}

// ---------------------------------------------------------------------------
// accounting periods (list) — GET /api/finance/ledger/periods?page=&size=
//   ledger-api.md § 7 envelope = { data: [ Period (no snapshot) ], meta }.
// ---------------------------------------------------------------------------

export async function listPeriods(
  params: PeriodsQueryParams = {},
): Promise<PeriodsResponse> {
  const qs = new URLSearchParams();
  pageParams(qs, params.page, params.size);
  return callLedger(
    {
      path: `/api/finance/ledger/periods?${qs.toString()}`,
      logPath: '/api/finance/ledger/periods',
    },
    (json) => PeriodsResponseSchema.parse(json),
  );
}

// ---------------------------------------------------------------------------
// accounting period (detail) — GET /api/finance/ledger/periods/{periodId}
//   ledger-api.md § 8 envelope = { data: Period (+ snapshot when CLOSED),
//   meta }. 404 ACCOUNTING_PERIOD_NOT_FOUND.
// ---------------------------------------------------------------------------

export async function getPeriod(periodId: string): Promise<Period> {
  return callLedger(
    {
      path: `/api/finance/ledger/periods/${encodeURIComponent(periodId)}`,
      // confidential / F7 — the log path carries NO periodId.
      logPath: '/api/finance/ledger/periods/{id}',
    },
    (json) => {
      const env = (json ?? {}) as { data?: unknown };
      return PeriodSchema.parse(env.data);
    },
  );
}

// ---------------------------------------------------------------------------
// journal entry (detail) — GET /api/finance/ledger/entries/{entryId}
//   ledger-api.md § 1 envelope = { data: JournalEntry, meta }. READ-ONLY.
//   Id-driven (no list/search GET — the honest ledger constraint). 404
//   JOURNAL_ENTRY_NOT_FOUND.
// ---------------------------------------------------------------------------

export async function getJournalEntry(
  entryId: string,
): Promise<JournalEntry> {
  return callLedger(
    {
      path: `/api/finance/ledger/entries/${encodeURIComponent(entryId)}`,
      // confidential / F7 — the log path carries NO entryId.
      logPath: '/api/finance/ledger/entries/{id}',
    },
    (json) => {
      const env = (json ?? {}) as { data?: unknown };
      return JournalEntrySchema.parse(env.data);
    },
  );
}

// ---------------------------------------------------------------------------
// account balance — GET /api/finance/ledger/accounts/{ledgerAccountCode}/balance
//   ledger-api.md § 3 envelope = { data: AccountBalance, meta }. READ-ONLY.
//   Id-driven (the colon-form code is URL-encoded on the path). 404
//   LEDGER_ACCOUNT_NOT_FOUND. This read adds NO mutation artifact.
//
//   F7 (§ 2.4.7.1 confidential / TASK-PC-FE-074): the account code is
//   confidential — the sanitised `logPath` carries NO account code (only
//   the `{code}` placeholder, consistent with the entryId / periodId /
//   discrepancyId / accountCode sanitisation pattern).
//
//   STRICTLY GET — NO method/body, NO Idempotency-Key, NO X-Operator-Reason,
//   NO X-Tenant-Id (all handled by `callLedger`). No 429 branch (the ledger
//   has no documented 429 — the no-429 honesty, TASK-PC-FE-072 / § 2.4.7.1).
// ---------------------------------------------------------------------------

export async function getAccountBalance(
  ledgerAccountCode: string,
): Promise<AccountBalance> {
  return callLedger(
    {
      path: `/api/finance/ledger/accounts/${encodeURIComponent(ledgerAccountCode)}/balance`,
      // confidential / F7 — the log path carries NO account code.
      logPath: '/api/finance/ledger/accounts/{code}/balance',
    },
    (json) => {
      const env = (json ?? {}) as { data?: unknown };
      return AccountBalanceSchema.parse(env.data);
    },
  );
}

// ---------------------------------------------------------------------------
// account entries — GET /api/finance/ledger/accounts/{ledgerAccountCode}/entries
//   ledger-api.md § 2 envelope = { data: [ AccountEntryLine ], meta }.
//   Paginated, most-recent first. READ-ONLY. Id-driven (code URL-encoded).
//   404 LEDGER_ACCOUNT_NOT_FOUND. This read adds NO mutation artifact.
//
//   F7: the sanitised `logPath` carries NO account code.
//   STRICTLY GET — same honesty constraints as `getAccountBalance`.
// ---------------------------------------------------------------------------

export async function getAccountEntries(
  ledgerAccountCode: string,
  params: AccountEntriesQueryParams = {},
): Promise<AccountEntriesResponse> {
  const qs = new URLSearchParams();
  pageParams(qs, params.page, params.size);
  return callLedger(
    {
      path: `/api/finance/ledger/accounts/${encodeURIComponent(ledgerAccountCode)}/entries?${qs.toString()}`,
      // confidential / F7 — the log path carries NO account code.
      logPath: '/api/finance/ledger/accounts/{code}/entries',
    },
    (json) => AccountEntriesResponseSchema.parse(json),
  );
}

// ---------------------------------------------------------------------------
// reconciliation statement-detail — TASK-PC-FE-075
//   GET /api/finance/ledger/reconciliation/statements/{id}
//   reconciliation-api.md § 3 envelope = { data: Statement, meta }.
//   STRICTLY READ-ONLY — this read adds NO mutation artifact. The statement
//   view is id-driven (the ledger has no statement list/search GET — the
//   honest constraint, same as entries + accounts).
//
//   Header matrix (honest, producer-faithful — § 2.4.7.1):
//     - the SAME domain-facing IAM OIDC token (NEVER getOperatorToken());
//     - GET only — NO body, NO Idempotency-Key, NO X-Operator-Reason,
//       NO X-Tenant-Id (handled by callLedger).
//     - No 429 branch (the ledger has no documented 429).
//
//   Errors (SAME taxonomy as the other reads):
//     - 404 RECONCILIATION_STATEMENT_NOT_FOUND → ApiError (inline
//       actionable; the lookup form stays mounted).
//     - 503 / timeout → LedgerUnavailableError (ledger section degrades).
//
//   F7 (§ 2.4.7.1 confidential): the sanitised `logPath` carries NO
//   statementId — only the `{id}` placeholder (consistent with the
//   entryId / periodId / discrepancyId / accountCode sanitisation pattern).
// ---------------------------------------------------------------------------

/**
 * `getStatement(statementId)` — reads the reconciliation statement detail.
 * READ-ONLY. The domain-facing IAM OIDC access token is attached by
 * `callLedger`; NEVER `getOperatorToken()`. Id-driven; `statementId` is
 * `encodeURIComponent`-encoded on the path. The sanitised `logPath` carries
 * NO statementId (F7). `404 RECONCILIATION_STATEMENT_NOT_FOUND` → ApiError;
 * `503`/timeout → LedgerUnavailableError. Adds NO mutation artifact.
 */
export async function getStatement(statementId: string): Promise<Statement> {
  return callLedger(
    {
      path: `/api/finance/ledger/reconciliation/statements/${encodeURIComponent(statementId)}`,
      // confidential / F7 — the log path carries NO statementId.
      logPath: '/api/finance/ledger/reconciliation/statements/{id}',
    },
    (json) => {
      const env = (json ?? {}) as { data?: unknown };
      return StatementSchema.parse(env.data);
    },
  );
}

// ---------------------------------------------------------------------------
// FX position open-lots drill — TASK-PC-FE-091
//   § 12 GET /api/finance/ledger/settlements/{ledgerAccountCode}/{currency}/lots
//   ledger-api.md § 12 envelope = { data: { lots[], totalRemainingForeignMinor,
//   totalCarryingBaseMinor, lotCount }, meta }. STRICTLY READ-ONLY — this read
//   adds NO mutation. id-driven by (ledgerAccountCode, currency): the
//   colon-form account code (e.g. `CUSTOMER_WALLET:acc-1`) is
//   `encodeURIComponent`-encoded on the producer path; `currency` is a
//   3-letter ISO-4217 code, also `encodeURIComponent`-encoded for safety.
//
//   Header matrix (honest, producer-faithful — § 2.4.7.1):
//     - the SAME domain-facing IAM OIDC token (NEVER getOperatorToken());
//     - GET only — NO body, NO Idempotency-Key, NO X-Operator-Reason,
//       NO X-Tenant-Id (handled by callLedger).
//     - No 429 branch (the ledger has no documented 429).
//
//   Errors (SAME taxonomy as the other reads):
//     - empty position → 200 with lots: [] / totals "0" / lotCount 0 (the
//       producer's empty-state — NOT a 404; the UI renders an empty-state
//       message, never an error).
//     - 400 VALIDATION_ERROR (unsupported currency) → ApiError (inline
//       actionable; the lookup form stays mounted).
//     - 403 TENANT_FORBIDDEN → ApiError (inline "not scoped").
//     - 503 / timeout → LedgerUnavailableError (ledger section degrades).
//
//   F7 (§ 2.4.7.1 confidential): the sanitised `logPath` carries NEITHER the
//   account code NOR the currency — only the `{code}/{currency}` placeholder
//   (consistent with the accountCode / statementId sanitisation pattern).
// ---------------------------------------------------------------------------

/**
 * `getPositionLots(ledgerAccountCode, currency)` — reads the open FX
 * acquisition lots for one `(account, currency)` position. READ-ONLY. The
 * domain-facing IAM OIDC access token is attached by `callLedger`; NEVER
 * `getOperatorToken()`. id-driven; both the account code AND the currency are
 * `encodeURIComponent`-encoded on the path. The sanitised `logPath` carries
 * NEITHER the account code NOR the currency (F7). An empty position is a
 * normal `200` (`lots: []`, totals `"0"`); `400 VALIDATION_ERROR` (bad
 * currency) → ApiError; `503`/timeout → LedgerUnavailableError. Adds NO
 * mutation artifact. All `*Minor` fields survive as F5 minor-units STRINGS.
 */
export async function getPositionLots(
  ledgerAccountCode: string,
  currency: string,
): Promise<PositionLotsResponse> {
  return callLedger(
    {
      path: `/api/finance/ledger/settlements/${encodeURIComponent(ledgerAccountCode)}/${encodeURIComponent(currency)}/lots`,
      // confidential / F7 — the log path carries NEITHER account code NOR currency.
      logPath: '/api/finance/ledger/settlements/{code}/{currency}/lots',
    },
    (json) => {
      const env = (json ?? {}) as { data?: unknown };
      return PositionLotsResponseSchema.parse(env.data);
    },
  );
}

// ---------------------------------------------------------------------------
// FX 환율 피드 대시보드 — TASK-PC-FE-092
//   GET /api/finance/ledger/fx-rates (FIN-BE-033)
//   Producer envelope = { data: { feedEnabled, rates: [...] }, meta }.
//   STRICTLY READ-ONLY — global list, no path/query parameters.
//
//   Header matrix (honest, producer-faithful — § 2.4.7.1 REUSE):
//     - the SAME domain-facing IAM OIDC token (NEVER getOperatorToken());
//     - GET only — NO body, NO Idempotency-Key, NO X-Operator-Reason,
//       NO X-Tenant-Id (handled by callLedger).
//     - No 429 branch (the ledger has no documented 429).
//
//   `rate` is a decimal **string** (F5 — NEVER Number/parseFloat/parseInt).
//   An empty cache → 200 with `rates: []` (NOT a 404 — empty-state).
//   `logPath` is a fixed constant (no id/code/currency to sanitise).
// ---------------------------------------------------------------------------

/**
 * `getFxRates()` — reads the FX feed cache from the ledger service.
 * Returns `{ feedEnabled, rates }` where each rate carries a pair of
 * currency codes, the exact decimal `rate` **string** (F5 — NOT a float),
 * freshness timestamps, `ageSeconds` (duration, not money), and `stale`.
 * READ-ONLY. The domain-facing IAM OIDC access token is attached by
 * `callLedger`; NEVER `getOperatorToken()`. No path parameters — global
 * list. An empty cache is a normal `200` (`rates: []`) — NOT a 404.
 */
export async function getFxRates(): Promise<FxRatesResponse> {
  return callLedger(
    {
      path: '/api/finance/ledger/fx-rates',
      // No id / code / currency to sanitise — the path is already generic.
      logPath: '/api/finance/ledger/fx-rates',
    },
    (json) => {
      const env = (json ?? {}) as { data?: unknown };
      return FxRatesResponseSchema.parse(env.data);
    },
  );
}
