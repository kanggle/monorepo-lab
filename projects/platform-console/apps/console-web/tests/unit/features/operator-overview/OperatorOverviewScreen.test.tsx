import { describe, it, expect } from 'vitest';
import { render, screen, within } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import type { ReactNode } from 'react';
import { OperatorOverviewScreen } from '@/features/operator-overview';
import type { OperatorOverview } from '@/features/operator-overview';

/**
 * `<OperatorOverviewScreen>` structure (TASK-PC-FE-011):
 *   - renders exactly 5 `<DomainCard>` children, in the FIXED order
 *     `[gap, wms, scm, finance, erp]` regardless of the BE `cards[]`
 *     array order (the screen indexes by domain, not position);
 *   - surfaces the `asOf` server-side timestamp verbatim;
 *   - renders the all-degraded banner only when every card is
 *     non-`ok` (covered separately in OverviewDegradeBanner.test.tsx).
 *
 * No `'use client'` here — the screen is a server component; rendering
 * it in vitest's jsdom is fine because it has no client hooks.
 */

function wrapper() {
  const qc = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={qc}>{children}</QueryClientProvider>
  );
}

const HAPPY: OperatorOverview = {
  asOf: '2026-05-20T10:30:00Z',
  cards: [
    { domain: 'iam', status: 'ok', data: { totalElements: 12345 } },
    {
      domain: 'wms',
      status: 'ok',
      data: { inventorySnapshot: { totalStockUnits: 99000, alertCount: 3 } },
    },
    {
      domain: 'scm',
      status: 'ok',
      data: { meta: { warning: 'Not for procurement decisions (S5)' } },
    },
    {
      domain: 'finance',
      status: 'ok',
      data: { balance: { amount: '1234500', currency: 'KRW' } },
    },
    {
      domain: 'erp',
      status: 'ok',
      data: { meta: { totalElements: 87 } },
    },
  ],
};

describe('OperatorOverviewScreen — renders 5 cards in FIXED order', () => {
  it('renders exactly 5 DomainCard children in [gap, wms, scm, finance, erp] order', () => {
    render(<OperatorOverviewScreen overview={HAPPY} />, {
      wrapper: wrapper(),
    });

    const container = screen.getByTestId('operator-overview-cards');
    const cards = within(container).getAllByTestId(/^operator-overview-card-/);
    // Filter to top-level domain cards only (data-domain set on the
    // section). The inner sub-testids (e.g. -gap-total) also start
    // with the prefix; isolate the 5 actual cards via the
    // `data-domain` attribute.
    const cardSections = cards.filter((el) => el.hasAttribute('data-domain'));
    expect(cardSections).toHaveLength(5);
    expect(cardSections.map((el) => el.getAttribute('data-domain'))).toEqual([
      'iam',
      'wms',
      'scm',
      'finance',
      'erp',
    ]);
  });

  it('renders the 5 cards in FIXED order even when the BE returns them shuffled', () => {
    const shuffled: OperatorOverview = {
      ...HAPPY,
      cards: [
        HAPPY.cards[4]!, // erp
        HAPPY.cards[2]!, // scm
        HAPPY.cards[0]!, // gap
        HAPPY.cards[3]!, // finance
        HAPPY.cards[1]!, // wms
      ],
    };
    render(<OperatorOverviewScreen overview={shuffled} />, {
      wrapper: wrapper(),
    });
    const container = screen.getByTestId('operator-overview-cards');
    const cards = within(container)
      .getAllByTestId(/^operator-overview-card-/)
      .filter((el) => el.hasAttribute('data-domain'));
    expect(cards.map((el) => el.getAttribute('data-domain'))).toEqual([
      'iam',
      'wms',
      'scm',
      'finance',
      'erp',
    ]);
  });

  it('surfaces the asOf timestamp verbatim', () => {
    render(<OperatorOverviewScreen overview={HAPPY} />, {
      wrapper: wrapper(),
    });
    expect(screen.getByTestId('operator-overview-asof')).toHaveTextContent(
      '2026-05-20T10:30:00Z',
    );
  });

  it('does NOT render the all-degraded banner when at least one card is ok', () => {
    render(<OperatorOverviewScreen overview={HAPPY} />, {
      wrapper: wrapper(),
    });
    expect(
      screen.queryByTestId('operator-overview-all-degraded'),
    ).not.toBeInTheDocument();
  });

  it('READ-ONLY: no destructive/confirm dialog and no reason-input form anywhere', () => {
    render(<OperatorOverviewScreen overview={HAPPY} />, {
      wrapper: wrapper(),
    });
    expect(screen.queryByTestId('confirm-dialog')).not.toBeInTheDocument();
    expect(screen.queryByTestId('confirm-reason')).not.toBeInTheDocument();
  });

  it('renders the section heading', () => {
    render(<OperatorOverviewScreen overview={HAPPY} />, {
      wrapper: wrapper(),
    });
    expect(
      screen.getByRole('heading', { name: '운영자 통합 개요' }),
    ).toBeInTheDocument();
  });
});
