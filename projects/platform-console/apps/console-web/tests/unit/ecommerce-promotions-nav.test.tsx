import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { ConsoleSidebarNav } from '@/shared/ui/ConsoleSidebarNav';

/**
 * Regression (TASK-PC-FE-086 AC): the `/ecommerce/promotions` surface is an
 * additive in-console NAV destination. After PC-FE-086:
 *   - `nav-ecommerce` = pinned parent back-toggle (button, no href).
 *   - `nav-ecommerce-ops` = 운영 child (→ `/ecommerce`).
 *   - `nav-ecommerce-products` = 상품 child (→ `/ecommerce/products`).
 *   - `nav-ecommerce-orders` = 주문 child (→ `/ecommerce/orders`).
 *   - `nav-ecommerce-users` = 사용자 child (→ `/ecommerce/users`).
 *   - `nav-ecommerce-promotions` = NEW 프로모션 child (→ `/ecommerce/promotions`).
 *
 * Existing 운영/상품/주문/사용자 testid+href must remain byte-unchanged (AC invariant).
 * On `/ecommerce/promotions` route the `프로모션` leaf is auto-active; others are not.
 */

vi.mock('next/navigation', () => ({
  usePathname: () => '/ecommerce/promotions',
}));

describe('ecommerce promotions nav — additive, preserves existing leaves', () => {
  it('the new /ecommerce/promotions nav item renders with correct href', () => {
    render(<ConsoleSidebarNav />);
    const link = screen.getByTestId('nav-ecommerce-promotions');
    expect(link).toBeInTheDocument();
    expect(link).toHaveAttribute('href', '/ecommerce/promotions');
    expect(link).toHaveTextContent('프로모션');
  });

  it('existing nav-ecommerce-ops, nav-ecommerce-products, nav-ecommerce-orders, nav-ecommerce-users are byte-unchanged', () => {
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
    expect(screen.getByTestId('nav-ecommerce-users')).toHaveAttribute(
      'href',
      '/ecommerce/users',
    );
  });

  it('nav-ecommerce is still the pinned parent back-toggle (no href)', () => {
    render(<ConsoleSidebarNav />);
    expect(screen.getByTestId('nav-ecommerce')).not.toHaveAttribute('href');
  });

  it('프로모션 leaf is active (aria-current=page) on /ecommerce/promotions; others are not', () => {
    render(<ConsoleSidebarNav />);
    expect(screen.getByTestId('nav-ecommerce-promotions')).toHaveAttribute(
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
    expect(screen.getByTestId('nav-ecommerce-users')).not.toHaveAttribute(
      'aria-current',
    );
  });
});
