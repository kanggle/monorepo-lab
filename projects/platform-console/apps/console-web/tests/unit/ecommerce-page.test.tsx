import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, cleanup } from '@testing-library/react';
import type { ReactNode } from 'react';
import { ApiError } from '@/shared/api/errors';
import type { CatalogState } from '@/shared/api/registry-types';

/**
 * TASK-MONO-241 — the `/ecommerce` drill-in route (ADR-MONO-030 Step 4 facet
 * a-후속). Closes the "catalog ecommerce tile (`baseRoute=/ecommerce`) click →
 * 404" gap MONO-240 left open. Surfaces the operator overview snapshot
 * (`EcommerceOverview`, TASK-PC-FE-156 — replaced the PC-FE-155 plain
 * quick-launch grid; the overview's own rendering is unit-tested in
 * `ecommerce-overview.test.tsx`, so here it is stubbed). STRICTLY READ-ONLY;
 * degrade-safe like scm/wms.
 *
 * TASK-PC-FE-172 removed the former 도메인 상태 `DomainHealthCard` block from
 * this page (the per-area service-status dots on each count card supersede it),
 * so there is no longer a domain-health leg here — 401 safety is preserved by
 * `getCatalog()`'s 401→/login and the overview leg's own 401→/login.
 *
 * Asserts:
 *   - eligible → renders the ecommerce section + the overview.
 *   - registry degraded → degraded note (never blocks on an unproven negative).
 *   - not eligible → "no ecommerce-scoped access" note (does not crash).
 *   - registry 401 → redirect('/login').
 */

const { redirectMock } = vi.hoisted(() => ({ redirectMock: vi.fn() }));
vi.mock('next/navigation', () => ({
  redirect: (path: string) => {
    redirectMock(path);
    throw new Error(`REDIRECT:${path}`);
  },
}));
vi.mock('next/link', () => ({
  default: ({
    children,
    href,
    ...rest
  }: {
    children: ReactNode;
    href: string;
  } & Record<string, unknown>) => (
    <a href={href} {...rest}>
      {children}
    </a>
  ),
}));

const { getCatalogMock } = vi.hoisted(() => ({
  getCatalogMock: vi.fn(),
}));
vi.mock('@/features/catalog', () => ({
  getCatalog: getCatalogMock,
}));

// The overview snapshot is unit-tested separately; stub it here so the page
// test stays focused on the section's eligibility/degrade branches.
const { getEcommerceOverviewStateMock } = vi.hoisted(() => ({
  getEcommerceOverviewStateMock: vi.fn(),
}));
vi.mock('@/features/ecommerce-ops', () => ({
  getEcommerceOverviewState: getEcommerceOverviewStateMock,
  EcommerceOverview: () => <div data-testid="ecommerce-overview" />,
}));

import EcommercePage from '@/app/(console)/ecommerce/page';

const ELIGIBLE_CATALOG: CatalogState = {
  degraded: false,
  products: [
    {
      productKey: 'ecommerce',
      displayName: 'E-Commerce',
      available: true,
      tenants: ['acme-corp'],
      baseRoute: '/ecommerce',
    },
  ],
};

beforeEach(() => {
  vi.clearAllMocks();
  cleanup();
  // Default: overview fan-out resolves to an (empty) snapshot on the eligible path.
  getEcommerceOverviewStateMock.mockResolvedValue({
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
});

async function renderPage() {
  render(await EcommercePage());
}

describe('EcommercePage (TASK-MONO-241 /ecommerce drill-in)', () => {
  it('eligible → renders the section and the overview (no 도메인 상태 / 준비중 note)', async () => {
    getCatalogMock.mockResolvedValue(ELIGIBLE_CATALOG);

    await renderPage();

    expect(screen.getByTestId('ecommerce-section')).toBeInTheDocument();
    expect(
      screen.getByRole('heading', { name: 'E-Commerce 개요' }),
    ).toBeInTheDocument();
    // The operator overview snapshot is composed onto the eligible path.
    expect(screen.getByTestId('ecommerce-overview')).toBeInTheDocument();
    expect(getEcommerceOverviewStateMock).toHaveBeenCalledWith(true);
    // The removed 도메인 상태 block no longer renders a health card.
    expect(screen.queryByTestId('domain-health-card-ecommerce')).toBeNull();
    expect(screen.queryByText('도메인 상태')).toBeNull();
    // The stale "준비중" coming-soon note is gone.
    expect(screen.queryByTestId('ecommerce-ops-coming-soon')).toBeNull();
  });

  it('registry degraded → degraded note (never blocks on an unproven negative)', async () => {
    getCatalogMock.mockResolvedValue({ products: [], degraded: true });

    await renderPage();

    expect(screen.getByTestId('ecommerce-degraded')).toBeInTheDocument();
    expect(screen.queryByTestId('ecommerce-overview')).toBeNull();
  });

  it('not eligible (no ecommerce product) → not-eligible note, does not crash', async () => {
    getCatalogMock.mockResolvedValue({ products: [], degraded: false });

    await renderPage();

    expect(screen.getByTestId('ecommerce-not-eligible')).toBeInTheDocument();
  });

  it('registry 401 → redirect to /login (no partial authed state)', async () => {
    getCatalogMock.mockRejectedValue(
      new ApiError(401, 'TOKEN_INVALID', 'expired'),
    );

    await expect(renderPage()).rejects.toThrow('REDIRECT:/login');
    expect(redirectMock).toHaveBeenCalledWith('/login');
  });
});
