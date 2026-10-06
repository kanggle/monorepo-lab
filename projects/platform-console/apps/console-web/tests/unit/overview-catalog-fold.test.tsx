import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import type { ReactNode } from 'react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';

/**
 * TASK-PC-FE-310 — the fold-specific assertions for the old `/console`
 * catalog home now living inside `/dashboards/overview` (AC-2..AC-7). The
 * ORIGINAL concurrency proof (TASK-PC-FE-117, extended to the 3rd fetcher)
 * stays in `overview-page-parallel.test.tsx`; this file is scoped to the DOM
 * shape of each branch.
 *
 * `ServiceCatalog`/`CatalogGrid`/`ServiceTile` are rendered FOR REAL (not
 * stubbed) so `tile-<product>` / `tile-<product>-tenant-<id>` assertions —
 * the literal AC-3 wording — exercise the actual component tree, the same
 * way `ServiceCatalog.test.tsx` does. `deriveHealthByDomain`/`healthTone`
 * (`@/features/domain-health`) are ALSO real — only the two screen
 * components and the three data fetchers are mocked.
 */

const getOperatorOverviewState = vi.fn();
const getDomainHealthState = vi.fn();
const getCatalog = vi.fn();
const redirect = vi.fn((path: string) => {
  throw new Error(`NEXT_REDIRECT:${path}`);
});
const routerRefresh = vi.fn();

vi.mock('@/features/operator-overview', () => ({
  getOperatorOverviewState: () => getOperatorOverviewState(),
  OperatorOverviewScreen: () => <div data-testid="overview-screen" />,
}));
vi.mock('@/features/domain-health', async (importOriginal) => {
  const actual =
    await importOriginal<typeof import('@/features/domain-health')>();
  return {
    ...actual,
    getDomainHealthState: () => getDomainHealthState(),
    DomainHealthSummaryCard: () => <div data-testid="health-summary" />,
  };
});
vi.mock('@/features/catalog', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/features/catalog')>();
  return { ...actual, getCatalog: () => getCatalog() };
});
// Not under test here — the zero/select distinction is `no-tenant-notice.test.tsx`'s
// subject. Stubbed so the empty-catalog edge case doesn't need a real registry fetch.
vi.mock('@/widgets/no-tenant-notice', () => ({
  NoTenantNotice: () => <div data-testid="operator-overview-no-tenant" />,
}));
vi.mock('next/navigation', () => ({
  redirect: (p: string) => redirect(p),
  useRouter: () => ({ refresh: routerRefresh, push: vi.fn(), replace: vi.fn() }),
}));
vi.mock('next/link', () => ({
  default: ({ href, children, ...rest }: { href: string; children: ReactNode }) => (
    <a href={href} {...rest}>
      {children}
    </a>
  ),
}));

import OperatorOverviewPage from '@/app/(console)/dashboards/overview/page';

const IAM = { productKey: 'iam', displayName: 'IAM', available: true, tenants: ['demo-corp'], baseRoute: '/iam' };
const WMS = { productKey: 'wms', displayName: 'WMS', available: true, tenants: ['demo-corp'], baseRoute: '/wms' };

const SUCCESS_STATE = {
  overview: { cards: [] },
  noTenant: false,
  unauthorized: false,
  bffUnavailable: false,
};

function renderPage(ui: Awaited<ReturnType<typeof OperatorOverviewPage>>) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(<QueryClientProvider client={qc}>{ui}</QueryClientProvider>);
}

beforeEach(() => {
  getOperatorOverviewState.mockReset();
  getDomainHealthState.mockReset();
  getCatalog.mockReset();
  redirect.mockClear();
  routerRefresh.mockClear();
});

