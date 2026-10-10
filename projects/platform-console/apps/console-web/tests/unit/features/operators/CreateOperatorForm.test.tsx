import { describe, it, expect } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { CreateOperatorForm } from '@/features/operators/components/CreateOperatorForm';
import {
  KNOWN_OPERATOR_ROLES,
  type CreateOperatorInput,
} from '@/features/operators/api/types';

/**
 * `CreateOperatorForm` — the PLATFORM-operator create form.
 *
 *  - grantable-roles pre-filter (feat/iam-grantable-roles-filter): a subset ⇒
 *    only that subset renders; `null`/omitted ⇒ every KNOWN role (fallback).
 *  - optional break-glass password (ADR-MONO-035 O2).
 *  - 🔴 TASK-MONO-772 S5 — the tenant is FIXED to `*` (§ 2.4.3 row 2:
 *    `POST /operators` creates platform-scope operators only; company
 *    operators are invited). There is no tenant picker and NO account-existence
 *    probe (the TASK-MONO-334 pre-gate is retired with the non-`*` create).
 */

const NOOP = () => undefined;

describe('CreateOperatorForm — grantable-roles pre-filter', () => {
  it('renders only the grantable subset', () => {
    render(
      <CreateOperatorForm
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
      screen.queryByTestId('create-operator-role-SECURITY_ANALYST'),
    ).not.toBeInTheDocument();
  });

  it('renders every KNOWN_OPERATOR_ROLES checkbox when grantableRoles is null (fallback)', () => {
    render(<CreateOperatorForm onSubmitDraft={NOOP} grantableRoles={null} />);
    for (const role of KNOWN_OPERATOR_ROLES) {
      expect(
        screen.getByTestId(`create-operator-role-${role}`),
      ).toBeInTheDocument();
    }
  });

  it('renders every KNOWN_OPERATOR_ROLES checkbox when grantableRoles is omitted (default fallback)', () => {
    render(<CreateOperatorForm onSubmitDraft={NOOP} />);
    for (const role of KNOWN_OPERATOR_ROLES) {
      expect(
        screen.getByTestId(`create-operator-role-${role}`),
      ).toBeInTheDocument();
    }
  });

  it('renders an empty role group (no crash) when grantableRoles is an empty array', () => {
    render(<CreateOperatorForm onSubmitDraft={NOOP} grantableRoles={[]} />);
    for (const role of KNOWN_OPERATOR_ROLES) {
      expect(
        screen.queryByTestId(`create-operator-role-${role}`),
      ).not.toBeInTheDocument();
    }
    expect(screen.getByTestId('create-operator-form')).toBeInTheDocument();
  });
});

describe('CreateOperatorForm — platform scope only (TASK-MONO-772 S5)', () => {
  it('shows the fixed `*` tenant and offers NO tenant picker', () => {
    render(<CreateOperatorForm onSubmitDraft={NOOP} />);
    expect(screen.getByTestId('create-operator-tenant')).toHaveTextContent('*');
    expect(screen.queryByRole('combobox')).not.toBeInTheDocument();
  });

  it('the draft always targets tenantId="*" — and there is no account probe to wait for', () => {
    const drafts: CreateOperatorInput[] = [];
    render(
      <CreateOperatorForm
        onSubmitDraft={(d) => drafts.push(d)}
        grantableRoles={['SUPER_ADMIN']}
      />,
    );
    fireEvent.change(screen.getByTestId('create-operator-email'), {
      target: { value: 'root@example.com' },
    });
    fireEvent.change(screen.getByTestId('create-operator-displayName'), {
      target: { value: 'Root' },
    });
    fireEvent.click(screen.getByTestId('create-operator-role-SUPER_ADMIN'));

    // Immediately submittable — the retired 334 pre-gate is not in the path.
    expect(screen.getByTestId('create-operator-submit')).not.toBeDisabled();
    expect(
      screen.queryByTestId('create-operator-account-error'),
    ).not.toBeInTheDocument();
    fireEvent.click(screen.getByTestId('create-operator-submit'));
    expect(drafts).toHaveLength(1);
    expect(drafts[0].tenantId).toBe('*');
    expect(drafts[0].roles).toEqual(['SUPER_ADMIN']);
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
    fireEvent.click(screen.getByTestId('create-operator-role-SUPPORT_LOCK'));
  }

  it('submit is enabled with NO password; draft omits password (OIDC-only)', () => {
    const drafts: CreateOperatorInput[] = [];
    render(
      <CreateOperatorForm
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
        onSubmitDraft={(d) => drafts.push(d)}
        grantableRoles={['SUPPORT_LOCK']}
      />,
    );
    fillRequired();
    fireEvent.change(screen.getByTestId('create-operator-password'), {
      target: { value: 'short' },
    });

    expect(screen.getByTestId('create-operator-submit')).toBeDisabled();
    expect(
      screen.getByTestId('create-operator-password-error'),
    ).toBeInTheDocument();

    fireEvent.change(screen.getByTestId('create-operator-password'), {
      target: { value: 'Str0ng!pass9' },
    });
    expect(screen.getByTestId('create-operator-submit')).not.toBeDisabled();
    fireEvent.click(screen.getByTestId('create-operator-submit'));
    expect(drafts).toHaveLength(1);
    expect(drafts[0].password).toBe('Str0ng!pass9');
  });
});
