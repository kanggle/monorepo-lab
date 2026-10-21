import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { ApiError } from '@/shared/api/errors';

/**
 * TASK-PC-FE-358 — the `/wms` 개요 throughput card + its CSS chart:
 * bar ratios, zero bars, all-zero window → empty notice, UTC date labels
 * (no timezone shift), degrade notices, selector only for ≥ 2 warehouses,
 * and a warehouse switch re-querying the same-origin proxy (zero-filled).
 */

const m = vi.hoisted(() => ({ get: vi.fn() }));
vi.mock('@/shared/api/client', () => ({ apiClient: { get: m.get } }));

import { WmsThroughputCard } from '@/features/wms-ops/components/WmsThroughputCard';
import {
  WmsThroughputChart,
  shortDateLabel,
  longDateLabel,
} from '@/features/wms-ops/components/WmsThroughputChart';
import type { WmsThroughputState } from '@/features/wms-ops/api/throughput-state';
import { zeroFillThroughput, sumThroughput } from '@/features/wms-ops/lib/throughput';

const WH1 = '01910000-0000-7000-8000-000000000001';
const WH2 = '01910000-0000-7000-8000-000000000002';

function wrap(ui: React.ReactElement) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(<QueryClientProvider client={qc}>{ui}</QueryClientProvider>);
}

function stateWith(over: Partial<WmsThroughputState> = {}): WmsThroughputState {
  const days = zeroFillThroughput(
    {
      days: [
        {
          date: '2026-10-20',
          inbound: { putawayCount: 10, qtyReceived: 400 },
          outbound: { shipmentCount: 5, qtyShipped: 150 },
        },
        {
          date: '2026-10-22',
          inbound: { putawayCount: 1, qtyReceived: 40 },
          outbound: { shipmentCount: 0, qtyShipped: 0 },
        },
      ],
    },
    '2026-10-09',
    '2026-10-22',
  );
  return {
    notEligible: false,
    status: 'ok',
    warehouses: [{ id: WH1, code: 'WH01', name: 'Seoul Main Warehouse' }],
    selectedWarehouseId: WH1,
    from: '2026-10-09',
    to: '2026-10-22',
    days,
    totals: sumThroughput(days),
    ...over,
  };
}

beforeEach(() => {
  m.get.mockReset();
});

describe('WmsThroughputChart', () => {
  it('scales both series against ONE window max; zero → 0; tiny non-zero → 2% sliver', () => {
    const days = zeroFillThroughput(
      {
        days: [
          { date: '2026-10-01', inbound: { putawayCount: 200 }, outbound: { shipmentCount: 100 } },
          { date: '2026-10-02', inbound: { putawayCount: 1 }, outbound: { shipmentCount: 0 } },
        ],
      },
      '2026-10-01',
      '2026-10-03',
    );
    render(<WmsThroughputChart days={days} />);
    const h = (id: string) => (screen.getByTestId(id) as HTMLElement).style.height;
    expect(h('wms-throughput-chart-bar-in-2026-10-01')).toBe('100%');
    expect(h('wms-throughput-chart-bar-out-2026-10-01')).toBe('50%');
    expect(h('wms-throughput-chart-bar-in-2026-10-02')).toBe('2%');
    expect(h('wms-throughput-chart-bar-out-2026-10-02')).toBe('0%');
    // zero-filled day is drawn (not skipped)
    expect(h('wms-throughput-chart-bar-in-2026-10-03')).toBe('0%');
  });

  it('all-zero days → no NaN/Infinity heights (divide-by-zero guard)', () => {
    const days = zeroFillThroughput({ days: [] }, '2026-10-01', '2026-10-02');
    render(<WmsThroughputChart days={days} />);
    expect(screen.getByTestId('wms-throughput-chart-bar-in-2026-10-01').style.height).toBe('0%');
  });

  it('bars are aria-hidden; each day has a text sentence with both numbers', () => {
    const days = zeroFillThroughput(
      { days: [{ date: '2026-10-20', inbound: { putawayCount: 12 }, outbound: { shipmentCount: 9 } }] },
      '2026-10-20',
      '2026-10-20',
    );
    render(<WmsThroughputChart days={days} />);
    expect(screen.getByText('10월 20일: 입고 12건, 출고 9건')).toBeTruthy();
    const bar = screen.getByTestId('wms-throughput-chart-bar-in-2026-10-20');
    expect(bar.closest('[aria-hidden="true"]')).not.toBeNull();
  });

  it('date labels come straight from the ISO string (no timezone shift)', () => {
    expect(shortDateLabel('2026-10-01')).toBe('10/1');
    expect(longDateLabel('2026-01-09')).toBe('1월 9일');
  });
});

