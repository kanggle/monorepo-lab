import { describe, it, expect, vi, beforeEach } from 'vitest';
import { ApiError, WmsUnavailableError } from '@/shared/api/errors';

/**
 * TASK-PC-FE-166 — `getWmsOverviewState` server fan-out (the first bff-domain
 * reference impl of the console domain-landing overview series). Console-web
 * DIRECT fan-out reusing the existing wms `list*` reads; counts derive from
 * `totalElements` read with `?page=0&size=1` (ADR-MONO-017 D3.B — no producer
 * `/summary`). Covers: not-eligible short-circuit, count/distribution mapping,
 * recent slice, per-cell degrade/forbidden, and the whole-session 401 redirect.
 */

const { redirectMock } = vi.hoisted(() => ({ redirectMock: vi.fn() }));
vi.mock('next/navigation', () => ({
  redirect: (p: string) => {
    redirectMock(p);
    throw new Error(`REDIRECT:${p}`);
  },
}));

const m = vi.hoisted(() => ({
  listInventory: vi.fn(),
  listShipments: vi.fn(),
  listAlerts: vi.fn(),
}));
vi.mock('@/features/wms-ops/api/wms-inventory-api', () => ({
  listInventory: m.listInventory,
}));
vi.mock('@/features/wms-ops/api/wms-shipments-api', () => ({
  listShipments: m.listShipments,
}));
vi.mock('@/features/wms-ops/api/wms-alerts-api', () => ({
  listAlerts: m.listAlerts,
}));

import { getWmsOverviewState } from '@/features/wms-ops/api/overview-state';

/** wms `WmsResult<Page>` envelope: { data: { content, page:{totalElements} }, lagSeconds }. */
const wmsResult = (totalElements: number, content: unknown[] = []) => ({
  data: {
    content,
    page: {
      number: 0,
      size: content.length || 1,
      totalElements,
      totalPages: 1,
    },
  },
  lagSeconds: null,
});

const shipRow = (shipmentId: string) => ({
  shipmentId,
  orderNo: 'ORD-1',
  shipmentNo: 'SHP-1',
  carrierCode: 'CJ',
  trackingNo: 'T1',
  shippedAt: '2026-07-01T00:00:00Z',
});

/** Default happy fan-out: every leg resolves. */
function seedHappy() {
  m.listInventory.mockResolvedValue(wmsResult(42));
  m.listShipments.mockImplementation((p: { size?: number } = {}) =>
    Promise.resolve(
      p.size === 5 ? wmsResult(7, [shipRow('sh1'), shipRow('sh2')]) : wmsResult(7),
    ),
  );
  m.listAlerts.mockImplementation(
    (p: { acknowledged?: boolean } = {}) => {
      if (p.acknowledged === false) return Promise.resolve(wmsResult(3));
      if (p.acknowledged === true) return Promise.resolve(wmsResult(9));
      return Promise.resolve(wmsResult(12)); // total
    },
  );
}

beforeEach(() => {
  vi.clearAllMocks();
});

describe('getWmsOverviewState (TASK-PC-FE-166)', () => {
  it('not eligible → no fan-out, notEligible flag, no wms call fabricated', async () => {
    const state = await getWmsOverviewState(false);
    expect(state.notEligible).toBe(true);
    expect(state.counts).toHaveLength(0);
    expect(m.listInventory).not.toHaveBeenCalled();
    expect(m.listShipments).not.toHaveBeenCalled();
    expect(m.listAlerts).not.toHaveBeenCalled();
  });

  it('happy → maps area counts + alert-ack distribution + recent shipments', async () => {
    seedHappy();
    const state = await getWmsOverviewState(true);

    expect(state.notEligible).toBe(false);
    const byKey = Object.fromEntries(state.counts.map((c) => [c.key, c]));
    expect(byKey.inventory.count).toBe(42);
    expect(byKey.inventory.status).toBe('ok');
    expect(byKey.shipments.count).toBe(7);
    expect(byKey.alerts.count).toBe(12);

    // Counts derive from a page=0,size=1 read (totalElements only).
    expect(m.listInventory).toHaveBeenCalledWith({ page: 0, size: 1 });

    // Alert-ack distribution.
    const unacked = state.alertStatus.find((b) => b.key === 'unacknowledged');
    const acked = state.alertStatus.find((b) => b.key === 'acknowledged');
    expect(unacked?.count).toBe(3);
    expect(unacked?.cellStatus).toBe('ok');
    expect(acked?.count).toBe(9);

    // Recent shipments from the size=5 read.
    expect(state.recentShipments).toHaveLength(2);
    expect(state.recentShipmentsStatus).toBe('ok');
  });

  it('zero counts render ok (not degraded)', async () => {
    seedHappy();
    m.listInventory.mockResolvedValue(wmsResult(0));
    const state = await getWmsOverviewState(true);
    const inv = state.counts.find((c) => c.key === 'inventory')!;
    expect(inv.status).toBe('ok');
    expect(inv.count).toBe(0);
  });

  it('per-cell degrade: a 503 leg → that count null/degraded, others unaffected', async () => {
    seedHappy();
    m.listInventory.mockRejectedValue(
      new WmsUnavailableError('downstream', 'SERVICE_UNAVAILABLE', 'down'),
    );

    const state = await getWmsOverviewState(true);
    const inv = state.counts.find((c) => c.key === 'inventory')!;
    expect(inv.count).toBeNull();
    expect(inv.status).toBe('degraded');
    // Siblings stay ok.
    expect(state.counts.find((c) => c.key === 'shipments')!.status).toBe('ok');
    expect(state.counts.find((c) => c.key === 'alerts')!.status).toBe('ok');
  });

  it('per-cell forbidden: a 403 leg → that count null/forbidden', async () => {
    seedHappy();
    m.listInventory.mockRejectedValue(new ApiError(403, 'FORBIDDEN', 'no'));

    const state = await getWmsOverviewState(true);
    const inv = state.counts.find((c) => c.key === 'inventory')!;
    expect(inv.count).toBeNull();
    expect(inv.status).toBe('forbidden');
  });

  it('a degraded recent-shipments leg → null rows + degraded status, counts unaffected', async () => {
    seedHappy();
    m.listShipments.mockImplementation((p: { size?: number } = {}) =>
      p.size === 5
        ? Promise.reject(
            new WmsUnavailableError('timeout', 'TIMEOUT', 'slow'),
          )
        : Promise.resolve(wmsResult(7)),
    );
    const state = await getWmsOverviewState(true);
    expect(state.recentShipments).toBeNull();
    expect(state.recentShipmentsStatus).toBe('degraded');
    // The shipments COUNT leg (size=1) still resolved.
    expect(state.counts.find((c) => c.key === 'shipments')!.count).toBe(7);
  });

  it('401 in any leg → whole-session redirect(/login) (not a per-cell degrade)', async () => {
    seedHappy();
    m.listAlerts.mockRejectedValue(new ApiError(401, 'TOKEN_INVALID', 'exp'));

    await expect(getWmsOverviewState(true)).rejects.toThrow('REDIRECT:/login');
    expect(redirectMock).toHaveBeenCalledWith('/login');
  });
});
