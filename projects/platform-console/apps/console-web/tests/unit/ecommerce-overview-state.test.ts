import { describe, it, expect, vi, beforeEach } from 'vitest';
import { ApiError, EcommerceUnavailableError } from '@/shared/api/errors';
import { ORDER_STATUS_VALUES } from '@/features/ecommerce-ops/api/order-types';

/**
 * TASK-PC-FE-156 — `getEcommerceOverviewState` server fan-out over the existing
 * ecommerce list endpoints (console-web direct; ADR-MONO-017 D3.B — no producer
 * `/summary`). Covers: not-eligible short-circuit, count mapping from
 * `totalElements`, order-status distribution, recent slices, per-cell
 * degrade/forbidden, and the whole-session 401 redirect.
 */

const { redirectMock } = vi.hoisted(() => ({ redirectMock: vi.fn() }));
vi.mock('next/navigation', () => ({
  redirect: (p: string) => {
    redirectMock(p);
    throw new Error(`REDIRECT:${p}`);
  },
}));

const m = vi.hoisted(() => ({
  listProducts: vi.fn(),
  listOrders: vi.fn(),
  listUsers: vi.fn(),
  listPromotions: vi.fn(),
  listShippings: vi.fn(),
  listSellers: vi.fn(),
  listTemplates: vi.fn(),
}));
vi.mock('@/features/ecommerce-ops/api/products-api', () => ({ listProducts: m.listProducts }));
vi.mock('@/features/ecommerce-ops/api/orders-api', () => ({ listOrders: m.listOrders }));
vi.mock('@/features/ecommerce-ops/api/users-api', () => ({ listUsers: m.listUsers }));
vi.mock('@/features/ecommerce-ops/api/promotions-api', () => ({ listPromotions: m.listPromotions }));
vi.mock('@/features/ecommerce-ops/api/shippings-api', () => ({ listShippings: m.listShippings }));
vi.mock('@/features/ecommerce-ops/api/sellers-api', () => ({ listSellers: m.listSellers }));
vi.mock('@/features/ecommerce-ops/api/notifications-api', () => ({ listTemplates: m.listTemplates }));

import { getEcommerceOverviewState } from '@/features/ecommerce-ops/api/overview-state';

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
  m.listProducts.mockResolvedValue(list(11));
  m.listUsers.mockResolvedValue(list(22));
  m.listPromotions.mockResolvedValue(list(33));
  m.listShippings.mockResolvedValue(list(44));
  m.listSellers.mockImplementation((p: { size?: number } = {}) =>
    Promise.resolve(
      p.size === 5 ? list(9, [sellerRow('s1'), sellerRow('s2')]) : list(9),
    ),
  );
  m.listTemplates.mockResolvedValue(list(55));
  m.listOrders.mockImplementation(
    (p: { status?: string; size?: number } = {}) => {
      if (p.status) return Promise.resolve(list(p.status === 'PENDING' ? 3 : 1));
      if (p.size === 5)
        return Promise.resolve(list(7, [orderRow('o1'), orderRow('o2')]));
      return Promise.resolve(list(7)); // total count
    },
  );
}

beforeEach(() => {
  vi.clearAllMocks();
});

describe('getEcommerceOverviewState (TASK-PC-FE-156)', () => {
  it('not eligible → no fan-out, notEligible flag', async () => {
    const state = await getEcommerceOverviewState(false);
    expect(state.notEligible).toBe(true);
    expect(state.counts).toHaveLength(0);
    expect(m.listProducts).not.toHaveBeenCalled();
    expect(m.listOrders).not.toHaveBeenCalled();
  });

  it('happy → maps counts, order-status distribution, and recent slices', async () => {
    seedHappy();
    const state = await getEcommerceOverviewState(true);

    expect(state.notEligible).toBe(false);
    // 7 area counts, each ok with the right totalElements.
    const byKey = Object.fromEntries(state.counts.map((c) => [c.key, c]));
    expect(byKey.products.count).toBe(11);
    expect(byKey.products.status).toBe('ok');
    expect(byKey.users.count).toBe(22);
    expect(byKey.notifications.count).toBe(55);
    expect(byKey.sellers.count).toBe(9);
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

  it('per-cell degrade: a 503 leg → that count null/degraded, others unaffected', async () => {
    seedHappy();
    m.listPromotions.mockRejectedValue(
      new EcommerceUnavailableError('downstream', 'ECOMMERCE_UNAVAILABLE', 'down'),
    );

    const state = await getEcommerceOverviewState(true);
    const promo = state.counts.find((c) => c.key === 'promotions')!;
    expect(promo.count).toBeNull();
    expect(promo.status).toBe('degraded');
    // A sibling stays ok.
    expect(state.counts.find((c) => c.key === 'products')!.status).toBe('ok');
  });

  it('per-cell forbidden: a 403 leg → that count null/forbidden', async () => {
    seedHappy();
    m.listUsers.mockRejectedValue(new ApiError(403, 'ACCESS_DENIED', 'no'));

    const state = await getEcommerceOverviewState(true);
    const users = state.counts.find((c) => c.key === 'users')!;
    expect(users.count).toBeNull();
    expect(users.status).toBe('forbidden');
  });

  it('401 in any leg → whole-session redirect(/login) (not a per-cell degrade)', async () => {
    seedHappy();
    m.listSellers.mockRejectedValue(new ApiError(401, 'TOKEN_INVALID', 'exp'));

    await expect(getEcommerceOverviewState(true)).rejects.toThrow(
      'REDIRECT:/login',
    );
    expect(redirectMock).toHaveBeenCalledWith('/login');
  });
});
