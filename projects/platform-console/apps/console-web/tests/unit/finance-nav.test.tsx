import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { resolveConsoleRoute } from '@/features/catalog';
import type { RegistryProduct } from '@/shared/api/registry-types';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import type { ReactNode } from 'react';
import { FinanceOpsScreen } from '@/features/finance-ops';

/**
 * Regression (TASK-PC-FE-009 AC): the `/finance` surface is an
 * in-console NAV destination and an ADDITIVE domain section. It must
 * NOT disturb the data-driven catalog routing (FE-001 / FE-002 /
 * FE-007 / FE-008 unchanged): `iam.baseRoute` still resolves to
 * `/accounts`, and a non-IAM product (incl. `wms`, `scm`, and
 * `finance`) keeps its registry `baseRoute` (resolveConsoleRoute is
 * additive). The finance section mounts as an in-console destination
 * without the GAP-section operator-token / X-Tenant-Id machinery (it
 * reuses the FE-007/FE-008 GAP-OIDC credential rule).
 */

const gap: RegistryProduct = {
  productKey: 'iam',
  displayName: 'Global Account Platform',
  available: true,
  tenants: ['finance'],
  baseRoute: '/iam',
};
const wms: RegistryProduct = {
  productKey: 'wms',
  displayName: 'WMS',
  available: true,
  tenants: ['wms'],
  baseRoute: '/wms',
};
const scm: RegistryProduct = {
  productKey: 'scm',
  displayName: 'SCM',
  available: true,
  tenants: ['scm'],
  baseRoute: '/scm',
};
const finance: RegistryProduct = {
  productKey: 'finance',
  displayName: 'Finance',
  available: true,
  tenants: ['finance'],
  baseRoute: '/finance',
};
const financeUnavailable: RegistryProduct = {
  productKey: 'finance',
  displayName: 'Finance',
  available: false,
  tenants: [],
  baseRoute: '/finance',
};

function wrapper() {
  const qc = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={qc}>{children}</QueryClientProvider>
  );
}

describe('finance nav — additive, does not disturb catalog routing (FE-001/002/007/008)', () => {
  it('iam still resolves to /accounts (FE-002 contract unchanged)', () => {
    expect(resolveConsoleRoute(gap)).toBe('/accounts');
  });

  it('wms keeps its registry baseRoute (FE-007 unchanged)', () => {
    expect(resolveConsoleRoute(wms)).toBe('/wms');
  });

  it('scm keeps its registry baseRoute (FE-008 unchanged)', () => {
    expect(resolveConsoleRoute(scm)).toBe('/scm');
  });

  it('finance keeps its registry baseRoute (resolveConsoleRoute additive)', () => {
    expect(resolveConsoleRoute(finance)).toBe('/finance');
  });

  it('an available:false finance product is still data-driven (the catalog Coming-Soon path handles it; no hard-crash)', () => {
    // The registry `baseRoute` is preserved verbatim regardless of
    // availability — the catalog "coming soon" tile composes from
    // `available:false`; the route resolver is additive (FE-001 AC).
    expect(resolveConsoleRoute(financeUnavailable)).toBe('/finance');
  });

  it('the finance section mounts as an in-console destination (read-only)', () => {
    render(
      <FinanceOpsScreen
        initialAccountId={null}
        initialAccount={null}
        initialBalances={null}
        initialTransactions={null}
      />,
      { wrapper: wrapper() },
    );
    expect(
      screen.getByRole('heading', { name: 'Finance 운영' }),
    ).toBeInTheDocument();
    // The lookup form renders even without an accountId — finance v1
    // has no list/search GET, so the section opens to the lookup
    // (account-id-driven).
    expect(screen.getByTestId('finance-account-input')).toBeInTheDocument();
    expect(screen.getByTestId('finance-no-account')).toBeInTheDocument();
  });
});
