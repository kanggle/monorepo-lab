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

  it('clicking IAM drills in: reveals the 8-item workforce plane (개요/가이드/운영자 관리/운영자 그룹/테넌트/권한/권한 세트/감사·보안) and pins IAM at the top (TASK-PC-FE-225)', () => {
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
    expect(screen.getByTestId('nav-operators')).toHaveAttribute(
      'href',
      '/operators',
    );
    // TASK-PC-FE-225 — 4 new workforce-plane stub children.
    expect(screen.getByTestId('nav-iam-operator-groups')).toHaveAttribute(
      'href',
      '/operator-groups',
    );
    expect(screen.getByTestId('nav-iam-tenants')).toHaveAttribute(
      'href',
      '/tenants',
    );
    expect(screen.getByTestId('nav-iam-permissions')).toHaveAttribute(
      'href',
      '/permissions',
    );
    expect(screen.getByTestId('nav-iam-permission-sets')).toHaveAttribute(
      'href',
      '/permission-sets',
    );
    expect(screen.getByTestId('nav-audit')).toHaveAttribute('href', '/audit');
    // 계정 운영 (customer-identity plane) is NO LONGER an IAM child
    // (TASK-PC-FE-225 — moved to its own 「고객 신원」 group).
    expect(screen.queryByTestId('nav-accounts')).toBeNull();
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

  it('a deep link to /accounts (customer-identity plane, TASK-PC-FE-225) does NOT open the IAM drill — it stays on the top-level list with 계정 운영 active (matches the catalog IAM tile target)', () => {
    mockPath = '/accounts';
    render(<ConsoleSidebarNav />);
    // /accounts is no longer an IAM child — the top-level list is shown, with
    // IAM rendered as its collapsed toggle button (not drilled/pinned).
    expect(screen.getByTestId('nav-iam').tagName).toBe('BUTTON');
    expect(screen.queryByTestId('nav-audit')).toBeNull();
    expect(screen.queryByTestId('nav-operators')).toBeNull();
    // 계정 운영 lives in the 「고객 신원」 group as a flat leaf and is active.
    expect(screen.getByTestId('nav-accounts')).toHaveAttribute(
      'aria-current',
      'page',
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
    // `/account` ⊄ `/accounts`; it must not light up 계정 운영 (the top-bar ⋮ →
    // 계정 설정 path is unrelated to the sidebar).
    mockPath = '/account';
    render(<ConsoleSidebarNav />);
    // 계정 운영 is a flat top-level leaf (TASK-PC-FE-225 — 고객 신원 group), so
    // it IS rendered, just not active (/account does not match /accounts).
    expect(screen.getByTestId('nav-accounts')).not.toHaveAttribute(
      'aria-current',
    );
    // IAM renders as the collapsed toggle, top-level list intact.
    expect(screen.getByTestId('nav-iam').tagName).toBe('BUTTON');
    expect(screen.getByTestId('nav-dashboards')).toBeInTheDocument();
  });

  // --- 고객 신원 group (TASK-PC-FE-225 — orthodox IAM taxonomy split) ---

  it('the 「고객 신원」 group renders with its own testid and a 계정 운영 leaf pointing at /accounts', () => {
    render(<ConsoleSidebarNav />);
    expect(
      screen.getByTestId('nav-group-customer-identity'),
    ).toHaveTextContent('고객 신원');
    expect(screen.getByTestId('nav-accounts')).toHaveAttribute(
      'href',
      '/accounts',
    );
    // It is a plain link, not a drill parent (single destination).
    expect(screen.getByTestId('nav-accounts').tagName).toBe('A');
  });

  it('drilling into IAM hides the 「고객 신원」 group (top-level list is replaced)', () => {
    render(<ConsoleSidebarNav />);
    fireEvent.click(screen.getByTestId('nav-iam'));
    expect(
      screen.queryByTestId('nav-group-customer-identity'),
    ).toBeNull();
    expect(screen.queryByTestId('nav-accounts')).toBeNull();
  });

  // --- new IAM workforce-plane stub children deep-link states (TASK-PC-FE-225) ---

  it('a deep link to /operator-groups auto-opens the IAM drill with 운영자 그룹 active', () => {
    mockPath = '/operator-groups';
    render(<ConsoleSidebarNav />);
    expect(screen.getByTestId('nav-iam-operator-groups')).toHaveAttribute(
      'aria-current',
      'page',
    );
    expect(screen.getByTestId('nav-iam-tenants')).not.toHaveAttribute(
      'aria-current',
    );
  });

  it('a deep link to /tenants auto-opens the IAM drill with 테넌트 active', () => {
    mockPath = '/tenants';
    render(<ConsoleSidebarNav />);
    expect(screen.getByTestId('nav-iam-tenants')).toHaveAttribute(
      'aria-current',
      'page',
    );
    expect(screen.getByTestId('nav-iam-permissions')).not.toHaveAttribute(
      'aria-current',
    );
  });

  it('a deep link to /permissions auto-opens the IAM drill with 권한 active', () => {
    mockPath = '/permissions';
    render(<ConsoleSidebarNav />);
    expect(screen.getByTestId('nav-iam-permissions')).toHaveAttribute(
      'aria-current',
      'page',
    );
    expect(screen.getByTestId('nav-iam-permission-sets')).not.toHaveAttribute(
      'aria-current',
    );
  });

  it('a deep link to /permission-sets auto-opens the IAM drill with 권한 세트 active', () => {
    mockPath = '/permission-sets';
    render(<ConsoleSidebarNav />);
    expect(screen.getByTestId('nav-iam-permission-sets')).toHaveAttribute(
      'aria-current',
      'page',
    );
    expect(screen.getByTestId('nav-iam-operator-groups')).not.toHaveAttribute(
      'aria-current',
    );
  });
});
