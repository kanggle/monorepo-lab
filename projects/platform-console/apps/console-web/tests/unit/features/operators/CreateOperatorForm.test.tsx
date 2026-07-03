import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { CreateOperatorForm } from '@/features/operators/components/CreateOperatorForm';
import { KNOWN_OPERATOR_ROLES } from '@/features/operators/api/types';

/**
 * `CreateOperatorForm` — role-checkbox grantable-roles pre-filter
 * (feat/iam-grantable-roles-filter).
 *
 *  - `grantableRoles` a subset ⇒ ONLY that subset's checkboxes render (e.g.
 *    `SUPER_ADMIN` hidden for a non-platform caller whose grantable set
 *    excludes it).
 *  - `grantableRoles={null}` (absent / fetch failed) ⇒ the FULL
 *    `KNOWN_OPERATOR_ROLES` set renders (fallback — never an empty list).
 */

const NOOP = () => undefined;

describe('CreateOperatorForm — grantable-roles pre-filter', () => {
  it('renders only the grantable subset (SUPER_ADMIN hidden for a non-platform caller)', () => {
    render(
      <CreateOperatorForm
        tenantOptions={['wms']}
        isPlatformOperator={false}
        onSubmitDraft={NOOP}
        grantableRoles={['TENANT_ADMIN', 'SUPPORT_LOCK']}
      />,
    );

    expect(
      screen.getByTestId('create-operator-role-TENANT_ADMIN'),
    ).toBeInTheDocument();
    expect(
      screen.getByTestId('create-operator-role-SUPPORT_LOCK'),
    ).toBeInTheDocument();
    expect(
      screen.queryByTestId('create-operator-role-SUPER_ADMIN'),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByTestId('create-operator-role-TENANT_BILLING_ADMIN'),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByTestId('create-operator-role-SUPPORT_READONLY'),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByTestId('create-operator-role-SECURITY_ANALYST'),
    ).not.toBeInTheDocument();
  });

  it('renders every KNOWN_OPERATOR_ROLES checkbox when grantableRoles is null (fallback)', () => {
    render(
      <CreateOperatorForm
        tenantOptions={['wms']}
        isPlatformOperator
        onSubmitDraft={NOOP}
        grantableRoles={null}
      />,
    );

    for (const role of KNOWN_OPERATOR_ROLES) {
      expect(
        screen.getByTestId(`create-operator-role-${role}`),
      ).toBeInTheDocument();
    }
  });

  it('renders every KNOWN_OPERATOR_ROLES checkbox when grantableRoles is omitted (default fallback)', () => {
    render(
      <CreateOperatorForm
        tenantOptions={['wms']}
        isPlatformOperator
        onSubmitDraft={NOOP}
      />,
    );

    for (const role of KNOWN_OPERATOR_ROLES) {
      expect(
        screen.getByTestId(`create-operator-role-${role}`),
      ).toBeInTheDocument();
    }
  });

  it('renders an empty role group (no crash) when grantableRoles is an empty array', () => {
    render(
      <CreateOperatorForm
        tenantOptions={['wms']}
        isPlatformOperator={false}
        onSubmitDraft={NOOP}
        grantableRoles={[]}
      />,
    );

    for (const role of KNOWN_OPERATOR_ROLES) {
      expect(
        screen.queryByTestId(`create-operator-role-${role}`),
      ).not.toBeInTheDocument();
    }
    expect(screen.getByTestId('create-operator-form')).toBeInTheDocument();
  });
});
