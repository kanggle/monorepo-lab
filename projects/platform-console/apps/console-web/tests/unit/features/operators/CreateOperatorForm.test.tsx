import { describe, it, expect } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { CreateOperatorForm } from '@/features/operators/components/CreateOperatorForm';
import {
  KNOWN_OPERATOR_ROLES,
  type CreateOperatorInput,
} from '@/features/operators/api/types';

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

describe('CreateOperatorForm — optional break-glass password (ADR-MONO-035 O2)', () => {
  function fillRequired() {
    fireEvent.change(screen.getByTestId('create-operator-email'), {
      target: { value: 'foo@example.com' },
    });
    fireEvent.change(screen.getByTestId('create-operator-displayName'), {
      target: { value: 'Foo' },
    });
    fireEvent.change(screen.getByTestId('create-operator-tenant'), {
      target: { value: 'wms' },
    });
    fireEvent.click(screen.getByTestId('create-operator-role-SUPPORT_LOCK'));
  }

  it('submit is enabled with NO password; the draft omits password (OIDC-only)', () => {
    const drafts: CreateOperatorInput[] = [];
    render(
      <CreateOperatorForm
        tenantOptions={['wms']}
        isPlatformOperator={false}
        onSubmitDraft={(d) => drafts.push(d)}
        grantableRoles={['SUPPORT_LOCK']}
      />,
    );
    fillRequired();

    const submit = screen.getByTestId('create-operator-submit');
    expect(submit).not.toBeDisabled();

    fireEvent.click(submit);
    expect(drafts).toHaveLength(1);
    expect(drafts[0]).not.toHaveProperty('password');
  });

  it('a NON-blank password must still satisfy the policy (submit blocked on a weak one)', () => {
    const drafts: CreateOperatorInput[] = [];
    render(
      <CreateOperatorForm
        tenantOptions={['wms']}
        isPlatformOperator={false}
        onSubmitDraft={(d) => drafts.push(d)}
        grantableRoles={['SUPPORT_LOCK']}
      />,
    );
    fillRequired();
    fireEvent.change(screen.getByTestId('create-operator-password'), {
      target: { value: 'short' }, // < 10 chars, no digit/special → policy fail
    });

    expect(screen.getByTestId('create-operator-submit')).toBeDisabled();
    expect(
      screen.getByTestId('create-operator-password-error'),
    ).toBeInTheDocument();

    // A policy-valid break-glass password re-enables submit and is carried.
    fireEvent.change(screen.getByTestId('create-operator-password'), {
      target: { value: 'Str0ng!pass9' },
    });
    expect(screen.getByTestId('create-operator-submit')).not.toBeDisabled();
    fireEvent.click(screen.getByTestId('create-operator-submit'));
    expect(drafts).toHaveLength(1);
    expect(drafts[0].password).toBe('Str0ng!pass9');
  });
});
