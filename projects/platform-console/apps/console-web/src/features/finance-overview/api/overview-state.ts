import { redirect } from 'next/navigation';
import { ApiError, FinanceUnavailableError, LedgerUnavailableError } from '@/shared/api/errors';
import {
  getTrialBalance,
  listPeriods,
  listDiscrepancies,
  getFxRates,
} from '@/shared/api/ledger-overview-read';
import { getAccount, getBalances } from '@/shared/api/finance-accounts-read';
import type {
  Account,
  BalancesResponse,
} from '@/shared/api/finance-accounts-types';
import { getFinanceDefaultAccountId } from '@/shared/lib/finance-default-account-id';
import type { MetricsPeriod } from '@/shared/lib/kst-period';
import {
  performanceMetricsCell,
  type PerformanceCell,
} from '@/shared/api/performance-metrics';
import { getFinanceMetrics } from './finance-metrics-api';

/**
 * Server-side finance domain **overview snapshot** for the `/finance`
 * landing (TASK-PC-FE-229 — repoints the domain root at a live overview,
 * orthodox parity with every other domain's 개요-at-root convention; the
 * former `/finance` account surface relocated to `/finance/accounts`).
 * Supersedes the PARKED TASK-PC-FE-160 "finance landing overview" —
 * PC-FE-160 declined a landing because finance v1 has no account
 * list/search GET (no count fan-out possible, no synthetic ₩ aggregation
 * allowed); THIS overview satisfies that honesty constraint by aggregating
 * ONLY (a) the ledger-service's EXISTING browsable index reads (§ 2.4.7.1 —
 * trial balance / periods / OPEN discrepancy queue / FX rates, already
 * consumed by `ledger-state.ts`) and (b) the operator's OWN default finance
 * account — a SINGLE-account snapshot, never a list/search/aggregate call.
 *
 * ── ARCHITECTURE (console-web direct fan-out; NO new producer) ──
 * Mirrors `features/iam-overview/api/overview-state.ts` (TASK-PC-FE-180):
 * this is a domain-internal composition over the EXISTING finance
 * `account-service` (§ 2.4.7) + `ledger-service` (§ 2.4.7.1) server
 * clients — REUSED, never re-implemented. No cross-domain composition leg, no new
 * endpoint, no new producer call.
 *
 * TASK-PC-FE-259 — those reads are consumed by this feature AND by
 * `features/finance-ops` / `features/ledger-ops`, so they live in
 * `shared/api/{finance-accounts-read,ledger-overview-read}.ts`. Earlier
 * revisions of this file imported them straight out of
 * `@/features/{finance-ops,ledger-ops}/api/*` and justified it as an
 * "overview aggregator exception … already established by `iam-overview`".
 * **There is no such exception.** `architecture.md` § Forbidden
 * Dependencies is unconditional — `features/A → features/B` 상호 참조
 * 금지, 공유 가치는 `shared/` 로 승격 — and `iam-overview` was simply the
 * same drift, fixed in the same task. Do not reintroduce a cross-feature
 * import here; promote the value instead.
 *
 * ── INDEPENDENT DEGRADE (the decisive rule) ──
 * The ledger leg and the account leg run in their OWN try/catch (never a
 * single shared try/catch around both — a 503 in one MUST NOT blank the
 * other, § Edge Cases / Failure Scenarios). `ledgerDegraded` /
 * `accountDegraded` are separate flags; a `null` `ledger` with `accountSnapshot`
 * populated (or vice versa) is a normal, expected shape.
 *
 * ── SHARED-CREDENTIAL 401/403 ──
 * Both legs are authenticated with the SAME domain-facing IAM OIDC token
 * against the SAME `finance` tenant scope (§ 2.4.7 / § 2.4.7.1 — the
 * finance tenant gate is shared by both producer services). A `401` in
 * EITHER leg therefore means the whole session's credential expired →
 * ONE whole-session `redirect('/login')` (never a per-leg re-login loop,
 * consistent with `ledger-state.ts` / `finance-state.ts`). A `403` in
 * EITHER leg means the operator's token is not finance-scoped at all
 * (the same credential drives both legs) → the top-level `forbidden`
 * flag, mirroring the `/finance` + `/ledger` page-level waterfall
 * (registryDegraded → notEligible → forbidden → degraded → happy).
 *
 * ── HONEST CONSTRAINT (asserted by test) ──
 * The account leg NEVER calls a list/search endpoint — only
 * `getFinanceDefaultAccountId()` (a registry read, not an account-service
 * call) followed by AT MOST one `getAccount(id)` + `getBalances(id)` pair
 * for the operator's OWN default account.
 *
 * ── «실적» strip (ADR-MONO-084 P7, TASK-MONO-812) ──
 * A third leg, `performance` (`GET /api/finance/accounts/metrics?period=`):
 * 충전 · 이체 · 캡처 건수 and their amounts, one card per currency. This is
 * NOT a list/search call and does not soften the honest constraint above: the
 * producer aggregates the tenant's settled transactions itself (ADR-MONO-084
 * D4) and returns only tenant-wide totals — no account id reaches the console,
 * and the console never sums anything. Unlike the two legs above it does NOT
 * follow the shared-credential `forbidden` rule: a `403` on it makes ONLY the
 * strip «권한 없음», and any other failure (`503`, timeout, an unparseable
 * body) makes it «불러올 수 없음» — never zeros — while the ledger tiles and
 * the account snapshot render as before. A `401` re-login is the same as every
 * leg. Not eligible ⇒ not called.
 */

