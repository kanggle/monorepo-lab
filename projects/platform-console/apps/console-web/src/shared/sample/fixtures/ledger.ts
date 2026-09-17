import { SAMPLE_AS_OF, SAMPLE_LABEL_SUFFIX } from '../codes';
import { fixtureNotFound } from '../router';

/**
 * Same shape as `fixtures/index.ts`'s `FixtureHandler` — written out here
 * (rather than imported) so this module has no edge back to `index.ts`, which
 * imports THIS module (`shared/sample/**` must stay a DAG the isolation guard
 * can read — see `iam.ts`/`ecommerce.ts`/`erp.ts`'s identical note).
 */
type LedgerFixtureHandler = (path: string) => unknown;

/**
 * ledger domain fixtures (TASK-PC-FE-286 — `ADR-MONO-074` execution 5/8): the
 * ONE `callFlatEnvelopeGateway` surface `ledger` — the ledger-service's 12
 * GETs (§ 2.4.7.1):
 *   - trial balance (browsable, no input)
 *   - accounting periods — list + detail (close snapshot when CLOSED)
 *   - journal entry — id-driven detail (no list/search GET)
 *   - ledger account — balance + entries, by `ledgerAccountCode` (id-driven)
 *   - reconciliation discrepancies — list (status filter) + detail
 *   - reconciliation statement — id-driven detail
 *   - FX position lots — by (ledgerAccountCode, currency)
 *   - FX rate feed cache (browsable, no input) + per-pair history
 *
 * Hand-authored synthetic data (ADR-MONO-074 A4 — no extraction path from any
 * backend). Parsed by the SAME zod schemas the real screens use
 * (`tests/unit/sample-fixtures-schema-finance-ledger.test.ts`).
 *
 * ── ONE DOUBLE-ENTRY WORLD (AC-3) ──────────────────────────────────────────
 * 🔴 The whole reason this screen exists is that its numbers agree with each
 * other — three journal entries, four ledger accounts, ALL money in KRW base
 * (F5 — minor units strings, scale 0, so one unit IS one 원):
 *
 *   entry-sample-0001  DEBIT  CASH            500,000  / CREDIT REVENUE  500,000
 *   entry-sample-0002  DEBIT  AR               50,000  / CREDIT REVENUE   50,000
 *   entry-sample-0003  DEBIT  FX_USD_HOLDING  $100.00 (USD, exchangeRate 1300,
 *                             baseAmount 130,000 KRW) / CREDIT CASH      130,000
 *
 * Per-account (base-currency) totals:
 *   CASH            debit 500,000  credit 130,000  → balance 370,000 (DEBIT side)
 *   AR              debit  50,000  credit       0  → balance  50,000 (DEBIT side)
 *   REVENUE         debit       0  credit 550,000  → balance 550,000 (CREDIT side)
 *   FX_USD_HOLDING  debit 130,000  credit       0  → balance 130,000 (DEBIT side, base)
 *                   (in ITS OWN currency: debit $100.00 / 10,000 minor units)
 *
 *   Σ base debit = 500,000 + 50,000 + 130,000         = 680,000
 *   Σ base credit =                130,000 + 550,000  = 680,000   ⇒ inBalance = true
 *
 * `tests/unit/sample-fixtures-schema-finance-ledger.test.ts` proves this
 * THROUGH the router for every account: the journal lines returned by
 * `GET .../accounts/{code}/entries` sum (by direction) to EXACTLY the
 * `GET .../accounts/{code}/balance` debit/credit totals AND to the matching
 * row on `GET .../trial-balance` — never re-derived from the seed arrays
 * directly (the same discipline TASK-PC-FE-285's AC-3/AC-4 tests use).
 *
 * FX position lots for (`FX_USD_HOLDING`, `USD`) are the SAME acquisition as
 * entry-sample-0003 (`sourceJournalEntryId: 'entry-sample-0003'`) — not an
 * independent number.
 *
 * A period CLOSE is a point-in-time SNAPSHOT, not a live re-derivation — the
 * one CLOSED period below (`period-sample-0001`, August) captures the world
 * as of Aug 31 (entries 1–2 only, entry-sample-0003 posted in September) —
 * 550,000/550,000, independently in balance. This is NOT the same number as
 * the live trial balance (680,000) and is not supposed to be (different
 * points in time); the OPEN period (`period-sample-0002`, September) carries
 * no snapshot at all (§ 2.4.7.1 — a period only gets one on close).
 *
 * A reconciliation discrepancy references a REAL journal entry
 * (`entry-sample-0003`) and a reconciliation statement's `matches` /
 * `discrepancies` reuse those SAME rows (`matchedCount` / `discrepancyCount`
 * literally equal the arrays' lengths) — the exact "summary tile = the rows
 * it summarises" invariant AC-3 names.
 *
 * ── MONEY REPRESENTATION (AC-4) ────────────────────────────────────────────
 * F5: every amount/rate is a STRING (`shared/lib/money.ts` `MoneySchema` +
 * the `*Minor` regex fields). KRW is scale 0 (one minor unit = one 원); USD is
 * scale 2 (one minor unit = one cent — `"10000"` renders `100.00 USD`).
 * `exchangeRate` "1300" is a decimal-string provenance factor (KRW per USD),
 * rendered verbatim, never used in arithmetic by this fixture or its tests.
 * All magnitudes are obviously a small sample ledger, not a real company's
 * book (AC-4 "합성임이 분명한 규모").
 *
 * ── DATES IN UTC (Edge Case 2) ──────────────────────────────────────────────
 * Every `asOf` / `fetchedAt` below is `<= SAMPLE_AS_OF` ('2026-09-15T11:00:00Z',
 * itself UTC) so the FX feed never reads as a future quote under `TZ=UTC`.
 *
 * R2ⓐ: `ledgerAccountCode` / `resolutionType` / `source` / etc. are all
 * ids/codes/enums a parser or `StatusBadge`-like element reads, not free
 * prose a person types (confirmed by reading every ledger-types schema), so
 * every one of THOSE classifies MACHINE in `label-rule.ts` (see that file's
 * TASK-PC-FE-286 additions). The ONE exception is the reconciliation
 * resolution's `note` — a free-text narrative the RESOLVING OPERATOR types —
 * which is already classified HUMAN_READABLE by the pre-existing base rule
 * in `label-rule.ts` (not a TASK-PC-FE-286 addition): it takes the «(샘플)»
 * suffix below, unlike `reason` (classified MACHINE by precedent, TASK-PC-FE-285
 * D5) or `finance.ts`'s surface (which truly has no human-readable field).
 */

