import { SAMPLE_AS_OF } from '../codes';
import { fixtureNotFound } from '../router';
import {
  SAMPLE_METRICS_WINDOWS,
  instantInside,
  kstDate,
  samplePeriod,
  type SamplePeriod,
  type SampleWindow,
} from './metrics-windows';

/**
 * Same shape as `fixtures/index.ts`'s `FixtureHandler` — written out here
 * (rather than imported) so this module has no edge back to `index.ts`, which
 * imports THIS module (`shared/sample/**` must stay a DAG the isolation guard
 * can read — see `iam.ts`/`ecommerce.ts`/`erp.ts`'s identical note).
 */
type FinanceFixtureHandler = (path: string) => unknown;

/**
 * finance domain fixtures (TASK-PC-FE-286 — `ADR-MONO-074` execution 5/8):
 * the ONE `callFlatEnvelopeGateway` surface `finance` — the account-service's
 * 3 GETs (§ 2.4.7):
 *   - `GET /api/finance/accounts/{id}`              — account detail
 *   - `GET /api/finance/accounts/{id}/balances`     — per-currency balances
 *   - `GET /api/finance/accounts/{id}/transactions` — paginated, filterable
 *
 * All GET, all server-side `getDomainFacingToken()`. There is NO list/search
 * GET (`shared/api/finance-accounts-read.ts`'s own docstring: "finance v1 has
 * NO account list/search GET — this client exposes none (account-id-driven)")
 * — TASK-PC-FE-160 froze this as a permanent honesty constraint (no synthetic
 * ₩ aggregation over an account list that cannot exist). So this fixture
 * world is deliberately ONE account, not a browsable roster.
 *
 * Hand-authored synthetic data (ADR-MONO-074 A4 — no extraction path from any
 * backend). Parsed by the SAME zod schemas the real screens use
 * (`tests/unit/sample-fixtures-schema-finance-ledger.test.ts`).
 *
 * ── MONEY REPRESENTATION (AC-4) ──────────────────────────────────────────
 * F5: every amount is a STRING of integer MINOR units (`shared/lib/money.ts`
 * `MoneySchema` / `DEFAULT_CURRENCY_SCALES`). This fixture is KRW-only
 * (scale 0) — one minor unit IS one 원, so `"100000"` renders as `100,000 원`
 * via `formatMoney`, never `1,000.00`. The magnitudes below (tens/hundreds of
 * thousands of 원) are obviously a small sample balance, not a real
 * institution's book (AC-4 "합성임이 분명한 규모").
 *
 * R2ⓐ: there is NO human-readable display field anywhere on this surface —
 * `AccountSchema` / `BalanceSchema` / `TransactionSchema` carry only
 * ids/codes/enums/dates/money (confirmed by reading the schemas; unlike IAM's
 * `email`, finance has no field a person types or reads as a label). So no
 * string here takes the «(샘플)» suffix — every key below classifies MACHINE
 * in `label-rule.ts` (see that file's TASK-PC-FE-286 additions).
 *
 * AC-7 (person-identifying values obviously synthetic): there is no person
 * name / email on this surface at all — nothing to launder.
 */

/**
 * The sample visitor's default finance account id — Edge Case 1
 * (`getFinanceDefaultAccountId()`). MUST stay byte-identical to
 * `fixtures/registry.ts`'s `finance.operatorContext.defaultAccountId` (the
 * registry is what the real `/finance` overview + operator-overview card
 * actually resolve the id FROM) — cross-checked by
 * `tests/unit/sample-fixtures-schema-finance-ledger.test.ts`.
 */
export const SAMPLE_FINANCE_DEFAULT_ACCOUNT_ID = 'sample-account-0001';

const ACCOUNTS_PATH = '/api/finance/accounts';

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

// ===========================================================================
// the one account — GET /api/finance/accounts/{id}
// ===========================================================================

/**
 * `ledger = available + held` — the one arithmetic invariant a balance/summary
 * tile on this surface can be checked against (AC-3's "summary tile = the
 * rows it summarises", applied to finance's own concept of "balance").
 * `ledger` "100000" (원 1,000 = KRW minor units, scale 0) = `available`
 * "80000" + `held` "20000".
 */
export const SAMPLE_FINANCE_BALANCES = [
  { currency: 'KRW', ledger: '100000', available: '80000', held: '20000' },
] as const;

export const SAMPLE_FINANCE_ACCOUNT = {
  accountId: SAMPLE_FINANCE_DEFAULT_ACCOUNT_ID,
  status: 'ACTIVE',
  currency: 'KRW',
  kycLevel: 'FULL',
  // The account detail's embedded `balances` is the SAME array `getBalances`
  // returns — two views of one number, never allowed to disagree (the
  // TASK-PC-FE-284 settlement defect class, applied here).
  balances: SAMPLE_FINANCE_BALANCES,
  createdAt: '2026-01-15T00:00:00Z',
  updatedAt: '2026-09-10T00:00:00Z',
} as const;

