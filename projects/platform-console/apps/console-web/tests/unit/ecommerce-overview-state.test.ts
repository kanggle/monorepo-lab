import { describe, it, expect, vi, beforeEach } from 'vitest';
import { ApiError, EcommerceUnavailableError } from '@/shared/api/errors';
import { ORDER_STATUS_VALUES } from '@/features/ecommerce-ops/api/order-types';

/**
 * TASK-PC-FE-156 / TASK-PC-FE-164 — `getEcommerceOverviewState` server fan-out.
 * PC-FE-164 switches the 7 area count legs from `list*({page:0,size:1})` to
 * dedicated `get*Summary()` calls returning `{ today, week, month, total }`.
 * Covers: not-eligible short-circuit, period-count mapping, order-status
 * distribution, recent slices, per-cell degrade/forbidden, and the
 * whole-session 401 redirect.
 */

const { redirectMock } = vi.hoisted(() => ({ redirectMock: vi.fn() }));
vi.mock('next/navigation', () => ({
  redirect: (p: string) => {
    redirectMock(p);
    throw new Error(`REDIRECT:${p}`);
  },
}));

const m = vi.hoisted(() => ({
  getProductsSummary: vi.fn(),
  getOrdersSummary: vi.fn(),
  getOrderInsights: vi.fn(),
  getUsersSummary: vi.fn(),
  getPromotionsSummary: vi.fn(),
  getShippingsSummary: vi.fn(),
  getSellersSummary: vi.fn(),
  getTemplatesSummary: vi.fn(),
  listOrders: vi.fn(),
  listSellers: vi.fn(),
}));
vi.mock('@/features/ecommerce-ops/api/products-api', () => ({ getProductsSummary: m.getProductsSummary }));
vi.mock('@/features/ecommerce-ops/api/orders-api', () => ({
  getOrdersSummary: m.getOrdersSummary,
  getOrderInsights: m.getOrderInsights,
  listOrders: m.listOrders,
}));
vi.mock('@/features/ecommerce-ops/api/users-api', () => ({ getUsersSummary: m.getUsersSummary }));
vi.mock('@/features/ecommerce-ops/api/promotions-api', () => ({ getPromotionsSummary: m.getPromotionsSummary }));
vi.mock('@/features/ecommerce-ops/api/shippings-api', () => ({ getShippingsSummary: m.getShippingsSummary }));
vi.mock('@/features/ecommerce-ops/api/sellers-api', () => ({
  getSellersSummary: m.getSellersSummary,
  listSellers: m.listSellers,
}));
vi.mock('@/features/ecommerce-ops/api/notifications-api', () => ({ getTemplatesSummary: m.getTemplatesSummary }));

import { getEcommerceOverviewState } from '@/features/ecommerce-ops/api/overview-state';

const summary = (today: number, week: number, month: number, total: number) => ({
  today,
  week,
  month,
  total,
});

const list = (totalElements: number, content: unknown[] = []) => ({
  content,
  page: 0,
  size: content.length || 1,
  totalElements,
});

const orderRow = (orderId: string) => ({
  orderId,
  userId: 'u1',
  status: 'PENDING',
  totalPrice: 1000,
  itemCount: 2,
  firstItemName: '상품A',
  createdAt: '2026-07-01T00:00:00Z',
});
const sellerRow = (sellerId: string, displayName = '셀러A') => ({
  sellerId,
  displayName,
  status: 'ACTIVE',
  createdAt: '2026-07-01T00:00:00Z',
});

/** Insights fixture — seller rankings carry the RAW id as label (the overlay
 *  in the fan-out replaces it with the resolved displayName when available). */
const insightsFixture = () => ({
  topProductsByOrderCount: [{ id: 'p1', label: '상품P', value: 50 }],
  topProductsByRevenue: [{ id: 'p1', label: '상품P', value: 500000 }],
  topSellersByOrderCount: [{ id: 's1', label: 's1', value: 40 }],
  topSellersByRevenue: [{ id: 's1', label: 's1', value: 400000 }],
});

