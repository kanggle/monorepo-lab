import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { FinanceOverviewScreen } from '@/features/finance-overview';
import type { FinanceOverviewState } from '@/features/finance-overview';

/**
 * TASK-PC-FE-229 — `FinanceOverviewScreen` (the `/finance` overview
 * landing render). Server component, no providers needed. Covers: ledger
 * tile rendering, F5 money rendering (default-account balances), the
 * defaultAccountMissing / ledgerDegraded / accountDegraded / accountNotFound
 * per-tile placeholders (never a whole-screen blank), and the three
 * screen links.
 */

const BASE: FinanceOverviewState = {
  notEligible: false,
  forbidden: false,
  ledger: {
    inBalance: true,
    openPeriodsCount: 2,
    periodsSampleSize: 3,
    openDiscrepanciesCount: 5,
    fxFeedEnabled: true,
    fxRatesCount: 2,
    fxStaleCount: 1,
    fxLatestAsOf: '2026-07-08T00:00:00Z',
  },
  ledgerDegraded: false,
  defaultAccountMissing: false,
  accountSnapshot: {
    account: {
      accountId: 'acct-1',
      status: 'ACTIVE',
      currency: 'KRW',
      kycLevel: 'BASIC',
    },
    balances: {
      data: [
        { currency: 'KRW', ledger: '123456', available: '100000', held: '23456' },
      ],
      meta: { timestamp: 'x' },
    },
  },
  accountDegraded: false,
  accountNotFound: false,
};

describe('FinanceOverviewScreen (TASK-PC-FE-229)', () => {
  it('renders the heading + ledger tiles + account snapshot on the happy path', () => {
    render(<FinanceOverviewScreen state={BASE} />);
    expect(
      screen.getByRole('heading', { name: 'Finance 개요' }),
    ).toBeInTheDocument();

    expect(screen.getByTestId('finance-overview-ledger-tiles')).toBeInTheDocument();
    expect(screen.getByTestId('finance-overview-ledger-inbalance')).toHaveTextContent(
      '균형',
    );
    expect(screen.getByTestId('finance-overview-ledger-periods')).toHaveTextContent(
      '2',
    );
    expect(
      screen.getByTestId('finance-overview-ledger-discrepancies'),
    ).toHaveTextContent('5');
    expect(screen.getByTestId('finance-overview-ledger-fx')).toHaveTextContent(
      '2건 중 1건 오래됨',
    );

    expect(
      screen.getByTestId('finance-overview-account-snapshot'),
    ).toBeInTheDocument();
    expect(screen.getByTestId('finance-overview-account-id')).toHaveTextContent(
      'acct-1',
    );
    expect(screen.getByTestId('finance-overview-account-status')).toHaveTextContent(
      'ACTIVE',
    );
  });

  it('F5: renders the available balance from the string minor-units via formatMoney (no raw minor-units digits)', () => {
    render(<FinanceOverviewScreen state={BASE} />);
    const row = screen.getByTestId('finance-overview-balance-0');
    // formatMoney(KRW scale 0) renders the digits as-is with a currency
    // suffix — assert the FORMATTED string, not a raw Number() coercion.
    expect(row).toHaveTextContent('100000 KRW');
  });

  it('trial-balance out-of-balance renders honestly (danger tone, "불균형")', () => {
    render(
      <FinanceOverviewScreen
        state={{
          ...BASE,
          ledger: { ...BASE.ledger!, inBalance: false },
        }}
      />,
    );
    expect(screen.getByTestId('finance-overview-ledger-inbalance')).toHaveTextContent(
      '불균형',
    );
  });

  it('ledgerDegraded → ledger placeholder renders, account snapshot UNAFFECTED', () => {
    render(
      <FinanceOverviewScreen
        state={{ ...BASE, ledger: null, ledgerDegraded: true }}
      />,
    );
    expect(screen.getByTestId('finance-overview-ledger-degraded')).toBeInTheDocument();
    expect(screen.queryByTestId('finance-overview-ledger-tiles')).toBeNull();
    // Account snapshot still renders.
    expect(
      screen.getByTestId('finance-overview-account-snapshot'),
    ).toBeInTheDocument();
  });

  it('accountDegraded → account placeholder renders, ledger tiles UNAFFECTED', () => {
    render(
      <FinanceOverviewScreen
        state={{ ...BASE, accountSnapshot: null, accountDegraded: true }}
      />,
    );
    expect(
      screen.getByTestId('finance-overview-account-degraded'),
    ).toBeInTheDocument();
    expect(
      screen.queryByTestId('finance-overview-account-snapshot'),
    ).toBeNull();
    // Ledger tiles still render.
    expect(screen.getByTestId('finance-overview-ledger-tiles')).toBeInTheDocument();
  });

  it('defaultAccountMissing → "미설정" hint + link to /account, ledger tiles UNAFFECTED', () => {
    render(
      <FinanceOverviewScreen
        state={{
          ...BASE,
          accountSnapshot: null,
          defaultAccountMissing: true,
        }}
      />,
    );
    const missing = screen.getByTestId('finance-overview-account-missing');
    expect(missing).toBeInTheDocument();
    expect(missing).toHaveTextContent('기본 finance 계좌 미설정');
    const link = screen.getByRole('link', { name: '계정 설정으로 이동' });
    expect(link).toHaveAttribute('href', '/account');
    expect(screen.getByTestId('finance-overview-ledger-tiles')).toBeInTheDocument();
  });

  it('accountNotFound → inline "계좌를 찾을 수 없음", ledger tiles UNAFFECTED', () => {
    render(
      <FinanceOverviewScreen
        state={{ ...BASE, accountSnapshot: null, accountNotFound: true }}
      />,
    );
    expect(
      screen.getByTestId('finance-overview-account-not-found'),
    ).toHaveTextContent('계좌를 찾을 수 없습니다');
    expect(screen.getByTestId('finance-overview-ledger-tiles')).toBeInTheDocument();
  });

  it('legacyAccountId (pre-TASK-PC-FE-229 /finance?accountId= bookmark) → surfaces a direct link to its new home', () => {
    render(<FinanceOverviewScreen state={BASE} legacyAccountId="acct-9" />);
    const hint = screen.getByTestId('finance-overview-legacy-account-hint');
    expect(hint).toBeInTheDocument();
    expect(screen.getByTestId('finance-overview-legacy-account-link')).toHaveAttribute(
      'href',
      '/finance/accounts?accountId=acct-9',
    );
  });

  it('no legacyAccountId → no legacy hint rendered', () => {
    render(<FinanceOverviewScreen state={BASE} />);
    expect(
      screen.queryByTestId('finance-overview-legacy-account-hint'),
    ).toBeNull();
  });

  it('renders links to 가이드/계좌/원장', () => {
    render(<FinanceOverviewScreen state={BASE} />);
    expect(screen.getByTestId('finance-overview-link-guide')).toHaveAttribute(
      'href',
      '/finance/guide',
    );
    expect(screen.getByTestId('finance-overview-link-accounts')).toHaveAttribute(
      'href',
      '/finance/accounts',
    );
    expect(screen.getByTestId('finance-overview-link-ledger')).toHaveAttribute(
      'href',
      '/ledger',
    );
  });
});
