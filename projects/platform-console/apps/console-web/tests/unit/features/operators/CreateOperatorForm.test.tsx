import { describe, it, expect, vi } from 'vitest';
import {
  render,
  screen,
  fireEvent,
  waitFor,
} from '@testing-library/react';
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

describe('CreateOperatorForm — dangling-account pre-flight (TASK-PC-FE-179)', () => {
  function fillEmailTenant(email = 'ghost@example.com', tenant = 'wms') {
    fireEvent.change(screen.getByTestId('create-operator-email'), {
      target: { value: email },
    });
    fireEvent.change(screen.getByTestId('create-operator-tenant'), {
      target: { value: tenant },
    });
  }

  it('email absent in tenant + NO password ⇒ dangling-operator warning (non-blocking)', async () => {
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
    fillEmailTenant();

    await screen.findByTestId('create-operator-account-warning');
    expect(check).toHaveBeenCalledWith('ghost@example.com', 'wms', expect.anything());
    // Non-blocking: displayName is required for submit, but the warning itself
    // must never disable the button. Fill name and assert submit is enabled.
    fireEvent.change(screen.getByTestId('create-operator-displayName'), {
      target: { value: 'Ghost' },
    });
    expect(screen.getByTestId('create-operator-submit')).not.toBeDisabled();
  });

  it('email absent + break-glass password present ⇒ softened note, no dangling warning', async () => {
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
    fillEmailTenant();
    fireEvent.change(screen.getByTestId('create-operator-password'), {
      target: { value: 'Str0ng!pass9' },
    });

    await screen.findByTestId('create-operator-account-breakglass-note');
    expect(
      screen.queryByTestId('create-operator-account-warning'),
    ).not.toBeInTheDocument();
  });

  it('email exists in tenant ⇒ OIDC-ok note, no warning', async () => {
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
    fillEmailTenant('real@example.com');

    await screen.findByTestId('create-operator-account-ok');
    expect(
      screen.queryByTestId('create-operator-account-warning'),
    ).not.toBeInTheDocument();
  });

  it('lookup unavailable (null) ⇒ no warning / ok / note (fail-soft)', async () => {
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
    fillEmailTenant();

    await waitFor(() => expect(check).toHaveBeenCalled());
    expect(
      screen.queryByTestId('create-operator-account-warning'),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByTestId('create-operator-account-ok'),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByTestId('create-operator-account-breakglass-note'),
    ).not.toBeInTheDocument();
  });

  it('platform-scope tenant (*) ⇒ probe skipped, no lookup call', async () => {
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
    fillEmailTenant('admin@example.com', '*');

    // Give the debounce window a chance to (not) fire, then assert no probe.
    await new Promise((r) => setTimeout(r, 500));
    expect(check).not.toHaveBeenCalled();
    expect(
      screen.queryByTestId('create-operator-account-warning'),
    ).not.toBeInTheDocument();
  });
});