export interface LedgerOverviewSummary {
  /** Trial balance double-entry invariant — surfaced honestly. */
  inBalance: boolean;
  /** Count of `status === 'OPEN'` periods within the fetched (bounded)
   *  page — NOT a producer-side filtered total (the periods list has no
   *  status filter); `periodsSampleSize` records the bound so the UI can
   *  caption it honestly (no fabricated exhaustive count). */
  openPeriodsCount: number;
  /** The size of the periods page the count above was derived from. */
  periodsSampleSize: number;
  /** Producer-side filtered total of OPEN discrepancies
   *  (`listDiscrepancies({status:'OPEN'}).meta.totalElements`) — an
   *  accurate total, unlike `openPeriodsCount`. */
  openDiscrepanciesCount: number;
  /** Whether the FX feed is enabled at all. */
  fxFeedEnabled: boolean;
  /** Count of cached FX rates. */
  fxRatesCount: number;
  /** Count of cached FX rates flagged `stale` by the producer. */
  fxStaleCount: number;
  /** The most recent `asOf` among the cached rates, or `null` when the
   *  cache is empty. */
  fxLatestAsOf: string | null;
}

export interface AccountSnapshot {
  account: Account;
  balances: BalancesResponse;
}

export interface FinanceOverviewState {
  /** True when the operator is not finance-eligible (no finance
   *  product/tenant in their registry) — actionable block, no finance or
   *  ledger call fabricated. */
  notEligible: boolean;
  /** True on a 403 in EITHER leg (token not finance-scoped) — the whole
   *  overview blocks (the shared-credential rule above). */
  forbidden: boolean;
  /** The ledger aggregate tile data — `null` when notEligible/forbidden
   *  OR when the ledger leg degraded. */
  ledger: LedgerOverviewSummary | null;
  /** True on 503 / timeout / network in the LEDGER leg only — the account
   *  leg is unaffected (independent degrade). */
  ledgerDegraded: boolean;
  /** True when the operator has no default finance account configured in
   *  the registry (`getFinanceDefaultAccountId()` returned `null`) — a
   *  normal "not set up yet" state, NOT a degrade. */
  defaultAccountMissing: boolean;
  /** The default-account snapshot — `null` when notEligible/forbidden OR
   *  `defaultAccountMissing` OR `accountDegraded` OR `accountNotFound`. */
  accountSnapshot: AccountSnapshot | null;
  /** True on 503 / timeout / network in the ACCOUNT leg only — the
   *  ledger leg is unaffected (independent degrade). */
  accountDegraded: boolean;
  /** True on a 404 `ACCOUNT_NOT_FOUND` for the registry-configured
   *  default account id (stale/deleted account — Edge Cases). */
  accountNotFound: boolean;
  /** The period the «실적» strip shows (`?period=`, default `month`). */
  period: MetricsPeriod;
  /** The «실적» strip (TASK-MONO-812) — its own cell, its own failure. */
  performance: PerformanceCell;
}

