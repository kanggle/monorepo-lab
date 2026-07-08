import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { ConsoleSidebarNav } from '@/shared/ui/ConsoleSidebarNav';

/**
 * Regression (TASK-PC-FE-221 AC-1): the `/ecommerce/settlements` surface is an
 * additive in-console NAV destination, placed after 셀러 and before 알림:
 *   - `nav-ecommerce-settlements` = NEW 정산 child (→ `/ecommerce/settlements`).
 *   - every existing ecommerce leaf's testid + href stays byte-unchanged.
 * On `/ecommerce/settlements` the 정산 leaf is auto-active; others are not.
 */

vi.mock('next/navigation', () => ({
  usePathname: () => '/ecommerce/settlements',
}));

describe('ecommerce settlements nav — additive, preserves existing leaves', () => {
  it('the new /ecommerce/settlements nav item renders with correct href + label', () => {
    render(<ConsoleSidebarNav />);
    const link = screen.getByTestId('nav-ecommerce-settlements');
    expect(link).toBeInTheDocument();
    expect(link).toHaveAttribute('href', '/ecommerce/settlements');
    expect(link).toHaveTextContent('정산');
  });

  it('existing ecommerce children are byte-unchanged (incl. 셀러 before / 알림 after)', () => {
    render(<ConsoleSidebarNav />);
    expect(screen.getByTestId('nav-ecommerce-ops')).toHaveAttribute(
      'href',
      '/ecommerce',
    );
    expect(screen.getByTestId('nav-ecommerce-sellers')).toHaveAttribute(
      'href',
      '/ecommerce/sellers',
    );
    expect(screen.getByTestId('nav-ecommerce-notifications')).toHaveAttribute(
      'href',
      '/ecommerce/notifications/templates',
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

  it('정산 leaf is active (aria-current=page) on /ecommerce/settlements; others are not', () => {
    render(<ConsoleSidebarNav />);
    expect(screen.getByTestId('nav-ecommerce-settlements')).toHaveAttribute(
      'aria-current',
      'page',
    );
    expect(screen.getByTestId('nav-ecommerce-sellers')).not.toHaveAttribute(
      'aria-current',
    );
    expect(screen.getByTestId('nav-ecommerce-notifications')).not.toHaveAttribute(
      'aria-current',
    );
  });
});
