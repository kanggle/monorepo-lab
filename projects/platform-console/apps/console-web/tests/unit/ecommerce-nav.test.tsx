import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { resolveConsoleRoute } from '@/features/catalog';
import type { RegistryProduct } from '@/shared/api/registry-types';
import { ConsoleSidebarNav } from '@/shared/ui/ConsoleSidebarNav';

/**
 * Regression (TASK-PC-FE-081 AC-4): the `/ecommerce/products` surface is an
 * additive in-console NAV destination — ecommerce becomes a Vercel-style
 * drill-in PARENT of 운영(/ecommerce) + 상품(/ecommerce/products). On the
 * mocked `/ecommerce/products` route the ecommerce group is auto-drilled —
 * `nav-ecommerce` is the pinned parent back-toggle (a button), `nav-ecommerce-ops`
 * is the 운영 child (→ `/ecommerce`), and `nav-ecommerce-products` is the active
 * 상품 child. It must NOT disturb the data-driven catalog routing.
 */

const ecommerce: RegistryProduct = {
  productKey: 'ecommerce',
  displayName: 'E-Commerce',
  available: true,
  tenants: ['ecommerce'],
  baseRoute: '/ecommerce',
};

vi.mock('next/navigation', () => ({
  usePathname: () => '/ecommerce/products',
}));

describe('ecommerce products nav — additive, does not disturb the catalog routing', () => {
  it('ecommerce keeps its registry baseRoute (resolveConsoleRoute additive)', () => {
    expect(resolveConsoleRoute(ecommerce)).toBe('/ecommerce');
  });

  it('the new /ecommerce/products nav item renders and resolves (상품 child)', () => {
    render(<ConsoleSidebarNav />);
    const link = screen.getByTestId('nav-ecommerce-products');
    expect(link).toBeInTheDocument();
    expect(link).toHaveAttribute('href', '/ecommerce/products');
    // ecommerce is a drill-in parent: the /ecommerce destination lives on the
    // 운영 child (nav-ecommerce-ops); nav-ecommerce is the pinned back-toggle
    // (a button, not a link → no href).
    expect(screen.getByTestId('nav-ecommerce-ops')).toHaveAttribute(
      'href',
      '/ecommerce',
    );
    expect(screen.getByTestId('nav-ecommerce')).not.toHaveAttribute('href');
  });

  it('the /ecommerce/products nav item is active on the products route (운영 is not)', () => {
    render(<ConsoleSidebarNav />);
    expect(screen.getByTestId('nav-ecommerce-products')).toHaveAttribute(
      'aria-current',
      'page',
    );
    expect(screen.getByTestId('nav-ecommerce-ops')).not.toHaveAttribute(
      'aria-current',
    );
  });
});