/** Default happy fan-out: every leg resolves. */
function seedHappy() {
  m.getProductsSummary.mockResolvedValue(summary(1, 5, 11, 100));
  m.getUsersSummary.mockResolvedValue(summary(2, 10, 22, 200));
  m.getPromotionsSummary.mockResolvedValue(summary(3, 15, 33, 300));
  m.getShippingsSummary.mockResolvedValue(summary(4, 20, 44, 400));
  m.getSellersSummary.mockResolvedValue(summary(0, 2, 9, 90));
  m.getTemplatesSummary.mockResolvedValue(summary(0, 3, 55, 550));
  m.getOrdersSummary.mockResolvedValue(summary(5, 25, 7, 70));
  m.getOrderInsights.mockResolvedValue(insightsFixture());
  // size===5 → the recent-5 sellers cell; size===100 → the top-volume
  // name-map cell (sellerId → displayName), with s1 named "셀러 원".
  m.listSellers.mockImplementation((p: { size?: number } = {}) =>
    Promise.resolve(
      p.size === 5
        ? list(9, [sellerRow('s1'), sellerRow('s2')])
        : p.size === 100
          ? list(2, [sellerRow('s1', '셀러 원'), sellerRow('s2', '셀러 투')])
          : list(9),
    ),
  );
  m.listOrders.mockImplementation(
    (p: { status?: string; size?: number } = {}) => {
      if (p.status) return Promise.resolve(list(p.status === 'PENDING' ? 3 : 1));
      if (p.size === 5)
        return Promise.resolve(list(7, [orderRow('o1'), orderRow('o2')]));
      return Promise.resolve(list(7));
    },
  );
}

beforeEach(() => {
  vi.clearAllMocks();
});

