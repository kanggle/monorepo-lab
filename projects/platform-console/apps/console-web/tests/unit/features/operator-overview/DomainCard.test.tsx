import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import type { ReactNode } from 'react';
import { DomainCard } from '@/features/operator-overview';
import type {
  Card,
  OperatorOverview,
} from '@/features/operator-overview';

/**
 * `<DomainCard>` per-status branch coverage (TASK-PC-FE-011 / AC-20).
 *
 * Asserts the 3 distinct UI shapes per § 2.4.9.1:
 *  - `ok` — domain-specific summary using `data` (each domain has a
 *    distinct testid for its primary metric).
 *  - `degraded` — "data unavailable" placeholder + reason copy +
 *    retry button (the only client-component branch).
 *  - `forbidden` — "not available to your role / tenant" placeholder
 *    + reason copy; the finance × MISSING_PREREQUISITE combo surfaces
 *    the actionable "Configure a default finance account" hint per
 *    § 2.4.9.1 Implementation guidance.
 */

function wrapper() {
  const qc = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={qc}>{children}</QueryClientProvider>
  );
}

function envelopeFor(card: Card): OperatorOverview {
  // The retry button needs an envelope to seed; for unit branch tests
  // the envelope is otherwise unused (we never click; we just render).
  return { asOf: '2026-05-20T10:30:00Z', cards: [card, card, card, card, card] };
}

describe('DomainCard — ok branch (per-domain summaries)', () => {
  it('gap ok → renders the total account count', () => {
    const card: Card = {
      domain: 'gap',
      status: 'ok',
      data: { totalElements: 12345 },
    };
    render(<DomainCard card={card} overviewForRetry={envelopeFor(card)} />, {
      wrapper: wrapper(),
    });
    expect(screen.getByTestId('operator-overview-card-gap')).toHaveAttribute(
      'data-status',
      'ok',
    );
    expect(
      screen.getByTestId('operator-overview-card-gap-total'),
    ).toHaveTextContent('12,345');
  });

  it('wms ok → renders stock + alerts', () => {
    const card: Card = {
      domain: 'wms',
      status: 'ok',
      data: {
        inventorySnapshot: { totalStockUnits: 99000, alertCount: 3 },
      },
    };
    render(<DomainCard card={card} overviewForRetry={envelopeFor(card)} />, {
      wrapper: wrapper(),
    });
    expect(
      screen.getByTestId('operator-overview-card-wms-stock'),
    ).toHaveTextContent('99,000');
    expect(
      screen.getByTestId('operator-overview-card-wms-alerts'),
    ).toHaveTextContent('3');
  });

  it('scm ok → renders node count + surfaces meta.warning when present (S5 hint)', () => {
    const card: Card = {
      domain: 'scm',
      status: 'ok',
      data: {
        nodes: [{}, {}, {}],
        meta: { warning: 'Not for procurement decisions (S5)' },
      },
    };
    render(<DomainCard card={card} overviewForRetry={envelopeFor(card)} />, {
      wrapper: wrapper(),
    });
    expect(
      screen.getByTestId('operator-overview-card-scm-nodes'),
    ).toHaveTextContent('3');
    expect(
      screen.getByTestId('operator-overview-card-scm-warning'),
    ).toHaveTextContent('S5');
  });

  it('finance ok → renders "balance available" + currency code WITHOUT numeric coercion (F5)', () => {
    const card: Card = {
      domain: 'finance',
      status: 'ok',
      data: { balance: { amount: '1234500', currency: 'KRW' } },
    };
    render(<DomainCard card={card} overviewForRetry={envelopeFor(card)} />, {
      wrapper: wrapper(),
    });
    // F5: the UI surfaces only the "available" status + currency
    // code; the raw amount string is NOT rendered as a number.
    expect(
      screen.getByTestId('operator-overview-card-finance-status'),
    ).toHaveTextContent('잔액 조회 가능');
    expect(
      screen.getByTestId('operator-overview-card-finance-currency'),
    ).toHaveTextContent('KRW');
    // The raw minor-units string MUST NOT appear in the rendered DOM
    // as a number — verify the literal string is not present.
    expect(
      screen.queryByText('1,234,500'),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByText('1234500'),
    ).not.toBeInTheDocument();
  });

  it('erp ok → renders active department count from meta.totalElements', () => {
    const card: Card = {
      domain: 'erp',
      status: 'ok',
      data: { meta: { totalElements: 87 } },
    };
    render(<DomainCard card={card} overviewForRetry={envelopeFor(card)} />, {
      wrapper: wrapper(),
    });
    expect(
      screen.getByTestId('operator-overview-card-erp-departments'),
    ).toHaveTextContent('87');
  });
});