const SAMPLE_ACTOR_ID = 'operator-sample-0001';

function splitPath(path: string): { pathname: string; query: URLSearchParams } {
  const [pathname, qs = ''] = path.split('?');
  return { pathname, query: new URLSearchParams(qs) };
}

function intParam(query: URLSearchParams, key: string, fallback: number): number {
  const raw = query.get(key);
  if (raw === null) return fallback;
  const n = Number(raw);
  return Number.isFinite(n) ? n : fallback;
}

function pageEnvelope<T>(rows: readonly T[], page: number, size: number) {
  const safeSize = Math.max(1, size);
  const start = page * safeSize;
  return {
    data: rows.slice(start, start + safeSize),
    meta: {
      timestamp: SAMPLE_AS_OF,
      page,
      size: safeSize,
      totalElements: rows.length,
    },
  };
}

// ===========================================================================
// the double-entry world — journal lines per account (the single source the
// trial balance / account balance / account entries are all DERIVED from).
// ===========================================================================

interface LineSeed {
  entryId: string;
  ledgerAccountCode: string;
  direction: 'DEBIT' | 'CREDIT';
  /** original transaction-currency amount (minor units, F5 string) */
  money: { amount: string; currency: string };
  exchangeRate: string;
  /** base-currency (KRW) amount (minor units, F5 string) */
  baseAmount: { amount: string; currency: 'KRW' };
  postedAt: string;
}

