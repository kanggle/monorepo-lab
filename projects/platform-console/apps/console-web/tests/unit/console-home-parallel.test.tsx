import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render } from '@testing-library/react';
import { ApiError } from '@/shared/api/errors';

/**
 * TASK-PC-FE-117 — `/console` (catalog home) SSR fetch is parallelised: the
 * catalog (IAM registry) and the per-domain health fan-out are fired
 * concurrently instead of `await getCatalog(); await getDomainHealthState();`.
 *
 * Same concurrency proof as the overview sibling: the async page body runs
 * synchronously to its first `await`, by which point BOTH fetchers must have
 * been invoked. The old waterfall would only call `getDomainHealthState`
 * after the catalog promise resolved.
 */

const getCatalog = vi.fn();
const getDomainHealthState = vi.fn();
const redirect = vi.fn((path: string) => {
  throw new Error(`NEXT_REDIRECT:${path}`);
});

vi.mock('@/features/catalog', () => ({
  getCatalog: () => getCatalog(),
  ServiceCatalog: ({
    healthByDomain,
  }: {
    healthByDomain: Record<string, string>;
  }) => (
    <div
      data-testid="service-catalog"
      data-dots={Object.keys(healthByDomain).join(',')}
    />
  ),
}));
vi.mock('@/features/domain-health', () => ({
  getDomainHealthState: () => getDomainHealthState(),
  healthTone: () => 'ok',
}));
vi.mock('next/navigation', () => ({ redirect: (p: string) => redirect(p) }));

import ConsoleHomePage from '@/app/(console)/console/page';

beforeEach(() => {
  getCatalog.mockReset();
  getDomainHealthState.mockReset();
  redirect.mockClear();
});

describe('ConsoleHomePage — parallel SSR fetch (TASK-PC-FE-117)', () => {
  it('fires the catalog and domain-health fetches concurrently (no waterfall)', () => {
    getCatalog.mockReturnValue(new Promise(() => {}));
    getDomainHealthState.mockReturnValue(new Promise(() => {}));

    void ConsoleHomePage();

    expect(getCatalog).toHaveBeenCalledTimes(1);
    // Regression to the waterfall would leave this at 0 until catalog resolves.
    expect(getDomainHealthState).toHaveBeenCalledTimes(1);
  });

  it('renders the catalog with per-domain health dots on the success path', async () => {
    getCatalog.mockResolvedValue({ products: [], degraded: false });
    getDomainHealthState.mockResolvedValue({
      health: { cards: [{ domain: 'wms' }, { domain: 'finance' }] },
      noTenant: false,
      unauthorized: false,
      bffUnavailable: false,
    });

    const ui = await ConsoleHomePage();
    const { getByTestId } = render(ui);
    const el = getByTestId('service-catalog');
    expect(el).toBeInTheDocument();
    expect(el).toHaveAttribute('data-dots', 'wms,finance');
  });

  it('renders the catalog without dots when health degrades (null health)', async () => {
    getCatalog.mockResolvedValue({ products: [], degraded: false });
    getDomainHealthState.mockResolvedValue({
      health: null,
      noTenant: false,
      unauthorized: false,
      bffUnavailable: true,
    });

    const ui = await ConsoleHomePage();
    const { getByTestId } = render(ui);
    expect(getByTestId('service-catalog')).toHaveAttribute('data-dots', '');
  });

  it('redirects to /login on catalog 401; the un-awaited health promise raises no unhandled rejection', async () => {
    getCatalog.mockRejectedValue(new ApiError(401, 'TOKEN_INVALID', 'nope'));
    getDomainHealthState.mockReturnValue(Promise.reject(new Error('ignored')));

    await expect(ConsoleHomePage()).rejects.toThrow('NEXT_REDIRECT:/login');
    expect(redirect).toHaveBeenCalledWith('/login');
  });

  it('re-throws a non-401 catalog error (no redirect)', async () => {
    getCatalog.mockRejectedValue(new ApiError(500, 'BOOM', 'server'));
    getDomainHealthState.mockReturnValue(new Promise(() => {}));

    await expect(ConsoleHomePage()).rejects.toThrow('server');
    expect(redirect).not.toHaveBeenCalled();
  });
});
