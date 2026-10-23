import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import {
  PoStatusBar,
  buildPoStatusSegments,
} from '@/features/scm-ops/components/PoStatusBar';
import {
  AlertStatusBar,
  buildAlertStatusSegments,
} from '@/features/wms-ops/components/AlertStatusBar';
import { ScmOverview } from '@/features/scm-ops';
import type { ScmOverviewState } from '@/features/scm-ops';
import { WmsOverview } from '@/features/wms-ops';
import type { WmsOverviewState } from '@/features/wms-ops';
import type { ScmPoStatusCount } from '@/features/scm-ops/api/overview-state';
import type { WmsAlertStatusCount } from '@/features/wms-ops/api/overview-state';
import { formatDecimalDisplay, formatMoneyDisplay } from '@/shared/lib/money';

/**
 * TASK-PC-FE-363 — PO-status bar (SCM overview), alert-status bar (WMS
 * overview) and the ecommerce-style money look (SCM recent POs, Finance
 * balances). Same bar rules as TASK-PC-FE-360: ratios, ANY non-ok bucket → no
 * bar, all-zero → note, numbers remain as text.
 */

const pct = (el: HTMLElement) => Number(el.getAttribute('data-pct'));

const PO_KEYS = [
  'DRAFT',
  'SUBMITTED',
  'ACKNOWLEDGED',
  'CONFIRMED',
  'PARTIALLY_RECEIVED',
  'RECEIVED',
  'SETTLED',
  'CLOSED',
  'CANCELED',
];
const po = (
  status: string,
  count: number | null,
  cellStatus: ScmPoStatusCount['cellStatus'] = 'ok',
): ScmPoStatusCount => ({ status, count, cellStatus });
const PO_ALL = (c: number) => PO_KEYS.map((k) => po(k, c));

const alert = (
  key: WmsAlertStatusCount['key'],
  count: number | null,
  cellStatus: WmsAlertStatusCount['cellStatus'] = 'ok',
): WmsAlertStatusCount => ({
  key,
  label: key === 'unacknowledged' ? '미확인' : '확인',
  count,
  cellStatus,
});

describe('① PO status bar', () => {
  it('segments follow lifecycle order with the PO-status tones', () => {
    expect(
      buildPoStatusSegments(PO_ALL(1))?.map((s) => [s.key, s.tone]),
    ).toEqual([
      ['DRAFT', 'neutral'],
      ['SUBMITTED', 'warning'],
      ['ACKNOWLEDGED', 'progress'],
      ['CONFIRMED', 'progress'],
      ['PARTIALLY_RECEIVED', 'progress'],
      ['RECEIVED', 'success'],
      ['SETTLED', 'success'],
      ['CLOSED', 'neutral'],
      ['CANCELED', 'danger'],
    ]);
  });

  it('renders proportional segments; zero buckets (incl. SETTLED / CLOSED) get none', () => {
    render(
      <PoStatusBar
        buckets={PO_KEYS.map((k) =>
          po(k, { SUBMITTED: 1, CONFIRMED: 3, RECEIVED: 4 }[k] ?? 0),
        )}
      />,
    );
    const bar = screen.getByTestId('scm-po-status-bar');
    expect(bar).toHaveAttribute('aria-hidden', 'true');
    const seg = (k: string) => bar.querySelector(`[data-segment="${k}"]`) as HTMLElement;
    expect(pct(seg('SUBMITTED'))).toBe(12.5);
    expect(pct(seg('CONFIRMED'))).toBe(37.5);
    expect(pct(seg('RECEIVED'))).toBe(50);
    expect(seg('SETTLED')).toBeNull();
    expect(seg('CLOSED')).toBeNull();
  });

  it('ONE degraded / forbidden bucket → no bar and no note', () => {
    const a = PO_ALL(2);
    a[3] = po('CONFIRMED', null, 'degraded');
    expect(render(<PoStatusBar buckets={a} />).container).toBeEmptyDOMElement();
    const b = PO_ALL(2);
    b[0] = po('DRAFT', null, 'forbidden');
    expect(render(<PoStatusBar buckets={b} />).container).toBeEmptyDOMElement();
  });

  it('empty list → nothing; all zero → «발주가 없습니다.»', () => {
    const empty = render(<PoStatusBar buckets={[]} />);
    expect(empty.container).toBeEmptyDOMElement();
    empty.unmount();
    render(<PoStatusBar buckets={PO_ALL(0)} />);
    expect(screen.getByTestId('scm-po-status-empty')).toHaveTextContent(
      '발주가 없습니다.',
    );
    expect(screen.queryByTestId('scm-po-status-bar')).toBeNull();
  });
});

