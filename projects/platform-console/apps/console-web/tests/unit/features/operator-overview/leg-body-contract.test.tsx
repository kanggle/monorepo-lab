import { readFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import type { ReactNode } from 'react';
import { DomainCard } from '@/features/operator-overview';
import {
  CARD_ORDER,
  type Card,
  type DomainKey,
  type OperatorOverview,
} from '@/features/operator-overview';

/**
 * Six-card leg-body census — TASK-PC-FE-295 AC-7.
 *
 * Feeds each card renderer the **producer's own response body** (the shared
 * contract fixture, not a shape invented here) and asserts the card renders a
 * value rather than its "no data" placeholder.
 *
 * WHY A CENSUS AND NOT PER-CARD TESTS: three of the six cards (finance, wms,
 * scm) were broken by the same cause at the same time — console-bff loads the
 * producer body verbatim, the contract's § 2.4.9.1 example taught a different
 * shape, and each suite seeded its own invented body so both sides stayed
 * green. Fixing the three cards one at a time cannot stop the fourth; a census
 * over `CARD_ORDER` can, because a new leg with no fixture entry fails here.
 *
 * The fixture is the SAME file the console-bff side reads
 * (`OperatorOverviewLegBodyContractTest`) — that shared set is the point of
 * AC-3. It is read with `fs` rather than imported so the app's `tsc --noEmit`
 * rootDir is untouched by a path outside `apps/console-web`.
 */

const FIXTURE_PATH = resolve(
  dirname(fileURLToPath(import.meta.url)),
  '../../../../../../specs/contracts/fixtures/operator-overview-leg-bodies.json',
);

type LegFixture = {
  producer: string;
  sourceOfShape: string;
  surfaced: string;
  body: unknown;
};

const fixture = JSON.parse(readFileSync(FIXTURE_PATH, 'utf8')) as {
  legs: Record<string, LegFixture>;
};

function wrapper() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={qc}>{children}</QueryClientProvider>
  );
}

function envelopeFor(card: Card): OperatorOverview {
  return { asOf: '2026-09-18T01:30:00Z', cards: [card, card, card, card, card] };
}

/**
 * The testid whose text carries the card's headline number / status, plus the
 * rendered text that means "this card could not read its own producer body".
 * `—` is the numeric cards' placeholder; the finance card words it instead.
 */
const HEADLINE: Record<DomainKey, { testId: string; emptyText: string }> = {
  iam: { testId: 'operator-overview-card-iam-total', emptyText: '—' },
  wms: { testId: 'operator-overview-card-wms-stock', emptyText: '—' },
  scm: { testId: 'operator-overview-card-scm-nodes', emptyText: '—' },
  finance: {
    testId: 'operator-overview-card-finance-status',
    emptyText: '잔액 정보 없음',
  },
  erp: { testId: 'operator-overview-card-erp-departments', emptyText: '—' },
  ecommerce: {
    testId: 'operator-overview-card-ecommerce-products',
    emptyText: '—',
  },
};

describe('operator overview — every leg renders its producer body (AC-7 census)', () => {
  it('the fixture covers exactly the six declared cards (a new leg must add one)', () => {
    expect(Object.keys(fixture.legs).sort()).toEqual([...CARD_ORDER].sort());
  });

  for (const domain of CARD_ORDER) {
    it(`${domain} card renders a value from the producer body, not its placeholder`, () => {
      const leg = fixture.legs[domain];
      expect(leg, `no fixture entry for the ${domain} leg`).toBeDefined();

      const card: Card = { domain, status: 'ok', data: leg.body };
      render(<DomainCard card={card} overviewForRetry={envelopeFor(card)} />, {
        wrapper: wrapper(),
      });

      const headline = screen.getByTestId(HEADLINE[domain].testId);
      expect(
        headline.textContent,
        `${domain}: the card rendered its "no data" placeholder for a body the ` +
          `producer really sends (${leg.producer}) — the renderer and the ` +
          `producer disagree about the shape`,
      ).not.toBe(HEADLINE[domain].emptyText);
    });
  }
});

describe('operator overview — money discipline survives the real finance body (F5)', () => {
  it('finance renders availability + currency without coercing any minor-units string', () => {
    const card: Card = {
      domain: 'finance',
      status: 'ok',
      data: fixture.legs.finance.body,
    };
    render(<DomainCard card={card} overviewForRetry={envelopeFor(card)} />, {
      wrapper: wrapper(),
    });
    expect(
      screen.getByTestId('operator-overview-card-finance-status'),
    ).toHaveTextContent('잔액 조회 가능');
    expect(
      screen.getByTestId('operator-overview-card-finance-currency'),
    ).toHaveTextContent('KRW');
    // F5: the minor-units string must never appear coerced/formatted.
    expect(screen.queryByText('9,876,500')).not.toBeInTheDocument();
    expect(screen.queryByText('9876500')).not.toBeInTheDocument();
  });

  it('an account with no balance rows is the one honest "no balance" case', () => {
    const card: Card = {
      domain: 'finance',
      status: 'ok',
      data: { data: [], meta: {} },
    };
    render(<DomainCard card={card} overviewForRetry={envelopeFor(card)} />, {
      wrapper: wrapper(),
    });
    expect(
      screen.getByTestId('operator-overview-card-finance-status'),
    ).toHaveTextContent('잔액 정보 없음');
  });
});