describe('WmsThroughputCard', () => {
  it('renders the UTC window label, the chart and the totals text', () => {
    wrap(<WmsThroughputCard state={stateWith()} />);
    expect(screen.getByTestId('wms-throughput-window').textContent).toBe(
      '최근 14일 · UTC 기준 (2026-10-09 ~ 2026-10-22)',
    );
    expect(screen.getByTestId('wms-throughput-chart')).toBeTruthy();
    expect(screen.getByTestId('wms-throughput-totals').textContent).toBe(
      '합계 — 입고 11건 (수량 440) · 출고 5건 (수량 150)',
    );
    // 14 drawn days — the zero-filled ones included
    expect(screen.getAllByTestId(/^wms-throughput-chart-day-/)).toHaveLength(14);
  });

  it('1 warehouse → no selector, its label shown', () => {
    wrap(<WmsThroughputCard state={stateWith()} />);
    expect(screen.queryByTestId('wms-throughput-warehouse')).toBeNull();
    expect(screen.getByTestId('wms-throughput-warehouse-single').textContent).toBe(
      '창고 WH01 · Seoul Main Warehouse',
    );
  });

  it('all-zero window → the empty notice, no chart', () => {
    const days = zeroFillThroughput({ days: [] }, '2026-10-09', '2026-10-22');
    wrap(<WmsThroughputCard state={stateWith({ days, totals: sumThroughput(days) })} />);
    expect(screen.getByTestId('wms-throughput-empty').textContent).toBe(
      '이 기간에 입고 · 출고 기록이 없습니다.',
    );
    expect(screen.queryByTestId('wms-throughput-chart')).toBeNull();
  });

  it('0 warehouses → the no-warehouse notice', () => {
    wrap(
      <WmsThroughputCard
        state={stateWith({ status: 'no-warehouses', warehouses: [], selectedWarehouseId: null, days: null, totals: null })}
      />,
    );
    expect(screen.getByTestId('wms-throughput-no-warehouses')).toBeTruthy();
  });

  it.each([
    ['forbidden', '권한 없음'],
    ['degraded', '점검 필요'],
  ] as const)('%s → card-local notice «%s»', (status, text) => {
    wrap(<WmsThroughputCard state={stateWith({ status, days: null, totals: null })} />);
    expect(screen.getByTestId('wms-throughput-unavailable').textContent).toBe(text);
  });

  it('not eligible → renders nothing', () => {
    const { container } = wrap(<WmsThroughputCard state={stateWith({ notEligible: true })} />);
    expect(container.innerHTML).toBe('');
  });

  it('≥ 2 warehouses → selector; the default is NOT re-fetched; a switch re-queries the proxy and zero-fills', async () => {
    m.get.mockResolvedValue({
      warehouseId: WH2,
      from: '2026-10-09',
      to: '2026-10-22',
      days: [
        {
          date: '2026-10-15',
          inbound: { putawayCount: 7, qtyReceived: 70 },
          outbound: { shipmentCount: 2, qtyShipped: 20 },
        },
      ],
    });
    wrap(
      <WmsThroughputCard
        state={stateWith({
          warehouses: [
            { id: WH1, code: 'WH01', name: 'Seoul Main Warehouse' },
            { id: WH2, code: 'WH02', name: null },
          ],
        })}
      />,
    );
    const select = screen.getByTestId('wms-throughput-warehouse') as HTMLSelectElement;
    expect(screen.getByLabelText('창고')).toBe(select);
    expect(select.value).toBe(WH1);
    expect(m.get).not.toHaveBeenCalled();

    fireEvent.change(select, { target: { value: WH2 } });
    await waitFor(() =>
      expect(screen.getByTestId('wms-throughput-totals').textContent).toBe(
        '합계 — 입고 7건 (수량 70) · 출고 2건 (수량 20)',
      ),
    );
    expect(m.get).toHaveBeenCalledWith(
      `/api/wms/throughput?warehouseId=${WH2}&from=2026-10-09&to=2026-10-22`,
    );
    expect(screen.getAllByTestId(/^wms-throughput-chart-day-/)).toHaveLength(14);
  });

  it('a failed switch (403) replaces only the chart body with «권한 없음»', async () => {
    m.get.mockRejectedValue(new ApiError(403, 'FORBIDDEN', 'no'));
    wrap(
      <WmsThroughputCard
        state={stateWith({
          warehouses: [
            { id: WH1, code: 'WH01', name: null },
            { id: WH2, code: 'WH02', name: null },
          ],
        })}
      />,
    );
    fireEvent.change(screen.getByTestId('wms-throughput-warehouse'), { target: { value: WH2 } });
    await waitFor(() =>
      expect(screen.getByTestId('wms-throughput-unavailable').textContent).toBe('권한 없음'),
    );
    // the selector (and window label) survive
    expect(screen.getByTestId('wms-throughput-warehouse')).toBeTruthy();
    expect(screen.getByTestId('wms-throughput-window')).toBeTruthy();
  });
});
