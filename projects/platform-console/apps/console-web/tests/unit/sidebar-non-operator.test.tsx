import { describe, it, expect, vi, beforeEach } from 'vitest';
import type { ComponentType } from 'react';
import { render, screen, cleanup } from '@testing-library/react';
import { ConsoleSidebarNav } from '@/shared/ui/ConsoleSidebarNav';

/**
 * TASK-MONO-773 S6 — console-integration-contract § 2.6.4 «Sidebar (non-operator state)»:
 *   - non-operator shell: EXACTLY two items, in this order — «가이드» (`/guide`) · «테넌트 생성» (`/new-tenant`).
 *     No switcher, no domain menus, no group labels.
 *   - operator shell: gains the same «테넌트 생성» item for every operator (role-independent — even when
 *     `/api/admin/me` answered roles that hide other items); `/tenants` keeps its list.
 */

let mockPath = '/new-tenant';
vi.mock('next/navigation', () => ({
  usePathname: () => mockPath,
}));

/** The sidebar under the `variant` prop the non-operator shell passes (typed loosely on purpose). */
const Nav = ConsoleSidebarNav as unknown as ComponentType<Record<string, unknown>>;

beforeEach(() => {
  cleanup();
  mockPath = '/new-tenant';
});

function navLinks(): Array<{ testid: string | null; href: string | null; text: string }> {
  return Array.from(screen.getByRole('navigation').querySelectorAll('a,button')).map((el) => ({
    testid: el.getAttribute('data-testid'),
    href: el.getAttribute('href'),
    text: el.textContent?.trim() ?? '',
  }));
}

describe('non-operator sidebar — two items only', () => {
  it('🔴 exactly «가이드» → «테넌트 생성», nothing else', () => {
    render(<Nav variant="non-operator" />);
    expect(navLinks()).toEqual([
      { testid: 'nav-global-guide', href: '/guide', text: '가이드' },
      { testid: 'nav-new-tenant', href: '/new-tenant', text: '테넌트 생성' },
    ]);
  });

  it('🔴 no group labels, no domain parents, no operator screens', () => {
    render(<Nav variant="non-operator" />);
    expect(screen.queryByTestId('nav-group-org-settings')).toBeNull();
    expect(screen.queryByTestId('nav-wms')).toBeNull();
    expect(screen.queryByTestId('nav-dashboards')).toBeNull();
    expect(screen.queryByTestId('nav-tenants')).toBeNull();
  });

  it('the current page is marked', () => {
    render(<Nav variant="non-operator" />);
    expect(screen.getByTestId('nav-new-tenant')).toHaveAttribute('aria-current', 'page');
    expect(screen.getByTestId('nav-global-guide')).not.toHaveAttribute('aria-current');
  });
});

describe('operator sidebar — gains «테넌트 생성» for every operator', () => {
  it('🔴 default render has the «테넌트 생성» link to /new-tenant', () => {
    mockPath = '/dashboards/overview';
    render(<ConsoleSidebarNav />);
    const link = screen.getByRole('link', { name: '테넌트 생성' });
    expect(link).toHaveAttribute('href', '/new-tenant');
    expect(link).toHaveAttribute('data-testid', 'nav-new-tenant');
  });

  it('🔴 a company operator whose roles hide /tenants still sees «테넌트 생성» (second company — ADR-MONO-082 D6 b)', () => {
    mockPath = '/dashboards/overview';
    render(<ConsoleSidebarNav myRoles={['TENANT_ADMIN', 'TENANT_BILLING_ADMIN']} />);
    expect(screen.queryByTestId('nav-tenants')).toBeNull();
    expect(screen.getByTestId('nav-new-tenant')).toHaveAttribute('href', '/new-tenant');
  });

  it('🔵 control — `/tenants` keeps its list entry for a SUPER_ADMIN', () => {
    mockPath = '/dashboards/overview';
    render(<ConsoleSidebarNav myRoles={['SUPER_ADMIN']} />);
    expect(screen.getByTestId('nav-tenants')).toHaveAttribute('href', '/tenants');
  });
});
