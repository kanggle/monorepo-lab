import { describe, it, expect, vi } from 'vitest';
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
  // TASK-MONO-334: submit now also requires the account-existence gate. These
  // tests target the PASSWORD logic, so they inject an "exists" probe and await
  // the OIDC-ok note before asserting the button state.
  const EXISTS = () => Promise.resolve(true);

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

  it('submit is enabled with NO password once the account exists; draft omits password (OIDC-only)', async () => {
    const drafts: CreateOperatorInput[] = [];
    render(
      <CreateOperatorForm
        tenantOptions={['wms']}
        isPlatformOperator={false}
        onSubmitDraft={(d) => drafts.push(d)}
        grantableRoles={['SUPPORT_LOCK']}
        checkAccountExists={EXISTS}
      />,
    );
    fillRequired();
    await screen.findByTestId('create-operator-account-ok');

    const submit = screen.getByTestId('create-operator-submit');
    expect(submit).not.toBeDisabled();

    fireEvent.click(submit);
    expect(drafts).toHaveLength(1);
    expect(drafts[0]).not.toHaveProperty('password');
  });

  it('a NON-blank password must still satisfy the policy (submit blocked on a weak one)', async () => {
    const drafts: CreateOperatorInput[] = [];
    render(
      <CreateOperatorForm
        tenantOptions={['wms']}
        isPlatformOperator={false}
        onSubmitDraft={(d) => drafts.push(d)}
        grantableRoles={['SUPPORT_LOCK']}
        checkAccountExists={EXISTS}
      />,
    );
    fillRequired();
    await screen.findByTestId('create-operator-account-ok');
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

describe('CreateOperatorForm — account-existence pre-gate (TASK-MONO-334)', () => {
  // Fill every OTHER required field so the ONLY thing standing between the form
  // and a valid submit is the account-existence gate.
  function fillAllExceptGate(email = 'ghost@example.com', tenant = 'wms') {
    fireEvent.change(screen.getByTestId('create-operator-email'), {
      target: { value: email },
    });
    fireEvent.change(screen.getByTestId('create-operator-displayName'), {
      target: { value: 'Op Name' },
    });
    fireEvent.change(screen.getByTestId('create-operator-tenant'), {
      target: { value: tenant },
    });
    fireEvent.click(screen.getByTestId('create-operator-role-SUPPORT_LOCK'));
  }

  it('email absent in tenant ⇒ BLOCKING error, submit disabled even with all fields valid', async () => {
    const check = vi.fn().mockResolvedValue(false); // definitively absent
    render(
      <CreateOperatorForm
        tenantOptions={['wms']}
        isPlatformOperator={false}
        onSubmitDraft={NOOP}
        grantableRoles={['SUPPORT_LOCK']}
        checkAccountExists={check}
      />,
    );
    fillAllExceptGate();

    await screen.findByTestId('create-operator-account-error');
    expect(check).toHaveBeenCalledWith('ghost@example.com', 'wms', expect.anything());
    // The gate BLOCKS: the button stays disabled although name/tenant/role are set.
    expect(screen.getByTestId('create-operator-submit')).toBeDisabled();
  });

  it('email absent + break-glass password present ⇒ STILL blocked (break-glass no longer bypasses)', async () => {
    const check = vi.fn().mockResolvedValue(false);
    render(
      <CreateOperatorForm
        tenantOptions={['wms']}
        isPlatformOperator={false}
        onSubmitDraft={NOOP}
        grantableRoles={['SUPPORT_LOCK']}
        checkAccountExists={check}
      />,
    );
    fillAllExceptGate();
    fireEvent.change(screen.getByTestId('create-operator-password'), {
      target: { value: 'Str0ng!pass9' },
    });

    await screen.findByTestId('create-operator-account-error');
    expect(
      screen.queryByTestId('create-operator-account-ok'),
    ).not.toBeInTheDocument();
    expect(screen.getByTestId('create-operator-submit')).toBeDisabled();
  });

  it('email exists in tenant ⇒ OIDC-ok note and submit enabled', async () => {
    const check = vi.fn().mockResolvedValue(true);
    render(
      <CreateOperatorForm
        tenantOptions={['wms']}
        isPlatformOperator={false}
        onSubmitDraft={NOOP}
        grantableRoles={['SUPPORT_LOCK']}
        checkAccountExists={check}
      />,
    );
    fillAllExceptGate('real@example.com');

    await screen.findByTestId('create-operator-account-ok');
    expect(
      screen.queryByTestId('create-operator-account-error'),
    ).not.toBeInTheDocument();
    expect(screen.getByTestId('create-operator-submit')).not.toBeDisabled();
  });

  it('lookup unavailable (null) ⇒ unavailable notice, submit disabled (fail-closed)', async () => {
    const check = vi.fn().mockResolvedValue(null); // unknown
    render(
      <CreateOperatorForm
        tenantOptions={['wms']}
        isPlatformOperator={false}
        onSubmitDraft={NOOP}
        grantableRoles={['SUPPORT_LOCK']}
        checkAccountExists={check}
      />,
    );
    fillAllExceptGate();

    await screen.findByTestId('create-operator-account-unavailable');
    expect(
      screen.queryByTestId('create-operator-account-ok'),
    ).not.toBeInTheDocument();
    // Fail-closed: cannot confirm the account exists → submit stays disabled.
    expect(screen.getByTestId('create-operator-submit')).toBeDisabled();
  });

  it('platform-scope tenant (*) ⇒ probe skipped and submit enabled (exempt from the gate)', async () => {
    const check = vi.fn().mockResolvedValue(false);
    render(
      <CreateOperatorForm
        tenantOptions={['wms']}
        isPlatformOperator
        onSubmitDraft={NOOP}
        grantableRoles={['SUPPORT_LOCK']}
        checkAccountExists={check}
      />,
    );
    fillAllExceptGate('admin@example.com', '*');

    // Give the debounce window a chance to (not) fire, then assert no probe and
    // that the '*' bootstrap path is submittable without an account check.
    await new Promise((r) => setTimeout(r, 500));
    expect(check).not.toHaveBeenCalled();
    expect(
      screen.queryByTestId('create-operator-account-error'),
    ).not.toBeInTheDocument();
    expect(screen.getByTestId('create-operator-submit')).not.toBeDisabled();
  });
});
