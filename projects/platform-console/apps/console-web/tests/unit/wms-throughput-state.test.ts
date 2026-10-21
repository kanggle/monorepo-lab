import { describe, it, expect, vi, beforeEach } from 'vitest';
import { ApiError, WmsUnavailableError } from '@/shared/api/errors';

/**
 * TASK-PC-FE-358 — `getWmsThroughputState` (contract § 2.4.5.2 «Daily
 * throughput card»): warehouse list → default warehouse → 14 UTC-day window,
 * zero-filled; card-local degrade; 401 → whole-session redirect.
 */

const { redirectMock } = vi.hoisted(() => ({ redirectMock: vi.fn() }));
vi.mock('next/navigation', () => ({
  redirect: (p: string) => {
    redirectMock(p);
    throw new Error(`REDIRECT:${p}`);
  },
}));

const m = vi.hoisted(() => ({ listRefs: vi.fn(), getThroughput: vi.fn() }));
vi.mock('@/features/wms-ops/api/wms-refs-api', () => ({ listRefs: m.listRefs }));
vi.mock('@/features/wms-ops/api/wms-inventory-api', () => ({
  getThroughput: m.getThroughput,
}));

import { getWmsThroughputState } from '@/features/wms-ops/api/throughput-state';

const NOW = new Date('2026-10-22T23:30:00Z');
const WH1 = '01910000-0000-7000-8000-000000000001';
const WH2 = '01910000-0000-7000-8000-000000000002';

const refs = (content: unknown[]) => ({
  data: {
    content,
    page: { number: 0, size: 100, totalElements: content.length, totalPages: 1 },
  },
  lagSeconds: null,
});

const throughput = (days: unknown[]) => ({
  data: {
    warehouseId: WH1,
    from: '2026-10-09',
    to: '2026-10-22',
    days,
    totals: {
      inbound: { putawayCount: 0, qtyReceived: 0 },
      outbound: { shipmentCount: 0, qtyShipped: 0 },
    },
  },
  lagSeconds: null,
});

const day = (date: string, pc: number, sc: number) => ({
  date,
  inbound: { putawayCount: pc, qtyReceived: pc * 10 },
  outbound: { shipmentCount: sc, qtyShipped: sc * 5 },
});

beforeEach(() => {
  m.listRefs.mockReset();
  m.getThroughput.mockReset();
  redirectMock.mockReset();
});