const LINES: readonly LineSeed[] = [
  {
    entryId: 'entry-sample-0001',
    ledgerAccountCode: 'CASH',
    direction: 'DEBIT',
    money: { amount: '500000', currency: 'KRW' },
    exchangeRate: '1',
    baseAmount: { amount: '500000', currency: 'KRW' },
    postedAt: '2026-08-05T00:00:00Z',
  },
  {
    entryId: 'entry-sample-0001',
    ledgerAccountCode: 'REVENUE',
    direction: 'CREDIT',
    money: { amount: '500000', currency: 'KRW' },
    exchangeRate: '1',
    baseAmount: { amount: '500000', currency: 'KRW' },
    postedAt: '2026-08-05T00:00:00Z',
  },
  {
    entryId: 'entry-sample-0002',
    ledgerAccountCode: 'AR',
    direction: 'DEBIT',
    money: { amount: '50000', currency: 'KRW' },
    exchangeRate: '1',
    baseAmount: { amount: '50000', currency: 'KRW' },
    postedAt: '2026-08-20T00:00:00Z',
  },
  {
    entryId: 'entry-sample-0002',
    ledgerAccountCode: 'REVENUE',
    direction: 'CREDIT',
    money: { amount: '50000', currency: 'KRW' },
    exchangeRate: '1',
    baseAmount: { amount: '50000', currency: 'KRW' },
    postedAt: '2026-08-20T00:00:00Z',
  },
  {
    entryId: 'entry-sample-0003',
    ledgerAccountCode: 'FX_USD_HOLDING',
    direction: 'DEBIT',
    money: { amount: '10000', currency: 'USD' },
    exchangeRate: '1300',
    baseAmount: { amount: '130000', currency: 'KRW' },
    postedAt: '2026-09-05T00:00:00Z',
  },
  {
    entryId: 'entry-sample-0003',
    ledgerAccountCode: 'CASH',
    direction: 'CREDIT',
    money: { amount: '130000', currency: 'KRW' },
    exchangeRate: '1',
    baseAmount: { amount: '130000', currency: 'KRW' },
    postedAt: '2026-09-05T00:00:00Z',
  },
];

const JOURNAL_ENTRIES: Readonly<
  Record<
    string,
    {
      entryId: string;
      postedAt: string;
      source: { sourceType: string; sourceTransactionId: string | null; sourceEventId: string | null };
      reversalOfEntryId: string | null;
      lines: { ledgerAccountCode: string; direction: string; money: { amount: string; currency: string }; exchangeRate: string; baseAmount: { amount: string; currency: string } }[];
      balanced: boolean;
    }
  >
> = {
  'entry-sample-0001': {
    entryId: 'entry-sample-0001',
    postedAt: '2026-08-05T00:00:00Z',
    source: { sourceType: 'TRANSACTION', sourceTransactionId: 'txn-sample-0101', sourceEventId: null },
    reversalOfEntryId: null,
    lines: LINES.filter((l) => l.entryId === 'entry-sample-0001').map((l) => ({
      ledgerAccountCode: l.ledgerAccountCode,
      direction: l.direction,
      money: l.money,
      exchangeRate: l.exchangeRate,
      baseAmount: l.baseAmount,
    })),
    balanced: true,
  },
  'entry-sample-0002': {
    entryId: 'entry-sample-0002',
    postedAt: '2026-08-20T00:00:00Z',
    source: { sourceType: 'TRANSACTION', sourceTransactionId: 'txn-sample-0102', sourceEventId: null },
    reversalOfEntryId: null,
    lines: LINES.filter((l) => l.entryId === 'entry-sample-0002').map((l) => ({
      ledgerAccountCode: l.ledgerAccountCode,
      direction: l.direction,
      money: l.money,
      exchangeRate: l.exchangeRate,
      baseAmount: l.baseAmount,
    })),
    balanced: true,
  },
  'entry-sample-0003': {
    entryId: 'entry-sample-0003',
    postedAt: '2026-09-05T00:00:00Z',
    source: { sourceType: 'MANUAL', sourceTransactionId: null, sourceEventId: null },
    reversalOfEntryId: null,
    lines: LINES.filter((l) => l.entryId === 'entry-sample-0003').map((l) => ({
      ledgerAccountCode: l.ledgerAccountCode,
      direction: l.direction,
      money: l.money,
      exchangeRate: l.exchangeRate,
      baseAmount: l.baseAmount,
    })),
    balanced: true,
  },
};

/** Per-account type/normalSide — the honest chart-of-accounts metadata
 *  `AccountBalance` carries (free strings, tolerant-parser discipline). */
