import { describe, it, expect, vi, beforeEach } from 'vitest';
import { ApiError, ScmUnavailableError } from '@/shared/api/errors';

/**
 * TASK-PC-FE-167 — `getScmOverviewState` server fan-out. Console-web DIRECT
 * fan-out reusing the existing scm `listPurchaseOrders` / `getSnapshot` reads;
 * counts derive from `totalElements` (`?page=0&size=1`; ADR-MONO-017 D3.B — no
 * producer `/summary`). Covers: not-eligible short-circuit, count/distribution
 * mapping, S5 warning surfacing, recent slice, per-cell degrade/forbidden, and
 * the whole-session 401 redirect.
 */

const { redirectMock } = vi.hoisted(() => ({ redirectMock: vi.fn() }));
vi.mock('next/navigation', () => ({
  redirect: (p: string) => {
    redirectMock(p);
    throw new Error(`REDIRECT:${p}`);
  },
}));

const m = vi.hoisted(() => ({
  listPurchaseOrders: vi.fn(),
  getSnapshot: vi.fn(),
  getPoMetrics: vi.fn(),
}));
vi.mock('@/features/scm-ops/api/scm-procurement-api', () => ({
  listPurchaseOrders: m.listPurchaseOrders,
}));
vi.mock('@/features/scm-ops/api/scm-inventory-visibility-api', () => ({
  getSnapshot: m.getSnapshot,
}));
vi.mock('@/features/scm-ops/api/po-metrics-api', () => ({
  getPoMetrics: m.getPoMetrics,
}));

/** `/po/metrics` body (TASK-MONO-810) — the shared contract shape. */
const metricsFixture = () => ({
  period: 'month',
  timezone: 'Asia/Seoul',
  current: { from: '2026-10-01T00:00:00+09:00', to: '2026-10-24T14:05:00+09:00' },
  previous: { from: '2026-09-01T00:00:00+09:00', to: '2026-09-24T14:05:00+09:00' },
  metrics: [
    { key: 'poCount', unit: 'count', currency: null, current: '7', previous: '5' },
    { key: 'poAmount:KRW', unit: 'money', currency: 'KRW', current: '1250000', previous: '1000000' },
    { key: 'receivedCount', unit: 'count', currency: null, current: '3', previous: '3' },
  ],
  series: [
    { date: '2026-10-01', values: { poCount: '2', 'poAmount:KRW': '250000', receivedCount: '1' } },
  ],
});

import { getScmOverviewState } from '@/features/scm-ops/api/overview-state';

const S5 = 'Not for procurement decisions (S5)';

/** scm `ScmResult<PoPage>` envelope. */
const poResult = (totalElements: number, content: unknown[] = []) => ({
  data: {
    content,
    page: 0,
    size: content.length || 1,
    totalElements,
    totalPages: 1,
  },
  cache: null,
});

/** scm `ScmResult<SnapshotResponse>` envelope (paginated cross-node form). */
const snapResult = (totalElements: number, warning = S5) => ({
  data: {
    data: { content: [], page: 0, size: 1, totalElements },
    meta: { warning },
  },
  cache: null,
});

const poRow = (id: string) => ({
  id,
  poNumber: `PO-${id}`,
  status: 'CONFIRMED',
  totalAmount: '1000',
  currency: 'KRW',
  createdAt: '2026-07-01T00:00:00Z',
});

function seedHappy() {
  m.listPurchaseOrders.mockImplementation(
    (p: { status?: string; size?: number } = {}) => {
      if (p.status) {
        return Promise.resolve(poResult(p.status === 'CONFIRMED' ? 4 : 1));
      }
      if (p.size === 5) {
        return Promise.resolve(poResult(12, [poRow('1'), poRow('2')]));
      }
      return Promise.resolve(poResult(12)); // total (size 1)
    },
  );
  m.getSnapshot.mockResolvedValue(snapResult(30));
  m.getPoMetrics.mockResolvedValue(metricsFixture());
}

beforeEach(() => {
  vi.clearAllMocks();
});

