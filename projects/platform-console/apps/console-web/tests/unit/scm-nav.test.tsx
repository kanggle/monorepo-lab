import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { resolveConsoleRoute } from '@/features/catalog';
import type { RegistryProduct } from '@/shared/api/registry-types';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import type { ReactNode } from 'react';
import { ScmOpsScreen } from '@/features/scm-ops';
import type {
  PoPage,
  SnapshotResponse,
  StalenessResponse,
} from '@/features/scm-ops';

/**
 * Regression (TASK-PC-FE-008 AC): the `/scm` surface is an in-console NAV
 * destination and an ADDITIVE domain section. It must NOT disturb the
 * data-driven catalog routing (FE-001/FE-002/FE-007 unchanged):
 * `iam.baseRoute` still resolves to `/accounts`, and a non-IAM product
 * (incl. `wms` and `scm`) keeps its registry `baseRoute`
 * (resolveConsoleRoute is additive). The scm section mounts as an
 * in-console destination without the GAP-section operator-token /
 * X-Tenant-Id machinery (it reuses the FE-007 GAP-OIDC credential rule).
 */

const gap: RegistryProduct = {
  productKey: 'iam',
  displayName: 'Global Account Platform',
  available: true,
  tenants: ['scm'],
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

const PO: PoPage = {
  content: [],
  page: 0,
  size: 20,
  totalElements: 0,
  totalPages: 0,
};
const SNAP: SnapshotResponse = {
  data: { content: [], page: 0, size: 20, totalElements: 0 },
  meta: { warning: 'Not for procurement decisions (S5)' },
};
const STALE: StalenessResponse = {
  data: [],
  meta: { warning: 'Not for procurement decisions (S5)' },
};

function wrapper() {
  const qc = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={qc}>{children}</QueryClientProvider>
  );
}

describe('scm nav — additive, does not disturb catalog routing (FE-001/002/007)', () => {
  it('iam still resolves to /accounts (FE-002 contract unchanged)', () => {
    expect(resolveConsoleRoute(gap)).toBe('/accounts');
  });

  it('wms keeps its registry baseRoute (FE-007 unchanged)', () => {
    expect(resolveConsoleRoute(wms)).toBe('/wms');
  });

  it('scm keeps its registry baseRoute (resolveConsoleRoute additive)', () => {
    expect(resolveConsoleRoute(scm)).toBe('/scm');
  });

  it('the scm section mounts as an in-console destination (read-only)', () => {
    render(
      <ScmOpsScreen poList={PO} snapshot={SNAP} staleness={STALE} />,
      { wrapper: wrapper() },
    );
    expect(
      screen.getByRole('heading', { name: 'SCM 개요' }),
    ).toBeInTheDocument();
    // The empty seeded pages render their empty states (no crash).
    expect(screen.getByTestId('scm-po-empty')).toBeInTheDocument();
    expect(screen.getByTestId('scm-snap-empty')).toBeInTheDocument();
    // The S5 warning is present even on an empty inventory-visibility view.
    expect(screen.getAllByTestId('scm-s5-warning').length).toBeGreaterThan(
      0,
    );
  });
});
