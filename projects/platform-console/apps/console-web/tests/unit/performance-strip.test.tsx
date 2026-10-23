import { describe, expect, it } from 'vitest';
import { render, screen, within } from '@testing-library/react';
import { PerformanceStrip } from '@/shared/ui/PerformanceStrip';
import type { PerformanceCell } from '@/shared/api/performance-metrics';

/** TASK-MONO-806 AC-4 — the shared «실적» strip. */

const HREFS = { week: '/x?period=week', month: '/x?period=month' } as const;
const LABELS = { amount: '매출', items: '판매 수량' };

const OK: PerformanceCell = {
  status: 'ok',
  value: {
    period: 'month',
    timezone: 'Asia/Seoul',
    current: { from: '2026-10-01T00:00:00+09:00', to: '2026-10-23T14:05:00+09:00' },
    previous: { from: '2026-09-01T00:00:00+09:00', to: '2026-09-23T14:05:00+09:00' },
    metrics: [
      { key: 'amount', unit: 'money', currency: 'KRW', current: '12340000', previous: '11020000' },
      { key: 'items', unit: 'count', currency: null, current: '0', previous: '0' },
    ],
    series: [
      { date: '2026-10-01', values: { amount: '410000', items: '0' } },
      { date: '2026-10-02', values: { amount: '0', items: '0' } },
    ],
  },
};

describe('PerformanceStrip', () => {
  it('renders one KPI card per metric with value, change and previous', () => {
    render(<PerformanceStrip cell={OK} selected="month" periodHrefs={HREFS} labels={LABELS} />);

    const amount = screen.getByTestId('performance-strip-kpi-amount');
    expect(within(amount).getByText('매출')).toBeInTheDocument();
    expect(screen.getByTestId('performance-strip-kpi-amount-value')).toHaveTextContent('₩12,340,000');
    expect(screen.getByTestId('performance-strip-kpi-amount-delta')).toHaveTextContent('▲ 12.0%');
    expect(within(amount).getByText('직전 ₩11,020,000')).toBeInTheDocument();
    expect(within(amount).getByText('직전 같은 기간 대비 12.0% 증가')).toBeInTheDocument();

    // 0 → 0: no percentage, said in words for a screen reader.
    expect(screen.getByTestId('performance-strip-kpi-items-delta')).toHaveTextContent('—');
  });

  it('states the window, the zone and what it is compared with', () => {
    render(<PerformanceStrip cell={OK} selected="month" periodHrefs={HREFS} labels={LABELS} />);
    const w = screen.getByTestId('performance-strip-window');
    expect(w).toHaveTextContent('이번 달 · 10월 1일 00:00 – 10월 23일 14:05');
    expect(w).toHaveTextContent('한국 시간 기준');
    expect(w).toHaveTextContent('9월 1일 00:00 – 9월 23일 14:05');
  });

  it('draws the first metric by default, or the chosen one', () => {
    const { unmount } = render(
      <PerformanceStrip cell={OK} selected="month" periodHrefs={HREFS} labels={LABELS} />,
    );
    expect(screen.getByTestId('performance-strip-trend')).toHaveTextContent('매출 · 일별');
    expect(screen.getByTestId('performance-strip-trend-bar-2026-10-01')).toHaveStyle({ height: '100%' });
    expect(screen.getByTestId('performance-strip-trend-bar-2026-10-02')).toHaveStyle({ height: '0%' });
    expect(screen.getByText('10/1: ₩410,000')).toBeInTheDocument();
    unmount();

    render(
      <PerformanceStrip cell={OK} selected="month" periodHrefs={HREFS} labels={LABELS} chartKey="items" />,
    );
    expect(screen.getByTestId('performance-strip-trend')).toHaveTextContent('판매 수량 · 일별');
  });

  it('period switch: two links, the selected one marked', () => {
    render(<PerformanceStrip cell={OK} selected="month" periodHrefs={HREFS} labels={LABELS} />);
    const week = screen.getByTestId('performance-strip-period-week');
    const month = screen.getByTestId('performance-strip-period-month');
    expect(week).toHaveAttribute('href', '/x?period=week');
    expect(week).not.toHaveAttribute('aria-current');
    expect(month).toHaveAttribute('aria-current', 'page');
  });

  it('a failed read says so in words and shows no number', () => {
    render(
      <PerformanceStrip cell={{ status: 'degraded', value: null }} selected="week" periodHrefs={HREFS} labels={LABELS} />,
    );
    const note = screen.getByTestId('performance-strip-degraded');
    expect(note).toHaveTextContent('실적을 불러올 수 없습니다');
    expect(note).toHaveAttribute('role', 'status');
    expect(screen.getByTestId('performance-strip').textContent).not.toMatch(/\d/);
    // the switch still works, so the operator can try the other period
    expect(screen.getByTestId('performance-strip-period-week')).toHaveAttribute('aria-current', 'page');
  });

  it('TASK-MONO-810 — no cardHref (every strip before it): no card is a link, markup unchanged', () => {
    render(<PerformanceStrip cell={OK} selected="month" periodHrefs={HREFS} labels={LABELS} />);
    const strip = screen.getByTestId('performance-strip');
    // Only the two period links exist.
    expect(within(strip).getAllByRole('link')).toHaveLength(2);
    expect(screen.queryByTestId('performance-strip-kpi-amount-link')).toBeNull();
    expect(screen.getByTestId('performance-strip-kpi-amount')).toHaveClass('px-4', 'py-3');
  });

  it('TASK-MONO-810 — cardHref makes the chosen cards links (with the response at hand); null → plain card', () => {
    const seen: string[] = [];
    render(
      <PerformanceStrip
        cell={OK}
        selected="month"
        periodHrefs={HREFS}
        labels={LABELS}
        cardHref={(metric, data) => {
          seen.push(`${metric.key}@${data.current.from}`);
          return metric.key === 'amount' ? `/list?from=${encodeURIComponent(data.current.from)}` : null;
        }}
      />,
    );
    expect(seen).toEqual(['amount@2026-10-01T00:00:00+09:00', 'items@2026-10-01T00:00:00+09:00']);
    const link = screen.getByTestId('performance-strip-kpi-amount-link');
    expect(link.tagName).toBe('A');
    expect(link).toHaveAttribute('href', '/list?from=2026-10-01T00%3A00%3A00%2B09%3A00');
    expect(screen.getByRole('link', { name: /^매출 ₩12,340,000.*목록에서 보기$/ })).toBe(link);
    // The value testid still resolves inside the link.
    expect(within(link).getByTestId('performance-strip-kpi-amount-value')).toHaveTextContent('₩12,340,000');
    expect(screen.queryByTestId('performance-strip-kpi-items-link')).toBeNull();
  });

  it('forbidden and empty', () => {
    const { unmount } = render(
      <PerformanceStrip cell={{ status: 'forbidden', value: null }} selected="week" periodHrefs={HREFS} labels={LABELS} />,
    );
    expect(screen.getByTestId('performance-strip-forbidden')).toHaveTextContent('실적을 볼 권한이 없습니다');
    unmount();

    const empty: PerformanceCell = { status: 'ok', value: { ...OK.value!, metrics: [], series: [] } };
    render(<PerformanceStrip cell={empty} selected="month" periodHrefs={HREFS} labels={LABELS} />);
    expect(screen.getByTestId('performance-strip-empty')).toHaveTextContent('표시할 실적이 없습니다');
  });
});
