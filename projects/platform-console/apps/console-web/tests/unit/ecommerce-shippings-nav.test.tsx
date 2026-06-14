import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { ConsoleSidebarNav } from '@/shared/ui/ConsoleSidebarNav';

/**
 * Regression (TASK-PC-FE-088 AC): the `/ecommerce/shippings` surface is an
 * additive in-console NAV destination. After PC-FE-088:
 *   - `nav-ecommerce` = pinned parent back-toggle (button, no href).
 *   - `nav-ecommerce-ops` = 운영 child (→ `/ecommerce`).
 *   - `nav-ecommerce-products` = 상품 child (→ `/ecommerce/products`).
 *   - `nav-ecommerce-orders` = 주문 child (→ `/ecommerce/orders`).
 *   - `nav-ecommerce-users` = 사용자 child (→ `/ecommerce/users`).
 *   - `nav-ecommerce-promotions` = 프로모션 child (→ `/ecommerce/promotions`).
 *   - `nav-ecommerce-shippings` = NEW 배송 child (→ `/ecommerce/shippings`).
 *
 * Existing testid+href must remain byte-unchanged (AC invariant).
 * On `/ecommerce/shippings` route the `배송` leaf is auto-active; others are not.
 */

vi.mock('next/navigation', () => ({
  usePathname: () => '/ecommerce/shippings',
}));

describe('ecommerce shippings nav — additive, preserves existing leaves', () => {
  it('the new /ecommerce/shippings nav item renders with correct href', () => {
    render(<ConsoleSidebarNav />);
    const link = screen.getByTestId('nav-ecommerce-shippings');
    expect(link).toBeInTheDocument();
    expect(link).toHaveAttribute('href', '/ecommerce/shippings');
    expect(link).toHaveTextContent('배송');
  });

  it('existing nav items are byte-unchanged (ops/products/orders/users/promotions)', () => {
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
    expect(screen.getByTestId('nav-ecommerce-promotions')).toHaveAttribute(
      'href',
      '/ecommerce/promotions',
    );
  });

  it('nav-ecommerce is still the pinned parent back-toggle (no href)', () => {
    render(<ConsoleSidebarNav />);
    expect(screen.getByTestId('nav-ecommerce')).not.toHaveAttribute('href');
  });

  it('배송 leaf is active (aria-current=page) on /ecommerce/shippings; others are not', () => {
    render(<ConsoleSidebarNav />);
    expect(screen.getByTestId('nav-ecommerce-shippings')).toHaveAttribute(
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
    expect(screen.getByTestId('nav-ecommerce-promotions')).not.toHaveAttribute(
      'aria-current',
    );
  });
});