describe('② alert status bar', () => {
  it('미확인 is warning, 확인 neutral', () => {
    expect(
      buildAlertStatusSegments([alert('unacknowledged', 1), alert('acknowledged', 3)])?.map(
        (s) => [s.key, s.value, s.tone],
      ),
    ).toEqual([
      ['unacknowledged', 1, 'warning'],
      ['acknowledged', 3, 'neutral'],
    ]);
  });

  it('renders proportional segments', () => {
    render(
      <AlertStatusBar buckets={[alert('unacknowledged', 1), alert('acknowledged', 3)]} />,
    );
    const bar = screen.getByTestId('wms-alert-status-bar');
    const seg = (k: string) => bar.querySelector(`[data-segment="${k}"]`) as HTMLElement;
    expect(pct(seg('unacknowledged'))).toBe(25);
    expect(pct(seg('acknowledged'))).toBe(75);
  });

  it('one degraded bucket → no bar; empty → nothing; all zero → «알림이 없습니다.»', () => {
    expect(
      render(
        <AlertStatusBar
          buckets={[alert('unacknowledged', null, 'degraded'), alert('acknowledged', 3)]}
        />,
      ).container,
    ).toBeEmptyDOMElement();
    expect(render(<AlertStatusBar buckets={[]} />).container).toBeEmptyDOMElement();
    render(
      <AlertStatusBar buckets={[alert('unacknowledged', 0), alert('acknowledged', 0)]} />,
    );
    expect(screen.getByTestId('wms-alert-status-empty')).toHaveTextContent(
      '알림이 없습니다.',
    );
    expect(screen.queryByTestId('wms-alert-status-bar')).toBeNull();
  });
});

const scmState = (over: Partial<ScmOverviewState> = {}): ScmOverviewState => ({
  notEligible: false,
  counts: [],
  poStatus: [po('CONFIRMED', 4), po('CANCELED', 1)],
  recentPos: [
    {
      id: 'po1',
      poNumber: 'PO-1',
      status: 'CONFIRMED',
      totalAmount: '125000.00',
      currency: 'KRW',
      createdAt: '2026-07-01T00:00:00Z',
    },
  ],
  recentPosStatus: 'ok',
  s5Warning: null,
  ...over,
});

describe('③ ScmOverview', () => {
  it('bar + numbers + testids intact; recent PO amount in the ₩ look', () => {
    render(<ScmOverview state={scmState()} />);
    expect(screen.getByTestId('scm-po-status-bar')).toBeInTheDocument();
    expect(screen.getByTestId('scm-po-status-CONFIRMED')).toHaveTextContent('4');
    expect(screen.getByTestId('scm-po-status')).toHaveTextContent('확정');
    const recent = screen.getByTestId('scm-recent-pos');
    expect(recent).toHaveTextContent('₩125,000');
    expect(recent).not.toHaveTextContent('125000.00');
  });

  it('one degraded bucket: «—», no bar', () => {
    render(
      <ScmOverview
        state={scmState({ poStatus: [po('CONFIRMED', null, 'degraded'), po('CANCELED', 1)] })}
      />,
    );
    expect(screen.queryByTestId('scm-po-status-bar')).toBeNull();
    expect(screen.getByTestId('scm-po-status-CONFIRMED')).toHaveTextContent('—');
    expect(screen.getByTestId('scm-po-status-CANCELED')).toHaveTextContent('1');
  });
});

