import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { WmsOverview } from '@/features/wms-ops';
import type { WmsOverviewState } from '@/features/wms-ops';

/**
 * TASK-PC-FE-166 — `WmsOverview` presentation. Per-area count tiles for the
 * operational-scale areas (재고 — total + 저재고 sub-count PC-FE-177 / 배송 —
 * 오늘/주간/월간/전체) are read-only stat tiles (NOT nav links — wms is a
 * single-route ops screen, PC-FE-168 deviation) and an alert-ack distribution
 * (미확인/확인 — the sole representation of alerts, PC-FE-170). The 최근 출고
 * glance moved to the `WmsRecentShipments` slot (PC-FE-177; see
 * `WmsRecentShipments.test.tsx`). A non-`ok` cell renders a compact placeholder
 * instead of a number (never blanks).
 */

const baseState = (
  over: Partial<WmsOverviewState> = {},
): WmsOverviewState => ({
  notEligible: false,
  counts: [
    {
      key: 'inventory',
      label: '재고',
      count: 42,
      status: 'ok',
      period: null,
      lowStock: 4,
    },
    {
      key: 'openOrders',
      label: '미출고 주문',
      count: 3,
      status: 'ok',
      period: null,
    },
    {
      key: 'shipments',
      label: '배송',
      count: 7,
      status: 'ok',
      period: { today: 2, week: 5, month: 6 },
    },
  ],
  alertStatus: [
    { key: 'unacknowledged', label: '미확인', count: 3, cellStatus: 'ok' },
    { key: 'acknowledged', label: '확인', count: 9, cellStatus: 'ok' },
  ],
  recentShipments: [
    {
      shipmentId: 'sh1',
      orderNo: 'ORD-1',
      shipmentNo: 'SHP-1',
      carrierCode: 'CJ',
      trackingNo: 'T1',
      shippedAt: '2026-07-01T00:00:00Z',
    },
  ],
  recentShipmentsStatus: 'ok',
  recentAdjustments: [
    {
      id: 'adj1',
      skuId: 'sku-1',
      bucket: 'AVAILABLE',
      delta: -5,
      reasonCode: 'ADJUSTMENT_CYCLE_COUNT',
      occurredAt: '2026-07-02T00:00:00Z',
    },
  ],
  recentAdjustmentsStatus: 'ok',
  // TASK-MONO-808 — the «실적» strip (warehouse-global /dashboard/metrics).
  period: 'month',
  performance: {
    status: 'ok',
    value: {
      period: 'month',
      timezone: 'Asia/Seoul',
      current: { from: '2026-10-01T00:00:00+09:00', to: '2026-10-24T14:05:00+09:00' },
      previous: { from: '2026-09-01T00:00:00+09:00', to: '2026-09-24T14:05:00+09:00' },
      metrics: [
        { key: 'qtyReceived', unit: 'count', currency: null, current: '12400', previous: '11000' },
        { key: 'qtyShipped', unit: 'count', currency: null, current: '9800', previous: '10100' },
        { key: 'shipmentCount', unit: 'count', currency: null, current: '312', previous: '300' },
      ],
      series: [
        { date: '2026-10-01', values: { qtyReceived: '500', qtyShipped: '400', shipmentCount: '12' } },
        { date: '2026-10-02', values: { qtyReceived: '0', qtyShipped: '800', shipmentCount: '20' } },
      ],
    },
  },
  ...over,
});

