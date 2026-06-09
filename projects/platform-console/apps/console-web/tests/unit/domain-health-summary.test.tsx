import { describe, it, expect, afterEach } from 'vitest';
import { render, screen, cleanup } from '@testing-library/react';
import { DomainHealthSummaryCard } from '@/features/domain-health';
import type { DomainHealthState } from '@/features/domain-health';
import type { Card } from '@/features/domain-health';

/**
 * TASK-PC-FE-061 — the 개요(operator overview) "도메인 상태 요약" card.
 * Reads the same `getDomainHealthState()` shape the /dashboards/health page
 * uses; bridges to the full screen via "전체 보기 →" without turning 개요 into
 * a sidebar drill parent (home stays one-click).
 */

afterEach(cleanup);

const ok = (domain: Card['domain'], s: 'UP' | 'DOWN' | 'OUT_OF_SERVICE' | 'UNKNOWN'): Card => ({
  domain,
  status: 'ok',
  data: { status: s },
});
const degraded = (domain: Card['domain']): Card => ({
  domain,
  status: 'degraded',
  reason: 'TIMEOUT',
});

function state(cards: Card[] | null): DomainHealthState {
  return cards === null
    ? { health: null, noTenant: false, unauthorized: false, bffUnavailable: true }
    : { health: { asOf: '2026-06-09T00:00:00Z', cards }, noTenant: false, unauthorized: false, bffUnavailable: false };
}

describe('DomainHealthSummaryCard (TASK-PC-FE-061)', () => {
  it('always renders the "전체 보기 →" link to /dashboards/health', () => {
    render(<DomainHealthSummaryCard state={state([ok('iam', 'UP')])} />);
    const link = screen.getByTestId('domain-health-summary-viewall');
    expect(link).toHaveAttribute('href', '/dashboards/health');
  });

  it('all UP → 정상 N, no 주의/점검 불가, all badges healthy', () => {
    render(
      <DomainHealthSummaryCard
        state={state([
          ok('iam', 'UP'),
          ok('wms', 'UP'),
          ok('scm', 'UP'),
          ok('finance', 'UP'),
          ok('erp', 'UP'),
        ])}
      />,
    );
    const counts = screen.getByTestId('domain-health-summary-counts');
    expect(counts).toHaveTextContent('정상 5');
    expect(counts).toHaveTextContent('주의 0');
    expect(counts).toHaveTextContent('점검 불가 0');
    expect(screen.getByTestId('domain-health-summary-badge-iam')).toHaveAttribute(
      'data-tone',
      'healthy',
    );
  });

  it('classifies tones: ok+UP=healthy, ok+non-UP=attention, degraded=unknown', () => {
    render(
      <DomainHealthSummaryCard
        state={state([
          ok('iam', 'UP'),
          ok('wms', 'DOWN'),
          ok('scm', 'OUT_OF_SERVICE'),
          degraded('finance'),
          ok('erp', 'UP'),
        ])}
      />,
    );
    expect(screen.getByTestId('domain-health-summary-badge-iam')).toHaveAttribute('data-tone', 'healthy');
    expect(screen.getByTestId('domain-health-summary-badge-wms')).toHaveAttribute('data-tone', 'attention');
    expect(screen.getByTestId('domain-health-summary-badge-scm')).toHaveAttribute('data-tone', 'attention');
    expect(screen.getByTestId('domain-health-summary-badge-finance')).toHaveAttribute('data-tone', 'unknown');
    expect(screen.getByTestId('domain-health-summary-badge-erp')).toHaveAttribute('data-tone', 'healthy');

    const counts = screen.getByTestId('domain-health-summary-counts');
    expect(counts).toHaveTextContent('정상 2');
    expect(counts).toHaveTextContent('주의 2');
    expect(counts).toHaveTextContent('점검 불가 1');
  });

  it('null health (bff unavailable) → compact unavailable note, NO counts, link still present', () => {
    render(<DomainHealthSummaryCard state={state(null)} />);
    expect(screen.getByTestId('domain-health-summary-unavailable')).toBeInTheDocument();
    expect(screen.queryByTestId('domain-health-summary-counts')).toBeNull();
    // Degrade never blanks the bridge — the link still works.
    expect(screen.getByTestId('domain-health-summary-viewall')).toHaveAttribute(
      'href',
      '/dashboards/health',
    );
  });
});
