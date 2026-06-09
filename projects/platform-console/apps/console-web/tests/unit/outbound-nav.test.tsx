import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { resolveConsoleRoute } from '@/features/catalog';
import type { RegistryProduct } from '@/shared/api/registry-types';
import { ConsoleSidebarNav } from '@/shared/ui/ConsoleSidebarNav';

/**
 * Regression (TASK-PC-FE-057 AC): the `/wms/outbound` surface is an additive
 * in-console NAV destination — the SECOND wms surface. It must NOT disturb the
 * data-driven catalog routing: `iam.baseRoute` still resolves to `/accounts`,
 * and a non-IAM product (incl. `wms`) keeps its registry `baseRoute`.
 *
 * TASK-PC-FE-059: WMS is now a Vercel-style drill-in PARENT of 운영(/wms) +
 * 출고(/wms/outbound). On the mocked `/wms/outbound` route the WMS group is
 * auto-drilled — `nav-wms` is the pinned parent back-toggle (a button),
 * `nav-wms-ops` is the 운영 child (→ `/wms`, the destination formerly reached
 * via `nav-wms`), and `nav-wms-outbound` is the active 출고 child.
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

  it('the new /wms/outbound nav item renders and resolves (출고 child)', () => {
    render(<ConsoleSidebarNav />);
    const link = screen.getByTestId('nav-wms-outbound');
    expect(link).toBeInTheDocument();
    expect(link).toHaveAttribute('href', '/wms/outbound');
    // WMS is now a drill-in parent: the /wms destination lives on the 운영
    // child (nav-wms-ops); nav-wms is the pinned parent back-toggle (a button,
    // not a link → no href).
    expect(screen.getByTestId('nav-wms-ops')).toHaveAttribute('href', '/wms');
    expect(screen.getByTestId('nav-wms')).not.toHaveAttribute('href');
  });

  it('the /wms/outbound nav item is marked active on the outbound route (운영 is not)', () => {
    render(<ConsoleSidebarNav />);
    // Longest-prefix active: 출고(/wms/outbound) lights up, 운영(/wms) does not.
    expect(screen.getByTestId('nav-wms-outbound')).toHaveAttribute(
      'aria-current',
      'page',
    );
    expect(screen.getByTestId('nav-wms-ops')).not.toHaveAttribute(
      'aria-current',
    );
  });
});
