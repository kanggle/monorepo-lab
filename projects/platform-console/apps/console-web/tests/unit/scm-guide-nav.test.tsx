import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, cleanup } from '@testing-library/react';
import { ConsoleSidebarNav } from '@/shared/ui/ConsoleSidebarNav';

/**
 * TASK-PC-FE-188 — the SCM drill gains a 가이드 child (static 도메인 서비스·발주·
 * 재고 가시성·보충·설정 reference), now placed FIRST, before 개요 (TASK-PC-FE-297 —
 * all domains 가이드 → 개요). Same drill machinery as FE-059; these
 * cases mirror the ecommerce guide-nav suite. The addition must NOT disturb the
 * existing longest-prefix active behaviour on the deeper scm child routes
 * (/scm/replenishment, /scm/config).
 */
let mockPath = '/dashboards/overview';
vi.mock('next/navigation', () => ({
  usePathname: () => mockPath,
}));

beforeEach(() => {
  cleanup();
  mockPath = '/dashboards/overview';
});

describe('scm 가이드 nav (TASK-PC-FE-188)', () => {
  it('clicking SCM reveals the 가이드 child with its /scm/guide destination', () => {
    render(<ConsoleSidebarNav />);
    fireEvent.click(screen.getByTestId('nav-scm'));
    const guide = screen.getByTestId('nav-scm-guide');
    expect(guide).toHaveAttribute('href', '/scm/guide');
    // 가이드 → 개요 → … → 보충 (TASK-PC-FE-297; order pinned in sidebar-nav-order-icons.test).
    expect(screen.getByTestId('nav-scm-ops')).toHaveAttribute('href', '/scm');
    expect(screen.getByTestId('nav-scm-replenishment')).toHaveAttribute(
      'href',
      '/scm/replenishment',
    );
  });

  it('a deep link to /scm/guide auto-opens the SCM drill with 가이드 active', () => {
    mockPath = '/scm/guide';
    render(<ConsoleSidebarNav />);
    expect(screen.getByTestId('nav-scm-guide')).toHaveAttribute(
      'aria-current',
      'page',
    );
    // 개요(/scm) must NOT also light up on the deeper /scm/guide route.
    expect(screen.getByTestId('nav-scm-ops')).not.toHaveAttribute(
      'aria-current',
    );
  });

  it('the 가이드 child does NOT disturb the 보충 longest-prefix active on /scm/replenishment', () => {
    mockPath = '/scm/replenishment';
    render(<ConsoleSidebarNav />);
    expect(screen.getByTestId('nav-scm-replenishment')).toHaveAttribute(
      'aria-current',
      'page',
    );
    expect(screen.getByTestId('nav-scm-guide')).not.toHaveAttribute(
      'aria-current',
    );
    // 개요 must not light up either.
    expect(screen.getByTestId('nav-scm-ops')).not.toHaveAttribute(
      'aria-current',
    );
  });
});
