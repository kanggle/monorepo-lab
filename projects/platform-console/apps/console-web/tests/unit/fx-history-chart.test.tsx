import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import {
  FX_LINE_HEIGHT,
  FX_LINE_WIDTH,
  compareDecimalStrings,
  fxHistoryLine,
} from '@/features/ledger-ops/lib/fx-history-line';
import { FxRateHistoryChart } from '@/features/ledger-ops/components/FxRateHistoryChart';
import { FxRateHistoryTable } from '@/features/ledger-ops/components/FxRateHistoryTable';
import type { FxRateHistoryQuote } from '@/features/ledger-ops';

/**
 * TASK-PC-FE-359 — FX 환율 추이 선 above the history table.
 *   - geometry: newest-first input → chronological line; 1 point; all equal;
 *     unreadable rates skipped; none readable → no chart;
 *   - min / max chosen by EXACT string comparison, printed as the ORIGINAL
 *     strings (Failure Scenario 1 — a float never leaks into text).
 */

const q = (rate: string, i: number): FxRateHistoryQuote => ({
  rate,
  asOf: `2026-10-${String(10 + i).padStart(2, '0')}T09:00:00Z`,
  fetchedAt: `2026-10-${String(10 + i).padStart(2, '0')}T09:00:05Z`,
  source: 'PROVIDER_A',
});

describe('fxHistoryLine — geometry', () => {
  it('reverses the producer\'s newest-first order: the newest quote is the right-most point', () => {
    const newestFirst = [q('1300.00000000', 3), q('1290.00000000', 2), q('1310.00000000', 1)];
    const geo = fxHistoryLine(newestFirst)!;
    expect(geo.points.map((p) => p.quote.rate)).toEqual([
      '1310.00000000',
      '1290.00000000',
      '1300.00000000',
    ]);
    const xs = geo.points.map((p) => p.x);
    expect(xs[0]).toBeLessThan(xs[1]);
    expect(xs[1]).toBeLessThan(xs[2]);
    expect(geo.latest.rate).toBe('1300.00000000');
    // Higher rate → smaller y (SVG y grows downward); all inside the box.
    const [oldest, middle, newest] = geo.points;
    expect(oldest.y).toBeLessThan(newest.y);
    expect(newest.y).toBeLessThan(middle.y);
    for (const p of geo.points) {
      expect(p.y).toBeGreaterThanOrEqual(0);
      expect(p.y).toBeLessThanOrEqual(FX_LINE_HEIGHT);
      expect(p.x).toBeGreaterThanOrEqual(0);
      expect(p.x).toBeLessThanOrEqual(FX_LINE_WIDTH);
    }
  });

  it('one point → centred single point', () => {
    const geo = fxHistoryLine([q('9.10000000', 0)])!;
    expect(geo.points).toHaveLength(1);
    expect(geo.points[0].x).toBe(FX_LINE_WIDTH / 2);
    expect(geo.points[0].y).toBe(FX_LINE_HEIGHT / 2);
  });

  it('all values equal → a flat line in the middle (no divide by zero)', () => {
    const geo = fxHistoryLine([q('1300.0', 2), q('1300.00000000', 1), q('1300', 0)])!;
    expect(geo.flat).toBe(true);
    expect(geo.points.every((p) => p.y === FX_LINE_HEIGHT / 2)).toBe(true);
    expect(geo.points.every((p) => Number.isFinite(p.x))).toBe(true);
  });

  it('an unreadable rate is skipped and counted; the rest still draw', () => {
    const geo = fxHistoryLine([q('1300.5', 2), q('n/a', 1), q('1299.5', 0)])!;
    expect(geo.points).toHaveLength(2);
    expect(geo.skipped).toBe(1);
  });

  it('every rate unreadable / empty series → null (table only)', () => {
    expect(fxHistoryLine([q('', 1), q('abc', 0)])).toBeNull();
    expect(fxHistoryLine([])).toBeNull();
  });

  it('min / max are the ORIGINAL strings, chosen by exact comparison', () => {
    const geo = fxHistoryLine([q('1300.10000000', 2), q('1300.09999999', 1), q('1300.1', 0)])!;
    expect(geo.min!.rate).toBe('1300.09999999');
    expect(geo.max!.rate).toBe('1300.10000000');
  });
});

