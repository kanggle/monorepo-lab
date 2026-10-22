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
 * scm) were broken by the same cause at the same time — the composition loads
 * the producer body verbatim, the contract's § 2.4.9.1 example taught a different
 * shape, and each suite seeded its own invented body so both sides stayed
 * green. Fixing the three cards one at a time cannot stop the fourth; a census
 * over `CARD_ORDER` can, because a new leg with no fixture entry fails here.
 *
 * The fixture is the SAME file the composition side reads
 * (`tests/unit/dashboard-composition-routes.test.ts`; before ADR-MONO-081 it
 * was the retired BFF's `OperatorOverviewLegBodyContractTest`) — that shared
 * set is the point of AC-3. It is read with `fs` rather than imported so the app's `tsc --noEmit`
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
  variants: { erpNotLinked: LegFixture };
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
 * The testid whose text carries the card's headline number, plus the number
 * the fixture body says it must show (TASK-PC-FE-364 — every card is one page
 * total now). `—` is the placeholder for «could not read my own producer body».
 * Asserting the EXACT value, not just «not —», is what catches a renderer that
 * reads a different field of the same body (e.g. `content.length`).
 */
const HEADLINE: Record<DomainKey, { testId: string; expected: string }> = {
  iam: { testId: 'operator-overview-card-iam-locked', expected: '3' },
  wms: { testId: 'operator-overview-card-wms-unshipped', expected: '14' },
  scm: { testId: 'operator-overview-card-scm-draft-po', expected: '4' },
  finance: { testId: 'operator-overview-card-finance-open-discrepancies', expected: '6' },
  erp: { testId: 'operator-overview-card-erp-inbox', expected: '2' },
  ecommerce: { testId: 'operator-overview-card-ecommerce-to-ship', expected: '5' },
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
        `${domain}: the card did not render the page total of a body the ` +
          `producer really sends (${leg.producer}) — the renderer and the ` +
          `producer disagree about the shape`,
      ).toBe(HEADLINE[domain].expected);
    });
  }
});

describe('operator overview — the erp «not linked» body the producer really sends (TASK-PC-FE-364)', () => {
  it('🔴 no meta.actorEmployeeId → «직원 연결 없음», and NO number (never a 0)', () => {
    const card: Card = { domain: 'erp', status: 'ok', data: fixture.variants.erpNotLinked.body };
    render(<DomainCard card={card} overviewForRetry={envelopeFor(card)} />, {
      wrapper: wrapper(),
    });
    expect(screen.getByTestId('operator-overview-card-erp-not-linked')).toHaveTextContent(
      '직원 연결 없음',
    );
    expect(screen.queryByTestId('operator-overview-card-erp-inbox')).not.toBeInTheDocument();
    // The body's own totalElements is 0 — it must not surface anywhere.
    expect(screen.getByTestId('operator-overview-card-erp').textContent).not.toMatch(/\b0\b/);
  });
});

describe('operator overview — money discipline survives the real finance body (F5)', () => {
  it('finance renders only the count — no minor-units string from a row, coerced or raw', () => {
    const card: Card = {
      domain: 'finance',
      status: 'ok',
      data: fixture.legs.finance.body,
    };
    render(<DomainCard card={card} overviewForRetry={envelopeFor(card)} />, {
      wrapper: wrapper(),
    });
    expect(
      screen.getByTestId('operator-overview-card-finance-open-discrepancies'),
    ).toHaveTextContent('6');
    // F5: the row's expectedMinor / actualMinor never reach the DOM.
    for (const s of ['9876500', '9,876,500', '9877500', '9,877,500']) {
      expect(screen.queryByText(s, { exact: false })).not.toBeInTheDocument();
    }
  });
});
