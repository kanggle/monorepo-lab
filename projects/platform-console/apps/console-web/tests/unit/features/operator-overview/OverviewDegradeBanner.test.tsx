import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import type { ReactNode } from 'react';
import {
  OverviewDegradeBanner,
  isAllDown,
} from '@/features/operator-overview';
import type {
  OperatorOverview,
  Card,
} from '@/features/operator-overview';

/**
 * `<OverviewDegradeBanner>` (TASK-PC-FE-011) — renders ONLY when all
 * 6 cards are non-`ok`; absent when at least 1 card is `ok`.
 * Server component (no `'use client'`); the embedded `<RetryButton>`
 * is the only client surface.
 */

function wrapper() {
  const qc = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={qc}>{children}</QueryClientProvider>
  );
}

function envelope(cards: Card[]): OperatorOverview {
  return { asOf: '2026-05-20T10:30:00Z', cards: cards as OperatorOverview['cards'] };
}

const ALL_DEGRADED: Card[] = [
  { domain: 'iam', status: 'degraded', reason: 'DOWNSTREAM_ERROR' },
  { domain: 'wms', status: 'degraded', reason: 'TIMEOUT' },
  { domain: 'scm', status: 'degraded', reason: 'CIRCUIT_OPEN' },
  { domain: 'finance', status: 'forbidden', reason: 'MISSING_PREREQUISITE' },
  { domain: 'erp', status: 'forbidden', reason: 'TENANT_FORBIDDEN' },
  { domain: 'ecommerce', status: 'degraded', reason: 'CIRCUIT_OPEN' },
];

const MIXED: Card[] = [
  { domain: 'iam', status: 'ok', data: { totalElements: 1 } },
  { domain: 'wms', status: 'degraded', reason: 'TIMEOUT' },
  { domain: 'scm', status: 'degraded', reason: 'CIRCUIT_OPEN' },
  { domain: 'finance', status: 'forbidden', reason: 'MISSING_PREREQUISITE' },
  { domain: 'erp', status: 'forbidden', reason: 'TENANT_FORBIDDEN' },
  { domain: 'ecommerce', status: 'degraded', reason: 'CIRCUIT_OPEN' },
];

describe('isAllDown helper', () => {
  it('returns true when every card is non-ok (mix of degraded + forbidden)', () => {
    expect(isAllDown(ALL_DEGRADED)).toBe(true);
  });

  it('returns false when at least one card is ok', () => {
    expect(isAllDown(MIXED)).toBe(false);
  });

  it('returns false on an empty card list (defensive)', () => {
    expect(isAllDown([])).toBe(false);
  });
});

describe('OverviewDegradeBanner — rendering', () => {
  it('renders the all-down banner when every card is non-ok', () => {
    render(<OverviewDegradeBanner initial={envelope(ALL_DEGRADED)} />, {
      wrapper: wrapper(),
    });
    expect(
      screen.getByTestId('operator-overview-all-degraded'),
    ).toBeInTheDocument();
    // includes the embedded retry button (client component child).
    expect(
      screen.getByTestId('operator-overview-retry-banner'),
    ).toBeInTheDocument();
  });

  it('returns null when at least one card is ok', () => {
    const { container } = render(
      <OverviewDegradeBanner initial={envelope(MIXED)} />,
      { wrapper: wrapper() },
    );
    expect(
      screen.queryByTestId('operator-overview-all-degraded'),
    ).not.toBeInTheDocument();
    expect(container.firstChild).toBeNull();
  });

  it('returns null on an all-ok envelope', () => {
    const allOk: Card[] = [
      { domain: 'iam', status: 'ok', data: {} },
      { domain: 'wms', status: 'ok', data: {} },
      { domain: 'scm', status: 'ok', data: {} },
      { domain: 'finance', status: 'ok', data: {} },
      { domain: 'erp', status: 'ok', data: {} },
      { domain: 'ecommerce', status: 'ok', data: { totalElements: 0 } },
    ];
    render(<OverviewDegradeBanner initial={envelope(allOk)} />, {
      wrapper: wrapper(),
    });
    expect(
      screen.queryByTestId('operator-overview-all-degraded'),
    ).not.toBeInTheDocument();
  });
});