const wmsState = (alertStatus: WmsAlertStatusCount[]): WmsOverviewState => ({
  notEligible: false,
  counts: [],
  alertStatus,
  recentShipments: null,
  recentShipmentsStatus: 'ok',
  recentAdjustments: null,
  recentAdjustmentsStatus: 'ok',
  // TASK-MONO-808 — the «실적» strip (not under test here).
  period: 'month',
  performance: { status: 'degraded', value: null },
});

describe('④ WmsOverview', () => {
  it('bar + both numbers; degraded bucket → «—» and no bar', () => {
    const ok = render(
      <WmsOverview state={wmsState([alert('unacknowledged', 2), alert('acknowledged', 6)])} />,
    );
    expect(screen.getByTestId('wms-alert-status-bar')).toBeInTheDocument();
    expect(screen.getByTestId('wms-alert-status-unacknowledged')).toHaveTextContent('2');
    expect(screen.getByTestId('wms-alert-status-acknowledged')).toHaveTextContent('6');
    ok.unmount();
    render(
      <WmsOverview
        state={wmsState([alert('unacknowledged', null, 'degraded'), alert('acknowledged', 6)])}
      />,
    );
    expect(screen.queryByTestId('wms-alert-status-bar')).toBeNull();
    expect(screen.getByTestId('wms-alert-status-unacknowledged')).toHaveTextContent('—');
  });
});

describe('⑤ money look (string only, F5)', () => {
  it('decimal strings: fraction zeros of a zero-scale currency drop, grouping applied', () => {
    expect(formatDecimalDisplay('125000.00', 'KRW')).toBe('₩125,000');
    expect(formatDecimalDisplay('1000', 'KRW')).toBe('₩1,000');
    expect(formatDecimalDisplay('0.00', 'KRW')).toBe('₩0');
    expect(formatDecimalDisplay('999', 'KRW')).toBe('₩999');
  });

  it('a non-zero fraction is kept (never rounds); negatives keep the sign before ₩', () => {
    expect(formatDecimalDisplay('125000.50', 'KRW')).toBe('₩125,000.50');
    expect(formatDecimalDisplay('-2500.00', 'KRW')).toBe('-₩2,500');
  });

  it('very large amounts keep every digit (no float)', () => {
    expect(formatDecimalDisplay('9007199254740993.00', 'KRW')).toBe('₩9,007,199,254,740,993');
    expect(
      formatMoneyDisplay({ amount: '12345678901234567890', currency: 'KRW' }),
    ).toBe('₩12,345,678,901,234,567,890');
  });

  it('other currency: code suffix, scale kept; unknown currency keeps the fraction', () => {
    expect(formatDecimalDisplay('1234.50', 'USD')).toBe('1,234.50 USD');
    expect(formatDecimalDisplay('1234.00', 'USD')).toBe('1,234.00 USD');
    expect(formatDecimalDisplay('1234.00', 'XYZ')).toBe('1,234.00 XYZ');
    expect(formatDecimalDisplay('5000', 'JPY')).toBe('5,000 JPY');
  });

  it('no currency → grouped number alone; unparsable / empty → verbatim', () => {
    expect(formatDecimalDisplay('125000.00')).toBe('125,000.00');
    expect(formatDecimalDisplay('12,5')).toBe('12,5');
    expect(formatDecimalDisplay('abc', 'KRW')).toBe('abc');
    expect(formatDecimalDisplay('', 'KRW')).toBe('');
  });

  it('minor-units Money: scale applied then displayed', () => {
    expect(formatMoneyDisplay({ amount: '80000', currency: 'KRW' })).toBe('₩80,000');
    expect(formatMoneyDisplay({ amount: '123456', currency: 'USD' })).toBe('1,234.56 USD');
    expect(formatMoneyDisplay({ amount: '5', currency: 'USD' })).toBe('0.05 USD');
    expect(formatMoneyDisplay({ amount: '-100000', currency: 'KRW' })).toBe('-₩100,000');
    expect(formatMoneyDisplay({ amount: '0', currency: 'KRW' })).toBe('₩0');
  });
});
