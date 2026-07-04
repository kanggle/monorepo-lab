import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { WmsRecentShipments } from '@/features/wms-ops';
import type { WmsOverviewState } from '@/features/wms-ops';

/**
 * TASK-PC-FE-177 — `WmsRecentShipments` (최근 출고 glance). Extracted from the
 * `WmsOverview` band into a SEPARATE slot rendered AFTER the alerts table so
 * the 개요 reads 규모 → 주의 → 활동. Server component fed by the same
 * `getWmsOverviewState` result. These cases moved from `wms-overview.test.tsx`.
 */

const baseState = (
  over: Partial<WmsOverviewState> = {},
): WmsOverviewState => ({
  notEligible: false,
  counts: [],
  alertStatus: [],
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
  recentAdjustments: null,
  recentAdjustmentsStatus: 'ok',
  ...over,
});

describe('WmsRecentShipments (TASK-PC-FE-177)', () => {
  it('notEligible → renders nothing', () => {
    const { container } = render(
      <WmsRecentShipments state={baseState({ notEligible: true })} />,
    );
    expect(container).toBeEmptyDOMElement();
  });

  it('renders the recent-shipments rows', () => {
    render(<WmsRecentShipments state={baseState()} />);
    expect(screen.getByTestId('wms-recent-shipments')).toHaveTextContent(
      'SHP-1',
    );
  });

  it('shows a friendly empty state when there are no recent shipments', () => {
    render(
      <WmsRecentShipments
        state={baseState({ recentShipments: [], recentShipmentsStatus: 'ok' })}
      />,
    );
    expect(screen.getByTestId('wms-recent-shipments')).toHaveTextContent(
      '최근 항목이 없습니다.',
    );
  });

  it('a degraded recent-shipments panel shows a placeholder, not rows', () => {
    render(
      <WmsRecentShipments
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
