import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { resolveConsoleRoute } from '@/features/catalog';
import type { RegistryProduct } from '@/shared/api/registry-types';
import { ConsoleSidebarNav } from '@/shared/ui/ConsoleSidebarNav';

/**
 * Regression (TASK-PC-FE-057 AC): the `/wms/outbound` surface is an additive
 * in-console NAV destination — the SECOND wms surface. It must NOT disturb the
 * data-driven catalog routing: `iam.baseRoute` still resolves to `/accounts`,
 * and a non-IAM product (incl. `wms`) keeps its registry `baseRoute`. The new
 * nav item resolves; the existing `/wms` nav is unchanged.
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

vi.mock('next/navigation', () => ({
  usePathname: () => '/wms/outbound',
}));

describe('wms outbound nav — additive, does not disturb the catalog routing', () => {
  it('iam still resolves to /accounts (FE-002 contract unchanged)', () => {
    expect(resolveConsoleRoute(gap)).toBe('/accounts');
  });

  it('wms keeps its registry baseRoute (resolveConsoleRoute additive)', () => {
    expect(resolveConsoleRoute(wms)).toBe('/wms');
  });

  it('the new /wms/outbound nav item renders and resolves', () => {
    render(<ConsoleSidebarNav />);
    const link = screen.getByTestId('nav-wms-outbound');
    expect(link).toBeInTheDocument();
    expect(link).toHaveAttribute('href', '/wms/outbound');
    // The existing /wms nav is unchanged (both present).
    expect(screen.getByTestId('nav-wms')).toHaveAttribute('href', '/wms');
  });

  it('the /wms/outbound nav item is marked active on the outbound route', () => {
    render(<ConsoleSidebarNav />);
    expect(screen.getByTestId('nav-wms-outbound')).toHaveAttribute(
      'aria-current',
      'page',
    );
  });
});
