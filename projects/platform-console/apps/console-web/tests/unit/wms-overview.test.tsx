import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { WmsOverview } from '@/features/wms-ops';
import type { WmsOverviewState } from '@/features/wms-ops';

/**
 * TASK-PC-FE-166 — `WmsOverview` presentation. Per-area count tiles for the
 * operational-scale areas (재고/배송) are read-only stat tiles (NOT nav links —
 * wms is a single-route ops screen, PC-FE-168 deviation), an alert-ack
 * distribution (미확인/확인 — the sole representation of alerts, PC-FE-170),
 * and a recent-shipments glance. A non-`ok` cell renders a compact placeholder
 * instead of a number (never blanks).
 */

const baseState = (
  over: Partial<WmsOverviewState> = {},
): WmsOverviewState => ({
  notEligible: false,
  counts: [
    { key: 'inventory', label: '재고', count: 42, status: 'ok', period: null },
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
  ...over,
});

describe('WmsOverview (TASK-PC-FE-166)', () => {
  it('notEligible → renders nothing', () => {
    const { container } = render(
      <WmsOverview state={baseState({ notEligible: true, counts: [] })} />,
    );
    expect(container).toBeEmptyDOMElement();
  });

  it('renders operational-scale count tiles (not links) with their totals', () => {
    render(<WmsOverview state={baseState()} />);
    expect(screen.getByTestId('wms-inventory-count')).toHaveTextContent('42');
    expect(screen.getByTestId('wms-shipments-count')).toHaveTextContent('7');
    // Alerts are NOT a count tile — represented solely by the 알림 상태
    // distribution (PC-FE-170).
    expect(screen.queryByTestId('wms-alerts-count')).toBeNull();
    expect(screen.queryByTestId('wms-alerts-count-degraded')).toBeNull();
    // Stat tiles, NOT nav links (no anchor / href).
    expect(screen.getByTestId('wms-overview').querySelector('a')).toBeNull();
  });

  it('배송 (FLOW) renders 오늘/주간/월간 period buckets + 전체 total; 재고 (LEVEL) stays a single total', () => {
    render(<WmsOverview state={baseState()} />);
    // 배송 flow tile: period buckets + 전체 secondary (back-compat testid).
    expect(screen.getByTestId('wms-shipments-count-today')).toHaveTextContent('2');
    expect(screen.getByTestId('wms-shipments-count-week')).toHaveTextContent('5');
    expect(screen.getByTestId('wms-shipments-count-month')).toHaveTextContent('6');
    expect(screen.getByTestId('wms-shipments-count')).toHaveTextContent('7');
    // 재고 level tile: single total, no period buckets.
    expect(screen.getByTestId('wms-inventory-count')).toHaveTextContent('42');
    expect(screen.queryByTestId('wms-inventory-count-today')).toBeNull();
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

  it('renders the alert-ack distribution and recent shipments', () => {
    render(<WmsOverview state={baseState()} />);
    expect(
      screen.getByTestId('wms-alert-status-unacknowledged'),
    ).toHaveTextContent('3');
    expect(
      screen.getByTestId('wms-alert-status-acknowledged'),
    ).toHaveTextContent('9');
    expect(screen.getByTestId('wms-recent-shipments')).toHaveTextContent('SHP-1');
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

  it('a degraded recent-shipments panel shows a placeholder, not rows', () => {
    render(
      <WmsOverview
        state={baseState({
          recentShipments: null,
          recentShipmentsStatus: 'degraded',
        })}
      />,
    );
    expect(screen.getByTestId('wms-recent-shipments')).toHaveTextContent(
      '점검 필요',
    );
  });
});
