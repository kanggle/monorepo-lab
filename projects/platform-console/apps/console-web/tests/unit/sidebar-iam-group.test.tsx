import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, cleanup } from '@testing-library/react';
import { ConsoleSidebarNav } from '@/shared/ui/ConsoleSidebarNav';

/**
 * TASK-PC-FE-060 — 감사·보안 + 운영자 관리 are both IAM-platform admin surfaces
 * (`${IAM_ADMIN_API_BASE}/api/admin/{audit,operators}`), so they nest under one
 * drill-in `IAM` parent — the same model as WMS → 운영/출고 (PC-FE-059).
 */
let mockPath = '/dashboards/overview';
vi.mock('next/navigation', () => ({
  usePathname: () => mockPath,
}));

beforeEach(() => {
  cleanup();
  mockPath = '/dashboards/overview';
});

describe('sidebar IAM parent group (TASK-PC-FE-060)', () => {
  it('collapsed: 관리 group shows IAM as a toggle button with 감사·보안/운영자 관리 hidden', () => {
    render(<ConsoleSidebarNav />);
    const iam = screen.getByTestId('nav-iam');
    expect(iam.tagName).toBe('BUTTON');
    expect(iam).not.toHaveAttribute('href');
    expect(screen.queryByTestId('nav-audit')).toBeNull();
    expect(screen.queryByTestId('nav-operators')).toBeNull();
  });

  it('clicking IAM drills in: reveals 감사·보안 + 운영자 관리 and pins IAM at the top', () => {
    render(<ConsoleSidebarNav />);
    fireEvent.click(screen.getByTestId('nav-iam'));

    expect(screen.getByTestId('nav-audit')).toHaveAttribute('href', '/audit');
    expect(screen.getByTestId('nav-operators')).toHaveAttribute(
      'href',
      '/operators',
    );
    const nav = screen.getByRole('navigation');
    expect(nav.querySelector('a,button')).toHaveAttribute(
      'data-testid',
      'nav-iam',
    );
    // The drill replaces the top-level list — other groups are gone.
    expect(screen.queryByTestId('nav-wms')).toBeNull();
    expect(screen.queryByTestId('nav-dashboards')).toBeNull();
  });

  it('clicking the pinned IAM drills back out to the full top-level list', () => {
    render(<ConsoleSidebarNav />);
    fireEvent.click(screen.getByTestId('nav-iam')); // in
    expect(screen.getByTestId('nav-audit')).toBeInTheDocument();

    fireEvent.click(screen.getByTestId('nav-iam')); // out
    expect(screen.queryByTestId('nav-audit')).toBeNull();
    expect(screen.getByTestId('nav-dashboards')).toBeInTheDocument();
    expect(screen.getByTestId('nav-wms')).toBeInTheDocument();
  });

  it('a deep link to /audit auto-opens the IAM drill with 감사·보안 active, 운영자 관리 inactive', () => {
    mockPath = '/audit';
    render(<ConsoleSidebarNav />);
    expect(screen.getByTestId('nav-audit')).toHaveAttribute(
      'aria-current',
      'page',
    );
    expect(screen.getByTestId('nav-operators')).not.toHaveAttribute(
      'aria-current',
    );
  });

  it('a deep link to /operators auto-opens the IAM drill with 운영자 관리 active, 감사·보안 inactive', () => {
    mockPath = '/operators';
    render(<ConsoleSidebarNav />);
    expect(screen.getByTestId('nav-operators')).toHaveAttribute(
      'aria-current',
      'page',
    );
    expect(screen.getByTestId('nav-audit')).not.toHaveAttribute('aria-current');
  });
});
