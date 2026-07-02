import { describe, it, expect, vi, beforeEach } from 'vitest';
import { ApiError, EcommerceUnavailableError } from '@/shared/api/errors';
import { ORDER_STATUS_VALUES } from '@/features/ecommerce-ops/api/order-types';

/**
 * TASK-PC-FE-156 / TASK-PC-FE-160 — `getEcommerceOverviewState` server fan-out.
 * PC-FE-160 switches the 7 area count legs from `list*({page:0,size:1})` to
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
const sellerRow = (sellerId: string) => ({
  sellerId,
  displayName: '셀러A',
  status: 'ACTIVE',
  createdAt: '2026-07-01T00:00:00Z',
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
  m.listSellers.mockImplementation((p: { size?: number } = {}) =>
    Promise.resolve(
      p.size === 5 ? list(9, [sellerRow('s1'), sellerRow('s2')]) : list(9),
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

describe('getEcommerceOverviewState (TASK-PC-FE-156 / TASK-PC-FE-160)', () => {
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
});
