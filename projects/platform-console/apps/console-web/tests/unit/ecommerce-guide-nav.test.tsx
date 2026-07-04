import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, cleanup } from '@testing-library/react';
import { ConsoleSidebarNav } from '@/shared/ui/ConsoleSidebarNav';

/**
 * TASK-PC-FE-184 — the E-Commerce drill gains a 가이드 child (static 도메인 서비스·
 * 주문·배송·… reference), placed between 개요 and 상품 (mirroring IAM/WMS's
 * 개요 → 가이드 order). Same drill machinery as FE-059; these cases mirror the WMS
 * guide-nav suite. The addition must NOT disturb the existing longest-prefix
 * active behaviour on the deeper ecommerce child routes.
 */
let mockPath = '/dashboards/overview';
vi.mock('next/navigation', () => ({
  usePathname: () => mockPath,
}));

beforeEach(() => {
  cleanup();
  mockPath = '/dashboards/overview';
});

describe('ecommerce 가이드 nav (TASK-PC-FE-184)', () => {
  it('clicking E-Commerce reveals the 가이드 child with its /ecommerce/guide destination', () => {
    render(<ConsoleSidebarNav />);
    fireEvent.click(screen.getByTestId('nav-ecommerce'));
    const guide = screen.getByTestId('nav-ecommerce-guide');
    expect(guide).toHaveAttribute('href', '/ecommerce/guide');
    // Ordered between 개요 and 상품.
    expect(screen.getByTestId('nav-ecommerce-ops')).toHaveAttribute(
      'href',
      '/ecommerce',
    );
    expect(screen.getByTestId('nav-ecommerce-products')).toHaveAttribute(
      'href',
      '/ecommerce/products',
    );
  });

  it('a deep link to /ecommerce/guide auto-opens the E-Commerce drill with 가이드 active', () => {
    mockPath = '/ecommerce/guide';
    render(<ConsoleSidebarNav />);
    expect(screen.getByTestId('nav-ecommerce-guide')).toHaveAttribute(
      'aria-current',
      'page',
    );
    // 개요(/ecommerce) must NOT also light up on the deeper /ecommerce/guide route.
    expect(screen.getByTestId('nav-ecommerce-ops')).not.toHaveAttribute(
      'aria-current',
    );
  });

  it('the 가이드 child does NOT disturb the 상품 longest-prefix active on /ecommerce/products', () => {
    mockPath = '/ecommerce/products';
    render(<ConsoleSidebarNav />);
    expect(screen.getByTestId('nav-ecommerce-products')).toHaveAttribute(
      'aria-current',
      'page',
    );
    expect(screen.getByTestId('nav-ecommerce-guide')).not.toHaveAttribute(
      'aria-current',
    );
    // 개요 must not light up either.
    expect(screen.getByTestId('nav-ecommerce-ops')).not.toHaveAttribute(
      'aria-current',
    );
  });
});
