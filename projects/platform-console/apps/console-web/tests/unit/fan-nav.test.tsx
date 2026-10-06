import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, cleanup } from '@testing-library/react';
import { ConsoleSidebarNav } from '@/shared/ui/ConsoleSidebarNav';
import { GROUPS, visibleGroups } from '@/shared/ui/console-nav-config';

/**
 * TASK-MONO-751 AC-3 — the fan directory nav entry is gated by the registry `fan` product
 * key. It renders only when the `(console)` layout passes `fan` among the products that have
 * a selectable tenant — which admin-service does for PLATFORM operators alone (ADR-MONO-079
 * rider R3). A customer operator (`demo@demo.com`, home `demo-corp`) gets `fan.tenants = []`,
 * so `fan` is not passed and the entry is absent.
 */
// TASK-PC-FE-310 — `/console` is no longer a nav leaf (folded into the
// overview); an arbitrary non-fan route is all this default needs to be.
let mockPath = '/dashboards/overview';
vi.mock('next/navigation', () => ({
  usePathname: () => mockPath,
}));

beforeEach(() => {
  cleanup();
  mockPath = '/dashboards/overview';
});

// What the layout passes for each operator kind (products with ≥1 selectable tenant).
const CUSTOMER_OPERATOR = ['iam', 'wms', 'scm', 'erp', 'finance', 'ecommerce'];
const PLATFORM_OPERATOR = [...CUSTOMER_OPERATOR, 'fan'];

describe('fan nav — registry-gated (AC-3 / R3)', () => {
  it('a customer operator (no `fan` product key) does NOT see the fan entry', () => {
    render(<ConsoleSidebarNav availableProductKeys={CUSTOMER_OPERATOR} />);
    expect(screen.queryByTestId('nav-fan')).toBeNull();
    // Control: the ungated domain entries are all still there.
    expect(screen.getByTestId('nav-ecommerce')).toBeInTheDocument();
    expect(screen.getByTestId('nav-wms')).toBeInTheDocument();
  });

  it('no registry answer (prop omitted) hides the gated entry and nothing else', () => {
    render(<ConsoleSidebarNav />);
    expect(screen.queryByTestId('nav-fan')).toBeNull();
    expect(screen.getByTestId('nav-iam')).toBeInTheDocument();
  });

  it('a platform operator (`fan` listed) sees 팬 디렉터리 with 소속사 · 아티스트 · 그룹', () => {
    render(<ConsoleSidebarNav availableProductKeys={PLATFORM_OPERATOR} />);
    fireEvent.click(screen.getByTestId('nav-fan'));
    expect(screen.getByTestId('nav-fan-agencies')).toHaveAttribute('href', '/fan/agencies');
    expect(screen.getByTestId('nav-fan-artists')).toHaveAttribute('href', '/fan/artists');
    expect(screen.getByTestId('nav-fan-groups')).toHaveAttribute('href', '/fan/groups');
  });

  it('a deep link into /fan/** does not open the hidden drill for a customer operator', () => {
    mockPath = '/fan/agencies';
    render(<ConsoleSidebarNav availableProductKeys={CUSTOMER_OPERATOR} />);
    expect(screen.queryByTestId('nav-fan-agencies')).toBeNull();
    expect(screen.queryByTestId('nav-fan')).toBeNull();
  });

  it('the active marker works on /fan/agencies for a platform operator', () => {
    mockPath = '/fan/agencies';
    render(<ConsoleSidebarNav availableProductKeys={PLATFORM_OPERATOR} />);
    expect(screen.getByTestId('nav-fan-agencies')).toHaveAttribute('aria-current', 'page');
  });

  it('visibleGroups() removes only the gated parent — every other node is kept verbatim', () => {
    const count = (gs: typeof GROUPS) => gs.reduce((n, g) => n + g.items.length, 0);
    expect(count(visibleGroups(GROUPS, PLATFORM_OPERATOR))).toBe(count(GROUPS));
    expect(count(visibleGroups(GROUPS, CUSTOMER_OPERATOR))).toBe(count(GROUPS) - 1);
  });
});
