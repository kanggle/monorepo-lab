import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { ConsoleSidebarNav } from '@/shared/ui/ConsoleSidebarNav';

/**
 * Regression (TASK-PC-FE-090 AC): the `/ecommerce/sellers` surface is an
 * additive in-console NAV destination. After PC-FE-090:
 *   - `nav-ecommerce` = pinned parent back-toggle (button, no href).
 *   - `nav-ecommerce-ops` = 운영 child (→ `/ecommerce`).
 *   - `nav-ecommerce-products` = 상품 child (→ `/ecommerce/products`).
 *   - `nav-ecommerce-orders` = 주문 child (→ `/ecommerce/orders`).
 *   - `nav-ecommerce-users` = 사용자 child (→ `/ecommerce/users`).
 *   - `nav-ecommerce-promotions` = 프로모션 child (→ `/ecommerce/promotions`).
 *   - `nav-ecommerce-shippings` = 배송 child (→ `/ecommerce/shippings`).
 *   - `nav-ecommerce-notifications` = 알림 child (→ `/ecommerce/notifications/templates`).
 *   - `nav-ecommerce-sellers` = NEW 셀러 child (→ `/ecommerce/sellers`).
 *
 * Existing testid+href must remain byte-unchanged (AC invariant).
 * On `/ecommerce/sellers` route the `셀러` leaf is auto-active; others are not.
 */

vi.mock('next/navigation', () => ({
  usePathname: () => '/ecommerce/sellers',
}));

describe('ecommerce sellers nav — additive, preserves existing leaves', () => {
  it('the new /ecommerce/sellers nav item renders with correct href', () => {
    render(<ConsoleSidebarNav />);
    const link = screen.getByTestId('nav-ecommerce-sellers');
    expect(link).toBeInTheDocument();
    expect(link).toHaveAttribute('href', '/ecommerce/sellers');
    expect(link).toHaveTextContent('셀러');
  });

  it('existing ecommerce children are byte-unchanged', () => {
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
    expect(screen.getByTestId('nav-ecommerce-notifications')).toHaveAttribute(
      'href',
      '/ecommerce/notifications/templates',
    );
  });

  it('nav-ecommerce is still the pinned parent back-toggle (no href)', () => {
    render(<ConsoleSidebarNav />);
    expect(screen.getByTestId('nav-ecommerce')).not.toHaveAttribute('href');
  });

  it('셀러 leaf is active (aria-current=page) on /ecommerce/sellers; others are not', () => {
    render(<ConsoleSidebarNav />);
    expect(screen.getByTestId('nav-ecommerce-sellers')).toHaveAttribute(
      'aria-current',
      'page',
    );
    expect(screen.getByTestId('nav-ecommerce-ops')).not.toHaveAttribute(
      'aria-current',
    );
    expect(screen.getByTestId('nav-ecommerce-products')).not.toHaveAttribute(
      'aria-current',
    );
    expect(screen.getByTestId('nav-ecommerce-promotions')).not.toHaveAttribute(
      'aria-current',
    );
  });
});
