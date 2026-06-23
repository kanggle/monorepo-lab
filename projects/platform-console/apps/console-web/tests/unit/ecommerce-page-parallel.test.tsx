import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render } from '@testing-library/react';
import { ApiError } from '@/shared/api/errors';

/**
 * TASK-PC-FE-118 — `/ecommerce` SSR fetch is parallelised: the public
 * domain-health fan-out is fired up-front, concurrently with the eligibility
 * pre-flight (`getCatalog`), instead of sequentially in the eligible branch.
 *
 * Same concurrency proof as TASK-PC-FE-117: the async page body runs
 * synchronously to its first `await`, by which point BOTH `getDomainHealthState`
 * and `getCatalog` must have been invoked. The old waterfall would only call
 * `getDomainHealthState` after the catalog resolved.
 */

const getCatalog = vi.fn();
const getDomainHealthState = vi.fn();
const redirect = vi.fn((path: string) => {
  throw new Error(`NEXT_REDIRECT:${path}`);
});

vi.mock('@/features/catalog', () => ({ getCatalog: () => getCatalog() }));
vi.mock('@/features/domain-health', () => ({
  getDomainHealthState: () => getDomainHealthState(),
  DomainHealthCard: () => <div data-testid="ecommerce-health-card" />,
}));
vi.mock('next/navigation', () => ({ redirect: (p: string) => redirect(p) }));
vi.mock('next/link', () => ({
  default: ({ href, children }: { href: string; children: React.ReactNode }) => (
    <a href={href}>{children}</a>
  ),
}));

import EcommercePage from '@/app/(console)/ecommerce/page';

const ELIGIBLE_CATALOG = {
  degraded: false,
  products: [
    { productKey: 'ecommerce', available: true, tenants: ['shop-a'] },
  ],
};
const HEALTH_WITH_ECOMMERCE = {
  health: { cards: [{ domain: 'ecommerce', status: 'ok' }] },
  noTenant: false,
  unauthorized: false,
  bffUnavailable: false,
};

beforeEach(() => {
  getCatalog.mockReset();
  getDomainHealthState.mockReset();
  redirect.mockClear();
});

describe('EcommercePage — parallel SSR fetch (TASK-PC-FE-118)', () => {
  it('fires domain-health concurrently with the catalog eligibility pre-flight (no waterfall)', () => {
    getCatalog.mockReturnValue(new Promise(() => {}));
    getDomainHealthState.mockReturnValue(new Promise(() => {}));

    void EcommercePage();

    expect(getCatalog).toHaveBeenCalledTimes(1);
    // The waterfall regression would leave this at 0 until catalog resolves.
    expect(getDomainHealthState).toHaveBeenCalledTimes(1);
  });

  it('renders the ecommerce health card on the eligible path', async () => {
    getCatalog.mockResolvedValue(ELIGIBLE_CATALOG);
    getDomainHealthState.mockResolvedValue(HEALTH_WITH_ECOMMERCE);

    const ui = await EcommercePage();
    const { getByTestId } = render(ui);
    expect(getByTestId('ecommerce-section')).toBeInTheDocument();
    expect(getByTestId('ecommerce-health-card')).toBeInTheDocument();
  });

  it('renders the not-eligible gate unchanged; the speculative health promise is ignored', async () => {
    getCatalog.mockResolvedValue({
      degraded: false,
      products: [{ productKey: 'ecommerce', available: false, tenants: [] }],
    });
    getDomainHealthState.mockResolvedValue(HEALTH_WITH_ECOMMERCE);

    const ui = await EcommercePage();
    const { getByTestId, queryByTestId } = render(ui);
    expect(getByTestId('ecommerce-not-eligible')).toBeInTheDocument();
    expect(queryByTestId('ecommerce-health-card')).toBeNull();
  });

  it('renders the registry-degraded gate unchanged', async () => {
    getCatalog.mockResolvedValue({ degraded: true, products: [] });
    getDomainHealthState.mockResolvedValue(HEALTH_WITH_ECOMMERCE);

    const ui = await EcommercePage();
    const { getByTestId, queryByTestId } = render(ui);
    expect(getByTestId('ecommerce-degraded')).toBeInTheDocument();
    expect(queryByTestId('ecommerce-health-card')).toBeNull();
  });

  it('redirects to /login on a catalog 401; the un-awaited health promise raises no unhandled rejection', async () => {
    getCatalog.mockRejectedValue(new ApiError(401, 'TOKEN_INVALID', 'nope'));
    getDomainHealthState.mockReturnValue(Promise.reject(new Error('ignored')));

    await expect(EcommercePage()).rejects.toThrow('NEXT_REDIRECT:/login');
    expect(redirect).toHaveBeenCalledWith('/login');
  });
});
