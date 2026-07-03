import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render } from '@testing-library/react';
import { ApiError } from '@/shared/api/errors';

/**
 * TASK-PC-FE-118 originally parallelised the `/ecommerce` SSR fetch by firing
 * the public domain-health fan-out up-front, concurrently with the eligibility
 * pre-flight. TASK-PC-FE-170 REMOVED the domain-health leg from this page (the
 * per-area service-status dots on each count card supersede the old
 * `DomainHealthCard`), so there is no longer a second up-front leg to race.
 *
 * This suite now pins the surviving SSR contract: the eligibility pre-flight
 * (`getCatalog`) fires, and only on the eligible branch is the operator
 * overview fan-out (`getEcommerceOverviewState`) invoked — the gated branches
 * (not-eligible / registry-degraded / 401) render unchanged and never call it.
 */

const getCatalog = vi.fn();
const redirect = vi.fn((path: string) => {
  throw new Error(`NEXT_REDIRECT:${path}`);
});

vi.mock('@/features/catalog', () => ({ getCatalog: () => getCatalog() }));
vi.mock('next/navigation', () => ({ redirect: (p: string) => redirect(p) }));
vi.mock('next/link', () => ({
  default: ({ href, children }: { href: string; children: React.ReactNode }) => (
    <a href={href}>{children}</a>
  ),
}));

// Overview snapshot is unit-tested elsewhere; stub it so this suite stays
// focused on the page's eligibility gating.
const getEcommerceOverviewState = vi.fn().mockResolvedValue({
  notEligible: false,
  counts: [],
  orderStatus: [],
  recentOrders: null,
  recentOrdersStatus: 'ok',
  recentSellers: null,
  recentSellersStatus: 'ok',
  insights: null,
  insightsStatus: 'ok',
});
vi.mock('@/features/ecommerce-ops', () => ({
  getEcommerceOverviewState: () => getEcommerceOverviewState(),
  EcommerceOverview: () => <div data-testid="ecommerce-overview" />,
}));

import EcommercePage from '@/app/(console)/ecommerce/page';

const ELIGIBLE_CATALOG = {
  degraded: false,
  products: [
    { productKey: 'ecommerce', available: true, tenants: ['shop-a'] },
  ],
};

beforeEach(() => {
  getCatalog.mockReset();
  getEcommerceOverviewState.mockClear();
  redirect.mockClear();
});

describe('EcommercePage — SSR gating (TASK-PC-FE-118 / TASK-PC-FE-170)', () => {
  it('fires the catalog eligibility pre-flight on entry', () => {
    getCatalog.mockReturnValue(new Promise(() => {}));

    void EcommercePage();

    expect(getCatalog).toHaveBeenCalledTimes(1);
  });

  it('renders the overview on the eligible path (no domain-health leg)', async () => {
    getCatalog.mockResolvedValue(ELIGIBLE_CATALOG);

    const ui = await EcommercePage();
    const { getByTestId } = render(ui);
    expect(getByTestId('ecommerce-section')).toBeInTheDocument();
    expect(getByTestId('ecommerce-overview')).toBeInTheDocument();
    expect(getEcommerceOverviewState).toHaveBeenCalledTimes(1);
  });

  it('renders the not-eligible gate unchanged; the overview fan-out is not fired', async () => {
    getCatalog.mockResolvedValue({
      degraded: false,
      products: [{ productKey: 'ecommerce', available: false, tenants: [] }],
    });

    const ui = await EcommercePage();
    const { getByTestId, queryByTestId } = render(ui);
    expect(getByTestId('ecommerce-not-eligible')).toBeInTheDocument();
    expect(queryByTestId('ecommerce-overview')).toBeNull();
    expect(getEcommerceOverviewState).not.toHaveBeenCalled();
  });

  it('renders the registry-degraded gate unchanged', async () => {
    getCatalog.mockResolvedValue({ degraded: true, products: [] });

    const ui = await EcommercePage();
    const { getByTestId, queryByTestId } = render(ui);
    expect(getByTestId('ecommerce-degraded')).toBeInTheDocument();
    expect(queryByTestId('ecommerce-overview')).toBeNull();
  });

  it('redirects to /login on a catalog 401', async () => {
    getCatalog.mockRejectedValue(new ApiError(401, 'TOKEN_INVALID', 'nope'));

    await expect(EcommercePage()).rejects.toThrow('NEXT_REDIRECT:/login');
    expect(redirect).toHaveBeenCalledWith('/login');
  });
});
