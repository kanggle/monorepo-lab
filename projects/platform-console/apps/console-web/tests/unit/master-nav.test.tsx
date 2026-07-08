import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { resolveConsoleRoute } from '@/features/catalog';
import type { RegistryProduct } from '@/shared/api/registry-types';
import { ConsoleSidebarNav } from '@/shared/ui/ConsoleSidebarNav';

/**
 * Regression (TASK-PC-FE-223 AC-1): the `/wms/master` surface is an
 * additive in-console NAV destination — the FIFTH wms surface (after
 * 개요/가이드/입고/재고/출고). It must NOT disturb the data-driven catalog
 * routing: `iam.baseRoute` still resolves to `/accounts`, and a non-IAM
 * product (incl. `wms`) keeps its registry `baseRoute`. Mirrors
 * `inbound-nav.test.tsx` — same WMS drill-in parent machinery
 * (TASK-PC-FE-059).
 *
 * 마스터 is inserted AFTER 출고 (참조/설정 성격은 물류 흐름 입고→재고→출고
 * 뒤 — task § Scope item 6); this suite pins the order and asserts the
 * addition does NOT disturb the existing 입고/재고/출고 longest-prefix
 * active behaviour.
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

let mockPath = '/wms/master';
vi.mock('next/navigation', () => ({
  usePathname: () => mockPath,
}));

describe('wms 마스터 nav — additive, does not disturb the catalog routing (TASK-PC-FE-223)', () => {
  it('iam still resolves to /accounts (FE-002 contract unchanged)', () => {
    mockPath = '/wms/master';
    expect(resolveConsoleRoute(gap)).toBe('/accounts');
  });

  it('wms keeps its registry baseRoute (resolveConsoleRoute additive)', () => {
    expect(resolveConsoleRoute(wms)).toBe('/wms');
  });

  it('the new /wms/master nav item renders and resolves, ordered after 출고 (last)', () => {
    mockPath = '/wms/master';
    render(<ConsoleSidebarNav />);
    const link = screen.getByTestId('nav-wms-master');
    expect(link).toBeInTheDocument();
    expect(link).toHaveAttribute('href', '/wms/master');
    // WMS is a drill-in parent: the /wms destination lives on the 운영
    // child (nav-wms-ops); nav-wms is the pinned parent back-toggle (a
    // button, not a link → no href).
    expect(screen.getByTestId('nav-wms-ops')).toHaveAttribute('href', '/wms');
    expect(screen.getByTestId('nav-wms-outbound')).toHaveAttribute(
      'href',
      '/wms/outbound',
    );
    expect(screen.getByTestId('nav-wms')).not.toHaveAttribute('href');
  });

  it('a deep link to /wms/master auto-opens the WMS drill with 마스터 active (재고/출고 inactive)', () => {
    mockPath = '/wms/master';
    render(<ConsoleSidebarNav />);
    expect(screen.getByTestId('nav-wms-master')).toHaveAttribute(
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

  it('마스터 does NOT disturb the 출고 longest-prefix active on /wms/outbound', () => {
    mockPath = '/wms/outbound';
    render(<ConsoleSidebarNav />);
    expect(screen.getByTestId('nav-wms-outbound')).toHaveAttribute(
      'aria-current',
      'page',
    );
    expect(screen.getByTestId('nav-wms-master')).not.toHaveAttribute(
      'aria-current',
    );
  });

  it('마스터 does NOT disturb the 재고 longest-prefix active on /wms/inventory', () => {
    mockPath = '/wms/inventory';
    render(<ConsoleSidebarNav />);
    expect(screen.getByTestId('nav-wms-inventory')).toHaveAttribute(
      'aria-current',
      'page',
    );
    expect(screen.getByTestId('nav-wms-master')).not.toHaveAttribute(
      'aria-current',
    );
  });
});
