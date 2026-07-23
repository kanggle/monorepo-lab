import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { ScmOverview } from '@/features/scm-ops';
import type { ScmOverviewState } from '@/features/scm-ops';

/**
 * TASK-PC-FE-167 — `ScmOverview` presentation. Per-area count tiles (발주/재고
 * 스냅샷) are read-only stat tiles (NOT nav links — single-route ops screen), a
 * PO-status distribution, a recent-PO glance, and the REQUIRED S5 warning when
 * inventory-visibility data is shown (§ 2.4.6).
 */

const S5 = 'Not for procurement decisions (S5)';

const baseState = (over: Partial<ScmOverviewState> = {}): ScmOverviewState => ({
  notEligible: false,
  counts: [
    { key: 'po', label: '발주', count: 12, status: 'ok' },
    { key: 'inventory', label: '재고 스냅샷', count: 30, status: 'ok' },
  ],
  poStatus: [
    { status: 'CONFIRMED', count: 4, cellStatus: 'ok' },
    { status: 'CANCELED', count: 1, cellStatus: 'ok' },
  ],
  recentPos: [
    {
      id: 'po1',
      poNumber: 'PO-1',
      status: 'CONFIRMED',
      totalAmount: '1000',
      currency: 'KRW',
      createdAt: '2026-07-01T00:00:00Z',
    },
  ],
  recentPosStatus: 'ok',
  s5Warning: S5,
  ...over,
});

describe('ScmOverview (TASK-PC-FE-167)', () => {
  it('notEligible → renders nothing', () => {
    const { container } = render(
      <ScmOverview state={baseState({ notEligible: true, counts: [] })} />,
    );
    expect(container).toBeEmptyDOMElement();
  });

  it('renders count tiles (not links), PO-status distribution, recent POs, and the S5 warning', () => {
    render(<ScmOverview state={baseState()} />);
    expect(screen.getByTestId('scm-po-count')).toHaveTextContent('12');
    expect(screen.getByTestId('scm-inventory-count')).toHaveTextContent('30');
    expect(screen.getByTestId('scm-po-status-CONFIRMED')).toHaveTextContent('4');
    expect(screen.getByTestId('scm-recent-pos')).toHaveTextContent('PO-1');
    // S5 obligation surfaced.
    expect(screen.getByTestId('scm-s5-warning')).toBeInTheDocument();
    // Stat tiles, NOT nav links — scoped to the count container so the
    // 바로가기 nav's 가이드 link (PC-FE-257) is not counted.
    expect(
      screen.getByTestId('scm-overview-counts').querySelector('a'),
    ).toBeNull();
  });

  it('links the overview to the SCM guide (PC-FE-257 discoverability parity)', () => {
    render(<ScmOverview state={baseState()} />);
    const link = screen.getByTestId('scm-overview-link-guide');
    expect(link.tagName).toBe('A');
    expect(link).toHaveAttribute('href', '/scm/guide');
  });

  it('no S5 warning when the inventory cell did not resolve', () => {
    render(<ScmOverview state={baseState({ s5Warning: null })} />);
    expect(screen.queryByTestId('scm-s5-warning')).toBeNull();
  });

  it('a non-ok count cell renders a placeholder + reflects its status indicator', () => {
    render(
      <ScmOverview
        state={baseState({
          counts: [
            { key: 'po', label: '발주', count: null, status: 'degraded' },
            { key: 'inventory', label: '재고 스냅샷', count: null, status: 'forbidden' },
          ],
        })}
      />,
    );
    expect(screen.getByTestId('scm-po-count-degraded')).toHaveTextContent(
      '점검 필요',
    );
    expect(screen.getByTestId('scm-inventory-count-degraded')).toHaveTextContent(
      '권한 없음',
    );
    expect(screen.queryByTestId('scm-po-count')).toBeNull();
    expect(
      screen.getByTestId('scm-po-service-status'),
    ).toHaveAttribute('data-status', 'degraded');
  });

  it('a degraded PO-status bucket renders "—", and a degraded recent panel a placeholder', () => {
    render(
      <ScmOverview
        state={baseState({
          poStatus: [
            { status: 'CONFIRMED', count: null, cellStatus: 'degraded' },
          ],
          recentPos: null,
          recentPosStatus: 'degraded',
        })}
      />,
    );
    expect(screen.getByTestId('scm-po-status-CONFIRMED')).toHaveTextContent('—');
    expect(screen.getByTestId('scm-recent-pos')).toHaveTextContent('점검 필요');
  });
});
