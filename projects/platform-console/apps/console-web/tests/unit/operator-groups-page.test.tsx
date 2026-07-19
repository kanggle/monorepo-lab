import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render } from '@testing-library/react';

/**
 * `/operator-groups` SSR gating waterfall (TASK-PC-FE-250 / ADR-MONO-046) —
 * mirrors the `/tenants` + `/org-hierarchy` page test conventions: noTenant →
 * permissionError → degraded → happy (AC-5's happy/degraded/permission-error
 * render coverage). Since `GET /api/admin/groups` itself requires
 * `group.manage`, the `permissionError` state is the ONE gate that also covers
 * "may not mutate". The stub (`operator-groups-stub`) is gone.
 */

const getOperatorGroupsState = vi.fn();
vi.mock('@/features/operator-groups', () => ({
  getOperatorGroupsState: () => getOperatorGroupsState(),
  OperatorGroupsScreen: ({
    initial,
    grantableRoles,
  }: {
    initial: { items: unknown[] };
    grantableRoles: string[] | null;
  }) => (
    <div
      data-testid="operator-groups-screen"
      data-items={initial.items.length}
      data-grantable={grantableRoles === null ? 'null' : grantableRoles.join(',')}
    />
  ),
}));

const getGrantableRolesOrNull = vi.fn();
vi.mock('@/features/operators/api/operators-api', () => ({
  getGrantableRolesOrNull: () => getGrantableRolesOrNull(),
}));

vi.mock('next/link', () => ({
  default: ({ href, children }: { href: string; children: React.ReactNode }) => (
    <a href={href}>{children}</a>
  ),
}));

import OperatorGroupsPage from '@/app/(console)/operator-groups/page';

beforeEach(() => {
  getOperatorGroupsState.mockReset();
  getGrantableRolesOrNull.mockReset();
  getGrantableRolesOrNull.mockResolvedValue(null);
});

describe('OperatorGroupsPage — SSR gating', () => {
  it('renders the no-tenant gate', async () => {
    getOperatorGroupsState.mockResolvedValue({
      page: null,
      degraded: false,
      noTenant: true,
      permissionError: null,
      query: {},
    });
    const ui = await OperatorGroupsPage();
    const { getByTestId } = render(ui);
    expect(getByTestId('operator-groups-no-tenant')).toBeInTheDocument();
  });

  it('renders the permission-denied gate for a non-group.manage operator', async () => {
    getOperatorGroupsState.mockResolvedValue({
      page: null,
      degraded: false,
      noTenant: false,
      permissionError: { code: 'PERMISSION_DENIED', message: 'no' },
      query: {},
    });
    const ui = await OperatorGroupsPage();
    const { getByTestId } = render(ui);
    expect(getByTestId('operator-groups-permission-denied')).toBeInTheDocument();
  });

  it('renders the degraded notice on a 503/timeout', async () => {
    getOperatorGroupsState.mockResolvedValue({
      page: null,
      degraded: true,
      noTenant: false,
      permissionError: null,
      query: {},
    });
    const ui = await OperatorGroupsPage();
    const { getByTestId } = render(ui);
    expect(getByTestId('operator-groups-degraded')).toBeInTheDocument();
  });

  it('renders OperatorGroupsScreen with the seeded page + grantable roles on success', async () => {
    getOperatorGroupsState.mockResolvedValue({
      page: {
        items: [{ groupId: 'g1' }],
        page: 0,
        size: 20,
        totalElements: 1,
        totalPages: 1,
      },
      degraded: false,
      noTenant: false,
      permissionError: null,
      query: {},
    });
    getGrantableRolesOrNull.mockResolvedValue(['SUPPORT_LOCK']);
    const ui = await OperatorGroupsPage();
    const { getByTestId } = render(ui);
    const screen = getByTestId('operator-groups-screen');
    expect(screen).toHaveAttribute('data-items', '1');
    expect(screen).toHaveAttribute('data-grantable', 'SUPPORT_LOCK');
  });

  it('does not leave a stub marker on any path', async () => {
    getOperatorGroupsState.mockResolvedValue({
      page: null,
      degraded: true,
      noTenant: false,
      permissionError: null,
      query: {},
    });
    const ui = await OperatorGroupsPage();
    const { queryByTestId } = render(ui);
    expect(queryByTestId('operator-groups-stub')).not.toBeInTheDocument();
  });
});
