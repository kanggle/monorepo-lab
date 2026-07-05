import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
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
  listOrders: vi.fn(),
  listShipments: vi.fn(),
  listAdjustments: vi.fn(),
  listAlerts: vi.fn(),
}));
vi.mock('@/features/wms-ops/api/wms-inventory-api', () => ({
  listInventory: m.listInventory,
  listOrders: m.listOrders,
}));
vi.mock('@/features/wms-ops/api/wms-shipments-api', () => ({
  listShipments: m.listShipments,
  listAdjustments: m.listAdjustments,
}));
vi.mock('@/features/wms-ops/api/wms-alerts-api', () => ({
  listAlerts: m.listAlerts,
}));

import { getWmsOverviewState } from '@/features/wms-ops/api/overview-state';
import { kstPeriodBounds } from '@/features/wms-ops/api/kst-period';
import type { KstPeriodBounds } from '@/features/wms-ops/api/kst-period';

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

const adjRow = (id: string) => ({
  id,
  skuId: 'sku-1',
  bucket: 'AVAILABLE',
  delta: -5,
  reasonCode: 'ADJUSTMENT_CYCLE_COUNT',
  occurredAt: '2026-07-02T00:00:00Z',
});

/** The KST period windows the fan-out passes as `shippedAtFrom` (PC-FE-174).
 *  `seedHappy`'s shipments mock keys distinct counts by which bound string it
 *  receives (day / week / month start), so the three starts MUST stay distinct.
 *  They are NOT unconditionally distinct: `kstPeriodBounds()` derives them from
 *  the current date, and on a real KST week boundary (Monday → todayStart ==
 *  weekStart) or the 1st of a month two bounds collide — the later `switch`
 *  `case` then goes unreachable and a windowed read is mis-bucketed.
 *
 *  PC-FE-207: the clock MUST be pinned to a fixed mid-week (Wednesday),
 *  mid-month KST instant so day (07-15) / week (07-13) / month (07-01) stay
 *  distinct. The pin is established in `beforeEach` (NOT at module top level):
 *  a top-level `vi.useFakeTimers()` runs at collection and is torn down before
 *  this file's tests execute (setup.ts's global `afterEach` runs
 *  `vi.restoreAllMocks()`, and under the concurrent full-suite run global
 *  `Date` is reset by sibling files), so the source's `kstPeriodBounds()` saw
 *  the REAL clock — failing on every KST Monday. Pinning per-test keeps the
 *  fake clock live during each `getWmsOverviewState` call. `bounds` is computed
 *  under the same per-test pin. */
let bounds: KstPeriodBounds;

/** Default happy fan-out: every leg resolves. 배송 period reads (keyed by
 *  `shippedAtFrom`) return distinct counts; the total read (no window) is 7. */
function seedHappy() {
  // Two inventory reads (PC-FE-177): the total (no filter) and the 저재고
  // sub-count (lowStockOnly=true). Keyed on the filter → distinct values.
  m.listInventory.mockImplementation((p: { lowStockOnly?: boolean } = {}) =>
    Promise.resolve(wmsResult(p.lowStockOnly ? 4 : 42)),
  );
  m.listShipments.mockImplementation(
    (p: { size?: number; shippedAtFrom?: string } = {}) => {
      if (p.size === 5) {
        return Promise.resolve(wmsResult(7, [shipRow('sh1'), shipRow('sh2')]));
      }
      switch (p.shippedAtFrom) {
        case bounds.todayStartInstant:
          return Promise.resolve(wmsResult(2));
        case bounds.weekStartInstant:
          return Promise.resolve(wmsResult(5));
        case bounds.monthStartInstant:
          return Promise.resolve(wmsResult(6));
        default:
          return Promise.resolve(wmsResult(7)); // total (no window)
      }
    },
  );
  m.listAlerts.mockImplementation(
    (p: { acknowledged?: boolean } = {}) => {
      if (p.acknowledged === false) return Promise.resolve(wmsResult(3));
      if (p.acknowledged === true) return Promise.resolve(wmsResult(9));
      // A no-filter total-alerts read must NOT be issued (PC-FE-170); this
      // branch exists only to surface a regression if one ever is.
      return Promise.resolve(wmsResult(12));
    },
  );
  // 미출고 주문 (open orders, PC-FE-186) — status=RECEIVED count.
  m.listOrders.mockResolvedValue(wmsResult(3));
  // 최근 재고 조정 (recent adjustments, PC-FE-186) — size=5 activity read.
  m.listAdjustments.mockResolvedValue(
    wmsResult(2, [adjRow('adj1'), adjRow('adj2')]),
  );
}