const ACCOUNT_META: Readonly<Record<string, { type: string; normalSide: 'DEBIT' | 'CREDIT'; currency: string }>> = {
  CASH: { type: 'ASSET', normalSide: 'DEBIT', currency: 'KRW' },
  AR: { type: 'ASSET', normalSide: 'DEBIT', currency: 'KRW' },
  REVENUE: { type: 'REVENUE', normalSide: 'CREDIT', currency: 'KRW' },
  FX_USD_HOLDING: { type: 'ASSET', normalSide: 'DEBIT', currency: 'USD' },
};

function sumMinor(values: readonly string[]): string {
  // Pure integer-string addition (no Number/parseFloat/parseInt — F5).
  // Every value here is a small non-negative integer string, so a bigint
  // accumulator is exact without any float involvement.
  return values.reduce((acc, v) => (BigInt(acc) + BigInt(v)).toString(), '0');
}

function accountLines(code: string): readonly LineSeed[] {
  return LINES.filter((l) => l.ledgerAccountCode === code);
}

// ===========================================================================
// trial balance — GET /api/finance/ledger/trial-balance
// ===========================================================================

const TRIAL_BALANCE_PATH = '/api/finance/ledger/trial-balance';

const TRIAL_BALANCE_ACCOUNT_CODES = ['CASH', 'AR', 'REVENUE', 'FX_USD_HOLDING'] as const;

function trialBalanceRow(code: string) {
  const meta = ACCOUNT_META[code];
  const lines = accountLines(code);
  const debitOriginal = sumMinor(lines.filter((l) => l.direction === 'DEBIT').map((l) => l.money.amount));
  const creditOriginal = sumMinor(lines.filter((l) => l.direction === 'CREDIT').map((l) => l.money.amount));
  const debitBase = sumMinor(lines.filter((l) => l.direction === 'DEBIT').map((l) => l.baseAmount.amount));
  const creditBase = sumMinor(lines.filter((l) => l.direction === 'CREDIT').map((l) => l.baseAmount.amount));
  return {
    ledgerAccountCode: code,
    debitTotal: { amount: debitOriginal, currency: meta.currency },
    creditTotal: { amount: creditOriginal, currency: meta.currency },
    baseDebitTotal: { amount: debitBase, currency: 'KRW' },
    baseCreditTotal: { amount: creditBase, currency: 'KRW' },
  };
}

function trialBalanceFixture(path: string): unknown {
  const { pathname } = splitPath(path);
  if (pathname !== TRIAL_BALANCE_PATH) return undefined;
  const accounts = TRIAL_BALANCE_ACCOUNT_CODES.map(trialBalanceRow);
  const grandBaseDebitTotal = sumMinor(accounts.map((a) => a.baseDebitTotal.amount));
  const grandBaseCreditTotal = sumMinor(accounts.map((a) => a.baseCreditTotal.amount));
  return {
    data: {
      accounts,
      // 🔵 the legacy (pre multi-currency) grand-total pair is kept mirroring
      // the base pair — comparing DEBIT/CREDIT totals ACROSS accounts that
      // transact in different original currencies (KRW + USD here) has no
      // principled single-currency reading; the base pair is the one live
      // double-entry invariant that means anything, and `inBalance` below is
      // computed from it.
      grandDebitTotal: { amount: grandBaseDebitTotal, currency: 'KRW' },
      grandCreditTotal: { amount: grandBaseCreditTotal, currency: 'KRW' },
      grandBaseDebitTotal: { amount: grandBaseDebitTotal, currency: 'KRW' },
      grandBaseCreditTotal: { amount: grandBaseCreditTotal, currency: 'KRW' },
      inBalance: grandBaseDebitTotal === grandBaseCreditTotal,
    },
    meta: { timestamp: SAMPLE_AS_OF },
  };
}

// ===========================================================================
// journal entry (id-driven) — GET /api/finance/ledger/entries/{entryId}
// ===========================================================================

const ENTRIES_PATH = '/api/finance/ledger/entries';

function journalEntryFixture(path: string): unknown {
  const { pathname } = splitPath(path);
  const m = pathname.match(new RegExp(`^${ENTRIES_PATH}/([^/]+)$`));
  if (!m) return undefined;
  const id = decodeURIComponent(m[1]);
  const found = JOURNAL_ENTRIES[id];
  if (!found) return fixtureNotFound('JOURNAL_ENTRY_NOT_FOUND', 'journal entry not found');
  return { data: found, meta: { timestamp: SAMPLE_AS_OF } };
}

