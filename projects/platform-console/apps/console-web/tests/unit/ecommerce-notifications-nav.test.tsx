import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { ConsoleSidebarNav } from '@/shared/ui/ConsoleSidebarNav';

/**
 * Regression (TASK-PC-FE-089 AC): the `/ecommerce/notifications/templates` surface is an
 * additive in-console NAV destination. After PC-FE-089:
 *   - `nav-ecommerce` = pinned parent back-toggle (button, no href).
 *   - `nav-ecommerce-ops` = 운영 child (→ `/ecommerce`).
 *   - `nav-ecommerce-products` = 상품 child (→ `/ecommerce/products`).
 *   - `nav-ecommerce-orders` = 주문 child (→ `/ecommerce/orders`).
 *   - `nav-ecommerce-users` = 사용자 child (→ `/ecommerce/users`).
 *   - `nav-ecommerce-promotions` = 프로모션 child (→ `/ecommerce/promotions`).
 *   - `nav-ecommerce-shippings` = 배송 child (→ `/ecommerce/shippings`).
 *   - `nav-ecommerce-notifications` = NEW 알림 child (→ `/ecommerce/notifications/templates`).
 *
 * Existing testid+href must remain byte-unchanged (AC invariant).
 * On `/ecommerce/notifications/templates` route the `알림` leaf is auto-active; others are not.
 */

vi.mock('next/navigation', () => ({
  usePathname: () => '/ecommerce/notifications/templates',
}));

describe('ecommerce notifications nav — additive, preserves existing leaves', () => {
  it('the new /ecommerce/notifications/templates nav item renders with correct href', () => {
    render(<ConsoleSidebarNav />);
    const link = screen.getByTestId('nav-ecommerce-notifications');
    expect(link).toBeInTheDocument();
    expect(link).toHaveAttribute('href', '/ecommerce/notifications/templates');
    expect(link).toHaveTextContent('알림');
  });

  it('existing nav items are byte-unchanged (ops/products/orders/users/promotions/shippings)', () => {
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
    expect(screen.getByTestId('nav-ecommerce-shippings')).toHaveAttribute(
      'href',
      '/ecommerce/shippings',
    );
  });

  it('nav-ecommerce is still the pinned parent back-toggle (no href)', () => {
    render(<ConsoleSidebarNav />);
    expect(screen.getByTestId('nav-ecommerce')).not.toHaveAttribute('href');
  });

  it('알림 leaf is active (aria-current=page) on /ecommerce/notifications/templates; others are not', () => {
    render(<ConsoleSidebarNav />);
    expect(screen.getByTestId('nav-ecommerce-notifications')).toHaveAttribute(
      'aria-current',
      'page',
    );
    expect(screen.getByTestId('nav-ecommerce-ops')).not.toHaveAttribute(
      'aria-current',
    );
    expect(screen.getByTestId('nav-ecommerce-products')).not.toHaveAttribute(
      'aria-current',
    );
    expect(screen.getByTestId('nav-ecommerce-shippings')).not.toHaveAttribute(
      'aria-current',
    );
  });
});