describe('getScmOverviewState (TASK-PC-FE-167)', () => {
  it('not eligible → no fan-out, notEligible flag, no scm call fabricated', async () => {
    const state = await getScmOverviewState(false);
    expect(state.notEligible).toBe(true);
    expect(state.counts).toHaveLength(0);
    expect(m.listPurchaseOrders).not.toHaveBeenCalled();
    expect(m.getSnapshot).not.toHaveBeenCalled();
    expect(m.getPoMetrics).not.toHaveBeenCalled();
    expect(state.performance.status).toBe('degraded');
  });

  it('happy → maps po/inventory counts + PO-status distribution + recent POs + S5 warning', async () => {
    seedHappy();
    const state = await getScmOverviewState(true);

    expect(state.notEligible).toBe(false);
    const byKey = Object.fromEntries(state.counts.map((c) => [c.key, c]));
    expect(byKey.po.count).toBe(12);
    expect(byKey.po.status).toBe('ok');
    expect(byKey.inventory.count).toBe(30);

    // Counts derive from a page=0,size=1 read.
    expect(m.listPurchaseOrders).toHaveBeenCalledWith({ page: 0, size: 1 });

    // S5 obligation surfaced when the inventory cell resolved.
    expect(state.s5Warning).toBe(S5);

    // PO-status distribution (9 buckets).
    expect(state.poStatus).toHaveLength(9);
    const confirmed = state.poStatus.find((b) => b.status === 'CONFIRMED');
    expect(confirmed?.count).toBe(4);
    expect(confirmed?.cellStatus).toBe('ok');

    // Recent POs from the size=5 read.
    expect(state.recentPos).toHaveLength(2);
    expect(state.recentPosStatus).toBe('ok');
  });

  it('zero counts render ok (not degraded)', async () => {
    seedHappy();
    m.listPurchaseOrders.mockImplementation(
      (p: { status?: string; size?: number } = {}) => {
        if (p.status) return Promise.resolve(poResult(0));
        return Promise.resolve(poResult(0));
      },
    );
    const state = await getScmOverviewState(true);
    const po = state.counts.find((c) => c.key === 'po')!;
    expect(po.status).toBe('ok');
    expect(po.count).toBe(0);
  });

  it('per-cell degrade: inventory 503 → inventory null/degraded + no S5, po unaffected', async () => {
    seedHappy();
    m.getSnapshot.mockRejectedValue(
      new ScmUnavailableError('downstream', 'SERVICE_UNAVAILABLE', 'down'),
    );

    const state = await getScmOverviewState(true);
    const inv = state.counts.find((c) => c.key === 'inventory')!;
    expect(inv.count).toBeNull();
    expect(inv.status).toBe('degraded');
    expect(state.s5Warning).toBeNull();
    expect(state.counts.find((c) => c.key === 'po')!.status).toBe('ok');
  });

  it('per-cell forbidden: a 403 po leg → that count null/forbidden', async () => {
    seedHappy();
    m.listPurchaseOrders.mockImplementation(
      (p: { status?: string; size?: number } = {}) => {
        if (!p.status && p.size !== 5) {
          return Promise.reject(new ApiError(403, 'TENANT_FORBIDDEN', 'no'));
        }
        if (p.status) return Promise.resolve(poResult(1));
        return Promise.resolve(poResult(12, [poRow('1')]));
      },
    );
    const state = await getScmOverviewState(true);
    const po = state.counts.find((c) => c.key === 'po')!;
    expect(po.count).toBeNull();
    expect(po.status).toBe('forbidden');
  });

  it('401 in any leg → whole-session redirect(/login) (not a per-cell degrade)', async () => {
    seedHappy();
    m.getSnapshot.mockRejectedValue(new ApiError(401, 'TOKEN_INVALID', 'exp'));

    await expect(getScmOverviewState(true)).rejects.toThrow('REDIRECT:/login');
    expect(redirectMock).toHaveBeenCalledWith('/login?error=session_expired');
  });

  // ── TASK-MONO-810 — the «실적» strip ────────────────────────────────────

  it('performance: the period reaches the metrics leg; ok → parsed cell', async () => {
    seedHappy();
    const state = await getScmOverviewState(true, 'week');
    expect(m.getPoMetrics).toHaveBeenCalledTimes(1);
    expect(m.getPoMetrics.mock.calls[0]).toEqual(['week']);
    expect(state.period).toBe('week');
    expect(state.performance.status).toBe('ok');
    expect(state.performance.value?.metrics.map((x) => x.key)).toEqual([
      'poCount',
      'poAmount:KRW',
      'receivedCount',
    ]);
  });

  it('performance: default period is the month; not eligible keeps the requested period', async () => {
    seedHappy();
    const state = await getScmOverviewState(true);
    expect(m.getPoMetrics).toHaveBeenCalledWith('month');
    expect(state.period).toBe('month');

    vi.clearAllMocks();
    const off = await getScmOverviewState(false, 'week');
    expect(off.period).toBe('week');
    expect(m.getPoMetrics).not.toHaveBeenCalled();
  });

  it('performance: a 503 degrades ONLY the strip — counts, distribution, recent stay ok', async () => {
    seedHappy();
    m.getPoMetrics.mockRejectedValue(
      new ScmUnavailableError('downstream', 'SERVICE_UNAVAILABLE', 'down'),
    );
    const state = await getScmOverviewState(true);
    expect(state.performance).toEqual({ status: 'degraded', value: null });
    expect(state.counts.every((c) => c.status === 'ok')).toBe(true);
    expect(state.poStatus.every((b) => b.cellStatus === 'ok')).toBe(true);
    expect(state.recentPosStatus).toBe('ok');
  });

  it('performance: a 400 or an unparseable body (number where a string belongs, F5) degrades the strip only', async () => {
    seedHappy();
    m.getPoMetrics.mockRejectedValue(new ApiError(400, 'VALIDATION_ERROR', 'period'));
    expect((await getScmOverviewState(true)).performance.status).toBe('degraded');

    m.getPoMetrics.mockResolvedValue({
      ...metricsFixture(),
      metrics: [{ key: 'poCount', unit: 'count', currency: null, current: 7, previous: '1' }],
    });
    const state = await getScmOverviewState(true);
    expect(state.performance.status).toBe('degraded');
    expect(state.counts.every((c) => c.status === 'ok')).toBe(true);
  });

  it('performance: a 403 → forbidden strip; a 401 → whole-session redirect', async () => {
    seedHappy();
    m.getPoMetrics.mockRejectedValue(new ApiError(403, 'PERMISSION_DENIED', 'no'));
    const state = await getScmOverviewState(true);
    expect(state.performance.status).toBe('forbidden');
    expect(state.counts.every((c) => c.status === 'ok')).toBe(true);

    m.getPoMetrics.mockRejectedValue(new ApiError(401, 'TOKEN_INVALID', 'exp'));
    await expect(getScmOverviewState(true)).rejects.toThrow('REDIRECT:/login');
    expect(redirectMock).toHaveBeenCalledWith('/login?error=session_expired');
  });
});
