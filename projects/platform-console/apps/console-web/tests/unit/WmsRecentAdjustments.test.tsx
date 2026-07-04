import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { WmsRecentAdjustments } from '@/features/wms-ops';
import type { WmsOverviewState } from '@/features/wms-ops';

/**
 * TASK-PC-FE-186 — `WmsRecentAdjustments` (최근 재고 조정 glance). The 재고-side
 * activity companion to `WmsRecentShipments`, rendered in the same
 * `recentActivity` slot. Server component fed by the `getWmsOverviewState`
 * result; mirrors the WmsRecentShipments cases (rows / empty / degraded /
 * notEligible).
 */

const baseState = (
  over: Partial<WmsOverviewState> = {},
): WmsOverviewState => ({
  notEligible: false,
  counts: [],
  alertStatus: [],
  recentShipments: null,
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
  ...over,
});

describe('WmsRecentAdjustments (TASK-PC-FE-186)', () => {
  it('notEligible → renders nothing', () => {
    const { container } = render(
      <WmsRecentAdjustments state={baseState({ notEligible: true })} />,
    );
    expect(container).toBeEmptyDOMElement();
  });

  it('renders the recent-adjustment rows (reason + bucket/delta)', () => {
    render(<WmsRecentAdjustments state={baseState()} />);
    const panel = screen.getByTestId('wms-recent-adjustments');
    expect(panel).toHaveTextContent('ADJUSTMENT_CYCLE_COUNT');
    expect(panel).toHaveTextContent('AVAILABLE');
    // Negative delta renders with its sign.
    expect(panel).toHaveTextContent('-5');
  });

  it('shows a friendly empty state when there are no recent adjustments', () => {
    render(
      <WmsRecentAdjustments
        state={baseState({
          recentAdjustments: [],
          recentAdjustmentsStatus: 'ok',
        })}
      />,
    );
    expect(screen.getByTestId('wms-recent-adjustments')).toHaveTextContent(
      '최근 항목이 없습니다.',
    );
  });

  it('a degraded panel shows a placeholder, not rows', () => {
    render(
      <WmsRecentAdjustments
        state={baseState({
          recentAdjustments: null,
          recentAdjustmentsStatus: 'degraded',
        })}
      />,
    );
    expect(screen.getByTestId('wms-recent-adjustments')).toHaveTextContent(
      '점검 필요',
    );
  });
});
