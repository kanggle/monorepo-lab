import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { resolveConsoleRoute } from '@/features/catalog';
import type { RegistryProduct } from '@/shared/api/registry-types';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import type { ReactNode } from 'react';

// next/navigation is jsdom-incompatible without an app router context;
// mock the hooks used by AsOfPicker / useAsOf so the screen mounts in
// vitest's jsdom env.
vi.mock('next/navigation', () => ({
  useRouter: () => ({ replace: vi.fn() }),
  usePathname: () => '/erp',
  useSearchParams: () => new URLSearchParams(),
  redirect: (to: string) => {
    throw new Error(`REDIRECT:${to}`);
  },
}));

import { ErpOpsScreen } from '@/features/erp-ops';

/**
 * Regression (TASK-PC-FE-010 AC): the `/erp` surface is an
 * in-console NAV destination and an ADDITIVE domain section. It
 * must NOT disturb the data-driven catalog routing (FE-001 /
 * FE-002 / FE-007 / FE-008 / FE-009 unchanged): `gap.baseRoute`
 * still resolves to `/accounts`, and a non-GAP product (incl.
 * `wms`, `scm`, `finance`, and `erp`) keeps its registry
 * `baseRoute` (resolveConsoleRoute is additive). The erp section
 * mounts as an in-console destination without the GAP-section
 * operator-token / X-Tenant-Id machinery (it reuses the
 * FE-007/FE-008/FE-009 GAP-OIDC credential rule).
 */

const gap: RegistryProduct = {
  productKey: 'gap',
  displayName: 'Global Account Platform',
  available: true,
  tenants: ['erp'],
  baseRoute: '/gap',
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
const erp: RegistryProduct = {
  productKey: 'erp',
  displayName: 'ERP',
  available: true,
  tenants: ['erp'],
  baseRoute: '/erp',
};
const erpUnavailable: RegistryProduct = {
  productKey: 'erp',
  displayName: 'ERP',
  available: false,
  tenants: [],
  baseRoute: '/erp',
};

function wrapper() {
  const qc = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={qc}>{children}</QueryClientProvider>
  );
}

describe('erp nav — additive, does not disturb catalog routing (FE-001/002/007/008/009)', () => {
  it('gap still resolves to /accounts (FE-002 contract unchanged)', () => {
    expect(resolveConsoleRoute(gap)).toBe('/accounts');
  });

  it('wms keeps its registry baseRoute (FE-007 unchanged)', () => {
    expect(resolveConsoleRoute(wms)).toBe('/wms');
  });

  it('scm keeps its registry baseRoute (FE-008 unchanged)', () => {
    expect(resolveConsoleRoute(scm)).toBe('/scm');
  });

  it('finance keeps its registry baseRoute (FE-009 unchanged)', () => {
    expect(resolveConsoleRoute(finance)).toBe('/finance');
  });

  it('erp keeps its registry baseRoute (resolveConsoleRoute additive)', () => {
    expect(resolveConsoleRoute(erp)).toBe('/erp');
  });

  it('an available:false erp product is still data-driven (the catalog Coming-Soon path handles it; no hard-crash)', () => {
    // The registry `baseRoute` is preserved verbatim regardless of
    // availability — the catalog "coming soon" tile composes from
    // `available:false`; the route resolver is additive (FE-001 AC).
    expect(resolveConsoleRoute(erpUnavailable)).toBe('/erp');
  });

  it('the erp section mounts as an in-console destination (read-only, list-driven)', () => {
    render(
      <ErpOpsScreen
        initialDepartments={{
          data: [],
          meta: { page: 0, size: 20, totalElements: 0, timestamp: 'x' },
        }}
        initialEmployees={{
          data: [],
          meta: { page: 0, size: 20, totalElements: 0, timestamp: 'x' },
        }}
        initialJobGrades={{
          data: [],
          meta: { page: 0, size: 20, totalElements: 0, timestamp: 'x' },
        }}
        initialCostCenters={{
          data: [],
          meta: { page: 0, size: 20, totalElements: 0, timestamp: 'x' },
        }}
        initialBusinessPartners={{
          data: [],
          meta: { page: 0, size: 20, totalElements: 0, timestamp: 'x' },
        }}
      />,
      { wrapper: wrapper() },
    );
    expect(
      screen.getByRole('heading', { name: 'ERP 운영' }),
    ).toBeInTheDocument();
    // The AsOfPicker renders as a first-class E3 control.
    expect(screen.getByTestId('erp-asof-input')).toBeInTheDocument();
    // All 5 master list headings render (the section is
    // list-driven — INVERSE of FE-009 finance account-id-driven).
    expect(
      screen.getByRole('heading', { name: /부서/ }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole('heading', { name: /직원/ }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole('heading', { name: /직급/ }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole('heading', { name: /비용센터/ }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole('heading', { name: /거래처/ }),
    ).toBeInTheDocument();
  });
});
