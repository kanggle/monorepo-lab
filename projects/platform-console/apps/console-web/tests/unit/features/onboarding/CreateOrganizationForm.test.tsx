import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { CreateOrganizationForm } from '@/features/onboarding';

/**
 * `CreateOrganizationForm` — pre-operator self-service org creation
 * (TASK-PC-FE-182 / ADR-MONO-044 §3.4). `submit` + `navigate` are injected so
 * the component is tested without the real fetch / hard navigation.
 *
 *  - client validation gates the submit button (slug + name);
 *  - 201 ready:true → navigate('/') (walk into the console);
 *  - 201 ready:false → navigate('/login?onboarded=1');
 *  - producer error → inline actionable (messageForCode), no navigation;
 *  - double-submit guarded (pending disables the button).
 */

function jsonResponse(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

beforeEach(() => {
  vi.clearAllMocks();
});

describe('CreateOrganizationForm — what-happens orientation panel (PC-FE-206)', () => {
  it('renders the what-happens panel (tenant+admin, confinement, zero-subscription, next steps)', () => {
    render(<CreateOrganizationForm submit={vi.fn()} navigate={vi.fn()} />);
    const panel = screen.getByTestId('onboarding-what-happens');
    expect(panel).toBeInTheDocument();
    // Grants a tenant-scoped admin, confined to the new org, born with zero
    // domain subscriptions, and points at the next step (구독 켜기).
    expect(panel).toHaveTextContent('관리자(TENANT_ADMIN · 구독 관리자)');
    expect(panel).toHaveTextContent('방금 만든 조직에만');
    expect(panel).toHaveTextContent('도메인 구독 0');
    expect(panel).toHaveTextContent('다음 단계');
  });
});

describe('CreateOrganizationForm — validation gate', () => {
  it('disables submit until both the slug and the name are valid', async () => {
    const user = userEvent.setup();
    render(<CreateOrganizationForm submit={vi.fn()} navigate={vi.fn()} />);

    const submit = screen.getByTestId('onboarding-submit');
    expect(submit).toBeDisabled();

    // A malformed slug keeps it disabled + surfaces the field error.
    await user.type(screen.getByTestId('onboarding-tenant-id'), 'Bad_Slug');
    await user.type(screen.getByTestId('onboarding-org-name'), 'Acme');
    expect(submit).toBeDisabled();
    expect(screen.getByTestId('onboarding-tenant-id-error')).toBeInTheDocument();

    // Fix the slug → enabled.
    await user.clear(screen.getByTestId('onboarding-tenant-id'));
    await user.type(screen.getByTestId('onboarding-tenant-id'), 'acme-corp');
    expect(submit).not.toBeDisabled();
  });
});

describe('CreateOrganizationForm — submit outcomes', () => {
  it('201 ready:true → navigates to the console (/)', async () => {
    const submit = vi.fn().mockResolvedValue(
      jsonResponse(201, { tenantId: 'acme-corp', ready: true }),
    );
    const navigate = vi.fn();
    const user = userEvent.setup();
    render(<CreateOrganizationForm submit={submit} navigate={navigate} />);

    await user.type(screen.getByTestId('onboarding-tenant-id'), 'acme-corp');
    await user.type(screen.getByTestId('onboarding-org-name'), 'Acme Corp');
    await user.click(screen.getByTestId('onboarding-submit'));

    await waitFor(() => expect(navigate).toHaveBeenCalledWith('/'));
    expect(submit).toHaveBeenCalledWith({
      tenantId: 'acme-corp',
      organizationName: 'Acme Corp',
    });
  });

  it('201 ready:false → navigates to /login to complete via a fresh login', async () => {
    const submit = vi.fn().mockResolvedValue(
      jsonResponse(201, { tenantId: 'acme-corp', ready: false }),
    );
    const navigate = vi.fn();
    const user = userEvent.setup();
    render(<CreateOrganizationForm submit={submit} navigate={navigate} />);

    await user.type(screen.getByTestId('onboarding-tenant-id'), 'acme-corp');
    await user.type(screen.getByTestId('onboarding-org-name'), 'Acme Corp');
    await user.click(screen.getByTestId('onboarding-submit'));

    await waitFor(() =>
      expect(navigate).toHaveBeenCalledWith('/login?onboarded=1'),
    );
  });

  it('409 TENANT_ALREADY_EXISTS → inline actionable error, no navigation', async () => {
    const submit = vi.fn().mockResolvedValue(
      jsonResponse(409, { code: 'TENANT_ALREADY_EXISTS' }),
    );
    const navigate = vi.fn();
    const user = userEvent.setup();
    render(<CreateOrganizationForm submit={submit} navigate={navigate} />);

    await user.type(screen.getByTestId('onboarding-tenant-id'), 'acme-corp');
    await user.type(screen.getByTestId('onboarding-org-name'), 'Acme Corp');
    await user.click(screen.getByTestId('onboarding-submit'));

    const err = await screen.findByTestId('onboarding-error');
    expect(err).toHaveTextContent('이미 사용 중인 조직 ID');
    expect(navigate).not.toHaveBeenCalled();
    // Re-enabled for a retry (not stuck pending).
    expect(screen.getByTestId('onboarding-submit')).not.toBeDisabled();
  });

  // TASK-MONO-784 S6 (ADR-MONO-082 b OD-1) — the owned-tenant limit.
  it('422 OPERATOR_OWNED_TENANT_LIMIT_EXCEEDED → inline actionable error, no navigation', async () => {
    const submit = vi.fn().mockResolvedValue(
      jsonResponse(422, { code: 'OPERATOR_OWNED_TENANT_LIMIT_EXCEEDED' }),
    );
    const navigate = vi.fn();
    const user = userEvent.setup();
    render(<CreateOrganizationForm submit={submit} navigate={navigate} />);

    await user.type(screen.getByTestId('onboarding-tenant-id'), 'acme-corp');
    await user.type(screen.getByTestId('onboarding-org-name'), 'Acme Corp');
    await user.click(screen.getByTestId('onboarding-submit'));

    const err = await screen.findByTestId('onboarding-error');
    expect(err).toHaveTextContent('한 계정으로 만들 수 있는 회사 수를 초과했습니다');
    expect(navigate).not.toHaveBeenCalled();
    expect(screen.getByTestId('onboarding-submit')).not.toBeDisabled();
  });

  it('503 unavailable → inline retryable error, session preserved (no navigation)', async () => {
    const submit = vi.fn().mockResolvedValue(
      jsonResponse(503, { code: 'DOWNSTREAM_ERROR' }),
    );
    const navigate = vi.fn();
    const user = userEvent.setup();
    render(<CreateOrganizationForm submit={submit} navigate={navigate} />);

    await user.type(screen.getByTestId('onboarding-tenant-id'), 'acme-corp');
    await user.type(screen.getByTestId('onboarding-org-name'), 'Acme Corp');
    await user.click(screen.getByTestId('onboarding-submit'));

    expect(await screen.findByTestId('onboarding-error')).toBeInTheDocument();
    expect(navigate).not.toHaveBeenCalled();
  });
});

/**
 * TASK-MONO-773 S6 — the self-mode form of the «테넌트 생성» screen (console-integration-contract § 2.6.4 error
 * table). Codes are IAM's; the copy is the contract's. `logout` is injectable (defaults to the shared
 * RP-initiated logout — the same one the account menu uses).
 */
describe('CreateOrganizationForm — 773 trust-gate answers (§ 2.6.4 error table)', () => {
  async function submitWith(
    status: number,
    body: unknown,
    extra: Record<string, unknown> = {},
  ) {
    const submit = vi.fn().mockResolvedValue(jsonResponse(status, body));
    const navigate = vi.fn();
    const user = userEvent.setup();
    const Form = CreateOrganizationForm as unknown as React.ComponentType<Record<string, unknown>>;
    render(
      <Form
        submit={submit}
        navigate={navigate}
        mode="first"
        emailVerificationUrl="http://iam.local/email-verification"
        {...extra}
      />,
    );
    await user.type(screen.getByTestId('onboarding-tenant-id'), 'acme-corp');
    await user.type(screen.getByTestId('onboarding-org-name'), 'Acme Corp');
    await user.click(screen.getByTestId('onboarding-submit'));
    const err = await screen.findByTestId('onboarding-error');
    return { err, navigate, user };
  }

  it('🔴 403 EMAIL_NOT_VERIFIED → «회사를 만들려면 먼저 이메일을 인증해야 합니다» + link to the IAM verification page', async () => {
    const { err, navigate } = await submitWith(403, { code: 'EMAIL_NOT_VERIFIED' });
    expect(err).toHaveTextContent('회사를 만들려면 먼저 이메일을 인증해야 합니다');
    expect(screen.getByTestId('onboarding-error-verify-link')).toHaveAttribute(
      'href',
      'http://iam.local/email-verification',
    );
    expect(navigate).not.toHaveBeenCalled();
  });

  it('🔴 403 ONBOARDING_ACCOUNT_NOT_ELIGIBLE → «개인 계정으로 로그인해 만드세요» + logout', async () => {
    const logout = vi.fn();
    const { err, user } = await submitWith(403, { code: 'ONBOARDING_ACCOUNT_NOT_ELIGIBLE' }, { logout });
    expect(err).toHaveTextContent('이 계정으로는 회사를 만들 수 없습니다');
    expect(err).toHaveTextContent('개인 계정으로 로그인해 만드세요');
    await user.click(screen.getByTestId('onboarding-error-logout'));
    expect(logout).toHaveBeenCalledTimes(1);
  });

  it('🔴 409 OPERATOR_ALREADY_PROVISIONED → «이 계정은 지금 회사를 새로 만들 수 없습니다»', async () => {
    const { err } = await submitWith(409, { code: 'OPERATOR_ALREADY_PROVISIONED' });
    expect(err).toHaveTextContent('이 계정은 지금 회사를 새로 만들 수 없습니다');
  });

  it('🔴 429 RATE_LIMITED → «잠시 후 다시 시도하세요»', async () => {
    const { err } = await submitWith(429, { code: 'RATE_LIMITED' });
    expect(err).toHaveTextContent('잠시 후 다시 시도하세요');
  });

  it('🔵 control — 409 TENANT_ALREADY_EXISTS keeps its slug-taken copy and has no verify link / logout', async () => {
    const { err } = await submitWith(409, { code: 'TENANT_ALREADY_EXISTS' });
    expect(err).toHaveTextContent('이미 사용 중인');
    expect(screen.queryByTestId('onboarding-error-verify-link')).toBeNull();
    expect(screen.queryByTestId('onboarding-error-logout')).toBeNull();
  });
});

describe('CreateOrganizationForm — 773 modes (first company vs second company)', () => {
  const Form = CreateOrganizationForm as unknown as React.ComponentType<Record<string, unknown>>;

  it('🔴 the button says «테넌트 만들기» (one name for the one act — 773 Goal)', () => {
    render(<Form submit={vi.fn()} navigate={vi.fn()} mode="first" />);
    expect(screen.getByTestId('onboarding-submit')).toHaveTextContent('테넌트 만들기');
  });

  it('🔴 second-company mode says the new tenant is ADDED and the existing companies are untouched', () => {
    render(<Form submit={vi.fn()} navigate={vi.fn()} mode="additional" />);
    const panel = screen.getByTestId('onboarding-what-happens');
    expect(panel).toHaveTextContent('지금 소속된 회사의 권한은 그대로');
  });
});
