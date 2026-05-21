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
 * Finance card render — Option (a) activation paths (TASK-PC-FE-014 /
 * `console-integration-contract.md § 2.4.9.1 Implementation guidance —
 * Option (a) activation`).
 *
 * Both paths are first-class:
 *   - header-present + finance leg ok → balance available state + currency
 *     (no numeric coercion of the amount minor-units string — F5 money
 *     discipline regression guard);
 *   - header-absent / un-provisioned operator + finance leg
 *     forbidden / MISSING_PREREQUISITE → actionable hint surfaces
 *     ("Configure a default finance account" — the existing FE-011 hint
 *     remains the user-facing affordance until the operator-profile
 *     setter ships as a future task).
 *
 * STRENGTHEN-ONLY: this is additive coverage of the FE-014 activation
 * chain's wire shapes; the existing `DomainCard.test.tsx` per-status
 * branch coverage remains untouched.
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
  return {
    asOf: '2026-05-21T01:30:00Z',
    cards: [card, card, card, card, card],
  };
}

describe('finance card — Option (a) activation render paths', () => {
  it('header-present + finance ok → balance status + currency (no amount coercion — F5)', () => {
    // The BFF `FinanceBalanceReadAdapter.readBalances(...)` returns the
    // finance leg payload verbatim; the existing FE-011 card render
    // surfaces only the "available" status + currency, never the raw
    // amount as a number (F5 money discipline).
    const card: Card = {
      domain: 'finance',
      status: 'ok',
      data: { balance: { amount: '9876500', currency: 'KRW' } },
    };
    render(<DomainCard card={card} overviewForRetry={envelopeFor(card)} />, {
      wrapper: wrapper(),
    });
    expect(
      screen.getByTestId('operator-overview-card-finance'),
    ).toHaveAttribute('data-status', 'ok');
    expect(
      screen.getByTestId('operator-overview-card-finance-status'),
    ).toHaveTextContent('잔액 조회 가능');
    expect(
      screen.getByTestId('operator-overview-card-finance-currency'),
    ).toHaveTextContent('KRW');
    // The raw minor-units string MUST NOT appear as a coerced number
    // anywhere in the rendered DOM (F5 regression guard preserved
    // across the FE-014 activation).
    expect(screen.queryByText('9,876,500')).not.toBeInTheDocument();
    expect(screen.queryByText('9876500')).not.toBeInTheDocument();
    // The MISSING_PREREQUISITE hint MUST NOT appear on the ok path.
    expect(
      screen.queryByTestId('operator-overview-card-finance-missing-hint'),
    ).not.toBeInTheDocument();
  });

  it('header-present + alternate currency → currency surfaced verbatim (no FX assumption)', () => {
    const card: Card = {
      domain: 'finance',
      status: 'ok',
      data: { balance: { amount: '1200', currency: 'USD' } },
    };
    render(<DomainCard card={card} overviewForRetry={envelopeFor(card)} />, {
      wrapper: wrapper(),
    });
    expect(
      screen.getByTestId('operator-overview-card-finance-currency'),
    ).toHaveTextContent('USD');
  });

  it('header-absent (un-provisioned operator) → MISSING_PREREQUISITE + actionable hint (AC-2 regression)', () => {
    // This is the exact payload the BFF emits when
    // `X-Finance-Default-Account-Id` is absent / blank: the use-case
    // gate emits forbidden / MISSING_PREREQUISITE without firing any
    // outbound HTTP. The card renders the existing FE-011 placeholder +
    // hint — no UI change needed by FE-014.
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
      screen.getByTestId('operator-overview-card-finance-forbidden-reason'),
    ).toHaveTextContent('MISSING_PREREQUISITE');
    expect(
      screen.getByTestId('operator-overview-card-finance-missing-hint'),
    ).toBeInTheDocument();
  });

  it('header-present + finance leg degraded (stale id → 404 → DOWNSTREAM_ERROR) → degraded UI (honest)', () => {
    // Edge case from § 2.4.9.1 Option (a) activation honest failure modes:
    // a stale account id surfaces honestly as a degraded leg, never a
    // fabricated ok. This verifies the card renders the degraded shape,
    // not the ok shape, when the header WAS forwarded but the finance
    // leg failed.
    const card: Card = {
      domain: 'finance',
      status: 'degraded',
      reason: 'DOWNSTREAM_ERROR',
    };
    render(<DomainCard card={card} overviewForRetry={envelopeFor(card)} />, {
      wrapper: wrapper(),
    });
    expect(
      screen.getByTestId('operator-overview-card-finance-degraded'),
    ).toBeInTheDocument();
    expect(
      screen.getByTestId(
        'operator-overview-card-finance-degraded-reason',
      ),
    ).toHaveTextContent('DOWNSTREAM_ERROR');
    // The MISSING_PREREQUISITE hint MUST NOT appear on the degraded path
    // (it is finance × forbidden × MISSING_PREREQUISITE-specific).
    expect(
      screen.queryByTestId('operator-overview-card-finance-missing-hint'),
    ).not.toBeInTheDocument();
  });
});
