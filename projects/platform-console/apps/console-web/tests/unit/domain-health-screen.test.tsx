import { describe, it, expect } from 'vitest';
import { render, screen, within } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import type { ReactNode } from 'react';
import { DomainHealthScreen, DomainHealthCard } from '@/features/domain-health';
import type { Card, DomainHealth } from '@/features/domain-health';

/**
 * `<DomainHealthScreen>` (TASK-PC-FE-013):
 *   - renders exactly 5 `<DomainHealthCard>` children in the FIXED
 *     order `[gap, wms, scm, finance, erp]` regardless of the BE
 *     `cards[]` array order (the screen indexes by domain, not
 *     position) and regardless of card status (cards never reordered
 *     by status — § 2.4.9.2 invariant).
 *   - renders all 4 `data.status` variants (UP/DOWN/OUT_OF_SERVICE/UNKNOWN)
 *     plus the degraded branch with distinct visuals.
 *   - DegradeBanner shown only when all 5 cards are `degraded`.
 */

function wrapper() {
  const qc = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={qc}>{children}</QueryClientProvider>
  );
}

const MIXED_VARIANTS: DomainHealth = {
  asOf: '2026-05-21T01:30:00Z',
  cards: [
    { domain: 'gap', status: 'ok', data: { status: 'UP' } },
    { domain: 'wms', status: 'ok', data: { status: 'DOWN' } },
    { domain: 'scm', status: 'degraded', reason: 'DOWNSTREAM_ERROR' },
    { domain: 'finance', status: 'ok', data: { status: 'OUT_OF_SERVICE' } },
    { domain: 'erp', status: 'ok', data: { status: 'UNKNOWN' } },
  ],
};

const ALL_DEGRADED_CARDS: Card[] = [
  { domain: 'gap', status: 'degraded', reason: 'DOWNSTREAM_ERROR' },
  { domain: 'wms', status: 'degraded', reason: 'TIMEOUT' },
  { domain: 'scm', status: 'degraded', reason: 'CIRCUIT_OPEN' },
  { domain: 'finance', status: 'degraded', reason: 'DOWNSTREAM_ERROR' },
  { domain: 'erp', status: 'degraded', reason: 'TIMEOUT' },
];

const ALL_DEGRADED: DomainHealth = {
  asOf: '2026-05-21T01:30:00Z',
  cards: ALL_DEGRADED_CARDS as DomainHealth['cards'],
};

describe('DomainHealthScreen — fixed 5-card order', () => {
  it('renders exactly 5 cards in [gap, wms, scm, finance, erp] order', () => {
    render(<DomainHealthScreen health={MIXED_VARIANTS} />, {
      wrapper: wrapper(),
    });
    const container = screen.getByTestId('domain-health-cards');
    const cardSections = within(container)
      .getAllByTestId(/^domain-health-card-/)
      .filter((el) => el.hasAttribute('data-domain'));
    expect(cardSections).toHaveLength(5);
    expect(cardSections.map((el) => el.getAttribute('data-domain'))).toEqual([
      'gap',
      'wms',
      'scm',
      'finance',
      'erp',
    ]);
  });

  it('renders in FIXED order even when the BE returns the array shuffled', () => {
    const shuffled: DomainHealth = {
      ...MIXED_VARIANTS,
      cards: [
        MIXED_VARIANTS.cards[4]!, // erp
        MIXED_VARIANTS.cards[2]!, // scm
        MIXED_VARIANTS.cards[0]!, // gap
        MIXED_VARIANTS.cards[3]!, // finance
        MIXED_VARIANTS.cards[1]!, // wms
      ],
    };
    render(<DomainHealthScreen health={shuffled} />, {
      wrapper: wrapper(),
    });
    const container = screen.getByTestId('domain-health-cards');
    const cards = within(container)
      .getAllByTestId(/^domain-health-card-/)
      .filter((el) => el.hasAttribute('data-domain'));
    expect(cards.map((el) => el.getAttribute('data-domain'))).toEqual([
      'gap',
      'wms',
      'scm',
      'finance',
      'erp',
    ]);
  });

  it('does NOT reorder cards by status (degraded card stays in its fixed position)', () => {
    render(<DomainHealthScreen health={MIXED_VARIANTS} />, {
      wrapper: wrapper(),
    });
    const scmCard = screen.getByTestId('domain-health-card-scm');
    expect(scmCard.getAttribute('data-status')).toBe('degraded');
    // The 3rd card section in the cards grid is scm — the fixed-order
    // invariant. (Test 1 already asserts the full order.)
  });

  it('surfaces the asOf timestamp verbatim', () => {
    render(<DomainHealthScreen health={MIXED_VARIANTS} />, {
      wrapper: wrapper(),
    });
    expect(screen.getByTestId('domain-health-asof')).toHaveTextContent(
      '2026-05-21T01:30:00Z',
    );
  });
});

