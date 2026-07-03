import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { resolveConsoleRoute } from '@/features/catalog';
import type { RegistryProduct } from '@/shared/api/registry-types';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import type { ReactNode } from 'react';
import { WmsOpsScreen } from '@/features/wms-ops';
import type { AlertPage, ShipmentPage } from '@/features/wms-ops';

/**
 * Regression (TASK-PC-FE-007 AC): the `/wms` surface is an in-console NAV
 * destination and an ADDITIVE domain section. It must NOT disturb the
 * data-driven catalog routing (FE-001/FE-002 unchanged): `iam.baseRoute`
 * still resolves to `/accounts`, and a non-IAM product (incl. `wms`)
 * keeps its registry `baseRoute` (resolveConsoleRoute is additive). The
 * wms section mounts as an in-console destination without the GAP-section
 * operator-token / X-Tenant-Id machinery.
 */

const gap: RegistryProduct = {
  productKey: 'iam',
  displayName: 'Global Account Platform',
  available: true,
  tenants: ['wms'],
  baseRoute: '/iam',
};
const wms: RegistryProduct = {
  productKey: 'wms',
  displayName: 'WMS',
  available: true,
  tenants: ['wms'],
  baseRoute: '/wms',
};

const ALERTS: AlertPage = {
  content: [],
  page: { number: 0, size: 20, totalElements: 0, totalPages: 0 },
};
const SHIPMENTS: ShipmentPage = {
  content: [],
  page: { number: 0, size: 20, totalElements: 0, totalPages: 0 },
};

function wrapper() {
  const qc = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={qc}>{children}</QueryClientProvider>
  );
}

describe('wms nav — additive, does not disturb the catalog routing (FE-001/FE-002)', () => {
  it('iam still resolves to /accounts (FE-002 contract unchanged)', () => {
    expect(resolveConsoleRoute(gap)).toBe('/accounts');
  });

  it('wms keeps its registry baseRoute (resolveConsoleRoute additive)', () => {
    expect(resolveConsoleRoute(wms)).toBe('/wms');
  });

  it('the wms section mounts as an in-console destination (read + ack only)', () => {
    render(
      <WmsOpsScreen
        alerts={ALERTS}
        shipments={SHIPMENTS}
        lagSeconds={null}
      />,
      { wrapper: wrapper() },
    );
    expect(
      screen.getByRole('heading', { name: 'WMS 개요' }),
    ).toBeInTheDocument();
    // The empty seeded pages render their empty states (no crash).
    expect(screen.getByTestId('wms-ship-empty')).toBeInTheDocument();
    expect(screen.getByTestId('wms-alerts-empty')).toBeInTheDocument();
  });
});
