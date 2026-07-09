import { describe, it, expect, afterEach } from 'vitest';
import { render, screen, cleanup, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { PermissionSetsScreen } from '@/features/permission-sets';
import type { Role } from '@/shared/api/rbac-catalog';

/**
 * TASK-PC-FE-228 「권한 세트」 — the SAME `admin_roles` rows reframed as
 * "permission sets" (permission_set_id physically = admin_roles.id). Usage
 * count is intentionally OMITTED per BE-486 response (renders "—", no
 * frontend N+1 aggregation). The permission_set_id NULL (operator-level
 * role inheritance) case gets an explicit legend note.
 */

afterEach(() => cleanup());

const PERMISSION_SETS: Role[] = [
  {
    id: 7,
    name: 'TENANT_ADMIN',
    description: 'Tenant-scoped delegated admin',
    permissions: ['operator.manage', 'tenant.admin.delegate'],
  },
  {
    id: 8,
    name: 'TENANT_BILLING_ADMIN',
    description: 'Tenant-scoped billing admin',
    permissions: [],
  },
];

describe('PermissionSetsScreen', () => {
  it('renders each permission set (=role) from the reused BE-486 roles response', () => {
    render(
      <PermissionSetsScreen permissionSets={PERMISSION_SETS} scope="global" />,
    );
    expect(
      screen.getByTestId('permission-set-TENANT_ADMIN'),
    ).toBeInTheDocument();
    expect(
      screen.getByTestId('permission-set-TENANT_BILLING_ADMIN'),
    ).toBeInTheDocument();
  });

  it('permission_set_id NULL (operator-level inheritance) is explicitly noted', () => {
    render(
      <PermissionSetsScreen permissionSets={PERMISSION_SETS} scope="global" />,
    );
    const notice = screen.getByTestId('permission-sets-null-notice');
    expect(within(notice).getByText(/permission_set_id = NULL/)).toBeInTheDocument();
    // "상속" appears in more than one node inside the notice (the emphasised
    // <strong> + the "(operator-level 상속)" tail), so match all of them.
    expect(within(notice).getAllByText(/상속/).length).toBeGreaterThan(0);
  });

  it('usage count is omitted (renders "—"), never frontend-aggregated', () => {
    render(
      <PermissionSetsScreen permissionSets={PERMISSION_SETS} scope="global" />,
    );
    expect(
      screen.getByTestId('permission-set-TENANT_ADMIN-usage'),
    ).toHaveTextContent('—');
  });

  it('set→permission drill-down: clicking a set reveals its permission keys', async () => {
    const user = userEvent.setup();
    render(
      <PermissionSetsScreen permissionSets={PERMISSION_SETS} scope="global" />,
    );

    const details = screen.getByTestId('permission-set-TENANT_ADMIN');
    expect(details).not.toHaveAttribute('open');

    await user.click(within(details).getByText('TENANT_ADMIN'));

    expect(details).toHaveAttribute('open');
    const keys = screen.getByTestId('permission-set-TENANT_ADMIN-keys');
    expect(within(keys).getByText('operator.manage')).toBeInTheDocument();
    expect(
      within(keys).getByText('tenant.admin.delegate'),
    ).toBeInTheDocument();
  });

  it('renders an explicit empty-permission notice for a set with 0 permissions', async () => {
    const user = userEvent.setup();
    render(
      <PermissionSetsScreen permissionSets={PERMISSION_SETS} scope="global" />,
    );
    const details = screen.getByTestId(
      'permission-set-TENANT_BILLING_ADMIN',
    );
    await user.click(within(details).getByText('TENANT_BILLING_ADMIN'));
    expect(
      screen.getByTestId('permission-set-TENANT_BILLING_ADMIN-empty'),
    ).toBeInTheDocument();
  });

  it('renders an empty state when there are no permission sets', () => {
    render(<PermissionSetsScreen permissionSets={[]} scope="global" />);
    expect(screen.getByTestId('permission-sets-empty')).toBeInTheDocument();
  });

  it('echoes the producer scope verbatim (never hard-assumes global)', () => {
    render(
      <PermissionSetsScreen permissionSets={PERMISSION_SETS} scope="tenant" />,
    );
    expect(
      screen.getByTestId('permission-sets-scope-note'),
    ).toHaveTextContent('tenant');
  });

  it('v1 is read-only — renders no edit/create/delete affordance', () => {
    render(
      <PermissionSetsScreen permissionSets={PERMISSION_SETS} scope="global" />,
    );
    expect(screen.queryAllByRole('button')).toHaveLength(0);
  });
});