// ===========================================================================
// ledger account — balance + entries, GET /api/finance/ledger/accounts/{code}/…
// ===========================================================================

const ACCOUNTS_PATH = '/api/finance/ledger/accounts';

function accountBalanceFixture(path: string): unknown {
  const { pathname } = splitPath(path);
  const m = pathname.match(new RegExp(`^${ACCOUNTS_PATH}/([^/]+)/balance$`));
  if (!m) return undefined;
  const code = decodeURIComponent(m[1]);
  const meta = ACCOUNT_META[code];
  if (!meta) return fixtureNotFound('LEDGER_ACCOUNT_NOT_FOUND', 'ledger account not found');
  const lines = accountLines(code);
  const debitTotal = sumMinor(lines.filter((l) => l.direction === 'DEBIT').map((l) => l.money.amount));
  const creditTotal = sumMinor(lines.filter((l) => l.direction === 'CREDIT').map((l) => l.money.amount));
  const balance = (BigInt(debitTotal) - BigInt(creditTotal)).toString();
  const balanceAbs = balance.startsWith('-') ? balance.slice(1) : balance;
  const balanceSide = balance.startsWith('-') ? 'CREDIT' : 'DEBIT';
  return {
    data: {
      ledgerAccountCode: code,
      type: meta.type,
      normalSide: meta.normalSide,
      debitTotal: { amount: debitTotal, currency: meta.currency },
      creditTotal: { amount: creditTotal, currency: meta.currency },
      balance: { amount: balanceAbs, currency: meta.currency },
      balanceSide,
    },
    meta: { timestamp: SAMPLE_AS_OF },
  };
}

function accountEntriesFixture(path: string): unknown {
  const { pathname, query } = splitPath(path);
  const m = pathname.match(new RegExp(`^${ACCOUNTS_PATH}/([^/]+)/entries$`));
  if (!m) return undefined;
  const code = decodeURIComponent(m[1]);
  if (!ACCOUNT_META[code]) {
    return fixtureNotFound('LEDGER_ACCOUNT_NOT_FOUND', 'ledger account not found');
  }
  const page = intParam(query, 'page', 0);
  const size = intParam(query, 'size', 20);
  // most-recent first (ledger-api.md § 2).
  const rows = accountLines(code)
    .slice()
    .sort((a, b) => (a.postedAt < b.postedAt ? 1 : -1))
    .map((l) => ({
      entryId: l.entryId,
      postedAt: l.postedAt,
      direction: l.direction,
      money: l.money,
    }));
  return pageEnvelope(rows, page, size);
}

// ===========================================================================
// accounting periods — GET /api/finance/ledger/periods (+ /{periodId})
// ===========================================================================

const PERIODS_PATH = '/api/finance/ledger/periods';

const CLOSED_SNAPSHOT_ACCOUNTS = [
  { ledgerAccountCode: 'CASH', debitTotal: { amount: '500000', currency: 'KRW' }, creditTotal: { amount: '0', currency: 'KRW' } },
  { ledgerAccountCode: 'AR', debitTotal: { amount: '50000', currency: 'KRW' }, creditTotal: { amount: '0', currency: 'KRW' } },
  { ledgerAccountCode: 'REVENUE', debitTotal: { amount: '0', currency: 'KRW' }, creditTotal: { amount: '550000', currency: 'KRW' } },
] as const;

interface PeriodSummarySeed {
  periodId: string;
  status: string;
  from: string;
  to?: string;
  closedAt: string | null;
  closedBy: string | null;
  entryCount: number;
}

interface PeriodSeed extends PeriodSummarySeed {
  snapshot: {
    accounts: typeof CLOSED_SNAPSHOT_ACCOUNTS;
    grandDebitTotal: { amount: string; currency: string };
    grandCreditTotal: { amount: string; currency: string };
    inBalance: boolean;
  } | null;
}