/** ADR-MONO-084 OD-4: week / month; the overview opens on the month. */
export const DEFAULT_OVERVIEW_PERIOD: MetricsPeriod = 'month';

const EMPTY: FinanceOverviewState = {
  notEligible: false,
  forbidden: false,
  ledger: null,
  ledgerDegraded: false,
  defaultAccountMissing: false,
  accountSnapshot: null,
  accountDegraded: false,
  accountNotFound: false,
  period: DEFAULT_OVERVIEW_PERIOD,
  performance: { status: 'degraded', value: null },
};

/** Bounded page size for the periods browsable read (mirrors
 *  `ledger-state.ts`'s existing default — no new bound introduced). */
const PERIODS_PAGE_SIZE = 20;
const DISCREPANCIES_PAGE_SIZE = 20;

type LedgerLegOutcome =
  | { kind: 'ok'; summary: LedgerOverviewSummary }
  | { kind: 'unauthorized' }
  | { kind: 'forbidden' }
  | { kind: 'degraded' };

async function fetchLedgerSummary(): Promise<LedgerLegOutcome> {
  try {
    const [trialBalance, periods, discrepancies, fx] = await Promise.all([
      getTrialBalance(),
      listPeriods({ page: 0, size: PERIODS_PAGE_SIZE }),
      listDiscrepancies({ status: 'OPEN', page: 0, size: DISCREPANCIES_PAGE_SIZE }),
      getFxRates(),
    ]);

    const openPeriodsCount = periods.data.filter(
      (p) => p.status === 'OPEN',
    ).length;

    const staleRates = fx.rates.filter((r) => r.stale);
    // `asOf` is a timestamp string (NOT money — F5 is amount/rate-only),
    // so Date comparison here is fine.
    const fxLatestAsOf = fx.rates.reduce<string | null>((latest, r) => {
      if (!latest) return r.asOf;
      return new Date(r.asOf).getTime() > new Date(latest).getTime()
        ? r.asOf
        : latest;
    }, null);

    return {
      kind: 'ok',
      summary: {
        inBalance: trialBalance.inBalance,
        openPeriodsCount,
        periodsSampleSize: periods.data.length,
        openDiscrepanciesCount:
          discrepancies.meta.totalElements ?? discrepancies.data.length,
        fxFeedEnabled: fx.feedEnabled,
        fxRatesCount: fx.rates.length,
        fxStaleCount: staleRates.length,
        fxLatestAsOf,
      },
    };
  } catch (err) {
    if (err instanceof ApiError && err.status === 401) return { kind: 'unauthorized' };
    if (err instanceof ApiError && err.status === 403) return { kind: 'forbidden' };
    if (err instanceof LedgerUnavailableError) return { kind: 'degraded' };
    // Any other producer error (incl. an unexpected 429 — the ledger has
    // no documented rate-limit) → degrade rather than crash.
    return { kind: 'degraded' };
  }
}

type AccountLegOutcome =
  | { kind: 'ok'; snapshot: AccountSnapshot }
  | { kind: 'missing' }
  | { kind: 'notFound' }
  | { kind: 'unauthorized' }
  | { kind: 'forbidden' }
  | { kind: 'degraded' };

