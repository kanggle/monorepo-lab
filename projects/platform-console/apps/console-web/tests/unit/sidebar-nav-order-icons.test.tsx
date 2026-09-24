import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, cleanup } from '@testing-library/react';
import { ConsoleSidebarNav } from '@/shared/ui/ConsoleSidebarNav';
import { GROUPS, isParent } from '@/shared/ui/console-nav-config';
import { navPathFor } from '@/shared/ui/console-nav-matching';

/**
 * TASK-PC-FE-297 — 가이드-first order, item icons, subordinate group labels,
 * the collapsed-drill active marker, and the `/dashboards/health` alias.
 */
let mockPath = '/console';
vi.mock('next/navigation', () => ({
  usePathname: () => mockPath,
}));

beforeEach(() => {
  cleanup();
  mockPath = '/console';
});

const DOMAIN_ORDER: Array<[parent: string, guide: string, overview: string]> = [
  ['nav-iam', 'nav-iam-guide', 'nav-iam-overview'],
  ['nav-wms', 'nav-wms-guide', 'nav-wms-ops'],
  ['nav-scm', 'nav-scm-guide', 'nav-scm-ops'],
  ['nav-finance', 'nav-finance-guide', 'nav-finance-overview'],
  ['nav-erp', 'nav-erp-guide', 'nav-erp-overview'],
  ['nav-ecommerce', 'nav-ecommerce-guide', 'nav-ecommerce-ops'],
];

describe('2뎁스 순서 — 가이드 → 개요 in all six domains (AC-1)', () => {
  it.each(DOMAIN_ORDER)(
    '%s drill lists %s first and %s second',
    (parent, guide, overview) => {
      render(<ConsoleSidebarNav />);
      fireEvent.click(screen.getByTestId(parent));
      const links = Array.from(
        screen.getByRole('navigation').querySelectorAll('a'),
      ).map((a) => a.getAttribute('data-testid'));
      expect(links.slice(0, 2)).toEqual([guide, overview]);
    },
  );

  it('the config itself has 가이드 at index 0 and 개요 at index 1 of every drill parent', () => {
    const parents = GROUPS.flatMap((g) => g.items).filter(isParent);
    expect(parents).toHaveLength(6);
    for (const p of parents) {
      expect(p.children[0].label).toBe('가이드');
      expect(p.children[1].label).toBe('개요');
    }
  });
});

describe('sidebar icons (AC-2)', () => {
  it('every top-level item carries exactly one decorative icon', () => {
    render(<ConsoleSidebarNav />);
    const controls = screen
      .getByRole('navigation')
      .querySelectorAll('a,button');
    expect(controls.length).toBeGreaterThan(0);
    controls.forEach((c) => {
      const icons = c.querySelectorAll('svg[data-nav-icon]');
      expect(icons).toHaveLength(1);
      expect(icons[0]).toHaveAttribute('aria-hidden', 'true');
    });
  });

  it('every drill child carries an icon, and the accessible name stays the label', () => {
    render(<ConsoleSidebarNav />);
    fireEvent.click(screen.getByTestId('nav-wms'));
    const nav = screen.getByRole('navigation');
    nav.querySelectorAll('a').forEach((a) => {
      expect(a.querySelector('svg[data-nav-icon]')).not.toBeNull();
    });
    expect(screen.getByRole('link', { name: '가이드' })).toHaveAttribute(
      'href',
      '/wms/guide',
    );
  });

  it('group labels stay non-heading <p> elements (accessibility tree unchanged) and are visually smaller than items', () => {
    render(<ConsoleSidebarNav />);
    const group = screen.getByTestId('nav-group-customer-identity');
    expect(group.tagName).toBe('P');
    expect(group.className).toContain('text-[11px]');
    expect(group.className).not.toContain('uppercase');
  });
});

describe('collapsed drill still marks the current parent (AC-3)', () => {
  it('on /wms/outbound, collapsing the drill leaves WMS marked active; other parents are not', () => {
    mockPath = '/wms/outbound';
    render(<ConsoleSidebarNav />);
    // auto-drilled; collapse manually via the pinned parent
    fireEvent.click(screen.getByTestId('nav-wms'));
    const wms = screen.getByTestId('nav-wms');
    expect(wms).toHaveAttribute('aria-expanded', 'false');
    expect(wms).toHaveAttribute('aria-current', 'true');
    expect(wms).toHaveAttribute('data-active', 'true');
    expect(wms.className).toContain('bg-accent');
    expect(screen.getByTestId('nav-scm')).not.toHaveAttribute('aria-current');
    expect(screen.getByTestId('nav-scm')).not.toHaveAttribute('data-active');
  });

  it('on a non-parent route, no parent is marked active', () => {
    mockPath = '/console';
    render(<ConsoleSidebarNav />);
    for (const [parent] of DOMAIN_ORDER) {
      expect(screen.getByTestId(parent)).not.toHaveAttribute('aria-current');
    }
  });
});

describe('/dashboards/health matches 개요 (AC-4)', () => {
  it('navPathFor aliases the health page (and only it) to the overview', () => {
    expect(navPathFor('/dashboards/health')).toBe('/dashboards/overview');
    expect(navPathFor('/dashboards/overview')).toBe('/dashboards/overview');
    expect(navPathFor('/dashboards')).toBe('/dashboards');
    expect(navPathFor('/account')).toBe('/account');
  });

  it('on /dashboards/health the 개요 item is aria-current=page', () => {
    mockPath = '/dashboards/health';
    render(<ConsoleSidebarNav />);
    expect(screen.getByTestId('nav-dashboards')).toHaveAttribute(
      'aria-current',
      'page',
    );
  });

  it('/account is intentionally unmatched (reached from the account menu, see TASK-PC-FE-297 record)', () => {
    mockPath = '/account';
    render(<ConsoleSidebarNav />);
    expect(
      screen.getByRole('navigation').querySelector('[aria-current]'),
    ).toBeNull();
  });
});
