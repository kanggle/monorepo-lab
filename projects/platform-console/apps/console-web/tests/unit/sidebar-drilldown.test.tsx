import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, cleanup } from '@testing-library/react';
import { ConsoleSidebarNav } from '@/shared/ui/ConsoleSidebarNav';

/**
 * TASK-PC-FE-059 — Vercel-style drill-in sidebar.
 *
 * `usePathname` is mocked per-test via a mutable holder so we can render the
 * sidebar on a non-WMS route (top-level list) and on a deep WMS route
 * (auto-drilled). Drill interactions use `fireEvent` (the nav is a client
 * component; jsdom mounts it with the mocked router hook).
 */
let mockPath = '/dashboards/overview';
vi.mock('next/navigation', () => ({
  usePathname: () => mockPath,
}));

beforeEach(() => {
  cleanup();
  mockPath = '/dashboards/overview';
});

describe('sidebar drill-in (TASK-PC-FE-059)', () => {
  it('top-level list renders WMS as a toggle button, not a link, with submenus hidden', () => {
    render(<ConsoleSidebarNav />);
    const wms = screen.getByTestId('nav-wms');
    expect(wms.tagName).toBe('BUTTON');
    expect(wms).not.toHaveAttribute('href');
    // Submenus are collapsed until the parent is opened.
    expect(screen.queryByTestId('nav-wms-ops')).toBeNull();
    expect(screen.queryByTestId('nav-wms-outbound')).toBeNull();
    // Sibling leaves are plain links and remain visible.
    expect(screen.getByTestId('nav-scm')).toHaveAttribute('href', '/scm');
    expect(screen.getByTestId('nav-finance')).toHaveAttribute('href', '/finance');
  });

  it('clicking WMS drills in: pins WMS at top and reveals 운영 + 출고; other top-level items disappear', () => {
    render(<ConsoleSidebarNav />);
    fireEvent.click(screen.getByTestId('nav-wms'));

    // Children now visible with their destinations.
    expect(screen.getByTestId('nav-wms-ops')).toHaveAttribute('href', '/wms');
    expect(screen.getByTestId('nav-wms-outbound')).toHaveAttribute(
      'href',
      '/wms/outbound',
    );
    // Pinned parent is the first focusable nav control (top of the rail).
    const nav = screen.getByRole('navigation');
    const firstControl = nav.querySelector('a,button');
    expect(firstControl).toHaveAttribute('data-testid', 'nav-wms');
    // The drill replaces the top-level list — sibling domains are gone.
    expect(screen.queryByTestId('nav-scm')).toBeNull();
    expect(screen.queryByTestId('nav-dashboards')).toBeNull();
  });

  it('clicking the pinned WMS drills back out to the full top-level list', () => {
    render(<ConsoleSidebarNav />);
    fireEvent.click(screen.getByTestId('nav-wms')); // in
    expect(screen.getByTestId('nav-wms-outbound')).toBeInTheDocument();

    fireEvent.click(screen.getByTestId('nav-wms')); // out
    // Submenus collapsed, the full top-level list restored.
    expect(screen.queryByTestId('nav-wms-outbound')).toBeNull();
    expect(screen.getByTestId('nav-scm')).toHaveAttribute('href', '/scm');
    expect(screen.getByTestId('nav-dashboards')).toBeInTheDocument();
  });

  it('a deep link to /wms/outbound auto-opens the WMS drill with 출고 active and 운영 inactive', () => {
    mockPath = '/wms/outbound';
    render(<ConsoleSidebarNav />);
    expect(screen.getByTestId('nav-wms-outbound')).toHaveAttribute(
      'aria-current',
      'page',
    );
    expect(screen.getByTestId('nav-wms-ops')).not.toHaveAttribute(
      'aria-current',
    );
  });

  it('a deep link to /wms auto-opens the WMS drill with 운영 active and 출고 inactive', () => {
    mockPath = '/wms';
    render(<ConsoleSidebarNav />);
    expect(screen.getByTestId('nav-wms-ops')).toHaveAttribute(
      'aria-current',
      'page',
    );
    expect(screen.getByTestId('nav-wms-outbound')).not.toHaveAttribute(
      'aria-current',
    );
  });

  it('leaf domains (SCM/Finance/ERP) stay direct links — no drill', () => {
    render(<ConsoleSidebarNav />);
    for (const [testid, href] of [
      ['nav-scm', '/scm'],
      ['nav-finance', '/finance'],
      ['nav-erp', '/erp'],
    ] as const) {
      const el = screen.getByTestId(testid);
      expect(el.tagName).toBe('A');
      expect(el).toHaveAttribute('href', href);
    }
  });
});