const PERIODS: Readonly<Record<string, PeriodSeed>> = {
  'period-sample-0001': {
    periodId: 'period-sample-0001',
    status: 'CLOSED',
    from: '2026-08-01',
    to: '2026-08-31',
    closedAt: '2026-09-01T00:00:00Z',
    closedBy: SAMPLE_ACTOR_ID,
    entryCount: 2,
    snapshot: {
      accounts: CLOSED_SNAPSHOT_ACCOUNTS,
      grandDebitTotal: { amount: '550000', currency: 'KRW' },
      grandCreditTotal: { amount: '550000', currency: 'KRW' },
      inBalance: true,
    },
  },
  'period-sample-0002': {
    periodId: 'period-sample-0002',
    status: 'OPEN',
    from: '2026-09-01',
    closedAt: null,
    closedBy: null,
    entryCount: 1,
    snapshot: null,
  },
};

const PERIOD_LIST_ORDER = ['period-sample-0002', 'period-sample-0001'] as const; // most-recent first

/** The list envelope carries NO snapshot (ledger-api.md § 7 — list vs § 8
 *  detail); built explicitly (never a spread-omit) so a new PeriodSeed field
 *  is a deliberate choice, not an accidental leak into the list shape. */
function periodSummary(seed: PeriodSeed): PeriodSummarySeed {
  return {
    periodId: seed.periodId,
    status: seed.status,
    from: seed.from,
    to: seed.to,
    closedAt: seed.closedAt,
    closedBy: seed.closedBy,
    entryCount: seed.entryCount,
  };
}

function periodsFixture(path: string): unknown {
  const { pathname, query } = splitPath(path);

  const detailMatch = pathname.match(new RegExp(`^${PERIODS_PATH}/([^/]+)$`));
  if (detailMatch) {
    const id = decodeURIComponent(detailMatch[1]);
    const found = PERIODS[id];
    if (!found) return fixtureNotFound('ACCOUNTING_PERIOD_NOT_FOUND', 'accounting period not found');
    return { data: found, meta: { timestamp: SAMPLE_AS_OF } };
  }

  if (pathname !== PERIODS_PATH) return undefined;
  const page = intParam(query, 'page', 0);
  const size = intParam(query, 'size', 20);
  const rows = PERIOD_LIST_ORDER.map((id) => periodSummary(PERIODS[id]));
  return pageEnvelope(rows, page, size);
}

// ===========================================================================
// reconciliation discrepancies — GET …/reconciliation/discrepancies (+ /{id})
// ===========================================================================

const DISCREPANCIES_PATH = '/api/finance/ledger/reconciliation/discrepancies';

interface DiscrepancySeed {
  discrepancyId: string;
  type: string;
  externalRef: string | null;
  journalEntryId: string | null;
  expectedMinor: string;
  actualMinor: string;
  currency: string;
  status: string;
  resolution: {
    resolutionType: string;
    note: string;
    resolvedBy: string;
    resolvedAt: string;
  } | null;
}

const DISCREPANCIES: Readonly<Record<string, DiscrepancySeed>> = {
  'discrepancy-sample-0001': {
    discrepancyId: 'discrepancy-sample-0001',
    type: 'AMOUNT_MISMATCH',
    externalRef: 'ext-sample-0001',
    journalEntryId: 'entry-sample-0003',
    // the bank statement shows $101.00 for the FX purchase our ledger booked
    // at $100.00 (entry-sample-0003) — a real, honest mismatch, not hidden.
    expectedMinor: '10000',
    actualMinor: '10100',
    currency: 'USD',
    status: 'OPEN',
    resolution: null,
  },
  'discrepancy-sample-0002': {
    discrepancyId: 'discrepancy-sample-0002',
    type: 'UNMATCHED_EXTERNAL',
    externalRef: 'ext-sample-0002',
    journalEntryId: null,
    expectedMinor: '0',
    actualMinor: '5000',
    currency: 'KRW',
    status: 'RESOLVED',
    resolution: {
      resolutionType: 'WRITTEN_OFF',
      note: `small residual write-off${SAMPLE_LABEL_SUFFIX}`,
      resolvedBy: SAMPLE_ACTOR_ID,
      resolvedAt: '2026-09-10T00:00:00Z',
    },
  },
};

