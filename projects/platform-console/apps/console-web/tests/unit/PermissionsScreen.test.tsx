import { describe, it, expect, afterEach } from 'vitest';
import { render, screen, cleanup, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { PermissionsScreen } from '@/features/permissions';
import type { Role } from '@/shared/api/rbac-catalog';

/**
 * TASK-PC-FE-227 「권한」 — role 목록 + permission-key 카탈로그 + role→
 * permission drill-down. v1 read-only: no edit affordance is asserted by
 * absence (no button with an edit/save/delete label anywhere in the tree).
 */

afterEach(() => cleanup());

const ROLES: Role[] = [
  {
    id: 1,
    name: 'SUPER_ADMIN',
    description: 'Full platform administrator',
    permissions: ['account.read', 'operator.manage'],
  },
  {
    id: 2,
    name: 'SUPPORT_READONLY',
    description: 'Read-only support',
    permissions: [],
  },
];

const PERMISSIONS = ['account.read', 'audit.read', 'operator.manage'];

describe('PermissionsScreen', () => {
  it('renders the permission-key catalog and the role list from the BE-486 API shape', () => {
    render(
      <PermissionsScreen
        roles={ROLES}
        permissions={PERMISSIONS}
        scope="global"
      />,
    );

    const catalog = screen.getByTestId('permissions-catalog-list');
    for (const key of PERMISSIONS) {
      expect(within(catalog).getByText(key)).toBeInTheDocument();
    }

    expect(
      screen.getByTestId('permissions-role-SUPER_ADMIN'),
    ).toBeInTheDocument();
    expect(
      screen.getByTestId('permissions-role-SUPPORT_READONLY'),
    ).toBeInTheDocument();
  });

  it('echoes the producer scope verbatim (never hard-assumes global)', () => {
    render(
      <PermissionsScreen roles={ROLES} permissions={PERMISSIONS} scope="tenant" />,
    );
    expect(screen.getByTestId('permissions-scope-note')).toHaveTextContent(
      'tenant',
    );
  });

  it('labels a "global" scope with the tenant-independence note', () => {
    render(
      <PermissionsScreen roles={ROLES} permissions={PERMISSIONS} scope="global" />,
    );
    expect(screen.getByTestId('permissions-scope-note')).toHaveTextContent(
      '전역',
    );
  });

  it('role→permission drill-down: clicking a role reveals its permission keys', async () => {
    const user = userEvent.setup();
    render(
      <PermissionsScreen
        roles={ROLES}
        permissions={PERMISSIONS}
        scope="global"
      />,
    );

    // Collapsed by default — native <details> keeps its content in the DOM
    // regardless of open state (jsdom does not visually hide it), so the
    // meaningful "collapsed" signal is the absence of the `open` attribute.
    const details = screen.getByTestId('permissions-role-SUPER_ADMIN');
    expect(details).not.toHaveAttribute('open');

    await user.click(within(details).getByText('SUPER_ADMIN'));

    expect(details).toHaveAttribute('open');
    const keys = screen.getByTestId('permissions-role-SUPER_ADMIN-keys');
    expect(within(keys).getByText('account.read')).toBeInTheDocument();
    expect(within(keys).getByText('operator.manage')).toBeInTheDocument();
  });

  it('renders an explicit empty-permission notice for a role with 0 permissions', async () => {
    const user = userEvent.setup();
    render(
      <PermissionsScreen
        roles={ROLES}
        permissions={PERMISSIONS}
        scope="global"
      />,
    );

    const details = screen.getByTestId('permissions-role-SUPPORT_READONLY');
    await user.click(within(details).getByText('SUPPORT_READONLY'));

    expect(
      screen.getByTestId('permissions-role-SUPPORT_READONLY-empty'),
    ).toBeInTheDocument();
  });

  it('renders an empty-roles state when the catalog has no roles', () => {
    render(<PermissionsScreen roles={[]} permissions={[]} scope="global" />);
    expect(screen.getByTestId('permissions-roles-empty')).toBeInTheDocument();
    expect(
      screen.getByTestId('permissions-catalog-empty'),
    ).toBeInTheDocument();
  });

  it('v1 is read-only — renders no edit/create/delete affordance', () => {
    render(
      <PermissionsScreen
        roles={ROLES}
        permissions={PERMISSIONS}
        scope="global"
      />,
    );
    expect(screen.queryAllByRole('button')).toHaveLength(0);
  });
});
