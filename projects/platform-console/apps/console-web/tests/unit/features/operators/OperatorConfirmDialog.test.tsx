import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { OperatorConfirmDialog } from '@/features/operators/components/OperatorConfirmDialog';
import { KNOWN_OPERATOR_ROLES } from '@/features/operators/api/types';

/**
 * `OperatorConfirmDialog` — edit-roles role-checkbox grantable-roles
 * pre-filter (feat/iam-grantable-roles-filter).
 *
 *  - `grantableRoles` a subset ⇒ ONLY that subset renders, EXCEPT a role the
 *    operator already holds (`roleEditor.initialRoles`) stays visible even
 *    when it falls outside the caller's grantable set (no silent drop of an
 *    already-granted role).
 *  - `grantableRoles={null}` ⇒ the FULL `KNOWN_OPERATOR_ROLES` set renders
 *    (fallback).
 */

const NOOP = () => undefined;

describe('OperatorConfirmDialog — edit-roles grantable-roles pre-filter', () => {
  it('renders only the grantable subset for a fresh edit (no already-held role outside the set)', () => {
    render(
      <OperatorConfirmDialog
        open
        title="역할 변경"
        description="desc"
        confirmLabel="확인"
        roleEditor={{ initialRoles: ['SUPPORT_LOCK'] }}
        grantableRoles={['TENANT_ADMIN', 'SUPPORT_LOCK']}
        onConfirm={NOOP}
        onCancel={NOOP}
      />,
    );

    expect(screen.getByTestId('edit-roles-TENANT_ADMIN')).toBeInTheDocument();
    expect(screen.getByTestId('edit-roles-SUPPORT_LOCK')).toBeInTheDocument();
    expect(
      screen.queryByTestId('edit-roles-SUPER_ADMIN'),
    ).not.toBeInTheDocument();
  });

  it('keeps an already-held role visible even when outside the grantable set (no silent drop)', () => {
    render(
      <OperatorConfirmDialog
        open
        title="역할 변경"
        description="desc"
        confirmLabel="확인"
        // The target operator already holds SUPER_ADMIN, but the calling
        // (non-platform) operator's grantable set excludes it.
        roleEditor={{ initialRoles: ['SUPER_ADMIN'] }}
        grantableRoles={['TENANT_ADMIN']}
        onConfirm={NOOP}
        onCancel={NOOP}
      />,
    );

    expect(screen.getByTestId('edit-roles-SUPER_ADMIN')).toBeInTheDocument();
    expect(screen.getByTestId('edit-roles-SUPER_ADMIN')).toBeChecked();
    expect(screen.getByTestId('edit-roles-TENANT_ADMIN')).toBeInTheDocument();
    expect(
      screen.queryByTestId('edit-roles-SUPPORT_LOCK'),
    ).not.toBeInTheDocument();
  });

  it('renders every KNOWN_OPERATOR_ROLES checkbox when grantableRoles is null (fallback)', () => {
    render(
      <OperatorConfirmDialog
        open
        title="역할 변경"
        description="desc"
        confirmLabel="확인"
        roleEditor={{ initialRoles: [] }}
        grantableRoles={null}
        onConfirm={NOOP}
        onCancel={NOOP}
      />,
    );

    for (const role of KNOWN_OPERATOR_ROLES) {
      expect(screen.getByTestId(`edit-roles-${role}`)).toBeInTheDocument();
    }
  });
});
