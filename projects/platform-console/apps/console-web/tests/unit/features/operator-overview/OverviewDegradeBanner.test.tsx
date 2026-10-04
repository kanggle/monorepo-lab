import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import type { ReactNode } from 'react';
import {
  OverviewDegradeBanner,
  isAllDown,
  isAllForbidden,
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

// TASK-PC-FE-307 — every leg refused (wrong operator/tenant). The finance
// leg's only forbidden reason without a call is MISSING_PREREQUISITE, so it
// rides along exactly as it does in a real all-refused envelope.
const ALL_FORBIDDEN: Card[] = [
  { domain: 'iam', status: 'forbidden', reason: 'PERMISSION_DENIED' },
  { domain: 'wms', status: 'forbidden', reason: 'TENANT_FORBIDDEN' },
  { domain: 'scm', status: 'forbidden', reason: 'TENANT_FORBIDDEN' },
  { domain: 'finance', status: 'forbidden', reason: 'MISSING_PREREQUISITE' },
  { domain: 'erp', status: 'forbidden', reason: 'PERMISSION_DENIED' },
  { domain: 'ecommerce', status: 'forbidden', reason: 'TENANT_FORBIDDEN' },
];

const ONLY_DEGRADED: Card[] = ALL_FORBIDDEN.map((c) => ({
  domain: c.domain,
  status: 'degraded',
  reason: 'TIMEOUT',
})) as Card[];

describe('isAllForbidden helper (TASK-PC-FE-307)', () => {
  it('true only when every card is forbidden', () => {
    expect(isAllForbidden(ALL_FORBIDDEN)).toBe(true);
    expect(isAllForbidden(ALL_DEGRADED)).toBe(false);
    expect(isAllForbidden(ONLY_DEGRADED)).toBe(false);
    expect(isAllForbidden(MIXED)).toBe(false);
    expect(isAllForbidden([])).toBe(false);
  });
});

describe('OverviewDegradeBanner — all forbidden is not an outage (TASK-PC-FE-307)', () => {
  it('AC-1: all forbidden → permission banner, no outage copy, no retry', () => {
    render(<OverviewDegradeBanner initial={envelope(ALL_FORBIDDEN)} />, {
      wrapper: wrapper(),
    });
    const banner = screen.getByTestId('operator-overview-all-forbidden');
    expect(banner).toHaveTextContent('이 계정과 테넌트로 볼 수 있는 도메인 개요가 없습니다.');
    expect(banner.textContent).not.toContain('일시적으로 불러올 수 없습니다');
    expect(banner.textContent).not.toContain('다시 시도');
    expect(
      screen.queryByTestId('operator-overview-all-degraded'),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByTestId('operator-overview-retry-banner'),
    ).not.toBeInTheDocument();
  });

  it('AC-2: all degraded → the outage banner with retry, unchanged', () => {
    render(<OverviewDegradeBanner initial={envelope(ONLY_DEGRADED)} />, {
      wrapper: wrapper(),
    });
    expect(
      screen.getByTestId('operator-overview-all-degraded'),
    ).toHaveTextContent('일시적으로 불러올 수 없습니다');
    expect(
      screen.getByTestId('operator-overview-retry-banner'),
    ).toBeInTheDocument();
    expect(
      screen.queryByTestId('operator-overview-all-forbidden'),
    ).not.toBeInTheDocument();
  });

  it('AC-2: forbidden mixed with degraded → still the outage banner (retry can change it)', () => {
    render(<OverviewDegradeBanner initial={envelope(ALL_DEGRADED)} />, {
      wrapper: wrapper(),
    });
    expect(
      screen.getByTestId('operator-overview-all-degraded'),
    ).toBeInTheDocument();
    expect(
      screen.queryByTestId('operator-overview-all-forbidden'),
    ).not.toBeInTheDocument();
  });

  it('AC-2: one ok card → no banner of either kind', () => {
    const oneOk = [
      { domain: 'iam', status: 'ok', data: {} },
      ...ALL_FORBIDDEN.slice(1),
    ] as Card[];
    const { container } = render(
      <OverviewDegradeBanner initial={envelope(oneOk)} />,
      { wrapper: wrapper() },
    );
    expect(container.firstChild).toBeNull();
  });
});

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
