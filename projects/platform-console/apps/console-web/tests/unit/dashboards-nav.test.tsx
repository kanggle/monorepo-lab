import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import type { ReactNode } from 'react';
import { resolveConsoleRoute } from '@/features/catalog';
import type { RegistryProduct } from '@/shared/api/registry-types';
import { OperatorOverviewScreen } from '@/features/dashboards';
import type { OperatorOverview } from '@/features/dashboards';
import { OVERVIEW_QUICK_LINKS } from '@/features/dashboards/api/types';

/**
 * Regression (TASK-PC-FE-005 AC): the `/dashboards` overview is an
 * in-console NAV destination + the console landing — it must NOT change
 * the catalog `gap.baseRoute` contract (FE-002 stays: gap → /accounts).
 * The overview screen mounts with NO FE-002/004 mutation scaffolding
 * (read-only) and its quick-links target the EXISTING routes.
 */

const gap: RegistryProduct = {
  productKey: 'gap',
  displayName: 'Global Account Platform',
  available: true,
  tenants: ['wms'],
  baseRoute: '/gap',
};

function wrapper() {
  const qc = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={qc}>{children}</QueryClientProvider>
  );
}

const OVERVIEW: OperatorOverview = {
  accounts: { status: 'ok', totalElements: 1, sampleCount: 1 },
  audit: {
    status: 'ok',
    totalElements: 0,
    recentCount: 0,
    latestOccurredAt: null,
  },
  operators: {
    status: 'ok',
    totalElements: 1,
    activeCount: 1,
    suspendedCount: 0,
  },
};

describe('dashboards nav — does not disturb the catalog gap.baseRoute (FE-002)', () => {
  it('gap still resolves to /accounts (FE-002 contract unchanged)', () => {
    expect(resolveConsoleRoute(gap)).toBe('/accounts');
  });

  it('the overview quick-links target the EXISTING in-console routes only', () => {
    expect(OVERVIEW_QUICK_LINKS.accounts).toBe('/accounts');
    expect(OVERVIEW_QUICK_LINKS.audit).toBe('/audit');
    expect(OVERVIEW_QUICK_LINKS.operators).toBe('/operators');
  });

  it('the GAP detail screen mounts as a drill-down destination (read-only, no confirm dialog)', () => {
    render(<OperatorOverviewScreen initial={OVERVIEW} />, {
      wrapper: wrapper(),
    });
    // TASK-PC-FE-034: re-framed as the GAP drill-down detail.
    expect(
      screen.getByRole('heading', { name: 'GAP 상세 (계정 · 감사 · 운영자)' }),
    ).toBeInTheDocument();
    expect(screen.queryByTestId('confirm-dialog')).not.toBeInTheDocument();
  });

  it('the GAP detail screen offers a back link to the home overview (/dashboards/overview)', () => {
    render(<OperatorOverviewScreen initial={OVERVIEW} />, {
      wrapper: wrapper(),
    });
    const back = screen.getByTestId('gap-detail-back-link');
    expect(back).toBeInTheDocument();
    expect(back).toHaveAttribute('href', '/dashboards/overview');
  });
});
