import { describe, it, expect } from 'vitest';
import { readFileSync } from 'node:fs';
import path from 'node:path';
import { render, screen, within } from '@testing-library/react';
import {
  FinanceOverviewScreen,
  FINANCE_PERFORMANCE_SCOPE_NOTE,
} from '@/features/finance-overview';
import type { FinanceOverviewState } from '@/features/finance-overview';
import type { PerformanceCell } from '@/shared/api/performance-metrics';
import { runAxe } from '../a11y/axe-helper';

/** `GET /api/finance/accounts/metrics` (TASK-MONO-812) — KRW and USD, in the producer's order. */
const PERF_OK: PerformanceCell = {
  status: 'ok',
  value: {
    period: 'month',
    timezone: 'Asia/Seoul',
    current: { from: '2026-10-01T00:00:00+09:00', to: '2026-10-24T14:05:00+09:00' },
    previous: { from: '2026-09-01T00:00:00+09:00', to: '2026-09-24T14:05:00+09:00' },
    metrics: [
      { key: 'topupCount', unit: 'count', currency: null, current: '3', previous: '2' },
      { key: 'topupAmount:KRW', unit: 'money', currency: 'KRW', current: '900000', previous: '400000' },
      { key: 'topupAmount:USD', unit: 'money', currency: 'USD', current: '2550', previous: '0' },
      { key: 'transferCount', unit: 'count', currency: null, current: '1', previous: '0' },
      { key: 'transferAmount:KRW', unit: 'money', currency: 'KRW', current: '50000', previous: '0' },
      { key: 'transferAmount:USD', unit: 'money', currency: 'USD', current: '0', previous: '0' },
      { key: 'captureCount', unit: 'count', currency: null, current: '1', previous: '1' },
      { key: 'captureAmount:KRW', unit: 'money', currency: 'KRW', current: '12000', previous: '8000' },
      { key: 'captureAmount:USD', unit: 'money', currency: 'USD', current: '0', previous: '0' },
    ],
    series: [
      {
        date: '2026-10-01',
        values: {
          topupCount: '1',
          'topupAmount:KRW': '300000',
          'topupAmount:USD': '0',
          transferCount: '0',
          'transferAmount:KRW': '0',
          'transferAmount:USD': '0',
          captureCount: '0',
          'captureAmount:KRW': '0',
          'captureAmount:USD': '0',
        },
      },
      {
        date: '2026-10-02',
        values: {
          topupCount: '2',
          'topupAmount:KRW': '600000',
          'topupAmount:USD': '2550',
          transferCount: '1',
          'transferAmount:KRW': '50000',
          'transferAmount:USD': '0',
          captureCount: '1',
          'captureAmount:KRW': '12000',
          'captureAmount:USD': '0',
        },
      },
    ],
  },
};

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
  period: 'month',
  performance: PERF_OK,
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

  it('F5: renders the available balance from the string minor-units via formatMoneyDisplay (grouped, ₩ mark — TASK-PC-FE-363)', () => {
    render(<FinanceOverviewScreen state={BASE} />);
    const row = screen.getByTestId('finance-overview-balance-0');
    // formatMoneyDisplay(KRW scale 0) groups the digits with a ₩ prefix —
    // assert the FORMATTED string, not a raw Number() coercion.
    expect(row).toHaveTextContent('KRW: ₩100,000 (가용)');
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

describe('FinanceOverviewScreen — «실적» strip (TASK-MONO-812)', () => {
  const kpis = () =>
    within(screen.getByTestId('finance-performance'))
      .getAllByRole('listitem')
      .filter((li) => /^finance-performance-kpi-[A-Za-z:]+$/.test(li.getAttribute('data-testid') ?? ''));

  it('is the first block under the heading and intro, before the ledger tiles', () => {
    render(<FinanceOverviewScreen state={BASE} />);
    const strip = screen.getByTestId('finance-performance');
    expect(within(strip).getByRole('heading', { name: '실적' })).toBeInTheDocument();
    const tiles = screen.getByTestId('finance-overview-ledger-tiles');
    // DOCUMENT_POSITION_FOLLOWING — the tiles come after the strip.
    expect(strip.compareDocumentPosition(tiles) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
  });

  it('one KPI per type count and per type × currency, in the producer order — KRW and USD separate', () => {
    render(<FinanceOverviewScreen state={BASE} />);
    expect(kpis().map((li) => li.getAttribute('data-testid'))).toEqual([
      'finance-performance-kpi-topupCount',
      'finance-performance-kpi-topupAmount:KRW',
      'finance-performance-kpi-topupAmount:USD',
      'finance-performance-kpi-transferCount',
      'finance-performance-kpi-transferAmount:KRW',
      'finance-performance-kpi-transferAmount:USD',
      'finance-performance-kpi-captureCount',
      'finance-performance-kpi-captureAmount:KRW',
      'finance-performance-kpi-captureAmount:USD',
    ]);
    const strip = screen.getByTestId('finance-performance');
    expect(within(strip).getByText('충전 건수')).toBeInTheDocument();
    expect(within(strip).getByText('충전 금액 (KRW)')).toBeInTheDocument();
    expect(within(strip).getByText('충전 금액 (USD)')).toBeInTheDocument();
    expect(within(strip).getByText('캡처 금액 (KRW)')).toBeInTheDocument();
    expect(screen.getByTestId('finance-performance-kpi-topupCount-value')).toHaveTextContent('3');
    expect(screen.getByTestId('finance-performance-kpi-topupAmount:KRW-value')).toHaveTextContent('900,000');
    // USD minor units (cents) rendered as dollars — and never folded into the won card.
    expect(screen.getByTestId('finance-performance-kpi-topupAmount:USD-value')).toHaveTextContent('25.50');
    expect(screen.getByTestId('finance-performance-kpi-topupAmount:KRW-value')).not.toHaveTextContent('25.50');
    // The chart draws 충전 건수; no card is a link (no list filters by settle time).
    expect(screen.getByTestId('finance-performance-trend')).toHaveTextContent('충전 건수 · 일별');
    expect(screen.queryByTestId('finance-performance-kpi-topupCount-link')).toBeNull();
  });

  it('carries the D5 scope note — and the contract quotes it verbatim', () => {
    render(<FinanceOverviewScreen state={BASE} />);
    expect(screen.getByTestId('finance-performance-scope')).toHaveTextContent(FINANCE_PERFORMANCE_SCOPE_NOTE);
    expect(FINANCE_PERFORMANCE_SCOPE_NOTE).toContain('보류는 캡처된 금액만');
    expect(FINANCE_PERFORMANCE_SCOPE_NOTE).toContain('서로 더하거나 환산하지 않습니다');
    const contract = readFileSync(
      path.resolve(__dirname, '..', '..', '..', '..', 'specs', 'contracts', 'console-integration-contract.md'),
      'utf8',
    );
    expect(contract).toContain(`«${FINANCE_PERFORMANCE_SCOPE_NOTE}»`);
  });

  it('period switch links to /finance?period=…, the selected one marked', () => {
    render(<FinanceOverviewScreen state={{ ...BASE, period: 'week' }} />);
    expect(screen.getByTestId('finance-performance-period-week')).toHaveAttribute('href', '/finance?period=week');
    expect(screen.getByTestId('finance-performance-period-week')).toHaveAttribute('aria-current', 'page');
    expect(screen.getByTestId('finance-performance-period-month')).toHaveAttribute('href', '/finance?period=month');
  });

  it('a failed strip says «불러올 수 없습니다» in words, shows no number (never 0) — tiles and snapshot stay', () => {
    render(<FinanceOverviewScreen state={{ ...BASE, performance: { status: 'degraded', value: null } }} />);
    expect(screen.getByTestId('finance-performance-degraded')).toHaveTextContent('실적을 불러올 수 없습니다');
    expect(screen.getByTestId('finance-performance').textContent).not.toMatch(/\d/);
    expect(screen.queryByTestId('finance-performance-kpi-topupCount')).toBeNull();
    expect(screen.getByTestId('finance-overview-ledger-tiles')).toBeInTheDocument();
    expect(screen.getByTestId('finance-overview-account-snapshot')).toBeInTheDocument();
  });

  it('forbidden strip → «권한이 없습니다», the rest of the overview renders', () => {
    render(<FinanceOverviewScreen state={{ ...BASE, performance: { status: 'forbidden', value: null } }} />);
    expect(screen.getByTestId('finance-performance-forbidden')).toHaveTextContent('실적을 볼 권한이 없습니다');
    expect(screen.getByTestId('finance-performance').textContent).not.toMatch(/\d/);
    expect(screen.getByTestId('finance-overview-ledger-discrepancies')).toHaveTextContent('5');
  });

  it('no account id appears inside the strip', () => {
    render(<FinanceOverviewScreen state={BASE} />);
    expect(screen.getByTestId('finance-performance').textContent).not.toContain('acct-1');
  });

  // Scoped to the strip: the pre-existing ledger tiles below it carry a `<p>`
  // directly inside a `<dl>` (axe `definition-list`, serious) — a defect of
  // those tiles, recorded by TASK-MONO-812 and not this strip's to fix.
  it('the strip is axe-clean', async () => {
    render(<FinanceOverviewScreen state={BASE} />);
    expect(await runAxe(screen.getByTestId('finance-performance'))).toEqual([]);
  });
});
