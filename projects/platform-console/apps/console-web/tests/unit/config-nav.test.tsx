import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { resolveConsoleRoute } from '@/features/catalog';
import type { RegistryProduct } from '@/shared/api/registry-types';
import { ConsoleSidebarNav } from '@/shared/ui/ConsoleSidebarNav';

/**
 * Regression (TASK-PC-FE-080 AC): the `/scm/config` seed/config surface is an
 * additive in-console NAV destination — the THIRD scm sub-route (운영 + 보충 +
 * 설정). It must NOT disturb the data-driven catalog routing: `iam.baseRoute`
 * still resolves to `/accounts`, and a non-IAM product (incl. `scm`) keeps its
 * registry `baseRoute`. The existing 운영(/scm) + 보충(/scm/replenishment)
 * children are unchanged.
 */

const gap: RegistryProduct = {
  productKey: 'iam',
  displayName: 'Global Account Platform',
  available: true,
  tenants: ['scm'],
  baseRoute: '/iam',
};
const scm: RegistryProduct = {
  productKey: 'scm',
  displayName: 'SCM',
  available: true,
  tenants: ['scm'],
  baseRoute: '/scm',
};

vi.mock('next/navigation', () => ({
  usePathname: () => '/scm/config',
}));

describe('scm config nav — additive third SCM child, does not disturb routing', () => {
  it('iam still resolves to /accounts (FE-002 contract unchanged)', () => {
    expect(resolveConsoleRoute(gap)).toBe('/accounts');
  });

  it('scm keeps its registry baseRoute (resolveConsoleRoute additive)', () => {
    expect(resolveConsoleRoute(scm)).toBe('/scm');
  });

  it('the new /scm/config nav item renders and resolves (설정 child), alongside 운영 + 보충', () => {
    render(<ConsoleSidebarNav />);
    const link = screen.getByTestId('nav-scm-config');
    expect(link).toBeInTheDocument();
    expect(link).toHaveAttribute('href', '/scm/config');
    // The existing children are unchanged.
    expect(screen.getByTestId('nav-scm-ops')).toHaveAttribute('href', '/scm');
    expect(screen.getByTestId('nav-scm-replenishment')).toHaveAttribute(
      'href',
      '/scm/replenishment',
    );
    expect(screen.getByTestId('nav-scm')).not.toHaveAttribute('href');
  });

  it('the /scm/config nav item is marked active on the config route (운영/보충 are not)', () => {
    render(<ConsoleSidebarNav />);
    expect(screen.getByTestId('nav-scm-config')).toHaveAttribute(
      'aria-current',
      'page',
    );
    expect(screen.getByTestId('nav-scm-ops')).not.toHaveAttribute(
      'aria-current',
    );
    expect(screen.getByTestId('nav-scm-replenishment')).not.toHaveAttribute(
      'aria-current',
    );
  });
});
