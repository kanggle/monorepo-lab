import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { ConsoleSidebarNav } from '@/shared/ui/ConsoleSidebarNav';

/**
 * Regression (TASK-PC-FE-084 AC-4): the `/ecommerce/users` surface is an
 * additive in-console NAV destination. After PC-FE-084:
 *   - `nav-ecommerce` = pinned parent back-toggle (button, no href).
 *   - `nav-ecommerce-ops` = 운영 child (→ `/ecommerce`).
 *   - `nav-ecommerce-products` = 상품 child (→ `/ecommerce/products`).
 *   - `nav-ecommerce-orders` = 주문 child (→ `/ecommerce/orders`).
 *   - `nav-ecommerce-users` = NEW 사용자 child (→ `/ecommerce/users`).
 *
 * Existing 운영/상품/주문 testid+href must remain byte-unchanged (AC-4 invariant).
 * On `/ecommerce/users` route the `사용자` leaf is auto-active; others are not.
 */

vi.mock('next/navigation', () => ({
  usePathname: () => '/ecommerce/users',
}));

describe('ecommerce users nav — additive, preserves existing leaves', () => {
  it('the new /ecommerce/users nav item renders with correct href', () => {
    render(<ConsoleSidebarNav />);
    const link = screen.getByTestId('nav-ecommerce-users');
    expect(link).toBeInTheDocument();
    expect(link).toHaveAttribute('href', '/ecommerce/users');
    expect(link).toHaveTextContent('사용자');
  });

  it('existing nav-ecommerce-ops, nav-ecommerce-products, nav-ecommerce-orders are byte-unchanged', () => {
    render(<ConsoleSidebarNav />);
    expect(screen.getByTestId('nav-ecommerce-ops')).toHaveAttribute(
      'href',
      '/ecommerce',
    );
    expect(screen.getByTestId('nav-ecommerce-products')).toHaveAttribute(
      'href',
      '/ecommerce/products',
    );
    expect(screen.getByTestId('nav-ecommerce-orders')).toHaveAttribute(
      'href',
      '/ecommerce/orders',
    );
  });

  it('nav-ecommerce is still the pinned parent back-toggle (no href)', () => {
    render(<ConsoleSidebarNav />);
    expect(screen.getByTestId('nav-ecommerce')).not.toHaveAttribute('href');
  });

  it('사용자 leaf is active (aria-current=page) on /ecommerce/users; others are not', () => {
    render(<ConsoleSidebarNav />);
    expect(screen.getByTestId('nav-ecommerce-users')).toHaveAttribute(
      'aria-current',
      'page',
    );
    expect(screen.getByTestId('nav-ecommerce-ops')).not.toHaveAttribute(
      'aria-current',
    );
    expect(screen.getByTestId('nav-ecommerce-products')).not.toHaveAttribute(
      'aria-current',
    );
    expect(screen.getByTestId('nav-ecommerce-orders')).not.toHaveAttribute(
      'aria-current',
    );
  });
});
