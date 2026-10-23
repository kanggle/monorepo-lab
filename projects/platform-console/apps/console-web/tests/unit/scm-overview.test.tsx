import { describe, it, expect } from 'vitest';
import { render, screen, within } from '@testing-library/react';
import { ScmOverview } from '@/features/scm-ops';
import type { ScmOverviewState } from '@/features/scm-ops';
import type { PerformanceCell } from '@/shared/api/performance-metrics';
import { runAxe } from '../a11y/axe-helper';

/**
 * TASK-PC-FE-167 — `ScmOverview` presentation. Per-area count tiles (발주/재고
 * 스냅샷) are read-only stat tiles (NOT nav links — single-route ops screen), a
 * PO-status distribution, a recent-PO glance, and the REQUIRED S5 warning when
 * inventory-visibility data is shown (§ 2.4.6).
 */

const S5 = 'Not for procurement decisions (S5)';

/** `GET /po/metrics` (TASK-MONO-810) — two currencies, so the per-currency
 *  keys are exercised (one card each, never added together). */
const PERF_OK: PerformanceCell = {
  status: 'ok',
  value: {
    period: 'month',
    timezone: 'Asia/Seoul',
    current: { from: '2026-10-01T00:00:00+09:00', to: '2026-10-24T14:05:00+09:00' },
    previous: { from: '2026-09-01T00:00:00+09:00', to: '2026-09-24T14:05:00+09:00' },
    metrics: [
      { key: 'poCount', unit: 'count', currency: null, current: '7', previous: '5' },
      { key: 'poAmount:KRW', unit: 'money', currency: 'KRW', current: '1250000', previous: '1000000' },
      { key: 'poAmount:USD', unit: 'money', currency: 'USD', current: '123456', previous: '0' },
      { key: 'receivedCount', unit: 'count', currency: null, current: '3', previous: '3' },
    ],
    series: [
      {
        date: '2026-10-01',
        values: { poCount: '2', 'poAmount:KRW': '250000', 'poAmount:USD': '0', receivedCount: '1' },
      },
      {
        date: '2026-10-02',
        values: { poCount: '0', 'poAmount:KRW': '0', 'poAmount:USD': '123456', receivedCount: '0' },
      },
    ],
  },
};

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
  period: 'month',
  performance: PERF_OK,
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