function accountDetailFixture(path: string): unknown {
  const { pathname } = splitPath(path);
  const m = pathname.match(new RegExp(`^${ACCOUNTS_PATH}/([^/]+)$`));
  if (!m) return undefined;
  const id = decodeURIComponent(m[1]);
  if (id !== SAMPLE_FINANCE_DEFAULT_ACCOUNT_ID) {
    return fixtureNotFound('ACCOUNT_NOT_FOUND', 'account not found');
  }
  return { data: SAMPLE_FINANCE_ACCOUNT, meta: { timestamp: SAMPLE_AS_OF } };
}

// ===========================================================================
// balances — GET /api/finance/accounts/{id}/balances
// ===========================================================================

function balancesFixture(path: string): unknown {
  const { pathname } = splitPath(path);
  const m = pathname.match(new RegExp(`^${ACCOUNTS_PATH}/([^/]+)/balances$`));
  if (!m) return undefined;
  const id = decodeURIComponent(m[1]);
  if (id !== SAMPLE_FINANCE_DEFAULT_ACCOUNT_ID) {
    return fixtureNotFound('ACCOUNT_NOT_FOUND', 'account not found');
  }
  return { data: SAMPLE_FINANCE_BALANCES, meta: { timestamp: SAMPLE_AS_OF } };
}

// ===========================================================================
// transactions — GET /api/finance/accounts/{id}/transactions
// ===========================================================================

/**
 * Three transactions, honestly disjoint outcomes: a COMPLETED top-up (landed
 * in `available`), an ACTIVE hold (sitting in `held` — never in `available`
 * until captured/released), and a FAILED transfer (never affected the balance
 * at all — a failed transaction is surfaced HONESTLY, not hidden, per
 * `txnStatusTone`'s FAILED/REVERSED danger tone). This is what makes
 * `ledger = available + held` true without needing the failed row.
 *
 * TASK-MONO-812 — the first row's type was `CREDIT`, a value the producer
 * never emits (account-service `TransactionType` = TOPUP · WITHDRAW · TRANSFER
 * · HOLD · CAPTURE · RELEASE · REVERSAL; the v1 credit IS a `TOPUP` —
 * account-api.md § POST /{id}/topups). It is `TOPUP` now so the «실적»
 * strip, which is computed from these rows by the producer's own rule, counts
 * it as the 충전 it is instead of silently dropping it.
 */