describe('getWmsThroughputState', () => {
  it('not eligible → no reads at all', async () => {
    const s = await getWmsThroughputState(false, NOW);
    expect(s.notEligible).toBe(true);
    expect(m.listRefs).not.toHaveBeenCalled();
    expect(m.getThroughput).not.toHaveBeenCalled();
  });

  it('asks for ACTIVE warehouses and the 14-day UTC window ending on the UTC date', async () => {
    m.listRefs.mockResolvedValue(refs([{ id: WH1, code: 'WH01', status: 'ACTIVE' }]));
    m.getThroughput.mockResolvedValue(throughput([]));
    const s = await getWmsThroughputState(true, NOW);
    expect(m.listRefs).toHaveBeenCalledWith(
      'warehouses',
      expect.objectContaining({ status: 'ACTIVE', size: 100 }),
    );
    expect(m.getThroughput).toHaveBeenCalledWith({
      warehouseId: WH1,
      from: '2026-10-09',
      to: '2026-10-22',
    });
    expect(s.from).toBe('2026-10-09');
    expect(s.to).toBe('2026-10-22');
  });

  it('KST early morning (00:30 KST = 15:30Z the day before) → window ends on the UTC date', async () => {
    m.listRefs.mockResolvedValue(refs([{ id: WH1, code: 'WH01' }]));
    m.getThroughput.mockResolvedValue(throughput([]));
    await getWmsThroughputState(true, new Date('2026-10-22T00:30:00+09:00'));
    expect(m.getThroughput).toHaveBeenCalledWith(
      expect.objectContaining({ from: '2026-10-08', to: '2026-10-21' }),
    );
  });

  it('0 warehouses → no-warehouses, NO throughput read', async () => {
    m.listRefs.mockResolvedValue(refs([{ id: WH1, code: 'WH01', status: 'INACTIVE' }]));
    const s = await getWmsThroughputState(true, NOW);
    expect(s.status).toBe('no-warehouses');
    expect(s.warehouses).toEqual([]);
    expect(m.getThroughput).not.toHaveBeenCalled();
  });

  it('1 warehouse → that warehouse, a single option', async () => {
    m.listRefs.mockResolvedValue(refs([{ id: WH1, code: 'WH01', name: 'Seoul' }]));
    m.getThroughput.mockResolvedValue(throughput([day('2026-10-22', 2, 1)]));
    const s = await getWmsThroughputState(true, NOW);
    expect(s.status).toBe('ok');
    expect(s.warehouses).toHaveLength(1);
    expect(s.selectedWarehouseId).toBe(WH1);
  });

  it('2 warehouses → default = first by code ascending, both offered', async () => {
    m.listRefs.mockResolvedValue(
      refs([
        { id: WH1, code: 'WH01', status: 'ACTIVE' },
        { id: WH2, code: 'WH-MAIN', status: 'ACTIVE' },
      ]),
    );
    m.getThroughput.mockResolvedValue(throughput([]));
    const s = await getWmsThroughputState(true, NOW);
    expect(s.warehouses.map((w) => w.code)).toEqual(['WH-MAIN', 'WH01']);
    expect(s.selectedWarehouseId).toBe(WH2);
    expect(m.getThroughput).toHaveBeenCalledWith(expect.objectContaining({ warehouseId: WH2 }));
  });

  it('200 → zero-filled 14 days; totals follow the drawn days', async () => {
    m.listRefs.mockResolvedValue(refs([{ id: WH1, code: 'WH01' }]));
    m.getThroughput.mockResolvedValue(
      throughput([day('2026-10-10', 4, 0), day('2026-10-22', 1, 3)]),
    );
    const s = await getWmsThroughputState(true, NOW);
    expect(s.days).toHaveLength(14);
    expect(s.days?.[0]).toMatchObject({ date: '2026-10-09', putawayCount: 0, shipmentCount: 0 });
    expect(s.days?.[1]).toMatchObject({ date: '2026-10-10', putawayCount: 4 });
    expect(s.days?.[13]).toMatchObject({ date: '2026-10-22', putawayCount: 1, shipmentCount: 3 });
    expect(s.totals).toEqual({ putawayCount: 5, shipmentCount: 3, qtyReceived: 50, qtyShipped: 15 });
  });

  it('200 with empty days → ok, all zeros (the card shows its empty notice)', async () => {
    m.listRefs.mockResolvedValue(refs([{ id: WH1, code: 'WH01' }]));
    m.getThroughput.mockResolvedValue(throughput([]));
    const s = await getWmsThroughputState(true, NOW);
    expect(s.status).toBe('ok');
    expect(s.totals).toEqual({ putawayCount: 0, shipmentCount: 0, qtyReceived: 0, qtyShipped: 0 });
  });

  it('warehouse list 403 → forbidden, no throughput read', async () => {
    m.listRefs.mockRejectedValue(new ApiError(403, 'FORBIDDEN', 'no'));
    const s = await getWmsThroughputState(true, NOW);
    expect(s.status).toBe('forbidden');
    expect(m.getThroughput).not.toHaveBeenCalled();
  });

  it('warehouse list 503 → degraded (never throws)', async () => {
    m.listRefs.mockRejectedValue(new WmsUnavailableError('downstream', 'SERVICE_UNAVAILABLE', 'x'));
    const s = await getWmsThroughputState(true, NOW);
    expect(s.status).toBe('degraded');
  });

  it('throughput 403 → forbidden but the warehouse list is kept (selector still usable)', async () => {
    m.listRefs.mockResolvedValue(
      refs([
        { id: WH1, code: 'WH01' },
        { id: WH2, code: 'WH02' },
      ]),
    );
    m.getThroughput.mockRejectedValue(new ApiError(403, 'FORBIDDEN', 'no'));
    const s = await getWmsThroughputState(true, NOW);
    expect(s.status).toBe('forbidden');
    expect(s.warehouses).toHaveLength(2);
    expect(s.days).toBeNull();
  });

  it('throughput 5xx / timeout → degraded', async () => {
    m.listRefs.mockResolvedValue(refs([{ id: WH1, code: 'WH01' }]));
    m.getThroughput.mockRejectedValue(new WmsUnavailableError('timeout', 'TIMEOUT', 't'));
    const s = await getWmsThroughputState(true, NOW);
    expect(s.status).toBe('degraded');
  });

  it('throughput 400 (producer VALIDATION_ERROR) → degraded, not a crash', async () => {
    m.listRefs.mockResolvedValue(refs([{ id: WH1, code: 'WH01' }]));
    m.getThroughput.mockRejectedValue(new ApiError(400, 'VALIDATION_ERROR', 'range'));
    const s = await getWmsThroughputState(true, NOW);
    expect(s.status).toBe('degraded');
  });

  it('401 on the warehouse list → whole-session redirect', async () => {
    m.listRefs.mockRejectedValue(new ApiError(401, 'UNAUTHORIZED', 'x'));
    await expect(getWmsThroughputState(true, NOW)).rejects.toThrow(
      'REDIRECT:/login?error=session_expired',
    );
    expect(redirectMock).toHaveBeenCalledWith('/login?error=session_expired');
  });

  it('401 on the throughput read → whole-session redirect', async () => {
    m.listRefs.mockResolvedValue(refs([{ id: WH1, code: 'WH01' }]));
    m.getThroughput.mockRejectedValue(new ApiError(401, 'UNAUTHORIZED', 'x'));
    await expect(getWmsThroughputState(true, NOW)).rejects.toThrow('REDIRECT:');
    expect(redirectMock).toHaveBeenCalledWith('/login?error=session_expired');
  });
});
