import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { resolveConsoleRoute } from '@/features/catalog';
import type { RegistryProduct } from '@/shared/api/registry-types';
import { ConsoleSidebarNav } from '@/shared/ui/ConsoleSidebarNav';

/**
 * Regression (TASK-PC-FE-222 AC-1): the `/wms/inbound` surface is an
 * additive in-console NAV destination — the FOURTH wms surface (after
 * 개요/가이드/재고/출고). It must NOT disturb the data-driven catalog
 * routing: `iam.baseRoute` still resolves to `/accounts`, and a non-IAM
 * product (incl. `wms`) keeps its registry `baseRoute`. Mirrors
 * `outbound-nav.test.tsx` / `wms-guide-nav.test.tsx` — same WMS drill-in
 * parent machinery (TASK-PC-FE-059).
 *
 * 입고 is inserted BETWEEN 가이드 and 재고 (물류 흐름 입고→재고→출고 —
 * task § Scope item 7); this suite pins the order and asserts the addition
 * does NOT disturb the existing 재고/출고 longest-prefix active behaviour.
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

let mockPath = '/wms/inbound';
vi.mock('next/navigation', () => ({
  usePathname: () => mockPath,
}));

describe('wms 입고 nav — additive, does not disturb the catalog routing (TASK-PC-FE-222)', () => {
  it('iam still resolves to /accounts (FE-002 contract unchanged)', () => {
    mockPath = '/wms/inbound';
    expect(resolveConsoleRoute(gap)).toBe('/accounts');
  });

  it('wms keeps its registry baseRoute (resolveConsoleRoute additive)', () => {
    expect(resolveConsoleRoute(wms)).toBe('/wms');
  });

  it('the new /wms/inbound nav item renders and resolves, ordered between 가이드 and 재고', () => {
    mockPath = '/wms/inbound';
    render(<ConsoleSidebarNav />);
    const link = screen.getByTestId('nav-wms-inbound');
    expect(link).toBeInTheDocument();
    expect(link).toHaveAttribute('href', '/wms/inbound');
    // WMS is a drill-in parent: the /wms destination lives on the 운영
    // child (nav-wms-ops); nav-wms is the pinned parent back-toggle (a
    // button, not a link → no href).
    expect(screen.getByTestId('nav-wms-ops')).toHaveAttribute('href', '/wms');
    expect(screen.getByTestId('nav-wms-guide')).toHaveAttribute(
      'href',
      '/wms/guide',
    );
    expect(screen.getByTestId('nav-wms-inventory')).toHaveAttribute(
      'href',
      '/wms/inventory',
    );
    expect(screen.getByTestId('nav-wms')).not.toHaveAttribute('href');
  });

  it('a deep link to /wms/inbound auto-opens the WMS drill with 입고 active (재고/출고 inactive)', () => {
    mockPath = '/wms/inbound';
    render(<ConsoleSidebarNav />);
    expect(screen.getByTestId('nav-wms-inbound')).toHaveAttribute(
      'aria-current',
      'page',
    );
    expect(screen.getByTestId('nav-wms-inventory')).not.toHaveAttribute(
      'aria-current',
    );
    expect(screen.getByTestId('nav-wms-outbound')).not.toHaveAttribute(
      'aria-current',
    );
  });

  it('입고 does NOT disturb the 재고 longest-prefix active on /wms/inventory', () => {
    mockPath = '/wms/inventory';
    render(<ConsoleSidebarNav />);
    expect(screen.getByTestId('nav-wms-inventory')).toHaveAttribute(
      'aria-current',
      'page',
    );
    expect(screen.getByTestId('nav-wms-inbound')).not.toHaveAttribute(
      'aria-current',
    );
  });

  it('입고 does NOT disturb the 출고 longest-prefix active on /wms/outbound', () => {
    mockPath = '/wms/outbound';
    render(<ConsoleSidebarNav />);
    expect(screen.getByTestId('nav-wms-outbound')).toHaveAttribute(
      'aria-current',
      'page',
    );
    expect(screen.getByTestId('nav-wms-inbound')).not.toHaveAttribute(
      'aria-current',
    );
  });
});
