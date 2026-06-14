import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { ConsoleSidebarNav } from '@/shared/ui/ConsoleSidebarNav';

/**
 * Regression (TASK-PC-FE-083 AC-4): the `/ecommerce/orders` surface is an
 * additive in-console NAV destination. After PC-FE-083:
 *   - `nav-ecommerce` = pinned parent back-toggle (button, no href).
 *   - `nav-ecommerce-ops` = 운영 child (→ `/ecommerce`).
 *   - `nav-ecommerce-products` = 상품 child (→ `/ecommerce/products`).
 *   - `nav-ecommerce-orders` = NEW 주문 child (→ `/ecommerce/orders`).
 *
 * Existing 운영/상품 testid+href must remain byte-unchanged (AC-4 invariant).
 * On `/ecommerce/orders` route the `주문` leaf is auto-active; 운영/상품 are not.
 */

vi.mock('next/navigation', () => ({
  usePathname: () => '/ecommerce/orders',
}));

describe('ecommerce orders nav — additive, preserves existing leaves', () => {
  it('the new /ecommerce/orders nav item renders with correct href', () => {
    render(<ConsoleSidebarNav />);
    const link = screen.getByTestId('nav-ecommerce-orders');
    expect(link).toBeInTheDocument();
    expect(link).toHaveAttribute('href', '/ecommerce/orders');
    expect(link).toHaveTextContent('주문');
  });

  it('existing nav-ecommerce-ops and nav-ecommerce-products are byte-unchanged', () => {
    render(<ConsoleSidebarNav />);
    expect(screen.getByTestId('nav-ecommerce-ops')).toHaveAttribute(
      'href',
      '/ecommerce',
    );
    expect(screen.getByTestId('nav-ecommerce-products')).toHaveAttribute(
      'href',
      '/ecommerce/products',
    );
  });

  it('nav-ecommerce is still the pinned parent back-toggle (no href)', () => {
    render(<ConsoleSidebarNav />);
    expect(screen.getByTestId('nav-ecommerce')).not.toHaveAttribute('href');
  });

  it('주문 leaf is active (aria-current=page) on /ecommerce/orders; 운영/상품 are not', () => {
    render(<ConsoleSidebarNav />);
    expect(screen.getByTestId('nav-ecommerce-orders')).toHaveAttribute(
      'aria-current',
      'page',
    );
    expect(screen.getByTestId('nav-ecommerce-ops')).not.toHaveAttribute(
      'aria-current',
    );
    expect(screen.getByTestId('nav-ecommerce-products')).not.toHaveAttribute(
      'aria-current',
    );
  });
});
