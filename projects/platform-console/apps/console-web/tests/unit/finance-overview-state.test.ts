import { describe, it, expect, vi, beforeEach } from 'vitest';
import { ApiError, LedgerUnavailableError, FinanceUnavailableError } from '@/shared/api/errors';

/**
 * TASK-PC-FE-229 — `getFinanceOverviewState` server fan-out for the
 * `/finance` overview landing (supersedes the PARKED TASK-PC-FE-160).
 *
 * Covers: notEligible short-circuit (no ledger/account call fabricated),
 * happy-path aggregate mapping, 403 forbidden (shared credential — either
 * leg), INDEPENDENT degrade (a 503 in one leg never blanks the other —
 * the decisive AC-2/AC-3 rule), defaultAccountMissing (no registry
 * default), accountNotFound (404 on a stale default account id), and the
 * whole-session 401 redirect. Also pins the honest constraint: the
 * account leg calls ONLY `getAccount`/`getBalances` — NEVER a list/search
 * endpoint.
 */

const { redirectMock } = vi.hoisted(() => ({ redirectMock: vi.fn() }));
vi.mock('next/navigation', () => ({
  redirect: (p: string) => {
    redirectMock(p);
    throw new Error(`REDIRECT:${p}`);
  },
}));

const m = vi.hoisted(() => ({
  getTrialBalance: vi.fn(),
  listPeriods: vi.fn(),
  listDiscrepancies: vi.fn(),
  getFxRates: vi.fn(),
  getAccount: vi.fn(),
  getBalances: vi.fn(),
  getFinanceDefaultAccountId: vi.fn(),
}));
vi.mock('@/features/ledger-ops/api/ledger-api', () => ({
  getTrialBalance: m.getTrialBalance,
  listPeriods: m.listPeriods,
  listDiscrepancies: m.listDiscrepancies,
  getFxRates: m.getFxRates,
}));
vi.mock('@/features/finance-ops/api/finance-api', () => ({
  getAccount: m.getAccount,
  getBalances: m.getBalances,
}));
vi.mock('@/shared/lib/finance-default-account-id', () => ({
  getFinanceDefaultAccountId: m.getFinanceDefaultAccountId,
}));

import { getFinanceOverviewState } from '@/features/finance-overview/api/overview-state';

const trialBalance = (inBalance: boolean) => ({
  accounts: [],
  grandDebitTotal: { amount: '100', currency: 'KRW' },
  grandCreditTotal: { amount: '100', currency: 'KRW' },
  grandBaseDebitTotal: { amount: '100', currency: 'KRW' },
  grandBaseCreditTotal: { amount: '100', currency: 'KRW' },
  inBalance,
});

const periods = (statuses: string[]) => ({
  data: statuses.map((status, i) => ({ periodId: `p${i}`, status })),
  meta: { totalElements: statuses.length },
});

const discrepancies = (totalElements: number) => ({
  data: [],
  meta: { totalElements },
});

const fxRates = (opts: {
  feedEnabled?: boolean;
  rates?: { asOf: string; stale: boolean }[];
} = {}) => ({
  feedEnabled: opts.feedEnabled ?? true,
  rates: (opts.rates ?? []).map((r, i) => ({
    baseCurrency: 'KRW',
    foreignCurrency: 'USD',
    rate: '1350.5',
    asOf: r.asOf,
    source: 'ecb',
    fetchedAt: r.asOf,
    ageSeconds: i,
    stale: r.stale,
  })),
});

const account = (status = 'ACTIVE') => ({
  accountId: 'acct-1',
  status,
  currency: 'KRW',
  kycLevel: 'BASIC',
});

const balances = () => ({
  data: [{ currency: 'KRW', ledger: '100', available: '80', held: '20' }],
  meta: { timestamp: 'x' },
});

