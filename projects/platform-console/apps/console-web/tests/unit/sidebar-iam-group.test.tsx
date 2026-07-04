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

  it('clicking IAM drills in: reveals 개요 + 가이드 + 계정 운영 + 감사·보안 + 운영자 관리 and pins IAM at the top', () => {
    render(<ConsoleSidebarNav />);
    fireEvent.click(screen.getByTestId('nav-iam'));

    // 개요 (TASK-PC-FE-180) — the LIVE overview snapshot, first child.
    expect(screen.getByTestId('nav-iam-overview')).toHaveAttribute(
      'href',
      '/iam',
    );
    // 가이드 (TASK-PC-FE-180) — the relocated static RBAC guide, second child.
    expect(screen.getByTestId('nav-iam-guide')).toHaveAttribute(
      'href',
      '/iam/guide',
    );
    // 계정 운영 (TASK-PC-FE-062) — the catalog IAM tile's target, now also a
    // sidebar IAM child.
    expect(screen.getByTestId('nav-accounts')).toHaveAttribute(
      'href',
      '/accounts',
    );
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

  it('a deep link to /accounts auto-opens the IAM drill with 계정 운영 active (matches the catalog IAM tile target)', () => {
    mockPath = '/accounts';
    render(<ConsoleSidebarNav />);
    expect(screen.getByTestId('nav-accounts')).toHaveAttribute(
      'aria-current',
      'page',
    );
    expect(screen.getByTestId('nav-audit')).not.toHaveAttribute('aria-current');
    expect(screen.getByTestId('nav-operators')).not.toHaveAttribute(
      'aria-current',
    );
  });

  it('a deep link to /iam auto-opens the IAM drill with 개요 active, 가이드 inactive (TASK-PC-FE-180)', () => {
    mockPath = '/iam';
    render(<ConsoleSidebarNav />);
    expect(screen.getByTestId('nav-iam-overview')).toHaveAttribute(
      'aria-current',
      'page',
    );
    // `/iam/guide` startsWith `/iam/` but the longest-match rule keeps 개요
    // active at the exact `/iam` route (가이드 is NOT lit up).
    expect(screen.getByTestId('nav-iam-guide')).not.toHaveAttribute(
      'aria-current',
    );
  });

  it('a deep link to /iam/guide auto-opens the IAM drill with 가이드 active, 개요 inactive (longest-match wins, TASK-PC-FE-180)', () => {
    mockPath = '/iam/guide';
    render(<ConsoleSidebarNav />);
    expect(screen.getByTestId('nav-iam-guide')).toHaveAttribute(
      'aria-current',
      'page',
    );
    expect(screen.getByTestId('nav-iam-overview')).not.toHaveAttribute(
      'aria-current',
    );
  });

  it('/account (singular, my settings) does NOT activate 계정 운영 — distinct from /accounts', () => {
    // `/account` ⊄ `/accounts`; it must not auto-open the IAM drill nor light
    // up 계정 운영 (the top-bar ⋮ → 계정 설정 path is unrelated to the sidebar).
    mockPath = '/account';
    render(<ConsoleSidebarNav />);
    // Not drilled into IAM → 계정 운영 child not rendered at all.
    expect(screen.queryByTestId('nav-accounts')).toBeNull();
    // IAM renders as the collapsed toggle, top-level list intact.
    expect(screen.getByTestId('nav-iam').tagName).toBe('BUTTON');
    expect(screen.getByTestId('nav-dashboards')).toBeInTheDocument();
  });
});