describe('WmsOverview (TASK-PC-FE-166)', () => {
  it('TASK-MONO-808 — the «실적» strip comes first, with the WMS labels and period links', () => {
    render(<WmsOverview state={baseState()} />);
    const strip = screen.getByTestId('wms-performance');
    expect(screen.getByTestId('wms-overview').firstElementChild).toBe(strip);

    expect(screen.getByTestId('wms-performance-kpi-qtyReceived')).toHaveTextContent('입고 수량');
    expect(screen.getByTestId('wms-performance-kpi-qtyReceived-value')).toHaveTextContent('12,400');
    expect(screen.getByTestId('wms-performance-kpi-qtyShipped')).toHaveTextContent('출고 수량');
    expect(screen.getByTestId('wms-performance-kpi-shipmentCount')).toHaveTextContent('출고 건수');
    expect(screen.getByTestId('wms-performance-period-week')).toHaveAttribute('href', '/wms?period=week');
    expect(screen.getByTestId('wms-performance-period-month')).toHaveAttribute('href', '/wms?period=month');
    expect(screen.getByTestId('wms-performance-period-month')).toHaveAttribute('aria-current', 'page');
    // The trend chart draws 출고 수량 (chartKey), not the first metric.
    expect(screen.getByTestId('wms-performance-trend')).toHaveTextContent('출고 수량 · 일별');
    // TASK-MONO-810 — the card-link prop is opt-in; the WMS strip's cards stay plain.
    expect(strip.querySelector('[data-testid$="-link"]')).toBeNull();
    expect(strip.querySelectorAll('a')).toHaveLength(2); // the period switch only
  });

  it('TASK-MONO-808 — a failed strip does not take the tiles with it; the 배송 tile keeps its own counts', () => {
    render(
      <WmsOverview
        state={baseState({ period: 'week', performance: { status: 'degraded', value: null } })}
      />,
    );
    expect(screen.getByTestId('wms-performance-degraded')).toHaveTextContent('실적을 불러올 수 없습니다');
    expect(screen.getByTestId('wms-performance-period-week')).toHaveAttribute('aria-current', 'page');
    expect(screen.getByTestId('wms-inventory-count')).toHaveTextContent('42');
    expect(screen.getByTestId('wms-shipments-count-week')).toHaveTextContent('5');
    expect(screen.getByTestId('wms-alert-status-unacknowledged')).toHaveTextContent('3');
  });

  it('TASK-MONO-808 — a forbidden strip says so in words, never a 0', () => {
    render(
      <WmsOverview state={baseState({ performance: { status: 'forbidden', value: null } })} />,
    );
    expect(screen.getByTestId('wms-performance-forbidden')).toHaveTextContent('권한이 없습니다');
    expect(screen.queryByTestId('wms-performance-kpi-qtyShipped')).toBeNull();
  });

  it('notEligible → renders nothing', () => {
    const { container } = render(
      <WmsOverview state={baseState({ notEligible: true, counts: [] })} />,
    );
    expect(container).toBeEmptyDOMElement();
  });

  it('renders operational-scale count tiles (not links) with their totals', () => {
    render(<WmsOverview state={baseState()} />);
    expect(screen.getByTestId('wms-inventory-count')).toHaveTextContent('42');
    // 미출고 주문 (open orders) LEVEL tile — single total, no period (PC-FE-186).
    expect(screen.getByTestId('wms-openOrders-count')).toHaveTextContent('3');
    expect(screen.queryByTestId('wms-openOrders-count-today')).toBeNull();
    expect(screen.getByTestId('wms-shipments-count')).toHaveTextContent('7');
    // Alerts are NOT a count tile — represented solely by the 알림 상태
    // distribution (PC-FE-170).
    expect(screen.queryByTestId('wms-alerts-count')).toBeNull();
    expect(screen.queryByTestId('wms-alerts-count-degraded')).toBeNull();
    // Stat tiles, NOT nav links (no anchor / href) — scoped to the count
    // container so the 바로가기 nav's 가이드 link (PC-FE-257) is not counted.
    expect(
      screen.getByTestId('wms-overview-counts').querySelector('a'),
    ).toBeNull();
  });

  it('links the overview to the WMS guide (PC-FE-257 discoverability parity)', () => {
    render(<WmsOverview state={baseState()} />);
    const link = screen.getByTestId('wms-overview-link-guide');
    expect(link.tagName).toBe('A');
    expect(link).toHaveAttribute('href', '/wms/guide');
  });

  it('배송 (FLOW) renders 오늘/주간/월간 period buckets + 전체 total; 재고 (LEVEL) stays a single total', () => {
    render(<WmsOverview state={baseState()} />);
    // 배송 flow tile: period buckets + 전체 secondary (back-compat testid).
    expect(screen.getByTestId('wms-shipments-count-today')).toHaveTextContent('2');
    expect(screen.getByTestId('wms-shipments-count-week')).toHaveTextContent('5');
    expect(screen.getByTestId('wms-shipments-count-month')).toHaveTextContent('6');
    expect(screen.getByTestId('wms-shipments-count')).toHaveTextContent('7');
    // 재고 level tile: single total + 저재고 sub-count, no period buckets.
    expect(screen.getByTestId('wms-inventory-count')).toHaveTextContent('42');
    expect(screen.getByTestId('wms-inventory-lowstock')).toHaveTextContent('4');
    expect(screen.queryByTestId('wms-inventory-count-today')).toBeNull();
  });

  it('재고 저재고 sub-count renders "—" when the sub-read degraded (lowStock null)', () => {
    render(
      <WmsOverview
        state={baseState({
          counts: [
            {
              key: 'inventory',
              label: '재고',
              count: 42,
              status: 'ok',
              period: null,
              lowStock: null,
            },
          ],
        })}
      />,
    );
    // Tile stays ok on its total; only the 저재고 sub-count shows "—".
    expect(screen.getByTestId('wms-inventory-count')).toHaveTextContent('42');
    expect(screen.getByTestId('wms-inventory-lowstock')).toHaveTextContent('—');
  });

  it('a null 배송 period bucket renders "—" while siblings + total render', () => {
    render(
      <WmsOverview
        state={baseState({
          counts: [
            {
              key: 'shipments',
              label: '배송',
              count: 7,
              status: 'ok',
              period: { today: null, week: 5, month: 6 },
            },
          ],
        })}
      />,
    );
    expect(screen.getByTestId('wms-shipments-count-today')).toHaveTextContent('—');
    expect(screen.getByTestId('wms-shipments-count-week')).toHaveTextContent('5');
    expect(screen.getByTestId('wms-shipments-count')).toHaveTextContent('7');
  });

  it('zero count renders as "0", not degraded', () => {
    render(
      <WmsOverview
        state={baseState({
          counts: [{ key: 'inventory', label: '재고', count: 0, status: 'ok' }],
        })}
      />,
    );
    expect(screen.getByTestId('wms-inventory-count')).toHaveTextContent('0');
    expect(screen.queryByTestId('wms-inventory-count-degraded')).toBeNull();
  });

  it('a non-ok count cell renders a placeholder + reflects its status indicator', () => {
    render(
      <WmsOverview
        state={baseState({
          counts: [
            { key: 'inventory', label: '재고', count: null, status: 'degraded' },
            { key: 'shipments', label: '배송', count: null, status: 'forbidden' },
          ],
        })}
      />,
    );
    expect(screen.getByTestId('wms-inventory-count-degraded')).toHaveTextContent(
      '점검 필요',
    );
    expect(screen.getByTestId('wms-shipments-count-degraded')).toHaveTextContent(
      '권한 없음',
    );
    expect(screen.queryByTestId('wms-inventory-count')).toBeNull();

    const invStatus = screen.getByTestId('wms-inventory-service-status');
    expect(invStatus).toHaveAttribute('data-status', 'degraded');
    expect(invStatus).toHaveTextContent('점검 필요');
    expect(
      screen.getByTestId('wms-shipments-service-status'),
    ).toHaveAttribute('data-status', 'forbidden');
  });

  it('renders the alert-ack distribution', () => {
    render(<WmsOverview state={baseState()} />);
    expect(
      screen.getByTestId('wms-alert-status-unacknowledged'),
    ).toHaveTextContent('3');
    expect(
      screen.getByTestId('wms-alert-status-acknowledged'),
    ).toHaveTextContent('9');
    // 최근 출고 is NOT rendered by WmsOverview anymore (moved to the
    // WmsRecentShipments slot, PC-FE-177).
    expect(screen.queryByTestId('wms-recent-shipments')).toBeNull();
  });

  it('a degraded alert-ack bucket renders "—", not a number', () => {
    render(
      <WmsOverview
        state={baseState({
          alertStatus: [
            {
              key: 'unacknowledged',
              label: '미확인',
              count: null,
              cellStatus: 'degraded',
            },
          ],
        })}
      />,
    );
    expect(
      screen.getByTestId('wms-alert-status-unacknowledged'),
    ).toHaveTextContent('—');
  });
});
