import { SAMPLE_AS_OF } from '../codes';
import { fixtureNotFound } from '../router';

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
 * Three transactions, honestly disjoint outcomes: a COMPLETED credit (landed
 * in `available`), an ACTIVE hold (sitting in `held` — never in `available`
 * until captured/released), and a FAILED transfer (never affected the balance
 * at all — a failed transaction is surfaced HONESTLY, not hidden, per
 * `txnStatusTone`'s FAILED/REVERSED danger tone). This is what makes
 * `ledger = available + held` true without needing the failed row.
 */
export const SAMPLE_FINANCE_TRANSACTIONS = [
  {
    transactionId: 'txn-sample-0001',
    type: 'CREDIT',
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
// finance surface — dispatch
// ===========================================================================

function financeFixture(path: string): unknown {
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
  },
};
