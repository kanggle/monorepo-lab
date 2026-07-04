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
