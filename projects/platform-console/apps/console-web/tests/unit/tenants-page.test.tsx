import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render } from '@testing-library/react';

/**
 * `/tenants` SSR gating waterfall (TASK-PC-FE-226) — mirrors the
 * `/operators` page test conventions (`operators-page-parallel.test.tsx`):
 * noTenant → permissionError → degraded → happy. Since `GET
 * /api/admin/tenants` itself requires SUPER_ADMIN, the `permissionError`
 * state is the ONE gate that also covers "may not mutate" (task AC).
 */

const getTenantsListState = vi.fn();
vi.mock('@/features/tenants', () => ({
  getTenantsListState: (a: unknown) => getTenantsListState(a),
  TenantsScreen: ({
    initial,
    orgNodeOptions,
  }: {
    initial: { items: unknown[] };
    orgNodeOptions?: { orgNodeId: string; name: string }[] | null;
  }) => (
    <div
      data-testid="tenants-screen"
      data-items={initial.items.length}
      data-org-node-options={
        orgNodeOptions === null || orgNodeOptions === undefined
          ? 'null'
          : orgNodeOptions.map((o) => `${o.orgNodeId}=${o.name}`).join(',')
      }
    />
  ),
}));
// TASK-PC-FE-312 — the success path also reads the org-node list for the
// create form's «소속 노드» select (fail-soft → null).
const listOrgNodes = vi.fn();
vi.mock('@/features/org-hierarchy', () => ({
  listOrgNodes: () => listOrgNodes(),
}));
vi.mock('next/link', () => ({
  default: ({ href, children }: { href: string; children: React.ReactNode }) => (
    <a href={href}>{children}</a>
  ),
}));

/**
 * TASK-PC-FE-301 — `/tenants` is SUPER_ADMIN-only, but a 0-selectable-tenant
 * operator cannot reach it regardless of role (the noTenant gate blocks
 * first) — so it renders the SAME shared `NoTenantNotice` as every other
 * gated screen. Defaults to 'select' so the pre-301 no-tenant test below
 * keeps its original assertion.
 */
let noTenantNoticeKindResult: 'zero' | 'select' = 'select';
vi.mock('@/shared/lib/active-tenant-default', () => ({
  noTenantNoticeKind: async () => noTenantNoticeKindResult,
}));

import TenantsPage from '@/app/(console)/tenants/page';

beforeEach(() => {
  getTenantsListState.mockReset();
  listOrgNodes.mockReset();
  listOrgNodes.mockResolvedValue({ items: [] });
  noTenantNoticeKindResult = 'select';
});

describe('TenantsPage — SSR gating', () => {
  it('renders the no-tenant gate', async () => {
    getTenantsListState.mockResolvedValue({
      page: null,
      degraded: false,
      noTenant: true,
      permissionError: null,
      query: {},
    });
    const ui = await TenantsPage();
    const { getByTestId } = render(ui);
    expect(getByTestId('tenants-no-tenant')).toBeInTheDocument();
  });

  it('🔴🔴 TASK-PC-FE-301 — 0 selectable tenants → the no-reachable-tenant notice, even though this screen is SUPER_ADMIN-only', async () => {
    getTenantsListState.mockResolvedValue({
      page: null,
      degraded: false,
      noTenant: true,
      permissionError: null,
      query: {},
    });
    noTenantNoticeKindResult = 'zero';
    const ui = await TenantsPage();
    const { getByTestId } = render(ui);
    const el = getByTestId('tenants-no-tenant');
    expect(el).toHaveTextContent('이 계정에는 접근 가능한 테넌트가 없습니다');
    expect(el).not.toHaveTextContent('테넌트를 먼저 선택하세요');
  });

  it('renders the permission-denied gate for a non-SUPER_ADMIN (covers both view + mutate)', async () => {
    getTenantsListState.mockResolvedValue({
      page: null,
      degraded: false,
      noTenant: false,
      permissionError: { code: 'TENANT_SCOPE_DENIED', message: 'no' },
      query: {},
    });
    const ui = await TenantsPage();
    const { getByTestId } = render(ui);
    expect(getByTestId('tenants-permission-denied')).toBeInTheDocument();
  });

  it('renders the degraded notice on a 503/timeout', async () => {
    getTenantsListState.mockResolvedValue({
      page: null,
      degraded: true,
      noTenant: false,
      permissionError: null,
      query: {},
    });
    const ui = await TenantsPage();
    const { getByTestId } = render(ui);
    expect(getByTestId('tenants-degraded')).toBeInTheDocument();
  });

  it('renders TenantsScreen with the seeded page on the success path', async () => {
    getTenantsListState.mockResolvedValue({
      page: { items: [{ tenantId: 'a' }], page: 0, size: 20, totalElements: 1, totalPages: 1 },
      degraded: false,
      noTenant: false,
      permissionError: null,
      query: {},
    });
    const ui = await TenantsPage();
    const { getByTestId } = render(ui);
    expect(getByTestId('tenants-screen')).toHaveAttribute('data-items', '1');
  });

  const HAPPY = {
    page: { items: [{ tenantId: 'a' }], page: 0, size: 20, totalElements: 1, totalPages: 1 },
    degraded: false,
    noTenant: false,
    permissionError: null,
    query: {},
  };

  it('TASK-PC-FE-312 — hands the org-node list to the create form as «소속 노드» options', async () => {
    getTenantsListState.mockResolvedValue(HAPPY);
    listOrgNodes.mockResolvedValue({
      items: [
        { orgNodeId: 'n-1', parentId: null, name: '본사', depth: 1 },
        { orgNodeId: 'n-2', parentId: 'n-1', name: '물류', depth: 2 },
      ],
    });
    const ui = await TenantsPage();
    const { getByTestId } = render(ui);
    expect(getByTestId('tenants-screen')).toHaveAttribute(
      'data-org-node-options',
      'n-1=본사,n-2=물류',
    );
  });

  it('TASK-PC-FE-312 — an org-node read failure never blocks the tenants screen (options = null)', async () => {
    getTenantsListState.mockResolvedValue(HAPPY);
    listOrgNodes.mockRejectedValue(new Error('org-nodes down'));
    const ui = await TenantsPage();
    const { getByTestId } = render(ui);
    expect(getByTestId('tenants-screen')).toHaveAttribute('data-items', '1');
    expect(getByTestId('tenants-screen')).toHaveAttribute('data-org-node-options', 'null');
  });

  it('TASK-PC-FE-312 — the gated states do not read the org-node list', async () => {
    getTenantsListState.mockResolvedValue({ ...HAPPY, page: null, degraded: true });
    await TenantsPage();
    expect(listOrgNodes).not.toHaveBeenCalled();
  });
});