export const SAMPLE_FINANCE_TRANSACTIONS = [
  {
    transactionId: 'txn-sample-0001',
    type: 'TOPUP',
    status: 'COMPLETED',
    money: { amount: '80000', currency: 'KRW' },
    counterpartyAccountId: null,
    reversalOfTransactionId: null,
    createdAt: '2026-09-01T00:00:00Z',
    settledAt: '2026-09-01T00:05:00Z',
  },
  {
    transactionId: 'txn-sample-0002',
    type: 'HOLD',
    status: 'ACTIVE',
    money: { amount: '20000', currency: 'KRW' },
    counterpartyAccountId: null,
    reversalOfTransactionId: null,
    createdAt: '2026-09-08T00:00:00Z',
    settledAt: null,
  },
  {
    transactionId: 'txn-sample-0003',
    type: 'TRANSFER',
    status: 'FAILED',
    money: { amount: '15000', currency: 'KRW' },
    counterpartyAccountId: 'sample-account-0002',
    reversalOfTransactionId: null,
    createdAt: '2026-09-09T00:00:00Z',
    settledAt: null,
  },
  // TASK-PC-FE-374 — six more COMPLETED rows spread over Sept 3..15 (KST) so the
  // 충전/이체 bars read across the month and the week window (09-14..15) is
  // non-empty. Added top-ups (50000 + 30000 + 20000) and added transfers
  // (40000 + 25000 + 35000) net to zero, so the typed balance above is left
  // untouched. Kinds the strip counts only: TOPUP / TRANSFER.
  {
    transactionId: 'txn-sample-0004',
    type: 'TOPUP',
    status: 'COMPLETED',
    money: { amount: '50000', currency: 'KRW' },
    counterpartyAccountId: null,
    reversalOfTransactionId: null,
    createdAt: '2026-09-03T01:00:00Z',
    settledAt: '2026-09-03T01:05:00Z', // KST 09-03
  },
  {
    transactionId: 'txn-sample-0005',
    type: 'TRANSFER',
    status: 'COMPLETED',
    money: { amount: '40000', currency: 'KRW' },
    counterpartyAccountId: 'sample-account-0002',
    reversalOfTransactionId: null,
    createdAt: '2026-09-06T03:00:00Z',
    settledAt: '2026-09-06T03:02:00Z', // KST 09-06
  },
  {
    transactionId: 'txn-sample-0006',
    type: 'TOPUP',
    status: 'COMPLETED',
    money: { amount: '30000', currency: 'KRW' },
    counterpartyAccountId: null,
    reversalOfTransactionId: null,
    createdAt: '2026-09-07T05:00:00Z',
    settledAt: '2026-09-07T05:03:00Z', // KST 09-07
  },
  {
    transactionId: 'txn-sample-0007',
    type: 'TRANSFER',
    status: 'COMPLETED',
    money: { amount: '25000', currency: 'KRW' },
    counterpartyAccountId: 'sample-account-0002',
    reversalOfTransactionId: null,
    createdAt: '2026-09-10T02:00:00Z',
    settledAt: '2026-09-10T02:01:00Z', // KST 09-10
  },
  {
    transactionId: 'txn-sample-0008',
    type: 'TRANSFER',
    status: 'COMPLETED',
    money: { amount: '35000', currency: 'KRW' },
    counterpartyAccountId: 'sample-account-0002',
    reversalOfTransactionId: null,
    createdAt: '2026-09-14T04:00:00Z',
    settledAt: '2026-09-14T04:02:00Z', // KST 09-14 (week window)
  },
  {
    transactionId: 'txn-sample-0009',
    type: 'TOPUP',
    status: 'COMPLETED',
    money: { amount: '20000', currency: 'KRW' },
    counterpartyAccountId: null,
    reversalOfTransactionId: null,
    createdAt: '2026-09-15T01:00:00Z',
    settledAt: '2026-09-15T01:04:00Z', // KST 09-15 (week window)
  },
  // TASK-PC-FE-378 — the 개요 «실적» chart draws `topupCount` ONLY, so TOPUP days
  // (not the TOPUP-or-TRANSFER union) must reach 6+ in the month window. Two more
  // TOPUP days (09-05, 09-12 KST). Added top-ups (10000 + 15000) and the added
  // transfer (25000) net to zero, so the typed balance above is left untouched.
  {
    transactionId: 'txn-sample-0010',
    type: 'TOPUP',
    status: 'COMPLETED',
    money: { amount: '10000', currency: 'KRW' },
    counterpartyAccountId: null,
    reversalOfTransactionId: null,
    createdAt: '2026-09-05T02:00:00Z',
    settledAt: '2026-09-05T02:03:00Z', // KST 09-05
  },
  {
    transactionId: 'txn-sample-0011',
    type: 'TOPUP',
    status: 'COMPLETED',
    money: { amount: '15000', currency: 'KRW' },
    counterpartyAccountId: null,
    reversalOfTransactionId: null,
    createdAt: '2026-09-12T03:00:00Z',
    settledAt: '2026-09-12T03:04:00Z', // KST 09-12
  },
  {
    transactionId: 'txn-sample-0012',
    type: 'TRANSFER',
    status: 'COMPLETED',
    money: { amount: '25000', currency: 'KRW' },
    counterpartyAccountId: 'sample-account-0002',
    reversalOfTransactionId: null,
    createdAt: '2026-09-12T05:00:00Z',
    settledAt: '2026-09-12T05:02:00Z', // KST 09-12
  },
] as const;