describe('DomainHealthScreen — 4 data.status variants + degraded', () => {
  it('renders distinct visuals for UP / DOWN / OUT_OF_SERVICE / UNKNOWN', () => {
    render(<DomainHealthScreen health={MIXED_VARIANTS} />, {
      wrapper: wrapper(),
    });
    // UP — gap
    const gapVisual = screen.getByTestId('domain-health-card-gap-visual');
    expect(gapVisual.getAttribute('data-health-status')).toBe('UP');
    // DOWN — wms (red-cross; producer self-reported critical; NOT degraded)
    const wmsVisual = screen.getByTestId('domain-health-card-wms-visual');
    expect(wmsVisual.getAttribute('data-health-status')).toBe('DOWN');
    const wmsCard = screen.getByTestId('domain-health-card-wms');
    expect(wmsCard.getAttribute('data-status')).toBe('ok');
    // OUT_OF_SERVICE — finance
    const financeVisual = screen.getByTestId(
      'domain-health-card-finance-visual',
    );
    expect(financeVisual.getAttribute('data-health-status')).toBe(
      'OUT_OF_SERVICE',
    );
    // UNKNOWN — erp
    const erpVisual = screen.getByTestId('domain-health-card-erp-visual');
    expect(erpVisual.getAttribute('data-health-status')).toBe('UNKNOWN');
  });

  it('renders the degraded card with reason + retry affordance (scm card)', () => {
    render(<DomainHealthScreen health={MIXED_VARIANTS} />, {
      wrapper: wrapper(),
    });
    expect(
      screen.getByTestId('domain-health-card-scm-degraded'),
    ).toBeInTheDocument();
    expect(
      screen.getByTestId('domain-health-card-scm-degraded-reason'),
    ).toHaveTextContent('DOWNSTREAM_ERROR');
    // RetryButton client component mounts under the degraded card.
    expect(
      screen.getByTestId('domain-health-retry-scm-degraded'),
    ).toBeInTheDocument();
  });

  it('READ-ONLY: no destructive/confirm dialog and no reason-input form anywhere', () => {
    render(<DomainHealthScreen health={MIXED_VARIANTS} />, {
      wrapper: wrapper(),
    });
    expect(screen.queryByTestId('confirm-dialog')).not.toBeInTheDocument();
    expect(screen.queryByTestId('confirm-reason')).not.toBeInTheDocument();
  });

  it('renders the section heading', () => {
    render(<DomainHealthScreen health={MIXED_VARIANTS} />, {
      wrapper: wrapper(),
    });
    expect(
      screen.getByRole('heading', { name: '도메인 상태 개요' }),
    ).toBeInTheDocument();
  });
});

describe('DomainHealthScreen — DegradeBanner', () => {
  it('shows the all-degraded banner when ALL 5 cards are degraded', () => {
    render(<DomainHealthScreen health={ALL_DEGRADED} />, {
      wrapper: wrapper(),
    });
    expect(
      screen.getByTestId('domain-health-all-degraded'),
    ).toBeInTheDocument();
    // The banner's retry button mounts too.
    expect(
      screen.getByTestId('domain-health-retry-banner'),
    ).toBeInTheDocument();
  });

  it('does NOT show the degrade banner when at least 1 card is ok', () => {
    render(<DomainHealthScreen health={MIXED_VARIANTS} />, {
      wrapper: wrapper(),
    });
    expect(
      screen.queryByTestId('domain-health-all-degraded'),
    ).not.toBeInTheDocument();
  });

  it('does NOT show the degrade banner when all 5 cards are ok', () => {
    const allOk: DomainHealth = {
      asOf: '2026-05-21T01:30:00Z',
      cards: [
        { domain: 'gap', status: 'ok', data: { status: 'UP' } },
        { domain: 'wms', status: 'ok', data: { status: 'UP' } },
        { domain: 'scm', status: 'ok', data: { status: 'UP' } },
        { domain: 'finance', status: 'ok', data: { status: 'UP' } },
        { domain: 'erp', status: 'ok', data: { status: 'UP' } },
      ],
    };
    render(<DomainHealthScreen health={allOk} />, { wrapper: wrapper() });
    expect(
      screen.queryByTestId('domain-health-all-degraded'),
    ).not.toBeInTheDocument();
  });
});

describe('DomainHealthCard — sub-branches surface visual hint', () => {
  it('UP card renders glyph "OK" + label "정상"', () => {
    const card: Card = { domain: 'gap', status: 'ok', data: { status: 'UP' } };
    const health: DomainHealth = { asOf: 't', cards: [card] as never };
    render(<DomainHealthCard card={card} healthForRetry={health} />, {
      wrapper: wrapper(),
    });
    expect(
      screen.getByTestId('domain-health-card-gap-glyph'),
    ).toHaveTextContent('OK');
    expect(
      screen.getByTestId('domain-health-card-gap-label'),
    ).toHaveTextContent('정상');
  });

  it('DOWN card stays ok but renders the red-cross "심각 (자가 보고)" label (NOT degraded)', () => {
    const card: Card = { domain: 'wms', status: 'ok', data: { status: 'DOWN' } };
    const health: DomainHealth = { asOf: 't', cards: [card] as never };
    render(<DomainHealthCard card={card} healthForRetry={health} />, {
      wrapper: wrapper(),
    });
    const cardEl = screen.getByTestId('domain-health-card-wms');
    expect(cardEl.getAttribute('data-status')).toBe('ok'); // NOT degraded
    expect(
      screen.getByTestId('domain-health-card-wms-label'),
    ).toHaveTextContent('심각');
  });
});