describe('ScmOverview — «실적» strip (TASK-MONO-810)', () => {
  it('is the first block of the overview, before the tiles', () => {
    render(<ScmOverview state={baseState()} />);
    const overview = screen.getByTestId('scm-overview');
    const strip = screen.getByTestId('scm-performance');
    expect(overview.firstElementChild).toBe(strip);
    expect(within(strip).getByRole('heading', { name: '실적' })).toBeInTheDocument();
  });

  it('names every card, including one card per currency (never summed across currencies)', () => {
    render(<ScmOverview state={baseState()} />);
    expect(within(screen.getByTestId('scm-performance-kpi-poCount')).getByText('발주 건수')).toBeInTheDocument();
    expect(
      within(screen.getByTestId('scm-performance-kpi-poAmount:KRW')).getByText('발주 금액 (KRW)'),
    ).toBeInTheDocument();
    expect(
      within(screen.getByTestId('scm-performance-kpi-poAmount:USD')).getByText('발주 금액 (USD)'),
    ).toBeInTheDocument();
    expect(within(screen.getByTestId('scm-performance-kpi-receivedCount')).getByText('입고 완료')).toBeInTheDocument();
    expect(screen.getByTestId('scm-performance-kpi-poAmount:KRW-value')).toHaveTextContent('₩1,250,000');
    expect(screen.getByTestId('scm-performance-kpi-poAmount:USD-value')).toHaveTextContent('1,234.56');
    expect(screen.getByTestId('scm-performance-kpi-poCount-value')).toHaveTextContent('7');
    // The trend chart draws 발주 건수.
    expect(screen.getByTestId('scm-performance-trend')).toHaveTextContent('발주 건수 · 일별');
    expect(screen.getByTestId('scm-performance-scope')).toHaveTextContent('입고된 시각 기준');
  });

  it('발주 건수 / every 발주 금액 card opens the list filtered to the response window', () => {
    render(<ScmOverview state={baseState()} />);
    const want =
      '/scm/procurement?submittedFrom=2026-10-01T00%3A00%3A00%2B09%3A00&submittedTo=2026-10-24T14%3A05%3A00%2B09%3A00&excludeStatus=CANCELED';
    for (const key of ['poCount', 'poAmount:KRW', 'poAmount:USD']) {
      const link = screen.getByTestId(`scm-performance-kpi-${key}-link`);
      expect(link.tagName).toBe('A');
      expect(link, key).toHaveAttribute('href', want);
      // The encoded `+` survives: decoding the href gives the wire bound back.
      const url = new URL(link.getAttribute('href')!, 'http://console.local');
      expect(url.searchParams.get('submittedFrom')).toBe('2026-10-01T00:00:00+09:00');
      expect(url.searchParams.get('submittedTo')).toBe('2026-10-24T14:05:00+09:00');
      // The metric leaves CANCELED out, so the list it opens does too.
      expect(url.searchParams.get('excludeStatus')).toBe('CANCELED');
    }
  });

  it('입고 완료 opens the RECEIVED list — no submit window on it (receipt time ≠ submit time)', () => {
    render(<ScmOverview state={baseState()} />);
    const link = screen.getByTestId('scm-performance-kpi-receivedCount-link');
    expect(link).toHaveAttribute('href', '/scm/procurement?status=RECEIVED');
    expect(link.getAttribute('href')).not.toContain('submitted');
    expect(link.getAttribute('href')).not.toContain('excludeStatus');
  });

  it('card links have accessible names (label + value + destination)', () => {
    render(<ScmOverview state={baseState()} />);
    expect(screen.getByRole('link', { name: /^발주 건수 7.*목록에서 보기$/ })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /^발주 금액 \(USD\).*목록에서 보기$/ })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /^입고 완료 3.*목록에서 보기$/ })).toBeInTheDocument();
  });

  it('period switch links to /scm?period=…, the selected one marked', () => {
    render(<ScmOverview state={baseState({ period: 'week' })} />);
    expect(screen.getByTestId('scm-performance-period-week')).toHaveAttribute('href', '/scm?period=week');
    expect(screen.getByTestId('scm-performance-period-week')).toHaveAttribute('aria-current', 'page');
    expect(screen.getByTestId('scm-performance-period-month')).toHaveAttribute('href', '/scm?period=month');
  });

  it('a failed strip says so in words, shows no number and no card link — the tiles stay', () => {
    render(<ScmOverview state={baseState({ performance: { status: 'degraded', value: null } })} />);
    expect(screen.getByTestId('scm-performance-degraded')).toHaveTextContent('실적을 불러올 수 없습니다');
    expect(screen.getByTestId('scm-performance').textContent).not.toMatch(/\d/);
    expect(screen.queryByTestId('scm-performance-kpi-poCount-link')).toBeNull();
    expect(screen.getByTestId('scm-po-count')).toHaveTextContent('12');
  });

  it('forbidden strip → «권한이 없습니다», the rest renders', () => {
    render(<ScmOverview state={baseState({ performance: { status: 'forbidden', value: null } })} />);
    expect(screen.getByTestId('scm-performance-forbidden')).toHaveTextContent('실적을 볼 권한이 없습니다');
    expect(screen.getByTestId('scm-po-status-CONFIRMED')).toHaveTextContent('4');
  });

  it('is axe-clean with linked cards', async () => {
    const { container } = render(<ScmOverview state={baseState()} />);
    expect(await runAxe(container)).toEqual([]);
  });
});