function transactionsFixture(path: string): unknown {
  const { pathname, query } = splitPath(path);
  const m = pathname.match(new RegExp(`^${ACCOUNTS_PATH}/([^/]+)/transactions$`));
  if (!m) return undefined;
  const id = decodeURIComponent(m[1]);
  if (id !== SAMPLE_FINANCE_DEFAULT_ACCOUNT_ID) {
    return fixtureNotFound('ACCOUNT_NOT_FOUND', 'account not found');
  }
  const type = query.get('type');
  const status = query.get('status');
  const page = intParam(query, 'page', 0);
  const size = intParam(query, 'size', 20);
  let rows: (typeof SAMPLE_FINANCE_TRANSACTIONS)[number][] = [...SAMPLE_FINANCE_TRANSACTIONS];
  if (type) rows = rows.filter((t) => t.type === type);
  if (status) rows = rows.filter((t) => t.status === status);
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
// metrics — GET /api/finance/accounts/metrics?period= (the 개요 «실적» strip,
// ADR-MONO-084 P7, TASK-MONO-812)
// ===========================================================================
//
// Derived from the SAME transaction rows the 계좌 screen lists — never a
// separate table of invented totals — by the producer's own rule
// (account-api.md § GET /api/finance/accounts/metrics): a row counts when its
// type is TOPUP / TRANSFER / CAPTURE and its status is SETTLED / COMPLETED,
// attributed to `settledAt`. So in this one-account sample world the month
// shows the one 80,000 원 top-up (09-01), while the ACTIVE hold and the FAILED
// transfer honestly move nothing — the strip's control group, visible.
// The sample world is KRW only, so the money keys are the `:KRW` ones (the
// producer's empty-world default is KRW too).

const METRICS_PATH = `${ACCOUNTS_PATH}/metrics`;

const FINANCE_COUNTED_TYPES = ['TOPUP', 'TRANSFER', 'CAPTURE'] as const;
type FinanceCountedType = (typeof FINANCE_COUNTED_TYPES)[number];
const FINANCE_SETTLED_STATUSES: readonly string[] = ['SETTLED', 'COMPLETED'];
const FINANCE_METRIC_PREFIX: Readonly<Record<FinanceCountedType, string>> = {
  TOPUP: 'topup',
  TRANSFER: 'transfer',
  CAPTURE: 'capture',
};
const FINANCE_SAMPLE_CURRENCY = 'KRW';

function countedRows(type: FinanceCountedType, win: SampleWindow, date?: string) {
  return SAMPLE_FINANCE_TRANSACTIONS.filter(
    (t) =>
      t.type === type &&
      FINANCE_SETTLED_STATUSES.includes(t.status) &&
      t.settledAt !== null &&
      instantInside(t.settledAt, win) &&
      (date === undefined || kstDate(t.settledAt) === date),
  );
}

/** Sum of integer-string minor units — BigInt, never a JS number (F5). */
function sumAmounts(rows: ReadonlyArray<{ money: { amount: string } }>): string {
  return String(rows.reduce((a, t) => a + BigInt(t.money.amount), BigInt(0)));
}

function computeFinanceMetrics(period: SamplePeriod) {
  const w = SAMPLE_METRICS_WINDOWS[period];
  const metrics = FINANCE_COUNTED_TYPES.flatMap((type) => {
    const prefix = FINANCE_METRIC_PREFIX[type];
    const now = countedRows(type, w.current);
    const before = countedRows(type, w.previous);
    return [
      {
        key: `${prefix}Count`,
        unit: 'count',
        currency: null,
        current: String(now.length),
        previous: String(before.length),
      },
      {
        key: `${prefix}Amount:${FINANCE_SAMPLE_CURRENCY}`,
        unit: 'money',
        currency: FINANCE_SAMPLE_CURRENCY,
        current: sumAmounts(now),
        previous: sumAmounts(before),
      },
    ];
  });
  return {
    period,
    timezone: 'Asia/Seoul',
    current: w.current,
    previous: w.previous,
    metrics,
    // Every date of the current window, zero-filled, every key.
    series: w.dates.map((date) => ({
      date,
      values: Object.fromEntries(
        FINANCE_COUNTED_TYPES.flatMap((type) => {
          const prefix = FINANCE_METRIC_PREFIX[type];
          const day = countedRows(type, w.current, date);
          return [
            [`${prefix}Count`, String(day.length)],
            [`${prefix}Amount:${FINANCE_SAMPLE_CURRENCY}`, sumAmounts(day)],
          ];
        }),
      ),
    })),
  };
}

function metricsFixture(path: string): unknown {
  const { pathname, query } = splitPath(path);
  if (pathname !== METRICS_PATH) return undefined;
  const p = samplePeriod(query);
  // No / unknown period: the producer answers 400; the console always sends
  // one, so the sample leaves it unanswered (-> the router's 503 «not ready»).
  // Never fall through to the account-detail matcher, which would read
  // `metrics` as an account id and answer 404.
  return p ? computeFinanceMetrics(p) : null;
}

// ===========================================================================
// finance surface — dispatch
// ===========================================================================

function financeFixture(path: string): unknown {
  // `metrics` first: `/accounts/metrics` would otherwise match the
  // `/accounts/{id}` detail pattern (the producer's literal route wins the
  // same way — account-api.md § GET /api/finance/accounts/metrics).
  const metrics = metricsFixture(path);
  if (metrics !== undefined) return metrics ?? undefined;
  return (
    transactionsFixture(path) ?? balancesFixture(path) ?? accountDetailFixture(path)
  );
}

export const FINANCE_FIXTURE_HANDLERS: Readonly<Record<string, FinanceFixtureHandler>> = {
  'flat:finance': financeFixture,
};

/**
 * The AGGREGATE of every seed array on this surface, for the R2ⓐ label guard
 * (same reasoning as `iam.ts`/`ecommerce.ts`/`erp.ts`).
 */
export const FINANCE_FIXTURE_DOCUMENTS: Readonly<Record<string, unknown>> = {
  'flat:finance': {
    account: SAMPLE_FINANCE_ACCOUNT,
    balances: SAMPLE_FINANCE_BALANCES,
    transactions: SAMPLE_FINANCE_TRANSACTIONS,
    metrics: { week: computeFinanceMetrics('week'), month: computeFinanceMetrics('month') },
  },
};
