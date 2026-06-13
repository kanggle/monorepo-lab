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
    // SCM is ALSO a drill-in parent (TASK-PC-FE-077 added 보충): a toggle
    // button, not a link, with its submenus collapsed at the top level.
    const scm = screen.getByTestId('nav-scm');
    expect(scm.tagName).toBe('BUTTON');
    expect(scm).not.toHaveAttribute('href');
    expect(screen.queryByTestId('nav-scm-ops')).toBeNull();
    expect(screen.queryByTestId('nav-scm-replenishment')).toBeNull();
    // Finance is ALSO a drill parent (운영 + 원장, TASK-PC-FE-078) — a toggle
    // button, not a link, with its submenus collapsed until opened.
    const finance = screen.getByTestId('nav-finance');
    expect(finance.tagName).toBe('BUTTON');
    expect(finance).not.toHaveAttribute('href');
    expect(screen.queryByTestId('nav-finance-ops')).toBeNull();
    expect(screen.queryByTestId('nav-ledger')).toBeNull();
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
    // The drill replaces the top-level list — sibling domains are gone (SCM is
    // a sibling drill parent; its toggle button is also hidden inside the WMS
    // drill).
    expect(screen.queryByTestId('nav-scm')).toBeNull();
    expect(screen.queryByTestId('nav-dashboards')).toBeNull();
  });

  it('clicking the pinned WMS drills back out to the full top-level list', () => {
    render(<ConsoleSidebarNav />);
    fireEvent.click(screen.getByTestId('nav-wms')); // in
    expect(screen.getByTestId('nav-wms-outbound')).toBeInTheDocument();

    fireEvent.click(screen.getByTestId('nav-wms')); // out
    // Submenus collapsed, the full top-level list restored. SCM is restored as
    // its (collapsed) drill-in parent toggle button (no href).
    expect(screen.queryByTestId('nav-wms-outbound')).toBeNull();
    expect(screen.getByTestId('nav-scm').tagName).toBe('BUTTON');
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

  // After TASK-PC-FE-076 (ERP → drill parent), TASK-PC-FE-077 (SCM → drill
  // parent), and TASK-PC-FE-078 (Finance → drill parent), every 도메인 운영
  // item is a drill parent — no leaf domain links remain to assert.

  // --- SCM drill parent (TASK-PC-FE-077) — mirrors the WMS tests above ---

  it('clicking SCM drills in: pins SCM at top and reveals 운영 + 보충 + 설정', () => {
    render(<ConsoleSidebarNav />);
    fireEvent.click(screen.getByTestId('nav-scm'));

    expect(screen.getByTestId('nav-scm-ops')).toHaveAttribute('href', '/scm');
    expect(screen.getByTestId('nav-scm-replenishment')).toHaveAttribute(
      'href',
      '/scm/replenishment',
    );
    // TASK-PC-FE-080 — 설정 is the THIRD SCM child (seed/config operator
    // surface), mirroring the 보충 child above.
    expect(screen.getByTestId('nav-scm-config')).toHaveAttribute(
      'href',
      '/scm/config',
    );
    const nav = screen.getByRole('navigation');
    const firstControl = nav.querySelector('a,button');
    expect(firstControl).toHaveAttribute('data-testid', 'nav-scm');
    // The drill replaces the top-level list — sibling domains are gone.
    expect(screen.queryByTestId('nav-wms')).toBeNull();
    expect(screen.queryByTestId('nav-finance')).toBeNull();
  });

  it('a deep link to /scm/replenishment auto-opens the SCM drill with 보충 active and 운영/설정 inactive', () => {
    mockPath = '/scm/replenishment';
    render(<ConsoleSidebarNav />);
    expect(screen.getByTestId('nav-scm-replenishment')).toHaveAttribute(
      'aria-current',
      'page',
    );
    expect(screen.getByTestId('nav-scm-ops')).not.toHaveAttribute(
      'aria-current',
    );
    expect(screen.getByTestId('nav-scm-config')).not.toHaveAttribute(
      'aria-current',
    );
  });

  it('a deep link to /scm/config auto-opens the SCM drill with 설정 active and 운영/보충 inactive (TASK-PC-FE-080)', () => {
    mockPath = '/scm/config';
    render(<ConsoleSidebarNav />);
    expect(screen.getByTestId('nav-scm-config')).toHaveAttribute(
      'aria-current',
      'page',
    );
    expect(screen.getByTestId('nav-scm-ops')).not.toHaveAttribute(
      'aria-current',
    );
    expect(screen.getByTestId('nav-scm-replenishment')).not.toHaveAttribute(
      'aria-current',
    );
  });

  it('a deep link to /scm auto-opens the SCM drill with 운영 active and 보충/설정 inactive (longest-prefix)', () => {
    mockPath = '/scm';
    render(<ConsoleSidebarNav />);
    expect(screen.getByTestId('nav-scm-ops')).toHaveAttribute(
      'aria-current',
      'page',
    );
    expect(screen.getByTestId('nav-scm-replenishment')).not.toHaveAttribute(
      'aria-current',
    );
    // `/scm` (운영) must NOT also light up on the deeper /scm/config route — but
    // here the path IS /scm, so 설정 stays inactive.
    expect(screen.getByTestId('nav-scm-config')).not.toHaveAttribute(
      'aria-current',
    );
  });

  // --- Finance drill parent (TASK-PC-FE-078) — mirrors the WMS tests above ---

  it('clicking Finance drills in: reveals 운영 + 원장 with their destinations', () => {
    render(<ConsoleSidebarNav />);
    fireEvent.click(screen.getByTestId('nav-finance'));

    expect(screen.getByTestId('nav-finance-ops')).toHaveAttribute(
      'href',
      '/finance',
    );
    expect(screen.getByTestId('nav-ledger')).toHaveAttribute('href', '/ledger');
    // Pinned parent is the first focusable nav control.
    const nav = screen.getByRole('navigation');
    expect(nav.querySelector('a,button')).toHaveAttribute(
      'data-testid',
      'nav-finance',
    );
    // The drill replaces the top-level list — sibling domains are gone.
    expect(screen.queryByTestId('nav-scm')).toBeNull();
  });

  it('a deep link to /ledger auto-opens the Finance drill with 원장 active and 운영 inactive', () => {
    mockPath = '/ledger';
    render(<ConsoleSidebarNav />);
    expect(screen.getByTestId('nav-ledger')).toHaveAttribute(
      'aria-current',
      'page',
    );
    expect(screen.getByTestId('nav-finance-ops')).not.toHaveAttribute(
      'aria-current',
    );
  });

  it('a deep link to /finance auto-opens the Finance drill with 운영 active and 원장 inactive', () => {
    mockPath = '/finance';
    render(<ConsoleSidebarNav />);
    expect(screen.getByTestId('nav-finance-ops')).toHaveAttribute(
      'aria-current',
      'page',
    );
    expect(screen.getByTestId('nav-ledger')).not.toHaveAttribute(
      'aria-current',
    );
  });
});