describe('AC-3 — overview, no active tenant: the catalog grid IS the primary content', () => {
  it('renders the product/tenant grid directly, with a single h1', async () => {
    getOperatorOverviewState.mockResolvedValue({
      overview: null,
      noTenant: true,
      unauthorized: false,
      bffUnavailable: false,
    });
    getDomainHealthState.mockReturnValue(new Promise(() => {})); // wasted on this branch
    getCatalog.mockResolvedValue({ products: [IAM, WMS], degraded: false });

    const ui = await OperatorOverviewPage();
    renderPage(ui);

    expect(screen.getAllByRole('heading', { level: 1 })).toHaveLength(1);
    expect(screen.getByRole('heading', { level: 1, name: '운영자 통합 개요' })).toBeInTheDocument();
    expect(screen.getByRole('heading', { level: 2, name: '제품·테넌트' })).toBeInTheDocument();
    expect(screen.getByTestId('tile-iam')).toBeInTheDocument();
    expect(screen.getByTestId('tile-iam-tenant-demo-corp')).toBeInTheDocument();
    expect(screen.getByTestId('tile-wms-tenant-demo-corp')).toBeInTheDocument();
    // Direct content, not tucked inside a closed/open <details> — unlike the
    // active-tenant branches below.
    expect(screen.queryByTestId('overview-catalog-section')).toBeNull();
    // Catalog has something to pick → the original no-tenant notice is NOT
    // also shown (Edge Case only fires on an EMPTY catalog).
    expect(screen.queryByTestId('operator-overview-no-tenant')).toBeNull();
  });

  it('Edge Case — an empty catalog ALSO shows the original no-tenant notice (nothing to pick)', async () => {
    getOperatorOverviewState.mockResolvedValue({
      overview: null,
      noTenant: true,
      unauthorized: false,
      bffUnavailable: false,
    });
    getDomainHealthState.mockReturnValue(new Promise(() => {}));
    getCatalog.mockResolvedValue({ products: [], degraded: false });

    const ui = await OperatorOverviewPage();
    renderPage(ui);

    expect(screen.getByTestId('catalog-empty')).toBeInTheDocument();
    expect(screen.getByTestId('operator-overview-no-tenant')).toBeInTheDocument();
  });
});

describe('AC-4 — overview, success: cards + health summary + a CLOSED 제품·테넌트 전체 section', () => {
  it('renders the section closed by default, with status dots sourced from healthState', async () => {
    getOperatorOverviewState.mockResolvedValue(SUCCESS_STATE);
    getDomainHealthState.mockResolvedValue({
      health: {
        cards: [
          { domain: 'iam', status: 'ok', data: { status: 'UP' } },
          { domain: 'wms', status: 'ok', data: { status: 'DOWN' } },
        ],
      },
      noTenant: false,
      unauthorized: false,
      bffUnavailable: false,
    });
    getCatalog.mockResolvedValue({ products: [IAM, WMS], degraded: false });

    const ui = await OperatorOverviewPage();
    renderPage(ui);

    expect(screen.getByTestId('overview-screen')).toBeInTheDocument();
    expect(screen.getByTestId('health-summary')).toBeInTheDocument();

    const section = screen.getByTestId('overview-catalog-section') as HTMLDetailsElement;
    expect(section.tagName).toBe('DETAILS');
    expect(section.open).toBe(false);
    expect(screen.getByText('제품·테넌트 전체')).toBeInTheDocument();

    // Healthy domain (UP) vs. attention domain (not UP) — same `healthTone()`
    // 3-tone classification the pre-fold `console/page.tsx` used.
    expect(screen.getByTestId('tile-iam-status')).toHaveAttribute('data-tone', 'healthy');
    expect(screen.getByTestId('tile-wms-status')).toHaveAttribute('data-tone', 'attention');
  });

  it('a catalog-only degrade (registry 5xx) shows catalog-degraded WITHOUT blanking the overview (AC-6)', async () => {
    getOperatorOverviewState.mockResolvedValue(SUCCESS_STATE);
    getDomainHealthState.mockResolvedValue({
      health: { cards: [] },
      noTenant: false,
      unauthorized: false,
      bffUnavailable: false,
    });
    // `getCatalog()` never throws for a non-401 failure — it resolves degraded
    // (see that function's own doc comment).
    getCatalog.mockResolvedValue({ products: [], degraded: true });

    const ui = await OperatorOverviewPage();
    renderPage(ui);

    expect(screen.getByTestId('overview-screen')).toBeInTheDocument();
    expect(screen.getByTestId('health-summary')).toBeInTheDocument();
    expect(screen.getByTestId('catalog-degraded')).toBeInTheDocument();
  });
});

describe('AC-5 — overview, BFF unavailable: banner + an OPEN 제품·테넌트 전체 section', () => {
  it('renders the section open by default — a dead overview must not also hide the way to a domain screen', async () => {
    getOperatorOverviewState.mockResolvedValue({
      overview: null,
      noTenant: false,
      unauthorized: false,
      bffUnavailable: true,
    });
    getDomainHealthState.mockReturnValue(new Promise(() => {})); // wasted on this branch
    getCatalog.mockResolvedValue({ products: [IAM], degraded: false });

    const ui = await OperatorOverviewPage();
    renderPage(ui);

    expect(screen.getByTestId('operator-overview-bff-unavailable')).toBeInTheDocument();
    const section = screen.getByTestId('overview-catalog-section') as HTMLDetailsElement;
    expect(section.open).toBe(true);
    expect(screen.getByTestId('tile-iam')).toBeInTheDocument();
  });
});
