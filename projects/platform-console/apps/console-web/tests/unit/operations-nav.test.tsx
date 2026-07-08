import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { resolveConsoleRoute } from '@/features/catalog';
import type { RegistryProduct } from '@/shared/api/registry-types';
import { ConsoleSidebarNav } from '@/shared/ui/ConsoleSidebarNav';

/**
 * Regression (TASK-PC-FE-224 AC-1): the `/wms/operations` surface is an
 * additive in-console NAV destination — the SIXTH wms surface (after
 * 개요/가이드/입고/재고/출고/마스터). It must NOT disturb the data-driven
 * catalog routing: `iam.baseRoute` still resolves to `/accounts`, and a
 * non-IAM product (incl. `wms`) keeps its registry `baseRoute`. Mirrors
 * `master-nav.test.tsx` — same WMS drill-in parent machinery
 * (TASK-PC-FE-059).
 *
 * 운영설정 is inserted AFTER 마스터 (task § Scope item 7 "맨 끝"); this
 * suite pins the order and asserts the addition does NOT disturb the
 * existing 입고/재고/출고/마스터 longest-prefix active behaviour.
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

let mockPath = '/wms/operations';
vi.mock('next/navigation', () => ({
  usePathname: () => mockPath,
}));

describe('wms 운영설정 nav — additive, does not disturb the catalog routing (TASK-PC-FE-224)', () => {
  it('iam still resolves to /accounts (FE-002 contract unchanged)', () => {
    mockPath = '/wms/operations';
    expect(resolveConsoleRoute(gap)).toBe('/accounts');
  });

  it('wms keeps its registry baseRoute (resolveConsoleRoute additive)', () => {
    expect(resolveConsoleRoute(wms)).toBe('/wms');
  });

  it('the new /wms/operations nav item renders and resolves, ordered after 마스터 (last)', () => {
    mockPath = '/wms/operations';
    render(<ConsoleSidebarNav />);
    const link = screen.getByTestId('nav-wms-operations');
    expect(link).toBeInTheDocument();
    expect(link).toHaveAttribute('href', '/wms/operations');
    // WMS is a drill-in parent: the /wms destination lives on the 운영
    // child (nav-wms-ops); nav-wms is the pinned parent back-toggle (a
    // button, not a link → no href).
    expect(screen.getByTestId('nav-wms-ops')).toHaveAttribute('href', '/wms');
    expect(screen.getByTestId('nav-wms-master')).toHaveAttribute(
      'href',
      '/wms/master',
    );
    expect(screen.getByTestId('nav-wms')).not.toHaveAttribute('href');
  });

  it('a deep link to /wms/operations auto-opens the WMS drill with 운영설정 active (마스터/출고 inactive)', () => {
    mockPath = '/wms/operations';
    render(<ConsoleSidebarNav />);
    expect(screen.getByTestId('nav-wms-operations')).toHaveAttribute(
      'aria-current',
      'page',
    );
    expect(screen.getByTestId('nav-wms-master')).not.toHaveAttribute(
      'aria-current',
    );
    expect(screen.getByTestId('nav-wms-outbound')).not.toHaveAttribute(
      'aria-current',
    );
  });

  it('운영설정 does NOT disturb the 마스터 longest-prefix active on /wms/master', () => {
    mockPath = '/wms/master';
    render(<ConsoleSidebarNav />);
    expect(screen.getByTestId('nav-wms-master')).toHaveAttribute(
      'aria-current',
      'page',
    );
    expect(screen.getByTestId('nav-wms-operations')).not.toHaveAttribute(
      'aria-current',
    );
  });

  it('운영설정 does NOT disturb the 출고 longest-prefix active on /wms/outbound', () => {
    mockPath = '/wms/outbound';
    render(<ConsoleSidebarNav />);
    expect(screen.getByTestId('nav-wms-outbound')).toHaveAttribute(
      'aria-current',
      'page',
    );
    expect(screen.getByTestId('nav-wms-operations')).not.toHaveAttribute(
      'aria-current',
    );
  });
});
