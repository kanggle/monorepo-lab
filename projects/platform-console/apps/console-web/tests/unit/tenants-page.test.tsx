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
  TenantsScreen: ({ initial }: { initial: { items: unknown[] } }) => (
    <div data-testid="tenants-screen" data-items={initial.items.length} />
  ),
}));
vi.mock('next/link', () => ({
  default: ({ href, children }: { href: string; children: React.ReactNode }) => (
    <a href={href}>{children}</a>
  ),
}));

import TenantsPage from '@/app/(console)/tenants/page';

beforeEach(() => {
  getTenantsListState.mockReset();
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
});