/** Default happy fan-out: eligible, default account configured, every leg resolves. */
function seedHappy() {
  m.getTrialBalance.mockResolvedValue(trialBalance(true));
  m.listPeriods.mockResolvedValue(periods(['OPEN', 'CLOSED', 'OPEN']));
  m.listDiscrepancies.mockResolvedValue(discrepancies(3));
  m.getFxRates.mockResolvedValue(
    fxRates({
      rates: [
        { asOf: '2026-07-08T00:00:00Z', stale: false },
        { asOf: '2026-07-07T00:00:00Z', stale: true },
      ],
    }),
  );
  m.getFinanceDefaultAccountId.mockResolvedValue('acct-1');
  m.getAccount.mockResolvedValue(account());
  m.getBalances.mockResolvedValue(balances());
}

beforeEach(() => {
  vi.clearAllMocks();
});

describe('getFinanceOverviewState (TASK-PC-FE-229)', () => {
  it('not eligible → notEligible block, NO ledger or account call fabricated', async () => {
    const state = await getFinanceOverviewState(false);
    expect(state.notEligible).toBe(true);
    expect(state.ledger).toBeNull();
    expect(state.accountSnapshot).toBeNull();
    expect(m.getTrialBalance).not.toHaveBeenCalled();
    expect(m.listPeriods).not.toHaveBeenCalled();
    expect(m.listDiscrepancies).not.toHaveBeenCalled();
    expect(m.getFxRates).not.toHaveBeenCalled();
    expect(m.getFinanceDefaultAccountId).not.toHaveBeenCalled();
    expect(m.getAccount).not.toHaveBeenCalled();
    expect(m.getBalances).not.toHaveBeenCalled();
  });

  it('happy → maps ledger aggregate tiles + default-account snapshot', async () => {
    seedHappy();
    const state = await getFinanceOverviewState(true);

    expect(state.notEligible).toBe(false);
    expect(state.forbidden).toBe(false);
    expect(state.ledgerDegraded).toBe(false);
    expect(state.accountDegraded).toBe(false);

    expect(state.ledger).not.toBeNull();
    expect(state.ledger?.inBalance).toBe(true);
    expect(state.ledger?.openPeriodsCount).toBe(2); // 2 of 3 are OPEN
    expect(state.ledger?.openDiscrepanciesCount).toBe(3);
    expect(state.ledger?.fxFeedEnabled).toBe(true);
    expect(state.ledger?.fxStaleCount).toBe(1);
    expect(state.ledger?.fxLatestAsOf).toBe('2026-07-08T00:00:00Z');

    expect(state.defaultAccountMissing).toBe(false);
    expect(state.accountSnapshot).not.toBeNull();
    expect(state.accountSnapshot?.account.accountId).toBe('acct-1');
    expect(state.accountSnapshot?.balances.data).toHaveLength(1);
  });

  it('honest constraint: the account leg calls ONLY getAccount/getBalances — NEVER a list/search endpoint', async () => {
    seedHappy();
    await getFinanceOverviewState(true);
    expect(m.getAccount).toHaveBeenCalledTimes(1);
    expect(m.getAccount).toHaveBeenCalledWith('acct-1');
    expect(m.getBalances).toHaveBeenCalledTimes(1);
    expect(m.getBalances).toHaveBeenCalledWith('acct-1');
    // No mock in this suite exposes a list/search function at all — the
    // module under test imports ONLY getAccount/getBalances from
    // finance-api (see the vi.mock factory above), so a fabricated
    // list/search call would be a compile-time impossibility, not just a
    // runtime assertion.
  });

  it('defaultAccountMissing: no registry default account id → missing state, NO account-service call', async () => {
    seedHappy();
    m.getFinanceDefaultAccountId.mockResolvedValue(null);
    const state = await getFinanceOverviewState(true);
    expect(state.defaultAccountMissing).toBe(true);
    expect(state.accountSnapshot).toBeNull();
    expect(state.accountDegraded).toBe(false);
    expect(m.getAccount).not.toHaveBeenCalled();
    expect(m.getBalances).not.toHaveBeenCalled();
    // The ledger leg is UNAFFECTED by the missing default account.
    expect(state.ledger).not.toBeNull();
    expect(state.ledgerDegraded).toBe(false);
  });

  it('INDEPENDENT degrade: ledger 503 but account 200 → account snapshot renders, ledger tile degrades only', async () => {
    seedHappy();
    m.getTrialBalance.mockRejectedValue(
      new LedgerUnavailableError('downstream', 'SERVICE_UNAVAILABLE', 'down'),
    );
    const state = await getFinanceOverviewState(true);
    expect(state.ledgerDegraded).toBe(true);
    expect(state.ledger).toBeNull();
    // Account leg unaffected.
    expect(state.accountDegraded).toBe(false);
    expect(state.accountSnapshot).not.toBeNull();
    expect(state.accountSnapshot?.account.accountId).toBe('acct-1');
  });

  it('INDEPENDENT degrade: account 503 but ledger 200 → ledger tiles render, account snapshot degrades only', async () => {
    seedHappy();
    m.getAccount.mockRejectedValue(
      new FinanceUnavailableError('downstream', 'SERVICE_UNAVAILABLE', 'down'),
    );
    const state = await getFinanceOverviewState(true);
    expect(state.accountDegraded).toBe(true);
    expect(state.accountSnapshot).toBeNull();
    // Ledger leg unaffected.
    expect(state.ledgerDegraded).toBe(false);
    expect(state.ledger).not.toBeNull();
    expect(state.ledger?.inBalance).toBe(true);
  });

  it('accountNotFound: 404 ACCOUNT_NOT_FOUND on the registry-configured default id → inline actionable, ledger unaffected', async () => {
    seedHappy();
    m.getAccount.mockRejectedValue(
      new ApiError(404, 'ACCOUNT_NOT_FOUND', 'no such account'),
    );
    const state = await getFinanceOverviewState(true);
    expect(state.accountNotFound).toBe(true);
    expect(state.accountSnapshot).toBeNull();
    expect(state.accountDegraded).toBe(false);
    expect(state.ledger).not.toBeNull();
  });

  it('403 in the ledger leg → whole-overview forbidden (shared credential), account leg result discarded', async () => {
    seedHappy();
    m.getTrialBalance.mockRejectedValue(
      new ApiError(403, 'TENANT_FORBIDDEN', 'not scoped'),
    );
    const state = await getFinanceOverviewState(true);
    expect(state.forbidden).toBe(true);
    expect(state.ledger).toBeNull();
    expect(state.accountSnapshot).toBeNull();
  });

  it('403 in the account leg → whole-overview forbidden (shared credential), ledger leg result discarded', async () => {
    seedHappy();
    m.getAccount.mockRejectedValue(
      new ApiError(403, 'TENANT_FORBIDDEN', 'not scoped'),
    );
    const state = await getFinanceOverviewState(true);
    expect(state.forbidden).toBe(true);
    expect(state.ledger).toBeNull();
    expect(state.accountSnapshot).toBeNull();
  });

  it('401 in the ledger leg → whole-session redirect(/login) (not a per-leg degrade)', async () => {
    seedHappy();
    m.getTrialBalance.mockRejectedValue(
      new ApiError(401, 'UNAUTHORIZED', 'expired'),
    );
    await expect(getFinanceOverviewState(true)).rejects.toThrow(
      'REDIRECT:/login',
    );
    expect(redirectMock).toHaveBeenCalledWith('/login');
  });

  it('401 in the account leg → whole-session redirect(/login) (not a per-leg degrade)', async () => {
    seedHappy();
    m.getAccount.mockRejectedValue(new ApiError(401, 'UNAUTHORIZED', 'expired'));
    await expect(getFinanceOverviewState(true)).rejects.toThrow(
      'REDIRECT:/login',
    );
    expect(redirectMock).toHaveBeenCalledWith('/login');
  });

  it('empty FX cache → fxLatestAsOf null, fxStaleCount 0 (no crash on empty rates)', async () => {
    seedHappy();
    m.getFxRates.mockResolvedValue(fxRates({ rates: [] }));
    const state = await getFinanceOverviewState(true);
    expect(state.ledger?.fxLatestAsOf).toBeNull();
    expect(state.ledger?.fxStaleCount).toBe(0);
    expect(state.ledger?.fxRatesCount).toBe(0);
  });
});
