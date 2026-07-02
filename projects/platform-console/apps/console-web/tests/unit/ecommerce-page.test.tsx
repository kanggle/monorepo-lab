import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, cleanup } from '@testing-library/react';
import type { ReactNode } from 'react';
import { ApiError } from '@/shared/api/errors';
import type { CatalogState } from '@/shared/api/registry-types';
import type {
  DomainHealth,
  DomainHealthState,
} from '@/features/domain-health';

/**
 * TASK-MONO-241 — the `/ecommerce` drill-in route (ADR-MONO-030 Step 4 facet
 * a-후속). Closes the "catalog ecommerce tile (`baseRoute=/ecommerce`) click →
 * 404" gap MONO-240 left open. Surfaces the ecommerce domain-health card + the
 * operator overview snapshot (`EcommerceOverview`, TASK-PC-FE-156 — replaced the
 * PC-FE-155 plain quick-launch grid; the overview's own rendering is unit-tested
 * in `ecommerce-overview.test.tsx`, so here it is stubbed). STRICTLY READ-ONLY;
 * degrade-safe like scm/wms.
 *
 * Asserts:
 *   - eligible → renders the ecommerce section + the health card + the overview.
 *   - registry degraded → degraded note (never blocks on an unproven negative).
 *   - not eligible → "no ecommerce-scoped access" note (does not crash).
 *   - registry 401 → redirect('/login').
 *   - health bff unavailable → section still renders + health-unavailable note
 *     + the overview (not health-gated, degrade-safe).
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

const { getCatalogMock, getDomainHealthStateMock } = vi.hoisted(() => ({
  getCatalogMock: vi.fn(),
  getDomainHealthStateMock: vi.fn(),
}));
vi.mock('@/features/catalog', () => ({
  getCatalog: getCatalogMock,
}));
vi.mock('@/features/domain-health', async (importOriginal) => {
  const actual =
    await importOriginal<typeof import('@/features/domain-health')>();
  return {
    ...actual,
    getDomainHealthState: getDomainHealthStateMock,
  };
});

// The overview snapshot is unit-tested separately; stub it here so the page
// test stays focused on the section's eligibility/degrade/health branches.
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

const HEALTH: DomainHealth = {
  asOf: '2026-06-13T00:00:00Z',
  cards: [
    { domain: 'iam', status: 'ok', data: { status: 'UP' } },
    { domain: 'wms', status: 'ok', data: { status: 'UP' } },
    { domain: 'scm', status: 'ok', data: { status: 'UP' } },
    { domain: 'finance', status: 'ok', data: { status: 'UP' } },
    { domain: 'erp', status: 'ok', data: { status: 'UP' } },
    { domain: 'ecommerce', status: 'ok', data: { status: 'UP' } },
  ],
};

function healthState(over: Partial<DomainHealthState>): DomainHealthState {
  return {
    health: HEALTH,
    noTenant: false,
    unauthorized: false,
    bffUnavailable: false,
    ...over,
  };
}

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
  });
});

async function renderPage() {
  render(await EcommercePage());
}

describe('EcommercePage (TASK-MONO-241 /ecommerce drill-in)', () => {
  it('eligible → renders the section, the health card, and the overview (no 준비중 note)', async () => {
    getCatalogMock.mockResolvedValue(ELIGIBLE_CATALOG);
    getDomainHealthStateMock.mockResolvedValue(healthState({}));

    await renderPage();

    expect(screen.getByTestId('ecommerce-section')).toBeInTheDocument();
    expect(
      screen.getByRole('heading', { name: 'E-Commerce 운영' }),
    ).toBeInTheDocument();
    // The 6th domain-health card renders inside the section.
    expect(
      screen.getByTestId('domain-health-card-ecommerce'),
    ).toBeInTheDocument();
    // The operator overview snapshot is composed onto the eligible path.
    expect(screen.getByTestId('ecommerce-overview')).toBeInTheDocument();
    expect(getEcommerceOverviewStateMock).toHaveBeenCalledWith(true);
    // The stale "준비중" coming-soon note is gone.
    expect(screen.queryByTestId('ecommerce-ops-coming-soon')).toBeNull();
  });

  it('registry degraded → degraded note (never blocks on an unproven negative)', async () => {
    getCatalogMock.mockResolvedValue({ products: [], degraded: true });
    // TASK-PC-FE-118 — health is now fired speculatively up-front (concurrently
    // with the catalog eligibility pre-flight) to remove the SSR waterfall, so
    // it MAY be called on this gated branch. The decisive assertion is that the
    // gated render OUTPUT is unaffected by the speculative call.
    getDomainHealthStateMock.mockResolvedValue(healthState({}));

    await renderPage();

    expect(screen.getByTestId('ecommerce-degraded')).toBeInTheDocument();
    // The speculative health result does not leak into the degraded render.
    expect(screen.queryByTestId('domain-health-card-ecommerce')).toBeNull();
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

  it('health bff unavailable → section renders + health-unavailable note (degrade-safe)', async () => {
    getCatalogMock.mockResolvedValue(ELIGIBLE_CATALOG);
    getDomainHealthStateMock.mockResolvedValue(
      healthState({ health: null, bffUnavailable: true }),
    );

    await renderPage();

    expect(screen.getByTestId('ecommerce-section')).toBeInTheDocument();
    expect(
      screen.getByTestId('ecommerce-health-unavailable'),
    ).toBeInTheDocument();
    // The shell + the overview still render — the overview is not health-gated,
    // so a health degrade never blanks the operator snapshot.
    expect(screen.getByTestId('ecommerce-overview')).toBeInTheDocument();
    expect(screen.queryByTestId('ecommerce-ops-coming-soon')).toBeNull();
  });
});
