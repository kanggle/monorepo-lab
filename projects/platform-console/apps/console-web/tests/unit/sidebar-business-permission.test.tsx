import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, cleanup } from '@testing-library/react';
import { ConsoleSidebarNav } from '@/shared/ui/ConsoleSidebarNav';

/**
 * TASK-PC-FE-338 c-2 AC-1 — the sidebar renders the per-menu «업무 권한 없음» badge next to
 * (never instead of) the link, and keeps it apart from «구독 필요». Contract § 2.4.3.4.
 */
let mockPath = '/dashboards/overview';
vi.mock('next/navigation', () => ({
  usePathname: () => mockPath,
}));

beforeEach(() => {
  cleanup();
  mockPath = '/dashboards/overview';
});

describe('AC-1 — inventory-set operator, WMS-only tenant', () => {
  function renderInventorySet() {
    render(
      <ConsoleSidebarNav
        subscribedDomains={new Set(['wms'])}
        myBusinessRoles={new Set(['INVENTORY_READ', 'INVENTORY_WRITE'])}
      />,
    );
    fireEvent.click(screen.getByTestId('nav-wms'));
  }

  it('출고 carries «업무 권한 없음» and stays a link', () => {
    renderInventorySet();
    const outbound = screen.getByTestId('nav-wms-outbound');
    expect(outbound).toHaveAttribute('href', '/wms/outbound');
    expect(screen.getByTestId('nav-wms-outbound-business-permission-badge')).toHaveTextContent(
      '업무 권한 없음',
    );
  });

  it('재고 has no badge of either kind', () => {
    renderInventorySet();
    expect(screen.getByTestId('nav-wms-inventory')).toHaveTextContent(/^재고$/);
    expect(screen.queryByTestId('nav-wms-inventory-business-permission-badge')).toBeNull();
    expect(screen.queryByTestId('nav-wms-inventory-subscription-badge')).toBeNull();
  });

  it('the WMS parent header carries neither badge', () => {
    renderInventorySet();
    expect(screen.queryByTestId('nav-wms-business-permission-badge')).toBeNull();
    expect(screen.queryByTestId('nav-wms-subscription-badge')).toBeNull();
  });
});

describe('two signals kept apart', () => {
  it('SCM unsubscribed → «구독 필요» on the parent, never «업무 권한 없음»', () => {
    render(
      <ConsoleSidebarNav subscribedDomains={new Set(['wms'])} myBusinessRoles={new Set()} />,
    );
    expect(screen.getByTestId('nav-scm-subscription-badge')).toHaveTextContent('구독 필요');
    expect(screen.queryByTestId('nav-scm-business-permission-badge')).toBeNull();
  });

  it('ecommerce subscribed without ECOMMERCE_OPERATOR → the parent shows «업무 권한 없음», not «구독 필요»', () => {
    render(
      <ConsoleSidebarNav subscribedDomains={new Set(['ecommerce'])} myBusinessRoles={new Set()} />,
    );
    expect(screen.getByTestId('nav-ecommerce-business-permission-badge')).toHaveTextContent(
      '업무 권한 없음',
    );
    expect(screen.queryByTestId('nav-ecommerce-subscription-badge')).toBeNull();
  });

  it('ERP never gets «업무 권한 없음» — the e2e pin /^ERP(구독 필요)?$/ stays true', () => {
    render(<ConsoleSidebarNav subscribedDomains={new Set(['erp'])} myBusinessRoles={new Set()} />);
    expect(screen.getByTestId('nav-erp')).toHaveTextContent(/^ERP(구독 필요)?$/);
  });
});

describe('TASK-PC-FE-342 — SCM per menu', () => {
  it('scm subscribed, no SCM role → the SCM parent shows «업무 권한 없음», not «구독 필요»', () => {
    render(<ConsoleSidebarNav subscribedDomains={new Set(['scm'])} myBusinessRoles={new Set()} />);
    expect(screen.queryByTestId('nav-scm-business-permission-badge')).not.toBeNull();
    expect(screen.getByTestId('nav-scm-business-permission-badge')).toHaveTextContent(
      '업무 권한 없음',
    );
    expect(screen.queryByTestId('nav-scm-subscription-badge')).toBeNull();
  });

  it('demand-planning set only → 조달 badged and still a link, 보충 계획 unbadged', () => {
    render(
      <ConsoleSidebarNav
        subscribedDomains={new Set(['scm'])}
        myBusinessRoles={new Set(['SCM_DEMAND_PLANNING_READ', 'SCM_DEMAND_PLANNING_WRITE'])}
      />,
    );
    fireEvent.click(screen.getByTestId('nav-scm'));
    expect(screen.getByTestId('nav-scm-procurement')).toHaveAttribute('href', '/scm/procurement');
    expect(screen.queryByTestId('nav-scm-procurement-business-permission-badge')).not.toBeNull();
    expect(screen.getByTestId('nav-scm-procurement-business-permission-badge')).toHaveTextContent(
      '업무 권한 없음',
    );
    expect(screen.queryByTestId('nav-scm-replenishment-business-permission-badge')).toBeNull();
    expect(screen.queryByTestId('nav-scm-business-permission-badge')).toBeNull();
  });
});

describe('fail-open', () => {
  it('myBusinessRoles omitted (row 12 failed) → no «업무 권한 없음» anywhere', () => {
    render(<ConsoleSidebarNav subscribedDomains={new Set(['wms', 'ecommerce'])} />);
    expect(screen.queryByTestId('nav-ecommerce-business-permission-badge')).toBeNull();
    fireEvent.click(screen.getByTestId('nav-wms'));
    expect(screen.queryByTestId('nav-wms-outbound-business-permission-badge')).toBeNull();
  });
});