describe('DomainCard — degraded branch', () => {
  it.each([
    ['gap', 'DOWNSTREAM_ERROR'],
    ['wms', 'TIMEOUT'],
    ['scm', 'CIRCUIT_OPEN'],
    ['finance', 'DOWNSTREAM_ERROR'],
    ['erp', 'TIMEOUT'],
  ] as const)(
    '%s degraded/%s → placeholder + reason + retry button',
    (domain, reason) => {
      const card: Card = { domain, status: 'degraded', reason };
      render(<DomainCard card={card} overviewForRetry={envelopeFor(card)} />, {
        wrapper: wrapper(),
      });
      expect(
        screen.getByTestId(`operator-overview-card-${domain}-degraded`),
      ).toBeInTheDocument();
      expect(
        screen.getByTestId(
          `operator-overview-card-${domain}-degraded-reason`,
        ),
      ).toHaveTextContent(reason);
      expect(
        screen.getByTestId(`operator-overview-retry-${domain}-degraded`),
      ).toBeInTheDocument();
    },
  );

  it('degraded card status attribute is "degraded"', () => {
    const card: Card = {
      domain: 'gap',
      status: 'degraded',
      reason: 'TIMEOUT',
    };
    render(<DomainCard card={card} overviewForRetry={envelopeFor(card)} />, {
      wrapper: wrapper(),
    });
    expect(screen.getByTestId('operator-overview-card-gap')).toHaveAttribute(
      'data-status',
      'degraded',
    );
  });
});

describe('DomainCard — forbidden branch', () => {
  it.each([
    ['gap', 'PERMISSION_DENIED'],
    ['wms', 'TENANT_FORBIDDEN'],
    ['scm', 'PERMISSION_DENIED'],
    ['erp', 'TENANT_FORBIDDEN'],
  ] as const)(
    '%s forbidden/%s → placeholder + reason, NO hint',
    (domain, reason) => {
      const card: Card = { domain, status: 'forbidden', reason };
      render(<DomainCard card={card} overviewForRetry={envelopeFor(card)} />, {
        wrapper: wrapper(),
      });
      expect(
        screen.getByTestId(`operator-overview-card-${domain}-forbidden`),
      ).toBeInTheDocument();
      expect(
        screen.getByTestId(
          `operator-overview-card-${domain}-forbidden-reason`,
        ),
      ).toHaveTextContent(reason);
      // Finance hint only appears on finance × MISSING_PREREQUISITE.
      expect(
        screen.queryByTestId('operator-overview-card-finance-missing-hint'),
      ).not.toBeInTheDocument();
    },
  );

  it('finance forbidden/MISSING_PREREQUISITE → actionable hint surfaces (§ 2.4.9.1)', () => {
    const card: Card = {
      domain: 'finance',
      status: 'forbidden',
      reason: 'MISSING_PREREQUISITE',
    };
    render(<DomainCard card={card} overviewForRetry={envelopeFor(card)} />, {
      wrapper: wrapper(),
    });
    expect(
      screen.getByTestId('operator-overview-card-finance-forbidden'),
    ).toBeInTheDocument();
    expect(
      screen.getByTestId(
        'operator-overview-card-finance-forbidden-reason',
      ),
    ).toHaveTextContent('MISSING_PREREQUISITE');
    expect(
      screen.getByTestId('operator-overview-card-finance-missing-hint'),
    ).toBeInTheDocument();
  });

  it('finance forbidden/TENANT_FORBIDDEN → NO MISSING_PREREQUISITE hint', () => {
    const card: Card = {
      domain: 'finance',
      status: 'forbidden',
      reason: 'TENANT_FORBIDDEN',
    };
    render(<DomainCard card={card} overviewForRetry={envelopeFor(card)} />, {
      wrapper: wrapper(),
    });
    expect(
      screen.queryByTestId('operator-overview-card-finance-missing-hint'),
    ).not.toBeInTheDocument();
  });
});