async function fetchAccountSnapshot(): Promise<AccountLegOutcome> {
  // Registry read — NOT an account-service call. Unreachable registry
  // (401/503/timeout) degrades to `null` inside `getFinanceDefaultAccountId`
  // itself (never throws) — surfaced here as the honest "not set up" state
  // rather than a fabricated degrade (the registry's own failure already
  // has an independent page-level signal via the eligibility pre-flight).
  const defaultAccountId = await getFinanceDefaultAccountId();
  if (!defaultAccountId) {
    return { kind: 'missing' };
  }

  try {
    // AT MOST one account + one balances call for the operator's OWN
    // default account — NEVER a list/search endpoint (the honest finance
    // constraint, PC-FE-160 non-negotiable; asserted by test).
    const [account, balances] = await Promise.all([
      getAccount(defaultAccountId),
      getBalances(defaultAccountId),
    ]);
    return { kind: 'ok', snapshot: { account, balances } };
  } catch (err) {
    if (err instanceof ApiError && err.status === 401) return { kind: 'unauthorized' };
    if (err instanceof ApiError && err.status === 403) return { kind: 'forbidden' };
    if (
      err instanceof ApiError &&
      err.status === 404 &&
      (err.code === 'ACCOUNT_NOT_FOUND' || err.code.startsWith('HTTP_404'))
    ) {
      // The registry-configured default account id no longer resolves
      // (stale/deleted) — inline actionable, NOT a crash (Edge Cases).
      return { kind: 'notFound' };
    }
    if (err instanceof FinanceUnavailableError) return { kind: 'degraded' };
    // Any other producer error (incl. an unexpected 429 — finance has no
    // documented rate-limit) → degrade rather than crash.
    return { kind: 'degraded' };
  }
}

/**
 * @param eligible  whether the operator is finance-eligible, resolved by
 *   the page from the data-driven registry (`productKey==='finance'`,
 *   mirrors `/finance/accounts` + `/ledger`). `false` ⇒ block (no ledger
 *   or account call fabricated).
 */
/** The «실적» leg: an outcome that never throws, like the two legs above. */
type PerformanceLegOutcome =
  | { kind: 'ok'; cell: PerformanceCell }
  | { kind: 'unauthorized' };

async function fetchPerformance(period: MetricsPeriod): Promise<PerformanceLegOutcome> {
  try {
    return { kind: 'ok', cell: await performanceMetricsCell(getFinanceMetrics(period)) };
  } catch (err) {
    // `performanceMetricsCell` re-throws ONLY a 401 — every other failure is
    // already a `forbidden` / `degraded` cell.
    if (err instanceof ApiError && err.status === 401) return { kind: 'unauthorized' };
    return { kind: 'ok', cell: { status: 'degraded', value: null } };
  }
}

/**
 * @param period  the «실적» strip's period (`?period=`; the page has already
 *   folded an absent / unknown value into {@link DEFAULT_OVERVIEW_PERIOD}).
 */
export async function getFinanceOverviewState(
  eligible: boolean,
  period: MetricsPeriod = DEFAULT_OVERVIEW_PERIOD,
): Promise<FinanceOverviewState> {
  if (!eligible) {
    return { ...EMPTY, notEligible: true, period };
  }

  // Each leg resolves to an outcome object and NEVER throws (own
  // try/catch) — Promise.all here only awaits all three settling, it does
  // NOT couple their failure handling (independent degrade).
  const [ledgerOutcome, accountOutcome, performanceOutcome] = await Promise.all([
    fetchLedgerSummary(),
    fetchAccountSnapshot(),
    fetchPerformance(period),
  ]);

  if (
    ledgerOutcome.kind === 'unauthorized' ||
    accountOutcome.kind === 'unauthorized' ||
    performanceOutcome.kind === 'unauthorized'
  ) {
    // Shared credential — no partial authed state → ONE whole-session
    // re-login (never per-leg).
    redirect('/login?error=session_expired');
  }

  if (ledgerOutcome.kind === 'forbidden' || accountOutcome.kind === 'forbidden') {
    // Same shared domain-facing token / finance tenant scope drives both
    // legs — a 403 on either means the operator's token is not
    // finance-scoped at all (mirrors the `/finance/accounts` + `/ledger`
    // page-level `forbidden` block).
    return { ...EMPTY, forbidden: true, period };
  }

  return {
    notEligible: false,
    forbidden: false,
    ledger: ledgerOutcome.kind === 'ok' ? ledgerOutcome.summary : null,
    ledgerDegraded: ledgerOutcome.kind === 'degraded',
    defaultAccountMissing: accountOutcome.kind === 'missing',
    accountSnapshot: accountOutcome.kind === 'ok' ? accountOutcome.snapshot : null,
    accountDegraded: accountOutcome.kind === 'degraded',
    accountNotFound: accountOutcome.kind === 'notFound',
    period,
    performance: performanceOutcome.kind === 'ok'
      ? performanceOutcome.cell
      : { status: 'degraded', value: null },
  };
}