describe('getEcommerceOverviewState (TASK-PC-FE-156 / TASK-PC-FE-164)', () => {
  it('not eligible → no fan-out, notEligible flag', async () => {
    const state = await getEcommerceOverviewState(false);
    expect(state.notEligible).toBe(true);
    expect(state.counts).toHaveLength(0);
    expect(m.getProductsSummary).not.toHaveBeenCalled();
    expect(m.getOrdersSummary).not.toHaveBeenCalled();
  });

  it('happy → maps period counts (today/week/month) + total + order-status distribution + recent slices', async () => {
    seedHappy();
    const state = await getEcommerceOverviewState(true);

    expect(state.notEligible).toBe(false);
    // 7 area counts, each ok with the right period values.
    const byKey = Object.fromEntries(state.counts.map((c) => [c.key, c]));

    // products: summary(1, 5, 11, 100)
    expect(byKey.products.status).toBe('ok');
    expect(byKey.products.today).toBe(1);
    expect(byKey.products.week).toBe(5);
    expect(byKey.products.month).toBe(11);
    expect(byKey.products.count).toBe(100); // total → back-compat `count`

    // users: summary(2, 10, 22, 200)
    expect(byKey.users.today).toBe(2);
    expect(byKey.users.week).toBe(10);
    expect(byKey.users.month).toBe(22);
    expect(byKey.users.count).toBe(200);

    // notifications: summary(0, 3, 55, 550)
    expect(byKey.notifications.today).toBe(0);
    expect(byKey.notifications.count).toBe(550);

    // sellers: summary(0, 2, 9, 90)
    expect(byKey.sellers.count).toBe(90);

    // Each area card carries its quick-launch href + back-compat testid.
    expect(byKey.products.href).toBe('/ecommerce/products');
    expect(byKey.products.testid).toBe('ecommerce-products-link');

    // Order-status distribution: one bucket per ORDER_STATUS_VALUES.
    expect(state.orderStatus).toHaveLength(ORDER_STATUS_VALUES.length);
    const pending = state.orderStatus.find((b) => b.status === 'PENDING');
    expect(pending?.count).toBe(3);
    expect(pending?.cellStatus).toBe('ok');

    // Recent slices from the size=5 reads.
    expect(state.recentOrders).toHaveLength(2);
    expect(state.recentOrdersStatus).toBe('ok');
    expect(state.recentSellers).toHaveLength(2);
    expect(state.recentSellersStatus).toBe('ok');
  });

  it('zero summary values render ok (not degraded)', async () => {
    seedHappy();
    m.getProductsSummary.mockResolvedValue(summary(0, 0, 0, 0));
    const state = await getEcommerceOverviewState(true);
    const products = state.counts.find((c) => c.key === 'products')!;
    expect(products.status).toBe('ok');
    expect(products.today).toBe(0);
    expect(products.count).toBe(0);
  });

  it('per-cell degrade: a 503 leg → that count null/degraded, period fields null, others unaffected', async () => {
    seedHappy();
    m.getPromotionsSummary.mockRejectedValue(
      new EcommerceUnavailableError('downstream', 'ECOMMERCE_UNAVAILABLE', 'down'),
    );

    const state = await getEcommerceOverviewState(true);
    const promo = state.counts.find((c) => c.key === 'promotions')!;
    expect(promo.count).toBeNull();
    expect(promo.today).toBeNull();
    expect(promo.week).toBeNull();
    expect(promo.month).toBeNull();
    expect(promo.status).toBe('degraded');
    // A sibling stays ok.
    expect(state.counts.find((c) => c.key === 'products')!.status).toBe('ok');
  });

  it('per-cell forbidden: a 403 leg → that count null/forbidden, period fields null', async () => {
    seedHappy();
    m.getUsersSummary.mockRejectedValue(new ApiError(403, 'ACCESS_DENIED', 'no'));

    const state = await getEcommerceOverviewState(true);
    const users = state.counts.find((c) => c.key === 'users')!;
    expect(users.count).toBeNull();
    expect(users.today).toBeNull();
    expect(users.status).toBe('forbidden');
  });

  it('401 in any leg → whole-session redirect(/login) (not a per-cell degrade)', async () => {
    seedHappy();
    m.getSellersSummary.mockRejectedValue(new ApiError(401, 'TOKEN_INVALID', 'exp'));

    await expect(getEcommerceOverviewState(true)).rejects.toThrow(
      'REDIRECT:/login',
    );
    expect(redirectMock).toHaveBeenCalledWith('/login');
  });

  // ── TASK-PC-FE-170 — insights leg + seller-name overlay ──────────────────

  it('insights ok → populated + seller labels overlaid from the name map', async () => {
    seedHappy();
    const state = await getEcommerceOverviewState(true);

    expect(state.insightsStatus).toBe('ok');
    expect(state.insights).not.toBeNull();
    // Product rankings pass through unchanged.
    expect(state.insights!.topProductsByOrderCount).toEqual([
      { id: 'p1', label: '상품P', value: 50 },
    ]);
    // Seller rankings: raw id label 's1' overlaid with the resolved displayName.
    expect(state.insights!.topSellersByOrderCount[0].label).toBe('셀러 원');
    expect(state.insights!.topSellersByOrderCount[0].value).toBe(40);
    expect(state.insights!.topSellersByRevenue[0].label).toBe('셀러 원');
    // A dedicated size=100 seller fetch backs the name map (not the recent-5).
    expect(m.listSellers).toHaveBeenCalledWith({ page: 0, size: 100 });
  });

  it('insights 403 → insightsStatus forbidden, insights null (charts unaffected)', async () => {
    seedHappy();
    m.getOrderInsights.mockRejectedValue(new ApiError(403, 'ACCESS_DENIED', 'no'));

    const state = await getEcommerceOverviewState(true);
    expect(state.insightsStatus).toBe('forbidden');
    expect(state.insights).toBeNull();
    // The rest of the snapshot still resolves.
    expect(state.counts.find((c) => c.key === 'products')!.status).toBe('ok');
  });

  it('insights 503 → insightsStatus degraded, insights null', async () => {
    seedHappy();
    m.getOrderInsights.mockRejectedValue(
      new EcommerceUnavailableError('downstream', 'ECOMMERCE_UNAVAILABLE', 'down'),
    );

    const state = await getEcommerceOverviewState(true);
    expect(state.insightsStatus).toBe('degraded');
    expect(state.insights).toBeNull();
  });

  it('seller-name leg degraded → seller labels fall back to the raw id (never blank)', async () => {
    seedHappy();
    // The size=100 name-map leg fails; the recent-5 (size=5) leg still resolves.
    m.listSellers.mockImplementation((p: { size?: number } = {}) => {
      if (p.size === 100)
        return Promise.reject(
          new EcommerceUnavailableError('downstream', 'ECOMMERCE_UNAVAILABLE', 'down'),
        );
      return Promise.resolve(list(9, [sellerRow('s1'), sellerRow('s2')]));
    });

    const state = await getEcommerceOverviewState(true);
    expect(state.insightsStatus).toBe('ok');
    // No name map → the raw sellerId stays the label (never blank).
    expect(state.insights!.topSellersByOrderCount[0].label).toBe('s1');
    expect(state.insights!.topSellersByRevenue[0].label).toBe('s1');
    // The recent-sellers panel is unaffected by the name-map degrade.
    expect(state.recentSellersStatus).toBe('ok');
  });
});