function discrepanciesFixture(path: string): unknown {
  const { pathname, query } = splitPath(path);

  const detailMatch = pathname.match(new RegExp(`^${DISCREPANCIES_PATH}/([^/]+)$`));
  if (detailMatch) {
    const id = decodeURIComponent(detailMatch[1]);
    const found = DISCREPANCIES[id];
    if (!found) {
      return fixtureNotFound('RECONCILIATION_DISCREPANCY_NOT_FOUND', 'discrepancy not found');
    }
    return { data: found, meta: { timestamp: SAMPLE_AS_OF } };
  }

  if (pathname !== DISCREPANCIES_PATH) return undefined;
  const status = query.get('status');
  const page = intParam(query, 'page', 0);
  const size = intParam(query, 'size', 20);
  let rows = Object.values(DISCREPANCIES);
  if (status) rows = rows.filter((d) => d.status === status);
  return pageEnvelope(rows, page, size);
}

// ===========================================================================
// reconciliation statement — GET …/reconciliation/statements/{id}
// ===========================================================================

const STATEMENTS_PATH = '/api/finance/ledger/reconciliation/statements';

const STATEMENTS: Readonly<Record<string, unknown>> = {
  'statement-sample-0001': {
    statementId: 'statement-sample-0001',
    ledgerAccountCode: 'CASH',
    source: 'BANK_FEED',
    statementDate: '2026-09-06',
    matchedCount: 1,
    discrepancyCount: 1,
    matches: [
      {
        statementLineExternalRef: 'ext-sample-0003',
        journalEntryId: 'entry-sample-0001',
        money: { amount: '500000', currency: 'KRW' },
      },
    ],
    // reuses the SAME discrepancy row `GET …/discrepancies/{id}` answers —
    // never a second, independently-typed copy.
    discrepancies: [DISCREPANCIES['discrepancy-sample-0001']],
  },
};

function statementFixture(path: string): unknown {
  const { pathname } = splitPath(path);
  const m = pathname.match(new RegExp(`^${STATEMENTS_PATH}/([^/]+)$`));
  if (!m) return undefined;
  const id = decodeURIComponent(m[1]);
  const found = STATEMENTS[id];
  if (!found) {
    return fixtureNotFound('RECONCILIATION_STATEMENT_NOT_FOUND', 'statement not found');
  }
  return { data: found, meta: { timestamp: SAMPLE_AS_OF } };
}

// ===========================================================================
// FX position lots — GET …/settlements/{code}/{currency}/lots
// ===========================================================================

const SETTLEMENTS_PATH = '/api/finance/ledger/settlements';

/** The ONE populated position: the SAME USD acquisition as entry-sample-0003
 *  (`sourceJournalEntryId` ties back to it — not an independent number). */
const FX_LOTS = [
  {
    lotId: 'lot-sample-0001',
    currency: 'USD',
    acquiredAt: '2026-09-05T00:00:00Z',
    seq: 0,
    originalForeignMinor: '10000',
    remainingForeignMinor: '10000',
    originalBaseMinor: '130000',
    carryingBaseMinor: '130000',
    sourceJournalEntryId: 'entry-sample-0003',
  },
] as const;

function positionLotsFixture(path: string): unknown {
  const { pathname } = splitPath(path);
  const m = pathname.match(new RegExp(`^${SETTLEMENTS_PATH}/([^/]+)/([^/]+)/lots$`));
  if (!m) return undefined;
  const code = decodeURIComponent(m[1]);
  const currency = decodeURIComponent(m[2]);
  const isKnownPosition = code === 'FX_USD_HOLDING' && currency === 'USD';
  const lots = isKnownPosition ? FX_LOTS : [];
  // An unknown/empty (code, currency) is a normal 200 empty-state (ledger-api.md
  // § 12 — never a 404), so every OTHER pair (incl. a code this world has no
  // FX exposure on) answers here rather than falling through to SAMPLE_NOT_READY.
  return {
    data: {
      lots,
      totalRemainingForeignMinor: isKnownPosition ? '10000' : '0',
      totalCarryingBaseMinor: isKnownPosition ? '130000' : '0',
      lotCount: lots.length,
    },
    meta: { timestamp: SAMPLE_AS_OF },
  };
}

// ===========================================================================
// FX rate feed cache — GET /api/finance/ledger/fx-rates
// ===========================================================================

