import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { resolveConsoleRoute } from '@/features/catalog';
import type { RegistryProduct } from '@/shared/api/registry-types';
import { ConsoleSidebarNav } from '@/shared/ui/ConsoleSidebarNav';

/**
 * Regression (TASK-PC-FE-077 AC): the `/scm/replenishment` surface is an
 * additive in-console NAV destination — the SECOND scm surface. It must NOT
 * disturb the data-driven catalog routing: `iam.baseRoute` still resolves to
 * `/accounts`, and a non-IAM product (incl. `scm`) keeps its registry
 * `baseRoute`. SCM becomes a Vercel-style drill-in PARENT of 운영(/scm) +
 * 보충(/scm/replenishment) — the FE-008 read section (운영) is unchanged.
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
  usePathname: () => '/scm/replenishment',
}));

describe('scm replenishment nav — additive, does not disturb the catalog routing', () => {
  it('iam still resolves to /accounts (FE-002 contract unchanged)', () => {
    expect(resolveConsoleRoute(gap)).toBe('/accounts');
  });

  it('scm keeps its registry baseRoute (resolveConsoleRoute additive)', () => {
    expect(resolveConsoleRoute(scm)).toBe('/scm');
  });

  it('the new /scm/replenishment nav item renders and resolves (보충 child)', () => {
    render(<ConsoleSidebarNav />);
    const link = screen.getByTestId('nav-scm-replenishment');
    expect(link).toBeInTheDocument();
    expect(link).toHaveAttribute('href', '/scm/replenishment');
    // SCM is now a drill-in parent: the /scm destination (the FE-008 read
    // section) lives on the 운영 child (nav-scm-ops); nav-scm is the pinned
    // parent back-toggle (a button, not a link → no href).
    expect(screen.getByTestId('nav-scm-ops')).toHaveAttribute('href', '/scm');
    expect(screen.getByTestId('nav-scm')).not.toHaveAttribute('href');
  });

  it('the /scm/replenishment nav item is marked active on the replenishment route (운영 is not)', () => {
    render(<ConsoleSidebarNav />);
    // Longest-prefix active: 보충(/scm/replenishment) lights up, 운영(/scm) not.
    expect(screen.getByTestId('nav-scm-replenishment')).toHaveAttribute(
      'aria-current',
      'page',
    );
    expect(screen.getByTestId('nav-scm-ops')).not.toHaveAttribute(
      'aria-current',
    );
  });
});
