import { describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { PeriodPayoutsTable } from '@/features/ecommerce-ops/components/PeriodPayoutsTable';
import {
  payoutBarMax,
  payoutBarScopeText,
} from '@/features/ecommerce-ops/lib/payout-bars';
import {
  periodEntryBarScopeText,
  periodEntryMax,
} from '@/features/ledger-ops/lib/period-entry-bars';
import type { Payout } from '@/features/ecommerce-ops/api/settlement-types';
import type { PeriodsResponse } from '@/features/ledger-ops';

// PeriodsTable reads through TanStack Query; the bars only need the seeded page.
vi.mock('@/features/ledger-ops/hooks/use-ledger-ops', () => ({
  usePeriods: (_q: unknown, initial: unknown) => ({ data: initial }),
}));
import { PeriodsTable } from '@/features/ledger-ops/components/PeriodsTable';

/**
 * TASK-PC-FE-359 — settlement-period payout bars + ledger period entry-count
 * bars. Both scale against THIS page and say so (Failure Scenario 3 — a
 * 20-row page must not be drawn as if it were the whole population).
 */

const payout = (id: string, net: number, status = 'PAID'): Payout => ({
  payoutId: `payout-${id}`,
  sellerId: `seller-${id}`,
  payableNetMinor: net,
  commissionMinor: 100,
  accrualCount: 1,
  status,
  payoutReference: null,
  paidAt: null,
});

const pagination = {
  prevDisabled: true,
  nextDisabled: true,
  pageInfo: '1 / 1 페이지 · 총 3건',
  onPrev: () => {},
  onNext: () => {},
};

const pct = (testid: string) =>
  Number(
    (screen.getByTestId(testid).firstElementChild as HTMLElement).dataset.pct,
  );

describe('payout bars — helpers', () => {
  it('max is the largest positive amount; negatives / zero never set the scale', () => {
    expect(payoutBarMax([payout('a', 82800), payout('b', 52800), payout('c', -5000)])).toBe(82800);
    expect(payoutBarMax([payout('a', -1), payout('b', 0)])).toBe(0);
    expect(payoutBarMax([])).toBe(0);
  });

  it('scope sentence: this page vs the whole period', () => {
    expect(payoutBarScopeText(20, 45)).toBe(
      '막대는 이 페이지 20건 안에서 가장 큰 지급액 대비 길이입니다 (전체 45건).',
    );
    expect(payoutBarScopeText(2, 2)).toBe(
      '막대는 이 기간 지급 2건 중 가장 큰 지급액 대비 길이입니다.',
    );
  });
});

describe('PeriodPayoutsTable — bars', () => {
  it('bar widths are proportional to payableNetMinor; the amount text is unchanged', () => {
    render(
      <PeriodPayoutsTable
        rows={[payout('a', 82800), payout('b', 41400, 'PENDING')]}
        totalElements={2}
        pagination={pagination}
      />,
    );
    expect(pct('payout-row-bar-0')).toBe(100);
    expect(pct('payout-row-bar-1')).toBe(50);
    expect(screen.getByTestId('payout-row-bar-0').getAttribute('aria-hidden')).toBe('true');
    expect(screen.getByTestId('payout-row-0').textContent).toContain('₩82,800');
    expect(screen.getByTestId('settlements-payouts-bar-scope').textContent).toBe(
      '막대는 이 기간 지급 2건 중 가장 큰 지급액 대비 길이입니다.',
    );
    expect(screen.queryByTestId('settlements-payouts-bar-nonpositive')).toBeNull();
  });

  it('a negative (clawback) or zero amount draws no bar — text only — and the note says so', () => {
    render(
      <PeriodPayoutsTable
        rows={[payout('a', 10000), payout('b', -5000), payout('c', 0)]}
        totalElements={3}
        pagination={pagination}
      />,
    );
    expect(screen.getByTestId('payout-row-bar-0')).toBeInTheDocument();
    expect(screen.queryByTestId('payout-row-bar-1')).toBeNull();
    expect(screen.queryByTestId('payout-row-bar-2')).toBeNull();
    expect(screen.getByTestId('payout-row-1').textContent).toContain('-₩5,000');
    expect(screen.getByTestId('settlements-payouts-bar-nonpositive').textContent).toBe(
      ' 0원이거나 음수(되돌림)인 지급액은 막대 없이 금액만 표시합니다.',
    );
  });

  it('a partial page discloses «이 페이지 M건 / 전체 N건»', () => {
    render(
      <PeriodPayoutsTable
        rows={[payout('a', 10000)]}
        totalElements={21}
        pagination={pagination}
      />,
    );
    expect(screen.getByTestId('settlements-payouts-bar-scope').textContent).toBe(
      '막대는 이 페이지 1건 안에서 가장 큰 지급액 대비 길이입니다 (전체 21건).',
    );
  });
});

describe('ledger period entry-count bars', () => {
  const periods = (counts: (number | null)[], totalElements: number, page = 0): PeriodsResponse => ({
    data: counts.map((c, i) => ({
      periodId: `period-${i}`,
      status: c === null ? 'OPEN' : 'CLOSED',
      entryCount: c,
    })),
    meta: { page, size: 20, totalElements },
  } as unknown as PeriodsResponse);

  it('helpers: max ignores null; scope says recent / this page / all', () => {
    expect(periodEntryMax(periods([12, null, 3], 3).data)).toBe(12);
    expect(periodEntryBarScopeText(20, 31, 0)).toBe(
      '막대는 최근 20개 기간 안에서 가장 많은 분개 수 대비 길이입니다 (전체 31개).',
    );
    expect(periodEntryBarScopeText(11, 31, 1)).toBe(
      '막대는 이 페이지 11개 기간 안에서 가장 많은 분개 수 대비 길이입니다 (전체 31개).',
    );
    expect(periodEntryBarScopeText(2, 2, 0)).toBe(
      '막대는 회계 기간 2개 전체 중 가장 많은 분개 수 대비 길이입니다.',
    );
  });

  it('renders proportional bars, no bar for a null (OPEN) count, numbers unchanged', () => {
    render(
      <PeriodsTable
        initial={periods([12, 3, null], 25)}
        selectedPeriodId={null}
        onSelect={() => {}}
      />,
    );
    expect(pct('ledger-period-entry-bar-0')).toBe(100);
    expect(pct('ledger-period-entry-bar-1')).toBe(25);
    expect(screen.queryByTestId('ledger-period-entry-bar-2')).toBeNull();
    expect(screen.getByTestId('ledger-period-row-2').textContent).toContain('—');
    expect(screen.getByTestId('ledger-period-row-0').textContent).toContain('12');
    expect(screen.getByTestId('ledger-periods-entry-bar-scope').textContent).toBe(
      '막대는 최근 3개 기간 안에서 가장 많은 분개 수 대비 길이입니다 (전체 25개).',
    );
    expect(screen.getByTestId('ledger-periods-pageinfo').textContent).toBe(
      '1 / 2 페이지 · 총 25건',
    );
  });
});
