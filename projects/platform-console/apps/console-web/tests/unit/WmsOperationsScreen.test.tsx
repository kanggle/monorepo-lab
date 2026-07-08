import { describe, it, expect } from 'vitest';
import { render, screen, within } from '@testing-library/react';
import { WmsOperationsScreen } from '@/features/wms-ops';
import type { WmsOperationsSectionState } from '@/features/wms-ops';
import { runAxe } from '../a11y/axe-helper';

/**
 * `features/wms-ops` `WmsOperationsScreen` — TASK-PC-FE-224, the dedicated
 * `/wms/operations` screen surfacing the previously-uncoded settings read
 * (`GET /settings`, § 5.1) alongside the already-exported-but-zero-consumer
 * `getProjectionStatus()` (§ 6.2):
 *   - 운영 설정 table (예약 TTL / 저재고 기본 임계치, key/value/description)
 *   - 프로젝션 상태 table (topic/lag/last-processed) + worst-lag summary
 *   - a producer-absent settings key ⇒ its row is OMITTED (never forced
 *     blank — task Edge Case)
 *   - the two sections are INDEPENDENT cells: one degraded/forbidden never
 *     blanks the other (task Failure Scenarios)
 *   - WCAG AA axe-clean
 *
 * Pure server-rendered presentational component — fed the already-resolved
 * `WmsOperationsSectionState` directly (no client fetch to mock, mirrors
 * `WmsRecentAdjustments`/`WmsRecentShipments`).
 */

const OK_STATE: WmsOperationsSectionState = {
  notEligible: false,
  settings: [
    {
      key: 'inventory.reservation.ttl_hours',
      scope: 'GLOBAL',
      valueJson: 24,
      description: 'Reservation TTL in hours',
      version: 3,
    },
    {
      key: 'inventory.low_stock.default_threshold_qty',
      scope: 'GLOBAL',
      valueJson: 10,
      description: 'Default low-stock threshold quantity',
      version: 1,
    },
  ],
  settingsStatus: 'ok',
  projection: {
    projections: [
      {
        topic: 'wms.inventory.adjusted.v1',
        consumerGroup: 'admin-projection',
        lagSeconds: 1.4,
        lastEventAt: '2026-07-08T01:00:00Z',
        lastProjectedAt: '2026-07-08T01:00:01.400Z',
        lifetimeApplied: 12048,
        lifetimeIgnoredDuplicate: 17,
        lifetimeFailed: 0,
      },
    ],
    worstLagSeconds: 4.8,
  },
  projectionStatus: 'ok',
};

describe('WmsOperationsScreen — render + columns', () => {
  it('renders the heading + both sections when both cells resolve ok', () => {
    render(<WmsOperationsScreen state={OK_STATE} />);
    expect(
      screen.getByRole('heading', { name: 'WMS 운영설정' }),
    ).toBeInTheDocument();

    const settingsTable = screen.getByTestId('wms-operations-settings-table');
    expect(
      within(settingsTable).getByText('예약 TTL (시간)'),
    ).toBeInTheDocument();
    expect(within(settingsTable).getByText('24')).toBeInTheDocument();
    expect(
      within(settingsTable).getByText('Reservation TTL in hours'),
    ).toBeInTheDocument();
    expect(
      within(settingsTable).getByText('저재고 기본 임계치 (수량)'),
    ).toBeInTheDocument();
    expect(within(settingsTable).getByText('10')).toBeInTheDocument();

    const projectionTable = screen.getByTestId(
      'wms-operations-projection-table',
    );
    expect(
      within(projectionTable).getByText('wms.inventory.adjusted.v1'),
    ).toBeInTheDocument();
    expect(
      within(projectionTable).getByText('1.4'),
    ).toBeInTheDocument();
    expect(screen.getByTestId('wms-operations-worst-lag')).toHaveTextContent(
      '4.8',
    );
  });

  it('omits a row for a settings key the producer did not return (task Edge Case)', () => {
    render(
      <WmsOperationsScreen
        state={{
          ...OK_STATE,
          settings: [OK_STATE.settings![0]],
        }}
      />,
    );
    const settingsTable = screen.getByTestId('wms-operations-settings-table');
    expect(
      within(settingsTable).getByText('예약 TTL (시간)'),
    ).toBeInTheDocument();
    expect(
      within(settingsTable).queryByText('저재고 기본 임계치 (수량)'),
    ).not.toBeInTheDocument();
  });

  it('shows the settings-empty state when settings resolve ok but no known key is present', () => {
    render(<WmsOperationsScreen state={{ ...OK_STATE, settings: [] }} />);
    expect(
      screen.getByTestId('wms-operations-settings-empty'),
    ).toBeInTheDocument();
  });

  it('shows the projection-empty state when the projections array is empty', () => {
    render(
      <WmsOperationsScreen
        state={{
          ...OK_STATE,
          projection: { projections: [], worstLagSeconds: undefined },
        }}
      />,
    );
    expect(
      screen.getByTestId('wms-operations-projection-empty'),
    ).toBeInTheDocument();
  });
});

describe('WmsOperationsScreen — independent cells (task Failure Scenarios)', () => {
  it('settings degraded, projection ok — the projection section stays intact', () => {
    render(
      <WmsOperationsScreen
        state={{ ...OK_STATE, settingsStatus: 'degraded', settings: null }}
      />,
    );
    expect(
      screen.getByTestId('wms-operations-settings-degraded'),
    ).toBeInTheDocument();
    expect(
      screen.getByTestId('wms-operations-projection-table'),
    ).toBeInTheDocument();
    expect(
      screen.getByRole('heading', { name: 'WMS 운영설정' }),
    ).toBeInTheDocument();
  });

  it('projection forbidden, settings ok — the settings section stays intact', () => {
    render(
      <WmsOperationsScreen
        state={{
          ...OK_STATE,
          projectionStatus: 'forbidden',
          projection: null,
        }}
      />,
    );
    expect(
      screen.getByTestId('wms-operations-projection-forbidden'),
    ).toBeInTheDocument();
    expect(
      screen.getByTestId('wms-operations-settings-table'),
    ).toBeInTheDocument();
  });

  it('both sections degrade independently without crashing', () => {
    render(
      <WmsOperationsScreen
        state={{
          notEligible: false,
          settings: null,
          settingsStatus: 'degraded',
          projection: null,
          projectionStatus: 'degraded',
        }}
      />,
    );
    expect(
      screen.getByTestId('wms-operations-settings-degraded'),
    ).toBeInTheDocument();
    expect(
      screen.getByTestId('wms-operations-projection-degraded'),
    ).toBeInTheDocument();
  });

  it('a settings 403 renders forbidden inline (no crash)', () => {
    render(
      <WmsOperationsScreen
        state={{ ...OK_STATE, settingsStatus: 'forbidden', settings: null }}
      />,
    );
    expect(
      screen.getByTestId('wms-operations-settings-forbidden'),
    ).toBeInTheDocument();
  });
});

describe('WmsOperationsScreen — a11y', () => {
  it('the screen is axe-clean (WCAG AA)', async () => {
    const { container } = render(<WmsOperationsScreen state={OK_STATE} />);
    const violations = await runAxe(container);
    expect(violations).toEqual([]);
  });
});