describe('compareDecimalStrings — exact, no float', () => {
  it('orders by sign, integer length, digits, then fraction', () => {
    expect(compareDecimalStrings('1300.5', '1298.50000000')).toBe(1);
    expect(compareDecimalStrings('99.9', '100')).toBe(-1);
    expect(compareDecimalStrings('1300.10', '1300.1')).toBe(0);
    expect(compareDecimalStrings('0.00076923', '0.00076924')).toBe(-1);
    expect(compareDecimalStrings('-2', '1')).toBe(-1);
    expect(compareDecimalStrings('-2', '-1')).toBe(-1);
    expect(compareDecimalStrings('-0', '0')).toBe(0);
    // Beyond double precision: a float would call these equal.
    expect(compareDecimalStrings('9007199254740993', '9007199254740992')).toBe(1);
  });

  it('non-plain decimals → null (drawn, never labelled)', () => {
    expect(compareDecimalStrings('1.3e3', '1300')).toBeNull();
    expect(compareDecimalStrings('', '1')).toBeNull();
    expect(compareDecimalStrings('.', '1')).toBeNull();
  });
});

describe('FxRateHistoryChart — render', () => {
  it('labels latest / min / max with the producer strings verbatim (no rounding)', () => {
    render(
      <FxRateHistoryChart
        quotes={[q('1300.00000000', 1), q('1298.50000000', 0)]}
      />,
    );
    expect(screen.getByTestId('ledger-fx-history-chart-latest').textContent).toBe(
      '최신 1300.00000000',
    );
    expect(screen.getByTestId('ledger-fx-history-chart-min').textContent).toBe(
      '최저 1298.50000000',
    );
    expect(screen.getByTestId('ledger-fx-history-chart-max').textContent).toBe(
      '최고 1300.00000000',
    );
    expect(screen.getByTestId('ledger-fx-history-chart-scope').textContent).toBe(
      '받아 온 최근 2건 · 왼쪽이 오래된 값, 오른쪽이 최신 값',
    );
    const svg = screen.getByTestId('ledger-fx-history-chart-svg');
    expect(svg.getAttribute('aria-hidden')).toBe('true');
    expect(screen.getByTestId('ledger-fx-history-chart-line')).toBeInTheDocument();
    // No float rendering of the rates anywhere in the chart's text.
    const text = screen.getByTestId('ledger-fx-history-chart').textContent ?? '';
    expect(text).not.toMatch(/1300(?!\.00000000)/);
    expect(text).not.toContain('1298.5 ');
  });

  it('one quote → a dot, no line', () => {
    render(<FxRateHistoryChart quotes={[q('9.10000000', 0)]} />);
    expect(screen.queryByTestId('ledger-fx-history-chart-line')).toBeNull();
    expect(screen.getByTestId('ledger-fx-history-chart-latest-dot')).toBeInTheDocument();
  });

  it('says how many quotes were left out', () => {
    render(<FxRateHistoryChart quotes={[q('1300', 2), q('bad', 1), q('1299', 0)]} />);
    expect(screen.getByTestId('ledger-fx-history-chart-skipped').textContent).toBe(
      '숫자로 읽을 수 없는 1건은 그래프에서 뺐습니다 (아래 표에는 있습니다).',
    );
  });

  it('every rate unreadable → no chart, the table still renders every row', () => {
    render(
      <FxRateHistoryTable
        data={{ base: 'KRW', foreign: 'USD', quotes: [q('x', 1), q('', 0)] }}
      />,
    );
    expect(screen.queryByTestId('ledger-fx-history-chart')).toBeNull();
    expect(screen.getByTestId('ledger-fx-history-row-0')).toBeInTheDocument();
    expect(screen.getByTestId('ledger-fx-history-row-1')).toBeInTheDocument();
  });

  it('the table renders the chart above its rows when quotes are drawable', () => {
    render(
      <FxRateHistoryTable
        data={{ base: 'KRW', foreign: 'USD', quotes: [q('1300.00000000', 0)] }}
      />,
    );
    const chart = screen.getByTestId('ledger-fx-history-chart');
    const table = screen.getByTestId('ledger-fx-history-table');
    expect(
      chart.compareDocumentPosition(table) & Node.DOCUMENT_POSITION_FOLLOWING,
    ).toBeTruthy();
  });
});
