import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, cleanup } from '@testing-library/react';
import type { ReactNode } from 'react';
import type { CatalogState } from '@/shared/api/registry-types';

/**
 * TASK-MONO-808 (ADR-MONO-084 P3) — the `/wms` 개요 page reads `?period=` for
 * the «실적» strip exactly like `/ecommerce` (TASK-MONO-807): `week|month` is
 * passed through to `getWmsOverviewState`; an absent or unknown value falls
 * back to `DEFAULT_OVERVIEW_PERIOD` (never a 400 page). The feature barrel is
 * stubbed — the overview's own rendering/state is unit-tested separately
 * (`wms-overview.test.tsx`, `wms-overview-state.test.ts`).
 */

vi.mock('next/navigation', () => ({
  redirect: (path: string) => {
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

const m = vi.hoisted(() => ({
  getCatalog: vi.fn(),
  getWmsOverviewState: vi.fn(),
  getWmsSectionState: vi.fn(),
  getWmsThroughputState: vi.fn(),
  getWmsBusinessRoles: vi.fn(),
}));
vi.mock('@/features/catalog', () => ({ getCatalog: m.getCatalog }));
vi.mock('@/features/wms-ops', () => ({
  DEFAULT_OVERVIEW_PERIOD: 'month',
  getWmsOverviewState: m.getWmsOverviewState,
  getWmsSectionState: m.getWmsSectionState,
  getWmsThroughputState: m.getWmsThroughputState,
  WmsOpsScreen: ({ overview, recentActivity }: { overview: ReactNode; recentActivity: ReactNode }) => (
    <div data-testid="wms-ops-screen">
      {overview}
      {recentActivity}
    </div>
  ),
  WmsOverview: () => <div data-testid="wms-overview" />,
  WmsThroughputCard: () => <div data-testid="wms-throughput-card" />,
  WmsRecentShipments: () => <div data-testid="wms-recent-shipments" />,
  WmsRecentAdjustments: () => <div data-testid="wms-recent-adjustments" />,
}));
vi.mock('@/app/(console)/wms/_lib/wms-business-roles', () => ({
  getWmsBusinessRoles: m.getWmsBusinessRoles,
}));

import WmsPage from '@/app/(console)/wms/page';

const ELIGIBLE: CatalogState = {
  degraded: false,
  products: [
    {
      productKey: 'wms',
      displayName: 'WMS',
      available: true,
      tenants: ['wms'],
      baseRoute: '/wms',
    },
  ],
};

beforeEach(() => {
  vi.clearAllMocks();
  cleanup();
  m.getCatalog.mockResolvedValue(ELIGIBLE);
  m.getWmsOverviewState.mockResolvedValue({ notEligible: false });
  m.getWmsSectionState.mockResolvedValue({
    notEligible: false,
    forbidden: false,
    degraded: false,
    alerts: { content: [], page: { number: 0, size: 20, totalElements: 0, totalPages: 0 } },
    lagSeconds: null,
  });
  m.getWmsThroughputState.mockResolvedValue({ notEligible: false });
  m.getWmsBusinessRoles.mockResolvedValue(undefined);
});

describe('WmsPage — ?period= (TASK-MONO-808)', () => {
  it('no searchParams → the default period', async () => {
    render(await WmsPage({}));
    expect(m.getWmsOverviewState).toHaveBeenCalledWith(true, 'month');
    expect(screen.getByTestId('wms-overview')).toBeInTheDocument();
    // The throughput card still renders alongside (its UTC window is untouched).
    expect(screen.getByTestId('wms-throughput-card')).toBeInTheDocument();
  });

  it('?period=week is passed through; an unknown value falls back to the default', async () => {
    render(await WmsPage({ searchParams: Promise.resolve({ period: 'week' }) }));
    expect(m.getWmsOverviewState).toHaveBeenLastCalledWith(true, 'week');

    cleanup();
    render(await WmsPage({ searchParams: Promise.resolve({ period: 'year' }) }));
    expect(m.getWmsOverviewState).toHaveBeenLastCalledWith(true, 'month');
    expect(screen.getByTestId('wms-ops-screen')).toBeInTheDocument();

    cleanup();
    render(await WmsPage({ searchParams: Promise.resolve({}) }));
    expect(m.getWmsOverviewState).toHaveBeenLastCalledWith(true, 'month');
  });

  it('not eligible → the period still reaches the (short-circuiting) overview read', async () => {
    m.getCatalog.mockResolvedValue({ degraded: false, products: [] });
    m.getWmsSectionState.mockResolvedValue({ notEligible: true });
    render(await WmsPage({ searchParams: Promise.resolve({ period: 'week' }) }));
    expect(m.getWmsOverviewState).toHaveBeenCalledWith(false, 'week');
    expect(screen.getByTestId('wms-not-eligible')).toBeInTheDocument();
  });
});