beforeEach(() => {
  vi.clearAllMocks();
  // PC-FE-207: pin the clock per-test (see the bounds rationale above) so the
  // source's kstPeriodBounds() runs against a fixed mid-week / mid-month KST
  // instant during every getWmsOverviewState call — immune to the collection-
  // time teardown and the concurrent full-suite global-Date reset that made a
  // module-top-level pin fail on KST Mondays. `bounds` is computed under it.
  vi.useFakeTimers({ toFake: ['Date'], now: new Date('2026-07-15T12:00:00+09:00') });
  bounds = kstPeriodBounds();
});

afterEach(() => {
  // Release the pinned clock after each test — do not leak fake `Date` into
  // sibling files (mirrors setup.ts's global restore).
  vi.useRealTimers();
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
    // Count tiles = operational-scale areas only (재고/배송); alerts is NOT a
    // count tile (PC-FE-170).
    expect(byKey.inventory.count).toBe(42);
    expect(byKey.inventory.status).toBe('ok');
    expect(byKey.shipments.count).toBe(7);
    expect(byKey.alerts).toBeUndefined();

    // 재고 is a point-in-time LEVEL → no period breakdown (PC-FE-174).
    expect(byKey.inventory.period).toBeNull();
    // 재고 carries a 저재고 attention sub-count (PC-FE-177).
    expect(byKey.inventory.lowStock).toBe(4);
    // 배송 is a FLOW → 오늘/주간/월간 period-to-date + 전체 total.
    expect(byKey.shipments.period).toEqual({ today: 2, week: 5, month: 6 });

    // 미출고 주문 (open orders, PC-FE-186) — a LEVEL count tile (no period),
    // sits between 재고 and 배송.
    expect(byKey.openOrders.count).toBe(3);
    expect(byKey.openOrders.status).toBe('ok');
    expect(byKey.openOrders.period).toBeNull();
    expect(state.counts.map((c) => c.key)).toEqual([
      'inventory',
      'openOrders',
      'shipments',
    ]);
    expect(m.listOrders).toHaveBeenCalledWith({
      status: 'RECEIVED',
      page: 0,
      size: 1,
    });

    // Counts derive from a page=0,size=1 read (totalElements only) — one for the
    // 재고 total, one for the 저재고 (lowStockOnly) sub-count.
    expect(m.listInventory).toHaveBeenCalledWith({ page: 0, size: 1 });
    expect(m.listInventory).toHaveBeenCalledWith({
      lowStockOnly: true,
      page: 0,
      size: 1,
    });
    // The three 배송 windowed reads carry the today/week/month `shippedAtFrom`
    // bounds + a `shippedAtTo` upper bound (a live `now`, so only its presence
    // is asserted — the day/week/month starts are stable and exact).
    const windowed = m.listShipments.mock.calls
      .map((c) => c[0])
      .filter((a) => a?.shippedAtFrom);
    expect(windowed).toHaveLength(3);
    expect(new Set(windowed.map((a) => a.shippedAtFrom))).toEqual(
      new Set([
        bounds.todayStartInstant,
        bounds.weekStartInstant,
        bounds.monthStartInstant,
      ]),
    );
    for (const a of windowed) {
      expect(typeof a.shippedAtTo).toBe('string');
      expect(a).toMatchObject({ page: 0, size: 1 });
    }

    // No no-filter total-alerts fan-out leg — alerts are surfaced only by the
    // ack distribution, whose two legs both carry an `acknowledged` filter.
    expect(m.listAlerts).toHaveBeenCalledTimes(2);
    for (const call of m.listAlerts.mock.calls) {
      expect(call[0]).toHaveProperty('acknowledged');
    }

    // Alert-ack distribution.
    const unacked = state.alertStatus.find((b) => b.key === 'unacknowledged');
    const acked = state.alertStatus.find((b) => b.key === 'acknowledged');
    expect(unacked?.count).toBe(3);
    expect(unacked?.cellStatus).toBe('ok');
    expect(acked?.count).toBe(9);

    // Recent shipments from the size=5 read.
    expect(state.recentShipments).toHaveLength(2);
    expect(state.recentShipmentsStatus).toBe('ok');

    // Recent adjustments from the size=5 read (PC-FE-186) — the 재고-side
    // activity companion.
    expect(state.recentAdjustments).toHaveLength(2);
    expect(state.recentAdjustmentsStatus).toBe('ok');
    expect(m.listAdjustments).toHaveBeenCalledWith({ page: 0, size: 5 });
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
    // Sibling stays ok; the alert-ack distribution is also unaffected.
    expect(state.counts.find((c) => c.key === 'shipments')!.status).toBe('ok');
    expect(
      state.alertStatus.find((b) => b.key === 'unacknowledged')!.cellStatus,
    ).toBe('ok');
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

  it('a degraded 배송 period sub-read → that bucket null, tile stays ok on the total', async () => {
    seedHappy();
    // The 오늘 window read 503s; the total + week + month reads still resolve.
    m.listShipments.mockImplementation(
      (p: { size?: number; shippedAtFrom?: string } = {}) => {
        if (p.size === 5) {
          return Promise.resolve(wmsResult(7, [shipRow('sh1')]));
        }
        if (p.shippedAtFrom === bounds.todayStartInstant) {
          return Promise.reject(
            new WmsUnavailableError('timeout', 'TIMEOUT', 'slow'),
          );
        }
        if (p.shippedAtFrom === bounds.weekStartInstant) {
          return Promise.resolve(wmsResult(5));
        }
        if (p.shippedAtFrom === bounds.monthStartInstant) {
          return Promise.resolve(wmsResult(6));
        }
        return Promise.resolve(wmsResult(7)); // total
      },
    );

    const state = await getWmsOverviewState(true);
    const ship = state.counts.find((c) => c.key === 'shipments')!;
    // Total read resolved → tile ok; only the 오늘 bucket is null.
    expect(ship.status).toBe('ok');
    expect(ship.count).toBe(7);
    expect(ship.period).toEqual({ today: null, week: 5, month: 6 });
  });

  it('a degraded 저재고 sub-read → 재고 tile stays ok on its total, lowStock null (PC-FE-177)', async () => {
    seedHappy();
    // The 저재고 (lowStockOnly) read 503s; the total inventory read resolves.
    m.listInventory.mockImplementation((p: { lowStockOnly?: boolean } = {}) =>
      p.lowStockOnly
        ? Promise.reject(new WmsUnavailableError('timeout', 'TIMEOUT', 'slow'))
        : Promise.resolve(wmsResult(42)),
    );
    const state = await getWmsOverviewState(true);
    const inv = state.counts.find((c) => c.key === 'inventory')!;
    expect(inv.status).toBe('ok');
    expect(inv.count).toBe(42);
    expect(inv.lowStock).toBeNull();
  });

  it('401 in any leg → whole-session redirect(/login) (not a per-cell degrade)', async () => {
    seedHappy();
    m.listAlerts.mockRejectedValue(new ApiError(401, 'TOKEN_INVALID', 'exp'));

    await expect(getWmsOverviewState(true)).rejects.toThrow('REDIRECT:/login');
    expect(redirectMock).toHaveBeenCalledWith('/login');
  });
});
