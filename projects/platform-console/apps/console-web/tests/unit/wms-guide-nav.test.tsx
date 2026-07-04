import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, cleanup } from '@testing-library/react';
import { ConsoleSidebarNav } from '@/shared/ui/ConsoleSidebarNav';

/**
 * TASK-PC-FE-183 — the WMS drill gains a 가이드 child (static 재고·출고 reference),
 * placed between 개요 and 재고 (mirroring IAM's 개요 → 가이드 order). Same drill
 * machinery as FE-059; these cases mirror the WMS suite in sidebar-drilldown.test.
 * The addition must NOT disturb the existing 출고 longest-prefix active behaviour.
 */
let mockPath = '/dashboards/overview';
vi.mock('next/navigation', () => ({
  usePathname: () => mockPath,
}));

beforeEach(() => {
  cleanup();
  mockPath = '/dashboards/overview';
});

describe('wms 가이드 nav (TASK-PC-FE-183)', () => {
  it('clicking WMS reveals the 가이드 child with its /wms/guide destination', () => {
    render(<ConsoleSidebarNav />);
    fireEvent.click(screen.getByTestId('nav-wms'));
    const guide = screen.getByTestId('nav-wms-guide');
    expect(guide).toHaveAttribute('href', '/wms/guide');
    // Ordered between 개요 and 재고.
    expect(screen.getByTestId('nav-wms-ops')).toHaveAttribute('href', '/wms');
    expect(screen.getByTestId('nav-wms-inventory')).toHaveAttribute(
      'href',
      '/wms/inventory',
    );
  });

  it('a deep link to /wms/guide auto-opens the WMS drill with 가이드 active', () => {
    mockPath = '/wms/guide';
    render(<ConsoleSidebarNav />);
    expect(screen.getByTestId('nav-wms-guide')).toHaveAttribute(
      'aria-current',
      'page',
    );
    // 개요(/wms) must NOT also light up on the deeper /wms/guide route.
    expect(screen.getByTestId('nav-wms-ops')).not.toHaveAttribute(
      'aria-current',
    );
  });

  it('the 가이드 child does NOT disturb the 출고 longest-prefix active on /wms/outbound', () => {
    mockPath = '/wms/outbound';
    render(<ConsoleSidebarNav />);
    expect(screen.getByTestId('nav-wms-outbound')).toHaveAttribute(
      'aria-current',
      'page',
    );
    expect(screen.getByTestId('nav-wms-guide')).not.toHaveAttribute(
      'aria-current',
    );
  });
});