const FX_RATES_PATH = '/api/finance/ledger/fx-rates';

/** Edge Case 2 — every `asOf`/`fetchedAt` is <= SAMPLE_AS_OF
 *  ('2026-09-15T11:00:00Z') so nothing reads as a future quote under TZ=UTC. */
const FX_RATES = [
  {
    baseCurrency: 'KRW',
    foreignCurrency: 'USD',
    rate: '1300.00000000',
    asOf: '2026-09-15T09:00:00Z',
    source: 'PROVIDER_A',
    fetchedAt: '2026-09-15T09:00:05Z',
    ageSeconds: 7200,
    stale: false,
  },
  {
    baseCurrency: 'KRW',
    foreignCurrency: 'JPY',
    rate: '9.10000000',
    asOf: '2026-09-14T09:00:00Z',
    source: 'PROVIDER_A',
    fetchedAt: '2026-09-14T09:00:05Z',
    ageSeconds: 108000,
    stale: true,
  },
] as const;

function fxRatesFixture(path: string): unknown {
  const { pathname } = splitPath(path);
  if (pathname !== FX_RATES_PATH) return undefined;
  return { data: { feedEnabled: true, rates: FX_RATES }, meta: { timestamp: SAMPLE_AS_OF } };
}

// ===========================================================================
// FX rate history — GET /api/finance/ledger/fx-rates/{foreignCurrency}/history
// ===========================================================================

const FX_HISTORY: Readonly<Record<string, readonly { rate: string; asOf: string; fetchedAt: string; source: string }[]>> = {
  USD: [
    { rate: '1300.00000000', asOf: '2026-09-15T09:00:00Z', fetchedAt: '2026-09-15T09:00:05Z', source: 'PROVIDER_A' },
    { rate: '1298.50000000', asOf: '2026-09-14T09:00:00Z', fetchedAt: '2026-09-14T09:00:05Z', source: 'PROVIDER_A' },
  ],
  JPY: [
    { rate: '9.10000000', asOf: '2026-09-14T09:00:00Z', fetchedAt: '2026-09-14T09:00:05Z', source: 'PROVIDER_A' },
  ],
};

function fxRateHistoryFixture(path: string): unknown {
  const { pathname } = splitPath(path);
  const m = pathname.match(/^\/api\/finance\/ledger\/fx-rates\/([^/]+)\/history$/);
  if (!m) return undefined;
  const foreign = decodeURIComponent(m[1]);
  const quotes = FX_HISTORY[foreign] ?? []; // unknown/never-polled → 200 empty (not 404).
  return { data: { base: 'KRW', foreign, quotes }, meta: { timestamp: SAMPLE_AS_OF } };
}

// ===========================================================================
// ledger surface — dispatch (ALL under the single `flat:ledger` fixture key —
// see `ledger-client.ts`'s `logPrefix: 'ledger'`).
// ===========================================================================

function ledgerFixture(path: string): unknown {
  return (
    trialBalanceFixture(path) ??
    journalEntryFixture(path) ??
    accountBalanceFixture(path) ??
    accountEntriesFixture(path) ??
    periodsFixture(path) ??
    discrepanciesFixture(path) ??
    statementFixture(path) ??
    positionLotsFixture(path) ??
    fxRateHistoryFixture(path) ??
    fxRatesFixture(path)
  );
}

export const LEDGER_FIXTURE_HANDLERS: Readonly<Record<string, LedgerFixtureHandler>> = {
  'flat:ledger': ledgerFixture,
};

/**
 * The AGGREGATE of every seed on this surface, for the R2ⓐ label guard (same
 * reasoning as `iam.ts`/`ecommerce.ts`/`erp.ts`).
 */
export const LEDGER_FIXTURE_DOCUMENTS: Readonly<Record<string, unknown>> = {
  'flat:ledger': {
    trialBalanceAccounts: TRIAL_BALANCE_ACCOUNT_CODES.map(trialBalanceRow),
    journalEntries: Object.values(JOURNAL_ENTRIES),
    periods: Object.values(PERIODS),
    discrepancies: Object.values(DISCREPANCIES),
    statements: Object.values(STATEMENTS),
    fxLots: FX_LOTS,
    fxRates: FX_RATES,
    fxHistory: FX_HISTORY,
  },
};