/**
 * TASK-PC-FE-076 — ERP becomes the SECOND drill parent (after WMS): the
 * single dense `/erp` page is split into 4 section routes. The parent route
 * `/erp` doubles as the first child (마스터), exactly as `/wms` is WMS's 운영
 * child. Same drill machinery as FE-059 — these cases mirror the WMS suite.
 */
describe('sidebar drill-in — ERP parent (TASK-PC-FE-076)', () => {
  it('top-level list renders ERP as a toggle button, not a link, with submenus hidden', () => {
    render(<ConsoleSidebarNav />);
    const erp = screen.getByTestId('nav-erp');
    expect(erp.tagName).toBe('BUTTON');
    expect(erp).not.toHaveAttribute('href');
    expect(screen.queryByTestId('nav-erp-masters')).toBeNull();
    expect(screen.queryByTestId('nav-erp-orgview')).toBeNull();
    expect(screen.queryByTestId('nav-erp-approval')).toBeNull();
    expect(screen.queryByTestId('nav-erp-delegation')).toBeNull();
  });

  it('clicking ERP drills in: pins ERP at top and reveals 마스터/통합 조회/결재함/위임', () => {
    render(<ConsoleSidebarNav />);
    fireEvent.click(screen.getByTestId('nav-erp'));

    expect(screen.getByTestId('nav-erp-masters')).toHaveAttribute(
      'href',
      '/erp',
    );
    expect(screen.getByTestId('nav-erp-orgview')).toHaveAttribute(
      'href',
      '/erp/orgview',
    );
    expect(screen.getByTestId('nav-erp-approval')).toHaveAttribute(
      'href',
      '/erp/approval',
    );
    expect(screen.getByTestId('nav-erp-delegation')).toHaveAttribute(
      'href',
      '/erp/delegation',
    );
    // Pinned parent is the first focusable nav control (top of the rail).
    const nav = screen.getByRole('navigation');
    const firstControl = nav.querySelector('a,button');
    expect(firstControl).toHaveAttribute('data-testid', 'nav-erp');
    // The drill replaces the top-level list — sibling domains are gone.
    expect(screen.queryByTestId('nav-scm')).toBeNull();
    expect(screen.queryByTestId('nav-wms')).toBeNull();
  });

  it('clicking the pinned ERP drills back out to the full top-level list', () => {
    render(<ConsoleSidebarNav />);
    fireEvent.click(screen.getByTestId('nav-erp')); // in
    expect(screen.getByTestId('nav-erp-orgview')).toBeInTheDocument();

    fireEvent.click(screen.getByTestId('nav-erp')); // out
    expect(screen.queryByTestId('nav-erp-orgview')).toBeNull();
    // SCM is restored as its (collapsed) drill-in parent toggle button.
    expect(screen.getByTestId('nav-scm').tagName).toBe('BUTTON');
    expect(screen.getByTestId('nav-dashboards')).toBeInTheDocument();
  });

  it('a deep link to /erp/orgview auto-opens the ERP drill with 통합 조회 active and 마스터(/erp) inactive (longest-prefix)', () => {
    mockPath = '/erp/orgview';
    render(<ConsoleSidebarNav />);
    expect(screen.getByTestId('nav-erp-orgview')).toHaveAttribute(
      'aria-current',
      'page',
    );
    // `/erp` (마스터) must NOT also light up on the deeper child route.
    expect(screen.getByTestId('nav-erp-masters')).not.toHaveAttribute(
      'aria-current',
    );
  });

  it('a deep link to /erp auto-opens the ERP drill with 마스터 active', () => {
    mockPath = '/erp';
    render(<ConsoleSidebarNav />);
    expect(screen.getByTestId('nav-erp-masters')).toHaveAttribute(
      'aria-current',
      'page',
    );
    expect(screen.getByTestId('nav-erp-orgview')).not.toHaveAttribute(
      'aria-current',
    );
  });
});
